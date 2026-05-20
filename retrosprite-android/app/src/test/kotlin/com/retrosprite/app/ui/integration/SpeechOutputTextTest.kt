package com.retrosprite.app.ui.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechOutputTextTest {

    @Test
    fun `shortSpeechAnswer speaks first sentence and removes sources`() {
        val text = "把两个相同数字滑到一起会合并成更大的数字。注意每个方块一次移动最多合并一次。\n来源：sample.2048.rules"

        assertEquals("把两个相同数字滑到一起会合并成更大的数字", text.shortSpeechAnswer())
    }

    @Test
    fun `shortSpeechAnswer trims long answers`() {
        val answer = "a".repeat(200)

        val speech = answer.shortSpeechAnswer(maxChars = 40)

        assertEquals(40, speech.length)
        assertTrue(speech.endsWith("…"))
    }
}
