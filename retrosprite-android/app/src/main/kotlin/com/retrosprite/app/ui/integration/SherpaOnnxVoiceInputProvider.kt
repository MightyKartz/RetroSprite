package com.retrosprite.app.ui.integration

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineStream
import com.retrosprite.app.ui.viewmodel.UiVoiceInputState
import com.retrosprite.app.ui.viewmodel.VoiceInputProvider
import com.retrosprite.app.voice.asr.AsrRecognitionContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SherpaOnnxVoiceInputProvider(
    context: Context,
    private val scope: CoroutineScope,
    private val model: SherpaOnnxAsrModel = SherpaOnnxAsrModel.defaultModel(),
) : VoiceInputProvider {

    private val appContext = context.applicationContext
    private val assets: AssetManager = appContext.assets
    private val mutex = Mutex()

    private val _state = MutableStateFlow(initialState())
    override val state: StateFlow<UiVoiceInputState> = _state.asStateFlow()
    override val requiresRecordAudioPermission: Boolean = true

    @Volatile
    private var shouldRecord: Boolean = false
    private var recognizer: OnlineRecognizer? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var eventId: Long = 0L

    override suspend fun startListening(context: AsrRecognitionContext?) {
        mutex.withLock {
            if (_state.value.isListening) return

            val missing = missingModelAssets()
            if (missing.isNotEmpty()) {
                _state.value = UiVoiceInputState(
                    isAvailable = false,
                    isListening = false,
                    engineLabel = model.engineLabel,
                    errorMessage = model.missingAssetsMessage(missing),
                ).withAsrDiagnostics()
                return
            }

            val activeRecognizer = try {
                val isFirstLoad = recognizer == null
                if (isFirstLoad) {
                    _state.update {
                        it.copy(
                            isAvailable = true,
                            isListening = false,
                            engineLabel = model.engineLabel,
                            statusMessage = "首次加载本地 ASR 模型，可能需要几秒钟…",
                            errorMessage = null,
                        ).withAsrDiagnostics()
                    }
                }
                recognizerFor()
            } catch (error: Throwable) {
                _state.value = UiVoiceInputState(
                    isAvailable = false,
                    isListening = false,
                    engineLabel = model.engineLabel,
                    statusMessage = null,
                    errorMessage = "sherpa-onnx 本地 ASR 初始化失败：${error.humanMessage()}",
                ).withAsrDiagnostics()
                return
            }

            val record = try {
                createAudioRecord()
            } catch (error: Throwable) {
                _state.update {
                    it.copy(
                        isAvailable = true,
                        isListening = false,
                        engineLabel = model.engineLabel,
                        statusMessage = null,
                        errorMessage = "麦克风初始化失败：${error.humanMessage()}",
                    )
                }
                return
            }

            val stream = runCatching { activeRecognizer.createStream() }
                .getOrElse { error ->
                    releaseAudioRecord(record)
                    _state.update {
                        it.copy(
                            isListening = false,
                            statusMessage = null,
                            errorMessage = "sherpa-onnx 本地 ASR 创建音频流失败：${error.humanMessage()}",
                        )
                    }
                    return
                }

            try {
                record.startRecording()
            } catch (error: Throwable) {
                stream.release()
                releaseAudioRecord(record)
                _state.update {
                    it.copy(
                        isListening = false,
                        statusMessage = null,
                        errorMessage = "录音启动失败：${error.humanMessage()}",
                    )
                }
                return
            }

            shouldRecord = true
            audioRecord = record
            _state.update {
                it.copy(
                    isAvailable = true,
                    isListening = true,
                    transcript = null,
                    amplitude = 0f,
                    engineLabel = model.engineLabel,
                    statusMessage = null,
                    errorMessage = null,
                    asrCommitReason = null,
                    asrLastPartial = null,
                    asrFinalText = null,
                    asrSelectedTranscript = null,
                    asrPostVoiceSilenceMillis = null,
                    asrPartialStableMillis = null,
                    asrRequiredStableMillis = null,
                    asrEndpointArmed = null,
                    asrFinalFlushMillis = null,
                ).withAsrDiagnostics()
            }

            recordingJob = scope.launch(Dispatchers.Default) {
                processSamples(
                    recognizer = activeRecognizer,
                    stream = stream,
                    record = record,
                )
            }
        }
    }

    override suspend fun stopListening() {
        val job = mutex.withLock {
            if (!_state.value.isListening) return
            shouldRecord = false
            runCatching { audioRecord?.stop() }
            recordingJob
        }
        job?.join()
    }

    override suspend fun cancelListening() {
        val job = mutex.withLock {
            shouldRecord = false
            _state.update { it.copy(isListening = false, statusMessage = null, errorMessage = null) }
            runCatching { audioRecord?.stop() }
            recordingJob
        }
        job?.cancelAndJoin()
    }

    private suspend fun processSamples(
        recognizer: OnlineRecognizer,
        stream: OnlineStream,
        record: AudioRecord,
    ) {
        try {
            val finalText = coroutineScope {
                val decodeFrames = Channel<FloatArray>(Channel.UNLIMITED)
                val decodeJob = async(Dispatchers.Default) {
                    decodeSamples(
                        recognizer = recognizer,
                        stream = stream,
                        frames = decodeFrames,
                    )
                }
                decodeJob.invokeOnCompletion { error ->
                    if (error != null) {
                        shouldRecord = false
                        runCatching { record.stop() }
                    }
                }

                val fanOut = VoiceSampleFanOut(
                    publishAmplitude = { amplitude ->
                        _state.update { it.copy(amplitude = amplitude) }
                    },
                    enqueueForDecode = { samples ->
                        decodeFrames.trySend(samples)
                    },
                )

                try {
                    val buffer = ShortArray(
                        (model.sampleRateHz * VISUAL_FRAME_MS / 1_000).coerceAtLeast(1)
                    )
                    while (shouldRecord && coroutineContext.isActive) {
                        val read = runCatching { record.read(buffer, 0, buffer.size) }.getOrDefault(0)
                        if (read <= 0) continue

                        val samples = FloatArray(read) { index -> buffer[index] / PCM_FLOAT_SCALE }
                        fanOut.dispatch(samples)
                    }
                } finally {
                    decodeFrames.close()
                }
                decodeJob.await()
            }

            publishFinalTranscript(finalText)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            _state.update {
                it.copy(
                    isListening = false,
                    statusMessage = null,
                    errorMessage = "sherpa-onnx 本地 ASR 识别失败：${error.humanMessage()}",
                )
            }
        } finally {
            shouldRecord = false
            releaseAudioRecord(record)
            stream.release()
            if (audioRecord === record) {
                audioRecord = null
            }
        }
    }

    private suspend fun decodeSamples(
        recognizer: OnlineRecognizer,
        stream: OnlineStream,
        frames: ReceiveChannel<FloatArray>,
    ): String {
        var latestText = ""
        val endpointCommitGate = SherpaEndpointCommitGate()
        for (samples in frames) {
            stream.acceptWaveform(samples, model.sampleRateHz)
            while (recognizer.isReady(stream)) {
                recognizer.decode(stream)
            }

            val partial = recognizer.getResult(stream).text.trim()
            if (partial.isNotBlank()) {
                latestText = partial
                _state.update {
                    it.copy(
                        transcript = partial,
                        statusMessage = null,
                        errorMessage = null,
                        asrLastPartial = partial,
                    )
                }
            }

            val commitDecision = endpointCommitGate.evaluate(
                nowMillis = SystemClock.elapsedRealtime(),
                endpointDetected = recognizer.isEndpoint(stream),
                partialText = partial,
                frameAmplitude = samples.rmsAmplitude(),
            )
            _state.update { it.withCommitDecision(commitDecision) }
            if (commitDecision.state == SherpaEndpointCommitState.RequestStopRecording) {
                // Stop recording, but keep decoding queued frames so the tail syllable survives.
                shouldRecord = false
            }
        }
        return finishStream(recognizer, stream, latestText)
    }

    private fun finishStream(
        recognizer: OnlineRecognizer,
        stream: OnlineStream,
        latestText: String,
    ): String {
        stream.acceptWaveform(
            FloatArray((model.sampleRateHz * FINAL_FLUSH_SILENCE_SECONDS).toInt()),
            model.sampleRateHz,
        )
        stream.inputFinished()
        while (recognizer.isReady(stream)) {
            recognizer.decode(stream)
        }
        val finalText = recognizer.getResult(stream).text.trim()
        val selectedTranscript = SherpaFinalTranscriptSelector.chooseFinalTranscript(
            finalText = finalText,
            latestPartialText = latestText,
        )
        _state.update {
            it.copy(
                asrFinalText = finalText.takeIf { text -> text.isNotBlank() },
                asrSelectedTranscript = selectedTranscript.takeIf { text -> text.isNotBlank() },
                asrFinalFlushMillis = (FINAL_FLUSH_SILENCE_SECONDS * 1_000).toLong(),
            )
        }
        return selectedTranscript
    }

    private fun publishFinalTranscript(text: String) {
        if (text.isBlank()) {
            _state.update {
                it.copy(
                    isListening = false,
                    statusMessage = null,
                    errorMessage = "没有识别到问题，可再试一次或使用文字输入。",
                )
            }
            return
        }
        eventId += 1
        _state.value = _state.value.copy(
            isAvailable = true,
            isListening = false,
            transcript = text,
            transcriptEventId = eventId,
            engineLabel = model.engineLabel,
            statusMessage = null,
            errorMessage = null,
        )
    }

    private suspend fun recognizerFor(): OnlineRecognizer {
        recognizer?.let { return it }
        return withContext(Dispatchers.Default) {
            SherpaOnnxRecognizerFactory.create(
                assetManager = assets,
                model = model,
            )
        }.also {
            recognizer = it
        }
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(): AudioRecord {
        val minBufferBytes = AudioRecord.getMinBufferSize(
            model.sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(minBufferBytes > 0) { "AudioRecord buffer size unavailable: $minBufferBytes" }

        return AudioRecord(
            MediaRecorder.AudioSource.MIC,
            model.sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferBytes * 2,
        )
    }

    private fun initialState(): UiVoiceInputState {
        val missing = missingModelAssets()
        return UiVoiceInputState(
            isAvailable = missing.isEmpty(),
            isListening = false,
            engineLabel = model.engineLabel,
            errorMessage = missing.takeIf { it.isNotEmpty() }?.let(model::missingAssetsMessage),
        ).withAsrDiagnostics()
    }

    private fun missingModelAssets(): List<String> =
        model.requiredAssetPaths.filterNot(::assetExists)

    private fun assetExists(path: String): Boolean =
        runCatching {
            assets.open(path).use { /* existence check only */ }
        }.isSuccess

    private fun releaseAudioRecord(record: AudioRecord) {
        runCatching { record.stop() }
        runCatching { record.release() }
    }

    private fun Throwable.humanMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: this::class.java.simpleName

    internal companion object {
        const val FINAL_FLUSH_SILENCE_SECONDS: Float = 2.0f
        const val PCM_FLOAT_SCALE = 32768.0f
        const val VISUAL_FRAME_MS = 10
    }
}

private fun UiVoiceInputState.withAsrDiagnostics(): UiVoiceInputState =
    copy(
        asrArchitecture = "paraformer",
        asrDecodingMethod = "greedy_search",
        asrModelingUnit = null,
    )

private fun UiVoiceInputState.withCommitDecision(
    decision: SherpaEndpointCommitDecision,
): UiVoiceInputState =
    copy(
        asrCommitReason = decision.reason,
        asrLastPartial = decision.partialText ?: asrLastPartial,
        asrPostVoiceSilenceMillis = decision.postVoiceSilenceMillis,
        asrPartialStableMillis = decision.partialStableMillis,
        asrRequiredStableMillis = decision.requiredStableMillis,
        asrEndpointArmed = decision.endpointArmed,
    )
