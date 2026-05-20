package com.retrosprite.app.llm

/**
 * Builds an [LlmAdapter] from a [LlmConfig].
 *
 * Phase 0 wiring still uses [LlmConfig.MOCK] in [ServiceLocator]. Callers
 * must opt into network-backed providers explicitly.
 */
object LlmAdapterFactory {

    fun create(config: LlmConfig): LlmAdapter {
        return when (config.providerName.lowercase()) {
            LlmConfig.PROVIDER_OPENAI,
            LlmConfig.PROVIDER_OPENAI_REAL,
            LlmConfig.PROVIDER_DEEPSEEK,
            LlmConfig.PROVIDER_CUSTOM,
            -> OpenAiCompatibleLlmAdapter(config)
            else -> MockLlmAdapter()
        }
    }
}
