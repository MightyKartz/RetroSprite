package com.retrosprite.app.llm

import com.retrosprite.app.domain.models.Evidence
import com.retrosprite.app.domain.models.LlmRequest
import com.retrosprite.app.domain.models.SpoilerLevel
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
    fun `complete returns deterministic evidence summary when evidence is present`() = runTest {
        val response = MockLlmAdapter().complete(
            LlmRequest(
                systemPrompt = "ignored",
                userPrompt = "ignored",
                evidence = listOf(
                    Evidence(
                        sourceId = "sample.2048.rules",
                        snippet = "两个相同数字滑到一起会合并。",
                        score = 0.9,
                        spoilerLevel = SpoilerLevel.LIGHT,
                    ),
                    Evidence(
                        sourceId = "sample.2048.strategy",
                        snippet = "棋盘快满时优先制造空格。",
                        score = 0.8,
                        spoilerLevel = SpoilerLevel.LIGHT,
                    ),
                ),
                maxTokens = 64,
            )
        )

        assertEquals("两个相同数字滑到一起会合并。 棋盘快满时优先制造空格。", response.text)
        assertEquals(listOf("sample.2048.rules", "sample.2048.strategy"), response.citationsUsed)
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
                providerName = LlmConfig.PROVIDER_OPENAI_REAL,
                baseUrl = "https://api.openai.com/v1",
                apiKey = "sk-xxx",
                model = "gpt-4o-mini",
            )
        )
        assertTrue(adapter is OpenAiCompatibleLlmAdapter)
    }

    @Test
    fun `factory returns OpenAi compatible adapter for deepseek`() {
        val adapter = LlmAdapterFactory.create(
            LlmConfig.deepSeek(apiKey = "sk-xxx")
        )
        assertTrue(adapter is OpenAiCompatibleLlmAdapter)
    }

    @Test
    fun `DeepSeek preset matches official OpenAI compatible defaults`() {
        val config = LlmConfig.deepSeek(apiKey = "sk-xxx")

        assertEquals(LlmConfig.PROVIDER_DEEPSEEK, config.providerName)
        assertEquals(LlmConfig.DEEPSEEK_BASE_URL, config.baseUrl)
        assertEquals("sk-xxx", config.apiKey)
        assertEquals(LlmConfig.DEEPSEEK_DEFAULT_MODEL, config.model)
    }

    @Test
    fun `MOCK config sentinel is reusable`() {
        // Sanity: the sentinel must not be mutated between calls.
        val a = LlmConfig.MOCK
        val b = LlmConfig.MOCK
        assertSame(a, b)
    }
}
