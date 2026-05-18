package com.retrosprite.app.ui.screens.settings

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retrosprite.app.ui.components.SectionCard
import com.retrosprite.app.ui.theme.RetroSpriteTheme
import com.retrosprite.app.ui.viewmodel.UiAboutInfo
import com.retrosprite.app.ui.viewmodel.UiLlmProvider
import com.retrosprite.app.ui.viewmodel.UiSettings
import com.retrosprite.app.ui.viewmodel.UiSpoilerLevel
import com.retrosprite.app.ui.viewmodel.rememberUiDependencies

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val deps = rememberUiDependencies()
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(deps.settingsStore, deps.endpoint, deps.about)
    )
    val settings by viewModel.settings.collectAsState(initial = UiSettings())

    SettingsContent(
        contentPadding = contentPadding,
        settings = settings,
        about = viewModel.about,
        onApplyPort = viewModel::applyPort,
        onApplyLlm = viewModel::applyLlmConfig,
        onApplySpoiler = viewModel::applySpoilerLevel,
        modifier = modifier
    )
}

@Composable
private fun SettingsContent(
    contentPadding: PaddingValues,
    settings: UiSettings,
    about: UiAboutInfo,
    onApplyPort: (Int) -> Unit,
    onApplyLlm: (UiLlmProvider, String, String, String) -> Unit,
    onApplySpoiler: (UiSpoilerLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        EndpointSection(currentPort = settings.port, onApply = onApplyPort)
        LlmSection(settings = settings, onApply = onApplyLlm)
        SpoilerSection(level = settings.spoilerLevel, onApply = onApplySpoiler)
        AboutSection(about = about)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun EndpointSection(currentPort: Int, onApply: (Int) -> Unit) {
    var portInput by remember(currentPort) { mutableStateOf(currentPort.toString()) }
    SectionCard(title = "ENDPOINT") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "\u4fee\u6539\u540e\u70b9\u51fb\u4fdd\u5b58\u5c06\u91cd\u542f\u672c\u5730\u670d\u52a1\u3002",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = portInput,
                    onValueChange = { v -> portInput = v.filter { it.isDigit() }.take(5) },
                    label = { Text("\u7aef\u53e3\u53f7") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = retroFieldColors()
                )
                Button(
                    onClick = {
                        val p = portInput.toIntOrNull() ?: 8080
                        onApply(p)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("\u4fdd\u5b58\u5e76\u91cd\u542f")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LlmSection(
    settings: UiSettings,
    onApply: (UiLlmProvider, String, String, String) -> Unit
) {
    var provider by remember(settings.llmProvider) { mutableStateOf(settings.llmProvider) }
    var apiKey by remember(settings.llmApiKey) { mutableStateOf(settings.llmApiKey) }
    var baseUrl by remember(settings.llmBaseUrl) { mutableStateOf(settings.llmBaseUrl) }
    var model by remember(settings.llmModel) { mutableStateOf(settings.llmModel) }
    var keyVisible by remember { mutableStateOf(false) }
    var dropdownOpen by remember { mutableStateOf(false) }

    SectionCard(title = "LLM PROVIDER") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "\u4ec5 UI \u9884\u89c8\uff0cPhase 1 \u63a5\u5165\u540e\u751f\u6548\u3002",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Provider selector via the M3-recommended ExposedDropdownMenuBox.
            ExposedDropdownMenuBox(
                expanded = dropdownOpen,
                onExpandedChange = { dropdownOpen = it }
            ) {
                OutlinedTextField(
                    value = provider.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("\u63d0\u4f9b\u5546") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownOpen)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    colors = retroFieldColors()
                )
                androidx.compose.material3.ExposedDropdownMenu(
                    expanded = dropdownOpen,
                    onDismissRequest = { dropdownOpen = false }
                ) {
                    UiLlmProvider.values().forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.displayName) },
                            onClick = {
                                provider = p
                                dropdownOpen = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = if (keyVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            imageVector = if (keyVisible) Icons.Filled.VisibilityOff
                            else Icons.Filled.Visibility,
                            contentDescription = null
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = retroFieldColors()
            )

            if (provider == UiLlmProvider.Custom) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = retroFieldColors()
                )
            }

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("\u6a21\u578b\u540d") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = retroFieldColors()
            )

            Button(
                onClick = { onApply(provider, apiKey, baseUrl, model) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("\u4fdd\u5b58\u914d\u7f6e")
            }
        }
    }
}

@Composable
private fun SpoilerSection(level: UiSpoilerLevel, onApply: (UiSpoilerLevel) -> Unit) {
    SectionCard(title = "\u5267\u900f\u7b49\u7ea7") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "\u63a7\u5236 AI \u63d0\u793a\u7684\u660e\u786e\u7a0b\u5ea6\u3002",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            UiSpoilerLevel.values().forEach { l ->
                RadioRow(
                    label = l.displayName,
                    description = l.description(),
                    selected = level == l,
                    onClick = { onApply(l) }
                )
            }
        }
    }
}

@Composable
private fun RadioRow(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(
                    1.5.dp,
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun UiSpoilerLevel.description(): String = when (this) {
    UiSpoilerLevel.Light -> "\u53ea\u7ed9\u65b9\u5411\uff0c\u4fdd\u7559\u63a2\u7d22\u4e50\u8da3\u3002"
    UiSpoilerLevel.Clear -> "\u660e\u786e\u63d0\u793a\u4e0b\u4e00\u6b65\uff0c\u4f46\u4e0d\u900f\u9732\u8c1c\u9898\u7b54\u6848\u3002"
    UiSpoilerLevel.Direct -> "\u76f4\u63a5\u544a\u8bc9\u4f60\u600e\u4e48\u505a\uff0c\u9002\u5408\u4e0d\u8c03\u67e5\u3002"
}

@Composable
private fun AboutSection(about: UiAboutInfo) {
    SectionCard(title = "\u5173\u4e8e") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AboutLine("\u5e94\u7528\u7248\u672c", about.appVersion)
            AboutLine("GKP \u534f\u8bae", "v${about.gkpSchemaVersion}")
            AboutLine("\u6784\u5efa", about.buildFlavor)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "RetroSprite \u662f\u4e00\u4e2a\u672c\u5730 RetroArch AI Service \u5b9e\u73b0\uff0c\u8fd0\u884c\u5b8c\u5168\u4e0d\u53d1\u9001\u4f60\u7684\u6e38\u620f\u622a\u56fe\u5230\u5176\u4ed6\u670d\u52a1\u5668\uff08\u9664\u4e86\u4f60\u9009\u62e9\u7684 LLM \u4f9b\u5e94\u5546\uff09\u3002",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AboutLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun retroFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary
)

@Preview(
    showBackground = true, backgroundColor = 0xFF0B0620,
    widthDp = 380, heightDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun SettingsPreview() {
    RetroSpriteTheme {
        SettingsContent(
            contentPadding = PaddingValues(),
            settings = UiSettings(),
            about = UiAboutInfo(),
            onApplyPort = {},
            onApplyLlm = { _, _, _, _ -> },
            onApplySpoiler = {}
        )
    }
}
