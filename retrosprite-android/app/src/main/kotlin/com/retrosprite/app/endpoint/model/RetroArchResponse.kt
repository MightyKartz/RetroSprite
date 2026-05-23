package com.retrosprite.app.endpoint.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Response payload returned to the RetroArch frontend.
 *
 * All fields are optional per the AI Service spec. The frontend decides how to render based
 * on which fields are present (e.g. `text` for an OSD overlay, `image` for a Base64 PNG
 * overlay, `text_position` 1=top / 2=bottom, `press` to inject button input, `auto` to
 * auto-resume after pause, `error` to surface a non-fatal protocol error).
 */
@Serializable
data class RetroArchResponse(
    val text: String? = null,
    val image: String? = null,
    val sound: String? = null,
    val text_position: Int? = null,
    val press: List<String>? = null,
    val auto: Int? = null,
    val error: String? = null,
    @Transient
    val diagnostics: ResponseDiagnostics = ResponseDiagnostics(),
) {
    companion object {
        /** Convenience builder for the most common case: a textual answer. */
        fun text(
            content: String,
            textPosition: Int? = null,
            diagnostics: ResponseDiagnostics = ResponseDiagnostics(),
        ): RetroArchResponse = RetroArchResponse(
            text = content,
            text_position = textPosition,
            diagnostics = diagnostics,
        )

        /** Convenience builder for protocol-level errors (still HTTP 200). */
        fun error(message: String): RetroArchResponse = RetroArchResponse(error = message)
    }
}

data class ResponseDiagnostics(
    val question: String? = null,
    val questionSource: String? = null,
    val answerShort: String? = null,
    val answerDetail: String? = null,
    val answerType: String? = null,
    val answerConfidence: String? = null,
    val spoilerLevelUsed: String? = null,
    val nextActions: List<String> = emptyList(),
    val llmStatus: String? = null,
    val llmProvider: String? = null,
    val llmModel: String? = null,
    val llmMaxTokens: Int? = null,
    val llmTimeoutMs: Long? = null,
    val llmLatencyMs: Long? = null,
    val llmTokensIn: Int = 0,
    val llmTokensOut: Int = 0,
    val llmError: String? = null,
)

/** Tiny payload returned by the `/health` route — useful for diagnostics surfaces. */
@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
)

/** Loopback-only summary returned by `/debug/latest-request`. */
@Serializable
data class DebugLatestRequestResponse(
    val has_entry: Boolean,
    val timestamp: Long? = null,
    val label: String? = null,
    val system: String? = null,
    val game: String? = null,
    val image_bytes: Int? = null,
    val paused: Boolean? = null,
    val output_mode: String? = null,
    val is_debug: Boolean? = null,
    val ok: Boolean? = null,
    val question: String? = null,
    val question_source: String? = null,
    val answer_short: String? = null,
    val answer_detail: String? = null,
    val answer_type: String? = null,
    val answer_confidence: String? = null,
    val spoiler_level_used: String? = null,
    val next_actions: List<String> = emptyList(),
    val pipeline_stage: String? = null,
    val llm_status: String? = null,
    val source_ids: List<String> = emptyList(),
    val response_preview: String? = null,
    val error_message: String? = null,
    val duration_ms: Long? = null,
    val llm_provider: String? = null,
    val llm_model: String? = null,
    val llm_max_tokens: Int? = null,
    val llm_timeout_ms: Long? = null,
    val llm_latency_ms: Long? = null,
    val llm_tokens_in: Int? = null,
    val llm_tokens_out: Int? = null,
    val llm_error: String? = null,
) {
    companion object {
        fun empty(): DebugLatestRequestResponse = DebugLatestRequestResponse(has_entry = false)
    }
}
