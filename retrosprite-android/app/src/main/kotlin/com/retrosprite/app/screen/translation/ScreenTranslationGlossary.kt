package com.retrosprite.app.screen.translation

data class ScreenTranslationGlossaryTerm(
    val source: String,
    val target: String,
    val category: String,
)

data class ScreenTranslationGlossary(
    val gameId: String,
    val displayName: String,
    val terms: List<ScreenTranslationGlossaryTerm>,
)

data class ScreenTranslationContext(
    val label: String = "",
    val glossary: ScreenTranslationGlossary? = null,
)
