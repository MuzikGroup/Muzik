package com.muzik.desktop.utils

import java.io.File

/**
 * Self-healing for repeated GPU render crashes: when the JVM dies with a
 * native `hs_err_pid*.log` that implicates the active Compose render backend
 * (skiko / GPU driver), automatically downgrade the backend for the next
 * launch instead of silently crashing again.
 *
 * Windows users hit this most: Direct3D device-lost scenarios (driver reset,
 * sleep/resume, dock/undock, driver updates) kill the process with no Java
 * exception, which looks exactly like "the app keeps shutting down by
 * itself". The fallback chain is the platform's own backend list, e.g.
 * Windows: AUTO (Direct3D) -> OPENGL -> SOFTWARE.
 */
internal object DesktopRenderAutoFallback {
    /** Only crashes from the last two weeks count, so a fixed driver stops influencing the choice. */
    const val MAX_AGE_MS: Long = 14L * 24 * 60 * 60 * 1000

    /** One crash is noise (power loss, unrelated driver bug); repeated crashes mean a real incompatibility. */
    const val MIN_IMPLICATED_CRASHES: Int = 2

    private const val SUMMARY_LIMIT: Int = 64 * 1024

    /** GPU driver DLLs shipped with vendor OpenGL ICDs (and Mesa on Linux). `libgl1`/`libGL.so` but never `libglib`. */
    private val OPENGL_DRIVER_MARKERS = Regex("(?i)nvoglv|atiogl|ig9icd|ig4icd|mesa|swrast|libgles|libgl1|libgl\\.so")

    /** GPU driver DLLs shipped with vendor Direct3D user-mode drivers plus the D3D runtime itself. */
    private val DIRECT3D_DRIVER_MARKERS = Regex("(?i)direct3d|d3d1[012]|dxgi|dxva|nvwgf|amdxc|atidxx|igd1[02]|igd10|igd12")

    /** skiko is almost always in the frames of a render crash. */
    private val SKIKO_MARKER = Regex("(?i)skiko")

    private val METAL_MARKER = Regex("(?i)metal")

    private val OPENGL_API_MARKER = Regex("(?i)opengl")

    /** Extracts the `Problematic frame` library name from a hs_err log summary. */
    internal fun problematicFrame(summary: String): String? {
        val lines = summary.lines()
        val headerIndex = lines.indexOfFirst { it.contains("Problematic frame", ignoreCase = true) }
        val candidates = if (headerIndex >= 0) lines.drop(headerIndex + 1) else lines
        return candidates
            .firstOrNull { it.trimStart().startsWith("#") && "[" in it }
            ?.substringAfter("[")
            ?.substringBefore("]")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    /**
     * Attributes a crash log to the render backend whose stack dominates it.
     * Returns null when the crash is unrelated to rendering (no skiko frame,
     * no GPU driver involved).
     */
    internal fun crashBackend(summary: String): DesktopRenderBackend? {
        val frame = problematicFrame(summary)
        val frameImplicatesRender =
            frame != null && (SKIKO_MARKER.containsMatchIn(frame) || METAL_MARKER.containsMatchIn(frame))
        val gpuDriver =
            when {
                OPENGL_DRIVER_MARKERS.containsMatchIn(summary) -> DesktopRenderBackend.OPENGL
                DIRECT3D_DRIVER_MARKERS.containsMatchIn(summary) -> DesktopRenderBackend.DIRECT3D
                else -> null
            }
        val renderCrash = frameImplicatesRender || gpuDriver != null
        if (!renderCrash) return null
        // A named GPU driver pins the backend down; otherwise skiko's frames reveal the API.
        if (gpuDriver != null) return gpuDriver
        if (METAL_MARKER.containsMatchIn(summary) && !OPENGL_API_MARKER.containsMatchIn(summary)) {
            return DesktopRenderBackend.METAL
        }
        if (OPENGL_API_MARKER.containsMatchIn(summary)) return DesktopRenderBackend.OPENGL
        // Ambiguous skiko crash with no API markers: leave it unattributed rather than guess.
        return null
    }

    /**
     * GPU-free / safer backends in escalating order for the platform.
     * The first entry is what a crashed accelerated backend degrades to, the
     * last entry (always SOFTWARE) is terminal.
     */
    internal fun safeChain(osName: String, osArch: String): List<DesktopRenderBackend> =
        when {
            osName.startsWith("Mac", ignoreCase = true) -> listOf(DesktopRenderBackend.OPENGL, DesktopRenderBackend.SOFTWARE)
            osName.startsWith("Windows", ignoreCase = true) &&
                (osArch.equals("aarch64", true) || osArch.equals("arm64", true)) ->
                // Windows-on-ARM skiko has no OPENGL backend.
                listOf(DesktopRenderBackend.SOFTWARE)
            osName.startsWith("Windows", ignoreCase = true) -> listOf(DesktopRenderBackend.OPENGL, DesktopRenderBackend.SOFTWARE)
            else -> listOf(DesktopRenderBackend.SOFTWARE)
        }

    /**
     * Decides the fallback backend from recent crash files.
     *
     * @param choice the persisted user setting (usually AUTO; explicit user choices are respected).
     * @return the backend to force via `skiko.renderApi`, or null to keep the current configuration.
     */
    fun fallback(
        crashes: List<DesktopRenderCrash>,
        choice: String,
        osName: String,
        osArch: String,
        nowMs: Long = System.currentTimeMillis(),
        maxAgeMs: Long = MAX_AGE_MS,
    ): DesktopRenderBackend? {
        val current =
            DesktopRenderBackend.entries.firstOrNull { it.name.equals(choice, ignoreCase = true) }
                ?: DesktopRenderBackend.AUTO
        // An explicitly selected backend is the user's own call - never silently override it.
        if (current != DesktopRenderBackend.AUTO) return null
        val chain = safeChain(osName, osArch)
        val counts =
            crashes
                .asSequence()
                .filter { nowMs - it.modifiedAtMs in 0..maxAgeMs }
                .mapNotNull { crashBackend(it.summary) }
                .groupingBy { it }
                .eachCount()
        // Crashes are attributed per backend; take the most frequent one and prefer the
        // more conservative backend on ties, so a machine where OPENGL crashes too moves
        // straight to SOFTWARE.
        val crashing =
            counts.entries
                .filter { it.value >= MIN_IMPLICATED_CRASHES }
                .maxByOrNull { (backend, count) -> count * 10 + chain.indexOf(backend) + 1 }
                ?.key
                ?: return null
        return when (val index = chain.indexOf(crashing)) {
            -1 -> chain.firstOrNull() // an accelerated backend crashed: degrade to the first safer one
            else -> chain.getOrNull(index + 1) // already on a safe backend: try the next, or give up
        }
    }

    /** Loads recent hs_err logs from the log directory, newest first, bounded for cheap startup. */
    fun collectCrashes(
        directory: File,
        nowMs: Long = System.currentTimeMillis(),
        maxAgeMs: Long = MAX_AGE_MS,
    ): List<DesktopRenderCrash> =
        runCatching {
            directory
                .listFiles()
                .orEmpty()
                .filter { it.isFile && it.name.startsWith("hs_err_pid") && it.name.endsWith(".log") }
                .filter { nowMs - it.lastModified() in 0..maxAgeMs }
                .sortedByDescending(File::lastModified)
                .take(5)
                .map { file ->
                    DesktopRenderCrash(
                        file = file,
                        modifiedAtMs = file.lastModified(),
                        summary = readSummary(file),
                    )
                }
        }.getOrDefault(emptyList())

    private fun readSummary(file: File): String =
        runCatching {
            file.reader().use { reader ->
                val buffer = CharArray(SUMMARY_LIMIT)
                var count = 0
                while (count < SUMMARY_LIMIT) {
                    val read = reader.read(buffer, count, SUMMARY_LIMIT - count)
                    if (read < 0) break
                    count += read
                }
                String(buffer, 0, count)
            }
        }.getOrDefault("")
}

/** One JVM hs_err crash file with the metadata needed to attribute it to a render backend. */
internal data class DesktopRenderCrash(
    val file: File,
    val modifiedAtMs: Long,
    val summary: String,
)
