package com.retrosprite.app.voice.asr

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaHotwordFileWriterTest {

    @Test
    fun `writes deterministic cjk hotwords file without score suffixes`() {
        val dir = Files.createTempDirectory("retrosprite-hotwords").toFile()
        val writer = SherpaHotwordFileWriter(rootDir = dir)
        val profile = AsrBiasingProfile(
            gameId = "shining_force_ii_md",
            packVersion = "0.2.5",
            entries = listOf(
                AsrHotwordEntry("修伊", 4.2f, AsrHotwordSource.Alias),
                AsrHotwordEntry("气合之玉", 4.2f, AsrHotwordSource.Alias),
                AsrHotwordEntry("米斯里鲁银", 4.2f, AsrHotwordSource.Alias),
                AsrHotwordEntry("Jaha", 3.2f, AsrHotwordSource.CanonicalName),
            ),
        )

        val file = writer.write(profile)
        val lines = file.readLines()

        assertTrue(file.name.startsWith("shining_force_ii_md-"))
        assertEquals(listOf("修伊", "气合之玉", "米斯里鲁银"), lines)
        assertEquals("修 伊/气 合 之 玉/米 斯 里 鲁 银", writer.streamTextFor(profile))
        assertEquals("修 伊", writer.streamTextFor(profile, AsrHotwordMode.StreamOne))
        assertEquals(
            "修 伊/气 合 之 玉/米 斯 里 鲁 银",
            writer.streamTextFor(profile, AsrHotwordMode.StreamSmall),
        )
    }

    @Test
    fun `auto stream hotwords stay focused on critical spoken terms`() {
        val dir = Files.createTempDirectory("retrosprite-hotwords").toFile()
        val writer = SherpaHotwordFileWriter(rootDir = dir)
        val suffixes = "甲乙丙丁戊己庚辛壬癸子丑寅卯辰巳午未申酉"
        val filler = suffixes.map { suffix ->
            AsrHotwordEntry("测试热词$suffix", 3.0f, AsrHotwordSource.Alias)
        }
        val profile = AsrBiasingProfile(
            gameId = "shining_force_ii_md",
            packVersion = "0.3.0",
            entries = filler + listOf(
                AsrHotwordEntry("米斯里鲁银", 4.8f, AsrHotwordSource.TemplatePattern),
                AsrHotwordEntry("气合之玉", 4.8f, AsrHotwordSource.TemplatePattern),
                AsrHotwordEntry("修伊", 4.8f, AsrHotwordSource.TemplatePattern),
                AsrHotwordEntry("什么时候发售", 4.8f, AsrHotwordSource.TemplatePattern),
                AsrHotwordEntry("买什么武器", 4.8f, AsrHotwordSource.TemplatePattern),
                AsrHotwordEntry("下一步去哪", 4.8f, AsrHotwordSource.TemplatePattern),
            ),
        )

        val streamTerms = writer.streamTextFor(profile).split("/")

        assertTrue(streamTerms.size <= 12)
        assertEquals("修 伊", streamTerms[0])
        assertEquals("气 合 之 玉", streamTerms[1])
        assertEquals("米 斯 里 鲁 银", streamTerms[2])
        assertFalse(streamTerms.contains("什 么 时 候 发 售"))
        assertFalse(streamTerms.contains("买 什 么 武 器"))
        assertFalse(streamTerms.contains("下 一 步 去 哪"))
    }
}
