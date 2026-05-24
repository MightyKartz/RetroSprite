package com.retrosprite.app.ui.integration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaEndpointCommitGateTest {

    @Test
    fun `endpoint waits for tail grace before committing final text`() {
        val gate = SherpaEndpointCommitGate(tailGraceMillis = 650L)

        assertFalse(
            gate.shouldCommit(
                nowMillis = 1_000L,
                endpointDetected = true,
                partialText = "气合之",
            )
        )
        assertFalse(
            gate.shouldCommit(
                nowMillis = 1_500L,
                endpointDetected = true,
                partialText = "气合之",
            )
        )
        assertTrue(
            gate.shouldCommit(
                nowMillis = 1_700L,
                endpointDetected = true,
                partialText = "气合之",
            )
        )
    }

    @Test
    fun `growing partial text resets endpoint tail grace`() {
        val gate = SherpaEndpointCommitGate(tailGraceMillis = 650L)

        assertFalse(gate.shouldCommit(1_000L, endpointDetected = true, partialText = "气合之"))
        assertFalse(gate.shouldCommit(1_500L, endpointDetected = true, partialText = "气合之玉"))
        assertFalse(gate.shouldCommit(2_000L, endpointDetected = true, partialText = "气合之玉"))
        assertTrue(gate.shouldCommit(2_200L, endpointDetected = true, partialText = "气合之玉"))
    }

    @Test
    fun `blank endpoint text never commits`() {
        val gate = SherpaEndpointCommitGate(tailGraceMillis = 650L)

        assertFalse(gate.shouldCommit(1_000L, endpointDetected = true, partialText = ""))
        assertFalse(gate.shouldCommit(2_000L, endpointDetected = true, partialText = " "))
    }
}
