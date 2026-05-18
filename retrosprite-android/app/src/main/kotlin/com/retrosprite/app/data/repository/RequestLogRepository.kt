package com.retrosprite.app.data.repository

import com.retrosprite.app.data.db.dao.RequestLogDao
import com.retrosprite.app.data.models.RequestLogDomain
import com.retrosprite.app.data.models.toDomain
import com.retrosprite.app.data.models.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Public-facing repository contract for request logs.
 *
 * Extends [RequestLogSink] so the same instance can be handed to Task 2's
 * `RequestLogger` (which only needs the sink methods) while exposing
 * additional repository-grade operations to the rest of the app.
 */
interface RequestLogRepository : RequestLogSink {

    /** Returns the number of logs currently persisted. */
    suspend fun count(): Int
}

/**
 * Default Room-backed implementation. Each [append] is followed by an
 * automatic trim to [RequestLogSink.MAX_RETAINED_ENTRIES] rows so the
 * table cannot grow unbounded.
 */
class DefaultRequestLogRepository(
    private val dao: RequestLogDao,
    private val maxRetainedEntries: Int = RequestLogSink.MAX_RETAINED_ENTRIES
) : RequestLogRepository {

    override suspend fun append(entry: RequestLogDomain) {
        dao.insert(entry.toEntity())
        dao.trimOldest(maxRetainedEntries)
    }

    override fun observeRecent(limit: Int): Flow<List<RequestLogDomain>> {
        return dao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun clear() {
        dao.clear()
    }

    override suspend fun count(): Int = dao.count()
}
