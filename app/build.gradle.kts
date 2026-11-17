import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File
import java.net.URI
import java.security.MessageDigest

val muzikVersion = providers.gradleProperty("muzik.version").orElse("7.8.4").get()
val muzikVersionCode = providers.gradleProperty("muzik.versionCode").orElse("70804").get().toInt()

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.desktop)
}

group = "com.muzikgroup"
version = muzikVersion

kotlin {
    jvmToolchain(21)
}

// Native presets that must ride the classpath so one build serves Windows,
// macOS (Intel + Apple Silicon) and Linux (x64 + ARM64).
val ffmpegPlatforms = listOf(
    "windows-x86_64",
    "macosx-x86_64",
    "macosx-arm64",
    "linux-x86_64",
    "linux-arm64",
)

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.encoding)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)

    // InnerTubeX: Metrolist's YouTube Music client library — browse/search plus
    // the full stream-extraction stack (multi-client fallback, SABR, ciphers).
    implementation(libs.innertubex)

    // OkHttp powers the bounded-range direct-media input of the player.
    implementation(libs.okhttp)

    // FFmpeg decode + Java Sound render for every desktop platform.
    implementation(libs.javacpp)
    implementation(libs.ffmpeg)
    ffmpegPlatforms.forEach { platform ->
        implementation("org.bytedeco:javacpp:${libs.versions.javacpp.get()}:$platform")
        implementation("org.bytedeco:ffmpeg:${libs.versions.ffmpegPresets.get()}:$platform")
    }
}

// PO-token sidecar binaries (bgutil-ytdlp-pot-provider-rs v0.8.1), pinned by SHA-256.
// Bundled into every native distribution so gated networks still get playback
// without a first-run download.
val sidecarVersion = "v0.8.1"
val sidecarRepo = "jim60105/bgutil-ytdlp-pot-provider-rs"
val sidecarAssets =
    listOf(
        arrayOf("windows", "bgutil-ytdlp-pot-provider-rs.exe", "bgutil-pot-windows-x86_64.exe", "25d6b05c79176aa792454c3d1727922ca47e56cf11cb1e866615d751819b14a0"),
        arrayOf("macos", "bgutil-ytdlp-pot-provider-rs-arm64", "bgutil-pot-macos-aarch64", "34b83baf0a557fecaa6d67a8177e53e169c2ccf987182883a4bae289a7176883"),
        arrayOf("macos", "bgutil-ytdlp-pot-provider-rs-x86_64", "bgutil-pot-macos-x86_64", "0391175fa938c7fabbb8b40a40bd43182ef75af97e1dd3fab56eb23b4ac3e113"),
        arrayOf("linux", "bgutil-ytdlp-pot-provider-rs-aarch64", "bgutil-pot-linux-aarch64", "4f4a1f681dba45e695e1c14d314517da180a1fd374afd09d634fd80ef6d0284b"),
        arrayOf("linux", "bgutil-ytdlp-pot-provider-rs-x86_64", "bgutil-pot-linux-x86_64", "e7c264a574fa2705b6e5dc62283a8a4e80130f27b9d7e9df44e6b09aa6151a87"),
    )

val sidecarResourcesDir = layout.buildDirectory.dir("sidecar-resources")

val downloadSidecarBinaries =
    tasks.register("downloadSidecarBinaries") {
        val outputDir = sidecarResourcesDir
        val assets = sidecarAssets
        val repo = sidecarRepo
        val version = sidecarVersion
        outputs.dir(outputDir)
        notCompatibleWithConfigurationCache("downloads release assets at execution time")
        doLast {
            val root = outputDir.get().asFile
            root.mkdirs()
            File(root, "common").mkdirs()
            val sha256Hex = { file: File ->
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().buffered().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        digest.update(buffer, 0, n)
                    }
                }
                digest.digest().joinToString("") { b -> "%02x".format(b) }
            }
            for (entry in sidecarAssets) {
                val parts = entry.toList()
                val dirName = parts[0]
                val fileName = parts[1]
                val assetName = parts[2]
                val sha = parts[3]
                val dir = File(root, dirName).apply { mkdirs() }
                val target = File(dir, fileName)
                if (target.isFile && sha256Hex(target) == sha) continue
                val url = "https://github.com/$repo/releases/download/$version/$assetName"
                val tmp = File(dir, "$assetName.download")
                tmp.delete()
                val conn = URI(url).toURL().openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 30_000
                conn.readTimeout = 120_000
                conn.setRequestProperty("User-Agent", "muzik-desktop-build")
                tmp.outputStream().use { out -> conn.getInputStream().use { it.copyTo(out) } }
                val got = sha256Hex(tmp)
                check(got == sha) { "Sidecar $assetName failed its SHA-256 check" }
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
                if (!target.setExecutable(true)) throw IllegalStateException("Cannot chmod sidecar $fileName")
                println("sidecar bundled: $dirName/$fileName (${target.length()} bytes)")
            }
        }
    }

compose.desktop {
    application {
        mainClass = "com.muzik.desktop.MainKt"

        val iconsDir = project.file("icons")

        nativeDistributions {
            appResourcesRootDir.set(sidecarResourcesDir)
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "Muzik"
            packageVersion = muzikVersion
            vendor = "MuzikGroup"
            description = "Muzik — a premium YouTube Music client for desktop"

            windows {
                iconFile.set(iconsDir.resolve("Muzik.ico"))
                menu = true
                dirChooser = false
                perUserInstall = true
            }
            macOS {
                iconFile.set(iconsDir.resolve("Muzik.icns"))
                bundleID = "com.muzikgroup.desktop"
                dockName = "Muzik"
            }
            linux {
                iconFile.set(iconsDir.resolve("muzik.png"))
                menuGroup = "Multimedia"
                debMaintainer = "motius@users.noreply.github.com"
                rpmLicenseType = "GPL-3.0"
            }
        }
    }
}

tasks.processResources {
    val v = providers.gradleProperty("muzik.version").orElse("7.8.4")
    val vc = providers.gradleProperty("muzik.versionCode").orElse("70804")
    filesMatching("version.properties") {
        expand("version" to v.get(), "versionCode" to vc.get())
    }
}

// Native distributions must always ship the (verified) sidecar binaries.
tasks.matching { it.name in setOf("createDistributable", "packageDistributionForCurrentOS", "packageMsi", "packageDmg", "packageDeb", "packageRpm", "packageReleaseDmg", "packageReleaseMsi", "packageReleaseDeb", "packageReleaseRpm", "prepareAppResources") }.configureEach {
    dependsOn(downloadSidecarBinaries)
}
