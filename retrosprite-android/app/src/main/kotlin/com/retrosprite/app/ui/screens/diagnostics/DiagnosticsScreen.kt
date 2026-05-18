package com.retrosprite.app.ui.screens.diagnostics

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
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

    DiagnosticsContent(
        contentPadding = contentPadding,
        status = status,
        items = items,
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
    onCheckHealth: () -> Unit,
    onConnectionTest: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var detail by remember { mutableStateOf<UiRequestLogItem?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        val isWide = maxWidth >= 600.dp ||
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE && maxWidth >= 520.dp

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
                    LogCard(items = items, onItemClick = { detail = it }, onClear = onClear)
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
                LogCard(items = items, onItemClick = { detail = it }, onClear = onClear)
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
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${if (item.ok) "\u2713 \u6210\u529f" else "\u2715 \u5931\u8d25"}  \u00b7  ${item.durationMillis} ms",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (item.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
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
                UiEndpointPhase.Running -> "RUNNING"
                UiEndpointPhase.Starting -> "STARTING"
                UiEndpointPhase.Stopped -> "STOPPED"
                UiEndpointPhase.Error -> "ERROR"
            })
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "\u5728\u63a5\u5165 RetroArch \u524d\u68c0\u9a8c\u672c\u5730\u670d\u52a1\u662f\u5426\u6b63\u5e38\u5de5\u4f5c\u3002",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    onItemClick: (UiRequestLogItem) -> Unit,
    onClear: () -> Unit
) {
    SectionCard(
        title = "\u8bf7\u6c42\u65e5\u5fd7 (${items.size})",
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
        if (items.isEmpty()) {
            EmptyLog()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(440.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    LogItemRow(item = item, onClick = { onItemClick(item) })
                }
            }
        }
    }
}

@Composable
private fun LogItemRow(item: UiRequestLogItem, onClick: () -> Unit) {
    val statusColor = if (item.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
            Spacer(Modifier.width(8.dp))
            if (item.paused) {
                Tag(text = "PAUSED")
                Spacer(Modifier.width(4.dp))
            }
            Tag(text = item.outputMode.name.uppercase())
            Spacer(Modifier.weight(1f))
            Text(
                text = "${item.imageBytes / 1024} KB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.label ?: "\u672a\u547d\u540d\u6e38\u620f",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = item.responsePreview,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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

private fun relTime(ts: Long): String {
    val diff = (System.currentTimeMillis() - ts).coerceAtLeast(0L)
    return when {
        diff < 5_000 -> "\u521a\u521a"
        diff < 60_000 -> "${diff / 1000} \u79d2\u524d"
        diff < 3_600_000 -> "${diff / 60_000} \u5206\u949f\u524d"
        else -> "${diff / 3_600_000} \u5c0f\u65f6\u524d"
    }
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
                port = 8080,
                baseUrl = "http://192.168.1.42:8080",
                lastHealthCheckMillis = System.currentTimeMillis() - 12_000,
                lastHealthOk = true
            ),
            items = previewItems(),
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
                port = 8080,
                baseUrl = "http://192.168.1.42:8080"
            ),
            items = emptyList(),
            onCheckHealth = {},
            onConnectionTest = {},
            onClear = {}
        )
    }
}
