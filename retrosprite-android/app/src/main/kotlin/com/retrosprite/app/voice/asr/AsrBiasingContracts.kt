package com.retrosprite.app.voice.asr

import java.security.MessageDigest
import java.util.Locale

enum class AsrHotwordSource {
    CanonicalName,
    Alias,
    TemplatePattern,
}

enum class AsrHotwordMode {
    Auto,
    None,
    StreamOne,
    StreamSmall,
    StreamMedium,
    AssetFileSmall,
}

data class AsrHotwordEntry(
    val term: String,
    val score: Float,
    val source: AsrHotwordSource,
)

data class AsrBiasingProfile(
    val gameId: String,
    val packVersion: String,
    val entries: List<AsrHotwordEntry>,
    val enabled: Boolean = true,
) {
    val normalizedEntries: List<AsrHotwordEntry> =
        entries.asSequence()
            .mapNotNull { entry ->
                val term = entry.term.cleanHotwordTerm()
                if (term.length < MIN_HOTWORD_CHARS) null else entry.copy(term = term)
            }
            .filterNot { it.term in STOP_TERMS }
            .groupBy { it.term.lowercase(Locale.ROOT) }
            .map { (_, values) -> values.maxBy { it.score } }
            .sortedWith(compareByDescending<AsrHotwordEntry> { it.score }.thenBy { it.term })
            .take(MAX_HOTWORDS_PER_PROFILE)
            .toList()

    val fingerprint: String =
        "$gameId:$packVersion:${normalizedEntries.joinToString("|") { "${it.term}:${it.score}" }.sha1Short()}"

    companion object {
        const val MAX_HOTWORDS_PER_PROFILE = 160
        const val MIN_HOTWORD_CHARS = 2

        private val STOP_TERMS = setOf(
            "是谁",
            "在哪",
            "在哪里",
            "怎么用",
            "有什么用",
            "怎么办",
            "怎么打",
            "怎么练",
        )
    }
}

data class AsrRecognitionContext(
    val label: String,
    val gameId: String?,
    val spoilerLevel: String,
    val source: String,
    val biasingProfile: AsrBiasingProfile? = null,
    val hotwordMode: AsrHotwordMode = AsrHotwordMode.Auto,
)

data class AsrBiasingResolution(
    val label: String,
    val hotwordMode: AsrHotwordMode,
    val profile: AsrBiasingProfile?,
)

data class AsrLabelOverride(
    val cleanLabel: String,
    val hotwordMode: AsrHotwordMode,
)

object AsrLabelOverrideParser {
    fun parse(label: String): AsrLabelOverride {
        val match = MODE_SUFFIX.find(label)
        val cleanLabel = match
            ?.let { label.removeRange(it.range) }
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: label
        val mode = match
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::modeForToken)
            ?: AsrHotwordMode.Auto
        return AsrLabelOverride(cleanLabel = cleanLabel, hotwordMode = mode)
    }

    private fun modeForToken(token: String): AsrHotwordMode =
        when (token.lowercase(Locale.ROOT)) {
            "none" -> AsrHotwordMode.None
            "stream_one" -> AsrHotwordMode.StreamOne
            "stream_small" -> AsrHotwordMode.StreamSmall
            "stream_medium" -> AsrHotwordMode.StreamMedium
            "asset_file_small" -> AsrHotwordMode.AssetFileSmall
            else -> AsrHotwordMode.Auto
        }

    private val MODE_SUFFIX = Regex("@@asr:([A-Za-z0-9_]+)\\s*$")
}

fun String.cleanHotwordTerm(): String =
    trim()
        .replace(Regex("\\s+"), " ")
        .replace("（", "(")
        .replace("）", ")")
        .trim(' ', '?', '？', '。', '，', ',', '.', ';', '；', ':', '：', '!', '！')

private fun String.sha1Short(): String {
    val bytes = MessageDigest.getInstance("SHA-1").digest(toByteArray(Charsets.UTF_8))
    return bytes.take(8).joinToString("") { "%02x".format(it) }
}
