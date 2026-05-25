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
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["request_key"])
    ]
)
data class RequestLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "request_key")
    val requestKey: String = "",

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

    @ColumnInfo(name = "question")
    val question: String? = null,

    @ColumnInfo(name = "question_source")
    val questionSource: String? = null,

    @ColumnInfo(name = "raw_question")
    val rawQuestion: String? = null,

    @ColumnInfo(name = "normalized_question")
    val normalizedQuestion: String? = null,

    @ColumnInfo(name = "question_normalization_reason")
    val questionNormalizationReason: String? = null,

    @ColumnInfo(name = "normalized_question_matched_term")
    val normalizedQuestionMatchedTerm: String? = null,

    @ColumnInfo(name = "normalized_question_matched_entity_id")
    val normalizedQuestionMatchedEntityId: String? = null,

    @ColumnInfo(name = "answer_short")
    val answerShort: String? = null,

    @ColumnInfo(name = "answer_detail")
    val answerDetail: String? = null,

    @ColumnInfo(name = "answer_type")
    val answerType: String? = null,

    @ColumnInfo(name = "answer_confidence")
    val answerConfidence: String? = null,

    @ColumnInfo(name = "spoiler_level_used")
    val spoilerLevelUsed: String? = null,

    @ColumnInfo(name = "source_ids")
    val sourceIds: String? = null,

    @ColumnInfo(name = "next_actions")
    val nextActions: String? = null,

    @ColumnInfo(name = "suggested_questions")
    val suggestedQuestions: String? = null,

    @ColumnInfo(name = "response_text")
    val responseText: String,

    @ColumnInfo(name = "error_message")
    val errorMessage: String?,

    @ColumnInfo(name = "duration_millis")
    val durationMillis: Long = 0L,

    @ColumnInfo(name = "llm_status")
    val llmStatus: String? = null,

    @ColumnInfo(name = "llm_provider")
    val llmProvider: String? = null,

    @ColumnInfo(name = "llm_model")
    val llmModel: String? = null,

    @ColumnInfo(name = "llm_max_tokens")
    val llmMaxTokens: Int? = null,

    @ColumnInfo(name = "llm_timeout_ms")
    val llmTimeoutMs: Long? = null,

    @ColumnInfo(name = "llm_latency_ms")
    val llmLatencyMs: Long? = null,

    @ColumnInfo(name = "llm_tokens_in")
    val llmTokensIn: Int = 0,

    @ColumnInfo(name = "llm_tokens_out")
    val llmTokensOut: Int = 0,

    @ColumnInfo(name = "llm_error")
    val llmError: String? = null,

    @ColumnInfo(name = "feedback")
    val feedback: String? = null,

    @ColumnInfo(name = "feedback_timestamp")
    val feedbackTimestamp: Long? = null
)
