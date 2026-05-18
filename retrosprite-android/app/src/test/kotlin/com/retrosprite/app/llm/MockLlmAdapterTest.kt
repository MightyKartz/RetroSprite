package com.retrosprite.app.llm

import com.retrosprite.app.domain.models.LlmRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MockLlmAdapterTest {

    @Test
    fun `provider name is mock`() {
        assertEquals("mock", MockLlmAdapter().providerName)
    }

    @Test
    fun `complete returns the fixed phase 0 text and zeroed metadata`() = runTest {
        val response = MockLlmAdapter().complete(
            LlmRequest(
                systemPrompt = "ignored",
                userPrompt = "ignored",
                evidence = emptyList(),
                maxTokens = 32,
            )
        )

        assertEquals(MockLlmAdapter.MOCK_TEXT, response.text)
        assertTrue(response.citationsUsed.isEmpty())
        assertEquals(0, response.tokensIn)
        assertEquals(0, response.tokensOut)
        assertEquals(0L, response.latencyMs)
    }

    @Test
    fun `factory returns mock adapter for default config`() {
        val adapter = LlmAdapterFactory.create(LlmConfig.MOCK)
        assertTrue(adapter is MockLlmAdapter)
    }

    @Test
    fun `factory falls back to mock for unknown providers`() {
        val adapter = LlmAdapterFactory.create(
            LlmConfig(
                providerName = "some-future-provider",
                baseUrl = "",
                apiKey = "",
                model = "x",
            )
        )
        assertTrue(adapter is MockLlmAdapter)
    }

    @Test
    fun `factory returns OpenAi skeleton for explicit opt-in`() {
        val adapter = LlmAdapterFactory.create(
            LlmConfig(
                providerName = "openai-real",
                baseUrl = "https://api.openai.com/v1",
                apiKey = "sk-xxx",
                model = "gpt-4o-mini",
            )
        )
        assertTrue(adapter is OpenAiCompatibleLlmAdapter)
    }

    @Test(expected = NotImplementedError::class)
    fun `OpenAi adapter throws NotImplementedError in phase 0`() = runTest {
        val adapter = OpenAiCompatibleLlmAdapter(
            LlmConfig(
                providerName = "openai-real",
                baseUrl = "https://api.openai.com/v1",
                apiKey = "sk-xxx",
                model = "gpt-4o-mini",
            )
        )
        adapter.complete(LlmRequest(systemPrompt = "x", userPrompt = "y"))
    }

    @Test
    fun `MOCK config sentinel is reusable`() {
        // Sanity: the sentinel must not be mutated between calls.
        val a = LlmConfig.MOCK
        val b = LlmConfig.MOCK
        assertSame(a, b)
    }
}
