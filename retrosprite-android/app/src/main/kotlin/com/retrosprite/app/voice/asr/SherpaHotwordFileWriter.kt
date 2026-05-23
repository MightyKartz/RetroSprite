package com.retrosprite.app.voice.asr

import java.io.File

class SherpaHotwordFileWriter(
    private val rootDir: File,
) {

    fun write(profile: AsrBiasingProfile): File {
        rootDir.mkdirs()
        val safeName = profile.fingerprint
            .replace(Regex("[^A-Za-z0-9_.:-]"), "_")
            .replace(":", "-")
        val file = File(rootDir, "$safeName.hotwords.txt")
        file.writeText(textFor(profile), Charsets.UTF_8)
        return file
    }

    fun textFor(profile: AsrBiasingProfile): String =
        cjkTermsFor(profile).joinToString(separator = "\n")

    fun streamTextFor(profile: AsrBiasingProfile): String =
        streamTextFor(profile, AsrHotwordMode.Auto)

    fun streamTextFor(profile: AsrBiasingProfile, mode: AsrHotwordMode): String =
        streamTermsFor(profile, mode)
            .map { term -> term.toList().joinToString(separator = " ") }
            .joinToString(separator = "/")

    private fun cjkTermsFor(profile: AsrBiasingProfile): List<String> =
        profile.normalizedEntries
            .asSequence()
            .map { it.term }
            .filter { it.isCjkHotword() }
            .sorted()
            .toList()

    private fun streamTermsFor(profile: AsrBiasingProfile, mode: AsrHotwordMode): List<String> {
        val maxTerms = when (mode) {
            AsrHotwordMode.StreamOne -> 1
            AsrHotwordMode.StreamSmall -> 3
            AsrHotwordMode.StreamMedium -> 8
            else -> MAX_STREAM_HOTWORDS
        }
        val terms = profile.normalizedEntries
            .asSequence()
            .map { it.term }
            .filter { it.isCjkHotword() }
            .filter { it.length <= MAX_STREAM_HOTWORD_CHARS }
            .toList()

        val preferred = PREFERRED_DIAGNOSTIC_TERMS.filter { it in terms }
        return (preferred + terms.filterNot { it in preferred })
            .distinct()
            .take(maxTerms)
    }

    private fun String.isCjkHotword(): Boolean =
        isNotBlank() && all { it.code in CJK_UNIFIED_IDEOGRAPHS }

    private companion object {
        val CJK_UNIFIED_IDEOGRAPHS = 0x4E00..0x9FFF
        val PREFERRED_DIAGNOSTIC_TERMS = listOf("修伊", "吉布", "皮特", "气合之玉", "精灵森林", "米斯里鲁银")
        const val MAX_STREAM_HOTWORDS = 32
        const val MAX_STREAM_HOTWORD_CHARS = 8
    }
}
