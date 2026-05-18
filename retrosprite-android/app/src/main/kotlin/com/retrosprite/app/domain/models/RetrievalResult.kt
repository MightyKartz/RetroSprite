package com.retrosprite.app.domain.models

/**
 * One ranked retrieval hit referencing one knowledge entity (e.g. an item,
 * NPC, location). Carries the supporting [Evidence] used for downstream
 * prompt construction and citation.
 */
data class RetrievalResult(
    val entityId: String,
    val canonicalName: String,
    val evidence: List<Evidence>,
    /** Aggregate confidence in `[0.0, 1.0]`. */
    val confidence: Double,
)
