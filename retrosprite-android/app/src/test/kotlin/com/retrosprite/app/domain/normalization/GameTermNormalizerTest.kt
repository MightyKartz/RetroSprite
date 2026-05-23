package com.retrosprite.app.domain.normalization

import com.retrosprite.app.data.models.KnowledgeChunkDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTermNormalizerTest {

    private val normalizer = GameTermNormalizer()

    @Test
    fun `normalizes homophone span to current game alias`() {
        val result = normalizer.normalize(
            rawQuestion = "修医是谁",
            rows = listOf(row(entityId = "npc.jaha", canonicalName = "Jaha / 吉布", aliases = listOf("修伊")))
        )

        assertTrue(result.applied)
        assertEquals("修伊是谁", result.normalizedQuestion)
        assertEquals("修医", result.candidates.single().rawSpan)
        assertEquals("修伊", result.matchedTerm)
        assertEquals("npc.jaha", result.matchedEntityId)
        assertEquals("homophone", result.reason)
    }

    @Test
    fun `normalizes longer item homophone`() {
        val result = normalizer.normalize(
            rawQuestion = "气和之玉怎么用",
            rows = listOf(row(entityId = "item.vigor_ball", canonicalName = "Vigor Ball / 气合之玉", aliases = listOf("气合之玉")))
        )

        assertTrue(result.applied)
        assertEquals("气合之玉怎么用", result.normalizedQuestion)
        assertEquals("气合之玉", result.matchedTerm)
    }

    @Test
    fun `does not rewrite when candidate is ambiguous`() {
        val result = normalizer.normalize(
            rawQuestion = "修医是谁",
            rows = listOf(
                row(entityId = "npc.jaha", canonicalName = "Jaha / 吉布", aliases = listOf("修伊")),
                row(entityId = "npc.fake", canonicalName = "Fake / 修一", aliases = listOf("修一")),
            )
        )

        assertFalse(result.applied)
        assertEquals("修医是谁", result.normalizedQuestion)
        assertTrue(result.candidates.size >= 2)
    }

    @Test
    fun `keeps exact term unchanged but reports no rewrite`() {
        val result = normalizer.normalize(
            rawQuestion = "修伊是谁",
            rows = listOf(row(entityId = "npc.jaha", canonicalName = "Jaha / 吉布", aliases = listOf("修伊")))
        )

        assertFalse(result.applied)
        assertEquals("修伊是谁", result.normalizedQuestion)
        assertEquals(null, result.reason)
    }

    @Test
    fun `leaves unrelated question unchanged`() {
        val result = normalizer.normalize(
            rawQuestion = "这游戏怎么玩",
            rows = listOf(row(entityId = "npc.jaha", canonicalName = "Jaha / 吉布", aliases = listOf("修伊")))
        )

        assertFalse(result.applied)
        assertEquals("这游戏怎么玩", result.normalizedQuestion)
        assertTrue(result.candidates.isEmpty())
    }

    private fun row(
        entityId: String,
        canonicalName: String,
        aliases: List<String>,
    ): KnowledgeChunkDomain = KnowledgeChunkDomain(
        id = 0L,
        gameId = "shining_force_ii_md",
        entityId = entityId,
        entityType = "npc",
        canonicalName = canonicalName,
        aliases = aliases,
        descriptionShort = "desc",
        descriptionLong = null,
        progressGate = "start",
        spoilerLevel = "light",
        sourceRefs = listOf("test.source"),
        confidence = "verified",
        answerTemplates = emptyList(),
    )
}
