package com.retrosprite.app.data.retrieval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateConceptExtractorTest {

    @Test
    fun `extracts gameplay and fun concepts from natural phrases`() {
        assertEquals(
            setOf(TemplateConceptTag.GameplayLoop),
            TemplateConceptExtractor.extract("这游戏玩什么？"),
        )
        assertEquals(
            setOf(TemplateConceptTag.GameplayLoop),
            TemplateConceptExtractor.extract("这个游戏主要是干嘛的？"),
        )
        assertEquals(
            setOf(TemplateConceptTag.FunFactor),
            TemplateConceptExtractor.extract("好玩在哪？"),
        )
    }

    @Test
    fun `does not assign helpful gameplay concept to unsupported system question`() {
        assertTrue(TemplateConceptExtractor.extract("有没有恋爱系统？").isEmpty())
    }

    @Test
    fun `extracts team building concept from observed composition phrases`() {
        assertEquals(
            setOf(TemplateConceptTag.TeamBuild),
            TemplateConceptExtractor.extract("角色如何搭配？"),
        )
        assertEquals(
            setOf(TemplateConceptTag.TeamBuild),
            TemplateConceptExtractor.extract("职业怎么搭配？"),
        )
    }

    @Test
    fun `extracts strategy concept from observed tips and win phrases`() {
        assertTrue(TemplateConceptExtractor.extract("怎么才能赢？").isNotEmpty())
        assertTrue(TemplateConceptExtractor.extract("这个游戏玩的话有什么技巧吗？").isNotEmpty())
    }
}
