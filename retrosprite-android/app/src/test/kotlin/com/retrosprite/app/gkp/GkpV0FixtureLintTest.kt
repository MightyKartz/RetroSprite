package com.retrosprite.app.gkp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class GkpV0FixtureLintTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    @Test
    fun `bundled sample fixtures pass gkp v0 lint`() {
        val gkpRoot = moduleRoot()
            .resolve("src/main/assets/gkp")
            .normalize()
        val packs = mapOf(
            "sample-2048" to 16,
            "sample-relay-station" to 12,
            "shining-force-ii-md" to 34,
        )
        packs.forEach { (packName, minGoldenRows) ->
            lintPack(gkpRoot.resolve(packName).normalize(), minGoldenRows)
        }
    }

    private fun lintPack(packDir: Path, minGoldenRows: Int) {
        assertTrue("sample pack missing: $packDir", Files.isDirectory(packDir))

        val manifest = readObject(packDir.resolve("manifest.json"))
        assertEquals("gkp.v0", manifest.string("schema_version"))
        assertIdentifier(manifest.string("pack_id"), "pack_id")
        assertNonBlank(manifest.string("pack_version"), "pack_version")
        assertIn(manifest.string("trust_level"), TRUST_LEVELS, "trust_level")

        val game = manifest.obj("game")
        val gameId = game.string("game_id")
        assertIdentifier(gameId, "game.game_id")
        assertNonBlank(game.string("title"), "game.title")
        assertNonBlank(game.string("platform"), "game.platform")
        assertTrue("game.languages must be non-empty", game.array("languages").isNotEmpty())

        val contents = manifest.obj("contents")
        val knowledgePaths = contents.array("knowledge").map { it.jsonPrimitive.content }
        assertTrue("manifest.contents.knowledge must not be empty", knowledgePaths.isNotEmpty())

        val citationsPath = packPath(packDir, contents.string("citations"))
        val aliasesPath = packPath(packDir, contents.string("aliases"))
        val spoilerGraphPath = packPath(packDir, contents.string("spoiler_graph"))
        val goldensPath = packPath(packDir, contents.string("qa_goldens"))
        val knowledgeFiles = knowledgePaths.map { packPath(packDir, it) }

        val citations = readJsonl(citationsPath)
        val sourceIds = citations.map { it.string("source_id") }.toSet()
        assertEquals("source ids must be unique", citations.size, sourceIds.size)
        citations.forEach { source ->
            assertIdentifier(source.string("source_id"), "source_id")
            assertNonBlank(source.string("title"), "source.title")
            assertIn(source.string("kind"), SOURCE_KINDS, "source.kind")
            assertIn(source.string("reliability"), RELIABILITY_LEVELS, "source.reliability")
        }

        val spoilerGraph = readObject(spoilerGraphPath)
        val gateIds = spoilerGraph.array("gates").map { gate ->
            gate.jsonObject.string("gate_id")
        }.toSet()
        assertTrue("spoiler_graph.gates must not be empty", gateIds.isNotEmpty())
        assertTrue(
            "spoiler_graph.default_gate must exist in gates",
            gateIds.contains(spoilerGraph.string("default_gate"))
        )
        spoilerGraph.array("edges").forEach { edge ->
            val obj = edge.jsonObject
            assertTrue("edge.from must exist: $obj", gateIds.contains(obj.string("from")))
            assertTrue("edge.to must exist: $obj", gateIds.contains(obj.string("to")))
        }

        val knowledge = knowledgeFiles.flatMap { readJsonl(it) }
        assertTrue("knowledge rows must not be empty", knowledge.isNotEmpty())
        val entityIds = knowledge.map { it.string("entity_id") }.toSet()
        assertEquals("entity ids must be unique", knowledge.size, entityIds.size)
        knowledge.forEach { row ->
            assertIdentifier(row.string("entity_id"), "entity_id")
            assertIn(row.string("entity_type"), ENTITY_TYPES, "entity_type")
            assertNonBlank(row.string("canonical_name"), "canonical_name")
            assertTrue("aliases must not be empty for ${row.string("entity_id")}", row.array("aliases").isNotEmpty())
            assertNonBlank(row.string("description_short"), "description_short")
            assertIn(row.string("spoiler_level"), SPOILER_LEVELS, "spoiler_level")
            assertIn(row.string("confidence"), CONFIDENCE_LEVELS, "confidence")
            row.stringOrNull("progress_gate")?.let { gate ->
                assertTrue("unknown progress_gate '$gate'", gateIds.contains(gate))
            }
            row.array("source_refs").forEach { source ->
                assertTrue(
                    "unknown source_ref '${source.jsonPrimitive.content}' in ${row.string("entity_id")}",
                    sourceIds.contains(source.jsonPrimitive.content)
                )
            }
            row.arrayOrEmpty("answer_templates").forEach { template ->
                val tmpl = template.jsonObject
                assertIdentifier(tmpl.string("template_id"), "template_id")
                tmpl.stringOrNull("intent")?.let { intent ->
                    assertIn(intent, ANSWER_INTENTS, "template.intent")
                }
                tmpl.stringOrNull("spoiler_level")?.let { level ->
                    assertIn(level, SPOILER_LEVELS, "template.spoiler_level")
                }
                listOf("spoiler_light", "spoiler_clear", "spoiler_direct").forEach { field ->
                    tmpl.stringOrNull(field)?.let { level ->
                        assertIn(level, SPOILER_LEVELS, "template.$field")
                    }
                }
                val hasFlatAnswer = !tmpl.stringOrNull("answer").isNullOrBlank()
                val hasLayeredAnswer = listOf("answer_light", "answer_clear", "answer_direct")
                    .any { !tmpl.stringOrNull(it).isNullOrBlank() }
                assertTrue("template must include answer or answer_* tier: $tmpl", hasFlatAnswer || hasLayeredAnswer)
                tmpl.array("source_refs").forEach { source ->
                    assertTrue(
                        "unknown template source_ref '${source.jsonPrimitive.content}'",
                        sourceIds.contains(source.jsonPrimitive.content)
                    )
                }
            }
        }

        val aliases = readObject(aliasesPath).array("aliases")
        assertTrue("aliases must not be empty", aliases.isNotEmpty())
        aliases.forEach { alias ->
            val obj = alias.jsonObject
            assertNonBlank(obj.string("term"), "alias.term")
            assertTrue(
                "alias entity_id must exist: ${obj.string("entity_id")}",
                entityIds.contains(obj.string("entity_id"))
            )
        }

        val goldens = readJsonl(goldensPath)
        assertTrue(
            "qa_goldens must contain at least $minGoldenRows rows",
            goldens.size >= minGoldenRows,
        )
        val qaIds = goldens.map { it.string("qa_id") }.toSet()
        assertEquals("qa ids must be unique", goldens.size, qaIds.size)
        goldens.forEach { qa ->
            assertIdentifier(qa.string("qa_id"), "qa_id")
            assertEquals(gameId, qa.string("game_id"))
            assertIn(qa.string("spoiler_level"), SPOILER_LEVELS, "qa.spoiler_level")
            qa.stringOrNull("progress_gate")?.let { gate ->
                assertTrue("unknown qa progress_gate '$gate'", gateIds.contains(gate))
            }
            qa.array("expected_entity_ids").forEach { entity ->
                assertTrue(
                    "qa expected entity must exist: ${entity.jsonPrimitive.content}",
                    entityIds.contains(entity.jsonPrimitive.content)
                )
            }
            qa.array("source_refs").forEach { source ->
                assertTrue(
                    "qa source_ref must exist: ${source.jsonPrimitive.content}",
                    sourceIds.contains(source.jsonPrimitive.content)
                )
            }
        }
    }

    private fun moduleRoot(): Path {
        var current = Paths.get("").toAbsolutePath().normalize()
        repeat(8) {
            if (Files.isDirectory(current.resolve("src/main/assets"))) return current
            if (Files.isDirectory(current.resolve("app/src/main/assets"))) return current.resolve("app")
            current = current.parent ?: current
        }
        error("Could not locate Android app module from ${Paths.get("").toAbsolutePath()}")
    }

    private fun packPath(packDir: Path, relative: String): Path {
        assertFalse("absolute pack paths are not allowed: $relative", relative.startsWith("/"))
        assertFalse("parent traversal is not allowed: $relative", relative.contains(".."))
        val resolved = packDir.resolve(relative).normalize()
        assertTrue("path escapes pack dir: $relative", resolved.startsWith(packDir))
        assertTrue("declared pack path does not exist: $relative", Files.exists(resolved))
        return resolved
    }

    private fun readObject(path: Path): JsonObject =
        json.parseToJsonElement(
            Files.readAllBytes(path).toString(Charsets.UTF_8)
        ).jsonObject

    private fun readJsonl(path: Path): List<JsonObject> {
        val rows = Files.readAllLines(path)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { json.parseToJsonElement(it).jsonObject }
        assertTrue("$path must contain at least one JSON object", rows.isNotEmpty())
        return rows
    }

    private fun JsonObject.obj(name: String): JsonObject =
        this[name]?.jsonObject ?: error("Missing object field '$name' in $this")

    private fun JsonObject.array(name: String): JsonArray =
        this[name]?.jsonArray ?: error("Missing array field '$name' in $this")

    private fun JsonObject.arrayOrEmpty(name: String): JsonArray =
        this[name]?.jsonArray ?: JsonArray(emptyList())

    private fun JsonObject.string(name: String): String =
        stringOrNull(name) ?: error("Missing string field '$name' in $this")

    private fun JsonObject.stringOrNull(name: String): String? {
        val value = this[name] ?: return null
        if (value is JsonNull) return null
        return (value as? JsonPrimitive)?.contentOrNull
    }

    private fun assertNonBlank(value: String, field: String) {
        assertTrue("$field must not be blank", value.isNotBlank())
    }

    private fun assertIdentifier(value: String, field: String) {
        assertTrue("$field has invalid id: $value", ID_PATTERN.matches(value))
    }

    private fun assertIn(value: String, allowed: Set<String>, field: String) {
        assertTrue("$field has invalid value '$value'; allowed=$allowed", allowed.contains(value))
    }

    private companion object {
        val ID_PATTERN = Regex("[a-z0-9][a-z0-9._-]*")

        val TRUST_LEVELS = setOf("official", "community", "personal", "sample")
        val SOURCE_KINDS = setOf(
            "manual",
            "official_site",
            "project_note",
            "community_note",
            "wiki",
            "transcript",
            "other"
        )
        val RELIABILITY_LEVELS = setOf("verified", "community", "uncertain")
        val ENTITY_TYPES = setOf(
            "mechanic",
            "item",
            "enemy",
            "boss",
            "location",
            "quest",
            "npc",
            "dialogue",
            "strategy",
            "faq",
            "note"
        )
        val SPOILER_LEVELS = setOf("none", "light", "medium", "heavy")
        val CONFIDENCE_LEVELS = setOf("verified", "community", "uncertain")
        val ANSWER_INTENTS = setOf(
            "game_overview",
            "beginner_guide",
            "team_build",
            "leveling",
            "name_mapping",
            "location",
            "usage",
            "mechanic",
            "route_hint",
            "strategy",
            "production",
            "no_evidence",
            "unknown_or_out_of_scope",
        )
    }
}
