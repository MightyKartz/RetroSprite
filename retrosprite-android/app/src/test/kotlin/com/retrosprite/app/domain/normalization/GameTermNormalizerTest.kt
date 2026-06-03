package com.retrosprite.app.domain.normalization

import com.retrosprite.app.data.models.KnowledgeAliasDomain
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
                    aliasMetadata = listOf(asrVariant("气合之欲", "气合之玉", "item.vigor-ball")),
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
                    aliasMetadata = listOf(asrVariant("气合之欲", "气合之玉", "item.vigor-ball")),
                )
            ),
        )

        assertTrue(result.applied)
        assertEquals("气合之玉怎么用", result.normalizedQuestion)
        assertEquals("气合之玉", result.matchedTerm)
        assertEquals("gkp_observed_asr_variant+duplicate_prefix+truncated_suffix", result.reason)
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
                    aliasMetadata = listOf(asrVariant("气合之欲", "气合之玉", "item.vigor-ball")),
                )
            ),
        )

        assertTrue(result.applied)
        assertEquals("气合之玉怎么用", result.normalizedQuestion)
        assertEquals("气合之玉", result.matchedTerm)
        assertEquals("item.vigor-ball", result.matchedEntityId)
        assertEquals("gkp_observed_asr_variant+truncated_suffix", result.reason)
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
                    aliasMetadata = listOf(asrVariant("米斯里鲁因", "米斯里鲁银", "item.mithril")),
                )
            ),
        )

        assertTrue(result.applied)
        assertEquals("米斯里鲁银有什么用", result.normalizedQuestion)
        assertEquals("米斯里鲁银", result.matchedTerm)
        assertEquals("gkp_observed_asr_variant", result.reason)
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
    fun `normalizes observed mithril voice variants only with matching game entity`() {
        listOf(
            Triple("密营武器怎么打造", "秘银武器怎么打造", "秘银"),
            Triple("密影武器怎么打造", "秘银武器怎么打造", "秘银"),
            Triple("米斯林鲁在哪里拿", "米斯里鲁在哪里拿", "米斯里鲁"),
            Triple("以斯列鲁在哪里拿", "米斯里鲁在哪里拿", "米斯里鲁"),
        ).forEach { (rawQuestion, normalizedQuestion, canonicalTerm) ->
            val result = normalizer.normalize(
                rawQuestion = rawQuestion,
                rows = listOf(
                    row(
                        entityId = "item.mithril",
                        entityType = "item",
                        canonicalName = "Mithril / 秘银",
                        aliases = listOf("秘银", "米斯里鲁", "米斯里鲁银"),
                        aliasMetadata = listOf(asrVariant(rawQuestion.takeWhile { it !in "武在" }, canonicalTerm, "item.mithril")),
                    )
                ),
            )

            assertTrue("$rawQuestion should be normalized", result.applied)
            assertEquals(normalizedQuestion, result.normalizedQuestion)
            assertEquals("item.mithril", result.matchedEntityId)
            assertEquals("gkp_observed_asr_variant", result.reason)
        }
    }

    @Test
    fun `does not normalize mithril voice variants without matching game entity`() {
        val result = normalizer.normalize(
            rawQuestion = "密营武器怎么打造",
            rows = emptyList(),
        )

        assertFalse(result.applied)
        assertEquals("密营武器怎么打造", result.normalizedQuestion)
        assertEquals(null, result.matchedEntityId)
        assertTrue(result.candidates.isEmpty())
    }

    @Test
    fun `does not apply display alias as asr variant`() {
        val result = normalizer.normalize(
            rawQuestion = "千年记在哪里",
            rows = listOf(
                row(
                    entityId = "location.millennial-fair",
                    entityType = "location",
                    canonicalName = "Millennial Fair / 千年祭",
                    aliases = listOf("千年记"),
                    aliasMetadata = listOf(
                        KnowledgeAliasDomain(
                            term = "千年记",
                            entityId = "location.millennial-fair",
                            kind = "display_alias",
                            source = "community",
                        ),
                    ),
                ),
            ),
        )

        assertFalse(result.applied)
        assertEquals("千年记在哪里", result.normalizedQuestion)
    }

    @Test
    fun `applies current game asr variant to canonical term`() {
        val result = normalizer.normalize(
            rawQuestion = "千年记是什么",
            rows = listOf(
                row(
                    entityId = "note.identity",
                    entityType = "note",
                    canonicalName = "Phantasy Star IV / 梦幻之星IV 千年纪的终结",
                    aliases = listOf("千年纪"),
                    aliasMetadata = listOf(asrVariant("千年记", "千年纪", "note.identity")),
                ),
            ),
        )

        assertTrue(result.applied)
        assertEquals("千年纪是什么", result.normalizedQuestion)
        assertEquals("gkp_observed_asr_variant", result.reason)
        assertEquals("note.identity", result.matchedEntityId)
    }

    @Test
    fun `normalizes observed all-pack voice failures through gkp asr aliases`() {
        val cases = listOf(
            NormalizationCase(
                raw = "契合之欲怎么",
                expected = "气合之玉怎么用",
                entityId = "item.vigor-ball",
                canonical = "气合之玉",
                asrTerm = "契合之欲怎么",
                canonicalTerm = "气合之玉怎么用",
            ),
            NormalizationCase(
                raw = "契合之域怎么用",
                expected = "气合之玉怎么用",
                entityId = "item.vigor-ball",
                canonical = "气合之玉",
                asrTerm = "契合之域怎么用",
                canonicalTerm = "气合之玉怎么用",
            ),
            NormalizationCase(
                raw = "契河之域怎么用",
                expected = "气合之玉怎么用",
                entityId = "item.vigor-ball",
                canonical = "气合之玉",
                asrTerm = "契河之域怎么用",
                canonicalTerm = "气合之玉怎么用",
            ),
            NormalizationCase(
                raw = "一凡事不是一",
                expected = "伊凡是不是伊万",
                entityId = "npc.ivan",
                canonical = "Ivan / 伊万",
                asrTerm = "一凡事不是一",
                canonicalTerm = "伊凡是不是伊万",
            ),
            NormalizationCase(
                raw = "一凡是不是亿万",
                expected = "伊凡是不是伊万",
                entityId = "npc.ivan",
                canonical = "Ivan / 伊万",
                asrTerm = "一凡是不是亿万",
                canonicalTerm = "伊凡是不是伊万",
            ),
            NormalizationCase(
                raw = "一凡是不是一",
                expected = "伊凡是不是伊万",
                entityId = "npc.ivan",
                canonical = "Ivan / 伊万",
                asrTerm = "一凡是不是一",
                canonicalTerm = "伊凡是不是伊万",
            ),
            NormalizationCase(
                raw = "伊凡是不是一",
                expected = "伊凡是不是伊万",
                entityId = "npc.ivan",
                canonical = "Ivan / 伊万",
                asrTerm = "伊凡是不是一",
                canonicalTerm = "伊凡是不是伊万",
            ),
            NormalizationCase(
                raw = "伊凡是不是因",
                expected = "伊凡是不是伊万",
                entityId = "npc.ivan",
                canonical = "Ivan / 伊万",
                asrTerm = "伊凡是不是因",
                canonicalTerm = "伊凡是不是伊万",
            ),
            NormalizationCase(
                raw = "一凡是不是因",
                expected = "伊凡是不是伊万",
                entityId = "npc.ivan",
                canonical = "Ivan / 伊万",
                asrTerm = "一凡是不是因",
                canonicalTerm = "伊凡是不是伊万",
            ),
            NormalizationCase(
                raw = "迈尔是",
                expected = "玛尔是谁",
                entityId = "npc.marle",
                canonical = "Marle / 玛尔",
                asrTerm = "迈尔是",
                canonicalTerm = "玛尔是谁",
            ),
            NormalizationCase(
                raw = "纳尔是谁",
                expected = "玛尔是谁",
                entityId = "npc.marle",
                canonical = "Marle / 玛尔",
                asrTerm = "纳尔是谁",
                canonicalTerm = "玛尔是谁",
            ),
            NormalizationCase(
                raw = "麦尔是谁",
                expected = "玛尔是谁",
                entityId = "npc.marle",
                canonical = "Marle / 玛尔",
                asrTerm = "麦尔是谁",
                canonicalTerm = "玛尔是谁",
            ),
            NormalizationCase(
                raw = "时间调战斗怎么理解",
                expected = "时间条战斗怎么理解",
                entityId = "mechanic.atb",
                canonical = "Active Time Battle / ATB 战斗",
                asrTerm = "时间调战斗",
                canonicalTerm = "时间条战斗",
            ),
            NormalizationCase(
                raw = "时间挑战斗怎么理解",
                expected = "时间条战斗怎么理解",
                entityId = "mechanic.atb",
                canonical = "Active Time Battle / ATB 战斗",
                asrTerm = "时间挑战斗",
                canonicalTerm = "时间条战斗",
            ),
            NormalizationCase(
                raw = "无时系统是什么",
                expected = "魔石系统是什么",
                entityId = "mechanic.magicite",
                canonical = "Magicite / 魔石",
                asrTerm = "无时系统是什么",
                canonicalTerm = "魔石系统是什么",
            ),
            NormalizationCase(
                raw = "无石系统是什么",
                expected = "魔石系统是什么",
                entityId = "mechanic.magicite",
                canonical = "Magicite / 魔石",
                asrTerm = "无石系统是什么",
                canonicalTerm = "魔石系统是什么",
            ),
            NormalizationCase(
                raw = "扶食系统是什",
                expected = "魔石系统是什么",
                entityId = "mechanic.magicite",
                canonical = "Magicite / 魔石",
                asrTerm = "扶食系统是什",
                canonicalTerm = "魔石系统是什么",
            ),
            NormalizationCase(
                raw = "我石心统是什么么",
                expected = "魔石系统是什么",
                entityId = "mechanic.magicite",
                canonical = "Magicite / 魔石",
                asrTerm = "我石心统是什么么",
                canonicalTerm = "魔石系统是什么",
            ),
        )

        cases.forEach { case ->
            val result = normalizer.normalize(
                rawQuestion = case.raw,
                rows = listOf(
                    row(
                        entityId = case.entityId,
                        entityType = "mechanic",
                        canonicalName = case.canonical,
                        aliases = listOf(case.canonicalTerm),
                        aliasMetadata = listOf(asrVariant(case.asrTerm, case.canonicalTerm, case.entityId)),
                    )
                ),
            )

            assertTrue("${case.raw} should normalize", result.applied)
            assertEquals(case.expected, result.normalizedQuestion)
            assertEquals(case.canonicalTerm, result.matchedTerm)
            assertEquals(case.entityId, result.matchedEntityId)
            assertTrue(result.reason!!.startsWith("gkp_observed_asr_variant"))
        }
    }

    @Test
    fun `collapses overlapped suffix after full-question asr variant replacement`() {
        val result = normalizer.normalize(
            rawQuestion = "契合之欲怎么用",
            rows = listOf(
                row(
                    entityId = "item.vigor-ball",
                    entityType = "item",
                    canonicalName = "Vigor Ball / 气合之玉",
                    aliases = listOf("气合之玉"),
                    aliasMetadata = listOf(asrVariant("契合之欲怎么", "气合之玉怎么用", "item.vigor-ball")),
                )
            ),
        )

        assertTrue(result.applied)
        assertEquals("气合之玉怎么用", result.normalizedQuestion)
        assertEquals("gkp_observed_asr_variant+overlap_suffix", result.reason)
    }

    @Test
    fun `collapses overlapped suffix for clipped core gameplay asr variant`() {
        val result = normalizer.normalize(
            rawQuestion = "时空之轮主要玩什么",
            rows = listOf(
                row(
                    entityId = "note.core-gameplay",
                    entityType = "note",
                    canonicalName = "核心玩法",
                    aliases = listOf("时空之轮主要玩什么"),
                    aliasMetadata = listOf(asrVariant("时空之轮主要玩什", "时空之轮主要玩什么", "note.core-gameplay")),
                )
            ),
        )

        assertTrue(result.applied)
        assertEquals("时空之轮主要玩什么", result.normalizedQuestion)
        assertEquals("gkp_observed_asr_variant+overlap_suffix", result.reason)
    }

    @Test
    fun `normalizes observed ff6 heshi magicite transcript from gkp metadata`() {
        val result = normalizer.normalize(
            rawQuestion = "何石系统是什么",
            rows = listOf(
                row(
                    entityId = "mechanic.magicite",
                    entityType = "mechanic",
                    canonicalName = "Magicite / 魔石",
                    aliases = listOf("魔石", "魔石系统"),
                    aliasMetadata = listOf(asrVariant("何石系统是什么", "魔石系统是什么", "mechanic.magicite")),
                )
            ),
        )

        assertTrue(result.applied)
        assertEquals("魔石系统是什么", result.normalizedQuestion)
        assertEquals("gkp_observed_asr_variant", result.reason)
        assertEquals("mechanic.magicite", result.matchedEntityId)
    }

    @Test
    fun `normalizes technique homophone span`() {
        val result = normalizer.normalize(
            rawQuestion = "继巧和技能有什么区别",
            rows = listOf(
                row(
                    entityId = "mechanic.techniques",
                    entityType = "mechanic",
                    canonicalName = "Techniques / 技巧",
                    aliases = listOf("技巧"),
                )
            ),
        )

        assertTrue(result.applied)
        assertEquals("技巧和技能有什么区别", result.normalizedQuestion)
        assertEquals("技巧", result.matchedTerm)
        assertEquals("mechanic.techniques", result.matchedEntityId)
        assertEquals("homophone", result.reason)
    }

    @Test
    fun `normalizes observed full question asr suffix after noisy prefix`() {
        val result = normalizer.normalize(
            rawQuestion = "他主要是今天要回复要要是美术生啊人准的是很娘吧同时系统是什么",
            rows = listOf(
                row(
                    entityId = "mechanic.magicite",
                    entityType = "mechanic",
                    canonicalName = "Magicite / 魔石",
                    aliases = listOf("魔石", "魔石系统"),
                    aliasMetadata = listOf(asrVariant("同时系统是什么", "魔石系统是什么", "mechanic.magicite")),
                )
            ),
        )

        assertTrue(result.applied)
        assertEquals("魔石系统是什么", result.normalizedQuestion)
        assertEquals("gkp_observed_asr_variant+noise_prefix", result.reason)
        assertEquals("mechanic.magicite", result.matchedEntityId)
    }

    @Test
    fun `applies full question observed asr from current gkp metadata`() {
        val result = normalizer.normalize(
            rawQuestion = "一凡事不是一",
            rows = listOf(
                row(
                    entityId = "npc.ivan",
                    entityType = "npc",
                    canonicalName = "Ivan / 伊万",
                    aliases = listOf("伊万", "伊凡"),
                    aliasMetadata = listOf(asrVariant("一凡事不是一", "伊凡是不是伊万", "npc.ivan")),
                )
            ),
        )

        assertTrue(result.applied)
        assertEquals("伊凡是不是伊万", result.normalizedQuestion)
        assertEquals("gkp_observed_asr_variant", result.reason)
        assertEquals("npc.ivan", result.matchedEntityId)
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
    fun `normalizes observed Golden Sun Ivan alias equivalence asr corruption`() {
        val result = normalizer.normalize(
            rawQuestion = "依然是不是意外",
            rows = listOf(
                row(
                    entityId = "npc.ivan",
                    entityType = "npc",
                    canonicalName = "Ivan / 伊万",
                    aliases = listOf("伊万", "伊凡"),
                    aliasMetadata = listOf(asrVariant("依然是不是意外", "伊凡是不是伊万", "npc.ivan")),
                )
            ),
        )

        assertTrue(result.applied)
        assertEquals("伊凡是不是伊万", result.normalizedQuestion)
        assertEquals("伊凡是不是伊万", result.matchedTerm)
        assertEquals("npc.ivan", result.matchedEntityId)
        assertEquals("gkp_observed_asr_variant", result.reason)
    }

    @Test
    fun `normalizes observed Golden Sun Ivan dropped subject asr corruption`() {
        val result = normalizer.normalize(
            rawQuestion = "是不是一万",
            rows = listOf(
                row(
                    entityId = "npc.ivan",
                    entityType = "npc",
                    canonicalName = "Ivan / 伊万",
                    aliases = listOf("伊万", "伊凡"),
                    aliasMetadata = listOf(asrVariant("是不是一万", "伊凡是不是伊万", "npc.ivan")),
                )
            ),
        )

        assertTrue(result.applied)
        assertEquals("伊凡是不是伊万", result.normalizedQuestion)
        assertEquals("伊凡是不是伊万", result.matchedTerm)
        assertEquals("npc.ivan", result.matchedEntityId)
        assertEquals("gkp_observed_asr_variant", result.reason)
    }

    @Test
    fun `normalizes observed Golden Sun Ivan yiran one-wan asr corruption`() {
        val result = normalizer.normalize(
            rawQuestion = "依然是不是一万",
            rows = listOf(
                row(
                    entityId = "npc.ivan",
                    entityType = "npc",
                    canonicalName = "Ivan / 伊万",
                    aliases = listOf("伊万", "伊凡"),
                    aliasMetadata = listOf(asrVariant("依然是不是一万", "伊凡是不是伊万", "npc.ivan")),
                )
            ),
        )

        assertTrue(result.applied)
        assertEquals("伊凡是不是伊万", result.normalizedQuestion)
        assertEquals("伊凡是不是伊万", result.matchedTerm)
        assertEquals("npc.ivan", result.matchedEntityId)
        assertEquals("gkp_observed_asr_variant", result.reason)
    }

    @Test
    fun `normalizes observed Golden Sun Ivan one-fan asr corruption`() {
        val result = normalizer.normalize(
            rawQuestion = "一凡是不是意外",
            rows = listOf(
                row(
                    entityId = "npc.ivan",
                    entityType = "npc",
                    canonicalName = "Ivan / 伊万",
                    aliases = listOf("伊万", "伊凡"),
                    aliasMetadata = listOf(asrVariant("一凡是不是意外", "伊凡是不是伊万", "npc.ivan")),
                )
            ),
        )

        assertTrue(result.applied)
        assertEquals("伊凡是不是伊万", result.normalizedQuestion)
        assertEquals("伊凡是不是伊万", result.matchedTerm)
        assertEquals("npc.ivan", result.matchedEntityId)
        assertEquals("gkp_observed_asr_variant", result.reason)
    }

    @Test
    fun `normalizes observed Golden Sun Ivan one-fan one-wan asr corruption`() {
        val result = normalizer.normalize(
            rawQuestion = "一凡是不是一万",
            rows = listOf(
                row(
                    entityId = "npc.ivan",
                    entityType = "npc",
                    canonicalName = "Ivan / 伊万",
                    aliases = listOf("伊万", "伊凡"),
                    aliasMetadata = listOf(asrVariant("一凡是不是一万", "伊凡是不是伊万", "npc.ivan")),
                )
            ),
        )

        assertTrue(result.applied)
        assertEquals("伊凡是不是伊万", result.normalizedQuestion)
        assertEquals("伊凡是不是伊万", result.matchedTerm)
        assertEquals("npc.ivan", result.matchedEntityId)
        assertEquals("gkp_observed_asr_variant", result.reason)
    }

    @Test
    fun `does not globally rewrite broad gameplay dropped prefix without gkp metadata`() {
        val result = normalizer.normalize(
            rawQuestion = "气怎么玩",
            rows = listOf(
                row(
                    entityId = "note.core-gameplay-loop",
                    entityType = "note",
                    canonicalName = "核心玩法与乐趣",
                    aliases = listOf("这游戏怎么玩", "主要玩什么"),
                )
            ),
        )

        assertFalse(result.applied)
        assertEquals("气怎么玩", result.normalizedQuestion)
        assertEquals(null, result.matchedTerm)
        assertEquals(null, result.matchedEntityId)
        assertEquals(null, result.reason)
    }

    @Test
    fun `does not globally rewrite broad gameplay bare how-to-play without gkp metadata`() {
        val result = normalizer.normalize(
            rawQuestion = "怎么玩",
            rows = listOf(
                row(
                    entityId = "note.core-gameplay-loop",
                    entityType = "note",
                    canonicalName = "核心玩法与乐趣",
                    aliases = listOf("这游戏怎么玩", "主要玩什么"),
                )
            ),
        )

        assertFalse(result.applied)
        assertEquals("怎么玩", result.normalizedQuestion)
        assertEquals(null, result.matchedTerm)
        assertEquals(null, result.matchedEntityId)
        assertEquals(null, result.reason)
    }

    @Test
    fun `prefers longer observed gameplay variant over broader suffix variant`() {
        val result = normalizer.normalize(
            rawQuestion = "气怎么玩",
            rows = listOf(
                row(
                    entityId = "note.core-gameplay-loop",
                    entityType = "note",
                    canonicalName = "核心玩法与乐趣",
                    aliases = listOf("这游戏怎么玩", "主要玩什么"),
                    aliasMetadata = listOf(
                        KnowledgeAliasDomain(
                            term = "怎么玩",
                            entityId = "note.core-gameplay-loop",
                            kind = "observed_asr",
                            source = "observed_asr",
                            weight = 0.90,
                            canonicalTerm = "这游戏怎么玩",
                        ),
                        KnowledgeAliasDomain(
                            term = "气怎么玩",
                            entityId = "note.core-gameplay-loop",
                            kind = "observed_asr",
                            source = "observed_asr",
                            weight = 0.70,
                            canonicalTerm = "这游戏怎么玩",
                        ),
                    ),
                )
            ),
        )

        assertTrue(result.applied)
        assertEquals("这游戏怎么玩", result.normalizedQuestion)
        assertEquals("这游戏怎么玩", result.matchedTerm)
        assertEquals("note.core-gameplay-loop", result.matchedEntityId)
        assertEquals("gkp_observed_asr_variant", result.reason)
    }

    @Test
    fun `does not apply broad gameplay suffix variant inside more specific question`() {
        val result = normalizer.normalize(
            rawQuestion = "新手怎么玩",
            rows = listOf(
                row(
                    entityId = "note.core-gameplay-loop",
                    entityType = "note",
                    canonicalName = "核心玩法与乐趣",
                    aliases = listOf("这游戏怎么玩", "主要玩什么"),
                    aliasMetadata = listOf(
                        KnowledgeAliasDomain(
                            term = "怎么玩",
                            entityId = "note.core-gameplay-loop",
                            kind = "observed_asr",
                            source = "observed_asr",
                            weight = 0.90,
                            canonicalTerm = "这游戏怎么玩",
                        ),
                    ),
                ),
                row(
                    entityId = "strategy.beginner-first-hour",
                    entityType = "strategy",
                    canonicalName = "新手前期路线",
                    aliases = listOf("新手怎么玩"),
                )
            ),
        )

        assertFalse(result.applied)
        assertEquals("新手怎么玩", result.normalizedQuestion)
        assertEquals(null, result.matchedTerm)
        assertEquals(null, result.matchedEntityId)
        assertEquals(null, result.reason)
    }

    @Test
    fun `normalizes observed Shining Force kraken asr corruption`() {
        val result = normalizer.normalize(
            rawQuestion = "赫拉很怎么过",
            rows = listOf(
                row(
                    entityId = "boss.kraken",
                    entityType = "boss",
                    canonicalName = "Kraken / 克拉肯",
                    aliases = listOf("克拉肯", "海妖"),
                    aliasMetadata = listOf(asrVariant("赫拉很", "克拉肯", "boss.kraken")),
                )
            ),
        )

        assertTrue(result.applied)
        assertEquals("克拉肯怎么过", result.normalizedQuestion)
        assertEquals("克拉肯", result.matchedTerm)
        assertEquals("boss.kraken", result.matchedEntityId)
        assertEquals("gkp_observed_asr_variant", result.reason)
    }

    @Test
    fun `normalizes observed Shining Force kraken inserted ding asr corruption`() {
        val result = normalizer.normalize(
            rawQuestion = "克拉肯定怎么过",
            rows = listOf(
                row(
                    entityId = "boss.kraken",
                    entityType = "boss",
                    canonicalName = "Kraken / 克拉肯",
                    aliases = listOf("克拉肯", "海妖"),
                    aliasMetadata = listOf(asrVariant("克拉肯定", "克拉肯", "boss.kraken")),
                )
            ),
        )

        assertTrue(result.applied)
        assertEquals("克拉肯怎么过", result.normalizedQuestion)
        assertEquals("克拉肯", result.matchedTerm)
        assertEquals("boss.kraken", result.matchedEntityId)
        assertEquals("gkp_observed_asr_variant", result.reason)
    }

    @Test
    fun `normalizes observed Shining Force kraken he-la asr corruption`() {
        val result = normalizer.normalize(
            rawQuestion = "赫拉肯怎么过",
            rows = listOf(
                row(
                    entityId = "boss.kraken",
                    entityType = "boss",
                    canonicalName = "Kraken / 克拉肯",
                    aliases = listOf("克拉肯", "海妖"),
                    aliasMetadata = listOf(asrVariant("赫拉肯", "克拉肯", "boss.kraken")),
                )
            ),
        )

        assertTrue(result.applied)
        assertEquals("克拉肯怎么过", result.normalizedQuestion)
        assertEquals("克拉肯", result.matchedTerm)
        assertEquals("boss.kraken", result.matchedEntityId)
        assertEquals("gkp_observed_asr_variant", result.reason)
    }

    @Test
    fun `normalizes observed Chrono Trigger Marle asr corruption`() {
        val result = normalizer.normalize(
            rawQuestion = "那儿是谁",
            rows = listOf(
                row(
                    entityId = "npc.marle",
                    entityType = "npc",
                    canonicalName = "Marle / 玛尔",
                    aliases = listOf("玛尔", "马尔"),
                    aliasMetadata = listOf(asrVariant("那儿是谁", "玛尔是谁", "npc.marle")),
                )
            ),
        )

        assertTrue(result.applied)
        assertEquals("玛尔是谁", result.normalizedQuestion)
        assertEquals("玛尔是谁", result.matchedTerm)
        assertEquals("npc.marle", result.matchedEntityId)
        assertEquals("gkp_observed_asr_variant", result.reason)
    }

    @Test
    fun `normalizes observed multi pack mechanic asr corruptions`() {
        listOf(
            ObservedQuestionRewriteCase(
                rawQuestion = "组合计要不要一开始研究",
                normalizedQuestion = "组合技要不要一开始研究",
                matchedTerm = "组合技",
                entityId = "mechanic.combo-attacks",
                canonicalName = "Combination attacks / 组合技",
                aliases = listOf("组合技", "组合攻击", "组合击", "连携"),
                asrTerm = "组合计",
                canonicalTerm = "组合技",
            ),
            ObservedQuestionRewriteCase(
                rawQuestion = "组合技药不要一开始研究",
                normalizedQuestion = "组合技要不要一开始研究",
                matchedTerm = "组合技要不要一开始研究",
                entityId = "mechanic.combo-attacks",
                canonicalName = "Combination attacks / 组合技",
                aliases = listOf("组合技", "组合攻击", "组合击", "连携"),
                asrTerm = "组合技药不要一开始研究",
                canonicalTerm = "组合技要不要一开始研究",
            ),
            ObservedQuestionRewriteCase(
                rawQuestion = "使婚姻范围有什么用",
                normalizedQuestion = "指挥范围有什么用",
                matchedTerm = "指挥范围",
                entityId = "mechanic.command-range",
                canonicalName = "Command range / 指挥范围",
                aliases = listOf("指挥范围", "统帅范围", "光环范围"),
                asrTerm = "使婚姻范围",
                canonicalTerm = "指挥范围",
            ),
            ObservedQuestionRewriteCase(
                rawQuestion = "怪兽和磨石有什么关系",
                normalizedQuestion = "幻兽和魔石有什么关系",
                matchedTerm = "幻兽和魔石有什么关系",
                entityId = "mechanic.espers",
                canonicalName = "Espers / 幻兽",
                aliases = listOf("幻兽", "幻受"),
                asrTerm = "怪兽和磨石有什么关系",
                canonicalTerm = "幻兽和魔石有什么关系",
            ),
            ObservedQuestionRewriteCase(
                rawQuestion = "万寿和磨石有什么关系",
                normalizedQuestion = "幻兽和魔石有什么关系",
                matchedTerm = "幻兽和魔石有什么关系",
                entityId = "mechanic.espers",
                canonicalName = "Espers / 幻兽",
                aliases = listOf("幻兽", "幻受"),
                asrTerm = "万寿和磨石有什么关系",
                canonicalTerm = "幻兽和魔石有什么关系",
            ),
            ObservedQuestionRewriteCase(
                rawQuestion = "师间旅行会不会巨头",
                normalizedQuestion = "时间旅行会不会剧透",
                matchedTerm = "时间旅行会不会剧透",
                entityId = "mechanic.time-travel",
                canonicalName = "Time travel / 时间旅行",
                aliases = listOf("时间旅行", "穿越时间", "时代旅行"),
                asrTerm = "师间旅行会不会巨头",
                canonicalTerm = "时间旅行会不会剧透",
            ),
            ObservedQuestionRewriteCase(
                rawQuestion = "时间旅行会不会巨头",
                normalizedQuestion = "时间旅行会不会剧透",
                matchedTerm = "时间旅行会不会剧透",
                entityId = "mechanic.time-travel",
                canonicalName = "Time travel / 时间旅行",
                aliases = listOf("时间旅行", "穿越时间", "时代旅行"),
                asrTerm = "时间旅行会不会巨头",
                canonicalTerm = "时间旅行会不会剧透",
            ),
            ObservedQuestionRewriteCase(
                rawQuestion = "精神密是什么",
                normalizedQuestion = "精神力是什么",
                matchedTerm = "精神力",
                entityId = "mechanic.psynergy",
                canonicalName = "Psynergy / 精神力",
                aliases = listOf("精神力", "念力", "精神利", "精神里"),
                asrTerm = "精神密",
                canonicalTerm = "精神力",
            ),
        ).forEach { case ->
            val result = normalizer.normalize(
                rawQuestion = case.rawQuestion,
                rows = listOf(
                    row(
                        entityId = case.entityId,
                        entityType = "mechanic",
                        canonicalName = case.canonicalName,
                        aliases = case.aliases,
                        aliasMetadata = listOf(asrVariant(case.asrTerm, case.canonicalTerm, case.entityId)),
                    )
                ),
            )

            assertTrue("${case.rawQuestion} should be normalized", result.applied)
            assertEquals(case.normalizedQuestion, result.normalizedQuestion)
            assertEquals(case.matchedTerm, result.matchedTerm)
            assertEquals(case.entityId, result.matchedEntityId)
            assertEquals("gkp_observed_asr_variant", result.reason)
        }
    }

    @Test
    fun `normalizes observed question rewrite with trailing punctuation`() {
        val result = normalizer.normalize(
            rawQuestion = "组合计要不要一开始研究？",
            rows = listOf(
                row(
                    entityId = "mechanic.combo-attacks",
                    entityType = "mechanic",
                    canonicalName = "Combination attacks / 组合技",
                    aliases = listOf("组合技", "组合攻击", "组合击", "连携"),
                    aliasMetadata = listOf(asrVariant("组合计", "组合技", "mechanic.combo-attacks")),
                )
            ),
        )

        assertTrue(result.applied)
        assertEquals("组合技要不要一开始研究", result.normalizedQuestion)
        assertEquals("组合技", result.matchedTerm)
        assertEquals("mechanic.combo-attacks", result.matchedEntityId)
        assertEquals("gkp_observed_asr_variant", result.reason)
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

    private data class ObservedQuestionRewriteCase(
        val rawQuestion: String,
        val normalizedQuestion: String,
        val matchedTerm: String,
        val entityId: String,
        val canonicalName: String,
        val aliases: List<String>,
        val asrTerm: String,
        val canonicalTerm: String,
    )

    private data class NormalizationCase(
        val raw: String,
        val expected: String,
        val entityId: String,
        val canonical: String,
        val asrTerm: String,
        val canonicalTerm: String,
    )

    private fun row(
        entityId: String,
        entityType: String = "npc",
        canonicalName: String,
        aliases: List<String>,
        aliasMetadata: List<KnowledgeAliasDomain> = emptyList(),
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
        aliasMetadata = aliasMetadata,
    )

    private fun asrVariant(
        term: String,
        canonicalTerm: String,
        entityId: String,
    ): KnowledgeAliasDomain =
        KnowledgeAliasDomain(
            term = term,
            entityId = entityId,
            kind = "observed_asr",
            source = "observed_asr",
            weight = 0.72,
            canonicalTerm = canonicalTerm,
        )
}
