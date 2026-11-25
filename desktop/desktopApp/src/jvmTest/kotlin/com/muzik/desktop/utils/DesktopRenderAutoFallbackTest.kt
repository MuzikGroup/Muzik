package com.muzik.desktop.utils

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopRenderAutoFallbackTest {
    private fun crash(
        summary: String,
        modifiedAtMs: Long = 1_000L,
    ): DesktopRenderCrash = DesktopRenderCrash(file = File("hs_err_pid${modifiedAtMs}.log"), modifiedAtMs = modifiedAtMs, summary = summary)

    private val d3dCrash =
        crash(
            """
            # A fatal error has been detected by the Java Runtime Environment:
            #  EXCEPTION_ACCESS_VIOLATION (0xc0000005)
            # Problematic frame:
            # C  [libskiko-windows-x64.dll+0x1a2b3]
            #
            # Native frames: (J=compiled Java code, j=interpreted, Vv=VM code, C=native C++ code)
            # C  [libskiko-windows-x64.dll+0x1a2b3]  skiko::render::direct3d::DeviceContext::draw
            # C  [d3d11.dll+0x3210]
            # C  [dxgi.dll+0x1234]
            """.trimIndent(),
        )

    private val glDriverCrash =
        crash(
            """
            # Problematic frame:
            # C  [nvoglv64.dll+0x9a1b2]
            #
            # Native frames:
            # C  [nvoglv64.dll+0x9a1b2]
            # C  [libskiko-windows-x64.dll+0x4567]
            """.trimIndent(),
        )

    private val unrelatedCrash =
        crash(
            """
            # Problematic frame:
            # C  [jvm.dll+0xabc]
            #
            # Native frames:
            # V  [jvm.dll+0xabc]
            # C  [kernel32.dll+0x1234]
            """.trimIndent(),
        )

    @Test
    fun explicitUserBackendChoiceIsNeverOverridden() {
        for (choice in listOf("DIRECT3D", "OPENGL", "SOFTWARE", "METAL")) {
            assertNull(
                DesktopRenderAutoFallback.fallback(
                    crashes = List(5) { d3dCrash },
                    choice = choice,
                    osName = "Windows 11",
                    osArch = "amd64",
                ),
                choice,
            )
        }
    }

    @Test
    fun unknownChoiceIsTreatedAsAuto() {
        assertEquals(
            DesktopRenderBackend.OPENGL,
            DesktopRenderAutoFallback.fallback(
                crashes = List(2) { d3dCrash },
                choice = "not-a-backend",
                osName = "Windows 11",
                osArch = "amd64",
            ),
        )
    }

    @Test
    fun repeatedDirect3dCrashesFallBackToOpenGlOnWindows() {
        assertEquals(
            DesktopRenderBackend.OPENGL,
            DesktopRenderAutoFallback.fallback(
                crashes = listOf(d3dCrash, d3dCrash),
                choice = "AUTO",
                osName = "Windows 11",
                osArch = "amd64",
            ),
        )
    }

    @Test
    fun singleCrashDoesNotFallBack() {
        assertNull(
            DesktopRenderAutoFallback.fallback(
                crashes = listOf(d3dCrash),
                choice = "AUTO",
                osName = "Windows 11",
                osArch = "amd64",
            ),
        )
    }

    @Test
    fun openGlCrashesMoveStraightToSoftware() {
        assertEquals(
            DesktopRenderBackend.SOFTWARE,
            DesktopRenderAutoFallback.fallback(
                crashes = listOf(glDriverCrash, glDriverCrash, d3dCrash, d3dCrash),
                choice = "AUTO",
                osName = "Windows 11",
                osArch = "amd64",
            ),
        )
    }

    @Test
    fun softwareIsTerminalOnWindowsArm64() {
        // Windows-on-ARM has no OPENGL backend: after DIRECT3D the only GPU-free step is SOFTWARE.
        val direct = DesktopRenderAutoFallback.fallback(
            crashes = listOf(glDriverCrash, glDriverCrash),
            choice = "AUTO",
            osName = "Windows 11",
            osArch = "aarch64",
        )
        assertEquals(DesktopRenderBackend.SOFTWARE, direct)
    }

    @Test
    fun unrelatedCrashesNeverTriggerFallback() {
        assertNull(
            DesktopRenderAutoFallback.fallback(
                crashes = List(4) { unrelatedCrash },
                choice = "AUTO",
                osName = "Windows 11",
                osArch = "amd64",
            ),
        )
    }

    @Test
    fun gtkLibraryCrashesAreNotMistakenForOpenGl() {
        assertNull(
            DesktopRenderAutoFallback.crashBackend(
                """
                # Problematic frame:
                # C  [libglib-2.0.so.0+0x1234]
                """.trimIndent(),
            ),
        )
        // Real Mesa GL crashes still attribute.
        assertEquals(
            DesktopRenderBackend.OPENGL,
            DesktopRenderAutoFallback.crashBackend("# C  [libGL.so.1+0x5678]"),
        )
    }

    @Test
    fun staleCrashesAreIgnored() {
        assertNull(
            DesktopRenderAutoFallback.fallback(
                crashes = listOf(crash(d3dCrash.summary, modifiedAtMs = 0L)),
                choice = "AUTO",
                osName = "Windows 11",
                osArch = "amd64",
                nowMs = DesktopRenderAutoFallback.MAX_AGE_MS + 1,
                maxAgeMs = DesktopRenderAutoFallback.MAX_AGE_MS,
            ),
        )
    }

    @Test
    fun macDefaultsToMetalThenOpenGl() {
        assertEquals(
            DesktopRenderBackend.OPENGL,
            DesktopRenderAutoFallback.fallback(
                crashes = List(2) { crash("# Problematic frame:\n# C  [libskiko-macos-aarch64.dylib+0x1] Metal render loop") },
                choice = "AUTO",
                osName = "Mac OS X",
                osArch = "aarch64",
            ),
        )
    }

    @Test
    fun problematicFrameParsesHeaderAndBracketLine() {
        val withHeader =
            """
            # Problematic frame:
            # C  [libskiko-windows-x64.dll+0x1a2b3]
            """.trimIndent()
        assertEquals("libskiko-windows-x64.dll+0x1a2b3", DesktopRenderAutoFallback.problematicFrame(withHeader))
        assertEquals(
            "libskiko-windows-x64.dll+0x1a2b3",
            DesktopRenderAutoFallback.problematicFrame("# C  [libskiko-windows-x64.dll+0x1a2b3]"),
        )
        assertNull(DesktopRenderAutoFallback.problematicFrame("no frames here"))
    }

    @Test
    fun ambiguousSkikoCrashWithoutMarkersIsNotAttributed() {
        assertNull(DesktopRenderAutoFallback.crashBackend("# C  [libskiko-windows-x64.dll+0x1]"))
        // But an ambiguous skiko crash on a machine with driver DLLs loaded still attributes.
        val withDriver =
            "# C  [libskiko-windows-x64.dll+0x1]\n# C  [d3d11.dll+0x2]"
        assertEquals(DesktopRenderBackend.DIRECT3D, DesktopRenderAutoFallback.crashBackend(withDriver))
    }

    @Test
    fun collectCrashesReadsRecentHsErrFilesOnly() {
        val dir = kotlin.io.path.createTempDirectory("muzik-logs").toFile()
        try {
            val fresh = File(dir, "hs_err_pid1.log").apply { writeText("# C  [libskiko-windows-x64.dll+0x1]"); setLastModified(5_000L) }
            File(dir, "hs_err_pid2.log").apply {
                writeText("# C  [libskiko-windows-x64.dll+0x2]")
                setLastModified(0L) // far too old
            }
            File(dir, "unrelated.txt").apply { writeText("not a crash") }
            val crashes = DesktopRenderAutoFallback.collectCrashes(dir, nowMs = 10_000L, maxAgeMs = 9_000L)
            assertEquals(listOf(fresh.name), crashes.map { it.file.name })
            assertTrue(crashes.single().summary.contains("libskiko"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
