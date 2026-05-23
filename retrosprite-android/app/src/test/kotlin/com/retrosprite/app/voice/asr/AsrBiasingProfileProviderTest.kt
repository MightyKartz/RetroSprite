package com.retrosprite.app.voice.asr

import com.retrosprite.app.data.models.GameDomain
import com.retrosprite.app.data.models.KnowledgeChunkDomain
import com.retrosprite.app.data.repository.GameRepository
import com.retrosprite.app.data.repository.KnowledgeRepository
import com.retrosprite.app.data.resolver.RepositoryGameResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrBiasingProfileProviderTest {

    @Test
    fun `builds profile from retroarch label`() = runTest {
        val games = FakeGameRepository()
        val provider = AsrBiasingProfileProvider(
            resolver = RepositoryGameResolver(games),
            gameRepository = games,
            knowledgeRepository = FakeKnowledgeRepository(),
            extractor = GkpAsrHotwordExtractor(),
        )

        val profile = provider.profileForLabel("mega_drive__光明力量2")

        requireNotNull(profile)
        assertEquals("shining_force_ii_md", profile.gameId)
        assertEquals("0.2.5", profile.packVersion)
        assertTrue(profile.normalizedEntries.any { it.term == "修伊" })
    }

    @Test
    fun `resolves diagnostic mode and strips marker before game lookup`() = runTest {
        val games = FakeGameRepository()
        val provider = AsrBiasingProfileProvider(
            resolver = RepositoryGameResolver(games),
            gameRepository = games,
            knowledgeRepository = FakeKnowledgeRepository(),
            extractor = GkpAsrHotwordExtractor(),
        )

        val resolution = provider.resolveForLabel("mega_drive__光明力量2@@asr:stream_small")

        assertEquals("mega_drive__光明力量2", resolution.label)
        assertEquals(AsrHotwordMode.StreamSmall, resolution.hotwordMode)
        assertEquals("shining_force_ii_md", resolution.profile?.gameId)
    }

    @Test
    fun `none mode strips marker and disables profile`() = runTest {
        val games = FakeGameRepository()
        val provider = AsrBiasingProfileProvider(
            resolver = RepositoryGameResolver(games),
            gameRepository = games,
            knowledgeRepository = FakeKnowledgeRepository(),
            extractor = GkpAsrHotwordExtractor(),
        )

        val resolution = provider.resolveForLabel("mega_drive__光明力量2@@asr:none")

        assertEquals("mega_drive__光明力量2", resolution.label)
        assertEquals(AsrHotwordMode.None, resolution.hotwordMode)
        assertEquals(null, resolution.profile)
    }

    private class FakeGameRepository : GameRepository {
        private val game = GameDomain(
            gameId = "shining_force_ii_md",
            packId = "community.shining-force-ii-md",
            title = "Shining Force II / 光明力量2",
            platform = "mega_drive",
            region = null,
            languages = listOf("zh", "en"),
            romCrc32 = null,
            romSha1 = null,
            packVersion = "0.2.5",
            schemaVersion = "gkp.v0",
            trustLevel = "community",
            installedAt = 0L,
        )

        override fun observeAll(): Flow<List<GameDomain>> = flowOf(listOf(game))
        override suspend fun getById(gameId: String): GameDomain? = game.takeIf { it.gameId == gameId }
        override suspend fun getByRomSha1(sha1: String): GameDomain? = null
        override suspend fun getByRomCrc32(crc32: String): GameDomain? = null
        override suspend fun searchByLabel(platform: String, titleQuery: String): List<GameDomain> = listOf(game)
        override suspend fun upsert(game: GameDomain) = Unit
        override suspend fun delete(gameId: String) = Unit
    }

    private class FakeKnowledgeRepository : KnowledgeRepository {
        override suspend fun listByGame(gameId: String): List<KnowledgeChunkDomain> =
            listOf(
                KnowledgeChunkDomain(
                    id = 0L,
                    gameId = gameId,
                    entityId = "npc.chester",
                    entityType = "npc",
                    canonicalName = "Chester / 切斯特",
                    aliases = listOf("修伊", "Chester"),
                    descriptionShort = "",
                    descriptionLong = null,
                    progressGate = "start",
                    spoilerLevel = "light",
                    sourceRefs = listOf("sf2.manual_translation"),
                    confidence = "community",
                    answerTemplates = emptyList(),
                ),
            )

        override suspend fun searchFts(gameId: String, query: String, limit: Int) = emptyList<KnowledgeChunkDomain>()
        override suspend fun getByEntityId(gameId: String, entityId: String): KnowledgeChunkDomain? = null
        override suspend fun listByType(gameId: String, entityType: String) = emptyList<KnowledgeChunkDomain>()
        override suspend fun upsertAll(chunks: List<KnowledgeChunkDomain>) = Unit
        override suspend fun clearForGame(gameId: String) = Unit
    }
}
