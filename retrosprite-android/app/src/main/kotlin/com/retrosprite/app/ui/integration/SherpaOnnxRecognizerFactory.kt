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
        hotwordsFile: String? = null,
        hotwordsScore: Float = DEFAULT_HOTWORDS_SCORE,
        enableHotwords: Boolean = !hotwordsFile.isNullOrBlank(),
    ): OnlineRecognizer =
        OnlineRecognizer(
            assetManager = assetManager,
            config = createConfig(
                model = model,
                hotwordsFile = hotwordsFile,
                hotwordsScore = hotwordsScore,
                enableHotwords = enableHotwords,
            ),
        )

    fun createConfig(
        model: SherpaOnnxAsrModel,
        hotwordsFile: String? = null,
        hotwordsScore: Float = DEFAULT_HOTWORDS_SCORE,
        enableHotwords: Boolean = !hotwordsFile.isNullOrBlank(),
    ): OnlineRecognizerConfig {
        return OnlineRecognizerConfig(
            decodingMethod = if (enableHotwords) "modified_beam_search" else "greedy_search",
            maxActivePaths = if (enableHotwords) HOTWORD_MAX_ACTIVE_PATHS else DEFAULT_MAX_ACTIVE_PATHS,
            hotwordsFile = hotwordsFile.orEmpty(),
            hotwordsScore = hotwordsScore,
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
                modelingUnit = if (enableHotwords) "cjkchar" else "",
            ),
            endpointConfig = EndpointConfig(
                rule1 = EndpointRule(false, 2.4f, 0.0f),
                rule2 = EndpointRule(true, 1.4f, 0.0f),
                rule3 = EndpointRule(false, 0.0f, 20.0f),
            ),
            enableEndpoint = true,
        )
    }

    private const val DEFAULT_MAX_ACTIVE_PATHS = 4
    private const val HOTWORD_MAX_ACTIVE_PATHS = 8
    private const val DEFAULT_HOTWORDS_SCORE = 2.5f
}
