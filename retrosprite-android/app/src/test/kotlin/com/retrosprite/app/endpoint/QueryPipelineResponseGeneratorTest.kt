package com.retrosprite.app.endpoint

import com.retrosprite.app.domain.policy.AnswerComposer
import com.retrosprite.app.domain.policy.FixedTextAnswerPolicy
import com.retrosprite.app.domain.resolver.LabelGameResolver
import com.retrosprite.app.domain.retrieval.NoOpRetrievalPipeline
import com.retrosprite.app.domain.DefaultQueryPipeline
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
