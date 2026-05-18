package com.retrosprite.app.ui.integration

import com.retrosprite.app.endpoint.EndpointStatus
import com.retrosprite.app.endpoint.RequestLogEntry
import com.retrosprite.app.ui.viewmodel.UiEndpointPhase
import com.retrosprite.app.ui.viewmodel.UiOutputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiModelMappersTest {

    // ---- EndpointStatus → UiEndpointStatus -------------------------------

    @Test
    fun `running endpoint reports actual port`() {
        val ui = EndpointStatus.Running(port = 9090).toUi(fallbackPort = 8080)
        assertEquals(UiEndpointPhase.Running, ui.phase)
        assertEquals(9090, ui.port)
        assertEquals("http://127.0.0.1:9090", ui.baseUrl)
        assertNull(ui.message)
    }

    @Test
    fun `stopped endpoint falls back to configured port`() {
        val ui = EndpointStatus.Stopped.toUi(fallbackPort = 8123)
        assertEquals(UiEndpointPhase.Stopped, ui.phase)
        assertEquals(8123, ui.port)
        assertEquals("http://127.0.0.1:8123", ui.baseUrl)
    }

    @Test
    fun `error endpoint surfaces message verbatim`() {
        val ui = EndpointStatus.Error("port_in_use").toUi(fallbackPort = 8080)
        assertEquals(UiEndpointPhase.Error, ui.phase)
        assertEquals("port_in_use", ui.message)
    }

    // ---- RequestLogEntry → UiRequestLogItem -------------------------------

    @Test
    fun `successful entry maps cleanly`() {
        val entry = RequestLogEntry(
            id = "row-42",
            timestamp = 1_700_000_000_000L,
            label = "snes__super_mario_world",
            system = "snes",
            game = "super_mario_world",
            imageBytes = 2_048,
            paused = true,
            outputMode = "text",
            responseText = "RetroSprite 已连接",
            errorMessage = null,
        )

        val ui = entry.toUi()

        assertEquals("row-42", ui.id)
        assertEquals(1_700_000_000_000L, ui.timestampMillis)
        assertEquals("snes__super_mario_world", ui.label)
        assertEquals(2_048, ui.imageBytes)
        assertTrue(ui.paused)
        assertEquals(UiOutputMode.Text, ui.outputMode)
        assertEquals("RetroSprite 已连接", ui.responsePreview)
        assertEquals("""{"text":"RetroSprite 已连接"}""", ui.fullResponseJson)
        assertEquals(0L, ui.durationMillis)
        assertTrue(ui.ok)
    }

    @Test
    fun `error entry produces error preview and false ok`() {
        val entry = RequestLogEntry(
            id = "row-7",
            timestamp = 0L,
            label = "",
            system = "",
            game = "",
            imageBytes = 0,
            paused = false,
            outputMode = "text",
            responseText = "",
            errorMessage = "malformed_request: not json",
        )

        val ui = entry.toUi()

        assertFalse(ui.ok)
        assertEquals("[error] malformed_request: not json", ui.responsePreview)
        assertEquals("""{"error":"malformed_request: not json"}""", ui.fullResponseJson)
        assertNull("empty label and empty system+game must collapse to null", ui.label)
    }

    @Test
    fun `long response is truncated with ellipsis`() {
        val long = "a".repeat(120)
        val entry = RequestLogEntry(
            label = "x", system = "x", game = "x",
            imageBytes = 0, paused = false, outputMode = "text",
            responseText = long, errorMessage = null,
        )
        val ui = entry.toUi()
        assertEquals(81, ui.responsePreview.length) // 80 chars + ellipsis
        assertTrue(ui.responsePreview.endsWith("…"))
    }

    @Test
    fun `output mode parsing collapses combinations to mixed`() {
        assertEquals(UiOutputMode.Text, "text".toUiOutputMode())
        assertEquals(UiOutputMode.Image, "image".toUiOutputMode())
        assertEquals(UiOutputMode.Sound, "sound".toUiOutputMode())
        assertEquals(UiOutputMode.Mixed, "text|sound".toUiOutputMode())
        assertEquals(UiOutputMode.Mixed, "text,image".toUiOutputMode())
        assertEquals(UiOutputMode.Text, "unknown_mode".toUiOutputMode())
        assertEquals(UiOutputMode.Text, "".toUiOutputMode())
    }

    @Test
    fun `quotes and backslashes in response are escaped in fullResponseJson`() {
        val entry = RequestLogEntry(
            label = "x", system = "x", game = "x",
            imageBytes = 0, paused = false, outputMode = "text",
            responseText = """She said "hi" and \o/""",
            errorMessage = null,
        )
        val ui = entry.toUi()
        assertEquals(
            """{"text":"She said \"hi\" and \\o/"}""",
            ui.fullResponseJson,
        )
    }
}
