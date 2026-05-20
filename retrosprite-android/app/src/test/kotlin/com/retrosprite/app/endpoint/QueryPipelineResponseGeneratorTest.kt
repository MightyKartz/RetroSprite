package com.retrosprite.app.endpoint

import com.retrosprite.app.domain.policy.AnswerComposer
import com.retrosprite.app.domain.policy.FixedTextAnswerPolicy
import com.retrosprite.app.domain.resolver.LabelGameResolver
import com.retrosprite.app.domain.retrieval.NoOpRetrievalPipeline
import com.retrosprite.app.domain.DefaultQueryPipeline
import com.retrosprite.app.domain.QueryPipeline
import com.retrosprite.app.domain.QueryPipelineResult
import com.retrosprite.app.domain.models.LlmCallTrace
import com.retrosprite.app.domain.models.SpoilerLevel
import com.retrosprite.app.endpoint.model.RetroArchRequest
import com.retrosprite.app.endpoint.model.RetroArchState
import com.retrosprite.app.llm.MockLlmAdapter
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
                    )
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
}
