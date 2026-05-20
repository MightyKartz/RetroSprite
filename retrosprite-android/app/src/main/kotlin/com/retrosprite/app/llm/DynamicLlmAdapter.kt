package com.retrosprite.app.llm

import com.retrosprite.app.domain.models.LlmRequest
import com.retrosprite.app.domain.models.LlmResponse

/**
 * Delegates LLM calls to the latest runtime config.
 *
 * The app-level object graph is intentionally static, but Settings are
 * reactive. This wrapper lets the query pipeline keep one [LlmAdapter] while
 * calls opt into a real provider only after the user has saved a usable BYOK
 * configuration.
 */
class DynamicLlmAdapter(
    private val configProvider: () -> LlmConfig?,
    private val fallback: LlmAdapter = MockLlmAdapter(),
    private val factory: (LlmConfig) -> LlmAdapter = LlmAdapterFactory::create,
) : LlmAdapter {

    override val providerName: String
        get() = configProvider()?.providerName ?: fallback.providerName

    override val modelName: String?
        get() = configProvider()?.model ?: fallback.modelName

    override val timeoutMs: Long?
        get() = configProvider()?.timeoutSeconds?.times(1_000L) ?: fallback.timeoutMs

    override suspend fun complete(request: LlmRequest): LlmResponse {
        val config = configProvider() ?: return fallback.complete(request)
        return factory(config).complete(request)
    }
}
