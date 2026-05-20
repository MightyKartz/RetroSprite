package com.retrosprite.app.data.repository

import com.retrosprite.app.data.db.dao.GameDao
import com.retrosprite.app.data.models.GameDomain
import com.retrosprite.app.data.models.toDomain
import com.retrosprite.app.data.models.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Read/write contract for installed Game Knowledge Pack headers.
 * Phase 1 only needs a thin pass-through; Phase 2 will add validation
 * and signature checks.
 */
interface GameRepository {
    fun observeAll(): Flow<List<GameDomain>>
    suspend fun getById(gameId: String): GameDomain?
    suspend fun getByRomSha1(sha1: String): GameDomain?
    suspend fun getByRomCrc32(crc32: String): GameDomain?
    suspend fun searchByLabel(platform: String, titleQuery: String): List<GameDomain>
    suspend fun upsert(game: GameDomain)
    suspend fun setEnabled(gameId: String, enabled: Boolean, disabledAt: Long?) {
        val current = getById(gameId) ?: return
        upsert(current.copy(isEnabled = enabled, disabledAt = disabledAt))
    }
    suspend fun delete(gameId: String)
}

class DefaultGameRepository(
    private val dao: GameDao
) : GameRepository {

    override fun observeAll(): Flow<List<GameDomain>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(gameId: String): GameDomain? =
        dao.getById(gameId)?.toDomain()

    override suspend fun getByRomSha1(sha1: String): GameDomain? =
        dao.getByRomSha1(sha1)?.toDomain()

    override suspend fun getByRomCrc32(crc32: String): GameDomain? =
        dao.getByRomCrc32(crc32)?.toDomain()

    override suspend fun searchByLabel(platform: String, titleQuery: String): List<GameDomain> {
        val pattern = "%${titleQuery.trim()}%"
        return dao.searchByLabel(platform, pattern).map { it.toDomain() }
    }

    override suspend fun upsert(game: GameDomain) {
        dao.upsert(game.toEntity())
    }

    override suspend fun setEnabled(gameId: String, enabled: Boolean, disabledAt: Long?) {
        dao.setEnabled(gameId, enabled, disabledAt)
    }

    override suspend fun delete(gameId: String) {
        dao.getById(gameId)?.let { dao.delete(it) }
    }
}
