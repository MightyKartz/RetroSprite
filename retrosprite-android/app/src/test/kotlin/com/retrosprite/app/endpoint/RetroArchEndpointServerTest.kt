package com.retrosprite.app.endpoint

import com.retrosprite.app.endpoint.model.RetroArchResponse
import com.retrosprite.app.endpoint.model.DebugHotkeyVoiceOverlayResponse
import com.retrosprite.app.endpoint.model.DebugLatestRequestResponse
import com.retrosprite.app.endpoint.model.ResponseDiagnostics
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
    fun `regular hotkey voice output ignores request question injection`() = testApplication {
        val logger = RequestLogger()
        val events = mutableListOf<RetroArchHotkeyEvent>()
        val listener = RetroArchHotkeyListener { event -> events += event }
        application { retroArchModule(PlaceholderResponseGenerator(), logger, listener) }

        val resp = client.post("/?output=hotkey_voice:text") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "image": "dGVzdA==",
                  "label": "snes__Final Fantasy VI",
                  "question": "翻译",
                  "state": { "paused": 1 }
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        assertEquals(1, events.size)
        assertEquals("", events.single().injectedQuestion)
    }

    @Test
    fun `debug hotkey voice output carries an injected question for qa only`() = testApplication {
        val logger = RequestLogger()
        val events = mutableListOf<RetroArchHotkeyEvent>()
        val listener = RetroArchHotkeyListener { event -> events += event }
        application { retroArchModule(PlaceholderResponseGenerator(), logger, listener) }

        val resp = client.post("/?output=hotkey_voice_debug:text") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "image": "dGVzdA==",
                  "label": "snes__Final Fantasy VI",
                  "question": "翻译",
                  "state": { "paused": 1 }
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        assertEquals(1, events.size)
        assertEquals("翻译", events.single().injectedQuestion)
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
            RetroArchResponse.text(
                content = "两个相同数字滑到一起会合并。\n来源：本地知识",
                diagnostics = ResponseDiagnostics(sourceIds = listOf("sample.2048.rules")),
            )
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
    fun `debug latest request includes suggested questions from diagnostics`() = testApplication {
        val logger = RequestLogger()
        val generator = ResponseGenerator { _, _ ->
            RetroArchResponse.text(
                content = "answer",
                diagnostics = com.retrosprite.app.endpoint.model.ResponseDiagnostics(
                    answerShort = "short",
                    suggestedQuestions = listOf("气合之玉在哪里？", "谁适合转 Master Monk？"),
                )
            )
        }
        application { retroArchModule(generator, logger) }

        client.post("/debug/ask?output=text") {
            contentType(ContentType.Application.Json)
            setBody("""{"label":"2048__","question":"气合之玉怎么用","state":{"paused":1}}""")
        }

        val parsed = json.decodeFromString(
            DebugLatestRequestResponse.serializer(),
            client.get("/debug/latest-request").bodyAsText(),
        )

        assertEquals(
            listOf("气合之玉在哪里？", "谁适合转 Master Monk？"),
            logger.entries.value.first().suggestedQuestions,
        )
        assertEquals(
            listOf("气合之玉在哪里？", "谁适合转 Master Monk？"),
            parsed.suggested_questions,
        )
    }

    @Test
    fun `silent hotkey wake notifies listener but does not replace latest request`() = testApplication {
        val logger = RequestLogger()
        val events = mutableListOf<RetroArchHotkeyEvent>()
        val listener = RetroArchHotkeyListener { event -> events += event }
        val generator = ResponseGenerator { _, _ -> RetroArchResponse.text("") }
        application { retroArchModule(generator, logger, listener) }

        client.post("/debug/ask?output=text") {
            contentType(ContentType.Application.Json)
            setBody("""{"label":"2048__","question":"两个 2 怎么合并？","state":{"paused":1}}""")
        }
        val before = client.get("/debug/latest-request").bodyAsText()

        val wake = client.post("/?output=text") {
            contentType(ContentType.Application.Json)
            setBody("""{"label":"mega_drive__光明力量2","state":{"paused":1}}""")
        }

        assertEquals(HttpStatusCode.OK, wake.status)
        assertEquals(1, events.size)
        assertEquals("mega_drive__光明力量2", events.first().label)
        assertEquals(1, logger.entries.value.size)
        assertEquals(before, client.get("/debug/latest-request").bodyAsText())
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
    fun `debug hotkey voice overlay route returns lifecycle snapshot`() = testApplication {
        application {
            retroArchModule(
                responseGenerator = PlaceholderResponseGenerator(),
                requestLogger = RequestLogger(),
                hotkeyVoiceOverlayDebugProvider = {
                    DebugHotkeyVoiceOverlayResponse(
                        lifecycle_phase = "finished",
                        lifecycle_phase_label = "Finished",
                        is_active = false,
                        is_visible = false,
                        label = "mega_drive__光明力量2",
                        render_phase = "speaking",
                        render_phase_label = "Answering",
                        mic_live = false,
                        source_ids = listOf("sf2.manual_translation"),
                        asr_architecture = "paraformer",
                        asr_decoding_method = "greedy_search",
                        asr_modeling_unit = null,
                        asr_commit_reason = "soft_stop_after_silence_and_stable_partial",
                        asr_last_partial = "修伊是谁",
                        asr_final_text = "修伊是",
                        asr_selected_transcript = "修伊是谁",
                        asr_post_voice_silence_ms = 1_000L,
                        asr_partial_stable_ms = 900L,
                        asr_required_stable_ms = 650L,
                        asr_endpoint_armed = true,
                        asr_final_flush_ms = 2_000L,
                        asr_sample_count = 48_000L,
                        asr_audio_read_count = 12L,
                        asr_audio_read_error_count = 0L,
                        asr_peak_amplitude = 0.18f,
                        asr_last_frame_amplitude = 0.04f,
                        started_at = 10_000L,
                        finished_at = 12_345L,
                        finish_reason = "answer_completed",
                    )
                },
            )
        }

        val resp = client.get("/debug/hotkey-voice-overlay")

        assertEquals(HttpStatusCode.OK, resp.status)
        val parsed = json.decodeFromString(
            DebugHotkeyVoiceOverlayResponse.serializer(),
            resp.bodyAsText(),
        )
        assertEquals("finished", parsed.lifecycle_phase)
        assertEquals("Finished", parsed.lifecycle_phase_label)
        assertEquals(false, parsed.is_active)
        assertEquals(false, parsed.is_visible)
        assertEquals("mega_drive__光明力量2", parsed.label)
        assertEquals("speaking", parsed.render_phase)
        assertEquals("Answering", parsed.render_phase_label)
        assertEquals(false, parsed.mic_live)
        assertEquals(listOf("sf2.manual_translation"), parsed.source_ids)
        assertEquals("paraformer", parsed.asr_architecture)
        assertEquals("greedy_search", parsed.asr_decoding_method)
        assertEquals(null, parsed.asr_modeling_unit)
        assertEquals("soft_stop_after_silence_and_stable_partial", parsed.asr_commit_reason)
        assertEquals("修伊是谁", parsed.asr_last_partial)
        assertEquals("修伊是", parsed.asr_final_text)
        assertEquals("修伊是谁", parsed.asr_selected_transcript)
        assertEquals(1_000L, parsed.asr_post_voice_silence_ms)
        assertEquals(900L, parsed.asr_partial_stable_ms)
        assertEquals(650L, parsed.asr_required_stable_ms)
        assertEquals(true, parsed.asr_endpoint_armed)
        assertEquals(2_000L, parsed.asr_final_flush_ms)
        assertEquals(48_000L, parsed.asr_sample_count)
        assertEquals(12L, parsed.asr_audio_read_count)
        assertEquals(0L, parsed.asr_audio_read_error_count)
        assertEquals(0.18f, parsed.asr_peak_amplitude)
        assertEquals(0.04f, parsed.asr_last_frame_amplitude)
        assertEquals(12_345L, parsed.finished_at)
        assertEquals("answer_completed", parsed.finish_reason)
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
