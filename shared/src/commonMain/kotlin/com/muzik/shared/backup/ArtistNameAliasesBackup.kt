package com.muzik.shared.backup

import com.muzik.shared.constants.ArtistNameAliasesJsonKey
import com.muzik.shared.settings.ArtistNameAliases
import com.muzik.shared.settings.PortableSetting
import com.muzik.shared.settings.PortableSettings
import com.muzik.shared.settings.SettingType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun PortableSettings?.withLegacyArtistNameAliases(serialized: String): PortableSettings {
    val aliases = ArtistNameAliases.deserialize(serialized)
    val settings = this ?: PortableSettings(entries = emptyList())
    return settings.copy(
        entries =
            settings.entries.filterNot { it.key == ArtistNameAliasesJsonKey } +
                PortableSetting(ArtistNameAliasesJsonKey, SettingType.STRING, Json.encodeToString(aliases)),
    )
}
