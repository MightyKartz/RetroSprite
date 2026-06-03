package com.retrosprite.app.ui.screens.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.retrosprite.app.ui.viewmodel.EndpointStatusProvider
import com.retrosprite.app.ui.viewmodel.RequestLogProvider
import com.retrosprite.app.ui.viewmodel.UiEndpointStatus
import com.retrosprite.app.ui.viewmodel.UiRequestLogItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DiagnosticsViewModel(
    private val endpoint: EndpointStatusProvider,
    private val requestLog: RequestLogProvider
) : ViewModel() {

    val status: StateFlow<UiEndpointStatus> = endpoint.status
    val log: Flow<List<UiRequestLogItem>> = requestLog.log

    fun checkHealth() { viewModelScope.launch { endpoint.checkHealth() } }
    fun runConnectionTest() { viewModelScope.launch { requestLog.sendConnectionTest() } }
    fun clearLog() { viewModelScope.launch { requestLog.clear() } }

    companion object {
        fun factory(
            endpoint: EndpointStatusProvider,
            requestLog: RequestLogProvider
        ) = viewModelFactory {
            initializer { DiagnosticsViewModel(endpoint, requestLog) }
        }
    }
}

internal enum class DiagnosticsSourceFilter(
    val id: String,
    val displayName: String,
) {
    All("all", "全部"),
    RetroArch("retroarch", "RetroArch"),
    Pending("pending", "Pending"),
    App("app", "App"),
    Debug("debug", "Debug"),
}

internal data class DiagnosticsSourceCounts(
    val all: Int,
    val retroArch: Int,
    val pending: Int,
    val app: Int,
    val debug: Int,
) {
    fun countFor(filter: DiagnosticsSourceFilter): Int = when (filter) {
        DiagnosticsSourceFilter.All -> all
        DiagnosticsSourceFilter.RetroArch -> retroArch
        DiagnosticsSourceFilter.Pending -> pending
        DiagnosticsSourceFilter.App -> app
        DiagnosticsSourceFilter.Debug -> debug
    }
}

internal fun List<UiRequestLogItem>.diagnosticsSourceCounts(): DiagnosticsSourceCounts =
    DiagnosticsSourceCounts(
        all = size,
        retroArch = count { it.diagnosticsSource() == DiagnosticsSourceFilter.RetroArch },
        pending = count { it.diagnosticsSource() == DiagnosticsSourceFilter.Pending },
        app = count { it.diagnosticsSource() == DiagnosticsSourceFilter.App },
        debug = count { it.diagnosticsSource() == DiagnosticsSourceFilter.Debug },
    )

internal fun List<UiRequestLogItem>.filterByDiagnosticsSource(
    filter: DiagnosticsSourceFilter,
): List<UiRequestLogItem> =
    if (filter == DiagnosticsSourceFilter.All) {
        this
    } else {
        filter { it.diagnosticsSource() == filter }
    }

internal fun UiRequestLogItem.diagnosticsSource(): DiagnosticsSourceFilter {
    val source = questionSource?.trim().orEmpty()
    val output = rawOutputMode.trim().lowercase()
    return when {
        source == QUESTION_SOURCE_PENDING_HOTKEY -> DiagnosticsSourceFilter.Pending
        source == QUESTION_SOURCE_APP || output.startsWith(APP_OUTPUT_PREFIX) -> DiagnosticsSourceFilter.App
        source == QUESTION_SOURCE_DEBUG || isDebug || output.startsWith(DEBUG_OUTPUT_PREFIX) -> DiagnosticsSourceFilter.Debug
        else -> DiagnosticsSourceFilter.RetroArch
    }
}

internal data class DiagnosticsFailureExplanation(
    val title: String,
    val message: String,
)

internal fun UiRequestLogItem.diagnosticFailureExplanations(): List<DiagnosticsFailureExplanation> {
    val output = rawOutputMode.trim().lowercase()
    val source = questionSource?.trim().orEmpty()
    val stage = pipelineStage.trim().lowercase()
    val haystack = listOfNotNull(
        responsePreview,
        responseText,
        fullResponseJson,
        llmError,
        answerDetail,
        answerShort,
    ).joinToString("\n")
    val lower = haystack.lowercase()
    val isHotkeyVoice = output.startsWith(HOTKEY_VOICE_OUTPUT_PREFIX) ||
        source == QUESTION_SOURCE_HOTKEY_VOICE
    val isScreenTranslation = output.startsWith(SCREEN_TRANSLATION_OUTPUT_PREFIX) ||
        source == QUESTION_SOURCE_SCREEN_TRANSLATION ||
        output.contains("screen_translation")

    return buildList {
        if (isHotkeyVoice && (stage == "no_evidence" || !ok)) {
            add(
                DiagnosticsFailureExplanation(
                    title = "ASR",
                    message = "已经收到热键语音转写；如果转写词和玩家原话不一致，把该转写记录为当前 GKP 的 observed_asr 别名。"
                )
            )
        }
        if (stage == GKP_DISABLED_STAGE) {
            add(
                DiagnosticsFailureExplanation(
                    title = "GKP",
                    message = "已匹配到本地知识包，但该包在 Packs 中被禁用；本次不会读取知识或调用 LLM。"
                )
            )
        } else if (stage == NO_EVIDENCE_STAGE) {
            add(
                DiagnosticsFailureExplanation(
                    title = "GKP",
                    message = "本地知识包没有命中可引用证据；需要补 aliases、knowledge row 或 qa_goldens 回归。"
                )
            )
        }
        if (isScreenTranslation && (imageBytes <= 0 || haystack.contains("没有截图"))) {
            add(
                DiagnosticsFailureExplanation(
                    title = "截图",
                    message = "当前请求没有可用截图；确认 RetroArch AI Service 发送了 image，并开启 Pause During Translation。"
                )
            )
        }
        if (isScreenTranslation && (haystack.contains("API Key", ignoreCase = true) ||
                haystack.contains("Base URL", ignoreCase = true) ||
                haystack.contains("翻译模型"))
        ) {
            add(
                DiagnosticsFailureExplanation(
                    title = "No-key",
                    message = "BYOK 翻译配置不完整；到 Settings 填写 Base URL、API Key 和推荐模型后再试。"
                )
            )
        }
        if (isScreenTranslation && (
                lower.contains("http ") ||
                    lower.contains(" api ") ||
                    lower.contains("api 返回") ||
                    lower.contains("screen_translation_failed"))
        ) {
            add(
                DiagnosticsFailureExplanation(
                    title = "BYOK API",
                    message = "截图已进入翻译链路，但兼容 API 返回错误；检查 provider、额度、模型名和网络连通性。"
                )
            )
        }
        if (lower.contains("timeout") || haystack.contains("超时")) {
            add(
                DiagnosticsFailureExplanation(
                    title = "超时",
                    message = "请求超过当前超时预算；可稍后重试，或在 Settings 提高 timeout 并检查网络/API 响应。"
                )
            )
        }
        if (lower.contains("permission") || haystack.contains("权限") || haystack.contains("授权")) {
            add(
                DiagnosticsFailureExplanation(
                    title = "权限",
                    message = "系统权限阻止了当前能力；检查麦克风、悬浮窗或 RetroArch/Android 授权状态。"
                )
            )
        }
    }.distinctBy { it.title }
}

private const val QUESTION_SOURCE_PENDING_HOTKEY: String = "pending_hotkey"
private const val QUESTION_SOURCE_APP: String = "app"
private const val QUESTION_SOURCE_DEBUG: String = "debug"
private const val QUESTION_SOURCE_HOTKEY_VOICE: String = "hotkey_voice"
private const val QUESTION_SOURCE_SCREEN_TRANSLATION: String = "hotkey_screen_translation"
private const val APP_OUTPUT_PREFIX: String = "app:"
private const val DEBUG_OUTPUT_PREFIX: String = "debug:"
private const val HOTKEY_VOICE_OUTPUT_PREFIX: String = "hotkey_voice:"
private const val SCREEN_TRANSLATION_OUTPUT_PREFIX: String = "hotkey_screen_translation:"
private const val GKP_DISABLED_STAGE: String = "gkp_disabled"
private const val NO_EVIDENCE_STAGE: String = "no_evidence"
