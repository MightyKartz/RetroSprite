package com.retrosprite.app.ui.settings

import com.retrosprite.app.domain.models.SpoilerLevel
import com.retrosprite.app.llm.LlmConfig
import com.retrosprite.app.ui.viewmodel.MAX_LLM_TIMEOUT_SECONDS
import com.retrosprite.app.ui.viewmodel.MIN_LLM_TIMEOUT_SECONDS
import com.retrosprite.app.ui.viewmodel.UiLlmProvider
import com.retrosprite.app.ui.viewmodel.UiSettings
import com.retrosprite.app.ui.viewmodel.UiSpoilerLevel

internal fun UiSettings.toLlmConfigOrNull(): LlmConfig? {
    val key = llmApiKey.trim()
    if (key.isBlank()) return null

    val baseUrl = llmBaseUrl.trim().ifBlank { llmProvider.defaultBaseUrl }
    val model = llmModel.trim().ifBlank { llmProvider.defaultModel }
    val timeoutSeconds = llmTimeoutSeconds
        .coerceIn(MIN_LLM_TIMEOUT_SECONDS, MAX_LLM_TIMEOUT_SECONDS)
        .toLong()

    return when (llmProvider) {
        UiLlmProvider.DeepSeek -> LlmConfig.deepSeek(
            apiKey = key,
            baseUrl = baseUrl,
            model = model,
            timeoutSeconds = timeoutSeconds,
        )

        UiLlmProvider.OpenAI -> LlmConfig.openAi(
            apiKey = key,
            baseUrl = baseUrl,
            model = model,
            timeoutSeconds = timeoutSeconds,
        )

        UiLlmProvider.Custom -> {
            if (baseUrl.isBlank() || model.isBlank()) return null
            LlmConfig.customOpenAiCompatible(
                apiKey = key,
                baseUrl = baseUrl,
                model = model,
                timeoutSeconds = timeoutSeconds,
            )
        }
    }
}

internal fun UiSpoilerLevel.toDomainSpoilerLevel(): SpoilerLevel = when (this) {
    UiSpoilerLevel.Light -> SpoilerLevel.LIGHT
    UiSpoilerLevel.Clear -> SpoilerLevel.CLEAR
    UiSpoilerLevel.Direct -> SpoilerLevel.FULL
}
