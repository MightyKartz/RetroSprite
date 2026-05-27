package com.retrosprite.app.screen.translation

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleScreenTranslationProviderTest {

    @Test
    fun `posts screenshot and translation instruction to OpenAI compatible vision endpoint`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "choices": [
                        {
                          "message": {
                            "content": "欢迎来到港口城市。\n物品\n状态"
                          }
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )
        server.start()

        val provider = OpenAiCompatibleScreenTranslationProvider(
            providerName = "SiliconFlow",
            baseUrl = server.url("/v1").toString().trimEnd('/'),
            apiKey = "secret-key",
            model = "Qwen/Qwen3-VL-8B-Instruct",
        )

        val result = provider.translateScreenshotToChinese("abc123")
        val request = server.takeRequest()
        val body = request.body.readUtf8()

        assertEquals("/v1/chat/completions", request.path)
        assertEquals("Bearer secret-key", request.getHeader("Authorization"))
        assertTrue(body.contains("\"model\":\"Qwen/Qwen3-VL-8B-Instruct\""))
        assertTrue(body.contains("data:image/png;base64,abc123"))
        assertTrue(body.contains("只输出中文译文"))
        assertEquals("欢迎来到港口城市。\n物品\n状态", result)

        server.shutdown()
    }

    @Test
    fun `includes game glossary terms in translation prompt`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "choices": [
                        {
                          "message": {
                            "content": "道具\n魔石"
                          }
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )
        server.start()

        val provider = OpenAiCompatibleScreenTranslationProvider(
            providerName = "SiliconFlow",
            baseUrl = server.url("/v1").toString().trimEnd('/'),
            apiKey = "secret-key",
            model = "Qwen/Qwen3-VL-8B-Instruct",
        )
        val context = ScreenTranslationContext(
            label = "playstation__Final Fantasy Anthology - Final Fantasy VI",
            glossary = ScreenTranslationGlossary(
                gameId = "final_fantasy_vi",
                displayName = "Final Fantasy VI",
                terms = listOf(
                    ScreenTranslationGlossaryTerm("ITEM", "道具", "menu"),
                    ScreenTranslationGlossaryTerm("ESPER", "魔石", "system"),
                ),
            ),
        )

        provider.translateScreenshotToChinese("abc123", context)
        val body = server.takeRequest().body.readUtf8()

        assertTrue(body.contains("当前游戏：Final Fantasy VI"))
        assertTrue(body.contains("ITEM = 道具"))
        assertTrue(body.contains("ESPER = 魔石"))
        assertTrue(body.contains("不要保留英文菜单原文"))
        assertTrue(body.contains("菜单/状态/物品/装备画面"))
        assertTrue(body.contains("严格 JSON"))
        assertTrue(body.contains("纯数字"))
        assertTrue(body.contains("状态项必须保留标签和数值"))
        assertTrue(body.contains("不要把标签和值拆成两个 entries"))
        assertTrue(body.contains("equipment"))
        assertTrue(body.contains("中英文对照"))
        assertTrue(body.contains("value 填"))

        server.shutdown()
    }

    @Test
    fun `requires user configured api key`() = runTest {
        val provider = OpenAiCompatibleScreenTranslationProvider(
            providerName = "SiliconFlow",
            baseUrl = "https://api.siliconflow.cn/v1",
            apiKey = "",
            model = "Qwen/Qwen3-VL-8B-Instruct",
        )

        val error = runCatching {
            provider.translateScreenshotToChinese("abc123")
        }.exceptionOrNull()

        assertEquals("请先在设置页填写翻译 API Key。", error?.message)
    }
}
