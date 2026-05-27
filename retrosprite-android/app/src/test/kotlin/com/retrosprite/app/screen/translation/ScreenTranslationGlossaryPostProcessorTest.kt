package com.retrosprite.app.screen.translation

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTranslationGlossaryPostProcessorTest {

    @Test
    fun `replaces leftover English menu terms with glossary targets`() {
        val glossary = ScreenTranslationGlossary(
            gameId = "final_fantasy_vi",
            displayName = "Final Fantasy VI",
            terms = listOf(
                ScreenTranslationGlossaryTerm("ITEM", "道具", "menu"),
                ScreenTranslationGlossaryTerm("RELIC", "饰品", "menu"),
                ScreenTranslationGlossaryTerm("SLOT", "存档栏", "menu"),
                ScreenTranslationGlossaryTerm("Not enough MP", "MP 不足", "fixed_prompt"),
            ),
        )

        val result = ScreenTranslationGlossaryPostProcessor().apply(
            text = "ITEM\nRELIC\nSLOT 1\nNot enough MP",
            glossary = glossary,
        )

        assertEquals("道具\n饰品\n存档栏 1\nMP 不足", result)
    }

    @Test
    fun `does not rewrite unrelated Chinese output`() {
        val glossary = ScreenTranslationGlossary(
            gameId = "final_fantasy_vi",
            displayName = "Final Fantasy VI",
            terms = listOf(ScreenTranslationGlossaryTerm("ITEM", "道具", "menu")),
        )

        val result = ScreenTranslationGlossaryPostProcessor().apply(
            text = "欢迎来到城镇\n状态",
            glossary = glossary,
        )

        assertEquals("欢迎来到城镇\n状态", result)
    }
}
