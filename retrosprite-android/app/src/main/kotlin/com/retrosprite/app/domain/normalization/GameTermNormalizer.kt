package com.retrosprite.app.domain.normalization

import com.retrosprite.app.data.models.KnowledgeChunkDomain
import kotlin.math.min

data class GameTermNormalizationResult(
    val rawQuestion: String,
    val normalizedQuestion: String,
    val applied: Boolean,
    val reason: String? = null,
    val matchedTerm: String? = null,
    val matchedEntityId: String? = null,
    val candidates: List<GameTermNormalizationCandidate> = emptyList(),
)

data class GameTermNormalizationCandidate(
    val rawSpan: String,
    val term: String,
    val entityId: String,
    val score: Double,
    val reason: String,
)

class GameTermNormalizer {

    fun normalize(
        rawQuestion: String,
        rows: List<KnowledgeChunkDomain>,
    ): GameTermNormalizationResult {
        val cleanQuestion = rawQuestion.trim()
        if (cleanQuestion.isBlank()) {
            return GameTermNormalizationResult(
                rawQuestion = rawQuestion,
                normalizedQuestion = cleanQuestion,
                applied = false,
            )
        }

        val terms = rows.flatMap { it.toTerms() }
            .distinctBy { it.term }
            .filter { it.term.length in MIN_TERM_CHARS..MAX_TERM_CHARS }
            .filterNot { it.term in STOP_TERMS }

        if (terms.any { cleanQuestion.contains(it.term) }) {
            return GameTermNormalizationResult(
                rawQuestion = rawQuestion,
                normalizedQuestion = cleanQuestion,
                applied = false,
            )
        }

        val candidates = terms.flatMap { term ->
            cleanQuestion.cjkWindows(term.term.length).mapNotNull { span ->
                score(span, term)?.let { scored ->
                    GameTermNormalizationCandidate(
                        rawSpan = span,
                        term = term.term,
                        entityId = term.entityId,
                        score = scored.score,
                        reason = scored.reason,
                    )
                }
            }
        }.sortedWith(compareByDescending<GameTermNormalizationCandidate> { it.score }.thenBy { it.term })

        val top = candidates.firstOrNull()
        if (top == null || top.score < MIN_AUTO_APPLY_SCORE) {
            return GameTermNormalizationResult(
                rawQuestion = rawQuestion,
                normalizedQuestion = cleanQuestion,
                applied = false,
                candidates = candidates.take(MAX_DIAGNOSTIC_CANDIDATES),
            )
        }

        val second = candidates.drop(1).firstOrNull()
        if (second != null && top.score - second.score < MIN_SCORE_GAP) {
            return GameTermNormalizationResult(
                rawQuestion = rawQuestion,
                normalizedQuestion = cleanQuestion,
                applied = false,
                candidates = candidates.take(MAX_DIAGNOSTIC_CANDIDATES),
            )
        }

        val normalized = cleanQuestion.replaceFirst(top.rawSpan, top.term)
        return GameTermNormalizationResult(
            rawQuestion = rawQuestion,
            normalizedQuestion = normalized,
            applied = normalized != cleanQuestion,
            reason = top.reason,
            matchedTerm = top.term,
            matchedEntityId = top.entityId,
            candidates = listOf(top),
        )
    }

    private fun score(rawSpan: String, term: Term): ScoredMatch? {
        if (rawSpan == term.term) {
            return ScoredMatch(EXACT_SCORE, "exact")
        }
        val rawPinyin = rawSpan.pinyinSignature()
        val termPinyin = term.term.pinyinSignature()
        if (rawSpan.length == term.term.length &&
            rawPinyin != null &&
            rawPinyin == termPinyin
        ) {
            return ScoredMatch(HOMOPHONE_SCORE, "homophone")
        }
        if (term.term.length >= MIN_EDIT_DISTANCE_TERM_CHARS &&
            rawSpan.length == term.term.length &&
            editDistance(rawSpan, term.term) == 1
        ) {
            return ScoredMatch(EDIT_DISTANCE_SCORE, "edit_distance")
        }
        return null
    }

    private fun KnowledgeChunkDomain.toTerms(): List<Term> =
        buildList {
            canonicalName.extractCjkTerms().forEach { add(Term(it, entityId)) }
            aliases.flatMap { it.extractCjkTerms() }.forEach { add(Term(it, entityId)) }
            entityId.substringAfterLast('.').extractCjkTerms().forEach { add(Term(it, entityId)) }
        }

    private fun String.extractCjkTerms(): List<String> =
        split('/', ',', '，', '(', ')', '（', '）', ' ', '\t')
            .map { it.trim() }
            .filter { it.isNotBlank() && it.all(Char::isCjk) }

    private fun String.cjkWindows(size: Int): List<String> {
        if (size <= 0 || length < size) return emptyList()
        return indices
            .filter { start -> start + size <= length }
            .map { start -> substring(start, start + size) }
            .filter { it.all(Char::isCjk) }
            .distinct()
    }

    private fun String.pinyinSignature(): String? {
        val parts = map { CJK_PINYIN[it] ?: return null }
        return parts.joinToString(separator = " ")
    }

    private fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val previous = IntArray(b.length + 1) { it }
        val current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(
                    min(current[j - 1] + 1, previous[j] + 1),
                    previous[j - 1] + cost,
                )
            }
            for (j in current.indices) previous[j] = current[j]
        }
        return previous[b.length]
    }

    private data class Term(val term: String, val entityId: String)
    private data class ScoredMatch(val score: Double, val reason: String)

    private companion object {
        const val EXACT_SCORE = 1.00
        const val HOMOPHONE_SCORE = 0.94
        const val EDIT_DISTANCE_SCORE = 0.88
        const val MIN_AUTO_APPLY_SCORE = 0.90
        const val MIN_SCORE_GAP = 0.08
        const val MIN_TERM_CHARS = 2
        const val MAX_TERM_CHARS = 8
        const val MIN_EDIT_DISTANCE_TERM_CHARS = 3
        const val MAX_DIAGNOSTIC_CANDIDATES = 5

        val STOP_TERMS = setOf("是谁", "在哪", "在哪里", "怎么用", "有什么用", "怎么办", "怎么打", "怎么练")

        val CJK_PINYIN = mapOf(
            '修' to "xiu",
            '伊' to "yi",
            '医' to "yi",
            '一' to "yi",
            '吉' to "ji",
            '布' to "bu",
            '步' to "bu",
            '皮' to "pi",
            '特' to "te",
            '气' to "qi",
            '合' to "he",
            '和' to "he",
            '之' to "zhi",
            '玉' to "yu",
            '精' to "jing",
            '灵' to "ling",
            '森' to "sen",
            '林' to "lin",
            '米' to "mi",
            '斯' to "si",
            '里' to "li",
            '鲁' to "lu",
            '路' to "lu",
            '银' to "yin",
        )
    }
}

private fun Char.isCjk(): Boolean = code in 0x4E00..0x9FFF
