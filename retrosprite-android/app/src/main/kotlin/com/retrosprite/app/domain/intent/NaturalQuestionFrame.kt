package com.retrosprite.app.domain.intent

import com.retrosprite.app.domain.models.AnswerType
import kotlinx.serialization.Serializable

@Serializable
data class NaturalQuestionFrame(
    val answerType: AnswerType = AnswerType.UnknownOrOutOfScope,
    val intentDetail: String = answerType.wireName,
    val subjectTerms: List<String> = emptyList(),
    val asksCurrentProgress: Boolean = false,
    val asksSpoilerEscalation: Boolean = false,
    val needsProgressContext: Boolean = false,
    val normalizedQuestion: String = "",
)

object NaturalQuestionFrameParser {

    fun parse(rawQuestion: String): NaturalQuestionFrame {
        val normalized = rawQuestion.normalizeNaturalQuestion()
        val answerType = QuestionIntentClassifier.classifyNormalized(normalized)
        val asksCurrentProgress = normalized.containsAny(CURRENT_PROGRESS_CUES)
        val asksSpoilerEscalation = normalized.containsAny(SPOILER_ESCALATION_CUES)
        val needsProgressContext = when (answerType) {
            AnswerType.TeamBuild -> asksCurrentProgress
            AnswerType.RouteHint -> true
            else -> false
        }

        return NaturalQuestionFrame(
            answerType = answerType,
            intentDetail = answerType.wireName,
            subjectTerms = extractSubjectTerms(normalized),
            asksCurrentProgress = asksCurrentProgress,
            asksSpoilerEscalation = asksSpoilerEscalation,
            needsProgressContext = needsProgressContext,
            normalizedQuestion = normalized,
        )
    }

    private fun extractSubjectTerms(normalized: String): List<String> =
        normalized.split(WHITESPACE)
            .map { it.trim() }
            .filter { it.length >= 2 }
            .filterNot { it in QUESTION_STOPWORDS }
            .distinct()

    private val CURRENT_PROGRESS_CUES = listOf(
        "现在",
        "当前",
        "目前",
        "我这队",
        "刚到",
        "卡住",
    )

    private val SPOILER_ESCALATION_CUES = listOf(
        "直接告诉我",
        "直接答案",
        "具体位置",
        "不怕剧透",
    )

    private val QUESTION_STOPWORDS = setOf(
        "这个",
        "游戏",
        "怎么玩",
        "哪些",
        "角色",
        "适合",
        "培养",
    )
}

internal fun String.normalizeNaturalQuestion(): String =
    trim()
        .lowercase()
        .replace(PUNCTUATION, " ")
        .replace(WHITESPACE, " ")
        .trim()
        .normalizeObservedAsrConfusions()

internal fun String.normalizeObservedAsrConfusions(): String =
    replace("轉職", "转职")
        .replace("转直", "转职")
        .replace("软直", "转职")
        .replace("专职", "转职")
        .replace("接受他几部", "什么时候转职")
        .replace("那这些角色", "哪些角色")
        .replace("那这些人物", "哪些角色")
        .replace("那这些队员", "哪些角色")
        .replace("哪先角色", "哪些角色")
        .replace("哪先人物", "哪些角色")
        .replace("哪先队员", "哪些角色")
        .replace("那些角色", "哪些角色")
        .replace("那些人物", "哪些角色")
        .replace("那些队员", "哪些角色")
        .replace("哪些人物", "哪些角色")
        .replace("哪些队员", "哪些角色")
        .replace("对于我怎么搭配", "队伍怎么搭配")
        .replace("对我怎么搭配", "队伍怎么搭配")
        .replace("直练", "值得练")

internal fun String.containsAny(terms: Iterable<String>): Boolean =
    terms.any { contains(it.lowercase()) }

internal fun String.containsAny(vararg terms: String): Boolean =
    containsAny(terms.asIterable())

private val PUNCTUATION = Regex("[\\p{Punct}，。？！、；：]+")
private val WHITESPACE = Regex("\\s+")
