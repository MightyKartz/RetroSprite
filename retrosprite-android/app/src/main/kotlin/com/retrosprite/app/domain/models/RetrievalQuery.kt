package com.retrosprite.app.domain.models

/**
 * Input to the retrieval pipeline.
 *
 * @param gameId Resolved canonical game id; `null` when the game is unknown
 *   (retrieval should typically short-circuit and the policy should ask for
 *   clarification).
 * @param normalizedQuery Question after locale-aware normalization
 *   (trim / lowercase / whitespace-collapse).
 * @param language ISO 639-1 language tag of the query / answer.
 * @param progressGate Optional player-progress tag that limits which
 *   snippets are admissible.
 * @param spoilerLevel The session's spoiler tolerance — evidence above
 *   this level must be filtered out.
 * @param limit Maximum number of [RetrievalResult] entries to return.
 */
data class RetrievalQuery(
    val gameId: String?,
    val normalizedQuery: String,
    val language: String,
    val progressGate: String? = null,
    val spoilerLevel: SpoilerLevel,
    val limit: Int = 5,
)
