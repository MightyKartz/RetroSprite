package com.retrosprite.app.llm

/**
 * Builds an [LlmAdapter] from a [LlmConfig].
 *
 * Phase 0 contract: ALWAYS returns [MockLlmAdapter] unless the caller
 * explicitly opts into the real provider via
 * [com.retrosprite.app.llm.LlmConfig.providerName] = `"openai-real"`.
 *
 * Even in that case the resulting [OpenAiCompatibleLlmAdapter] will throw
 * [NotImplementedError] until Phase 1, so no live HTTP calls can leak.
 *
 * Phase 1 will route by [LlmConfig.providerName] (e.g. `"openai"`,
 * `"deepseek"`, `"ollama"`) and inject auth / retry policies from a
 * settings repository.
 */
object LlmAdapterFactory {

    fun create(config: LlmConfig): LlmAdapter {
        return when (config.providerName.lowercase()) {
            // Explicit opt-in for the real adapter — still a skeleton in Phase 0.
            "openai-real" -> OpenAiCompatibleLlmAdapter(config)
            // TODO(Phase 1): "openai", "deepseek", "ollama", ... → real adapters.
            else -> MockLlmAdapter()
        }
    }
}
