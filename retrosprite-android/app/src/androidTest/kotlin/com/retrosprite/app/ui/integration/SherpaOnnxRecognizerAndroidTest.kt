package com.retrosprite.app.ui.integration

import androidx.test.platform.app.InstrumentationRegistry
import kotlin.system.measureTimeMillis
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaOnnxRecognizerAndroidTest {

    @Test
    fun defaultModelInitializesFromBundledAssets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = SherpaOnnxAsrModel.defaultModel()

        val elapsedMillis = measureTimeMillis {
            val recognizer = SherpaOnnxRecognizerFactory.create(
                assetManager = context.assets,
                model = model,
            )
            recognizer.release()
        }

        assertTrue("sherpa-onnx model init took ${elapsedMillis}ms", elapsedMillis < 30_000)
    }
}
