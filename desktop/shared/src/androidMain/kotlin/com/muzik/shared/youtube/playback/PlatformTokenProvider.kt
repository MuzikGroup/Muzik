package com.muzik.shared.youtube.playback

import com.muzik.innertubex.extraction.TokenProvider

@Suppress("FunctionName")
actual fun PlatformTokenProvider(): TokenProvider = AndroidTokenProvider(AndroidContextProvider.applicationContext)
