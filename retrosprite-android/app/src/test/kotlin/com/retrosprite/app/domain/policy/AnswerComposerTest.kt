package com.retrosprite.app.domain.policy

import com.retrosprite.app.domain.models.AnswerDecision
import com.retrosprite.app.domain.models.AnswerConfidence
import com.retrosprite.app.domain.models.AnswerType
import com.retrosprite.app.domain.models.ControllerState
import com.retrosprite.app.domain.models.Evidence
import com.retrosprite.app.domain.models.GameIdentity
import com.retrosprite.app.domain.models.LlmRequest
import com.retrosprite.app.domain.models.LlmResponse
import com.retrosprite.app.domain.models.SessionContext
import com.retrosprite.app.domain.models.SpoilerLevel
import com.retrosprite.app.llm.LlmAdapter
import com.retrosprite.app.llm.MockLlmAdapter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerComposerTest {

    private val composer = AnswerComposer()

    @Test
    fun `direct answers append traceable source ids`() = runTest {
        val answer = composer.composeDetailed(
            decision = AnswerDecision.DirectAnswer(
                text = "两个相同数字滑到一起会合并。",
                sources = listOf("sample.2048.rules", "sample.2048.rules"),
                spoilerLevel = SpoilerLevel.LIGHT,
                answerType = AnswerType.Mechanic,
                confidence = AnswerConfidence.High,
            ),
            context = ctx(),
            llm = MockLlmAdapter(),
        )

        assertEquals("两个相同数字滑到一起会合并。\n来源：sample.2048.rules", answer.text)
        assertEquals("两个相同数字滑到一起会合并。", answer.answerResult.answerShort)
        assertEquals("两个相同数字滑到一起会合并。", answer.answerResult.answerDetail)
        assertEquals(listOf("sample.2048.rules"), answer.answerResult.sources)
        assertEquals(AnswerType.Mechanic, answer.answerResult.answerType)
        assertEquals(AnswerConfidence.High, answer.answerResult.confidence)
    }

    @Test
    fun `direct answers without sources stay unchanged`() = runTest {
        val text = composer.compose(
            decision = AnswerDecision.DirectAnswer(
                text = "我还没有足够证据回答这个问题。",
                sources = emptyList(),
                spoilerLevel = SpoilerLevel.LIGHT,
            ),
            context = ctx(),
            llm = MockLlmAdapter(),
        )

        assertEquals("我还没有足够证据回答这个问题。", text)
    }

    @Test
    fun `compose with llm sends evidence prompt and appends fallback sources`() = runTest {
        val llm = CapturingLlmAdapter(
            response = LlmResponse(
                text = "综合答案。",
                citationsUsed = emptyList(),
                tokensIn = 10,
                tokensOut = 4,
                latencyMs = 123L,
            ),
        )
        val answer = composer.composeDetailed(
            decision = AnswerDecision.ComposeWithLlm(
                prompt = "请综合证据回答。",
                evidence = listOf(
                    evidence("sample.2048.rules", "两个相同数字滑到一起会合并。"),
                    evidence("sample.2048.strategy", "棋盘快满时优先制造空格。"),
                ),
                spoilerLevel = SpoilerLevel.LIGHT,
            ),
            context = ctx(),
            llm = llm,
        )

        assertEquals(1, llm.callCount)
        assertTrue(llm.lastRequest!!.userPrompt.contains("玩家问题：怎么合并？"))
        assertTrue(llm.lastRequest!!.userPrompt.contains("sample.2048.rules"))
        assertEquals("综合答案。\n来源：sample.2048.rules, sample.2048.strategy", answer.text)
        assertEquals("综合答案。", answer.answerResult.answerShort)
        assertEquals(AnswerType.Mechanic, answer.answerResult.answerType)
        assertEquals(AnswerConfidence.Medium, answer.answerResult.confidence)
        assertEquals("used", answer.llmTrace.status)
        assertEquals("capturing", answer.llmTrace.providerName)
        assertEquals("capturing-model", answer.llmTrace.modelName)
        assertEquals(256, answer.llmTrace.maxTokens)
        assertEquals(30_000L, answer.llmTrace.timeoutMs)
        assertEquals(123L, answer.llmTrace.latencyMs)
        assertEquals(10, answer.llmTrace.tokensIn)
        assertEquals(4, answer.llmTrace.tokensOut)
    }

    @Test
    fun `local summary does not call llm and preserves sources`() = runTest {
        val llm = CapturingLlmAdapter(response = LlmResponse(text = "should not be called"))

        val answer = composer.composeDetailed(
            decision = AnswerDecision.LocalSummary(
                evidence = listOf(
                    evidence("sf2.rules", "让低等级角色补最后一击。"),
                    evidence("sf2.tactics", "治疗和辅助行动也能帮助部分角色追经验。"),
                ),
                spoilerLevel = SpoilerLevel.LIGHT,
                answerType = AnswerType.Leveling,
                confidence = AnswerConfidence.Medium,
            ),
            context = ctx(),
            llm = llm,
        )

        assertEquals(0, llm.callCount)
        assertEquals("skipped", answer.llmTrace.status)
        assertTrue(answer.text.contains("低等级角色"))
        assertTrue(answer.text.contains("来源：sf2.rules, sf2.tactics"))
        assertEquals(AnswerType.Leveling, answer.answerResult.answerType)
    }

    @Test
    fun `compose with llm uses configured max token budget`() = runTest {
        val composer = AnswerComposer(maxTokensProvider = { 64 })
        val llm = CapturingLlmAdapter(response = LlmResponse(text = "短答案。"))

        val answer = composer.composeDetailed(
            decision = AnswerDecision.ComposeWithLlm(
                prompt = "请综合证据回答。",
                evidence = listOf(evidence("sample.2048.rules", "两个相同数字滑到一起会合并。")),
                spoilerLevel = SpoilerLevel.LIGHT,
            ),
            context = ctx(),
            llm = llm,
        )

        assertEquals(64, llm.lastRequest?.maxTokens)
        assertEquals(64, answer.llmTrace.maxTokens)
    }

    @Test
    fun `compose with llm clamps configured max token budget`() = runTest {
        val lowBudgetLlm = CapturingLlmAdapter(response = LlmResponse(text = "短答案。"))
        AnswerComposer(maxTokensProvider = { 1 }).composeDetailed(
            decision = AnswerDecision.ComposeWithLlm(
                prompt = "请综合证据回答。",
                evidence = listOf(evidence("sample.2048.rules", "两个相同数字滑到一起会合并。")),
                spoilerLevel = SpoilerLevel.LIGHT,
            ),
            context = ctx(),
            llm = lowBudgetLlm,
        )

        val highBudgetLlm = CapturingLlmAdapter(response = LlmResponse(text = "短答案。"))
        AnswerComposer(maxTokensProvider = { 9_999 }).composeDetailed(
            decision = AnswerDecision.ComposeWithLlm(
                prompt = "请综合证据回答。",
                evidence = listOf(evidence("sample.2048.rules", "两个相同数字滑到一起会合并。")),
                spoilerLevel = SpoilerLevel.LIGHT,
            ),
            context = ctx(),
            llm = highBudgetLlm,
        )

        assertEquals(32, lowBudgetLlm.lastRequest?.maxTokens)
        assertEquals(2048, highBudgetLlm.lastRequest?.maxTokens)
    }

    @Test
    fun `compose with llm failure returns visible failure answer and failed trace`() = runTest {
        val llm = FailingLlmAdapter()

        val answer = composer.composeDetailed(
            decision = AnswerDecision.ComposeWithLlm(
                prompt = "请综合证据回答。",
                evidence = listOf(evidence("sample.2048.rules", "两个相同数字滑到一起会合并。")),
                spoilerLevel = SpoilerLevel.LIGHT,
            ),
            context = ctx(),
            llm = llm,
        )

        assertTrue(answer.text.contains("LLM 调用失败"))
        assertTrue(answer.text.contains("来源：sample.2048.rules"))
        assertEquals("failed", answer.llmTrace.status)
        assertEquals("capturing", answer.llmTrace.providerName)
        assertEquals("capturing-model", answer.llmTrace.modelName)
        assertEquals(256, answer.llmTrace.maxTokens)
        assertEquals(30_000L, answer.llmTrace.timeoutMs)
        assertTrue(answer.llmTrace.errorMessage.orEmpty().contains("timeout"))
    }

    @Test
    fun `compose with empty evidence does not call llm`() = runTest {
        val llm = CapturingLlmAdapter(
            response = LlmResponse(text = "should not happen"),
        )
        val text = composer.compose(
            decision = AnswerDecision.ComposeWithLlm(
                prompt = "ignored",
                evidence = emptyList(),
                spoilerLevel = SpoilerLevel.LIGHT,
            ),
            context = ctx(),
            llm = llm,
        )

        assertEquals(0, llm.callCount)
        assertTrue(text.contains("没有足够证据"))
    }

    private fun ctx(): SessionContext = SessionContext(
        gameIdentity = GameIdentity(
            gameId = "2048",
            title = "2048",
            platform = "libretro",
            region = null,
            source = "gkp_index",
        ),
        playerQuestion = "怎么合并？",
        screenshotBase64 = null,
        state = ControllerState.EMPTY,
        spoilerLevel = SpoilerLevel.LIGHT,
        language = "zh",
        recentTurns = emptyList(),
        questionIntent = AnswerType.Mechanic,
    )

    private fun evidence(sourceId: String, snippet: String): Evidence =
        Evidence(
            sourceId = sourceId,
            snippet = snippet,
            score = 0.9,
            spoilerLevel = SpoilerLevel.LIGHT,
            progressGate = null,
        )

    private class CapturingLlmAdapter(
        private val response: LlmResponse,
    ) : LlmAdapter {
        override val providerName: String = "capturing"
        override val modelName: String = "capturing-model"
        override val timeoutMs: Long = 30_000L
        var callCount: Int = 0
            private set
        var lastRequest: LlmRequest? = null
            private set

        override suspend fun complete(request: LlmRequest): LlmResponse {
            callCount += 1
            lastRequest = request
            return response
        }
    }

    private class FailingLlmAdapter : LlmAdapter {
        override val providerName: String = "capturing"
        override val modelName: String = "capturing-model"
        override val timeoutMs: Long = 30_000L

        override suspend fun complete(request: LlmRequest): LlmResponse {
            error("timeout while waiting for provider")
        }
    }
}
