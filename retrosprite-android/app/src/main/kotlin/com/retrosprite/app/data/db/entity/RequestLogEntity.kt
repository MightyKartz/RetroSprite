package com.retrosprite.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent record of a single AI Service request handled by RetroSprite.
 *
 * Notes:
 * - We never store ROM bytes here. `game` is the human/system label, not a hash.
 * - `responseText` is the LLM textual answer surfaced back to RetroArch.
 */
@Entity(
    tableName = "request_logs",
    indices = [Index(value = ["timestamp"])]
)
data class RequestLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "label")
    val label: String,

    @ColumnInfo(name = "system")
    val system: String?,

    @ColumnInfo(name = "game")
    val game: String?,

    @ColumnInfo(name = "image_size")
    val imageSize: Int,

    @ColumnInfo(name = "paused")
    val paused: Boolean,

    @ColumnInfo(name = "output_mode")
    val outputMode: String,

    @ColumnInfo(name = "response_text")
    val responseText: String,

    @ColumnInfo(name = "error_message")
    val errorMessage: String?
)
