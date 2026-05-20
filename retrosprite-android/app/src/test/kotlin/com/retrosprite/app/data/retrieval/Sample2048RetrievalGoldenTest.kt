package com.retrosprite.app.data.retrieval

import com.retrosprite.app.data.gkp.GkpV0Parser
import com.retrosprite.app.data.models.KnowledgeChunkDomain
import com.retrosprite.app.data.repository.KnowledgeRepository
import com.retrosprite.app.domain.models.RetrievalQuery
import com.retrosprite.app.domain.models.SpoilerLevel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class Sample2048RetrievalGoldenTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    @Test
    fun `sample 2048 qa goldens resolve expected entities`() = runTest {
        val packDir = moduleRoot()
            .resolve("src/main/assets/gkp/sample-2048")
            .normalize()
        val manifestText = readText(packDir.resolve("manifest.json"))
        val parser = GkpV0Parser(nowMillis = { 0L })
        val knowledgeFiles = parser.knowledgePaths(manifestText)
            .associateWith { relative -> readText(packDir.resolve(relative)) }
        val parsed = parser.parse(manifestText, knowledgeFiles)
        val pipeline = LocalKnowledgeRetrievalPipeline(FixtureKnowledgeRepository(parsed.knowledge))
        val goldens = readJsonl(packDir.resolve("qa_goldens.jsonl")).map { it.toGoldenCase() }

        goldens.forEach { golden ->
            val query = RetrievalQuery(
                gameId = golden.gameId,
                normalizedQuery = pipeline.normalizeQuestion(golden.question, golden.language),
                language = golden.language,
                progressGate = golden.progressGate,
                spoilerLevel = golden.spoilerLevel.toDomainSpoiler(),
                limit = 5,
            )
            val results = pipeline.retrieve(query)

            if (golden.expectedEntityIds.isEmpty()) {
                assertTrue(
                    "${golden.qaId} expected no evidence, got ${results.map { it.entityId }}",
                    results.isEmpty(),
                )
            } else {
                val hitIds = results.map { it.entityId }.toSet()
                assertTrue(
                    "${golden.qaId} missing expected entities ${golden.expectedEntityIds}; got $hitIds",
                    hitIds.containsAll(golden.expectedEntityIds),
                )

                val sourceIds = results.flatMap { result ->
                    result.evidence.map { evidence -> evidence.sourceId }
                }.toSet()
                assertTrue(
                    "${golden.qaId} missing expected source ${golden.sourceRefs}; got $sourceIds",
                    sourceIds.any { it in golden.sourceRefs },
                )
            }
        }
    }

    @Test
    fun `sample 2048 medium spoiler golden is hidden under light tolerance`() = runTest {
        val packDir = moduleRoot()
            .resolve("src/main/assets/gkp/sample-2048")
            .normalize()
        val manifestText = readText(packDir.resolve("manifest.json"))
        val parser = GkpV0Parser(nowMillis = { 0L })
        val knowledgeFiles = parser.knowledgePaths(manifestText)
            .associateWith { relative -> readText(packDir.resolve(relative)) }
        val parsed = parser.parse(manifestText, knowledgeFiles)
        val pipeline = LocalKnowledgeRetrievalPipeline(FixtureKnowledgeRepository(parsed.knowledge))

        val normalized = pipeline.normalizeQuestion("后期怎么摆数字？", "zh")
        val results = pipeline.retrieve(
            RetrievalQuery(
                gameId = "2048",
                normalizedQuery = normalized,
                language = "zh",
                progressGate = "stable_corner",
                spoilerLevel = SpoilerLevel.LIGHT,
                limit = 5,
            )
        )

        assertFalse(results.any { it.entityId == "strategy.snake-order" })
    }

    private data class GoldenCase(
        val qaId: String,
        val language: String,
        val question: String,
        val gameId: String,
        val spoilerLevel: String,
        val progressGate: String?,
        val expectedEntityIds: List<String>,
        val sourceRefs: List<String>,
    )

    private class FixtureKnowledgeRepository(
        private val rows: List<KnowledgeChunkDomain>,
    ) : KnowledgeRepository {
        override suspend fun searchFts(
            gameId: String,
            query: String,
            limit: Int,
        ): List<KnowledgeChunkDomain> {
            val normalizedQuery = normalizeForSearch(query)
            val tokens = normalizedQuery.split(WHITESPACE)
                .map { it.trim() }
                .filter { it.length >= 2 }
            if (tokens.isEmpty()) return emptyList()

            return rows.filter { row ->
                row.gameId == gameId &&
                    tokens.any { token -> row.searchText().contains(token) }
            }.take(limit)
        }

        override suspend fun getByEntityId(gameId: String, entityId: String): KnowledgeChunkDomain? =
            rows.firstOrNull { it.gameId == gameId && it.entityId == entityId }

        override suspend fun listByGame(gameId: String): List<KnowledgeChunkDomain> =
            rows.filter { it.gameId == gameId }

        override suspend fun listByType(gameId: String, entityType: String): List<KnowledgeChunkDomain> =
            rows.filter { it.gameId == gameId && it.entityType == entityType }

        override suspend fun upsertAll(chunks: List<KnowledgeChunkDomain>) = Unit
        override suspend fun clearForGame(gameId: String) = Unit

        private fun KnowledgeChunkDomain.searchText(): String =
            listOf(
                entityId,
                entityType,
                canonicalName,
                aliases.joinToString(" "),
                descriptionShort,
                descriptionLong.orEmpty(),
            ).joinToString(" ")
                .let(::normalizeForSearch)
    }

    private fun JsonObject.toGoldenCase(): GoldenCase =
        GoldenCase(
            qaId = string("qa_id"),
            language = string("language"),
            question = string("question"),
            gameId = string("game_id"),
            spoilerLevel = string("spoiler_level"),
            progressGate = stringOrNull("progress_gate"),
            expectedEntityIds = arrayStrings("expected_entity_ids"),
            sourceRefs = arrayStrings("source_refs"),
        )

    private fun String.toDomainSpoiler(): SpoilerLevel = when (lowercase()) {
        "none", "light" -> SpoilerLevel.LIGHT
        "medium" -> SpoilerLevel.CLEAR
        "heavy" -> SpoilerLevel.FULL
        else -> error("Unsupported spoiler_level: $this")
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

    private fun readText(path: Path): String =
        Files.readAllBytes(path).toString(Charsets.UTF_8)

    private fun readJsonl(path: Path): List<JsonObject> =
        Files.readAllLines(path)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { json.parseToJsonElement(it).jsonObject }

    private fun JsonObject.arrayStrings(name: String): List<String> =
        array(name).map { it.jsonPrimitive.content }

    private fun JsonObject.array(name: String): JsonArray =
        this[name]?.jsonArray ?: error("Missing array field '$name' in $this")

    private fun JsonObject.string(name: String): String =
        stringOrNull(name) ?: error("Missing string field '$name' in $this")

    private fun JsonObject.stringOrNull(name: String): String? {
        val value = this[name] ?: return null
        if (value is JsonNull) return null
        return (value as? JsonPrimitive)?.contentOrNull
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val PUNCTUATION = Regex("[\\p{Punct}，。？！、；：]+")

        fun normalizeForSearch(value: String): String =
            value.lowercase()
                .replace(PUNCTUATION, " ")
                .replace(WHITESPACE, " ")
                .trim()
    }
}
