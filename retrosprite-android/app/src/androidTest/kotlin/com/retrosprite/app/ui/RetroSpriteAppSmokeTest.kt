package com.retrosprite.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.retrosprite.app.ui.theme.RetroSpriteTheme
import com.retrosprite.app.ui.viewmodel.GkpLibraryProvider
import com.retrosprite.app.ui.viewmodel.LlmConfigTestProvider
import com.retrosprite.app.ui.viewmodel.PendingQuestionProvider
import com.retrosprite.app.ui.viewmodel.ProvideUiDependencies
import com.retrosprite.app.ui.viewmodel.PlayerQuestionProvider
import com.retrosprite.app.ui.viewmodel.PreviewStub
import com.retrosprite.app.ui.viewmodel.RequestLogProvider
import com.retrosprite.app.ui.viewmodel.UiAnswerFeedback
import com.retrosprite.app.ui.viewmodel.UiDependencies
import com.retrosprite.app.ui.viewmodel.UiGkpDeletePhase
import com.retrosprite.app.ui.viewmodel.UiGkpDeletePlan
import com.retrosprite.app.ui.viewmodel.UiGkpDeleteState
import com.retrosprite.app.ui.viewmodel.UiGkpLibraryState
import com.retrosprite.app.ui.viewmodel.UiLlmConfigTestResult
import com.retrosprite.app.ui.viewmodel.UiLlmProvider
import com.retrosprite.app.ui.viewmodel.UiOutputMode
import com.retrosprite.app.ui.viewmodel.UiPendingQuestion
import com.retrosprite.app.ui.viewmodel.UiPendingQuestionState
import com.retrosprite.app.ui.viewmodel.UiQuestionResult
import com.retrosprite.app.ui.viewmodel.UiRequestLogItem
import com.retrosprite.app.ui.viewmodel.UiSettings
import com.retrosprite.app.ui.viewmodel.UiSpoilerLevel
import com.retrosprite.app.ui.viewmodel.UiVoiceInputState
import com.retrosprite.app.ui.viewmodel.VoiceInputProvider
import com.retrosprite.app.voice.asr.AsrRecognitionContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test verifying that the root Scaffold renders player-facing tabs and that
 * advanced diagnostics stays reachable through Settings/recovery actions.
 *
 * Uses [PreviewStub] dependencies so the test runs without a real endpoint or DataStore.
 */
@RunWith(AndroidJUnit4::class)
class RetroSpriteAppSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setRoot(deps: UiDependencies = previewDeps()) {
        composeRule.setContent {
            RetroSpriteTheme {
                ProvideUiDependencies(deps = deps) {
                    RetroSpriteRoot()
                }
            }
        }
    }

    private fun previewDeps(): UiDependencies = UiDependencies(
        endpoint = PreviewStub.endpoint(),
        requestLog = PreviewStub.requestLog(),
        settingsStore = PreviewStub.settings()
    )

    @Test
    fun bottomBarShowsPlayerFacingTabs() {
        setRoot()
        composeRule.onNodeWithText("\u9996\u9875").assertIsDisplayed()
        composeRule.onNodeWithText("\u77e5\u8bc6\u5305").assertIsDisplayed()
        composeRule.onNodeWithText("\u8bbe\u7f6e").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("\u8bca\u65ad").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("RETROARCH 接入指引").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("home_advanced_question_tools").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun canNavigateThroughPlayerTabsAndAdvancedDiagnostics() {
        setRoot()

        // Default Home tab: readiness card visible
        composeRule.onNodeWithText("游戏内语音就绪").assertIsDisplayed()
        composeRule.onNodeWithTag("home_hotkey_signal_diagnostics").assertIsDisplayed()

        // Switch to Packs
        composeRule.onNodeWithText("\u77e5\u8bc6\u5305").performClick()
        composeRule.onNodeWithText("\u6e38\u620f\u77e5\u8bc6\u5305").assertIsDisplayed()
        composeRule.onNodeWithTag("packs_import_status").assertIsDisplayed()

        // Switch to Settings
        composeRule.onNodeWithText("\u8bbe\u7f6e").performClick()
        composeRule.onNodeWithTag("settings_overlay_permission_section")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("settings_microphone_permission_section")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("RetroArch 连接设置")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Narrator Mode（旁白模式）")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("RetroArch -> Settings -> AI Service -> Pause During Translation -> ON")
            .performScrollTo()
            .assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Image Mode").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("settings_developer_diagnostics_open")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("\u5feb\u901f\u8bca\u65ad".uppercase()).assertIsDisplayed()
        composeRule.onNodeWithText(
            "推荐：RetroArch -> Settings -> AI Service -> Pause During Translation -> ON"
        ).assertIsDisplayed()

        // Back to Home
        composeRule.onNodeWithText("\u9996\u9875").performClick()
        composeRule.onNodeWithText("游戏内语音就绪").assertIsDisplayed()
    }

    @Test
    fun packsDeleteConfirmationPlanRenders() {
        setRoot(
            deps = previewDeps().copy(
                gkpLibrary = DeletePlanGkpLibraryProvider(),
            )
        )

        composeRule.onNodeWithText("\u77e5\u8bc6\u5305").performClick()
        composeRule.onNodeWithTag("packs_delete_plan")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("packs_delete_confirm_button")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("packs_delete_cancel_button")
            .assertIsDisplayed()
    }

    @Test
    fun homeQuestionShowsAnswerAndWritesAppDiagnosticsLog() {
        val requestLog = RecordingRequestLogProvider()
        val pendingQuestion = RecordingPendingQuestionProvider()
        setRoot(
            deps = UiDependencies(
                endpoint = PreviewStub.endpoint(),
                requestLog = requestLog,
                settingsStore = PreviewStub.settings(),
                playerQuestion = LoggingPlayerQuestionProvider(requestLog),
                pendingQuestion = pendingQuestion,
            )
        )
        openAdvancedQuestionTools()

        composeRule.onNodeWithTag("home_input_flow_note")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("上下文：默认样例").assertIsDisplayed()
        composeRule.onNodeWithText("问题：App 内输入").assertIsDisplayed()
        composeRule.onNodeWithTag("home_voice_controls")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("home_voice_input_button")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("home_voice_transcript")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("home_voice_transcript")
            .performScrollTo()
            .assertTextContains("两个 2 怎么合并？", substring = true)
        composeRule.onNodeWithTag("home_question_input")
            .assertTextContains("两个 2 怎么合并？")
        composeRule.onNodeWithTag("home_question_drafts")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("home_question_draft_1")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("home_question_input")
            .assertTextContains("开局应该优先往哪个方向滑？")

        composeRule.onNodeWithTag("home_question_input")
            .performScrollTo()
            .performTextReplacement(HOME_QUESTION)
        composeRule.onNodeWithTag("home_prepare_hotkey_question_button")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            pendingQuestion.state.value.pending?.question == HOME_QUESTION
        }
        composeRule.onNodeWithTag("home_pending_question_card")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("已准备给下次 RetroArch 热键").assertIsDisplayed()
        assertEquals(0, requestLog.items.value.size)
        composeRule.onNodeWithTag("home_pending_question_clear_button")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            pendingQuestion.state.value.pending == null
        }

        composeRule.onNodeWithTag("home_ask_button")
            .performScrollTo()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            requestLog.items.value.any { it.rawOutputMode == "app:text" }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home_question_result")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("home_speak_answer_button")
            .performScrollTo()
            .assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithText(HOME_ANSWER)
                .fetchSemanticsNodes()
                .isNotEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText("\u6765\u6e90\uff1a$SOURCE_ID")
                .fetchSemanticsNodes()
                .isNotEmpty()
        )
        composeRule.onNodeWithTag("home_conversation_tray")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("home_conversation_turn_0")
            .assertTextContains(HOME_QUESTION)
            .assertTextContains(HOME_ANSWER)
            .performClick()
        composeRule.onNodeWithTag("home_question_input")
            .assertTextContains(HOME_QUESTION)
        composeRule.onNodeWithTag("home_conversation_followup_0_direct")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("home_question_input")
            .assertTextContains("我确认可以接受更多剧透。请直接回答：$HOME_QUESTION")
        composeRule.onNodeWithTag("home_spoiler_escalation_notice")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("剧透级别提升").assertIsDisplayed()
        composeRule.onNodeWithText("直接答案会主动提高剧透级别", substring = true).assertIsDisplayed()
        assertEquals(1, requestLog.items.value.size)
        composeRule.onNodeWithTag("home_feedback_incorrect")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("home_feedback_status")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("已记录：这不对").assertIsDisplayed()

        openDiagnosticsFromSettings()
        composeRule.onNodeWithTag("diagnostics_log_item_app-question").assertIsDisplayed()
        composeRule.onNodeWithText("APP").assertIsDisplayed()
        composeRule.onNodeWithText("INCORRECT").assertIsDisplayed()
        composeRule.onNodeWithText("EVIDENCE").assertIsDisplayed()
        composeRule.onNodeWithText("SRC 1").assertIsDisplayed()
    }

    @Test
    fun homeVoiceControlsShowLocalAsrStatusAndEmptyResultHint() {
        val voiceInput = MutableVoiceInputProvider(
            UiVoiceInputState(
                isAvailable = true,
                engineLabel = "sherpa-onnx 本地 ASR",
                statusMessage = "首次加载本地 ASR 模型，可能需要几秒钟…",
            )
        )
        setRoot(
            deps = previewDeps().copy(
                voiceInput = voiceInput,
            )
        )
        openAdvancedQuestionTools()

        composeRule.onNodeWithTag("home_voice_controls")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("home_voice_status")
            .assertTextContains("首次加载本地 ASR 模型", substring = true)

        voiceInput.setState(
            UiVoiceInputState(
                isAvailable = true,
                engineLabel = "sherpa-onnx 本地 ASR",
                errorMessage = "没有识别到问题，可再试一次或使用文字输入。",
            )
        )
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("home_voice_error")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithTag("home_voice_controls")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("home_voice_error")
            .assertTextContains("没有识别到问题", substring = true)
    }

    @Test
    fun disabledGkpContextIsExplainedOnHomeAndDiagnostics() {
        val requestLog = RecordingRequestLogProvider()
        requestLog.prepend(
            UiRequestLogItem(
                id = "disabled-gkp",
                timestampMillis = System.currentTimeMillis(),
                label = "2048__",
                imageBytes = 0,
                paused = true,
                outputMode = UiOutputMode.Text,
                responsePreview = "知识包已禁用：我找到了当前游戏的本地 GKP。",
                fullResponseJson = """{"text":"知识包已禁用","pipeline_stage":"gkp_disabled"}""",
                durationMillis = 3L,
                ok = true,
                pipelineStage = "gkp_disabled",
                llmStatus = "skipped",
            )
        )
        setRoot(
            deps = UiDependencies(
                endpoint = PreviewStub.endpoint(),
                requestLog = requestLog,
                settingsStore = PreviewStub.settings(),
            )
        )

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("知识包已停用").fetchSemanticsNodes().isNotEmpty()
        }
        openAdvancedQuestionTools()
        composeRule.onNodeWithTag("home_gkp_disabled_notice")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("home_context_use_button")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("home_context_copy_debug_curl")
            .performScrollTo()
            .assertIsDisplayed()

        openDiagnosticsFromSettings()
        composeRule.onNodeWithTag("diagnostics_log_item_disabled-gkp")
            .assertIsDisplayed()
            .performClick()
        assertTrue(
            composeRule.onAllNodesWithText("GKP_DISABLED", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        )
        composeRule.onNodeWithText("已匹配到知识包", substring = true).assertIsDisplayed()
    }

    @Test
    fun diagnosticsSourceFiltersCountLogs() {
        val requestLog = RecordingRequestLogProvider()
        requestLog.items.value = listOf(
            diagnosticsLogItem(id = "retroarch-log"),
            diagnosticsLogItem(
                id = "pending-log",
                question = HOME_QUESTION,
                questionSource = "pending_hotkey",
            ),
            diagnosticsLogItem(
                id = "app-log",
                rawOutputMode = "app:text",
                question = HOME_QUESTION,
                questionSource = "app",
            ),
            diagnosticsLogItem(
                id = "debug-log",
                rawOutputMode = "debug:text",
                question = HOME_QUESTION,
                questionSource = "debug",
                isDebug = true,
            ),
        )
        setRoot(
            deps = UiDependencies(
                endpoint = PreviewStub.endpoint(),
                requestLog = requestLog,
                settingsStore = PreviewStub.settings(),
            )
        )

        openDiagnosticsFromSettings()
        composeRule.onNodeWithTag("diagnostics_source_filters")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("diagnostics_filter_all")
            .assertTextContains("全部 4")
        composeRule.onNodeWithTag("diagnostics_filter_retroarch")
            .assertTextContains("RetroArch 1")
        composeRule.onNodeWithTag("diagnostics_filter_pending")
            .assertTextContains("Pending 1")
        composeRule.onNodeWithTag("diagnostics_filter_app")
            .assertTextContains("App 1")
        composeRule.onNodeWithTag("diagnostics_filter_debug")
            .assertTextContains("Debug 1")

        composeRule.onNodeWithTag("diagnostics_filter_summary")
            .assertTextContains("当前：全部 4 / 4")
    }

    @Test
    fun homeRestoresPendingHotkeyLogIntoConversationTray() {
        val requestLog = RecordingRequestLogProvider()
        requestLog.prepend(
            UiRequestLogItem(
                id = "pending-hotkey-log",
                timestampMillis = System.currentTimeMillis(),
                label = "2048__",
                imageBytes = 0,
                paused = true,
                outputMode = UiOutputMode.Text,
                question = HOME_QUESTION,
                questionSource = "pending_hotkey",
                responsePreview = HOME_ANSWER,
                responseText = HOME_ANSWER,
                fullResponseJson = """{"text":"$HOME_ANSWER","question":"$HOME_QUESTION","question_source":"pending_hotkey"}""",
                durationMillis = 7L,
                ok = true,
                sourceIds = listOf(SOURCE_ID),
                pipelineStage = "evidence",
                llmStatus = "skipped",
            )
        )
        setRoot(
            deps = UiDependencies(
                endpoint = PreviewStub.endpoint(),
                requestLog = requestLog,
                settingsStore = PreviewStub.settings(),
            )
        )
        openAdvancedQuestionTools()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("最近问答").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home_conversation_tray")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("home_conversation_turn_0")
            .assertTextContains(HOME_QUESTION)
            .assertTextContains(HOME_ANSWER)
            .performClick()
        composeRule.onNodeWithTag("home_question_input")
            .assertTextContains(HOME_QUESTION)
        composeRule.onNodeWithTag("home_question_result")
            .performScrollTo()
            .assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithText("来源：$SOURCE_ID")
                .fetchSemanticsNodes()
                .isNotEmpty()
        )
    }

    @Test
    fun homeRecoveryActionForDisabledGkpNavigatesToPacks() {
        val requestLog = RecordingRequestLogProvider()
        setRoot(
            deps = UiDependencies(
                endpoint = PreviewStub.endpoint(),
                requestLog = requestLog,
                settingsStore = PreviewStub.settings(),
                playerQuestion = RecoveryPlayerQuestionProvider(
                    requestLog = requestLog,
                    requestId = "app-disabled-gkp-question",
                    answer = DISABLED_GKP_ANSWER,
                    ok = true,
                    pipelineStage = "gkp_disabled",
                    llmStatus = "skipped",
                ),
            )
        )
        openAdvancedQuestionTools()

        composeRule.onNodeWithTag("home_question_input")
            .performScrollTo()
            .performTextReplacement("Can you answer from the disabled pack?")
        composeRule.onNodeWithTag("home_ask_button")
            .performScrollTo()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            requestLog.items.value.any { it.id == "app-disabled-gkp-question" }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home_recovery_hint")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("下一步：重新启用 GKP").assertIsDisplayed()
        composeRule.onNodeWithText("打开知识包").assertIsDisplayed()
        composeRule.onNodeWithTag("home_recovery_action")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("当前知识包").assertIsDisplayed()
        composeRule.onNodeWithTag("packs_import_status").assertIsDisplayed()
    }

    @Test
    fun homeRecoveryActionForRequestErrorNavigatesToDiagnostics() {
        val requestLog = RecordingRequestLogProvider()
        setRoot(
            deps = UiDependencies(
                endpoint = PreviewStub.endpoint(),
                requestLog = requestLog,
                settingsStore = PreviewStub.settings(),
                playerQuestion = RecoveryPlayerQuestionProvider(
                    requestLog = requestLog,
                    requestId = "app-error-question",
                    answer = "",
                    ok = false,
                    pipelineStage = "error",
                    llmStatus = "skipped",
                    errorMessage = ERROR_ANSWER,
                ),
            )
        )
        openAdvancedQuestionTools()

        composeRule.onNodeWithTag("home_question_input")
            .performScrollTo()
            .performTextReplacement("Why did the request fail?")
        composeRule.onNodeWithTag("home_ask_button")
            .performScrollTo()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            requestLog.items.value.any { it.id == "app-error-question" }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home_recovery_hint")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("下一步：查看诊断详情").assertIsDisplayed()
        composeRule.onNodeWithText("打开诊断日志").assertIsDisplayed()
        composeRule.onNodeWithTag("home_recovery_action")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("快速诊断".uppercase()).assertIsDisplayed()
        composeRule.onNodeWithTag("diagnostics_log_item_app-error-question")
            .assertIsDisplayed()
            .performClick()
        assertTrue(
            composeRule.onAllNodesWithText("ERROR", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        )
    }

    @Test
    fun homeQuestionSurfacesLlmTimeoutDiagnostics() {
        val requestLog = RecordingRequestLogProvider()
        setRoot(
            deps = UiDependencies(
                endpoint = PreviewStub.endpoint(),
                requestLog = requestLog,
                settingsStore = PreviewStub.settings(),
                playerQuestion = TimeoutPlayerQuestionProvider(requestLog),
            )
        )
        openAdvancedQuestionTools()

        composeRule.onNodeWithTag("home_question_input")
            .performScrollTo()
            .performTextReplacement("What should I do next?")
        composeRule.onNodeWithTag("home_ask_button")
            .performScrollTo()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            requestLog.items.value.any { it.id == "app-timeout-question" }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home_question_result")
            .performScrollTo()
            .assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithText(TIMEOUT_ANSWER)
                .fetchSemanticsNodes()
                .isNotEmpty()
        )
        composeRule.onNodeWithText("LLM FAILED").assertIsDisplayed()
        composeRule.onNodeWithText("模型：deepseek / deepseek-v4-pro").assertIsDisplayed()
        composeRule.onNodeWithText("预算：64 tok · timeout 5000ms").assertIsDisplayed()
        composeRule.onNodeWithText("LLM 错误：timeout while waiting for provider").assertIsDisplayed()
        composeRule.onNodeWithTag("home_recovery_hint")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("下一步：检查 LLM 配置").assertIsDisplayed()
        composeRule.onNodeWithText("打开设置").assertIsDisplayed()
        composeRule.onNodeWithTag("home_recovery_action")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_overlay_permission_section")
            .performScrollTo()
            .assertIsDisplayed()

        openDiagnosticsFromSettings()
        composeRule.onNodeWithTag("diagnostics_log_item_app-timeout-question")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("LLM FAILED", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("模型：deepseek / deepseek-v4-pro").assertIsDisplayed()
        composeRule.onNodeWithText("LLM 错误：timeout while waiting for provider").assertIsDisplayed()
    }

    private fun openAdvancedQuestionTools() {
        composeRule.onNodeWithText("\u8bbe\u7f6e").performClick()
        composeRule.onNodeWithTag("settings_app_question_console_open")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
    }

    private fun openDiagnosticsFromSettings() {
        composeRule.onNodeWithText("\u8bbe\u7f6e").performClick()
        composeRule.onNodeWithTag("settings_developer_diagnostics_open")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun settingsLlmSelfTestShowsResultWithoutWritingRequestLog() {
        val requestLog = RecordingRequestLogProvider()
        val llmConfigTest = RecordingLlmConfigTestProvider()
        setRoot(
            deps = UiDependencies(
                endpoint = PreviewStub.endpoint(),
                requestLog = requestLog,
                settingsStore = PreviewStub.settings(
                    UiSettings(
                        llmProvider = UiLlmProvider.DeepSeek,
                        llmApiKey = "sk-test",
                        llmBaseUrl = "",
                        llmModel = "",
                        llmTimeoutSeconds = 5,
                        llmMaxTokens = 32,
                    )
                ),
                llmConfigTest = llmConfigTest,
            )
        )

        composeRule.onNodeWithText("\u8bbe\u7f6e").performClick()
        composeRule.onNodeWithTag("settings_llm_test_button")
            .performScrollTo()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { llmConfigTest.callCount == 1 }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_llm_test_result")
            .performScrollTo()
            .assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("模型连接正常").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("模型：deepseek / deepseek-v4-pro").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("长度上限：32 token · 超时 5000ms · 耗时 42ms").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("用量：输入 9 / 输出 1").fetchSemanticsNodes().isNotEmpty())
        assertTrue(requestLog.items.value.isEmpty())
    }

    private class RecordingRequestLogProvider : RequestLogProvider {
        val items = MutableStateFlow<List<UiRequestLogItem>>(emptyList())
        override val log: Flow<List<UiRequestLogItem>> = items

        fun prepend(item: UiRequestLogItem) {
            items.update { current -> listOf(item) + current }
        }

        override suspend fun clear() {
            items.value = emptyList()
        }

        override suspend fun sendConnectionTest() {
            prepend(
                UiRequestLogItem(
                    id = "connection-test",
                    timestampMillis = System.currentTimeMillis(),
                    label = "[connection-test]",
                    imageBytes = 0,
                    paused = true,
                    outputMode = UiOutputMode.Text,
                    responsePreview = "Connection ok",
                    fullResponseJson = """{"text":"Connection ok"}""",
                    durationMillis = 1L,
                    ok = true,
                )
            )
        }

        override suspend fun submitFeedback(requestId: String, feedback: UiAnswerFeedback) {
            val now = System.currentTimeMillis()
            items.update { current ->
                current.map { row ->
                    if (row.id == requestId) {
                        row.copy(feedback = feedback, feedbackTimestampMillis = now)
                    } else {
                        row
                    }
                }
            }
        }
    }

    private fun diagnosticsLogItem(
        id: String,
        rawOutputMode: String = "text",
        question: String? = null,
        questionSource: String? = null,
        isDebug: Boolean = false,
    ): UiRequestLogItem = UiRequestLogItem(
        id = id,
        timestampMillis = System.currentTimeMillis(),
        label = "2048__",
        imageBytes = 0,
        paused = true,
        outputMode = UiOutputMode.Text,
        rawOutputMode = rawOutputMode,
        question = question,
        questionSource = questionSource,
        responsePreview = "OK",
        fullResponseJson = """{"text":"OK"}""",
        durationMillis = 4L,
        ok = true,
        isDebug = isDebug,
        pipelineStage = "evidence",
        llmStatus = "skipped",
    )

    private class RecordingLlmConfigTestProvider : LlmConfigTestProvider {
        @Volatile
        var callCount = 0
            private set

        override suspend fun test(settings: UiSettings): UiLlmConfigTestResult {
            callCount += 1
            return UiLlmConfigTestResult(
                ok = true,
                provider = settings.llmProvider.id,
                model = settings.llmModel.ifBlank { settings.llmProvider.defaultModel },
                maxTokens = settings.llmMaxTokens,
                timeoutMs = settings.llmTimeoutSeconds * 1_000L,
                latencyMs = 42L,
                tokensIn = 9,
                tokensOut = 1,
                responsePreview = "OK",
            )
        }
    }

    private class RecordingPendingQuestionProvider : PendingQuestionProvider {
        private val _state = MutableStateFlow(UiPendingQuestionState())
        override val state: StateFlow<UiPendingQuestionState> = _state

        override suspend fun prepare(
            label: String,
            question: String,
            spoilerLevelOverride: UiSpoilerLevel?,
        ) {
            val cleanQuestion = question.trim()
            _state.value = UiPendingQuestionState(
                pending = if (cleanQuestion.isBlank()) {
                    null
                } else {
                    UiPendingQuestion(
                        label = label.trim().ifBlank { "2048__" },
                        question = cleanQuestion,
                        spoilerLevel = spoilerLevelOverride ?: UiSpoilerLevel.Light,
                        createdAtMillis = System.currentTimeMillis(),
                    )
                }
            )
        }

        override suspend fun clear() {
            _state.value = UiPendingQuestionState()
        }
    }

    private class TimeoutPlayerQuestionProvider(
        private val requestLog: RecordingRequestLogProvider,
    ) : PlayerQuestionProvider {
        override suspend fun ask(label: String, question: String): UiQuestionResult {
            val cleanLabel = label.trim().ifBlank { "2048__" }
            requestLog.prepend(
                UiRequestLogItem(
                    id = "app-timeout-question",
                    timestampMillis = System.currentTimeMillis(),
                    label = cleanLabel,
                    imageBytes = 0,
                    paused = true,
                    outputMode = UiOutputMode.Text,
                    rawOutputMode = "app:text",
                    responsePreview = TIMEOUT_ANSWER,
                    fullResponseJson = """{
  "text": "$TIMEOUT_ANSWER",
  "output_mode": "app:text",
  "source_ids": ["sample.2048.rules"],
  "llm_status": "failed",
  "llm_provider": "deepseek",
  "llm_model": "deepseek-v4-pro",
  "llm_error": "timeout while waiting for provider"
}""",
                    durationMillis = 5_012L,
                    ok = true,
                    sourceIds = listOf(SOURCE_ID),
                    pipelineStage = "evidence",
                    llmStatus = "failed",
                    llmProvider = "deepseek",
                    llmModel = "deepseek-v4-pro",
                    llmMaxTokens = 64,
                    llmTimeoutMs = 5_000L,
                    llmError = "timeout while waiting for provider",
                )
            )
            return UiQuestionResult(
                requestLogId = "app-timeout-question",
                label = cleanLabel,
                question = question,
                answer = TIMEOUT_ANSWER,
                ok = true,
                timestampMillis = System.currentTimeMillis(),
                sourceIds = listOf(SOURCE_ID),
                pipelineStage = "evidence",
                llmStatus = "failed",
                durationMillis = 5_012L,
                llmProvider = "deepseek",
                llmModel = "deepseek-v4-pro",
                llmMaxTokens = 64,
                llmTimeoutMs = 5_000L,
                llmError = "timeout while waiting for provider",
            )
        }
    }

    private class RecoveryPlayerQuestionProvider(
        private val requestLog: RecordingRequestLogProvider,
        private val requestId: String,
        private val answer: String,
        private val ok: Boolean,
        private val pipelineStage: String,
        private val llmStatus: String,
        private val errorMessage: String? = null,
    ) : PlayerQuestionProvider {
        override suspend fun ask(label: String, question: String): UiQuestionResult {
            val cleanLabel = label.trim().ifBlank { "2048__" }
            val preview = errorMessage ?: answer
            requestLog.prepend(
                UiRequestLogItem(
                    id = requestId,
                    timestampMillis = System.currentTimeMillis(),
                    label = cleanLabel,
                    imageBytes = 0,
                    paused = true,
                    outputMode = UiOutputMode.Text,
                    rawOutputMode = "app:text",
                    responsePreview = preview,
                    fullResponseJson = """{"text":"$preview","pipeline_stage":"$pipelineStage","llm_status":"$llmStatus"}""",
                    durationMillis = 9L,
                    ok = ok,
                    pipelineStage = pipelineStage,
                    llmStatus = llmStatus,
                )
            )
            return UiQuestionResult(
                requestLogId = requestId,
                label = cleanLabel,
                question = question,
                answer = answer,
                ok = ok,
                timestampMillis = System.currentTimeMillis(),
                pipelineStage = pipelineStage,
                llmStatus = llmStatus,
                durationMillis = 9L,
                errorMessage = errorMessage,
            )
        }
    }

    private class LoggingPlayerQuestionProvider(
        private val requestLog: RecordingRequestLogProvider,
    ) : PlayerQuestionProvider {
        override suspend fun ask(label: String, question: String): UiQuestionResult {
            val cleanLabel = label.trim().ifBlank { "2048__" }
            requestLog.prepend(
                UiRequestLogItem(
                    id = "app-question",
                    timestampMillis = System.currentTimeMillis(),
                    label = cleanLabel,
                    imageBytes = 0,
                    paused = true,
                    outputMode = UiOutputMode.Text,
                    rawOutputMode = "app:text",
                    responsePreview = HOME_ANSWER,
                    fullResponseJson = """{"text":"Merge matching tiles.","output_mode":"app:text","source_ids":["sample.2048.rules"]}""",
                    durationMillis = 8L,
                    ok = true,
                    sourceIds = listOf(SOURCE_ID),
                    pipelineStage = "evidence",
                    llmStatus = "skipped",
                )
            )
            return UiQuestionResult(
                requestLogId = "app-question",
                label = cleanLabel,
                question = question,
                answer = HOME_ANSWER,
                ok = true,
                timestampMillis = System.currentTimeMillis(),
                sourceIds = listOf(SOURCE_ID),
                pipelineStage = "evidence",
                llmStatus = "skipped",
            )
        }
    }

    private class DeletePlanGkpLibraryProvider : GkpLibraryProvider {
        override val state: StateFlow<UiGkpLibraryState> = MutableStateFlow(
            PreviewStub.gkpLibrary().state.value.copy(
                deleteState = UiGkpDeleteState(
                    phase = UiGkpDeletePhase.AwaitingConfirmation,
                    plan = UiGkpDeletePlan(
                        packId = "sample.relay-station",
                        gameId = "relay_station",
                        title = "Relay Station",
                        packVersion = "0.1.0",
                        knowledgeCount = 14,
                        sourceCount = 4,
                        warning = "这是内置样例包，删除后下次启动可能会自动恢复。",
                    ),
                    message = "请确认删除 Relay Station。",
                    updatedAtMillis = System.currentTimeMillis(),
                )
            )
        )

        override suspend fun disablePack(gameId: String) = Unit
        override suspend fun enablePack(gameId: String) = Unit
        override suspend fun requestDelete(gameId: String) = Unit
        override suspend fun confirmDelete() = Unit
        override suspend fun cancelDelete() = Unit
    }

    private class MutableVoiceInputProvider(
        initialState: UiVoiceInputState,
    ) : VoiceInputProvider {
        private val mutableState = MutableStateFlow(initialState)
        override val state: StateFlow<UiVoiceInputState> = mutableState
        override val requiresRecordAudioPermission: Boolean = false
        fun setState(nextState: UiVoiceInputState) {
            mutableState.value = nextState
        }
        override suspend fun startListening(context: AsrRecognitionContext?) = Unit
        override suspend fun stopListening() = Unit
        override suspend fun cancelListening() = Unit
    }

    private companion object {
        const val HOME_QUESTION = "How do I merge two 2 tiles?"
        const val HOME_ANSWER = "Swipe matching tiles together to merge them into the next value."
        const val TIMEOUT_ANSWER = "LLM 调用失败：timeout while waiting for provider。已保留本地证据，请稍后重试或检查模型配置。"
        const val DISABLED_GKP_ANSWER = "知识包已禁用：我找到了当前游戏的本地 GKP，但它不会参与检索或调用 LLM。"
        const val ERROR_ANSWER = "请求处理失败：malformed request body"
        const val SOURCE_ID = "sample.2048.rules"
    }
}
