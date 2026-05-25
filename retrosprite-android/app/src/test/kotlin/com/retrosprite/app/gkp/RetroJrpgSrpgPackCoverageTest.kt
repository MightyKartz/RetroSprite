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

class RetroJrpgSrpgPackCoverageTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    @Test
    fun `phase 1 chinese lite packs meet coverage contract`() {
        val root = moduleRoot()
        val gkpRoot = root.resolve("src/main/assets/gkp")

        PACKS.forEach { pack ->
            val packDir = gkpRoot.resolve(pack.slug)
            assertTrue("Phase 1 pack directory missing: ${pack.slug}", Files.isDirectory(packDir))

            val manifest = readObject(packDir.resolve("manifest.json"))
            assertEquals("pack_id must end with -zh", true, manifest.string("pack_id").endsWith("-zh"))
            assertEquals("coverage_tier for ${pack.slug}", "lite", manifest.string("coverage_tier"))
            assertEquals("zh", manifest.string("default_language"))
            val game = manifest.obj("game")
            assertEquals(pack.gameId, game.string("game_id"))
            assertTrue("${pack.slug} retroarch_system_ids", game.array("retroarch_system_ids").isNotEmpty())
            assertTrue("${pack.slug} retroarch_labels", game.array("retroarch_labels").size >= 6)

            val contents = manifest.obj("contents")
            val knowledge = contents.array("knowledge")
                .flatMap { readJsonl(packDir.resolve(it.jsonPrimitive.content)) }
            assertTrue("${pack.slug} knowledge rows=${knowledge.size}", knowledge.size in 20..60)
            assertTrue("${pack.slug} all rows must be zh or language-omitted", knowledge.all {
                it.stringOrNull("language") == null || it.stringOrNull("language") == "zh"
            })
            assertFalse("${pack.slug} contains scaffold placeholders", packContainsPlaceholder(packDir))

            val entityIds = knowledge.map { it.string("entity_id") }.toSet()
            assertEquals("${pack.slug} entity ids must be unique", knowledge.size, entityIds.size)
            assertSkeletonMatches(root, pack, entityIds)

            val sourceIds = readJsonl(packDir.resolve(contents.string("citations")))
                .map { it.string("source_id") }
                .toSet()
            assertTrue("${pack.slug} source rows=${sourceIds.size}", sourceIds.size >= 5)

            knowledge.forEach { row ->
                row.array("source_refs").forEach { source ->
                    assertTrue(
                        "${pack.slug} dangling source_ref ${source.jsonPrimitive.content}",
                        sourceIds.contains(source.jsonPrimitive.content),
                    )
                }
            }

            val aliases = readObject(packDir.resolve(contents.string("aliases"))).array("aliases")
            aliases.forEach { alias ->
                val entityId = alias.jsonObject.string("entity_id")
                assertTrue("${pack.slug} dangling alias entity_id $entityId", entityIds.contains(entityId))
            }
            val asrAliases = aliases.count { alias ->
                val obj = alias.jsonObject
                obj.stringOrNull("kind") in ASR_ALIAS_KINDS || obj.stringOrNull("source") == "observed_asr"
            }
            assertTrue("${pack.slug} scoped ASR alias metadata=$asrAliases", asrAliases >= 3)
            val localizedAliases = aliases.count { it.jsonObject.string("term").hasCjk() }
            assertTrue("${pack.slug} localized aliases=$localizedAliases", localizedAliases >= 40)
            val entityTypes = knowledge.associate { it.string("entity_id") to it.string("entity_type") }
            val sourceBackedLocalizedAliases = aliases.count { alias ->
                val term = alias.jsonObject.string("term")
                val entityId = alias.jsonObject.string("entity_id")
                term.hasCjk() && entityTypes[entityId] in PROPER_NAME_ENTITY_TYPES
            }
            assertTrue(
                "${pack.slug} source-backed localized proper-name aliases=$sourceBackedLocalizedAliases",
                sourceBackedLocalizedAliases >= 25,
            )

            val goldens = readJsonl(packDir.resolve(contents.string("qa_goldens")))
            assertTrue("${pack.slug} golden rows=${goldens.size}", goldens.size >= 20)
            val localizedGoldens = goldens.count { it.stringOrNull("language") == "zh" }
            assertTrue("${pack.slug} localized goldens=$localizedGoldens", localizedGoldens >= 20)
            val pureChineseGoldens = goldens.count { qa ->
                val question = qa.string("question")
                question.hasCjk() && !ASCII_LETTER.containsMatchIn(question)
            }
            assertTrue("${pack.slug} pure Chinese goldens=$pureChineseGoldens", pureChineseGoldens >= 12)
            val coreGameplayGoldens = goldens.count { qa ->
                val question = qa.string("question")
                val expected = qa.array("expected_entity_ids").map { it.jsonPrimitive.content }
                expected.contains("note.core-gameplay") ||
                    question.contains("主要玩什么") ||
                    question.contains("好玩") ||
                    question.contains("核心玩法") ||
                    question.contains("适合")
            }
            assertTrue("${pack.slug} core gameplay/fun-hook goldens=$coreGameplayGoldens", coreGameplayGoldens >= 4)
            val noEvidenceGoldens = goldens.count { qa ->
                qa.array("expected_entity_ids").isEmpty() ||
                    qa.string("question").contains("不确定") ||
                    qa.string("question").contains("会剧透") ||
                    qa.string("question").contains("直接告诉")
            }
            assertTrue("${pack.slug} no-evidence/clarification goldens=$noEvidenceGoldens", noEvidenceGoldens >= 3)

            goldens.forEach { qa ->
                qa.array("expected_entity_ids").forEach { entity ->
                    assertTrue(
                        "${pack.slug} dangling golden entity_id ${entity.jsonPrimitive.content}",
                        entityIds.contains(entity.jsonPrimitive.content),
                    )
                }
                qa.array("source_refs").forEach { source ->
                    assertTrue(
                        "${pack.slug} dangling golden source_ref ${source.jsonPrimitive.content}",
                        sourceIds.contains(source.jsonPrimitive.content),
                    )
                }
            }
        }
    }

    private fun assertSkeletonMatches(root: Path, pack: Pack, entityIds: Set<String>) {
        val skeleton = root.parent.resolve("docs/gkp/skeletons/${pack.skeletonName}")
        assertTrue("skeleton missing for ${pack.slug}: $skeleton", Files.isRegularFile(skeleton))
        val skeletonIds = Files.readAllLines(skeleton)
            .asSequence()
            .filter { it.startsWith("|") && !it.contains("entity_id |") && !it.contains("---") }
            .map { line -> line.split("|").getOrNull(1).orEmpty().trim() }
            .filter { ID_PATTERN.matches(it) }
            .toSet()
        assertTrue("${pack.slug} skeleton ids must not be empty", skeletonIds.isNotEmpty())
        skeletonIds.forEach { id ->
            assertFalse("${pack.slug} skeleton entity_id contains CJK: $id", id.hasCjk())
        }
        entityIds.forEach { entityId ->
            assertTrue("${pack.slug} entity_id not in skeleton: $entityId", skeletonIds.contains(entityId))
        }
    }

    private fun packContainsPlaceholder(packDir: Path): Boolean =
        Files.walk(packDir).use { paths ->
            paths.filter { Files.isRegularFile(it) }.anyMatch { path ->
                readText(path).contains("__REPLACE_WITH_REVIEWED_GKP_DATA__")
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

    private fun readObject(path: Path): JsonObject =
        json.parseToJsonElement(readText(path)).jsonObject

    private fun readJsonl(path: Path): List<JsonObject> =
        Files.readAllLines(path)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { json.parseToJsonElement(it).jsonObject }

    private fun readText(path: Path): String =
        Files.readAllBytes(path).toString(Charsets.UTF_8)

    private fun JsonObject.obj(name: String): JsonObject =
        this[name]?.jsonObject ?: error("Missing object field '$name' in $this")

    private fun JsonObject.array(name: String): JsonArray =
        this[name]?.jsonArray ?: error("Missing array field '$name' in $this")

    private fun JsonObject.string(name: String): String =
        stringOrNull(name) ?: error("Missing string field '$name' in $this")

    private fun JsonObject.stringOrNull(name: String): String? {
        val value = this[name] ?: return null
        if (value is JsonNull) return null
        return (value as? JsonPrimitive)?.contentOrNull
    }

    private fun String.hasCjk(): Boolean =
        CJK.containsMatchIn(this)

    private data class Pack(
        val slug: String,
        val gameId: String,
        val skeletonName: String,
    )

    private companion object {
        val ID_PATTERN = Regex("[a-z0-9][a-z0-9._-]*")
        val CJK = Regex("[\\u4E00-\\u9FFF]")
        val ASCII_LETTER = Regex("[A-Za-z]")
        val PROPER_NAME_ENTITY_TYPES = setOf("npc", "item", "location", "boss", "enemy")
        val ASR_ALIAS_KINDS = setOf("asr_variant", "observed_asr")
        val PACKS = listOf(
            Pack("golden-sun-gba-zh", "golden_sun_gba", "golden-sun-gba-entity-skeleton.md"),
            Pack("phantasy-star-iv-md-zh", "phantasy_star_iv_md", "phantasy-star-iv-md-entity-skeleton.md"),
            Pack("langrisser-ii-md-zh", "langrisser_ii_md", "langrisser-ii-md-entity-skeleton.md"),
            Pack("chrono-trigger-snes-zh", "chrono_trigger_snes", "chrono-trigger-snes-entity-skeleton.md"),
            Pack("final-fantasy-vi-snes-zh", "final_fantasy_vi_snes", "final-fantasy-vi-snes-entity-skeleton.md"),
        )
    }
}
