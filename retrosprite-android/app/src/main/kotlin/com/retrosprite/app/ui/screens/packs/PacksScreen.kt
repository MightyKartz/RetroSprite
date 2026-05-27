package com.retrosprite.app.ui.screens.packs

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retrosprite.app.ui.components.SectionCard
import com.retrosprite.app.ui.theme.RetroSpriteTheme
import com.retrosprite.app.ui.theme.StatusError
import com.retrosprite.app.ui.theme.StatusRunning
import com.retrosprite.app.ui.theme.StatusStarting
import com.retrosprite.app.ui.theme.StatusStopped
import com.retrosprite.app.ui.viewmodel.PreviewStub
import com.retrosprite.app.ui.viewmodel.UiGkpDeletePhase
import com.retrosprite.app.ui.viewmodel.UiGkpDeletePlan
import com.retrosprite.app.ui.viewmodel.UiGkpDeleteState
import com.retrosprite.app.ui.viewmodel.UiGkpInstallMode
import com.retrosprite.app.ui.viewmodel.UiGkpInstallPhase
import com.retrosprite.app.ui.viewmodel.UiGkpInstallPlan
import com.retrosprite.app.ui.viewmodel.UiGkpInstallStatus
import com.retrosprite.app.ui.viewmodel.UiGkpImportPhase
import com.retrosprite.app.ui.viewmodel.UiGkpImportStatus
import com.retrosprite.app.ui.viewmodel.UiGkpLibraryState
import com.retrosprite.app.ui.viewmodel.UiGkpPackItem
import com.retrosprite.app.ui.viewmodel.UiGkpPreflightIssue
import com.retrosprite.app.ui.viewmodel.UiGkpPreflightResult
import com.retrosprite.app.ui.viewmodel.UiGkpPreflightSeverity
import com.retrosprite.app.ui.viewmodel.UiGkpPreflightState
import com.retrosprite.app.ui.viewmodel.rememberUiDependencies

@Composable
fun PacksScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val deps = rememberUiDependencies()
    val viewModel: PacksViewModel = viewModel(
        factory = PacksViewModel.factory(deps.gkpLibrary, deps.gkpPreflight)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val preflight by viewModel.preflight.collectAsStateWithLifecycle()
    val treePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let { viewModel.preflightExternalTree(it.toString()) }
    }

    PacksContent(
        contentPadding = contentPadding,
        state = state,
        preflight = preflight,
        onPickExternalPack = { treePicker.launch(null) },
        onInstallExternalPack = viewModel::installPreflightedTree,
        onClearPreflight = viewModel::clearPreflight,
        onDisablePack = viewModel::disablePack,
        onEnablePack = viewModel::enablePack,
        onRequestDeletePack = viewModel::requestDeletePack,
        onConfirmDeletePack = viewModel::confirmDeletePack,
        onCancelDeletePack = viewModel::cancelDeletePack,
        modifier = modifier,
    )
}

@Composable
private fun PacksContent(
    contentPadding: PaddingValues,
    state: UiGkpLibraryState,
    preflight: UiGkpPreflightState,
    onPickExternalPack: () -> Unit,
    onInstallExternalPack: () -> Unit,
    onClearPreflight: () -> Unit,
    onDisablePack: (String) -> Unit,
    onEnablePack: (String) -> Unit,
    onRequestDeletePack: (String) -> Unit,
    onConfirmDeletePack: () -> Unit,
    onCancelDeletePack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilterName by rememberSaveable { mutableStateOf(PackFilter.All.name) }
    val selectedFilter = PackFilter.fromName(selectedFilterName)
    val visiblePacks = state.packs
        .asSequence()
        .filter { selectedFilter.matches(it) }
        .filter { it.matchesSearch(searchQuery) }
        .sortedWith(
            compareByDescending<UiGkpPackItem> { it.isEnabled }
                .thenBy { it.title.lowercase() }
                .thenBy { it.platform.lowercase() }
        )
        .toList()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .testTag("packs_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            HeaderBlock(state)
        }
        item {
            ImportStatusCard(status = state.importStatus)
        }
        if (state.deleteState.phase != UiGkpDeletePhase.Idle) {
            item {
                DeletePlanCard(
                    state = state.deleteState,
                    onConfirmDeletePack = onConfirmDeletePack,
                    onCancelDeletePack = onCancelDeletePack,
                )
            }
        }
        item {
            PreflightCard(
                state = preflight,
                onPickExternalPack = onPickExternalPack,
                onClearPreflight = onClearPreflight,
            )
        }
        preflight.installPlan?.let { plan ->
            item {
                InstallPlanCard(
                    plan = plan,
                    status = preflight.installStatus,
                    onInstallExternalPack = onInstallExternalPack,
                )
            }
        }
        item {
            PackLibraryControls(
                state = state,
                visibleCount = visiblePacks.size,
                searchQuery = searchQuery,
                selectedFilter = selectedFilter,
                onSearchQueryChange = { searchQuery = it },
                onFilterChange = { selectedFilterName = it.name },
            )
        }
        if (state.packs.isEmpty() || visiblePacks.isEmpty()) {
            item {
                EmptyPackList(
                    status = state.importStatus,
                    hasInstalledPacks = state.packs.isNotEmpty(),
                    hasActiveSearch = searchQuery.isNotBlank() || selectedFilter != PackFilter.All,
                )
            }
        } else {
            items(
                items = visiblePacks,
                key = { it.gameId },
            ) { pack ->
                PackRow(
                    pack = pack,
                    onDisablePack = onDisablePack,
                    onEnablePack = onEnablePack,
                    onRequestDeletePack = onRequestDeletePack,
                )
            }
        }
    }
}

@Composable
private fun PackLibraryControls(
    state: UiGkpLibraryState,
    visibleCount: Int,
    searchQuery: String,
    selectedFilter: PackFilter,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (PackFilter) -> Unit,
) {
    SectionCard(
        title = "知识包库",
        accent = state.enabledPackCount > 0,
        trailing = {
            InfoChip(text = if (state.packs.isEmpty()) "空" else "显示 $visibleCount/${state.packs.size}")
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "按游戏名、平台、版本或语言查找。已启用的知识包会参与游戏识别和本地问答。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("packs_search_field"),
                singleLine = true,
                label = { Text("搜索知识包") },
                placeholder = { Text("例如 Final Fantasy / ps1 / zh") },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PackFilter.values().forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { onFilterChange(filter) },
                        label = { Text("${filter.label} ${filter.count(state)}") },
                    )
                }
            }
        }
    }
}

@Composable
private fun PreflightCard(
    state: UiGkpPreflightState,
    onPickExternalPack: () -> Unit,
    onClearPreflight: () -> Unit,
) {
    SectionCard(
        title = "导入外部知识包",
        trailing = {
            PreflightStatusChip(state)
        },
    ) {
        Column(
            modifier = Modifier.testTag("packs_preflight"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPickExternalPack,
                    enabled = !state.isRunning,
                    modifier = Modifier.testTag("packs_preflight_pick_button"),
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("选择文件夹")
                }
                if (state.result != null) {
                    TextButton(
                        onClick = onClearPreflight,
                        enabled = !state.isRunning,
                        modifier = Modifier.testTag("packs_preflight_clear_button"),
                    ) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = "清除预检结果",
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("清除")
                    }
                }
            }

            when {
                state.isRunning -> Text(
                    text = "正在检查文件夹。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.result == null -> Text(
                    text = "选择本机文件夹后，RetroSprite 会先检查格式和来源；确认后才会安装。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> PreflightResultBlock(state.result)
            }
        }
    }
}

@Composable
private fun PreflightResultBlock(result: UiGkpPreflightResult) {
    Column(
        modifier = Modifier.testTag("packs_preflight_result"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.gameTitle ?: result.targetName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = result.packId ?: "pack_id 未识别",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (result.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            InfoChip(text = if (result.ok) "可预检通过" else "需修复")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile(label = "知识", value = result.knowledgeRows.toString(), modifier = Modifier.weight(1f))
            MetricTile(label = "来源", value = result.sourceCount.toString(), modifier = Modifier.weight(1f))
            MetricTile(label = "测试", value = result.goldenRows.toString(), modifier = Modifier.weight(1f))
        }

        Text(
            text = "${result.coverageTierLabel} · 许可：${result.licenseStatus} · 签名：${result.signatureStatus} · 游戏 ID ${result.gameId ?: "未知"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        result.contentDigest?.let { digest ->
            Text(
                text = "SHA-256：${digest.take(12)}… · 错误 ${result.errorCount} · 警告 ${result.warningCount}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val issues = result.issues.take(5)
        if (issues.isEmpty()) {
            IssueLine(
                issue = UiGkpPreflightIssue(
                    severity = UiGkpPreflightSeverity.Info,
                    code = "clean",
                    path = null,
                    message = "未发现阻断问题。",
                )
            )
        } else {
            issues.forEach { issue -> IssueLine(issue) }
        }
    }
}

@Composable
private fun InstallPlanCard(
    plan: UiGkpInstallPlan,
    status: UiGkpInstallStatus,
    onInstallExternalPack: () -> Unit,
) {
    val statusColor = status.phase.statusColor()
    SectionCard(
        title = "安装确认",
        trailing = { InfoChip(text = plan.mode.chipLabel()) },
    ) {
        Column(
            modifier = Modifier.testTag("packs_install_plan"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.mode.title(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "目标 ${plan.gameId} · ${plan.currentPackVersion ?: "无"} → ${plan.newPackVersion ?: "未知"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile(label = "当前", value = plan.currentKnowledgeRows.toString(), modifier = Modifier.weight(1f))
                MetricTile(label = "新包", value = plan.newKnowledgeRows.toString(), modifier = Modifier.weight(1f))
                MetricTile(label = "变化", value = plan.knowledgeDelta.signed(), modifier = Modifier.weight(1f))
            }

            Text(
                text = "${plan.coverageTierLabel} · ${plan.provenanceLabel} · ${plan.signatureLabel} · ${plan.sourceCount} 个来源 · ${plan.goldenRows} 条测试题",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            plan.contentDigest?.let { digest ->
                Text(
                    text = "SHA-256：${digest.take(12)}… · 安装前可取消",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (status.phase != UiGkpInstallPhase.Idle) {
                Text(
                    text = status.message,
                    modifier = Modifier.testTag("packs_install_status"),
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                )
            }

            Button(
                onClick = onInstallExternalPack,
                enabled = !status.isRunning,
                modifier = Modifier.testTag("packs_install_confirm_button"),
            ) {
                Icon(Icons.Filled.Extension, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(plan.mode.buttonLabel(status.phase))
            }
        }
    }
}

@Composable
private fun IssueLine(issue: UiGkpPreflightIssue) {
    val color = issue.severity.issueColor()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = issue.path?.let { "$it · ${issue.code}" } ?: issue.code,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = issue.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeaderBlock(state: UiGkpLibraryState) {
    SectionCard(title = "当前知识包", accent = state.enabledPackCount > 0) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.62f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = "游戏知识包",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "${state.enabledPackCount} 启用 · ${state.disabledPackCount} 禁用 · ${state.totalKnowledgeRows} 条知识 · ${state.totalSources} 个来源",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "RetroSprite 会优先用已启用的本地知识包回答游戏问题；停用只是不参与问答，删除才会移除本机数据。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PreflightStatusChip(state: UiGkpPreflightState) {
    val result = state.result
    val color = when {
        state.isRunning -> StatusStarting
        result == null -> StatusStopped
        result.ok -> StatusRunning
        else -> StatusError
    }
    val label = when {
        state.isRunning -> "检查中"
        result == null -> "未选择"
        result.ok -> "可安装"
        else -> "需修复"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Extension,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun ImportStatusCard(status: UiGkpImportStatus) {
    SectionCard(
        title = "内置知识包扫描",
        trailing = {
            StatusChip(status)
        },
    ) {
        Column(
            modifier = Modifier.testTag("packs_import_status"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = status.message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile(label = "总计", value = status.totalPacks.toString(), modifier = Modifier.weight(1f))
                MetricTile(label = "成功", value = status.importedPacks.toString(), modifier = Modifier.weight(1f))
                MetricTile(label = "失败", value = status.failedPacks.toString(), modifier = Modifier.weight(1f))
            }
            status.updatedAtMillis?.let {
                Text(
                    text = "最近更新：${relativeTime(it)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(62.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun EmptyPackList(
    status: UiGkpImportStatus,
    hasInstalledPacks: Boolean,
    hasActiveSearch: Boolean,
) {
    val message = when {
        status.phase == UiGkpImportPhase.Importing -> "正在扫描内置知识包，完成后会自动出现在这里。"
        hasInstalledPacks && hasActiveSearch -> "没有找到符合条件的知识包。可以换一个游戏名、平台或切回“全部”。"
        else -> "还没有可用知识包。内置包会自动扫描；也可以用“导入外部知识包”选择本机文件夹。"
    }
    SectionCard(title = if (hasInstalledPacks) "没有匹配结果" else "还没有知识包") {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PackRow(
    pack: UiGkpPackItem,
    onDisablePack: (String) -> Unit,
    onEnablePack: (String) -> Unit,
    onRequestDeletePack: (String) -> Unit,
) {
    var actionsExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("packs_item_${pack.gameId}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = pack.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pack.compactMeta(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            InfoChip(text = if (pack.isEnabled) "已启用" else "已停用")
            Box {
                IconButton(
                    onClick = { actionsExpanded = true },
                    modifier = Modifier.testTag("packs_more_${pack.gameId}"),
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
                }
                DropdownMenu(
                    expanded = actionsExpanded,
                    onDismissRequest = { actionsExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("删除本机数据") },
                        leadingIcon = {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                        },
                        onClick = {
                            actionsExpanded = false
                            onRequestDeletePack(pack.gameId)
                        },
                        modifier = Modifier.testTag("packs_delete_request_${pack.gameId}"),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InfoChip(text = pack.coverageTierLabel)
            InfoChip(text = pack.trustLabel)
            InfoChip(text = pack.provenanceLabel)
            InfoChip(text = pack.signatureLabel)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${pack.knowledgeCount} 条知识 · ${pack.sourceCount} 个来源",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (pack.isEnabled) {
                TextButton(
                    onClick = { onDisablePack(pack.gameId) },
                    modifier = Modifier.testTag("packs_disable_${pack.gameId}"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("停用")
                }
            } else {
                Button(
                    onClick = { onEnablePack(pack.gameId) },
                    modifier = Modifier.testTag("packs_enable_${pack.gameId}"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("启用")
                }
            }
        }

        if (!pack.isEnabled && pack.disabledAtMillis != null) {
            Text(
                text = "停用时间：${relativeTime(pack.disabledAtMillis)}。此包保留在本机，但不会参与游戏识别或问答。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun DeletePlanCard(
    state: UiGkpDeleteState,
    onConfirmDeletePack: () -> Unit,
    onCancelDeletePack: () -> Unit,
) {
    val plan = state.plan
    SectionCard(
        title = "删除确认",
        trailing = {
            InfoChip(text = state.phase.label())
        },
    ) {
        Column(
            modifier = Modifier.testTag("packs_delete_plan"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (plan == null) {
                Text(
                    text = state.message,
                    modifier = Modifier.testTag("packs_delete_status"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.phase == UiGkpDeletePhase.Error) {
                        StatusError
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            } else {
                DeletePlanDetails(plan)
                plan.warning?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusStarting,
                    )
                }
                Text(
                    text = state.message,
                    modifier = Modifier.testTag("packs_delete_status"),
                    style = MaterialTheme.typography.bodySmall,
                    color = state.phase.statusColor(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onCancelDeletePack,
                        enabled = !state.isRunning,
                        modifier = Modifier.testTag("packs_delete_cancel_button"),
                    ) {
                        Text(if (state.phase == UiGkpDeletePhase.Deleted) "关闭" else "取消")
                    }
                    if (state.phase != UiGkpDeletePhase.Deleted) {
                        Button(
                            onClick = onConfirmDeletePack,
                            enabled = !state.isRunning,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                            modifier = Modifier.testTag("packs_delete_confirm_button"),
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.phase == UiGkpDeletePhase.Deleting) "删除中" else "确认删除")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeletePlanDetails(plan: UiGkpDeletePlan) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = plan.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${plan.packId} · 游戏 ID ${plan.gameId} · v${plan.packVersion}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile(label = "知识", value = plan.knowledgeCount.toString(), modifier = Modifier.weight(1f))
            MetricTile(label = "来源", value = plan.sourceCount.toString(), modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusChip(status: UiGkpImportStatus) {
    val color = status.phase.statusColor()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = status.phase.label(),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = color,
        )
    }
}

private enum class PackFilter(val label: String) {
    All("全部"),
    Enabled("已启用"),
    Disabled("已停用");

    fun matches(pack: UiGkpPackItem): Boolean = when (this) {
        All -> true
        Enabled -> pack.isEnabled
        Disabled -> !pack.isEnabled
    }

    fun count(state: UiGkpLibraryState): Int = state.packs.count { matches(it) }

    companion object {
        fun fromName(name: String): PackFilter = values().firstOrNull { it.name == name } ?: All
    }
}

private fun UiGkpPackItem.matchesSearch(query: String): Boolean {
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isEmpty()) return true
    val searchableText = listOfNotNull(
        title,
        packId,
        gameId,
        platform,
        region,
        packVersion,
        coverageTierLabel,
        schemaVersion,
        trustLabel,
        provenanceLabel,
        signatureLabel,
        availabilityLabel,
        licenseSummary,
        languages.joinToString(" "),
    ).joinToString(" ").lowercase()
    return searchableText.contains(normalizedQuery)
}

private fun UiGkpPackItem.compactMeta(): String {
    val regionPart = region?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
    return "${platform.uppercase()}$regionPart · v$packVersion · ${languages.joinToString("/")}"
}

private fun UiGkpImportPhase.statusColor(): Color = when (this) {
    UiGkpImportPhase.Idle -> StatusStopped
    UiGkpImportPhase.Importing -> StatusStarting
    UiGkpImportPhase.Ready -> StatusRunning
    UiGkpImportPhase.Error -> StatusError
}

@Composable
private fun UiGkpInstallPhase.statusColor(): Color = when (this) {
    UiGkpInstallPhase.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
    UiGkpInstallPhase.Installing -> StatusStarting
    UiGkpInstallPhase.Installed -> StatusRunning
    UiGkpInstallPhase.Error -> StatusError
}

private fun UiGkpImportPhase.label(): String = when (this) {
    UiGkpImportPhase.Idle -> "待扫描"
    UiGkpImportPhase.Importing -> "扫描中"
    UiGkpImportPhase.Ready -> "已就绪"
    UiGkpImportPhase.Error -> "有错误"
}

private fun UiGkpDeletePhase.label(): String = when (this) {
    UiGkpDeletePhase.Idle -> "待选择"
    UiGkpDeletePhase.AwaitingConfirmation -> "待确认"
    UiGkpDeletePhase.Deleting -> "删除中"
    UiGkpDeletePhase.Deleted -> "已删除"
    UiGkpDeletePhase.Error -> "有错误"
}

@Composable
private fun UiGkpDeletePhase.statusColor(): Color = when (this) {
    UiGkpDeletePhase.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
    UiGkpDeletePhase.AwaitingConfirmation -> StatusStarting
    UiGkpDeletePhase.Deleting -> StatusStarting
    UiGkpDeletePhase.Deleted -> StatusRunning
    UiGkpDeletePhase.Error -> StatusError
}

private fun UiGkpPreflightSeverity.issueColor(): Color = when (this) {
    UiGkpPreflightSeverity.Info -> StatusRunning
    UiGkpPreflightSeverity.Warning -> StatusStarting
    UiGkpPreflightSeverity.Error -> StatusError
}

private fun UiGkpInstallMode.title(): String = when (this) {
    UiGkpInstallMode.NewInstall -> "准备安装新知识包"
    UiGkpInstallMode.ReplaceExisting -> "准备覆盖已有知识包"
}

private fun UiGkpInstallMode.chipLabel(): String = when (this) {
    UiGkpInstallMode.NewInstall -> "新安装"
    UiGkpInstallMode.ReplaceExisting -> "覆盖"
}

private fun UiGkpInstallMode.buttonLabel(phase: UiGkpInstallPhase): String = when {
    phase == UiGkpInstallPhase.Installing -> "安装中"
    phase == UiGkpInstallPhase.Installed -> "再次安装"
    this == UiGkpInstallMode.NewInstall -> "安装知识包"
    else -> "确认覆盖"
}

private fun Int.signed(): String = when {
    this > 0 -> "+$this"
    else -> toString()
}

private fun relativeTime(timestampMillis: Long): String {
    val delta = (System.currentTimeMillis() - timestampMillis).coerceAtLeast(0L)
    val seconds = delta / 1_000L
    return when {
        seconds < 5L -> "刚刚"
        seconds < 60L -> "${seconds}s 前"
        seconds < 3_600L -> "${seconds / 60L}m 前"
        else -> "${seconds / 3_600L}h 前"
    }
}

@Preview(
    showBackground = true, backgroundColor = 0xFF0B0620,
    widthDp = 380, heightDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun PacksPreview() {
    RetroSpriteTheme {
        val state = PreviewStub.gkpLibrary().state.value
        val preflight = PreviewStub.gkpPreflight().state.value
        PacksContent(
            contentPadding = PaddingValues(),
            state = state,
            preflight = preflight,
            onPickExternalPack = {},
            onInstallExternalPack = {},
            onClearPreflight = {},
            onDisablePack = {},
            onEnablePack = {},
            onRequestDeletePack = {},
            onConfirmDeletePack = {},
            onCancelDeletePack = {},
        )
    }
}
