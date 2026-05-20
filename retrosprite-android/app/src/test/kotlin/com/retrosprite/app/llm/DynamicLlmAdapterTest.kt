package com.retrosprite.app.llm

import com.retrosprite.app.domain.models.LlmRequest
import com.retrosprite.app.domain.models.LlmResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DynamicLlmAdapterTest {

    @Test
    fun `uses fallback when config is missing`() = runTest {
        val fallback = RecordingAdapter("mock", "fallback")
        val adapter = DynamicLlmAdapter(
            configProvider = { null },
            fallback = fallback,
            factory = { error("factory should not be called") },
        )

        val response = adapter.complete(LlmRequest(systemPrompt = "system", userPrompt = "user"))

        assertEquals("mock", adapter.providerName)
        assertEquals("fallback", response.text)
        assertEquals(1, fallback.calls)
    }

    @Test
    fun `delegates to latest configured provider`() = runTest {
        val config = LlmConfig.deepSeek(apiKey = "sk-test")
        var seenConfig: LlmConfig? = null
        val real = RecordingAdapter("deepseek", "real")
        val adapter = DynamicLlmAdapter(
            configProvider = { config },
            fallback = RecordingAdapter("mock", "fallback"),
            factory = {
                seenConfig = it
                real
            },
        )

        val response = adapter.complete(LlmRequest(systemPrompt = "system", userPrompt = "user"))

        assertEquals("deepseek", adapter.providerName)
        assertEquals("deepseek-v4-pro", adapter.modelName)
        assertEquals(30_000L, adapter.timeoutMs)
        assertEquals("real", response.text)
        assertSame(config, seenConfig)
        assertEquals(1, real.calls)
    }

    private class RecordingAdapter(
        override val providerName: String,
        private val text: String,
    ) : LlmAdapter {
        var calls: Int = 0
            private set

        override suspend fun complete(request: LlmRequest): LlmResponse {
            calls += 1
            return LlmResponse(text = text)
        }
    }
}
