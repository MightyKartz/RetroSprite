package com.retrosprite.app.ui.overlay

import com.retrosprite.app.endpoint.RetroArchHotkeyEvent
import com.retrosprite.app.endpoint.RetroArchHotkeyListener
import com.retrosprite.app.endpoint.model.DebugHotkeyVoiceOverlayResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface HotkeyVoiceOverlayState {
    data object Idle : HotkeyVoiceOverlayState
    data class Listening(val event: RetroArchHotkeyEvent) : HotkeyVoiceOverlayState
    data class PermissionRequired(val event: RetroArchHotkeyEvent) : HotkeyVoiceOverlayState
    data class Finished(
        val event: RetroArchHotkeyEvent,
        val finishedAtMillis: Long,
        val reason: String,
    ) : HotkeyVoiceOverlayState
}

enum class HotkeyVoiceOverlayPhase {
    Wake,
    Preparing,
    Listening,
    Muted,
    Thinking,
    Speaking,
    NoEvidence,
    Error,
}

data class HotkeyVoiceOverlayRenderState(
    val event: RetroArchHotkeyEvent,
    val phase: HotkeyVoiceOverlayPhase,
    val amplitude: Float = 0f,
    val message: String = "",
    val transcript: String? = null,
    val normalizedTranscript: String? = null,
    val transcriptMatchedTerm: String? = null,
    val showTranscriptHud: Boolean = true,
    val answerText: String? = null,
    val sourceIds: List<String> = emptyList(),
    val micLive: Boolean = false,
    val asrArchitecture: String? = null,
    val asrDecodingMethod: String? = null,
    val asrModelingUnit: String? = null,
    val asrCommitReason: String? = null,
    val asrLastPartial: String? = null,
    val asrFinalText: String? = null,
    val asrSelectedTranscript: String? = null,
    val asrPostVoiceSilenceMillis: Long? = null,
    val asrPartialStableMillis: Long? = null,
    val asrRequiredStableMillis: Long? = null,
    val asrEndpointArmed: Boolean? = null,
    val asrFinalFlushMillis: Long? = null,
)

interface HotkeyVoiceOverlayRenderer {
    fun show(event: RetroArchHotkeyEvent)
    fun render(state: HotkeyVoiceOverlayRenderState) = Unit
    fun hide()
}

class HotkeyVoiceOverlayCoordinator(
    private val renderer: HotkeyVoiceOverlayRenderer,
    private val canDrawOverlays: () -> Boolean,
    private val scheduleAutoHide: (() -> Unit) -> Unit,
    private val cancelAutoHide: () -> Unit,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
) : RetroArchHotkeyListener {

    private val _state = MutableStateFlow<HotkeyVoiceOverlayState>(HotkeyVoiceOverlayState.Idle)
    val state: StateFlow<HotkeyVoiceOverlayState> = _state.asStateFlow()

    private var activeEvent: RetroArchHotkeyEvent? = null
    private var debugSnapshot: DebugHotkeyVoiceOverlayResponse =
        DebugHotkeyVoiceOverlayResponse.idle()

    override fun onHotkey(event: RetroArchHotkeyEvent) {
        if (beginVoiceSession(event)) {
            scheduleAutoHide {
                hideIfActive(event, reason = "auto_hide")
            }
        }
    }

    fun beginVoiceSession(event: RetroArchHotkeyEvent): Boolean {
        if (!canDrawOverlays()) {
            cancelAutoHide()
            activeEvent = null
            _state.value = HotkeyVoiceOverlayState.PermissionRequired(event)
            debugSnapshot = event.toDebugSnapshot(
                lifecyclePhase = "permission_required",
                isActive = false,
                isVisible = false,
                startedAt = null,
                updatedAt = clockMillis(),
            )
            return false
        }

        cancelAutoHide()
        activeEvent = event
        _state.value = HotkeyVoiceOverlayState.Listening(event)
        val now = clockMillis()
        debugSnapshot = event.toDebugSnapshot(
            lifecyclePhase = "preparing",
            lifecyclePhaseLabel = "Preparing",
            isActive = true,
            isVisible = true,
            renderPhase = HotkeyVoiceOverlayPhase.Preparing.debugName(),
            renderPhaseLabel = HotkeyVoiceOverlayPhase.Preparing.statusLabel(),
            message = "Preparing - mic off",
            startedAt = now,
            updatedAt = now,
        )
        renderer.show(event)
        renderer.render(
            HotkeyVoiceOverlayRenderState(
                event = event,
                phase = HotkeyVoiceOverlayPhase.Preparing,
                message = "Preparing - mic off",
            )
        )
        return true
    }

    fun renderVoiceState(
        phase: HotkeyVoiceOverlayPhase,
        amplitude: Float = 0f,
        message: String = "",
        transcript: String? = null,
        normalizedTranscript: String? = null,
        transcriptMatchedTerm: String? = null,
        showTranscriptHud: Boolean = true,
        answerText: String? = null,
        sourceIds: List<String> = emptyList(),
        micLive: Boolean? = null,
        asrArchitecture: String? = null,
        asrDecodingMethod: String? = null,
        asrModelingUnit: String? = null,
        asrCommitReason: String? = null,
        asrLastPartial: String? = null,
        asrFinalText: String? = null,
        asrSelectedTranscript: String? = null,
        asrPostVoiceSilenceMillis: Long? = null,
        asrPartialStableMillis: Long? = null,
        asrRequiredStableMillis: Long? = null,
        asrEndpointArmed: Boolean? = null,
        asrFinalFlushMillis: Long? = null,
    ) {
        val event = activeEvent ?: return
        val nextMicLive = micLive ?: false
        val nextAsrArchitecture = asrArchitecture ?: debugSnapshot.asr_architecture
        val nextAsrDecodingMethod = asrDecodingMethod ?: debugSnapshot.asr_decoding_method
        val nextAsrModelingUnit = asrModelingUnit ?: debugSnapshot.asr_modeling_unit
        val nextAsrCommitReason = asrCommitReason ?: debugSnapshot.asr_commit_reason
        val nextAsrLastPartial = asrLastPartial ?: debugSnapshot.asr_last_partial
        val nextAsrFinalText = asrFinalText ?: debugSnapshot.asr_final_text
        val nextAsrSelectedTranscript = asrSelectedTranscript ?: debugSnapshot.asr_selected_transcript
        val nextAsrPostVoiceSilenceMillis =
            asrPostVoiceSilenceMillis ?: debugSnapshot.asr_post_voice_silence_ms
        val nextAsrPartialStableMillis =
            asrPartialStableMillis ?: debugSnapshot.asr_partial_stable_ms
        val nextAsrRequiredStableMillis =
            asrRequiredStableMillis ?: debugSnapshot.asr_required_stable_ms
        val nextAsrEndpointArmed = asrEndpointArmed ?: debugSnapshot.asr_endpoint_armed
        val nextAsrFinalFlushMillis = asrFinalFlushMillis ?: debugSnapshot.asr_final_flush_ms
        debugSnapshot = event.toDebugSnapshot(
            lifecyclePhase = phase.lifecycleDebugName(),
            lifecyclePhaseLabel = phase.statusLabel(),
            isActive = true,
            isVisible = true,
            renderPhase = phase.debugName(),
            renderPhaseLabel = phase.statusLabel(),
            message = message,
            micLive = nextMicLive,
            transcript = transcript,
            normalizedTranscript = normalizedTranscript,
            transcriptMatchedTerm = transcriptMatchedTerm,
            answerVisible = !answerText.isNullOrBlank(),
            sourceIds = sourceIds,
            asrArchitecture = nextAsrArchitecture,
            asrDecodingMethod = nextAsrDecodingMethod,
            asrModelingUnit = nextAsrModelingUnit,
            asrCommitReason = nextAsrCommitReason,
            asrLastPartial = nextAsrLastPartial,
            asrFinalText = nextAsrFinalText,
            asrSelectedTranscript = nextAsrSelectedTranscript,
            asrPostVoiceSilenceMillis = nextAsrPostVoiceSilenceMillis,
            asrPartialStableMillis = nextAsrPartialStableMillis,
            asrRequiredStableMillis = nextAsrRequiredStableMillis,
            asrEndpointArmed = nextAsrEndpointArmed,
            asrFinalFlushMillis = nextAsrFinalFlushMillis,
            startedAt = debugSnapshot.started_at ?: clockMillis(),
            updatedAt = clockMillis(),
        )
        renderer.render(
            HotkeyVoiceOverlayRenderState(
                event = event,
                phase = phase,
                amplitude = amplitude,
                message = message,
                micLive = nextMicLive,
                transcript = transcript,
                normalizedTranscript = normalizedTranscript,
                transcriptMatchedTerm = transcriptMatchedTerm,
                showTranscriptHud = showTranscriptHud,
                answerText = answerText,
                sourceIds = sourceIds,
                asrArchitecture = nextAsrArchitecture,
                asrDecodingMethod = nextAsrDecodingMethod,
                asrModelingUnit = nextAsrModelingUnit,
                asrCommitReason = nextAsrCommitReason,
                asrLastPartial = nextAsrLastPartial,
                asrFinalText = nextAsrFinalText,
                asrSelectedTranscript = nextAsrSelectedTranscript,
                asrPostVoiceSilenceMillis = nextAsrPostVoiceSilenceMillis,
                asrPartialStableMillis = nextAsrPartialStableMillis,
                asrRequiredStableMillis = nextAsrRequiredStableMillis,
                asrEndpointArmed = nextAsrEndpointArmed,
                asrFinalFlushMillis = nextAsrFinalFlushMillis,
            )
        )
    }

    fun finishVoiceSession(reason: String = "finished") {
        val event = activeEvent ?: return
        hideIfActive(event, reason)
    }

    fun debugSnapshot(): DebugHotkeyVoiceOverlayResponse = debugSnapshot

    private fun hideIfActive(event: RetroArchHotkeyEvent, reason: String) {
        if (activeEvent != event) return
        renderer.hide()
        activeEvent = null
        val now = clockMillis()
        debugSnapshot = debugSnapshot.copy(
            lifecycle_phase = "finished",
            lifecycle_phase_label = "Finished",
            is_active = false,
            is_visible = false,
            render_phase = "finished",
            render_phase_label = "Finished",
            message = "已结束",
            mic_live = false,
            answer_visible = false,
            updated_at = now,
            finished_at = now,
            finish_reason = reason,
        )
        _state.value = HotkeyVoiceOverlayState.Finished(
            event = event,
            finishedAtMillis = now,
            reason = reason,
        )
    }
}

private fun RetroArchHotkeyEvent.toDebugSnapshot(
    lifecyclePhase: String,
    lifecyclePhaseLabel: String? = null,
    isActive: Boolean,
    isVisible: Boolean,
    renderPhase: String? = null,
    renderPhaseLabel: String? = null,
    message: String? = null,
    micLive: Boolean = false,
    transcript: String? = null,
    normalizedTranscript: String? = null,
    transcriptMatchedTerm: String? = null,
    answerVisible: Boolean = false,
    sourceIds: List<String> = emptyList(),
    asrArchitecture: String? = null,
    asrDecodingMethod: String? = null,
    asrModelingUnit: String? = null,
    asrCommitReason: String? = null,
    asrLastPartial: String? = null,
    asrFinalText: String? = null,
    asrSelectedTranscript: String? = null,
    asrPostVoiceSilenceMillis: Long? = null,
    asrPartialStableMillis: Long? = null,
    asrRequiredStableMillis: Long? = null,
    asrEndpointArmed: Boolean? = null,
    asrFinalFlushMillis: Long? = null,
    startedAt: Long? = null,
    updatedAt: Long? = null,
): DebugHotkeyVoiceOverlayResponse =
    DebugHotkeyVoiceOverlayResponse(
        lifecycle_phase = lifecyclePhase,
        lifecycle_phase_label = lifecyclePhaseLabel.takeUnless { it.isNullOrBlank() },
        is_active = isActive,
        is_visible = isVisible,
        label = label,
        output_mode = outputMode,
        image_bytes = imageBytes,
        paused = paused,
        render_phase = renderPhase,
        render_phase_label = renderPhaseLabel.takeUnless { it.isNullOrBlank() },
        message = message.takeUnless { it.isNullOrBlank() },
        mic_live = micLive,
        transcript = transcript.takeUnless { it.isNullOrBlank() },
        normalized_transcript = normalizedTranscript.takeUnless { it.isNullOrBlank() },
        transcript_matched_term = transcriptMatchedTerm.takeUnless { it.isNullOrBlank() },
        answer_visible = answerVisible,
        source_ids = sourceIds,
        asr_architecture = asrArchitecture.takeUnless { it.isNullOrBlank() },
        asr_decoding_method = asrDecodingMethod.takeUnless { it.isNullOrBlank() },
        asr_modeling_unit = asrModelingUnit.takeUnless { it.isNullOrBlank() },
        asr_commit_reason = asrCommitReason.takeUnless { it.isNullOrBlank() },
        asr_last_partial = asrLastPartial.takeUnless { it.isNullOrBlank() },
        asr_final_text = asrFinalText.takeUnless { it.isNullOrBlank() },
        asr_selected_transcript = asrSelectedTranscript.takeUnless { it.isNullOrBlank() },
        asr_post_voice_silence_ms = asrPostVoiceSilenceMillis,
        asr_partial_stable_ms = asrPartialStableMillis,
        asr_required_stable_ms = asrRequiredStableMillis,
        asr_endpoint_armed = asrEndpointArmed,
        asr_final_flush_ms = asrFinalFlushMillis,
        started_at = startedAt,
        updated_at = updatedAt,
    )

private fun HotkeyVoiceOverlayPhase.debugName(): String =
    name.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()

private fun HotkeyVoiceOverlayPhase.lifecycleDebugName(): String =
    when (this) {
        HotkeyVoiceOverlayPhase.Preparing,
        HotkeyVoiceOverlayPhase.Wake -> "preparing"
        else -> "listening"
    }
