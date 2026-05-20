package com.retrosprite.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.retrosprite.app.data.db.RetroSpriteDatabase
import com.retrosprite.app.data.models.RequestLogDomain
import com.retrosprite.app.data.repository.DefaultRequestLogRepository
import com.retrosprite.app.data.repository.RequestLogSink
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RequestLogDaoTest {

    private lateinit var db: RetroSpriteDatabase
    private lateinit var repository: DefaultRequestLogRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = RetroSpriteDatabase.buildInMemory(context)
        repository = DefaultRequestLogRepository(db.requestLogDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndObserve_returnsNewestFirst() = runTest {
        repository.append(sample(timestamp = 100L, label = "old"))
        repository.append(sample(timestamp = 200L, label = "newer"))
        repository.append(sample(timestamp = 300L, label = "newest"))

        repository.observeRecent(limit = 10).test {
            val rows = awaitItem()
            assertEquals(listOf("newest", "newer", "old"), rows.map { it.label })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun append_trimsToRetentionWindow() = runTest {
        val retention = 5
        val pruning = DefaultRequestLogRepository(
            db.requestLogDao(),
            maxRetainedEntries = retention
        )
        repeat(20) { i ->
            pruning.append(sample(timestamp = i.toLong(), label = "row-$i"))
        }
        assertEquals(retention, pruning.count())
        pruning.observeRecent(limit = retention).test {
            val rows = awaitItem()
            assertEquals(retention, rows.size)
            // Newest five are 19..15
            assertEquals(
                (15..19).reversed().map { "row-$it" },
                rows.map { it.label }
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun productionRetentionIsTwoHundred() = runTest {
        // Sanity: contract surfaced to Task 2 must remain at 200.
        assertEquals(200, RequestLogSink.MAX_RETAINED_ENTRIES)
    }

    @Test
    fun clear_emptiesTable() = runTest {
        repository.append(sample(timestamp = 1L, label = "a"))
        repository.append(sample(timestamp = 2L, label = "b"))
        assertTrue(repository.count() > 0)
        repository.clear()
        assertEquals(0, repository.count())
    }

    @Test
    fun updateFeedback_attachesLocalFeedbackToMatchingRequestKey() = runTest {
        repository.append(sample(timestamp = 1L, label = "a", requestKey = "request-a"))

        val updated = repository.updateFeedback(
            requestKey = "request-a",
            feedback = "incorrect",
            timestamp = 123L,
        )

        assertEquals(1, updated)
        repository.observeRecent(limit = 1).test {
            val row = awaitItem().single()
            assertEquals("incorrect", row.feedback)
            assertEquals(123L, row.feedbackTimestamp)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun append_persistsQuestionMetadata() = runTest {
        repository.append(
            sample(
                timestamp = 1L,
                label = "2048__",
                question = "两个 2 怎么合并？",
                questionSource = "pending_hotkey",
            )
        )

        repository.observeRecent(limit = 1).test {
            val row = awaitItem().single()
            assertEquals("两个 2 怎么合并？", row.question)
            assertEquals("pending_hotkey", row.questionSource)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun sample(
        timestamp: Long,
        label: String,
        requestKey: String = "",
        question: String? = null,
        questionSource: String? = null,
        responseText: String = "ok",
        errorMessage: String? = null
    ) = RequestLogDomain(
        requestKey = requestKey,
        timestamp = timestamp,
        label = label,
        system = "Nintendo - Game Boy",
        game = "Demo",
        imageSize = 4096,
        paused = false,
        outputMode = "sound",
        question = question,
        questionSource = questionSource,
        responseText = responseText,
        errorMessage = errorMessage
    )
}
