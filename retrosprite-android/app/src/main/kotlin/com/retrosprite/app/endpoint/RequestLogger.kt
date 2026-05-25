package com.retrosprite.app.endpoint

import android.util.Log
import com.retrosprite.app.endpoint.model.ResponseDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Single record describing one inbound RetroArch request + the response we sent back.
 *
 * Surfaced to the UI layer (Task #3 Diagnostics screen) via [RequestLogger.entries].
 */
data class RequestLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val label: String,
    val system: String,
    val game: String,
    val imageBytes: Int,
    val paused: Boolean,
    val outputMode: String,
    val question: String? = null,
    val questionSource: String? = null,
    val rawQuestion: String? = null,
    val normalizedQuestion: String? = null,
    val questionNormalizationReason: String? = null,
    val normalizedQuestionMatchedTerm: String? = null,
    val normalizedQuestionMatchedEntityId: String? = null,
    val answerShort: String? = null,
    val answerDetail: String? = null,
    val answerType: String? = null,
    val answerConfidence: String? = null,
    val spoilerLevelUsed: String? = null,
    val diagnosticSourceIds: List<String> = emptyList(),
    val nextActions: List<String> = emptyList(),
    val suggestedQuestions: List<String> = emptyList(),
    val responseText: String,
    val errorMessage: String? = null,
    val durationMillis: Long = 0L,
    val llmStatusOverride: String? = null,
    val llmProvider: String? = null,
    val llmModel: String? = null,
    val llmMaxTokens: Int? = null,
    val llmTimeoutMs: Long? = null,
    val llmLatencyMs: Long? = null,
    val llmTokensIn: Int = 0,
    val llmTokensOut: Int = 0,
    val llmError: String? = null,
    val feedback: String? = null,
    val feedbackTimestamp: Long? = null,
) {
    val isDebugRequest: Boolean
        get() = outputMode.startsWith(DEBUG_OUTPUT_PREFIX)

    val sourceIds: List<String>
        get() = diagnosticSourceIds.cleanSourceIds().ifEmpty { extractSourceIds(responseText) }

    val pipelineStage: String
        get() = when {
            errorMessage != null -> "error"
            sourceIds.isNotEmpty() -> "evidence"
            responseText.contains(GKP_DISABLED_MARKER) -> "gkp_disabled"
            responseText.contains(NO_EVIDENCE_MARKER) -> "no_evidence"
            isDebugRequest -> "debug"
            else -> "unknown"
        }

    val llmStatus: String
        get() = llmStatusOverride ?: when {
            errorMessage != null -> "skipped"
            sourceIds.size >= 2 -> "used"
            else -> "skipped"
        }
}

private const val DEBUG_OUTPUT_PREFIX = "debug:"
private const val SOURCE_PREFIX = "来源："
private const val LOCAL_SOURCE_NOTICE_LABEL = "本地知识"
private const val GKP_DISABLED_MARKER = "知识包已禁用"
private const val NO_EVIDENCE_MARKER = "没有足够证据"

private fun extractSourceIds(responseText: String): List<String> {
    val sourceLine = responseText
        .lineSequence()
        .firstOrNull { it.trim().startsWith(SOURCE_PREFIX) }
        ?: return emptyList()
    return sourceLine.substringAfter(SOURCE_PREFIX)
        .split(',', '，')
        .map { it.trim() }
        .filter { it.isNotEmpty() && it != LOCAL_SOURCE_NOTICE_LABEL }
        .distinct()
}

private fun List<String>.cleanSourceIds(): List<String> =
    map { it.trim() }
        .filter { it.isNotEmpty() && it != LOCAL_SOURCE_NOTICE_LABEL }
        .distinct()

/**
 * Persistence strategy for log entries.
 *
 * Phase 0 ships [DefaultInMemorySink]; Task #4 will provide a Room-backed implementation
 * that the [RequestLogger] consumes through this interface — no call sites change.
 */
interface RequestLogSink {
    fun append(entry: RequestLogEntry)
    val entries: StateFlow<List<RequestLogEntry>>
}

/**
 * Bounded in-memory ring buffer (capacity 200) used until the Room sink lands.
 *
 * Newest entries first. Thread-safe via [MutableStateFlow.update] CAS loop.
 */
class DefaultInMemorySink(private val maxSize: Int = 200) : RequestLogSink {

    private val state = MutableStateFlow<List<RequestLogEntry>>(emptyList())
    override val entries: StateFlow<List<RequestLogEntry>> = state.asStateFlow()

    override fun append(entry: RequestLogEntry) {
        state.update { current ->
            (listOf(entry) + current).take(maxSize)
        }
    }
}

/**
 * Records inbound RetroArch requests + Logcat tracing.
 *
 * The [sink] indirection lets Task #4 swap the in-memory store for a Room DAO without
 * touching the endpoint code. UI observes [entries].
 */
class RequestLogger(
    private val sink: RequestLogSink = DefaultInMemorySink(),
) {

    val entries: StateFlow<List<RequestLogEntry>> get() = sink.entries

    fun log(
        label: String,
        imageBase64: String,
        paused: Boolean,
        outputMode: String,
        responseText: String,
        errorMessage: String? = null,
        durationMillis: Long = 0L,
        diagnostics: ResponseDiagnostics = ResponseDiagnostics(),
        question: String? = diagnostics.question,
        questionSource: String? = diagnostics.questionSource,
        rawQuestion: String? = diagnostics.rawQuestion,
        normalizedQuestion: String? = diagnostics.normalizedQuestion,
        questionNormalizationReason: String? = diagnostics.questionNormalizationReason,
        normalizedQuestionMatchedTerm: String? = diagnostics.normalizedQuestionMatchedTerm,
        normalizedQuestionMatchedEntityId: String? = diagnostics.normalizedQuestionMatchedEntityId,
    ): RequestLogEntry {
        val parsed = LabelParser.parse(label)
        val cleanQuestion = question?.trim()?.takeIf { it.isNotEmpty() }
        val cleanQuestionSource = questionSource?.trim()?.takeIf { it.isNotEmpty() }
        val cleanRawQuestion = rawQuestion?.trim()?.takeIf { it.isNotEmpty() }
        val cleanNormalizedQuestion = normalizedQuestion?.trim()?.takeIf { it.isNotEmpty() }
        val entry = RequestLogEntry(
            label = label,
            system = parsed.system,
            game = parsed.game,
            imageBytes = decodedBase64Length(imageBase64),
            paused = paused,
            outputMode = outputMode,
            question = cleanQuestion,
            questionSource = cleanQuestionSource,
            rawQuestion = cleanRawQuestion,
            normalizedQuestion = cleanNormalizedQuestion,
            questionNormalizationReason = questionNormalizationReason,
            normalizedQuestionMatchedTerm = normalizedQuestionMatchedTerm,
            normalizedQuestionMatchedEntityId = normalizedQuestionMatchedEntityId,
            answerShort = diagnostics.answerShort,
            answerDetail = diagnostics.answerDetail,
            answerType = diagnostics.answerType,
            answerConfidence = diagnostics.answerConfidence,
            spoilerLevelUsed = diagnostics.spoilerLevelUsed,
            diagnosticSourceIds = diagnostics.sourceIds,
            nextActions = diagnostics.nextActions,
            suggestedQuestions = diagnostics.suggestedQuestions,
            responseText = responseText,
            errorMessage = errorMessage,
            durationMillis = durationMillis,
            llmStatusOverride = diagnostics.llmStatus,
            llmProvider = diagnostics.llmProvider,
            llmModel = diagnostics.llmModel,
            llmMaxTokens = diagnostics.llmMaxTokens,
            llmTimeoutMs = diagnostics.llmTimeoutMs,
            llmLatencyMs = diagnostics.llmLatencyMs,
            llmTokensIn = diagnostics.llmTokensIn,
            llmTokensOut = diagnostics.llmTokensOut,
            llmError = diagnostics.llmError,
        )
        sink.append(entry)
        Log.d(
            TAG,
            "RetroArch req: system=${entry.system} game=${entry.game} " +
                "imgBytes=${entry.imageBytes} paused=${entry.paused} " +
                "out=${entry.outputMode} llm=${entry.llmStatus} " +
                "duration=${entry.durationMillis}ms err=${entry.errorMessage ?: "-"}",
        )
        return entry
    }

    companion object {
        private const val TAG = "RetroSprite/Endpoint"

        /**
         * Computes the byte length a Base64 string would decode to, **without** allocating
         * the decoded byte array. Counts trailing `=` padding precisely.
         *
         * Returns `0` for blank input or strings whose length is not a multiple of 4 (which
         * would be invalid Base64 anyway).
         */
        fun decodedBase64Length(base64: String): Int {
            if (base64.isEmpty()) return 0
            val len = base64.length
            if (len % 4 != 0) {
                // Tolerate non-padded variants by rounding down.
                val padding = if (base64.endsWith("==")) 2 else if (base64.endsWith("=")) 1 else 0
                return ((len * 3) / 4) - padding
            }
            val padding = when {
                base64.endsWith("==") -> 2
                base64.endsWith("=") -> 1
                else -> 0
            }
            return (len * 3 / 4) - padding
        }

    }
}
