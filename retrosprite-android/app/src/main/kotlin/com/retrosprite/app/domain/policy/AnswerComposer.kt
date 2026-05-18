package com.retrosprite.app.domain.policy

import com.retrosprite.app.domain.models.AnswerDecision
import com.retrosprite.app.domain.models.LlmRequest
import com.retrosprite.app.domain.models.SessionContext
import com.retrosprite.app.llm.LlmAdapter

/**
 * Renders an [AnswerDecision] into the final user-visible text.
 *
 * This is the single funnel where Endpoint -> Policy meets the LLM (or its
 * mock). Keeping it tiny and decision-driven lets us swap providers /
 * prompt strategies without touching the resolver, retrieval or policy.
 */
class AnswerComposer {

    suspend fun compose(
        decision: AnswerDecision,
        context: SessionContext,
        llm: LlmAdapter,
    ): String = when (decision) {
        is AnswerDecision.DirectAnswer -> decision.text

        is AnswerDecision.ComposeWithLlm -> {
            val response = llm.complete(
                LlmRequest(
                    systemPrompt = SYSTEM_PROMPT_ZH,
                    userPrompt = decision.prompt,
                    evidence = decision.evidence,
                    maxTokens = DEFAULT_MAX_TOKENS,
                )
            )
            response.text
        }

        is AnswerDecision.AskClarification -> decision.question

        is AnswerDecision.Refuse -> POLITE_REFUSAL_ZH
    }

    companion object {
        // Phase 0 / 1 default system prompt skeleton. Real prompt template
        // (with citation contract, spoiler rules, ≤ 3 sentences cap) lives
        // in the prompts module added in Phase 1.
        private const val SYSTEM_PROMPT_ZH: String =
            "你是 RetroSprite，一个专注于复古游戏内问答的伙伴。" +
                "请根据提供的证据用简体中文回答，最长 3 句话，避免剧透与无关闲聊。"

        private const val POLITE_REFUSAL_ZH: String =
            "抱歉，这个问题暂时超出我的范围，建议先继续游戏，或换个更具体的提问。"

        private const val DEFAULT_MAX_TOKENS: Int = 256
    }
}
