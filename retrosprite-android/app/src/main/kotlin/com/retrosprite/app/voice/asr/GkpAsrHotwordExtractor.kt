package com.retrosprite.app.voice.asr

import com.retrosprite.app.data.models.KnowledgeChunkDomain

class GkpAsrHotwordExtractor {

    fun extract(
        gameId: String,
        packVersion: String,
        rows: List<KnowledgeChunkDomain>,
    ): AsrBiasingProfile {
        val entries = buildList {
            rows.filter { it.gameId == gameId }.forEach { row ->
                row.canonicalName.split("/", "／").forEach { name ->
                    addHotword(name, scoreForCanonical(row), AsrHotwordSource.CanonicalName)
                }
                row.aliases.forEach { alias ->
                    addHotword(alias, scoreForAlias(row, alias), AsrHotwordSource.Alias)
                }
                row.answerTemplates.forEach { template ->
                    templatePatternTerms(template).forEach { pattern ->
                        addHotword(pattern, TEMPLATE_PATTERN_SCORE, AsrHotwordSource.TemplatePattern)
                    }
                }
            }
        }
        return AsrBiasingProfile(gameId = gameId, packVersion = packVersion, entries = entries)
    }

    private fun MutableList<AsrHotwordEntry>.addHotword(
        term: String,
        score: Float,
        source: AsrHotwordSource,
    ) {
        val cleaned = term.cleanHotwordTerm()
        if (cleaned.isBlank()) return
        if (cleaned.length > MAX_TERM_LENGTH) return
        if (cleaned in GENERIC_TERMS) return
        add(AsrHotwordEntry(term = cleaned, score = score, source = source))
    }

    private fun scoreForCanonical(row: KnowledgeChunkDomain): Float =
        when (row.entityType) {
            "npc", "item", "location" -> CANONICAL_ENTITY_SCORE
            else -> CANONICAL_GENERIC_SCORE
        }

    private fun scoreForAlias(row: KnowledgeChunkDomain, alias: String): Float {
        val cleaned = alias.cleanHotwordTerm()
        val isPatchLikeChineseName = cleaned.any { it.code in CJK_RANGE } &&
            cleaned.length in 2..6 &&
            row.entityType in ENTITY_TYPES
        return when {
            isPatchLikeChineseName -> PATCH_NAME_SCORE
            row.entityType in ENTITY_TYPES -> ENTITY_ALIAS_SCORE
            else -> GENERIC_ALIAS_SCORE
        }
    }

    private fun templatePatternTerms(template: String): List<String> =
        QUESTION_PATTERNS.find(template)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { content ->
                QUOTED_STRING.findAll(content)
                    .map { it.groupValues[1] }
                    .map(::stripQuestionScaffold)
                    .filter { it.isNotBlank() }
                    .toList()
            }
            .orEmpty()

    private fun stripQuestionScaffold(pattern: String): String {
        val cleaned = pattern.cleanHotwordTerm()
        return QUESTION_SUFFIXES.fold(cleaned) { current, suffix ->
            current.removeSuffix(suffix).cleanHotwordTerm()
        }
    }

    companion object {
        const val PATCH_NAME_SCORE = 4.2f
        const val ENTITY_ALIAS_SCORE = 3.2f
        const val CANONICAL_ENTITY_SCORE = 3.0f
        const val CANONICAL_GENERIC_SCORE = 2.0f
        const val GENERIC_ALIAS_SCORE = 1.8f
        const val TEMPLATE_PATTERN_SCORE = 4.8f

        private const val MAX_TERM_LENGTH = 24
        private val CJK_RANGE = 0x4E00..0x9FFF
        private val ENTITY_TYPES = setOf("npc", "item", "location", "boss", "enemy")
        private val QUESTION_PATTERNS = Regex("\\\"question_patterns\\\"\\s*:\\s*\\[(.*?)\\]")
        private val QUOTED_STRING = Regex("\\\"([^\\\"]+)\\\"")
        private val QUESTION_SUFFIXES = listOf(
            "怎么用",
            "有什么用",
            "是谁",
            "是什么",
            "在哪里",
            "在哪",
            "给谁用",
            "值得练吗",
        )
        private val GENERIC_TERMS = setOf(
            "主角",
            "主人公",
            "治疗",
            "牧师",
            "骑士",
            "战士",
            "前排",
            "法师",
            "道具",
            "装备",
            "物品",
            "隐藏",
            "村庄",
            "城堡",
            "王国",
        )
    }
}
