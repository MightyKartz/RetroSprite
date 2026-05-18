package com.retrosprite.app.data.repository

import com.retrosprite.app.data.db.RetroSpriteDatabase
import com.retrosprite.app.data.db.dao.KnowledgeDao
import com.retrosprite.app.data.db.dao.KnowledgeFtsDao
import com.retrosprite.app.data.models.KnowledgeChunkDomain
import com.retrosprite.app.data.models.toDomain
import com.retrosprite.app.data.models.toEntity

/**
 * Knowledge retrieval contract used by the Phase 1 GKP retriever.
 *
 * `searchFts` automatically falls back to LIKE-based search when the
 * device SQLite does not support FTS5 (see [RetroSpriteDatabase.isFtsAvailable]).
 */
interface KnowledgeRepository {
    suspend fun searchFts(
        gameId: String,
        query: String,
        limit: Int = 10
    ): List<KnowledgeChunkDomain>

    suspend fun getByEntityId(gameId: String, entityId: String): KnowledgeChunkDomain?
    suspend fun listByGame(gameId: String): List<KnowledgeChunkDomain>
    suspend fun listByType(gameId: String, entityType: String): List<KnowledgeChunkDomain>
    suspend fun upsertAll(chunks: List<KnowledgeChunkDomain>)
    suspend fun clearForGame(gameId: String)
}

class DefaultKnowledgeRepository(
    private val database: RetroSpriteDatabase,
    private val dao: KnowledgeDao,
    private val ftsDao: KnowledgeFtsDao
) : KnowledgeRepository {

    override suspend fun searchFts(
        gameId: String,
        query: String,
        limit: Int
    ): List<KnowledgeChunkDomain> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val safeLimit = limit.coerceAtLeast(1)

        if (database.isFtsAvailable()) {
            runCatching {
                val matchExpr = buildFtsMatchExpression(trimmed)
                ftsDao.search(gameId, matchExpr, safeLimit)
            }.onSuccess { rows ->
                return rows.map { it.toDomain() }
            }
            // fall through to LIKE if FTS query failed at runtime
        }
        val likePattern = "%$trimmed%"
        return dao.fallbackSearch(gameId, likePattern, safeLimit)
            .map { it.toDomain() }
    }

    override suspend fun getByEntityId(
        gameId: String,
        entityId: String
    ): KnowledgeChunkDomain? = dao.getByEntityId(gameId, entityId)?.toDomain()

    override suspend fun listByGame(gameId: String): List<KnowledgeChunkDomain> =
        dao.listByGame(gameId).map { it.toDomain() }

    override suspend fun listByType(
        gameId: String,
        entityType: String
    ): List<KnowledgeChunkDomain> =
        dao.listByType(gameId, entityType).map { it.toDomain() }

    override suspend fun upsertAll(chunks: List<KnowledgeChunkDomain>) {
        dao.insertAll(chunks.map { it.toEntity() })
    }

    override suspend fun clearForGame(gameId: String) {
        dao.clearForGame(gameId)
    }

    /**
     * Sanitises a free-form query into something safe for FTS5 MATCH.
     * Any token containing characters outside `[A-Za-z0-9_]` is wrapped
     * in double quotes (with embedded quotes doubled) so users can search
     * for hyphens, apostrophes, etc. without triggering FTS syntax errors.
     * Each term is OR-joined to favour recall over precision in Phase 1.
     */
    private fun buildFtsMatchExpression(raw: String): String {
        val tokens = raw.split(WHITESPACE)
            .filter { it.isNotBlank() }
            .map { token ->
                if (token.all { it.isLetterOrDigit() || it == '_' }) {
                    token
                } else {
                    "\"" + token.replace("\"", "\"\"") + "\""
                }
            }
        if (tokens.isEmpty()) return "\"\""
        return tokens.joinToString(separator = " OR ")
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
