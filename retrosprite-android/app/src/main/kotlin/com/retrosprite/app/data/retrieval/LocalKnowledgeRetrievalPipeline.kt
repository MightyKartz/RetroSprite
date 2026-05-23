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
                if (intent != null && intent != queryIntent.wireName) return@mapNotNull null
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
                val matched = patterns.any { pattern ->
                    val normalizedPattern = normalizeSync(pattern)
                    normalizedPattern.isNotEmpty() && (
                        normalizedQuery.contains(normalizedPattern) ||
                            normalizedPattern.contains(normalizedQuery)
                        )
                } || (intent != null && row.matchingTerm(normalizedQuery) != null)
                if (!matched) return@mapNotNull null
                row.toResult(
                    snippet = selectedAnswer.text,
                    matchScore = TEMPLATE_MATCH_SCORE,
                    sourceId = template.templateArrayStrings("source_refs").firstOrNull(),
                    spoilerOverride = selectedSpoiler,
                    progressGateOverride = null,
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
            )
        )
    }

    private fun aliasAndEntityMatches(
        rows: List<KnowledgeChunkDomain>,
        normalizedQuery: String,
        queryIntent: AnswerType,
    ): List<RetrievalResult> =
        rows.mapNotNull { row ->
            val terms = buildList {
                add(row.canonicalName)
                addAll(row.aliases)
                add(row.entityId.substringAfterLast('.'))
            }.map(::normalizeSync)
                .filter { it.isNotEmpty() }

            val bestTerm = terms.firstOrNull { term ->
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

    private fun KnowledgeChunkDomain.toResult(
        snippet: String,
        matchScore: Double,
        sourceId: String? = null,
        spoilerOverride: SpoilerLevel? = null,
        progressGateOverride: String? = progressGate,
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
        buildList {
            add(canonicalName)
            addAll(aliases)
            add(entityId.substringAfterLast('.'))
        }
            .map(::normalizeSync)
            .filter { it.isNotEmpty() }
            .firstOrNull { term ->
                normalizedQuery.contains(term) || term.contains(normalizedQuery)
            }

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

    private enum class NameMappingRequest {
        English,
        Chinese,
    }

    private data class TemplateAnswer(
        val text: String,
        val spoilerLevel: String,
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
        const val FTS_OVERFETCH_FACTOR = 3
        const val INTENT_STRONG_BOOST = 0.25
        const val INTENT_MEDIUM_BOOST = 0.16
        const val BROAD_TERM_PENALTY = 0.10
        val BROAD_NATURAL_TERMS = setOf(
            "怎么玩",
            "游戏",
            "这个游戏",
            "这游戏",
        )
    }
}
