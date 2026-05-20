package com.retrosprite.app.ui.screens.diagnostics

import com.retrosprite.app.ui.viewmodel.UiOutputMode
import com.retrosprite.app.ui.viewmodel.UiRequestLogItem
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticsSourceFilterTest {

    @Test
    fun `classifies request logs by source and counts each bucket`() {
        val retroArch = item(id = "retroarch")
        val pending = item(id = "pending", questionSource = "pending_hotkey")
        val app = item(id = "app", rawOutputMode = "app:text", questionSource = "app")
        val debug = item(id = "debug", rawOutputMode = "debug:text", isDebug = true)
        val rows = listOf(retroArch, pending, app, debug)

        assertEquals(DiagnosticsSourceFilter.RetroArch, retroArch.diagnosticsSource())
        assertEquals(DiagnosticsSourceFilter.Pending, pending.diagnosticsSource())
        assertEquals(DiagnosticsSourceFilter.App, app.diagnosticsSource())
        assertEquals(DiagnosticsSourceFilter.Debug, debug.diagnosticsSource())

        val counts = rows.diagnosticsSourceCounts()
        assertEquals(4, counts.all)
        assertEquals(1, counts.retroArch)
        assertEquals(1, counts.pending)
        assertEquals(1, counts.app)
        assertEquals(1, counts.debug)
        assertEquals(listOf(pending), rows.filterByDiagnosticsSource(DiagnosticsSourceFilter.Pending))
    }

    @Test
    fun `pending question source wins over plain RetroArch output mode`() {
        val row = item(
            id = "pending-hotkey",
            rawOutputMode = "text",
            questionSource = "pending_hotkey",
        )

        assertEquals(DiagnosticsSourceFilter.Pending, row.diagnosticsSource())
    }

    private fun item(
        id: String,
        rawOutputMode: String = "text",
        questionSource: String? = null,
        isDebug: Boolean = false,
    ): UiRequestLogItem = UiRequestLogItem(
        id = id,
        timestampMillis = 1L,
        label = "2048__",
        imageBytes = 0,
        paused = true,
        outputMode = UiOutputMode.Text,
        responsePreview = "ok",
        fullResponseJson = """{"text":"ok"}""",
        durationMillis = 1L,
        ok = true,
        rawOutputMode = rawOutputMode,
        questionSource = questionSource,
        isDebug = isDebug,
    )
}
