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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class RetroJrpgSrpgPackRetrievalGoldenTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    @Test
    fun `retro jrpg srpg chinese lite qa goldens resolve expected entities`() = runTest {
        val failures = mutableListOf<String>()

        PACKS.forEach { pack ->
            val fixture = loadPack(pack.slug)
            val pipeline = LocalKnowledgeRetrievalPipeline(FixtureKnowledgeRepository(fixture.knowledge))
            val goldens = readJsonl(fixture.packDir.resolve("qa_goldens.jsonl")).map { it.toGoldenCase() }

            goldens.forEach { golden ->
                val normalizedQuestion = normalizeGoldenQuestion(
                    pipeline = pipeline,
                    golden = golden,
                    rows = fixture.knowledge,
                )
                val results = pipeline.retrieve(
                    RetrievalQuery(
                        gameId = golden.gameId,
                        normalizedQuery = normalizedQuestion,
                        language = golden.language,
                        progressGate = golden.progressGate,
                        spoilerLevel = golden.spoilerLevel.toDomainSpoiler(),
                        limit = 5,
                    )
                )

                if (golden.expectedEntityIds.isEmpty()) {
                    val hasNoEvidencePath = results.any { it.answerType == AnswerType.NoEvidence }
                    if (results.isNotEmpty() && !hasNoEvidencePath) {
                        failures += "${pack.slug}/${golden.qaId} expected no evidence, got " +
                            results.map { it.entityId }
                    }
                } else {
                    val hitIds = results.map { it.entityId }.toSet()
                    if (!hitIds.containsAll(golden.expectedEntityIds)) {
                        failures += "${pack.slug}/${golden.qaId} missing expected entities " +
                            "${golden.expectedEntityIds}; got $hitIds"
                    }
                }
            }
        }

        assertTrue(failures.joinToString(separator = "\n"), failures.isEmpty())
    }

    @Test
    fun `generic stat usage questions resolve only when the pack has stat evidence`() = runTest {
        val failures = mutableListOf<String>()
        val statQuestions = listOf("攻击力高有什么用", "防御力高有什么用", "速度高有什么用")

        STAT_GUARD_PACKS.forEach { pack ->
            val fixture = loadPack(pack.slug)
            val pipeline = LocalKnowledgeRetrievalPipeline(FixtureKnowledgeRepository(fixture.knowledge))
            val gameId = fixture.knowledge.firstOrNull()?.gameId
                ?: return@forEach

            statQuestions.forEach { question ->
                val normalizedQuestion = GameTermNormalizer()
                    .normalize(pipeline.normalizeQuestion(question, "zh"), fixture.knowledge)
                    .normalizedQuestion
                val results = pipeline.retrieve(
                    RetrievalQuery(
                        gameId = gameId,
                        normalizedQuery = normalizedQuestion,
                        language = "zh",
                        progressGate = "start",
                        spoilerLevel = SpoilerLevel.LIGHT,
                        limit = 5,
                    )
                )
                val hitIds = results.map { it.entityId }
                if (pack.slug == "golden-sun-gba-zh") {
                    if ("mechanic.stats-equipment" !in hitIds) {
                        failures += "${pack.slug}/$question should resolve to stats evidence; got $hitIds"
                    }
                } else if (hitIds.isNotEmpty()) {
                    failures += "${pack.slug}/$question should not resolve without stat evidence; got $hitIds"
                }
            }
        }

        assertTrue(failures.joinToString(separator = "\n"), failures.isEmpty())
    }

    @Test
    fun `runtime wording variants resolve without explicit progress gate when low spoiler`() = runTest {
        val failures = mutableListOf<String>()

        RUNTIME_WORDING_CASES.forEach { case ->
            val fixture = loadPack(case.packSlug)
            val pipeline = LocalKnowledgeRetrievalPipeline(FixtureKnowledgeRepository(fixture.knowledge))
            val normalizedQuestion = GameTermNormalizer()
                .normalize(pipeline.normalizeQuestion(case.question, "zh"), fixture.knowledge)
                .normalizedQuestion
            val results = pipeline.retrieve(
                RetrievalQuery(
                    gameId = case.gameId,
                    normalizedQuery = normalizedQuestion,
                    language = "zh",
                    progressGate = null,
                    spoilerLevel = SpoilerLevel.LIGHT,
                    limit = 5,
                )
            )
            val hit = results.firstOrNull { it.entityId == case.expectedEntityId }
            if (hit == null) {
                failures += "${case.packSlug}/${case.question} expected ${case.expectedEntityId}; " +
                    "got ${results.map { it.entityId }}"
            } else if (hit.answerType != case.expectedType) {
                failures += "${case.packSlug}/${case.question} expected type ${case.expectedType}; " +
                    "got ${hit.answerType}"
            }
        }

        assertTrue(failures.joinToString(separator = "\n"), failures.isEmpty())
    }

    private data class Pack(
        val slug: String,
    )

    private data class RuntimeWordingCase(
        val packSlug: String,
        val gameId: String,
        val question: String,
        val expectedEntityId: String,
        val expectedType: AnswerType,
    )

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

    private fun loadPack(slug: String): PackFixture {
        val packDir = moduleRoot()
            .resolve("src/main/assets/gkp/$slug")
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
        val PACKS = listOf(
            Pack("golden-sun-gba-zh"),
            Pack("phantasy-star-iv-md-zh"),
            Pack("langrisser-ii-md-zh"),
            Pack("chrono-trigger-snes-zh"),
            Pack("final-fantasy-vi-snes-zh"),
        )
        val STAT_GUARD_PACKS = PACKS + Pack("shining-force-ii-md")
        val RUNTIME_WORDING_CASES = listOf(
            RuntimeWordingCase(
                packSlug = "chrono-trigger-snes-zh",
                gameId = "chrono_trigger_snes",
                question = "三人技要不要背全？",
                expectedEntityId = "mechanic.dual-triple-techs",
                expectedType = AnswerType.Mechanic,
            ),
            RuntimeWordingCase(
                packSlug = "phantasy-star-iv-md-zh",
                gameId = "phantasy_star_iv_md",
                question = "组合技要不要一开始研究？",
                expectedEntityId = "mechanic.combo-attacks",
                expectedType = AnswerType.Mechanic,
            ),
            RuntimeWordingCase(
                packSlug = "shining-force-ii-md",
                gameId = "shining_force_ii_md",
                question = "帕卡隆是什么地方？",
                expectedEntityId = "location.pacalon",
                expectedType = AnswerType.Location,
            ),
            RuntimeWordingCase(
                packSlug = "shining-force-ii-md",
                gameId = "shining_force_ii_md",
                question = "克拉肯怎么过？",
                expectedEntityId = "boss.kraken",
                expectedType = AnswerType.Strategy,
            ),
            RuntimeWordingCase(
                packSlug = "shining-force-ii-md",
                gameId = "shining_force_ii_md",
                question = "红男爵是谁？",
                expectedEntityId = "boss.red-baron",
                expectedType = AnswerType.NameMapping,
            ),
        )

        fun normalizeForSearch(value: String): String =
            value.lowercase()
                .replace(PUNCTUATION, " ")
                .replace(WHITESPACE, " ")
                .trim()
    }
}
