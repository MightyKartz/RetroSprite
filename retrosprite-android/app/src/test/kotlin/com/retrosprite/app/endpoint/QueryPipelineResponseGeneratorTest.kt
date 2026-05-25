package com.retrosprite.app.endpoint

import com.retrosprite.app.data.models.GameDomain
import com.retrosprite.app.data.models.KnowledgeAliasDomain
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
    fun `normalizes observed location ASR homophone and clipped suffix`() = runTest {
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
                question = "金陵村是不是隐藏地",
                state = RetroArchState(paused = 1),
            ),
            outputMode = "hotkey_voice:text",
        )

        assertEquals("精灵村是不是隐藏地点", capturedQuestion)
        assertEquals("answered: 精灵村是不是隐藏地点", response.text)
        assertEquals("精灵村是不是隐藏地点", response.diagnostics.question)
        assertEquals("金陵村是不是隐藏地", response.diagnostics.rawQuestion)
        assertEquals("精灵村是不是隐藏地点", response.diagnostics.normalizedQuestion)
        assertEquals("homophone+truncated_suffix", response.diagnostics.questionNormalizationReason)
        assertEquals("精灵村", response.diagnostics.normalizedQuestionMatchedTerm)
        assertEquals("location.elven-town", response.diagnostics.normalizedQuestionMatchedEntityId)
    }

    @Test
    fun `normalizes observed item ASR homophones before forwarding to pipeline`() = runTest {
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
                question = "气和之欲怎么有",
                state = RetroArchState(paused = 1),
            ),
            outputMode = "hotkey_voice:text",
        )

        assertEquals("气合之玉怎么用", capturedQuestion)
        assertEquals("answered: 气合之玉怎么用", response.text)
        assertEquals("气和之欲怎么有", response.diagnostics.rawQuestion)
        assertEquals("气合之玉怎么用", response.diagnostics.normalizedQuestion)
        assertEquals("homophone+truncated_suffix", response.diagnostics.questionNormalizationReason)
        assertEquals("气合之玉", response.diagnostics.normalizedQuestionMatchedTerm)
        assertEquals("item.vigor-ball", response.diagnostics.normalizedQuestionMatchedEntityId)
    }

    @Test
    fun `normalizes observed duplicate item ASR before forwarding to pipeline`() = runTest {
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
                question = "气气合之欲怎么又",
                state = RetroArchState(paused = 1),
            ),
            outputMode = "hotkey_voice:text",
        )

        assertEquals("气合之玉怎么用", capturedQuestion)
        assertEquals("answered: 气合之玉怎么用", response.text)
        assertEquals("气气合之欲怎么又", response.diagnostics.rawQuestion)
        assertEquals("气合之玉怎么用", response.diagnostics.normalizedQuestion)
        assertEquals(
            "gkp_observed_asr_variant+duplicate_prefix+truncated_suffix",
            response.diagnostics.questionNormalizationReason,
        )
        assertEquals("气合之玉", response.diagnostics.normalizedQuestionMatchedTerm)
        assertEquals("item.vigor-ball", response.diagnostics.normalizedQuestionMatchedEntityId)
    }

    @Test
    fun `normalizes observed bare item ASR before forwarding to pipeline`() = runTest {
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
                question = "米斯里鲁",
                state = RetroArchState(paused = 1),
            ),
            outputMode = "hotkey_voice:text",
        )

        assertEquals("米斯里鲁有什么用", capturedQuestion)
        assertEquals("answered: 米斯里鲁有什么用", response.text)
        assertEquals("米斯里鲁", response.diagnostics.rawQuestion)
        assertEquals("米斯里鲁有什么用", response.diagnostics.normalizedQuestion)
        assertEquals("bare_item_usage", response.diagnostics.questionNormalizationReason)
        assertEquals("米斯里鲁", response.diagnostics.normalizedQuestionMatchedTerm)
        assertEquals("item.mithril", response.diagnostics.normalizedQuestionMatchedEntityId)
    }

    @Test
    fun `normalizes observed mithril homophone despite shorter exact alias`() = runTest {
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
                question = "米斯里鲁因有什么用",
                state = RetroArchState(paused = 1),
            ),
            outputMode = "hotkey_voice:text",
        )

        assertEquals("米斯里鲁银有什么用", capturedQuestion)
        assertEquals("answered: 米斯里鲁银有什么用", response.text)
        assertEquals("米斯里鲁因有什么用", response.diagnostics.rawQuestion)
        assertEquals("米斯里鲁银有什么用", response.diagnostics.normalizedQuestion)
        assertEquals("gkp_observed_asr_variant", response.diagnostics.questionNormalizationReason)
        assertEquals("米斯里鲁银", response.diagnostics.normalizedQuestionMatchedTerm)
        assertEquals("item.mithril", response.diagnostics.normalizedQuestionMatchedEntityId)
    }

    @Test
    fun `normalizes chrono trigger observed asr only for current game`() = runTest {
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
                label = "sfc__Chrono Trigger (USA)",
                question = "纳尔是谁",
                state = RetroArchState(paused = 1),
            ),
            outputMode = "hotkey_voice:text",
        )

        assertEquals("玛尔是谁", capturedQuestion)
        assertEquals("answered: 玛尔是谁", response.text)
        assertEquals("纳尔是谁", response.diagnostics.rawQuestion)
        assertEquals("玛尔是谁", response.diagnostics.normalizedQuestion)
        assertEquals("gkp_observed_asr_variant", response.diagnostics.questionNormalizationReason)
        assertEquals("玛尔是谁", response.diagnostics.normalizedQuestionMatchedTerm)
        assertEquals("npc.marle", response.diagnostics.normalizedQuestionMatchedEntityId)
    }

    @Test
    fun `does not apply chrono trigger observed asr for a different current game`() = runTest {
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
                question = "纳尔是谁",
                state = RetroArchState(paused = 1),
            ),
            outputMode = "hotkey_voice:text",
        )

        assertEquals("纳尔是谁", capturedQuestion)
        assertEquals("answered: 纳尔是谁", response.text)
        assertNull(response.diagnostics.rawQuestion)
        assertNull(response.diagnostics.normalizedQuestion)
        assertNull(response.diagnostics.questionNormalizationReason)
        assertNull(response.diagnostics.normalizedQuestionMatchedEntityId)
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
                        suggestedQuestions = listOf("气合之玉在哪里？", "谁适合转 Master Monk？"),
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
        assertEquals(listOf("sample.2048.rules"), response.diagnostics.sourceIds)
        assertEquals(listOf("查看来源", "这不对"), response.diagnostics.nextActions)
        assertEquals(
            listOf("气合之玉在哪里？", "谁适合转 Master Monk？"),
            response.diagnostics.suggestedQuestions,
        )
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
        private val games = listOf(
            GameDomain(
                gameId = "shining_force_ii_md",
                packId = "community.shining-force-ii-md",
                title = "Shining Force II / 光明力量2",
                platform = "mega_drive",
                region = null,
                languages = listOf("zh", "en"),
                romCrc32 = null,
                romSha1 = null,
                retroarchSystemIds = listOf("md", "mega_drive", "genesis"),
                retroarchLabels = listOf("mega_drive__光明力量2", "md__Shining Force II"),
                packVersion = "0.3.3",
                schemaVersion = "gkp.v0",
                trustLevel = "community",
                installedAt = 0L,
            ),
            GameDomain(
                gameId = "chrono_trigger_snes",
                packId = "community.chrono-trigger-snes-zh",
                title = "Chrono Trigger / 时空之轮",
                platform = "snes",
                region = "USA",
                languages = listOf("zh"),
                romCrc32 = null,
                romSha1 = null,
                retroarchSystemIds = listOf("sfc", "snes", "super_nintendo"),
                retroarchLabels = listOf("sfc__Chrono Trigger (USA)"),
                packVersion = "0.1.1",
                schemaVersion = "gkp.v0",
                trustLevel = "community",
                installedAt = 0L,
            ),
        )

        override fun observeAll(): Flow<List<GameDomain>> = flowOf(games)
        override suspend fun getById(gameId: String): GameDomain? = games.firstOrNull { it.gameId == gameId }
        override suspend fun getByRomSha1(sha1: String): GameDomain? = null
        override suspend fun getByRomCrc32(crc32: String): GameDomain? = null
        override suspend fun searchByLabel(platform: String, titleQuery: String): List<GameDomain> {
            val normalizedPlatform = platform.lowercase()
            val normalizedTitle = titleQuery.lowercase()
            return games.filter { game ->
                val systems = (game.retroarchSystemIds + game.platform).map { it.lowercase() }
                val labels = game.retroarchLabels.map { it.lowercase() }
                normalizedPlatform in systems &&
                    (labels.any { it.contains(normalizedTitle) } || game.title.lowercase().contains(normalizedTitle))
            }
        }
        override suspend fun upsert(game: GameDomain) = Unit
        override suspend fun delete(gameId: String) = Unit
    }

    private class FakeKnowledgeRepository : KnowledgeRepository {
        override suspend fun listByGame(gameId: String): List<KnowledgeChunkDomain> =
            when (gameId) {
                "chrono_trigger_snes" -> chronoRows(gameId)
                else -> shiningForceRows(gameId)
            }

        private fun shiningForceRows(gameId: String): List<KnowledgeChunkDomain> =
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
                ),
                KnowledgeChunkDomain(
                    id = 1L,
                    gameId = gameId,
                    entityId = "location.elven-town",
                    entityType = "location",
                    canonicalName = "Elven Town / 精灵森林",
                    aliases = listOf("精灵森林", "精灵村"),
                    descriptionShort = "desc",
                    descriptionLong = null,
                    progressGate = "elven_town",
                    spoilerLevel = "medium",
                    sourceRefs = listOf("sf2.secrets"),
                    confidence = "verified",
                    answerTemplates = emptyList(),
                ),
                KnowledgeChunkDomain(
                    id = 2L,
                    gameId = gameId,
                    entityId = "item.vigor-ball",
                    entityType = "item",
                    canonicalName = "Vigor Ball / 气合之玉",
                    aliases = listOf("气合之玉"),
                    aliasMetadata = listOf(asrVariant("气合之欲", "气合之玉", "item.vigor-ball")),
                    descriptionShort = "desc",
                    descriptionLong = null,
                    progressGate = "elven_town",
                    spoilerLevel = "medium",
                    sourceRefs = listOf("sf2.promotion"),
                    confidence = "verified",
                    answerTemplates = emptyList(),
                ),
                KnowledgeChunkDomain(
                    id = 3L,
                    gameId = gameId,
                    entityId = "item.mithril",
                    entityType = "item",
                    canonicalName = "Mithril / 秘银",
                    aliases = listOf("秘银", "米斯里鲁", "米斯里鲁银"),
                    aliasMetadata = listOf(asrVariant("米斯里鲁因", "米斯里鲁银", "item.mithril")),
                    descriptionShort = "desc",
                    descriptionLong = null,
                    progressGate = "new_granseal",
                    spoilerLevel = "medium",
                    sourceRefs = listOf("sf2.items"),
                    confidence = "verified",
                    answerTemplates = emptyList(),
                ),
            )

        private fun chronoRows(gameId: String): List<KnowledgeChunkDomain> =
            listOf(
                KnowledgeChunkDomain(
                    id = 10L,
                    gameId = gameId,
                    entityId = "npc.marle",
                    entityType = "npc",
                    canonicalName = "Marle / 玛尔",
                    aliases = listOf("玛尔", "马尔"),
                    aliasMetadata = listOf(asrVariant("纳尔是谁", "玛尔是谁", "npc.marle")),
                    descriptionShort = "desc",
                    descriptionLong = null,
                    progressGate = "start",
                    spoilerLevel = "light",
                    sourceRefs = listOf("ct.project_notes"),
                    confidence = "verified",
                    answerTemplates = emptyList(),
                ),
            )

        override suspend fun searchFts(gameId: String, query: String, limit: Int) = emptyList<KnowledgeChunkDomain>()
        override suspend fun getByEntityId(gameId: String, entityId: String): KnowledgeChunkDomain? = null
        override suspend fun listByType(gameId: String, entityType: String) = emptyList<KnowledgeChunkDomain>()
        override suspend fun upsertAll(chunks: List<KnowledgeChunkDomain>) = Unit
        override suspend fun clearForGame(gameId: String) = Unit

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
}
