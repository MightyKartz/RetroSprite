package com.retrosprite.app.ui.integration

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceSampleFanOutTest {

    @Test
    fun `publishes amplitude before enqueueing samples for asr decode`() {
        val events = mutableListOf<String>()
        var publishedAmplitude = 0f
        val fanOut = VoiceSampleFanOut(
            publishAmplitude = { amplitude ->
                events += "amplitude"
                publishedAmplitude = amplitude
            },
            enqueueForDecode = { samples ->
                events += "decode:${samples.size}"
            },
        )

        fanOut.dispatch(floatArrayOf(0.5f, -0.5f))

        assertEquals(listOf("amplitude", "decode:2"), events)
        assertEquals(0.5f, publishedAmplitude, 0.0001f)
    }

    @Test
    fun `rms amplitude is zero for silence and bounded for loud frames`() {
        assertEquals(0f, FloatArray(320).rmsAmplitude(), 0.0001f)
        assertEquals(1f, floatArrayOf(1.4f, -1.4f).rmsAmplitude(), 0.0001f)
    }
}
