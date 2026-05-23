package com.retrosprite.app.ui.integration

import com.retrosprite.app.endpoint.EndpointStatus
import com.retrosprite.app.endpoint.RequestLogEntry
import com.retrosprite.app.ui.viewmodel.UiAnswerFeedback
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
        val ui = EndpointStatus.Running(port = 9090).toUi(fallbackPort = 4_404)
        assertEquals(UiEndpointPhase.Running, ui.phase)
        assertEquals(9090, ui.port)
        assertEquals("http://localhost:9090", ui.baseUrl)
        assertNull(ui.message)
    }

    @Test
    fun `stopped endpoint falls back to configured port`() {
        val ui = EndpointStatus.Stopped.toUi(fallbackPort = 8123)
        assertEquals(UiEndpointPhase.Stopped, ui.phase)
        assertEquals(8123, ui.port)
        assertEquals("http://localhost:8123", ui.baseUrl)
    }

    @Test
    fun `error endpoint surfaces message verbatim`() {
        val ui = EndpointStatus.Error("port_in_use").toUi(fallbackPort = 4_404)
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
        assertEquals("text", ui.rawOutputMode)
        assertEquals("RetroSprite 已连接", ui.responsePreview)
        assertEquals("RetroSprite 已连接", ui.responseText)
        assertTrue(ui.fullResponseJson.contains(""""text":"RetroSprite 已连接""""))
        assertTrue(ui.fullResponseJson.contains(""""llm_status":"skipped""""))
        assertTrue(ui.fullResponseJson.contains(""""llm_provider":null"""))
        assertEquals(0L, ui.durationMillis)
        assertTrue(ui.ok)
        assertFalse(ui.isDebug)
        assertEquals(emptyList<String>(), ui.sourceIds)
        assertEquals("unknown", ui.pipelineStage)
        assertEquals("skipped", ui.llmStatus)
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
        assertTrue(ui.fullResponseJson.contains(""""error":"malformed_request: not json""""))
        assertTrue(ui.fullResponseJson.contains(""""pipeline_stage":"error""""))
        assertTrue(ui.fullResponseJson.contains(""""llm_status":"skipped""""))
        assertNull("empty label and empty system+game must collapse to null", ui.label)
    }

    @Test
    fun `debug evidence entry exposes trace metadata`() {
        val entry = RequestLogEntry(
            id = "debug-1",
            timestamp = 1L,
            label = "2048__",
            system = "2048",
            game = "",
            imageBytes = 0,
            paused = false,
            outputMode = "debug:text",
            responseText = "两个相同数字滑到一起会合并。\n来源：sample.2048.rules, sample.2048.strategy",
            errorMessage = null,
        )

        val ui = entry.toUi()

        assertTrue(ui.isDebug)
        assertEquals("debug:text", ui.rawOutputMode)
        assertEquals(listOf("sample.2048.rules", "sample.2048.strategy"), ui.sourceIds)
        assertEquals("evidence", ui.pipelineStage)
        assertEquals("used", ui.llmStatus)
        assertTrue(ui.fullResponseJson.contains(""""debug":true"""))
        assertTrue(ui.fullResponseJson.contains("sample.2048.rules"))
    }

    @Test
    fun `disabled gkp response maps to explicit pipeline stage`() {
        val entry = RequestLogEntry(
            id = "disabled-1",
            timestamp = 1L,
            label = "2048__",
            system = "2048",
            game = "",
            imageBytes = 0,
            paused = true,
            outputMode = "text",
            responseText = "知识包已禁用：我找到了当前游戏的本地 GKP，但它在 Packs 中处于禁用状态。",
            errorMessage = null,
        )

        val ui = entry.toUi()

        assertEquals("gkp_disabled", ui.pipelineStage)
        assertEquals("skipped", ui.llmStatus)
        assertEquals(emptyList<String>(), ui.sourceIds)
        assertTrue(ui.fullResponseJson.contains(""""pipeline_stage":"gkp_disabled""""))
    }

    @Test
    fun `llm diagnostics map into ui and detail json`() {
        val entry = RequestLogEntry(
            id = "llm-1",
            timestamp = 1L,
            label = "2048__",
            system = "2048",
            game = "",
            imageBytes = 0,
            paused = true,
            outputMode = "app:text",
            question = "How do I merge?",
            questionSource = "app",
            responseText = "综合答案。\n来源：sample.2048.rules",
            durationMillis = 1_234L,
            answerShort = "短答。",
            answerDetail = "完整解释。",
            answerType = "mechanic",
            answerConfidence = "high",
            spoilerLevelUsed = "light",
            nextActions = listOf("查看来源", "这不对"),
            llmStatusOverride = "used",
            llmProvider = "deepseek",
            llmModel = "deepseek-v4-pro",
            llmMaxTokens = 256,
            llmTimeoutMs = 30_000L,
            llmLatencyMs = 1_111L,
            llmTokensIn = 42,
            llmTokensOut = 12,
            errorMessage = null,
        )

        val ui = entry.toUi()

        assertEquals(1_234L, ui.durationMillis)
        assertEquals("How do I merge?", ui.question)
        assertEquals("app", ui.questionSource)
        assertEquals("used", ui.llmStatus)
        assertEquals("短答。", ui.answerShort)
        assertEquals("完整解释。", ui.answerDetail)
        assertEquals("mechanic", ui.answerType)
        assertEquals("high", ui.answerConfidence)
        assertEquals("light", ui.spoilerLevelUsed)
        assertEquals(listOf("查看来源", "这不对"), ui.nextActions)
        assertEquals("deepseek", ui.llmProvider)
        assertEquals("deepseek-v4-pro", ui.llmModel)
        assertEquals(256, ui.llmMaxTokens)
        assertEquals(30_000L, ui.llmTimeoutMs)
        assertEquals(1_111L, ui.llmLatencyMs)
        assertEquals(42, ui.llmTokensIn)
        assertEquals(12, ui.llmTokensOut)
        assertTrue(ui.fullResponseJson.contains(""""llm_provider":"deepseek""""))
        assertTrue(ui.fullResponseJson.contains(""""llm_model":"deepseek-v4-pro""""))
        assertTrue(ui.fullResponseJson.contains(""""llm_max_tokens":256"""))
        assertTrue(ui.fullResponseJson.contains(""""question":"How do I merge?""""))
        assertTrue(ui.fullResponseJson.contains(""""question_source":"app""""))
        assertTrue(ui.fullResponseJson.contains(""""answer_type":"mechanic""""))
        assertTrue(ui.fullResponseJson.contains(""""answer_confidence":"high""""))
    }

    @Test
    fun `feedback maps into ui and detail json`() {
        val entry = RequestLogEntry(
            id = "app-question",
            timestamp = 1L,
            label = "2048__",
            system = "2048",
            game = "",
            imageBytes = 0,
            paused = true,
            outputMode = "app:text",
            responseText = "本地答案",
            feedback = "incorrect",
            feedbackTimestamp = 99L,
        )

        val ui = entry.toUi()

        assertEquals(UiAnswerFeedback.Incorrect, ui.feedback)
        assertEquals(99L, ui.feedbackTimestampMillis)
        assertTrue(ui.fullResponseJson.contains(""""feedback":"incorrect""""))
        assertTrue(ui.fullResponseJson.contains(""""feedback_timestamp":99"""))
    }

    @Test
    fun `natural answer types expose diagnostics display labels`() {
        val entry = RequestLogEntry(
            id = "natural-1",
            timestamp = 1L,
            label = "md__Shining Force II",
            system = "md",
            game = "Shining Force II",
            imageBytes = 0,
            paused = false,
            outputMode = "hotkey_voice:text",
            responseText = "它主要玩剧情推进和网格回合制战斗。",
            answerType = "game_overview",
        )

        val ui = entry.toUi()

        assertEquals("核心玩法", ui.answerTypeLabel)
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
        assertEquals(long, ui.responseText)
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
        assertTrue(ui.fullResponseJson.contains(""""text":"She said \"hi\" and \\o/""""))
    }
}
