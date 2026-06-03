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

    @Test
    fun `hotkey voice no evidence explains asr and gkp gap`() {
        val row = item(
            id = "voice-no-evidence",
            rawOutputMode = "hotkey_voice:text",
            questionSource = "hotkey_voice",
            pipelineStage = "no_evidence",
            responsePreview = "我还没有足够证据回答这个问题。",
        )

        val explanations = row.diagnosticFailureExplanations().map { it.title }

        assertEquals(listOf("ASR", "GKP"), explanations)
    }

    @Test
    fun `gkp disabled explains disabled pack`() {
        val row = item(
            id = "gkp-disabled",
            pipelineStage = "gkp_disabled",
            responsePreview = "知识包已禁用",
        )

        val explanations = row.diagnosticFailureExplanations()

        assertEquals(listOf("GKP"), explanations.map { it.title })
        assertEquals(true, explanations.single().message.contains("Packs"))
    }

    @Test
    fun `screen translation no screenshot explains screenshot failure`() {
        val row = item(
            id = "no-screenshot",
            rawOutputMode = "hotkey_screen_translation:text",
            questionSource = "hotkey_screen_translation",
            imageBytes = 0,
            responsePreview = "当前热键请求没有截图，无法翻译画面。",
        )

        val explanations = row.diagnosticFailureExplanations().map { it.title }

        assertEquals(listOf("截图"), explanations)
    }

    @Test
    fun `screen translation missing key explains no key and byok api`() {
        val row = item(
            id = "missing-key",
            rawOutputMode = "hotkey_screen_translation:text",
            questionSource = "hotkey_screen_translation",
            imageBytes = 42_000,
            ok = false,
            responsePreview = "[error] screen_translation_failed: 请先在设置页填写翻译 API Key。",
        )

        val explanations = row.diagnosticFailureExplanations().map { it.title }

        assertEquals(listOf("No-key", "BYOK API"), explanations)
    }

    @Test
    fun `screen translation timeout explains timeout and byok api`() {
        val row = item(
            id = "timeout",
            rawOutputMode = "hotkey_screen_translation:text",
            questionSource = "hotkey_screen_translation",
            imageBytes = 42_000,
            ok = false,
            responsePreview = "[error] screen_translation_failed: 画面翻译超时，可能是 API 网络不可用。",
        )

        val explanations = row.diagnosticFailureExplanations().map { it.title }

        assertEquals(listOf("BYOK API", "超时"), explanations)
    }

    @Test
    fun `permission error explains permission failure`() {
        val row = item(
            id = "permission",
            ok = false,
            responsePreview = "[error] 没有麦克风权限",
        )

        val explanations = row.diagnosticFailureExplanations().map { it.title }

        assertEquals(listOf("权限"), explanations)
    }

    private fun item(
        id: String,
        rawOutputMode: String = "text",
        questionSource: String? = null,
        isDebug: Boolean = false,
        imageBytes: Int = 0,
        ok: Boolean = true,
        pipelineStage: String = "unknown",
        responsePreview: String = "ok",
    ): UiRequestLogItem = UiRequestLogItem(
        id = id,
        timestampMillis = 1L,
        label = "2048__",
        imageBytes = imageBytes,
        paused = true,
        outputMode = UiOutputMode.Text,
        responsePreview = responsePreview,
        responseText = responsePreview.removePrefix("[error] "),
        fullResponseJson = """{"text":"$responsePreview"}""",
        durationMillis = 1L,
        ok = ok,
        rawOutputMode = rawOutputMode,
        questionSource = questionSource,
        isDebug = isDebug,
        pipelineStage = pipelineStage,
    )
}
