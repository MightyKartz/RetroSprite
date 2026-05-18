package com.retrosprite.app.ui.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.retrosprite.app.ui.viewmodel.SettingsStore
import com.retrosprite.app.ui.viewmodel.UiLlmProvider
import com.retrosprite.app.ui.viewmodel.UiSettings
import com.retrosprite.app.ui.viewmodel.UiSpoilerLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed implementation of [SettingsStore].
 *
 * Phase 0 stores the API key in plain Preferences with a TODO marker. Phase 1 should
 * migrate the key (and any future credentials) to EncryptedSharedPreferences or the
 * Android Keystore-backed Tink AEAD pattern. Tracking issue: see
 * RetroSprite_Development_Plan.md \u00a7 Security.
 */
private val Context.uiSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "retrosprite_ui_settings"
)

class UiSettingsStore(private val context: Context) : SettingsStore {

    override val settings: Flow<UiSettings> =
        context.uiSettingsDataStore.data.map { prefs ->
            UiSettings(
                port = prefs[Keys.PORT] ?: 8080,
                llmProvider = (prefs[Keys.LLM_PROVIDER] ?: UiLlmProvider.OpenAI.id).toProvider(),
                llmApiKey = prefs[Keys.LLM_API_KEY].orEmpty(), // TODO(phase1): encrypt
                llmBaseUrl = prefs[Keys.LLM_BASE_URL].orEmpty(),
                llmModel = prefs[Keys.LLM_MODEL] ?: "gpt-4o-mini",
                spoilerLevel = (prefs[Keys.SPOILER_LEVEL] ?: UiSpoilerLevel.Light.id).toSpoiler()
            )
        }

    override suspend fun updatePort(port: Int) {
        context.uiSettingsDataStore.edit { it[Keys.PORT] = port.coerceIn(1024, 65_535) }
    }

    override suspend fun updateLlmConfig(
        provider: UiLlmProvider,
        apiKey: String,
        baseUrl: String,
        model: String
    ) {
        context.uiSettingsDataStore.edit { prefs ->
            prefs[Keys.LLM_PROVIDER] = provider.id
            prefs[Keys.LLM_API_KEY] = apiKey
            prefs[Keys.LLM_BASE_URL] = baseUrl
            prefs[Keys.LLM_MODEL] = model
        }
    }

    override suspend fun updateSpoilerLevel(level: UiSpoilerLevel) {
        context.uiSettingsDataStore.edit { it[Keys.SPOILER_LEVEL] = level.id }
    }

    private object Keys {
        val PORT = intPreferencesKey("port")
        val LLM_PROVIDER = stringPreferencesKey("llm_provider")
        val LLM_API_KEY = stringPreferencesKey("llm_api_key")
        val LLM_BASE_URL = stringPreferencesKey("llm_base_url")
        val LLM_MODEL = stringPreferencesKey("llm_model")
        val SPOILER_LEVEL = stringPreferencesKey("spoiler_level")
    }
}

private fun String.toProvider(): UiLlmProvider =
    UiLlmProvider.values().firstOrNull { it.id == this } ?: UiLlmProvider.OpenAI

private fun String.toSpoiler(): UiSpoilerLevel =
    UiSpoilerLevel.values().firstOrNull { it.id == this } ?: UiSpoilerLevel.Light
