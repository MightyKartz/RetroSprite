package com.retrosprite.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.retrosprite.app.ui.viewmodel.EndpointStatusProvider
import com.retrosprite.app.ui.viewmodel.LlmConfigTestProvider
import com.retrosprite.app.ui.viewmodel.OverlayPermissionProvider
import com.retrosprite.app.ui.viewmodel.SettingsStore
import com.retrosprite.app.ui.viewmodel.UiAboutInfo
import com.retrosprite.app.ui.viewmodel.UiLlmConfigTestResult
import com.retrosprite.app.ui.viewmodel.UiLlmProvider
import com.retrosprite.app.ui.viewmodel.UiScreenTranslationApiProvider
import com.retrosprite.app.ui.viewmodel.UiSettings
import com.retrosprite.app.ui.viewmodel.UiSpoilerLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val store: SettingsStore,
    private val endpoint: EndpointStatusProvider,
    private val llmConfigTest: LlmConfigTestProvider,
    private val overlayPermission: OverlayPermissionProvider,
    val about: UiAboutInfo
) : ViewModel() {

    val settings: Flow<UiSettings> = store.settings
    val overlayPermissionState = overlayPermission.state
    private val _llmTestState = MutableStateFlow(SettingsLlmTestState())
    val llmTestState: StateFlow<SettingsLlmTestState> = _llmTestState.asStateFlow()

    fun applyPort(port: Int) {
        viewModelScope.launch {
            store.updatePort(port)
            endpoint.restart()
        }
    }

    fun applyLlmConfig(
        provider: UiLlmProvider,
        apiKey: String,
        baseUrl: String,
        model: String,
        timeoutSeconds: Int,
        maxTokens: Int,
    ) {
        viewModelScope.launch {
            store.updateLlmConfig(
                provider = provider,
                apiKey = apiKey,
                baseUrl = baseUrl.trim().ifBlank { provider.defaultBaseUrl },
                model = model.trim().ifBlank { provider.defaultModel },
                timeoutSeconds = timeoutSeconds,
                maxTokens = maxTokens,
            )
        }
    }

    fun applySpoilerLevel(level: UiSpoilerLevel) {
        viewModelScope.launch { store.updateSpoilerLevel(level) }
    }

    fun applyHotkeyVoiceTranscriptHudEnabled(enabled: Boolean) {
        viewModelScope.launch { store.updateHotkeyVoiceTranscriptHudEnabled(enabled) }
    }

    fun applyScreenTranslationApiConfig(
        provider: UiScreenTranslationApiProvider,
        baseUrl: String,
        apiKey: String,
        model: String,
        timeoutSeconds: Int,
    ) {
        viewModelScope.launch {
            store.updateScreenTranslationApiConfig(
                provider = provider,
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
                timeoutSeconds = timeoutSeconds,
            )
        }
    }

    fun refreshOverlayPermission() {
        viewModelScope.launch { overlayPermission.refresh() }
    }

    fun openOverlayPermissionSettings() {
        viewModelScope.launch { overlayPermission.openSettings() }
    }

    fun testLlmConfig(
        provider: UiLlmProvider,
        apiKey: String,
        baseUrl: String,
        model: String,
        timeoutSeconds: Int,
        maxTokens: Int,
    ) {
        viewModelScope.launch {
            _llmTestState.value = SettingsLlmTestState(isRunning = true)
            val settings = UiSettings(
                llmProvider = provider,
                llmApiKey = apiKey,
                llmBaseUrl = baseUrl.trim().ifBlank { provider.defaultBaseUrl },
                llmModel = model.trim().ifBlank { provider.defaultModel },
                llmTimeoutSeconds = timeoutSeconds,
                llmMaxTokens = maxTokens,
            )
            val result = llmConfigTest.test(settings)
            _llmTestState.value = SettingsLlmTestState(isRunning = false, result = result)
        }
    }

    companion object {
        fun factory(
            store: SettingsStore,
            endpoint: EndpointStatusProvider,
            llmConfigTest: LlmConfigTestProvider,
            overlayPermission: OverlayPermissionProvider,
            about: UiAboutInfo
        ) = viewModelFactory {
            initializer { SettingsViewModel(store, endpoint, llmConfigTest, overlayPermission, about) }
        }
    }
}

data class SettingsLlmTestState(
    val isRunning: Boolean = false,
    val result: UiLlmConfigTestResult? = null,
)
