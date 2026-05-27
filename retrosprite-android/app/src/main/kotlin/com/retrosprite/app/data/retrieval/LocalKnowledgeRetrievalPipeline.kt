package com.retrosprite.app.data.retrieval

import com.retrosprite.app.data.models.KnowledgeChunkDomain
import com.retrosprite.app.data.repository.KnowledgeRepository
import com.retrosprite.app.domain.intent.QuestionIntentClassifier
import com.retrosprite.app.domain.intent.containsAny
import com.retrosprite.app.domain.intent.normalizeNaturalQuestion
import com.retrosprite.app.domain.models.AnswerType
import com.retrosprite.app.domain.models.Evidence
import com.retrosprite.app.domain.models.RetrievalQuery
import com.retrosprite.app.domain.models.RetrievalResult
import com.retrosprite.app.domain.models.SpoilerLevel
import com.retrosprite.app.domain.retrieval.RetrievalPipeline
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.max

/**
 * Local-first GKP retriever for Phase 1.
 *
 * Funnel:
 *  1. template/question-pattern matches from installed rows
 *  2. entity/canonical/alias matches
 *  3. repository FTS5 search with LIKE fallback supplied by [KnowledgeRepository]
 */
class LocalKnowledgeRetrievalPipeline(
    private val knowledgeRepository: KnowledgeRepository,
) : RetrievalPipeline {
    private val templateDocumentMatcher = TemplateDocumentMatcher()

    override suspend fun retrieve(query: RetrievalQuery): List<RetrievalResult> {
        val gameId = query.gameId?.trim().orEmpty()
        val normalizedQuery = query.normalizedQuery.trim()
        if (gameId.isEmpty() || normalizedQuery.isEmpty()) return emptyList()

        val rows = knowledgeRepository.listByGame(gameId)
        val allRows = rows.filter { it.allowedFor(query) }
        val queryIntent = QuestionIntentClassifier.classify(normalizedQuery)

        val candidates = buildList {
            addAll(nameMappingMatches(rows, normalizedQuery))
            addAll(templateMatches(rows, normalizedQuery, query))
            addAll(templateDocumentMatches(rows, normalizedQuery, query, queryIntent))
            addAll(aliasAndEntityMatches(allRows, normalizedQuery, queryIntent))
            addAll(ftsMatches(gameId, normalizedQuery, query, queryIntent))
            addAll(scopedEntityFallbackMatches(rows, normalizedQuery, query))
        }

        return candidates
            .filter { it.evidence.isNotEmpty() }
            .groupBy { it.entityId }
            .map { (_, rows) ->
                rows.maxBy { it.confidence }
            }
            .sortedByDescending { it.confidence }
            .take(query.limit.coerceAtLeast(1))
    }

    override suspend fun normalizeQuestion(raw: String, language: String): String =
        raw.normalizeNaturalQuestion()

    override suspend fun suggestQuestions(
        query: RetrievalQuery,
        results: List<RetrievalResult>,
    ): List<String> {
        val gameId = query.gameId?.trim().orEmpty()
        val normalizedQuery = query.normalizedQuery.trim()
        if (gameId.isEmpty() || normalizedQuery.isEmpty()) return emptyList()

        val rows = knowledgeRepository.listByGame(gameId)
        val resultEntityIds = results.map { it.entityId }.toSet()
        val queryIntent = QuestionIntentClassifier.classify(normalizedQuery)
        val allCandidates = rows.flatMapIndexed { rowIndex, row ->
            row.questionSuggestionCandidates(
                query = query,
                queryIntent = queryIntent,
                sourceOrder = rowIndex * SUGGESTION_SOURCE_ORDER_ROW_STRIDE,
            )
        }
        val scopedCandidates = if (resultEntityIds.isNotEmpty()) {
            allCandidates.filter { it.entityId in resultEntityIds }.ifEmpty { allCandidates }
        } else {
            allCandidates
        }

        return scopedCandidates
            .asSequence()
            .map { candidate ->
                ScoredQuestionSuggestion(
                    question = candidate.question,
                    normalizedQuestion = normalizeSync(candidate.question),
                    semanticKey = candidate.semanticKey(),
                    score = candidate.scoreFor(normalizedQuery, queryIntent, resultEntityIds),
                    sourceOrder = candidate.sourceOrder,
                )
            }
            .filter { it.normalizedQuestion.isNotBlank() }
            .filterNot { it.normalizedQuestion.isNearDuplicateOf(normalizedQuery) }
            .sortedWith(
                compareByDescending<ScoredQuestionSuggestion> { it.score }
                    .thenBy { it.sourceOrder }
            )
            .distinctBy { it.semanticKey }
            .distinctBy { it.normalizedQuestion }
            .map { it.question.toSuggestedQuestionText() }
            .take(MAX_SUGGESTED_QUESTIONS)
            .toList()
    }

    private fun nameMappingMatches(
        rows: List<KnowledgeChunkDomain>,
        normalizedQuery: String,
    ): List<RetrievalResult> {
        val request = nameMappingRequest(normalizedQuery) ?: return emptyList()
        return rows.mapNotNull { row ->
            val matchedTerm = row.matchingTerm(normalizedQuery) ?: return@mapNotNull null
            val answer = row.nameMappingAnswer(request, matchedTerm) ?: return@mapNotNull null
            row.toResult(
                snippet = answer,
                matchScore = NAME_MAPPING_MATCH_SCORE,
                sourceId = row.sourceRefs.firstOrNull(),
                spoilerOverride = SpoilerLevel.LIGHT,
                progressGateOverride = null,
                answerType = AnswerType.NameMapping,
            )
        }
    }

    private fun templateMatches(
        rows: List<KnowledgeChunkDomain>,
        normalizedQuery: String,
        query: RetrievalQuery,
    ): List<RetrievalResult> {
        val queryIntent = QuestionIntentClassifier.classify(normalizedQuery)
        return rows.flatMap { row ->
            row.answerTemplates.mapNotNull { rawTemplate ->
                val template = runCatching { JSON.parseToJsonElement(rawTemplate).jsonObject }
                    .getOrNull()
                    ?: return@mapNotNull null
                val intent = template.templateStringOrNull("intent")
                val matchedTerm = row.matchingTerm(normalizedQuery)
                if (!templateIntentCompatible(
                        intent = intent,
                        queryIntent = queryIntent,
                        normalizedQuery = normalizedQuery,
                        matchedTerm = matchedTerm,
                    )
                ) {
                    return@mapNotNull null
                }
                val selectedAnswer = TemplateAnswerSelector.select(template, query.spoilerLevel)
                    ?: return@mapNotNull null
                val patterns = template.templateArrayStrings("question_patterns")
                if (!gkpSpoilerAllowed(selectedAnswer.spoilerLevel, query.spoilerLevel)) return@mapNotNull null
                val selectedSpoiler = selectedAnswer.spoilerLevel.toDomainSpoiler()
                if (!progressGateAllowed(row.progressGate, query.progressGate) &&
                    selectedSpoiler != SpoilerLevel.LIGHT
                ) {
                    return@mapNotNull null
                }
                val patternMatched = patterns.any { pattern ->
                    val normalizedPattern = normalizeSync(pattern)
                    normalizedPattern.isNotEmpty() && (
                        normalizedQuery == normalizedPattern ||
                            normalizedQuery.contains(normalizedPattern)
                        )
                }
                val entityIntentFallback = intent != null &&
                    matchedTerm != null &&
                    queryIntent == AnswerType.UnknownOrOutOfScope &&
                    entityAnchoredIntentCompatible(normalizedQuery, intent)
                val matched = patternMatched || entityIntentFallback
                if (!matched) return@mapNotNull null
                row.toResult(
                    snippet = selectedAnswer.text,
                    matchScore = TEMPLATE_MATCH_SCORE,
                    sourceId = template.templateArrayStrings("source_refs").firstOrNull(),
                    spoilerOverride = selectedSpoiler,
                    progressGateOverride = null,
                    answerType = intent.toAnswerTypeOrNull(),
                )
            }
        }
    }

    private fun templateDocumentMatches(
        rows: List<KnowledgeChunkDomain>,
        normalizedQuery: String,
        query: RetrievalQuery,
        queryIntent: AnswerType,
    ): List<RetrievalResult> {
        val match = templateDocumentMatcher.bestMatch(
            query = normalizedQuery,
            queryIntent = queryIntent,
            rows = rows,
            tolerance = query.spoilerLevel,
            progressGate = query.progressGate,
        ) ?: return emptyList()
        val row = rows.firstOrNull { it.entityId == match.document.entityId }
            ?: return emptyList()
        return listOf(
            row.toResult(
                snippet = match.document.selectedAnswer,
                matchScore = TEMPLATE_DOCUMENT_MATCH_SCORE,
                sourceId = match.document.sourceRefs.firstOrNull(),
                spoilerOverride = match.document.spoilerLevel,
                progressGateOverride = null,
                answerType = match.document.intent.toAnswerTypeOrNull(),
            )
        )
    }

    private fun aliasAndEntityMatches(
        rows: List<KnowledgeChunkDomain>,
        normalizedQuery: String,
        queryIntent: AnswerType,
    ): List<RetrievalResult> =
        rows.mapNotNull { row ->
            val bestTerm = row.entityTerms().firstOrNull { term ->
                normalizedQuery.contains(term) || term.contains(normalizedQuery)
            } ?: return@mapNotNull null

            val termBoost = (bestTerm.length.toDouble() / normalizedQuery.length.coerceAtLeast(1))
                .coerceIn(0.10, 1.0)
            val broadPenalty = if (bestTerm in BROAD_NATURAL_TERMS) BROAD_TERM_PENALTY else 0.0
            row.toResult(
                snippet = row.descriptionShort,
                matchScore = ALIAS_MATCH_SCORE + (termBoost * 0.10) -
                    broadPenalty + row.intentBoost(queryIntent),
            )
        }

    private suspend fun ftsMatches(
        gameId: String,
        normalizedQuery: String,
        query: RetrievalQuery,
        queryIntent: AnswerType,
    ): List<RetrievalResult> =
        knowledgeRepository.searchFts(
            gameId = gameId,
            query = normalizedQuery,
            limit = query.limit.coerceAtLeast(1) * FTS_OVERFETCH_FACTOR,
        )
            .asSequence()
            .filter { it.allowedFor(query) }
            .map { row ->
                row.toResult(
                    snippet = row.descriptionShort,
                    matchScore = FTS_MATCH_SCORE + row.intentBoost(queryIntent),
                )
            }
            .toList()

    private fun scopedEntityFallbackMatches(
        rows: List<KnowledgeChunkDomain>,
        normalizedQuery: String,
        query: RetrievalQuery,
    ): List<RetrievalResult> {
        if (normalizedQuery.isExhaustiveListRequest()) return emptyList()
        return rows.mapNotNull { row ->
            if (!gkpSpoilerAllowed(row.spoilerLevel, query.spoilerLevel)) return@mapNotNull null
            if (row.allowedFor(query)) return@mapNotNull null
            val matchedTerm = row.fallbackMatchingTerm(normalizedQuery) ?: return@mapNotNull null
            val termBoost = (matchedTerm.length.toDouble() / normalizedQuery.length.coerceAtLeast(1))
                .coerceIn(0.10, 1.0)
            row.toResult(
                snippet = row.scopedEntityFallbackSnippet(matchedTerm),
                matchScore = ENTITY_FALLBACK_MATCH_SCORE + termBoost * 0.08,
                sourceId = row.sourceRefs.firstOrNull(),
                spoilerOverride = row.spoilerLevel.toDomainSpoiler(),
                progressGateOverride = null,
                answerType = null,
            )
        }
    }

    private fun KnowledgeChunkDomain.toResult(
        snippet: String,
        matchScore: Double,
        sourceId: String? = null,
        spoilerOverride: SpoilerLevel? = null,
        progressGateOverride: String? = progressGate,
        answerType: AnswerType? = null,
    ): RetrievalResult {
        val evidenceSpoiler = spoilerOverride ?: spoilerLevel.toDomainSpoiler()
        return RetrievalResult(
            entityId = entityId,
            canonicalName = canonicalName,
            evidence = listOf(
                Evidence(
                    sourceId = sourceId ?: sourceRefs.firstOrNull() ?: entityId,
                    snippet = snippet,
                    score = matchScore,
                    spoilerLevel = evidenceSpoiler,
                    progressGate = progressGateOverride,
                )
            ),
            confidence = (confidence.baseConfidence() * 0.60 + matchScore.coerceIn(0.0, 1.0) * 0.40)
                .coerceIn(0.0, 1.0),
            answerType = answerType,
        )
    }

    private fun KnowledgeChunkDomain.intentBoost(queryIntent: AnswerType): Double {
        val type = entityType.lowercase()
        val terms = buildList {
            add(entityId)
            add(canonicalName)
            addAll(aliases)
        }.joinToString(" ")
            .lowercase()
        return when (queryIntent) {
            AnswerType.GameOverview ->
                if (type in setOf("note", "strategy") ||
                    terms.containsAny("核心玩法", "overview", "gameplay", "主要玩什么")
                ) {
                    INTENT_STRONG_BOOST
                } else {
                    0.0
                }

            AnswerType.BeginnerGuide ->
                if (type in setOf("strategy", "quest") ||
                    progressGate.isNullOrBlank() ||
                    progressGate == "start"
                ) {
                    INTENT_MEDIUM_BOOST
                } else {
                    0.0
                }

            AnswerType.TeamBuild ->
                if (type in setOf("strategy", "npc", "character")) INTENT_STRONG_BOOST else 0.0

            AnswerType.Leveling ->
                if (type in setOf("mechanic", "strategy") ||
                    terms.containsAny("经验", "练级", "leveling")
                ) {
                    INTENT_STRONG_BOOST
                } else {
                    0.0
                }

            AnswerType.Location ->
                if (type in setOf("location", "item", "quest")) INTENT_MEDIUM_BOOST else 0.0

            else -> 0.0
        }
    }

    private fun KnowledgeChunkDomain.matchingTerm(normalizedQuery: String): String? =
        entityTerms()
            .firstOrNull { term ->
                normalizedQuery.contains(term) || term.contains(normalizedQuery)
            }

    private fun KnowledgeChunkDomain.fallbackMatchingTerm(normalizedQuery: String): String? =
        entityTerms()
            .filterNot { it in BROAD_NATURAL_TERMS }
            .filter { it.length >= MIN_FALLBACK_ENTITY_TERM_LENGTH }
            .firstOrNull { term -> normalizedQuery.contains(term) }

    private fun KnowledgeChunkDomain.scopedEntityFallbackSnippet(matchedTerm: String): String {
        val displayName = canonicalName.ifBlank { matchedTerm }
        val known = descriptionShort.trim().trimEnd('。', '.', '；', ';')
        return if (known.isBlank()) {
            "我找到你提到的「$displayName」，但当前低剧透知识还不能可靠回答这个具体问法。"
        } else {
            "我找到你提到的「$displayName」，但当前低剧透知识还不能可靠回答这个具体问法。已知：$known。"
        }
    }

    private fun KnowledgeChunkDomain.entityTerms(): List<String> =
        buildList {
            add(canonicalName)
            addAll(aliases)
            add(entityId.substringAfterLast('.'))
        }
            .map(::normalizeSync)
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedWith(compareByDescending<String> { it.length }.thenBy { it })

    private fun KnowledgeChunkDomain.nameMappingAnswer(
        request: NameMappingRequest,
        matchedTerm: String,
    ): String? {
        val parts = canonicalName.split("/")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val englishName = parts.firstOrNull { LATIN.containsMatchIn(it) }
            ?: aliases.firstOrNull { LATIN.containsMatchIn(it) }
        val localizedName = parts.firstOrNull { CJK.containsMatchIn(it) }
            ?: aliases.firstOrNull { CJK.containsMatchIn(it) }
            ?: matchedTerm.takeIf { CJK.containsMatchIn(it) }

        return when (request) {
            NameMappingRequest.English ->
                if (englishName != null && localizedName != null) {
                    "${localizedName}对应英文名是 $englishName。"
                } else {
                    englishName?.let { "${canonicalName} 的英文名是 $it。" }
                }

            NameMappingRequest.Chinese ->
                if (englishName != null && localizedName != null) {
                    "$englishName 对应中文名是$localizedName。"
                } else {
                    localizedName?.let { "${canonicalName} 的中文名是 $it。" }
                }
        }
    }

    private fun nameMappingRequest(normalizedQuery: String): NameMappingRequest? {
        val asksName = NAME_MAPPING_CUES.any { normalizedQuery.contains(it) }
        return when {
            !asksName -> null
            normalizedQuery.contains("英文") ||
                normalizedQuery.contains("english") ||
                normalizedQuery.contains("原名") -> NameMappingRequest.English

            normalizedQuery.contains("中文") ||
                normalizedQuery.contains("汉化") -> NameMappingRequest.Chinese

            else -> null
        }
    }

    private fun templateIntentCompatible(
        intent: String?,
        queryIntent: AnswerType,
        normalizedQuery: String,
        matchedTerm: String?,
    ): Boolean {
        if (intent == null) return true
        if (intent == queryIntent.wireName) return true
        return matchedTerm != null &&
            queryIntent == AnswerType.UnknownOrOutOfScope &&
            entityAnchoredIntentCompatible(normalizedQuery, intent)
    }

    private fun entityAnchoredIntentCompatible(normalizedQuery: String, intent: String): Boolean =
        when (intent) {
            AnswerType.Usage.wireName ->
                normalizedQuery.containsAny("怎么", "咋", "用", "用途", "有什么", "给谁", "干嘛")

            AnswerType.Location.wireName ->
                normalizedQuery.containsAny("在哪", "哪里", "位置", "怎么拿", "哪拿")

            AnswerType.NameMapping.wireName ->
                normalizedQuery.containsAny("是谁", "中文", "英文", "原名", "叫什么", "叫啥")

            else -> false
        }

    private fun KnowledgeChunkDomain.questionSuggestionCandidates(
        query: RetrievalQuery,
        queryIntent: AnswerType,
        sourceOrder: Int,
    ): List<QuestionSuggestionCandidate> =
        answerTemplates.flatMapIndexed { templateIndex, rawTemplate ->
            val template = runCatching { JSON.parseToJsonElement(rawTemplate).jsonObject }
                .getOrNull()
                ?: return@flatMapIndexed emptyList()
            val selectedAnswer = TemplateAnswerSelector.select(template, query.spoilerLevel)
                ?: return@flatMapIndexed emptyList()
            if (!gkpSpoilerAllowed(selectedAnswer.spoilerLevel, query.spoilerLevel)) {
                return@flatMapIndexed emptyList()
            }
            val selectedSpoiler = selectedAnswer.spoilerLevel.toDomainSpoiler()
            if (!progressGateAllowed(progressGate, query.progressGate) &&
                selectedSpoiler != SpoilerLevel.LIGHT
            ) {
                return@flatMapIndexed emptyList()
            }
            val intent = template.templateStringOrNull("intent")
            val patterns = template.templateArrayStrings("question_patterns")
            patterns.mapIndexed { patternIndex, pattern ->
                QuestionSuggestionCandidate(
                    question = pattern,
                    entityId = entityId,
                    intent = intent,
                    entityMatched = matchingTerm(query.normalizedQuery) != null,
                    intentMatched = intent == queryIntent.wireName,
                    sourceOrder = sourceOrder + templateIndex * SUGGESTION_SOURCE_ORDER_TEMPLATE_STRIDE +
                        patternIndex,
                )
            }
        }

    private fun QuestionSuggestionCandidate.scoreFor(
        normalizedQuery: String,
        queryIntent: AnswerType,
        resultEntityIds: Set<String>,
    ): Double {
        val normalizedQuestion = normalizeSync(question)
        val sameEntityBoost = if (entityId in resultEntityIds) SAME_ENTITY_SUGGESTION_BOOST else 0.0
        val entityBoost = if (entityMatched) ENTITY_SUGGESTION_BOOST else 0.0
        val intentBoost = if (intentMatched || intent == queryIntent.wireName) {
            INTENT_SUGGESTION_BOOST
        } else {
            0.0
        }
        return (phraseSimilarity(normalizedQuery, normalizedQuestion) +
            sameEntityBoost +
            entityBoost +
            intentBoost)
            .coerceIn(0.0, 1.0)
    }

    private fun QuestionSuggestionCandidate.semanticKey(): String =
        "$entityId:${question.suggestionCueBucket()}:${intent.orEmpty()}"

    private fun String.suggestionCueBucket(): String {
        val normalized = normalizeSync(this)
        return when {
            normalized.containsAny("在哪", "哪里", "位置", "怎么拿", "哪拿") -> "location"
            normalized.containsAny("给谁", "谁适合", "适合谁", "培养谁", "谁值得", "谁强") -> "target"
            normalized.containsAny("有什么用", "怎么用", "用途", "干嘛", "是什么") -> "usage"
            normalized.containsAny("怎么打", "打不过", "站位", "打法") -> "strategy"
            normalized.containsAny("怎么玩", "主要玩什么", "核心玩法", "介绍") -> "overview"
            else -> normalized
        }
    }

    private fun String.isExhaustiveListRequest(): Boolean {
        val asksList = containsAny("列出", "列表", "清单", "表", "图鉴", "全收集")
        val asksAll = containsAny("全部", "所有", "完整", "全")
        return asksList && asksAll
    }

    private fun String.isNearDuplicateOf(other: String): Boolean {
        if (this == other) return true
        if (length >= 4 && other.contains(this)) return true
        if (other.length >= 4 && contains(other)) return true
        return phraseSimilarity(this, other) >= NEAR_DUPLICATE_QUESTION_SIMILARITY
    }

    private fun phraseSimilarity(left: String, right: String): Double =
        max(dice(left.ngrams(2), right.ngrams(2)), dice(left.ngrams(3), right.ngrams(3)))

    private fun String.ngrams(size: Int): Set<String> {
        val compact = replace(" ", "")
        if (compact.length < size) return if (compact.isBlank()) emptySet() else setOf(compact)
        return compact.windowed(size).toSet()
    }

    private fun dice(left: Set<String>, right: Set<String>): Double {
        if (left.isEmpty() || right.isEmpty()) return 0.0
        return (2.0 * left.intersect(right).size) / (left.size + right.size)
    }

    private fun String.toSuggestedQuestionText(): String {
        val clean = trim()
        if (clean.isBlank()) return clean
        return if (clean.endsWith("？") || clean.endsWith("?")) clean else "$clean？"
    }

    private fun KnowledgeChunkDomain.allowedFor(query: RetrievalQuery): Boolean =
        gkpSpoilerAllowed(spoilerLevel, query.spoilerLevel) &&
            progressGateAllowed(progressGate, query.progressGate)

    private fun gkpSpoilerAllowed(level: String, tolerance: SpoilerLevel): Boolean {
        val rank = when (level.lowercase()) {
            "none" -> 0
            "light" -> 1
            "medium" -> 2
            "heavy" -> 3
            else -> 3
        }
        val maxRank = when (tolerance) {
            SpoilerLevel.LIGHT -> 1
            SpoilerLevel.CLEAR -> 2
            SpoilerLevel.FULL -> 3
        }
        return rank <= maxRank
    }

    private fun progressGateAllowed(rowGate: String?, queryGate: String?): Boolean {
        if (rowGate.isNullOrBlank()) return true
        if (rowGate == "start") return true
        return rowGate == queryGate
    }

    private fun String.toDomainSpoiler(): SpoilerLevel = when (lowercase()) {
        "none", "light" -> SpoilerLevel.LIGHT
        "medium" -> SpoilerLevel.CLEAR
        else -> SpoilerLevel.FULL
    }

    private fun String.baseConfidence(): Double = when (lowercase()) {
        "verified" -> 0.95
        "community" -> 0.75
        "uncertain" -> 0.45
        else -> 0.35
    }

    private fun JsonObject.arrayStrings(name: String): List<String> =
        this[name]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()

    private fun JsonObject.selectAnswer(tolerance: SpoilerLevel): TemplateAnswer? {
        val tiered = when (tolerance) {
            SpoilerLevel.LIGHT -> stringOrNull("answer_light")?.let {
                TemplateAnswer(it, stringOrNull("spoiler_light") ?: "light")
            }

            SpoilerLevel.CLEAR -> stringOrNull("answer_clear")?.let {
                TemplateAnswer(it, stringOrNull("spoiler_clear") ?: "medium")
            } ?: stringOrNull("answer_light")?.let {
                TemplateAnswer(it, stringOrNull("spoiler_light") ?: "light")
            }

            SpoilerLevel.FULL -> stringOrNull("answer_direct")?.let {
                TemplateAnswer(it, stringOrNull("spoiler_direct") ?: "heavy")
            } ?: stringOrNull("answer_clear")?.let {
                TemplateAnswer(it, stringOrNull("spoiler_clear") ?: "medium")
            } ?: stringOrNull("answer_light")?.let {
                TemplateAnswer(it, stringOrNull("spoiler_light") ?: "light")
            }
        }
        return tiered?.takeIf { it.text.isNotBlank() }
            ?: stringOrNull("answer")?.takeIf { it.isNotBlank() }?.let {
                TemplateAnswer(it, stringOrNull("spoiler_level") ?: "light")
            }
    }

    private fun JsonObject.stringOrNull(name: String): String? {
        val value = this[name] ?: return null
        if (value is JsonNull) return null
        return (value as? JsonPrimitive)?.contentOrNull
    }

    private fun normalizeSync(value: String): String =
        value.trim()
            .replace(WHITESPACE, " ")
            .lowercase()
            .normalizeNaturalQuestion()

    private fun String?.toAnswerTypeOrNull(): AnswerType? {
        if (this.isNullOrBlank()) return null
        return AnswerType.values().firstOrNull { it.wireName == this }
    }

    private enum class NameMappingRequest {
        English,
        Chinese,
    }

    private data class TemplateAnswer(
        val text: String,
        val spoilerLevel: String,
    )

    private data class QuestionSuggestionCandidate(
        val question: String,
        val entityId: String,
        val intent: String?,
        val entityMatched: Boolean,
        val intentMatched: Boolean,
        val sourceOrder: Int,
    )

    private data class ScoredQuestionSuggestion(
        val question: String,
        val normalizedQuestion: String,
        val semanticKey: String,
        val score: Double,
        val sourceOrder: Int,
    )

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val LATIN = Regex("[A-Za-z]")
        val CJK = Regex("\\p{IsHan}")
        val NAME_MAPPING_CUES = listOf(
            "英文",
            "中文",
            "汉化",
            "原名",
            "叫什么",
            "叫啥",
            "对应",
            "是谁",
            "english",
        )
        val JSON = Json {
            ignoreUnknownKeys = true
            isLenient = false
        }

        const val TEMPLATE_MATCH_SCORE = 1.0
        const val TEMPLATE_DOCUMENT_MATCH_SCORE = 0.98
        const val NAME_MAPPING_MATCH_SCORE = 1.0
        const val ALIAS_MATCH_SCORE = 0.82
        const val FTS_MATCH_SCORE = 0.66
        const val ENTITY_FALLBACK_MATCH_SCORE = 0.74
        const val FTS_OVERFETCH_FACTOR = 3
        const val INTENT_STRONG_BOOST = 0.25
        const val INTENT_MEDIUM_BOOST = 0.16
        const val BROAD_TERM_PENALTY = 0.10
        const val MIN_FALLBACK_ENTITY_TERM_LENGTH = 2
        const val MAX_SUGGESTED_QUESTIONS = 3
        const val SAME_ENTITY_SUGGESTION_BOOST = 0.35
        const val ENTITY_SUGGESTION_BOOST = 0.20
        const val INTENT_SUGGESTION_BOOST = 0.12
        const val NEAR_DUPLICATE_QUESTION_SIMILARITY = 0.92
        const val SUGGESTION_SOURCE_ORDER_ROW_STRIDE = 1000
        const val SUGGESTION_SOURCE_ORDER_TEMPLATE_STRIDE = 100
        val BROAD_NATURAL_TERMS = setOf(
            "怎么玩",
            "游戏",
            "这个游戏",
            "这游戏",
        )
    }
}
