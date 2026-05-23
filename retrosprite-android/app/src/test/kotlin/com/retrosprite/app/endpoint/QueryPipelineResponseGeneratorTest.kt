package com.retrosprite.app.endpoint

import com.retrosprite.app.data.models.GameDomain
import com.retrosprite.app.data.models.KnowledgeChunkDomain
import com.retrosprite.app.data.repository.GameRepository
import com.retrosprite.app.data.repository.KnowledgeRepository
import com.retrosprite.app.data.resolver.RepositoryGameResolver
import com.retrosprite.app.domain.policy.AnswerComposer
import com.retrosprite.app.domain.policy.FixedTextAnswerPolicy
import com.retrosprite.app.domain.resolver.LabelGameResolver
import com.retrosprite.app.domain.retrieval.NoOpRetrievalPipeline
import com.retrosprite.app.domain.DefaultQueryPipeline
import com.retrosprite.app.domain.QueryPipeline
import com.retrosprite.app.domain.QueryPipelineResult
import com.retrosprite.app.domain.models.AnswerConfidence
import com.retrosprite.app.domain.models.AnswerNextAction
import com.retrosprite.app.domain.models.AnswerResult
import com.retrosprite.app.domain.models.AnswerType
import com.retrosprite.app.domain.models.LlmCallTrace
import com.retrosprite.app.domain.models.SpoilerLevel
import com.retrosprite.app.endpoint.model.RetroArchRequest
import com.retrosprite.app.endpoint.model.RetroArchState
import com.retrosprite.app.llm.MockLlmAdapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Verifies the endpoint↔domain bridge introduced during cross-module integration:
 * a [RetroArchRequest] is funnelled through the real [DefaultQueryPipeline] and
 * lands as a [com.retrosprite.app.endpoint.model.RetroArchResponse] with the
 * Phase 0 fixed acknowledgement.
 */
class QueryPipelineResponseGeneratorTest {

    private val pipeline = DefaultQueryPipeline(
        resolver = LabelGameResolver(),
        retrieval = NoOpRetrievalPipeline(),
        policy = FixedTextAnswerPolicy(),
        composer = AnswerComposer(),
        llm = MockLlmAdapter(),
    )

    private val generator = QueryPipelineResponseGenerator(pipeline)

    @Test
    fun `forwards request through pipeline and wraps result as text response`() = runTest {
        val response = generator.generate(
            request = RetroArchRequest(
                image = "iVBORw0KGgo=", // placeholder base64
                label = "snes__super_mario_world",
                state = RetroArchState(paused = 1, a = 1),
            ),
            outputMode = "text",
        )

        assertEquals(FixedTextAnswerPolicy.PHASE_0_ACK_TEXT, response.text)
        assertNull("error must be null on success path", response.error)
    }

    @Test
    fun `tolerates empty image and empty label`() = runTest {
        val response = generator.generate(
            request = RetroArchRequest(image = "", label = "", state = RetroArchState()),
            outputMode = "text",
        )
        assertEquals(FixedTextAnswerPolicy.PHASE_0_ACK_TEXT, response.text)
    }

    @Test
    fun `mixed output mode is still satisfied`() = runTest {
        val response = generator.generate(
            request = RetroArchRequest(label = "nes__contra"),
            outputMode = "text|sound",
        )
        assertNotNull(response.text)
    }

    @Test
    fun `forwards optional question field to pipeline`() = runTest {
        var capturedQuestion: String? = null
        val generator = QueryPipelineResponseGenerator(
            object : QueryPipeline {
                override suspend fun answer(
                    label: String,
                    romHash: String?,
                    question: String?,
                    screenshot: String?,
                    state: Map<String, Int>?,
                    spoilerLevel: SpoilerLevel,
                    language: String,
                ): String {
                    capturedQuestion = question
                    return "answered: $question"
                }
            }
        )

        val response = generator.generate(
            request = RetroArchRequest(
                label = "2048__",
                question = "两个 2 怎么合并？",
            ),
            outputMode = "text",
        )

        assertEquals("两个 2 怎么合并？", capturedQuestion)
        assertEquals("answered: 两个 2 怎么合并？", response.text)
    }

    @Test
    fun `normalizes hotkey voice question before forwarding to pipeline`() = runTest {
        val games = FakeGameRepository()
        val knowledge = FakeKnowledgeRepository()
        var capturedQuestion: String? = null
        val generator = QueryPipelineResponseGenerator(
            pipeline = object : QueryPipeline {
                override suspend fun answer(
                    label: String,
                    romHash: String?,
                    question: String?,
                    screenshot: String?,
                    state: Map<String, Int>?,
                    spoilerLevel: SpoilerLevel,
                    language: String,
                ): String {
                    capturedQuestion = question
                    return "answered: $question"
                }
            },
            gameResolver = RepositoryGameResolver(games),
            knowledgeRepository = knowledge,
        )

        val response = generator.generate(
            request = RetroArchRequest(
                image = "",
                label = "mega_drive__光明力量2",
                question = "修医是谁",
                state = RetroArchState(paused = 1),
            ),
            outputMode = "hotkey_voice:text",
        )

        assertEquals("修伊是谁", capturedQuestion)
        assertEquals("answered: 修伊是谁", response.text)
        assertEquals("修伊是谁", response.diagnostics.question)
        assertEquals("修医是谁", response.diagnostics.rawQuestion)
        assertEquals("修伊是谁", response.diagnostics.normalizedQuestion)
        assertEquals("homophone", response.diagnostics.questionNormalizationReason)
        assertEquals("修伊", response.diagnostics.normalizedQuestionMatchedTerm)
        assertEquals("npc.jaha", response.diagnostics.normalizedQuestionMatchedEntityId)
    }

    @Test
    fun `uses configured default spoiler level when request has no override`() = runTest {
        var capturedSpoilerLevel: SpoilerLevel? = null
        val generator = QueryPipelineResponseGenerator(
            pipeline = object : QueryPipeline {
                override suspend fun answer(
                    label: String,
                    romHash: String?,
                    question: String?,
                    screenshot: String?,
                    state: Map<String, Int>?,
                    spoilerLevel: SpoilerLevel,
                    language: String,
                ): String {
                    capturedSpoilerLevel = spoilerLevel
                    return "ok"
                }
            },
            spoilerLevelProvider = { SpoilerLevel.CLEAR },
        )

        generator.generate(
            request = RetroArchRequest(label = "2048__", question = "下一步？"),
            outputMode = "text",
        )

        assertEquals(SpoilerLevel.CLEAR, capturedSpoilerLevel)
    }

    @Test
    fun `request spoiler level overrides configured default`() = runTest {
        var capturedSpoilerLevel: SpoilerLevel? = null
        val generator = QueryPipelineResponseGenerator(
            pipeline = object : QueryPipeline {
                override suspend fun answer(
                    label: String,
                    romHash: String?,
                    question: String?,
                    screenshot: String?,
                    state: Map<String, Int>?,
                    spoilerLevel: SpoilerLevel,
                    language: String,
                ): String {
                    capturedSpoilerLevel = spoilerLevel
                    return "ok"
                }
            },
            spoilerLevelProvider = { SpoilerLevel.LIGHT },
        )

        generator.generate(
            request = RetroArchRequest(
                label = "2048__",
                question = "直接答案？",
                spoilerLevel = "direct",
            ),
            outputMode = "text",
        )

        assertEquals(SpoilerLevel.FULL, capturedSpoilerLevel)
    }

    @Test
    fun `attaches llm diagnostics without serializing them into text`() = runTest {
        val generator = QueryPipelineResponseGenerator(
            object : QueryPipeline {
                override suspend fun answer(
                    label: String,
                    romHash: String?,
                    question: String?,
                    screenshot: String?,
                    state: Map<String, Int>?,
                    spoilerLevel: SpoilerLevel,
                    language: String,
                ): String = "unused"

                override suspend fun answerDetailed(
                    label: String,
                    romHash: String?,
                    question: String?,
                    screenshot: String?,
                    state: Map<String, Int>?,
                    spoilerLevel: SpoilerLevel,
                    language: String,
                ): QueryPipelineResult = QueryPipelineResult(
                    text = "answer",
                    llmTrace = LlmCallTrace(
                        status = "used",
                        providerName = "deepseek",
                        modelName = "deepseek-v4-pro",
                        maxTokens = 256,
                        timeoutMs = 30_000L,
                        latencyMs = 1_234L,
                        tokensIn = 12,
                        tokensOut = 5,
                    ),
                    answerResult = AnswerResult(
                        answerShort = "short answer",
                        answerDetail = "answer",
                        sources = listOf("sample.2048.rules"),
                        confidence = AnswerConfidence.High,
                        answerType = AnswerType.Mechanic,
                        spoilerLevelUsed = SpoilerLevel.LIGHT,
                        nextActions = listOf(AnswerNextAction.ViewSources, AnswerNextAction.MarkIncorrect),
                    ),
                )
            }
        )

        val response = generator.generate(
            request = RetroArchRequest(label = "2048__", question = "怎么合并？"),
            outputMode = "text",
        )

        assertEquals("answer", response.text)
        assertEquals("used", response.diagnostics.llmStatus)
        assertEquals("deepseek", response.diagnostics.llmProvider)
        assertEquals("deepseek-v4-pro", response.diagnostics.llmModel)
        assertEquals(256, response.diagnostics.llmMaxTokens)
        assertEquals(30_000L, response.diagnostics.llmTimeoutMs)
        assertEquals(1_234L, response.diagnostics.llmLatencyMs)
        assertEquals(12, response.diagnostics.llmTokensIn)
        assertEquals(5, response.diagnostics.llmTokensOut)
        assertEquals("short answer", response.diagnostics.answerShort)
        assertEquals("answer", response.diagnostics.answerDetail)
        assertEquals("mechanic", response.diagnostics.answerType)
        assertEquals("high", response.diagnostics.answerConfidence)
        assertEquals("light", response.diagnostics.spoilerLevelUsed)
        assertEquals(listOf("查看来源", "这不对"), response.diagnostics.nextActions)
    }

    @Test
    fun `attaches failed llm diagnostics for timeout answers`() = runTest {
        val generator = QueryPipelineResponseGenerator(
            object : QueryPipeline {
                override suspend fun answer(
                    label: String,
                    romHash: String?,
                    question: String?,
                    screenshot: String?,
                    state: Map<String, Int>?,
                    spoilerLevel: SpoilerLevel,
                    language: String,
                ): String = "unused"

                override suspend fun answerDetailed(
                    label: String,
                    romHash: String?,
                    question: String?,
                    screenshot: String?,
                    state: Map<String, Int>?,
                    spoilerLevel: SpoilerLevel,
                    language: String,
                ): QueryPipelineResult = QueryPipelineResult(
                    text = "LLM 调用失败：timeout while waiting for provider。已保留本地证据，请稍后重试或检查模型配置。",
                    llmTrace = LlmCallTrace(
                        status = "failed",
                        providerName = "deepseek",
                        modelName = "deepseek-v4-pro",
                        maxTokens = 64,
                        timeoutMs = 5_000L,
                        errorMessage = "timeout while waiting for provider",
                    )
                )
            }
        )

        val response = generator.generate(
            request = RetroArchRequest(label = "2048__", question = "下一步？"),
            outputMode = "text",
        )

        assertEquals("failed", response.diagnostics.llmStatus)
        assertEquals("deepseek", response.diagnostics.llmProvider)
        assertEquals("deepseek-v4-pro", response.diagnostics.llmModel)
        assertEquals(64, response.diagnostics.llmMaxTokens)
        assertEquals(5_000L, response.diagnostics.llmTimeoutMs)
        assertEquals("timeout while waiting for provider", response.diagnostics.llmError)
    }

    @Test
    fun `pressed buttons are surfaced in the flag map`() {
        val map = RetroArchState(paused = 1, a = 1, b = 0, start = 1).toFlagMap()
        assertEquals(setOf("a", "start"), map.keys)
        assertEquals(1, map["a"])
        assertEquals(1, map["start"])
    }

    @Test
    fun `flag map omits paused since it is plumbed separately`() {
        val map = RetroArchState(paused = 1).toFlagMap()
        assertEquals(emptyMap<String, Int>(), map)
    }

    private class FakeGameRepository : GameRepository {
        private val game = GameDomain(
            gameId = "shining_force_ii_md",
            packId = "community.shining-force-ii-md",
            title = "Shining Force II / 光明力量2",
            platform = "mega_drive",
            region = null,
            languages = listOf("zh", "en"),
            romCrc32 = null,
            romSha1 = null,
            packVersion = "0.2.5",
            schemaVersion = "gkp.v0",
            trustLevel = "community",
            installedAt = 0L,
        )

        override fun observeAll(): Flow<List<GameDomain>> = flowOf(listOf(game))
        override suspend fun getById(gameId: String): GameDomain? = game.takeIf { it.gameId == gameId }
        override suspend fun getByRomSha1(sha1: String): GameDomain? = null
        override suspend fun getByRomCrc32(crc32: String): GameDomain? = null
        override suspend fun searchByLabel(platform: String, titleQuery: String): List<GameDomain> = listOf(game)
        override suspend fun upsert(game: GameDomain) = Unit
        override suspend fun delete(gameId: String) = Unit
    }

    private class FakeKnowledgeRepository : KnowledgeRepository {
        override suspend fun listByGame(gameId: String): List<KnowledgeChunkDomain> =
            listOf(
                KnowledgeChunkDomain(
                    id = 0L,
                    gameId = gameId,
                    entityId = "npc.jaha",
                    entityType = "npc",
                    canonicalName = "Jaha / 吉布",
                    aliases = listOf("修伊"),
                    descriptionShort = "desc",
                    descriptionLong = null,
                    progressGate = "start",
                    spoilerLevel = "light",
                    sourceRefs = listOf("test.source"),
                    confidence = "verified",
                    answerTemplates = emptyList(),
                )
            )

        override suspend fun searchFts(gameId: String, query: String, limit: Int) = emptyList<KnowledgeChunkDomain>()
        override suspend fun getByEntityId(gameId: String, entityId: String): KnowledgeChunkDomain? = null
        override suspend fun listByType(gameId: String, entityType: String) = emptyList<KnowledgeChunkDomain>()
        override suspend fun upsertAll(chunks: List<KnowledgeChunkDomain>) = Unit
        override suspend fun clearForGame(gameId: String) = Unit
    }
}
