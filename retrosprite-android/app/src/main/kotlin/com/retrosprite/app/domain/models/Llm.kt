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
