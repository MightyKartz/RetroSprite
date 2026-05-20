package com.retrosprite.app.ui.integration

import com.retrosprite.app.endpoint.EndpointStatus
import com.retrosprite.app.endpoint.RequestLogEntry
import com.retrosprite.app.ui.viewmodel.UiEndpointPhase
import com.retrosprite.app.ui.viewmodel.UiEndpointStatus
import com.retrosprite.app.ui.viewmodel.UiAnswerFeedback
import com.retrosprite.app.ui.viewmodel.UiOutputMode
import com.retrosprite.app.ui.viewmodel.UiRequestLogItem

/**
 * Mappers between Bill's endpoint-layer types and Lee's UI façade types.
 *
 * Kept in `ui.integration` (not in the endpoint package) so the endpoint module
 * stays free of any UI types. Conversely, the UI module never imports endpoint
 * types directly — it only sees `Ui*` shapes produced by these functions.
 */

/** Maps the endpoint sealed status into the UI snapshot the viewmodels render. */
internal fun EndpointStatus.toUi(
    fallbackPort: Int,
    baseUrl: String = LOOPBACK_BASE_URL_TEMPLATE.format(fallbackPort),
    lastHealthCheckMillis: Long? = null,
    lastHealthOk: Boolean? = null,
): UiEndpointStatus = when (this) {
    EndpointStatus.Stopped -> UiEndpointStatus(
        phase = UiEndpointPhase.Stopped,
        port = fallbackPort,
        baseUrl = baseUrl,
        message = null,
        lastHealthCheckMillis = lastHealthCheckMillis,
        lastHealthOk = lastHealthOk,
    )

    EndpointStatus.Starting -> UiEndpointStatus(
        phase = UiEndpointPhase.Starting,
        port = fallbackPort,
        baseUrl = baseUrl,
        message = "正在启动本地端点…",
        lastHealthCheckMillis = lastHealthCheckMillis,
        lastHealthOk = lastHealthOk,
    )

    is EndpointStatus.Running -> UiEndpointStatus(
        phase = UiEndpointPhase.Running,
        port = port,
        baseUrl = LOOPBACK_BASE_URL_TEMPLATE.format(port),
        message = null,
        lastHealthCheckMillis = lastHealthCheckMillis,
        lastHealthOk = lastHealthOk,
    )

    is EndpointStatus.Error -> UiEndpointStatus(
        phase = UiEndpointPhase.Error,
        port = fallbackPort,
        baseUrl = baseUrl,
        message = message,
        lastHealthCheckMillis = lastHealthCheckMillis,
        lastHealthOk = lastHealthOk,
    )
}

/**
 * Maps a single endpoint log entry into the UI list item.
 *
 * Request duration and LLM budget fields are local diagnostics only; prompts and
 * API keys are intentionally not carried through this mapper.
 */
internal fun RequestLogEntry.toUi(): UiRequestLogItem = UiRequestLogItem(
    id = id,
    timestampMillis = timestamp,
    label = label.ifEmpty { listOfNotNull(system.takeIf { it.isNotEmpty() }, game.takeIf { it.isNotEmpty() }).joinToString(" / ").ifEmpty { null } },
    imageBytes = imageBytes,
    paused = paused,
    outputMode = outputMode.toUiOutputMode(),
    rawOutputMode = outputMode,
    question = question,
    questionSource = questionSource,
    responsePreview = buildPreview(responseText, errorMessage),
    responseText = this.responseText,
    fullResponseJson = buildFullResponseJson(this),
    durationMillis = durationMillis,
    ok = errorMessage == null,
    isDebug = isDebugRequest,
    sourceIds = sourceIds,
    pipelineStage = pipelineStage,
    llmStatus = llmStatus,
    llmProvider = llmProvider,
    llmModel = llmModel,
    llmMaxTokens = llmMaxTokens,
    llmTimeoutMs = llmTimeoutMs,
    llmLatencyMs = llmLatencyMs,
    llmTokensIn = llmTokensIn,
    llmTokensOut = llmTokensOut,
    llmError = llmError,
    feedback = feedback.toUiAnswerFeedbackOrNull(),
    feedbackTimestampMillis = feedbackTimestamp,
)

/**
 * Decodes the RetroArch `output` query parameter into the UI's discrete enum.
 * The protocol allows multiple values separated by `|` (e.g. `text|sound`); any
 * combination of two or more we collapse to [UiOutputMode.Mixed] to keep the
 * tab counter UI tidy.
 */
internal fun String.toUiOutputMode(): UiOutputMode {
    val tokens = this
        .lowercase()
        .split('|', ',', ' ')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (tokens.size > 1) return UiOutputMode.Mixed
    return when (tokens.firstOrNull()) {
        "text" -> UiOutputMode.Text
        "image" -> UiOutputMode.Image
        "sound" -> UiOutputMode.Sound
        else -> UiOutputMode.Text
    }
}

private fun buildPreview(responseText: String, errorMessage: String?): String {
    val raw = errorMessage?.let { "[error] $it" } ?: responseText
    return if (raw.length > PREVIEW_MAX) raw.take(PREVIEW_MAX) + "…" else raw
}

private fun buildFullResponseJson(entry: RequestLogEntry): String {
    // Hand-rolled minimal JSON to avoid pulling kotlinx.serialization into the
    // mapping path. Escapes only the bare minimum (\\, ", and newlines) that
    // would otherwise break a textual preview dialog.
    val sourcesJson = entry.sourceIds.joinToString(",") { "\"${escapeJson(it)}\"" }
    val metadata = listOf(
        "\"output_mode\":\"${escapeJson(entry.outputMode)}\"",
        "\"debug\":${entry.isDebugRequest}",
        "\"question\":${entry.question.jsonStringOrNull()}",
        "\"question_source\":${entry.questionSource.jsonStringOrNull()}",
        "\"pipeline_stage\":\"${escapeJson(entry.pipelineStage)}\"",
        "\"llm_status\":\"${escapeJson(entry.llmStatus)}\"",
        "\"llm_provider\":${entry.llmProvider.jsonStringOrNull()}",
        "\"llm_model\":${entry.llmModel.jsonStringOrNull()}",
        "\"llm_max_tokens\":${entry.llmMaxTokens?.toString() ?: "null"}",
        "\"llm_timeout_ms\":${entry.llmTimeoutMs?.toString() ?: "null"}",
        "\"llm_latency_ms\":${entry.llmLatencyMs?.toString() ?: "null"}",
        "\"llm_tokens_in\":${entry.llmTokensIn}",
        "\"llm_tokens_out\":${entry.llmTokensOut}",
        "\"llm_error\":${entry.llmError.jsonStringOrNull()}",
        "\"feedback\":${entry.feedback.jsonStringOrNull()}",
        "\"feedback_timestamp\":${entry.feedbackTimestamp?.toString() ?: "null"}",
        "\"source_ids\":[$sourcesJson]",
    ).joinToString(",")
    return if (entry.errorMessage != null) {
        """{"error":"${escapeJson(entry.errorMessage)}",$metadata}"""
    } else {
        """{"text":"${escapeJson(entry.responseText)}",$metadata}"""
    }
}

private fun escapeJson(input: String): String =
    input.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

private fun String?.jsonStringOrNull(): String =
    this?.let { "\"${escapeJson(it)}\"" } ?: "null"

private fun String?.toUiAnswerFeedbackOrNull(): UiAnswerFeedback? {
    val raw = this?.trim().orEmpty()
    if (raw.isEmpty()) return null
    return UiAnswerFeedback.values().firstOrNull { it.id == raw }
}

internal const val LOOPBACK_BASE_URL_TEMPLATE: String = "http://localhost:%d"
private const val PREVIEW_MAX: Int = 80
