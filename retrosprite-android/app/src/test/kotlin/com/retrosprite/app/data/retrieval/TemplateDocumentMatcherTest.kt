package com.retrosprite.app.data.retrieval

import com.retrosprite.app.data.models.KnowledgeChunkDomain
import com.retrosprite.app.domain.models.AnswerType
import com.retrosprite.app.domain.models.SpoilerLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TemplateDocumentMatcherTest {

    @Test
    fun `matches nearby gameplay question without exact pattern`() {
        val match = TemplateDocumentMatcher().bestMatch(
            query = "这游戏玩什么？",
            queryIntent = AnswerType.GameOverview,
            rows = listOf(coreGameplayRow()),
            tolerance = SpoilerLevel.LIGHT,
        )

        assertNotNull(match)
        requireNotNull(match)
        assertEquals("note.core-gameplay-loop", match.document.entityId)
        assertEquals("note", match.document.entityType)
        assertEquals("note.core-gameplay-loop#template.sf2.core-gameplay.zh", match.document.documentId)
        assertFalse(match.exactPattern)
        assertEquals("主要玩剧情推进、队伍培养和网格回合制战斗。", match.document.selectedAnswer)
        assertEquals(listOf("sf2.official_overview"), match.document.sourceRefs)
    }

    @Test
    fun `matches colloquial what is this game about question`() {
        val match = TemplateDocumentMatcher().bestMatch(
            query = "这个游戏主要是干嘛的？",
            queryIntent = AnswerType.GameOverview,
            rows = listOf(coreGameplayRow()),
            tolerance = SpoilerLevel.LIGHT,
        )

        assertNotNull(match)
        requireNotNull(match)
        assertEquals("note.core-gameplay-loop", match.document.entityId)
        assertFalse(match.exactPattern)
    }

    @Test
    fun `rejects unrelated system and mismatched intent questions`() {
        val matcher = TemplateDocumentMatcher()
        val rows = listOf(coreGameplayRow())

        assertNull(
            matcher.bestMatch(
                query = "有没有恋爱系统？",
                queryIntent = AnswerType.UnknownOrOutOfScope,
                rows = rows,
                tolerance = SpoilerLevel.LIGHT,
            )
        )
        assertNull(
            matcher.bestMatch(
                query = "这游戏玩什么？",
                queryIntent = AnswerType.Production,
                rows = rows,
                tolerance = SpoilerLevel.LIGHT,
            )
        )
    }

    @Test
    fun `respects template spoiler selection`() {
        val match = TemplateDocumentMatcher().bestMatch(
            query = "Mithril 在哪里？",
            queryIntent = AnswerType.Location,
            rows = listOf(
                chunk(
                    entityId = "item.mithril",
                    entityType = "item",
                    canonicalName = "Mithril / 米斯里鲁",
                    aliases = listOf("Mithril", "米斯里鲁"),
                    answerTemplates = listOf(
                        """
                        {
                          "template_id":"template.sf2.mithril.location.zh",
                          "intent":"location",
                          "question_patterns":["Mithril 在哪里","米斯里鲁在哪里"],
                          "answer_light":"低剧透：先记得它是隐藏锻造材料。",
                          "answer_direct":"直接答案：查隐藏位置清单。",
                          "spoiler_light":"light",
                          "spoiler_direct":"heavy",
                          "source_refs":["sf2.items"]
                        }
                        """
                    ),
                )
            ),
            tolerance = SpoilerLevel.LIGHT,
        )

        assertNotNull(match)
        requireNotNull(match)
        assertEquals("低剧透：先记得它是隐藏锻造材料。", match.document.selectedAnswer)
    }

    private fun coreGameplayRow(): KnowledgeChunkDomain =
        chunk(
            entityId = "note.core-gameplay-loop",
            entityType = "note",
            canonicalName = "核心玩法",
            aliases = listOf("这游戏怎么玩", "主要玩什么", "好玩在哪"),
            sourceRefs = listOf("sf2.official_overview", "sf2.project_mechanics"),
            answerTemplates = listOf(
                """
                {
                  "template_id":"template.sf2.core-gameplay.zh",
                  "language":"zh",
                  "intent":"game_overview",
                  "question_patterns":["这游戏怎么玩","主要玩什么","核心玩法是什么"],
                  "answer":"主要玩剧情推进、队伍培养和网格回合制战斗。",
                  "source_refs":["sf2.official_overview"],
                  "spoiler_level":"none"
                }
                """
            ),
        )

    private fun chunk(
        entityId: String,
        entityType: String,
        canonicalName: String,
        aliases: List<String> = emptyList(),
        sourceRefs: List<String> = listOf("sample.source"),
        answerTemplates: List<String>,
    ): KnowledgeChunkDomain = KnowledgeChunkDomain(
        id = 0L,
        gameId = "shining_force_ii_md",
        entityId = entityId,
        entityType = entityType,
        canonicalName = canonicalName,
        aliases = aliases,
        descriptionShort = canonicalName,
        descriptionLong = null,
        progressGate = "start",
        spoilerLevel = "none",
        sourceRefs = sourceRefs,
        confidence = "verified",
        answerTemplates = answerTemplates.map { it.trimIndent() },
    )
}
