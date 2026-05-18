package com.retrosprite.app.ui.screens.home

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retrosprite.app.ui.components.CopyToClipboardButton
import com.retrosprite.app.ui.components.InfoRow
import com.retrosprite.app.ui.components.SectionCard
import com.retrosprite.app.ui.components.StatusIndicator
import com.retrosprite.app.ui.theme.RetroSpriteTheme
import com.retrosprite.app.ui.viewmodel.UiEndpointPhase
import com.retrosprite.app.ui.viewmodel.UiEndpointStatus
import com.retrosprite.app.ui.viewmodel.rememberUiDependencies

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val deps = rememberUiDependencies()
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(deps.endpoint))
    val status by viewModel.status.collectAsStateWithLifecycle()

    HomeScreenContent(
        contentPadding = contentPadding,
        status = status,
        onRestart = viewModel::restart,
        onCheckHealth = viewModel::checkHealth,
        modifier = modifier
    )
}

@Composable
private fun HomeScreenContent(
    contentPadding: PaddingValues,
    status: UiEndpointStatus,
    onRestart: () -> Unit,
    onCheckHealth: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                    StatusHeader(status)
                    EndpointCard(status, onRestart, onCheckHealth)
                }
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    HowToConfigureCard(status)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                StatusHeader(status)
                EndpointCard(status, onRestart, onCheckHealth)
                HowToConfigureCard(status)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun StatusHeader(status: UiEndpointStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "RETROSPRITE",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = status.headline(),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = status.subhead(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EndpointCard(
    status: UiEndpointStatus,
    onRestart: () -> Unit,
    onCheckHealth: () -> Unit
) {
    val context = LocalContext.current
    SectionCard(
        title = "\u672c\u673a\u7aef\u70b9",
        accent = status.phase == UiEndpointPhase.Running,
        trailing = {
            StatusIndicator(phase = status.phase, label = status.statusChipLabel())
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // BIG URL display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "RETROARCH \u8bf7\u6c42\u5730\u5740",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = status.baseUrl,
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CopyToClipboardButton(textToCopy = status.baseUrl, label = "\u590d\u5236 URL")
                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(status.baseUrl + status.healthPath)
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInBrowser,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "\u5728\u6d4f\u89c8\u5668\u6d4b\u8bd5",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // Quick stats
            Column {
                InfoRow(label = "\u7aef\u53e3", value = status.port.toString(), valueMonospace = true)
                InfoRow(
                    label = "\u4e0a\u6b21\u5fc3\u8df3",
                    value = status.lastHealthCheckMillis?.let { relTime(it) } ?: "\u5c1a\u672a\u68c0\u67e5",
                    valueMonospace = true
                )
                if (status.message != null) {
                    InfoRow(label = "\u72b6\u6001", value = status.message)
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onCheckHealth,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(text = "\u5065\u5eb7\u68c0\u67e5", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onRestart,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(text = "\u91cd\u542f", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun HowToConfigureCard(status: UiEndpointStatus) {
    SectionCard(title = "RETROARCH \u63a5\u5165\u6307\u5f15") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Step(
                index = 1,
                title = "\u6253\u5f00 RetroArch \u8bbe\u7f6e",
                detail = "\u8fdb\u5165 Settings \u2192 AI Service\uff0c\u6253\u5f00 \"AI Service Enable\"\u3002"
            )
            Step(
                index = 2,
                title = "\u586b\u5165\u672c\u5730\u7aef\u70b9",
                detail = "\u5c06 AI Service URL \u8bbe\u4e3a \u300c${status.baseUrl}\u300d\uff0c\u6a21\u5f0f\u9009\u62e9 Image \u6216 Text\u3002"
            )
            Step(
                index = 3,
                title = "\u9a8c\u8bc1\u8fde\u901a",
                detail = "\u5728\u6e38\u620f\u4e2d\u6309\u4e0b AI Service \u70ed\u952e\uff08\u9ed8\u8ba4 ALT+...\uff09\uff0c\u8fd4\u56de\u672c App \u67e5\u770b\u201c\u8bca\u65ad\u201d\u9875\u3002"
            )
        }
    }
}

@Composable
private fun Step(index: Int, title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = index.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun UiEndpointStatus.headline(): String = when (phase) {
    UiEndpointPhase.Running -> "\u670d\u52a1\u8fd0\u884c\u4e2d"
    UiEndpointPhase.Starting -> "\u542f\u52a8\u4e2d\u2026"
    UiEndpointPhase.Stopped -> "\u670d\u52a1\u5df2\u505c\u6b62"
    UiEndpointPhase.Error -> "\u51fa\u73b0\u9519\u8bef"
}

private fun UiEndpointStatus.subhead(): String = when (phase) {
    UiEndpointPhase.Running -> "RetroArch \u53ef\u8bbf\u95ee\u4ee5\u4e0b\u672c\u5730\u5730\u5740\u3002"
    UiEndpointPhase.Starting -> "\u9a6c\u4e0a\u5c31\u53ef\u4ee5\u63a5\u53d7\u8bf7\u6c42\u3002"
    UiEndpointPhase.Stopped -> "\u70b9\u51fb\u91cd\u542f\u4ee5\u91cd\u65b0\u63d0\u4f9b\u670d\u52a1\u3002"
    UiEndpointPhase.Error -> message ?: "\u8bf7\u67e5\u770b\u8bca\u65ad\u9875\u9762\u4ee5\u4e86\u89e3\u8be6\u60c5\u3002"
}

private fun UiEndpointStatus.statusChipLabel(): String = when (phase) {
    UiEndpointPhase.Running -> "RUNNING : ${port}"
    UiEndpointPhase.Starting -> "STARTING"
    UiEndpointPhase.Stopped -> "STOPPED"
    UiEndpointPhase.Error -> "ERROR"
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

@Preview(
    showBackground = true,
    backgroundColor = 0xFF0B0620,
    widthDp = 380,
    heightDp = 760,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun HomeScreenPreviewRunning() {
    RetroSpriteTheme {
        HomeScreenContent(
            contentPadding = PaddingValues(),
            status = UiEndpointStatus(
                phase = UiEndpointPhase.Running,
                port = 8080,
                baseUrl = "http://192.168.1.42:8080",
                message = "0 \u4e2a\u8bf7\u6c42\u5728\u6392\u961f",
                lastHealthCheckMillis = System.currentTimeMillis() - 12_000,
                lastHealthOk = true
            ),
            onRestart = {},
            onCheckHealth = {}
        )
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF0B0620,
    widthDp = 380,
    heightDp = 760,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun HomeScreenPreviewError() {
    RetroSpriteTheme {
        HomeScreenContent(
            contentPadding = PaddingValues(),
            status = UiEndpointStatus(
                phase = UiEndpointPhase.Error,
                port = 8080,
                baseUrl = "http://localhost:8080",
                message = "\u7aef\u53e3\u88ab\u5176\u4ed6\u8fdb\u7a0b\u5360\u7528"
            ),
            onRestart = {},
            onCheckHealth = {}
        )
    }
}
