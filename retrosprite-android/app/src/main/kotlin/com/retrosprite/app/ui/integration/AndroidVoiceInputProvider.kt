package com.retrosprite.app.ui.integration

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.retrosprite.app.ui.viewmodel.UiVoiceInputState
import com.retrosprite.app.ui.viewmodel.VoiceInputProvider
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AndroidVoiceInputProvider(
    private val context: Context,
) : VoiceInputProvider, RecognitionListener {

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(
        UiVoiceInputState(
            isAvailable = isRecognizerAvailable(),
            engineLabel = initialEngineLabel(),
            errorMessage = if (isRecognizerAvailable()) null else "系统语音识别不可用",
        )
    )
    override val state: StateFlow<UiVoiceInputState> = _state.asStateFlow()
    override val requiresRecordAudioPermission: Boolean = true

    private var recognizer: SpeechRecognizer? = null
    private var eventId: Long = 0L

    override suspend fun startListening() {
        if (!isRecognizerAvailable()) {
            _state.update {
                it.copy(
                    isAvailable = false,
                    isListening = false,
                    errorMessage = "系统语音识别不可用",
                )
            }
            return
        }
        val speechRecognizer = recognizer ?: createRecognizer().also { recognizer = it }
        if (speechRecognizer == null) {
            _state.update { it.copy(isListening = false, errorMessage = "无法启动语音识别") }
            return
        }

        _state.update {
            it.copy(
                isAvailable = true,
                isListening = true,
                engineLabel = engineLabel(),
                errorMessage = null,
            )
        }
        speechRecognizer.startListening(recognizerIntent())
    }

    override suspend fun stopListening() {
        recognizer?.stopListening()
        _state.update { it.copy(isListening = false) }
    }

    override suspend fun cancelListening() {
        recognizer?.cancel()
        _state.update { it.copy(isListening = false, errorMessage = null) }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        _state.update { it.copy(isListening = true, errorMessage = null) }
    }

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        _state.update { it.copy(isListening = false) }
    }

    override fun onError(error: Int) {
        _state.update {
            it.copy(
                isListening = false,
                errorMessage = error.displayNameForSpeechRecognizer(),
            )
        }
    }

    override fun onResults(results: Bundle?) {
        val transcript = results.bestSpeechResult()
        if (transcript.isNullOrBlank()) {
            _state.update {
                it.copy(isListening = false, errorMessage = "没有识别到有效语音")
            }
            return
        }
        eventId += 1
        _state.value = _state.value.copy(
            isListening = false,
            transcript = transcript,
            transcriptEventId = eventId,
            errorMessage = null,
        )
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val partial = partialResults.bestSpeechResult() ?: return
        _state.update { it.copy(transcript = partial, errorMessage = null) }
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun createRecognizer(): SpeechRecognizer? {
        val recognizer = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext) ->
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)

            SpeechRecognizer.isRecognitionAvailable(appContext) ->
                SpeechRecognizer.createSpeechRecognizer(appContext)

            else -> null
        }
        recognizer?.setRecognitionListener(this)
        return recognizer
    }

    private fun isRecognizerAvailable(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
        ) {
            true
        } else {
            SpeechRecognizer.isRecognitionAvailable(appContext)
        }

    private fun initialEngineLabel(): String = if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
    ) {
        "端侧语音识别"
    } else {
        "系统语音识别"
    }

    private fun engineLabel(): String = initialEngineLabel()

    private fun recognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
}

private fun Bundle?.bestSpeechResult(): String? =
    this
        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

private fun Int.displayNameForSpeechRecognizer(): String = when (this) {
    SpeechRecognizer.ERROR_AUDIO -> "录音失败，请检查麦克风"
    SpeechRecognizer.ERROR_CLIENT -> "语音识别客户端错误"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "没有麦克风权限"
    SpeechRecognizer.ERROR_NETWORK ->
        "系统语音识别网络错误：当前设备可能没有可离线使用的识别引擎，或 Google 语音服务无法联网。可先用文字输入，或安装/更新支持离线识别的语音服务。"

    SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
        "系统语音识别网络超时：当前识别引擎可能依赖云端服务。可先用文字输入，或安装/更新支持离线识别的语音服务。"
    SpeechRecognizer.ERROR_NO_MATCH -> "没有识别到问题"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音识别忙，请稍后再试"
    SpeechRecognizer.ERROR_SERVER -> "语音识别服务错误"
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有听到语音"
    else -> "语音识别失败：$this"
}
