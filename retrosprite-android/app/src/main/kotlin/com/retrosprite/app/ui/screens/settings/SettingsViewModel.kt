package com.retrosprite.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.retrosprite.app.ui.viewmodel.EndpointStatusProvider
import com.retrosprite.app.ui.viewmodel.SettingsStore
import com.retrosprite.app.ui.viewmodel.UiAboutInfo
import com.retrosprite.app.ui.viewmodel.UiLlmProvider
import com.retrosprite.app.ui.viewmodel.UiSettings
import com.retrosprite.app.ui.viewmodel.UiSpoilerLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val store: SettingsStore,
    private val endpoint: EndpointStatusProvider,
    val about: UiAboutInfo
) : ViewModel() {

    val settings: Flow<UiSettings> = store.settings

    fun applyPort(port: Int) {
        viewModelScope.launch {
            store.updatePort(port)
            endpoint.restart()
        }
    }

    fun applyLlmConfig(provider: UiLlmProvider, apiKey: String, baseUrl: String, model: String) {
        viewModelScope.launch { store.updateLlmConfig(provider, apiKey, baseUrl, model) }
    }

    fun applySpoilerLevel(level: UiSpoilerLevel) {
        viewModelScope.launch { store.updateSpoilerLevel(level) }
    }

    companion object {
        fun factory(
            store: SettingsStore,
            endpoint: EndpointStatusProvider,
            about: UiAboutInfo
        ) = viewModelFactory {
            initializer { SettingsViewModel(store, endpoint, about) }
        }
    }
}
