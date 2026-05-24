package com.retrosprite.app.data.retrieval

import com.retrosprite.app.data.models.KnowledgeChunkDomain
import com.retrosprite.app.domain.intent.normalizeNaturalQuestion
import com.retrosprite.app.domain.models.AnswerType
import com.retrosprite.app.domain.models.SpoilerLevel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.math.max

internal data class TemplateRetrievalDocument(
    val documentId: String,
    val entityId: String,
    val canonicalName: String,
    val entityType: String,
    val intent: String?,
    val questionPatterns: List<String>,
    val aliases: List<String>,
    val conceptTags: Set<TemplateConceptTag>,
    val selectedAnswer: String,
    val sourceRefs: List<String>,
    val spoilerLevel: SpoilerLevel,
    val sourceOrder: Int,
    val searchText: String,
)

internal data class TemplateDocumentScore(
    val document: TemplateRetrievalDocument,
    val score: Double,
    val exactPattern: Boolean,
    val patternSimilarity: Double,
    val conceptOverlap: Double,
    val aliasSimilarity: Double,
    val intentCompatible: Boolean,
    val entityAnchored: Boolean,
    val strongPatternMatch: Boolean,
    val requiresEntityAnchor: Boolean,
    val requiresSpecificConceptAnchor: Boolean,
) {
    fun passesThreshold(): Boolean {
        val passesEntityAnchor = !requiresEntityAnchor || entityAnchored || strongPatternMatch
        val passesSpecificConceptAnchor = !requiresSpecificConceptAnchor || entityAnchored || strongPatternMatch
        val passesScore = score >= DIRECT_ACCEPT_THRESHOLD ||
            (score >= CONDITIONAL_ACCEPT_THRESHOLD && intentCompatible && conceptOverlap > 0.0)
        return passesEntityAnchor && passesSpecificConceptAnchor && passesScore
    }

    private companion object {
        const val DIRECT_ACCEPT_THRESHOLD = 0.72
        const val CONDITIONAL_ACCEPT_THRESHOLD = 0.45
    }
}

internal class TemplateDocumentMatcher {
    fun bestMatch(
        query: String,
        queryIntent: AnswerType,
        rows: List<KnowledgeChunkDomain>,
        tolerance: SpoilerLevel,
        progressGate: String? = null,
    ): TemplateDocumentScore? {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return null
        val queryConcepts = TemplateConceptExtractor.extract(normalizedQuery)
        return buildDocuments(rows, tolerance, progressGate)
            .map { score(normalizedQuery, queryIntent, queryConcepts, it) }
            .filter { it.passesThreshold() }
            .maxWithOrNull(
                compareBy<TemplateDocumentScore> { it.score }
                    .thenBy { it.exactPattern }
                    .thenByDescending { -it.document.sourceOrder }
            )
    }

    private fun buildDocuments(
        rows: List<KnowledgeChunkDomain>,
        tolerance: SpoilerLevel,
        progressGate: String?,
    ): List<TemplateRetrievalDocument> =
        rows.flatMapIndexed { rowIndex, row ->
            row.answerTemplates.mapIndexedNotNull { templateIndex, rawTemplate ->
                val template = runCatching { JSON.parseToJsonElement(rawTemplate).jsonObject }
                    .getOrNull()
                    ?: return@mapIndexedNotNull null
                val selectedAnswer = TemplateAnswerSelector.select(template, tolerance)
                    ?: return@mapIndexedNotNull null
                if (!gkpSpoilerAllowed(selectedAnswer.spoilerLevel, tolerance)) {
                    return@mapIndexedNotNull null
                }
                val selectedSpoiler = selectedAnswer.spoilerLevel.toDomainSpoiler()
                if (!progressGateAllowed(row.progressGate, progressGate) &&
                    selectedSpoiler != SpoilerLevel.LIGHT
                ) {
                    return@mapIndexedNotNull null
                }
                val patterns = template.templateArrayStrings("question_patterns")
                val intent = template.templateStringOrNull("intent")
                val sourceRefs = template.templateArrayStrings("source_refs").ifEmpty { row.sourceRefs }
                val conceptTags = TemplateConceptExtractor.extract(
                    row.canonicalName,
                    row.aliases.joinToString(" "),
                    patterns.joinToString(" "),
                    intent.orEmpty(),
                )
                TemplateRetrievalDocument(
                    documentId = stableDocumentId(row.entityId, template, intent, templateIndex),
                    entityId = row.entityId,
                    canonicalName = row.canonicalName,
                    entityType = row.entityType,
                    intent = intent,
                    questionPatterns = patterns,
                    aliases = row.aliases,
                    conceptTags = conceptTags,
                    selectedAnswer = selectedAnswer.text,
                    sourceRefs = sourceRefs,
                    spoilerLevel = selectedSpoiler,
                    sourceOrder = rowIndex * SOURCE_ORDER_ROW_STRIDE + templateIndex,
                    searchText = buildSearchText(row, patterns, intent, selectedAnswer.text),
                )
            }
        }

    private fun score(
        normalizedQuery: String,
        queryIntent: AnswerType,
        queryConcepts: Set<TemplateConceptTag>,
        document: TemplateRetrievalDocument,
    ): TemplateDocumentScore {
        val normalizedPatterns = document.questionPatterns.map(::normalize).filter { it.isNotEmpty() }
        val strongPatternMatch = normalizedPatterns.any { pattern ->
            normalizedQuery == pattern || normalizedQuery.contains(pattern)
        }
        val exactPattern = normalizedPatterns.any { pattern ->
            normalizedQuery.contains(pattern) || pattern.contains(normalizedQuery)
        }
        val patternSimilarity = document.questionPatterns.maxOfOrNull { pattern ->
            phraseSimilarity(normalizedQuery, normalize(pattern))
        } ?: 0.0
        val conceptOverlap = conceptOverlap(queryConcepts, document.conceptTags)
        val aliasSimilarity = (document.aliases + document.canonicalName).maxOfOrNull { alias ->
            phraseSimilarity(normalizedQuery, normalize(alias))
        } ?: 0.0
        val entityAnchored = entityAnchored(normalizedQuery, document)
        val requiresEntityAnchor = requiresEntityAnchor(queryIntent, document)
        val requiresSpecificConceptAnchor = requiresSpecificConceptAnchor(queryConcepts, document)
        val answerSimilarity = phraseSimilarity(normalizedQuery, normalize(document.selectedAnswer))
        val intentCompatible = intentCompatible(queryIntent, document.intent)
        val intentKnownMismatch = document.intent != null &&
            queryIntent != AnswerType.UnknownOrOutOfScope &&
            queryIntent.wireName != document.intent
        val rawScore = (if (exactPattern) 1.0 else 0.0) +
            conceptOverlap * CONCEPT_WEIGHT +
            patternSimilarity * PATTERN_WEIGHT +
            aliasSimilarity * ALIAS_WEIGHT +
            answerSimilarity * ANSWER_WEIGHT +
            (if (document.intent == queryIntent.wireName) INTENT_MATCH_BONUS else 0.0) -
            (if (intentKnownMismatch) INTENT_MISMATCH_PENALTY else 0.0)
        return TemplateDocumentScore(
            document = document,
            score = rawScore.coerceIn(0.0, 1.0),
            exactPattern = exactPattern,
            patternSimilarity = patternSimilarity,
            conceptOverlap = conceptOverlap,
            aliasSimilarity = aliasSimilarity,
            intentCompatible = intentCompatible,
            entityAnchored = entityAnchored,
            strongPatternMatch = strongPatternMatch,
            requiresEntityAnchor = requiresEntityAnchor,
            requiresSpecificConceptAnchor = requiresSpecificConceptAnchor,
        )
    }

    private fun stableDocumentId(
        entityId: String,
        template: kotlinx.serialization.json.JsonObject,
        intent: String?,
        index: Int,
    ): String {
        val templateId = template.templateStringOrNull("template_id")
        return when {
            !templateId.isNullOrBlank() -> "$entityId#$templateId"
            !intent.isNullOrBlank() -> "$entityId#$intent#$index"
            else -> "$entityId#template#$index"
        }
    }

    private fun buildSearchText(
        row: KnowledgeChunkDomain,
        patterns: List<String>,
        intent: String?,
        answer: String,
    ): String =
        listOf(
            row.canonicalName,
            row.aliases.joinToString(" "),
            patterns.joinToString(" "),
            intent.orEmpty(),
            answer,
        ).joinToString(" ").normalizeNaturalQuestion()

    private fun intentCompatible(queryIntent: AnswerType, documentIntent: String?): Boolean =
        documentIntent == null ||
            queryIntent == AnswerType.UnknownOrOutOfScope ||
            queryIntent.wireName == documentIntent

    private fun requiresEntityAnchor(
        queryIntent: AnswerType,
        document: TemplateRetrievalDocument,
    ): Boolean {
        if (document.entityType.lowercase() !in CONCRETE_USAGE_ENTITY_TYPES) return false
        return queryIntent == AnswerType.Usage ||
            document.intent == AnswerType.Usage.wireName ||
            TemplateConceptTag.ItemUsage in document.conceptTags
    }

    private fun requiresSpecificConceptAnchor(
        queryConcepts: Set<TemplateConceptTag>,
        document: TemplateRetrievalDocument,
    ): Boolean =
        TemplateConceptTag.StatMechanic in queryConcepts &&
            TemplateConceptTag.StatMechanic !in document.conceptTags

    private fun entityAnchored(
        normalizedQuery: String,
        document: TemplateRetrievalDocument,
    ): Boolean =
        document.entityAnchorTerms()
            .any { term -> normalizedQuery.contains(term) }

    private fun TemplateRetrievalDocument.entityAnchorTerms(): List<String> =
        buildList {
            add(canonicalName)
            canonicalName.split("/", "／", "-", " - ").forEach(::add)
            addAll(aliases)
        }
            .map(::normalize)
            .filter { it.length >= MIN_ENTITY_ANCHOR_LENGTH }
            .distinct()

    private fun conceptOverlap(
        queryConcepts: Set<TemplateConceptTag>,
        documentConcepts: Set<TemplateConceptTag>,
    ): Double {
        if (queryConcepts.isEmpty() || documentConcepts.isEmpty()) return 0.0
        return queryConcepts.intersect(documentConcepts).size.toDouble() / queryConcepts.size
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

    private fun normalize(value: String): String =
        value.normalizeNaturalQuestion()

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

    private companion object {
        val JSON = Json {
            ignoreUnknownKeys = true
            isLenient = false
        }
        const val SOURCE_ORDER_ROW_STRIDE = 1000
        const val CONCEPT_WEIGHT = 0.35
        const val PATTERN_WEIGHT = 0.35
        const val ALIAS_WEIGHT = 0.12
        const val ANSWER_WEIGHT = 0.08
        const val INTENT_MATCH_BONUS = 0.12
        const val INTENT_MISMATCH_PENALTY = 0.25
        const val MIN_ENTITY_ANCHOR_LENGTH = 2
        val CONCRETE_USAGE_ENTITY_TYPES = setOf(
            "item",
            "equipment",
            "weapon",
            "armor",
            "consumable",
        )
    }
}
