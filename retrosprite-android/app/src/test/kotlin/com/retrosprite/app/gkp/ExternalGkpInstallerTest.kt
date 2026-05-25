package com.retrosprite.app.gkp

import com.retrosprite.app.data.gkp.ExternalGkpInstaller
import com.retrosprite.app.data.gkp.GkpExternalInstallMode
import com.retrosprite.app.data.gkp.GkpPackProvenance
import com.retrosprite.app.data.gkp.GkpPreflightInput
import com.retrosprite.app.data.gkp.GkpSignatureStatus
import com.retrosprite.app.data.gkp.GkpV0Parser
import com.retrosprite.app.data.gkp.GkpV0PreflightValidator
import com.retrosprite.app.data.models.GameDomain
import com.retrosprite.app.data.models.KnowledgeChunkDomain
import com.retrosprite.app.data.repository.GameRepository
import com.retrosprite.app.data.repository.KnowledgeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ExternalGkpInstallerTest {

    private val validator = GkpV0PreflightValidator()

    @Test
    fun `creates overwrite plan from passing preflight`() = runTest {
        val games = FakeGameRepository(
            existing = listOf(existingGoldenSun(packVersion = "0.0.1")),
        )
        val knowledge = FakeKnowledgeRepository(
            initial = listOf(
                knowledge("golden_sun_gba", "old.one"),
                knowledge("golden_sun_gba", "old.two"),
            )
        )
        val installer = ExternalGkpInstaller(
            gameRepository = games,
            knowledgeRepository = knowledge,
        )
        val report = validator.validate(readPack("golden-sun-gba-zh"))

        val plan = installer.createPlan(report)

        assertEquals(GkpExternalInstallMode.ReplaceExisting, plan.mode)
        assertEquals("community.golden-sun-gba-zh", plan.packId)
        assertEquals("golden_sun_gba", plan.gameId)
        assertEquals("0.0.1", plan.currentPackVersion)
        assertEquals("0.1.2", plan.newPackVersion)
        assertEquals("lite", plan.coverageTier)
        assertEquals(2, plan.currentKnowledgeRows)
        assertEquals(42, plan.newKnowledgeRows)
        assertEquals(40, plan.knowledgeDelta)
        assertEquals(GkpPackProvenance.External.id, plan.provenance)
        assertEquals(GkpSignatureStatus.Unsigned.id, plan.signatureStatus)
        assertTrue(plan.contentDigest.orEmpty().matches(Regex("[a-f0-9]{64}")))
    }

    @Test
    fun `install revalidates input and replaces game knowledge rows`() = runTest {
        var transactionCalls = 0
        val games = FakeGameRepository(existing = listOf(existingGoldenSun(packVersion = "0.0.1")))
        val knowledge = FakeKnowledgeRepository(initial = listOf(knowledge("golden_sun_gba", "old.one")))
        val installer = ExternalGkpInstaller(
            gameRepository = games,
            knowledgeRepository = knowledge,
            parser = GkpV0Parser(nowMillis = { 999L }),
            nowMillis = { 1234L },
            runInTransaction = { block ->
                transactionCalls += 1
                block()
            },
        )

        val result = installer.install(readPack("golden-sun-gba-zh"))

        assertEquals(1, transactionCalls)
        assertEquals(GkpExternalInstallMode.ReplaceExisting, result.plan.mode)
        assertEquals(42, result.installedKnowledgeRows)
        assertEquals(1234L, result.installedAtMillis)
        assertEquals("0.1.2", games.rows.getValue("golden_sun_gba").packVersion)
        assertEquals("lite", games.rows.getValue("golden_sun_gba").coverageTier)
        assertEquals("community.golden-sun-gba-zh", games.rows.getValue("golden_sun_gba").packId)
        assertEquals(GkpPackProvenance.External.id, games.rows.getValue("golden_sun_gba").provenance)
        assertEquals(GkpSignatureStatus.Unsigned.id, games.rows.getValue("golden_sun_gba").signatureStatus)
        assertTrue(games.rows.getValue("golden_sun_gba").contentDigest.orEmpty().matches(Regex("[a-f0-9]{64}")))
        assertEquals(999L, games.rows.getValue("golden_sun_gba").installedAt)
        assertEquals(42, knowledge.rows.count { it.gameId == "golden_sun_gba" })
        assertTrue(knowledge.rows.none { it.entityId == "old.one" })
    }

    @Test
    fun `install rejects input when preflight fails`() = runTest {
        val input = readPack("golden-sun-gba-zh")
        val badInput = input.copy(
            files = input.files - "sources/licenses.md",
            allPaths = input.allPaths - "sources/licenses.md",
        )
        val installer = ExternalGkpInstaller(
            gameRepository = FakeGameRepository(),
            knowledgeRepository = FakeKnowledgeRepository(),
        )

        val failure = runCatching { installer.install(badInput) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("missing_license"))
    }

    private class FakeGameRepository(
        existing: List<GameDomain> = emptyList(),
    ) : GameRepository {
        val rows: MutableMap<String, GameDomain> = existing.associateBy { it.gameId }.toMutableMap()

        override fun observeAll(): Flow<List<GameDomain>> = flowOf(rows.values.toList())
        override suspend fun getById(gameId: String): GameDomain? = rows[gameId]
        override suspend fun getByRomSha1(sha1: String): GameDomain? =
            rows.values.firstOrNull { it.romSha1 == sha1 }

        override suspend fun getByRomCrc32(crc32: String): GameDomain? =
            rows.values.firstOrNull { it.romCrc32 == crc32 }

        override suspend fun searchByLabel(platform: String, titleQuery: String): List<GameDomain> =
            rows.values.filter { it.platform == platform && it.title.contains(titleQuery.trim('%'), ignoreCase = true) }

        override suspend fun upsert(game: GameDomain) {
            rows[game.gameId] = game
        }

        override suspend fun delete(gameId: String) {
            rows.remove(gameId)
        }
    }

    private class FakeKnowledgeRepository(
        initial: List<KnowledgeChunkDomain> = emptyList(),
    ) : KnowledgeRepository {
        val rows: MutableList<KnowledgeChunkDomain> = initial.toMutableList()

        override suspend fun searchFts(
            gameId: String,
            query: String,
            limit: Int,
        ): List<KnowledgeChunkDomain> = rows.filter { it.gameId == gameId }.take(limit)

        override suspend fun getByEntityId(gameId: String, entityId: String): KnowledgeChunkDomain? =
            rows.firstOrNull { it.gameId == gameId && it.entityId == entityId }

        override suspend fun listByGame(gameId: String): List<KnowledgeChunkDomain> =
            rows.filter { it.gameId == gameId }

        override suspend fun listByType(gameId: String, entityType: String): List<KnowledgeChunkDomain> =
            rows.filter { it.gameId == gameId && it.entityType == entityType }

        override suspend fun upsertAll(chunks: List<KnowledgeChunkDomain>) {
            rows += chunks
        }

        override suspend fun clearForGame(gameId: String) {
            rows.removeAll { it.gameId == gameId }
        }
    }

    private fun existingGoldenSun(packVersion: String): GameDomain = GameDomain(
        gameId = "golden_sun_gba",
        packId = "community.golden-sun-gba-zh",
        title = "Golden Sun / 黄金太阳",
        platform = "gba",
        region = null,
        languages = listOf("zh"),
        romCrc32 = null,
        romSha1 = null,
        packVersion = packVersion,
        schemaVersion = "gkp.v0",
        trustLevel = "community",
        installedAt = 1L,
    )

    private fun knowledge(gameId: String, entityId: String): KnowledgeChunkDomain = KnowledgeChunkDomain(
        id = 0L,
        gameId = gameId,
        entityId = entityId,
        entityType = "mechanic",
        canonicalName = entityId,
        aliases = listOf(entityId),
        descriptionShort = entityId,
        descriptionLong = null,
        progressGate = null,
        spoilerLevel = "none",
        sourceRefs = listOf("sample.source"),
        confidence = "verified",
        answerTemplates = emptyList(),
    )

    private fun readPack(packName: String): GkpPreflightInput {
        val packDir = moduleRoot()
            .resolve("src/main/assets/gkp/$packName")
            .normalize()
        val files = linkedMapOf<String, String>()
        Files.walk(packDir).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .forEach { path ->
                    val relative = packDir.relativize(path).toString().replace('\\', '/')
                    files[relative] = Files.readAllBytes(path).toString(Charsets.UTF_8)
                }
        }
        return GkpPreflightInput(
            displayName = packName,
            files = files,
            allPaths = files.keys,
        )
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
}
