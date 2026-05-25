package com.retrosprite.app.ui.integration

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.retrosprite.app.data.gkp.ExternalGkpInstaller
import com.retrosprite.app.data.gkp.GkpPackProvenance
import com.retrosprite.app.data.gkp.GkpExternalInstallMode
import com.retrosprite.app.data.gkp.GkpExternalInstallPlan
import com.retrosprite.app.data.gkp.GkpExternalInstallResult
import com.retrosprite.app.data.gkp.GkpSignatureStatus
import com.retrosprite.app.data.gkp.GkpPreflightInput
import com.retrosprite.app.data.gkp.GkpPreflightIssue
import com.retrosprite.app.data.gkp.GkpPreflightReport
import com.retrosprite.app.data.gkp.GkpPreflightSeverity
import com.retrosprite.app.data.gkp.GkpV0PreflightValidator
import com.retrosprite.app.ui.viewmodel.GkpPreflightProvider
import com.retrosprite.app.ui.viewmodel.UiGkpInstallMode
import com.retrosprite.app.ui.viewmodel.UiGkpInstallPhase
import com.retrosprite.app.ui.viewmodel.UiGkpInstallPlan
import com.retrosprite.app.ui.viewmodel.UiGkpInstallStatus
import com.retrosprite.app.ui.viewmodel.UiGkpPreflightIssue
import com.retrosprite.app.ui.viewmodel.UiGkpPreflightResult
import com.retrosprite.app.ui.viewmodel.UiGkpPreflightSeverity
import com.retrosprite.app.ui.viewmodel.UiGkpPreflightState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class RealGkpPreflightProvider(
    private val context: Context,
    private val installer: ExternalGkpInstaller,
    private val validator: GkpV0PreflightValidator = GkpV0PreflightValidator(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) : GkpPreflightProvider {

    private val _state = MutableStateFlow(UiGkpPreflightState())
    override val state: StateFlow<UiGkpPreflightState> = _state.asStateFlow()
    private var lastInstallCandidate: GkpPreflightInput? = null

    override suspend fun preflightTree(uriString: String) {
        _state.value = UiGkpPreflightState(isRunning = true)
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriString)
                val input = readTree(uri)
                val report = validator.validate(input)
                val plan = if (report.ok) installer.createPlan(report).toUi() else null
                PreflightOutcome(
                    state = UiGkpPreflightState(
                        isRunning = false,
                        result = report.toUi(),
                        installPlan = plan,
                    ),
                    candidate = if (report.ok && plan != null) input else null,
                )
            }.getOrElse { throwable ->
                PreflightOutcome(
                    state = UiGkpPreflightState(
                        isRunning = false,
                        result = failedResult(
                            targetName = uriString.substringAfterLast('/').ifBlank { "外部 GKP" },
                            message = throwable.message ?: throwable::class.java.simpleName,
                        ),
                    ),
                    candidate = null,
                )
            }
        }
        lastInstallCandidate = outcome.candidate
        _state.value = outcome.state
    }

    override suspend fun installPreflightedTree() {
        val candidate = lastInstallCandidate
        val current = _state.value
        if (candidate == null || current.result?.ok != true || current.installPlan == null) {
            _state.value = current.copy(
                installStatus = UiGkpInstallStatus(
                    phase = UiGkpInstallPhase.Error,
                    message = "请先选择文件夹并通过预检。",
                )
            )
            return
        }

        _state.value = current.copy(
            installStatus = UiGkpInstallStatus(
                phase = UiGkpInstallPhase.Installing,
                message = "正在写入本地知识库。",
            )
        )
        val status = withContext(Dispatchers.IO) {
            runCatching { installer.install(candidate).toUiStatus() }
                .getOrElse { throwable ->
                    UiGkpInstallStatus(
                        phase = UiGkpInstallPhase.Error,
                        message = throwable.message ?: throwable::class.java.simpleName,
                    )
                }
        }
        _state.value = _state.value.copy(installStatus = status)
    }

    override suspend fun clearPreflight() {
        lastInstallCandidate = null
        _state.value = UiGkpPreflightState()
    }

    private fun readTree(uri: Uri): GkpPreflightInput {
        val root = DocumentFile.fromTreeUri(context, uri)
            ?: error("无法打开所选目录")
        require(root.isDirectory) { "请选择 GKP 文件夹，而不是单个文件。" }

        val files = linkedMapOf<String, String>()
        val allPaths = linkedSetOf<String>()
        collectFiles(root, prefix = "", files = files, allPaths = allPaths)
        return GkpPreflightInput(
            displayName = root.name ?: "外部 GKP",
            files = files,
            allPaths = allPaths,
        )
    }

    private fun collectFiles(
        node: DocumentFile,
        prefix: String,
        files: MutableMap<String, String>,
        allPaths: MutableSet<String>,
    ) {
        if (node.isDirectory) {
            node.listFiles().forEach { child ->
                val name = child.name ?: return@forEach
                val path = if (prefix.isBlank()) name else "$prefix/$name"
                collectFiles(child, path, files, allPaths)
            }
            return
        }

        if (!node.isFile || prefix.isBlank()) return
        val normalizedPath = prefix.replace('\\', '/')
        allPaths += normalizedPath
        if (!normalizedPath.isReadableTextPath()) return

        val length = node.length()
        if (length > MAX_TEXT_FILE_BYTES) {
            return
        }
        val text = context.contentResolver.openInputStream(node.uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: return
        files[normalizedPath] = text
    }

    private fun GkpPreflightReport.toUi(): UiGkpPreflightResult =
        UiGkpPreflightResult(
            targetName = displayName,
            ok = ok,
            packId = packId,
            gameId = gameId,
            gameTitle = gameTitle,
            packVersion = packVersion,
            coverageTierLabel = coverageTier.toCoverageTierLabel(),
            schemaVersion = schemaVersion,
            knowledgeRows = knowledgeRows,
            sourceCount = sourceCount,
            goldenRows = goldenRows,
            licenseStatus = licenseStatus,
            signatureStatus = signatureStatus.toSignatureLabel(signatureKeyId),
            signatureKeyId = signatureKeyId,
            contentDigest = contentDigest,
            errorCount = errorCount,
            warningCount = warningCount,
            checkedAtMillis = nowMillis(),
            issues = issues.map { it.toUi() },
        )

    private fun GkpExternalInstallPlan.toUi(): UiGkpInstallPlan =
        UiGkpInstallPlan(
            mode = when (mode) {
                GkpExternalInstallMode.NewInstall -> UiGkpInstallMode.NewInstall
                GkpExternalInstallMode.ReplaceExisting -> UiGkpInstallMode.ReplaceExisting
            },
            packId = packId,
            gameId = gameId,
            gameTitle = gameTitle,
            currentPackVersion = currentPackVersion,
            newPackVersion = newPackVersion,
            coverageTierLabel = coverageTier.toCoverageTierLabel(),
            currentKnowledgeRows = currentKnowledgeRows,
            newKnowledgeRows = newKnowledgeRows,
            sourceCount = sourceCount,
            goldenRows = goldenRows,
            provenanceLabel = provenance.toProvenanceLabel(),
            signatureLabel = signatureStatus.toSignatureLabel(signatureKeyId),
            contentDigest = contentDigest,
        )

    private fun GkpExternalInstallResult.toUiStatus(): UiGkpInstallStatus =
        UiGkpInstallStatus(
            phase = UiGkpInstallPhase.Installed,
            message = "已安装 ${plan.gameTitle ?: plan.gameId}，写入 $installedKnowledgeRows 条知识。",
            installedAtMillis = installedAtMillis,
        )

    private fun GkpPreflightIssue.toUi(): UiGkpPreflightIssue =
        UiGkpPreflightIssue(
            severity = when (severity) {
                GkpPreflightSeverity.Info -> UiGkpPreflightSeverity.Info
                GkpPreflightSeverity.Warning -> UiGkpPreflightSeverity.Warning
                GkpPreflightSeverity.Error -> UiGkpPreflightSeverity.Error
            },
            code = code,
            path = path,
            message = message,
        )

    private fun failedResult(
        targetName: String,
        message: String,
    ): UiGkpPreflightResult =
        UiGkpPreflightResult(
            targetName = targetName,
            ok = false,
            packId = null,
            gameId = null,
            gameTitle = null,
            packVersion = null,
            coverageTierLabel = "GKP Legacy",
            schemaVersion = null,
            knowledgeRows = 0,
            sourceCount = 0,
            goldenRows = 0,
            licenseStatus = "未检查",
            signatureStatus = "未检查",
            signatureKeyId = null,
            contentDigest = null,
            errorCount = 1,
            warningCount = 0,
            checkedAtMillis = nowMillis(),
            issues = listOf(
                UiGkpPreflightIssue(
                    severity = UiGkpPreflightSeverity.Error,
                    code = "read_failed",
                    path = null,
                    message = message,
                )
            ),
        )

    private fun String.isReadableTextPath(): Boolean {
        if (this == "manifest.json") return true
        val extension = substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return extension in READABLE_TEXT_EXTENSIONS
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

    private fun String?.toCoverageTierLabel(): String = when (this?.lowercase()) {
        "lite" -> "GKP Lite"
        "expanded" -> "GKP Expanded"
        "deep" -> "GKP Deep"
        else -> "GKP Legacy"
    }

    private companion object {
        const val MAX_TEXT_FILE_BYTES = 1_000_000L
        val READABLE_TEXT_EXTENSIONS = setOf("json", "jsonl", "md", "txt")
    }

    private data class PreflightOutcome(
        val state: UiGkpPreflightState,
        val candidate: GkpPreflightInput?,
    )
}
