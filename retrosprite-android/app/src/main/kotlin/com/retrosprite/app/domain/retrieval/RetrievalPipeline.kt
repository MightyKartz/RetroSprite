package com.retrosprite.app.domain.retrieval

import com.retrosprite.app.domain.models.RetrievalQuery
import com.retrosprite.app.domain.models.RetrievalResult

/**
 * Knowledge retrieval pipeline.
 *
 * Phase 1 implementation will:
 *  - call `KnowledgeRepository.searchFts(...)` against the curated SQLite
 *    + FTS5 index for the resolved game
 *  - apply `progressGate` / `spoilerLevel` filtering on the returned rows
 *  - rank / dedupe by entity and return the top-N
 */
interface RetrievalPipeline {

    /** Search the knowledge base for snippets relevant to [query]. */
    suspend fun retrieve(query: RetrievalQuery): List<RetrievalResult>

    /**
     * Locale-aware question normalization applied before retrieval and
     * also used as the cache key.
     */
    suspend fun normalizeQuestion(raw: String, language: String): String
}
