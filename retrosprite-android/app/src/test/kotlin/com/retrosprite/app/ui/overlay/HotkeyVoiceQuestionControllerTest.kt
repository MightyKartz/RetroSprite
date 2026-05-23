package com.retrosprite.app.ui.overlay

import com.retrosprite.app.endpoint.RequestLogger
import com.retrosprite.app.endpoint.ResponseGenerator
import com.retrosprite.app.endpoint.RetroArchHotkeyEvent
import com.retrosprite.app.endpoint.model.RetroArchRequest
import com.retrosprite.app.endpoint.model.RetroArchResponse
import com.retrosprite.app.endpoint.model.ResponseDiagnostics
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
    fun `hotkey voice renders compact answer card with local source`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val voice = FakeVoiceInputProvider("什么时候转职？")
        val controller = HotkeyVoiceQuestionController(
            coordinator = coordinator,
            voiceInput = voice,
            responseGenerator = CapturingGenerator("角色至少 20 级才能转职。\n来源：sf2.promotion"),
            speechOutput = FakeSpeechOutputProvider(),
            loggerProvider = { RequestLogger() },
            scope = this,
        )

        controller.onHotkey(event())
        advanceUntilIdle()

        val speakingState = renderer.renderedStates.last {
            it.phase == HotkeyVoiceOverlayPhase.Speaking
        }
        assertEquals("角色至少 20 级才能转职。", speakingState.answerText)
        assertEquals(listOf("sf2.promotion"), speakingState.sourceIds)
    }

    @Test
    fun `blank hotkey voice renders muted recovery state`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val voice = FakeVoiceInputProvider("")
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

        val mutedState = renderer.renderedStates.last {
            it.phase == HotkeyVoiceOverlayPhase.Muted
        }
        assertEquals("Muted", mutedState.message)
        assertEquals("没有听到问题，请再按一次热键。", mutedState.answerText)
        assertEquals(emptyList<String>(), mutedState.sourceIds)
        assertEquals(
            listOf(
                HotkeyVoiceOverlayPhase.Wake,
                HotkeyVoiceOverlayPhase.Muted,
            ),
            renderer.renderedPhases,
        )
    }

    @Test
    fun `hotkey voice no evidence uses no evidence overlay state and does not speak long answer`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val voice = FakeVoiceInputProvider("这是谁？")
        val speech = FakeSpeechOutputProvider()
        val controller = HotkeyVoiceQuestionController(
            coordinator = coordinator,
            voiceInput = voice,
            responseGenerator = CapturingGenerator("没有足够证据回答这个问题。"),
            speechOutput = speech,
            loggerProvider = { RequestLogger() },
            scope = this,
        )

        controller.onHotkey(event())
        advanceUntilIdle()

        assertEquals(
            listOf(
                HotkeyVoiceOverlayPhase.Wake,
                HotkeyVoiceOverlayPhase.Listening,
                HotkeyVoiceOverlayPhase.Thinking,
                HotkeyVoiceOverlayPhase.NoEvidence,
            ),
            renderer.renderedPhases,
        )
        assertEquals(listOf("没有足够证据回答这个问题。"), speech.spoken)
    }

    @Test
    fun `hotkey voice no evidence shows suggested questions in answer card`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val voice = FakeVoiceInputProvider("这个人物厉害吗？")
        val speech = FakeSpeechOutputProvider()
        val noEvidenceDetail = """
            我还没有足够证据回答这个问题。
            你可以这样问：
            · 哪些角色适合培养？
            · 队伍怎么搭配？
            · Sarah 值得练吗？
        """.trimIndent()
        val controller = HotkeyVoiceQuestionController(
            coordinator = coordinator,
            voiceInput = voice,
            responseGenerator = CapturingGenerator(
                answer = noEvidenceDetail,
                diagnostics = ResponseDiagnostics(
                    answerShort = "我还没有足够证据回答这个问题。",
                    answerDetail = noEvidenceDetail,
                    answerType = "no_evidence",
                    llmStatus = "skipped",
                ),
            ),
            speechOutput = speech,
            loggerProvider = { RequestLogger() },
            scope = this,
        )

        controller.onHotkey(event())
        advanceUntilIdle()

        val noEvidenceState = renderer.renderedStates.last {
            it.phase == HotkeyVoiceOverlayPhase.NoEvidence
        }
        assertEquals(HotkeyVoiceOverlayPhase.NoEvidence, noEvidenceState.phase)
        assertEquals("NO RELIABLE EVIDENCE", noEvidenceState.message)
        assertEquals(noEvidenceDetail, noEvidenceState.answerText)
        assertEquals(emptyList<String>(), noEvidenceState.sourceIds)
        assertEquals(listOf("我还没有足够证据回答这个问题。"), speech.spoken)
    }

    @Test
    fun `hotkey voice keeps listening visual briefly after final transcript`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val voice = HangingVoiceInputProvider()
        val generator = CapturingGenerator("角色至少 20 级才能转职。\n来源：sf2.promotion")
        val controller = HotkeyVoiceQuestionController(
            coordinator = coordinator,
            voiceInput = voice,
            responseGenerator = generator,
            speechOutput = FakeSpeechOutputProvider(),
            loggerProvider = { RequestLogger() },
            scope = this,
        )

        controller.onHotkey(event())
        runCurrent()
        voice.finish("什么时候转职？")
        runCurrent()

        assertEquals(HotkeyVoiceOverlayPhase.Listening, renderer.renderedPhases.last())
        assertEquals(null, generator.request)

        advanceTimeBy(180)
        runCurrent()

        assertEquals(HotkeyVoiceOverlayPhase.Listening, renderer.renderedPhases.last())
        assertEquals(null, generator.request)

        advanceTimeBy(80)
        advanceUntilIdle()

        assertEquals("什么时候转职？", generator.request?.question)
        assertEquals(true, renderer.renderedPhases.contains(HotkeyVoiceOverlayPhase.Thinking))
    }

    @Test
    fun `hotkey voice evidence answer card keeps complete local short answer`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val fullShortAnswer = "通用原则：优先练能稳定出场、补足治疗或远程输出、移动和生存不拖队伍的角色；队伍搭配上保留治疗、稳定前排和安全输出。"
        val voice = FakeVoiceInputProvider("那些角色适合培养的")
        val controller = HotkeyVoiceQuestionController(
            coordinator = coordinator,
            voiceInput = voice,
            responseGenerator = CapturingGenerator(
                answer = "$fullShortAnswer\n来源：sf2.project_mechanics",
                diagnostics = ResponseDiagnostics(
                    answerShort = fullShortAnswer,
                    answerDetail = "$fullShortAnswer 告诉我你现在到哪一章或刚收了哪些角色，我可以更具体。",
                    answerType = "team_build",
                    llmStatus = "skipped",
                ),
            ),
            speechOutput = FakeSpeechOutputProvider(),
            loggerProvider = { RequestLogger() },
            scope = this,
        )

        controller.onHotkey(event())
        advanceUntilIdle()

        val speakingState = renderer.renderedStates.last {
            it.phase == HotkeyVoiceOverlayPhase.Speaking
        }
        assertEquals(fullShortAnswer, speakingState.answerText)
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
        advanceUntilIdle()

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
        val renderedPhases = mutableListOf<HotkeyVoiceOverlayPhase>()
        val renderedStates = mutableListOf<HotkeyVoiceOverlayRenderState>()

        override fun show(event: RetroArchHotkeyEvent) {
            calls += "show:${event.label}"
        }

        override fun render(state: HotkeyVoiceOverlayRenderState) {
            renderedPhases += state.phase
            renderedStates += state
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
        private val diagnostics: ResponseDiagnostics = ResponseDiagnostics(),
    ) : ResponseGenerator {
        var request: RetroArchRequest? = null
        var outputMode: String? = null

        override suspend fun generate(
            request: RetroArchRequest,
            outputMode: String,
        ): RetroArchResponse {
            this.request = request
            this.outputMode = outputMode
            return RetroArchResponse.text(answer, diagnostics = diagnostics)
        }
    }
}
