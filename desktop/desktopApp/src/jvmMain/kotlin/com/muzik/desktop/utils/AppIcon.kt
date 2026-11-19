/*
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | Built by Ignatius (lead developer) and the Pipo Team
 */

package com.muzik.desktop.utils

import java.awt.Image
import java.awt.Taskbar
import java.awt.Window
import javax.imageio.ImageIO

/**
 * Hard-coded application icon.
 *
 * The bundled Muzik logo is loaded straight from the packaged resources and
 * applied at every size the operating system asks for, so the taskbar button,
 * Alt-Tab switcher, pinned taskbar entries and window decorations always show
 * the M logo. Nothing here depends on the theme, playback state or any other
 * runtime color: the icon is fixed at build time and identical everywhere.
 */
object AppIcon {
    // The sizes Windows wants for its window-icon slots: small taskbar button
    // (16/24), Alt-Tab and window decorations (32/48), shortcut overlays (64),
    // pinned previews and the AWT Taskbar API (128/256).
    private val ICON_SIZES = intArrayOf(16, 24, 32, 48, 64, 128, 256)

    @Volatile
    private var cachedImages: List<Image>? = null

    /** All hard-coded icon sizes, scaled smoothly from the bundled 512px logo. */
    fun awtIconImages(): List<Image> = cachedImages ?: loadIconImages().also { cachedImages = it }

    private fun loadIconImages(): List<Image> {
        val base =
            Thread
                .currentThread()
                .contextClassLoader
                .getResourceAsStream("logo.png")
                ?.use { stream -> ImageIO.read(stream) }
                ?: return emptyList()

        return ICON_SIZES.map { size -> base.getScaledInstance(size, size, Image.SCALE_SMOOTH) }
    }

    /**
     * Applies the hard-coded logo to an [Window] across every icon slot.
     *
     * [Window.setIconImages] drives the window, taskbar and Alt-Tab icons on
     * Windows and Linux; the [Taskbar] API is set as well so pinned taskbar
     * buttons and tray-associated previews keep the logo on every supported
     * desktop.
     */
    fun install(window: Window) {
        runCatching {
            val images = awtIconImages()
            if (images.isNotEmpty()) {
                window.setIconImages(images)
            }
        }.onFailure {
            AppLogger.warning("AppIcon", "Unable to apply window icon images", it)
        }
        runCatching {
            if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
                awtIconImages().lastOrNull()?.let { Taskbar.getTaskbar().iconImage = it }
            }
        }.onFailure {
            AppLogger.warning("AppIcon", "Unable to apply Taskbar icon image", it)
        }
    }
}
