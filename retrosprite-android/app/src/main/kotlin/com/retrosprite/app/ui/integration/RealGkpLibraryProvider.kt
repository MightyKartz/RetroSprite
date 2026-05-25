package com.retrosprite.app.ui.integration

import com.retrosprite.app.data.gkp.BundledGkpImportPhase
import com.retrosprite.app.data.gkp.BundledGkpImportStatus
import com.retrosprite.app.data.gkp.GkpPackProvenance
import com.retrosprite.app.data.gkp.GkpSignatureStatus
import com.retrosprite.app.data.models.GameDomain
import com.retrosprite.app.data.models.KnowledgeChunkDomain
import com.retrosprite.app.data.repository.GameRepository
import com.retrosprite.app.data.repository.KnowledgeRepository
import com.retrosprite.app.ui.viewmodel.GkpLibraryProvider
import com.retrosprite.app.ui.viewmodel.UiGkpDeletePhase
import com.retrosprite.app.ui.viewmodel.UiGkpDeletePlan
import com.retrosprite.app.ui.viewmodel.UiGkpDeleteState
import com.retrosprite.app.ui.viewmodel.UiGkpImportPhase
import com.retrosprite.app.ui.viewmodel.UiGkpImportStatus
import com.retrosprite.app.ui.viewmodel.UiGkpLibraryState
import com.retrosprite.app.ui.viewmodel.UiGkpPackItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

class RealGkpLibraryProvider(
    private val gameRepository: GameRepository,
    private val knowledgeRepository: KnowledgeRepository,
    importStatus: StateFlow<BundledGkpImportStatus>,
    scope: CoroutineScope,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val runInTransaction: suspend (suspend () -> Unit) -> Unit = { block -> block() },
) : GkpLibraryProvider {

    private val deleteState = MutableStateFlow(UiGkpDeleteState())

    override val state: StateFlow<UiGkpLibraryState> =
        combine(gameRepository.observeAll(), importStatus, deleteState) { games, status, delete ->
            UiGkpLibraryState(
                importStatus = status.toUi(),
                packs = games.map { game -> game.toUiPackItem() },
                deleteState = delete,
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = UiGkpLibraryState(),
        )

    override suspend fun disablePack(gameId: String) {
        withContext(Dispatchers.IO) {
            gameRepository.setEnabled(gameId, enabled = false, disabledAt = nowMillis())
        }
    }

    override suspend fun enablePack(gameId: String) {
        withContext(Dispatchers.IO) {
            gameRepository.setEnabled(gameId, enabled = true, disabledAt = null)
        }
    }

    override suspend fun requestDelete(gameId: String) {
        deleteState.value = withContext(Dispatchers.IO) {
            val game = gameRepository.getById(gameId)
                ?: return@withContext UiGkpDeleteState(
                    phase = UiGkpDeletePhase.Error,
                    message = "未找到要删除的知识包：$gameId",
                    updatedAtMillis = nowMillis(),
                )
            val knowledgeRows = knowledgeRepository.listByGame(gameId)
            UiGkpDeleteState(
                phase = UiGkpDeletePhase.AwaitingConfirmation,
                plan = UiGkpDeletePlan(
                    packId = game.packId.ifBlank { game.gameId },
                    gameId = game.gameId,
                    title = game.title,
                    packVersion = game.packVersion,
                    knowledgeCount = knowledgeRows.size,
                    sourceCount = knowledgeRows.distinctSourceCount(),
                    warning = deletionWarning(game),
                ),
                message = "请确认删除 ${game.title}。",
                updatedAtMillis = nowMillis(),
            )
        }
    }

    override suspend fun confirmDelete() {
        val plan = deleteState.value.plan
        if (plan == null) {
            deleteState.value = UiGkpDeleteState(
                phase = UiGkpDeletePhase.Error,
                message = "请先选择要删除的知识包。",
                updatedAtMillis = nowMillis(),
            )
            return
        }

        deleteState.value = deleteState.value.copy(
            phase = UiGkpDeletePhase.Deleting,
            message = "正在删除 ${plan.title}。",
            updatedAtMillis = nowMillis(),
        )
        deleteState.value = withContext(Dispatchers.IO) {
            runCatching {
                runInTransaction {
                    knowledgeRepository.clearForGame(plan.gameId)
                    gameRepository.delete(plan.gameId)
                }
                UiGkpDeleteState(
                    phase = UiGkpDeletePhase.Deleted,
                    plan = plan,
                    message = "已删除 ${plan.title}，移除 ${plan.knowledgeCount} 条知识。",
                    updatedAtMillis = nowMillis(),
                )
            }.getOrElse { throwable ->
                UiGkpDeleteState(
                    phase = UiGkpDeletePhase.Error,
                    plan = plan,
                    message = throwable.message ?: throwable::class.java.simpleName,
                    updatedAtMillis = nowMillis(),
                )
            }
        }
    }

    override suspend fun cancelDelete() {
        deleteState.value = UiGkpDeleteState()
    }

    private suspend fun GameDomain.toUiPackItem(): UiGkpPackItem {
        val knowledgeRows = knowledgeRepository.listByGame(gameId)
        return UiGkpPackItem(
            packId = packId.ifBlank { gameId },
            gameId = gameId,
            title = title,
            platform = platform,
            region = region,
            languages = languages,
            packVersion = packVersion,
            coverageTierLabel = coverageTier.toCoverageTierLabel(),
            schemaVersion = schemaVersion,
            trustLabel = trustLevel.toTrustLabel(),
            provenanceLabel = provenance.toProvenanceLabel(),
            signatureLabel = signatureStatus.toSignatureLabel(signatureKeyId),
            contentDigest = contentDigest,
            isEnabled = isEnabled,
            availabilityLabel = if (isEnabled) "启用" else "已禁用",
            disabledAtMillis = disabledAt,
            knowledgeCount = knowledgeRows.size,
            sourceCount = knowledgeRows.distinctSourceCount(),
            licenseSummary = trustLevel.toLicenseSummary(),
            installedAtMillis = installedAt,
        )
    }

    private fun BundledGkpImportStatus.toUi(): UiGkpImportStatus =
        UiGkpImportStatus(
            phase = phase.toUi(),
            totalPacks = totalPacks,
            importedPacks = importedPacks,
            failedPacks = failedPacks,
            message = message.toUiImportMessage(),
            updatedAtMillis = updatedAtMillis,
        )

    private fun BundledGkpImportPhase.toUi(): UiGkpImportPhase = when (this) {
        BundledGkpImportPhase.Idle -> UiGkpImportPhase.Idle
        BundledGkpImportPhase.Importing -> UiGkpImportPhase.Importing
        BundledGkpImportPhase.Ready -> UiGkpImportPhase.Ready
        BundledGkpImportPhase.Error -> UiGkpImportPhase.Error
    }

    private fun String.toUiImportMessage(): String = when {
        startsWith("imported") && contains("failed") -> replace("imported", "已导入")
            .replace("bundled GKP packs", "个内置知识包")
            .replace("failed", "失败")
        startsWith("imported") -> replace("imported", "已导入")
            .replace("of", "/")
            .replace("bundled GKP packs", "个内置知识包")
        startsWith("importing") -> "正在导入内置知识包"
        else -> this
    }

    private fun List<KnowledgeChunkDomain>.distinctSourceCount(): Int =
        flatMap { it.sourceRefs }.distinct().size

    private fun deletionWarning(game: GameDomain): String? =
        if (game.provenance == GkpPackProvenance.Bundled.id) {
            "这是内置样例包，删除后下次启动可能会由 bundled importer 自动恢复。"
        } else {
            null
        }

    private fun String.toTrustLabel(): String = when (lowercase()) {
        "sample" -> "自写样例"
        "official" -> "官方"
        "community" -> "社区"
        "personal" -> "个人"
        else -> this
    }

    private fun String.toProvenanceLabel(): String = when (GkpPackProvenance.fromId(this)) {
        GkpPackProvenance.Bundled -> "内置"
        GkpPackProvenance.External -> "外部"
        GkpPackProvenance.Registry -> "Registry"
        GkpPackProvenance.Unknown -> "未知来源"
    }

    private fun String.toSignatureLabel(keyId: String?): String = when (GkpSignatureStatus.fromId(this)) {
        GkpSignatureStatus.Unsigned -> "未签名"
        GkpSignatureStatus.Declared -> keyId?.let { "声明签名 $it" } ?: "声明签名"
        GkpSignatureStatus.Verified -> keyId?.let { "已验证 $it" } ?: "已验证"
        GkpSignatureStatus.Failed -> "签名失败"
        GkpSignatureStatus.Unknown -> "签名未知"
    }

    private fun String.toLicenseSummary(): String = when (lowercase()) {
        "sample" -> "自写 / 本地夹具"
        "official" -> "官方来源"
        "community" -> "社区来源"
        "personal" -> "个人本地包"
        else -> "本地知识包"
    }

    private fun String?.toCoverageTierLabel(): String = when (this?.lowercase()) {
        "lite" -> "GKP Lite"
        "expanded" -> "GKP Expanded"
        "deep" -> "GKP Deep"
        else -> "GKP Legacy"
    }
}
