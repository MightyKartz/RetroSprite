package com.retrosprite.app.ui.integration

internal object SherpaFinalTranscriptSelector {

    fun chooseFinalTranscript(
        finalText: String,
        latestPartialText: String,
    ): String {
        val cleanFinal = finalText.trim()
        val cleanPartial = latestPartialText.trim()
        return when {
            cleanFinal.isBlank() -> cleanPartial
            cleanPartial.isBlank() -> cleanFinal
            cleanFinal == cleanPartial -> cleanFinal
            else -> {
                val droppedCharacters = cleanPartial.length - cleanFinal.length
                if (
                    cleanPartial.startsWith(cleanFinal) &&
                    droppedCharacters in 1..2 &&
                    cleanPartial.hasQuestionTail()
                ) {
                    cleanPartial
                } else {
                    cleanFinal
                }
            }
        }
    }

    private fun String.hasQuestionTail(): Boolean =
        QUESTION_TAILS.any { endsWith(it) }

    private val QUESTION_TAILS = listOf(
        "什么",
        "是谁",
        "怎么用",
        "怎么过",
        "怎么打",
        "怎么办",
        "怎么理解",
        "有什么用",
        "好不好",
        "哪里",
        "哪儿",
        "多少",
        "多久",
        "区别",
        "吗",
        "么",
    )
}
