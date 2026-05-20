package com.retrosprite.app.ui.integration

import com.retrosprite.app.endpoint.EndpointController
import com.retrosprite.app.endpoint.RequestLogger
import com.retrosprite.app.endpoint.ResponseGenerator
import com.retrosprite.app.endpoint.model.RetroArchRequest
import com.retrosprite.app.endpoint.model.RetroArchState
import com.retrosprite.app.ui.viewmodel.PlayerQuestionProvider
import com.retrosprite.app.ui.viewmodel.UiQuestionResult
import com.retrosprite.app.ui.viewmodel.UiSpoilerLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * App-side text question entry for the Phase 1 Q&A loop.
 *
 * This intentionally bypasses HTTP while preserving the same endpoint adapter,
 * domain pipeline, and request log semantics used by RetroArch. The result
 * appears in Diagnostics as `output_mode=app:text`, which makes it distinct
 * from both real RetroArch calls and loopback-only `/debug/ask` probes.
 */
class RealPlayerQuestionProvider(
    private val responseGenerator: ResponseGenerator,
    private val loggerProvider: () -> RequestLogger = { EndpointController.requestLogger },
) : PlayerQuestionProvider {

    override suspend fun ask(label: String, question: String): UiQuestionResult =
        ask(label = label, question = question, spoilerLevelOverride = null)

    override suspend fun ask(
        label: String,
        question: String,
        spoilerLevelOverride: UiSpoilerLevel?,
    ): UiQuestionResult =
        withContext(Dispatchers.Default) {
            val cleanLabel = label.trim().ifBlank { DEFAULT_LABEL }
            val cleanQuestion = question.trim()

            if (cleanQuestion.isBlank()) {
                return@withContext UiQuestionResult(
                    label = cleanLabel,
                    question = cleanQuestion,
                    answer = "",
                    ok = false,
                    timestampMillis = System.currentTimeMillis(),
                    errorMessage = "missing_question",
                    pipelineStage = "error",
                )
            }

            val request = RetroArchRequest(
                image = "",
                label = cleanLabel,
                question = cleanQuestion,
                spoilerLevel = spoilerLevelOverride?.id.orEmpty(),
                state = RetroArchState(paused = 1),
            )
            val logger = loggerProvider()

            val startedAt = System.currentTimeMillis()
            val response = runCatching {
                responseGenerator.generate(request, "text")
            }.getOrElse { error ->
                val entry = logger.log(
                    label = cleanLabel,
                    imageBase64 = "",
                    paused = true,
                    outputMode = OUTPUT_MODE,
                    responseText = "",
                    errorMessage = "app_generator_failed: ${error.message}",
                    durationMillis = System.currentTimeMillis() - startedAt,
                    question = cleanQuestion,
                    questionSource = QUESTION_SOURCE_APP,
                )
                return@withContext entry.toQuestionResult(cleanQuestion)
            }
            val durationMillis = System.currentTimeMillis() - startedAt

            val entry = logger.log(
                label = cleanLabel,
                imageBase64 = "",
                paused = true,
                outputMode = OUTPUT_MODE,
                responseText = response.text.orEmpty(),
                errorMessage = response.error,
                durationMillis = durationMillis,
                diagnostics = response.diagnostics,
                question = cleanQuestion,
                questionSource = QUESTION_SOURCE_APP,
            )
            entry.toQuestionResult(cleanQuestion)
        }

    private fun com.retrosprite.app.endpoint.RequestLogEntry.toQuestionResult(
        question: String,
    ): UiQuestionResult = UiQuestionResult(
        requestLogId = id,
        label = label,
        question = question,
        answer = responseText,
        ok = errorMessage == null,
        timestampMillis = timestamp,
        sourceIds = sourceIds,
        pipelineStage = pipelineStage,
        llmStatus = llmStatus,
        durationMillis = durationMillis,
        llmProvider = llmProvider,
        llmModel = llmModel,
        llmMaxTokens = llmMaxTokens,
        llmTimeoutMs = llmTimeoutMs,
        llmLatencyMs = llmLatencyMs,
        llmTokensIn = llmTokensIn,
        llmTokensOut = llmTokensOut,
        llmError = llmError,
        errorMessage = errorMessage,
    )

    companion object {
        const val DEFAULT_LABEL: String = "2048__"
        const val OUTPUT_MODE: String = "app:text"
        const val QUESTION_SOURCE_APP: String = "app"
    }
}
