package com.retrosprite.app.domain.models

import kotlinx.serialization.Serializable

/**
 * Identifies a game across the resolver / retrieval / policy layers.
 *
 * Phase 0: typically resolved purely from the RetroArch core+content label
 * (e.g. `snes__super_mario_world`). Phase 1 will additionally consult ROM
 * hash and a curated repository.
 *
 * @param gameId Stable canonical id when known (e.g. an internal slug, or
 *   a hash-based id). `null` until a knowledge source confirms the game.
 * @param title Human readable title (already prettified for display).
 * @param platform Platform / system slug (e.g. "snes", "nes", "gba").
 *   Falls back to "unknown" when label cannot be parsed.
 * @param region Optional region tag ("us", "jp", "eu", ...). `null` when unknown.
 * @param source How this identity was obtained: "label", "rom_hash",
 *   "user_pick", or "unknown".
 */
@Serializable
data class GameIdentity(
    val gameId: String?,
    val title: String,
    val platform: String,
    val region: String? = null,
    val source: String,
) {
    companion object {
        /** Sentinel used when no signal is available. */
        fun unknown(): GameIdentity = GameIdentity(
            gameId = null,
            title = "unknown",
            platform = "unknown",
            region = null,
            source = "unknown",
        )
    }
}
