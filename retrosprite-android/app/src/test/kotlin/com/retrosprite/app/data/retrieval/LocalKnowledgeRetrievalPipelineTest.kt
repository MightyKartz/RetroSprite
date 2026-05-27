package com.retrosprite.app.data.retrieval

import com.retrosprite.app.data.models.KnowledgeChunkDomain
import com.retrosprite.app.data.repository.KnowledgeRepository
import com.retrosprite.app.domain.models.RetrievalQuery
import com.retrosprite.app.domain.models.SpoilerLevel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalKnowledgeRetrievalPipelineTest {

    private val rows = listOf(
        chunk(
            entityId = "mechanic.tile-merge",
            canonicalName = "方块合并",
            aliases = listOf("合并", "两个 2", "翻倍"),
            descriptionShort = "两个相邻且数字相同的方块会合并成一个数值翻倍的方块。",
            spoilerLevel = "none",
            answerTemplates = listOf(
                """{"template_id":"template.merge","language":"zh","question_patterns":["两个2怎么合并","怎么合并"],"answer":"两个相同数字滑到一起会合并并翻倍。","source_refs":["sample.2048.rules"],"spoiler_level":"none"}"""
            ),
        ),
        chunk(
            entityId = "strategy.snake-order",
            entityType = "strategy",
            canonicalName = "蛇形排序",
            aliases = listOf("蛇形", "后期摆法"),
            descriptionShort = "把大数字沿边缘按大小顺序排列，可以让合并路径更稳定。",
            spoilerLevel = "medium",
            progressGate = "stable_corner",
            sourceRefs = listOf("sample.2048.strategy"),
        ),
        chunk(
            entityId = "strategy.keep-space",
            entityType = "strategy",
            canonicalName = "保留空格",
            aliases = listOf("空格", "快满了"),
            descriptionShort = "保持空格比追求一次大合并更重要。",
            spoilerLevel = "light",
            sourceRefs = listOf("sample.2048.strategy"),
        ),
    )

    @Test
    fun `normalizes question consistently`() = runTest {
        val pipeline = LocalKnowledgeRetrievalPipeline(FakeKnowledgeRepository(rows))

        assertEquals("马里奥 在哪", pipeline.normalizeQuestion("  马里奥  在哪 ", "zh"))
        assertEquals("where is mario", pipeline.normalizeQuestion(" WHERE   is Mario ", "en"))
    }

    @Test
    fun `returns template match before generic matches`() = runTest {
        val pipeline = LocalKnowledgeRetrievalPipeline(FakeKnowledgeRepository(rows))

        val results = pipeline.retrieve(query("两个2怎么合并"))

        assertEquals("mechanic.tile-merge", results.first().entityId)
        assertEquals("两个相同数字滑到一起会合并并翻倍。", results.first().evidence.first().snippet)
        assertEquals("sample.2048.rules", results.first().evidence.first().sourceId)
    }

    @Test
    fun `filters medium spoiler when tolerance is light`() = runTest {
        val pipeline = LocalKnowledgeRetrievalPipeline(FakeKnowledgeRepository(rows))

        val results = pipeline.retrieve(query("蛇形后期怎么摆", spoilerLevel = SpoilerLevel.LIGHT))

        assertTrue(results.none { it.entityId == "strategy.snake-order" })
    }

    @Test
    fun `allows medium spoiler when tolerance and progress gate match`() = runTest {
        val pipeline = LocalKnowledgeRetrievalPipeline(FakeKnowledgeRepository(rows))

        val results = pipeline.retrieve(
            query(
                raw = "蛇形后期怎么摆",
                spoilerLevel = SpoilerLevel.CLEAR,
                progressGate = "stable_corner",
            )
        )

        assertEquals("strategy.snake-order", results.first().entityId)
    }

    @Test
    fun `falls through to fts search when aliases do not match directly`() = runTest {
        val pipeline = LocalKnowledgeRetrievalPipeline(FakeKnowledgeRepository(rows))

        val results = pipeline.retrieve(query("棋盘快满"))

        assertEquals("strategy.keep-space", results.first().entityId)
    }

    @Test
    fun `short circuits when game id or query is empty`() = runTest {
        val pipeline = LocalKnowledgeRetrievalPipeline(FakeKnowledgeRepository(rows))

        assertTrue(pipeline.retrieve(query("合并", gameId = null)).isEmpty())
        assertTrue(pipeline.retrieve(query("   ")).isEmpty())
    }

    @Test
    fun `intent boosts broad gameplay questions toward overview rows`() = runTest {
        val pipeline = LocalKnowledgeRetrievalPipeline(
            FakeKnowledgeRepository(
                listOf(
                    chunk(
                        entityId = "mechanic.basic-controls",
                        entityType = "mechanic",
                        canonicalName = "基础操作",
                        aliases = listOf("游戏", "怎么玩"),
                        descriptionShort = "菜单和操作是基础。",
                        spoilerLevel = "none",
                    ),
                    chunk(
                        entityId = "note.core-gameplay-loop",
                        entityType = "note",
                        canonicalName = "核心玩法",
                        aliases = listOf("这游戏怎么玩", "主要玩什么", "好玩在哪"),
                        descriptionShort = "核心是推进剧情、组建队伍，并在网格回合战斗中打赢战斗。",
                        spoilerLevel = "none",
                        answerTemplates = listOf(
                            """{"intent":"game_overview","question_patterns":["这游戏怎么玩","主要玩什么"],"answer":"主要玩剧情推进、队伍培养和网格回合制战斗。","source_refs":["sf2.official_overview"],"spoiler_level":"none"}"""
                        ),
                    ),
                )
            )
        )

        val results = pipeline.retrieve(query("这游戏怎么玩"))

        assertEquals("note.core-gameplay-loop", results.first().entityId)
        assertEquals("主要玩剧情推进、队伍培养和网格回合制战斗。", results.first().evidence.first().snippet)
    }

    @Test
    fun `matches nearby gameplay template question through template documents`() = runTest {
        val pipeline = LocalKnowledgeRetrievalPipeline(
            FakeKnowledgeRepository(
                listOf(
                    chunk(
                        entityId = "mechanic.basic-controls",
                        entityType = "mechanic",
                        canonicalName = "基础操作",
                        aliases = listOf("游戏", "怎么玩"),
                        descriptionShort = "菜单和操作是基础。",
                        spoilerLevel = "none",
                    ),
                    chunk(
                        entityId = "note.core-gameplay-loop",
                        entityType = "note",
                        canonicalName = "核心玩法",
                        aliases = listOf("这游戏怎么玩", "主要玩什么", "好玩在哪"),
                        descriptionShort = "核心是推进剧情、组建队伍，并在网格回合战斗中打赢战斗。",
                        spoilerLevel = "none",
                        sourceRefs = listOf("sf2.official_overview"),
                        answerTemplates = listOf(
                            """{"template_id":"template.sf2.core-gameplay.zh","intent":"game_overview","question_patterns":["这游戏怎么玩","主要玩什么","核心玩法是什么"],"answer":"主要玩剧情推进、队伍培养和网格回合制战斗。","source_refs":["sf2.official_overview"],"spoiler_level":"none"}"""
                        ),
                    ),
                )
            )
        )

        val results = pipeline.retrieve(query("这游戏玩什么"))

        assertEquals("note.core-gameplay-loop", results.first().entityId)
        assertEquals("主要玩剧情推进、队伍培养和网格回合制战斗。", results.first().evidence.first().snippet)
        assertEquals("sf2.official_overview", results.first().evidence.first().sourceId)

        val colloquialResults = pipeline.retrieve(query("这个游戏主要是干嘛的"))

        assertEquals("note.core-gameplay-loop", colloquialResults.first().entityId)
        assertEquals("主要玩剧情推进、队伍培养和网格回合制战斗。", colloquialResults.first().evidence.first().snippet)
    }

    @Test
    fun `template document matching respects progress gate and spoiler tolerance`() = runTest {
        val pipeline = LocalKnowledgeRetrievalPipeline(
            FakeKnowledgeRepository(
                listOf(
                    chunk(
                        entityId = "location.secret-route",
                        entityType = "location",
                        canonicalName = "隐藏路线",
                        aliases = listOf("隐藏路线"),
                        descriptionShort = "隐藏路线需要到中期后再提示。",
                        spoilerLevel = "medium",
                        progressGate = "mid_game",
                        sourceRefs = listOf("sample.secret"),
                        answerTemplates = listOf(
                            """{"template_id":"template.secret.route","intent":"location","question_patterns":["隐藏路线在哪","秘密路线在哪"],"answer":"直接从中期城镇向东走。","source_refs":["sample.secret"],"spoiler_level":"medium"}"""
                        ),
                    ),
                )
            )
        )

        assertTrue(
            pipeline.retrieve(
                query("秘密路线在哪", spoilerLevel = SpoilerLevel.LIGHT)
            ).isEmpty()
        )
        assertTrue(
            pipeline.retrieve(
                query("秘密路线在哪", spoilerLevel = SpoilerLevel.CLEAR)
            ).isEmpty()
        )
        assertEquals(
            "location.secret-route",
            pipeline.retrieve(
                query(
                    "秘密路线在哪",
                    spoilerLevel = SpoilerLevel.CLEAR,
                    progressGate = "mid_game",
                )
            ).first().entityId,
        )
    }

    @Test
    fun `known low spoiler entity outside progress gate returns scoped fallback`() = runTest {
        val pipeline = LocalKnowledgeRetrievalPipeline(
            FakeKnowledgeRepository(
                listOf(
                    chunk(
                        entityId = "mechanic.magicite",
                        entityType = "mechanic",
                        canonicalName = "Magicite / 魔石",
                        aliases = listOf("魔石", "魔石系统"),
                        descriptionShort = "魔石会影响魔法学习和成长，是中前期后角色培养的关键系统。",
                        spoilerLevel = "light",
                        progressGate = "early_game",
                        sourceRefs = listOf("ff6.magicite_wiki"),
                    ),
                )
            )
        )

        val results = pipeline.retrieve(query("怎么获得魔石"))

        assertFalse("known low-spoiler entity should produce a scoped fallback", results.isEmpty())
        assertEquals("mechanic.magicite", results.first().entityId)
        assertTrue(results.first().evidence.first().snippet.contains("我找到你提到的"))
        assertTrue(results.first().evidence.first().snippet.contains("Magicite / 魔石"))
        assertTrue(results.first().evidence.first().snippet.contains("魔石会影响魔法学习和成长"))
        assertEquals("ff6.magicite_wiki", results.first().evidence.first().sourceId)
    }

    @Test
    fun `known medium spoiler entity outside progress gate stays hidden under light tolerance`() =
        runTest {
            val pipeline = LocalKnowledgeRetrievalPipeline(
                FakeKnowledgeRepository(
                    listOf(
                        chunk(
                            entityId = "location.secret-route",
                            entityType = "location",
                            canonicalName = "隐藏路线",
                            aliases = listOf("隐藏路线", "秘密路线"),
                            descriptionShort = "隐藏路线需要到中期后再提示。",
                            spoilerLevel = "medium",
                            progressGate = "mid_game",
                            sourceRefs = listOf("sample.secret"),
                        ),
                    )
                )
            )

            val results = pipeline.retrieve(query("隐藏路线怎么走", spoilerLevel = SpoilerLevel.LIGHT))

            assertTrue(results.isEmpty())
        }

    @Test
    fun `exhaustive list request does not use scoped entity fallback`() = runTest {
        val pipeline = LocalKnowledgeRetrievalPipeline(
            FakeKnowledgeRepository(
                listOf(
                    chunk(
                        entityId = "mechanic.combo-attacks",
                        entityType = "mechanic",
                        canonicalName = "Combination attacks / 组合技",
                        aliases = listOf("组合技", "组合攻击", "连携"),
                        descriptionShort = "组合技由特定行动组合触发，不必开局就背完整表。",
                        spoilerLevel = "light",
                        progressGate = "early_game",
                        sourceRefs = listOf("ps4.project_notes"),
                    ),
                )
            )
        )

        val results = pipeline.retrieve(query("列出全部组合技表"))

        assertTrue(results.isEmpty())
    }

    @Test
    fun `intent boost can outrank generic alias matches when template is absent`() = runTest {
        val pipeline = LocalKnowledgeRetrievalPipeline(
            FakeKnowledgeRepository(
                listOf(
                    chunk(
                        entityId = "mechanic.generic-how-to",
                        entityType = "mechanic",
                        canonicalName = "基础怎么玩",
                        aliases = listOf("怎么玩"),
                        descriptionShort = "这是一个很宽泛的操作说明。",
                        spoilerLevel = "none",
                    ),
                    chunk(
                        entityId = "note.core-gameplay-loop",
                        entityType = "note",
                        canonicalName = "核心玩法",
                        aliases = listOf("主要玩什么", "核心玩法"),
                        descriptionShort = "这游戏怎么玩：核心是剧情推进、队伍培养和网格战斗。",
                        spoilerLevel = "none",
                    ),
                )
            )
        )

        val results = pipeline.retrieve(query("这游戏怎么玩"))

        assertEquals("note.core-gameplay-loop", results.first().entityId)
    }

    @Test
    fun `intent boosts leveling questions toward leveling rows`() = runTest {
        val pipeline = LocalKnowledgeRetrievalPipeline(
            FakeKnowledgeRepository(
                listOf(
                    chunk(
                        entityId = "strategy.safe-formation",
                        entityType = "strategy",
                        canonicalName = "稳健打法",
                        aliases = listOf("怎么玩", "打法"),
                        descriptionShort = "抱团推进更稳。",
                        spoilerLevel = "none",
                    ),
                    chunk(
                        entityId = "mechanic.leveling-general",
                        entityType = "mechanic",
                        canonicalName = "经验与练级",
                        aliases = listOf("经验高", "练级快", "升级快"),
                        descriptionShort = "让低等级角色补刀，治疗和辅助行动也能拿经验。",
                        spoilerLevel = "none",
                        answerTemplates = listOf(
                            """{"intent":"leveling","question_patterns":["怎么玩经验高","怎么练级快"],"answer":"让低等级角色补刀，治疗和辅助行动也能拿经验。","source_refs":["sf2.project_mechanics"],"spoiler_level":"none"}"""
                        ),
                    ),
                )
            )
        )

        val results = pipeline.retrieve(query("怎么玩经验高"))

        assertEquals("mechanic.leveling-general", results.first().entityId)
    }

    @Test
    fun `entity anchored noisy usage tail matches the entity usage template`() = runTest {
        val pipeline = LocalKnowledgeRetrievalPipeline(
            FakeKnowledgeRepository(
                listOf(
                    chunk(
                        entityId = "item.vigor-ball",
                        entityType = "item",
                        canonicalName = "Vigor Ball / 活力球",
                        aliases = listOf("活力球", "气合之玉"),
                        descriptionShort = "Vigor Ball 可让 Priest 系角色转成 Master Monk。",
                        spoilerLevel = "medium",
                        sourceRefs = listOf("sf2.promotion"),
                        answerTemplates = listOf(
                            """{"template_id":"template.sf2.vigor-ball.zh","intent":"usage","question_patterns":["气合之玉怎么用","气合之玉给谁"],"answer":"Vigor Ball（活力球/气合之玉）给 Priest 系角色用于转 Master Monk。","source_refs":["sf2.promotion"],"spoiler_level":"light"}"""
                        ),
                    ),
                )
            )
        )

        val results = pipeline.retrieve(query("气合之玉怎么又"))

        assertEquals("item.vigor-ball", results.first().entityId)
        assertEquals(
            "Vigor Ball（活力球/气合之玉）给 Priest 系角色用于转 Master Monk。",
            results.first().evidence.first().snippet,
        )
    }

    @Test
    fun `multiple templates on same entity choose the matching question pattern`() = runTest {
        val pipeline = LocalKnowledgeRetrievalPipeline(
            FakeKnowledgeRepository(
                listOf(
                    chunk(
                        entityId = "strategy.team-build-general",
                        entityType = "strategy",
                        canonicalName = "通用角色培养原则",
                        aliases = listOf(
                            "现在哪些角色适合培养",
                            "开局哪些角色值得练",
                            "哪些角色值得练",
                            "直接告诉我强力角色名单",
                        ),
                        descriptionShort = "如果还没提供进度，默认给低剧透培养原则。",
                        spoilerLevel = "light",
                        sourceRefs = listOf("sf2.project_mechanics"),
                        answerTemplates = listOf(
                            """{"template_id":"template.sf2.team-build-early.zh","intent":"team_build","question_patterns":["开局哪些角色值得练"],"answer_light":"开局低剧透推荐。","spoiler_light":"light","source_refs":["sf2.project_mechanics"]}""",
                            """{"template_id":"template.sf2.team-build-direct-roster.zh","intent":"team_build","question_patterns":["直接告诉我强力角色名单"],"answer_light":"这会涉及后期加入角色。","spoiler_light":"light","source_refs":["sf2.project_mechanics"]}""",
                            """{"template_id":"template.sf2.team-build-general.zh","intent":"team_build","question_patterns":["现在哪些角色适合培养","哪些角色值得练"],"answer_light":"通用原则：先不列全角色名单。","spoiler_light":"light","source_refs":["sf2.project_mechanics"]}""",
                        ),
                    ),
                )
            )
        )

        assertEquals(
            "通用原则：先不列全角色名单。",
            pipeline.retrieve(query("现在哪些角色适合培养")).first().evidence.first().snippet,
        )
        assertEquals(
            "开局低剧透推荐。",
            pipeline.retrieve(query("开局哪些角色值得练")).first().evidence.first().snippet,
        )
        assertEquals(
            "通用原则：先不列全角色名单。",
            pipeline.retrieve(query("哪些角色直练")).first().evidence.first().snippet,
        )
        assertEquals(
            "这会涉及后期加入角色。",
            pipeline.retrieve(query("直接告诉我强力角色名单")).first().evidence.first().snippet,
        )
    }

    @Test
    fun `longest localized item alias matches mithril usage template`() = runTest {
        val pipeline = LocalKnowledgeRetrievalPipeline(
            FakeKnowledgeRepository(
                listOf(
                    chunk(
                        entityId = "item.mithril",
                        entityType = "item",
                        canonicalName = "Mithril / 秘银",
                        aliases = listOf("秘银", "米斯里鲁", "米斯里鲁银"),
                        descriptionShort = "Mithril 是中期后锻造材料。",
                        spoilerLevel = "medium",
                        sourceRefs = listOf("sf2.items"),
                        answerTemplates = listOf(
                            """{"template_id":"template.sf2.mithril.usage.zh","intent":"usage","question_patterns":["米斯里鲁有什么用","米斯里鲁银有什么用"],"answer":"Mithril（米斯里鲁银）可交给 Dwarven Blacksmith 打造强力武器。","source_refs":["sf2.items"],"spoiler_level":"light"}"""
                        ),
                    ),
                )
            )
        )

        val results = pipeline.retrieve(query("米斯里鲁银有什么用"))

        assertEquals("item.mithril", results.first().entityId)
        assertEquals(
            "Mithril（米斯里鲁银）可交给 Dwarven Blacksmith 打造强力武器。",
            results.first().evidence.first().snippet,
        )
    }

    @Test
    fun `suggests nearby template questions without repeating the current question`() = runTest {
        val pipeline = LocalKnowledgeRetrievalPipeline(
            FakeKnowledgeRepository(
                listOf(
                    chunk(
                        entityId = "item.vigor-ball",
                        entityType = "item",
                        canonicalName = "Vigor Ball / 活力球",
                        aliases = listOf("活力球", "气合之玉"),
                        descriptionShort = "Vigor Ball 可让 Priest 系角色转成 Master Monk。",
                        spoilerLevel = "medium",
                        sourceRefs = listOf("sf2.promotion"),
                        answerTemplates = listOf(
                            """{"template_id":"template.sf2.vigor-ball.zh","intent":"usage","question_patterns":["气合之玉怎么用","气合之玉给谁","谁适合转 Master Monk"],"answer":"Vigor Ball（活力球/气合之玉）给 Priest 系角色用于转 Master Monk。","source_refs":["sf2.promotion"],"spoiler_level":"light"}"""
                        ),
                    ),
                )
            )
        )

        val suggestions = pipeline.suggestQuestions(
            query = query("气合之欲怎么又"),
            results = emptyList(),
        )

        assertTrue(suggestions.contains("气合之玉怎么用？"))
        assertFalse(suggestions.contains("气合之欲怎么又？"))
    }

    @Test
    fun `successful entity hits suggest related questions from the same entity`() = runTest {
        val pipeline = LocalKnowledgeRetrievalPipeline(
            FakeKnowledgeRepository(
                listOf(
                    chunk(
                        entityId = "item.vigor-ball",
                        entityType = "item",
                        canonicalName = "Vigor Ball / 活力球",
                        aliases = listOf("活力球", "气合之玉"),
                        descriptionShort = "Vigor Ball 可让 Priest 系角色转成 Master Monk。",
                        spoilerLevel = "medium",
                        sourceRefs = listOf("sf2.promotion"),
                        answerTemplates = listOf(
                            """{"template_id":"template.sf2.vigor-ball.zh","intent":"usage","question_patterns":["气合之玉怎么用","气合之玉在哪里","谁适合转 Master Monk"],"answer":"Vigor Ball（活力球/气合之玉）给 Priest 系角色用于转 Master Monk。","source_refs":["sf2.promotion"],"spoiler_level":"light"}"""
                        ),
                    ),
                    chunk(
                        entityId = "item.mithril",
                        entityType = "item",
                        canonicalName = "Mithril / 秘银",
                        aliases = listOf("米斯里鲁银"),
                        descriptionShort = "Mithril 是锻造材料。",
                        spoilerLevel = "medium",
                        sourceRefs = listOf("sf2.items"),
                        answerTemplates = listOf(
                            """{"template_id":"template.sf2.mithril.usage.zh","intent":"usage","question_patterns":["米斯里鲁银有什么用"],"answer":"Mithril 可锻造武器。","source_refs":["sf2.items"],"spoiler_level":"light"}"""
                        ),
                    ),
                )
            )
        )
        val results = pipeline.retrieve(query("气合之玉怎么用"))

        val suggestions = pipeline.suggestQuestions(
            query = query("气合之玉怎么用"),
            results = results,
        )

        assertFalse(suggestions.contains("气合之玉怎么用？"))
        assertTrue(suggestions.contains("气合之玉在哪里？"))
        assertTrue(suggestions.contains("谁适合转 Master Monk？"))
        assertFalse(suggestions.contains("米斯里鲁银有什么用？"))
    }

    @Test
    fun `successful item hits do not spend all suggestions on alias variants`() = runTest {
        val pipeline = LocalKnowledgeRetrievalPipeline(
            FakeKnowledgeRepository(
                listOf(
                    chunk(
                        entityId = "item.mithril",
                        entityType = "item",
                        canonicalName = "Mithril / 秘银",
                        aliases = listOf("Mithril", "秘银", "米斯里鲁", "米斯里鲁银"),
                        descriptionShort = "Mithril 是中期后锻造材料。",
                        spoilerLevel = "medium",
                        sourceRefs = listOf("sf2.items"),
                        answerTemplates = listOf(
                            """{"template_id":"template.sf2.mithril.usage.zh","intent":"usage","question_patterns":["Mithril 有什么用","秘银有什么用","米斯里鲁有什么用","米斯里鲁银有什么用"],"answer":"Mithril 可锻造武器。","source_refs":["sf2.items"],"spoiler_level":"light"}""",
                            """{"template_id":"template.sf2.mithril.location.zh","intent":"location","question_patterns":["Mithril 在哪里","秘银在哪里","米斯里鲁银在哪里"],"answer_light":"位置清单会剧透探索路线。","source_refs":["sf2.items"],"spoiler_light":"light"}""",
                        ),
                    ),
                )
            )
        )
        val results = pipeline.retrieve(query("米斯里鲁银有什么用"))

        val suggestions = pipeline.suggestQuestions(
            query = query("米斯里鲁银有什么用"),
            results = results,
        )

        assertTrue(suggestions.contains("米斯里鲁银在哪里？"))
        assertTrue(
            "usage aliases should not consume every slot: $suggestions",
            suggestions.count { it.contains("有什么用") } <= 1,
        )
    }

    private fun query(
        raw: String,
        gameId: String? = "2048",
        spoilerLevel: SpoilerLevel = SpoilerLevel.LIGHT,
        progressGate: String? = null,
    ): RetrievalQuery = RetrievalQuery(
        gameId = gameId,
        normalizedQuery = raw.trim().lowercase(),
        language = "zh",
        progressGate = progressGate,
        spoilerLevel = spoilerLevel,
        limit = 5,
    )

    private fun chunk(
        entityId: String,
        entityType: String = "mechanic",
        canonicalName: String,
        aliases: List<String>,
        descriptionShort: String,
        spoilerLevel: String,
        progressGate: String? = "start",
        sourceRefs: List<String> = listOf("sample.2048.rules"),
        answerTemplates: List<String> = emptyList(),
    ): KnowledgeChunkDomain = KnowledgeChunkDomain(
        id = 0L,
        gameId = "2048",
        entityId = entityId,
        entityType = entityType,
        canonicalName = canonicalName,
        aliases = aliases,
        descriptionShort = descriptionShort,
        descriptionLong = null,
        progressGate = progressGate,
        spoilerLevel = spoilerLevel,
        sourceRefs = sourceRefs,
        confidence = "verified",
        answerTemplates = answerTemplates,
    )

    private class FakeKnowledgeRepository(
        private val rows: List<KnowledgeChunkDomain>,
    ) : KnowledgeRepository {
        override suspend fun searchFts(
            gameId: String,
            query: String,
            limit: Int,
        ): List<KnowledgeChunkDomain> =
            rows.filter { row ->
                row.gameId == gameId &&
                    (
                        row.descriptionShort.contains(query) ||
                            (query.contains("棋盘") && row.entityId == "strategy.keep-space")
                        )
            }.take(limit)

        override suspend fun getByEntityId(gameId: String, entityId: String): KnowledgeChunkDomain? =
            rows.firstOrNull { it.gameId == gameId && it.entityId == entityId }

        override suspend fun listByGame(gameId: String): List<KnowledgeChunkDomain> =
            rows.filter { it.gameId == gameId }

        override suspend fun listByType(gameId: String, entityType: String): List<KnowledgeChunkDomain> =
            rows.filter { it.gameId == gameId && it.entityType == entityType }

        override suspend fun upsertAll(chunks: List<KnowledgeChunkDomain>) = Unit
        override suspend fun clearForGame(gameId: String) = Unit
    }
}
