package com.retrosprite.app.ui.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaOnnxAsrModelTest {

    @Test
    fun `default model uses compact chinese zipformer int8 assets`() {
        val model = SherpaOnnxAsrModel.defaultModel()

        assertEquals(
            "sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23",
            model.assetDir,
        )
        assertEquals("sherpa-onnx 本地 ASR", model.engineLabel)
        assertEquals(16000, model.sampleRateHz)
        assertEquals(4, model.requiredAssetPaths.size)
        assertTrue(model.requiredAssetPaths.any { it.endsWith("encoder-epoch-99-avg-1.int8.onnx") })
        assertTrue(model.requiredAssetPaths.any { it.endsWith("joiner-epoch-99-avg-1.int8.onnx") })
    }

    @Test
    fun `validation reports missing model assets without offering system fallback`() {
        val model = SherpaOnnxAsrModel.defaultModel()
        val existing = setOf(
            "${model.assetDir}/encoder-epoch-99-avg-1.int8.onnx",
            "${model.assetDir}/tokens.txt",
        )

        val missing = model.missingAssetPaths(existing)
        val message = model.missingAssetsMessage(missing)

        assertEquals(
            listOf(
                "${model.assetDir}/decoder-epoch-99-avg-1.onnx",
                "${model.assetDir}/joiner-epoch-99-avg-1.int8.onnx",
            ),
            missing,
        )
        assertTrue(message.contains("sherpa-onnx 本地 ASR 模型未安装"))
        assertTrue(message.contains("decoder-epoch-99-avg-1.onnx"))
        assertFalse(message.contains("系统语音"))
        assertFalse(message.contains("云 ASR"))
    }
}
