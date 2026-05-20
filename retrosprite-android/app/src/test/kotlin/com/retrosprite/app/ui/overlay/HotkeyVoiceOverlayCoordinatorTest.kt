package com.retrosprite.app.ui.overlay

import com.retrosprite.app.endpoint.RetroArchHotkeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HotkeyVoiceOverlayCoordinatorTest {

    @Test
    fun `hotkey with overlay permission shows listening overlay`() {
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
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = { action -> scheduled = action },
            cancelAutoHide = {},
        )

        coordinator.onHotkey(event())
        scheduled?.invoke()

        assertEquals(listOf("show:mega_drive__光明力量2", "hide"), renderer.calls)
        assertSame(HotkeyVoiceOverlayState.Idle, coordinator.state.value)
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

        override fun show(event: RetroArchHotkeyEvent) {
            calls += "show:${event.label}"
        }

        override fun hide() {
            calls += "hide"
        }
    }
}
