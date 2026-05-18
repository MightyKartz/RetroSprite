package com.retrosprite.app.llm

import com.retrosprite.app.domain.models.LlmRequest
import com.retrosprite.app.domain.models.LlmResponse

/**
 * Phase 0 [LlmAdapter] that returns a fixed acknowledgement string and
 * performs no I/O. Used by [com.retrosprite.app.domain.policy.AnswerComposer]
 * when the policy emits [com.retrosprite.app.domain.models.AnswerDecision.ComposeWithLlm]
 * during integration tests.
 */
class MockLlmAdapter : LlmAdapter {

    override val providerName: String = "mock"

    override suspend fun complete(request: LlmRequest): LlmResponse {
        return LlmResponse(
            text = MOCK_TEXT,
            citationsUsed = emptyList(),
            tokensIn = 0,
            tokensOut = 0,
            latencyMs = 0L,
        )
    }

    companion object {
        const val MOCK_TEXT: String = "(Phase 0 mock LLM response)"
    }
}
