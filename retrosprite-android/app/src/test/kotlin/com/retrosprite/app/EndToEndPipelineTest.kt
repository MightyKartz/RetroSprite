package com.retrosprite.app

import com.retrosprite.app.domain.DefaultQueryPipeline
import com.retrosprite.app.domain.QueryPipeline
import com.retrosprite.app.domain.policy.AnswerComposer
import com.retrosprite.app.domain.policy.FixedTextAnswerPolicy
import com.retrosprite.app.domain.resolver.LabelGameResolver
import com.retrosprite.app.domain.retrieval.NoOpRetrievalPipeline
import com.retrosprite.app.endpoint.QueryPipelineResponseGenerator
import com.retrosprite.app.endpoint.RequestLogger
import com.retrosprite.app.endpoint.ResponseGenerator
import com.retrosprite.app.endpoint.model.RetroArchResponse
import com.retrosprite.app.endpoint.retroArchModule
import com.retrosprite.app.llm.MockLlmAdapter
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end protocol test that walks the full Phase 0 wiring:
 *
 *   HTTP request → Ktor route → [QueryPipelineResponseGenerator]
 *               → [DefaultQueryPipeline]
 *                   → [LabelGameResolver] + [NoOpRetrievalPipeline]
 *                   → [FixedTextAnswerPolicy] + [AnswerComposer]
 *                   → [MockLlmAdapter] (never reached for Phase 0 fixed policy)
 *               → JSON response
 *               → [RequestLogger] (in-memory sink)
 *
 * Deliberately avoids the real [ServiceLocator] / [com.retrosprite.app.endpoint.RoomBackedRequestLogSink]
 * (those need an Android `Context` / Robolectric). The graph below is the same set of
 * collaborators wired by `ServiceLocator.Graph`, minus the Room persistence layer.
 *
 * If this test passes, the entire Phase 0 protocol path is verified at the JVM level.
 */
@OptIn(ExperimentalSerializationApi::class)
class EndToEndPipelineTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = false
        explicitNulls = false
    }

    /**
     * Lightweight, Context-free analogue of [ServiceLocator] used purely by unit tests.
     * Exposes the same collaborators the production graph builds, but skips Room / DataStore.
     */
    private class LightServiceLocator {
        val pipeline: QueryPipeline = DefaultQueryPipeline(
            resolver = LabelGameResolver(),
            retrieval = NoOpRetrievalPipeline(),
            policy = FixedTextAnswerPolicy(),
            composer = AnswerComposer(),
            llm = MockLlmAdapter(),
        )
        val responseGenerator: ResponseGenerator = QueryPipelineResponseGenerator(pipeline)
        val requestLogger: RequestLogger = RequestLogger()
    }

    @Test
    fun `realistic retroarch request flows end to end and is logged`() = testApplication {
        val sl = LightServiceLocator()
        application { retroArchModule(sl.responseGenerator, sl.requestLogger) }

        val payload = """
            {
              "image": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=",
              "label": "snes__super_mario_world",
              "state": {
                "paused": 1,
                "a": 0, "b": 0, "x": 0, "y": 0,
                "select": 0, "start": 1,
                "up": 0, "down": 0, "left": 0, "right": 0,
                "l": 0, "r": 0, "l2": 0, "r2": 0, "l3": 0, "r3": 0
              }
            }
        """.trimIndent()

        val resp = client.post("/?output=text") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }

        // ---- HTTP-level assertions ----
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        val parsed = json.decodeFromString(RetroArchResponse.serializer(), body)

        assertNull("success path has no error", parsed.error)
        assertNotNull("text field must be set", parsed.text)
        assertEquals(FixedTextAnswerPolicy.PHASE_0_ACK_TEXT, parsed.text)
        assertTrue(
            "Phase 0 ack text must mention RetroSprite",
            parsed.text!!.contains("RetroSprite"),
        )

        // ---- Logger-level assertions ----
        val entries = sl.requestLogger.entries.value
        assertEquals("exactly one log entry expected", 1, entries.size)
        val entry = entries.first()
        assertEquals("snes", entry.system)
        assertEquals("super_mario_world", entry.game)
        assertTrue("paused must reflect state.paused=1", entry.paused)
        assertEquals("text", entry.outputMode)
        assertNull("no error on success path", entry.errorMessage)
        assertEquals(parsed.text, entry.responseText)
    }

    @Test
    fun `request without query params or state still produces ack and one log row`() = testApplication {
        val sl = LightServiceLocator()
        application { retroArchModule(sl.responseGenerator, sl.requestLogger) }

        val resp = client.post("/") {
            contentType(ContentType.Application.Json)
            setBody("""{"image":"","label":"","state":{}}""")
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        val parsed = json.decodeFromString(RetroArchResponse.serializer(), resp.bodyAsText())
        assertEquals(FixedTextAnswerPolicy.PHASE_0_ACK_TEXT, parsed.text)
        assertEquals(1, sl.requestLogger.entries.value.size)
        assertEquals("text", sl.requestLogger.entries.value.first().outputMode)
    }

    @Test
    fun `health endpoint reports ok without touching the pipeline`() = testApplication {
        val sl = LightServiceLocator()
        application { retroArchModule(sl.responseGenerator, sl.requestLogger) }

        val resp = client.get("/health")
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(resp.bodyAsText().contains("\"status\":\"ok\""))
        // Health probes must not generate request log entries.
        assertEquals(0, sl.requestLogger.entries.value.size)
    }
}
