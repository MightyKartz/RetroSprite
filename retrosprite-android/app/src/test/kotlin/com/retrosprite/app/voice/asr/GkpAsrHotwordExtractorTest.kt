package com.retrosprite.app.voice.asr

import com.retrosprite.app.data.models.KnowledgeChunkDomain
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GkpAsrHotwordExtractorTest {

    @Test
    fun `extracts chinese patch names with higher score than english canonical words`() {
        val rows = listOf(
            chunk(
                entityId = "npc.chester",
                entityType = "npc",
                canonicalName = "Chester / 切斯特",
                aliases = listOf("Chester", "切斯特", "修伊", "骑士"),
            ),
            chunk(
                entityId = "item.vigor-ball",
                entityType = "item",
                canonicalName = "活力球 / Vigor Ball",
                aliases = listOf("活力球", "气合之玉", "Vigor Ball", "武僧"),
            ),
        )

        val profile = GkpAsrHotwordExtractor().extract(
            gameId = "shining_force_ii_md",
            packVersion = "0.2.5",
            rows = rows,
        )

        val terms = profile.normalizedEntries.associateBy { it.term }
        assertTrue(terms.getValue("修伊").score >= GkpAsrHotwordExtractor.PATCH_NAME_SCORE)
        assertTrue(terms.getValue("气合之玉").score >= GkpAsrHotwordExtractor.PATCH_NAME_SCORE)
        assertTrue(terms.getValue("修伊").score > terms.getValue("Chester").score)
        assertFalse("generic role terms should not be boosted", terms.containsKey("骑士"))
    }

    @Test
    fun `extracts entity term from template question pattern without question scaffold`() {
        val rows = listOf(
            chunk(
                entityId = "location.secret-villages",
                entityType = "location",
                canonicalName = "秘密村庄概览",
                aliases = listOf("精灵森林"),
                answerTemplates = listOf(
                    """{"question_patterns":["精灵森林是什么","精灵森林在哪"],"answer":"..."}""",
                ),
            ),
        )

        val profile = GkpAsrHotwordExtractor().extract(
            gameId = "shining_force_ii_md",
            packVersion = "0.2.5",
            rows = rows,
        )

        assertTrue(profile.normalizedEntries.any { it.term == "精灵森林" })
        assertFalse(profile.normalizedEntries.any { it.term == "精灵森林是什么" })
    }

    private fun chunk(
        entityId: String,
        entityType: String,
        canonicalName: String,
        aliases: List<String>,
        answerTemplates: List<String> = emptyList(),
    ): KnowledgeChunkDomain =
        KnowledgeChunkDomain(
            id = 0L,
            gameId = "shining_force_ii_md",
            entityId = entityId,
            entityType = entityType,
            canonicalName = canonicalName,
            aliases = aliases,
            descriptionShort = "",
            descriptionLong = null,
            progressGate = "start",
            spoilerLevel = "light",
            sourceRefs = emptyList(),
            confidence = "community",
            answerTemplates = answerTemplates,
        )
}
