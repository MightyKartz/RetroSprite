package com.retrosprite.app.endpoint

import com.retrosprite.app.endpoint.model.RetroArchRequest
import com.retrosprite.app.endpoint.model.RetroArchResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingQuestionResponseGeneratorTest {

    @Test
    fun `matching empty RetroArch request consumes pending question`() = runTest {
        val store = InMemoryPendingQuestionStore(
            PendingQuestion(
                label = "2048__",
                question = "两个 2 怎么合并？",
                spoilerLevel = "clear",
                createdAtMillis = 1L,
            )
        )
        val delegate = CapturingGenerator()
        val generator = PendingQuestionResponseGenerator(
            delegate = delegate,
            pendingQuestions = store,
        )

        val response = generator.generate(
            request = RetroArchRequest(label = "2048__"),
            outputMode = "text",
        )

        assertEquals("ok: 两个 2 怎么合并？", response.text)
        assertEquals("两个 2 怎么合并？", delegate.request?.question)
        assertEquals("clear", delegate.request?.spoilerLevel)
        assertEquals("两个 2 怎么合并？", response.diagnostics.question)
        assertEquals("pending_hotkey", response.diagnostics.questionSource)
        assertNull(store.pending.value)
    }

    @Test
    fun `explicit question bypasses pending queue and leaves it intact`() = runTest {
        val store = InMemoryPendingQuestionStore(
            PendingQuestion(
                label = "2048__",
                question = "queued question",
                spoilerLevel = "direct",
            )
        )
        val delegate = CapturingGenerator()
        val generator = PendingQuestionResponseGenerator(delegate, store)

        generator.generate(
            request = RetroArchRequest(
                label = "2048__",
                question = "debug route question",
            ),
            outputMode = "text",
        )

        assertEquals("debug route question", delegate.request?.question)
        assertEquals("queued question", store.pending.value?.question)
    }

    @Test
    fun `different label does not consume pending question`() = runTest {
        val store = InMemoryPendingQuestionStore(
            PendingQuestion(
                label = "relay_station__",
                question = "蓝色保险丝在哪？",
                spoilerLevel = "light",
            )
        )
        val delegate = CapturingGenerator()
        val generator = PendingQuestionResponseGenerator(delegate, store)

        generator.generate(
            request = RetroArchRequest(label = "2048__"),
            outputMode = "text",
        )

        assertEquals("", delegate.request?.question)
        assertEquals("蓝色保险丝在哪？", store.pending.value?.question)
    }

    private class CapturingGenerator : ResponseGenerator {
        var request: RetroArchRequest? = null
            private set
        var outputMode: String? = null
            private set

        override suspend fun generate(
            request: RetroArchRequest,
            outputMode: String,
        ): RetroArchResponse {
            this.request = request
            this.outputMode = outputMode
            return RetroArchResponse.text("ok: ${request.question}")
        }
    }
}
