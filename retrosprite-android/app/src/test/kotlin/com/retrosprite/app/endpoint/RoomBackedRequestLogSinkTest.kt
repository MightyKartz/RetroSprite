package com.retrosprite.app.endpoint

import com.retrosprite.app.data.models.RequestLogDomain
import com.retrosprite.app.data.repository.RequestLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * In-memory fake of [RequestLogRepository] used to exercise
 * [RoomBackedRequestLogSink] without a Robolectric/Android Context.
 *
 * The fake mirrors the real DAO contract: appended rows acquire a monotonic id,
 * `observeRecent` is a hot stream that re-emits on every mutation, and `clear`
 * empties the store.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomBackedRequestLogSinkTest {

    private class FakeRepository : RequestLogRepository {
        private val state = MutableStateFlow<List<RequestLogDomain>>(emptyList())
        private var nextId: Long = 1L

        override suspend fun append(entry: RequestLogDomain) {
            val withId = entry.copy(id = nextId++)
            state.value = (listOf(withId) + state.value)
        }

        override fun observeRecent(limit: Int): Flow<List<RequestLogDomain>> =
            state.asStateFlow()

        override suspend fun clear() {
            state.value = emptyList()
        }

        override suspend fun count(): Int = state.value.size

        override suspend fun updateFeedback(
            requestKey: String,
            feedback: String,
            timestamp: Long,
        ): Int {
            var updated = 0
            state.value = state.value.map { row ->
                if (row.requestKey == requestKey) {
                    updated += 1
                    row.copy(feedback = feedback, feedbackTimestamp = timestamp)
                } else {
                    row
                }
            }
            return updated
        }
    }

    private fun TestScope.sinkScope(): CoroutineScope =
        CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))

    @Test
    fun `append goes through repository and is reflected in entries flow`() = runTest {
        val repo = FakeRepository()
        val sink = RoomBackedRequestLogSink(repository = repo, scope = sinkScope())

        val endpointEntry = RequestLogEntry(
            id = "request-1",
            label = "snes__smw",
            system = "snes",
            game = "smw",
            imageBytes = 1024,
            paused = true,
            outputMode = "text",
            question = "queued question",
            questionSource = "pending_hotkey",
            suggestedQuestions = listOf("气合之玉在哪里？"),
            responseText = "ok",
        )
        sink.append(endpointEntry)

        // Drain the launched coroutines (append + observeRecent collector).
        testScheduler.advanceUntilIdle()

        val entries = sink.entries.value
        assertEquals(1, entries.size)
        val first = entries.first()
        assertEquals("snes", first.system)
        assertEquals("smw", first.game)
        assertEquals("text", first.outputMode)
        assertTrue(first.paused)
        assertEquals(1024, first.imageBytes)
        assertEquals("request-1", first.id)
        assertEquals("queued question", first.question)
        assertEquals("pending_hotkey", first.questionSource)
        assertEquals(listOf("气合之玉在哪里？"), first.suggestedQuestions)
        assertEquals("ok", first.responseText)
    }

    @Test
    fun `null system in domain becomes empty string in endpoint entry`() = runTest {
        val repo = FakeRepository()
        val sink = RoomBackedRequestLogSink(repository = repo, scope = sinkScope())

        // Bypass append() to seed the repository with a null-system row.
        repo.append(
            RequestLogDomain(
                timestamp = 1L,
                label = "",
                system = null,
                game = null,
                imageSize = 0,
                paused = false,
                outputMode = "text",
                responseText = "",
                errorMessage = null,
            )
        )

        testScheduler.advanceUntilIdle()

        val mapped = sink.entries.value.first()
        assertEquals("", mapped.system)
        assertEquals("", mapped.game)
    }

    @Test
    fun `clear empties the underlying flow`() = runTest {
        val repo = FakeRepository()
        val sink = RoomBackedRequestLogSink(repository = repo, scope = sinkScope())

        sink.append(
            RequestLogEntry(
                label = "x", system = "x", game = "x",
                imageBytes = 0, paused = false, outputMode = "text",
                responseText = "x",
            )
        )
        testScheduler.advanceUntilIdle()
        assertEquals(1, sink.entries.value.size)

        repo.clear()
        testScheduler.advanceUntilIdle()
        assertEquals(0, sink.entries.value.size)
    }

    @Test
    fun `domain to endpoint mapping preserves all fields`() {
        val domain = RequestLogDomain(
            id = 99L,
            timestamp = 12_345L,
            label = "nes__contra",
            system = "nes",
            game = "contra",
            imageSize = 4096,
            paused = false,
            outputMode = "text",
            question = "why?",
            questionSource = "app",
            rawQuestion = "修医是谁",
            normalizedQuestion = "修伊是谁",
            questionNormalizationReason = "homophone",
            normalizedQuestionMatchedTerm = "修伊",
            normalizedQuestionMatchedEntityId = "character.chester",
            suggestedQuestions = listOf("修伊什么时候加入？"),
            responseText = "answer",
            errorMessage = "boom",
            feedback = "helpful",
            feedbackTimestamp = 77L,
        )

        val entry = domain.toEndpointEntry()

        assertEquals("row-99", entry.id)
        assertEquals(12_345L, entry.timestamp)
        assertEquals("nes__contra", entry.label)
        assertEquals("nes", entry.system)
        assertEquals("contra", entry.game)
        assertEquals(4096, entry.imageBytes)
        assertEquals(false, entry.paused)
        assertEquals("text", entry.outputMode)
        assertEquals("answer", entry.responseText)
        assertEquals("why?", entry.question)
        assertEquals("app", entry.questionSource)
        assertEquals("修医是谁", entry.rawQuestion)
        assertEquals("修伊是谁", entry.normalizedQuestion)
        assertEquals("homophone", entry.questionNormalizationReason)
        assertEquals("修伊", entry.normalizedQuestionMatchedTerm)
        assertEquals("character.chester", entry.normalizedQuestionMatchedEntityId)
        assertEquals(listOf("修伊什么时候加入？"), entry.suggestedQuestions)
        assertEquals("boom", entry.errorMessage)
        assertEquals("helpful", entry.feedback)
        assertEquals(77L, entry.feedbackTimestamp)
    }

    @Test
    fun `endpoint to domain mapping preserves request key and zeroes the numeric id`() {
        val entry = RequestLogEntry(
            id = "uuid-string",
            timestamp = 9L,
            label = "snes__smw",
            system = "snes",
            game = "smw",
            imageBytes = 8,
            paused = true,
            outputMode = "text",
            question = "why?",
            questionSource = "app",
            rawQuestion = "修医是谁",
            normalizedQuestion = "修伊是谁",
            questionNormalizationReason = "homophone",
            normalizedQuestionMatchedTerm = "修伊",
            normalizedQuestionMatchedEntityId = "character.chester",
            suggestedQuestions = listOf("修伊什么时候加入？"),
            responseText = "r",
            errorMessage = null,
            feedback = "incorrect",
            feedbackTimestamp = 88L,
        )

        val domain = entry.toDomainModel()

        assertEquals(0L, domain.id)
        assertEquals("uuid-string", domain.requestKey)
        assertEquals(9L, domain.timestamp)
        assertEquals("snes", domain.system)
        assertEquals("smw", domain.game)
        assertEquals("why?", domain.question)
        assertEquals("app", domain.questionSource)
        assertEquals("修医是谁", domain.rawQuestion)
        assertEquals("修伊是谁", domain.normalizedQuestion)
        assertEquals("homophone", domain.questionNormalizationReason)
        assertEquals("修伊", domain.normalizedQuestionMatchedTerm)
        assertEquals("character.chester", domain.normalizedQuestionMatchedEntityId)
        assertEquals(listOf("修伊什么时候加入？"), domain.suggestedQuestions)
        assertEquals("incorrect", domain.feedback)
        assertEquals(88L, domain.feedbackTimestamp)
    }
}
