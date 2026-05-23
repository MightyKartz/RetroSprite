package com.retrosprite.app.data.retrieval

import com.retrosprite.app.domain.models.SpoilerLevel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TemplateAnswerSelectorTest {

    @Test
    fun `selects tiered answer by spoiler tolerance`() {
        val template = json(
            """
            {
              "answer_light": "轻提示",
              "answer_clear": "明确提示",
              "answer_direct": "直接答案",
              "spoiler_light": "light",
              "spoiler_clear": "medium",
              "spoiler_direct": "heavy"
            }
            """
        )

        assertEquals(
            SelectedTemplateAnswer("轻提示", "light"),
            TemplateAnswerSelector.select(template, SpoilerLevel.LIGHT),
        )
        assertEquals(
            SelectedTemplateAnswer("明确提示", "medium"),
            TemplateAnswerSelector.select(template, SpoilerLevel.CLEAR),
        )
        assertEquals(
            SelectedTemplateAnswer("直接答案", "heavy"),
            TemplateAnswerSelector.select(template, SpoilerLevel.FULL),
        )
    }

    @Test
    fun `falls back to plain answer when tiered answer is absent`() {
        val template = json(
            """{"answer":"普通答案","spoiler_level":"none"}"""
        )

        assertEquals(
            SelectedTemplateAnswer("普通答案", "none"),
            TemplateAnswerSelector.select(template, SpoilerLevel.LIGHT),
        )
    }

    @Test
    fun `returns null when selected text is blank`() {
        val template = json(
            """{"answer":"   ","spoiler_level":"none"}"""
        )

        assertNull(TemplateAnswerSelector.select(template, SpoilerLevel.LIGHT))
    }

    private fun json(raw: String) =
        Json.parseToJsonElement(raw.trimIndent()).jsonObject
}
