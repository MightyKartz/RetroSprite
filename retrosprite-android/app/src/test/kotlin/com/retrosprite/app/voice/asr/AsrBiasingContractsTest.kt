package com.retrosprite.app.voice.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrBiasingContractsTest {

    @Test
    fun `profile fingerprint is stable and excludes duplicate terms`() {
        val profile = AsrBiasingProfile(
            gameId = "shining_force_ii_md",
            packVersion = "0.2.5",
            entries = listOf(
                AsrHotwordEntry(term = "修伊", score = 3.5f, source = AsrHotwordSource.Alias),
                AsrHotwordEntry(term = "修伊", score = 3.5f, source = AsrHotwordSource.Alias),
                AsrHotwordEntry(term = "Chester", score = 2.0f, source = AsrHotwordSource.CanonicalName),
            ),
        )

        assertEquals(2, profile.normalizedEntries.size)
        assertTrue(profile.fingerprint.startsWith("shining_force_ii_md:0.2.5:"))
        assertFalse(profile.fingerprint.contains(" "))
    }

    @Test
    fun `parses asr mode suffix and strips diagnostic marker`() {
        val override = AsrLabelOverrideParser.parse("mega_drive__光明力量2@@asr:stream_one")

        assertEquals("mega_drive__光明力量2", override.cleanLabel)
        assertEquals(AsrHotwordMode.StreamOne, override.hotwordMode)
    }

    @Test
    fun `unknown asr mode keeps clean label and defaults to auto`() {
        val override = AsrLabelOverrideParser.parse("mega_drive__光明力量2@@asr:surprise")

        assertEquals("mega_drive__光明力量2", override.cleanLabel)
        assertEquals(AsrHotwordMode.Auto, override.hotwordMode)
    }
}
