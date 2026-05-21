package com.retrosprite.app.domain

import com.retrosprite.app.data.gkp.GkpV0Parser
import com.retrosprite.app.data.models.GameDomain
import com.retrosprite.app.data.models.KnowledgeChunkDomain
import com.retrosprite.app.data.repository.GameRepository
import com.retrosprite.app.data.repository.KnowledgeRepository
import com.retrosprite.app.data.resolver.RepositoryGameResolver
import com.retrosprite.app.data.retrieval.LocalKnowledgeRetrievalPipeline
import com.retrosprite.app.domain.models.LlmRequest
import com.retrosprite.app.domain.models.LlmResponse
import com.retrosprite.app.domain.models.SpoilerLevel
import com.retrosprite.app.domain.policy.AnswerComposer
import com.retrosprite.app.domain.policy.EvidenceAnswerPolicy
import com.retrosprite.app.llm.LlmAdapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class SampleShiningForceIIQuestionPipelineTest {

    @Test
    fun `shining force ii promotion question returns local evidence answer`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val text = pipeline.answer(
            label = "md__Shining Force II",
            question = "什么时候转职？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<$text>", text.contains("20"))
        assertTrue("answer=<$text>", text.contains("转职"))
        assertTrue("answer=<$text>", text.contains("来源：sf2.promotion"))
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii chinese RetroArch playlist label resolves to local evidence`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val text = pipeline.answer(
            label = "md__光明力量2",
            question = "什么时候转职？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<$text>", text.contains("20"))
        assertTrue("answer=<$text>", text.contains("来源：sf2.promotion"))
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii true-device mega drive label resolves to local evidence`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val text = pipeline.answer(
            label = "mega_drive__光明力量2",
            question = "什么时候转职？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<$text>", text.contains("20"))
        assertTrue("answer=<$text>", text.contains("来源：sf2.promotion"))
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii low spoiler next step returns early direction`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val text = pipeline.answer(
            label = "md__Shining Force II",
            question = "不要剧透下一步去哪？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<$text>", text.contains("城堡"))
        assertTrue("answer=<$text>", text.contains("周边"))
        assertTrue("answer=<$text>", text.contains("来源：sf2.early_route"))
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii unknown system question returns uncertainty and no llm call`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val text = pipeline.answer(
            label = "md__Shining Force II",
            question = "这个游戏有没有交易系统？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue(text.contains("没有足够证据"))
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii production question returns source backed developer answer`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val text = pipeline.answer(
            label = "mega_drive__光明力量2",
            question = "谁开发的？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<$text>", text.contains("SEGA"))
        assertTrue("answer=<$text>", text.contains("Sonic"))
        assertTrue("answer=<$text>", text.contains("来源：sf2.production"))
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii early character question answers Sarah role without spoilers`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val text = pipeline.answer(
            label = "md__Shining Force II",
            question = "Sarah 值得练吗？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<$text>", text.contains("治疗"))
        assertTrue("answer=<$text>", text.contains("来源：sf2.manual_translation"))
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii early item question explains medical herb use`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val text = pipeline.answer(
            label = "md__Shining Force II",
            question = "医疗草怎么用？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<$text>", text.contains("10 HP"))
        assertTrue("answer=<$text>", text.contains("来源：sf2.items"))
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii hidden content overview stays low spoiler`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val text = pipeline.answer(
            label = "md__Shining Force II",
            question = "这里有隐藏物品吗？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<$text>", text.contains("不直接列清单"))
        assertTrue("answer=<$text>", text.contains("来源：sf2.secrets"))
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `shining force ii observed asr miss still resolves promotion intent`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        val text = pipeline.answer(
            label = "mega_drive__光明力量2",
            question = "接受他几部这个角色",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<$text>", text.contains("20"))
        assertTrue("answer=<$text>", text.contains("来源：sf2.promotion"))
        assertEquals(0, llm.callCount)
    }

    private fun newPipeline(
        fixture: SamplePackFixture,
        llm: LlmAdapter,
    ): DefaultQueryPipeline =
        DefaultQueryPipeline(
            resolver = RepositoryGameResolver(FakeGameRepository(listOf(fixture.game))),
            retrieval = LocalKnowledgeRetrievalPipeline(FakeKnowledgeRepository(fixture.knowledge)),
            policy = EvidenceAnswerPolicy(),
            composer = AnswerComposer(),
            llm = llm,
        )

    private data class SamplePackFixture(
        val game: GameDomain,
        val knowledge: List<KnowledgeChunkDomain>,
    )

    private class FakeGameRepository(
        private val games: List<GameDomain>,
    ) : GameRepository {
        override fun observeAll(): Flow<List<GameDomain>> = flowOf(games)
        override suspend fun getById(gameId: String): GameDomain? =
            games.firstOrNull { it.gameId == gameId }

        override suspend fun getByRomSha1(sha1: String): GameDomain? =
            games.firstOrNull { it.romSha1 == sha1 }

        override suspend fun getByRomCrc32(crc32: String): GameDomain? =
            games.firstOrNull { it.romCrc32 == crc32 }

        override suspend fun searchByLabel(platform: String, titleQuery: String): List<GameDomain> =
            games.filter {
                it.platform == platform && it.title.contains(titleQuery.trim('%'), ignoreCase = true)
            }

        override suspend fun upsert(game: GameDomain) = Unit
        override suspend fun delete(gameId: String) = Unit
    }

    private class FakeKnowledgeRepository(
        private val rows: List<KnowledgeChunkDomain>,
    ) : KnowledgeRepository {
        override suspend fun searchFts(
            gameId: String,
            query: String,
            limit: Int,
        ): List<KnowledgeChunkDomain> {
            val tokens = normalize(query).split(WHITESPACE)
                .filter { it.length >= 2 }
            if (tokens.isEmpty()) return emptyList()
            return rows.filter { row ->
                row.gameId == gameId && tokens.any { token -> row.searchText().contains(token) }
            }.take(limit)
        }

        override suspend fun getByEntityId(gameId: String, entityId: String): KnowledgeChunkDomain? =
            rows.firstOrNull { it.gameId == gameId && it.entityId == entityId }

        override suspend fun listByGame(gameId: String): List<KnowledgeChunkDomain> =
            rows.filter { it.gameId == gameId }

        override suspend fun listByType(gameId: String, entityType: String): List<KnowledgeChunkDomain> =
            rows.filter { it.gameId == gameId && it.entityType == entityType }

        override suspend fun upsertAll(chunks: List<KnowledgeChunkDomain>) = Unit
        override suspend fun clearForGame(gameId: String) = Unit

        private fun KnowledgeChunkDomain.searchText(): String =
            listOf(
                entityId,
                entityType,
                canonicalName,
                aliases.joinToString(" "),
                descriptionShort,
                descriptionLong.orEmpty(),
            ).joinToString(" ")
                .let(::normalize)
    }

    private class CountingLlmAdapter : LlmAdapter {
        override val providerName: String = "counting"
        var callCount: Int = 0
            private set

        override suspend fun complete(request: LlmRequest): LlmResponse {
            callCount += 1
            return LlmResponse(text = "LLM should not be needed for this fixture")
        }
    }

    private fun loadSamplePack(): SamplePackFixture {
        val packDir = moduleRoot()
            .resolve("src/main/assets/gkp/shining-force-ii-md")
            .normalize()
        val manifestText = readText(packDir.resolve("manifest.json"))
        val parser = GkpV0Parser(nowMillis = { 0L })
        val knowledgeFiles = parser.knowledgePaths(manifestText)
            .associateWith { relative -> readText(packDir.resolve(relative)) }
        val parsed = parser.parse(manifestText, knowledgeFiles)
        return SamplePackFixture(parsed.game, parsed.knowledge)
    }

    private fun moduleRoot(): Path {
        var current = Paths.get("").toAbsolutePath().normalize()
        repeat(8) {
            if (Files.isDirectory(current.resolve("src/main/assets"))) return current
            if (Files.isDirectory(current.resolve("app/src/main/assets"))) return current.resolve("app")
            current = current.parent ?: current
        }
        error("Could not locate Android app module from ${Paths.get("").toAbsolutePath()}")
    }

    private fun readText(path: Path): String =
        Files.readAllBytes(path).toString(Charsets.UTF_8)

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val PUNCTUATION = Regex("[\\p{Punct}，。？！、；：]+")

        fun normalize(value: String): String =
            value.lowercase()
                .replace(PUNCTUATION, " ")
                .replace(WHITESPACE, " ")
                .trim()
    }
}
