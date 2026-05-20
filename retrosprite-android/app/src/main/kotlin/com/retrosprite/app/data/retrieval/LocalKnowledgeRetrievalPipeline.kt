package com.retrosprite.app.data.retrieval

import com.retrosprite.app.data.models.KnowledgeChunkDomain
import com.retrosprite.app.data.repository.KnowledgeRepository
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

    override suspend fun retrieve(query: RetrievalQuery): List<RetrievalResult> {
        val gameId = query.gameId?.trim().orEmpty()
        val normalizedQuery = query.normalizedQuery.trim()
        if (gameId.isEmpty() || normalizedQuery.isEmpty()) return emptyList()

        val allRows = knowledgeRepository.listByGame(gameId)
            .filter { it.allowedFor(query) }

        val candidates = buildList {
            addAll(templateMatches(allRows, normalizedQuery, query))
            addAll(aliasAndEntityMatches(allRows, normalizedQuery))
            addAll(ftsMatches(gameId, normalizedQuery, query))
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
        raw.trim()
            .replace(WHITESPACE, " ")
            .lowercase()

    private fun templateMatches(
        rows: List<KnowledgeChunkDomain>,
        normalizedQuery: String,
        query: RetrievalQuery,
    ): List<RetrievalResult> =
        rows.flatMap { row ->
            row.answerTemplates.mapNotNull { rawTemplate ->
                val template = runCatching { JSON.parseToJsonElement(rawTemplate).jsonObject }
                    .getOrNull()
                    ?: return@mapNotNull null
                val answer = template.stringOrNull("answer")?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val patterns = template.arrayStrings("question_patterns")
                val templateSpoiler = template.stringOrNull("spoiler_level") ?: row.spoilerLevel
                if (!gkpSpoilerAllowed(templateSpoiler, query.spoilerLevel)) return@mapNotNull null
                val matched = patterns.any { pattern ->
                    val normalizedPattern = normalizeSync(pattern)
                    normalizedPattern.isNotEmpty() && (
                        normalizedQuery.contains(normalizedPattern) ||
                            normalizedPattern.contains(normalizedQuery)
                        )
                }
                if (!matched) return@mapNotNull null
                row.toResult(
                    snippet = answer,
                    matchScore = TEMPLATE_MATCH_SCORE,
                    sourceId = template.arrayStrings("source_refs").firstOrNull()
                )
            }
        }

    private fun aliasAndEntityMatches(
        rows: List<KnowledgeChunkDomain>,
        normalizedQuery: String,
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
            row.toResult(
                snippet = row.descriptionShort,
                matchScore = ALIAS_MATCH_SCORE + (termBoost * 0.10),
            )
        }

    private suspend fun ftsMatches(
        gameId: String,
        normalizedQuery: String,
        query: RetrievalQuery,
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
                    matchScore = FTS_MATCH_SCORE,
                )
            }
            .toList()

    private fun KnowledgeChunkDomain.toResult(
        snippet: String,
        matchScore: Double,
        sourceId: String? = null,
    ): RetrievalResult {
        val evidenceSpoiler = spoilerLevel.toDomainSpoiler()
        return RetrievalResult(
            entityId = entityId,
            canonicalName = canonicalName,
            evidence = listOf(
                Evidence(
                    sourceId = sourceId ?: sourceRefs.firstOrNull() ?: entityId,
                    snippet = snippet,
                    score = matchScore,
                    spoilerLevel = evidenceSpoiler,
                    progressGate = progressGate,
                )
            ),
            confidence = (confidence.baseConfidence() * 0.60 + matchScore * 0.40)
                .coerceIn(0.0, 1.0),
        )
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

    private fun JsonObject.stringOrNull(name: String): String? {
        val value = this[name] ?: return null
        if (value is JsonNull) return null
        return (value as? JsonPrimitive)?.contentOrNull
    }

    private fun normalizeSync(value: String): String =
        value.trim()
            .replace(WHITESPACE, " ")
            .lowercase()

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val JSON = Json {
            ignoreUnknownKeys = true
            isLenient = false
        }

        const val TEMPLATE_MATCH_SCORE = 1.0
        const val ALIAS_MATCH_SCORE = 0.82
        const val FTS_MATCH_SCORE = 0.66
        const val FTS_OVERFETCH_FACTOR = 3
    }
}
