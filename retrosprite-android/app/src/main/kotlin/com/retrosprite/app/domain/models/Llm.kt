package com.retrosprite.app.domain.models

/**
 * LLM completion request envelope. Provider-agnostic.
 *
 * @param systemPrompt The system / instruction prompt (role, persona,
 *   spoiler rules, citation contract...).
 * @param userPrompt The user-facing prompt assembled from question +
 *   evidence.
 * @param evidence Original evidence carried through for traceability /
 *   citation rendering. The adapter does not have to re-send it.
 * @param maxTokens Soft cap on response length. `null` means provider default.
 */
data class LlmRequest(
    val systemPrompt: String,
    val userPrompt: String,
    val evidence: List<Evidence> = emptyList(),
    val maxTokens: Int? = null,
)

/**
 * LLM completion response envelope. Provider-agnostic.
 *
 * @param text The generated text (post-trim).
 * @param citationsUsed Source ids the model actually referenced. May be
 *   empty when the model did not cite anything.
 * @param tokensIn Prompt token count (0 if unknown).
 * @param tokensOut Completion token count (0 if unknown).
 * @param latencyMs Wall clock round-trip time in milliseconds.
 */
data class LlmResponse(
    val text: String,
    val citationsUsed: List<String> = emptyList(),
    val tokensIn: Int = 0,
    val tokensOut: Int = 0,
    val latencyMs: Long = 0L,
)

/**
 * Diagnostic summary for one optional LLM call inside the answer pipeline.
 *
 * This intentionally carries no prompts or API keys. It is safe to surface in
 * local diagnostics and request logs.
 */
data class LlmCallTrace(
    val status: String = STATUS_SKIPPED,
    val providerName: String? = null,
    val modelName: String? = null,
    val maxTokens: Int? = null,
    val timeoutMs: Long? = null,
    val latencyMs: Long? = null,
    val tokensIn: Int = 0,
    val tokensOut: Int = 0,
    val errorMessage: String? = null,
) {
    companion object {
        const val STATUS_SKIPPED: String = "skipped"
        const val STATUS_USED: String = "used"
        const val STATUS_FAILED: String = "failed"
    }
}

data class ComposedAnswer(
    val text: String,
    val llmTrace: LlmCallTrace = LlmCallTrace(),
)
