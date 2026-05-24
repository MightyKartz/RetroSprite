package com.retrosprite.app.gkp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ShiningForceIILocalizedTermCoverageTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    @Test
    fun `localized Chinese term coverage is broad enough for real-game pilot`() {
        val packDir = moduleRoot().resolve("src/main/assets/gkp/shining-force-ii-md")
        val manifest = readObject(packDir.resolve("manifest.json"))
        val knowledgeRows = manifest.obj("contents")
            .arrayStrings("knowledge")
            .flatMap { relative -> readJsonl(packDir.resolve(relative)) }
        val aliases = readObject(packDir.resolve("aliases.json")).array("aliases")
        val goldens = readJsonl(packDir.resolve("qa_goldens.jsonl"))

        val entityCounts = knowledgeRows
            .groupingBy { it.string("entity_type") }
            .eachCount()
        val localizedAliasCount = aliases.count { alias ->
            alias.string("term").any { it in CJK_UNIFIED_IDEOGRAPHS }
        }
        val localizedGoldenCount = goldens.count { golden ->
            golden.string("question").any { it in CJK_UNIFIED_IDEOGRAPHS }
        }

        assertTrue("npc rows=${entityCounts["npc"]}", entityCounts.getValue("npc") >= 25)
        assertTrue("item rows=${entityCounts["item"]}", entityCounts.getValue("item") >= 45)
        assertTrue("location rows=${entityCounts["location"]}", entityCounts.getValue("location") >= 35)
        assertTrue("boss rows=${entityCounts["boss"]}", entityCounts.getValue("boss") >= 12)
        assertTrue("enemy rows=${entityCounts["enemy"]}", entityCounts.getValue("enemy") >= 20)
        assertTrue("localized aliases=$localizedAliasCount", localizedAliasCount >= 160)
        assertTrue("all aliases=${aliases.size}", aliases.size >= 260)
        assertTrue("goldens=${goldens.size}", goldens.size >= 120)
        assertTrue("localized goldens=$localizedGoldenCount", localizedGoldenCount >= 90)
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
        json.parseToJsonElement(Files.readAllBytes(path).toString(Charsets.UTF_8)).jsonObject

    private fun readJsonl(path: Path): List<JsonObject> =
        Files.readAllLines(path)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { json.parseToJsonElement(it).jsonObject }

    private fun JsonObject.obj(key: String): JsonObject =
        getValue(key).jsonObject

    private fun JsonObject.array(key: String): List<JsonObject> =
        getValue(key).jsonArray.map { it.jsonObject }

    private fun JsonObject.arrayStrings(key: String): List<String> =
        getValue(key).jsonArray.map { it.jsonPrimitive.contentOrNull ?: error("Missing string in $key") }

    private fun JsonObject.string(key: String): String =
        getValue(key).jsonPrimitive.contentOrNull ?: error("Missing string $key in $this")

    private companion object {
        val CJK_UNIFIED_IDEOGRAPHS = '\u4E00'..'\u9FFF'
    }
}
