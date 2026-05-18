package com.retrosprite.app.domain.policy

import com.retrosprite.app.domain.models.AnswerDecision
import com.retrosprite.app.domain.models.RetrievalResult
import com.retrosprite.app.domain.models.SessionContext

/**
 * Decides how a retrieved result set + session context should be answered.
 *
 * Phase 1 mapping (target):
 * - empty results              → [AnswerDecision.AskClarification] / [AnswerDecision.Refuse]
 * - 1 high-confidence result   → [AnswerDecision.DirectAnswer]
 * - multiple consistent hits   → [AnswerDecision.ComposeWithLlm]
 * - conflicting hits           → [AnswerDecision.AskClarification]
 * - hits exceed spoiler budget → degrade or ask
 */
interface AnswerPolicy {
    suspend fun decide(
        results: List<RetrievalResult>,
        context: SessionContext,
    ): AnswerDecision
}
