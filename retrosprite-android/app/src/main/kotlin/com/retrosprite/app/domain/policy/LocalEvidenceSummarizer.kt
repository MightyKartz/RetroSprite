package com.retrosprite.app.domain.policy

import com.retrosprite.app.domain.models.Evidence

class LocalEvidenceSummarizer {

    fun summarize(evidence: List<Evidence>): LocalEvidenceSummary {
        val cleanEvidence = evidence
            .filter { it.snippet.isNotBlank() }
            .distinctBy { it.sourceId to it.snippet.cleanSnippet() }
            .take(MAX_EVIDENCE)
        val snippets = cleanEvidence
            .map { it.snippet.cleanSnippet() }
            .distinct()
        val detail = snippets.joinToString(separator = "")
        return LocalEvidenceSummary(
            answerShort = snippets.firstOrNull().orEmpty(),
            answerDetail = detail,
            sources = cleanEvidence.map { it.sourceId }
                .filter { it.isNotBlank() }
                .distinct(),
        )
    }

    private fun String.cleanSnippet(): String =
        trim()
            .replace(WHITESPACE, " ")

    private companion object {
        val WHITESPACE = Regex("\\s+")
        const val MAX_EVIDENCE = 3
    }
}

data class LocalEvidenceSummary(
    val answerShort: String,
    val answerDetail: String,
    val sources: List<String>,
)
