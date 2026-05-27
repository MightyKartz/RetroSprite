package com.retrosprite.app.screen.translation

data class ScreenTranslationResult(
    val translatedText: String,
    val pages: List<String>,
    val providerName: String,
    val model: String,
)
