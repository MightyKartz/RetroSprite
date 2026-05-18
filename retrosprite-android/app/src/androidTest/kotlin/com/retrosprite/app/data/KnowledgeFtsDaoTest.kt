package com.retrosprite.app.data

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.retrosprite.app.data.db.RetroSpriteDatabase
import com.retrosprite.app.data.models.GameDomain
import com.retrosprite.app.data.models.KnowledgeChunkDomain
import com.retrosprite.app.data.repository.DefaultGameRepository
import com.retrosprite.app.data.repository.DefaultKnowledgeRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the FTS5 path end-to-end: virtual table creation, trigger sync
 * and BM25-ranked retrieval. If FTS5 is not available on the host SQLite
 * (very rare on API 26+), the test is skipped via [Assume]. The fallback
 * LIKE path is exercised by [KnowledgeDaoTest].
 */
@RunWith(AndroidJUnit4::class)
class KnowledgeFtsDaoTest {

    private lateinit var db: RetroSpriteDatabase
    private lateinit var games: DefaultGameRepository
    private lateinit var knowledge: DefaultKnowledgeRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = RetroSpriteDatabase.buildInMemory(context)
        games = DefaultGameRepository(db.gameDao())
        knowledge = DefaultKnowledgeRepository(
            database = db,
            dao = db.knowledgeDao(),
            ftsDao = db.knowledgeFtsDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun ftsSearch_returnsMatchingRowOrFallsBackGracefully() = runTest {
        if (!db.isFtsAvailable()) {
            Log.w("KnowledgeFtsDaoTest", "FTS5 unavailable - skipping FTS5 specific assertions")
            Assume.assumeTrue("FTS5 unavailable on this device", db.isFtsAvailable())
        }
        games.upsert(GAME)
        knowledge.upsertAll(SAMPLE_CHUNKS)

        val results = knowledge.searchFts(GAME.gameId, "Ridley", limit = 5)
        assertTrue("Expected at least one Ridley match", results.isNotEmpty())
        assertEquals("Ridley", results.first().canonicalName)
    }

    @Test
    fun ftsSearch_byAliasReturnsCanonicalRow() = runTest {
        Assume.assumeTrue("FTS5 unavailable on this device", db.isFtsAvailable())
        games.upsert(GAME)
        knowledge.upsertAll(SAMPLE_CHUNKS)

        val byAlias = knowledge.searchFts(GAME.gameId, "Maru Mari", limit = 5)
        assertTrue("Expected alias-based match", byAlias.isNotEmpty())
        assertEquals("Morph Ball", byAlias.first().canonicalName)
    }

    @Test
    fun fallbackSearch_alwaysWorks() = runTest {
        // Validates that LIKE-based fallback can locate entries regardless
        // of FTS5 capability — used as a safety net by [KnowledgeRepository].
        games.upsert(GAME)
        knowledge.upsertAll(SAMPLE_CHUNKS)
        val rows = db.knowledgeDao().fallbackSearch(GAME.gameId, "%Morph%", 5)
        assertTrue(rows.any { it.canonicalName == "Morph Ball" })
    }

    private companion object {
        val GAME = GameDomain(
            gameId = "nes.metroid",
            title = "Metroid",
            platform = "NES",
            region = "USA",
            languages = listOf("en"),
            romCrc32 = "12345678",
            romSha1 = "deadbeefcafebabedeadbeefcafebabedeadbeef",
            packVersion = "1.0.0",
            schemaVersion = "1",
            trustLevel = "official",
            installedAt = 0L
        )

        val SAMPLE_CHUNKS = listOf(
            chunk(
                entityId = "boss.ridley",
                type = "boss",
                name = "Ridley",
                aliases = listOf("Space Pirate Leader"),
                desc = "Final boss of the original Metroid."
            ),
            chunk(
                entityId = "boss.kraid",
                type = "boss",
                name = "Kraid",
                aliases = listOf("Lizard Boss"),
                desc = "Mid-game boss of the original Metroid."
            ),
            chunk(
                entityId = "item.morph_ball",
                type = "item",
                name = "Morph Ball",
                aliases = listOf("Maru Mari"),
                desc = "Allows Samus to roll through narrow passages."
            )
        )

        private fun chunk(
            entityId: String,
            type: String,
            name: String,
            aliases: List<String>,
            desc: String
        ) = KnowledgeChunkDomain(
            id = 0L,
            gameId = GAME.gameId,
            entityId = entityId,
            entityType = type,
            canonicalName = name,
            aliases = aliases,
            descriptionShort = desc,
            descriptionLong = null,
            progressGate = null,
            spoilerLevel = "light",
            sourceRefs = listOf("https://example.com/$entityId"),
            confidence = "medium",
            answerTemplates = emptyList()
        )
    }
}
