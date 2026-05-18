package com.retrosprite.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.retrosprite.app.data.db.RetroSpriteDatabase
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke tests for the root [RetroSpriteDatabase] wiring: ensures Room can
 * generate code for all entities/DAOs and that the FTS5 callback runs
 * without throwing.
 */
@RunWith(AndroidJUnit4::class)
class RetroSpriteDatabaseTest {

    private lateinit var db: RetroSpriteDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = RetroSpriteDatabase.buildInMemory(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun database_exposesAllDaos() {
        assertNotNull(db.requestLogDao())
        assertNotNull(db.gameDao())
        assertNotNull(db.knowledgeDao())
        assertNotNull(db.knowledgeFtsDao())
    }

    @Test
    fun database_isFtsAvailable_resolvesWithoutCrash() {
        // Either true (FTS5 supported on the emulator/device) or false; the
        // contract is that the property does not throw.
        db.isFtsAvailable()
    }
}
