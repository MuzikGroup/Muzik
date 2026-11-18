package com.muzik.shared.db

fun interface OfflineSongAvailability {
    suspend fun songIds(): Set<String>
}
