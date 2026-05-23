package com.retrosprite.app.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardCommandKey
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.retrosprite.app.ui.viewmodel.DEFAULT_LLM_MAX_TOKENS
import com.retrosprite.app.ui.viewmodel.DEFAULT_LLM_TIMEOUT_SECONDS
import com.retrosprite.app.ui.components.CopyToClipboardButton
import com.retrosprite.app.ui.components.SectionCard
import com.retrosprite.app.endpoint.RetroArchHotkeyEvent
import com.retrosprite.app.ui.overlay.AndroidHotkeyVoiceOverlayRenderer
import com.retrosprite.app.ui.overlay.HotkeyVoiceOverlayPhase
import com.retrosprite.app.ui.overlay.HotkeyVoiceOverlayRenderState
import com.retrosprite.app.ui.theme.RetroSpriteTheme
import com.retrosprite.app.ui.viewmodel.UiAboutInfo
import com.retrosprite.app.ui.viewmodel.UiLlmProvider
import com.retrosprite.app.ui.viewmodel.UiOverlayPermissionState
import com.retrosprite.app.ui.viewmodel.UiSettings
import com.retrosprite.app.ui.viewmodel.UiSpoilerLevel
import com.retrosprite.app.ui.viewmodel.UiVoiceInputState
import com.retrosprite.app.ui.viewmodel.rememberUiDependencies
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onOpenDiagnostics: () -> Unit = {},
    onOpenAppQuestionConsole: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val deps = rememberUiDependencies()
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            deps.settingsStore,
            deps.endpoint,
            deps.llmConfigTest,
            deps.overlayPermission,
            deps.about,
        )
    )
    val settings by viewModel.settings.collectAsState(initial = UiSettings())
    val overlayPermissionState by viewModel.overlayPermissionState.collectAsState()
    val llmTestState by viewModel.llmTestState.collectAsState()
    val voiceInputState by deps.voiceInput.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val testOverlayRenderer = remember(context) {
        AndroidHotkeyVoiceOverlayRenderer(context)
    }
    DisposableEffect(testOverlayRenderer) {
        onDispose { testOverlayRenderer.hide() }
    }
    var hasRecordAudioPermission by remember(deps.voiceInput.requiresRecordAudioPermission) {
        mutableStateOf(
            !deps.voiceInput.requiresRecordAudioPermission ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasRecordAudioPermission = granted
    }

    LaunchedEffect(Unit) {
        viewModel.refreshOverlayPermission()
    }
    DisposableEffect(lifecycleOwner, deps.overlayPermission, deps.voiceInput.requiresRecordAudioPermission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasRecordAudioPermission = !deps.voiceInput.requiresRecordAudioPermission ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                viewModel.refreshOverlayPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsContent(
        contentPadding = contentPadding,
        settings = settings,
        llmTestState = llmTestState,
        overlayPermissionState = overlayPermissionState,
        voiceInputState = voiceInputState,
        hasRecordAudioPermission = hasRecordAudioPermission,
        about = viewModel.about,
        onApplyPort = viewModel::applyPort,
        onApplyLlm = viewModel::applyLlmConfig,
        onTestLlm = viewModel::testLlmConfig,
        onApplySpoiler = viewModel::applySpoilerLevel,
        onOpenOverlayPermission = viewModel::openOverlayPermissionSettings,
        onRefreshOverlayPermission = viewModel::refreshOverlayPermission,
        onTestOverlay = {
            coroutineScope.launch {
                val event = RetroArchHotkeyEvent(
                    label = "settings_overlay_test",
                    outputMode = "test",
                    imageBytes = 0,
                    paused = false,
                    receivedAtMillis = SystemClock.uptimeMillis(),
                )
                testOverlayRenderer.show(event)
                testOverlayRenderer.render(
                    HotkeyVoiceOverlayRenderState(
                        event = event,
                        phase = HotkeyVoiceOverlayPhase.Wake,
                        message = "RETROSPRITE LISTENING",
                    )
                )
                delay(2_500L)
                testOverlayRenderer.hide()
            }
        },
        onOpenMicrophonePermission = {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        },
        onTestMicrophone = {
            coroutineScope.launch {
                if (!hasRecordAudioPermission) {
                    recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else if (voiceInputState.isListening) {
                    deps.voiceInput.stopListening()
                } else {
                    deps.voiceInput.startListening()
                }
            }
        },
        onRefreshMicrophonePermission = {
            hasRecordAudioPermission = !deps.voiceInput.requiresRecordAudioPermission ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
            coroutineScope.launch { deps.voiceInput.cancelListening() }
        },
        onOpenDiagnostics = onOpenDiagnostics,
        onOpenAppQuestionConsole = onOpenAppQuestionConsole,
        modifier = modifier
    )
}

@Composable
private fun SettingsContent(
    contentPadding: PaddingValues,
    settings: UiSettings,
    llmTestState: SettingsLlmTestState,
    overlayPermissionState: UiOverlayPermissionState,
    voiceInputState: UiVoiceInputState,
    hasRecordAudioPermission: Boolean,
    about: UiAboutInfo,
    onApplyPort: (Int) -> Unit,
    onApplyLlm: (UiLlmProvider, String, String, String, Int, Int) -> Unit,
    onTestLlm: (UiLlmProvider, String, String, String, Int, Int) -> Unit,
    onApplySpoiler: (UiSpoilerLevel) -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onRefreshOverlayPermission: () -> Unit,
    onTestOverlay: () -> Unit,
    onOpenMicrophonePermission: () -> Unit,
    onTestMicrophone: () -> Unit,
    onRefreshMicrophonePermission: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenAppQuestionConsole: () -> Unit,
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
        RetroArchSetupSection(port = settings.port)
        OverlayPermissionSection(
            state = overlayPermissionState,
            onOpenSettings = onOpenOverlayPermission,
            onRefresh = onRefreshOverlayPermission,
            onTestOverlay = onTestOverlay,
        )
        MicrophonePermissionSection(
            voiceInputState = voiceInputState,
            hasRecordAudioPermission = hasRecordAudioPermission,
            onOpenPermission = onOpenMicrophonePermission,
            onTestMicrophone = onTestMicrophone,
            onRefresh = onRefreshMicrophonePermission,
        )
        EndpointSection(currentPort = settings.port, onApply = onApplyPort)
        SpoilerSection(level = settings.spoilerLevel, onApply = onApplySpoiler)
        LlmSection(
            settings = settings,
            testState = llmTestState,
            onApply = onApplyLlm,
            onTest = onTestLlm,
        )
        DeveloperDiagnosticsSection(
            onOpenAppQuestionConsole = onOpenAppQuestionConsole,
            onOpenDiagnostics = onOpenDiagnostics,
        )
        AboutSection(about = about)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun OverlayPermissionSection(
    state: UiOverlayPermissionState,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onTestOverlay: () -> Unit,
) {
    SectionCard(title = "助手模式", accent = state.isGranted) {
        Column(
            modifier = Modifier.testTag("settings_overlay_permission_section"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = if (state.isGranted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (state.isGranted) "旁白模式 HUD 可用" else "需要开启显示在其他应用上层",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = if (state.isGranted) onTestOverlay else onOpenSettings,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings_overlay_permission_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.isGranted) "测试游戏内波形" else "打开系统授权")
                }
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings_overlay_permission_refresh_button"),
                ) {
                    Text("刷新状态")
                }
            }
        }
    }
}

@Composable
private fun MicrophonePermissionSection(
    voiceInputState: UiVoiceInputState,
    hasRecordAudioPermission: Boolean,
    onOpenPermission: () -> Unit,
    onTestMicrophone: () -> Unit,
    onRefresh: () -> Unit,
) {
    val ready = hasRecordAudioPermission && voiceInputState.isAvailable
    val detail = when {
        !hasRecordAudioPermission -> "热键呼出后需要一次性收音，请允许麦克风权限。RetroSprite 不会持续监听。"
        !voiceInputState.isAvailable -> voiceInputState.errorMessage ?: "本地语音识别暂不可用，请检查 ASR 模型。"
        else -> "麦克风和本地语音识别可用，热键呼出后可以直接提问。"
    }
    val testLabel = when {
        !hasRecordAudioPermission -> "授权麦克风"
        voiceInputState.isListening -> "停止测试"
        else -> "测试麦克风"
    }
    val testStatus = when {
        voiceInputState.isListening -> "正在听：${voiceInputState.engineLabel}，说一句短问题后可停止测试。"
        !voiceInputState.transcript.isNullOrBlank() -> "最近识别：${voiceInputState.transcript}"
        !voiceInputState.statusMessage.isNullOrBlank() -> voiceInputState.statusMessage
        !voiceInputState.errorMessage.isNullOrBlank() -> voiceInputState.errorMessage
        else -> null
    }
    SectionCard(title = "麦克风与语音识别", accent = ready) {
        Column(
            modifier = Modifier.testTag("settings_microphone_permission_section"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = null,
                    tint = if (ready) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (ready) "麦克风已就绪" else "需要麦克风权限",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = if (hasRecordAudioPermission) onTestMicrophone else onOpenPermission,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings_microphone_permission_button"),
                ) {
                    Text(testLabel)
                }
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings_microphone_permission_refresh_button"),
                ) {
                    Text("刷新状态")
                }
            }
            testStatus?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (voiceInputState.errorMessage == it) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.testTag("settings_microphone_test_status"),
                )
            }
        }
    }
}

@Composable
private fun DeveloperDiagnosticsSection(
    onOpenAppQuestionConsole: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    SectionCard(title = "开发者诊断") {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.testTag("settings_developer_diagnostics_section"),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "请求日志和排错工具",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "普通游玩不需要进入这里；当热键、GKP 或模型返回异常时再查看诊断记录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedButton(
                onClick = onOpenAppQuestionConsole,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_app_question_console_open"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardCommandKey,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("打开 App 内问答")
            }
            OutlinedButton(
                onClick = onOpenDiagnostics,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_developer_diagnostics_open"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.BugReport,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("打开诊断日志")
            }
        }
    }
}

@Composable
private fun EndpointSection(currentPort: Int, onApply: (Int) -> Unit) {
    var portInput by remember(currentPort) { mutableStateOf(currentPort.toString()) }
    SectionCard(title = "连接详情") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "通常不需要修改。只有 RetroArch 里使用了不同端口时，才在这里同步调整本地服务。",
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
                        val p = portInput.toIntOrNull() ?: 4_404
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

@Composable
private fun RetroArchSetupSection(port: Int) {
    val aiServiceUrl = remember(port) { "http://localhost:$port" }
    SectionCard(title = "连接状态") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "RetroArch 快捷键指引",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RetroArchSetupLine("AI Service", "开启")
            RetroArchSetupLine("AI Service URL", aiServiceUrl)
            RetroArchSetupLine("AI Service Output", "旁白模式（Narrator Mode）")
            RetroArchSetupLine(
                "Pause During Translation",
                "RetroArch -> Settings -> AI Service -> Pause During Translation -> ON",
            )
            RetroArchSetupLine("AI Service 快捷键", "在 RetroArch 中确认或绑定")
            CopyToClipboardButton(
                textToCopy = aiServiceUrl,
                label = "复制本地服务地址",
                successMessage = "已复制本地服务地址",
                clipLabel = "RetroArch AI Service URL",
                modifier = Modifier.testTag("settings_retroarch_ai_url_copy"),
            )
        }
    }
}

@Composable
private fun RetroArchSetupLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LlmSection(
    settings: UiSettings,
    testState: SettingsLlmTestState,
    onApply: (UiLlmProvider, String, String, String, Int, Int) -> Unit,
    onTest: (UiLlmProvider, String, String, String, Int, Int) -> Unit,
) {
    var provider by remember(settings.llmProvider) { mutableStateOf(settings.llmProvider) }
    var apiKey by remember(settings.llmApiKey) { mutableStateOf(settings.llmApiKey) }
    var baseUrl by remember(settings.llmBaseUrl, settings.llmProvider) {
        mutableStateOf(settings.llmBaseUrl.ifBlank { settings.llmProvider.defaultBaseUrl })
    }
    var model by remember(settings.llmModel, settings.llmProvider) {
        mutableStateOf(settings.llmModel.ifBlank { settings.llmProvider.defaultModel })
    }
    var timeoutSeconds by remember(settings.llmTimeoutSeconds) {
        mutableStateOf(settings.llmTimeoutSeconds.toString())
    }
    var maxTokens by remember(settings.llmMaxTokens) {
        mutableStateOf(settings.llmMaxTokens.toString())
    }
    var keyVisible by remember { mutableStateOf(false) }
    var dropdownOpen by remember { mutableStateOf(false) }

    SectionCard(title = "高级 AI 配置") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                ExposedDropdownMenu(
                    expanded = dropdownOpen,
                    onDismissRequest = { dropdownOpen = false }
                ) {
                    UiLlmProvider.values().forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.displayName) },
                            onClick = {
                                provider = p
                                if (p != UiLlmProvider.Custom) {
                                    baseUrl = p.defaultBaseUrl
                                    model = p.defaultModel
                                }
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = timeoutSeconds,
                    onValueChange = { value ->
                        timeoutSeconds = value.filter { it.isDigit() }.take(3)
                    },
                    label = { Text("\u8d85\u65f6 (\u79d2)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = retroFieldColors()
                )
                OutlinedTextField(
                    value = maxTokens,
                    onValueChange = { value ->
                        maxTokens = value.filter { it.isDigit() }.take(4)
                    },
                    label = { Text("Max Tokens") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = retroFieldColors()
                )
            }

            val parsedTimeout = timeoutSeconds.toIntOrNull() ?: DEFAULT_LLM_TIMEOUT_SECONDS
            val parsedMaxTokens = maxTokens.toIntOrNull() ?: DEFAULT_LLM_MAX_TOKENS
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onApply(
                            provider,
                            apiKey,
                            baseUrl,
                            model,
                            parsedTimeout,
                            parsedMaxTokens,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("\u4fdd\u5b58\u914d\u7f6e")
                }
                OutlinedButton(
                    onClick = {
                        onTest(
                            provider,
                            apiKey,
                            baseUrl,
                            model,
                            parsedTimeout,
                            parsedMaxTokens,
                        )
                    },
                    enabled = !testState.isRunning,
                    modifier = Modifier.weight(1f).testTag("settings_llm_test_button"),
                ) {
                    if (testState.isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (testState.isRunning) "\u6d4b\u8bd5\u4e2d" else "\u6d4b\u8bd5\u914d\u7f6e")
                }
            }

            LlmTestResultBox(testState)
        }
    }
}

@Composable
private fun LlmTestResultBox(state: SettingsLlmTestState) {
    val result = state.result ?: return
    val borderColor = if (result.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val bgColor = if (result.ok) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    } else {
        MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_llm_test_result")
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (result.ok) "模型连接正常" else "模型连接失败",
                style = MaterialTheme.typography.labelLarge,
                color = borderColor,
            )
            Text(
                text = "\u6a21\u578b\uff1a${result.provider} / ${result.model}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "\u9884\u7b97\uff1a${result.maxTokens} tok \u00b7 timeout ${result.timeoutMs}ms \u00b7 latency ${result.latencyMs}ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (result.tokensIn > 0 || result.tokensOut > 0) {
                Text(
                    text = "Token\uff1ain ${result.tokensIn} / out ${result.tokensOut}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            result.responsePreview?.let {
                Text(
                    text = "\u54cd\u5e94\uff1a$it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            result.errorMessage?.let {
                Text(
                    text = "LLM \u9519\u8bef\uff1a$it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SpoilerSection(level: UiSpoilerLevel, onApply: (UiSpoilerLevel) -> Unit) {
    SectionCard(title = "默认回答明确度") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "作为默认低剧透策略。游玩中也可以直接说“别剧透”“直接告诉我”等自然跟进。",
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
            llmTestState = SettingsLlmTestState(),
            overlayPermissionState = UiOverlayPermissionState(isGranted = false),
            voiceInputState = UiVoiceInputState(isAvailable = true),
            hasRecordAudioPermission = false,
            about = UiAboutInfo(),
            onApplyPort = {},
            onApplyLlm = { _, _, _, _, _, _ -> },
            onTestLlm = { _, _, _, _, _, _ -> },
            onApplySpoiler = {},
            onOpenOverlayPermission = {},
            onRefreshOverlayPermission = {},
            onTestOverlay = {},
            onOpenMicrophonePermission = {},
            onTestMicrophone = {},
            onRefreshMicrophonePermission = {},
            onOpenDiagnostics = {},
            onOpenAppQuestionConsole = {},
        )
    }
}
