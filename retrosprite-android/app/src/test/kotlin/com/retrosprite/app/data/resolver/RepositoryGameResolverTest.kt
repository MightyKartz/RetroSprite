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

    @Test
    fun `resolves bundled gkp retroarch platform aliases and rom-style titles`() = runTest {
        val resolver = RepositoryGameResolver(
            FakeGameRepository(
                listOf(
                    game("chrono_trigger_snes", "Chrono Trigger / 时空之轮", "snes"),
                    game(
                        "final_fantasy_vi_snes",
                        "Final Fantasy VI / 最终幻想 VI",
                        "snes",
                        retroarchLabels = listOf(
                            "playstation__Final Fantasy Anthology - Final Fantasy VI",
                        ),
                    ),
                    game("golden_sun_gba", "Golden Sun / 黄金太阳", "gba"),
                    game("langrisser_ii_md", "Langrisser II / 梦幻模拟战 II", "md"),
                    game("phantasy_star_iv_md", "Phantasy Star IV / 梦幻之星 IV", "md"),
                    game(
                        "shining_force_ii_md",
                        "Shining Force II / 光明力量2",
                        "md",
                        retroarchLabels = listOf(
                            "Sega - Mega Drive - Genesis__光明力量2",
                            "md__光明與黑暗續戰篇Ⅱ 古代的封印",
                        ),
                    ),
                )
            )
        )

        val cases = listOf(
            "sfc__Chrono Trigger (USA)" to "chrono_trigger_snes",
            "playstation__Final Fantasy Anthology - Final Fantasy VI" to "final_fantasy_vi_snes",
            "super_nintendo__Final Fantasy VI (USA)" to "final_fantasy_vi_snes",
            "snes__最终幻想VI" to "final_fantasy_vi_snes",
            "game_boy_advance__黄金太阳-开启的封印" to "golden_sun_gba",
            "gba__Golden Sun (USA)" to "golden_sun_gba",
            "md__Langrisser II (Japan)" to "langrisser_ii_md",
            "mega_drive__梦幻模拟战2" to "langrisser_ii_md",
            "genesis__Phantasy_Star_IV" to "phantasy_star_iv_md",
            "md__梦幻之星IV 千年纪的终结" to "phantasy_star_iv_md",
            "Sega - Mega Drive - Genesis__光明力量2" to "shining_force_ii_md",
            "md__光明與黑暗續戰篇Ⅱ 古代的封印" to "shining_force_ii_md",
        )

        cases.forEach { (label, expectedGameId) ->
            val identity = resolver.resolve(label)

            assertEquals("label=<$label>", expectedGameId, identity.gameId)
            assertEquals("label=<$label>", "gkp_index", identity.source)
        }
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

    private fun game(
        gameId: String,
        title: String,
        platform: String,
        retroarchSystemIds: List<String> = emptyList(),
        retroarchLabels: List<String> = emptyList(),
    ): GameDomain = sample2048.copy(
        gameId = gameId,
        title = title,
        platform = platform,
        retroarchSystemIds = retroarchSystemIds,
        retroarchLabels = retroarchLabels,
        packId = "community.$gameId",
        trustLevel = "community",
    )
}
