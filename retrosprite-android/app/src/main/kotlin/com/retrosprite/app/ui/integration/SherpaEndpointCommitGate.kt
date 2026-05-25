package com.retrosprite.app.ui.integration

/**
 * Requests a soft stop after real voice activity has ended and the partial
 * transcript has stopped changing. The decoder can still drain queued frames
 * before committing the final transcript.
 */
internal enum class SherpaEndpointCommitState {
    KeepListening,
    RequestStopRecording,
}

internal data class SherpaEndpointCommitDecision(
    val state: SherpaEndpointCommitState,
    val reason: String,
    val endpointArmed: Boolean,
    val partialText: String?,
    val postVoiceSilenceMillis: Long?,
    val partialStableMillis: Long?,
    val requiredStableMillis: Long?,
    val frameAmplitude: Float,
)

internal class SherpaEndpointCommitGate(
    private val postVoiceSilenceMillis: Long = DEFAULT_POST_VOICE_SILENCE_MILLIS,
    private val partialStableMillis: Long = DEFAULT_PARTIAL_STABLE_MILLIS,
    private val incompleteQuestionTailExtraMillis: Long = DEFAULT_INCOMPLETE_QUESTION_TAIL_EXTRA_MILLIS,
    private val shortFragmentExtraMillis: Long = DEFAULT_SHORT_FRAGMENT_EXTRA_MILLIS,
    private val voiceActivityThreshold: Float = DEFAULT_VOICE_ACTIVITY_THRESHOLD,
) {
    private var endpointArmed: Boolean = false
    private var lastVoiceActivityMillis: Long? = null
    private var latestPartialText: String? = null
    private var partialStableSinceMillis: Long? = null

    fun shouldCommit(
        nowMillis: Long,
        endpointDetected: Boolean,
        partialText: String,
        frameAmplitude: Float,
    ): SherpaEndpointCommitState =
        evaluate(
            nowMillis = nowMillis,
            endpointDetected = endpointDetected,
            partialText = partialText,
            frameAmplitude = frameAmplitude,
        ).state

    fun evaluate(
        nowMillis: Long,
        endpointDetected: Boolean,
        partialText: String,
        frameAmplitude: Float,
    ): SherpaEndpointCommitDecision {
        if (frameAmplitude >= voiceActivityThreshold) {
            lastVoiceActivityMillis = nowMillis
        }

        val clean = partialText.trim()
        if (clean.isBlank()) {
            return decision(
                state = SherpaEndpointCommitState.KeepListening,
                reason = "blank_partial",
                partialText = null,
                frameAmplitude = frameAmplitude,
            )
        }

        if (latestPartialText != clean) {
            latestPartialText = clean
            partialStableSinceMillis = nowMillis
        }

        if (endpointDetected) {
            endpointArmed = true
            if (lastVoiceActivityMillis == null) {
                lastVoiceActivityMillis = nowMillis
            }
        }
        if (!endpointArmed) {
            return decision(
                state = SherpaEndpointCommitState.KeepListening,
                reason = "endpoint_not_armed",
                partialText = clean,
                frameAmplitude = frameAmplitude,
            )
        }

        val stableSince = partialStableSinceMillis ?: return decision(
            state = SherpaEndpointCommitState.KeepListening,
            reason = "waiting_for_silence_or_stable_partial",
            partialText = clean,
            frameAmplitude = frameAmplitude,
        )
        val lastVoiceAt = lastVoiceActivityMillis ?: return decision(
            state = SherpaEndpointCommitState.KeepListening,
            reason = "waiting_for_silence_or_stable_partial",
            partialText = clean,
            frameAmplitude = frameAmplitude,
        )
        val requiredStableMillis = partialStableMillis + clean.extraStableMillis()
        val postVoiceSilenceElapsedMillis = nowMillis - lastVoiceAt
        val partialStableElapsedMillis = nowMillis - stableSince
        val state = if (postVoiceSilenceElapsedMillis >= postVoiceSilenceMillis &&
            partialStableElapsedMillis >= requiredStableMillis
        ) {
            SherpaEndpointCommitState.RequestStopRecording
        } else {
            SherpaEndpointCommitState.KeepListening
        }
        val reason = when {
            state == SherpaEndpointCommitState.RequestStopRecording ->
                "soft_stop_after_silence_and_stable_partial"
            clean.hasIncompleteQuestionTail() && partialStableElapsedMillis < requiredStableMillis ->
                "waiting_for_incomplete_question_tail"
            clean.needsShortFragmentExtraWait() && partialStableElapsedMillis < requiredStableMillis ->
                "waiting_for_short_fragment"
            else -> "waiting_for_silence_or_stable_partial"
        }
        return decision(
            state = state,
            reason = reason,
            partialText = clean,
            postVoiceSilenceElapsedMillis = postVoiceSilenceElapsedMillis,
            partialStableElapsedMillis = partialStableElapsedMillis,
            requiredStableMillis = requiredStableMillis,
            frameAmplitude = frameAmplitude,
        )
    }

    private fun decision(
        state: SherpaEndpointCommitState,
        reason: String,
        partialText: String?,
        postVoiceSilenceElapsedMillis: Long? = null,
        partialStableElapsedMillis: Long? = null,
        requiredStableMillis: Long? = null,
        frameAmplitude: Float,
    ): SherpaEndpointCommitDecision =
        SherpaEndpointCommitDecision(
            state = state,
            reason = reason,
            endpointArmed = endpointArmed,
            partialText = partialText,
            postVoiceSilenceMillis = postVoiceSilenceElapsedMillis,
            partialStableMillis = partialStableElapsedMillis,
            requiredStableMillis = requiredStableMillis,
            frameAmplitude = frameAmplitude,
        )

    private fun String.extraStableMillis(): Long =
        when {
            hasIncompleteQuestionTail() -> incompleteQuestionTailExtraMillis
            needsShortFragmentExtraWait() -> shortFragmentExtraMillis
            else -> 0L
        }

    private fun String.hasIncompleteQuestionTail(): Boolean =
        INCOMPLETE_QUESTION_TAILS.any { endsWith(it) }

    private fun String.needsShortFragmentExtraWait(): Boolean =
        count { it.isLetterOrDigit() } <= 2

    companion object {
        const val DEFAULT_POST_VOICE_SILENCE_MILLIS: Long = 900L
        const val DEFAULT_PARTIAL_STABLE_MILLIS: Long = 650L
        const val DEFAULT_INCOMPLETE_QUESTION_TAIL_EXTRA_MILLIS: Long = 1_500L
        const val DEFAULT_SHORT_FRAGMENT_EXTRA_MILLIS: Long = 1_200L
        const val DEFAULT_VOICE_ACTIVITY_THRESHOLD: Float = 0.012f

        private val INCOMPLETE_QUESTION_TAILS = listOf(
            "是什",
            "为什",
            "玩什",
            "做什",
            "干什",
        )
    }
}
