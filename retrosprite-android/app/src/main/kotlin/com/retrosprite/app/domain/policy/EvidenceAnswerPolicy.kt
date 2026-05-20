package com.retrosprite.app.domain.policy

import com.retrosprite.app.domain.models.AnswerDecision
import com.retrosprite.app.domain.models.Evidence
import com.retrosprite.app.domain.models.GameIdentity
import com.retrosprite.app.domain.models.RetrievalResult
import com.retrosprite.app.domain.models.SessionContext
import com.retrosprite.app.domain.models.SpoilerLevel

/**
 * Phase 1 policy for local GKP evidence.
 *
 * The policy is intentionally deterministic for M2.4: it answers from the
 * best admissible snippet, refuses to leak higher-spoiler evidence, and never
 * calls the LLM on empty evidence. LLM synthesis is introduced in M2.5.
 */
class EvidenceAnswerPolicy(
    private val composeWithLlmForMultipleEvidence: Boolean = true,
) : AnswerPolicy {

    override suspend fun decide(
        results: List<RetrievalResult>,
        context: SessionContext,
    ): AnswerDecision {
        if (context.gameIdentity.gameId.isNullOrBlank()) {
            if (context.gameIdentity.source == GameIdentity.SOURCE_GKP_DISABLED) {
                return direct(GKP_DISABLED_TEXT)
            }
            return direct(UNKNOWN_GAME_TEXT)
        }

        val allEvidence = results.flatMap { result ->
            result.evidence.map { evidence -> result to evidence }
        }
        val admissible = allEvidence
            .filter { (_, evidence) -> evidence.spoilerLevel.allowedBy(context.spoilerLevel) }
            .sortedWith(
                compareByDescending<Pair<RetrievalResult, Evidence>> { (result, _) -> result.confidence }
                    .thenByDescending { (_, evidence) -> evidence.score }
            )

        if (admissible.isEmpty()) {
            return if (allEvidence.isEmpty()) {
                direct(NO_EVIDENCE_TEXT)
            } else {
                direct(SPOILER_DOWNGRADE_TEXT)
            }
        }

        val evidence = admissible
            .map { (_, evidence) -> evidence }
            .distinctBy { it.sourceId to it.snippet }

        val best = evidence.first()
        if (shouldComposeWithLlm(evidence, best, context)) {
            return AnswerDecision.ComposeWithLlm(
                prompt = "请综合本地证据回答玩家问题，保持 1 到 3 句话，并保留低剧透表达。",
                evidence = evidence.take(MAX_LLM_EVIDENCE),
                spoilerLevel = context.spoilerLevel,
            )
        }

        return AnswerDecision.DirectAnswer(
            text = best.snippet.cleanOneLine(),
            sources = listOf(best.sourceId).filter { it.isNotBlank() }.distinct(),
            spoilerLevel = best.spoilerLevel,
        )
    }

    private fun direct(text: String): AnswerDecision.DirectAnswer =
        AnswerDecision.DirectAnswer(
            text = text,
            sources = emptyList(),
            spoilerLevel = SpoilerLevel.LIGHT,
        )

    private fun SpoilerLevel.allowedBy(tolerance: SpoilerLevel): Boolean =
        rank() <= tolerance.rank()

    private fun SpoilerLevel.rank(): Int = when (this) {
        SpoilerLevel.LIGHT -> 1
        SpoilerLevel.CLEAR -> 2
        SpoilerLevel.FULL -> 3
    }

    private fun shouldComposeWithLlm(
        evidence: List<Evidence>,
        best: Evidence,
        context: SessionContext,
    ): Boolean =
        composeWithLlmForMultipleEvidence &&
            best.score < DIRECT_TEMPLATE_SCORE_THRESHOLD &&
            evidence.size >= 2 &&
            evidence.map { it.sourceId }.filter { it.isNotBlank() }.distinct().size >= 2 &&
            !context.playerQuestion.isNullOrBlank()

    private fun String.cleanOneLine(): String =
        trim()
            .replace(WHITESPACE, " ")

    private companion object {
        val WHITESPACE = Regex("\\s+")

        const val UNKNOWN_GAME_TEXT: String =
            "我还没识别出当前游戏，暂时不能给可靠答案。请确认 RetroArch label 或安装对应知识包。"

        const val GKP_DISABLED_TEXT: String =
            "知识包已禁用：我找到了当前游戏的本地 GKP，但它在 Packs 中处于禁用状态，所以不会用于回答。请到 Packs 重新启用后再问。"

        const val NO_EVIDENCE_TEXT: String =
            "我还没有足够证据回答这个问题。请补充版本、位置或换个更具体的问法。"

        const val SPOILER_DOWNGRADE_TEXT: String =
            "这条答案可能包含超过当前提示级别的内容。我先不直接展开；如果你愿意，可以切到更明确或直接答案后再问。"

        const val MAX_LLM_EVIDENCE: Int = 4
        const val DIRECT_TEMPLATE_SCORE_THRESHOLD: Double = 0.99
    }
}
