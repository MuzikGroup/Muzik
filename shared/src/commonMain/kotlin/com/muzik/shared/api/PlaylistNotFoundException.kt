package com.muzik.shared.api

class PlaylistNotFoundException(
    val playlistId: String,
) : Exception()
