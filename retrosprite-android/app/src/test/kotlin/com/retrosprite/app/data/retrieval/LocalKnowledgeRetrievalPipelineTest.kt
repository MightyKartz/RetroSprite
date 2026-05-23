package com.retrosprite.app.data.retrieval

import com.retrosprite.app.data.models.KnowledgeChunkDomain
import com.retrosprite.app.data.repository.KnowledgeRepository
import com.retrosprite.app.domain.models.RetrievalQuery
import com.retrosprite.app.domain.models.SpoilerLevel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
