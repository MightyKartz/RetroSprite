package com.retrosprite.app.ui.overlay

import com.retrosprite.app.endpoint.RetroArchHotkeyEvent
import com.retrosprite.app.endpoint.RetroArchHotkeyListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface HotkeyVoiceOverlayState {
    data object Idle : HotkeyVoiceOverlayState
    data class Listening(val event: RetroArchHotkeyEvent) : HotkeyVoiceOverlayState
    data class PermissionRequired(val event: RetroArchHotkeyEvent) : HotkeyVoiceOverlayState
}

enum class HotkeyVoiceOverlayPhase {
    Listening,
    Thinking,
    Speaking,
    Error,
}

data class HotkeyVoiceOverlayRenderState(
    val event: RetroArchHotkeyEvent,
    val phase: HotkeyVoiceOverlayPhase,
    val amplitude: Float = 0f,
    val message: String = "",
    val transcript: String? = null,
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
) : RetroArchHotkeyListener {

    private val _state = MutableStateFlow<HotkeyVoiceOverlayState>(HotkeyVoiceOverlayState.Idle)
    val state: StateFlow<HotkeyVoiceOverlayState> = _state.asStateFlow()

    private var activeEvent: RetroArchHotkeyEvent? = null

    override fun onHotkey(event: RetroArchHotkeyEvent) {
        if (beginVoiceSession(event)) {
            scheduleAutoHide {
                hideIfActive(event)
            }
        }
    }

    fun beginVoiceSession(event: RetroArchHotkeyEvent): Boolean {
        if (!canDrawOverlays()) {
            cancelAutoHide()
            activeEvent = null
            _state.value = HotkeyVoiceOverlayState.PermissionRequired(event)
            return false
        }

        cancelAutoHide()
        activeEvent = event
        _state.value = HotkeyVoiceOverlayState.Listening(event)
        renderer.show(event)
        renderer.render(
            HotkeyVoiceOverlayRenderState(
                event = event,
                phase = HotkeyVoiceOverlayPhase.Listening,
                message = "正在收音",
            )
        )
        return true
    }

    fun renderVoiceState(
        phase: HotkeyVoiceOverlayPhase,
        amplitude: Float = 0f,
        message: String = "",
        transcript: String? = null,
    ) {
        val event = activeEvent ?: return
        renderer.render(
            HotkeyVoiceOverlayRenderState(
                event = event,
                phase = phase,
                amplitude = amplitude,
                message = message,
                transcript = transcript,
            )
        )
    }

    fun finishVoiceSession() {
        val event = activeEvent ?: return
        hideIfActive(event)
    }

    private fun hideIfActive(event: RetroArchHotkeyEvent) {
        if (activeEvent != event) return
        renderer.hide()
        activeEvent = null
        _state.value = HotkeyVoiceOverlayState.Idle
    }
}
