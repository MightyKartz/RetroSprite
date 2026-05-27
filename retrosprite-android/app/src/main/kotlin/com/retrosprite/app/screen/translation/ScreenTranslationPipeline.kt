package com.retrosprite.app.screen.translation

interface ScreenTranslationPipeline {
    suspend fun translateCurrentScreen(
        imageBase64: String,
        context: ScreenTranslationContext = ScreenTranslationContext(),
    ): ScreenTranslationResult
}

interface ScreenTranslationProvider {
    val providerName: String
    val model: String
    suspend fun translateScreenshotToChinese(
        imageBase64: String,
        context: ScreenTranslationContext = ScreenTranslationContext(),
    ): String
}

class ApiScreenTranslationPipeline(
    private val provider: ScreenTranslationProvider,
    private val formatter: ScreenTranslationFormatter = ScreenTranslationFormatter(),
    private val postProcessor: ScreenTranslationGlossaryPostProcessor =
        ScreenTranslationGlossaryPostProcessor(),
    private val structuredResponseParser: ScreenTranslationStructuredResponseParser =
        ScreenTranslationStructuredResponseParser(postProcessor),
) : ScreenTranslationPipeline {

    override suspend fun translateCurrentScreen(
        imageBase64: String,
        context: ScreenTranslationContext,
    ): ScreenTranslationResult {
        if (imageBase64.isBlank()) {
            return fixedFailure("当前热键请求没有截图，无法翻译画面。")
        }

        val rawText = provider.translateScreenshotToChinese(imageBase64, context).trim()
        val translated = structuredResponseParser.parse(rawText, context.glossary)
            ?: postProcessor.apply(
                text = rawText,
                glossary = context.glossary,
            )
        if (translated.isBlank()) {
            return fixedFailure("翻译结果为空，请稍后再试。")
        }

        val pages = formatter.format(translated)
        return ScreenTranslationResult(
            translatedText = translated,
            pages = pages.ifEmpty { listOf(translated) },
            providerName = provider.providerName,
            model = provider.model,
        )
    }

    private fun fixedFailure(message: String): ScreenTranslationResult =
        ScreenTranslationResult(
            translatedText = message,
            pages = listOf(message),
            providerName = provider.providerName,
            model = provider.model,
        )
}
