package com.retrosprite.app.domain.intent

import com.retrosprite.app.domain.models.AnswerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalQuestionFrameParserTest {

    @Test
    fun `detects broad gameplay overview questions`() {
        val frame = NaturalQuestionFrameParser.parse("这游戏怎么玩？")

        assertEquals(AnswerType.GameOverview, frame.answerType)
        assertEquals("game_overview", frame.intentDetail)
        assertEquals("这游戏怎么玩", frame.normalizedQuestion)
        assertFalse(frame.needsProgressContext)
    }

    @Test
    fun `detects current team building questions as progress sensitive`() {
        val frame = NaturalQuestionFrameParser.parse("现在哪些角色适合培养？")

        assertEquals(AnswerType.TeamBuild, frame.answerType)
        assertEquals("team_build", frame.intentDetail)
        assertTrue(frame.asksCurrentProgress)
        assertTrue(frame.needsProgressContext)
    }

    @Test
    fun `detects leveling questions without requiring progress context`() {
        val frame = NaturalQuestionFrameParser.parse("怎么玩经验高？")

        assertEquals(AnswerType.Leveling, frame.answerType)
        assertEquals("leveling", frame.intentDetail)
        assertFalse(frame.needsProgressContext)
    }

    @Test
    fun `detects direct answer spoiler escalation`() {
        val frame = NaturalQuestionFrameParser.parse("直接告诉我 Mithril 在哪")

        assertEquals(AnswerType.Location, frame.answerType)
        assertTrue(frame.asksSpoilerEscalation)
    }
}
