package com.retrosprite.app.ui.integration

import com.retrosprite.app.voice.asr.AsrBiasingProfile
import com.retrosprite.app.voice.asr.AsrHotwordMode

internal data class SherpaAsrNativeHotwordDiagnostics(
    val architecture: String,
    val decodingMethod: String,
    val modelingUnit: String?,
    val nativeHotwordsEnabled: Boolean,
    val reason: String?,
    val hotwordMode: String,
    val hotwordPreview: String?,
) {
    companion object {
        fun from(
            model: SherpaOnnxAsrModel,
            profile: AsrBiasingProfile?,
            hotwordMode: AsrHotwordMode,
            hotwordPlanEnabled: Boolean,
            streamHotwords: String?,
            hotwordsFile: String?,
        ): SherpaAsrNativeHotwordDiagnostics {
            val nativeEnabled = model.supportsHotwords && hotwordPlanEnabled
            return SherpaAsrNativeHotwordDiagnostics(
                architecture = model.architecture.name.lowercase(),
                decodingMethod = if (nativeEnabled) "modified_beam_search" else "greedy_search",
                modelingUnit = "cjkchar".takeIf { nativeEnabled },
                nativeHotwordsEnabled = nativeEnabled,
                reason = reasonFor(
                    model = model,
                    profile = profile,
                    hotwordMode = hotwordMode,
                    hotwordPlanEnabled = hotwordPlanEnabled,
                ),
                hotwordMode = hotwordMode.name,
                hotwordPreview = previewFor(streamHotwords, hotwordsFile),
            )
        }

        private fun reasonFor(
            model: SherpaOnnxAsrModel,
            profile: AsrBiasingProfile?,
            hotwordMode: AsrHotwordMode,
            hotwordPlanEnabled: Boolean,
        ): String? =
            when {
                model.supportsHotwords && hotwordPlanEnabled -> null
                profile == null -> "no_hotword_profile"
                hotwordMode == AsrHotwordMode.None -> "hotword_mode_none"
                !model.supportsHotwords -> "model_architecture_${model.architecture.name.lowercase()}_not_supported"
                else -> "no_cjk_hotwords"
            }

        private fun previewFor(streamHotwords: String?, hotwordsFile: String?): String? =
            streamHotwords
                ?.takeIf { it.isNotBlank() }
                ?.let { it.take(MAX_PREVIEW_CHARS) }
                ?: hotwordsFile?.takeIf { it.isNotBlank() }

        private const val MAX_PREVIEW_CHARS = 120
    }
}
