package com.retrosprite.app.endpoint

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers every documented edge case of [LabelParser.parse]. The parser is the single source
 * of truth for `system` / `game` extraction and feeds both [RequestLogger] and (later) the
 * RAG retrieval key, so behavior here is locked in.
 */
class LabelParserTest {

    @Test
    fun `standard label splits on first delimiter`() {
        val parsed = LabelParser.parse("snes__super_mario_world")
        assertEquals("snes", parsed.system)
        assertEquals("super_mario_world", parsed.game)
    }

    @Test
    fun `label with multiple double underscores keeps later ones inside game`() {
        val parsed = LabelParser.parse("a__b__c")
        assertEquals("a", parsed.system)
        assertEquals("b__c", parsed.game)
    }

    @Test
    fun `label without delimiter falls back to system only`() {
        val parsed = LabelParser.parse("snes")
        assertEquals("snes", parsed.system)
        assertEquals("", parsed.game)
    }

    @Test
    fun `trailing delimiter yields empty game`() {
        val parsed = LabelParser.parse("snes__")
        assertEquals("snes", parsed.system)
        assertEquals("", parsed.game)
    }

    @Test
    fun `leading delimiter yields empty system`() {
        val parsed = LabelParser.parse("__game")
        assertEquals("", parsed.system)
        assertEquals("game", parsed.game)
    }

    @Test
    fun `null label yields empty parts`() {
        val parsed = LabelParser.parse(null)
        assertEquals(ParsedLabel.EMPTY, parsed)
    }

    @Test
    fun `blank label yields empty parts`() {
        val parsed = LabelParser.parse("   ")
        assertEquals(ParsedLabel.EMPTY, parsed)
    }

    @Test
    fun `single underscore is not treated as delimiter`() {
        val parsed = LabelParser.parse("snes_super")
        assertEquals("snes_super", parsed.system)
        assertEquals("", parsed.game)
    }
}
