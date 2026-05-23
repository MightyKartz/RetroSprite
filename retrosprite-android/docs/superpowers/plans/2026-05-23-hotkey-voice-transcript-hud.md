# Hotkey Voice Transcript HUD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the ASR-recognized question text in the hotkey voice HUD so players can immediately see what RetroSprite heard before judging the answer.

**Architecture:** Keep the existing text-first voice architecture: ASR produces text, the local Q&A pipeline answers text, and the overlay displays both the heard text and the answer. Extend the existing `HotkeyVoiceOverlayRenderState` contract with optional normalized transcript metadata, then draw a compact transcript caption inside the top-right wave HUD. Do not change ASR provider selection, TTS, GKP retrieval, RetroArch hotkeys, or fallback answer policy.

**Tech Stack:** Kotlin, JUnit4, Android custom `View`, `WindowManager` overlay, existing `HotkeyVoiceQuestionController`, existing `AndroidHotkeyVoiceOverlayRenderer`, adb/manual RG476H verification.

---

## Problem Evidence

Current real-device voice testing shows the endpoint and answer pipeline are working, but ASR sometimes hears the wrong phrase:

- Intended `修伊是谁` can become `苏苏杀人`, which correctly produces `no_evidence`.
- Intended `开局先做什么` can become `天天天星做什么`, which correctly produces `no_evidence`.
- Intended `角色什么时候转职` can become `角色什么时候转直`, which still reaches the promotion answer because normalization/retrieval can tolerate it.
- Player phrasing such as `角色如何搭配` and `怎么才能赢` has now been added to local GKP/template coverage, so the next bottleneck is helping the player see what was actually recognized.

The code already has a partial data path:

- `HotkeyVoiceOverlayRenderState.transcript` exists.
- `HotkeyVoiceQuestionController` passes `state.transcript` during listening.
- `HotkeyVoiceQuestionController` passes the final `question` during thinking/speaking/no-evidence.

The missing product behavior is stronger visible feedback in the HUD:

- During listening: show the current partial/final recognized text.
- During thinking and answer phases: keep showing the final recognized question.
- When normalization changes the question: show both the heard text and the normalized/search term, so `修医是谁 -> 修伊是谁` is understandable.

## Non-Goals

Do not do these in this implementation:

- Do not replace sherpa-onnx or Android voice input.
- Do not add a fallback LLM path.
- Do not change answer selection, retrieval thresholds, or GKP content.
- Do not add interactive buttons to the overlay.
- Do not store extra transcript history beyond the existing request log fields.
- Do not move the top-right HUD geometry unless a test proves text no longer fits.

## File Structure

Modify:

- `app/src/main/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceOverlayCoordinator.kt`
  - Extend `HotkeyVoiceOverlayRenderState` and `renderVoiceState` with normalized transcript metadata.

- `app/src/main/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionController.kt`
  - Pass final normalized-question diagnostics into the overlay after the response is generated.

- `app/src/main/kotlin/com/retrosprite/app/ui/overlay/AndroidHotkeyVoiceOverlayRenderer.kt`
  - Add a small caption helper and draw the transcript text in the wave HUD.
  - Keep answer card rendering unchanged.

- `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceOverlayRendererTest.kt`
  - Test transcript caption formatting and truncation behavior without Android canvas rendering.

- `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionControllerTest.kt`
  - Test that listening, thinking, speaking, and no-evidence states carry the transcript.
  - Test that normalized diagnostics are propagated to the overlay.

No new production files are required.

---

### Task 1: Add Failing Renderer Transcript Caption Tests

**Files:**
- Modify: `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceOverlayRendererTest.kt`

- [ ] **Step 1: Write failing formatter tests**

Append these tests before `private val suggestedNoEvidenceText`:

```kotlin
    @Test
    fun `transcript hud text shows heard question`() {
        val state = HotkeyVoiceOverlayRenderState(
            event = event(),
            phase = HotkeyVoiceOverlayPhase.Listening,
            transcript = "角色如何搭配",
        )

        assertEquals(
            "听到：角色如何搭配",
            state.transcriptHudText(maxChars = 24),
        )
    }

    @Test
    fun `transcript hud text shows normalized search term when different`() {
        val state = HotkeyVoiceOverlayRenderState(
            event = event(),
            phase = HotkeyVoiceOverlayPhase.Speaking,
            transcript = "修医是谁",
            normalizedTranscript = "修伊是谁",
            transcriptMatchedTerm = "修伊",
        )

        assertEquals(
            "听到：修医是谁 · 按「修伊」检索",
            state.transcriptHudText(maxChars = 32),
        )
    }

    @Test
    fun `transcript hud text truncates long recognized text`() {
        val state = HotkeyVoiceOverlayRenderState(
            event = event(),
            phase = HotkeyVoiceOverlayPhase.Listening,
            transcript = "这个游戏玩的话有什么技巧吗我现在应该怎么才能赢",
        )

        assertEquals(
            "听到：这个游戏玩的话有什么技巧吗我现在应该...",
            state.transcriptHudText(maxChars = 20),
        )
    }

    private fun event(): RetroArchHotkeyEvent =
        RetroArchHotkeyEvent(
            label = "mega_drive__光明力量2",
            outputMode = "hotkey_voice:text",
            imageBytes = 0,
            paused = true,
        )
```

Add the import at the top:

```kotlin
import com.retrosprite.app.endpoint.RetroArchHotkeyEvent
```

- [ ] **Step 2: Run tests and verify red**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:testDebugUnitTest \
  --tests 'com.retrosprite.app.ui.overlay.HotkeyVoiceOverlayRendererTest'
```

Expected before implementation:

```text
Unresolved reference: normalizedTranscript
Unresolved reference: transcriptMatchedTerm
Unresolved reference: transcriptHudText
```

- [ ] **Step 3: Keep the failing test diff scoped**

Run:

```bash
git diff -- app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceOverlayRendererTest.kt
```

Expected: only renderer test changes from this task.

---

### Task 2: Extend Overlay Render State With Normalized Transcript Metadata

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceOverlayCoordinator.kt`

- [ ] **Step 1: Add fields to the render state**

Change `HotkeyVoiceOverlayRenderState` to:

```kotlin
data class HotkeyVoiceOverlayRenderState(
    val event: RetroArchHotkeyEvent,
    val phase: HotkeyVoiceOverlayPhase,
    val amplitude: Float = 0f,
    val message: String = "",
    val transcript: String? = null,
    val normalizedTranscript: String? = null,
    val transcriptMatchedTerm: String? = null,
    val answerText: String? = null,
    val sourceIds: List<String> = emptyList(),
)
```

- [ ] **Step 2: Add parameters to `renderVoiceState`**

Change the function signature and render-state construction to:

```kotlin
    fun renderVoiceState(
        phase: HotkeyVoiceOverlayPhase,
        amplitude: Float = 0f,
        message: String = "",
        transcript: String? = null,
        normalizedTranscript: String? = null,
        transcriptMatchedTerm: String? = null,
        answerText: String? = null,
        sourceIds: List<String> = emptyList(),
    ) {
        val event = activeEvent ?: return
        renderer.render(
            HotkeyVoiceOverlayRenderState(
                event = event,
                phase = phase,
                amplitude = amplitude,
                message = message,
                transcript = transcript,
                normalizedTranscript = normalizedTranscript,
                transcriptMatchedTerm = transcriptMatchedTerm,
                answerText = answerText,
                sourceIds = sourceIds,
            )
        )
    }
```

- [ ] **Step 3: Run the renderer tests again**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:testDebugUnitTest \
  --tests 'com.retrosprite.app.ui.overlay.HotkeyVoiceOverlayRendererTest'
```

Expected now: still failing only on unresolved `transcriptHudText`, because the data fields exist.

---

### Task 3: Add Transcript HUD Formatting Helper

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/overlay/AndroidHotkeyVoiceOverlayRenderer.kt`

- [ ] **Step 1: Add formatter helpers near `HotkeyVoiceWindowSpec` helpers**

Add this code near the bottom of `AndroidHotkeyVoiceOverlayRenderer.kt`, before `private fun Context.dp(value: Int): Int`:

```kotlin
internal fun HotkeyVoiceOverlayRenderState.transcriptHudText(maxChars: Int = TRANSCRIPT_HUD_MAX_CHARS): String? {
    val heard = transcript?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val normalized = normalizedTranscript?.trim()?.takeIf { it.isNotEmpty() }
    val matched = transcriptMatchedTerm?.trim()?.takeIf { it.isNotEmpty() }
    val suffix = when {
        normalized != null && normalized != heard && matched != null -> " · 按「$matched」检索"
        normalized != null && normalized != heard -> " · 按「$normalized」检索"
        else -> ""
    }
    return ("听到：${heard.compactForHud(maxChars)}$suffix").takeWithEllipsis(
        maxChars = maxChars + 18,
    )
}

private fun String.compactForHud(maxChars: Int): String =
    takeWithEllipsis(maxChars = maxChars)

private fun String.takeWithEllipsis(maxChars: Int): String {
    if (length <= maxChars) return this
    return take((maxChars - 3).coerceAtLeast(1)).trimEnd() + "..."
}

private const val TRANSCRIPT_HUD_MAX_CHARS = 24
```

- [ ] **Step 2: Run renderer tests and verify green**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:testDebugUnitTest \
  --tests 'com.retrosprite.app.ui.overlay.HotkeyVoiceOverlayRendererTest'
```

Expected:

```text
BUILD SUCCESSFUL
```

---

### Task 4: Draw Transcript Caption In The Top HUD

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/overlay/AndroidHotkeyVoiceOverlayRenderer.kt`

- [ ] **Step 1: Add caption paint to `HotkeyVoiceWaveView`**

Inside `HotkeyVoiceWaveView`, after `statusPaint`, add:

```kotlin
    private val transcriptPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 232, 246, 249)
        textSize = context.sp(13)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        letterSpacing = 0f
    }
```

- [ ] **Step 2: Call transcript drawing from `onDraw`**

In `onDraw`, after `drawHudLabel(canvas, phase, accent)`, add:

```kotlin
        drawTranscriptCaption(canvas, w)
```

- [ ] **Step 3: Add the drawing function**

Add this method inside `HotkeyVoiceWaveView`, after `drawHudLabel`:

```kotlin
    private fun drawTranscriptCaption(canvas: Canvas, w: Float) {
        val caption = renderState?.transcriptHudText() ?: return
        val left = context.dp(28f)
        val top = context.dp(46f)
        val textWidth = (w - left - context.dp(28f)).toInt().coerceAtLeast(1)
        val originalSize = transcriptPaint.textSize
        transcriptPaint.textSize = context.sp(13)
        val layout = StaticLayout.Builder
            .obtain(caption, 0, caption.length, transcriptPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .setMaxLines(1)
            .build()
        canvas.save()
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restore()
        transcriptPaint.textSize = originalSize
    }
```

- [ ] **Step 4: Check HUD vertical fit**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:testDebugUnitTest \
  --tests 'com.retrosprite.app.ui.overlay.HotkeyVoiceOverlayRendererTest'
```

Expected:

```text
BUILD SUCCESSFUL
```

Implementation note: the wave HUD already uses `WAVE_COMPACT_HEIGHT_DP`; this caption is a one-line overlay above the waveform band. If a screenshot later shows crowding, increase `WAVE_COMPACT_HEIGHT_DP` by 8 and adjust `hotkeyWaveWindowSpec` tests in the safe-area plan, but do not do that unless visual evidence requires it.

---

### Task 5: Propagate Normalized Diagnostics From The Controller

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionController.kt`
- Modify: `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionControllerTest.kt`

- [ ] **Step 1: Add failing controller test for normalized transcript metadata**

In `HotkeyVoiceQuestionControllerTest`, append this test before `missing overlay permission does not start voice session`:

```kotlin
    @Test
    fun `hotkey voice overlay keeps heard and normalized transcript on answer state`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val voice = FakeVoiceInputProvider("修医是谁")
        val controller = HotkeyVoiceQuestionController(
            coordinator = coordinator,
            voiceInput = voice,
            responseGenerator = CapturingGenerator(
                answer = "Chester（汉化名常见“修伊”）是早期骑士型同伴。\n来源：sf2.manual_translation",
                diagnostics = ResponseDiagnostics(
                    question = "修伊是谁",
                    rawQuestion = "修医是谁",
                    normalizedQuestion = "修伊是谁",
                    questionNormalizationReason = "homophone",
                    normalizedQuestionMatchedTerm = "修伊",
                    normalizedQuestionMatchedEntityId = "npc.chester",
                    answerShort = "Chester（汉化名常见“修伊”）是早期骑士型同伴。",
                    answerType = "unknown_or_out_of_scope",
                    llmStatus = "skipped",
                ),
            ),
            speechOutput = FakeSpeechOutputProvider(),
            loggerProvider = { RequestLogger() },
            scope = this,
        )

        controller.onHotkey(event())
        advanceUntilIdle()

        val speaking = renderer.renderedStates.last {
            it.phase == HotkeyVoiceOverlayPhase.Speaking
        }
        assertEquals("修医是谁", speaking.transcript)
        assertEquals("修伊是谁", speaking.normalizedTranscript)
        assertEquals("修伊", speaking.transcriptMatchedTerm)
    }
```

- [ ] **Step 2: Run controller test and verify red**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:testDebugUnitTest \
  --tests 'com.retrosprite.app.ui.overlay.HotkeyVoiceQuestionControllerTest.hotkey voice overlay keeps heard and normalized transcript on answer state'
```

Expected before implementation:

```text
expected:<修医是谁> but was:<修伊是谁>
```

or equivalent failure showing normalized metadata is not passed.

- [ ] **Step 3: Pass diagnostics into speaking/no-evidence render state**

In `HotkeyVoiceQuestionController.kt`, in the successful `entry.errorMessage == null` branch, change the final `coordinator.renderVoiceState(...)` call to include:

```kotlin
                transcript = entry.rawQuestion ?: question,
                normalizedTranscript = entry.normalizedQuestion
                    ?: entry.question.takeIf { it != (entry.rawQuestion ?: question) },
                transcriptMatchedTerm = entry.normalizedQuestionMatchedTerm,
```

The full relevant argument section should be:

```kotlin
                transcript = entry.rawQuestion ?: question,
                normalizedTranscript = entry.normalizedQuestion
                    ?: entry.question.takeIf { it != (entry.rawQuestion ?: question) },
                transcriptMatchedTerm = entry.normalizedQuestionMatchedTerm,
                answerText = if (responsePhase == HotkeyVoiceOverlayPhase.NoEvidence) {
                    (entry.answerDetail ?: entry.responseText).toOverlayAnswerText(
                        maxChars = OVERLAY_NO_EVIDENCE_MAX_CHARS,
                        preserveLineBreaks = true,
                    )
                } else {
                    (entry.answerShort ?: entry.responseText).toOverlayAnswerText()
                },
```

- [ ] **Step 4: Keep thinking state simple**

Do not add normalized metadata to the earlier `Thinking` state because diagnostics are not available yet. It should remain:

```kotlin
        coordinator.renderVoiceState(
            phase = HotkeyVoiceOverlayPhase.Thinking,
            message = "正在检索本地知识",
            transcript = question,
        )
```

- [ ] **Step 5: Run controller tests and verify green**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:testDebugUnitTest \
  --tests 'com.retrosprite.app.ui.overlay.HotkeyVoiceQuestionControllerTest'
```

Expected:

```text
BUILD SUCCESSFUL
```

---

### Task 6: Add A Regression Test For No-Evidence Heard Text

**Files:**
- Modify: `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionControllerTest.kt`

- [ ] **Step 1: Add no-evidence transcript assertion**

In the existing `hotkey voice no evidence uses no evidence overlay state and does not speak long answer` test, after the `noEvidenceState` lookup, add:

```kotlin
        assertEquals("这是谁？", noEvidenceState.transcript)
        assertEquals(null, noEvidenceState.normalizedTranscript)
        assertEquals(null, noEvidenceState.transcriptMatchedTerm)
```

- [ ] **Step 2: Add wrong-ASR example test**

Append this test near the no-evidence tests:

```kotlin
    @Test
    fun `hotkey voice no evidence card preserves wrong asr transcript for player diagnosis`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val voice = FakeVoiceInputProvider("苏苏杀人")
        val controller = HotkeyVoiceQuestionController(
            coordinator = coordinator,
            voiceInput = voice,
            responseGenerator = CapturingGenerator(
                answer = "我还没有足够证据回答这个问题。\n你可以这样问：\n· 修伊是谁？",
                diagnostics = ResponseDiagnostics(
                    question = "苏苏杀人",
                    answerShort = "我还没有足够证据回答这个问题。",
                    answerDetail = "我还没有足够证据回答这个问题。\n你可以这样问：\n· 修伊是谁？",
                    answerType = "no_evidence",
                    answerConfidence = "low",
                    llmStatus = "skipped",
                ),
            ),
            speechOutput = FakeSpeechOutputProvider(),
            loggerProvider = { RequestLogger() },
            scope = this,
        )

        controller.onHotkey(event())
        advanceUntilIdle()

        val noEvidence = renderer.renderedStates.last {
            it.phase == HotkeyVoiceOverlayPhase.NoEvidence
        }
        assertEquals("苏苏杀人", noEvidence.transcript)
        assertEquals("听到：苏苏杀人", noEvidence.transcriptHudText())
    }
```

- [ ] **Step 3: Run no-evidence/controller tests**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:testDebugUnitTest \
  --tests 'com.retrosprite.app.ui.overlay.HotkeyVoiceQuestionControllerTest'
```

Expected:

```text
BUILD SUCCESSFUL
```

---

### Task 7: Verification, Build, And Device Install

**Files:**
- No source edits in this task.

- [ ] **Step 1: Run focused unit tests**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:testDebugUnitTest \
  --tests 'com.retrosprite.app.ui.overlay.HotkeyVoiceOverlayRendererTest' \
  --tests 'com.retrosprite.app.ui.overlay.HotkeyVoiceQuestionControllerTest' \
  --tests 'com.retrosprite.app.endpoint.RequestLoggerTest' \
  --tests 'com.retrosprite.app.endpoint.RoomBackedRequestLogSinkTest'
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 2: Assemble debug APK**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Install on connected test device**

Run:

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.retrosprite.app android.permission.RECORD_AUDIO || true
adb shell appops set com.retrosprite.app RECORD_AUDIO allow || true
```

Expected:

```text
RG476H01077813	device
Success
```

- [ ] **Step 4: Start App and return RetroArch to foreground**

Run:

```bash
adb shell am force-stop com.retrosprite.app
adb shell am start -W -n com.retrosprite.app/.MainActivity
sleep 2
curl --max-time 3 -sS http://127.0.0.1:4404/health
adb shell am start -W -n com.retroarch.aarch64/com.retroarch.browser.retroactivity.RetroActivityFuture
adb shell dumpsys window | rg -n 'mCurrentFocus|mFocusedApp'
```

Expected:

```text
{"status":"ok","version":"0.1.0"}
com.retroarch.aarch64/com.retroarch.browser.retroactivity.RetroActivityFuture
```

---

### Task 8: Manual True-Device QA Checklist

**Files:**
- No source edits in this task.

- [ ] **Step 1: Confirm HUD shows live recognized text**

Manual steps:

1. In RetroArch, trigger the hotkey voice flow.
2. Ask: `角色如何搭配`.
3. Watch the top-right HUD.

Expected visual result:

```text
RETROSPRITE   Listening...
听到：角色如何搭配
```

Expected latest request after the answer:

```bash
curl --max-time 3 -sS http://127.0.0.1:4404/debug/latest-request | jq .
```

Expected fields:

```json
{
  "output_mode": "hotkey_voice:text",
  "question_source": "hotkey_voice",
  "question": "角色如何搭配",
  "pipeline_stage": "evidence"
}
```

- [ ] **Step 2: Confirm wrong ASR is visible instead of mysterious**

Manual steps:

1. Trigger hotkey voice.
2. Ask a short name question that often misrecognizes, such as `修伊是谁`.
3. Watch the top-right HUD.

Acceptable outcomes:

```text
听到：修伊是谁
```

or, if ASR hears it wrong:

```text
听到：苏苏杀人
```

The second outcome is still useful because the player now knows the failure is ASR recognition, not a hidden answer-policy failure.

- [ ] **Step 3: Confirm normalized query is visible**

Manual steps:

1. Trigger hotkey voice.
2. Ask a phrase likely to normalize, such as `修医是谁`.

Expected after answer state:

```text
听到：修医是谁 · 按「修伊」检索
```

Expected latest request:

```json
{
  "raw_question": "修医是谁",
  "normalized_question": "修伊是谁",
  "question_normalization_reason": "homophone",
  "normalized_question_matched_term": "修伊",
  "pipeline_stage": "evidence"
}
```

- [ ] **Step 4: Confirm text does not cover the answer card**

Manual check:

- Top-right HUD should stay in the wave HUD only.
- Bottom answer card should still show the answer.
- The transcript caption should not appear in the bottom answer card.
- No text should overlap the microphone icon or status label.

- [ ] **Step 5: Record exact evidence if something fails**

If a manual test fails, collect:

```bash
curl --max-time 3 -sS http://127.0.0.1:4404/debug/latest-request | jq . > /tmp/retrosprite_transcript_hud_latest.json
adb exec-out screencap -p > /tmp/retrosprite_transcript_hud.png
adb logcat -d -v time | rg -i 'RetroSprite|AudioRecord|sherpa|error|Exception' | tail -200 > /tmp/retrosprite_transcript_hud_log.txt
```

Then inspect:

```bash
jq '{question,raw_question,normalized_question,question_source,pipeline_stage,answer_short}' /tmp/retrosprite_transcript_hud_latest.json
ls -lh /tmp/retrosprite_transcript_hud.png /tmp/retrosprite_transcript_hud_log.txt
```

---

## Acceptance Criteria

- The top-right HUD displays `听到：<recognized text>` during voice recognition once ASR provides text.
- Thinking, Speaking, and NoEvidence states keep the final recognized text visible until the overlay hides.
- If the pipeline normalizes a voice question, the HUD can display `听到：<raw> · 按「<matched term>」检索`.
- NoEvidence states preserve the wrong ASR transcript for player diagnosis.
- Focused unit tests pass.
- Debug APK builds and installs on the connected Android test device.
- Manual RG476H QA confirms the caption is readable and does not overlap status/mic/answer text.

## Self-Review

- Spec coverage: the plan covers renderer text formatting, state propagation, controller diagnostics, tests, APK install, and manual device QA.
- Placeholder scan: no `TBD`, no open-ended "add tests", and every code-changing task includes exact snippets.
- Type consistency: new render-state fields are consistently named `normalizedTranscript` and `transcriptMatchedTerm`; renderer tests, controller tests, coordinator, and controller all use the same names.
- Scope control: ASR engine, GKP retrieval, fallback policy, and RetroArch setup remain out of scope.
