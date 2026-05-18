package com.retrosprite.app.endpoint

import com.retrosprite.app.endpoint.model.RetroArchRequest
import com.retrosprite.app.endpoint.model.RetroArchResponse
import com.retrosprite.app.endpoint.model.RetroArchState
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that the RetroArch protocol DTOs round-trip cleanly through kotlinx-serialization
 * **and** that they tolerate the partial / forward-compatible payloads we expect to receive
 * from real RetroArch builds in the wild.
 */
class RetroArchModelTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = false
        explicitNulls = false
    }

    @Test
    fun `state defaults are all zero`() {
        val state = RetroArchState()
        assertEquals(0, state.paused)
        assertFalse(state.isPaused)
        listOf(
            state.a, state.b, state.x, state.y,
            state.select, state.start,
            state.up, state.down, state.left, state.right,
            state.l, state.r, state.l2, state.r2, state.l3, state.r3,
        ).forEach { assertEquals(0, it) }
    }

    @Test
    fun `request deserializes full RetroArch payload`() {
        val payload = """
            {
              "image": "iVBORw0KGgo=",
              "label": "snes__zelda",
              "state": {
                "paused": 1, "a": 0, "b": 1, "x": 0, "y": 0,
                "select": 0, "start": 0,
                "up": 1, "down": 0, "left": 0, "right": 0,
                "l": 0, "r": 0, "l2": 0, "r2": 0, "l3": 0, "r3": 0
              }
            }
        """.trimIndent()

        val req = json.decodeFromString(RetroArchRequest.serializer(), payload)
        assertEquals("iVBORw0KGgo=", req.image)
        assertEquals("snes__zelda", req.label)
        assertTrue(req.state.isPaused)
        assertEquals(1, req.state.b)
        assertEquals(1, req.state.up)
    }

    @Test
    fun `request tolerates missing state fields and unknown keys`() {
        val payload = """
            {
              "image": "x",
              "label": "snes__game",
              "state": { "paused": 1 },
              "future_field": "ignored"
            }
        """.trimIndent()

        val req = json.decodeFromString(RetroArchRequest.serializer(), payload)
        assertEquals(1, req.state.paused)
        assertEquals(0, req.state.a)
        assertEquals(0, req.state.r3)
    }

    @Test
    fun `response factory builds text payload and omits nulls when serialized`() {
        val resp = RetroArchResponse.text("hello")
        val encoded = json.encodeToString(RetroArchResponse.serializer(), resp)
        assertTrue("expected text field present", encoded.contains("\"text\""))
        assertFalse("nullable fields should be omitted", encoded.contains("\"image\""))
        assertFalse(encoded.contains("\"error\""))
    }

    @Test
    fun `response error factory produces error-only payload`() {
        val resp = RetroArchResponse.error("boom")
        val encoded = json.encodeToString(RetroArchResponse.serializer(), resp)
        assertTrue(encoded.contains("\"error\""))
        assertFalse(encoded.contains("\"text\""))
    }

    @Test
    fun `response decodes optional fields as null when absent`() {
        val resp = json.decodeFromString(
            RetroArchResponse.serializer(),
            """{"text":"hi"}""",
        )
        assertNotNull(resp.text)
        assertNull(resp.image)
        assertNull(resp.error)
        assertNull(resp.text_position)
    }
}
