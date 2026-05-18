package com.retrosprite.app.ui.viewmodel

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
    val baseUrl: String,           // e.g. "http://192.168.1.42:8080" or "http://localhost:8080"
    val healthPath: String = "/health",
    val message: String? = null,   // human-readable note, esp. for Error
    val lastHealthCheckMillis: Long? = null,
    val lastHealthOk: Boolean? = null
)

/** Mode bit decoded from RetroArch's `output` flags ("text", "image", "sound"). */
enum class UiOutputMode { Text, Image, Sound, Mixed }

/** A single request in the diagnostics log list. */
data class UiRequestLogItem(
    val id: String,
    val timestampMillis: Long,
    val label: String?,            // RetroArch `label` field (often game name)
    val imageBytes: Int,           // raw screenshot size before base64
    val paused: Boolean,
    val outputMode: UiOutputMode,
    val responsePreview: String,   // first ~80 chars of LLM output
    val fullResponseJson: String,  // pretty-printed JSON for the detail dialog
    val durationMillis: Long,
    val ok: Boolean
)

/** Provider configurations the UI knows how to render. */
enum class UiLlmProvider(val id: String, val displayName: String) {
    OpenAI("openai", "OpenAI"),
    DeepSeek("deepseek", "DeepSeek"),
    Custom("custom", "\u81ea\u5b9a\u4e49 (Custom)")
}

/** Spoiler intensity. Strings are persisted; enum is for UI binding. */
enum class UiSpoilerLevel(val id: String, val displayName: String) {
    Light("light", "\u8f7b\u63d0\u793a"),
    Clear("clear", "\u66f4\u660e\u786e"),
    Direct("direct", "\u76f4\u63a5\u7b54\u6848")
}

/** A flat snapshot of all user-tunable settings. */
data class UiSettings(
    val port: Int = 8080,
    val llmProvider: UiLlmProvider = UiLlmProvider.OpenAI,
    val llmApiKey: String = "",
    val llmBaseUrl: String = "",
    val llmModel: String = "gpt-4o-mini",
    val spoilerLevel: UiSpoilerLevel = UiSpoilerLevel.Light
)

/** Header info for the "About" section. Static for Phase 0. */
data class UiAboutInfo(
    val appVersion: String = "0.1.0",
    val gkpSchemaVersion: String = "0.1",
    val buildFlavor: String = "phase0-preview"
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
}

interface SettingsStore {
    val settings: Flow<UiSettings>
    suspend fun updatePort(port: Int)
    suspend fun updateLlmConfig(
        provider: UiLlmProvider,
        apiKey: String,
        baseUrl: String,
        model: String
    )
    suspend fun updateSpoilerLevel(level: UiSpoilerLevel)
}
