package com.retrosprite.app.domain.models

enum class AnswerConfidence(val wireName: String) {
    High("high"),
    Medium("medium"),
    Low("low"),
}

enum class AnswerType(val wireName: String) {
    GameOverview("game_overview"),
    BeginnerGuide("beginner_guide"),
    TeamBuild("team_build"),
    Leveling("leveling"),
    NameMapping("name_mapping"),
    Location("location"),
    Usage("usage"),
    Mechanic("mechanic"),
    RouteHint("route_hint"),
    Strategy("strategy"),
    Production("production"),
    NoEvidence("no_evidence"),
    UnknownOrOutOfScope("unknown_or_out_of_scope"),
}

enum class AnswerNextAction(val label: String) {
    MoreSpecific("更明确"),
    DirectAnswer("直接答案"),
    ViewSources("查看来源"),
    MarkIncorrect("这不对"),
}

data class AnswerResult(
    val answerShort: String,
    val answerDetail: String,
    val sources: List<String> = emptyList(),
    val confidence: AnswerConfidence = AnswerConfidence.Low,
    val answerType: AnswerType = AnswerType.UnknownOrOutOfScope,
    val spoilerLevelUsed: SpoilerLevel = SpoilerLevel.LIGHT,
    val nextActions: List<AnswerNextAction> = emptyList(),
    val suggestedQuestions: List<String> = emptyList(),
) {
    val textWithSources: String
        get() {
            val cleanSources = sources
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            return buildString {
                append(answerDetail)
                if (cleanSources.isNotEmpty()) {
                    appendLine()
                    append("来源：${cleanSources.joinToString(", ")}")
                }
                suggestedQuestions.cleanSuggestedQuestions()
                    .takeIf { it.isNotEmpty() && !answerDetail.containsSuggestedQuestionBlock() }
                    ?.let { questions ->
                    appendLine()
                    appendLine("你还可以问：")
                    questions.forEachIndexed { index, question ->
                        if (index > 0) appendLine()
                        append("· $question")
                    }
                }
            }
        }

    companion object {
        fun fromText(
            text: String,
            sources: List<String> = emptyList(),
            confidence: AnswerConfidence = AnswerConfidence.Low,
            answerType: AnswerType = AnswerType.UnknownOrOutOfScope,
            spoilerLevelUsed: SpoilerLevel = SpoilerLevel.LIGHT,
            nextActions: List<AnswerNextAction> = emptyList(),
            suggestedQuestions: List<String> = emptyList(),
        ): AnswerResult = AnswerResult(
            answerShort = text.shortAnswer(),
            answerDetail = text,
            sources = sources.cleanSourceIds(),
            confidence = confidence,
            answerType = answerType,
            spoilerLevelUsed = spoilerLevelUsed,
            nextActions = nextActions,
            suggestedQuestions = suggestedQuestions.cleanSuggestedQuestions(),
        )
    }
}

internal fun String.shortAnswer(maxChars: Int = 72): String {
    val clean = trim()
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        .orEmpty()
    if (clean.length <= maxChars) return clean
    val sentenceEnd = listOf("。", "！", "？", ".", "!", "?")
        .map { clean.indexOf(it) }
        .filter { it in 1 until maxChars }
        .minOrNull()
    val sentence = sentenceEnd?.let { clean.take(it + 1) } ?: clean.take(maxChars).trimEnd()
    return if (sentence.length < clean.length && !sentence.endsWithAnySentencePunctuation()) {
        "$sentence..."
    } else {
        sentence
    }
}

private fun String.endsWithAnySentencePunctuation(): Boolean =
    endsWith("。") || endsWith("！") || endsWith("？") ||
        endsWith(".") || endsWith("!") || endsWith("?")

private fun String.containsSuggestedQuestionBlock(): Boolean =
    contains("你可以这样问：") || contains("你还可以问：")

private fun List<String>.cleanSourceIds(): List<String> =
    map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

private fun List<String>.cleanSuggestedQuestions(): List<String> =
    map { it.trim().ensureQuestionMark() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(3)

private fun String.ensureQuestionMark(): String {
    if (isBlank()) return ""
    return if (endsWith("？") || endsWith("?")) this else "$this？"
}
