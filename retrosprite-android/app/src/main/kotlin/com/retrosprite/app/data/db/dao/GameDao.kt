package com.retrosprite.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.retrosprite.app.data.db.entity.GameEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {

    @Query("SELECT * FROM games ORDER BY title ASC")
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games ORDER BY title ASC")
    suspend fun getAll(): List<GameEntity>

    @Query("SELECT * FROM games WHERE game_id = :gameId LIMIT 1")
    suspend fun getById(gameId: String): GameEntity?

    @Query("SELECT * FROM games WHERE rom_sha1 = :sha1 LIMIT 1")
    suspend fun getByRomSha1(sha1: String): GameEntity?

    @Query("SELECT * FROM games WHERE rom_crc32 = :crc32 LIMIT 1")
    suspend fun getByRomCrc32(crc32: String): GameEntity?

    @Query(
        """
        SELECT * FROM games
        WHERE platform = :platform AND title LIKE :titlePattern
        ORDER BY title ASC
        """
    )
    suspend fun searchByLabel(platform: String, titlePattern: String): List<GameEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GameEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<GameEntity>)

    @Query("UPDATE games SET enabled = :enabled, disabled_at = :disabledAt WHERE game_id = :gameId")
    suspend fun setEnabled(gameId: String, enabled: Boolean, disabledAt: Long?)

    @Delete
    suspend fun delete(entity: GameEntity)

    @Query("DELETE FROM games")
    suspend fun clear()
}
