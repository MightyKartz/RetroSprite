package com.retrosprite.app.ui.viewmodel

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
            port = 8080,
            baseUrl = "http://192.168.1.42:8080",
            message = "0 \u4e2a\u8bf7\u6c42\u5728\u6392\u961f",
            lastHealthCheckMillis = System.currentTimeMillis() - 12_000,
            lastHealthOk = true
        )
    ): EndpointStatusProvider = FakeEndpointStatusProvider(initial)

    fun requestLog(items: List<UiRequestLogItem> = sampleLog()): RequestLogProvider =
        FakeRequestLogProvider(items)

    fun settings(initial: UiSettings = UiSettings()): SettingsStore =
        FakeSettingsStore(initial)

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
  "image": "",
  "sound": ""
}""",
                durationMillis = 1_842,
                ok = true
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
        model: String
    ) {
        _settings.update {
            it.copy(
                llmProvider = provider,
                llmApiKey = apiKey,
                llmBaseUrl = baseUrl,
                llmModel = model
            )
        }
    }

    override suspend fun updateSpoilerLevel(level: UiSpoilerLevel) {
        _settings.update { it.copy(spoilerLevel = level) }
    }
}
