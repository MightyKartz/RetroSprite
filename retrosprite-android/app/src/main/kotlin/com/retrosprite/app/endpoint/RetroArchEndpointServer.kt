package com.retrosprite.app.endpoint

import android.util.Log
import com.retrosprite.app.endpoint.model.DebugHotkeyVoiceOverlayResponse
import com.retrosprite.app.endpoint.model.DebugLatestRequestResponse
import com.retrosprite.app.endpoint.model.HealthResponse
import com.retrosprite.app.endpoint.model.RetroArchRequest
import com.retrosprite.app.endpoint.model.RetroArchResponse
import com.retrosprite.app.endpoint.model.ResponseDiagnostics
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local Ktor (CIO) server that implements the RetroArch AI Service protocol.
 *
 * Design constraints:
 *  - Binds **only** to `127.0.0.1` so the device cannot accept LAN traffic.
 *  - Returns HTTP **200** for every protocol-level error (per RetroArch quirks: the frontend
 *    treats 4xx/5xx as transport errors and silently drops the response, so we surface
 *    issues via `{ "error": "..." }` instead).
 *  - Response generation is delegated to [responseGenerator] so Task #5's `QueryPipeline`
 *    can replace [PlaceholderResponseGenerator] without touching this class.
 *
 * Lifecycle methods are idempotent and safe to call from any thread; [start] blocks until
 * the engine has bound the port.
 */
class RetroArchEndpointServer(
    private val port: Int = DEFAULT_PORT,
    private val host: String = DEFAULT_HOST,
    private val responseGenerator: ResponseGenerator = PlaceholderResponseGenerator(),
    private val requestLogger: RequestLogger = RequestLogger(),
    private val hotkeyListener: RetroArchHotkeyListener = NoopRetroArchHotkeyListener,
    private val hotkeyVoiceOverlayDebugProvider: () -> DebugHotkeyVoiceOverlayResponse = {
        DebugHotkeyVoiceOverlayResponse.idle()
    },
) {

    private val running = AtomicBoolean(false)

    @Volatile
    private var engine: ApplicationEngine? = null

    val isRunning: Boolean get() = running.get()
    val boundPort: Int get() = port
    val logger: RequestLogger get() = requestLogger

    /** Starts the embedded engine. Throws if the port is already in use. */
    fun start() {
        if (!running.compareAndSet(false, true)) {
            Log.w(TAG, "start() called but server already running on $host:$port")
            return
        }
        try {
            engine = embeddedServer(CIO, host = host, port = port) {
                retroArchModule(
                    responseGenerator = responseGenerator,
                    requestLogger = requestLogger,
                    hotkeyListener = hotkeyListener,
                    hotkeyVoiceOverlayDebugProvider = hotkeyVoiceOverlayDebugProvider,
                )
            }.also { it.start(wait = false) }
            Log.i(TAG, "RetroArch endpoint listening on $host:$port")
        } catch (t: Throwable) {
            running.set(false)
            engine = null
            Log.e(TAG, "Failed to bind RetroArch endpoint on $host:$port", t)
            throw t
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        try {
            engine?.stop(gracePeriodMillis = 500, timeoutMillis = 1_500)
            Log.i(TAG, "RetroArch endpoint stopped")
        } catch (t: Throwable) {
            Log.w(TAG, "stop() encountered an error (ignored)", t)
        } finally {
            engine = null
        }
    }

    companion object {
        const val DEFAULT_HOST: String = "127.0.0.1"
        const val DEFAULT_PORT: Int = 4_404
        private const val TAG = "RetroSprite/Endpoint"
    }
}

/**
 * Lenient JSON parser used by both production and tests:
 *  - `ignoreUnknownKeys`  → tolerate forward-compatible RetroArch additions.
 *  - `coerceInputValues`  → fall back to defaults for the wrong shape (e.g. `null` for an Int).
 *  - `encodeDefaults`     → omit `null` fields so the response stays minimal on the wire.
 */
@OptIn(ExperimentalSerializationApi::class)
internal val retroArchJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = false
    explicitNulls = false
}

/**
 * Installs the RetroArch routes on an [Application]. Exposed as an extension so unit tests
 * can mount the same module inside `testApplication { application { retroArchModule(...) } }`.
 */
fun Application.retroArchModule(
    responseGenerator: ResponseGenerator,
    requestLogger: RequestLogger,
    hotkeyListener: RetroArchHotkeyListener = NoopRetroArchHotkeyListener,
    hotkeyVoiceOverlayDebugProvider: () -> DebugHotkeyVoiceOverlayResponse = {
        DebugHotkeyVoiceOverlayResponse.idle()
    },
) {
    install(ContentNegotiation) {
        json(retroArchJson)
    }
    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok", version = "0.1.0"))
        }

        get("/debug/latest-request") {
            val response = requestLogger.entries.value.firstOrNull()
                ?.toDebugLatestRequestResponse()
                ?: DebugLatestRequestResponse.empty()
            call.respond(response)
        }

        get("/debug/hotkey-voice-overlay") {
            call.respond(hotkeyVoiceOverlayDebugProvider())
        }

        post("/debug/ask") {
            val outputMode = call.request.queryParameters["output"]?.takeIf { it.isNotBlank() }
                ?: "text"
            val debugOutputMode = "debug:$outputMode"

            val request: RetroArchRequest = try {
                retroArchJson.decodeFromString(
                    RetroArchRequest.serializer(),
                    call.receiveText(),
                )
            } catch (t: Throwable) {
                requestLogger.log(
                    label = "",
                    imageBase64 = "",
                    paused = false,
                    outputMode = debugOutputMode,
                    responseText = "",
                    errorMessage = "malformed_debug_request: ${t.message}",
                )
                call.respondJson(RetroArchResponse.error("Malformed debug request body"))
                return@post
            }

            if (request.question.isBlank()) {
                requestLogger.log(
                    label = request.label,
                    imageBase64 = request.image,
                    paused = request.state.isPaused,
                    outputMode = debugOutputMode,
                    responseText = "",
                    errorMessage = "missing_debug_question",
                    questionSource = QUESTION_SOURCE_DEBUG,
                )
                call.respondJson(RetroArchResponse.error("Missing debug question"))
                return@post
            }

            val startedAt = System.currentTimeMillis()
            val response: RetroArchResponse = try {
                responseGenerator.generate(request, outputMode)
            } catch (t: Throwable) {
                Log.e("RetroSprite/Endpoint", "Debug ResponseGenerator failed", t)
                requestLogger.log(
                    label = request.label,
                    imageBase64 = request.image,
                    paused = request.state.isPaused,
                    outputMode = debugOutputMode,
                    responseText = "",
                    errorMessage = "debug_generator_failed: ${t.message}",
                    durationMillis = System.currentTimeMillis() - startedAt,
                    question = request.question,
                    questionSource = QUESTION_SOURCE_DEBUG,
                )
                call.respondJson(RetroArchResponse.error("Internal debug generator failure"))
                return@post
            }
            val durationMillis = System.currentTimeMillis() - startedAt

            val normalizedQuestion = response.diagnostics.question
                ?: response.diagnostics.normalizedQuestion
            val inferredRawQuestion = request.question.trim().takeIf {
                it.isNotBlank() && normalizedQuestion != null && it != normalizedQuestion
            }
            requestLogger.log(
                label = request.label,
                imageBase64 = request.image,
                paused = request.state.isPaused,
                outputMode = debugOutputMode,
                responseText = response.text.orEmpty(),
                errorMessage = response.error,
                durationMillis = durationMillis,
                diagnostics = response.diagnostics.withInferredNormalization(
                    rawQuestion = request.question,
                ),
                question = response.diagnostics.question
                    ?: response.diagnostics.normalizedQuestion
                    ?: request.question,
                questionSource = QUESTION_SOURCE_DEBUG,
                rawQuestion = response.diagnostics.rawQuestion ?: inferredRawQuestion,
                normalizedQuestion = response.diagnostics.normalizedQuestion
                    ?: normalizedQuestion.takeIf { inferredRawQuestion != null },
                questionNormalizationReason = response.diagnostics.questionNormalizationReason
                    ?: "normalized".takeIf { inferredRawQuestion != null },
                normalizedQuestionMatchedTerm = response.diagnostics.normalizedQuestionMatchedTerm,
                normalizedQuestionMatchedEntityId = response.diagnostics.normalizedQuestionMatchedEntityId,
            )
            call.respondJson(response)
        }

        post("/") {
            // 1. Query parameters — `output` is required by spec; default to "text" if absent.
            val outputMode = call.request.queryParameters["output"]?.takeIf { it.isNotBlank() }
                ?: "text"

            // 2. Body parsing wrapped in try/catch so RetroArch never sees a 4xx/5xx.
            // RetroArch currently sends a JSON payload with
            // `Content-Type: application/x-www-form-urlencoded`, so parse the raw body
            // instead of relying on Ktor's content negotiation.
            val request: RetroArchRequest = try {
                retroArchJson.decodeFromString(
                    RetroArchRequest.serializer(),
                    call.receiveText(),
                )
            } catch (t: Throwable) {
                requestLogger.log(
                    label = "",
                    imageBase64 = "",
                    paused = false,
                    outputMode = outputMode,
                    responseText = "",
                    errorMessage = "malformed_request: ${t.message}",
                )
                call.respondJson(RetroArchResponse.error("Malformed request body"))
                return@post
            }

            hotkeyListener.notifySafely(request, outputMode)

            // 3. Delegate to the generator; treat any failure as a protocol-level error.
            val startedAt = System.currentTimeMillis()
            val response: RetroArchResponse = try {
                responseGenerator.generate(request, outputMode)
            } catch (t: Throwable) {
                Log.e("RetroSprite/Endpoint", "ResponseGenerator failed", t)
                requestLogger.log(
                    label = request.label,
                    imageBase64 = request.image,
                    paused = request.state.isPaused,
                    outputMode = outputMode,
                    responseText = "",
                    errorMessage = "generator_failed: ${t.message}",
                    durationMillis = System.currentTimeMillis() - startedAt,
                    question = request.question,
                    questionSource = request.question.takeIf { it.isNotBlank() }?.let { QUESTION_SOURCE_RETROARCH },
                )
                call.respondJson(RetroArchResponse.error("Internal generator failure"))
                return@post
            }
            val durationMillis = System.currentTimeMillis() - startedAt

            if (request.isSilentHotkeyWakeResponse(response)) {
                call.respondJson(response)
                return@post
            }

            // 4. Record success path.
            val normalizedQuestion = response.diagnostics.question
                ?: response.diagnostics.normalizedQuestion
            val inferredRawQuestion = request.question.trim().takeIf {
                it.isNotBlank() && normalizedQuestion != null && it != normalizedQuestion
            }
            requestLogger.log(
                label = request.label,
                imageBase64 = request.image,
                paused = request.state.isPaused,
                outputMode = outputMode,
                responseText = response.text.orEmpty(),
                errorMessage = response.error,
                durationMillis = durationMillis,
                diagnostics = response.diagnostics.withInferredNormalization(
                    rawQuestion = request.question,
                ),
                question = response.diagnostics.question ?: request.question,
                questionSource = response.diagnostics.questionSource
                    ?: request.question.takeIf { it.isNotBlank() }?.let { QUESTION_SOURCE_RETROARCH },
                rawQuestion = response.diagnostics.rawQuestion ?: inferredRawQuestion,
                normalizedQuestion = response.diagnostics.normalizedQuestion
                    ?: normalizedQuestion.takeIf { inferredRawQuestion != null },
                questionNormalizationReason = response.diagnostics.questionNormalizationReason
                    ?: "normalized".takeIf { inferredRawQuestion != null },
                normalizedQuestionMatchedTerm = response.diagnostics.normalizedQuestionMatchedTerm,
                normalizedQuestionMatchedEntityId = response.diagnostics.normalizedQuestionMatchedEntityId,
            )
            call.respondJson(response)
        }
    }
}

private fun RequestLogEntry.toDebugLatestRequestResponse(): DebugLatestRequestResponse =
    DebugLatestRequestResponse(
        has_entry = true,
        timestamp = timestamp,
        label = label.ifBlank { null },
        system = system.ifBlank { null },
        game = game.ifBlank { null },
        image_bytes = imageBytes,
        paused = paused,
        output_mode = outputMode,
        is_debug = isDebugRequest,
        ok = errorMessage == null,
        question = question,
        question_source = questionSource,
        raw_question = rawQuestion,
        normalized_question = normalizedQuestion,
        question_normalization_reason = questionNormalizationReason,
        normalized_question_matched_term = normalizedQuestionMatchedTerm,
        normalized_question_matched_entity_id = normalizedQuestionMatchedEntityId,
        answer_short = answerShort,
        answer_detail = answerDetail,
        answer_type = answerType,
        answer_confidence = answerConfidence,
        spoiler_level_used = spoilerLevelUsed,
        next_actions = nextActions,
        suggested_questions = suggestedQuestions,
        pipeline_stage = pipelineStage,
        llm_status = llmStatus,
        source_ids = sourceIds,
        response_preview = responseText.take(PREVIEW_MAX).ifBlank { null },
        error_message = errorMessage,
        duration_ms = durationMillis,
        llm_provider = llmProvider,
        llm_model = llmModel,
        llm_max_tokens = llmMaxTokens,
        llm_timeout_ms = llmTimeoutMs,
        llm_latency_ms = llmLatencyMs,
        llm_tokens_in = llmTokensIn,
        llm_tokens_out = llmTokensOut,
        llm_error = llmError,
    )

private fun ResponseDiagnostics.withInferredNormalization(rawQuestion: String): ResponseDiagnostics {
    val cleanRaw = rawQuestion.trim()
    val cleanQuestion = question?.trim().orEmpty()
    if (cleanRaw.isBlank() || cleanQuestion.isBlank() || cleanRaw == cleanQuestion) {
        return this
    }
    return copy(
        rawQuestion = this.rawQuestion ?: cleanRaw,
        normalizedQuestion = this.normalizedQuestion ?: cleanQuestion,
        questionNormalizationReason = questionNormalizationReason ?: "normalized",
    )
}

/** Helper that serializes via the lenient parser regardless of negotiated content type. */
private suspend fun io.ktor.server.application.ApplicationCall.respondJson(
    response: RetroArchResponse,
) {
    val body = retroArchJson.encodeToString(RetroArchResponse.serializer(), response)
    respondText(text = body, contentType = ContentType.Application.Json, status = HttpStatusCode.OK)
}

private fun RetroArchRequest.isSilentHotkeyWakeResponse(response: RetroArchResponse): Boolean =
    question.isBlank() &&
        response.text.orEmpty().isBlank() &&
        response.image == null &&
        response.sound == null &&
        response.text_position == null &&
        response.press == null &&
        response.auto == null &&
        response.error == null &&
        response.diagnostics == com.retrosprite.app.endpoint.model.ResponseDiagnostics()

private fun RetroArchHotkeyListener.notifySafely(
    request: RetroArchRequest,
    outputMode: String,
) {
    runCatching {
        onHotkey(request.toHotkeyEvent(outputMode))
    }.onFailure { error ->
        Log.w("RetroSprite/Endpoint", "Hotkey listener failed; continuing response", error)
    }
}

private const val PREVIEW_MAX: Int = 200
private const val QUESTION_SOURCE_DEBUG: String = "debug"
private const val QUESTION_SOURCE_RETROARCH: String = "retroarch"
