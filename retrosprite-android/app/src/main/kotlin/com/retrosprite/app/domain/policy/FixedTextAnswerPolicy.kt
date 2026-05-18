package com.retrosprite.app.domain.policy

import com.retrosprite.app.domain.models.AnswerDecision
import com.retrosprite.app.domain.models.RetrievalResult
import com.retrosprite.app.domain.models.SessionContext
import com.retrosprite.app.domain.models.SpoilerLevel

/**
 * Phase 0 [AnswerPolicy] that always emits a single, fixed Chinese
 * acknowledgement regardless of the retrieved evidence.
 *
 * Purpose: validate the end-to-end RetroArch ↔ Sprite protocol path with
 * deterministic output before any knowledge base / LLM is wired up. The
 * returned text is intentionally one short paragraph, naturally satisfying
 * the "≤ 3 sentences" product rule.
 */
class FixedTextAnswerPolicy : AnswerPolicy {

    override suspend fun decide(
        results: List<RetrievalResult>,
        context: SessionContext,
    ): AnswerDecision {
        return AnswerDecision.DirectAnswer(
            text = PHASE_0_ACK_TEXT,
            sources = emptyList(),
            spoilerLevel = SpoilerLevel.LIGHT,
        )
    }

    companion object {
        const val PHASE_0_ACK_TEXT: String =
            "RetroSprite 已连接，目前还在 Phase 0 协议验证阶段。Phase 1 将接入游戏知识库。"
    }
}
