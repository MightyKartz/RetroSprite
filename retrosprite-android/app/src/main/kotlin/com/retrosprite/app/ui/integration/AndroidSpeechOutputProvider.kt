package com.retrosprite.app.ui.integration

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.retrosprite.app.ui.viewmodel.SpeechOutputProvider
import com.retrosprite.app.ui.viewmodel.UiSpeechOutputState
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AndroidSpeechOutputProvider(
    context: Context,
) : SpeechOutputProvider, TextToSpeech.OnInitListener {

    private val _state = MutableStateFlow(
        UiSpeechOutputState(isAvailable = true, isReady = false)
    )
    override val state: StateFlow<UiSpeechOutputState> = _state.asStateFlow()

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            _state.value = UiSpeechOutputState(
                isAvailable = false,
                isReady = false,
                errorMessage = "系统 TTS 初始化失败",
            )
            return
        }
        val languageResult = tts.setLanguage(Locale.getDefault())
        if (
            languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            _state.value = UiSpeechOutputState(
                isAvailable = false,
                isReady = false,
                errorMessage = "当前语言没有可用 TTS 语音",
            )
            return
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _state.update { it.copy(isSpeaking = true, errorMessage = null) }
            }

            override fun onDone(utteranceId: String?) {
                _state.update { it.copy(isSpeaking = false) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _state.update { it.copy(isSpeaking = false, errorMessage = "朗读失败") }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _state.update {
                    it.copy(isSpeaking = false, errorMessage = "朗读失败：$errorCode")
                }
            }
        })
        _state.value = UiSpeechOutputState(isAvailable = true, isReady = true)
    }

    override suspend fun speak(text: String) {
        val speechText = text.shortSpeechAnswer()
        if (speechText.isBlank()) return
        if (!_state.value.isReady) {
            _state.update { it.copy(errorMessage = "TTS 尚未准备好") }
            return
        }
        _state.update {
            it.copy(
                isSpeaking = true,
                spokenText = speechText,
                errorMessage = null,
            )
        }
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UUID.randomUUID().toString())
        }
        val result = tts.speak(
            speechText,
            TextToSpeech.QUEUE_FLUSH,
            params,
            UUID.randomUUID().toString(),
        )
        if (result == TextToSpeech.ERROR) {
            _state.update {
                it.copy(isSpeaking = false, errorMessage = "朗读启动失败")
            }
        }
    }

    override suspend fun stop() {
        tts.stop()
        _state.update { it.copy(isSpeaking = false) }
    }
}

internal fun String.shortSpeechAnswer(maxChars: Int = 160): String {
    val compact = lineSequence()
        .joinToString(" ") { it.trim() }
        .replace(Regex("\\s+"), " ")
        .trim()
    if (compact.isBlank()) return ""
    val withoutSources = compact.substringBefore("来源：").trim()
    val firstSentence = withoutSources
        .splitToSequence('。', '！', '？', '.', '!', '?')
        .firstOrNull()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: withoutSources
    return if (firstSentence.length <= maxChars) {
        firstSentence
    } else {
        firstSentence.take(maxChars - 1) + "…"
    }
}
