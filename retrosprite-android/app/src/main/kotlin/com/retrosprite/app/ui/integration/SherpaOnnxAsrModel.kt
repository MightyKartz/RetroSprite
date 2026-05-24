package com.retrosprite.app.ui.integration

data class SherpaOnnxAsrModel(
    val architecture: Architecture,
    val assetDir: String,
    val encoderAsset: String,
    val decoderAsset: String,
    val joinerAsset: String? = null,
    val tokensAsset: String,
    val modelType: String,
    val engineLabel: String,
    val sampleRateHz: Int = 16_000,
    val featureDim: Int = 80,
    val numThreads: Int = 2,
) {
    val requiredAssetPaths: List<String>
        get() = listOfNotNull(encoderAsset, decoderAsset, joinerAsset, tokensAsset)

    val supportsHotwords: Boolean
        get() = architecture == Architecture.Transducer

    fun missingAssetPaths(existingAssetPaths: Set<String>): List<String> =
        requiredAssetPaths.filterNot { it in existingAssetPaths }

    fun missingAssetsMessage(missing: List<String>): String {
        val detail = missing
            .take(3)
            .joinToString(separator = "、") { it.substringAfterLast('/') }
        val suffix = if (missing.size > 3) " 等 ${missing.size} 个文件" else ""
        return "sherpa-onnx 本地 ASR 模型未安装：缺少 $detail$suffix。请先安装本地模型资源，或暂时使用文字输入。"
    }

    enum class Architecture {
        Transducer,
        Paraformer,
    }

    companion object {
        fun defaultModel(): SherpaOnnxAsrModel {
            val dir = "sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"
            return SherpaOnnxAsrModel(
                architecture = Architecture.Transducer,
                assetDir = dir,
                encoderAsset = "$dir/encoder-epoch-99-avg-1.int8.onnx",
                decoderAsset = "$dir/decoder-epoch-99-avg-1.onnx",
                joinerAsset = "$dir/joiner-epoch-99-avg-1.int8.onnx",
                tokensAsset = "$dir/tokens.txt",
                modelType = "zipformer",
                engineLabel = "sherpa-onnx Transducer 本地 ASR",
            )
        }
    }
}
