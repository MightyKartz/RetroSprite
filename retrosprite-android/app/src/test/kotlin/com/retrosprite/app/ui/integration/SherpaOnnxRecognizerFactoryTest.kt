package com.retrosprite.app.ui.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaOnnxRecognizerFactoryTest {

    @Test
    fun `transducer hotwords config switches to modified beam search`() {
        val config = SherpaOnnxRecognizerFactory.createConfig(
            model = transducerModel(),
            enableHotwords = true,
        )

        assertEquals("modified_beam_search", config.decodingMethod)
        assertEquals("", config.hotwordsFile)
        assertEquals(4.0f, config.hotwordsScore, 0.001f)
        assertEquals("cjkchar", config.modelConfig.modelingUnit)
        assertTrue(config.maxActivePaths >= 4)
    }

    @Test
    fun `paraformer hotwords request stays greedy search`() {
        val config = SherpaOnnxRecognizerFactory.createConfig(
            model = paraformerModel(),
            hotwordsFile = "asr-hotwords/shining-force-ii-md-small.hotwords.txt",
            enableHotwords = true,
        )

        assertEquals("greedy_search", config.decodingMethod)
        assertEquals("", config.hotwordsFile)
        assertEquals(0.0f, config.hotwordsScore, 0.001f)
        assertEquals("", config.modelConfig.modelingUnit)
        assertEquals(4, config.maxActivePaths)
    }

    @Test
    fun `default hotwords config wires transducer native hotword decoding`() {
        val model = SherpaOnnxAsrModel.defaultModel()
        val config = SherpaOnnxRecognizerFactory.createConfig(
            model = model,
            enableHotwords = true,
        )

        assertEquals("modified_beam_search", config.decodingMethod)
        assertEquals("cjkchar", config.modelConfig.modelingUnit)
        assertEquals(4.0f, config.hotwordsScore, 0.001f)
        assertEquals(8, config.maxActivePaths)
        assertEquals("", config.hotwordsFile)
    }

    @Test
    fun `default config wires transducer encoder decoder and joiner`() {
        val model = SherpaOnnxAsrModel.defaultModel()
        val config = SherpaOnnxRecognizerFactory.createConfig(model = model)

        assertEquals("${model.assetDir}/encoder-epoch-99-avg-1.int8.onnx", config.modelConfig.transducer.encoder)
        assertEquals("${model.assetDir}/decoder-epoch-99-avg-1.onnx", config.modelConfig.transducer.decoder)
        assertEquals("${model.assetDir}/joiner-epoch-99-avg-1.int8.onnx", config.modelConfig.transducer.joiner)
        assertEquals("", config.modelConfig.paraformer.encoder)
        assertEquals("", config.modelConfig.paraformer.decoder)
        assertEquals("${model.assetDir}/tokens.txt", config.modelConfig.tokens)
    }

    @Test
    fun `asset hotwords config keeps asset file path`() {
        val config = SherpaOnnxRecognizerFactory.createConfig(
            model = transducerModel(),
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

    private fun transducerModel(): SherpaOnnxAsrModel =
        SherpaOnnxAsrModel(
            architecture = SherpaOnnxAsrModel.Architecture.Transducer,
            assetDir = "test-transducer",
            encoderAsset = "test-transducer/encoder.onnx",
            decoderAsset = "test-transducer/decoder.onnx",
            joinerAsset = "test-transducer/joiner.onnx",
            tokensAsset = "test-transducer/tokens.txt",
            modelType = "zipformer",
            engineLabel = "test transducer",
        )

    private fun paraformerModel(): SherpaOnnxAsrModel =
        SherpaOnnxAsrModel(
            architecture = SherpaOnnxAsrModel.Architecture.Paraformer,
            assetDir = "test-paraformer",
            encoderAsset = "test-paraformer/encoder.int8.onnx",
            decoderAsset = "test-paraformer/decoder.int8.onnx",
            tokensAsset = "test-paraformer/tokens.txt",
            modelType = "",
            engineLabel = "test paraformer",
        )
}
