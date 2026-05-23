# Hotkey Voice HUD Safe Area Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the RetroArch hotkey voice HUD readable without covering Shining Force II battle UI, while keeping the hotkey voice answer chain evidence-grounded and local-first.

**Architecture:** Keep the existing `AndroidHotkeyVoiceOverlayRenderer` custom `View` boundary, but move all overlay window geometry into small internal spec helpers that can be JVM-tested against the RG476H viewport. Keep answer generation and TTS policy unchanged except for the short on-screen answer budget. Treat empty RetroArch wake POSTs as control-plane signals, not user-visible answer log entries.

**Tech Stack:** Kotlin, JUnit4, Android `WindowManager.LayoutParams`, custom `View`, Ktor endpoint tests, adb/curl true-device smoke checks, RG476H `1280x960` landscape validation.

---

## Problem Evidence

True-device test on `RG476H01077813`, `1280x960`, density `280`:

- RetroArch foreground app: `com.retroarch.aarch64/com.retroarch.browser.retroactivity.RetroActivityFuture`.
- Loaded game: `mega_drive__光明力量2`.
- Permissions are good:
  - `android.permission.SYSTEM_ALERT_WINDOW: granted=true`
  - `android.permission.RECORD_AUDIO: granted=true`
- Full voice loop worked when the host spoke `那些角色适合培养`:
  - ASR transcript: `那些角色适合培养的`
  - `question_source=hotkey_voice`
  - `answer_type=team_build`
  - `pipeline_stage=evidence`
  - `llm_status=skipped`
  - `source_ids=["sf2.project_mechanics"]`

Observed UI/log issues:

- Top-right listening/speaking HUD is too wide and covers the game's right-side HP/MP panel.
- Bottom-left answer card no longer clips text, but it covers part of the battle command menu.
- Evidence answers are still truncated at `OVERLAY_ANSWER_MAX_CHARS = 56`, so the new growing HUD cannot display a complete short local answer.
- Android logs repeatedly warn that a non-touchable overlay has alpha `1.00 > 0.80`; the system clamps it to `0.80`.
- Empty RetroArch wake POSTs log as successful `output_mode=text`, `pipeline_stage=unknown`, which can overwrite `/debug/latest-request` even when no voice answer happened.

Do not change:

- RetroArch AI Service as the trigger.
- Local-first GKP/evidence answer path.
- ASR/TTS provider selection.
- Overlay permissions or Android manifest scope.
- RetroArch config or hotkey bindings.

---

## File Structure

Modify:

- `app/src/main/kotlin/com/retrosprite/app/ui/overlay/AndroidHotkeyVoiceOverlayRenderer.kt`
  - Owns wave HUD and answer card window geometry, alpha, drawing, typewriter text, and answer card sizing.

- `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceOverlayRendererTest.kt`
  - Fast JVM coverage for RG476H safe-area geometry, overlay alpha, answer card capacity, and typewriter behavior.

- `app/src/main/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionController.kt`
  - Owns the short on-screen answer budget and answer text passed to the renderer.

- `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionControllerTest.kt`
  - Contract coverage for complete short evidence answers and no-evidence detail display.

- `app/src/main/kotlin/com/retrosprite/app/endpoint/RetroArchEndpointServer.kt`
  - Owns route-level request logging for real RetroArch POSTs.

- `app/src/test/kotlin/com/retrosprite/app/endpoint/RetroArchEndpointServerTest.kt`
  - Ktor route coverage proving silent wake POSTs still notify the hotkey listener but do not replace latest user-answer diagnostics.

No new production files are required.

---

### Task 1: Lock RG476H HUD Geometry In Renderer Tests

**Files:**
- Modify: `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceOverlayRendererTest.kt`

- [ ] **Step 1: Add failing window-spec tests**

Append these tests before `private val suggestedNoEvidenceText`:

```kotlin
    @Test
    fun `rg476h wave hud avoids battle status panels`() {
        val spec = hotkeyWaveWindowSpec(displayWidthPx = 1280, density = 1.75f)

        assertEquals(HotkeyVoiceWindowAnchor.TopStart, spec.anchor)
        assertTrue("wave HUD should be compact", spec.widthDp <= 240)
        assertTrue("wave HUD should clear the terrain box", spec.xDp >= 180)
        assertTrue("wave HUD should clear the right HP panel", spec.xDp + spec.widthDp <= 435)
        assertTrue("wave HUD should stay in the top band", spec.yDp in 44..60)
        assertEquals(0.80f, spec.alpha, 0.001f)
    }

    @Test
    fun `rg476h answer hud leaves the battle command menu readable`() {
        val card = HotkeyVoiceOverlayPhase.Speaking.answerCardSpec(
            fontScale = 1.0f,
            answerText = "通用原则：优先练能稳定出场、补足治疗或远程输出、移动和生存不拖队伍的角色；队伍搭配上保留治疗、稳定前排和安全输出。",
            cardWidthDp = 390,
        )
        val spec = hotkeyAnswerWindowSpec(
            displayWidthPx = 1280,
            density = 1.75f,
            cardSpec = card,
        )

        assertEquals(HotkeyVoiceWindowAnchor.BottomStart, spec.anchor)
        assertTrue("answer HUD should not span into the command cluster", spec.widthDp <= 390)
        assertTrue("answer HUD should leave a larger bottom safe area", spec.yDp >= 148)
        assertEquals(0.80f, spec.alpha, 0.001f)
    }
```

- [ ] **Step 2: Run the renderer tests and verify they fail**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest --tests com.retrosprite.app.ui.overlay.HotkeyVoiceOverlayRendererTest
```

Expected before implementation:

```text
FAILED
Unresolved reference: hotkeyWaveWindowSpec
Unresolved reference: hotkeyAnswerWindowSpec
Unresolved reference: HotkeyVoiceWindowAnchor
```

- [ ] **Step 3: Commit the failing tests**

```bash
git add app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceOverlayRendererTest.kt
git commit -m "test: cover rg476h hotkey hud safe areas"
```

---

### Task 2: Make Overlay Window Geometry Testable And Safe

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/overlay/AndroidHotkeyVoiceOverlayRenderer.kt`
- Modify: `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceOverlayRendererTest.kt`

- [ ] **Step 1: Add internal window spec types and helpers**

In `AndroidHotkeyVoiceOverlayRenderer.kt`, add these declarations near `HotkeyVoiceAnswerCardSpec`:

```kotlin
internal enum class HotkeyVoiceWindowAnchor {
    TopStart,
    TopEnd,
    BottomStart,
}

internal data class HotkeyVoiceWindowSpec(
    val widthDp: Int,
    val heightDp: Int,
    val anchor: HotkeyVoiceWindowAnchor,
    val xDp: Int,
    val yDp: Int,
    val alpha: Float = OVERLAY_WINDOW_ALPHA,
)

internal fun hotkeyWaveWindowSpec(
    displayWidthPx: Int,
    density: Float,
): HotkeyVoiceWindowSpec {
    val screenWidthDp = (displayWidthPx / density).toInt().coerceAtLeast(320)
    val widthDp = 240.coerceAtMost(screenWidthDp - 96).coerceAtLeast(196)
    val xDp = ((screenWidthDp - widthDp) * 0.38f).toInt().coerceAtLeast(24)
    return HotkeyVoiceWindowSpec(
        widthDp = widthDp,
        heightDp = WAVE_COMPACT_HEIGHT_DP,
        anchor = HotkeyVoiceWindowAnchor.TopStart,
        xDp = xDp,
        yDp = WAVE_TOP_SAFE_MARGIN_DP,
    )
}

internal fun hotkeyAnswerWindowSpec(
    displayWidthPx: Int,
    density: Float,
    cardSpec: HotkeyVoiceAnswerCardSpec,
): HotkeyVoiceWindowSpec {
    val screenWidthDp = (displayWidthPx / density).toInt().coerceAtLeast(320)
    val widthDp = min(ANSWER_SAFE_WIDTH_DP, (screenWidthDp - 96).coerceAtLeast(300))
    return HotkeyVoiceWindowSpec(
        widthDp = widthDp,
        heightDp = cardSpec.heightDp,
        anchor = HotkeyVoiceWindowAnchor.BottomStart,
        xDp = ANSWER_LEFT_MARGIN_DP,
        yDp = ANSWER_BOTTOM_MARGIN_DP,
    )
}
```

- [ ] **Step 2: Add the new constants**

Replace the old width/bottom constants with this block, keeping existing line-height and no-evidence constants:

```kotlin
private const val DEFAULT_ANSWER_CARD_WIDTH_DP = 390
private const val ANSWER_SAFE_WIDTH_DP = 390
private const val ANSWER_LEFT_MARGIN_DP = 24
private const val ANSWER_BOTTOM_MARGIN_DP = 152
private const val WAVE_COMPACT_HEIGHT_DP = 76
private const val WAVE_TOP_SAFE_MARGIN_DP = 52
private const val OVERLAY_WINDOW_ALPHA = 0.80f
```

Keep:

```kotlin
private const val ANSWER_TEXT_START_DP = 58
private const val ANSWER_TEXT_END_DP = 20
private const val ANSWER_FONT_SIZE_SP = 18
private const val ANSWER_LINE_HEIGHT_DP = 22
```

- [ ] **Step 3: Apply the spec to `WindowManager.LayoutParams`**

Add these private helpers below `waveWidthPx()` or near the other renderer helpers:

```kotlin
private fun Context.waveWindowSpec(): HotkeyVoiceWindowSpec =
    hotkeyWaveWindowSpec(
        displayWidthPx = resources.displayMetrics.widthPixels,
        density = resources.displayMetrics.density,
    )

private fun Context.answerWindowSpec(cardSpec: HotkeyVoiceAnswerCardSpec): HotkeyVoiceWindowSpec =
    hotkeyAnswerWindowSpec(
        displayWidthPx = resources.displayMetrics.widthPixels,
        density = resources.displayMetrics.density,
        cardSpec = cardSpec,
    )

private fun WindowManager.LayoutParams.applyWindowSpec(
    context: Context,
    spec: HotkeyVoiceWindowSpec,
) {
    width = context.dp(spec.widthDp)
    height = context.dp(spec.heightDp)
    x = context.dp(spec.xDp)
    y = context.dp(spec.yDp)
    alpha = spec.alpha
    gravity = when (spec.anchor) {
        HotkeyVoiceWindowAnchor.TopStart -> Gravity.TOP or Gravity.START
        HotkeyVoiceWindowAnchor.TopEnd -> Gravity.TOP or Gravity.END
        HotkeyVoiceWindowAnchor.BottomStart -> Gravity.BOTTOM or Gravity.START
    }
}
```

- [ ] **Step 4: Use the wave spec in renderer initialization and `show()`**

Change `waveParams` initialization to use a harmless default size, then apply the spec:

```kotlin
private val waveParams = WindowManager.LayoutParams(
    appContext.dp(240),
    appContext.dp(WAVE_COMPACT_HEIGHT_DP),
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
    PixelFormat.TRANSLUCENT,
).apply {
    applyWindowSpec(appContext, appContext.waveWindowSpec())
}
```

In `show(event)`, replace the direct width/height assignments:

```kotlin
waveParams.applyWindowSpec(appContext, appContext.waveWindowSpec())
```

- [ ] **Step 5: Use the answer spec in `showAnswerWindow()` and `updateAnswerWindowForVisibleText()`**

Replace the repeated `answerParams.width/height/y` assignments with:

```kotlin
val cardSpec = answerView.currentAnswerCardSpec(answerWidth)
answerParams.applyWindowSpec(appContext, appContext.answerWindowSpec(cardSpec))
```

The local `answerWidth` remains useful for computing the card text capacity, but the actual window width comes from `hotkeyAnswerWindowSpec()`.

- [ ] **Step 6: Keep `answerWidthPx()` aligned with the spec**

Replace `answerWidthPx()` with:

```kotlin
private fun answerWidthPx(): Int =
    appContext.dp(appContext.answerWindowSpec(HotkeyVoiceOverlayPhase.Speaking.answerCardSpec(
        fontScale = appContext.resources.configuration.fontScale,
        answerText = "",
        cardWidthDp = DEFAULT_ANSWER_CARD_WIDTH_DP,
    )).widthDp)
```

- [ ] **Step 7: Remove or stop using `waveWidthPx()`**

Delete `waveWidthPx()` if no references remain. If one reference remains in a test-only or initialization path, replace it with `appContext.waveWindowSpec().widthDp`.

- [ ] **Step 8: Run renderer tests**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest --tests com.retrosprite.app.ui.overlay.HotkeyVoiceOverlayRendererTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 9: Commit the geometry implementation**

```bash
git add app/src/main/kotlin/com/retrosprite/app/ui/overlay/AndroidHotkeyVoiceOverlayRenderer.kt \
  app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceOverlayRendererTest.kt
git commit -m "fix: keep hotkey hud out of rg476h battle ui"
```

---

### Task 3: Let Evidence Answers Use The Growing HUD

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionController.kt`
- Modify: `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionControllerTest.kt`

- [ ] **Step 1: Add failing evidence overlay text coverage**

Append this test to `HotkeyVoiceQuestionControllerTest` before `missing overlay permission does not start voice session`:

```kotlin
    @Test
    fun `hotkey voice evidence answer card keeps complete local short answer`() = runTest {
        val renderer = FakeRenderer()
        val coordinator = HotkeyVoiceOverlayCoordinator(
            renderer = renderer,
            canDrawOverlays = { true },
            scheduleAutoHide = {},
            cancelAutoHide = {},
        )
        val fullShortAnswer = "通用原则：优先练能稳定出场、补足治疗或远程输出、移动和生存不拖队伍的角色；队伍搭配上保留治疗、稳定前排和安全输出。"
        val voice = FakeVoiceInputProvider("那些角色适合培养的")
        val controller = HotkeyVoiceQuestionController(
            coordinator = coordinator,
            voiceInput = voice,
            responseGenerator = CapturingGenerator(
                answer = "$fullShortAnswer\n来源：sf2.project_mechanics",
                diagnostics = ResponseDiagnostics(
                    answerShort = fullShortAnswer,
                    answerDetail = "$fullShortAnswer 告诉我你现在到哪一章或刚收了哪些角色，我可以更具体。",
                    answerType = "team_build",
                    llmStatus = "skipped",
                ),
            ),
            speechOutput = FakeSpeechOutputProvider(),
            loggerProvider = { RequestLogger() },
            scope = this,
        )

        controller.onHotkey(event())
        advanceUntilIdle()

        val speakingState = renderer.renderedStates.last {
            it.phase == HotkeyVoiceOverlayPhase.Speaking
        }
        assertEquals(fullShortAnswer, speakingState.answerText)
    }
```

- [ ] **Step 2: Run the controller test and verify it fails**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest --tests com.retrosprite.app.ui.overlay.HotkeyVoiceQuestionControllerTest
```

Expected before implementation:

```text
FAILED
expected:<...安全输出。> but was:<...安全输出...>
```

- [ ] **Step 3: Increase the on-screen evidence answer budget**

In `HotkeyVoiceQuestionController.kt`, replace:

```kotlin
private const val OVERLAY_ANSWER_MAX_CHARS = 56
```

with:

```kotlin
private const val OVERLAY_ANSWER_MAX_CHARS = 96
```

- [ ] **Step 4: Prefer `answerShort`, but allow it to fit completely**

Keep this production branch:

```kotlin
(entry.answerShort ?: entry.responseText).toOverlayAnswerText()
```

Do not switch ordinary evidence answers to `answerDetail`; that would duplicate TTS-style follow-up prompts inside gameplay. The larger budget is enough for the current Shining Force II local short answer.

- [ ] **Step 5: Run controller tests**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest --tests com.retrosprite.app.ui.overlay.HotkeyVoiceQuestionControllerTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 6: Commit the answer budget change**

```bash
git add app/src/main/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionController.kt \
  app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionControllerTest.kt
git commit -m "fix: show complete short hotkey answers"
```

---

### Task 4: Stop Silent Wake POSTs From Polluting Latest Request

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/endpoint/RetroArchEndpointServer.kt`
- Modify: `app/src/test/kotlin/com/retrosprite/app/endpoint/RetroArchEndpointServerTest.kt`

- [ ] **Step 1: Add failing endpoint coverage**

Append this test to `RetroArchEndpointServerTest` before `debug latest request returns empty object before any request`:

```kotlin
    @Test
    fun `silent hotkey wake notifies listener but does not replace latest request`() = testApplication {
        val logger = RequestLogger()
        val events = mutableListOf<RetroArchHotkeyEvent>()
        val listener = RetroArchHotkeyListener { event -> events += event }
        val generator = ResponseGenerator { _, _ -> RetroArchResponse.text("") }
        application { retroArchModule(generator, logger, listener) }

        client.post("/debug/ask?output=text") {
            contentType(ContentType.Application.Json)
            setBody("""{"label":"2048__","question":"两个 2 怎么合并？","state":{"paused":1}}""")
        }
        val before = client.get("/debug/latest-request").bodyAsText()

        val wake = client.post("/?output=text") {
            contentType(ContentType.Application.Json)
            setBody("""{"label":"mega_drive__光明力量2","state":{"paused":1}}""")
        }

        assertEquals(HttpStatusCode.OK, wake.status)
        assertEquals(1, events.size)
        assertEquals("mega_drive__光明力量2", events.first().label)
        assertEquals(1, logger.entries.value.size)
        assertEquals(before, client.get("/debug/latest-request").bodyAsText())
    }
```

- [ ] **Step 2: Run the endpoint test and verify it fails**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest --tests com.retrosprite.app.endpoint.RetroArchEndpointServerTest
```

Expected before implementation:

```text
FAILED
expected:<1> but was:<2>
```

- [ ] **Step 3: Add the silent wake helper**

In `RetroArchEndpointServer.kt`, add this helper near `respondJson()`:

```kotlin
private fun RetroArchRequest.isSilentHotkeyWakeResponse(response: RetroArchResponse): Boolean =
    question.isBlank() &&
        response.text.orEmpty().isBlank() &&
        response.error == null &&
        response.diagnostics.question == null &&
        response.diagnostics.questionSource == null &&
        response.diagnostics.answerShort == null &&
        response.diagnostics.answerDetail == null &&
        response.diagnostics.answerType == null
```

- [ ] **Step 4: Skip logging only for silent wake success**

In the root `post("/")` route, after `val durationMillis = ...` and before `requestLogger.log(...)`, insert:

```kotlin
            if (request.isSilentHotkeyWakeResponse(response)) {
                call.respondJson(response)
                return@post
            }
```

Do not move `hotkeyListener.notifySafely(request, outputMode)`; the overlay still needs the wake event.

- [ ] **Step 5: Run endpoint tests**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest --tests com.retrosprite.app.endpoint.RetroArchEndpointServerTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 6: Commit the logging fix**

```bash
git add app/src/main/kotlin/com/retrosprite/app/endpoint/RetroArchEndpointServer.kt \
  app/src/test/kotlin/com/retrosprite/app/endpoint/RetroArchEndpointServerTest.kt
git commit -m "fix: keep silent hotkey wake out of latest request"
```

---

### Task 5: Run Focused And Full Regression

**Files:**
- No code changes.

- [ ] **Step 1: Run focused hotkey HUD and endpoint tests**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest \
  --tests com.retrosprite.app.ui.overlay.HotkeyVoiceOverlayRendererTest \
  --tests com.retrosprite.app.ui.overlay.HotkeyVoiceQuestionControllerTest \
  --tests com.retrosprite.app.endpoint.RetroArchEndpointServerTest \
  --tests com.retrosprite.app.endpoint.HotkeyWakeResponseGeneratorTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 2: Run the full JVM suite**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Build the debug APK**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 4: Check whitespace**

```bash
git diff --check -- \
  app/src/main/kotlin/com/retrosprite/app/ui/overlay/AndroidHotkeyVoiceOverlayRenderer.kt \
  app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceOverlayRendererTest.kt \
  app/src/main/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionController.kt \
  app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionControllerTest.kt \
  app/src/main/kotlin/com/retrosprite/app/endpoint/RetroArchEndpointServer.kt \
  app/src/test/kotlin/com/retrosprite/app/endpoint/RetroArchEndpointServerTest.kt
```

Expected: no output and exit code `0`.

---

### Task 6: Install And Validate On RG476H

**Files:**
- No repo changes.

- [ ] **Step 1: Confirm device and install**

```bash
adb devices -l
adb -s RG476H01077813 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s RG476H01077813 shell am start -n com.retrosprite.app/.MainActivity
adb -s RG476H01077813 forward tcp:4404 tcp:4404
curl -sS --max-time 5 http://127.0.0.1:4404/health
```

Expected:

```text
RG476H01077813 device
Success
{"status":"ok","version":"0.1.0"}
```

- [ ] **Step 2: Verify silent wake no longer replaces latest**

```bash
curl -sS --max-time 8 -X POST 'http://127.0.0.1:4404/debug/ask?output=text' \
  -H 'Content-Type: application/json' \
  --data '{"image":"","label":"mega_drive__光明力量2","question":"那些角色适合培养","state":{"paused":1}}' >/tmp/retrosprite_before_wake.txt

curl -sS --max-time 5 http://127.0.0.1:4404/debug/latest-request >/tmp/retrosprite_latest_before_wake.json

curl -sS --max-time 5 -X POST 'http://127.0.0.1:4404/?output=text' \
  -H 'Content-Type: application/json' \
  --data '{"image":"","label":"mega_drive__光明力量2","question":"","state":{"paused":1}}' >/tmp/retrosprite_silent_wake_response.json

sleep 1
curl -sS --max-time 5 http://127.0.0.1:4404/debug/latest-request >/tmp/retrosprite_latest_after_wake.json
diff -u /tmp/retrosprite_latest_before_wake.json /tmp/retrosprite_latest_after_wake.json
```

Expected:

```text
```

The `diff` command should print no differences.

- [ ] **Step 3: Run a full hotkey voice smoke with host speech**

Keep RetroArch foreground with Shining Force II loaded, then run:

```bash
adb -s RG476H01077813 logcat -c
curl -sS --max-time 5 -X POST 'http://127.0.0.1:4404/?output=text' \
  -H 'Content-Type: application/json' \
  --data '{"image":"","label":"mega_drive__光明力量2","question":"","state":{"paused":1}}' >/tmp/retrosprite_voice_wake_response.json

sleep 1
say -v Tingting -r 165 '那些角色适合培养'

for i in 1 2 3 4 5 6 7 8; do
  sleep 1
  adb -s RG476H01077813 shell screencap -p /sdcard/retrosprite_hud_safe_$i.png
  adb -s RG476H01077813 pull /sdcard/retrosprite_hud_safe_$i.png /tmp/retrosprite_hud_safe_$i.png >/dev/null
done

curl -sS --max-time 5 http://127.0.0.1:4404/debug/latest-request | jq .
adb -s RG476H01077813 logcat -d -v time | \
  rg -i 'RetroSprite|WindowManager|AudioRecord|RECORD_AUDIO|system alert window' \
  >/tmp/retrosprite_hud_safe_log.txt || true
```

Expected latest request:

```json
{
  "question_source": "hotkey_voice",
  "answer_type": "team_build",
  "pipeline_stage": "evidence",
  "llm_status": "skipped",
  "source_ids": ["sf2.project_mechanics"]
}
```

- [ ] **Step 4: Manually review screenshots**

Open the key screenshots:

```bash
open /tmp/retrosprite_hud_safe_1.png
open /tmp/retrosprite_hud_safe_5.png
```

Acceptance:

- Listening/speaking wave HUD does not cover the right HP/MP panel.
- Wave HUD does not cover the left terrain-effect panel.
- Answer HUD text is readable and not bottom-clipped.
- Answer HUD bottom edge sits above the battle command menu.
- Evidence answer does not show `...` for the current team-building short answer.
- No `alpha = 1.00 > 0.80` warning appears in `/tmp/retrosprite_hud_safe_log.txt`.
- No `silencing record` or `Operation not started RECORD_AUDIO` appears in `/tmp/retrosprite_hud_safe_log.txt`.

- [ ] **Step 5: Commit final validation notes**

Append a short dated note to `docs/TEST_COVERAGE.md`:

```markdown
- **2026-05-23, RG 476H hotkey HUD safe-area validation:** Verified Shining Force II hotkey voice loop on `1280x960` / density `280`; compact wave HUD avoids top battle panels, answer HUD avoids bottom command menu, evidence answer remains untruncated, silent wake does not replace `/debug/latest-request`, and overlay alpha is explicitly `0.80`.
```

Then commit:

```bash
git add docs/TEST_COVERAGE.md
git commit -m "docs: record rg476h hotkey hud safe-area validation"
```

---

## Acceptance Criteria

- True-device hotkey voice still reaches `question_source=hotkey_voice`.
- `那些角色适合培养` and close ASR variants still resolve to `team_build` evidence from `sf2.project_mechanics`.
- The listening/speaking wave HUD no longer covers the top-right HP/MP panel on RG476H.
- The answer HUD no longer covers the bottom command menu on the tested Shining Force II battle screen.
- The answer HUD still shows at least 3 lines by default and grows by visible text line count.
- The current local team-building short answer displays without `...`.
- Empty RetroArch wake POSTs still trigger overlay listening but do not overwrite `/debug/latest-request`.
- Android logcat no longer contains the overlay alpha clamp warning for RetroSprite windows.
- No new permissions, no RetroArch cfg writes, no network requirement, and no changes to GKP factual content.

## Self-Review

- Spec coverage: Each observed issue has a task: top HUD safe area in Task 1-2, bottom answer safe area in Task 1-2, truncation in Task 3, silent wake logs in Task 4, alpha warning in Task 2 and Task 6.
- Placeholder scan: The plan contains concrete file paths, code snippets, commands, expected failures, expected successes, and acceptance checks.
- Type consistency: `HotkeyVoiceWindowSpec`, `HotkeyVoiceWindowAnchor`, `hotkeyWaveWindowSpec`, and `hotkeyAnswerWindowSpec` are introduced before tests depend on them in implementation tasks.
