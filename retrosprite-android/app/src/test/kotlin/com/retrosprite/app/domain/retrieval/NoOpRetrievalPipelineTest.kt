package com.retrosprite.app.domain.retrieval

import com.retrosprite.app.domain.models.RetrievalQuery
import com.retrosprite.app.domain.models.SpoilerLevel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoOpRetrievalPipelineTest {

    private val pipeline = NoOpRetrievalPipeline()

    @Test
    fun `retrieve always returns empty list`() = runTest {
        val results = pipeline.retrieve(
            RetrievalQuery(
                gameId = "anything",
                normalizedQuery = "where is the master sword",
                language = "zh",
                spoilerLevel = SpoilerLevel.LIGHT,
            )
        )
        assertTrue(results.isEmpty())
    }

    @Test
    fun `normalizeQuestion trims leading and trailing whitespace`() = runTest {
        assertEquals(
            "hello world",
            pipeline.normalizeQuestion("  Hello World  ", "en"),
        )
    }

    @Test
    fun `normalizeQuestion lowercases latin characters`() = runTest {
        assertEquals(
            "where is mario",
            pipeline.normalizeQuestion("WHERE is Mario", "en"),
        )
    }

    @Test
    fun `normalizeQuestion collapses runs of whitespace`() = runTest {
        assertEquals(
            "a b c",
            pipeline.normalizeQuestion("a   b\t\tc", "en"),
        )
    }

    @Test
    fun `normalizeQuestion preserves CJK characters`() = runTest {
        // lowercase() is a no-op for CJK but should not corrupt them.
        assertEquals(
            "马里奥 在哪",
            pipeline.normalizeQuestion("  马里奥  在哪 ", "zh"),
        )
    }

    @Test
    fun `normalizeQuestion of empty string returns empty string`() = runTest {
        assertEquals("", pipeline.normalizeQuestion("", "zh"))
    }
}
