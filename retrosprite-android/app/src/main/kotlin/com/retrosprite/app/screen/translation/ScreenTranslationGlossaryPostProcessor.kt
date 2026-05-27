package com.retrosprite.app.screen.translation

class ScreenTranslationGlossaryPostProcessor {

    fun apply(text: String, glossary: ScreenTranslationGlossary?): String {
        if (glossary == null || text.isBlank()) return text
        return glossary.terms
            .filter { it.source.isNotBlank() && it.target.isNotBlank() && it.source != it.target }
            .sortedByDescending { it.source.length }
            .fold(text) { current, term ->
                val pattern = Regex(
                    pattern = "(?i)(?<![A-Za-z0-9])${Regex.escape(term.source)}(?![A-Za-z0-9])",
                )
                current.replace(pattern, term.target)
            }
    }
}
