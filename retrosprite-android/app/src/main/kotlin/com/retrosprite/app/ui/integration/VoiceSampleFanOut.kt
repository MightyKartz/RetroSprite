package com.retrosprite.app.ui.integration

import kotlin.math.sqrt

internal class VoiceSampleFanOut(
    private val publishAmplitude: (Float) -> Unit,
    private val enqueueForDecode: (FloatArray) -> Unit,
) {
    fun dispatch(samples: FloatArray) {
        publishAmplitude(samples.rmsAmplitude())
        enqueueForDecode(samples)
    }
}

internal fun FloatArray.rmsAmplitude(): Float {
    if (isEmpty()) return 0f
    var sum = 0.0
    for (sample in this) {
        sum += sample * sample
    }
    return sqrt(sum / size).toFloat().coerceIn(0f, 1f)
}
