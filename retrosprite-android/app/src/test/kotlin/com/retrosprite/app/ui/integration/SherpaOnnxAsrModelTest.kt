package com.retrosprite.app.ui.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaOnnxAsrModelTest {

    @Test
    fun `default model uses only streaming paraformer assets`() {
        val model = SherpaOnnxAsrModel.defaultModel()

        assertEquals(
            "sherpa-onnx-streaming-paraformer-bilingual-zh-en",
            model.assetDir,
        )
        assertEquals("sherpa-onnx Paraformer 本地 ASR", model.engineLabel)
        assertEquals(16000, model.sampleRateHz)
        assertEquals(null, model.joinerAsset)
        assertEquals(
            listOf(
                "${model.assetDir}/encoder.int8.onnx",
                "${model.assetDir}/decoder.int8.onnx",
                "${model.assetDir}/tokens.txt",
            ),
            model.requiredAssetPaths,
        )
    }

    @Test
    fun `validation reports missing model assets without offering system fallback`() {
        val model = SherpaOnnxAsrModel.defaultModel()
        val existing = setOf(
            "${model.assetDir}/encoder.int8.onnx",
            "${model.assetDir}/tokens.txt",
        )

        val missing = model.missingAssetPaths(existing)
        val message = model.missingAssetsMessage(missing)

        assertEquals(
            listOf(
                "${model.assetDir}/decoder.int8.onnx",
            ),
            missing,
        )
        assertTrue(message.contains("sherpa-onnx 本地 ASR 模型未安装"))
        assertTrue(message.contains("decoder.int8.onnx"))
        assertFalse(message.contains("系统语音"))
        assertFalse(message.contains("云 ASR"))
    }
}
