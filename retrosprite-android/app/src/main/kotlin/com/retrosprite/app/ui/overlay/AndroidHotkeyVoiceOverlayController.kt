package com.retrosprite.app.ui.overlay

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.retrosprite.app.endpoint.RequestLogger
import com.retrosprite.app.endpoint.ResponseGenerator
import com.retrosprite.app.endpoint.RetroArchHotkeyEvent
import com.retrosprite.app.endpoint.RetroArchHotkeyListener
import com.retrosprite.app.ui.viewmodel.SpeechOutputProvider
import com.retrosprite.app.ui.viewmodel.VoiceInputProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AndroidHotkeyVoiceOverlayController(
    context: Context,
    voiceInput: VoiceInputProvider,
    responseGenerator: ResponseGenerator,
    speechOutput: SpeechOutputProvider,
    loggerProvider: () -> RequestLogger,
    displayMillis: Long = DEFAULT_DISPLAY_MILLIS,
) : RetroArchHotkeyListener {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val renderer = AndroidHotkeyVoiceOverlayRenderer(appContext)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var hideRunnable: Runnable? = null

    private val coordinator: HotkeyVoiceOverlayCoordinator = HotkeyVoiceOverlayCoordinator(
        renderer = renderer,
        canDrawOverlays = { Settings.canDrawOverlays(appContext) },
        scheduleAutoHide = { action ->
            val runnable = Runnable(action)
            hideRunnable = runnable
            mainHandler.postDelayed(runnable, displayMillis)
        },
        cancelAutoHide = {
            hideRunnable?.let(mainHandler::removeCallbacks)
            hideRunnable = null
        },
    )

    val state = coordinator.state

    private val voiceQuestionController = HotkeyVoiceQuestionController(
        coordinator = coordinator,
        voiceInput = voiceInput,
        responseGenerator = responseGenerator,
        speechOutput = speechOutput,
        loggerProvider = loggerProvider,
        scope = mainScope,
    )

    override fun onHotkey(event: RetroArchHotkeyEvent) {
        mainHandler.post {
            voiceQuestionController.onHotkey(event)
        }
    }

    private companion object {
        const val DEFAULT_DISPLAY_MILLIS: Long = 8_000L
    }
}
