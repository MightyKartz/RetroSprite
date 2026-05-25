package com.retrosprite.app.voice.asr

data class AsrRecognitionContext(
    val label: String,
    val gameId: String?,
    val spoilerLevel: String,
    val source: String,
)
