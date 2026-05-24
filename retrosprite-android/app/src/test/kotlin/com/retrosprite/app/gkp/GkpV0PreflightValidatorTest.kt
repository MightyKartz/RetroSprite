package com.retrosprite.app.gkp

import com.retrosprite.app.data.gkp.GkpPreflightInput
import com.retrosprite.app.data.gkp.GkpPreflightSeverity
import com.retrosprite.app.data.gkp.GkpSignatureStatus
import com.retrosprite.app.data.gkp.GkpV0PreflightValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class GkpV0PreflightValidatorTest {

    private val validator = GkpV0PreflightValidator()

    @Test
    fun `valid golden sun pack passes external preflight`() {
        val report = validator.validate(readPack("golden-sun-gba-zh"))

        assertTrue(report.issues.joinToString("\n") { it.toString() }, report.ok)
        assertEquals("community.golden-sun-gba-zh", report.packId)
        assertEquals("Golden Sun / 黄金太阳", report.gameTitle)
        assertEquals("0.1.1", report.packVersion)
        assertEquals("gkp.v0", report.schemaVersion)
        assertEquals(42, report.knowledgeRows)
        assertEquals(6, report.sourceCount)
        assertEquals(34, report.goldenRows)
        assertEquals("已声明", report.licenseStatus)
        assertEquals(GkpSignatureStatus.Unsigned.id, report.signatureStatus)
        assertTrue(report.contentDigest.orEmpty().matches(Regex("[a-f0-9]{64}")))
        assertTrue(report.issues.any { it.code == "unsigned_pack" })
    }

    @Test
    fun `missing license blocks external preflight`() {
        val input = readPack("golden-sun-gba-zh")
        val report = validator.validate(
            input.copy(
                files = input.files - "sources/licenses.md",
                allPaths = input.allPaths - "sources/licenses.md",
            )
        )

        assertFalse(report.ok)
        assertTrue(report.issues.any { it.code == "missing_license" })
    }

    @Test
    fun `rom and executable-like files are rejected even when undeclared`() {
        val input = readPack("golden-sun-gba-zh")
        val report = validator.validate(
            input.copy(
                allPaths = input.allPaths + setOf("roms/golden_sun.gba", "tools/build.sh"),
            )
        )

        assertFalse(report.ok)
        assertEquals(
            2,
            report.issues.count {
                it.severity == GkpPreflightSeverity.Error && it.code == "blocked_file_type"
            },
        )
    }

    @Test
    fun `unknown source refs fail preflight`() {
        val input = readPack("golden-sun-gba-zh")
        val files = input.files.toMutableMap()
        files["knowledge/items.jsonl"] = files.getValue("knowledge/items.jsonl")
            .replace("gs.official_manual", "missing.source")

        val report = validator.validate(input.copy(files = files))

        assertFalse(report.ok)
        assertTrue(report.issues.any { it.code == "unknown_source_ref" })
    }

    private fun readPack(packName: String): GkpPreflightInput {
        val packDir = moduleRoot()
            .resolve("src/main/assets/gkp/$packName")
            .normalize()
        val files = linkedMapOf<String, String>()
        Files.walk(packDir).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .forEach { path ->
                    val relative = packDir.relativize(path).toString().replace('\\', '/')
                    files[relative] = Files.readAllBytes(path).toString(Charsets.UTF_8)
                }
        }
        return GkpPreflightInput(
            displayName = packName,
            files = files,
            allPaths = files.keys,
        )
    }

    private fun moduleRoot(): Path {
        var current = Paths.get("").toAbsolutePath().normalize()
        repeat(8) {
            if (Files.isDirectory(current.resolve("src/main/assets"))) return current
            if (Files.isDirectory(current.resolve("app/src/main/assets"))) return current.resolve("app")
            current = current.parent ?: current
        }
        error("Could not locate Android app module")
    }
}
