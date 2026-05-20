package com.retrosprite.app.gkp

import com.retrosprite.app.data.gkp.GkpV0Parser
import com.retrosprite.app.data.gkp.GkpPackProvenance
import com.retrosprite.app.data.gkp.GkpSignatureStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class GkpV0ParserTest {

    private val parser = GkpV0Parser(nowMillis = { 1234L })

    @Test
    fun `parses bundled sample 2048 pack into domain rows`() {
        val parsed = parsePack("sample-2048")

        assertEquals("2048", parsed.game.gameId)
        assertEquals("sample.2048", parsed.game.packId)
        assertEquals("2048", parsed.game.title)
        assertEquals("libretro", parsed.game.platform)
        assertEquals(listOf("zh", "en"), parsed.game.languages)
        assertEquals("0.1.1", parsed.game.packVersion)
        assertEquals("gkp.v0", parsed.game.schemaVersion)
        assertEquals("sample", parsed.game.trustLevel)
        assertEquals(GkpPackProvenance.Unknown.id, parsed.game.provenance)
        assertEquals(GkpSignatureStatus.Unsigned.id, parsed.game.signatureStatus)
        assertEquals(1234L, parsed.game.installedAt)

        assertEquals(14, parsed.knowledge.size)
        val merge = parsed.knowledge.first { it.entityId == "mechanic.tile-merge" }
        assertEquals("mechanic", merge.entityType)
        assertTrue(merge.aliases.contains("合并"))
        assertEquals("none", merge.spoilerLevel)
        assertEquals(listOf("sample.2048.rules"), merge.sourceRefs)
        assertFalse(merge.answerTemplates.isEmpty())

        val snake = parsed.knowledge.first { it.entityId == "strategy.snake-order" }
        assertEquals("stable_corner", snake.progressGate)
        assertEquals("medium", snake.spoilerLevel)

        val undoRestart = parsed.knowledge.first { it.entityId == "faq.undo-restart" }
        assertEquals("faq", undoRestart.entityType)
        assertTrue(undoRestart.aliases.contains("撤销"))
        assertTrue(undoRestart.aliases.contains("重开"))
    }

    @Test
    fun `parses bundled relay station pack into domain rows`() {
        val parsed = parsePack("sample-relay-station")

        assertEquals("relay_station", parsed.game.gameId)
        assertEquals("sample.relay-station", parsed.game.packId)
        assertEquals("Relay Station", parsed.game.title)
        assertEquals("sample", parsed.game.platform)
        assertEquals(listOf("zh", "en"), parsed.game.languages)
        assertEquals("0.1.0", parsed.game.packVersion)
        assertEquals("gkp.v0", parsed.game.schemaVersion)
        assertEquals("sample", parsed.game.trustLevel)
        assertEquals(1234L, parsed.game.installedAt)

        assertEquals(14, parsed.knowledge.size)
        val fuse = parsed.knowledge.first { it.entityId == "item.blue-fuse" }
        assertEquals("item", fuse.entityType)
        assertTrue(fuse.aliases.contains("蓝色保险丝"))
        assertEquals("start", fuse.progressGate)
        assertEquals(listOf("sample.relay.items"), fuse.sourceRefs)
        assertFalse(fuse.answerTemplates.isEmpty())

        val alignment = parsed.knowledge.first { it.entityId == "quest.align-beacon" }
        assertEquals("power_restored", alignment.progressGate)
        assertEquals("medium", alignment.spoilerLevel)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects unsupported schema version`() {
        parser.knowledgePaths("""{"schema_version":"gkp.v9","contents":{"knowledge":[]}}""")
    }

    private fun parsePack(packName: String): com.retrosprite.app.data.gkp.ParsedGkpPack {
        val packDir = moduleRoot().resolve("src/main/assets/gkp/$packName")
        val manifestText = readText(packDir.resolve("manifest.json"))
        val knowledgeFiles = parser.knowledgePaths(manifestText).associateWith { path ->
            readText(packDir.resolve(path))
        }
        return parser.parse(manifestText, knowledgeFiles)
    }

    private fun moduleRoot(): Path {
        var current = Paths.get("").toAbsolutePath().normalize()
        repeat(8) {
            if (Files.isDirectory(current.resolve("src/main/assets"))) return current
            if (Files.isDirectory(current.resolve("app/src/main/assets"))) return current.resolve("app")
            current = current.parent ?: current
        }
        error("Could not locate Android app module")
    }

    private fun readText(path: Path): String =
        Files.readAllBytes(path).toString(Charsets.UTF_8)
}
