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
 * Round-trip caveat: the endpoint's `RequestLogEntry.id` is a UUID string, but
 * Room uses an auto-increment `Long`. We do **not** preserve the UUID on the
 * write path (it would require a schema change in Task 4); instead, on read we
 * synthesise a stable string id from the row's primary key. UI keys remain
 * stable per-row, which is all Compose needs for `LazyColumn` reuse.
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
    // Stable per-row id; Room PK doubles as the UI key.
    id = "row-$id",
    timestamp = timestamp,
    label = label,
    system = system.orEmpty(),
    game = game.orEmpty(),
    imageBytes = imageSize,
    paused = paused,
    outputMode = outputMode,
    responseText = responseText,
    errorMessage = errorMessage,
)

/**
 * Maps an endpoint entry into the data-layer domain row. The endpoint's UUID
 * string is intentionally dropped — Room generates the canonical identity.
 */
internal fun RequestLogEntry.toDomainModel(): RequestLogDomain = RequestLogDomain(
    id = 0L, // auto-increment
    timestamp = timestamp,
    label = label,
    system = system.ifEmpty { null },
    game = game.ifEmpty { null },
    imageSize = imageBytes,
    paused = paused,
    outputMode = outputMode,
    responseText = responseText,
    errorMessage = errorMessage,
)
