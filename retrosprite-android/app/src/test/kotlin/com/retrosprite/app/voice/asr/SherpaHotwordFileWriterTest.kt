package com.retrosprite.app.voice.asr

import java.nio.file.Files
import org.junit.Assert.assertEquals
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
                AsrHotwordEntry("Jaha", 3.2f, AsrHotwordSource.CanonicalName),
            ),
        )

        val file = writer.write(profile)
        val lines = file.readLines()

        assertTrue(file.name.startsWith("shining_force_ii_md-"))
        assertEquals(listOf("修伊", "气合之玉"), lines)
        assertEquals("修 伊/气 合 之 玉", writer.streamTextFor(profile))
        assertEquals("修 伊", writer.streamTextFor(profile, AsrHotwordMode.StreamOne))
        assertEquals("修 伊/气 合 之 玉", writer.streamTextFor(profile, AsrHotwordMode.StreamSmall))
    }
}
