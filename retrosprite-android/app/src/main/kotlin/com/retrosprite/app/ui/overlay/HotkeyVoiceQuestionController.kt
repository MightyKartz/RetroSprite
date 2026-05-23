package com.retrosprite.app.ui.overlay

import com.retrosprite.app.endpoint.RequestLogger
import com.retrosprite.app.endpoint.ResponseGenerator
import com.retrosprite.app.endpoint.RetroArchHotkeyEvent
import com.retrosprite.app.endpoint.RetroArchHotkeyListener
import com.retrosprite.app.endpoint.model.RetroArchRequest
import com.retrosprite.app.endpoint.model.RetroArchResponse
import com.retrosprite.app.endpoint.model.RetroArchState
import com.retrosprite.app.endpoint.model.ResponseDiagnostics
import com.retrosprite.app.ui.viewmodel.SpeechOutputProvider
import com.retrosprite.app.ui.viewmodel.VoiceInputProvider
import com.retrosprite.app.voice.asr.AsrBiasingProfileProvider
import com.retrosprite.app.voice.asr.AsrHotwordMode
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
    private val asrBiasingProfileProvider: AsrBiasingProfileProvider? = null,
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
            coordinator.finishVoiceSession()
            throw cancelled
        } catch (error: Throwable) {
            voiceInput.cancelListening()
            coordinator.renderVoiceState(
                phase = HotkeyVoiceOverlayPhase.Error,
                message = error.message ?: "语音问答失败",
                answerText = "语音问答失败，请再按一次热键。",
            )
            finishVoiceSessionAfter(RECOVERY_LINGER_MS)
        }
    }

    private suspend fun runVoiceQuestionAfterOverlayStarted(event: RetroArchHotkeyEvent) {
        val recognitionContext = recognitionContextFor(event)
        val voiceState = coroutineScope {
            val initialEventId = voiceInput.state.value.transcriptEventId
            val progressJob = launch {
                voiceInput.state.collect { state ->
                    if (state.isListening || state.amplitude > 0f || !state.transcript.isNullOrBlank()) {
                        coordinator.renderVoiceState(
                            phase = HotkeyVoiceOverlayPhase.Listening,
                            amplitude = state.amplitude,
                            message = state.statusMessage ?: "正在收音",
                            transcript = state.transcript,
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
        if (question.isBlank()) {
            voiceInput.cancelListening()
            val errorMessage = voiceState?.errorMessage
            coordinator.renderVoiceState(
                phase = if (errorMessage == null) {
                    HotkeyVoiceOverlayPhase.Muted
                } else {
                    HotkeyVoiceOverlayPhase.Error
                },
                message = errorMessage ?: "Muted",
                answerText = if (errorMessage == null) {
                    "没有听到问题，请再按一次热键。"
                } else {
                    "语音识别失败，请再试一次。"
                },
            )
            finishVoiceSessionAfter(RECOVERY_LINGER_MS)
            return
        }

        delay(LISTENING_VISUAL_LINGER_MS)
        coordinator.renderVoiceState(
            phase = HotkeyVoiceOverlayPhase.Thinking,
            message = "正在检索本地知识",
            transcript = question,
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
                answerText = "回答失败，请稍后重试。",
            )
            finishVoiceSessionAfter(RECOVERY_LINGER_MS)
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
        if (entry.errorMessage == null) {
            val responsePhase = when (entry.pipelineStage) {
                "no_evidence", "gkp_disabled" -> HotkeyVoiceOverlayPhase.NoEvidence
                else -> HotkeyVoiceOverlayPhase.Speaking
            }
            coordinator.renderVoiceState(
                phase = responsePhase,
                message = if (responsePhase == HotkeyVoiceOverlayPhase.NoEvidence) {
                    "NO RELIABLE EVIDENCE"
                } else {
                    "正在朗读答案"
                },
                transcript = overlayTranscript,
                answerText = if (responsePhase == HotkeyVoiceOverlayPhase.NoEvidence) {
                    (entry.answerDetail ?: entry.responseText).toOverlayAnswerText(
                        maxChars = OVERLAY_NO_EVIDENCE_MAX_CHARS,
                        preserveLineBreaks = true,
                    )
                } else {
                    (entry.answerShort ?: entry.responseText).toOverlayAnswerText()
                },
                sourceIds = entry.sourceIds,
            )
            speakIfPossible(response)
            finishVoiceSessionAfter(ANSWER_LINGER_MS)
        } else {
            coordinator.renderVoiceState(
                phase = HotkeyVoiceOverlayPhase.Error,
                message = entry.errorMessage,
                transcript = overlayTranscript,
                answerText = "回答失败，请稍后重试。",
            )
            finishVoiceSessionAfter(RECOVERY_LINGER_MS)
        }
    }

    private suspend fun finishVoiceSessionAfter(delayMillis: Long) {
        delay(delayMillis)
        coordinator.finishVoiceSession()
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

    private suspend fun recognitionContextFor(event: RetroArchHotkeyEvent): AsrRecognitionContext {
        val resolution = asrBiasingProfileProvider?.resolveForLabel(event.label)
        return AsrRecognitionContext(
            label = resolution?.label ?: event.label,
            gameId = resolution?.profile?.gameId,
            spoilerLevel = "light",
            source = QUESTION_SOURCE,
            biasingProfile = resolution?.profile,
            hotwordMode = resolution?.hotwordMode ?: AsrHotwordMode.Auto,
        )
    }

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
