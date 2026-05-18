package com.retrosprite.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.retrosprite.app.data.db.RetroSpriteDatabase
import com.retrosprite.app.data.models.GameDomain
import com.retrosprite.app.data.repository.DefaultGameRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameDaoTest {

    private lateinit var db: RetroSpriteDatabase
    private lateinit var repository: DefaultGameRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = RetroSpriteDatabase.buildInMemory(context)
        repository = DefaultGameRepository(db.gameDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsertAndLookup_byHashAndLabel() = runTest {
        val metroid = sample(
            gameId = "nes.metroid",
            title = "Metroid",
            sha1 = "deadbeefcafebabedeadbeefcafebabedeadbeef",
            crc32 = "12345678"
        )
        val zelda = sample(
            gameId = "nes.zelda",
            title = "The Legend of Zelda",
            sha1 = "1111111111111111111111111111111111111111",
            crc32 = "abcdef00"
        )
        repository.upsert(metroid)
        repository.upsert(zelda)

        assertEquals(metroid, repository.getByRomSha1(metroid.romSha1!!))
        assertEquals(zelda, repository.getByRomCrc32(zelda.romCrc32!!))

        val zeldaMatches = repository.searchByLabel("NES", "Zelda")
        assertEquals(1, zeldaMatches.size)
        assertEquals(zelda, zeldaMatches.first())

        val metroidById = repository.getById("nes.metroid")
        assertNotNull(metroidById)
        assertEquals("Metroid", metroidById!!.title)
        assertTrue(metroidById.languages.contains("en"))
    }

    @Test
    fun delete_removesById() = runTest {
        val game = sample(gameId = "nes.kirby", title = "Kirby")
        repository.upsert(game)
        assertNotNull(repository.getById("nes.kirby"))
        repository.delete("nes.kirby")
        assertNull(repository.getById("nes.kirby"))
    }

    private fun sample(
        gameId: String,
        title: String,
        sha1: String? = null,
        crc32: String? = null
    ) = GameDomain(
        gameId = gameId,
        title = title,
        platform = "NES",
        region = "USA",
        languages = listOf("en"),
        romCrc32 = crc32,
        romSha1 = sha1,
        packVersion = "1.0.0",
        schemaVersion = "1",
        trustLevel = "official",
        installedAt = System.currentTimeMillis()
    )
}
