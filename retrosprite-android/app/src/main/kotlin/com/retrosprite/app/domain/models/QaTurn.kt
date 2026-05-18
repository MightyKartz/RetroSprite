package com.retrosprite.app.domain.models

import kotlinx.serialization.Serializable

/**
 * A single previous Q/A turn carried in [SessionContext] for context-aware
 * retrieval / answer composition (Phase 1+).
 */
@Serializable
data class QaTurn(
    val question: String,
    val answer: String,
    /** Epoch milliseconds when the turn was answered. */
    val timestamp: Long,
)
