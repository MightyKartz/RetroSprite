package com.retrosprite.app.domain.policy

import com.retrosprite.app.domain.models.AnswerDecision
import com.retrosprite.app.domain.models.ControllerState
import com.retrosprite.app.domain.models.GameIdentity
import com.retrosprite.app.domain.models.SessionContext
import com.retrosprite.app.domain.models.SpoilerLevel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedTextAnswerPolicyTest {

    private val policy = FixedTextAnswerPolicy()

    private fun ctx(): SessionContext = SessionContext(
        gameIdentity = GameIdentity.unknown(),
        playerQuestion = null,
        screenshotBase64 = null,
        state = ControllerState.EMPTY,
        spoilerLevel = SpoilerLevel.LIGHT,
        language = "zh",
        recentTurns = emptyList(),
    )

    @Test
    fun `returns DirectAnswer with the fixed phase 0 text on empty results`() = runTest {
        val decision = policy.decide(results = emptyList(), context = ctx())

        assertTrue(decision is AnswerDecision.DirectAnswer)
        decision as AnswerDecision.DirectAnswer
        assertEquals(FixedTextAnswerPolicy.PHASE_0_ACK_TEXT, decision.text)
        assertEquals(emptyList<String>(), decision.sources)
        assertEquals(SpoilerLevel.LIGHT, decision.spoilerLevel)
    }

    @Test
    fun `phase 0 acknowledgement text is at most 3 sentences`() = runTest {
        // Product rule: answers are capped at 3 sentences. Phase 0 text uses
        // CJK full-stops 「。」 — count those plus any latin '.' to be safe.
        val text = FixedTextAnswerPolicy.PHASE_0_ACK_TEXT
        val sentenceTerminators = text.count { it == '。' || it == '.' }
        assertTrue(
            "Phase 0 ack text exceeds 3 sentences: <$text>",
            sentenceTerminators in 1..3,
        )
    }
}
