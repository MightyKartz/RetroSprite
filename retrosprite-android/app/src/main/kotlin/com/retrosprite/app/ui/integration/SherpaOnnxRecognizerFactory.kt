package com.retrosprite.app.ui.integration

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineParaformerModelConfig
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
            config = createConfig(model = model),
        )

    fun createConfig(model: SherpaOnnxAsrModel): OnlineRecognizerConfig =
        OnlineRecognizerConfig(
            decodingMethod = "greedy_search",
            maxActivePaths = DEFAULT_MAX_ACTIVE_PATHS,
            hotwordsFile = "",
            hotwordsScore = 0.0f,
            featConfig = FeatureConfig(
                sampleRate = model.sampleRateHz,
                featureDim = model.featureDim,
            ),
            modelConfig = OnlineModelConfig(
                // sherpa's OnlineModelConfig keeps a transducer slot even for
                // Paraformer models. Leave it empty; RetroSprite's product ASR
                // path is Paraformer-only and has no native-hotword runtime.
                transducer = OnlineTransducerModelConfig(),
                paraformer = OnlineParaformerModelConfig(
                    encoder = model.encoderAsset,
                    decoder = model.decoderAsset,
                ),
                tokens = model.tokensAsset,
                numThreads = model.numThreads,
                provider = "cpu",
                modelType = model.modelType,
                modelingUnit = "",
            ),
            endpointConfig = EndpointConfig(
                rule1 = EndpointRule(false, 2.4f, 0.0f),
                rule2 = EndpointRule(true, 2.8f, 0.0f),
                rule3 = EndpointRule(false, 0.0f, 20.0f),
            ),
            enableEndpoint = true,
        )

    private const val DEFAULT_MAX_ACTIVE_PATHS = 4
}
