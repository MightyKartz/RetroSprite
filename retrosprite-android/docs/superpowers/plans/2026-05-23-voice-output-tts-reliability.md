# Voice Output TTS Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make RetroSprite's voice output path inspectable, user-testable, and ready for a later local TTS provider while keeping Android `TextToSpeech` as the default short-answer engine.

**Architecture:** Keep `SpeechOutputProvider` as the stable UI/domain boundary. First harden the current Android `TextToSpeech` provider and its call sites, then expose TTS readiness and a manual test in Settings, then document the local sherpa-onnx TTS path as a separate provider spike without changing the ASR or evidence-gated answer pipeline.

**Tech Stack:** Kotlin, Android `TextToSpeech`, Jetpack Compose, `StateFlow`, JVM unit tests, Compose instrumentation smoke tests, RG 476H true-device validation, optional external sherpa-onnx TTS Engine APK validation.

---

## Current State

RetroSprite currently creates one app-wide `AndroidSpeechOutputProvider` in `ServiceLocator` and passes it to:

- Home screen manual "朗读短答" button.
- Hotkey voice overlay `HotkeyVoiceQuestionController`.

The provider wraps Android system `TextToSpeech`, sets `Locale.getDefault()`, strips sources via `shortSpeechAnswer()`, reads only the first sentence or first 160 characters, and tracks `UiSpeechOutputState`.

The current product decision remains:

- Keep Android system `TextToSpeech` as the default implementation.
- Do not treat the sherpa-onnx ASR model as a TTS model.
- Do not implement RetroArch `output=sound` yet.
- Verify local neural TTS first through the system TTS-engine route, then decide whether an app-bundled provider is worth the APK size and runtime cost.

## File Structure

Modify:

- `app/src/main/kotlin/com/retrosprite/app/ui/integration/AndroidSpeechOutputProvider.kt`
  - Owns Android `TextToSpeech` initialization, short answer extraction, `speak()`, `stop()`, and `UiSpeechOutputState` updates.

- `app/src/main/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionController.kt`
  - Owns hotkey voice answer speech policy and the wait-for-speech lifecycle.

- `app/src/main/kotlin/com/retrosprite/app/ui/screens/settings/SettingsScreen.kt`
  - Adds a TTS status/test section next to overlay and microphone diagnostics.

- `app/src/main/kotlin/com/retrosprite/app/ui/viewmodel/UiContracts.kt`
  - Keeps the `SpeechOutputProvider` interface stable; add fields only if the Settings UI needs engine metadata.

- `app/src/test/kotlin/com/retrosprite/app/ui/integration/SpeechOutputTextTest.kt`
  - Unit tests for source stripping, first-sentence behavior, truncation, and no-evidence short-answer text.

- `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionControllerTest.kt`
  - Contract tests for which answer text is passed to `SpeechOutputProvider`.

- `app/src/androidTest/kotlin/com/retrosprite/app/ui/RetroSpriteAppSmokeTest.kt`
  - Compose smoke coverage for the new Settings TTS section.

- `docs/NEXT_IMPLEMENTATION_PLAN.md`
  - Record the Android TTS default, system-engine local TTS validation path, and app-bundled provider decision gate.

Create:

- `docs/TTS_PROVIDER_SPIKE.md`
  - Technical spike document for a future `SherpaOnnxTtsSpeechOutputProvider`.

---

### Task 1: Lock Current Short-Speech Contract

**Files:**
- Modify: `app/src/test/kotlin/com/retrosprite/app/ui/integration/SpeechOutputTextTest.kt`
- Modify: `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionControllerTest.kt`

- [ ] **Step 1: Add short-answer text coverage**

Append these tests to `SpeechOutputTextTest`:

```kotlin
    @Test
    fun `shortSpeechAnswer keeps no evidence fallback concise`() {
        val answer = """
            我还没有足够证据回答这个问题。
            你可以这样问：
            · 哪些角色适合培养？
            · 队伍怎么搭配？
        """.trimIndent()

        assertEquals("我还没有足够证据回答这个问题", answer.shortSpeechAnswer())
    }

    @Test
    fun `shortSpeechAnswer removes source section before sentence split`() {
        val answer = "角色至少 20 级才能转职。\n来源：sf2.promotion。更多来源文字不应朗读。"

        assertEquals("角色至少 20 级才能转职", answer.shortSpeechAnswer())
    }

    @Test
    fun `shortSpeechAnswer ignores blank source-only text`() {
        val answer = "来源：sf2.promotion"

        assertEquals("", answer.shortSpeechAnswer())
    }
```

- [ ] **Step 2: Run the text tests**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest --tests com.retrosprite.app.ui.integration.SpeechOutputTextTest
```

Expected: all tests pass. If the source-only case fails, update `shortSpeechAnswer()` so it returns blank after removing `来源：...`.

- [ ] **Step 3: Add hotkey speech policy coverage**

In `HotkeyVoiceQuestionControllerTest`, add this test:

```kotlin
    @Test
    fun `hotkey voice speaks answerShort instead of detailed no evidence suggestions`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val speech = FakeSpeechOutputProvider()
        val noEvidenceDetail = """
            我还没有足够证据回答这个问题。
            你可以这样问：
            · 哪些角色适合培养？
            · 队伍怎么搭配？
        """.trimIndent()
        val controller = HotkeyVoiceQuestionController(
            coordinator = coordinator,
            voiceInput = FakeVoiceInputProvider("这个人物厉害吗？"),
            responseGenerator = CapturingGenerator(
                answer = noEvidenceDetail,
                diagnostics = ResponseDiagnostics(
                    answerShort = "我还没有足够证据回答这个问题。",
                    answerDetail = noEvidenceDetail,
                    answerType = "no_evidence",
                    llmStatus = "skipped",
                ),
            ),
            speechOutput = speech,
            loggerProvider = { RequestLogger() },
            scope = this,
        )

        controller.onHotkey(event())
        advanceUntilIdle()

        assertEquals(listOf("我还没有足够证据回答这个问题。"), speech.spoken)
        val noEvidenceState = renderer.renderedStates.last {
            it.phase == HotkeyVoiceOverlayPhase.NoEvidence
        }
        assertEquals(noEvidenceDetail, noEvidenceState.answerText)
    }
```

- [ ] **Step 4: Run hotkey speech policy tests**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest --tests com.retrosprite.app.ui.overlay.HotkeyVoiceQuestionControllerTest
```

Expected: pass. The overlay may show detailed suggested questions, but TTS must read only `answerShort`.

- [ ] **Step 5: Commit the test lock**

```bash
git add \
  app/src/test/kotlin/com/retrosprite/app/ui/integration/SpeechOutputTextTest.kt \
  app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionControllerTest.kt
git commit -m "test: lock short answer tts contract"
```

---

### Task 2: Harden AndroidSpeechOutputProvider State Handling

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/integration/AndroidSpeechOutputProvider.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/ui/integration/SpeechOutputTextTest.kt`

- [ ] **Step 1: Make `shortSpeechAnswer` return blank for source-only text**

Update `shortSpeechAnswer()` so `withoutSources` is checked before sentence extraction:

```kotlin
internal fun String.shortSpeechAnswer(maxChars: Int = 160): String {
    val compact = lineSequence()
        .joinToString(" ") { it.trim() }
        .replace(Regex("\\s+"), " ")
        .trim()
    if (compact.isBlank()) return ""
    val withoutSources = compact.substringBefore("来源：").trim()
    if (withoutSources.isBlank()) return ""
    val firstSentence = withoutSources
        .splitToSequence('。', '！', '？', '.', '!', '?')
        .firstOrNull()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: withoutSources
    return if (firstSentence.length <= maxChars) {
        firstSentence
    } else {
        firstSentence.take(maxChars - 1) + "…"
    }
}
```

- [ ] **Step 2: Run short-speech tests**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest --tests com.retrosprite.app.ui.integration.SpeechOutputTextTest
```

Expected: pass.

- [ ] **Step 3: Keep Android TTS failures non-fatal**

Confirm `AndroidSpeechOutputProvider.speak()` keeps this behavior:

```kotlin
if (!_state.value.isReady) {
    _state.update { it.copy(errorMessage = "TTS 尚未准备好") }
    return
}
```

If this block has changed, restore it so failed TTS readiness does not block the text answer or overlay lifecycle.

- [ ] **Step 4: Keep `QUEUE_FLUSH` semantics**

Confirm `tts.speak()` still uses:

```kotlin
TextToSpeech.QUEUE_FLUSH
```

This keeps repeated hotkey answers from queueing stale speech after the player has moved on.

- [ ] **Step 5: Commit provider hardening**

```bash
git add app/src/main/kotlin/com/retrosprite/app/ui/integration/AndroidSpeechOutputProvider.kt
git commit -m "fix: harden short speech extraction"
```

---

### Task 3: Add Settings TTS Status And Test Button

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/screens/settings/SettingsScreen.kt`
- Modify: `app/src/androidTest/kotlin/com/retrosprite/app/ui/RetroSpriteAppSmokeTest.kt`

- [ ] **Step 1: Collect `speechOutputState` in Settings**

In `SettingsScreen`, collect the state beside the existing voice input state:

```kotlin
val speechOutputState by deps.speechOutput.state.collectAsStateWithLifecycle()
```

Pass it into `SettingsContent`:

```kotlin
speechOutputState = speechOutputState,
```

- [ ] **Step 2: Extend `SettingsContent` parameters**

Add these parameters after `voiceInputState`:

```kotlin
speechOutputState: UiSpeechOutputState,
onTestSpeechOutput: () -> Unit,
onStopSpeechOutput: () -> Unit,
```

Import `UiSpeechOutputState` if it is not already imported:

```kotlin
import com.retrosprite.app.ui.viewmodel.UiSpeechOutputState
```

- [ ] **Step 3: Wire test and stop actions**

In the top-level `SettingsScreen` call to `SettingsContent`, add:

```kotlin
onTestSpeechOutput = {
    coroutineScope.launch {
        deps.speechOutput.speak("RetroSprite 语音输出测试。")
    }
},
onStopSpeechOutput = {
    coroutineScope.launch {
        deps.speechOutput.stop()
    }
},
```

- [ ] **Step 4: Insert `SpeechOutputSection` below `MicrophonePermissionSection`**

In `SettingsContent`, after `MicrophonePermissionSection(...)`, add:

```kotlin
SpeechOutputSection(
    speechOutputState = speechOutputState,
    onTestSpeechOutput = onTestSpeechOutput,
    onStopSpeechOutput = onStopSpeechOutput,
)
```

- [ ] **Step 5: Add the Compose section**

Add this composable near `MicrophonePermissionSection`:

```kotlin
@Composable
private fun SpeechOutputSection(
    speechOutputState: UiSpeechOutputState,
    onTestSpeechOutput: () -> Unit,
    onStopSpeechOutput: () -> Unit,
) {
    val ready = speechOutputState.isAvailable && speechOutputState.isReady
    val detail = when {
        speechOutputState.isSpeaking -> "正在朗读短答案。热键问答会自动朗读 evidence-gated 短答。"
        ready -> "系统 TTS 可用。当前实现使用 Android TextToSpeech；如安装离线 TTS 引擎，会通过系统引擎间接受益。"
        !speechOutputState.isAvailable -> speechOutputState.errorMessage ?: "系统 TTS 不可用。"
        else -> speechOutputState.errorMessage ?: "系统 TTS 正在初始化。"
    }
    val buttonLabel = if (speechOutputState.isSpeaking) "停止朗读" else "测试朗读"
    SectionCard(title = "语音输出", accent = ready) {
        Column(
            modifier = Modifier.testTag("settings_speech_output_section"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
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
                        text = if (ready) "短答案朗读可用" else "语音输出未就绪",
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
                    onClick = if (speechOutputState.isSpeaking) {
                        onStopSpeechOutput
                    } else {
                        onTestSpeechOutput
                    },
                    enabled = speechOutputState.isAvailable,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings_speech_output_test_button"),
                ) {
                    Text(buttonLabel)
                }
            }
            speechOutputState.spokenText?.takeIf { it.isNotBlank() }?.let { spoken ->
                Text(
                    text = "最近朗读：$spoken",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("settings_speech_output_spoken_text"),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            speechOutputState.errorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("settings_speech_output_error"),
                )
            }
        }
    }
}
```

- [ ] **Step 6: Add instrumentation smoke coverage**

In `RetroSpriteAppSmokeTest`, add a settings smoke assertion in the existing settings test or a new test:

```kotlin
composeRule.onNodeWithTag("settings_speech_output_section").assertIsDisplayed()
composeRule.onNodeWithText("语音输出").assertIsDisplayed()
composeRule.onNodeWithTag("settings_speech_output_test_button").assertIsDisplayed()
```

- [ ] **Step 7: Run Settings smoke tests**

Run the smallest available instrumentation target that covers Settings. If a narrow test name exists, use it; otherwise run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.retrosprite.app.ui.RetroSpriteAppSmokeTest
```

Expected: Settings screen shows `语音输出` and the test button.

- [ ] **Step 8: Commit Settings TTS diagnostics**

```bash
git add \
  app/src/main/kotlin/com/retrosprite/app/ui/screens/settings/SettingsScreen.kt \
  app/src/androidTest/kotlin/com/retrosprite/app/ui/RetroSpriteAppSmokeTest.kt
git commit -m "feat: add tts diagnostics to settings"
```

---

### Task 4: Make Hotkey Overlay Speech Readiness Explicit

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionController.kt`
- Modify: `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionControllerTest.kt`

- [ ] **Step 1: Add fake provider configurability**

Change `FakeSpeechOutputProvider` in `HotkeyVoiceQuestionControllerTest` to accept initial state:

```kotlin
private class FakeSpeechOutputProvider(
    initialState: UiSpeechOutputState = UiSpeechOutputState(isAvailable = true, isReady = true),
) : SpeechOutputProvider {
    override val state = MutableStateFlow(initialState)
    val spoken = mutableListOf<String>()

    override suspend fun speak(text: String) {
        spoken += text
        state.value = state.value.copy(isSpeaking = false, spokenText = text)
    }

    override suspend fun stop() = Unit
}
```

- [ ] **Step 2: Add TTS-not-ready behavior test**

Add this test:

```kotlin
    @Test
    fun `hotkey voice keeps answer visible when tts is not ready`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val speech = FakeSpeechOutputProvider(
            UiSpeechOutputState(isAvailable = true, isReady = false, errorMessage = "TTS 尚未准备好")
        )
        val controller = HotkeyVoiceQuestionController(
            coordinator = coordinator,
            voiceInput = FakeVoiceInputProvider("什么时候转职？"),
            responseGenerator = CapturingGenerator("角色至少 20 级才能转职。\n来源：sf2.promotion"),
            speechOutput = speech,
            loggerProvider = { RequestLogger() },
            scope = this,
        )

        controller.onHotkey(event())
        advanceUntilIdle()

        assertEquals(emptyList<String>(), speech.spoken)
        val speakingState = renderer.renderedStates.last {
            it.phase == HotkeyVoiceOverlayPhase.Speaking
        }
        assertEquals("角色至少 20 级才能转职。", speakingState.answerText)
    }
```

- [ ] **Step 3: Change `speakIfPossible` to check readiness before speaking**

Update `HotkeyVoiceQuestionController.speakIfPossible(text: String)`:

```kotlin
    private suspend fun speakIfPossible(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val speechState = speechOutput.state.value
        if (!speechState.isAvailable || !speechState.isReady) return
        speechOutput.speak(clean)
        withTimeoutOrNull(SPEECH_TIMEOUT_MS) {
            speechOutput.state
                .filter { !it.isSpeaking }
                .first()
        }
    }
```

- [ ] **Step 4: Run overlay speech tests**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest --tests com.retrosprite.app.ui.overlay.HotkeyVoiceQuestionControllerTest
```

Expected: pass. Hotkey voice should still show the answer card even when TTS is not ready.

- [ ] **Step 5: Commit overlay speech readiness**

```bash
git add \
  app/src/main/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionController.kt \
  app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionControllerTest.kt
git commit -m "fix: keep hotkey answer visible when tts is unavailable"
```

---

### Task 5: Document Local TTS Validation Path

**Files:**
- Create: `docs/TTS_PROVIDER_SPIKE.md`
- Modify: `docs/NEXT_IMPLEMENTATION_PLAN.md`

- [ ] **Step 1: Create the TTS provider spike document**

Create `docs/TTS_PROVIDER_SPIKE.md`:

```markdown
# TTS Provider Spike

## Current Default

RetroSprite uses Android `TextToSpeech` through `AndroidSpeechOutputProvider`.

The provider reads only short answers:

- Prefer `answerShort`.
- Remove `来源：...`.
- Read the first sentence.
- Keep long detail text in HUD only.

## Near-Term Validation: System TTS Engine

Install a local offline Android TTS engine, such as sherpa-onnx TTS Engine APK, and set it as the Android default TTS engine.

RetroSprite should not need app code changes for this validation because `AndroidSpeechOutputProvider` calls the system `TextToSpeech` API.

Validation checklist:

- Settings `语音输出` section shows TTS ready.
- Press `测试朗读`.
- Disable network and repeat `测试朗读`.
- Trigger RetroArch hotkey voice.
- Confirm short answer is spoken without network.
- Confirm Home `朗读短答` still works.

## App-Bundled Provider Decision Gate

Only build `SherpaOnnxTtsSpeechOutputProvider` if the system-engine validation proves meaningfully better than device default TTS.

Before implementation, confirm:

- Android AAR or JNI API exposes TTS synthesis.
- Chinese voice quality is acceptable on RG 476H speakers.
- Model and native libraries keep APK size within release target.
- Cold start is short enough for hotkey flow.
- PCM playback through `AudioTrack` can be stopped immediately.
- The provider reports readiness and errors through `SpeechOutputProvider`.
- No ASR model is reused as TTS.

## Proposed App-Bundled Shape

```text
SpeechOutputProvider
  -> AndroidSpeechOutputProvider       (default)
  -> SherpaOnnxTtsSpeechOutputProvider (future optional)
       -> model assets
       -> native synthesis wrapper
       -> AudioTrack PCM playback
```

## Non-Goals

- No cloud TTS.
- No RetroArch `output=sound` response for this milestone.
- No speech-to-speech pipeline.
- No answer generation from audio without text/evidence gates.
```

- [ ] **Step 2: Update `NEXT_IMPLEMENTATION_PLAN.md` with the plan reference**

Under the existing M11 TTS notes, add:

```markdown
- `docs/TTS_PROVIDER_SPIKE.md` records the concrete TTS provider path: Android `TextToSpeech` remains default, sherpa-onnx TTS Engine APK is the first offline validation route, and an app-bundled `SherpaOnnxTtsSpeechOutputProvider` requires separate model/API/PCM/playback validation before implementation.
```

- [ ] **Step 3: Run markdown grep checks**

Run:

```bash
rg -n "TTS_PROVIDER_SPIKE|SherpaOnnxTtsSpeechOutputProvider|TextToSpeech remains default|Android `TextToSpeech` remains default" docs
```

Expected: references appear in `docs/TTS_PROVIDER_SPIKE.md` and `docs/NEXT_IMPLEMENTATION_PLAN.md`.

- [ ] **Step 4: Commit TTS documentation**

```bash
git add docs/TTS_PROVIDER_SPIKE.md docs/NEXT_IMPLEMENTATION_PLAN.md
git commit -m "docs: define tts provider validation path"
```

---

### Task 6: True-Device Validation On RG 476H

**Files:**
- Modify: `docs/NEXT_IMPLEMENTATION_PLAN.md`

- [ ] **Step 1: Install the debug APK**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.retrosprite.app/.MainActivity
```

Expected:

```text
BUILD SUCCESSFUL
Success
Starting: Intent { cmp=com.retrosprite.app/.MainActivity }
```

- [ ] **Step 2: Verify endpoint health**

Run:

```bash
adb forward tcp:14404 tcp:4404 >/dev/null
curl -s http://127.0.0.1:14404/health
```

Expected:

```json
{"status":"ok","version":"0.1.0"}
```

- [ ] **Step 3: Settings TTS test**

On RG 476H:

1. Open RetroSprite.
2. Go to Settings.
3. Find `语音输出`.
4. Press `测试朗读`.
5. Confirm the device speaks `RetroSprite 语音输出测试。`.
6. Press `停止朗读` during speech and confirm it stops.

- [ ] **Step 4: Hotkey TTS test**

On RG 476H:

1. Open RetroArch.
2. Trigger AI Service hotkey.
3. Ask a known answerable question, such as `角色什么时候转职`.
4. Confirm top-right HUD enters `Listening...`.
5. Confirm answer card appears.
6. Confirm only the short answer is spoken.
7. Confirm `/debug/latest-request` has `output_mode=hotkey_voice:text` and `question_source=hotkey_voice`.

- [ ] **Step 5: Optional offline TTS engine validation**

If sherpa-onnx TTS Engine APK is installed as the system default TTS engine:

1. Turn off Wi-Fi.
2. Repeat Settings `测试朗读`.
3. Repeat RetroArch hotkey TTS test.
4. Confirm speech still works offline.

- [ ] **Step 6: Record validation result**

Add a dated note to `docs/NEXT_IMPLEMENTATION_PLAN.md`:

```markdown
- 2026-05-23 TTS validation: Settings `语音输出` test and hotkey short-answer TTS were verified on RG 476H. Android `TextToSpeech` remains the default provider. Offline TTS engine validation was not run in this pass.
```

When the offline TTS engine validation is run, replace the final sentence with the actual engine and observed result, for example: `Offline TTS engine validation: sherpa-onnx TTS Engine passed on RG 476H with Wi-Fi disabled.`

- [ ] **Step 7: Commit true-device note**

```bash
git add docs/NEXT_IMPLEMENTATION_PLAN.md
git commit -m "docs: record rg476h tts validation"
```

---

## Verification Commands

Run these before considering the plan complete:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest \
  --tests com.retrosprite.app.ui.integration.SpeechOutputTextTest \
  --tests com.retrosprite.app.ui.overlay.HotkeyVoiceQuestionControllerTest
```

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew assembleDebug
```

If a device is connected:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.retrosprite.app/.MainActivity
adb forward tcp:14404 tcp:4404 >/dev/null
curl -s http://127.0.0.1:14404/health
```

Expected:

```text
BUILD SUCCESSFUL
Success
{"status":"ok","version":"0.1.0"}
```

## Self-Review

- Spec coverage: The plan covers current Android `TextToSpeech` behavior, short-answer policy, Home/manual TTS surface, hotkey overlay TTS behavior, Settings diagnostics, local offline TTS validation, and future app-bundled provider decision gates.
- Placeholder scan: No `TBD`, angle-bracket placeholders, broad "handle edge cases", or unnamed tests remain. Device validation records offline TTS engine results only after true-device execution.
- Type consistency: Existing names are used consistently: `AndroidSpeechOutputProvider`, `SpeechOutputProvider`, `UiSpeechOutputState`, `HotkeyVoiceQuestionController`, `SpeechOutputTextTest`, and `RetroSpriteAppSmokeTest`.
