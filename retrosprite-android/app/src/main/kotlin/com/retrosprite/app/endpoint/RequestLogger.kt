package com.retrosprite.app.endpoint

import android.util.Log
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
    val responseText: String,
    val errorMessage: String? = null,
)

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
    ): RequestLogEntry {
        val parsed = LabelParser.parse(label)
        val entry = RequestLogEntry(
            label = label,
            system = parsed.system,
            game = parsed.game,
            imageBytes = decodedBase64Length(imageBase64),
            paused = paused,
            outputMode = outputMode,
            responseText = responseText,
            errorMessage = errorMessage,
        )
        sink.append(entry)
        Log.d(
            TAG,
            "RetroArch req: system=${entry.system} game=${entry.game} " +
                "imgBytes=${entry.imageBytes} paused=${entry.paused} " +
                "out=${entry.outputMode} err=${entry.errorMessage ?: "-"}",
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
