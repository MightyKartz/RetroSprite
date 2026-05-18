package com.retrosprite.app.domain.models

import kotlinx.serialization.Serializable

/**
 * A single retrieved knowledge snippet that may back an answer.
 *
 * @param sourceId Stable id of the underlying document / fts row.
 * @param snippet Short excerpt (≤ a few hundred chars) used for prompt
 *   stitching and citations.
 * @param score Normalized retrieval score in `[0.0, 1.0]`. Higher = better.
 * @param spoilerLevel Maximum spoiler level this snippet is allowed to leak.
 *   The policy must drop snippets whose level exceeds the session's
 *   tolerance.
 * @param progressGate Optional player-progress requirement (free-form tag,
 *   e.g. "post-credits", "chapter-3"). `null` when no gating required.
 */
@Serializable
data class Evidence(
    val sourceId: String,
    val snippet: String,
    val score: Double,
    val spoilerLevel: SpoilerLevel,
    val progressGate: String? = null,
)
