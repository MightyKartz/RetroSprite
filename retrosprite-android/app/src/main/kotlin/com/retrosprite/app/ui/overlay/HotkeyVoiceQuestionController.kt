package com.retrosprite.app.ui.overlay

import com.retrosprite.app.endpoint.RequestLogger
import com.retrosprite.app.endpoint.ResponseGenerator
import com.retrosprite.app.endpoint.RetroArchHotkeyEvent
import com.retrosprite.app.endpoint.RetroArchHotkeyListener
import com.retrosprite.app.endpoint.model.RetroArchRequest
import com.retrosprite.app.endpoint.model.RetroArchResponse
import com.retrosprite.app.endpoint.model.RetroArchState
import com.retrosprite.app.ui.viewmodel.SpeechOutputProvider
import com.retrosprite.app.ui.viewmodel.VoiceInputProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.cancelAndJoin
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
            )
            coordinator.finishVoiceSession()
        }
    }

    private suspend fun runVoiceQuestionAfterOverlayStarted(event: RetroArchHotkeyEvent) {
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
            val finalVoiceState = async {
                voiceInput.state
                    .filter { state ->
                        state.errorMessage != null ||
                            (!state.isListening && state.transcriptEventId > initialEventId)
                    }
                    .first()
            }

            voiceInput.startListening()
            val final = withTimeoutOrNull(VOICE_TIMEOUT_MS) { finalVoiceState.await() }
            if (final == null) {
                finalVoiceState.cancelAndJoin()
            }
            progressJob.cancelAndJoin()
            final
        }
        val question = voiceState?.transcript?.trim().orEmpty()
        if (question.isBlank()) {
            voiceInput.cancelListening()
            coordinator.renderVoiceState(
                phase = HotkeyVoiceOverlayPhase.Error,
                message = voiceState?.errorMessage ?: "没有识别到问题",
            )
            coordinator.finishVoiceSession()
            return
        }

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
                    label = event.label,
                    question = question,
                    state = RetroArchState(paused = if (event.paused) 1 else 0),
                ),
                outputMode = "text",
            )
        }.getOrElse { error ->
            val entry = logger.log(
                label = event.label,
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
            )
            coordinator.finishVoiceSession()
            return
        }
        val durationMillis = System.currentTimeMillis() - startedAt

        val entry = logger.log(
            label = event.label,
            imageBase64 = "",
            paused = event.paused,
            outputMode = OUTPUT_MODE,
            responseText = response.text.orEmpty(),
            errorMessage = response.error,
            durationMillis = durationMillis,
            diagnostics = response.diagnostics,
            question = question,
            questionSource = QUESTION_SOURCE,
        )
        if (entry.errorMessage == null) {
            coordinator.renderVoiceState(
                phase = HotkeyVoiceOverlayPhase.Speaking,
                message = "正在朗读答案",
                transcript = question,
            )
            speakIfPossible(response)
        } else {
            coordinator.renderVoiceState(
                phase = HotkeyVoiceOverlayPhase.Error,
                message = entry.errorMessage,
                transcript = question,
            )
        }
        coordinator.finishVoiceSession()
    }

    private suspend fun speakIfPossible(response: RetroArchResponse) {
        speakIfPossible(response.text.orEmpty())
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

    companion object {
        const val OUTPUT_MODE: String = "hotkey_voice:text"
        const val QUESTION_SOURCE: String = "hotkey_voice"
        private const val VOICE_TIMEOUT_MS: Long = 20_000L
        private const val SPEECH_TIMEOUT_MS: Long = 15_000L
    }
}
