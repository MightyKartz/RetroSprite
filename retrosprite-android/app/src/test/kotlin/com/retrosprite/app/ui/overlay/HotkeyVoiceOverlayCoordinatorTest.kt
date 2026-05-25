package com.retrosprite.app.ui.overlay

import com.retrosprite.app.endpoint.RetroArchHotkeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class HotkeyVoiceOverlayCoordinatorTest {

    @Test
    fun `hotkey with overlay permission shows preparing overlay`() {
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
            listOf("Preparing:Preparing - mic off"),
            renderer.renderCalls,
        )
        assertEquals("preparing", coordinator.debugSnapshot().lifecycle_phase)
        assertEquals("Preparing", coordinator.debugSnapshot().lifecycle_phase_label)
        assertEquals("preparing", coordinator.debugSnapshot().render_phase)
        assertEquals("Preparing - mic off", coordinator.debugSnapshot().render_phase_label)
        assertEquals(false, coordinator.debugSnapshot().mic_live)
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
            message = "Answering",
            transcript = "修伊是谁",
            answerText = "Chester 是早期骑士型同伴。",
            sourceIds = listOf("sf2.manual_translation"),
            asrArchitecture = "paraformer",
            asrDecodingMethod = "greedy_search",
            asrModelingUnit = null,
            asrCommitReason = "soft_stop_after_silence_and_stable_partial",
            asrLastPartial = "修伊是谁",
            asrFinalText = "修伊是",
            asrSelectedTranscript = "修伊是谁",
            asrPostVoiceSilenceMillis = 1_000L,
            asrPartialStableMillis = 900L,
            asrRequiredStableMillis = 650L,
            asrEndpointArmed = true,
            asrFinalFlushMillis = 2_000L,
        )
        assertEquals("listening", coordinator.debugSnapshot().lifecycle_phase)
        assertEquals("Answering", coordinator.debugSnapshot().lifecycle_phase_label)
        assertEquals("speaking", coordinator.debugSnapshot().render_phase)
        assertEquals("Answering", coordinator.debugSnapshot().render_phase_label)
        assertEquals(false, coordinator.debugSnapshot().mic_live)
        now = 12_345L
        coordinator.finishVoiceSession(reason = "answer_completed")

        val snapshot = coordinator.debugSnapshot()
        assertEquals("finished", snapshot.lifecycle_phase)
        assertEquals("Finished", snapshot.lifecycle_phase_label)
        assertEquals(false, snapshot.is_active)
        assertEquals(false, snapshot.is_visible)
        assertEquals("mega_drive__光明力量2", snapshot.label)
        assertEquals("finished", snapshot.render_phase)
        assertEquals("Finished", snapshot.render_phase_label)
        assertEquals("已结束", snapshot.message)
        assertEquals(false, snapshot.mic_live)
        assertEquals("修伊是谁", snapshot.transcript)
        assertEquals(false, snapshot.answer_visible)
        assertEquals(listOf("sf2.manual_translation"), snapshot.source_ids)
        assertEquals("paraformer", snapshot.asr_architecture)
        assertEquals("greedy_search", snapshot.asr_decoding_method)
        assertEquals(null, snapshot.asr_modeling_unit)
        assertEquals("soft_stop_after_silence_and_stable_partial", snapshot.asr_commit_reason)
        assertEquals("修伊是谁", snapshot.asr_last_partial)
        assertEquals("修伊是", snapshot.asr_final_text)
        assertEquals("修伊是谁", snapshot.asr_selected_transcript)
        assertEquals(1_000L, snapshot.asr_post_voice_silence_ms)
        assertEquals(900L, snapshot.asr_partial_stable_ms)
        assertEquals(650L, snapshot.asr_required_stable_ms)
        assertEquals(true, snapshot.asr_endpoint_armed)
        assertEquals(2_000L, snapshot.asr_final_flush_ms)
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
