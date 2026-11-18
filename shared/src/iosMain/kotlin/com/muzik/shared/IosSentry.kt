package com.muzik.shared

import com.muzik.shared.utils.isCrashReportingAvailable

/** Sideloading changes bundle IDs and drops version suffixes, so neither identifies the release. */
fun iosSentryRelease(): String = "com.muzik.ios@${BuildConfig.APP_VERSION}"

fun iosSentrySourceRevision(): String = BuildConfig.SOURCE_REVISION

fun iosCrashReportingAvailable(): Boolean = isCrashReportingAvailable()
