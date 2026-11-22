package com.muzik.shared.youtube.playback

import com.muzik.innertubex.cipher.PlayerConfigRepository
import com.muzik.shared.settings.Settings

object MuzikPlayerConfigRepository : PlayerConfigRepository {
    // Hints only load from the MuzikGroup/faraday registry; the library rejects every other source.
    override val enabled: Boolean = true

    override val sourceUrl: String
        get() = Settings.shared.githubNTransformConfigUrl

    override val defaultSourceUrl: String
        get() = Settings.DEFAULT_FARADAY_PLAYER_CONFIG_URL

    override var cachedJson: String
        get() = Settings.shared.playerConfigRemoteJson
        set(value) {
            Settings.shared.playerConfigRemoteJson = value
        }

    override var cachedAtMs: Long
        get() = Settings.shared.playerConfigRemoteFetchedAt
        set(value) {
            Settings.shared.playerConfigRemoteFetchedAt = value
        }

    override var cachedSourceUrl: String
        get() = Settings.shared.playerConfigRemoteSourceUrl
        set(value) {
            Settings.shared.playerConfigRemoteSourceUrl = value
        }

    override var cachedEtag: String
        get() = Settings.shared.playerConfigRemoteEtag
        set(value) {
            Settings.shared.playerConfigRemoteEtag = value
        }
}
