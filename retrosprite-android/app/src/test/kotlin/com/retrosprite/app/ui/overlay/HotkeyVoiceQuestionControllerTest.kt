package com.retrosprite.app.ui.overlay

import com.retrosprite.app.endpoint.RequestLogger
import com.retrosprite.app.endpoint.ResponseGenerator
import com.retrosprite.app.endpoint.RetroArchHotkeyEvent
import com.retrosprite.app.endpoint.model.RetroArchRequest
import com.retrosprite.app.endpoint.model.RetroArchResponse
import com.retrosprite.app.ui.viewmodel.SpeechOutputProvider
import com.retrosprite.app.ui.viewmodel.UiSpeechOutputState
import com.retrosprite.app.ui.viewmodel.UiVoiceInputState
import com.retrosprite.app.ui.viewmodel.VoiceInputProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HotkeyVoiceQuestionControllerTest {

    @Test
    fun `hotkey voice asks pipeline logs answer and speaks`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = { action -> action() },
            cancelAutoHide = {},
        )
        val voice = FakeVoiceInputProvider("什么时候转职？")
        val speech = FakeSpeechOutputProvider()
        val logger = RequestLogger()
        val generator = CapturingGenerator("角色至少 20 级才能转职。\n来源：sf2.promotion")
        val controller = HotkeyVoiceQuestionController(
            coordinator = coordinator,
            voiceInput = voice,
            responseGenerator = generator,
            speechOutput = speech,
            loggerProvider = { logger },
            scope = this,
        )

        controller.onHotkey(event())
        advanceUntilIdle()

        assertEquals(1, voice.startCount)
        assertEquals("mega_drive__光明力量2", generator.request?.label)
        assertEquals("什么时候转职？", generator.request?.question)
        assertEquals("text", generator.outputMode)
        assertEquals(listOf("角色至少 20 级才能转职。\n来源：sf2.promotion"), speech.spoken)

        val entry = logger.entries.value.first()
        assertEquals("hotkey_voice:text", entry.outputMode)
        assertEquals("什么时候转职？", entry.question)
        assertEquals("hotkey_voice", entry.questionSource)
        assertEquals(listOf("sf2.promotion"), entry.sourceIds)
        assertEquals(listOf("show:mega_drive__光明力量2", "hide"), renderer.calls)
    }

    @Test
    fun `missing overlay permission does not start voice session`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { false },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val voice = FakeVoiceInputProvider("什么时候转职？")
        val controller = HotkeyVoiceQuestionController(
            coordinator = coordinator,
            voiceInput = voice,
            responseGenerator = CapturingGenerator("answer"),
            speechOutput = FakeSpeechOutputProvider(),
            loggerProvider = { RequestLogger() },
            scope = this,
        )

        controller.onHotkey(event())
        advanceUntilIdle()

        assertEquals(0, voice.startCount)
        assertEquals(HotkeyVoiceOverlayState.PermissionRequired(event()), coordinator.state.value)
    }

    @Test
    fun `repeated hotkey while listening is ignored instead of restarting session`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val voice = HangingVoiceInputProvider()
        val controller = HotkeyVoiceQuestionController(
            coordinator = coordinator,
            voiceInput = voice,
            responseGenerator = CapturingGenerator("answer"),
            speechOutput = FakeSpeechOutputProvider(),
            loggerProvider = { RequestLogger() },
            scope = this,
        )

        controller.onHotkey(event())
        runCurrent()
        controller.onHotkey(event())
        runCurrent()

        try {
            assertEquals(1, voice.startCount)
            assertEquals(listOf("show:mega_drive__光明力量2"), renderer.calls)
        } finally {
            voice.finish("什么时候转职？")
            advanceUntilIdle()
        }
    }

    @Test
    fun `voice timeout cancels hanging recognizer and hides overlay`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val voice = HangingVoiceInputProvider()
        val controller = HotkeyVoiceQuestionController(
            coordinator = coordinator,
            voiceInput = voice,
            responseGenerator = CapturingGenerator("answer"),
            speechOutput = FakeSpeechOutputProvider(),
            loggerProvider = { RequestLogger() },
            scope = this,
        )

        controller.onHotkey(event())
        runCurrent()
        advanceTimeBy(20_001L)
        runCurrent()

        assertEquals(1, voice.cancelCount)
        assertEquals(listOf("show:mega_drive__光明力量2", "hide"), renderer.calls)
        assertEquals(HotkeyVoiceOverlayState.Idle, coordinator.state.value)
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

    private class FakeVoiceInputProvider(
        private val finalTranscript: String,
    ) : VoiceInputProvider {
        private val _state = MutableStateFlow(UiVoiceInputState(engineLabel = "fake"))
        override val state: StateFlow<UiVoiceInputState> = _state
        override val requiresRecordAudioPermission: Boolean = false
        var startCount: Int = 0

        override suspend fun startListening() {
            startCount += 1
            _state.value = UiVoiceInputState(
                isAvailable = true,
                isListening = true,
                engineLabel = "fake",
            )
            _state.value = UiVoiceInputState(
                isAvailable = true,
                isListening = false,
                transcript = finalTranscript,
                transcriptEventId = startCount.toLong(),
                engineLabel = "fake",
            )
        }

        override suspend fun stopListening() = Unit
        override suspend fun cancelListening() = Unit
    }

    private class HangingVoiceInputProvider : VoiceInputProvider {
        private val _state = MutableStateFlow(UiVoiceInputState(engineLabel = "fake"))
        override val state: StateFlow<UiVoiceInputState> = _state
        override val requiresRecordAudioPermission: Boolean = false
        var startCount: Int = 0
        var cancelCount: Int = 0

        override suspend fun startListening() {
            startCount += 1
            _state.value = UiVoiceInputState(
                isAvailable = true,
                isListening = true,
                engineLabel = "fake",
            )
        }

        override suspend fun stopListening() = Unit

        override suspend fun cancelListening() {
            cancelCount += 1
            _state.value = _state.value.copy(isListening = false)
        }

        fun finish(transcript: String) {
            _state.value = UiVoiceInputState(
                isAvailable = true,
                isListening = false,
                transcript = transcript,
                transcriptEventId = startCount.toLong(),
                engineLabel = "fake",
            )
        }
    }

    private class FakeSpeechOutputProvider : SpeechOutputProvider {
        override val state = MutableStateFlow(
            UiSpeechOutputState(isAvailable = true, isReady = true)
        )
        val spoken = mutableListOf<String>()

        override suspend fun speak(text: String) {
            spoken += text
            state.value = state.value.copy(isSpeaking = false, spokenText = text)
        }

        override suspend fun stop() = Unit
    }

    private class CapturingGenerator(
        private val answer: String,
    ) : ResponseGenerator {
        var request: RetroArchRequest? = null
        var outputMode: String? = null

        override suspend fun generate(
            request: RetroArchRequest,
            outputMode: String,
        ): RetroArchResponse {
            this.request = request
            this.outputMode = outputMode
            return RetroArchResponse.text(answer)
        }
    }
}
