package com.retrosprite.app.ui.integration

import com.retrosprite.app.domain.models.LlmRequest
import com.retrosprite.app.llm.LlmAdapter
import com.retrosprite.app.llm.LlmAdapterFactory
import com.retrosprite.app.llm.LlmConfig
import com.retrosprite.app.ui.settings.toLlmConfigOrNull
import com.retrosprite.app.ui.viewmodel.LlmConfigTestProvider
import com.retrosprite.app.ui.viewmodel.MAX_LLM_MAX_TOKENS
import com.retrosprite.app.ui.viewmodel.MAX_LLM_TIMEOUT_SECONDS
import com.retrosprite.app.ui.viewmodel.MIN_LLM_MAX_TOKENS
import com.retrosprite.app.ui.viewmodel.MIN_LLM_TIMEOUT_SECONDS
import com.retrosprite.app.ui.viewmodel.UiLlmConfigTestResult
import com.retrosprite.app.ui.viewmodel.UiLlmProvider
import com.retrosprite.app.ui.viewmodel.UiSettings

/**
 * Runs a Settings-only LLM smoke test.
 *
 * This intentionally bypasses RequestLogger/request_logs so configuration
 * checks do not pollute the player's RetroArch/App question history.
 */
class RealLlmConfigTestProvider(
    private val factory: (LlmConfig) -> LlmAdapter = LlmAdapterFactory::create,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) : LlmConfigTestProvider {

    override suspend fun test(settings: UiSettings): UiLlmConfigTestResult {
        val provider = settings.llmProvider.id
        val model = settings.displayModel()
        val maxTokens = settings.llmMaxTokens.coerceIn(MIN_LLM_MAX_TOKENS, MAX_LLM_MAX_TOKENS)
        val timeoutMs = settings.llmTimeoutSeconds
            .coerceIn(MIN_LLM_TIMEOUT_SECONDS, MAX_LLM_TIMEOUT_SECONDS)
            .times(1_000L)

        val config = settings.toLlmConfigOrNull()
        if (config == null) {
            return UiLlmConfigTestResult(
                ok = false,
                provider = provider,
                model = model,
                maxTokens = maxTokens,
                timeoutMs = timeoutMs,
                errorMessage = settings.missingConfigMessage(),
            )
        }

        val startedAt = clockMillis()
        return runCatching {
            val response = factory(config).complete(
                LlmRequest(
                    systemPrompt = "You are a RetroSprite LLM configuration probe. Reply with exactly OK.",
                    userPrompt = "Reply with exactly: OK",
                    maxTokens = maxTokens,
                )
            )
            UiLlmConfigTestResult(
                ok = true,
                provider = config.providerName,
                model = config.model,
                maxTokens = maxTokens,
                timeoutMs = timeoutMs,
                latencyMs = response.latencyMs.takeIf { it > 0L } ?: elapsedSince(startedAt),
                tokensIn = response.tokensIn,
                tokensOut = response.tokensOut,
                responsePreview = response.text.trim().take(RESPONSE_PREVIEW_MAX_CHARS),
            )
        }.getOrElse { error ->
            UiLlmConfigTestResult(
                ok = false,
                provider = config.providerName,
                model = config.model,
                maxTokens = maxTokens,
                timeoutMs = timeoutMs,
                latencyMs = elapsedSince(startedAt),
                errorMessage = error.safeMessage(settings),
            )
        }
    }

    private fun elapsedSince(startedAt: Long): Long =
        (clockMillis() - startedAt).coerceAtLeast(0L)

    private fun UiSettings.displayModel(): String =
        llmModel.trim().ifBlank { llmProvider.defaultModel }.ifBlank { "-" }

    private fun UiSettings.missingConfigMessage(): String = when {
        llmApiKey.isBlank() -> "请先填写问答 API Key"
        llmProvider == UiLlmProvider.Custom && llmBaseUrl.isBlank() -> "请填写问答 Base URL"
        llmProvider == UiLlmProvider.Custom && llmModel.isBlank() -> "请填写问答模型"
        else -> "问答模型配置不完整"
    }

    private fun Throwable.safeMessage(settings: UiSettings): String {
        var clean = message?.ifBlank { null } ?: javaClass.simpleName.ifBlank { "provider_error" }
        settings.llmApiKey.trim().takeIf { it.isNotBlank() }?.let { key ->
            clean = clean.replace(key, "[redacted]")
        }
        return clean.take(ERROR_MAX_CHARS)
    }

    private companion object {
        const val RESPONSE_PREVIEW_MAX_CHARS = 80
        const val ERROR_MAX_CHARS = 220
    }
}
