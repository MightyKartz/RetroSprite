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

    @Test
    fun `extracts localized boss and enemy names for ASR biasing`() {
        val rows = listOf(
            chunk(
                entityId = "boss.kraken",
                entityType = "boss",
                canonicalName = "Kraken / 克拉肯",
                aliases = listOf("Kraken", "克拉肯", "海妖"),
            ),
            chunk(
                entityId = "enemy.chaos-wizard",
                entityType = "enemy",
                canonicalName = "Chaos Wizard / 混沌法师",
                aliases = listOf("Chaos Wizard", "混沌法师", "高级法师"),
            ),
        )

        val profile = GkpAsrHotwordExtractor().extract(
            gameId = "shining_force_ii_md",
            packVersion = "0.3.0",
            rows = rows,
        )

        val terms = profile.normalizedEntries.associateBy { it.term }
        assertTrue(terms.getValue("克拉肯").score >= GkpAsrHotwordExtractor.PATCH_NAME_SCORE)
        assertTrue(terms.getValue("混沌法师").score >= GkpAsrHotwordExtractor.PATCH_NAME_SCORE)
        assertTrue(terms.getValue("克拉肯").score > terms.getValue("Kraken").score)
    }

    @Test
    fun `template question terms survive hotword cap for real spoken item names`() {
        val fillerRows = (0 until 170).map { index ->
            chunk(
                entityId = "npc.filler-$index",
                entityType = "npc",
                canonicalName = "一一甲$index",
                aliases = listOf("一一甲$index"),
            )
        }
        val rows = fillerRows + listOf(
            chunk(
                entityId = "item.vigor-ball",
                entityType = "item",
                canonicalName = "活力球 / Vigor Ball",
                aliases = listOf("活力球", "气合之玉"),
                answerTemplates = listOf(
                    """{"question_patterns":["气合之玉怎么用","气合之玉给谁"],"answer":"..."}""",
                ),
            ),
            chunk(
                entityId = "item.mithril",
                entityType = "item",
                canonicalName = "Mithril / 秘银",
                aliases = listOf("秘银", "米斯里鲁银"),
                answerTemplates = listOf(
                    """{"question_patterns":["米斯里鲁银有什么用","米斯里鲁银在哪里"],"answer":"..."}""",
                ),
            ),
        )

        val profile = GkpAsrHotwordExtractor().extract(
            gameId = "shining_force_ii_md",
            packVersion = "0.3.0",
            rows = rows,
        )

        val terms = profile.normalizedEntries.associateBy { it.term }
        assertTrue(terms.containsKey("气合之玉"))
        assertTrue(terms.containsKey("米斯里鲁银"))
        assertTrue(terms.getValue("气合之玉").score > GkpAsrHotwordExtractor.PATCH_NAME_SCORE)
        assertTrue(terms.getValue("米斯里鲁银").score > GkpAsrHotwordExtractor.PATCH_NAME_SCORE)
    }

    @Test
    fun `extracts phase one retro jrpg srpg localized names above english anchors`() {
        val fixtures = listOf(
            HotwordFixture(
                gameId = "golden_sun_gba",
                rows = listOf(
                    hotwordRow("npc.isaac", "npc", "Isaac / 伊萨克", "Isaac", "伊萨克"),
                    hotwordRow("npc.mia", "npc", "Mia / 米娅", "Mia", "米娅"),
                    hotwordRow("item.herb", "item", "Herb / 药草", "Herb", "药草"),
                    hotwordRow("location.venus-lighthouse", "location", "Venus Lighthouse / 金星灯塔", "Venus Lighthouse", "金星灯塔"),
                    hotwordRow("boss.saturos", "boss", "Saturos / 萨丢罗斯", "Saturos", "萨丢罗斯"),
                ),
            ),
            HotwordFixture(
                gameId = "phantasy_star_iv_md",
                rows = listOf(
                    hotwordRow("npc.chaz", "npc", "Chaz / 查兹", "Chaz", "查兹"),
                    hotwordRow("npc.alys", "npc", "Alys / 艾莉丝", "Alys", "艾莉丝"),
                    hotwordRow("item.monomate", "item", "Monomate / 单体药", "Monomate", "单体药"),
                    hotwordRow("location.motavia", "location", "Motavia / 莫塔维亚", "Motavia", "莫塔维亚"),
                    hotwordRow("boss.zio", "boss", "Zio / 吉奥", "Zio", "吉奥"),
                ),
            ),
            HotwordFixture(
                gameId = "langrisser_ii_md",
                rows = listOf(
                    hotwordRow("npc.elwin", "npc", "Elwin / 艾尔文", "Elwin", "艾尔文"),
                    hotwordRow("npc.leon", "npc", "Leon / 利昂", "Leon", "利昂"),
                    hotwordRow("item.langrisser", "item", "Langrisser / 圣剑兰古利萨", "Langrisser", "圣剑兰古利萨"),
                    hotwordRow("location.bardia", "location", "Bardia Castle / 巴尔迪亚城", "Bardia Castle", "巴尔迪亚城"),
                    hotwordRow("boss.egbert", "boss", "Egbert / 艾格贝尔特", "Egbert", "艾格贝尔特"),
                ),
            ),
            HotwordFixture(
                gameId = "chrono_trigger_snes",
                rows = listOf(
                    hotwordRow("npc.crono", "npc", "Crono / 克罗诺", "Crono", "克罗诺"),
                    hotwordRow("npc.marle", "npc", "Marle / 玛尔", "Marle", "玛尔"),
                    hotwordRow("item.tonic", "item", "Tonic / 回复药", "Tonic", "回复药"),
                    hotwordRow("location.millennial-fair", "location", "Millennial Fair / 千年祭", "Millennial Fair", "千年祭"),
                    hotwordRow("boss.dragon-tank", "boss", "Dragon Tank / 龙战车", "Dragon Tank", "龙战车"),
                ),
            ),
            HotwordFixture(
                gameId = "final_fantasy_vi_snes",
                rows = listOf(
                    hotwordRow("npc.terra", "npc", "Terra / 蒂娜", "Terra", "蒂娜"),
                    hotwordRow("npc.locke", "npc", "Locke / 洛克", "Locke", "洛克"),
                    hotwordRow("item.magicite-bucket", "item", "Magicite / 魔石", "Magicite", "魔石"),
                    hotwordRow("location.narshe", "location", "Narshe / 纳尔谢", "Narshe", "纳尔谢"),
                    hotwordRow("boss.kefka", "boss", "Kefka / 凯夫卡", "Kefka", "凯夫卡"),
                ),
            ),
        )

        fixtures.forEach { fixture ->
            val rows = fixture.rows.map { row ->
                chunk(
                    gameId = fixture.gameId,
                    entityId = row.entityId,
                    entityType = row.entityType,
                    canonicalName = row.canonicalName,
                    aliases = listOf(row.englishAlias, row.localizedAlias),
                )
            }
            val profile = GkpAsrHotwordExtractor().extract(
                gameId = fixture.gameId,
                packVersion = "0.1.0",
                rows = rows,
            )
            val terms = profile.normalizedEntries.associateBy { it.term }

            fixture.rows.forEach { row ->
                assertTrue("${fixture.gameId} missing ${row.localizedAlias}", terms.containsKey(row.localizedAlias))
                assertTrue(
                    "${fixture.gameId} ${row.localizedAlias} should be patch-name boosted",
                    terms.getValue(row.localizedAlias).score >= GkpAsrHotwordExtractor.PATCH_NAME_SCORE,
                )
                assertTrue(
                    "${fixture.gameId} ${row.localizedAlias} should outrank ${row.englishAlias}",
                    terms.getValue(row.localizedAlias).score > terms.getValue(row.englishAlias).score,
                )
            }
        }
    }

    private data class HotwordFixture(
        val gameId: String,
        val rows: List<HotwordRow>,
    )

    private data class HotwordRow(
        val entityId: String,
        val entityType: String,
        val canonicalName: String,
        val englishAlias: String,
        val localizedAlias: String,
    )

    private fun hotwordRow(
        entityId: String,
        entityType: String,
        canonicalName: String,
        englishAlias: String,
        localizedAlias: String,
    ): HotwordRow = HotwordRow(
        entityId = entityId,
        entityType = entityType,
        canonicalName = canonicalName,
        englishAlias = englishAlias,
        localizedAlias = localizedAlias,
    )

    private fun chunk(
        gameId: String = "shining_force_ii_md",
        entityId: String,
        entityType: String,
        canonicalName: String,
        aliases: List<String>,
        answerTemplates: List<String> = emptyList(),
    ): KnowledgeChunkDomain =
        KnowledgeChunkDomain(
            id = 0L,
            gameId = gameId,
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
