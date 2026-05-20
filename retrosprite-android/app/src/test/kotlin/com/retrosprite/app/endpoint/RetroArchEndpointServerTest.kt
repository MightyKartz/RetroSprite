package com.retrosprite.app.endpoint

import com.retrosprite.app.endpoint.model.RetroArchResponse
import com.retrosprite.app.endpoint.model.DebugLatestRequestResponse
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end tests for the Ktor module wired by [retroArchModule].
 *
 * Uses `ktor-server-test-host` to mount the same module in-process — no real socket bind —
 * so the engine, ContentNegotiation plugin, and route handlers are exercised exactly as on
 * device. Avoids touching the foreground service or [EndpointController].
 */
@OptIn(ExperimentalSerializationApi::class)
class RetroArchEndpointServerTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = false
        explicitNulls = false
    }

    @Test
    fun `health endpoint reports ok and version`() = testApplication {
        application { retroArchModule(PlaceholderResponseGenerator(), RequestLogger()) }
        val resp = client.get("/health")
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"status\":\"ok\""))
        assertTrue(body.contains("\"version\":\"0.1.0\""))
    }

    @Test
    fun `post root returns placeholder text and logs entry`() = testApplication {
        val logger = RequestLogger()
        application { retroArchModule(PlaceholderResponseGenerator(), logger) }

        val payload = """
            {
              "image": "aGVsbG8=",
              "label": "snes__zelda",
              "state": { "paused": 1, "a": 1 }
            }
        """.trimIndent()

        val resp = client.post("/?output=text") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        val parsed = json.decodeFromString(RetroArchResponse.serializer(), resp.bodyAsText())
        assertEquals(PlaceholderResponseGenerator.DEFAULT_MESSAGE, parsed.text)
        assertNull(parsed.error)

        val entries = logger.entries.value
        assertEquals(1, entries.size)
        val entry = entries.first()
        assertEquals("snes", entry.system)
        assertEquals("zelda", entry.game)
        assertTrue(entry.paused)
        assertEquals("text", entry.outputMode)
    }

    @Test
    fun `post root notifies hotkey listener with RetroArch context`() = testApplication {
        val logger = RequestLogger()
        val events = mutableListOf<RetroArchHotkeyEvent>()
        val listener = RetroArchHotkeyListener { event -> events += event }
        application { retroArchModule(PlaceholderResponseGenerator(), logger, listener) }

        val resp = client.post("/?output=text") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "image": "dGVzdA==",
                  "label": "mega_drive__光明力量2",
                  "state": { "paused": 0 }
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        assertEquals(1, events.size)
        val event = events.first()
        assertEquals("mega_drive__光明力量2", event.label)
        assertEquals("text", event.outputMode)
        assertEquals(4, event.imageBytes)
        assertEquals(false, event.paused)
    }

    @Test
    fun `debug ask does not notify hotkey listener`() = testApplication {
        val logger = RequestLogger()
        val events = mutableListOf<RetroArchHotkeyEvent>()
        val listener = RetroArchHotkeyListener { event -> events += event }
        application { retroArchModule(PlaceholderResponseGenerator(), logger, listener) }

        val resp = client.post("/debug/ask?output=text") {
            contentType(ContentType.Application.Json)
            setBody("""{"label":"2048__","question":"两个 2 怎么合并？","state":{"paused":1}}""")
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        assertEquals(emptyList<RetroArchHotkeyEvent>(), events)
    }

    @Test
    fun `post root accepts RetroArch form content type with json body`() = testApplication {
        val logger = RequestLogger()
        application { retroArchModule(PlaceholderResponseGenerator(), logger) }

        val payload = """
            {
              "image": "aGVsbG8=",
              "format": "png",
              "label": "nes__2048",
              "state": { "paused": 1, "a": 0, "b": 0 }
            }
        """.trimIndent()

        val resp = client.post("/?output=text") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(payload)
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        val parsed = json.decodeFromString(RetroArchResponse.serializer(), resp.bodyAsText())
        assertEquals(PlaceholderResponseGenerator.DEFAULT_MESSAGE, parsed.text)
        assertNull(parsed.error)

        val entry = logger.entries.value.first()
        assertEquals("nes", entry.system)
        assertEquals("2048", entry.game)
        assertTrue(entry.paused)
    }

    @Test
    fun `malformed json returns 200 with error field`() = testApplication {
        val logger = RequestLogger()
        application { retroArchModule(PlaceholderResponseGenerator(), logger) }

        val resp = client.post("/?output=text") {
            contentType(ContentType.Application.Json)
            setBody("{not valid json")
        }

        assertEquals(
            "RetroArch must always see HTTP 200, never 4xx/5xx",
            HttpStatusCode.OK,
            resp.status,
        )
        val parsed = json.decodeFromString(RetroArchResponse.serializer(), resp.bodyAsText())
        assertNotNull(parsed.error)
        assertNull(parsed.text)
        assertEquals(1, logger.entries.value.size)
        assertNotNull(logger.entries.value.first().errorMessage)
    }

    @Test
    fun `partial state body uses defaults`() = testApplication {
        val logger = RequestLogger()
        application { retroArchModule(PlaceholderResponseGenerator(), logger) }

        val resp = client.post("/?output=text") {
            contentType(ContentType.Application.Json)
            setBody("""{"image":"x","label":"snes__game","state":{"paused":0}}""")
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        val parsed = json.decodeFromString(RetroArchResponse.serializer(), resp.bodyAsText())
        assertEquals(PlaceholderResponseGenerator.DEFAULT_MESSAGE, parsed.text)
        val entry = logger.entries.value.first()
        assertEquals("snes", entry.system)
        assertEquals("game", entry.game)
        assertTrue(!entry.paused)
    }

    @Test
    fun `missing output query parameter defaults to text mode`() = testApplication {
        val logger = RequestLogger()
        application { retroArchModule(PlaceholderResponseGenerator(), logger) }

        val resp = client.post("/") {
            contentType(ContentType.Application.Json)
            setBody("""{"image":"x","label":"snes__game","state":{}}""")
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        assertEquals("text", logger.entries.value.first().outputMode)
    }

    @Test
    fun `debug ask route forwards question and marks debug log entry`() = testApplication {
        val logger = RequestLogger()
        val generator = ResponseGenerator { request, outputMode ->
            RetroArchResponse.text("${request.label}|${request.question}|$outputMode")
        }
        application { retroArchModule(generator, logger) }

        val resp = client.post("/debug/ask?output=text") {
            contentType(ContentType.Application.Json)
            setBody("""{"label":"2048__","question":"两个 2 怎么合并？","state":{"paused":1}}""")
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        val parsed = json.decodeFromString(RetroArchResponse.serializer(), resp.bodyAsText())
        assertEquals("2048__|两个 2 怎么合并？|text", parsed.text)
        assertNull(parsed.error)
        val entry = logger.entries.value.first()
        assertEquals("debug:text", entry.outputMode)
        assertTrue(entry.paused)
        assertEquals("两个 2 怎么合并？", entry.question)
        assertEquals("debug", entry.questionSource)
    }

    @Test
    fun `debug latest request returns latest diagnostic summary`() = testApplication {
        val logger = RequestLogger()
        val generator = ResponseGenerator { _, _ ->
            RetroArchResponse.text("两个相同数字滑到一起会合并。\n来源：sample.2048.rules")
        }
        application { retroArchModule(generator, logger) }

        client.post("/debug/ask?output=text") {
            contentType(ContentType.Application.Json)
            setBody("""{"label":"2048__","question":"两个 2 怎么合并？","state":{"paused":1}}""")
        }

        val resp = client.get("/debug/latest-request")

        assertEquals(HttpStatusCode.OK, resp.status)
        val parsed = json.decodeFromString(DebugLatestRequestResponse.serializer(), resp.bodyAsText())
        assertTrue(parsed.has_entry)
        assertEquals("2048__", parsed.label)
        assertEquals("debug:text", parsed.output_mode)
        assertEquals(true, parsed.is_debug)
        assertEquals(true, parsed.paused)
        assertEquals("两个 2 怎么合并？", parsed.question)
        assertEquals("debug", parsed.question_source)
        assertEquals(true, parsed.ok)
        assertEquals("evidence", parsed.pipeline_stage)
        assertEquals("skipped", parsed.llm_status)
        assertEquals(listOf("sample.2048.rules"), parsed.source_ids)
    }

    @Test
    fun `debug latest request includes pending hotkey question metadata`() = testApplication {
        val logger = RequestLogger()
        val generator = ResponseGenerator { _, _ ->
            RetroArchResponse.text(
                content = "answer",
                diagnostics = com.retrosprite.app.endpoint.model.ResponseDiagnostics(
                    question = "queued question",
                    questionSource = "pending_hotkey",
                )
            )
        }
        application { retroArchModule(generator, logger) }

        client.post("/?output=text") {
            contentType(ContentType.Application.Json)
            setBody("""{"label":"2048__","state":{"paused":1}}""")
        }

        val resp = client.get("/debug/latest-request")
        val parsed = json.decodeFromString(DebugLatestRequestResponse.serializer(), resp.bodyAsText())

        assertEquals("queued question", logger.entries.value.first().question)
        assertEquals("pending_hotkey", logger.entries.value.first().questionSource)
        assertEquals("queued question", parsed.question)
        assertEquals("pending_hotkey", parsed.question_source)
    }

    @Test
    fun `debug latest request returns empty object before any request`() = testApplication {
        application { retroArchModule(PlaceholderResponseGenerator(), RequestLogger()) }

        val resp = client.get("/debug/latest-request")

        assertEquals(HttpStatusCode.OK, resp.status)
        val parsed = json.decodeFromString(DebugLatestRequestResponse.serializer(), resp.bodyAsText())
        assertTrue(!parsed.has_entry)
        assertNull(parsed.label)
        assertEquals(emptyList<String>(), parsed.source_ids)
    }

    @Test
    fun `debug ask route requires question`() = testApplication {
        val logger = RequestLogger()
        application { retroArchModule(PlaceholderResponseGenerator(), logger) }

        val resp = client.post("/debug/ask?output=text") {
            contentType(ContentType.Application.Json)
            setBody("""{"label":"2048__","state":{}}""")
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        val parsed = json.decodeFromString(RetroArchResponse.serializer(), resp.bodyAsText())
        assertNotNull(parsed.error)
        assertNull(parsed.text)
        val entry = logger.entries.value.first()
        assertEquals("debug:text", entry.outputMode)
        assertEquals("missing_debug_question", entry.errorMessage)
    }

    @Test
    fun `generator failure surfaces as protocol error not transport error`() = runTest {
        testApplication {
            val logger = RequestLogger()
            val failing = ResponseGenerator { _, _ -> throw IllegalStateException("nope") }
            application { retroArchModule(failing, logger) }

            val resp = client.post("/?output=text") {
                contentType(ContentType.Application.Json)
                setBody("""{"image":"x","label":"snes__game","state":{}}""")
            }

            assertEquals(HttpStatusCode.OK, resp.status)
            val parsed = json.decodeFromString(RetroArchResponse.serializer(), resp.bodyAsText())
            assertNotNull(parsed.error)
            assertEquals(1, logger.entries.value.size)
            assertNotNull(logger.entries.value.first().errorMessage)
        }
    }

    @Test
    fun `decodedBase64Length matches actual decoded byte length`() {
        // "RetroSprite" -> 11 bytes -> Base64 "UmV0cm9TcHJpdGU=" (16 chars, 1 padding)
        assertEquals(11, RequestLogger.decodedBase64Length("UmV0cm9TcHJpdGU="))
        // 6 bytes -> "aGVsbG8h" (8 chars, 0 padding) — wait: "hello!" = 6 bytes -> length 8, no padding
        assertEquals(6, RequestLogger.decodedBase64Length("aGVsbG8h"))
        // 4 bytes -> 8 chars with 0 padding ("hell" -> aGVsbA==): length 8, 2 padding -> 4
        assertEquals(4, RequestLogger.decodedBase64Length("aGVsbA=="))
        assertEquals(0, RequestLogger.decodedBase64Length(""))
    }
}
