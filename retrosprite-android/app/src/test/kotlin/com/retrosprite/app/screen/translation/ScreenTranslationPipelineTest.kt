package com.retrosprite.app.screen.translation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTranslationPipelineTest {

    @Test
    fun `pipeline sends screenshot to api provider and formats translated pages`() = runTest {
        val provider = FakeScreenTranslationProvider("欢迎来到港口城市。")
        val pipeline = ApiScreenTranslationPipeline(
            provider = provider,
            formatter = ScreenTranslationFormatter(maxCharsPerPage = 100),
        )

        val result = pipeline.translateCurrentScreen("base64_png")

        assertEquals("base64_png", provider.imageBase64)
        assertEquals("欢迎来到港口城市。", result.translatedText)
        assertEquals(listOf("欢迎来到港口城市。"), result.pages)
        assertEquals("fake-api", result.providerName)
        assertEquals("fake-model", result.model)
    }

    @Test
    fun `pipeline injects context and applies glossary post processing`() = runTest {
        val provider = FakeScreenTranslationProvider("ITEM\nRELIC\nSLOT 1")
        val glossary = ScreenTranslationGlossary(
            gameId = "final_fantasy_vi",
            displayName = "Final Fantasy VI",
            terms = listOf(
                ScreenTranslationGlossaryTerm("ITEM", "道具", "menu"),
                ScreenTranslationGlossaryTerm("RELIC", "饰品", "menu"),
                ScreenTranslationGlossaryTerm("SLOT", "存档栏", "menu"),
            ),
        )
        val pipeline = ApiScreenTranslationPipeline(
            provider = provider,
            formatter = ScreenTranslationFormatter(maxCharsPerPage = 100),
        )

        val result = pipeline.translateCurrentScreen(
            imageBase64 = "base64_png",
            context = ScreenTranslationContext(
                label = "playstation__Final Fantasy Anthology - Final Fantasy VI",
                glossary = glossary,
            ),
        )

        assertEquals("base64_png", provider.imageBase64)
        assertEquals("playstation__Final Fantasy Anthology - Final Fantasy VI", provider.context?.label)
        assertEquals(glossary, provider.context?.glossary)
        assertEquals("道具\n饰品\n存档栏 1", result.translatedText)
        assertEquals(listOf("道具\n饰品\n存档栏 1"), result.pages)
    }

    @Test
    fun `pipeline renders structured menu response without raw numbers or json`() = runTest {
        val provider = FakeScreenTranslationProvider(
            """
            {
              "mode": "menu",
              "entries": [
                {"source": "ITEM", "translation": "ITEM", "type": "menu"},
                {"source": "EQUIP", "translation": "EQUIP", "type": "menu"},
                {"source": "344", "translation": "三百四十四", "type": "number"},
                {"source": "HP 344/344", "translation": "HP 三百四十四/三百四十四", "type": "stat"}
              ]
            }
            """.trimIndent(),
        )
        val glossary = ScreenTranslationGlossary(
            gameId = "final_fantasy_vi",
            displayName = "Final Fantasy VI",
            terms = listOf(
                ScreenTranslationGlossaryTerm("ITEM", "道具", "menu"),
                ScreenTranslationGlossaryTerm("EQUIP", "装备", "menu"),
                ScreenTranslationGlossaryTerm("HP", "HP", "system"),
            ),
        )
        val pipeline = ApiScreenTranslationPipeline(
            provider = provider,
            formatter = ScreenTranslationFormatter(maxCharsPerPage = 100),
        )

        val result = pipeline.translateCurrentScreen(
            imageBase64 = "base64_png",
            context = ScreenTranslationContext(glossary = glossary),
        )

        assertEquals(
            """
            菜单
            ITEM 道具 | EQUIP 装备
            属性
            HP 344/344
            """.trimIndent(),
            result.translatedText,
        )
        assertEquals(listOf(result.translatedText), result.pages)
    }

    @Test
    fun `pipeline returns clear failure when api translation is blank`() = runTest {
        val pipeline = ApiScreenTranslationPipeline(
            provider = FakeScreenTranslationProvider(""),
            formatter = ScreenTranslationFormatter(),
        )

        val result = pipeline.translateCurrentScreen("base64_png")

        assertEquals("翻译结果为空，请稍后再试。", result.translatedText)
        assertEquals(listOf("翻译结果为空，请稍后再试。"), result.pages)
    }

    private class FakeScreenTranslationProvider(
        private val translated: String,
    ) : ScreenTranslationProvider {
        override val providerName: String = "fake-api"
        override val model: String = "fake-model"
        var imageBase64: String? = null

        var context: ScreenTranslationContext? = null

        override suspend fun translateScreenshotToChinese(
            imageBase64: String,
            context: ScreenTranslationContext,
        ): String {
            this.imageBase64 = imageBase64
            this.context = context
            return translated
        }
    }
}
