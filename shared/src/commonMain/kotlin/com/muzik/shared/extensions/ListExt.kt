/**
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | Built by Ignatius (lead developer) and the Pipo Team
 */

package com.muzik.shared.extensions

fun <T> List<T>.reversed(reversed: Boolean) = if (reversed) asReversed() else this
