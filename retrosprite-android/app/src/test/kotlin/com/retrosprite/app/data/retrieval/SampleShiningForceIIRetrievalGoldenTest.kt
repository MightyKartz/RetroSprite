package com.retrosprite.app.data.retrieval

import com.retrosprite.app.data.gkp.GkpV0Parser
import com.retrosprite.app.data.models.KnowledgeChunkDomain
import com.retrosprite.app.data.repository.KnowledgeRepository
import com.retrosprite.app.domain.models.AnswerType
import com.retrosprite.app.domain.models.RetrievalQuery
import com.retrosprite.app.domain.models.SpoilerLevel
import com.retrosprite.app.domain.normalization.GameTermNormalizer
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class SampleShiningForceIIRetrievalGoldenTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    @Test
    fun `sample shining force ii qa goldens resolve expected entities`() = runTest {
        val fixture = loadPack()
        val pipeline = LocalKnowledgeRetrievalPipeline(FixtureKnowledgeRepository(fixture.knowledge))
        val goldens = readJsonl(fixture.packDir.resolve("qa_goldens.jsonl")).map { it.toGoldenCase() }

        goldens.forEach { golden ->
            val normalizedQuestion = normalizeGoldenQuestion(
                pipeline = pipeline,
                golden = golden,
                rows = fixture.knowledge,
            )
            val query = RetrievalQuery(
                gameId = golden.gameId,
                normalizedQuery = normalizedQuestion,
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
    fun `sample shining force ii promotion item locations return low spoiler hints under light tolerance`() = runTest {
        val fixture = loadPack()
        val pipeline = LocalKnowledgeRetrievalPipeline(FixtureKnowledgeRepository(fixture.knowledge))
        val normalized = pipeline.normalizeQuestion("勇者之证在哪里？", "zh")

        val results = pipeline.retrieve(
            RetrievalQuery(
                gameId = "shining_force_ii_md",
                normalizedQuery = normalized,
                language = "zh",
                progressGate = "new_granseal",
                spoilerLevel = SpoilerLevel.LIGHT,
                limit = 5,
            )
        )

        val warriorPride = results.firstOrNull { it.entityId == "item.warrior-pride" }
        assertTrue("expected low spoiler warrior pride hint, got ${results.map { it.entityId }}", warriorPride != null)
        val text = warriorPride!!.evidence.joinToString(" ") { it.snippet }
        assertTrue("answer=<$text>", text.contains("先别查具体位置"))
        assertFalse("answer=<$text>", text.contains("战术基地相关位置"))
    }

    @Test
    fun `sample shining force ii observed asr team building variants resolve team strategy`() = runTest {
        val fixture = loadPack()
        val pipeline = LocalKnowledgeRetrievalPipeline(FixtureKnowledgeRepository(fixture.knowledge))

        listOf(
            "那些角色适合培养",
            "那这些角色适合培养",
            "那些人物适合培养",
            "哪些人物适合培养",
            "那些队员适合培养",
        ).forEach { question ->
            val normalized = pipeline.normalizeQuestion(question, "zh")
            val results = pipeline.retrieve(
                RetrievalQuery(
                    gameId = "shining_force_ii_md",
                    normalizedQuery = normalized,
                    language = "zh",
                    progressGate = "start",
                    spoilerLevel = SpoilerLevel.LIGHT,
                    limit = 5,
                )
            )

            assertTrue(
                "question=<$question> normalized=<$normalized> got ${results.map { it.entityId }}",
                results.any { it.entityId == "strategy.team-build-general" },
            )
            val sources = results.flatMap { result -> result.evidence.map { it.sourceId } }
            assertTrue(
                "question=<$question> sources=$sources",
                sources.contains("sf2.project_mechanics"),
            )
        }
    }

    @Test
    fun `sample shining force ii identity variants keep game overview type`() = runTest {
        val fixture = loadPack()
        val pipeline = LocalKnowledgeRetrievalPipeline(FixtureKnowledgeRepository(fixture.knowledge))

        listOf(
            "这是什么游戏",
            "这个游戏是什么",
            "光明力量2是什么游戏",
            "Shining Force II 是什么游戏",
        ).forEach { question ->
            val normalized = GameTermNormalizer()
                .normalize(pipeline.normalizeQuestion(question, "zh"), fixture.knowledge)
                .normalizedQuestion
            val results = pipeline.retrieve(
                RetrievalQuery(
                    gameId = "shining_force_ii_md",
                    normalizedQuery = normalized,
                    language = "zh",
                    progressGate = "start",
                    spoilerLevel = SpoilerLevel.LIGHT,
                    limit = 5,
                )
            )
            val identity = results.firstOrNull { it.entityId == "note.game-identity" }

            assertTrue(
                "question=<$question> normalized=<$normalized> got ${results.map { it.entityId }}",
                identity != null,
            )
            assertEquals("question=<$question>", AnswerType.GameOverview, identity?.answerType)
        }
    }

    private data class PackFixture(
        val packDir: Path,
        val knowledge: List<KnowledgeChunkDomain>,
    )

    private data class GoldenCase(
        val qaId: String,
        val language: String,
        val question: String,
        val gameId: String,
        val spoilerLevel: String,
        val progressGate: String?,
        val expectedNormalizedQuestion: String?,
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
            val tokens = normalizeForSearch(query).split(WHITESPACE)
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

    private fun loadPack(): PackFixture {
        val packDir = moduleRoot()
            .resolve("src/main/assets/gkp/shining-force-ii-md")
            .normalize()
        val manifestText = readText(packDir.resolve("manifest.json"))
        val parser = GkpV0Parser(nowMillis = { 0L })
        val knowledgeFiles = parser.knowledgePaths(manifestText)
            .associateWith { relative -> readText(packDir.resolve(relative)) }
        val aliasFiles = parser.aliasPath(manifestText)
            ?.let { relative -> mapOf(relative to readText(packDir.resolve(relative))) }
            .orEmpty()
        val parsed = parser.parse(manifestText, knowledgeFiles, aliasFiles)
        return PackFixture(packDir = packDir, knowledge = parsed.knowledge)
    }

    private fun JsonObject.toGoldenCase(): GoldenCase =
        GoldenCase(
            qaId = string("qa_id"),
            language = string("language"),
            question = string("question"),
            gameId = string("game_id"),
            spoilerLevel = string("spoiler_level"),
            progressGate = stringOrNull("progress_gate"),
            expectedNormalizedQuestion = stringOrNull("expected_normalized_question"),
            expectedEntityIds = arrayStrings("expected_entity_ids"),
            sourceRefs = arrayStrings("source_refs"),
        )

    private suspend fun normalizeGoldenQuestion(
        pipeline: LocalKnowledgeRetrievalPipeline,
        golden: GoldenCase,
        rows: List<KnowledgeChunkDomain>,
    ): String {
        val baseNormalized = pipeline.normalizeQuestion(golden.question, golden.language)
        val termNormalized = GameTermNormalizer().normalize(baseNormalized, rows).normalizedQuestion
        golden.expectedNormalizedQuestion?.let { expected ->
            assertEquals("${golden.qaId} normalized question", expected, termNormalized)
        }
        return termNormalized
    }

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
