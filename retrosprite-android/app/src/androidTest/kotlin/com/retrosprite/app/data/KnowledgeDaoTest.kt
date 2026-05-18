package com.retrosprite.app.data

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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KnowledgeDaoTest {

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
    fun upsertAndLookup_byEntityIdAndType() = runTest {
        games.upsert(GAME)
        knowledge.upsertAll(SAMPLE_CHUNKS)

        val ridley = knowledge.getByEntityId(GAME.gameId, "boss.ridley")
        assertNotNull(ridley)
        assertEquals("Ridley", ridley!!.canonicalName)

        val bosses = knowledge.listByType(GAME.gameId, "boss")
        assertEquals(2, bosses.size)
        assertEquals(setOf("Kraid", "Ridley"), bosses.map { it.canonicalName }.toSet())
    }

    @Test
    fun clearForGame_removesAllChunks() = runTest {
        games.upsert(GAME)
        knowledge.upsertAll(SAMPLE_CHUNKS)
        knowledge.clearForGame(GAME.gameId)
        assertEquals(emptyList<KnowledgeChunkDomain>(), knowledge.listByGame(GAME.gameId))
        assertNull(knowledge.getByEntityId(GAME.gameId, "boss.ridley"))
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
            chunk("boss.ridley", "boss", "Ridley", listOf("Space Pirate Leader")),
            chunk("boss.kraid", "boss", "Kraid", listOf("Lizard Boss")),
            chunk("item.morph_ball", "item", "Morph Ball", listOf("Maru Mari"))
        )

        private fun chunk(
            entityId: String,
            entityType: String,
            name: String,
            aliases: List<String>
        ) = KnowledgeChunkDomain(
            id = 0L,
            gameId = GAME.gameId,
            entityId = entityId,
            entityType = entityType,
            canonicalName = name,
            aliases = aliases,
            descriptionShort = "$name is a notable entity.",
            descriptionLong = null,
            progressGate = null,
            spoilerLevel = "light",
            sourceRefs = listOf("https://example.com/$entityId"),
            confidence = "medium",
            answerTemplates = emptyList()
        )
    }
}
