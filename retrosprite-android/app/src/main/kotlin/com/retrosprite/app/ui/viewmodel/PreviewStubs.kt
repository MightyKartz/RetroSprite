package com.retrosprite.app.ui.viewmodel

import com.retrosprite.app.voice.asr.AsrRecognitionContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * In-memory implementations of the UI fa\u00e7ade. Used by:
 *   - @Preview composables (so designs render in Android Studio).
 *   - The default Application wiring while Tasks #2/#4 land.
 *   - androidTest smoke tests.
 *
 * Behaviour is intentionally chatty: status flips, fake requests appear so a
 * developer running a debug build sees a "live looking" diagnostics list.
 */

object PreviewStub {

    fun endpoint(
        initial: UiEndpointStatus = UiEndpointStatus(
            phase = UiEndpointPhase.Running,
            port = 4_404,
            baseUrl = "http://localhost:4404",
            message = "0 \u4e2a\u8bf7\u6c42\u5728\u6392\u961f",
            lastHealthCheckMillis = System.currentTimeMillis() - 12_000,
            lastHealthOk = true
        )
    ): EndpointStatusProvider = FakeEndpointStatusProvider(initial)

    fun requestLog(items: List<UiRequestLogItem> = sampleLog()): RequestLogProvider =
        FakeRequestLogProvider(items)

    fun settings(initial: UiSettings = UiSettings()): SettingsStore =
        FakeSettingsStore(initial)

    fun playerQuestion(): PlayerQuestionProvider = FakePlayerQuestionProvider()

    fun pendingQuestion(): PendingQuestionProvider = FakePendingQuestionProvider()

    fun voiceInput(): VoiceInputProvider = FakeVoiceInputProvider()

    fun speechOutput(): SpeechOutputProvider = FakeSpeechOutputProvider()

    fun overlayPermission(
        initial: UiOverlayPermissionState = UiOverlayPermissionState(isGranted = true),
    ): OverlayPermissionProvider = FakeOverlayPermissionProvider(initial)

    fun llmConfigTest(): LlmConfigTestProvider = FakeLlmConfigTestProvider()

    fun gkpLibrary(): GkpLibraryProvider = FakeGkpLibraryProvider()

    fun gkpPreflight(): GkpPreflightProvider = FakeGkpPreflightProvider()

    fun sampleLog(): List<UiRequestLogItem> {
        val now = System.currentTimeMillis()
        return listOf(
            UiRequestLogItem(
                id = "r-1",
                timestampMillis = now - 4_000,
                label = "Chrono Trigger",
                imageBytes = 87_412,
                paused = true,
                outputMode = UiOutputMode.Text,
                responsePreview = "\u793a\u4f8b\u56de\u7b54\uff1a\u8fd9\u91cc\u4f1a\u51fa\u73b0\u4e00\u6bb5\u4e0e\u5f53\u524d\u573a\u666f\u76f8\u5173\u7684\u8f7b\u63d0\u793a\u3002",
                fullResponseJson = """{
  "text": "示例回答：这里会出现一段与当前场景相关的轻提示。",
  "debug": true,
  "pipeline_stage": "evidence",
  "llm_status": "skipped",
  "source_ids": ["sf2.promotion"]
}""",
                durationMillis = 1_842,
                ok = true,
                isDebug = true,
                rawOutputMode = "debug:text",
                sourceIds = listOf("sf2.promotion"),
                pipelineStage = "evidence",
                llmStatus = "skipped",
                feedback = UiAnswerFeedback.Helpful,
                feedbackTimestampMillis = now - 2_000,
            ),
            UiRequestLogItem(
                id = "r-2",
                timestampMillis = now - 32_000,
                label = "Final Fantasy VI",
                imageBytes = 102_004,
                paused = false,
                outputMode = UiOutputMode.Text,
                responsePreview = "\u793a\u4f8b\u56de\u7b54\uff1a\u771f\u5b9e\u73af\u5883\u4e0b\u4f1a\u6839\u636e\u77e5\u8bc6\u5e93\u751f\u6210\u4f4e\u5267\u900f\u63d0\u793a\u3002",
                fullResponseJson = """{"text":"示例回答：真实环境下会根据知识库生成低剧透提示。"}""",
                durationMillis = 1_244,
                ok = true
            ),
            UiRequestLogItem(
                id = "r-3",
                timestampMillis = now - 5 * 60_000,
                label = null,
                imageBytes = 64_220,
                paused = true,
                outputMode = UiOutputMode.Text,
                responsePreview = "\u8bf7\u6c42\u8d85\u65f6\uff1a\u8fde\u63a5 OpenAI \u5931\u8d25 (timeout 30s)",
                fullResponseJson = """{"error":"timeout"}""",
                durationMillis = 30_000,
                ok = false
            )
        )
    }
}

private class FakeGkpPreflightProvider : GkpPreflightProvider {
    private val _state = MutableStateFlow(
        UiGkpPreflightState(
            result = UiGkpPreflightResult(
                targetName = "golden-sun-gba-zh",
                ok = true,
                packId = "community.golden-sun-gba-zh",
                gameId = "golden_sun_gba",
                gameTitle = "Golden Sun / 黄金太阳",
                packVersion = "0.1.0",
                coverageTierLabel = "GKP Lite",
                schemaVersion = "gkp.v0",
                knowledgeRows = 41,
                sourceCount = 6,
                goldenRows = 33,
                licenseStatus = "已声明",
                signatureStatus = "未签名",
                signatureKeyId = null,
                contentDigest = "6f2c9db1f2f6e3a046a7417c62c1ecdd0f084df4549765e822641927ad6c67dd",
                errorCount = 0,
                warningCount = 0,
                checkedAtMillis = System.currentTimeMillis() - 2_000,
                issues = listOf(
                    UiGkpPreflightIssue(
                        severity = UiGkpPreflightSeverity.Info,
                        code = "readonly",
                        path = null,
                        message = "预检只读完成，未安装或覆盖任何知识包。",
                    )
                ),
            ),
            installPlan = UiGkpInstallPlan(
                mode = UiGkpInstallMode.ReplaceExisting,
                packId = "community.golden-sun-gba-zh",
                gameId = "golden_sun_gba",
                gameTitle = "Golden Sun / 黄金太阳",
                currentPackVersion = "0.1.0",
                newPackVersion = "0.1.0",
                coverageTierLabel = "GKP Lite",
                currentKnowledgeRows = 41,
                newKnowledgeRows = 41,
                sourceCount = 6,
                goldenRows = 33,
                provenanceLabel = "外部",
                signatureLabel = "未签名",
                contentDigest = "6f2c9db1f2f6e3a046a7417c62c1ecdd0f084df4549765e822641927ad6c67dd",
            )
        )
    )
    override val state: StateFlow<UiGkpPreflightState> = _state

    override suspend fun preflightTree(uriString: String) {
        delay(250)
        _state.value = _state.value.copy(
            isRunning = false,
            result = _state.value.result?.copy(
                targetName = uriString.substringAfterLast('/').ifBlank { "外部 GKP" },
                checkedAtMillis = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun installPreflightedTree() {
        delay(250)
        _state.value = _state.value.copy(
            installStatus = UiGkpInstallStatus(
                phase = UiGkpInstallPhase.Installed,
                message = "已安装 Golden Sun / 黄金太阳，写入 41 条知识。",
                installedAtMillis = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun clearPreflight() {
        _state.value = UiGkpPreflightState()
    }
}

private class FakeGkpLibraryProvider : GkpLibraryProvider {
    private val _state = MutableStateFlow(
        UiGkpLibraryState(
            importStatus = UiGkpImportStatus(
                phase = UiGkpImportPhase.Ready,
                totalPacks = 2,
                importedPacks = 2,
                failedPacks = 0,
                message = "已导入 2 个内置真实知识包",
                updatedAtMillis = System.currentTimeMillis() - 1_500,
            ),
            packs = listOf(
                UiGkpPackItem(
                    packId = "community.shining-force-ii-md",
                    gameId = "shining_force_ii_md",
                    title = "Shining Force II / 光明力量2",
                    platform = "md",
                    region = null,
                    languages = listOf("zh", "en"),
                    packVersion = "0.3.0",
                    coverageTierLabel = "GKP Expanded",
                    schemaVersion = "gkp.v0",
                    trustLabel = "社区",
                    provenanceLabel = "内置",
                    signatureLabel = "未签名",
                    contentDigest = "d38f26a3fddf19bff1ac311808f0d3c4f66d2c78aa1272327601c7cf2d63e96f",
                    isEnabled = true,
                    availabilityLabel = "启用",
                    disabledAtMillis = null,
                    knowledgeCount = 160,
                    sourceCount = 16,
                    licenseSummary = "已声明 / 链接来源",
                    installedAtMillis = System.currentTimeMillis() - 86_000,
                ),
                UiGkpPackItem(
                    packId = "community.golden-sun-gba-zh",
                    gameId = "golden_sun_gba",
                    title = "Golden Sun / 黄金太阳",
                    platform = "gba",
                    region = null,
                    languages = listOf("zh"),
                    packVersion = "0.1.0",
                    coverageTierLabel = "GKP Lite",
                    schemaVersion = "gkp.v0",
                    trustLabel = "社区",
                    provenanceLabel = "内置",
                    signatureLabel = "未签名",
                    contentDigest = "6f2c9db1f2f6e3a046a7417c62c1ecdd0f084df4549765e822641927ad6c67dd",
                    isEnabled = true,
                    availabilityLabel = "启用",
                    disabledAtMillis = null,
                    knowledgeCount = 41,
                    sourceCount = 6,
                    licenseSummary = "已声明 / 链接来源",
                    installedAtMillis = System.currentTimeMillis() - 42_000,
                ),
            ),
        )
    )
    override val state: StateFlow<UiGkpLibraryState> = _state

    override suspend fun disablePack(gameId: String) {
        _state.update { current ->
            current.copy(
                packs = current.packs.map { pack ->
                    if (pack.gameId == gameId) {
                        pack.copy(
                            isEnabled = false,
                            availabilityLabel = "已禁用",
                            disabledAtMillis = System.currentTimeMillis(),
                        )
                    } else {
                        pack
                    }
                }
            )
        }
    }

    override suspend fun enablePack(gameId: String) {
        _state.update { current ->
            current.copy(
                packs = current.packs.map { pack ->
                    if (pack.gameId == gameId) {
                        pack.copy(
                            isEnabled = true,
                            availabilityLabel = "启用",
                            disabledAtMillis = null,
                        )
                    } else {
                        pack
                    }
                }
            )
        }
    }

    override suspend fun requestDelete(gameId: String) {
        val pack = _state.value.packs.firstOrNull { it.gameId == gameId }
        if (pack == null) {
            _state.update {
                it.copy(
                    deleteState = UiGkpDeleteState(
                        phase = UiGkpDeletePhase.Error,
                        message = "未找到要删除的知识包：$gameId",
                        updatedAtMillis = System.currentTimeMillis(),
                    )
                )
            }
            return
        }
        _state.update {
            it.copy(
                deleteState = UiGkpDeleteState(
                    phase = UiGkpDeletePhase.AwaitingConfirmation,
                    plan = UiGkpDeletePlan(
                        packId = pack.packId,
                        gameId = pack.gameId,
                        title = pack.title,
                        packVersion = pack.packVersion,
                        knowledgeCount = pack.knowledgeCount,
                        sourceCount = pack.sourceCount,
                        warning = if (pack.provenanceLabel == "内置") {
                            "这是内置知识包，删除后下次启动可能会自动恢复。"
                        } else {
                            null
                        },
                    ),
                    message = "请确认删除 ${pack.title}。",
                    updatedAtMillis = System.currentTimeMillis(),
                )
            )
        }
    }

    override suspend fun confirmDelete() {
        val plan = _state.value.deleteState.plan
        if (plan == null) {
            _state.update {
                it.copy(
                    deleteState = UiGkpDeleteState(
                        phase = UiGkpDeletePhase.Error,
                        message = "请先选择要删除的知识包。",
                        updatedAtMillis = System.currentTimeMillis(),
                    )
                )
            }
            return
        }
        _state.update { current ->
            current.copy(
                packs = current.packs.filterNot { it.gameId == plan.gameId },
                deleteState = UiGkpDeleteState(
                    phase = UiGkpDeletePhase.Deleted,
                    plan = plan,
                    message = "已删除 ${plan.title}，移除 ${plan.knowledgeCount} 条知识。",
                    updatedAtMillis = System.currentTimeMillis(),
                )
            )
        }
    }

    override suspend fun cancelDelete() {
        _state.update { it.copy(deleteState = UiGkpDeleteState()) }
    }
}

private class FakeLlmConfigTestProvider : LlmConfigTestProvider {
    override suspend fun test(settings: UiSettings): UiLlmConfigTestResult {
        delay(300)
        return UiLlmConfigTestResult(
            ok = settings.llmApiKey.isNotBlank(),
            provider = settings.llmProvider.id,
            model = settings.llmModel.ifBlank { settings.llmProvider.defaultModel },
            maxTokens = settings.llmMaxTokens.coerceIn(MIN_LLM_MAX_TOKENS, MAX_LLM_MAX_TOKENS),
            timeoutMs = settings.llmTimeoutSeconds
                .coerceIn(MIN_LLM_TIMEOUT_SECONDS, MAX_LLM_TIMEOUT_SECONDS)
                .times(1_000L),
            latencyMs = if (settings.llmApiKey.isNotBlank()) 42L else 0L,
            tokensIn = if (settings.llmApiKey.isNotBlank()) 9 else 0,
            tokensOut = if (settings.llmApiKey.isNotBlank()) 1 else 0,
            responsePreview = if (settings.llmApiKey.isNotBlank()) "OK" else null,
            errorMessage = if (settings.llmApiKey.isBlank()) "请先填写问答 API Key" else null,
        )
    }
}

private class FakePlayerQuestionProvider : PlayerQuestionProvider {
    override suspend fun ask(label: String, question: String): UiQuestionResult {
        delay(350)
        val cleanLabel = label.trim().ifBlank { "md__Shining Force II" }
        val cleanQuestion = question.trim()
        return UiQuestionResult(
            requestLogId = "preview-question",
            label = cleanLabel,
            question = cleanQuestion,
            answer = "角色至少 20 级才能转职。第一周目可以 20 级左右转，想练更高属性再考虑晚转。",
            ok = true,
            timestampMillis = System.currentTimeMillis(),
            sourceIds = listOf("sf2.promotion"),
            pipelineStage = "evidence",
            llmStatus = "skipped",
        )
    }
}

private class FakePendingQuestionProvider : PendingQuestionProvider {
    private val _state = MutableStateFlow(UiPendingQuestionState())
    override val state: StateFlow<UiPendingQuestionState> = _state.asStateFlow()

    override suspend fun prepare(
        label: String,
        question: String,
        spoilerLevelOverride: UiSpoilerLevel?,
    ) {
        val cleanQuestion = question.trim()
        if (cleanQuestion.isBlank()) {
            _state.value = UiPendingQuestionState()
            return
        }
        _state.value = UiPendingQuestionState(
            pending = UiPendingQuestion(
                label = label.trim().ifBlank { "md__Shining Force II" },
                question = cleanQuestion,
                spoilerLevel = spoilerLevelOverride ?: UiSpoilerLevel.Light,
                createdAtMillis = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun clear() {
        _state.value = UiPendingQuestionState()
    }
}

private class FakeVoiceInputProvider : VoiceInputProvider {
    private val _state = MutableStateFlow(
        UiVoiceInputState(engineLabel = "预览语音", isAvailable = true)
    )
    override val state: StateFlow<UiVoiceInputState> = _state.asStateFlow()
    override val requiresRecordAudioPermission: Boolean = false
    private var eventId: Long = 0L

    override suspend fun startListening(context: AsrRecognitionContext?) {
        _state.update {
            it.copy(
                isListening = true,
                errorMessage = null,
            )
        }
        delay(120)
        eventId += 1
        _state.value = UiVoiceInputState(
            isAvailable = true,
            isListening = false,
            transcript = "什么时候转职？",
            transcriptEventId = eventId,
            engineLabel = "预览语音",
        )
    }

    override suspend fun stopListening() {
        _state.update { it.copy(isListening = false) }
    }

    override suspend fun cancelListening() {
        _state.update { it.copy(isListening = false, errorMessage = null) }
    }
}

private class FakeSpeechOutputProvider : SpeechOutputProvider {
    private val _state = MutableStateFlow(
        UiSpeechOutputState(isAvailable = true, isReady = true)
    )
    override val state: StateFlow<UiSpeechOutputState> = _state.asStateFlow()

    override suspend fun speak(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        _state.value = UiSpeechOutputState(
            isAvailable = true,
            isReady = true,
            isSpeaking = true,
            spokenText = clean,
        )
        delay(120)
        _state.update { it.copy(isSpeaking = false) }
    }

    override suspend fun stop() {
        _state.update { it.copy(isSpeaking = false) }
    }
}

private class FakeOverlayPermissionProvider(
    initial: UiOverlayPermissionState,
) : OverlayPermissionProvider {
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<UiOverlayPermissionState> = _state.asStateFlow()

    override suspend fun refresh() {
        _state.update {
            it.copy(
                message = if (it.isGranted) {
                    "已允许游戏内语音 overlay。"
                } else {
                    "需要授权后才能在 RetroArch 上方显示语音波形。"
                },
            )
        }
    }

    override suspend fun openSettings() {
        _state.update { it.copy(message = "已打开系统授权页。") }
    }
}

private class FakeEndpointStatusProvider(initial: UiEndpointStatus) : EndpointStatusProvider {
    private val _status = MutableStateFlow(initial)
    override val status: StateFlow<UiEndpointStatus> = _status.asStateFlow()

    override suspend fun restart() {
        _status.update { it.copy(phase = UiEndpointPhase.Starting, message = "\u91cd\u542f\u4e2d\u2026") }
        delay(700)
        _status.update {
            it.copy(
                phase = UiEndpointPhase.Running,
                message = "\u5df2\u91cd\u542f",
                lastHealthCheckMillis = System.currentTimeMillis(),
                lastHealthOk = true
            )
        }
    }

    override suspend fun checkHealth() {
        delay(300)
        _status.update {
            it.copy(
                lastHealthCheckMillis = System.currentTimeMillis(),
                lastHealthOk = it.phase == UiEndpointPhase.Running
            )
        }
    }
}

private class FakeRequestLogProvider(seed: List<UiRequestLogItem>) : RequestLogProvider {
    private val _log = MutableStateFlow(seed)
    override val log: Flow<List<UiRequestLogItem>> = _log

    override suspend fun clear() {
        _log.value = emptyList()
    }

    override suspend fun sendConnectionTest() {
        delay(450)
        val item = UiRequestLogItem(
            id = UUID.randomUUID().toString(),
            timestampMillis = System.currentTimeMillis(),
            label = "[\u8fde\u63a5\u6d4b\u8bd5]",
            imageBytes = 1_024,
            paused = true,
            outputMode = UiOutputMode.Text,
            responsePreview = "\u8fde\u63a5\u6210\u529f\uff0c\u5b8c\u6574\u94fe\u8def\u5df2\u9a8c\u8bc1\u3002",
            fullResponseJson = """{"text":"连接成功，完整链路已验证。"}""",
            durationMillis = 412,
            ok = true
        )
        _log.update { listOf(item) + it }
    }

    override suspend fun submitFeedback(requestId: String, feedback: UiAnswerFeedback) {
        val now = System.currentTimeMillis()
        _log.update { rows ->
            rows.map { row ->
                if (row.id == requestId) {
                    row.copy(feedback = feedback, feedbackTimestampMillis = now)
                } else {
                    row
                }
            }
        }
    }
}

private class FakeSettingsStore(initial: UiSettings) : SettingsStore {
    private val _settings = MutableStateFlow(initial)
    override val settings: Flow<UiSettings> = _settings

    override suspend fun updatePort(port: Int) {
        _settings.update { it.copy(port = port.coerceIn(1024, 65_535)) }
    }

    override suspend fun updateLlmConfig(
        provider: UiLlmProvider,
        apiKey: String,
        baseUrl: String,
        model: String,
        timeoutSeconds: Int,
        maxTokens: Int,
    ) {
        _settings.update {
            it.copy(
                llmProvider = provider,
                llmApiKey = apiKey,
                llmBaseUrl = baseUrl.trim().ifBlank { provider.defaultBaseUrl },
                llmModel = model.trim().ifBlank { provider.defaultModel },
                llmTimeoutSeconds = timeoutSeconds.coerceIn(
                    MIN_LLM_TIMEOUT_SECONDS,
                    MAX_LLM_TIMEOUT_SECONDS,
                ),
                llmMaxTokens = maxTokens.coerceIn(
                    MIN_LLM_MAX_TOKENS,
                    MAX_LLM_MAX_TOKENS,
                ),
            )
        }
    }

    override suspend fun updateSpoilerLevel(level: UiSpoilerLevel) {
        _settings.update { it.copy(spoilerLevel = level) }
    }

    override suspend fun updateHotkeyVoiceTranscriptHudEnabled(enabled: Boolean) {
        _settings.update { it.copy(hotkeyVoiceTranscriptHudEnabled = enabled) }
    }

    override suspend fun updateScreenTranslationApiConfig(
        provider: UiScreenTranslationApiProvider,
        baseUrl: String,
        apiKey: String,
        model: String,
        timeoutSeconds: Int,
    ) {
        _settings.update {
            it.copy(
                screenTranslationApiProvider = provider,
                screenTranslationBaseUrl = baseUrl.trim().ifBlank { provider.defaultBaseUrl },
                screenTranslationApiKey = apiKey.trim(),
                screenTranslationModel = model.trim().ifBlank { provider.defaultModel },
                screenTranslationTimeoutSeconds = timeoutSeconds.coerceIn(
                    MIN_SCREEN_TRANSLATION_TIMEOUT_SECONDS,
                    MAX_SCREEN_TRANSLATION_TIMEOUT_SECONDS,
                ),
            )
        }
    }
}
