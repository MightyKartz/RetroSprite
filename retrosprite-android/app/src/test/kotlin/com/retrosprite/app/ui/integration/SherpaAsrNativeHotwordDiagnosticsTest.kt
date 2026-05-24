package com.retrosprite.app.ui.integration

import com.retrosprite.app.voice.asr.AsrBiasingProfile
import com.retrosprite.app.voice.asr.AsrHotwordEntry
import com.retrosprite.app.voice.asr.AsrHotwordMode
import com.retrosprite.app.voice.asr.AsrHotwordSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaAsrNativeHotwordDiagnosticsTest {

    @Test
    fun `transducer stream hotwords are reported as native decoding enabled`() {
        val diagnostics = SherpaAsrNativeHotwordDiagnostics.from(
            model = SherpaOnnxAsrModel.defaultModel(),
            profile = profile(),
            hotwordMode = AsrHotwordMode.Auto,
            hotwordPlanEnabled = true,
            streamHotwords = "修 伊/气 合 之 玉",
            hotwordsFile = null,
        )

        assertTrue(diagnostics.nativeHotwordsEnabled)
        assertEquals("transducer", diagnostics.architecture)
        assertEquals("modified_beam_search", diagnostics.decodingMethod)
        assertEquals("cjkchar", diagnostics.modelingUnit)
        assertEquals("Auto", diagnostics.hotwordMode)
        assertEquals("修 伊/气 合 之 玉", diagnostics.hotwordPreview)
        assertNull(diagnostics.reason)
    }

    @Test
    fun `paraformer profile reports native hotwords unsupported`() {
        val diagnostics = SherpaAsrNativeHotwordDiagnostics.from(
            model = paraformerModel(),
            profile = profile(),
            hotwordMode = AsrHotwordMode.Auto,
            hotwordPlanEnabled = false,
            streamHotwords = null,
            hotwordsFile = null,
        )

        assertFalse(diagnostics.nativeHotwordsEnabled)
        assertEquals("paraformer", diagnostics.architecture)
        assertEquals("greedy_search", diagnostics.decodingMethod)
        assertNull(diagnostics.modelingUnit)
        assertEquals("model_architecture_paraformer_not_supported", diagnostics.reason)
    }

    @Test
    fun `missing profile reports no hotword profile`() {
        val diagnostics = SherpaAsrNativeHotwordDiagnostics.from(
            model = SherpaOnnxAsrModel.defaultModel(),
            profile = null,
            hotwordMode = AsrHotwordMode.Auto,
            hotwordPlanEnabled = false,
            streamHotwords = null,
            hotwordsFile = null,
        )

        assertFalse(diagnostics.nativeHotwordsEnabled)
        assertEquals("no_hotword_profile", diagnostics.reason)
    }

    private fun profile(): AsrBiasingProfile =
        AsrBiasingProfile(
            gameId = "shining_force_ii_md",
            packVersion = "0.2.5",
            entries = listOf(
                AsrHotwordEntry("修伊", 4.2f, AsrHotwordSource.Alias),
            ),
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
