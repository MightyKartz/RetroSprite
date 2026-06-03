package com.retrosprite.app.endpoint

import com.retrosprite.app.endpoint.model.RetroArchRequest

data class RetroArchHotkeyEvent(
    val label: String,
    val outputMode: String,
    val imageBytes: Int,
    val paused: Boolean,
    val imageBase64: String = "",
    val injectedQuestion: String = "",
    val receivedAtMillis: Long = System.currentTimeMillis(),
)

fun interface RetroArchHotkeyListener {
    fun onHotkey(event: RetroArchHotkeyEvent)
}

object NoopRetroArchHotkeyListener : RetroArchHotkeyListener {
    override fun onHotkey(event: RetroArchHotkeyEvent) = Unit
}

internal fun RetroArchRequest.toHotkeyEvent(
    outputMode: String,
    receivedAtMillis: Long = System.currentTimeMillis(),
): RetroArchHotkeyEvent =
    RetroArchHotkeyEvent(
        label = label,
        outputMode = outputMode,
        imageBytes = RequestLogger.decodedBase64Length(image),
        paused = state.isPaused,
        imageBase64 = image,
        injectedQuestion = question
            .trim()
            .takeIf { outputMode.allowsDebugInjectedQuestion() }
            .orEmpty(),
        receivedAtMillis = receivedAtMillis,
    )

private fun String.allowsDebugInjectedQuestion(): Boolean =
    startsWith("hotkey_voice_debug")
