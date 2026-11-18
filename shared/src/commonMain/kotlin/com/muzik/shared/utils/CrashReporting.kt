package com.muzik.shared.utils

import com.muzik.shared.BuildConfig

fun isCrashReportingAvailable(appVersion: String = BuildConfig.APP_VERSION): Boolean = '+' !in appVersion
