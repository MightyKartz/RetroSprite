package com.retrosprite.app.domain

import com.retrosprite.app.domain.models.SpoilerLevel

/**
 * Top-level orchestration for one AI-Service request.
 *
 * Wires GameResolver → RetrievalPipeline → AnswerPolicy → AnswerComposer
 * and returns the final user-visible string. The endpoint layer
 * (`com.retrosprite.app.endpoint`) calls into this interface — it MUST NOT
 * touch any of the underlying resolver/retrieval/policy types directly so
 * we can re-wire internals without breaking the HTTP contract.
 */
interface QueryPipeline {

    /**
     * Run the full pipeline for one request.
     *
     * All parameters mirror the RetroArch AI-Service request body fields
     * (see protocol spec). Reasonable defaults match Phase 0 expectations.
     *
     * @param label RetroArch core+content label, e.g. `"snes__super_mario_world"`.
     *   Empty string is tolerated and treated as "unknown game".
     * @param romHash Optional ROM content hash. Phase 0: ignored.
     * @param question Player question (text mode). May be `null` when
     *   RetroArch sent a screenshot-only request.
     * @param screenshot Base64-encoded PNG of the current frame, or `null`.
     * @param state Verbatim controller / register state map, or `null`.
     * @param spoilerLevel Player's spoiler tolerance for this session.
     * @param language ISO 639-1 tag — defaults to Simplified Chinese.
     * @return The final answer text. Always non-null, never empty.
     */
    suspend fun answer(
        label: String,
        romHash: String? = null,
        question: String? = null,
        screenshot: String? = null,
        state: Map<String, Int>? = null,
        spoilerLevel: SpoilerLevel = SpoilerLevel.LIGHT,
        language: String = "zh",
    ): String
}
