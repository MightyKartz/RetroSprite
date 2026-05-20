package com.retrosprite.app.llm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.retrosprite.app.domain.models.LlmRequest
import com.retrosprite.app.ui.settings.UiSettingsStore
import com.retrosprite.app.ui.viewmodel.UiLlmProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicit live smoke for M1.6.
 *
 * This test is skipped unless scripts/deepseek_live_smoke.sh has staged the
 * API key into the app-private files directory. The file is deleted immediately
 * after reading; Settings then persists the key through the Keystore-backed
 * path before the real adapter call.
 */
@RunWith(AndroidJUnit4::class)
class DeepSeekLiveSmokeTest {

    @Test
    fun callsDeepSeekThroughSettingsAndOpenAiCompatibleAdapter() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val keyFile = File(context.filesDir, KEY_FILE_NAME)
        assumeTrue("DeepSeek live smoke key file is not staged", keyFile.isFile)

        val apiKey = keyFile.readText().trim()
        keyFile.delete()
        assumeTrue("DeepSeek live smoke key is blank", apiKey.isNotBlank())

        val store = UiSettingsStore(context)
        store.updateLlmConfig(
            provider = UiLlmProvider.DeepSeek,
            apiKey = apiKey,
            baseUrl = "",
            model = "",
            timeoutSeconds = 60,
            maxTokens = 64,
        )

        val settings = store.settings.first()
        assertEquals(UiLlmProvider.DeepSeek, settings.llmProvider)
        assertEquals(LlmConfig.DEEPSEEK_BASE_URL, settings.llmBaseUrl)
        assertEquals(LlmConfig.DEEPSEEK_DEFAULT_MODEL, settings.llmModel)
        assertFalse(settings.llmApiKey.isBlank())

        val response = OpenAiCompatibleLlmAdapter(
            LlmConfig.deepSeek(
                apiKey = settings.llmApiKey,
                baseUrl = settings.llmBaseUrl,
                model = settings.llmModel,
                timeoutSeconds = 60L,
            )
        ).complete(
            LlmRequest(
                systemPrompt = "你是 RetroSprite 的联调探针。只根据用户文本回答，输出不超过 20 个中文字。",
                userPrompt = "请回复：RetroSprite DeepSeek smoke ok",
                maxTokens = 32,
            )
        )

        assertTrue(response.text.isNotBlank())
        assertTrue(response.latencyMs >= 0L)
    }

    private companion object {
        const val KEY_FILE_NAME = "deepseek_live_smoke.key"
    }
}
