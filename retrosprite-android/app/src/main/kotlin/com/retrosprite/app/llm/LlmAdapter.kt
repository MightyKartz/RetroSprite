package com.retrosprite.app.llm

import com.retrosprite.app.domain.models.LlmRequest
import com.retrosprite.app.domain.models.LlmResponse

/**
 * Provider-agnostic LLM completion adapter.
 *
 * Implementations MUST:
 * - be safe to call from any coroutine context
 * - avoid logging API keys / prompts at info level
 * - translate provider errors to plain Kotlin exceptions
 *
 * Phase 0 ships [MockLlmAdapter] by default. Phase 1 can opt into
 * [OpenAiCompatibleLlmAdapter] through [LlmAdapterFactory].
 */
interface LlmAdapter {
    /** Stable identifier for routing / logging (e.g. "mock", "openai"). */
    val providerName: String

    /** Provider model id when known. Used only for diagnostics, never for routing. */
    val modelName: String?
        get() = null

    /** Per-call timeout budget in milliseconds when known. */
    val timeoutMs: Long?
        get() = null

    /** Synchronous completion call (use a coroutine dispatcher inside). */
    suspend fun complete(request: LlmRequest): LlmResponse
}

/**
 * Static configuration for an [LlmAdapter] instance.
 *
 * @param providerName Routing key consumed by [LlmAdapterFactory].
 * @param baseUrl HTTP base URL (provider-specific, e.g.
 *   `https://api.deepseek.com`).
 * @param apiKey Bearer token. Treat as secret — never log.
 * @param model Model id (e.g. `"gpt-4o-mini"`).
 * @param temperature Sampling temperature in `[0.0, 2.0]`.
 * @param timeoutSeconds HTTP per-call timeout.
 */
data class LlmConfig(
    val providerName: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val temperature: Double = 0.2,
    val timeoutSeconds: Long = 30L,
) {
    companion object {
        const val PROVIDER_MOCK: String = "mock"
        const val PROVIDER_OPENAI: String = "openai"
        const val PROVIDER_OPENAI_REAL: String = "openai-real"
        const val PROVIDER_DEEPSEEK: String = "deepseek"
        const val PROVIDER_CUSTOM: String = "custom"
        const val OPENAI_BASE_URL: String = "https://api.openai.com/v1"
        const val OPENAI_DEFAULT_MODEL: String = "gpt-4o-mini"
        const val DEEPSEEK_BASE_URL: String = "https://api.deepseek.com"
        const val DEEPSEEK_DEFAULT_MODEL: String = "deepseek-v4-pro"

        /** Convenience for tests / Phase 0 wiring. */
        val MOCK: LlmConfig = LlmConfig(
            providerName = PROVIDER_MOCK,
            baseUrl = "",
            apiKey = "",
            model = "mock",
            temperature = 0.0,
            timeoutSeconds = 1L,
        )

        fun deepSeek(
            apiKey: String,
            model: String = DEEPSEEK_DEFAULT_MODEL,
            baseUrl: String = DEEPSEEK_BASE_URL,
            temperature: Double = 0.2,
            timeoutSeconds: Long = 30L,
        ): LlmConfig = LlmConfig(
            providerName = PROVIDER_DEEPSEEK,
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            temperature = temperature,
            timeoutSeconds = timeoutSeconds,
        )

        fun openAi(
            apiKey: String,
            model: String = OPENAI_DEFAULT_MODEL,
            baseUrl: String = OPENAI_BASE_URL,
            temperature: Double = 0.2,
            timeoutSeconds: Long = 30L,
        ): LlmConfig = LlmConfig(
            providerName = PROVIDER_OPENAI,
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            temperature = temperature,
            timeoutSeconds = timeoutSeconds,
        )

        fun customOpenAiCompatible(
            apiKey: String,
            baseUrl: String,
            model: String,
            temperature: Double = 0.2,
            timeoutSeconds: Long = 30L,
        ): LlmConfig = LlmConfig(
            providerName = PROVIDER_CUSTOM,
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            temperature = temperature,
            timeoutSeconds = timeoutSeconds,
        )
    }
}
