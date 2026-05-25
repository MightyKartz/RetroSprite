package com.retrosprite.app.ui.overlay

import com.retrosprite.app.endpoint.RequestLogger
import com.retrosprite.app.endpoint.RequestLogEntry
import com.retrosprite.app.endpoint.ResponseGenerator
import com.retrosprite.app.endpoint.RetroArchHotkeyEvent
import com.retrosprite.app.endpoint.RetroArchHotkeyListener
import com.retrosprite.app.endpoint.model.RetroArchRequest
import com.retrosprite.app.endpoint.model.RetroArchResponse
import com.retrosprite.app.endpoint.model.RetroArchState
import com.retrosprite.app.endpoint.model.ResponseDiagnostics
import com.retrosprite.app.ui.viewmodel.SpeechOutputProvider
import com.retrosprite.app.ui.viewmodel.VoiceInputProvider
import com.retrosprite.app.voice.asr.AsrRecognitionContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class HotkeyVoiceQuestionController(
    private val coordinator: HotkeyVoiceOverlayCoordinator,
    private val voiceInput: VoiceInputProvider,
    private val responseGenerator: ResponseGenerator,
    private val speechOutput: SpeechOutputProvider,
    private val loggerProvider: () -> RequestLogger,
    private val scope: CoroutineScope,
    private val showTranscriptHudProvider: () -> Boolean = { true },
) : RetroArchHotkeyListener {

    private var activeJob: Job? = null

    override fun onHotkey(event: RetroArchHotkeyEvent) {
        if (activeJob?.isActive == true) return
        activeJob = scope.launch {
            try {
                runVoiceQuestion(event)
            } finally {
                activeJob = null
            }
        }
    }

    private suspend fun runVoiceQuestion(event: RetroArchHotkeyEvent) {
        if (!coordinator.beginVoiceSession(event)) return
        try {
            runVoiceQuestionAfterOverlayStarted(event)
        } catch (cancelled: CancellationException) {
            voiceInput.cancelListening()
            coordinator.finishVoiceSession(reason = "cancelled")
            throw cancelled
        } catch (error: Throwable) {
            voiceInput.cancelListening()
            coordinator.renderVoiceState(
                phase = HotkeyVoiceOverlayPhase.Error,
                message = error.message ?: "语音问答失败",
                answerText = "语音问答失败，请再按一次热键。",
            )
            finishVoiceSessionAfter(RECOVERY_LINGER_MS, reason = "error_recovery")
        }
    }

    private suspend fun runVoiceQuestionAfterOverlayStarted(event: RetroArchHotkeyEvent) {
        val recognitionContext = recognitionContextFor(event)
        val voiceState = coroutineScope {
            val initialEventId = voiceInput.state.value.transcriptEventId
            val progressJob = launch {
                voiceInput.state.collect { state ->
                    val transcriptForSession = state.transcript
                        ?.takeIf { it.isNotBlank() }
                        ?.takeIf { state.isListening || state.transcriptEventId > initialEventId }
                    val isPreparing = !state.isListening &&
                        transcriptForSession == null &&
                        state.statusMessage != null
                    if (isPreparing || state.isListening || state.amplitude > 0f || transcriptForSession != null) {
                        coordinator.renderVoiceState(
                            phase = if (state.isListening || transcriptForSession != null) {
                                HotkeyVoiceOverlayPhase.Listening
                            } else {
                                HotkeyVoiceOverlayPhase.Preparing
                            },
                            amplitude = if (state.isListening) state.amplitude else 0f,
                            message = if (state.isListening || transcriptForSession != null) {
                                state.statusMessage ?: "Mic live"
                            } else {
                                state.statusMessage ?: "Preparing - mic off"
                            },
                            micLive = state.isListening,
                            transcript = transcriptForSession,
                            showTranscriptHud = showTranscriptHudProvider(),
                            asrArchitecture = state.asrArchitecture,
                            asrDecodingMethod = state.asrDecodingMethod,
                            asrModelingUnit = state.asrModelingUnit,
                            asrCommitReason = state.asrCommitReason,
                            asrLastPartial = state.asrLastPartial,
                            asrFinalText = state.asrFinalText,
                            asrSelectedTranscript = state.asrSelectedTranscript,
                            asrPostVoiceSilenceMillis = state.asrPostVoiceSilenceMillis,
                            asrPartialStableMillis = state.asrPartialStableMillis,
                            asrRequiredStableMillis = state.asrRequiredStableMillis,
                            asrEndpointArmed = state.asrEndpointArmed,
                            asrFinalFlushMillis = state.asrFinalFlushMillis,
                        )
                    }
                }
            }

            voiceInput.startListening(recognitionContext)
            val final = withTimeoutOrNull(VOICE_TIMEOUT_MS) {
                voiceInput.state
                    .filter { state ->
                        state.errorMessage != null ||
                            (!state.isListening && state.transcriptEventId > initialEventId)
                    }
                    .first()
            }
            progressJob.cancelAndJoin()
            final
        }
        val question = voiceState?.transcript?.trim().orEmpty()
        val requestLabel = recognitionContext.label
        if (!question.hasEnoughVoiceQuestionSignal()) {
            voiceInput.cancelListening()
            val errorMessage = voiceState?.errorMessage
            coordinator.renderVoiceState(
                phase = if (errorMessage == null) {
                    HotkeyVoiceOverlayPhase.Muted
                } else {
                    HotkeyVoiceOverlayPhase.Error
                },
                message = errorMessage ?: "No speech",
                answerText = if (errorMessage == null) {
                    "没有听到问题，请再按一次热键。"
                } else {
                    "语音识别失败，请再试一次。"
                },
                asrCommitReason = voiceState?.asrCommitReason,
                asrLastPartial = voiceState?.asrLastPartial,
                asrFinalText = voiceState?.asrFinalText,
                asrSelectedTranscript = voiceState?.asrSelectedTranscript,
                asrPostVoiceSilenceMillis = voiceState?.asrPostVoiceSilenceMillis,
                asrPartialStableMillis = voiceState?.asrPartialStableMillis,
                asrRequiredStableMillis = voiceState?.asrRequiredStableMillis,
                asrEndpointArmed = voiceState?.asrEndpointArmed,
                asrFinalFlushMillis = voiceState?.asrFinalFlushMillis,
            )
            finishVoiceSessionAfter(
                RECOVERY_LINGER_MS,
                reason = if (errorMessage == null) "muted_recovery" else "asr_error_recovery",
            )
            return
        }

        delay(LISTENING_VISUAL_LINGER_MS)
        coordinator.renderVoiceState(
            phase = HotkeyVoiceOverlayPhase.Thinking,
            message = "Thinking",
            transcript = question,
            showTranscriptHud = showTranscriptHudProvider(),
            asrCommitReason = voiceState?.asrCommitReason,
            asrLastPartial = voiceState?.asrLastPartial,
            asrFinalText = voiceState?.asrFinalText,
            asrSelectedTranscript = voiceState?.asrSelectedTranscript,
            asrPostVoiceSilenceMillis = voiceState?.asrPostVoiceSilenceMillis,
            asrPartialStableMillis = voiceState?.asrPartialStableMillis,
            asrRequiredStableMillis = voiceState?.asrRequiredStableMillis,
            asrEndpointArmed = voiceState?.asrEndpointArmed,
            asrFinalFlushMillis = voiceState?.asrFinalFlushMillis,
        )

        val logger = loggerProvider()
        val startedAt = System.currentTimeMillis()
        val response = runCatching {
            responseGenerator.generate(
                request = RetroArchRequest(
                    image = "",
                    label = requestLabel,
                    question = question,
                    state = RetroArchState(paused = if (event.paused) 1 else 0),
                ),
                outputMode = OUTPUT_MODE,
            )
        }.getOrElse { error ->
            val entry = logger.log(
                label = requestLabel,
                imageBase64 = "",
                paused = event.paused,
                outputMode = OUTPUT_MODE,
                responseText = "",
                errorMessage = "hotkey_voice_generator_failed: ${error.message}",
                durationMillis = System.currentTimeMillis() - startedAt,
                question = question,
                questionSource = QUESTION_SOURCE,
            )
            speakIfPossible(entry.responseText)
            coordinator.renderVoiceState(
                phase = HotkeyVoiceOverlayPhase.Error,
                message = entry.errorMessage ?: "回答失败",
                transcript = question,
                showTranscriptHud = showTranscriptHudProvider(),
                answerText = "回答失败，请稍后重试。",
            )
            finishVoiceSessionAfter(RECOVERY_LINGER_MS, reason = "generator_error_recovery")
            return
        }
        val durationMillis = System.currentTimeMillis() - startedAt
        val diagnostics = response.diagnostics.withInferredNormalization(rawQuestion = question)
        val inferredRawQuestion = question.trim().takeIf {
            it.isNotBlank() && diagnostics.question != null && it != diagnostics.question
        }

        val entry = logger.log(
            label = requestLabel,
            imageBase64 = "",
            paused = event.paused,
            outputMode = OUTPUT_MODE,
            responseText = response.text.orEmpty(),
            errorMessage = response.error,
            durationMillis = durationMillis,
            diagnostics = diagnostics,
            question = diagnostics.question
                ?: diagnostics.normalizedQuestion
                ?: question,
            questionSource = QUESTION_SOURCE,
            rawQuestion = diagnostics.rawQuestion ?: inferredRawQuestion,
            normalizedQuestion = diagnostics.normalizedQuestion
                ?: diagnostics.question.takeIf { inferredRawQuestion != null },
            questionNormalizationReason = diagnostics.questionNormalizationReason
                ?: "normalized".takeIf { inferredRawQuestion != null },
            normalizedQuestionMatchedTerm = diagnostics.normalizedQuestionMatchedTerm,
            normalizedQuestionMatchedEntityId = diagnostics.normalizedQuestionMatchedEntityId,
        )
        val overlayTranscript = entry.rawQuestion ?: question
        val overlayNormalizedTranscript = entry.normalizedQuestion
            ?.takeIf { it != overlayTranscript }
        if (entry.errorMessage == null) {
            val responsePhase = when (entry.pipelineStage) {
                "no_evidence", "gkp_disabled" -> HotkeyVoiceOverlayPhase.NoEvidence
                else -> HotkeyVoiceOverlayPhase.Speaking
            }
            coordinator.renderVoiceState(
                phase = responsePhase,
                message = if (responsePhase == HotkeyVoiceOverlayPhase.NoEvidence) {
                    "No evidence"
                } else {
                    "Answering"
                },
                transcript = overlayTranscript,
                normalizedTranscript = overlayNormalizedTranscript,
                transcriptMatchedTerm = entry.normalizedQuestionMatchedTerm,
                showTranscriptHud = showTranscriptHudProvider(),
                answerText = if (responsePhase == HotkeyVoiceOverlayPhase.NoEvidence) {
                    (entry.answerDetail ?: entry.responseText).toOverlayAnswerText(
                        maxChars = OVERLAY_NO_EVIDENCE_MAX_CHARS,
                        preserveLineBreaks = true,
                    )
                } else {
                    entry.toOverlayAnswerTextWithSuggestions()
                },
                sourceIds = entry.sourceIds,
            )
            speakIfPossible(response)
            finishVoiceSessionAfter(ANSWER_LINGER_MS, reason = "answer_completed")
        } else {
            coordinator.renderVoiceState(
                phase = HotkeyVoiceOverlayPhase.Error,
                message = entry.errorMessage,
                transcript = overlayTranscript,
                normalizedTranscript = overlayNormalizedTranscript,
                transcriptMatchedTerm = entry.normalizedQuestionMatchedTerm,
                showTranscriptHud = showTranscriptHudProvider(),
                answerText = "回答失败，请稍后重试。",
            )
            finishVoiceSessionAfter(RECOVERY_LINGER_MS, reason = "answer_error_recovery")
        }
    }

    private suspend fun finishVoiceSessionAfter(delayMillis: Long, reason: String) {
        delay(delayMillis)
        coordinator.finishVoiceSession(reason = reason)
    }

    private suspend fun speakIfPossible(response: RetroArchResponse) {
        speakIfPossible(response.diagnostics.answerShort ?: response.text.orEmpty())
    }

    private suspend fun speakIfPossible(text: String) {
        val clean = text.trim()
        if (clean.isNotBlank()) {
            speechOutput.speak(clean)
            withTimeoutOrNull(SPEECH_TIMEOUT_MS) {
                speechOutput.state
                    .filter { !it.isSpeaking }
                    .first()
            }
        }
    }

    private fun recognitionContextFor(event: RetroArchHotkeyEvent): AsrRecognitionContext =
        AsrRecognitionContext(
            label = event.label,
            gameId = null,
            spoilerLevel = "light",
            source = QUESTION_SOURCE,
        )

    companion object {
        const val OUTPUT_MODE: String = "hotkey_voice:text"
        const val QUESTION_SOURCE: String = "hotkey_voice"
        private const val VOICE_TIMEOUT_MS: Long = 20_000L
        private const val SPEECH_TIMEOUT_MS: Long = 15_000L
        private const val LISTENING_VISUAL_LINGER_MS: Long = 220L
        private const val ANSWER_LINGER_MS: Long = 2_000L
        private const val RECOVERY_LINGER_MS: Long = 2_000L
    }
}

private const val SOURCE_PREFIX = "来源："
private const val OVERLAY_ANSWER_MAX_CHARS = 96
private const val OVERLAY_NO_EVIDENCE_MAX_CHARS = 180
private const val OVERLAY_MAX_SUGGESTED_QUESTIONS = 3
private const val MIN_VOICE_QUESTION_CHARS = 3
private val PATHOLOGICAL_VOICE_FRAGMENTS = setOf(
    "是什",
    "关是",
    "是不",
    "什",
    "么",
)
private val SHORT_SAFE_VOICE_TERMS = setOf(
    "伊凡",
    "玛尔",
    "魔石",
)

private fun String.hasEnoughVoiceQuestionSignal(): Boolean {
    val clean = filter { it.isLetterOrDigit() }
    if (clean.isBlank()) return false
    if (clean in PATHOLOGICAL_VOICE_FRAGMENTS) return false
    if (clean.length >= MIN_VOICE_QUESTION_CHARS) return true
    return clean in SHORT_SAFE_VOICE_TERMS
}

private fun ResponseDiagnostics.withInferredNormalization(rawQuestion: String): ResponseDiagnostics {
    val cleanRaw = rawQuestion.trim()
    val cleanQuestion = question?.trim().orEmpty()
    if (cleanRaw.isBlank() || cleanQuestion.isBlank() || cleanRaw == cleanQuestion) return this
    return copy(
        rawQuestion = this.rawQuestion ?: cleanRaw,
        normalizedQuestion = this.normalizedQuestion ?: cleanQuestion,
        questionNormalizationReason = questionNormalizationReason ?: "normalized",
    )
}

private fun String.toOverlayAnswerText(
    maxChars: Int = OVERLAY_ANSWER_MAX_CHARS,
    preserveLineBreaks: Boolean = false,
): String {
    val answer = lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith(SOURCE_PREFIX) }
        .joinToString(if (preserveLineBreaks) "\n" else " ")
        .trim()
    if (answer.length <= maxChars) return answer
    return answer.take(maxChars).trimEnd() + "..."
}

private fun RequestLogEntry.toOverlayAnswerTextWithSuggestions(): String {
    val suggestions = suggestedQuestions
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(OVERLAY_MAX_SUGGESTED_QUESTIONS)
    val answer = (answerShort ?: responseText).toOverlayAnswerText(
        maxChars = if (suggestions.isEmpty()) {
            OVERLAY_ANSWER_MAX_CHARS
        } else {
            Int.MAX_VALUE
        }
    )
    if (suggestions.isEmpty()) return answer
    return buildString {
        append(answer)
        append("\n\n你还可以问：")
        suggestions.forEach { question ->
            append("\n· ")
            append(question)
        }
    }
}
