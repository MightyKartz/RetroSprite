package com.retrosprite.app.screen.translation

class ScreenTranslationFormatter(
    private val maxCharsPerPage: Int = 220,
) {

    fun format(translatedText: String): List<String> {
        val clean = translatedText
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()

        if (clean.isBlank()) return emptyList()
        if (clean.length <= maxCharsPerPage) return listOf(clean)

        val sentences = clean.split(sentenceBoundaryRegex)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val pages = mutableListOf<String>()
        val current = StringBuilder()
        for (sentence in sentences) {
            val next = if (current.isEmpty()) sentence else current.toString() + sentence
            if (next.length > maxCharsPerPage && current.isNotEmpty()) {
                pages += current.toString()
                current.clear()
            }
            if (sentence.length > maxCharsPerPage) {
                sentence.chunked(maxCharsPerPage).forEach { chunk ->
                    if (current.isNotEmpty()) {
                        pages += current.toString()
                        current.clear()
                    }
                    pages += chunk
                }
            } else {
                current.append(sentence)
            }
        }
        if (current.isNotEmpty()) pages += current.toString()
        return pages
    }

    private companion object {
        val sentenceBoundaryRegex = Regex("(?<=[。！？!?])")
    }
}
