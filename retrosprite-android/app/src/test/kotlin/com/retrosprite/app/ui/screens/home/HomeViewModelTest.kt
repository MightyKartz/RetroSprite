package com.retrosprite.app.ui.screens.home

import com.retrosprite.app.ui.viewmodel.EndpointStatusProvider
import com.retrosprite.app.ui.viewmodel.PendingQuestionProvider
import com.retrosprite.app.ui.viewmodel.PlayerQuestionProvider
import com.retrosprite.app.ui.viewmodel.RequestLogProvider
import com.retrosprite.app.ui.viewmodel.UiAnswerFeedback
import com.retrosprite.app.ui.viewmodel.UiEndpointPhase
import com.retrosprite.app.ui.viewmodel.UiEndpointStatus
import com.retrosprite.app.ui.viewmodel.UiOutputMode
import com.retrosprite.app.ui.viewmodel.UiPendingQuestion
import com.retrosprite.app.ui.viewmodel.UiPendingQuestionState
import com.retrosprite.app.ui.viewmodel.UiQuestionResult
import com.retrosprite.app.ui.viewmodel.UiRequestLogItem
import com.retrosprite.app.ui.viewmodel.UiSpoilerLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `uses latest real RetroArch label when label was not manually edited`() = runTest(mainDispatcherRule.dispatcher) {
        val requestLog = FakeRequestLogProvider()
        val viewModel = newViewModel(requestLog = requestLog)

        requestLog.items.value = listOf(logItem(label = "gb__tetris"))
        advanceUntilIdle()

        assertEquals("gb__tetris", viewModel.askState.value.label)
        assertEquals("gb__tetris", viewModel.askState.value.latestRetroArchContext?.label)
        assertFalse(viewModel.askState.value.labelManuallyEdited)
    }

    @Test
    fun `ignores app debug diagnostic failed and blank labels when choosing context`() = runTest(mainDispatcherRule.dispatcher) {
        val requestLog = FakeRequestLogProvider()
        val viewModel = newViewModel(requestLog = requestLog)

        requestLog.items.value = listOf(
            logItem(label = "2048__", rawOutputMode = "app:text"),
            logItem(label = "2048__", rawOutputMode = "debug:text", isDebug = true),
            logItem(label = "diagnostic__retrosprite_self_test"),
            logItem(label = "snes__mario", ok = false),
            logItem(label = ""),
            logItem(label = "nes__zelda"),
        )
        advanceUntilIdle()

        assertEquals("nes__zelda", viewModel.askState.value.label)
        assertEquals("nes__zelda", viewModel.askState.value.latestRetroArchContext?.label)
    }

    @Test
    fun `manual label edit prevents later RetroArch context from overwriting current label`() = runTest(mainDispatcherRule.dispatcher) {
        val requestLog = FakeRequestLogProvider()
        val viewModel = newViewModel(requestLog = requestLog)

        viewModel.updateAskLabel("manual__game")
        requestLog.items.value = listOf(logItem(label = "gb__tetris"))
        advanceUntilIdle()

        assertEquals("manual__game", viewModel.askState.value.label)
        assertEquals("gb__tetris", viewModel.askState.value.latestRetroArchContext?.label)
        assertTrue(viewModel.askState.value.labelManuallyEdited)
    }

    @Test
    fun `latest RetroArch context keeps timestamp paused and evidence status`() = runTest(mainDispatcherRule.dispatcher) {
        val requestLog = FakeRequestLogProvider()
        val viewModel = newViewModel(requestLog = requestLog)

        requestLog.items.value = listOf(
            logItem(
                label = "2048__",
                timestampMillis = 123L,
                paused = true,
                pipelineStage = "evidence",
                sourceIds = listOf("sample.2048.rules"),
            )
        )
        advanceUntilIdle()

        val context = viewModel.askState.value.latestRetroArchContext
        assertEquals("2048__", context?.label)
        assertEquals(123L, context?.timestampMillis)
        assertEquals(true, context?.paused)
        assertEquals("evidence", context?.pipelineStage)
        assertEquals(listOf("sample.2048.rules"), context?.sourceIds)
        assertEquals(true, context?.hasGkpEvidence)
        assertEquals("两个 2 怎么合并？", context?.debugQuestion)
        assertTrue(context?.debugAskCurl.orEmpty().contains("/debug/ask?output=text"))
        assertTrue(context?.debugAskCurl.orEmpty().contains("\"label\":\"2048__\""))
        assertTrue(context?.debugAskCurl.orEmpty().contains("\"question\":\"两个 2 怎么合并？\""))
    }

    @Test
    fun `latest RetroArch context carries consumed hotkey question metadata`() = runTest(mainDispatcherRule.dispatcher) {
        val requestLog = FakeRequestLogProvider()
        val viewModel = newViewModel(requestLog = requestLog)

        requestLog.items.value = listOf(
            logItem(
                label = "2048__",
                question = "两个 2 怎么合并？",
                questionSource = "pending_hotkey",
            )
        )
        advanceUntilIdle()

        val context = viewModel.askState.value.latestRetroArchContext
        assertEquals("两个 2 怎么合并？", context?.question)
        assertEquals("pending_hotkey", context?.questionSource)
    }

    @Test
    fun `real RetroArch hotkey question enters conversation tray and can be restored`() =
        runTest(mainDispatcherRule.dispatcher) {
            val requestLog = FakeRequestLogProvider()
            val viewModel = newViewModel(requestLog = requestLog)

            requestLog.items.value = listOf(
                logItem(
                    id = "hotkey-1",
                    label = "2048__",
                    timestampMillis = 44L,
                    pipelineStage = "evidence",
                    sourceIds = listOf("sample.2048.rules"),
                    question = "两个 2 怎么合并？",
                    questionSource = "pending_hotkey",
                    responseText = "两个相同数字滑到一起会合并成更大的数字。",
                )
            )
            advanceUntilIdle()

            val turn = viewModel.askState.value.conversationTurns.single()
            assertEquals("hotkey-1", turn.id)
            assertEquals("2048__", turn.label)
            assertEquals("两个 2 怎么合并？", turn.question)
            assertEquals("两个相同数字滑到一起会合并成更大的数字。", turn.answerPreview)
            assertEquals(listOf("sample.2048.rules"), turn.result.sourceIds)
            assertEquals("evidence", turn.result.pipelineStage)

            viewModel.applyConversationTurn(turn)

            assertEquals("2048__", viewModel.askState.value.label)
            assertEquals("两个 2 怎么合并？", viewModel.askState.value.question)
            assertEquals("hotkey-1", viewModel.askState.value.lastResult?.requestLogId)
            assertEquals("两个相同数字滑到一起会合并成更大的数字。", viewModel.askState.value.lastResult?.answer)
            assertTrue(viewModel.askState.value.labelManuallyEdited)
        }

    @Test
    fun `conversation tray ignores app debug diagnostic failed and questionless logs`() =
        runTest(mainDispatcherRule.dispatcher) {
            val requestLog = FakeRequestLogProvider()
            val viewModel = newViewModel(requestLog = requestLog)

            requestLog.items.value = listOf(
                logItem(label = "2048__", rawOutputMode = "app:text", question = "app question"),
                logItem(label = "2048__", rawOutputMode = "debug:text", question = "debug question"),
                logItem(label = "diagnostic__retrosprite_self_test", question = "diagnostic question"),
                logItem(label = "snes__mario", ok = false, question = "failed question"),
                logItem(label = "nes__zelda"),
            )
            advanceUntilIdle()

            assertTrue(viewModel.askState.value.conversationTurns.isEmpty())
        }

    @Test
    fun `question drafts follow known RetroArch label`() = runTest(mainDispatcherRule.dispatcher) {
        val requestLog = FakeRequestLogProvider()
        val viewModel = newViewModel(requestLog = requestLog)

        assertEquals("两个 2 怎么合并？", viewModel.askState.value.questionDrafts.first().question)

        requestLog.items.value = listOf(logItem(label = "relay_station__"))
        advanceUntilIdle()

        val drafts = viewModel.askState.value.questionDrafts
        assertEquals("蓝色保险丝在哪？", drafts.first().question)
        assertEquals("我现在应该先修什么？", drafts[1].question)
    }

    @Test
    fun `applyQuestionDraft fills question without submitting`() = runTest(mainDispatcherRule.dispatcher) {
        val requestLog = FakeRequestLogProvider()
        val playerQuestion = FakePlayerQuestionProvider()
        val viewModel = newViewModel(
            requestLog = requestLog,
            playerQuestion = playerQuestion,
        )

        val draft = viewModel.askState.value.questionDrafts[1]
        viewModel.applyQuestionDraft(draft)
        advanceUntilIdle()

        assertEquals("开局应该优先往哪个方向滑？", viewModel.askState.value.question)
        assertEquals(null, playerQuestion.lastQuestion)
    }

    @Test
    fun `restoreLatestRetroArchContext resets manual override to latest label`() = runTest(mainDispatcherRule.dispatcher) {
        val requestLog = FakeRequestLogProvider()
        val viewModel = newViewModel(requestLog = requestLog)

        viewModel.updateAskLabel("manual__game")
        requestLog.items.value = listOf(logItem(label = "gb__tetris"))
        advanceUntilIdle()

        viewModel.restoreLatestRetroArchContext()

        assertEquals("gb__tetris", viewModel.askState.value.label)
        assertFalse(viewModel.askState.value.labelManuallyEdited)
    }

    @Test
    fun `askQuestion uses auto applied RetroArch label`() = runTest(mainDispatcherRule.dispatcher) {
        val requestLog = FakeRequestLogProvider()
        val playerQuestion = FakePlayerQuestionProvider()
        val viewModel = newViewModel(
            requestLog = requestLog,
            playerQuestion = playerQuestion,
        )

        requestLog.items.value = listOf(logItem(label = "gb__tetris"))
        viewModel.updateQuestion("怎么开始？")
        advanceUntilIdle()

        viewModel.askQuestion()
        advanceUntilIdle()

        assertEquals("gb__tetris", playerQuestion.lastLabel)
        assertEquals("怎么开始？", playerQuestion.lastQuestion)
        assertEquals(null, playerQuestion.lastSpoilerLevelOverride)
        assertEquals("gb__tetris", viewModel.askState.value.lastResult?.label)
    }

    @Test
    fun `askQuestion records recent conversation turns and restores selected turn`() =
        runTest(mainDispatcherRule.dispatcher) {
            val playerQuestion = SequencedPlayerQuestionProvider()
            val viewModel = newViewModel(playerQuestion = playerQuestion)

            viewModel.updateQuestion("first question?")
            viewModel.askQuestion()
            advanceUntilIdle()
            viewModel.updateAskLabel("relay_station__")
            viewModel.updateQuestion("second question?")
            viewModel.askQuestion()
            advanceUntilIdle()

            val turns = viewModel.askState.value.conversationTurns
            assertEquals(2, turns.size)
            assertEquals("second question?", turns[0].question)
            assertEquals("second answer", turns[0].answerPreview)
            assertEquals("first question?", turns[1].question)
            assertEquals("first answer", turns[1].answerPreview)

            viewModel.applyConversationTurn(turns[1])

            assertEquals("2048__", viewModel.askState.value.label)
            assertEquals("first question?", viewModel.askState.value.question)
            assertEquals("first answer", viewModel.askState.value.lastResult?.answer)
            assertTrue(viewModel.askState.value.labelManuallyEdited)
        }

    @Test
    fun `applyConversationFollowUpDraft fills follow up without submitting`() =
        runTest(mainDispatcherRule.dispatcher) {
            val playerQuestion = SequencedPlayerQuestionProvider()
            val viewModel = newViewModel(playerQuestion = playerQuestion)

            viewModel.updateQuestion("where is the blue fuse?")
            viewModel.askQuestion()
            advanceUntilIdle()

            val turn = viewModel.askState.value.conversationTurns.first()
            val clearerDraft = turn.followUpDrafts.first { it.id == "clearer" }
            val directDraft = turn.followUpDrafts.first { it.id == "direct" }
            assertEquals(UiSpoilerLevel.Clear, clearerDraft.spoilerLevelOverride)
            viewModel.applyConversationFollowUpDraft(turn, directDraft)
            advanceUntilIdle()

            assertEquals("2048__", viewModel.askState.value.label)
            assertEquals(
                "我确认可以接受更多剧透。请直接回答：where is the blue fuse?",
                viewModel.askState.value.question,
            )
            assertEquals(
                "直接答案会主动提高剧透级别；提交前请确认你愿意看到更明确的信息。",
                viewModel.askState.value.spoilerEscalationNotice,
            )
            assertEquals(UiSpoilerLevel.Direct, viewModel.askState.value.spoilerLevelOverride)
            assertEquals("first answer", viewModel.askState.value.lastResult?.answer)
            assertEquals(1, playerQuestion.count)
            assertTrue(viewModel.askState.value.labelManuallyEdited)

            viewModel.askQuestion()
            advanceUntilIdle()

            assertEquals(UiSpoilerLevel.Direct, playerQuestion.lastSpoilerLevelOverride)
            assertEquals(null, viewModel.askState.value.spoilerLevelOverride)

            viewModel.updateQuestion("manual follow-up")

            assertEquals(null, viewModel.askState.value.spoilerEscalationNotice)
            assertEquals(null, viewModel.askState.value.spoilerLevelOverride)
        }

    @Test
    fun `preparePendingQuestion queues current question without submitting`() =
        runTest(mainDispatcherRule.dispatcher) {
            val playerQuestion = FakePlayerQuestionProvider()
            val pendingQuestion = FakePendingQuestionProvider()
            val viewModel = newViewModel(
                playerQuestion = playerQuestion,
                pendingQuestion = pendingQuestion,
            )

            viewModel.updateAskLabel("relay_station__")
            viewModel.updateQuestion("蓝色保险丝在哪？")
            viewModel.preparePendingQuestion()
            advanceUntilIdle()

            assertEquals(null, playerQuestion.lastQuestion)
            assertEquals("relay_station__", pendingQuestion.state.value.pending?.label)
            assertEquals("蓝色保险丝在哪？", pendingQuestion.state.value.pending?.question)
            assertEquals(UiSpoilerLevel.Light, pendingQuestion.state.value.pending?.spoilerLevel)
        }

    @Test
    fun `preparePendingQuestion preserves one shot spoiler override and clears warning`() =
        runTest(mainDispatcherRule.dispatcher) {
            val pendingQuestion = FakePendingQuestionProvider()
            val playerQuestion = SequencedPlayerQuestionProvider()
            val viewModel = newViewModel(
                playerQuestion = playerQuestion,
                pendingQuestion = pendingQuestion,
            )

            viewModel.updateQuestion("where is the blue fuse?")
            viewModel.askQuestion()
            advanceUntilIdle()

            val turn = viewModel.askState.value.conversationTurns.first()
            val directDraft = turn.followUpDrafts.first { it.id == "direct" }
            viewModel.applyConversationFollowUpDraft(turn, directDraft)
            viewModel.preparePendingQuestion()
            advanceUntilIdle()

            assertEquals(UiSpoilerLevel.Direct, pendingQuestion.state.value.pending?.spoilerLevel)
            assertEquals(null, viewModel.askState.value.spoilerEscalationNotice)
            assertEquals(null, viewModel.askState.value.spoilerLevelOverride)
            assertEquals(1, playerQuestion.count)
        }

    @Test
    fun `clearPendingQuestion delegates to provider`() =
        runTest(mainDispatcherRule.dispatcher) {
            val pendingQuestion = FakePendingQuestionProvider()
            val viewModel = newViewModel(pendingQuestion = pendingQuestion)

            viewModel.preparePendingQuestion()
            advanceUntilIdle()
            assertEquals("两个 2 怎么合并？", pendingQuestion.state.value.pending?.question)

            viewModel.clearPendingQuestion()
            advanceUntilIdle()

            assertEquals(null, pendingQuestion.state.value.pending)
        }

    @Test
    fun `submitAnswerFeedback stores feedback locally and delegates to request log provider`() =
        runTest(mainDispatcherRule.dispatcher) {
            val requestLog = FakeRequestLogProvider()
            val playerQuestion = FakePlayerQuestionProvider()
            val viewModel = newViewModel(
                requestLog = requestLog,
                playerQuestion = playerQuestion,
            )

            viewModel.askQuestion()
            advanceUntilIdle()

            viewModel.submitAnswerFeedback(UiAnswerFeedback.Incorrect)
            advanceUntilIdle()

            assertEquals(UiAnswerFeedback.Incorrect, viewModel.askState.value.lastResult?.feedback)
            assertEquals("app-question", requestLog.lastFeedbackRequestId)
            assertEquals(UiAnswerFeedback.Incorrect, requestLog.lastFeedback)
        }

    private fun newViewModel(
        endpoint: EndpointStatusProvider = FakeEndpointStatusProvider(),
        playerQuestion: PlayerQuestionProvider = FakePlayerQuestionProvider(),
        pendingQuestion: PendingQuestionProvider = FakePendingQuestionProvider(),
        requestLog: FakeRequestLogProvider = FakeRequestLogProvider(),
    ): HomeViewModel = HomeViewModel(
        endpoint = endpoint,
        playerQuestion = playerQuestion,
        pendingQuestion = pendingQuestion,
        requestLog = requestLog,
    )

    private fun logItem(
        id: String? = null,
        label: String?,
        ok: Boolean = true,
        rawOutputMode: String = "text",
        isDebug: Boolean = false,
        timestampMillis: Long = 1L,
        paused: Boolean = true,
        pipelineStage: String = "unknown",
        sourceIds: List<String> = emptyList(),
        question: String? = null,
        questionSource: String? = null,
        responseText: String = "ok",
    ): UiRequestLogItem = UiRequestLogItem(
        id = id ?: label ?: "blank",
        timestampMillis = timestampMillis,
        label = label,
        imageBytes = 0,
        paused = paused,
        outputMode = UiOutputMode.Text,
        question = question,
        questionSource = questionSource,
        responsePreview = responseText,
        responseText = responseText,
        fullResponseJson = """{"text":"ok"}""",
        durationMillis = 0L,
        ok = ok,
        isDebug = isDebug,
        sourceIds = sourceIds,
        pipelineStage = pipelineStage,
        rawOutputMode = rawOutputMode,
    )

    private class FakeEndpointStatusProvider : EndpointStatusProvider {
        override val status: StateFlow<UiEndpointStatus> = MutableStateFlow(
            UiEndpointStatus(
                phase = UiEndpointPhase.Running,
                port = 4_404,
                baseUrl = "http://localhost:4404",
            )
        )

        override suspend fun restart() = Unit

        override suspend fun checkHealth() = Unit
    }

    private class FakeRequestLogProvider : RequestLogProvider {
        val items = MutableStateFlow<List<UiRequestLogItem>>(emptyList())
        var lastFeedbackRequestId: String? = null
            private set
        var lastFeedback: UiAnswerFeedback? = null
            private set
        override val log: Flow<List<UiRequestLogItem>> = items

        override suspend fun clear() {
            items.value = emptyList()
        }

        override suspend fun sendConnectionTest() = Unit

        override suspend fun submitFeedback(requestId: String, feedback: UiAnswerFeedback) {
            lastFeedbackRequestId = requestId
            lastFeedback = feedback
        }
    }

    private class FakePlayerQuestionProvider : PlayerQuestionProvider {
        var lastLabel: String? = null
        var lastQuestion: String? = null
        var lastSpoilerLevelOverride: UiSpoilerLevel? = null

        override suspend fun ask(label: String, question: String): UiQuestionResult {
            return ask(label, question, spoilerLevelOverride = null)
        }

        override suspend fun ask(
            label: String,
            question: String,
            spoilerLevelOverride: UiSpoilerLevel?,
        ): UiQuestionResult {
            lastLabel = label
            lastQuestion = question
            lastSpoilerLevelOverride = spoilerLevelOverride
            return UiQuestionResult(
                requestLogId = "app-question",
                label = label,
                question = question,
                answer = "answer",
                ok = true,
                timestampMillis = 1L,
                pipelineStage = "evidence",
            )
        }
    }

    private class FakePendingQuestionProvider : PendingQuestionProvider {
        private val _state = MutableStateFlow(UiPendingQuestionState())
        override val state: StateFlow<UiPendingQuestionState> = _state

        override suspend fun prepare(
            label: String,
            question: String,
            spoilerLevelOverride: UiSpoilerLevel?,
        ) {
            _state.value = UiPendingQuestionState(
                pending = UiPendingQuestion(
                    label = label,
                    question = question,
                    spoilerLevel = spoilerLevelOverride ?: UiSpoilerLevel.Light,
                    createdAtMillis = 1L,
                )
            )
        }

        override suspend fun clear() {
            _state.value = UiPendingQuestionState()
        }
    }

    private class SequencedPlayerQuestionProvider : PlayerQuestionProvider {
        var count = 0
            private set
        var lastSpoilerLevelOverride: UiSpoilerLevel? = null
            private set

        override suspend fun ask(label: String, question: String): UiQuestionResult {
            return ask(label, question, spoilerLevelOverride = null)
        }

        override suspend fun ask(
            label: String,
            question: String,
            spoilerLevelOverride: UiSpoilerLevel?,
        ): UiQuestionResult {
            count += 1
            lastSpoilerLevelOverride = spoilerLevelOverride
            return UiQuestionResult(
                requestLogId = "app-question-$count",
                label = label,
                question = question,
                answer = when (count) {
                    1 -> "first answer"
                    else -> "second answer"
                },
                ok = true,
                timestampMillis = count.toLong(),
                pipelineStage = "evidence",
                llmStatus = "skipped",
            )
        }
    }

    class MainDispatcherRule(
        val dispatcher: TestDispatcher = StandardTestDispatcher(),
    ) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(dispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
