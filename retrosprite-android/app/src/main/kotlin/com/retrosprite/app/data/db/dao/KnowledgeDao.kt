package com.retrosprite.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.retrosprite.app.data.db.entity.KnowledgeEntity

@Dao
interface KnowledgeDao {

    @Query(
        """
        SELECT * FROM knowledge
        WHERE game_id = :gameId AND entity_id = :entityId
        LIMIT 1
        """
    )
    suspend fun getByEntityId(gameId: String, entityId: String): KnowledgeEntity?

    @Query(
        """
        SELECT * FROM knowledge
        WHERE game_id = :gameId AND entity_type = :entityType
        ORDER BY canonical_name ASC
        """
    )
    suspend fun listByType(gameId: String, entityType: String): List<KnowledgeEntity>

    @Query("SELECT * FROM knowledge WHERE game_id = :gameId")
    suspend fun listByGame(gameId: String): List<KnowledgeEntity>

    /**
     * LIKE-based fallback used when FTS5 is unavailable on the device.
     * Matches against canonical name, aliases (raw JSON) and short description.
     */
    @Query(
        """
        SELECT * FROM knowledge
        WHERE game_id = :gameId AND (
            canonical_name LIKE :likePattern OR
            aliases_json LIKE :likePattern OR
            description_short LIKE :likePattern
        )
        ORDER BY canonical_name ASC
        LIMIT :limit
        """
    )
    suspend fun fallbackSearch(
        gameId: String,
        likePattern: String,
        limit: Int
    ): List<KnowledgeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<KnowledgeEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: KnowledgeEntity): Long

    @Query("DELETE FROM knowledge WHERE game_id = :gameId")
    suspend fun clearForGame(gameId: String): Int

    @Query("DELETE FROM knowledge")
    suspend fun clear()
}
