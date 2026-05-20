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

class Sample2048QuestionPipelineTest {

    @Test
    fun `sample 2048 question returns local evidence answer with source and no llm call`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = DefaultQueryPipeline(
            resolver = RepositoryGameResolver(FakeGameRepository(listOf(fixture.game))),
            retrieval = LocalKnowledgeRetrievalPipeline(FakeKnowledgeRepository(fixture.knowledge)),
            policy = EvidenceAnswerPolicy(),
            composer = AnswerComposer(),
            llm = llm,
        )

        val text = pipeline.answer(
            label = "2048__",
            question = "两个 2 怎么合并？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<$text>", text.contains("合并"))
        assertTrue("answer=<$text>", text.contains("翻倍"))
        assertTrue("answer=<$text>", text.contains("来源：sample.2048.rules"))
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `sample 2048 unknown question returns uncertainty and no llm call`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = DefaultQueryPipeline(
            resolver = RepositoryGameResolver(FakeGameRepository(listOf(fixture.game))),
            retrieval = LocalKnowledgeRetrievalPipeline(FakeKnowledgeRepository(fixture.knowledge)),
            policy = EvidenceAnswerPolicy(),
            composer = AnswerComposer(),
            llm = llm,
        )

        val text = pipeline.answer(
            label = "2048__",
            question = "这个版本有没有多人对战模式？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue(text.contains("没有足够证据"))
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `sample 2048 undo and restart question returns local evidence`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = DefaultQueryPipeline(
            resolver = RepositoryGameResolver(FakeGameRepository(listOf(fixture.game))),
            retrieval = LocalKnowledgeRetrievalPipeline(FakeKnowledgeRepository(fixture.knowledge)),
            policy = EvidenceAnswerPolicy(),
            composer = AnswerComposer(),
            llm = llm,
        )

        val text = pipeline.answer(
            label = "2048__",
            question = "这个版本有没有撤销键？怎么重开？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<$text>", text.contains("撤销"))
        assertTrue("answer=<$text>", text.contains("重开"))
        assertTrue("answer=<$text>", text.contains("来源：sample.2048.rules"))
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `disabled sample 2048 pack explains disabled state without retrieval or llm`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = DefaultQueryPipeline(
            resolver = RepositoryGameResolver(
                FakeGameRepository(listOf(fixture.game.copy(isEnabled = false, disabledAt = 123L)))
            ),
            retrieval = LocalKnowledgeRetrievalPipeline(FakeKnowledgeRepository(fixture.knowledge)),
            policy = EvidenceAnswerPolicy(),
            composer = AnswerComposer(),
            llm = llm,
        )

        val text = pipeline.answer(
            label = "2048__",
            question = "两个 2 怎么合并？",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue("answer=<$text>", text.contains("知识包已禁用"))
        assertTrue("answer=<$text>", text.contains("Packs"))
        assertTrue("answer=<$text>", !text.contains("来源："))
        assertEquals(0, llm.callCount)
    }

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
            .resolve("src/main/assets/gkp/sample-2048")
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
