/**
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.muzik.shared.db.entities

sealed class LocalItem {
    abstract val id: String
    abstract val title: String
    abstract val thumbnailUrl: String?
}
