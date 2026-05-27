package com.retrosprite.app.screen.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInScreenTranslationGlossaryRepositoryTest {

    private val repository = BuiltInScreenTranslationGlossaryRepository()

    @Test
    fun `returns FF6 glossary for SNES and PlayStation labels`() {
        val snes = repository.findForLabel("snes__Final Fantasy VI (USA)")
        val ps1 = repository.findForLabel("playstation__Final Fantasy Anthology - Final Fantasy VI")
        val chinese = repository.findForLabel("playstation__最终幻想6")

        assertNotNull(snes)
        assertNotNull(ps1)
        assertNotNull(chinese)
        assertEquals("final_fantasy_vi", snes?.gameId)
    }

    @Test
    fun `FF6 glossary includes menu magic item character skill and fixed prompt terms`() {
        val glossary = repository.findForLabel("playstation__Final Fantasy Anthology - Final Fantasy VI")
        val bySource = glossary?.terms.orEmpty().associateBy { it.source }

        assertEquals("道具", bySource["ITEM"]?.target)
        assertEquals("战斗", bySource["BATTLE"]?.target)
        assertEquals("当前经验", bySource["Your Exp"]?.target)
        assertEquals("升级所需", bySource["To Next Level"]?.target)
        assertEquals("火焰", bySource["Fire"]?.target)
        assertEquals("凤凰尾巴", bySource["Phoenix Down"]?.target)
        assertEquals("蒂娜", bySource["Terra"]?.target)
        assertEquals("偷盗", bySource["Steal"]?.target)
        assertEquals("MP 不足", bySource["Not enough MP"]?.target)
        assertTrue(glossary?.terms.orEmpty().size >= 40)
    }

    @Test
    fun `returns null for unrelated game labels`() {
        assertNull(repository.findForLabel("mega_drive__Shining Force II"))
    }
}
