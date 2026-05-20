package com.retrosprite.app.ui.integration

import com.retrosprite.app.domain.models.LlmRequest
import com.retrosprite.app.domain.models.LlmResponse
import com.retrosprite.app.llm.LlmAdapter
import com.retrosprite.app.llm.LlmConfig
import com.retrosprite.app.ui.viewmodel.UiLlmProvider
import com.retrosprite.app.ui.viewmodel.UiSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealLlmConfigTestProviderTest {

    @Test
    fun `blank api key fails without creating adapter`() = runTest {
        var factoryCalls = 0
        val provider = RealLlmConfigTestProvider(
            factory = {
                factoryCalls += 1
                CapturingAdapter()
            }
        )

        val result = provider.test(
            UiSettings(
                llmProvider = UiLlmProvider.DeepSeek,
                llmApiKey = "",
                llmModel = "",
                llmTimeoutSeconds = 7,
                llmMaxTokens = 64,
            )
        )

        assertFalse(result.ok)
        assertEquals(0, factoryCalls)
        assertEquals("deepseek", result.provider)
        assertEquals("deepseek-v4-pro", result.model)
        assertEquals(64, result.maxTokens)
        assertEquals(7_000L, result.timeoutMs)
        assertEquals("请先填写 API Key", result.errorMessage)
    }

    @Test
    fun `successful smoke uses configured timeout and max tokens`() = runTest {
        val adapter = CapturingAdapter(
            response = LlmResponse(
                text = "OK",
                tokensIn = 8,
                tokensOut = 1,
                latencyMs = 123L,
            )
        )
        val provider = RealLlmConfigTestProvider(factory = { adapter })

        val result = provider.test(
            UiSettings(
                llmProvider = UiLlmProvider.DeepSeek,
                llmApiKey = "sk-test",
                llmBaseUrl = "",
                llmModel = "",
                llmTimeoutSeconds = 45,
                llmMaxTokens = 96,
            )
        )

        assertTrue(result.ok)
        assertEquals(96, adapter.lastRequest?.maxTokens)
        assertEquals("deepseek", result.provider)
        assertEquals("deepseek-v4-pro", result.model)
        assertEquals(96, result.maxTokens)
        assertEquals(45_000L, result.timeoutMs)
        assertEquals(123L, result.latencyMs)
        assertEquals(8, result.tokensIn)
        assertEquals(1, result.tokensOut)
        assertEquals("OK", result.responsePreview)
        assertNull(result.errorMessage)
    }

    @Test
    fun `provider failure redacts api key from error`() = runTest {
        val adapter = CapturingAdapter(errorMessage = "bad key sk-secret-123")
        val provider = RealLlmConfigTestProvider(
            factory = { adapter },
            clockMillis = Clock(sequence = listOf(1_000L, 1_042L))::next,
        )

        val result = provider.test(
            UiSettings(
                llmProvider = UiLlmProvider.OpenAI,
                llmApiKey = "sk-secret-123",
                llmTimeoutSeconds = 5,
                llmMaxTokens = 32,
            )
        )

        assertFalse(result.ok)
        assertEquals(42L, result.latencyMs)
        assertEquals("bad key [redacted]", result.errorMessage)
    }

    private class CapturingAdapter(
        private val response: LlmResponse = LlmResponse(text = "OK"),
        private val errorMessage: String? = null,
    ) : LlmAdapter {
        override val providerName: String = "capturing"
        override val modelName: String = "capturing-model"
        var lastRequest: LlmRequest? = null
            private set

        override suspend fun complete(request: LlmRequest): LlmResponse {
            lastRequest = request
            errorMessage?.let { error(it) }
            return response
        }
    }

    private class Clock(private val sequence: List<Long>) {
        private var index = 0
        fun next(): Long {
            val value = sequence[index.coerceAtMost(sequence.lastIndex)]
            index += 1
            return value
        }
    }
}
