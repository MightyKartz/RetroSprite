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
