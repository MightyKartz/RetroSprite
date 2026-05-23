package com.retrosprite.app.ui.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaOnnxRecognizerFactoryTest {

    @Test
    fun `hotwords config switches to modified beam search`() {
        val config = SherpaOnnxRecognizerFactory.createConfig(
            model = SherpaOnnxAsrModel.defaultModel(),
            hotwordsScore = 2.5f,
            enableHotwords = true,
        )

        assertEquals("modified_beam_search", config.decodingMethod)
        assertEquals("", config.hotwordsFile)
        assertEquals(2.5f, config.hotwordsScore, 0.001f)
        assertEquals("cjkchar", config.modelConfig.modelingUnit)
        assertTrue(config.maxActivePaths >= 4)
    }

    @Test
    fun `asset hotwords config keeps asset file path`() {
        val config = SherpaOnnxRecognizerFactory.createConfig(
            model = SherpaOnnxAsrModel.defaultModel(),
            hotwordsFile = "asr-hotwords/shining-force-ii-md-small.hotwords.txt",
            hotwordsScore = 2.5f,
            enableHotwords = true,
        )

        assertEquals("modified_beam_search", config.decodingMethod)
        assertEquals("asr-hotwords/shining-force-ii-md-small.hotwords.txt", config.hotwordsFile)
        assertEquals("cjkchar", config.modelConfig.modelingUnit)
    }

    @Test
    fun `default config keeps greedy search`() {
        val config = SherpaOnnxRecognizerFactory.createConfig(
            model = SherpaOnnxAsrModel.defaultModel(),
        )

        assertEquals("greedy_search", config.decodingMethod)
        assertEquals("", config.hotwordsFile)
        assertEquals("", config.modelConfig.modelingUnit)
    }
}
