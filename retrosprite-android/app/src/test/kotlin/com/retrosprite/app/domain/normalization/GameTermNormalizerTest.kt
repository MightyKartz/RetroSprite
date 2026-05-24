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
    fun `normalizes observed vigor ball asr corruption and tail`() {
        val result = normalizer.normalize(
            rawQuestion = "气和之欲怎么有",
            rows = listOf(
                row(
                    entityId = "item.vigor-ball",
                    canonicalName = "Vigor Ball / 气合之玉",
                    aliases = listOf("气合之玉"),
                )
            ),
        )

        assertTrue(result.applied)
        assertEquals("气合之玉怎么用", result.normalizedQuestion)
        assertEquals("气合之玉", result.matchedTerm)
        assertEquals("homophone+truncated_suffix", result.reason)
    }

    @Test
    fun `normalizes observed vigor ball duplicate prefix and tail`() {
        val result = normalizer.normalize(
            rawQuestion = "气气合之欲怎么又",
            rows = listOf(
                row(
                    entityId = "item.vigor-ball",
                    canonicalName = "Vigor Ball / 气合之玉",
                    aliases = listOf("气合之玉"),
                )
            ),
        )

        assertTrue(result.applied)
        assertEquals("气合之玉怎么用", result.normalizedQuestion)
        assertEquals("气合之玉", result.matchedTerm)
        assertEquals("observed_asr_rewrite+duplicate_prefix+truncated_suffix", result.reason)
    }

    @Test
    fun `normalizes observed exact vigor ball with noisy usage tail`() {
        val result = normalizer.normalize(
            rawQuestion = "气合之玉怎么也有",
            rows = listOf(
                row(
                    entityId = "item.vigor_ball",
                    canonicalName = "Vigor Ball / 气合之玉",
                    aliases = listOf("气合之玉"),
                )
            ),
        )

        assertTrue(result.applied)
        assertEquals("气合之玉怎么用", result.normalizedQuestion)
        assertEquals("truncated_suffix", result.reason)
    }

    @Test
    fun `normalizes observed vigor ball entity rewrite without duplicate prefix`() {
        val result = normalizer.normalize(
            rawQuestion = "气合之欲怎么又",
            rows = listOf(
                row(
                    entityId = "item.vigor-ball",
                    canonicalName = "Vigor Ball / 活力球",
                    aliases = emptyList(),
                )
            ),
        )

        assertTrue(result.applied)
        assertEquals("气合之玉怎么用", result.normalizedQuestion)
        assertEquals("气合之玉", result.matchedTerm)
        assertEquals("item.vigor-ball", result.matchedEntityId)
        assertEquals("observed_asr_rewrite+truncated_suffix", result.reason)
    }

    @Test
    fun `normalizes observed mithril silver asr homophone`() {
        val result = normalizer.normalize(
            rawQuestion = "米斯里鲁因有什么用",
            rows = listOf(
                row(
                    entityId = "item.mithril",
                    canonicalName = "Mithril / 米斯里鲁银",
                    aliases = listOf("米斯里鲁", "米斯里鲁银"),
                )
            ),
        )

        assertTrue(result.applied)
        assertEquals("米斯里鲁银有什么用", result.normalizedQuestion)
        assertEquals("米斯里鲁银", result.matchedTerm)
        assertEquals("observed_asr_rewrite", result.reason)
    }

    @Test
    fun `normalizes observed bare mithril item as usage when asr drops tail`() {
        val result = normalizer.normalize(
            rawQuestion = "米斯里鲁",
            rows = listOf(
                row(
                    entityId = "item.mithril",
                    entityType = "item",
                    canonicalName = "Mithril / 秘银",
                    aliases = listOf("秘银", "米斯里鲁", "米斯里鲁银"),
                )
            ),
        )

        assertTrue(result.applied)
        assertEquals("米斯里鲁有什么用", result.normalizedQuestion)
        assertEquals("米斯里鲁", result.matchedTerm)
        assertEquals("item.mithril", result.matchedEntityId)
        assertEquals("bare_item_usage", result.reason)
    }

    @Test
    fun `normalizes observed location homophone and truncated suffix`() {
        val result = normalizer.normalize(
            rawQuestion = "金陵村是不是隐藏地",
            rows = listOf(
                row(
                    entityId = "location.elven-town",
                    canonicalName = "Elven Town / 精灵森林",
                    aliases = listOf("精灵森林", "精灵村"),
                )
            )
        )

        assertTrue(result.applied)
        assertEquals("精灵村是不是隐藏地点", result.normalizedQuestion)
        assertEquals("金陵村", result.candidates.single().rawSpan)
        assertEquals("精灵村", result.matchedTerm)
        assertEquals("location.elven-town", result.matchedEntityId)
        assertEquals("homophone+truncated_suffix", result.reason)
    }

    @Test
    fun `completes truncated hidden location suffix for exact location term`() {
        val result = normalizer.normalize(
            rawQuestion = "精灵村是不是隐藏地",
            rows = listOf(
                row(
                    entityId = "location.elven-town",
                    canonicalName = "Elven Town / 精灵森林",
                    aliases = listOf("精灵森林", "精灵村"),
                )
            )
        )

        assertTrue(result.applied)
        assertEquals("精灵村是不是隐藏地点", result.normalizedQuestion)
        assertEquals("truncated_suffix", result.reason)
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

    @Test
    fun `does not rewrite ambiguous retro pack route and monster terms`() {
        listOf(
            NoRewriteCase(
                rawQuestion = "光辉线怎么进",
                rows = listOf(
                    row(
                        entityId = "route.light",
                        entityType = "strategy",
                        canonicalName = "Light route / 光辉线",
                        aliases = listOf("光辉线"),
                    ),
                    row(
                        entityId = "route.empire",
                        entityType = "strategy",
                        canonicalName = "Empire route / 帝国线",
                        aliases = listOf("帝国线", "雷昂线"),
                    ),
                ),
            ),
            NoRewriteCase(
                rawQuestion = "克罗诺是谁",
                rows = listOf(
                    row(
                        entityId = "note.identity",
                        entityType = "note",
                        canonicalName = "Chrono Trigger / 时空之轮",
                        aliases = listOf("时空之轮", "超时空之钥"),
                    ),
                    row(
                        entityId = "npc.crono",
                        canonicalName = "Crono / 克罗诺",
                        aliases = listOf("克罗诺"),
                    ),
                ),
            ),
            NoRewriteCase(
                rawQuestion = "幻兽是普通怪物吗",
                rows = listOf(
                    row(
                        entityId = "mechanic.espers",
                        entityType = "mechanic",
                        canonicalName = "Espers / 幻兽",
                        aliases = listOf("幻兽"),
                    ),
                    row(
                        entityId = "enemy.monster-bucket",
                        entityType = "enemy",
                        canonicalName = "Monsters / 怪物",
                        aliases = listOf("普通怪物"),
                    ),
                ),
            ),
            NoRewriteCase(
                rawQuestion = "魔兽是普通怪物吗",
                rows = listOf(
                    row(
                        entityId = "mechanic.espers",
                        entityType = "mechanic",
                        canonicalName = "Espers / 幻兽",
                        aliases = listOf("幻兽"),
                    ),
                    row(
                        entityId = "enemy.monster-bucket",
                        entityType = "enemy",
                        canonicalName = "Monsters / 怪物",
                        aliases = listOf("普通怪物"),
                    ),
                ),
            ),
        ).forEach { case ->
            val result = normalizer.normalize(case.rawQuestion, case.rows)

            assertFalse("${case.rawQuestion} should not be rewritten", result.applied)
            assertEquals(case.rawQuestion, result.normalizedQuestion)
        }
    }

    private data class NoRewriteCase(
        val rawQuestion: String,
        val rows: List<KnowledgeChunkDomain>,
    )

    private fun row(
        entityId: String,
        entityType: String = "npc",
        canonicalName: String,
        aliases: List<String>,
    ): KnowledgeChunkDomain = KnowledgeChunkDomain(
        id = 0L,
        gameId = "shining_force_ii_md",
        entityId = entityId,
        entityType = entityType,
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
