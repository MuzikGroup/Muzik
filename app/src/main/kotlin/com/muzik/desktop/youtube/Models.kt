package com.muzik.desktop.youtube

import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val videoId: String,
    val title: String,
    val artist: String,
    val artistBrowseId: String? = null,
    val album: String? = null,
    val albumBrowseId: String? = null,
    val durationText: String? = null,
    val durationSeconds: Int = 0,
    val thumbnail: String? = null,
    val isVideo: Boolean = false,
    val views: String? = null,
)

@Serializable
data class Album(
    val browseId: String,
    val title: String,
    val artist: String,
    val artistBrowseId: String? = null,
    val year: String? = null,
    val thumbnail: String? = null,
    val playlistId: String? = null,
)

@Serializable
data class Artist(
    val browseId: String,
    val name: String,
    val thumbnail: String? = null,
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
)

@Serializable
data class AlbumDetail(
    val album: Album,
    val tracks: List<Song>,
)

data class SearchPage(
    val songs: List<Song>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val continuation: String? = null,
)

data class StreamInfo(
    val url: String,
    val itag: Int,
    val bitrate: Int,
    val mimeType: String,
    val profileId: String,
)

enum class SearchFilter(val params: String, val label: String) {
    SONGS("EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D", "Songs"),
    VIDEOS("EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D", "Videos"),
    ALBUMS("EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D", "Albums"),
    ARTISTS("EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D", "Artists"),
}
