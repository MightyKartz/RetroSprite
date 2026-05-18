package com.retrosprite.app.domain.resolver

import com.retrosprite.app.domain.models.GameIdentity

/**
 * Resolves a [GameIdentity] for the currently running content.
 *
 * Implementations may consult any combination of:
 * - the RetroArch core+content label (always available)
 * - a ROM hash (Phase 1+, when the launcher exposes it)
 * - a curated [com.retrosprite.app.data.repository.GameRepository] (Phase 1+)
 * - explicit user picks (Phase 1+ disambiguation flow)
 *
 * The resolver MUST never block on network and MUST be safe to call from
 * any coroutine dispatcher.
 */
interface GameResolver {
    /**
     * @param label RetroArch label, typically `"<core>__<content>"` (e.g.
     *   `"snes__super_mario_world"`). May be empty.
     * @param romHash Optional ROM content hash (Phase 1+). Ignored in Phase 0.
     */
    suspend fun resolve(label: String, romHash: String? = null): GameIdentity
}
