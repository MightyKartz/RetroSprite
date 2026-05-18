package com.retrosprite.app.data.db.dao

import androidx.room.Dao
import androidx.room.RawQuery
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.retrosprite.app.data.db.entity.KnowledgeEntity

/**
 * FTS5-backed search over the `knowledge_fts` virtual table.
 *
 * The virtual table is created lazily by [com.retrosprite.app.data.db.RetroSpriteDatabase]
 * via a [androidx.room.RoomDatabase.Callback]. If FTS5 is not available on
 * the host SQLite, the virtual table is absent and callers must fall back
 * to [KnowledgeDao.fallbackSearch].
 *
 * We use [@RawQuery] because Room cannot statically validate the FTS5
 * table (it is not a Room entity).
 */
@Dao
abstract class KnowledgeFtsDao {

    @RawQuery
    protected abstract suspend fun rawSearch(query: SupportSQLiteQuery): List<KnowledgeEntity>

    /**
     * Returns knowledge rows matching [match] (FTS5 query syntax) for [gameId],
     * ordered by BM25 ranking.
     */
    suspend fun search(gameId: String, match: String, limit: Int): List<KnowledgeEntity> {
        val sql = """
            SELECT k.* FROM knowledge AS k
            JOIN knowledge_fts AS fts ON k.id = fts.rowid
            WHERE knowledge_fts MATCH ? AND k.game_id = ?
            ORDER BY bm25(knowledge_fts)
            LIMIT ?
        """.trimIndent()
        val args = arrayOf<Any>(match, gameId, limit)
        return rawSearch(SimpleSQLiteQuery(sql, args))
    }
}
