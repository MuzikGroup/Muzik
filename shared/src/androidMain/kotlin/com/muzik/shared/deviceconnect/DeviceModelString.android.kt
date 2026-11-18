/**
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.muzik.shared.deviceconnect

import android.os.Build

actual fun getDeviceModelString(): String = "${Build.MANUFACTURER} ${Build.MODEL}"

internal actual fun getDefaultDeviceName(): String = getDeviceModelString()
