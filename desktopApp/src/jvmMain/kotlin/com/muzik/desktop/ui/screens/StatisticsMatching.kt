package com.muzik.desktop.ui.screens

import com.muzik.desktop.history.AlbumStat
import com.muzik.shared.model.AlbumItem
import com.muzik.shared.ui.statistics.matchingStatisticsAlbum

internal fun statisticsArtistName(id: String): String = id.removePrefix("artist:").substringAfter(':')

internal fun List<AlbumItem>.matchingStatisticsAlbum(album: AlbumStat): AlbumItem? = matchingStatisticsAlbum(album.name, album.artists)
