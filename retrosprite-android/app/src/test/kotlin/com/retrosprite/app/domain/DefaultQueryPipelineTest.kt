package com.retrosprite.app.domain

import com.retrosprite.app.domain.policy.AnswerComposer
import com.retrosprite.app.domain.policy.FixedTextAnswerPolicy
import com.retrosprite.app.domain.resolver.LabelGameResolver
import com.retrosprite.app.domain.retrieval.NoOpRetrievalPipeline
import com.retrosprite.app.llm.MockLlmAdapter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    // The endpoint↔domain bridge moved to
    // com.retrosprite.app.endpoint.QueryPipelineResponseGenerator and is
    // covered by QueryPipelineResponseGeneratorTest in that package.
}
