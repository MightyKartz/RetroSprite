package com.retrosprite.app.ui.screens.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardCommandKey
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retrosprite.app.ui.components.CopyToClipboardButton
import com.retrosprite.app.ui.components.InfoRow
import com.retrosprite.app.ui.components.SectionCard
import com.retrosprite.app.ui.components.StatusIndicator
import com.retrosprite.app.ui.theme.RetroSpriteTheme
import com.retrosprite.app.ui.viewmodel.UiAnswerFeedback
import com.retrosprite.app.ui.viewmodel.UiEndpointPhase
import com.retrosprite.app.ui.viewmodel.UiEndpointStatus
import com.retrosprite.app.ui.viewmodel.UiPendingQuestion
import com.retrosprite.app.ui.viewmodel.UiPendingQuestionState
import com.retrosprite.app.ui.viewmodel.UiQuestionResult
import com.retrosprite.app.ui.viewmodel.UiSpeechOutputState
import com.retrosprite.app.ui.viewmodel.UiVoiceInputState
import com.retrosprite.app.ui.viewmodel.rememberUiDependencies
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class HomeNavigationTarget {
    Diagnostics,
    Packs,
    Settings,
}

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onNavigateToTarget: (HomeNavigationTarget) -> Unit = {},
) {
    val deps = rememberUiDependencies()
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            deps.endpoint,
            deps.playerQuestion,
            deps.pendingQuestion,
            deps.requestLog,
        )
    )
    val status by viewModel.status.collectAsStateWithLifecycle()
    val askState by viewModel.askState.collectAsStateWithLifecycle()
    val pendingQuestionState by viewModel.pendingQuestionState.collectAsStateWithLifecycle()
    val voiceInputState by deps.voiceInput.state.collectAsStateWithLifecycle()
    val speechOutputState by deps.speechOutput.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var hasRecordAudioPermission by remember(deps.voiceInput.requiresRecordAudioPermission) {
        mutableStateOf(
            !deps.voiceInput.requiresRecordAudioPermission ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var recordAudioPermissionDenied by remember { mutableStateOf(false) }
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasRecordAudioPermission = granted
        recordAudioPermissionDenied = !granted
        if (granted) {
            coroutineScope.launch { deps.voiceInput.startListening() }
        }
    }
    var lastAppliedVoiceEventId by remember { mutableStateOf(0L) }
    LaunchedEffect(voiceInputState.transcriptEventId) {
        val eventId = voiceInputState.transcriptEventId
        val transcript = voiceInputState.transcript?.trim().orEmpty()
        if (eventId > 0L && eventId != lastAppliedVoiceEventId && transcript.isNotEmpty()) {
            lastAppliedVoiceEventId = eventId
            viewModel.updateQuestion(transcript)
        }
    }

    HomeScreenContent(
        contentPadding = contentPadding,
        status = status,
        askState = askState,
        pendingQuestionState = pendingQuestionState,
        voiceInputState = voiceInputState,
        speechOutputState = speechOutputState,
        recordAudioPermissionDenied = recordAudioPermissionDenied,
        onRestart = viewModel::restart,
        onCheckHealth = viewModel::checkHealth,
        onAskLabelChange = viewModel::updateAskLabel,
        onQuestionChange = viewModel::updateQuestion,
        onQuestionDraftSelected = viewModel::applyQuestionDraft,
        onAskQuestion = viewModel::askQuestion,
        onPreparePendingQuestion = viewModel::preparePendingQuestion,
        onClearPendingQuestion = viewModel::clearPendingQuestion,
        onFeedback = viewModel::submitAnswerFeedback,
        onRestoreContext = viewModel::restoreLatestRetroArchContext,
        onConversationTurnSelected = viewModel::applyConversationTurn,
        onFollowUpDraftSelected = viewModel::applyConversationFollowUpDraft,
        onVoiceInputClick = {
            recordAudioPermissionDenied = false
            if (voiceInputState.isListening) {
                coroutineScope.launch { deps.voiceInput.stopListening() }
            } else if (hasRecordAudioPermission) {
                coroutineScope.launch { deps.voiceInput.startListening() }
            } else {
                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onSpeakAnswer = { text ->
            coroutineScope.launch { deps.speechOutput.speak(text) }
        },
        onStopSpeechOutput = {
            coroutineScope.launch { deps.speechOutput.stop() }
        },
        onNavigateToTarget = onNavigateToTarget,
        modifier = modifier
    )
}

@Composable
private fun HomeScreenContent(
    contentPadding: PaddingValues,
    status: UiEndpointStatus,
    askState: HomeAskState,
    pendingQuestionState: UiPendingQuestionState,
    voiceInputState: UiVoiceInputState,
    speechOutputState: UiSpeechOutputState,
    recordAudioPermissionDenied: Boolean,
    onRestart: () -> Unit,
    onCheckHealth: () -> Unit,
    onAskLabelChange: (String) -> Unit,
    onQuestionChange: (String) -> Unit,
    onQuestionDraftSelected: (HomeQuestionDraft) -> Unit,
    onAskQuestion: () -> Unit,
    onPreparePendingQuestion: () -> Unit,
    onClearPendingQuestion: () -> Unit,
    onFeedback: (UiAnswerFeedback) -> Unit,
    onRestoreContext: () -> Unit,
    onConversationTurnSelected: (HomeConversationTurn) -> Unit,
    onFollowUpDraftSelected: (HomeConversationTurn, HomeFollowUpDraft) -> Unit,
    onVoiceInputClick: () -> Unit,
    onSpeakAnswer: (String) -> Unit,
    onStopSpeechOutput: () -> Unit,
    onNavigateToTarget: (HomeNavigationTarget) -> Unit,
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
                    TextQuestionCard(
                        askState = askState,
                        pendingQuestion = pendingQuestionState.pending,
                        voiceInputState = voiceInputState,
                        speechOutputState = speechOutputState,
                        recordAudioPermissionDenied = recordAudioPermissionDenied,
                        onLabelChange = onAskLabelChange,
                        onQuestionChange = onQuestionChange,
                        onQuestionDraftSelected = onQuestionDraftSelected,
                        onAskQuestion = onAskQuestion,
                        onPreparePendingQuestion = onPreparePendingQuestion,
                        onClearPendingQuestion = onClearPendingQuestion,
                        onFeedback = onFeedback,
                        onRestoreContext = onRestoreContext,
                        onConversationTurnSelected = onConversationTurnSelected,
                        onFollowUpDraftSelected = onFollowUpDraftSelected,
                        onVoiceInputClick = onVoiceInputClick,
                        onSpeakAnswer = onSpeakAnswer,
                        onStopSpeechOutput = onStopSpeechOutput,
                        onNavigateToTarget = onNavigateToTarget,
                    )
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
                TextQuestionCard(
                    askState = askState,
                    pendingQuestion = pendingQuestionState.pending,
                    voiceInputState = voiceInputState,
                    speechOutputState = speechOutputState,
                    recordAudioPermissionDenied = recordAudioPermissionDenied,
                    onLabelChange = onAskLabelChange,
                    onQuestionChange = onQuestionChange,
                    onQuestionDraftSelected = onQuestionDraftSelected,
                    onAskQuestion = onAskQuestion,
                    onPreparePendingQuestion = onPreparePendingQuestion,
                    onClearPendingQuestion = onClearPendingQuestion,
                    onFeedback = onFeedback,
                    onRestoreContext = onRestoreContext,
                    onConversationTurnSelected = onConversationTurnSelected,
                    onFollowUpDraftSelected = onFollowUpDraftSelected,
                    onVoiceInputClick = onVoiceInputClick,
                    onSpeakAnswer = onSpeakAnswer,
                    onStopSpeechOutput = onStopSpeechOutput,
                    onNavigateToTarget = onNavigateToTarget,
                )
                HowToConfigureCard(status)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun TextQuestionCard(
    askState: HomeAskState,
    pendingQuestion: UiPendingQuestion?,
    voiceInputState: UiVoiceInputState,
    speechOutputState: UiSpeechOutputState,
    recordAudioPermissionDenied: Boolean,
    onLabelChange: (String) -> Unit,
    onQuestionChange: (String) -> Unit,
    onQuestionDraftSelected: (HomeQuestionDraft) -> Unit,
    onAskQuestion: () -> Unit,
    onPreparePendingQuestion: () -> Unit,
    onClearPendingQuestion: () -> Unit,
    onFeedback: (UiAnswerFeedback) -> Unit,
    onRestoreContext: () -> Unit,
    onConversationTurnSelected: (HomeConversationTurn) -> Unit,
    onFollowUpDraftSelected: (HomeConversationTurn, HomeFollowUpDraft) -> Unit,
    onVoiceInputClick: () -> Unit,
    onSpeakAnswer: (String) -> Unit,
    onStopSpeechOutput: () -> Unit,
    onNavigateToTarget: (HomeNavigationTarget) -> Unit,
) {
    val result = askState.lastResult
    SectionCard(
        title = "\u6587\u5b57\u63d0\u95ee",
        accent = result?.ok == true
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            askState.latestRetroArchContext?.let { context ->
                RetroArchContextBox(
                    context = context,
                    labelManuallyEdited = askState.labelManuallyEdited,
                    onRestoreContext = onRestoreContext,
                )
            }
            InputFlowBox(askState)
            QuestionDraftsBox(
                drafts = askState.questionDrafts,
                enabled = !askState.isAsking,
                onQuestionDraftSelected = onQuestionDraftSelected,
            )
            OutlinedTextField(
                value = askState.label,
                onValueChange = onLabelChange,
                label = { Text("\u6e38\u620f Label") },
                singleLine = true,
                enabled = !askState.isAsking,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().testTag("home_label_input")
            )
            OutlinedTextField(
                value = askState.question,
                onValueChange = onQuestionChange,
                label = { Text("\u95ee\u9898") },
                enabled = !askState.isAsking,
                minLines = 2,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                modifier = Modifier.fillMaxWidth().testTag("home_question_input")
            )
            VoiceControlsBox(
                voiceInputState = voiceInputState,
                speechOutputState = speechOutputState,
                recordAudioPermissionDenied = recordAudioPermissionDenied,
                voiceEnabled = !askState.isAsking,
                answerText = result?.takeIf { it.ok }?.answer.orEmpty(),
                onVoiceInputClick = onVoiceInputClick,
                onSpeakAnswer = onSpeakAnswer,
                onStopSpeechOutput = onStopSpeechOutput,
            )
            askState.spoilerEscalationNotice?.let { notice ->
                SpoilerEscalationBox(notice = notice)
            }
            PendingQuestionBox(
                pendingQuestion = pendingQuestion,
                enabled = !askState.isAsking && askState.question.isNotBlank(),
                onPreparePendingQuestion = onPreparePendingQuestion,
                onClearPendingQuestion = onClearPendingQuestion,
            )
            Button(
                onClick = onAskQuestion,
                enabled = !askState.isAsking && askState.question.isNotBlank(),
                modifier = Modifier.fillMaxWidth().testTag("home_ask_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                if (askState.isAsking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (askState.isAsking) "\u5904\u7406\u4e2d" else "\u63d0\u95ee",
                    style = MaterialTheme.typography.labelLarge
                )
            }
            if (askState.isAsking) {
                ProcessingStatusBox(startedAtMillis = askState.askStartedAtMillis)
            }

            if (result != null) {
                QuestionResultBox(result, onFeedback, onNavigateToTarget)
            } else if (askState.errorMessage != null) {
                Text(
                    text = askState.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            ConversationTrayBox(
                turns = askState.conversationTurns,
                onConversationTurnSelected = onConversationTurnSelected,
                onFollowUpDraftSelected = onFollowUpDraftSelected,
            )
        }
    }
}

@Composable
private fun SpoilerEscalationBox(notice: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_spoiler_escalation_notice")
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.38f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Filled.ReportProblem,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "剧透级别提升",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VoiceControlsBox(
    voiceInputState: UiVoiceInputState,
    speechOutputState: UiSpeechOutputState,
    recordAudioPermissionDenied: Boolean,
    voiceEnabled: Boolean,
    answerText: String,
    onVoiceInputClick: () -> Unit,
    onSpeakAnswer: (String) -> Unit,
    onStopSpeechOutput: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_voice_controls"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onVoiceInputClick,
                enabled = voiceEnabled && voiceInputState.isAvailable,
                modifier = Modifier
                    .weight(1f)
                    .testTag("home_voice_input_button"),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = if (voiceInputState.isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (voiceInputState.isListening) "停止录音" else "语音输入",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            OutlinedButton(
                onClick = {
                    if (speechOutputState.isSpeaking) {
                        onStopSpeechOutput()
                    } else {
                        onSpeakAnswer(answerText)
                    }
                },
                enabled = speechOutputState.isAvailable &&
                    (speechOutputState.isSpeaking || answerText.isNotBlank()),
                modifier = Modifier
                    .weight(1f)
                    .testTag("home_speak_answer_button"),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = if (speechOutputState.isSpeaking) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (speechOutputState.isSpeaking) "停止朗读" else "朗读短答",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Text(
            text = "语音只填充问题框；提交后仍走本地 GKP、低剧透和证据链路。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val voiceStatus = voiceInputState.statusMessage
            ?: if (voiceInputState.isListening) "正在听：${voiceInputState.engineLabel}" else null
        voiceStatus?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("home_voice_status"),
            )
        }
        voiceInputState.transcript?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = "已识别：$it",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("home_voice_transcript"),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val voiceError = when {
            recordAudioPermissionDenied -> "需要麦克风权限才能语音输入"
            !voiceInputState.isAvailable -> voiceInputState.errorMessage ?: "系统语音识别不可用"
            else -> voiceInputState.errorMessage
        }
        voiceError?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("home_voice_error"),
            )
        }
        if (speechOutputState.isSpeaking) {
            Text(
                text = "正在朗读短答案",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("home_tts_status"),
            )
        }
        speechOutputState.errorMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("home_tts_error"),
            )
        }
    }
}

@Composable
private fun PendingQuestionBox(
    pendingQuestion: UiPendingQuestion?,
    enabled: Boolean,
    onPreparePendingQuestion: () -> Unit,
    onClearPendingQuestion: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_pending_question_box"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onPreparePendingQuestion,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_prepare_hotkey_question_button"),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardCommandKey,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (pendingQuestion == null) "准备给下次热键" else "更新热键问题",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        pendingQuestion?.let { pending ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_pending_question_card")
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "已准备给下次 RetroArch 热键",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ContextTag(text = pending.label)
                                ContextTag(text = pending.spoilerLevel.displayName)
                            }
                        }
                        OutlinedButton(
                            onClick = onClearPendingQuestion,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("home_pending_question_clear_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "取消",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    Text(
                        text = pending.question,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestionDraftsBox(
    drafts: List<HomeQuestionDraft>,
    enabled: Boolean,
    onQuestionDraftSelected: (HomeQuestionDraft) -> Unit,
) {
    if (drafts.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_question_drafts"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "快捷问题草稿",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "点选只会填入问题框；提交仍会经过本地 GKP 和低剧透策略。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        drafts.forEachIndexed { index, draft ->
            OutlinedButton(
                onClick = { onQuestionDraftSelected(draft) },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_question_draft_$index"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = draft.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = draft.question,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationTrayBox(
    turns: List<HomeConversationTurn>,
    onConversationTurnSelected: (HomeConversationTurn) -> Unit,
    onFollowUpDraftSelected: (HomeConversationTurn, HomeFollowUpDraft) -> Unit,
) {
    if (turns.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_conversation_tray"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "最近问答",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        turns.forEachIndexed { index, turn ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { onConversationTurnSelected(turn) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_conversation_turn_$index"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ContextTag(text = turn.label)
                            ContextTag(text = turn.statusLabel)
                        }
                        Text(
                            text = turn.question,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = turn.answerPreview.ifBlank { "无回答内容" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (turn.result.sourceIds.isNotEmpty()) {
                            Text(
                                text = "来源：${turn.result.sourceIds.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    turn.followUpDrafts.forEach { draft ->
                        OutlinedButton(
                            onClick = { onFollowUpDraftSelected(turn, draft) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_conversation_followup_${index}_${draft.id}"),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = draft.title,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessingStatusBox(startedAtMillis: Long?) {
    var now by remember(startedAtMillis) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMillis) {
        while (startedAtMillis != null) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val elapsedSeconds = startedAtMillis
        ?.let { ((now - it).coerceAtLeast(0L) / 1_000L).toInt() }
        ?: 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_processing_status")
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "处理中 · ${elapsedSeconds}s",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "先查本地 GKP；只有证据需要综合时才会调用 LLM。若模型超时，会在这里显示错误并写入 Diagnostics。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InputFlowBox(askState: HomeAskState) {
    val contextSource = if (askState.latestRetroArchContext != null) {
        "上下文：RetroArch"
    } else {
        "上下文：默认样例"
    }
    val labelSource = when {
        askState.labelManuallyEdited -> "Label：手动覆盖"
        askState.latestRetroArchContext != null -> "Label：跟随热键"
        else -> "Label：默认样例"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_input_flow_note")
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "输入来源",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "上下文来自最近 RetroArch 请求；问题来自当前输入框。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ContextTag(text = contextSource)
                    ContextTag(text = "问题：App 内输入")
                }
                ContextTag(text = labelSource)
            }
        }
    }
}

@Composable
private fun RetroArchContextBox(
    context: HomeRetroArchContext,
    labelManuallyEdited: Boolean,
    onRestoreContext: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "\u6700\u8fd1 RETROARCH",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = context.label,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (labelManuallyEdited) {
                    OutlinedButton(
                        onClick = onRestoreContext,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "\u6062\u590d",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ContextTag(text = relTime(context.timestampMillis))
                ContextTag(text = if (context.paused) "\u5df2\u6682\u505c" else "\u8fd0\u884c\u4e2d")
                ContextTag(text = context.gkpStatusLabel)
                context.questionSource?.let {
                    ContextTag(text = it.displayNameForQuestionSource())
                }
            }
            context.question?.let {
                Text(
                    text = "热键问题：$it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("home_context_question")
                )
            }
            RetroArchContextActionBar(
                context = context,
                labelManuallyEdited = labelManuallyEdited,
                onRestoreContext = onRestoreContext,
            )
            if (context.isGkpDisabled) {
                Text(
                    text = "\u5bf9\u5e94\u77e5\u8bc6\u5305\u5df2\u7981\u7528\uff1b\u5b83\u4e0d\u4f1a\u53c2\u4e0e\u95ee\u7b54\u6216\u8c03\u7528 LLM\uff0c\u53ef\u5728 Packs \u4e2d\u91cd\u65b0\u542f\u7528\u3002",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("home_gkp_disabled_notice")
                )
            }
            if (context.sourceIds.isNotEmpty()) {
                Text(
                    text = "\u6765\u6e90\uff1a${context.sourceIds.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RetroArchContextActionBar(
    context: HomeRetroArchContext,
    labelManuallyEdited: Boolean,
    onRestoreContext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onRestoreContext,
            enabled = labelManuallyEdited,
            modifier = Modifier
                .weight(1f)
                .testTag("home_context_use_button"),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Restore,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (labelManuallyEdited) "使用此上下文" else "正在使用",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        CopyToClipboardButton(
            textToCopy = context.debugAskCurl,
            label = "复制 debug curl",
            clipLabel = "RetroSprite debug ask",
            successMessage = "已复制 debug curl",
            modifier = Modifier
                .weight(1f)
                .testTag("home_context_copy_debug_curl")
        )
    }
}

@Composable
private fun ContextTag(text: String) {
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
private fun QuestionResultBox(
    result: UiQuestionResult,
    onFeedback: (UiAnswerFeedback) -> Unit,
    onNavigateToTarget: (HomeNavigationTarget) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_question_result")
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = if (result.ok) "ANSWER" else "ERROR",
                style = MaterialTheme.typography.labelSmall,
                color = if (result.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            SelectionContainer {
                Text(
                    text = if (result.ok) result.answer else result.errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = result.pipelineStage.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "LLM ${result.llmStatus.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ResultDiagnostics(result)
            RecoveryHintBox(
                result = result,
                onNavigateToTarget = onNavigateToTarget,
            )
            if (result.sourceIds.isNotEmpty()) {
                Text(
                    text = "\u6765\u6e90\uff1a${result.sourceIds.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (result.ok && result.requestLogId != null) {
                AnswerFeedbackRow(
                    selected = result.feedback,
                    onFeedback = onFeedback,
                )
            }
        }
    }
}

@Composable
private fun RecoveryHintBox(
    result: UiQuestionResult,
    onNavigateToTarget: (HomeNavigationTarget) -> Unit,
) {
    val hint = result.recoveryHint() ?: return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_recovery_hint")
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = hint.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = hint.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { onNavigateToTarget(hint.target) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_recovery_action"),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.OpenInBrowser,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = hint.actionLabel,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun AnswerFeedbackRow(
    selected: UiAnswerFeedback?,
    onFeedback: (UiAnswerFeedback) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeedbackButton(
                feedback = UiAnswerFeedback.Helpful,
                selected = selected == UiAnswerFeedback.Helpful,
                onClick = onFeedback,
            )
            FeedbackButton(
                feedback = UiAnswerFeedback.Incorrect,
                selected = selected == UiAnswerFeedback.Incorrect,
                onClick = onFeedback,
            )
        }
        selected?.let {
            Text(
                text = "已记录：${it.displayName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("home_feedback_status")
            )
        }
    }
}

private data class RecoveryHint(
    val title: String,
    val detail: String,
    val actionLabel: String,
    val target: HomeNavigationTarget,
)

private fun UiQuestionResult.recoveryHint(): RecoveryHint? {
    val stage = pipelineStage.lowercase()
    val llm = llmStatus.lowercase()
    val text = listOf(answer, errorMessage.orEmpty(), llmError.orEmpty()).joinToString(" ")
    return when {
        !ok || stage == "error" -> RecoveryHint(
            title = "下一步：查看诊断详情",
            detail = "打开 Diagnostics 对应记录，确认请求、label、provider 和错误信息；修复后再提交同一个问题。",
            actionLabel = "打开 Diagnostics",
            target = HomeNavigationTarget.Diagnostics,
        )

        stage == "gkp_disabled" || text.contains("知识包已禁用") -> RecoveryHint(
            title = "下一步：重新启用 GKP",
            detail = "到 Packs 找到当前游戏知识包并点击启用；禁用状态下不会检索本地知识，也不会调用 LLM。",
            actionLabel = "打开 Packs",
            target = HomeNavigationTarget.Packs,
        )

        llm == "failed" -> RecoveryHint(
            title = "下一步：检查 LLM 配置",
            detail = "到 Settings 运行 LLM 自检，确认 API key、provider/model、timeout 和 max token；本地 evidence 仍会保留在 Diagnostics。",
            actionLabel = "打开 Settings",
            target = HomeNavigationTarget.Settings,
        )

        stage == "no_evidence" || text.contains("没有足够证据") || text.contains("暂时不能给可靠答案") -> RecoveryHint(
            title = "下一步：补充上下文",
            detail = "换成更具体的问题，补充当前位置、版本或目标；如果当前游戏没有 GKP，请先安装或启用对应知识包。",
            actionLabel = "打开 Packs",
            target = HomeNavigationTarget.Packs,
        )

        stage == "unknown" && sourceIds.isEmpty() -> RecoveryHint(
            title = "下一步：确认游戏识别",
            detail = "检查 Home 的 label 是否来自最近 RetroArch 热键；必要时手动改 label，或在 Packs 安装对应 GKP。",
            actionLabel = "打开 Packs",
            target = HomeNavigationTarget.Packs,
        )

        else -> null
    }
}

@Composable
private fun RowScope.FeedbackButton(
    feedback: UiAnswerFeedback,
    selected: Boolean,
    onClick: (UiAnswerFeedback) -> Unit,
) {
    val tag = when (feedback) {
        UiAnswerFeedback.Helpful -> "home_feedback_helpful"
        UiAnswerFeedback.Incorrect -> "home_feedback_incorrect"
    }
    val icon = when (feedback) {
        UiAnswerFeedback.Helpful -> Icons.Filled.ThumbUp
        UiAnswerFeedback.Incorrect -> Icons.Filled.ReportProblem
    }
    val content: @Composable () -> Unit = {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(text = feedback.displayName, style = MaterialTheme.typography.labelLarge)
    }
    if (selected) {
        Button(
            onClick = { onClick(feedback) },
            modifier = Modifier.weight(1f).testTag(tag),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            content()
        }
    } else {
        OutlinedButton(
            onClick = { onClick(feedback) },
            modifier = Modifier.weight(1f).testTag(tag),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun ResultDiagnostics(result: UiQuestionResult) {
    val llmLabel = listOfNotNull(
        result.llmProvider,
        result.llmModel,
    ).joinToString(" / ").ifBlank { null }
    val budget = listOfNotNull(
        result.llmMaxTokens?.let { "$it tok" },
        result.llmTimeoutMs?.let { "timeout ${it}ms" },
        result.llmLatencyMs?.let { "latency ${it}ms" },
    ).joinToString(" · ")
    if (result.durationMillis <= 0L && llmLabel == null && budget.isBlank() && result.llmError == null) {
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "耗时：${result.durationMillis} ms",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        llmLabel?.let {
            Text(
                text = "模型：$it",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (budget.isNotBlank()) {
            Text(
                text = "预算：$budget",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        result.llmError?.let {
            Text(
                text = "LLM 错误：$it",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
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
                detail = "进入 Settings → Accessibility → AI Service，打开 \"AI Service Enable\"。"
            )
            Step(
                index = 2,
                title = "\u586b\u5165\u672c\u5730\u7aef\u70b9",
                detail = "将 AI Service URL 设为「${status.baseUrl}」，模式选择 Image。"
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

private fun String.displayNameForQuestionSource(): String = when (this) {
    "app" -> "App 提问"
    "debug" -> "Debug ask"
    "pending_hotkey" -> "Pending hotkey"
    "retroarch" -> "RetroArch 提问"
    else -> this
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
                port = 4_404,
                baseUrl = "http://localhost:4404",
                message = "0 \u4e2a\u8bf7\u6c42\u5728\u6392\u961f",
                lastHealthCheckMillis = System.currentTimeMillis() - 12_000,
                lastHealthOk = true
            ),
            askState = HomeAskState(
                latestRetroArchContext = HomeRetroArchContext(
                    label = "2048__",
                    timestampMillis = System.currentTimeMillis() - 12_000,
                    paused = true,
                    pipelineStage = "evidence",
                    sourceIds = listOf("sample.2048.rules"),
                ),
                lastResult = UiQuestionResult(
                    requestLogId = "preview-question",
                    label = "2048__",
                    question = "\u4e24\u4e2a 2 \u600e\u4e48\u5408\u5e76\uff1f",
                    answer = "\u628a\u4e24\u4e2a\u76f8\u540c\u6570\u5b57\u6ed1\u5230\u540c\u4e00\u65b9\u5411\u76f8\u90bb\u4f4d\u7f6e\uff0c\u5b83\u4eec\u4f1a\u5408\u6210\u4e00\u4e2a\u7ffb\u500d\u65b9\u5757\u3002",
                    ok = true,
                    timestampMillis = System.currentTimeMillis(),
                    sourceIds = listOf("sample.2048.rules"),
                    pipelineStage = "evidence",
                    llmStatus = "skipped",
                )
            ),
            pendingQuestionState = UiPendingQuestionState(),
            voiceInputState = UiVoiceInputState(engineLabel = "预览语音"),
            speechOutputState = UiSpeechOutputState(isAvailable = true, isReady = true),
            recordAudioPermissionDenied = false,
            onRestart = {},
            onCheckHealth = {},
            onAskLabelChange = {},
            onQuestionChange = {},
            onQuestionDraftSelected = {},
            onAskQuestion = {},
            onPreparePendingQuestion = {},
            onClearPendingQuestion = {},
            onFeedback = {},
            onRestoreContext = {},
            onConversationTurnSelected = {},
            onFollowUpDraftSelected = { _, _ -> },
            onVoiceInputClick = {},
            onSpeakAnswer = {},
            onStopSpeechOutput = {},
            onNavigateToTarget = {},
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
                port = 4_404,
                baseUrl = "http://localhost:4404",
                message = "\u7aef\u53e3\u88ab\u5176\u4ed6\u8fdb\u7a0b\u5360\u7528"
            ),
            askState = HomeAskState(),
            pendingQuestionState = UiPendingQuestionState(),
            voiceInputState = UiVoiceInputState(engineLabel = "预览语音"),
            speechOutputState = UiSpeechOutputState(isAvailable = true, isReady = true),
            recordAudioPermissionDenied = false,
            onRestart = {},
            onCheckHealth = {},
            onAskLabelChange = {},
            onQuestionChange = {},
            onQuestionDraftSelected = {},
            onAskQuestion = {},
            onPreparePendingQuestion = {},
            onClearPendingQuestion = {},
            onFeedback = {},
            onRestoreContext = {},
            onConversationTurnSelected = {},
            onFollowUpDraftSelected = { _, _ -> },
            onVoiceInputClick = {},
            onSpeakAnswer = {},
            onStopSpeechOutput = {},
            onNavigateToTarget = {},
        )
    }
}
