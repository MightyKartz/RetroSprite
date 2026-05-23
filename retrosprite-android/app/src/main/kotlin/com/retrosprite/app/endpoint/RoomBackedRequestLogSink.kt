package com.retrosprite.app.endpoint

import com.retrosprite.app.data.models.RequestLogDomain
import com.retrosprite.app.data.repository.RequestLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Adapter that lets the endpoint layer's synchronous [RequestLogSink] write into
 * the Room-backed [RequestLogRepository] (which exposes a `suspend` API).
 *
 * Why an adapter instead of refactoring [RequestLogger]?
 *  - Keeps Bill's endpoint-internal abstraction untouched (single-responsibility
 *    per task ownership): `RequestLogger` still believes it's writing to a
 *    bounded in-memory list.
 *  - Two different domain models exist on either side of the boundary
 *    ([RequestLogEntry] in endpoint, [RequestLogDomain] in data); the mapping
 *    is centralised in this single class.
 *  - The endpoint's `entries: StateFlow<List<RequestLogEntry>>` is preserved so
 *    [RequestLogger]/[EndpointController] consumers do not need to know that
 *    persistence happens out-of-band.
 *
 * Concurrency model:
 *  - [append] returns immediately; the actual Room insert is dispatched onto the
 *    provided [scope] (typically `applicationScope` on `Dispatchers.IO`).
 *  - The Room observation is a single long-lived collector that mirrors the
 *    most recent `observeLimit` entries into [_entries].
 *
 * Round-trip note: Room keeps its auto-increment `Long` primary key, while
 * `request_key` preserves the endpoint's UUID string. Feedback uses that
 * stable request key so Home can update the matching row after an app-side
 * question returns.
 */
class RoomBackedRequestLogSink(
    private val repository: RequestLogRepository,
    private val scope: CoroutineScope,
    private val observeLimit: Int = DEFAULT_OBSERVE_LIMIT,
) : RequestLogSink {

    private val _entries = MutableStateFlow<List<RequestLogEntry>>(emptyList())
    override val entries: StateFlow<List<RequestLogEntry>> = _entries.asStateFlow()

    init {
        scope.launch {
            repository.observeRecent(observeLimit).collect { domainList ->
                _entries.value = domainList.map { it.toEndpointEntry() }
            }
        }
    }

    override fun append(entry: RequestLogEntry) {
        scope.launch {
            repository.append(entry.toDomainModel())
        }
    }

    companion object {
        const val DEFAULT_OBSERVE_LIMIT: Int = 200
    }
}

/** Maps the data-layer domain row into the endpoint-layer entry consumed by UI. */
internal fun RequestLogDomain.toEndpointEntry(): RequestLogEntry = RequestLogEntry(
    id = requestKey.ifBlank { "row-$id" },
    timestamp = timestamp,
    label = label,
    system = system.orEmpty(),
    game = game.orEmpty(),
    imageBytes = imageSize,
    paused = paused,
    outputMode = outputMode,
    question = question,
    questionSource = questionSource,
    answerShort = answerShort,
    answerDetail = answerDetail,
    answerType = answerType,
    answerConfidence = answerConfidence,
    spoilerLevelUsed = spoilerLevelUsed,
    nextActions = nextActions,
    responseText = responseText,
    errorMessage = errorMessage,
    durationMillis = durationMillis,
    llmStatusOverride = llmStatus,
    llmProvider = llmProvider,
    llmModel = llmModel,
    llmMaxTokens = llmMaxTokens,
    llmTimeoutMs = llmTimeoutMs,
    llmLatencyMs = llmLatencyMs,
    llmTokensIn = llmTokensIn,
    llmTokensOut = llmTokensOut,
    llmError = llmError,
    feedback = feedback,
    feedbackTimestamp = feedbackTimestamp,
)

/**
 * Maps an endpoint entry into the data-layer domain row. Room still generates
 * the numeric identity; `requestKey` preserves the endpoint UUID for UI actions.
 */
internal fun RequestLogEntry.toDomainModel(): RequestLogDomain = RequestLogDomain(
    id = 0L, // auto-increment
    requestKey = id,
    timestamp = timestamp,
    label = label,
    system = system.ifEmpty { null },
    game = game.ifEmpty { null },
    imageSize = imageBytes,
    paused = paused,
    outputMode = outputMode,
    question = question,
    questionSource = questionSource,
    answerShort = answerShort,
    answerDetail = answerDetail,
    answerType = answerType,
    answerConfidence = answerConfidence,
    spoilerLevelUsed = spoilerLevelUsed,
    nextActions = nextActions,
    responseText = responseText,
    errorMessage = errorMessage,
    durationMillis = durationMillis,
    llmStatus = llmStatus,
    llmProvider = llmProvider,
    llmModel = llmModel,
    llmMaxTokens = llmMaxTokens,
    llmTimeoutMs = llmTimeoutMs,
    llmLatencyMs = llmLatencyMs,
    llmTokensIn = llmTokensIn,
    llmTokensOut = llmTokensOut,
    llmError = llmError,
    feedback = feedback,
    feedbackTimestamp = feedbackTimestamp,
)
