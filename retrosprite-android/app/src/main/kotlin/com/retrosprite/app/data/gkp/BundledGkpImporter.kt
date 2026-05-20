package com.retrosprite.app.data.gkp

import android.content.Context
import com.retrosprite.app.data.repository.GameRepository
import com.retrosprite.app.data.repository.KnowledgeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class BundledGkpImportPhase { Idle, Importing, Ready, Error }

data class BundledGkpImportStatus(
    val phase: BundledGkpImportPhase = BundledGkpImportPhase.Idle,
    val totalPacks: Int = 0,
    val importedPacks: Int = 0,
    val failedPacks: Int = 0,
    val message: String = "waiting",
    val updatedAtMillis: Long? = null,
)

class BundledGkpImporter(
    private val context: Context,
    private val gameRepository: GameRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val parser: GkpV0Parser = GkpV0Parser(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {

    suspend fun importBundledPacks(
        onStatus: (BundledGkpImportStatus) -> Unit = {},
    ): BundledGkpImportStatus = withContext(Dispatchers.IO) {
        val total = BUNDLED_PACK_PATHS.size
        var imported = 0
        var failed = 0
        val failures = mutableListOf<String>()

        fun status(
            phase: BundledGkpImportPhase,
            message: String,
        ): BundledGkpImportStatus = BundledGkpImportStatus(
            phase = phase,
            totalPacks = total,
            importedPacks = imported,
            failedPacks = failed,
            message = message,
            updatedAtMillis = nowMillis(),
        )

        onStatus(status(BundledGkpImportPhase.Importing, "importing bundled GKP packs"))

        BUNDLED_PACK_PATHS.forEach { path ->
            runCatching {
                importPack(path)
                imported += 1
            }.onFailure { throwable ->
                failed += 1
                failures += "$path: ${throwable.message ?: throwable::class.java.simpleName}"
            }
            onStatus(status(BundledGkpImportPhase.Importing, "imported $imported of $total bundled GKP packs"))
        }

        val finalStatus = if (failed == 0) {
            status(BundledGkpImportPhase.Ready, "imported $imported bundled GKP packs")
        } else {
            status(
                BundledGkpImportPhase.Error,
                "imported $imported bundled GKP packs; failed $failed: ${failures.joinToString("; ")}",
            )
        }
        onStatus(finalStatus)
        finalStatus
    }

    private suspend fun importPack(assetPackPath: String) {
        val manifestText = readAsset("$assetPackPath/manifest.json")
        val knowledgeFiles = parser.knowledgePaths(manifestText)
            .associateWith { relativePath ->
                readAsset("$assetPackPath/$relativePath")
            }
        val digestFiles = mapOf("manifest.json" to manifestText) + knowledgeFiles
        val parsed = parser.parse(
            manifestText = manifestText,
            knowledgeFiles = knowledgeFiles,
            provenance = GkpPackProvenance.Bundled,
            signature = parser.signatureMetadata(
                manifestText = manifestText,
                contentDigest = GkpContentDigests.sha256(digestFiles),
            ),
        )

        val existing = gameRepository.getById(parsed.game.gameId)
        if (existing != null && existing.provenance != GkpPackProvenance.Bundled.id) {
            return
        }

        val game = if (existing == null) {
            parsed.game
        } else {
            parsed.game.copy(
                isEnabled = existing.isEnabled,
                disabledAt = existing.disabledAt,
            )
        }

        gameRepository.upsert(game)
        knowledgeRepository.clearForGame(parsed.game.gameId)
        knowledgeRepository.upsertAll(parsed.knowledge)
    }

    private fun readAsset(path: String): String =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private companion object {
        val BUNDLED_PACK_PATHS = listOf(
            "gkp/sample-2048",
            "gkp/sample-relay-station",
            "gkp/shining-force-ii-md",
        )
    }
}
