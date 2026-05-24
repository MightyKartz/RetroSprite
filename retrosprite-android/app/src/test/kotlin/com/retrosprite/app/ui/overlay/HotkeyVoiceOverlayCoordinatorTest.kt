package com.retrosprite.app.ui.overlay

import com.retrosprite.app.endpoint.RetroArchHotkeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class HotkeyVoiceOverlayCoordinatorTest {

    @Test
    fun `hotkey with overlay permission shows mic starting overlay`() {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val event = event()

        coordinator.onHotkey(event)

        assertEquals(listOf("show:mega_drive__光明力量2"), renderer.calls)
        assertEquals(
            listOf("Wake:MIC STARTING"),
            renderer.renderCalls,
        )
        assertEquals(HotkeyVoiceOverlayState.Listening(event), coordinator.state.value)
    }

    @Test
    fun `hotkey without overlay permission records permission state and does not show`() {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { false },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val event = event()

        coordinator.onHotkey(event)

        assertEquals(emptyList<String>(), renderer.calls)
        assertEquals(HotkeyVoiceOverlayState.PermissionRequired(event), coordinator.state.value)
    }

    @Test
    fun `scheduled auto hide hides the active overlay`() {
        val renderer = FakeRenderer()
        var scheduled: (() -> Unit)? = null
        var now = 1_000L
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = { action -> scheduled = action },
            cancelAutoHide = {},
            clockMillis = { now },
        )
        val event = event()

        coordinator.onHotkey(event)
        now = 1_500L
        scheduled?.invoke()

        assertEquals(listOf("show:mega_drive__光明力量2", "hide"), renderer.calls)
        assertEquals(
            HotkeyVoiceOverlayState.Finished(
                event = event,
                finishedAtMillis = 1_500L,
                reason = "auto_hide",
            ),
            coordinator.state.value,
        )
    }

    @Test
    fun `finishing voice session publishes a detectable finished snapshot`() {
        val renderer = FakeRenderer()
        var now = 10_000L
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
            clockMillis = { now },
        )
        val event = event()

        coordinator.beginVoiceSession(event)
        coordinator.renderVoiceState(
            phase = HotkeyVoiceOverlayPhase.Speaking,
            message = "正在朗读答案",
            transcript = "修伊是谁",
            answerText = "Chester 是早期骑士型同伴。",
            sourceIds = listOf("sf2.manual_translation"),
            asrArchitecture = "transducer",
            asrDecodingMethod = "modified_beam_search",
            asrModelingUnit = "cjkchar",
            asrNativeHotwordsEnabled = true,
            asrHotwordCount = 160,
            asrHotwordMode = "Auto",
            asrHotwordPreview = "修 伊/气 合 之 玉",
        )
        now = 12_345L
        coordinator.finishVoiceSession(reason = "answer_completed")

        val snapshot = coordinator.debugSnapshot()
        assertEquals("finished", snapshot.lifecycle_phase)
        assertEquals(false, snapshot.is_active)
        assertEquals(false, snapshot.is_visible)
        assertEquals("mega_drive__光明力量2", snapshot.label)
        assertEquals("finished", snapshot.render_phase)
        assertEquals("已结束", snapshot.message)
        assertEquals("修伊是谁", snapshot.transcript)
        assertEquals(false, snapshot.answer_visible)
        assertEquals(listOf("sf2.manual_translation"), snapshot.source_ids)
        assertEquals("transducer", snapshot.asr_architecture)
        assertEquals("modified_beam_search", snapshot.asr_decoding_method)
        assertEquals("cjkchar", snapshot.asr_modeling_unit)
        assertEquals(true, snapshot.asr_native_hotwords_enabled)
        assertEquals(160, snapshot.asr_hotword_count)
        assertEquals("Auto", snapshot.asr_hotword_mode)
        assertEquals("修 伊/气 合 之 玉", snapshot.asr_hotword_preview)
        assertEquals(10_000L, snapshot.started_at)
        assertEquals(12_345L, snapshot.finished_at)
        assertEquals("answer_completed", snapshot.finish_reason)
        assertEquals(
            HotkeyVoiceOverlayState.Finished(
                event = event,
                finishedAtMillis = 12_345L,
                reason = "answer_completed",
            ),
            coordinator.state.value,
        )
    }

    private fun event(): RetroArchHotkeyEvent =
        RetroArchHotkeyEvent(
            label = "mega_drive__光明力量2",
            outputMode = "text",
            imageBytes = 4,
            paused = false,
            receivedAtMillis = 1L,
        )

    private class FakeRenderer : HotkeyVoiceOverlayRenderer {
        val calls = mutableListOf<String>()
        val renderCalls = mutableListOf<String>()

        override fun show(event: RetroArchHotkeyEvent) {
            calls += "show:${event.label}"
        }

        override fun render(state: HotkeyVoiceOverlayRenderState) {
            renderCalls += "${state.phase}:${state.message}"
        }

        override fun hide() {
            calls += "hide"
        }
    }
}
