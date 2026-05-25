package com.retrosprite.app.data.gkp

import com.retrosprite.app.data.repository.GameRepository
import com.retrosprite.app.data.repository.KnowledgeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class GkpExternalInstallMode { NewInstall, ReplaceExisting }

data class GkpExternalInstallPlan(
    val mode: GkpExternalInstallMode,
    val packId: String?,
    val gameId: String,
    val gameTitle: String?,
    val currentPackVersion: String?,
    val newPackVersion: String?,
    val coverageTier: String?,
    val currentKnowledgeRows: Int,
    val newKnowledgeRows: Int,
    val sourceCount: Int,
    val goldenRows: Int,
    val provenance: String,
    val signatureStatus: String,
    val signatureKeyId: String?,
    val contentDigest: String?,
) {
    val knowledgeDelta: Int get() = newKnowledgeRows - currentKnowledgeRows
}

data class GkpExternalInstallResult(
    val plan: GkpExternalInstallPlan,
    val installedKnowledgeRows: Int,
    val installedAtMillis: Long,
)

class ExternalGkpInstaller(
    private val gameRepository: GameRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val validator: GkpV0PreflightValidator = GkpV0PreflightValidator(),
    private val parser: GkpV0Parser = GkpV0Parser(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val runInTransaction: suspend (suspend () -> Unit) -> Unit = { block -> block() },
) {

    suspend fun createPlan(report: GkpPreflightReport): GkpExternalInstallPlan =
        withContext(Dispatchers.IO) {
            require(report.ok) { "预检未通过，不能安装。" }
            val gameId = requireNotNull(report.gameId) { "预检缺少 game_id，不能安装。" }
            val existing = gameRepository.getById(gameId)
            val currentRows = existing?.let { knowledgeRepository.listByGame(gameId).size } ?: 0
            GkpExternalInstallPlan(
                mode = if (existing == null) {
                    GkpExternalInstallMode.NewInstall
                } else {
                    GkpExternalInstallMode.ReplaceExisting
                },
                packId = report.packId,
                gameId = gameId,
                gameTitle = report.gameTitle,
                currentPackVersion = existing?.packVersion,
                newPackVersion = report.packVersion,
                coverageTier = report.coverageTier,
                currentKnowledgeRows = currentRows,
                newKnowledgeRows = report.knowledgeRows,
                sourceCount = report.sourceCount,
                goldenRows = report.goldenRows,
                provenance = GkpPackProvenance.External.id,
                signatureStatus = report.signatureStatus,
                signatureKeyId = report.signatureKeyId,
                contentDigest = report.contentDigest,
            )
        }

    suspend fun install(input: GkpPreflightInput): GkpExternalInstallResult =
        withContext(Dispatchers.IO) {
            val normalizedInput = input.withNormalizedPaths()
            val report = validator.validate(normalizedInput)
            require(report.ok) {
                report.issues
                    .filter { it.severity == GkpPreflightSeverity.Error }
                    .joinToString(separator = "; ") { "${it.code}: ${it.message}" }
                    .ifBlank { "预检未通过，不能安装。" }
            }
            val plan = createPlan(report)
            val parsed = parseValidated(normalizedInput)
            require(parsed.game.gameId == plan.gameId) {
                "解析结果 game_id 与预检结果不一致。"
            }

            runInTransaction {
                gameRepository.upsert(parsed.game)
                knowledgeRepository.clearForGame(parsed.game.gameId)
                knowledgeRepository.upsertAll(parsed.knowledge)
            }

            GkpExternalInstallResult(
                plan = plan,
                installedKnowledgeRows = parsed.knowledge.size,
                installedAtMillis = nowMillis(),
            )
        }

    private fun parseValidated(input: GkpPreflightInput): ParsedGkpPack {
        val manifestText = input.files[MANIFEST_PATH] ?: error("缺少 manifest.json。")
        val knowledgeFiles = parser.knowledgePaths(manifestText).associateWith { path ->
            input.files[path] ?: error("缺少知识文件：$path")
        }
        val aliasFiles = parser.aliasPath(manifestText)
            ?.let { path -> mapOf(path to (input.files[path] ?: error("缺少别名文件：$path"))) }
            .orEmpty()
        return parser.parse(
            manifestText = manifestText,
            knowledgeFiles = knowledgeFiles,
            aliasFiles = aliasFiles,
            provenance = GkpPackProvenance.External,
            signature = parser.signatureMetadata(
                manifestText = manifestText,
                contentDigest = GkpContentDigests.sha256(input.files),
            ),
        )
    }

    private fun GkpPreflightInput.withNormalizedPaths(): GkpPreflightInput =
        copy(
            files = files.mapKeys { (path, _) -> path.normalizePackPath() },
            allPaths = allPaths.map { it.normalizePackPath() }.toSet(),
        )

    private fun String.normalizePackPath(): String =
        replace('\\', '/').trim('/')

    private companion object {
        const val MANIFEST_PATH = "manifest.json"
    }
}
