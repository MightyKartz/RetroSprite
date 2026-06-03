package com.retrosprite.app.ui.overlay

import com.retrosprite.app.endpoint.RequestLogger
import com.retrosprite.app.endpoint.ResponseGenerator
import com.retrosprite.app.endpoint.RetroArchHotkeyEvent
import com.retrosprite.app.endpoint.model.RetroArchRequest
import com.retrosprite.app.endpoint.model.RetroArchResponse
import com.retrosprite.app.endpoint.model.ResponseDiagnostics
import com.retrosprite.app.screen.translation.ScreenTranslationIntentClassifier
import com.retrosprite.app.screen.translation.ScreenTranslationContext
import com.retrosprite.app.screen.translation.ScreenTranslationPipeline
import com.retrosprite.app.screen.translation.ScreenTranslationResult
import com.retrosprite.app.ui.viewmodel.SpeechOutputProvider
import com.retrosprite.app.ui.viewmodel.UiSpeechOutputState
import com.retrosprite.app.ui.viewmodel.UiVoiceInputState
import com.retrosprite.app.ui.viewmodel.VoiceInputProvider
import com.retrosprite.app.voice.asr.AsrRecognitionContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        assertEquals("hotkey_voice:text", generator.outputMode)
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
    fun `hotkey voice renders follow up suggestions without speaking them`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val speech = FakeSpeechOutputProvider()
        val voice = FakeVoiceInputProvider("气合之玉怎么用")
        val controller = HotkeyVoiceQuestionController(
            coordinator = coordinator,
            voiceInput = voice,
            responseGenerator = CapturingGenerator(
                answer = "Vigor Ball 给 Priest 系角色用于转 Master Monk。\n来源：item.vigor-ball",
                diagnostics = ResponseDiagnostics(
                    answerShort = "Vigor Ball 给 Priest 系角色用于转 Master Monk。",
                    answerDetail = "Vigor Ball 给 Priest 系角色用于转 Master Monk。",
                    suggestedQuestions = listOf("气合之玉在哪里？", "谁适合转 Master Monk？"),
                ),
            ),
            speechOutput = speech,
            loggerProvider = { RequestLogger() },
            scope = this,
        )

        controller.onHotkey(event())
        advanceUntilIdle()

        val speakingState = renderer.renderedStates.last {
            it.phase == HotkeyVoiceOverlayPhase.Speaking
        }
        assertEquals(
            "Vigor Ball 给 Priest 系角色用于转 Master Monk。\n\n你还可以问：\n· 气合之玉在哪里？\n· 谁适合转 Master Monk？",
            speakingState.answerText,
        )
        assertEquals(listOf("Vigor Ball 给 Priest 系角色用于转 Master Monk。"), speech.spoken)
    }

    @Test
    fun `hotkey voice keeps all three follow up suggestions visible`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val voice = FakeVoiceInputProvider("哪些角色适合培养")
        val speech = FakeSpeechOutputProvider()
        val controller = HotkeyVoiceQuestionController(
            coordinator = coordinator,
            voiceInput = voice,
            responseGenerator = CapturingGenerator(
                answer = "通用原则：优先练能稳定出场、补足治疗或远程输出、移动和生存不拖队伍的角色。\n来源：sf2.project_mechanics",
                diagnostics = ResponseDiagnostics(
                    answerShort = "通用原则：优先练能稳定出场、补足治疗或远程输出、移动和生存不拖队伍的角色。",
                    answerDetail = "通用原则：优先练能稳定出场、补足治疗或远程输出、移动和生存不拖队伍的角色。告诉我你现在到哪一章或刚收了哪些角色，我可以更具体。",
                    suggestedQuestions = listOf("哪些角色值得练？", "角色练哪些比较稳？", "培养谁？"),
                ),
            ),
            speechOutput = speech,
            loggerProvider = { RequestLogger() },
            scope = this,
        )

        controller.onHotkey(event())
        advanceUntilIdle()

        val speakingState = renderer.renderedStates.last {
            it.phase == HotkeyVoiceOverlayPhase.Speaking
        }
        assertTrue(speakingState.answerText.orEmpty().contains("· 哪些角色值得练？"))
        assertTrue(speakingState.answerText.orEmpty().contains("· 角色练哪些比较稳？"))
        assertTrue(speakingState.answerText.orEmpty().contains("· 培养谁？"))
        assertTrue(
            "follow-up block should not be pre-truncated: ${speakingState.answerText}",
            !speakingState.answerText.orEmpty().contains("..."),
        )
        assertEquals(
            listOf("通用原则：优先练能稳定出场、补足治疗或远程输出、移动和生存不拖队伍的角色。"),
            speech.spoken,
        )
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
        assertEquals("No speech", mutedState.message)
        assertEquals("没有听到问题，请再按一次热键。", mutedState.answerText)
        assertEquals(emptyList<String>(), mutedState.sourceIds)
        assertEquals(
            listOf(
                HotkeyVoiceOverlayPhase.Preparing,
                HotkeyVoiceOverlayPhase.Listening,
                HotkeyVoiceOverlayPhase.Muted,
            ),
            renderer.renderedPhases,
        )
    }

    @Test
    fun `single character asr noise renders muted recovery and skips pipeline`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val voice = FakeVoiceInputProvider("心")
        val generator = CapturingGenerator("answer")
        val controller = HotkeyVoiceQuestionController(
            coordinator = coordinator,
            voiceInput = voice,
            responseGenerator = generator,
            speechOutput = FakeSpeechOutputProvider(),
            loggerProvider = { RequestLogger() },
            scope = this,
        )

        controller.onHotkey(event())
        advanceUntilIdle()

        val mutedState = renderer.renderedStates.last {
            it.phase == HotkeyVoiceOverlayPhase.Muted
        }
        assertEquals("No speech", mutedState.message)
        assertEquals("没有听到问题，请再按一次热键。", mutedState.answerText)
        assertEquals(null, generator.request)
    }

    @Test
    fun `pathological short asr fragments render muted recovery and skip pipeline`() = runTest {
        for (fragment in listOf("是什", "关是", "是不")) {
            val renderer = FakeRenderer()
            val coordinator = HotkeyVoiceOverlayCoordinator(
                renderer = renderer,
                canDrawOverlays = { true },
                scheduleAutoHide = {},
                cancelAutoHide = {},
            )
            val voice = FakeVoiceInputProvider(fragment)
            val generator = CapturingGenerator("answer")
            val controller = HotkeyVoiceQuestionController(
                coordinator = coordinator,
                voiceInput = voice,
                responseGenerator = generator,
                speechOutput = FakeSpeechOutputProvider(),
                loggerProvider = { RequestLogger() },
                scope = this,
            )

            controller.onHotkey(event())
            advanceUntilIdle()

            val mutedState = renderer.renderedStates.last {
                it.phase == HotkeyVoiceOverlayPhase.Muted
            }
            assertEquals("No speech", mutedState.message)
            assertEquals("没有听到问题，请再按一次热键。", mutedState.answerText)
            assertEquals(null, generator.request)
        }
    }

    @Test
    fun `short useful voice questions still reach pipeline`() = runTest {
        for (question in listOf("这游戏", "气合之玉", "玛尔是谁")) {
            val renderer = FakeRenderer()
            val coordinator = HotkeyVoiceOverlayCoordinator(
                renderer = renderer,
                canDrawOverlays = { true },
                scheduleAutoHide = {},
                cancelAutoHide = {},
            )
            val voice = FakeVoiceInputProvider(question)
            val generator = CapturingGenerator("answer")
            val controller = HotkeyVoiceQuestionController(
                coordinator = coordinator,
                voiceInput = voice,
                responseGenerator = generator,
                speechOutput = FakeSpeechOutputProvider(),
                loggerProvider = { RequestLogger() },
                scope = this,
            )

            controller.onHotkey(event())
            advanceUntilIdle()

            assertEquals(question, generator.request?.question)
        }
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
                HotkeyVoiceOverlayPhase.Preparing,
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
        assertEquals("No evidence", noEvidenceState.message)
        assertEquals(noEvidenceDetail, noEvidenceState.answerText)
        assertEquals(emptyList<String>(), noEvidenceState.sourceIds)
        assertEquals("这个人物厉害吗？", noEvidenceState.transcript)
        assertEquals(listOf("我还没有足够证据回答这个问题。"), speech.spoken)
    }

    @Test
    fun `hotkey voice carries transcript through visible overlay phases`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val voice = FakeVoiceInputProvider(
            finalTranscript = "角色如何搭配",
            partialTranscript = "角色如何搭配",
        )
        val controller = HotkeyVoiceQuestionController(
            coordinator = coordinator,
            voiceInput = voice,
            responseGenerator = CapturingGenerator("通用原则：保留治疗、前排和远程输出。\n来源：sf2.team_build"),
            speechOutput = FakeSpeechOutputProvider(),
            loggerProvider = { RequestLogger() },
            scope = this,
        )

        controller.onHotkey(event())
        advanceUntilIdle()

        val listeningState = renderer.renderedStates.last {
            it.phase == HotkeyVoiceOverlayPhase.Listening
        }
        val thinkingState = renderer.renderedStates.last {
            it.phase == HotkeyVoiceOverlayPhase.Thinking
        }
        val speakingState = renderer.renderedStates.last {
            it.phase == HotkeyVoiceOverlayPhase.Speaking
        }
        assertEquals("角色如何搭配", listeningState.transcript)
        assertEquals("角色如何搭配", thinkingState.transcript)
        assertEquals("角色如何搭配", speakingState.transcript)
    }

    @Test
    fun `hotkey voice keeps hud preparing until microphone is actually listening`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val voice = PreparingThenHangingVoiceInputProvider()
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

        assertTrue(
            renderer.renderedStates.any {
                it.phase == HotkeyVoiceOverlayPhase.Preparing &&
                    it.message == "Preparing - mic off"
            }
        )
        val preparingState = renderer.renderedStates.last {
            it.phase == HotkeyVoiceOverlayPhase.Preparing
        }
        val listeningState = renderer.renderedStates.first {
            it.phase == HotkeyVoiceOverlayPhase.Listening
        }
        assertEquals("首次加载本地 ASR 模型，可能需要几秒钟…", preparingState.message)
        assertEquals("Mic live", listeningState.message)
        assertEquals("listening", coordinator.debugSnapshot().lifecycle_phase)
        assertEquals("listening", coordinator.debugSnapshot().render_phase)
        assertEquals(true, coordinator.debugSnapshot().mic_live)

        voice.finish("什么时候转职？")
        advanceUntilIdle()
    }

    @Test
    fun `hotkey voice passes normalized transcript diagnostics to overlay`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val voice = FakeVoiceInputProvider("修医是谁")
        val controller = HotkeyVoiceQuestionController(
            coordinator = coordinator,
            voiceInput = voice,
            responseGenerator = CapturingGenerator(
                answer = "Higins 是一名中后期加入的骑士。\n来源：sf2.character.higins",
                diagnostics = ResponseDiagnostics(
                    question = "修伊是谁",
                    rawQuestion = "修医是谁",
                    normalizedQuestion = "修伊是谁",
                    questionNormalizationReason = "game_term",
                    normalizedQuestionMatchedTerm = "修伊",
                    normalizedQuestionMatchedEntityId = "sf2.character.higins",
                    answerShort = "Higins 是一名中后期加入的骑士。",
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
        assertEquals("修医是谁", speakingState.transcript)
        assertEquals("修伊是谁", speakingState.normalizedTranscript)
        assertEquals("修伊", speakingState.transcriptMatchedTerm)
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
    fun `hotkey voice ignores stale recognizer error when starting next session`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val voice = RecoveringVoiceInputProvider(
            staleError = "没有识别到问题，可再试一次或使用文字输入。",
            finalTranscript = "角色什么时候转职",
        )
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
        advanceUntilIdle()

        assertEquals(1, voice.startCount)
        assertEquals(0, voice.cancelCount)
        assertEquals("角色什么时候转职", generator.request?.question)
        assertEquals(true, renderer.renderedPhases.contains(HotkeyVoiceOverlayPhase.Speaking))
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
        val finished = coordinator.state.value
        assertTrue(finished is HotkeyVoiceOverlayState.Finished)
        assertEquals("muted_recovery", (finished as HotkeyVoiceOverlayState.Finished).reason)
        assertEquals("finished", coordinator.debugSnapshot().lifecycle_phase)
        assertEquals(false, coordinator.debugSnapshot().is_visible)
    }

    @Test
    fun `new hotkey voice session does not render stale transcript before fresh ASR starts`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val voice = StaleThenFreshVoiceInputProvider(
            staleTranscript = "上一轮的问题",
            freshTranscript = "什么时候转职？",
        )
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

        assertEquals(false, renderer.renderedStates.any { it.transcript == "上一轮的问题" })
        assertEquals(true, renderer.renderedStates.any { it.transcript == "什么时候转职？" })
    }

    @Test
    fun `hotkey voice session carries audio capture diagnostics into overlay snapshot`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val voice = FakeVoiceInputProvider(
            finalTranscript = "什么时候转职？",
            asrSampleCount = 48_000L,
            asrAudioReadCount = 12L,
            asrAudioReadErrorCount = 0L,
            asrPeakAmplitude = 0.18f,
            asrLastFrameAmplitude = 0.04f,
        )
        val controller = HotkeyVoiceQuestionController(
            coordinator = coordinator,
            voiceInput = voice,
            responseGenerator = CapturingGenerator("20 级以后可以转职。\n来源：sf2.promotion"),
            speechOutput = FakeSpeechOutputProvider(),
            loggerProvider = { RequestLogger() },
            scope = this,
        )

        controller.onHotkey(event())
        advanceUntilIdle()

        val snapshot = coordinator.debugSnapshot()
        assertEquals(48_000L, snapshot.asr_sample_count)
        assertEquals(12L, snapshot.asr_audio_read_count)
        assertEquals(0L, snapshot.asr_audio_read_error_count)
        assertEquals(0.18f, snapshot.asr_peak_amplitude)
        assertEquals(0.04f, snapshot.asr_last_frame_amplitude)
    }

    @Test
    fun `translation intent uses hotkey screenshot and skips normal QA pipeline`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val voice = FakeVoiceInputProvider("翻译一下")
        val generator = CapturingGenerator("normal answer")
        val translationPipeline = RecordingScreenTranslationPipeline(
            ScreenTranslationResult(
                translatedText = "欢迎来到港口城市。",
                pages = listOf("欢迎来到港口城市。"),
                providerName = "fake-api",
                model = "fake-model",
            )
        )
        val logger = RequestLogger()
        val controller = HotkeyVoiceQuestionController(
            coordinator = coordinator,
            voiceInput = voice,
            responseGenerator = generator,
            screenTranslationPipeline = translationPipeline,
            screenTranslationIntentClassifier = ScreenTranslationIntentClassifier(),
            speechOutput = FakeSpeechOutputProvider(),
            loggerProvider = { logger },
            scope = this,
        )

        controller.onHotkey(event(imageBase64 = "hotkey_image"))
        advanceUntilIdle()

        assertEquals("hotkey_image", translationPipeline.imageBase64)
        assertEquals("mega_drive__光明力量2", translationPipeline.context?.label)
        assertEquals(1, translationPipeline.callCount)
        assertEquals(null, generator.request)
        val translationState = renderer.renderedStates.last {
            it.phase == HotkeyVoiceOverlayPhase.Translation
        }
        assertEquals(HotkeyVoiceOverlayContentKind.ScreenTranslation, translationState.contentKind)
        assertEquals("欢迎来到港口城市。", translationState.answerText)
        assertEquals("hotkey_screen_translation:text", logger.entries.value.first().outputMode)
    }

    @Test
    fun `translation intent logs common tail-dropped command as canonical phrase`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val voice = FakeVoiceInputProvider("翻译一")
        val translationPipeline = RecordingScreenTranslationPipeline(
            ScreenTranslationResult(
                translatedText = "菜单\n物品\n状态",
                pages = listOf("菜单\n物品\n状态"),
                providerName = "fake-api",
                model = "fake-model",
            )
        )
        val logger = RequestLogger()
        val controller = HotkeyVoiceQuestionController(
            coordinator = coordinator,
            voiceInput = voice,
            responseGenerator = CapturingGenerator("normal answer"),
            screenTranslationPipeline = translationPipeline,
            screenTranslationIntentClassifier = ScreenTranslationIntentClassifier(),
            speechOutput = FakeSpeechOutputProvider(),
            loggerProvider = { logger },
            scope = this,
        )

        controller.onHotkey(event(imageBase64 = "menu_image"))
        advanceUntilIdle()

        val entry = logger.entries.value.first()
        assertEquals("hotkey_screen_translation:text", entry.outputMode)
        assertEquals("翻译一下", entry.question)
        assertEquals("翻译一", entry.rawQuestion)
        assertEquals("翻译一下", entry.normalizedQuestion)
        assertEquals("screen_translation_intent_tail_completion", entry.questionNormalizationReason)
        assertEquals(1, translationPipeline.callCount)
        val translationState = renderer.renderedStates.last {
            it.phase == HotkeyVoiceOverlayPhase.Translation
        }
        assertEquals("翻译一下", translationState.transcript)
    }

    @Test
    fun `standalone translation command bypasses normal voice question length gate`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val voice = FakeVoiceInputProvider("翻译")
        val translationPipeline = RecordingScreenTranslationPipeline(
            ScreenTranslationResult(
                translatedText = "卫兵：看招！",
                pages = listOf("卫兵：看招！"),
                providerName = "fake-api",
                model = "fake-model",
            )
        )
        val logger = RequestLogger()
        val controller = HotkeyVoiceQuestionController(
            coordinator = coordinator,
            voiceInput = voice,
            responseGenerator = CapturingGenerator("normal answer"),
            screenTranslationPipeline = translationPipeline,
            screenTranslationIntentClassifier = ScreenTranslationIntentClassifier(),
            speechOutput = FakeSpeechOutputProvider(),
            loggerProvider = { logger },
            scope = this,
        )

        controller.onHotkey(event(imageBase64 = "dialogue_image"))
        advanceUntilIdle()

        val entry = logger.entries.value.first()
        assertEquals("hotkey_screen_translation:text", entry.outputMode)
        assertEquals("翻译一下", entry.question)
        assertEquals("翻译", entry.rawQuestion)
        assertEquals("翻译一下", entry.normalizedQuestion)
        assertEquals("screen_translation_intent_tail_completion", entry.questionNormalizationReason)
        assertEquals(1, translationPipeline.callCount)
        assertEquals(false, renderer.renderedStates.any { it.phase == HotkeyVoiceOverlayPhase.Muted })
    }

    @Test
    fun `debug injected translation question bypasses microphone and translates screenshot`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val voice = FakeVoiceInputProvider("should not be used")
        val translationPipeline = RecordingScreenTranslationPipeline(
            ScreenTranslationResult(
                translatedText = "菜单\nITEM 道具",
                pages = listOf("菜单\nITEM 道具"),
                providerName = "fake-api",
                model = "fake-model",
            )
        )
        val controller = HotkeyVoiceQuestionController(
            coordinator = coordinator,
            voiceInput = voice,
            responseGenerator = CapturingGenerator("normal answer"),
            screenTranslationPipeline = translationPipeline,
            screenTranslationIntentClassifier = ScreenTranslationIntentClassifier(),
            speechOutput = FakeSpeechOutputProvider(),
            loggerProvider = { RequestLogger() },
            scope = this,
        )

        controller.onHotkey(event(imageBase64 = "menu_image").copy(injectedQuestion = "翻译"))
        advanceUntilIdle()

        assertEquals(0, voice.startCount)
        assertEquals(1, translationPipeline.callCount)
        assertEquals("menu_image", translationPipeline.imageBase64)
        assertEquals("菜单\nITEM 道具", lastTranslationText(renderer))
    }

    @Test
    fun `screen translation keeps every result page visible for ten seconds`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val voice = FakeVoiceInputProvider("翻译一下")
        val translationPipeline = RecordingScreenTranslationPipeline(
            ScreenTranslationResult(
                translatedText = "第一页译文\n第二页译文",
                pages = listOf("第一页译文", "第二页译文"),
                providerName = "fake-api",
                model = "fake-model",
            )
        )
        val controller = HotkeyVoiceQuestionController(
            coordinator = coordinator,
            voiceInput = voice,
            responseGenerator = CapturingGenerator("normal answer"),
            screenTranslationPipeline = translationPipeline,
            screenTranslationIntentClassifier = ScreenTranslationIntentClassifier(),
            speechOutput = FakeSpeechOutputProvider(),
            loggerProvider = { RequestLogger() },
            scope = this,
        )

        controller.onHotkey(event(imageBase64 = "two_page_translation"))
        advanceTimeBy(220L)
        runCurrent()

        assertEquals("第一页译文", lastTranslationText(renderer))

        advanceTimeBy(9_999L)
        runCurrent()
        assertEquals("第一页译文", lastTranslationText(renderer))

        advanceTimeBy(1L)
        runCurrent()
        assertEquals("第二页译文", lastTranslationText(renderer))

        advanceTimeBy(9_999L)
        runCurrent()
        assertEquals("第二页译文", lastTranslationText(renderer))
        assertTrue(coordinator.state.value is HotkeyVoiceOverlayState.Listening)

        advanceTimeBy(1L)
        runCurrent()
        assertTrue(coordinator.state.value is HotkeyVoiceOverlayState.Finished)
    }

    private fun lastTranslationText(renderer: FakeRenderer): String? =
        renderer.renderedStates.last {
            it.phase == HotkeyVoiceOverlayPhase.Translation
        }.answerText

    private fun event(imageBase64: String = "fake_screen_png_base64"): RetroArchHotkeyEvent =
        RetroArchHotkeyEvent(
            label = "mega_drive__光明力量2",
            outputMode = "text",
            imageBytes = 4,
            paused = false,
            imageBase64 = imageBase64,
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
        private val partialTranscript: String? = null,
        private val asrSampleCount: Long? = null,
        private val asrAudioReadCount: Long? = null,
        private val asrAudioReadErrorCount: Long? = null,
        private val asrPeakAmplitude: Float? = null,
        private val asrLastFrameAmplitude: Float? = null,
    ) : VoiceInputProvider {
        private val _state = MutableStateFlow(UiVoiceInputState(engineLabel = "fake"))
        override val state: StateFlow<UiVoiceInputState> = _state
        override val requiresRecordAudioPermission: Boolean = false
        var startCount: Int = 0
        var lastContext: AsrRecognitionContext? = null

        override suspend fun startListening(context: AsrRecognitionContext?) {
            startCount += 1
            lastContext = context
            _state.value = UiVoiceInputState(
                isAvailable = true,
                isListening = true,
                transcript = partialTranscript,
                engineLabel = "fake",
                asrSampleCount = asrSampleCount,
                asrAudioReadCount = asrAudioReadCount,
                asrAudioReadErrorCount = asrAudioReadErrorCount,
                asrPeakAmplitude = asrPeakAmplitude,
                asrLastFrameAmplitude = asrLastFrameAmplitude,
            )
            kotlinx.coroutines.yield()
            _state.value = UiVoiceInputState(
                isAvailable = true,
                isListening = false,
                transcript = finalTranscript,
                transcriptEventId = startCount.toLong(),
                engineLabel = "fake",
                asrSampleCount = asrSampleCount,
                asrAudioReadCount = asrAudioReadCount,
                asrAudioReadErrorCount = asrAudioReadErrorCount,
                asrPeakAmplitude = asrPeakAmplitude,
                asrLastFrameAmplitude = asrLastFrameAmplitude,
            )
        }

        override suspend fun stopListening() = Unit
        override suspend fun cancelListening() = Unit
    }

    private class StaleThenFreshVoiceInputProvider(
        staleTranscript: String,
        private val freshTranscript: String,
    ) : VoiceInputProvider {
        private val _state = MutableStateFlow(
            UiVoiceInputState(
                isAvailable = true,
                isListening = false,
                transcript = staleTranscript,
                transcriptEventId = 7L,
                engineLabel = "fake",
            )
        )
        override val state: StateFlow<UiVoiceInputState> = _state
        override val requiresRecordAudioPermission: Boolean = false
        var startCount: Int = 0

        override suspend fun startListening(context: AsrRecognitionContext?) {
            startCount += 1
            kotlinx.coroutines.yield()
            _state.value = UiVoiceInputState(
                isAvailable = true,
                isListening = true,
                transcript = null,
                transcriptEventId = 7L,
                engineLabel = "fake",
            )
            kotlinx.coroutines.yield()
            _state.value = UiVoiceInputState(
                isAvailable = true,
                isListening = false,
                transcript = freshTranscript,
                transcriptEventId = 8L,
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
        var lastContext: AsrRecognitionContext? = null

        override suspend fun startListening(context: AsrRecognitionContext?) {
            startCount += 1
            lastContext = context
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

    private class PreparingThenHangingVoiceInputProvider : VoiceInputProvider {
        private val _state = MutableStateFlow(UiVoiceInputState(engineLabel = "fake"))
        override val state: StateFlow<UiVoiceInputState> = _state
        override val requiresRecordAudioPermission: Boolean = false
        var startCount: Int = 0

        override suspend fun startListening(context: AsrRecognitionContext?) {
            startCount += 1
            _state.value = UiVoiceInputState(
                isAvailable = true,
                isListening = false,
                engineLabel = "fake",
                statusMessage = "首次加载本地 ASR 模型，可能需要几秒钟…",
            )
            kotlinx.coroutines.yield()
            _state.value = UiVoiceInputState(
                isAvailable = true,
                isListening = true,
                engineLabel = "fake",
            )
        }

        override suspend fun stopListening() = Unit

        override suspend fun cancelListening() {
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

    private class RecoveringVoiceInputProvider(
        private val staleError: String,
        private val finalTranscript: String,
    ) : VoiceInputProvider {
        private val _state = MutableStateFlow(
            UiVoiceInputState(
                engineLabel = "fake",
                isListening = false,
                errorMessage = staleError,
            )
        )
        override val state: StateFlow<UiVoiceInputState> = _state
        override val requiresRecordAudioPermission: Boolean = false
        var startCount: Int = 0
        var cancelCount: Int = 0

        override suspend fun startListening(context: AsrRecognitionContext?) {
            startCount += 1
            kotlinx.coroutines.yield()
            _state.value = UiVoiceInputState(
                isAvailable = true,
                isListening = true,
                engineLabel = "fake",
                errorMessage = null,
            )
            _state.value = UiVoiceInputState(
                isAvailable = true,
                isListening = false,
                transcript = finalTranscript,
                transcriptEventId = startCount.toLong(),
                engineLabel = "fake",
                errorMessage = null,
            )
        }

        override suspend fun stopListening() = Unit

        override suspend fun cancelListening() {
            cancelCount += 1
            _state.value = _state.value.copy(isListening = false)
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

    private class RecordingScreenTranslationPipeline(
        private val result: ScreenTranslationResult,
    ) : ScreenTranslationPipeline {
        var imageBase64: String? = null
        var context: ScreenTranslationContext? = null
        var callCount: Int = 0

        override suspend fun translateCurrentScreen(
            imageBase64: String,
            context: ScreenTranslationContext,
        ): ScreenTranslationResult {
            callCount += 1
            this.imageBase64 = imageBase64
            this.context = context
            return result
        }
    }
}
