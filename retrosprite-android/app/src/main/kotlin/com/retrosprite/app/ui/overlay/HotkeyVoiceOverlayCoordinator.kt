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
    val answerText: String? = null,
    val sourceIds: List<String> = emptyList(),
    val asrArchitecture: String? = null,
    val asrDecodingMethod: String? = null,
    val asrModelingUnit: String? = null,
    val asrNativeHotwordsEnabled: Boolean? = null,
    val asrNativeHotwordsReason: String? = null,
    val asrHotwordCount: Int? = null,
    val asrHotwordMode: String? = null,
    val asrHotwordPreview: String? = null,
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
            lifecyclePhase = "listening",
            isActive = true,
            isVisible = true,
            renderPhase = HotkeyVoiceOverlayPhase.Wake.debugName(),
            message = "MIC STARTING",
            startedAt = now,
            updatedAt = now,
        )
        renderer.show(event)
        renderer.render(
            HotkeyVoiceOverlayRenderState(
                event = event,
                phase = HotkeyVoiceOverlayPhase.Wake,
                message = "MIC STARTING",
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
        answerText: String? = null,
        sourceIds: List<String> = emptyList(),
        asrArchitecture: String? = null,
        asrDecodingMethod: String? = null,
        asrModelingUnit: String? = null,
        asrNativeHotwordsEnabled: Boolean? = null,
        asrNativeHotwordsReason: String? = null,
        asrHotwordCount: Int? = null,
        asrHotwordMode: String? = null,
        asrHotwordPreview: String? = null,
    ) {
        val event = activeEvent ?: return
        val nextAsrArchitecture = asrArchitecture ?: debugSnapshot.asr_architecture
        val nextAsrDecodingMethod = asrDecodingMethod ?: debugSnapshot.asr_decoding_method
        val nextAsrModelingUnit = asrModelingUnit ?: debugSnapshot.asr_modeling_unit
        val nextAsrNativeHotwordsEnabled =
            asrNativeHotwordsEnabled ?: debugSnapshot.asr_native_hotwords_enabled
        val nextAsrNativeHotwordsReason =
            asrNativeHotwordsReason ?: debugSnapshot.asr_native_hotwords_reason
        val nextAsrHotwordCount = asrHotwordCount ?: debugSnapshot.asr_hotword_count
        val nextAsrHotwordMode = asrHotwordMode ?: debugSnapshot.asr_hotword_mode
        val nextAsrHotwordPreview = asrHotwordPreview ?: debugSnapshot.asr_hotword_preview
        debugSnapshot = event.toDebugSnapshot(
            lifecyclePhase = "listening",
            isActive = true,
            isVisible = true,
            renderPhase = phase.debugName(),
            message = message,
            transcript = transcript,
            normalizedTranscript = normalizedTranscript,
            transcriptMatchedTerm = transcriptMatchedTerm,
            answerVisible = !answerText.isNullOrBlank(),
            sourceIds = sourceIds,
            asrArchitecture = nextAsrArchitecture,
            asrDecodingMethod = nextAsrDecodingMethod,
            asrModelingUnit = nextAsrModelingUnit,
            asrNativeHotwordsEnabled = nextAsrNativeHotwordsEnabled,
            asrNativeHotwordsReason = nextAsrNativeHotwordsReason,
            asrHotwordCount = nextAsrHotwordCount,
            asrHotwordMode = nextAsrHotwordMode,
            asrHotwordPreview = nextAsrHotwordPreview,
            startedAt = debugSnapshot.started_at ?: clockMillis(),
            updatedAt = clockMillis(),
        )
        renderer.render(
            HotkeyVoiceOverlayRenderState(
                event = event,
                phase = phase,
                amplitude = amplitude,
                message = message,
                transcript = transcript,
                normalizedTranscript = normalizedTranscript,
                transcriptMatchedTerm = transcriptMatchedTerm,
                answerText = answerText,
                sourceIds = sourceIds,
                asrArchitecture = nextAsrArchitecture,
                asrDecodingMethod = nextAsrDecodingMethod,
                asrModelingUnit = nextAsrModelingUnit,
                asrNativeHotwordsEnabled = nextAsrNativeHotwordsEnabled,
                asrNativeHotwordsReason = nextAsrNativeHotwordsReason,
                asrHotwordCount = nextAsrHotwordCount,
                asrHotwordMode = nextAsrHotwordMode,
                asrHotwordPreview = nextAsrHotwordPreview,
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
            is_active = false,
            is_visible = false,
            render_phase = "finished",
            message = "已结束",
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
    isActive: Boolean,
    isVisible: Boolean,
    renderPhase: String? = null,
    message: String? = null,
    transcript: String? = null,
    normalizedTranscript: String? = null,
    transcriptMatchedTerm: String? = null,
    answerVisible: Boolean = false,
    sourceIds: List<String> = emptyList(),
    asrArchitecture: String? = null,
    asrDecodingMethod: String? = null,
    asrModelingUnit: String? = null,
    asrNativeHotwordsEnabled: Boolean? = null,
    asrNativeHotwordsReason: String? = null,
    asrHotwordCount: Int? = null,
    asrHotwordMode: String? = null,
    asrHotwordPreview: String? = null,
    startedAt: Long? = null,
    updatedAt: Long? = null,
): DebugHotkeyVoiceOverlayResponse =
    DebugHotkeyVoiceOverlayResponse(
        lifecycle_phase = lifecyclePhase,
        is_active = isActive,
        is_visible = isVisible,
        label = label,
        output_mode = outputMode,
        image_bytes = imageBytes,
        paused = paused,
        render_phase = renderPhase,
        message = message.takeUnless { it.isNullOrBlank() },
        transcript = transcript.takeUnless { it.isNullOrBlank() },
        normalized_transcript = normalizedTranscript.takeUnless { it.isNullOrBlank() },
        transcript_matched_term = transcriptMatchedTerm.takeUnless { it.isNullOrBlank() },
        answer_visible = answerVisible,
        source_ids = sourceIds,
        asr_architecture = asrArchitecture.takeUnless { it.isNullOrBlank() },
        asr_decoding_method = asrDecodingMethod.takeUnless { it.isNullOrBlank() },
        asr_modeling_unit = asrModelingUnit.takeUnless { it.isNullOrBlank() },
        asr_native_hotwords_enabled = asrNativeHotwordsEnabled,
        asr_native_hotwords_reason = asrNativeHotwordsReason.takeUnless { it.isNullOrBlank() },
        asr_hotword_count = asrHotwordCount,
        asr_hotword_mode = asrHotwordMode.takeUnless { it.isNullOrBlank() },
        asr_hotword_preview = asrHotwordPreview.takeUnless { it.isNullOrBlank() },
        started_at = startedAt,
        updated_at = updatedAt,
    )

private fun HotkeyVoiceOverlayPhase.debugName(): String =
    name.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
