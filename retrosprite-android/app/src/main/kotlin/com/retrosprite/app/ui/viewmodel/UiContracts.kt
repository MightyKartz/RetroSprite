package com.retrosprite.app.ui.viewmodel

import com.retrosprite.app.llm.LlmConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

// ---------------------------------------------------------------------------
// UI fa\u00e7ade contracts
// ---------------------------------------------------------------------------
// Why these exist:
//   Task #2 (EndpointController, RequestLogger) and Task #4 (RequestLogRepository,
//   DataStore) are running in parallel with this UI task. We can't depend on
//   their concrete types yet, so the UI consumes thin "providers" defined here.
//   When the real services land, an Application-level wiring layer maps them
//   onto these interfaces and passes them through ProvideUiDependencies(...).
//
// Keep these interfaces *UI-shaped* (already-formatted strings, tab counters,
// no domain leaks). Mapping is the integration layer's job, not the UI's.
// ---------------------------------------------------------------------------

/** Lifecycle phase of the local HTTP endpoint, as the UI wants to show it. */
enum class UiEndpointPhase { Stopped, Starting, Running, Error }

/** Snapshot of endpoint status for the Home/Diagnostics surfaces. */
data class UiEndpointStatus(
    val phase: UiEndpointPhase,
    val port: Int,
    val baseUrl: String,           // e.g. "http://192.168.1.42:4404" or "http://localhost:4404"
    val healthPath: String = "/health",
    val message: String? = null,   // human-readable note, esp. for Error
    val lastHealthCheckMillis: Long? = null,
    val lastHealthOk: Boolean? = null
)

/** Mode bit decoded from RetroArch's `output` flags ("text", "image", "sound"). */
enum class UiOutputMode { Text, Image, Sound, Mixed }

/** Local-only feedback attached to a request log row. */
enum class UiAnswerFeedback(
    val id: String,
    val displayName: String,
    val diagnosticsTag: String,
) {
    Helpful("helpful", "有帮助", "HELPFUL"),
    Incorrect("incorrect", "这不对", "INCORRECT")
}

/** A single request in the diagnostics log list. */
data class UiRequestLogItem(
    val id: String,
    val timestampMillis: Long,
    val label: String?,            // RetroArch `label` field (often game name)
    val imageBytes: Int,           // raw screenshot size before base64
    val paused: Boolean,
    val outputMode: UiOutputMode,
    val question: String? = null,
    val questionSource: String? = null,
    val responsePreview: String,   // first ~80 chars of LLM output
    val responseText: String = responsePreview, // full text, used when restoring persisted conversations
    val fullResponseJson: String,  // pretty-printed JSON for the detail dialog
    val durationMillis: Long,
    val ok: Boolean,
    val isDebug: Boolean = false,
    val sourceIds: List<String> = emptyList(),
    val pipelineStage: String = "unknown",
    val llmStatus: String = "skipped",
    val rawOutputMode: String = "text",
    val llmProvider: String? = null,
    val llmModel: String? = null,
    val llmMaxTokens: Int? = null,
    val llmTimeoutMs: Long? = null,
    val llmLatencyMs: Long? = null,
    val llmTokensIn: Int = 0,
    val llmTokensOut: Int = 0,
    val llmError: String? = null,
    val feedback: UiAnswerFeedback? = null,
    val feedbackTimestampMillis: Long? = null,
)

/** Result returned after an in-app player question goes through the Q&A pipeline. */
data class UiQuestionResult(
    val requestLogId: String? = null,
    val label: String,
    val question: String,
    val answer: String,
    val ok: Boolean,
    val timestampMillis: Long,
    val sourceIds: List<String> = emptyList(),
    val pipelineStage: String = "unknown",
    val llmStatus: String = "skipped",
    val durationMillis: Long = 0L,
    val llmProvider: String? = null,
    val llmModel: String? = null,
    val llmMaxTokens: Int? = null,
    val llmTimeoutMs: Long? = null,
    val llmLatencyMs: Long? = null,
    val llmTokensIn: Int = 0,
    val llmTokensOut: Int = 0,
    val llmError: String? = null,
    val feedback: UiAnswerFeedback? = null,
    val errorMessage: String? = null,
)

/** A player-authored question waiting for the next matching RetroArch hotkey request. */
data class UiPendingQuestion(
    val label: String,
    val question: String,
    val spoilerLevel: UiSpoilerLevel,
    val createdAtMillis: Long,
)

data class UiPendingQuestionState(
    val pending: UiPendingQuestion? = null,
)

/** One-shot app-side speech recognition state. */
data class UiVoiceInputState(
    val isAvailable: Boolean = true,
    val isListening: Boolean = false,
    val transcript: String? = null,
    val transcriptEventId: Long = 0L,
    val amplitude: Float = 0f,
    val engineLabel: String = "系统语音",
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)

/** App-side short-answer speech output state. */
data class UiSpeechOutputState(
    val isAvailable: Boolean = true,
    val isReady: Boolean = false,
    val isSpeaking: Boolean = false,
    val spokenText: String? = null,
    val errorMessage: String? = null,
)

/** Android "display over other apps" permission used by the hotkey voice overlay. */
data class UiOverlayPermissionState(
    val isGranted: Boolean = false,
    val message: String = "需要授权后才能在 RetroArch 上方显示语音波形。",
)

/** Result of a Settings-only LLM smoke test. Never contains API keys or prompts. */
data class UiLlmConfigTestResult(
    val ok: Boolean,
    val provider: String,
    val model: String,
    val maxTokens: Int,
    val timeoutMs: Long,
    val latencyMs: Long = 0L,
    val tokensIn: Int = 0,
    val tokensOut: Int = 0,
    val responsePreview: String? = null,
    val errorMessage: String? = null,
)

/** Provider configurations the UI knows how to render. */
enum class UiLlmProvider(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
) {
    OpenAI("openai", "OpenAI", LlmConfig.OPENAI_BASE_URL, LlmConfig.OPENAI_DEFAULT_MODEL),
    DeepSeek("deepseek", "DeepSeek", LlmConfig.DEEPSEEK_BASE_URL, LlmConfig.DEEPSEEK_DEFAULT_MODEL),
    Custom("custom", "\u81ea\u5b9a\u4e49 (Custom)", "", "")
}

/** Spoiler intensity. Strings are persisted; enum is for UI binding. */
enum class UiSpoilerLevel(val id: String, val displayName: String) {
    Light("light", "\u8f7b\u63d0\u793a"),
    Clear("clear", "\u66f4\u660e\u786e"),
    Direct("direct", "\u76f4\u63a5\u7b54\u6848")
}

const val DEFAULT_LLM_TIMEOUT_SECONDS: Int = 30
const val MIN_LLM_TIMEOUT_SECONDS: Int = 5
const val MAX_LLM_TIMEOUT_SECONDS: Int = 120
const val DEFAULT_LLM_MAX_TOKENS: Int = 256
const val MIN_LLM_MAX_TOKENS: Int = 32
const val MAX_LLM_MAX_TOKENS: Int = 2048

/** A flat snapshot of all user-tunable settings. */
data class UiSettings(
    val port: Int = 4_404,
    val llmProvider: UiLlmProvider = UiLlmProvider.OpenAI,
    val llmApiKey: String = "",
    val llmBaseUrl: String = UiLlmProvider.OpenAI.defaultBaseUrl,
    val llmModel: String = UiLlmProvider.OpenAI.defaultModel,
    val llmTimeoutSeconds: Int = DEFAULT_LLM_TIMEOUT_SECONDS,
    val llmMaxTokens: Int = DEFAULT_LLM_MAX_TOKENS,
    val spoilerLevel: UiSpoilerLevel = UiSpoilerLevel.Light
)

/** Header info for the "About" section. Static for Phase 0. */
data class UiAboutInfo(
    val appVersion: String = "0.1.0",
    val gkpSchemaVersion: String = "0.1",
    val buildFlavor: String = "phase0-preview"
)

/** Import lifecycle for the bundled/local GKP library. */
enum class UiGkpImportPhase { Idle, Importing, Ready, Error }

/** Last known import result shown by Packs and Diagnostics-style surfaces. */
data class UiGkpImportStatus(
    val phase: UiGkpImportPhase = UiGkpImportPhase.Idle,
    val totalPacks: Int = 0,
    val importedPacks: Int = 0,
    val failedPacks: Int = 0,
    val message: String = "等待导入",
    val updatedAtMillis: Long? = null,
)

/** One installed Game Knowledge Pack row as the Packs screen renders it. */
data class UiGkpPackItem(
    val packId: String,
    val gameId: String,
    val title: String,
    val platform: String,
    val region: String?,
    val languages: List<String>,
    val packVersion: String,
    val schemaVersion: String,
    val trustLabel: String,
    val provenanceLabel: String,
    val signatureLabel: String,
    val contentDigest: String?,
    val isEnabled: Boolean,
    val availabilityLabel: String,
    val disabledAtMillis: Long?,
    val knowledgeCount: Int,
    val sourceCount: Int,
    val licenseSummary: String,
    val installedAtMillis: Long,
)

data class UiGkpDeletePlan(
    val packId: String,
    val gameId: String,
    val title: String,
    val packVersion: String,
    val knowledgeCount: Int,
    val sourceCount: Int,
    val warning: String?,
)

enum class UiGkpDeletePhase { Idle, AwaitingConfirmation, Deleting, Deleted, Error }

data class UiGkpDeleteState(
    val phase: UiGkpDeletePhase = UiGkpDeletePhase.Idle,
    val plan: UiGkpDeletePlan? = null,
    val message: String = "未选择知识包",
    val updatedAtMillis: Long? = null,
) {
    val isRunning: Boolean get() = phase == UiGkpDeletePhase.Deleting
}

/** Combined Packs screen state. */
data class UiGkpLibraryState(
    val importStatus: UiGkpImportStatus = UiGkpImportStatus(),
    val packs: List<UiGkpPackItem> = emptyList(),
    val deleteState: UiGkpDeleteState = UiGkpDeleteState(),
) {
    val totalKnowledgeRows: Int get() = packs.sumOf { it.knowledgeCount }
    val totalSources: Int get() = packs.sumOf { it.sourceCount }
    val enabledPackCount: Int get() = packs.count { it.isEnabled }
    val disabledPackCount: Int get() = packs.count { !it.isEnabled }
}

enum class UiGkpPreflightSeverity { Info, Warning, Error }

data class UiGkpPreflightIssue(
    val severity: UiGkpPreflightSeverity,
    val code: String,
    val path: String?,
    val message: String,
)

data class UiGkpPreflightResult(
    val targetName: String,
    val ok: Boolean,
    val packId: String?,
    val gameId: String?,
    val gameTitle: String?,
    val packVersion: String?,
    val schemaVersion: String?,
    val knowledgeRows: Int,
    val sourceCount: Int,
    val goldenRows: Int,
    val licenseStatus: String,
    val signatureStatus: String,
    val signatureKeyId: String?,
    val contentDigest: String?,
    val errorCount: Int,
    val warningCount: Int,
    val checkedAtMillis: Long,
    val issues: List<UiGkpPreflightIssue>,
)

enum class UiGkpInstallMode { NewInstall, ReplaceExisting }

data class UiGkpInstallPlan(
    val mode: UiGkpInstallMode,
    val packId: String?,
    val gameId: String,
    val gameTitle: String?,
    val currentPackVersion: String?,
    val newPackVersion: String?,
    val currentKnowledgeRows: Int,
    val newKnowledgeRows: Int,
    val sourceCount: Int,
    val goldenRows: Int,
    val provenanceLabel: String,
    val signatureLabel: String,
    val contentDigest: String?,
) {
    val knowledgeDelta: Int get() = newKnowledgeRows - currentKnowledgeRows
}

enum class UiGkpInstallPhase { Idle, Installing, Installed, Error }

data class UiGkpInstallStatus(
    val phase: UiGkpInstallPhase = UiGkpInstallPhase.Idle,
    val message: String = "等待确认",
    val installedAtMillis: Long? = null,
) {
    val isRunning: Boolean get() = phase == UiGkpInstallPhase.Installing
}

data class UiGkpPreflightState(
    val isRunning: Boolean = false,
    val result: UiGkpPreflightResult? = null,
    val installPlan: UiGkpInstallPlan? = null,
    val installStatus: UiGkpInstallStatus = UiGkpInstallStatus(),
)

// ---------------------------------------------------------------------------
// Provider interfaces consumed by ViewModels
// ---------------------------------------------------------------------------

interface EndpointStatusProvider {
    val status: StateFlow<UiEndpointStatus>
    suspend fun restart()
    suspend fun checkHealth()
}

interface RequestLogProvider {
    val log: Flow<List<UiRequestLogItem>>
    suspend fun clear()
    /** Sends a synthetic request through the loop to verify wiring. */
    suspend fun sendConnectionTest()
    /** Stores local-only answer feedback against a request log id. */
    suspend fun submitFeedback(requestId: String, feedback: UiAnswerFeedback)
}

interface PlayerQuestionProvider {
    suspend fun ask(label: String, question: String): UiQuestionResult
    suspend fun ask(
        label: String,
        question: String,
        spoilerLevelOverride: UiSpoilerLevel?,
    ): UiQuestionResult = ask(label, question)
}

interface PendingQuestionProvider {
    val state: StateFlow<UiPendingQuestionState>
    suspend fun prepare(
        label: String,
        question: String,
        spoilerLevelOverride: UiSpoilerLevel?,
    )
    suspend fun clear()
}

interface VoiceInputProvider {
    val state: StateFlow<UiVoiceInputState>
    val requiresRecordAudioPermission: Boolean get() = true
    suspend fun startListening()
    suspend fun stopListening()
    suspend fun cancelListening()
}

interface SpeechOutputProvider {
    val state: StateFlow<UiSpeechOutputState>
    suspend fun speak(text: String)
    suspend fun stop()
}

interface OverlayPermissionProvider {
    val state: StateFlow<UiOverlayPermissionState>
    suspend fun refresh()
    suspend fun openSettings()
}

interface LlmConfigTestProvider {
    suspend fun test(settings: UiSettings): UiLlmConfigTestResult
}

interface GkpLibraryProvider {
    val state: StateFlow<UiGkpLibraryState>
    suspend fun disablePack(gameId: String)
    suspend fun enablePack(gameId: String)
    suspend fun requestDelete(gameId: String)
    suspend fun confirmDelete()
    suspend fun cancelDelete()
}

interface GkpPreflightProvider {
    val state: StateFlow<UiGkpPreflightState>
    suspend fun preflightTree(uriString: String)
    suspend fun installPreflightedTree()
    suspend fun clearPreflight()
}

interface SettingsStore {
    val settings: Flow<UiSettings>
    suspend fun updatePort(port: Int)
    suspend fun updateLlmConfig(
        provider: UiLlmProvider,
        apiKey: String,
        baseUrl: String,
        model: String,
        timeoutSeconds: Int = DEFAULT_LLM_TIMEOUT_SECONDS,
        maxTokens: Int = DEFAULT_LLM_MAX_TOKENS,
    )
    suspend fun updateSpoilerLevel(level: UiSpoilerLevel)
}
