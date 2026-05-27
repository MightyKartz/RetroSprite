package com.retrosprite.app.screen.translation

class ScreenTranslationIntentClassifier {

    fun isScreenTranslationRequest(transcript: String): Boolean {
        return normalizeScreenTranslationRequest(transcript) != null
    }

    fun normalizeScreenTranslationRequest(transcript: String): String? {
        val cleanTranscript = transcript.trim()
        val normalized = transcript
            .normalizedForScreenTranslationMatch()

        if (normalized.isBlank()) return null
        if (normalized.withoutSpaces() in tailDroppedChineseTriggers) {
            return CANONICAL_CHINESE_TRANSLATION_REQUEST
        }

        return cleanTranscript.takeIf {
            chineseTriggers.any { trigger -> normalized.contains(trigger) } ||
                englishTriggers.any { trigger -> normalized.contains(trigger) }
        }
    }

    private companion object {
        const val CANONICAL_CHINESE_TRANSLATION_REQUEST = "翻译一下"

        val chineseTriggers = listOf(
            "翻译",
            "翻一下",
            "读一下",
            "念一下",
            "这是什么意思",
            "这段话是什么意思",
            "这句话啥意思",
            "啥意思",
        )

        val tailDroppedChineseTriggers = setOf(
            "翻译一",
            "请翻译一",
            "帮我翻译一",
            "帮忙翻译一",
            "给我翻译一",
            "麻烦翻译一",
        )

        val englishTriggers = listOf(
            "translate this",
            "read this",
            "what does this mean",
            "what is this saying",
        )
    }
}

private fun String.normalizedForScreenTranslationMatch(): String =
    lowercase()
        .filter { it.isLetterOrDigit() || it == ' ' }
        .replace(Regex("\\s+"), " ")
        .trim()

private fun String.withoutSpaces(): String = filterNot { it == ' ' }
