package com.retrosprite.app.data.resolver

import com.retrosprite.app.data.models.GameDomain
import com.retrosprite.app.data.repository.GameRepository
import com.retrosprite.app.domain.models.GameIdentity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepositoryGameResolverTest {

    private val sample2048 = GameDomain(
        gameId = "2048",
        title = "2048",
        platform = "libretro",
        region = null,
        languages = listOf("zh", "en"),
        romCrc32 = null,
        romSha1 = null,
        packVersion = "0.1.0",
        schemaVersion = "gkp.v0",
        trustLevel = "sample",
        installedAt = 1L,
    )

    @Test
    fun `resolves RetroArch empty-title label to installed game id candidate`() = runTest {
        val resolver = RepositoryGameResolver(FakeGameRepository(listOf(sample2048)))

        val identity = resolver.resolve("2048__")

        assertEquals("2048", identity.gameId)
        assertEquals("2048", identity.title)
        assertEquals("libretro", identity.platform)
        assertEquals("gkp_index", identity.source)
    }

    @Test
    fun `resolves by rom hash before label fallback`() = runTest {
        val game = sample2048.copy(gameId = "hash-game", romSha1 = "abc123")
        val resolver = RepositoryGameResolver(FakeGameRepository(listOf(game)))

        val identity = resolver.resolve("unknown__", romHash = "abc123")

        assertEquals("hash-game", identity.gameId)
        assertEquals("rom_sha1", identity.source)
    }

    @Test
    fun `falls back to label identity when repository has no match`() = runTest {
        val resolver = RepositoryGameResolver(FakeGameRepository(emptyList()))

        val identity = resolver.resolve("snes__super_mario_world")

        assertNull(identity.gameId)
        assertEquals("Super Mario World", identity.title)
        assertEquals("label", identity.source)
    }

    @Test
    fun `ignores disabled installed games during resolution`() = runTest {
        val resolver = RepositoryGameResolver(
            FakeGameRepository(
                listOf(sample2048.copy(isEnabled = false, disabledAt = 123L))
            )
        )

        val identity = resolver.resolve("2048__")

        assertNull(identity.gameId)
        assertEquals("2048", identity.title)
        assertEquals("libretro", identity.platform)
        assertEquals(GameIdentity.SOURCE_GKP_DISABLED, identity.source)
    }

    @Test
    fun `reports disabled rom hash match without resolving to game id`() = runTest {
        val resolver = RepositoryGameResolver(
            FakeGameRepository(
                listOf(sample2048.copy(romSha1 = "abc123", isEnabled = false, disabledAt = 123L))
            )
        )

        val identity = resolver.resolve("unknown__", romHash = "abc123")

        assertNull(identity.gameId)
        assertEquals("2048", identity.title)
        assertEquals(GameIdentity.SOURCE_GKP_DISABLED, identity.source)
    }

    private class FakeGameRepository(
        private val games: List<GameDomain>,
    ) : GameRepository {
        override fun observeAll(): Flow<List<GameDomain>> = flowOf(games)
        override suspend fun getById(gameId: String): GameDomain? =
            games.firstOrNull { it.gameId == gameId }

        override suspend fun getByRomSha1(sha1: String): GameDomain? =
            games.firstOrNull { it.romSha1 == sha1 }

        override suspend fun getByRomCrc32(crc32: String): GameDomain? =
            games.firstOrNull { it.romCrc32 == crc32 }

        override suspend fun searchByLabel(platform: String, titleQuery: String): List<GameDomain> =
            games.filter {
                it.platform == platform && it.title.contains(titleQuery.trim('%'), ignoreCase = true)
            }

        override suspend fun upsert(game: GameDomain) = Unit
        override suspend fun delete(gameId: String) = Unit
    }
}
