package com.retrosprite.app.domain.policy

import com.retrosprite.app.domain.models.AnswerDecision
import com.retrosprite.app.domain.models.AnswerConfidence
import com.retrosprite.app.domain.models.AnswerNextAction
import com.retrosprite.app.domain.models.AnswerResult
import com.retrosprite.app.domain.models.AnswerType
import com.retrosprite.app.domain.models.ComposedAnswer
import com.retrosprite.app.domain.models.LlmCallTrace
import com.retrosprite.app.domain.models.LlmRequest
import com.retrosprite.app.domain.models.SessionContext
import com.retrosprite.app.llm.LlmAdapter

/**
 * Renders an [AnswerDecision] into the final user-visible text.
 *
 * This is the single funnel where Endpoint -> Policy meets the LLM (or its
 * mock). Keeping it tiny and decision-driven lets us swap providers /
 * prompt strategies without touching the resolver, retrieval or policy.
 */
class AnswerComposer(
    private val maxTokensProvider: () -> Int = { DEFAULT_MAX_TOKENS },
    private val localEvidenceSummarizer: LocalEvidenceSummarizer = LocalEvidenceSummarizer(),
) {

    suspend fun compose(
        decision: AnswerDecision,
        context: SessionContext,
        llm: LlmAdapter,
    ): String = composeDetailed(decision, context, llm).text

    suspend fun composeDetailed(
        decision: AnswerDecision,
        context: SessionContext,
        llm: LlmAdapter,
    ): ComposedAnswer = when (decision) {
        is AnswerDecision.DirectAnswer -> {
            val result = AnswerResult.fromText(
                text = decision.text,
                sources = decision.sources,
                confidence = decision.confidence,
                answerType = decision.answerType,
                spoilerLevelUsed = decision.spoilerLevel,
                nextActions = decision.nextActions,
                suggestedQuestions = decision.suggestedQuestions,
            ).localizedFor(context)
            ComposedAnswer(
                text = result.textWithSources,
                llmTrace = llm.skippedTrace(),
                answerResult = result,
            )
        }

        is AnswerDecision.ComposeWithLlm -> {
            if (decision.evidence.isEmpty()) {
                val result = AnswerResult.fromText(
                    text = NO_EVIDENCE_TEXT,
                    confidence = AnswerConfidence.Low,
                    answerType = AnswerType.NoEvidence,
                    spoilerLevelUsed = decision.spoilerLevel,
                    nextActions = listOf(AnswerNextAction.MoreSpecific, AnswerNextAction.MarkIncorrect),
                    suggestedQuestions = decision.suggestedQuestions,
                ).localizedFor(context)
                ComposedAnswer(
                    text = result.textWithSources,
                    llmTrace = llm.skippedTrace(),
                    answerResult = result,
                )
            } else {
                val request = LlmRequest(
                    systemPrompt = systemPromptFor(context),
                    userPrompt = buildEvidencePrompt(decision, context),
                    evidence = decision.evidence,
                    maxTokens = currentMaxTokens(),
                )
                runCatching {
                    val response = llm.complete(request)
                    val sources = response.citationsUsed.ifEmpty {
                        decision.evidence.map { it.sourceId }
                    }
                    val result = AnswerResult.fromText(
                        text = response.text,
                        sources = sources,
                        confidence = decision.confidence,
                        answerType = decision.answerType.takeUnless { it == AnswerType.UnknownOrOutOfScope }
                            ?: context.questionIntent,
                        spoilerLevelUsed = decision.spoilerLevel,
                        nextActions = decision.nextActions,
                        suggestedQuestions = decision.suggestedQuestions,
                    ).localizedFor(context)
                    ComposedAnswer(
                        text = result.textWithSources,
                        llmTrace = LlmCallTrace(
                            status = LlmCallTrace.STATUS_USED,
                            providerName = llm.providerName,
                            modelName = llm.modelName,
                            maxTokens = request.maxTokens,
                            timeoutMs = llm.timeoutMs,
                            latencyMs = response.latencyMs,
                            tokensIn = response.tokensIn,
                            tokensOut = response.tokensOut,
                        ),
                        answerResult = result,
                    )
                }.getOrElse { error ->
                    val sources = decision.evidence.map { it.sourceId }
                    val detail = buildLlmFailureAnswer(error)
                    val result = AnswerResult.fromText(
                        text = detail,
                        sources = sources,
                        confidence = AnswerConfidence.Low,
                        answerType = decision.answerType,
                        spoilerLevelUsed = decision.spoilerLevel,
                        nextActions = listOf(AnswerNextAction.ViewSources, AnswerNextAction.MarkIncorrect),
                        suggestedQuestions = decision.suggestedQuestions,
                    ).localizedFor(context)
                    ComposedAnswer(
                        text = result.textWithSources,
                        llmTrace = LlmCallTrace(
                            status = LlmCallTrace.STATUS_FAILED,
                            providerName = llm.providerName,
                            modelName = llm.modelName,
                            maxTokens = request.maxTokens,
                            timeoutMs = llm.timeoutMs,
                            errorMessage = error.message?.take(ERROR_MAX_CHARS),
                        ),
                        answerResult = result,
                    )
                }
            }
        }

        is AnswerDecision.LocalSummary -> {
            val summary = localEvidenceSummarizer.summarize(decision.evidence)
            val result = AnswerResult(
                answerShort = summary.answerShort,
                answerDetail = summary.answerDetail,
                sources = summary.sources,
                confidence = decision.confidence,
                answerType = decision.answerType.takeUnless { it == AnswerType.UnknownOrOutOfScope }
                    ?: context.questionIntent,
                spoilerLevelUsed = decision.spoilerLevel,
                nextActions = decision.nextActions,
                suggestedQuestions = decision.suggestedQuestions,
            ).localizedFor(context)
            ComposedAnswer(
                text = result.textWithSources,
                llmTrace = llm.skippedTrace(),
                answerResult = result,
            )
        }

        is AnswerDecision.AskClarification -> {
            val result = AnswerResult.fromText(
                text = decision.question,
                confidence = AnswerConfidence.Low,
                answerType = context.questionIntent,
                spoilerLevelUsed = context.spoilerLevel,
                nextActions = listOf(AnswerNextAction.MoreSpecific, AnswerNextAction.MarkIncorrect),
            ).localizedFor(context)
            ComposedAnswer(
                text = result.textWithSources,
                llmTrace = llm.skippedTrace(),
                answerResult = result,
            )
        }

        is AnswerDecision.Refuse -> {
            val result = AnswerResult.fromText(
                text = POLITE_REFUSAL_ZH,
                confidence = AnswerConfidence.Low,
                answerType = AnswerType.UnknownOrOutOfScope,
                spoilerLevelUsed = context.spoilerLevel,
                nextActions = listOf(AnswerNextAction.MarkIncorrect),
            ).localizedFor(context)
            ComposedAnswer(
                text = result.textWithSources,
                llmTrace = llm.skippedTrace(),
                answerResult = result,
            )
        }
    }

    companion object {
        // Phase 0 / 1 default system prompt skeleton. Real prompt template
        // (with citation contract, spoiler rules, ≤ 3 sentences cap) lives
        // in the prompts module added in Phase 1.
        private const val SYSTEM_PROMPT_ZH: String =
            "你是 RetroSprite，一个专注于复古游戏内问答的伙伴。" +
                "请根据提供的证据用简体中文回答，最长 3 句话，避免剧透与无关闲聊。"

        private const val POLITE_REFUSAL_ZH: String =
            "抱歉，这个问题暂时超出我的范围，建议先继续游戏，或换个更具体的提问。"

        private const val NO_EVIDENCE_TEXT: String =
            "我还没有足够证据回答这个问题。请补充版本、位置或换个更具体的问法。"

        private const val DEFAULT_MAX_TOKENS: Int = 256
        private const val MIN_MAX_TOKENS: Int = 32
        private const val MAX_MAX_TOKENS: Int = 2048
        private const val ERROR_MAX_CHARS: Int = 180
    }

    private fun currentMaxTokens(): Int =
        maxTokensProvider().coerceIn(MIN_MAX_TOKENS, MAX_MAX_TOKENS)

    private fun LlmAdapter.skippedTrace(): LlmCallTrace = LlmCallTrace(
        status = LlmCallTrace.STATUS_SKIPPED,
        providerName = providerName,
        modelName = modelName,
        timeoutMs = timeoutMs,
    )

    private fun buildLlmFailureAnswer(error: Throwable): String {
        val cleanMessage = error.message
            ?.take(ERROR_MAX_CHARS)
            ?.ifBlank { null }
            ?: "provider_error"
        return "LLM 调用失败：$cleanMessage。已保留本地证据，请稍后重试或检查模型配置。"
    }

    private fun systemPromptFor(context: SessionContext): String =
        when (context.language.lowercase()) {
            else -> SYSTEM_PROMPT_ZH
        }

    private fun buildEvidencePrompt(
        decision: AnswerDecision.ComposeWithLlm,
        context: SessionContext,
    ): String {
        val question = context.playerQuestion?.trim().orEmpty()
        val evidenceText = decision.evidence
            .mapIndexed { index, evidence ->
                "[${index + 1}] source=${evidence.sourceId}; spoiler=${evidence.spoilerLevel}; text=${evidence.snippet}"
            }
            .joinToString(separator = "\n")

        return buildString {
            appendLine("玩家问题：${question.ifBlank { "未提供明确问题" }}")
            appendLine("任务：${decision.prompt}")
            appendLine("可用证据：")
            appendLine(evidenceText)
            append("只能依据上述证据回答；如果证据不足，请明确说不确定。")
        }
    }
}

private fun AnswerResult.localizedFor(context: SessionContext): AnswerResult =
    ChineseAnswerTextLocalizer.localize(this, context)
