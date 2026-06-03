package com.retrosprite.app.ui.screens.diagnostics

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retrosprite.app.ui.components.SectionCard
import com.retrosprite.app.ui.components.StatusIndicator
import com.retrosprite.app.ui.theme.RetroSpriteTheme
import com.retrosprite.app.ui.viewmodel.PreviewStub
import com.retrosprite.app.ui.viewmodel.UiEndpointPhase
import com.retrosprite.app.ui.viewmodel.UiEndpointStatus
import com.retrosprite.app.ui.viewmodel.UiOutputMode
import com.retrosprite.app.ui.viewmodel.UiRequestLogItem
import com.retrosprite.app.ui.viewmodel.rememberUiDependencies

@Composable
fun DiagnosticsScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val deps = rememberUiDependencies()
    val viewModel: DiagnosticsViewModel = viewModel(
        factory = DiagnosticsViewModel.factory(deps.endpoint, deps.requestLog)
    )
    val status by viewModel.status.collectAsStateWithLifecycle()
    val items by viewModel.log.collectAsStateWithLifecycle(initialValue = emptyList())
    var sourceFilter by remember { mutableStateOf(DiagnosticsSourceFilter.All) }

    DiagnosticsContent(
        contentPadding = contentPadding,
        status = status,
        items = items,
        sourceFilter = sourceFilter,
        onSourceFilterSelected = { sourceFilter = it },
        onCheckHealth = viewModel::checkHealth,
        onConnectionTest = viewModel::runConnectionTest,
        onClear = viewModel::clearLog,
        modifier = modifier
    )
}

@Composable
private fun DiagnosticsContent(
    contentPadding: PaddingValues,
    status: UiEndpointStatus,
    items: List<UiRequestLogItem>,
    sourceFilter: DiagnosticsSourceFilter,
    onSourceFilterSelected: (DiagnosticsSourceFilter) -> Unit,
    onCheckHealth: () -> Unit,
    onConnectionTest: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var detail by remember { mutableStateOf<UiRequestLogItem?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        val isWide = maxWidth >= 600.dp ||
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE && maxWidth >= 520.dp
        val logListHeight = if (isWide) {
            (maxHeight - 176.dp).coerceIn(190.dp, 440.dp)
        } else {
            (maxHeight - 248.dp).coerceIn(240.dp, 440.dp)
        }

        if (isWide) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    ToolsCard(status, onCheckHealth, onConnectionTest)
                }
                Box(modifier = Modifier.weight(1.2f)) {
                    LogCard(
                        items = items,
                        sourceFilter = sourceFilter,
                        onSourceFilterSelected = onSourceFilterSelected,
                        onItemClick = { detail = it },
                        onClear = onClear,
                        listHeight = logListHeight,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                ToolsCard(status, onCheckHealth, onConnectionTest)
                LogCard(
                    items = items,
                    sourceFilter = sourceFilter,
                    onSourceFilterSelected = onSourceFilterSelected,
                    onItemClick = { detail = it },
                    onClear = onClear,
                    listHeight = logListHeight,
                )
            }
        }

        detail?.let { item ->
            AlertDialog(
                onDismissRequest = { detail = null },
                confirmButton = {
                    TextButton(onClick = { detail = null }) { Text("\u5173\u95ed") }
                },
                title = {
                    Text(
                        text = item.label ?: "\u8bf7\u6c42\u8be6\u60c5",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "${if (item.ok) "\u2713 \u6210\u529f" else "\u2715 \u5931\u8d25"}  \u00b7  ${item.durationMillis} ms",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (item.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "\u94fe\u8def\uff1a${item.pipelineStage.uppercase()}  \u00b7  LLM ${item.llmStatus.uppercase()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        item.answerTypeLabel?.let { label ->
                            Text(
                                text = "答案类型：$label",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        item.diagnosticFailureExplanations().forEach { explanation ->
                            Text(
                                text = "诊断：${explanation.title} · ${explanation.message}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        item.feedback?.let {
                            Text(
                                text = "反馈：${it.displayName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        item.question?.let {
                            Text(
                                text = "问题：$it",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        item.questionSource?.let {
                            Text(
                                text = "问题来源：${it.displayNameForQuestionSource()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (item.llmProvider != null || item.llmModel != null) {
                            Text(
                                text = "模型：${listOfNotNull(item.llmProvider, item.llmModel).joinToString(" / ")}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "预算：${item.llmMaxTokens?.let { "$it tok" } ?: "-"}  ·  timeout ${item.llmTimeoutMs?.toString() ?: "-"} ms  ·  latency ${item.llmLatencyMs?.toString() ?: "-"} ms",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (item.llmTokensIn > 0 || item.llmTokensOut > 0) {
                            Text(
                                text = "Token：in ${item.llmTokensIn} / out ${item.llmTokensOut}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        item.llmError?.let {
                            Text(
                                text = "LLM 错误：$it",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (item.sourceIds.isNotEmpty()) {
                            Text(
                                text = "\u6765\u6e90\uff1a${item.sourceIds.joinToString(", ")}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = item.fullResponseJson,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ToolsCard(
    status: UiEndpointStatus,
    onCheckHealth: () -> Unit,
    onConnectionTest: () -> Unit
) {
    SectionCard(
        title = "\u5feb\u901f\u8bca\u65ad",
        accent = false,
        trailing = {
            StatusIndicator(phase = status.phase, label = when (status.phase) {
                UiEndpointPhase.Running -> "运行中"
                UiEndpointPhase.Starting -> "启动中"
                UiEndpointPhase.Stopped -> "已停止"
                UiEndpointPhase.Error -> "错误"
            })
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "\u5728\u63a5\u5165 RetroArch \u524d\u68c0\u9a8c\u672c\u5730\u670d\u52a1\u662f\u5426\u6b63\u5e38\u5de5\u4f5c\u3002",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "推荐：RetroArch -> Settings -> AI Service -> Pause During Translation -> ON",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCheckHealth,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Filled.HealthAndSafety, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("\u5065\u5eb7\u68c0\u67e5")
                }
                OutlinedButton(
                    onClick = onConnectionTest,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.NetworkPing, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("\u8fde\u63a5\u6d4b\u8bd5")
                }
            }
            status.lastHealthCheckMillis?.let {
                Text(
                    text = "\u4e0a\u6b21\u5fc3\u8df3\uff1a${if (status.lastHealthOk == true) "OK" else "\u5931\u8d25"}  \u00b7  ${relTime(it)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LogCard(
    items: List<UiRequestLogItem>,
    sourceFilter: DiagnosticsSourceFilter,
    onSourceFilterSelected: (DiagnosticsSourceFilter) -> Unit,
    onItemClick: (UiRequestLogItem) -> Unit,
    onClear: () -> Unit,
    listHeight: Dp = 440.dp,
) {
    val sourceCounts = items.diagnosticsSourceCounts()
    val filteredItems = items.filterByDiagnosticsSource(sourceFilter)
    val title = if (sourceFilter == DiagnosticsSourceFilter.All) {
        "请求日志 (${items.size})"
    } else {
        "请求日志 (${filteredItems.size}/${items.size})"
    }
    SectionCard(
        title = title,
        contentPadding = PaddingValues(0.dp),
        trailing = {
            TextButton(onClick = onClear) {
                Icon(
                    Icons.Filled.DeleteSweep,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "\u6e05\u7a7a",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SourceFilterBar(
                selected = sourceFilter,
                counts = sourceCounts,
                onSelected = onSourceFilterSelected,
            )
            if (items.isEmpty()) {
                EmptyLog()
            } else if (filteredItems.isEmpty()) {
                EmptyFilteredLog(sourceFilter)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(listHeight),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        LogItemRow(item = item, onClick = { onItemClick(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceFilterBar(
    selected: DiagnosticsSourceFilter,
    counts: DiagnosticsSourceCounts,
    onSelected: (DiagnosticsSourceFilter) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("diagnostics_source_filters"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "来源筛选",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DiagnosticsSourceFilter.values().toList().chunked(SOURCE_FILTERS_PER_ROW).forEach { rowFilters ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rowFilters.forEach { filter ->
                    val isSelected = selected == filter
                    OutlinedButton(
                        onClick = { onSelected(filter) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("diagnostics_filter_${filter.id}"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            contentColor = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "${filter.displayName} ${counts.countFor(filter)}",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                repeat(SOURCE_FILTERS_PER_ROW - rowFilters.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        Text(
            text = "当前：${selected.displayName} ${counts.countFor(selected)} / ${counts.all}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("diagnostics_filter_summary"),
        )
    }
}

@Composable
private fun LogItemRow(item: UiRequestLogItem, onClick: () -> Unit) {
    val statusColor = if (item.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val tags = item.diagnosticTags()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diagnostics_log_item_${item.id}")
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = relTime(item.timestampMillis),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${item.imageBytes / 1024} KB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            DiagnosticTagFlow(tags = tags)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.label ?: "\u672a\u547d\u540d\u6e38\u620f",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = item.responsePreview,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiagnosticTagFlow(tags: List<String>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tags.forEach { tag ->
            Tag(text = tag)
        }
    }
}

@Composable
private fun Tag(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
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

private fun UiRequestLogItem.diagnosticTags(): List<String> = buildList {
    if (paused) add("PAUSED")
    if (isDebug) add("DEBUG")
    if (rawOutputMode.startsWith("app:")) add("APP")
    if (rawOutputMode.startsWith("hotkey_screen_translation:")) add("TRANSLATE")
    if (questionSource == QUESTION_SOURCE_PENDING_HOTKEY) {
        add("PENDING")
    } else if (question != null) {
        add("QUESTION")
    }
    feedback?.let { add(it.diagnosticsTag) }
    add(outputMode.name.uppercase())
    add(pipelineStage.uppercase())
    llmProvider?.let { add(it.uppercase()) }
    if (sourceIds.isNotEmpty()) add("SRC ${sourceIds.size}")
}

@Composable
private fun EmptyLog() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "// \u8bf7\u6c42\u65e5\u5fd7\u4e3a\u7a7a",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "\u4f7f RetroArch \u53d1\u9001\u4e00\u6b21 AI Service \u8bf7\u6c42\u540e\u5237\u65b0\u3002",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyFilteredLog(sourceFilter: DiagnosticsSourceFilter) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "// ${sourceFilter.displayName} 来源暂无请求",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "切回“全部”查看完整日志，或触发对应来源后再检查。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun relTime(ts: Long): String {
    val diff = (System.currentTimeMillis() - ts).coerceAtLeast(0L)
    return when {
        diff < 5_000 -> "\u521a\u521a"
        diff < 60_000 -> "${diff / 1000} \u79d2\u524d"
        diff < 3_600_000 -> "${diff / 60_000} \u5206\u949f\u524d"
        else -> "${diff / 3_600_000} \u5c0f\u65f6\u524d"
    }
}

private const val QUESTION_SOURCE_PENDING_HOTKEY: String = "pending_hotkey"
private const val SOURCE_FILTERS_PER_ROW: Int = 2

private fun String.displayNameForQuestionSource(): String = when (this) {
    "app" -> "App 内提问"
    "debug" -> "Debug ask"
    "pending_hotkey" -> "Pending hotkey"
    "retroarch" -> "RetroArch 请求"
    else -> this
}

@Suppress("unused")
private fun previewItems(): List<UiRequestLogItem> = PreviewStub.sampleLog()

@Preview(
    showBackground = true, backgroundColor = 0xFF0B0620,
    widthDp = 380, heightDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun DiagnosticsPreview() {
    RetroSpriteTheme {
        DiagnosticsContent(
            contentPadding = PaddingValues(),
            status = UiEndpointStatus(
                phase = UiEndpointPhase.Running,
                port = 4_404,
                baseUrl = "http://localhost:4404",
                lastHealthCheckMillis = System.currentTimeMillis() - 12_000,
                lastHealthOk = true
            ),
            items = previewItems(),
            sourceFilter = DiagnosticsSourceFilter.All,
            onSourceFilterSelected = {},
            onCheckHealth = {},
            onConnectionTest = {},
            onClear = {}
        )
    }
}

@Preview(
    showBackground = true, backgroundColor = 0xFF0B0620,
    widthDp = 380, heightDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun DiagnosticsEmptyPreview() {
    RetroSpriteTheme {
        DiagnosticsContent(
            contentPadding = PaddingValues(),
            status = UiEndpointStatus(
                phase = UiEndpointPhase.Running,
                port = 4_404,
                baseUrl = "http://localhost:4404"
            ),
            items = emptyList(),
            sourceFilter = DiagnosticsSourceFilter.All,
            onSourceFilterSelected = {},
            onCheckHealth = {},
            onConnectionTest = {},
            onClear = {}
        )
    }
}
