package com.muzik.desktop.youtube

import com.metrolist.innertubex.bodyAsTextLimited
import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.TokenProviderCapabilities
import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Desktop PO-token provider: runs the bgutil-ytdlp-pot-provider sidecar on
 * loopback and mints Web BotGuard tokens for the playback clients that
 * require them. This is the compact, sidecar-only port of Metrolist's
 * DesktopTokenProvider — tokenless profiles stay first in line, and the
 * sidecar unlocks the token-gated clients whenever YouTube tightens its
 * bot checks. The binary ships bundled with the app or is fetched from the
 * upstream GitHub release with a pinned SHA-256.
 */
class DesktopTokenProvider(
    private val client: HttpClient = createTokenHttpClient(),
    providerUrl: String? = configuredProviderUrl(),
) : TokenProvider {

    @Serializable
    private data class PotRequestBody(val content_binding: String)

    @Serializable
    private data class PotResponse(val poToken: String, val contentBinding: String)

    private data class BinarySpec(
        val resourceRelativePath: String,
        val assetName: String,
        val assetSha256: String,
        val binaryFileName: String,
    )

    private data class Endpoint(val baseUrl: String, val host: String, val port: Int, val managed: Boolean)

    class SidecarUnavailableException(message: String) : Exception(message)

    private val mutex = Mutex()
    private val bootstrapScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var bootstrap: Deferred<Boolean>? = null
    private val endpoint = parseEndpoint(providerUrl)
    private val sidecarClient =
        HttpClient(client.engine) {
            expectSuccess = false
            followRedirects = false
            install(HttpTimeout) {
                requestTimeoutMillis = 5 * 60 * 1_000L
                connectTimeoutMillis = 30_000L
                socketTimeoutMillis = 60_000L
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

    override val capabilities: TokenProviderCapabilities
        get() =
            TokenProviderCapabilities(
                providers =
                    if (endpoint.managed) setOf(PoTokenProviderKind.WEB_BOTGUARD) else configuredExternalKinds(),
            )

    private var process: Process? = null

    @Volatile
    internal var usedBundledBinary = false
        private set
    private var sidecarPlayerRequestToken: String? = null
    private var sidecarPlayerVisitorData: String? = null
    private var sidecarPlayerExpiresAtMs = 0L

    override suspend fun getPoToken(
        videoId: String,
        visitorData: String,
        cookie: String?,
    ): PoTokenResult? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    if (!ensureServerRunning()) return@withLock null
                    val cachedPlayerToken =
                        sidecarPlayerRequestToken
                            ?.takeIf {
                                sidecarPlayerVisitorData == visitorData &&
                                    System.currentTimeMillis() < sidecarPlayerExpiresAtMs
                            }?.let { PotResponse(it, visitorData) }
                    val (playerToken, streamingToken) =
                        coroutineScope {
                            val playerRequest = if (cachedPlayerToken == null) async { requestToken(visitorData) } else null
                            val streamingRequest = async { requestToken(videoId) }
                            (cachedPlayerToken ?: checkNotNull(playerRequest).await()) to streamingRequest.await()
                        }
                    if (playerToken.contentBinding != visitorData || streamingToken.contentBinding != videoId) {
                        return@withLock null
                    }
                    if (cachedPlayerToken == null) {
                        sidecarPlayerRequestToken = playerToken.poToken
                        sidecarPlayerVisitorData = visitorData
                        sidecarPlayerExpiresAtMs = System.currentTimeMillis() + TOKEN_CACHE_TTL_MS
                    }
                    PoTokenResult(
                        playerRequestToken = playerToken.poToken,
                        streamingDataToken = streamingToken.poToken,
                        visitorData = visitorData,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
        }

    override suspend fun prewarm(cookie: String?) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!ensureServerRunning()) return@withLock
                runCatching { requestToken("prewarm") }
            }
        }
    }

    override suspend fun close() {
        bootstrapScope.cancel()
        withContext(Dispatchers.IO) {
            mutex.withLock {
                bootstrap?.cancelAndJoin()
                bootstrap = null
                sidecarPlayerRequestToken = null
                sidecarPlayerVisitorData = null
                sidecarPlayerExpiresAtMs = 0L
                process?.let(::stopProcess)
                process = null
            }
        }
    }

    private suspend fun requestToken(contentBinding: String): PotResponse {
        val response =
            sidecarClient.post("${endpoint.baseUrl}/get_pot") {
                contentType(ContentType.Application.Json)
                setBody(PotRequestBody(contentBinding))
            }
        if (!response.status.isSuccess()) {
            response.bodyAsChannel().cancel(null)
            throw SidecarUnavailableException("PO token sidecar request failed with HTTP ${response.status.value}")
        }
        return POT_RESPONSE_JSON.decodeFromString(response.bodyAsTextLimited(MAX_SIDECAR_RESPONSE_BYTES))
    }

    private suspend fun ensureServerRunning(): Boolean {
        if (isServerRunning()) return true
        if (!endpoint.managed) return false
        val task =
            bootstrap ?: bootstrapScope
                .async {
                    startServer()
                }.also { bootstrap = it }
        return try {
            task.await()
        } finally {
            if (task.isCompleted) bootstrap = null
        }
    }

    private suspend fun isServerRunning(): Boolean =
        try {
            sidecarClient.get("${endpoint.baseUrl}/ping").status.isSuccess()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }

    private suspend fun startServer(): Boolean {
        val spec = binarySpec() ?: return false
        val bundled = bundledSidecarPath(spec)
        val path = bundled ?: cachedSidecarPath(spec) ?: return false
        usedBundledBinary = bundled != null
        return try {
            val p =
                ProcessBuilder(path, "server", "--host", "127.0.0.1", "--port", endpoint.port.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start()
            process = p
            val deadlineMs = 15_000L // first launch may be a 51 MB download
            val startMs = System.currentTimeMillis()
            while (System.currentTimeMillis() - startMs < deadlineMs) {
                Thread.sleep(100)
                if (!p.isAlive) break
                if (isServerRunning()) return true
            }
            stopProcess(p)
            process = null
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun binarySpec(): BinarySpec? {
        val architecture = System.getProperty("os.arch").lowercase()
        val isArm64 = architecture.contains("aarch64") || architecture.contains("arm64")
        val isX64 = architecture.contains("amd64") || architecture.contains("x86_64") || architecture == "x64"
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("win") && isX64 ->
                BinarySpec(
                    "windows/bgutil-ytdlp-pot-provider-rs.exe",
                    "bgutil-pot-windows-x86_64.exe",
                    "25d6b05c79176aa792454c3d1727922ca47e56cf11cb1e866615d751819b14a0",
                    "bgutil-ytdlp-pot-provider-rs.exe",
                )
            osName.contains("mac") || osName.contains("darwin") ->
                when {
                    isArm64 ->
                        BinarySpec(
                            "macos/bgutil-ytdlp-pot-provider-rs-arm64",
                            "bgutil-pot-macos-aarch64",
                            "34b83baf0a557fecaa6d67a8177e53e169c2ccf987182883a4bae289a7176883",
                            "bgutil-ytdlp-pot-provider-rs-arm64",
                        )
                    isX64 ->
                        BinarySpec(
                            "macos/bgutil-ytdlp-pot-provider-rs-x86_64",
                            "bgutil-pot-macos-x86_64",
                            "0391175fa938c7fabbb8b40a40bd43182ef75af97e1dd3fab56eb23b4ac3e113",
                            "bgutil-ytdlp-pot-provider-rs-x86_64",
                        )
                    else -> null
                }
            osName.contains("linux") ->
                when {
                    isArm64 ->
                        BinarySpec(
                            "linux/bgutil-ytdlp-pot-provider-rs-aarch64",
                            "bgutil-pot-linux-aarch64",
                            "4f4a1f681dba45e695e1c14d314517da180a1fd374afd09d634fd80ef6d0284b",
                            "bgutil-ytdlp-pot-provider-rs-aarch64",
                        )
                    isX64 ->
                        BinarySpec(
                            "linux/bgutil-ytdlp-pot-provider-rs-x86_64",
                            "bgutil-pot-linux-x86_64",
                            "e7c264a574fa2705b6e5dc62283a8a4e80130f27b9d7e9df44e6b09aa6151a87",
                            "bgutil-ytdlp-pot-provider-rs-x86_64",
                        )
                    else -> null
                }
            else -> null
        }
    }

    private fun bundledSidecarPath(spec: BinarySpec): String? {
        val resourcesDir = System.getProperty("compose.application.resources.dir") ?: return null
        val dir = File(resourcesDir)
        val candidate =
            listOf(File(dir, spec.resourceRelativePath), File(dir, spec.binaryFileName))
                .firstOrNull { isLikelyNativeBinary(it) } ?: return null
        if (!candidate.canExecute()) candidate.setExecutable(true)
        return candidate.takeIf { it.canExecute() }?.absolutePath
    }

    /** Downloads the upstream sidecar into ~/.cache/muzik/bgutil-pot-provider when absent. */
    private suspend fun cachedSidecarPath(spec: BinarySpec): String? {
        val cacheRoot = File(System.getProperty("user.home"), ".cache/muzik/bgutil-pot-provider")
        val target = File(cacheRoot, spec.binaryFileName)
        if (isLikelyNativeBinary(target)) {
            if (sha256(target) == spec.assetSha256) {
                if (target.canExecute()) return target.absolutePath
                if (target.setExecutable(true) && target.canExecute()) return target.absolutePath
            } else {
                println("DesktopTokenProvider: cached sidecar failed its pinned SHA-256 check; re-downloading")
            }
        }

        val download = File(cacheRoot, "${spec.assetName}.download")
        return try {
            cacheRoot.mkdirs()
            download.delete()
            downloadAsset(spec.assetName, download)
            check(sha256(download) == spec.assetSha256) { "Downloaded PO token sidecar failed its SHA-256 check" }
            check(isLikelyNativeBinary(download)) { "Downloaded PO token sidecar is not a native executable" }
            runCatching {
                Files.move(download.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }.getOrElse {
                Files.move(download.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            if (!target.setExecutable(true) || !target.canExecute()) null else target.absolutePath
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            download.delete()
        }
    }

    private suspend fun downloadAsset(
        assetName: String,
        destination: File,
    ) {
        val url = "https://github.com/$POT_PROVIDER_REPOSITORY/releases/download/$POT_PROVIDER_VERSION/$assetName"
        client.prepareGet(url).execute { response ->
            check(response.status.isSuccess()) { "PO token sidecar download returned HTTP ${response.status.value}" }
            val expectedLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            check(expectedLength == null || expectedLength in 1..MAX_SIDECAR_DOWNLOAD_BYTES) {
                "PO token sidecar download exceeded its size limit"
            }
            var downloadedLength = 0L
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
            FileOutputStream(destination).use { output ->
                while (true) {
                    val count = channel.readAvailable(buffer, 0, buffer.size)
                    if (count == -1) break
                    if (count == 0) continue
                    check(downloadedLength <= MAX_SIDECAR_DOWNLOAD_BYTES - count) {
                        "PO token sidecar download exceeded its size limit"
                    }
                    output.write(buffer, 0, count)
                    downloadedLength += count
                }
            }
            check(downloadedLength > 0L) { "PO token sidecar download was empty" }
            check(expectedLength == null || downloadedLength == expectedLength) {
                "PO token sidecar download was incomplete"
            }
        }
    }

    /** Rejects placeholders without ELF, Mach-O or PE magic before trusting a sidecar binary. */
    private fun isLikelyNativeBinary(file: File): Boolean {
        if (!file.isFile || file.length() < 4) return false
        return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(4)
                if (input.read(header) != 4) return false
                val elf = header[0] == 0x7F.toByte() && header[1] == 'E'.code.toByte() &&
                    header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte()
                val machO1 = header[0] == 0xCF.toByte() && header[1] == 0xFA.toByte() &&
                    header[2] == 0xED.toByte() && header[3] == 0xFE.toByte()
                val machO2 = header[0] == 0xFE.toByte() && header[1] == 0xED.toByte() &&
                    header[2] == 0xFA.toByte() && header[3] == 0xCE.toByte()
                val pe = header[0] == 'M'.code.toByte() && header[1] == 'Z'.code.toByte()
                elf || machO1 || machO2 || pe
            }
        }.getOrDefault(false)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun stopProcess(activeProcess: Process) {
        activeProcess.destroyForcibly()
        runCatching { activeProcess.waitFor(PROCESS_STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
    }

    private fun parseEndpoint(configuredUrl: String?): Endpoint {
        val managed = configuredUrl.isNullOrBlank()
        val url = Url(if (managed) DEFAULT_URL else configuredUrl.trim())
        require(url.protocol == URLProtocol.HTTP) { "PO token provider must use loopback HTTP" }
        require(url.host == "127.0.0.1" || url.host == "::1") { "PO token provider must use a loopback address" }
        return Endpoint(url.toString().trimEnd('/'), url.host, url.port, managed)
    }

    private fun configuredExternalKinds(): Set<PoTokenProviderKind> = setOf(PoTokenProviderKind.WEB_BOTGUARD)

    companion object {
        private const val POT_PROVIDER_REPOSITORY = "jim60105/bgutil-ytdlp-pot-provider-rs"
        private const val POT_PROVIDER_VERSION = "v0.8.1"
        private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024
        private const val MAX_SIDECAR_DOWNLOAD_BYTES = 64L * 1024L * 1024L
        private const val MAX_SIDECAR_RESPONSE_BYTES = 1 * 1024 * 1024
        private const val PROCESS_STOP_TIMEOUT_SECONDS = 1L
        private const val TOKEN_CACHE_TTL_MS = 10L * 60L * 1000L
        private const val DEFAULT_URL = "http://127.0.0.1:4416"
        private val POT_RESPONSE_JSON = Json { ignoreUnknownKeys = true }

        private fun configuredProviderUrl(): String? =
            System.getProperty("innerTubePoTokenProviderUrl")?.takeIf(String::isNotBlank)
                ?: System.getenv("INNERTUBE_PO_TOKEN_PROVIDER_URL")?.takeIf(String::isNotBlank)

        private fun createTokenHttpClient(): HttpClient = HttpClient(CIO) { expectSuccess = false }
    }
}
