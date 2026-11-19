/**
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | Built by Ignatius (lead developer) and the Pipo Team
 *
 * Dedicated WebSocket HTTP client for DeviceConnect (with the WebSockets plugin
 * installed). Mirrors the existing [com.muzik.shared.network.createHttpClient].
 */

package com.muzik.shared.deviceconnect

import io.ktor.client.HttpClient

expect fun createDeviceConnectHttpClient(): HttpClient
