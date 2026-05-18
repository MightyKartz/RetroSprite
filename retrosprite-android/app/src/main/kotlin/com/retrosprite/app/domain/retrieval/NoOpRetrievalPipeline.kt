package com.retrosprite.app.domain.retrieval

import com.retrosprite.app.domain.models.RetrievalQuery
import com.retrosprite.app.domain.models.RetrievalResult

/**
 * Phase 0 [RetrievalPipeline] that pretends the knowledge base is empty.
 *
 * - [retrieve] always returns `emptyList()` — downstream policy in Phase 0
 *   ignores results anyway and emits a fixed acknowledgement.
 * - [normalizeQuestion] performs locale-agnostic, low-risk normalization
 *   that is safe for both Latin and CJK input:
 *     * trim leading/trailing whitespace
 *     * lowercase (no-op for CJK characters)
 *     * collapse internal runs of whitespace into a single space
 */
class NoOpRetrievalPipeline : RetrievalPipeline {

    override suspend fun retrieve(query: RetrievalQuery): List<RetrievalResult> = emptyList()

    override suspend fun normalizeQuestion(raw: String, language: String): String {
        return raw.trim()
            .lowercase()
            .replace(WHITESPACE, " ")
    }

    companion object {
        private val WHITESPACE = Regex("\\s+")
    }
}
