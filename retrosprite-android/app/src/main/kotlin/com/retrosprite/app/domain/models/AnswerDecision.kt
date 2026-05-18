package com.retrosprite.app.domain.models

/**
 * Decision produced by [com.retrosprite.app.domain.policy.AnswerPolicy] from
 * the retrieval results + session context. Consumed by
 * [com.retrosprite.app.domain.policy.AnswerComposer] to produce the final
 * text response.
 */
sealed interface AnswerDecision {

    /**
     * The policy is confident enough to return a deterministic answer with
     * no LLM call. Used for simple lookups, refusals-with-text, and Phase 0
     * placeholders.
     */
    data class DirectAnswer(
        val text: String,
        val sources: List<String>,
        val spoilerLevel: SpoilerLevel,
    ) : AnswerDecision

    /**
     * Multiple consistent evidence snippets — the LLM should fuse them into
     * a citation-aware reply.
     */
    data class ComposeWithLlm(
        val prompt: String,
        val evidence: List<Evidence>,
        val spoilerLevel: SpoilerLevel,
    ) : AnswerDecision

    /**
     * Question is ambiguous or the retrieval results conflict — surface a
     * disambiguating question to the player.
     */
    data class AskClarification(
        val question: String,
        val options: List<String> = emptyList(),
    ) : AnswerDecision

    /**
     * Out-of-scope / unsafe request — refuse politely with a fixed reason.
     */
    data class Refuse(
        val reason: String,
    ) : AnswerDecision
}
