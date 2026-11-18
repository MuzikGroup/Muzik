/*
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.muzik.desktop.utils

import java.awt.EventQueue
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * System-level notifications for Muzik on the desktop.
 *
 * Messages are delivered through an AWT [TrayIcon] carrying the hard-coded M
 * logo, so update alerts and other notices reach the user even when the main
 * window is hidden in the tray. The icon is created on demand and removed
 * shortly after the message is shown, leaving the tray untouched.
 */
object DesktopNotifier {
    private val scope = MainScope()
    private var trayIcon: TrayIcon? = null
    private var cleanupJob: kotlinx.coroutines.Job? = null

    fun notify(
        title: String,
        message: String,
        onClick: (() -> Unit)? = null,
    ) {
        EventQueue.invokeLater {
            val tray = runCatching { SystemTray.getSystemTray() }.getOrNull() ?: return@invokeLater
            runCatching {
                val image =
                    Thread
                        .currentThread()
                        .contextClassLoader
                        .getResourceAsStream("logo.png")
                        ?.use { stream -> ImageIO.read(stream) }
                        ?: return@invokeLater

                val existing = trayIcon
                if (existing != null) {
                    tray.remove(existing)
                    trayIcon = null
                }

                val icon =
                    TrayIcon(image.getScaledInstance(16, 16, java.awt.Image.SCALE_SMOOTH), "Muzik").apply {
                        isImageAutoSize = true
                        if (onClick != null) {
                            addMouseListener(
                                object : MouseAdapter() {
                                    override fun mouseClicked(event: MouseEvent) {
                                        EventQueue.invokeLater { onClick() }
                                    }
                                },
                            )
                        }
                    }
                tray.add(icon)
                trayIcon = icon
                icon.displayMessage(title, message, TrayIcon.MessageType.INFO)

                cleanupJob?.cancel()
                cleanupJob =
                    scope.launch(Dispatchers.Default) {
                        delay(15_000)
                        EventQueue.invokeLater {
                            runCatching { tray.remove(icon) }
                            if (trayIcon === icon) trayIcon = null
                        }
                    }
            }.onFailure {
                AppLogger.warning("DesktopNotifier", "Unable to show system notification", it)
            }
        }
    }
}
