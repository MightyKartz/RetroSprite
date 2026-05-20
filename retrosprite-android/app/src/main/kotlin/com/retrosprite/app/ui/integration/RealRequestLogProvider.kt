package com.retrosprite.app.ui.integration

import com.retrosprite.app.data.repository.RequestLogRepository
import com.retrosprite.app.endpoint.EndpointController
import com.retrosprite.app.endpoint.RequestLogger
import com.retrosprite.app.ui.viewmodel.RequestLogProvider
import com.retrosprite.app.ui.viewmodel.UiAnswerFeedback
import com.retrosprite.app.ui.viewmodel.UiRequestLogItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Real [RequestLogProvider] that joins three layers:
 *
 *  - **read path**:   resolves the current [RequestLogger] lazily via
 *    [loggerProvider] so the flow surfaces the Room-backed sink even if it was
 *    swapped in *after* this provider was constructed (which is exactly the
 *    boot sequence in `RetroSpriteApp.onCreate`).
 *  - **clear path**:  delegates to [RequestLogRepository.clear] so both Room
 *    AND the in-memory mirror inside the active
 *    [com.retrosprite.app.endpoint.RoomBackedRequestLogSink] are wiped (the
 *    sink re-reads from Room and updates its cached `StateFlow`).
 *  - **connection-test path**: POSTs a synthetic AI-Service body to the
 *    loopback endpoint so the user can verify that the entire pipeline (HTTP
 *    route → [com.retrosprite.app.endpoint.QueryPipelineResponseGenerator] →
 *    domain pipeline → Room sink → Compose log list) is wired correctly.
 *
 * The `portProvider` indirection means port changes (Phase 1 dynamic-restart
 * feature) do not need a new provider instance.
 */
class RealRequestLogProvider(
    private val repository: RequestLogRepository,
    private val portProvider: () -> Int,
    private val loggerProvider: () -> RequestLogger = { EndpointController.requestLogger },
    private val httpClient: OkHttpClient = defaultClient(),
) : RequestLogProvider {

    override val log: Flow<List<UiRequestLogItem>> =
        flowOf(Unit).flatMapLatest {
            // Resolve the current logger at collection time. This matters at
            // app boot: ServiceLocator constructs this provider BEFORE
            // EndpointController.setRequestLogSink replaces the default
            // in-memory logger with the Room-backed one.
            loggerProvider().entries.map { entries -> entries.map { it.toUi() } }
        }

    override suspend fun clear() {
        repository.clear()
    }

    override suspend fun submitFeedback(requestId: String, feedback: UiAnswerFeedback) {
        val cleanId = requestId.trim()
        if (cleanId.isBlank()) return
        withContext(Dispatchers.IO) {
            repeat(FEEDBACK_UPDATE_ATTEMPTS) { attempt ->
                val updated = repository.updateFeedback(
                    requestKey = cleanId,
                    feedback = feedback.id,
                    timestamp = System.currentTimeMillis(),
                )
                if (updated > 0) return@withContext
                delay(FEEDBACK_RETRY_DELAY_MS * (attempt + 1))
            }
        }
    }

    override suspend fun sendConnectionTest() {
        withContext(Dispatchers.IO) {
            val url = LOOPBACK_BASE_URL_TEMPLATE.format(portProvider()) + "/?output=text"
            val request = Request.Builder()
                .url(url)
                .post(SYNTHETIC_BODY.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            try {
                httpClient.newCall(request).execute().use { /* discard body */ }
            } catch (_: Throwable) {
                // Failure is surfaced through the entries stream when the route
                // logs the malformed/dropped request; we deliberately don't
                // throw to avoid crashing the UI on transient socket errors.
            }
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        // Minimal valid AI-Service body — empty image, label, and zeroed state.
        private const val SYNTHETIC_BODY: String =
            """{"image":"","label":"diagnostic__retrosprite_self_test","state":{"paused":1}}"""

        private const val FEEDBACK_UPDATE_ATTEMPTS: Int = 5
        private const val FEEDBACK_RETRY_DELAY_MS: Long = 80L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
