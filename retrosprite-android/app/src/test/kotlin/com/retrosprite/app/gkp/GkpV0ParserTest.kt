package com.retrosprite.app.gkp

import com.retrosprite.app.data.gkp.GkpV0Parser
import com.retrosprite.app.data.gkp.GkpPackProvenance
import com.retrosprite.app.data.gkp.GkpSignatureStatus
import com.retrosprite.app.data.models.KnowledgeAliasDomain
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
    fun `parses bundled shining force ii pack into domain rows`() {
        val parsed = parsePack("shining-force-ii-md")

        assertEquals("shining_force_ii_md", parsed.game.gameId)
        assertEquals("community.shining-force-ii-md", parsed.game.packId)
        assertEquals("Shining Force II / 光明力量2", parsed.game.title)
        assertEquals("md", parsed.game.platform)
        assertEquals(listOf("zh", "en"), parsed.game.languages)
        assertEquals(listOf("md", "genesis", "megadrive", "mega_drive"), parsed.game.retroarchSystemIds)
        assertTrue(parsed.game.retroarchLabels.contains("mega_drive__光明力量2"))
        assertEquals("expanded", parsed.game.coverageTier)
        assertEquals("0.3.4", parsed.game.packVersion)
        assertEquals("gkp.v0", parsed.game.schemaVersion)
        assertEquals("community", parsed.game.trustLevel)
        assertEquals(GkpPackProvenance.Unknown.id, parsed.game.provenance)
        assertEquals(GkpSignatureStatus.Unsigned.id, parsed.game.signatureStatus)
        assertEquals(1234L, parsed.game.installedAt)

        assertEquals(160, parsed.knowledge.size)
        val promotion = parsed.knowledge.first { it.entityId == "mechanic.promotion-level" }
        assertEquals("mechanic", promotion.entityType)
        assertTrue(promotion.aliases.contains("转职"))
        assertEquals("none", promotion.spoilerLevel)
        assertEquals(listOf("sf2.promotion"), promotion.sourceRefs)
        assertFalse(promotion.answerTemplates.isEmpty())

        val core = parsed.knowledge.first { it.entityId == "note.core-gameplay-loop" }
        assertEquals("note", core.entityType)
        assertTrue(core.aliases.contains("核心玩法"))
        assertEquals("start", core.progressGate)
    }

    @Test
    fun `parses bundled golden sun lite pack into domain rows`() {
        val parsed = parsePack("golden-sun-gba-zh")

        assertEquals("golden_sun_gba", parsed.game.gameId)
        assertEquals("community.golden-sun-gba-zh", parsed.game.packId)
        assertEquals("Golden Sun / 黄金太阳", parsed.game.title)
        assertEquals("gba", parsed.game.platform)
        assertEquals(listOf("zh"), parsed.game.languages)
        assertEquals(listOf("gba", "game_boy_advance"), parsed.game.retroarchSystemIds)
        assertTrue(parsed.game.retroarchLabels.contains("gba__黄金太阳"))
        assertEquals("lite", parsed.game.coverageTier)
        assertEquals("0.1.2", parsed.game.packVersion)
        assertEquals("gkp.v0", parsed.game.schemaVersion)
        assertEquals("community", parsed.game.trustLevel)
        assertEquals(1234L, parsed.game.installedAt)

        assertEquals(42, parsed.knowledge.size)
        val psynergy = parsed.knowledge.first { it.entityId == "mechanic.psynergy" }
        assertEquals("mechanic", psynergy.entityType)
        assertTrue(psynergy.aliases.contains("精神力"))
        assertTrue(psynergy.aliases.contains("精神利"))
        assertEquals("start", psynergy.progressGate)
        assertEquals(listOf("gs.official_manual", "gs.community_wiki"), psynergy.sourceRefs)
        assertFalse(psynergy.answerTemplates.isEmpty())

        val identity = parsed.knowledge.first { it.entityId == "note.identity" }
        assertEquals("note", identity.entityType)
        assertTrue(identity.aliases.contains("黄金太阳"))
    }

    @Test
    fun `merges aliases file terms into parsed knowledge rows`() {
        val manifestText = """
            {
              "schema_version": "gkp.v0",
              "pack_id": "community.test",
              "pack_version": "0.1.0",
              "trust_level": "community",
              "game": {
                "game_id": "test_game",
                "title": "Test Game",
                "platform": "md",
                "region": null,
                "languages": ["zh", "en"],
                "rom_identity": {"crc32": null, "sha1": null}
              },
              "contents": {
                "knowledge": ["knowledge/items.jsonl"],
                "aliases": "aliases.json"
              }
            }
        """.trimIndent()
        val knowledgeText = """
            {"entity_id":"item.warrior-pride","entity_type":"item","canonical_name":"Warrior Pride / 勇者之证","language":"zh","aliases":["Warrior Pride","勇者之证"],"description_short":"Warrior Pride 是战士特殊转职道具。","description_long":null,"progress_gate":"start","spoiler_level":"light","source_refs":["test.source"],"confidence":"community","answer_templates":[]}
        """.trimIndent()
        val aliasesText = """
            {
              "language": "zh",
              "aliases": [
                {"term": "战士之傲", "entity_id": "item.warrior-pride", "weight": 1.0}
                ,{"term": "勇者之政", "entity_id": "item.warrior-pride", "weight": 0.72, "kind": "observed_asr", "source": "observed_asr", "canonical_term": "勇者之证", "notes": "Observed mic result."}
              ]
            }
        """.trimIndent()

        val parsed = parser.parse(
            manifestText = manifestText,
            knowledgeFiles = mapOf("knowledge/items.jsonl" to knowledgeText),
            aliasFiles = mapOf("aliases.json" to aliasesText),
        )

        val item = parsed.knowledge.single()
        assertTrue(item.aliases.contains("Warrior Pride"))
        assertTrue(item.aliases.contains("勇者之证"))
        assertTrue(item.aliases.contains("战士之傲"))
        assertTrue(item.aliases.contains("勇者之政"))
        assertEquals(
            KnowledgeAliasDomain(
                term = "勇者之政",
                entityId = "item.warrior-pride",
                kind = "observed_asr",
                source = "observed_asr",
                weight = 0.72,
                canonicalTerm = "勇者之证",
                notes = "Observed mic result.",
            ),
            item.aliasMetadata.single { it.term == "勇者之政" },
        )
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
        val aliasFiles = parser.aliasPath(manifestText)
            ?.let { path -> mapOf(path to readText(packDir.resolve(path))) }
            .orEmpty()
        return parser.parse(manifestText, knowledgeFiles, aliasFiles)
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
