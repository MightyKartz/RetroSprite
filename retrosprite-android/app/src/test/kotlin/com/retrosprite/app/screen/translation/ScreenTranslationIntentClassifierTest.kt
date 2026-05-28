package com.retrosprite.app.screen.translation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTranslationIntentClassifierTest {

    private val classifier = ScreenTranslationIntentClassifier()

    @Test
    fun `detects Chinese translation requests`() {
        assertTrue(classifier.isScreenTranslationRequest("翻译"))
        assertTrue(classifier.isScreenTranslationRequest("翻译一下"))
        assertTrue(classifier.isScreenTranslationRequest("帮我翻译一下"))
        assertTrue(classifier.isScreenTranslationRequest("翻译一"))
        assertTrue(classifier.isScreenTranslationRequest("读一下"))
        assertTrue(classifier.isScreenTranslationRequest("这是什么意思"))
        assertTrue(classifier.isScreenTranslationRequest("这段话是什么意思"))
        assertTrue(classifier.isScreenTranslationRequest("这句话啥意思"))
    }

    @Test
    fun `normalizes common tail-dropped translation command`() {
        assertEquals("翻译一下", classifier.normalizeScreenTranslationRequest("翻译一"))
        assertEquals("翻译一下", classifier.normalizeScreenTranslationRequest("请翻译一"))
        assertEquals("翻译一下", classifier.normalizeScreenTranslationRequest("帮我翻译一。"))
    }

    @Test
    fun `normalizes standalone translation command`() {
        assertEquals("翻译一下", classifier.normalizeScreenTranslationRequest("翻译"))
        assertEquals("翻译一下", classifier.normalizeScreenTranslationRequest("请翻译"))
        assertEquals("翻译一下", classifier.normalizeScreenTranslationRequest("帮我翻译。"))
    }

    @Test
    fun `detects English translation requests`() {
        assertTrue(classifier.isScreenTranslationRequest("translate this"))
        assertTrue(classifier.isScreenTranslationRequest("read this"))
        assertTrue(classifier.isScreenTranslationRequest("what does this mean"))
    }

    @Test
    fun `does not steal normal game questions`() {
        assertFalse(classifier.isScreenTranslationRequest("什么时候转职"))
        assertFalse(classifier.isScreenTranslationRequest("这个角色强吗"))
        assertFalse(classifier.isScreenTranslationRequest("现在应该往哪里走"))
        assertFalse(classifier.isScreenTranslationRequest(""))
        assertNull(classifier.normalizeScreenTranslationRequest("什么时候转职"))
    }
}
