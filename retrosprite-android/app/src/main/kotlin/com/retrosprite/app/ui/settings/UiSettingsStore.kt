package com.retrosprite.app.ui.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.retrosprite.app.security.AndroidKeystoreSecretCipher
import com.retrosprite.app.security.SecretCipher
import com.retrosprite.app.ui.viewmodel.DEFAULT_LLM_MAX_TOKENS
import com.retrosprite.app.ui.viewmodel.DEFAULT_LLM_TIMEOUT_SECONDS
import com.retrosprite.app.ui.viewmodel.MAX_LLM_MAX_TOKENS
import com.retrosprite.app.ui.viewmodel.MAX_LLM_TIMEOUT_SECONDS
import com.retrosprite.app.ui.viewmodel.MIN_LLM_MAX_TOKENS
import com.retrosprite.app.ui.viewmodel.MIN_LLM_TIMEOUT_SECONDS
import com.retrosprite.app.ui.viewmodel.SettingsStore
import com.retrosprite.app.ui.viewmodel.UiLlmProvider
import com.retrosprite.app.ui.viewmodel.UiSettings
import com.retrosprite.app.ui.viewmodel.UiSpoilerLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed implementation of [SettingsStore].
 *
 * API keys are encrypted with Android Keystore before persistence. A legacy
 * plaintext key from older builds is read only long enough to migrate it into
 * the encrypted slot, then removed from Preferences.
 */
private val Context.uiSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "retrosprite_ui_settings"
)

class UiSettingsStore(
    private val context: Context,
    private val secretCipher: SecretCipher = AndroidKeystoreSecretCipher(),
) : SettingsStore {

    override val settings: Flow<UiSettings> =
        context.uiSettingsDataStore.data.map { prefs ->
            val provider = (prefs[Keys.LLM_PROVIDER] ?: UiLlmProvider.OpenAI.id).toProvider()
            UiSettings(
                port = prefs[Keys.PORT] ?: 4_404,
                llmProvider = provider,
                llmApiKey = decryptApiKey(prefs),
                llmBaseUrl = prefs[Keys.LLM_BASE_URL] ?: provider.defaultBaseUrl,
                llmModel = prefs[Keys.LLM_MODEL] ?: provider.defaultModel,
                llmTimeoutSeconds = (prefs[Keys.LLM_TIMEOUT_SECONDS] ?: DEFAULT_LLM_TIMEOUT_SECONDS)
                    .coerceIn(MIN_LLM_TIMEOUT_SECONDS, MAX_LLM_TIMEOUT_SECONDS),
                llmMaxTokens = (prefs[Keys.LLM_MAX_TOKENS] ?: DEFAULT_LLM_MAX_TOKENS)
                    .coerceIn(MIN_LLM_MAX_TOKENS, MAX_LLM_MAX_TOKENS),
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
        model: String,
        timeoutSeconds: Int,
        maxTokens: Int,
    ) {
        context.uiSettingsDataStore.edit { prefs ->
            prefs[Keys.LLM_PROVIDER] = provider.id
            if (apiKey.isBlank()) {
                prefs.remove(Keys.LLM_API_KEY_ENCRYPTED)
            } else {
                prefs[Keys.LLM_API_KEY_ENCRYPTED] = secretCipher.encryptToString(apiKey.trim())
            }
            prefs.remove(Keys.LEGACY_LLM_API_KEY)
            prefs[Keys.LLM_BASE_URL] = baseUrl.trim().ifBlank { provider.defaultBaseUrl }
            prefs[Keys.LLM_MODEL] = model.trim().ifBlank { provider.defaultModel }
            prefs[Keys.LLM_TIMEOUT_SECONDS] =
                timeoutSeconds.coerceIn(MIN_LLM_TIMEOUT_SECONDS, MAX_LLM_TIMEOUT_SECONDS)
            prefs[Keys.LLM_MAX_TOKENS] =
                maxTokens.coerceIn(MIN_LLM_MAX_TOKENS, MAX_LLM_MAX_TOKENS)
        }
    }

    override suspend fun updateSpoilerLevel(level: UiSpoilerLevel) {
        context.uiSettingsDataStore.edit { it[Keys.SPOILER_LEVEL] = level.id }
    }

    suspend fun migrateLegacyApiKeyIfNeeded() {
        context.uiSettingsDataStore.edit { prefs ->
            val legacy = prefs[Keys.LEGACY_LLM_API_KEY]
            if (legacy.isNullOrBlank()) {
                prefs.remove(Keys.LEGACY_LLM_API_KEY)
                return@edit
            }
            if (prefs[Keys.LLM_API_KEY_ENCRYPTED].isNullOrBlank()) {
                prefs[Keys.LLM_API_KEY_ENCRYPTED] = secretCipher.encryptToString(legacy)
            }
            prefs.remove(Keys.LEGACY_LLM_API_KEY)
        }
    }

    private fun decryptApiKey(prefs: Preferences): String {
        prefs[Keys.LLM_API_KEY_ENCRYPTED]?.let { encrypted ->
            return runCatching { secretCipher.decryptFromString(encrypted) }.getOrDefault("")
        }
        return ""
    }

    private object Keys {
        val PORT = intPreferencesKey("port")
        val LLM_PROVIDER = stringPreferencesKey("llm_provider")
        val LEGACY_LLM_API_KEY = stringPreferencesKey("llm_api_key")
        val LLM_API_KEY_ENCRYPTED = stringPreferencesKey("llm_api_key_encrypted")
        val LLM_BASE_URL = stringPreferencesKey("llm_base_url")
        val LLM_MODEL = stringPreferencesKey("llm_model")
        val LLM_TIMEOUT_SECONDS = intPreferencesKey("llm_timeout_seconds")
        val LLM_MAX_TOKENS = intPreferencesKey("llm_max_tokens")
        val SPOILER_LEVEL = stringPreferencesKey("spoiler_level")
    }
}

private fun String.toProvider(): UiLlmProvider =
    UiLlmProvider.values().firstOrNull { it.id == this } ?: UiLlmProvider.OpenAI

private fun String.toSpoiler(): UiSpoilerLevel =
    UiSpoilerLevel.values().firstOrNull { it.id == this } ?: UiSpoilerLevel.Light
