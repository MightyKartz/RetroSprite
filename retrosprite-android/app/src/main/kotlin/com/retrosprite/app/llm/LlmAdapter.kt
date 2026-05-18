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
 * Phase 0 ships [MockLlmAdapter]; [OpenAiCompatibleLlmAdapter] is reserved
 * as a wiring target (skeleton only — `complete` throws).
 */
interface LlmAdapter {
    /** Stable identifier for routing / logging (e.g. "mock", "openai"). */
    val providerName: String

    /** Synchronous completion call (use a coroutine dispatcher inside). */
    suspend fun complete(request: LlmRequest): LlmResponse
}

/**
 * Static configuration for an [LlmAdapter] instance.
 *
 * @param providerName Routing key consumed by [LlmAdapterFactory]. Phase 0
 *   recognized values: `"mock"`, `"openai"`. Anything else falls back to
 *   `"mock"`.
 * @param baseUrl HTTP base URL (provider-specific, e.g.
 *   `https://api.openai.com/v1`).
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
        /** Convenience for tests / Phase 0 wiring. */
        val MOCK: LlmConfig = LlmConfig(
            providerName = "mock",
            baseUrl = "",
            apiKey = "",
            model = "mock",
            temperature = 0.0,
            timeoutSeconds = 1L,
        )
    }
}
