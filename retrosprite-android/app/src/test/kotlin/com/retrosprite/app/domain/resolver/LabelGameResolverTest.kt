package com.retrosprite.app.domain.resolver

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LabelGameResolverTest {

    private val resolver = LabelGameResolver()

    @Test
    fun `parses standard system__title label`() = runTest {
        val identity = resolver.resolve("snes__super_mario_world")

        assertEquals("snes", identity.platform)
        assertEquals("Super Mario World", identity.title)
        assertEquals("label", identity.source)
        assertNull(identity.gameId)
        assertNull(identity.region)
    }

    @Test
    fun `lowercases platform but title-cases title words`() = runTest {
        val identity = resolver.resolve("SNES__legend_of_zelda")

        assertEquals("snes", identity.platform)
        assertEquals("Legend Of Zelda", identity.title)
    }

    @Test
    fun `uses first __ as the separator when title contains another __`() = runTest {
        // "genesis__sonic_2__hack" -> platform alias "md", title="sonic_2__hack"
        val identity = resolver.resolve("genesis__sonic_2__hack")

        assertEquals("md", identity.platform)
        // First underscore inside the title becomes a space; the embedded
        // double-underscore collapses to a single space (whitespace-collapse
        // happens after underscore-to-space conversion).
        assertEquals("Sonic 2 Hack", identity.title)
    }

    @Test
    fun `canonicalizes mega drive platform aliases to md`() = runTest {
        assertEquals("md", resolver.resolve("mega_drive__光明力量2").platform)
        assertEquals("md", resolver.resolve("megadrive__Shining Force II").platform)
        assertEquals("md", resolver.resolve("genesis__Shining Force II").platform)
    }

    @Test
    fun `label without separator yields unknown platform and prettified title`() = runTest {
        val identity = resolver.resolve("super_mario_world")

        assertEquals("unknown", identity.platform)
        assertEquals("Super Mario World", identity.title)
        assertEquals("label", identity.source)
    }

    @Test
    fun `empty label returns unknown identity`() = runTest {
        val identity = resolver.resolve("")

        assertEquals("unknown", identity.platform)
        assertEquals("unknown", identity.title)
        assertEquals("unknown", identity.source)
    }

    @Test
    fun `blank whitespace label returns unknown identity`() = runTest {
        val identity = resolver.resolve("   \t  ")

        assertEquals("unknown", identity.platform)
        assertEquals("unknown", identity.title)
        assertEquals("unknown", identity.source)
    }

    @Test
    fun `empty title after separator falls back to unknown`() = runTest {
        // "snes__" -> platform="snes", title="" -> "unknown"
        val identity = resolver.resolve("snes__")

        assertEquals("snes", identity.platform)
        assertEquals("unknown", identity.title)
    }

    @Test
    fun `empty platform before separator falls back to unknown platform`() = runTest {
        val identity = resolver.resolve("__zelda")

        assertEquals("unknown", identity.platform)
        assertEquals("Zelda", identity.title)
    }

    @Test
    fun `single word label is title-cased`() = runTest {
        val identity = resolver.resolve("nes__contra")

        assertEquals("nes", identity.platform)
        assertEquals("Contra", identity.title)
    }

    @Test
    fun `rom hash is accepted but ignored in phase 0`() = runTest {
        val identity = resolver.resolve("snes__super_mario_world", romHash = "deadbeef")

        // Phase 0 still uses label as the source; gameId stays null.
        assertEquals("label", identity.source)
        assertNull(identity.gameId)
    }
}
