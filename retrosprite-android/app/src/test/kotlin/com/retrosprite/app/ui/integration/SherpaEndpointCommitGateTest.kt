package com.retrosprite.app.ui.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaEndpointCommitGateTest {

    @Test
    fun `default endpoint gate waits for silence and stable partial text`() {
        assertEquals(900L, SherpaEndpointCommitGate.DEFAULT_POST_VOICE_SILENCE_MILLIS)
        assertEquals(650L, SherpaEndpointCommitGate.DEFAULT_PARTIAL_STABLE_MILLIS)
        assertEquals(1_500L, SherpaEndpointCommitGate.DEFAULT_INCOMPLETE_QUESTION_TAIL_EXTRA_MILLIS)
        assertEquals(1_200L, SherpaEndpointCommitGate.DEFAULT_SHORT_FRAGMENT_EXTRA_MILLIS)
        assertEquals(0.012f, SherpaEndpointCommitGate.DEFAULT_VOICE_ACTIVITY_THRESHOLD, 0.0001f)
    }

    @Test
    fun `endpoint requests recording stop after voice activity ends and partial text is stable`() {
        val gate = SherpaEndpointCommitGate(
            postVoiceSilenceMillis = 900L,
            partialStableMillis = 600L,
            voiceActivityThreshold = 0.01f,
        )

        assertEquals(
            SherpaEndpointCommitState.KeepListening,
            gate.shouldCommit(
                nowMillis = 1_000L,
                endpointDetected = false,
                partialText = "气合之",
                frameAmplitude = 0.03f,
            ),
        )
        assertEquals(
            SherpaEndpointCommitState.KeepListening,
            gate.shouldCommit(
                nowMillis = 1_200L,
                endpointDetected = true,
                partialText = "气合之玉",
                frameAmplitude = 0.03f,
            ),
        )
        assertEquals(
            SherpaEndpointCommitState.KeepListening,
            gate.shouldCommit(
                nowMillis = 1_900L,
                endpointDetected = false,
                partialText = "气合之玉",
                frameAmplitude = 0.0f,
            ),
        )
        assertEquals(
            SherpaEndpointCommitState.RequestStopRecording,
            gate.shouldCommit(
                nowMillis = 2_100L,
                endpointDetected = false,
                partialText = "气合之玉",
                frameAmplitude = 0.0f,
            ),
        )
    }

    @Test
    fun `decision exposes stable and silence timing before stop`() {
        val gate = SherpaEndpointCommitGate(
            postVoiceSilenceMillis = 900L,
            partialStableMillis = 650L,
            voiceActivityThreshold = 0.01f,
        )

        val waiting = gate.evaluate(
            nowMillis = 1_000L,
            endpointDetected = true,
            partialText = "气合之玉怎么用",
            frameAmplitude = 0.001f,
        )

        assertEquals(SherpaEndpointCommitState.KeepListening, waiting.state)
        assertEquals("waiting_for_silence_or_stable_partial", waiting.reason)
        assertTrue(waiting.endpointArmed)
        assertEquals("气合之玉怎么用", waiting.partialText)
        assertEquals(0L, waiting.postVoiceSilenceMillis)
        assertEquals(0L, waiting.partialStableMillis)
        assertEquals(650L, waiting.requiredStableMillis)

        val stop = gate.evaluate(
            nowMillis = 2_000L,
            endpointDetected = false,
            partialText = "气合之玉怎么用",
            frameAmplitude = 0.001f,
        )

        assertEquals(SherpaEndpointCommitState.RequestStopRecording, stop.state)
        assertEquals("soft_stop_after_silence_and_stable_partial", stop.reason)
        assertEquals(1_000L, stop.postVoiceSilenceMillis)
        assertEquals(1_000L, stop.partialStableMillis)
        assertEquals(650L, stop.requiredStableMillis)
    }

    @Test
    fun `continued voice after endpoint delays recording stop`() {
        val gate = SherpaEndpointCommitGate(
            postVoiceSilenceMillis = 900L,
            partialStableMillis = 600L,
            voiceActivityThreshold = 0.01f,
        )

        assertEquals(
            SherpaEndpointCommitState.KeepListening,
            gate.shouldCommit(1_000L, endpointDetected = true, partialText = "指挥官", frameAmplitude = 0.03f),
        )
        assertEquals(
            SherpaEndpointCommitState.KeepListening,
            gate.shouldCommit(1_700L, endpointDetected = false, partialText = "指挥官", frameAmplitude = 0.03f),
        )
        assertEquals(
            SherpaEndpointCommitState.KeepListening,
            gate.shouldCommit(2_500L, endpointDetected = false, partialText = "指挥官", frameAmplitude = 0.0f),
        )
        assertEquals(
            SherpaEndpointCommitState.RequestStopRecording,
            gate.shouldCommit(2_600L, endpointDetected = false, partialText = "指挥官", frameAmplitude = 0.0f),
        )
    }

    @Test
    fun `growing partial text resets the stability timer`() {
        val gate = SherpaEndpointCommitGate(
            postVoiceSilenceMillis = 700L,
            partialStableMillis = 600L,
            voiceActivityThreshold = 0.01f,
        )

        assertEquals(
            SherpaEndpointCommitState.KeepListening,
            gate.shouldCommit(1_000L, endpointDetected = true, partialText = "气合之", frameAmplitude = 0.03f),
        )
        assertEquals(
            SherpaEndpointCommitState.KeepListening,
            gate.shouldCommit(1_300L, endpointDetected = false, partialText = "气合之玉", frameAmplitude = 0.0f),
        )
        assertEquals(
            SherpaEndpointCommitState.KeepListening,
            gate.shouldCommit(1_800L, endpointDetected = false, partialText = "气合之玉", frameAmplitude = 0.0f),
        )
        assertEquals(
            SherpaEndpointCommitState.RequestStopRecording,
            gate.shouldCommit(1_900L, endpointDetected = false, partialText = "气合之玉", frameAmplitude = 0.0f),
        )
    }

    @Test
    fun `blank endpoint text never commits`() {
        val gate = SherpaEndpointCommitGate(
            postVoiceSilenceMillis = 700L,
            partialStableMillis = 600L,
            voiceActivityThreshold = 0.01f,
        )

        assertEquals(
            SherpaEndpointCommitState.KeepListening,
            gate.shouldCommit(1_000L, endpointDetected = true, partialText = "", frameAmplitude = 0.0f),
        )
        assertEquals(
            SherpaEndpointCommitState.KeepListening,
            gate.shouldCommit(2_000L, endpointDetected = true, partialText = " ", frameAmplitude = 0.0f),
        )
    }

    @Test
    fun `non endpoint audio does not arm recording stop`() {
        val gate = SherpaEndpointCommitGate(
            postVoiceSilenceMillis = 700L,
            partialStableMillis = 600L,
            voiceActivityThreshold = 0.01f,
        )

        assertEquals(
            SherpaEndpointCommitState.KeepListening,
            gate.shouldCommit(1_000L, endpointDetected = false, partialText = "克拉肯怎么", frameAmplitude = 0.03f),
        )
        assertEquals(
            SherpaEndpointCommitState.KeepListening,
            gate.shouldCommit(2_300L, endpointDetected = false, partialText = "克拉肯怎么", frameAmplitude = 0.0f),
        )
        assertEquals(
            SherpaEndpointCommitState.KeepListening,
            gate.shouldCommit(2_400L, endpointDetected = true, partialText = "克拉肯怎么过", frameAmplitude = 0.0f),
        )
        assertEquals(
            SherpaEndpointCommitState.RequestStopRecording,
            gate.shouldCommit(3_100L, endpointDetected = false, partialText = "克拉肯怎么过", frameAmplitude = 0.0f),
        )
    }

    @Test
    fun `incomplete question tail waits a little longer without inventing text`() {
        val gate = SherpaEndpointCommitGate(
            postVoiceSilenceMillis = 500L,
            partialStableMillis = 500L,
            incompleteQuestionTailExtraMillis = 800L,
            voiceActivityThreshold = 0.01f,
        )

        assertEquals(
            SherpaEndpointCommitState.KeepListening,
            gate.shouldCommit(1_000L, endpointDetected = true, partialText = "指挥官是什", frameAmplitude = 0.03f),
        )
        assertEquals(
            SherpaEndpointCommitState.KeepListening,
            gate.shouldCommit(1_500L, endpointDetected = false, partialText = "指挥官是什", frameAmplitude = 0.0f),
        )
        assertEquals(
            SherpaEndpointCommitState.KeepListening,
            gate.shouldCommit(2_200L, endpointDetected = false, partialText = "指挥官是什", frameAmplitude = 0.0f),
        )
        assertEquals(
            SherpaEndpointCommitState.RequestStopRecording,
            gate.shouldCommit(2_300L, endpointDetected = false, partialText = "指挥官是什", frameAmplitude = 0.0f),
        )
    }

    @Test
    fun `two character ambiguous fragment waits beyond normal stable window`() {
        val gate = SherpaEndpointCommitGate(
            postVoiceSilenceMillis = 500L,
            partialStableMillis = 500L,
            incompleteQuestionTailExtraMillis = 900L,
            shortFragmentExtraMillis = 1_200L,
            voiceActivityThreshold = 0.01f,
        )

        assertEquals(
            SherpaEndpointCommitState.KeepListening,
            gate.shouldCommit(1_000L, endpointDetected = true, partialText = "关是", frameAmplitude = 0.03f),
        )
        val waiting = gate.evaluate(
            nowMillis = 2_000L,
            endpointDetected = false,
            partialText = "关是",
            frameAmplitude = 0.0f,
        )

        assertEquals(SherpaEndpointCommitState.KeepListening, waiting.state)
        assertEquals("waiting_for_short_fragment", waiting.reason)
        assertEquals(1_700L, waiting.requiredStableMillis)

        val stop = gate.evaluate(
            nowMillis = 2_700L,
            endpointDetected = false,
            partialText = "关是",
            frameAmplitude = 0.0f,
        )

        assertEquals(SherpaEndpointCommitState.RequestStopRecording, stop.state)
    }

    @Test
    fun `three character broad game question can still stop after normal grace`() {
        val gate = SherpaEndpointCommitGate(
            postVoiceSilenceMillis = 500L,
            partialStableMillis = 500L,
            voiceActivityThreshold = 0.01f,
        )

        assertEquals(
            SherpaEndpointCommitState.KeepListening,
            gate.shouldCommit(1_000L, endpointDetected = true, partialText = "这游戏", frameAmplitude = 0.03f),
        )
        val decision = gate.evaluate(
            nowMillis = 1_600L,
            endpointDetected = false,
            partialText = "这游戏",
            frameAmplitude = 0.0f,
        )

        assertEquals(SherpaEndpointCommitState.RequestStopRecording, decision.state)
        assertEquals("soft_stop_after_silence_and_stable_partial", decision.reason)
    }
}
