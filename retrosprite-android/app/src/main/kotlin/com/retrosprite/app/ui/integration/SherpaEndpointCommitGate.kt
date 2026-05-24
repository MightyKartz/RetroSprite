package com.retrosprite.app.ui.integration

/**
 * Debounces sherpa-onnx endpoint detection so short endpoint pulses do not
 * commit a partial transcript before the final syllables are decoded.
 */
internal class SherpaEndpointCommitGate(
    private val tailGraceMillis: Long = DEFAULT_TAIL_GRACE_MILLIS,
) {
    private var endpointStartedAtMillis: Long? = null
    private var stablePartialText: String? = null

    fun shouldCommit(
        nowMillis: Long,
        endpointDetected: Boolean,
        partialText: String,
    ): Boolean {
        val clean = partialText.trim()
        if (!endpointDetected || clean.isBlank()) {
            reset()
            return false
        }

        if (stablePartialText != clean) {
            stablePartialText = clean
            endpointStartedAtMillis = nowMillis
            return false
        }

        val startedAt = endpointStartedAtMillis ?: nowMillis.also {
            endpointStartedAtMillis = it
        }
        return nowMillis - startedAt >= tailGraceMillis
    }

    private fun reset() {
        endpointStartedAtMillis = null
        stablePartialText = null
    }

    companion object {
        const val DEFAULT_TAIL_GRACE_MILLIS: Long = 650L
    }
}
