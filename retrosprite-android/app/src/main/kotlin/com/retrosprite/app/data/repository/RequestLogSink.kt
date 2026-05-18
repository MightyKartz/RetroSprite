package com.retrosprite.app.data.repository

import com.retrosprite.app.data.models.RequestLogDomain
import kotlinx.coroutines.flow.Flow

/**
 * Persistence sink for RetroSprite request logs.
 *
 * Task 2 (`RequestLogger`) writes through this interface instead of
 * holding an in-memory list, so logs survive process death and Task 3
 * can observe them in the UI without coupling to Room.
 */
interface RequestLogSink {

    /** Append a single log entry. Implementations bound storage to a recent window. */
    suspend fun append(entry: RequestLogDomain)

    /** Observe the most recent [limit] entries, ordered newest first. */
    fun observeRecent(limit: Int = DEFAULT_OBSERVE_LIMIT): Flow<List<RequestLogDomain>>

    /** Remove every persisted log entry. */
    suspend fun clear()

    companion object {
        const val DEFAULT_OBSERVE_LIMIT: Int = 50

        /** Maximum number of rows kept on disk; older rows are pruned automatically. */
        const val MAX_RETAINED_ENTRIES: Int = 200
    }
}
