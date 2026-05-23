package com.retrosprite.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.retrosprite.app.ui.viewmodel.EndpointStatusProvider
import com.retrosprite.app.ui.viewmodel.PendingQuestionProvider
import com.retrosprite.app.ui.viewmodel.PlayerQuestionProvider
import com.retrosprite.app.ui.viewmodel.RequestLogProvider
import com.retrosprite.app.ui.viewmodel.UiAnswerFeedback
import com.retrosprite.app.ui.viewmodel.UiEndpointStatus
import com.retrosprite.app.ui.viewmodel.UiPendingQuestionState
import com.retrosprite.app.ui.viewmodel.UiQuestionResult
import com.retrosprite.app.ui.viewmodel.UiRequestLogItem
import com.retrosprite.app.ui.viewmodel.UiSpoilerLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val endpoint: EndpointStatusProvider,
    private val playerQuestion: PlayerQuestionProvider,
    private val pendingQuestion: PendingQuestionProvider,
    private val requestLog: RequestLogProvider,
) : ViewModel() {

    val status: StateFlow<UiEndpointStatus> = endpoint.status
    val pendingQuestionState: StateFlow<UiPendingQuestionState> = pendingQuestion.state
    private val _askState = MutableStateFlow(HomeAskState())
    val askState: StateFlow<HomeAskState> = _askState.asStateFlow()

    init {
        viewModelScope.launch {
            requestLog.log.collect { items ->
                val latestContext = items.firstNotNullOfOrNull { it.toRetroArchContextOrNull() }
                val persistedTurns = items.mapNotNull { it.toRetroArchConversationTurnOrNull() }

                _askState.update { current ->
                    val shouldApply = latestContext != null &&
                        !current.labelManuallyEdited &&
                        !current.isAsking &&
                        current.label != latestContext.label
                    current.copy(
                        label = if (shouldApply && latestContext != null) latestContext.label else current.label,
                        latestRetroArchContext = latestContext ?: current.latestRetroArchContext,
                        conversationTurns = mergeConversationTurns(
                            persistedTurns = persistedTurns,
                            currentTurns = current.conversationTurns,
                        ),
                    )
                }
            }
        }
    }

    fun restart() {
        viewModelScope.launch { endpoint.restart() }
    }

    fun checkHealth() {
        viewModelScope.launch { endpoint.checkHealth() }
    }

    fun updateAskLabel(label: String) {
        _askState.update {
            it.copy(
                label = label,
                labelManuallyEdited = true,
                errorMessage = null,
                spoilerEscalationNotice = null,
                spoilerLevelOverride = null,
            )
        }
    }

    fun updateQuestion(question: String) {
        _askState.update {
            it.copy(
                question = question,
                errorMessage = null,
                spoilerEscalationNotice = null,
                spoilerLevelOverride = null,
            )
        }
    }

    fun applyQuestionDraft(draft: HomeQuestionDraft) {
        _askState.update {
            it.copy(
                question = draft.question,
                errorMessage = null,
                spoilerEscalationNotice = null,
                spoilerLevelOverride = null,
            )
        }
    }

    fun applyConversationTurn(turn: HomeConversationTurn) {
        _askState.update {
            it.copy(
                label = turn.label,
                question = turn.question,
                lastResult = turn.result,
                labelManuallyEdited = true,
                errorMessage = turn.result.errorMessage,
                spoilerEscalationNotice = null,
                spoilerLevelOverride = null,
            )
        }
    }

    fun applyConversationFollowUpDraft(
        turn: HomeConversationTurn,
        draft: HomeFollowUpDraft,
    ) {
        _askState.update {
            it.copy(
                label = turn.label,
                question = draft.question,
                lastResult = turn.result,
                labelManuallyEdited = true,
                errorMessage = null,
                spoilerEscalationNotice = draft.spoilerEscalationNotice,
                spoilerLevelOverride = draft.spoilerLevelOverride,
            )
        }
    }

    fun restoreLatestRetroArchContext() {
        _askState.update { current ->
            val context = current.latestRetroArchContext ?: return@update current
            current.copy(
                label = context.label,
                labelManuallyEdited = false,
                errorMessage = null,
                spoilerEscalationNotice = null,
                spoilerLevelOverride = null,
            )
        }
    }

    fun toggleAdvancedQuestionTools() {
        _askState.update {
            it.copy(advancedQuestionToolsExpanded = !it.advancedQuestionToolsExpanded)
        }
    }

    fun expandAdvancedQuestionTools() {
        _askState.update {
            if (it.advancedQuestionToolsExpanded) it else it.copy(advancedQuestionToolsExpanded = true)
        }
    }

    fun askQuestion() {
        val current = _askState.value
        val cleanQuestion = current.question.trim()
        val spoilerLevelOverride = current.spoilerLevelOverride
        if (cleanQuestion.isBlank() || current.isAsking) return

        val startedAt = System.currentTimeMillis()
        _askState.update {
            it.copy(
                isAsking = true,
                askStartedAtMillis = startedAt,
                errorMessage = null,
                spoilerEscalationNotice = null,
                spoilerLevelOverride = null,
            )
        }
        viewModelScope.launch {
            val result = runCatching {
                playerQuestion.ask(current.label, cleanQuestion, spoilerLevelOverride)
            }.getOrElse { error ->
                UiQuestionResult(
                    label = current.label.trim().ifBlank { DEFAULT_ASK_LABEL },
                    question = cleanQuestion,
                    answer = "",
                    ok = false,
                    timestampMillis = System.currentTimeMillis(),
                    errorMessage = error.message ?: "question_failed",
                    pipelineStage = "error",
                    durationMillis = System.currentTimeMillis() - startedAt,
                )
            }
            _askState.update {
                val turn = result.toConversationTurn()
                it.copy(
                    label = result.label,
                    question = cleanQuestion,
                    isAsking = false,
                    askStartedAtMillis = null,
                    lastResult = result,
                    errorMessage = result.errorMessage,
                    conversationTurns = (listOf(turn) + it.conversationTurns.filterNot { existing ->
                        existing.id == turn.id
                    }).take(MAX_CONVERSATION_TURNS),
                )
            }
        }
    }

    fun preparePendingQuestion() {
        val current = _askState.value
        val cleanQuestion = current.question.trim()
        val spoilerLevelOverride = current.spoilerLevelOverride
        if (cleanQuestion.isBlank() || current.isAsking) return

        _askState.update {
            it.copy(
                errorMessage = null,
                spoilerEscalationNotice = null,
                spoilerLevelOverride = null,
            )
        }
        viewModelScope.launch {
            pendingQuestion.prepare(
                label = current.label,
                question = cleanQuestion,
                spoilerLevelOverride = spoilerLevelOverride,
            )
        }
    }

    fun clearPendingQuestion() {
        viewModelScope.launch {
            pendingQuestion.clear()
        }
    }

    fun submitAnswerFeedback(feedback: UiAnswerFeedback) {
        val result = _askState.value.lastResult ?: return
        val requestLogId = result.requestLogId?.trim().orEmpty()
        if (!result.ok || requestLogId.isBlank()) return

        _askState.update { current ->
            val currentResult = current.lastResult ?: return@update current
            if (currentResult.requestLogId != result.requestLogId) {
                current
            } else {
                val updatedResult = currentResult.copy(feedback = feedback)
                current.copy(
                    lastResult = updatedResult,
                    conversationTurns = current.conversationTurns.map { turn ->
                        if (turn.id == updatedResult.conversationId()) {
                            turn.copy(result = updatedResult)
                        } else {
                            turn
                        }
                    },
                )
            }
        }
        viewModelScope.launch {
            runCatching { requestLog.submitFeedback(requestLogId, feedback) }
        }
    }

    companion object {
        fun factory(
            endpoint: EndpointStatusProvider,
            playerQuestion: PlayerQuestionProvider,
            pendingQuestion: PendingQuestionProvider,
            requestLog: RequestLogProvider,
        ) = viewModelFactory {
            initializer { HomeViewModel(endpoint, playerQuestion, pendingQuestion, requestLog) }
        }
    }
}

data class HomeAskState(
    val label: String = DEFAULT_ASK_LABEL,
    val question: String = "两个 2 怎么合并？",
    val isAsking: Boolean = false,
    val askStartedAtMillis: Long? = null,
    val lastResult: UiQuestionResult? = null,
    val latestRetroArchContext: HomeRetroArchContext? = null,
    val labelManuallyEdited: Boolean = false,
    val errorMessage: String? = null,
    val conversationTurns: List<HomeConversationTurn> = emptyList(),
    val spoilerEscalationNotice: String? = null,
    val spoilerLevelOverride: UiSpoilerLevel? = null,
    val advancedQuestionToolsExpanded: Boolean = false,
) {
    val questionDrafts: List<HomeQuestionDraft>
        get() = questionDraftsFor(label)
}

data class HomeConversationTurn(
    val id: String,
    val result: UiQuestionResult,
) {
    val label: String
        get() = result.label

    val question: String
        get() = result.question

    val answerPreview: String
        get() = if (result.ok) result.answer else result.errorMessage.orEmpty()

    val statusLabel: String
        get() = listOf(
            result.pipelineStage.uppercase(),
            "LLM ${result.llmStatus.uppercase()}",
        ).joinToString(" · ")

    val followUpDrafts: List<HomeFollowUpDraft>
        get() = followUpDraftsFor(result)
}

data class HomeFollowUpDraft(
    val id: String,
    val title: String,
    val question: String,
    val spoilerEscalationNotice: String? = null,
    val spoilerLevelOverride: UiSpoilerLevel? = null,
)

data class HomeQuestionDraft(
    val id: String,
    val title: String,
    val question: String,
)

data class HomeRetroArchContext(
    val label: String,
    val timestampMillis: Long,
    val paused: Boolean,
    val pipelineStage: String,
    val sourceIds: List<String>,
    val outputMode: String,
    val imageBytes: Int,
    val question: String? = null,
    val questionSource: String? = null,
) {
    val debugQuestion: String
        get() = questionDraftsFor(label).firstOrNull()?.question ?: DEFAULT_DEBUG_QUESTION

    val debugAskCurl: String
        get() = buildDebugAskCurl(label, debugQuestion)

    val isGkpDisabled: Boolean
        get() = pipelineStage == GKP_DISABLED_STAGE

    val hasGkpEvidence: Boolean
        get() = !isGkpDisabled && (sourceIds.isNotEmpty() || pipelineStage == "evidence")

    val gkpStatusLabel: String
        get() = when {
            isGkpDisabled -> "知识包已停用"
            hasGkpEvidence -> "知识包已命中"
            else -> "等待知识包匹配"
        }
}

private fun UiRequestLogItem.toRetroArchContextOrNull(): HomeRetroArchContext? {
    val cleanLabel = label
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.takeIf { !it.startsWith(DIAGNOSTIC_LABEL_PREFIX) }
        ?: return null

    val normalizedOutputMode = rawOutputMode.lowercase()
    if (
        !ok ||
        isDebug ||
        normalizedOutputMode.startsWith(APP_OUTPUT_PREFIX) ||
        normalizedOutputMode.startsWith(DEBUG_OUTPUT_PREFIX)
    ) {
        return null
    }

    return HomeRetroArchContext(
        label = cleanLabel,
        timestampMillis = timestampMillis,
        paused = paused,
        pipelineStage = pipelineStage,
        sourceIds = sourceIds,
        outputMode = rawOutputMode.ifBlank { outputMode.name.lowercase() },
        imageBytes = imageBytes,
        question = question,
        questionSource = questionSource,
    )
}

private fun UiRequestLogItem.toRetroArchConversationTurnOrNull(): HomeConversationTurn? {
    val cleanLabel = label
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.takeIf { !it.startsWith(DIAGNOSTIC_LABEL_PREFIX) }
        ?: return null
    val cleanQuestion = question
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: return null
    val normalizedOutputMode = rawOutputMode.lowercase()
    if (
        !ok ||
        isDebug ||
        normalizedOutputMode.startsWith(APP_OUTPUT_PREFIX) ||
        normalizedOutputMode.startsWith(DEBUG_OUTPUT_PREFIX)
    ) {
        return null
    }

    val answer = responseText.ifBlank { responsePreview }
    return UiQuestionResult(
        requestLogId = id,
        label = cleanLabel,
        question = cleanQuestion,
        answer = answer,
        ok = true,
        timestampMillis = timestampMillis,
        sourceIds = sourceIds,
        pipelineStage = pipelineStage,
        llmStatus = llmStatus,
        durationMillis = durationMillis,
        llmProvider = llmProvider,
        llmModel = llmModel,
        llmMaxTokens = llmMaxTokens,
        llmTimeoutMs = llmTimeoutMs,
        llmLatencyMs = llmLatencyMs,
        llmTokensIn = llmTokensIn,
        llmTokensOut = llmTokensOut,
        llmError = llmError,
        feedback = feedback,
    ).toConversationTurn()
}

private fun mergeConversationTurns(
    persistedTurns: List<HomeConversationTurn>,
    currentTurns: List<HomeConversationTurn>,
): List<HomeConversationTurn> =
    (persistedTurns + currentTurns)
        .distinctBy { it.id }
        .take(MAX_CONVERSATION_TURNS)

private const val DEFAULT_ASK_LABEL: String = "2048__"
private const val APP_OUTPUT_PREFIX: String = "app:"
private const val DEBUG_OUTPUT_PREFIX: String = "debug:"
private const val DIAGNOSTIC_LABEL_PREFIX: String = "diagnostic__"
private const val GKP_DISABLED_STAGE: String = "gkp_disabled"
private const val DEFAULT_DEBUG_QUESTION: String = "现在应该做什么？"
private const val MAX_CONVERSATION_TURNS: Int = 5
private const val DIRECT_ANSWER_SPOILER_NOTICE: String =
    "直接答案会主动提高剧透级别；提交前请确认你愿意看到更明确的信息。"

private fun UiQuestionResult.toConversationTurn(): HomeConversationTurn =
    HomeConversationTurn(id = conversationId(), result = this)

private fun UiQuestionResult.conversationId(): String =
    requestLogId?.trim()?.takeIf { it.isNotEmpty() } ?: "local-$timestampMillis"

private fun followUpDraftsFor(result: UiQuestionResult): List<HomeFollowUpDraft> {
    val baseQuestion = result.question.trim().ifBlank { DEFAULT_DEBUG_QUESTION }.compactForPrompt()
    return listOf(
        HomeFollowUpDraft(
            id = "clearer",
            title = "更明确",
            question = "基于刚才的问题「$baseQuestion」，请给我一个更明确但仍低剧透的提示。",
            spoilerLevelOverride = UiSpoilerLevel.Clear,
        ),
        HomeFollowUpDraft(
            id = "direct",
            title = "直接答案",
            question = "我确认可以接受更多剧透。请直接回答：$baseQuestion",
            spoilerEscalationNotice = DIRECT_ANSWER_SPOILER_NOTICE,
            spoilerLevelOverride = UiSpoilerLevel.Direct,
        ),
        HomeFollowUpDraft(
            id = "rephrase",
            title = "换个问法",
            question = "换一种问法：$baseQuestion。请说明我下一步应该确认什么信息。",
        ),
    )
}

private fun String.compactForPrompt(maxLength: Int = 72): String {
    val compact = lineSequence()
        .joinToString(" ") { it.trim() }
        .replace(Regex("\\s+"), " ")
        .trim()
    return if (compact.length <= maxLength) compact else compact.take(maxLength - 1) + "…"
}

private fun questionDraftsFor(label: String): List<HomeQuestionDraft> {
    val normalized = label.trim().lowercase().replace('-', '_').replace(' ', '_')
    return when {
        normalized.contains("2048") -> listOf(
            HomeQuestionDraft(
                id = "2048-merge",
                title = "合并规则",
                question = "两个 2 怎么合并？",
            ),
            HomeQuestionDraft(
                id = "2048-opening",
                title = "开局方向",
                question = "开局应该优先往哪个方向滑？",
            ),
            HomeQuestionDraft(
                id = "2048-rescue",
                title = "满盘救局",
                question = "棋盘快满了，怎么救？",
            ),
        )

        normalized.contains("relay_station") -> listOf(
            HomeQuestionDraft(
                id = "relay-blue-fuse",
                title = "蓝色保险丝",
                question = "蓝色保险丝在哪？",
            ),
            HomeQuestionDraft(
                id = "relay-route",
                title = "低剧透路线",
                question = "我现在应该先修什么？",
            ),
            HomeQuestionDraft(
                id = "relay-access",
                title = "门禁卡",
                question = "门禁卡怎么拿？",
            ),
        )

        else -> emptyList()
    }
}

private fun buildDebugAskCurl(label: String, question: String): String {
    val payload = """{"label":"${label.jsonEscaped()}","question":"${question.jsonEscaped()}","state":{}}"""
    return """
        curl -fsS -X POST 'http://127.0.0.1:18080/debug/ask?output=text' \
          -H 'Content-Type: application/json' \
          --data ${payload.shellSingleQuoted()}
    """.trimIndent()
}

private fun String.jsonEscaped(): String =
    replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

private fun String.shellSingleQuoted(): String =
    "'${replace("'", "'\"'\"'")}'"
