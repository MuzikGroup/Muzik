package com.muzik.desktop.deviceconnect

import com.muzik.shared.deviceconnect.DeviceConnectDeviceType
import com.muzik.shared.deviceconnect.DeviceConnectManager

object DesktopDeviceConnect {
    val manager = DeviceConnectManager(localDeviceType = DeviceConnectDeviceType.DESKTOP)
}
