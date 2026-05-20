package com.retrosprite.app.domain.resolver

import com.retrosprite.app.domain.models.GameIdentity

/**
 * Phase 0 [GameResolver] that derives a [GameIdentity] purely from the
 * RetroArch label using the convention `"<system>__<title>"` (double
 * underscore separator).
 *
 * Splitting rules — only the FIRST `__` acts as the separator. This is
 * intentional so that titles which themselves contain `__` (rare but
 * possible in RetroArch playlists, e.g. `"genesis__sonic_2__hack"`) keep
 * the trailing portion intact as the title.
 *
 * Title prettification: underscores become spaces and each whitespace-split
 * word is title-cased.
 *
 * Boundary behavior:
 * - empty / blank label                → [GameIdentity.unknown]
 * - label without `__`                 → platform = "unknown", title = label (prettified)
 * - label like `"a__"` (empty title)   → platform = "a",       title = "unknown"
 * - label like `"__b"` (empty system)  → platform = "unknown", title = b (prettified)
 *
 * Phase 1 will additionally consult a ROM-hash → game id index and the
 * curated game repository before falling back to label parsing.
 */
class LabelGameResolver : GameResolver {

    override suspend fun resolve(label: String, romHash: String?): GameIdentity {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return GameIdentity.unknown()

        val sepIndex = trimmed.indexOf(SEPARATOR)
        val rawPlatform: String
        val rawTitle: String
        if (sepIndex < 0) {
            rawPlatform = ""
            rawTitle = trimmed
        } else {
            rawPlatform = trimmed.substring(0, sepIndex)
            rawTitle = trimmed.substring(sepIndex + SEPARATOR.length)
        }

        val platform = rawPlatform.ifBlank { "unknown" }
            .lowercase()
            .toCanonicalPlatform()
        val title = prettifyTitle(rawTitle).ifBlank { "unknown" }

        return GameIdentity(
            gameId = null,
            title = title,
            platform = platform,
            region = null,
            source = "label",
        )
    }

    /** Replace `_` with space, collapse whitespace, title-case each word. */
    private fun prettifyTitle(raw: String): String {
        if (raw.isBlank()) return ""
        val withSpaces = raw.replace('_', ' ').trim()
        if (withSpaces.isEmpty()) return ""
        return withSpaces
            .split(WHITESPACE)
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase() else ch.toString()
                }
            }
    }

    companion object {
        private const val SEPARATOR = "__"
        private val WHITESPACE = Regex("\\s+")
    }
}

private fun String.toCanonicalPlatform(): String =
    when (this) {
        "mega_drive", "megadrive", "genesis" -> "md"
        else -> this
    }
