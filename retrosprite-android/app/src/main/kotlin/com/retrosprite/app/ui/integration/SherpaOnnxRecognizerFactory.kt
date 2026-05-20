package com.retrosprite.app.ui.integration

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig

internal object SherpaOnnxRecognizerFactory {

    fun create(
        assetManager: AssetManager,
        model: SherpaOnnxAsrModel,
    ): OnlineRecognizer =
        OnlineRecognizer(
            assetManager = assetManager,
            config = createConfig(model),
        )

    fun createConfig(model: SherpaOnnxAsrModel): OnlineRecognizerConfig =
        OnlineRecognizerConfig(
            featConfig = FeatureConfig(
                sampleRate = model.sampleRateHz,
                featureDim = model.featureDim,
            ),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = model.encoderAsset,
                    decoder = model.decoderAsset,
                    joiner = model.joinerAsset,
                ),
                tokens = model.tokensAsset,
                numThreads = model.numThreads,
                provider = "cpu",
                modelType = model.modelType,
            ),
            endpointConfig = EndpointConfig(
                rule1 = EndpointRule(false, 2.4f, 0.0f),
                rule2 = EndpointRule(true, 1.4f, 0.0f),
                rule3 = EndpointRule(false, 0.0f, 20.0f),
            ),
            enableEndpoint = true,
        )
}
