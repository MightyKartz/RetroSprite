package com.retrosprite.app.ui.settings

import com.retrosprite.app.domain.models.SpoilerLevel
import com.retrosprite.app.llm.LlmConfig
import com.retrosprite.app.ui.viewmodel.UiLlmProvider
import com.retrosprite.app.ui.viewmodel.UiSettings
import com.retrosprite.app.ui.viewmodel.UiSpoilerLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UiLlmConfigMapperTest {

    @Test
    fun `returns null when api key is blank`() {
        assertNull(
            UiSettings(
                llmProvider = UiLlmProvider.DeepSeek,
                llmApiKey = " ",
            ).toLlmConfigOrNull()
        )
    }

    @Test
    fun `maps DeepSeek settings to official defaults`() {
        val config = UiSettings(
            llmProvider = UiLlmProvider.DeepSeek,
            llmApiKey = " sk-test ",
            llmBaseUrl = "",
            llmModel = "",
            llmTimeoutSeconds = 45,
        ).toLlmConfigOrNull()

        assertEquals(LlmConfig.PROVIDER_DEEPSEEK, config?.providerName)
        assertEquals(LlmConfig.DEEPSEEK_BASE_URL, config?.baseUrl)
        assertEquals(LlmConfig.DEEPSEEK_DEFAULT_MODEL, config?.model)
        assertEquals("sk-test", config?.apiKey)
        assertEquals(45L, config?.timeoutSeconds)
    }

    @Test
    fun `clamps timeout settings before building config`() {
        val tooLow = UiSettings(
            llmProvider = UiLlmProvider.OpenAI,
            llmApiKey = "sk-test",
            llmTimeoutSeconds = 1,
        ).toLlmConfigOrNull()
        val tooHigh = UiSettings(
            llmProvider = UiLlmProvider.OpenAI,
            llmApiKey = "sk-test",
            llmTimeoutSeconds = 999,
        ).toLlmConfigOrNull()

        assertEquals(5L, tooLow?.timeoutSeconds)
        assertEquals(120L, tooHigh?.timeoutSeconds)
    }

    @Test
    fun `maps custom OpenAI-compatible settings only when base url and model exist`() {
        assertNull(
            UiSettings(
                llmProvider = UiLlmProvider.Custom,
                llmApiKey = "sk-test",
                llmBaseUrl = "",
                llmModel = "custom-model",
            ).toLlmConfigOrNull()
        )

        val config = UiSettings(
            llmProvider = UiLlmProvider.Custom,
            llmApiKey = "sk-test",
            llmBaseUrl = "https://example.test/v1",
            llmModel = "custom-model",
        ).toLlmConfigOrNull()

        assertEquals(LlmConfig.PROVIDER_CUSTOM, config?.providerName)
        assertEquals("https://example.test/v1", config?.baseUrl)
        assertEquals("custom-model", config?.model)
    }

    @Test
    fun `maps UI spoiler levels to domain spoiler levels`() {
        assertEquals(SpoilerLevel.LIGHT, UiSpoilerLevel.Light.toDomainSpoilerLevel())
        assertEquals(SpoilerLevel.CLEAR, UiSpoilerLevel.Clear.toDomainSpoilerLevel())
        assertEquals(SpoilerLevel.FULL, UiSpoilerLevel.Direct.toDomainSpoilerLevel())
    }
}
