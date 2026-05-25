package com.retrosprite.app.ui.integration

import org.junit.Assert.assertEquals
import org.junit.Test

class SherpaOnnxRecognizerFactoryTest {

    @Test
    fun `default config wires paraformer encoder and decoder with greedy search`() {
        val model = SherpaOnnxAsrModel.defaultModel()
        val config = SherpaOnnxRecognizerFactory.createConfig(model = model)

        assertEquals("greedy_search", config.decodingMethod)
        assertEquals("", config.hotwordsFile)
        assertEquals(0.0f, config.hotwordsScore, 0.001f)
        assertEquals("", config.modelConfig.modelingUnit)
        assertEquals(4, config.maxActivePaths)
        assertEquals("${model.assetDir}/encoder.int8.onnx", config.modelConfig.paraformer.encoder)
        assertEquals("${model.assetDir}/decoder.int8.onnx", config.modelConfig.paraformer.decoder)
        assertEquals("", config.modelConfig.transducer.encoder)
        assertEquals("", config.modelConfig.transducer.decoder)
        assertEquals("", config.modelConfig.transducer.joiner)
        assertEquals("${model.assetDir}/tokens.txt", config.modelConfig.tokens)
        assertEquals(2.8f, config.endpointConfig.rule2.minTrailingSilence, 0.001f)
        assertEquals(2.0f, SherpaOnnxVoiceInputProvider.FINAL_FLUSH_SILENCE_SECONDS, 0.001f)
    }
}
