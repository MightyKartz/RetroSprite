package com.retrosprite.app.domain

import com.retrosprite.app.domain.policy.AnswerComposer
import com.retrosprite.app.domain.policy.AnswerPolicy
import com.retrosprite.app.domain.policy.FixedTextAnswerPolicy
import com.retrosprite.app.domain.resolver.LabelGameResolver
import com.retrosprite.app.domain.retrieval.NoOpRetrievalPipeline
import com.retrosprite.app.domain.retrieval.RetrievalPipeline
import com.retrosprite.app.domain.models.AnswerConfidence
import com.retrosprite.app.domain.models.AnswerDecision
import com.retrosprite.app.domain.models.AnswerType
import com.retrosprite.app.domain.models.Evidence
import com.retrosprite.app.domain.models.RetrievalQuery
import com.retrosprite.app.domain.models.RetrievalResult
import com.retrosprite.app.domain.models.SessionContext
import com.retrosprite.app.domain.models.SpoilerLevel
import com.retrosprite.app.llm.MockLlmAdapter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end test of the Phase 0 wiring: runs every collaborator with its
 * real Phase 0 implementation and checks the final string is the fixed
 * acknowledgement.
 */
class DefaultQueryPipelineTest {

    private val pipeline: QueryPipeline = DefaultQueryPipeline(
        resolver = LabelGameResolver(),
        retrieval = NoOpRetrievalPipeline(),
        policy = FixedTextAnswerPolicy(),
        composer = AnswerComposer(),
        llm = MockLlmAdapter(),
    )

    @Test
    fun `returns fixed phase 0 acknowledgement for a typical request`() = runTest {
        val text = pipeline.answer(
            label = "snes__super_mario_world",
            question = null,
            screenshot = null,
            state = mapOf("A" to 0, "B" to 1),
        )

        assertEquals(FixedTextAnswerPolicy.PHASE_0_ACK_TEXT, text)
    }

    @Test
    fun `returns fixed phase 0 text even when label is empty`() = runTest {
        val text = pipeline.answer(label = "")
        assertEquals(FixedTextAnswerPolicy.PHASE_0_ACK_TEXT, text)
    }

    @Test
    fun `returns fixed phase 0 text when state is null`() = runTest {
        val text = pipeline.answer(
            label = "nes__contra",
            state = null,
        )
        assertEquals(FixedTextAnswerPolicy.PHASE_0_ACK_TEXT, text)
    }

    @Test
    fun `passes retrieval suggested questions through policy and composer`() = runTest {
        val pipeline = DefaultQueryPipeline(
            resolver = LabelGameResolver(),
            retrieval = SuggestingRetrievalPipeline(),
            policy = SuggestionEchoPolicy(),
            composer = AnswerComposer(),
            llm = MockLlmAdapter(),
        )

        val result = pipeline.answerDetailed(
            label = "mega_drive__光明力量2",
            question = "气合之玉怎么用",
            spoilerLevel = SpoilerLevel.LIGHT,
        )

        assertTrue(result.text.contains("你还可以问："))
        assertTrue(result.text.contains("· 气合之玉在哪里？"))
        assertEquals(listOf("气合之玉在哪里？"), result.answerResult.suggestedQuestions)
    }

    // The endpoint↔domain bridge moved to
    // com.retrosprite.app.endpoint.QueryPipelineResponseGenerator and is
    // covered by QueryPipelineResponseGeneratorTest in that package.

    private class SuggestingRetrievalPipeline : RetrievalPipeline {
        override suspend fun retrieve(query: RetrievalQuery): List<RetrievalResult> =
            listOf(
                RetrievalResult(
                    entityId = "item.vigor-ball",
                    canonicalName = "Vigor Ball / 气合之玉",
                    evidence = listOf(
                        Evidence(
                            sourceId = "sf2.promotion",
                            snippet = "Vigor Ball 给 Priest 系角色用于转 Master Monk。",
                            score = 1.0,
                            spoilerLevel = SpoilerLevel.LIGHT,
                            progressGate = null,
                        )
                    ),
                    confidence = 0.99,
                )
            )

        override suspend fun suggestQuestions(
            query: RetrievalQuery,
            results: List<RetrievalResult>,
        ): List<String> = listOf("气合之玉在哪里？")

        override suspend fun normalizeQuestion(raw: String, language: String): String =
            raw.trim()
    }

    private class SuggestionEchoPolicy : AnswerPolicy {
        override suspend fun decide(
            results: List<RetrievalResult>,
            context: SessionContext,
        ): AnswerDecision =
            AnswerDecision.DirectAnswer(
                text = results.first().evidence.first().snippet,
                sources = listOf("sf2.promotion"),
                spoilerLevel = SpoilerLevel.LIGHT,
                answerType = AnswerType.Usage,
                confidence = AnswerConfidence.High,
                suggestedQuestions = context.suggestedQuestions,
            )
    }
}
