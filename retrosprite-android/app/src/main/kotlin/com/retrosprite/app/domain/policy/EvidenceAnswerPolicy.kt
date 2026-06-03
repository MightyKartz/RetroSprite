package com.retrosprite.app.domain.policy

import com.retrosprite.app.domain.models.AnswerDecision
import com.retrosprite.app.domain.models.AnswerConfidence
import com.retrosprite.app.domain.models.AnswerNextAction
import com.retrosprite.app.domain.models.AnswerType
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
    private val composeWithLlmForMultipleEvidence: Boolean = false,
) : AnswerPolicy {

    override suspend fun decide(
        results: List<RetrievalResult>,
        context: SessionContext,
    ): AnswerDecision {
        if (context.gameIdentity.gameId.isNullOrBlank()) {
            if (context.gameIdentity.source == GameIdentity.SOURCE_GKP_DISABLED) {
                return direct(GKP_DISABLED_TEXT, answerType = AnswerType.NoEvidence)
            }
            return direct(UNKNOWN_GAME_TEXT, answerType = AnswerType.NoEvidence)
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
                val suggestions = noEvidenceSuggestionsFor(context)
                direct(
                    noEvidenceTextFor(context, suggestions),
                    answerType = noEvidenceAnswerTypeFor(context),
                    suggestedQuestions = suggestions,
                )
            } else {
                direct(SPOILER_DOWNGRADE_TEXT, answerType = context.questionIntent)
            }
        }

        closeCandidateClarification(admissible, context)?.let { return it }

        val bestPair = admissible.first()
        val evidence = admissible
            .map { (_, evidence) -> evidence }
            .distinctBy { it.sourceId to it.snippet }

        val best = evidence.first()
        val answerType = bestPair.first.answerType ?: context.questionIntent
        val confidence = confidenceFor(bestPair.first.confidence, best)
        if (shouldComposeWithLlm(evidence, best, context)) {
            return AnswerDecision.ComposeWithLlm(
                prompt = "请综合本地证据回答玩家问题，保持 1 到 3 句话，并保留低剧透表达。",
                evidence = evidence.take(MAX_LLM_EVIDENCE),
                spoilerLevel = context.spoilerLevel,
                answerType = answerType,
                confidence = confidence,
                nextActions = nextActionsFor(answerType, hasSources = true),
                suggestedQuestions = context.suggestedQuestions,
            )
        }
        if (shouldSummarizeLocally(evidence, best)) {
            return AnswerDecision.LocalSummary(
                evidence = evidence.take(MAX_LOCAL_EVIDENCE),
                spoilerLevel = best.spoilerLevel,
                answerType = answerType,
                confidence = confidence,
                nextActions = nextActionsFor(answerType, hasSources = true),
                suggestedQuestions = context.suggestedQuestions,
            )
        }

        return AnswerDecision.DirectAnswer(
            text = best.snippet.cleanOneLine(),
            sources = listOf(best.sourceId).filter { it.isNotBlank() }.distinct(),
            spoilerLevel = best.spoilerLevel,
            answerType = answerType,
            confidence = confidence,
            nextActions = nextActionsFor(answerType, hasSources = true),
            suggestedQuestions = context.suggestedQuestions,
        )
    }

    private fun direct(
        text: String,
        answerType: AnswerType = AnswerType.UnknownOrOutOfScope,
        suggestedQuestions: List<String> = emptyList(),
    ): AnswerDecision.DirectAnswer =
        AnswerDecision.DirectAnswer(
            text = text,
            sources = emptyList(),
            spoilerLevel = SpoilerLevel.LIGHT,
            answerType = answerType,
            confidence = AnswerConfidence.Low,
            nextActions = listOf(AnswerNextAction.MoreSpecific, AnswerNextAction.MarkIncorrect),
            suggestedQuestions = suggestedQuestions,
        )

    private fun noEvidenceTextFor(context: SessionContext, suggestions: List<String>): String =
        when {
            context.playerQuestion.orEmpty().isLikelyIncompleteQuestionFragment() ->
                INCOMPLETE_QUESTION_FRAGMENT_TEXT

            context.questionIntent == AnswerType.RouteHint &&
                context.naturalQuestionFrame.needsProgressContext ->
                ROUTE_NEEDS_PROGRESS_TEXT

            context.questionIntent == AnswerType.TeamBuild &&
                context.naturalQuestionFrame.needsProgressContext ->
                TEAM_BUILD_NEEDS_PROGRESS_TEXT

            else -> NO_EVIDENCE_TEXT
        }.withSuggestedQuestions(suggestions)

    private fun noEvidenceSuggestionsFor(context: SessionContext): List<String> =
        context.suggestedQuestions.ifEmpty {
            QuestionSuggestionEngine.suggest(context)
        }

    private fun noEvidenceAnswerTypeFor(context: SessionContext): AnswerType =
        when (context.questionIntent) {
            AnswerType.RouteHint,
            AnswerType.TeamBuild -> context.questionIntent

            else -> AnswerType.NoEvidence
        }

    private fun closeCandidateClarification(
        admissible: List<Pair<RetrievalResult, Evidence>>,
        context: SessionContext,
    ): AnswerDecision.AskClarification? {
        if (context.questionIntent !in ENTITY_CLARIFICATION_TYPES) return null
        val distinct = admissible
            .map { (result, _) -> result }
            .distinctBy { it.entityId }
        if (distinct.size < 2) return null
        val first = distinct[0]
        val second = distinct[1]
        if (first.confidence - second.confidence > CLOSE_CANDIDATE_CONFIDENCE_DELTA) return null
        return AnswerDecision.AskClarification(
            question = "你说的是 ${first.canonicalName} 还是 ${second.canonicalName}？",
            options = listOf(first.canonicalName, second.canonicalName),
        )
    }

    private fun confidenceFor(resultConfidence: Double, evidence: Evidence): AnswerConfidence =
        if (resultConfidence >= HIGH_CONFIDENCE_THRESHOLD || evidence.score >= DIRECT_TEMPLATE_SCORE_THRESHOLD) {
            AnswerConfidence.High
        } else {
            AnswerConfidence.Medium
        }

    private fun nextActionsFor(answerType: AnswerType, hasSources: Boolean): List<AnswerNextAction> =
        buildList {
            if (answerType in SPOILER_LAYERED_TYPES) {
                add(AnswerNextAction.MoreSpecific)
                add(AnswerNextAction.DirectAnswer)
            }
            if (hasSources) add(AnswerNextAction.ViewSources)
            add(AnswerNextAction.MarkIncorrect)
        }

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

    private fun shouldSummarizeLocally(
        evidence: List<Evidence>,
        best: Evidence,
    ): Boolean =
        best.score < DIRECT_TEMPLATE_SCORE_THRESHOLD &&
            evidence.size >= 2 &&
            evidence.map { it.sourceId to it.snippet }.distinct().size >= 2

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

        const val INCOMPLETE_QUESTION_FRAGMENT_TEXT: String =
            "没听清这个问题，可以再按一次热键重问。也可以说得更具体一点，比如角色、道具、地点或目标。"

        const val ROUTE_NEEDS_PROGRESS_TEXT: String =
            "我还不知道你的当前进度。你现在在哪个城镇、刚打完哪场战斗，或刚收到哪个角色？"

        const val TEAM_BUILD_NEEDS_PROGRESS_TEXT: String =
            "我还不知道你的当前队伍和进度。告诉我你现在到哪一章、刚收了哪些角色，我可以更具体地建议培养谁。"

        const val SPOILER_DOWNGRADE_TEXT: String =
            "这条答案可能包含超过当前提示级别的内容。我先不直接展开；如果你愿意，可以切到更明确或直接答案后再问。"

        const val MAX_LLM_EVIDENCE: Int = 4
        const val MAX_LOCAL_EVIDENCE: Int = 3
        const val DIRECT_TEMPLATE_SCORE_THRESHOLD: Double = 0.99
        const val HIGH_CONFIDENCE_THRESHOLD: Double = 0.95
        const val CLOSE_CANDIDATE_CONFIDENCE_DELTA: Double = 0.05
        val SPOILER_LAYERED_TYPES: Set<AnswerType> = setOf(
            AnswerType.Location,
            AnswerType.RouteHint,
            AnswerType.Strategy,
            AnswerType.TeamBuild,
        )
        val ENTITY_CLARIFICATION_TYPES: Set<AnswerType> = setOf(
            AnswerType.NameMapping,
            AnswerType.Usage,
            AnswerType.Location,
        )
    }
}

private object QuestionSuggestionEngine {

    fun suggest(context: SessionContext): List<String> {
        val question = context.playerQuestion.orEmpty().lowercase()
        val candidates = when {
            context.questionIntent == AnswerType.TeamBuild ||
                question.containsAny("人物", "角色", "厉害", "培养", "队伍", "阵容", "谁强") ->
                listOf("哪些角色适合培养？", "队伍怎么搭配？", "Sarah 值得练吗？")

            context.questionIntent == AnswerType.Usage ||
                question.containsAny("道具", "物品", "装备", "咋办", "怎么用", "有什么用") ->
                listOf("医疗草怎么用？", "Mithril 有什么用？", "特殊转职道具怎么用？")

            context.questionIntent == AnswerType.Location ||
                question.containsAny("在哪", "哪里", "位置", "怎么拿", "哪拿") ->
                listOf("Mithril 在哪里？", "勇者之证在哪里？", "有隐藏物品吗？")

            context.questionIntent == AnswerType.Leveling ||
                question.containsAny("升级", "经验", "练级", "刷级") ->
                listOf("怎么玩经验高？", "怎么练级快？", "低等级怎么追经验？")

            context.questionIntent == AnswerType.RouteHint ||
                question.containsAny("卡住", "下一步", "去哪", "路线") ->
                listOf("卡住了下一步去哪？", "开局先做什么？", "不要剧透下一步去哪？")

            context.questionIntent == AnswerType.Strategy ||
                question.containsAny("打不过", "敌人", "战斗", "站位", "稳") ->
                listOf("打不过敌人怎么办？", "新手战斗怎么站位？", "怎么打得稳？")

            else ->
                listOf("这游戏怎么玩？", "新手前期怎么玩稳？", "队伍怎么搭配？")
        }

        return candidates
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_SUGGESTIONS)
    }

    private const val MAX_SUGGESTIONS: Int = 3
}

private fun String.withSuggestedQuestions(suggestions: List<String>): String {
    if (suggestions.isEmpty()) return this
    return buildString {
        append(this@withSuggestedQuestions.trim())
        appendLine()
        appendLine("你可以这样问：")
        suggestions.forEach { suggestion ->
            appendLine("· $suggestion")
        }
    }.trimEnd()
}

private fun String.containsAny(vararg terms: String): Boolean =
    terms.any { contains(it.lowercase()) }

private fun String.isLikelyIncompleteQuestionFragment(): Boolean {
    val compact = trim()
        .lowercase()
        .replace(Regex("[\\p{Punct}，。？！、；：\\s]+"), "")
    if (compact.length < 2) return false
    if (compact in COMPLETE_SHORT_QUESTIONS) return false
    if (compact in INCOMPLETE_QUESTION_FRAGMENTS) return true
    if (compact.length <= 4 &&
        compact.startsWith("怎么") &&
        compact.lastOrNull() in INCOMPLETE_ACTION_TAILS
    ) {
        return true
    }
    if (compact.length <= 8 &&
        INCOMPLETE_QUESTION_SUFFIXES.any { compact.endsWith(it) }
    ) {
        return true
    }
    return false
}

private val COMPLETE_SHORT_QUESTIONS = setOf(
    "去哪",
    "在哪",
    "怎么用",
    "怎么打",
    "怎么过",
    "是什么",
)

private val INCOMPLETE_QUESTION_FRAGMENTS = setOf(
    "怎么",
    "怎么获",
    "怎么获得",
    "怎么找",
    "怎么去",
    "怎么拿",
    "是什",
    "为什",
    "玩什",
    "做什",
    "干什",
)

private val INCOMPLETE_ACTION_TAILS = setOf(
    '获',
    '找',
    '去',
    '拿',
)

private val INCOMPLETE_QUESTION_SUFFIXES = listOf(
    "是什",
    "为什",
    "玩什",
    "做什",
    "干什",
)
