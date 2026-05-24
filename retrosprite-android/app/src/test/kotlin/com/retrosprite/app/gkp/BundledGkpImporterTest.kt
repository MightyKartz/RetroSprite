package com.retrosprite.app.gkp

import com.retrosprite.app.data.gkp.BundledGkpAssetReader
import com.retrosprite.app.data.gkp.BundledGkpImportPhase
import com.retrosprite.app.data.gkp.BundledGkpImporter
import com.retrosprite.app.data.gkp.GkpV0Parser
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

class BundledGkpImporterTest {

    @Test
    fun `imports every manifest backed pack discovered under bundled gkp assets`() = runTest {
        val reader = FakeAssetReader(
            directories = mapOf(
                "gkp" to listOf("z-pack", "readme.txt", "a-pack", "empty-dir"),
            ),
            files = mapOf(
                "gkp/a-pack/manifest.json" to manifest(
                    packId = "sample.a-pack",
                    gameId = "a_game",
                    title = "A Game",
                ),
                "gkp/a-pack/knowledge.jsonl" to knowledge("a.entity"),
                "gkp/z-pack/manifest.json" to manifest(
                    packId = "sample.z-pack",
                    gameId = "z_game",
                    title = "Z Game",
                ),
                "gkp/z-pack/knowledge.jsonl" to knowledge("z.entity"),
            ),
        )
        val games = FakeGameRepository()
        val knowledge = FakeKnowledgeRepository()
        val importer = BundledGkpImporter(
            assetReader = reader,
            gameRepository = games,
            knowledgeRepository = knowledge,
            parser = GkpV0Parser(nowMillis = { 42L }),
        )

        val status = importer.importBundledPacks()

        assertEquals(BundledGkpImportPhase.Ready, status.phase)
        assertEquals(2, status.totalPacks)
        assertEquals(2, status.importedPacks)
        assertEquals(0, status.failedPacks)
        assertEquals(setOf("a_game", "z_game"), games.rows.keys)
        assertEquals(setOf("a.entity", "z.entity"), knowledge.rows.map { it.entityId }.toSet())
        assertTrue(knowledge.rows.all { it.gameId in setOf("a_game", "z_game") })
    }

    private class FakeAssetReader(
        private val directories: Map<String, List<String>>,
        private val files: Map<String, String>,
    ) : BundledGkpAssetReader {
        override fun list(path: String): List<String> = directories[path].orEmpty()

        override fun readText(path: String): String =
            files[path] ?: error("Missing fake asset: $path")
    }

    private class FakeGameRepository : GameRepository {
        val rows: MutableMap<String, GameDomain> = linkedMapOf()

        override fun observeAll(): Flow<List<GameDomain>> = flowOf(rows.values.toList())
        override suspend fun getById(gameId: String): GameDomain? = rows[gameId]
        override suspend fun getByRomSha1(sha1: String): GameDomain? = null
        override suspend fun getByRomCrc32(crc32: String): GameDomain? = null
        override suspend fun searchByLabel(platform: String, titleQuery: String): List<GameDomain> =
            rows.values.filter { it.platform == platform && it.title.contains(titleQuery.trim('%'), ignoreCase = true) }

        override suspend fun upsert(game: GameDomain) {
            rows[game.gameId] = game
        }

        override suspend fun delete(gameId: String) {
            rows.remove(gameId)
        }
    }

    private class FakeKnowledgeRepository : KnowledgeRepository {
        val rows: MutableList<KnowledgeChunkDomain> = mutableListOf()

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

    private fun manifest(
        packId: String,
        gameId: String,
        title: String,
    ): String =
        """
        {
          "schema_version": "gkp.v0",
          "pack_id": "$packId",
          "pack_version": "1.0.0",
          "trust_level": "sample",
          "game": {
            "game_id": "$gameId",
            "title": "$title",
            "platform": "sample",
            "languages": ["zh"]
          },
          "contents": {
            "knowledge": ["knowledge.jsonl"]
          }
        }
        """.trimIndent()

    private fun knowledge(entityId: String): String =
        """
        {"entity_id":"$entityId","entity_type":"mechanic","canonical_name":"Core","aliases":["core"],"description_short":"Core row","spoiler_level":"none","source_refs":["sample.source"],"confidence":"verified"}
        """.trimIndent()
}
