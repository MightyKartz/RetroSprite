package com.retrosprite.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.retrosprite.app.data.db.entity.RequestLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RequestLogDao {

    @Query(
        """
        SELECT * FROM request_logs
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    fun observeRecent(limit: Int): Flow<List<RequestLogEntity>>

    @Query("SELECT COUNT(*) FROM request_logs")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RequestLogEntity): Long

    @Query(
        """
        UPDATE request_logs
        SET feedback = :feedback,
            feedback_timestamp = :timestamp
        WHERE request_key = :requestKey
        """
    )
    suspend fun updateFeedbackByRequestKey(
        requestKey: String,
        feedback: String,
        timestamp: Long,
    ): Int

    @Query("DELETE FROM request_logs")
    suspend fun clear()

    /**
     * Removes every entry except the most recent [keep] (ordered by timestamp DESC).
     * Should be called after each insert to bound storage.
     */
    @Query(
        """
        DELETE FROM request_logs WHERE id NOT IN (
            SELECT id FROM request_logs ORDER BY timestamp DESC LIMIT :keep
        )
        """
    )
    suspend fun trimOldest(keep: Int): Int
}
