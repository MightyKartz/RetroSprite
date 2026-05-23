# Hotkey Voice QA HUD Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the real-device hotkey voice path so observed ASR variants like `那些角色适合培养` resolve to the existing Shining Force II team-building answer, and make the bottom-left answer HUD reliably show its text on RG476H-class landscape devices.

**Architecture:** Keep the answer factual path local-first. Add deterministic ASR confusion normalization in the existing natural-question normalization layer so intent classification, template matching, alias matching, and FTS all see the same corrected query. Fix HUD clipping by making answer-card text capacity explicit and font-scale-aware in the overlay renderer, without changing the RetroArch endpoint contract or adding new permissions.

**Tech Stack:** Kotlin, JUnit4, kotlinx-coroutines-test, Android custom `View` overlay, Room/GKP fixtures, adb/curl smoke checks.

---

## Problem Evidence

Real-device request log from `RG476H01077813` showed:

- `label=mega_drive__光明力量2`
- `question_source=hotkey_voice`
- latest transcript: `那些角色适合培养`
- `answer_type=team_build`
- `pipeline_stage=no_evidence`
- `llm_status=skipped`
- answer: `我还没有足够证据回答这个问题...`

Debug contrast on the same installed app showed:

```bash
curl -sS -X POST 'http://127.0.0.1:4404/debug/ask?output=text' \
  -H 'Content-Type: application/json' \
  --data '{"image":"","label":"mega_drive__光明力量2","question":"哪些角色适合培养","state":{"paused":1}}'
```

returns:

```text
通用原则：优先练能稳定出场、补足治疗或远程输出、移动和生存不拖队伍的角色；队伍搭配上保留治疗、稳定前排和安全输出。告诉我你现在到哪一章或刚收了哪些角色，我可以更具体。
来源：sf2.project_mechanics
```

while `那些角色适合培养` returns no evidence. This means:

- Voice capture and ASR completed.
- The game/GKP label resolved.
- The retrieval string was too brittle for the observed ASR confusion.
- HUD clipping is a separate presentation-layer defect.

## File Structure

Modify:

- `app/src/main/kotlin/com/retrosprite/app/domain/intent/NaturalQuestionFrame.kt`
  - Owns natural question normalization and observed ASR confusion fixes.

- `app/src/test/kotlin/com/retrosprite/app/domain/intent/QuestionIntentClassifierTest.kt`
  - Unit coverage for normalized ASR variants and intent classification.

- `app/src/test/kotlin/com/retrosprite/app/domain/SampleShiningForceIIQuestionPipelineTest.kt`
  - End-to-end zero-LLM coverage for real-device team-building ASR variants.

- `app/src/test/kotlin/com/retrosprite/app/data/retrieval/SampleShiningForceIIRetrievalGoldenTest.kt`
  - Retrieval-only coverage for the same variants against the sample GKP.

- `app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl`
  - Durable GKP golden cases for observed ASR variants.

- `app/src/main/kotlin/com/retrosprite/app/ui/overlay/AndroidHotkeyVoiceOverlayRenderer.kt`
  - Bottom-left answer-card sizing, max line count, and text fitting behavior.

- `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceOverlayRendererTest.kt`
  - Fast JVM coverage for answer-card sizing rules.

- `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionControllerTest.kt`
  - Contract coverage that the no-evidence/answer card receives the text that must be visible.

No new production files are needed for this pass.

---

### Task 1: Lock The ASR Failure In Tests

**Files:**
- Modify: `app/src/test/kotlin/com/retrosprite/app/domain/intent/QuestionIntentClassifierTest.kt`
- Modify: `app/src/test/kotlin/com/retrosprite/app/domain/SampleShiningForceIIQuestionPipelineTest.kt`
- Modify: `app/src/test/kotlin/com/retrosprite/app/data/retrieval/SampleShiningForceIIRetrievalGoldenTest.kt`

- [ ] **Step 1: Add failing intent-classifier coverage**

Append this test to `QuestionIntentClassifierTest`:

```kotlin
    @Test
    fun `normalizes observed asr team building confusions before intent classification`() {
        val cases = listOf(
            "那些角色适合培养",
            "那这些角色适合培养",
            "那些人物适合培养",
            "哪些人物适合培养",
            "那些队员适合培养",
        )

        cases.forEach { question ->
            assertEquals(
                "question=<$question>",
                AnswerType.TeamBuild,
                QuestionIntentClassifier.classify(question),
            )
            assertEquals(
                "question=<$question>",
                "哪些角色适合培养",
                question.normalizeNaturalQuestion(),
            )
        }
    }
```

- [ ] **Step 2: Run the intent test and verify it fails**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest --tests com.retrosprite.app.domain.intent.QuestionIntentClassifierTest
```

Expected before implementation:

```text
FAILED
expected:<哪些角色适合培养> but was:<那些角色适合培养>
```

- [ ] **Step 3: Add failing end-to-end pipeline coverage**

Append this test to `SampleShiningForceIIQuestionPipelineTest`:

```kotlin
    @Test
    fun `shining force ii observed asr team building variants return local principles`() = runTest {
        val fixture = loadSamplePack()
        val llm = CountingLlmAdapter()
        val pipeline = newPipeline(fixture, llm)

        listOf(
            "那些角色适合培养",
            "那这些角色适合培养",
            "那些人物适合培养",
            "哪些人物适合培养",
            "那些队员适合培养",
        ).forEach { question ->
            val result = pipeline.answerDetailed(
                label = "mega_drive__光明力量2",
                question = question,
                spoilerLevel = SpoilerLevel.LIGHT,
            )

            assertTrue(
                "question=<$question> answer=<${result.text}>",
                result.text.contains("通用原则") || result.text.contains("治疗"),
            )
            assertTrue(
                "question=<$question> answer=<${result.text}>",
                result.text.contains("来源：sf2.project_mechanics"),
            )
            assertEquals("question=<$question>", AnswerType.TeamBuild, result.answerResult.answerType)
            assertEquals("question=<$question>", "skipped", result.llmTrace.status)
        }
        assertEquals(0, llm.callCount)
    }
```

- [ ] **Step 4: Run the pipeline test and verify it fails**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest --tests com.retrosprite.app.domain.SampleShiningForceIIQuestionPipelineTest
```

Expected before implementation:

```text
FAILED
question=<那些角色适合培养> answer=<我还没有足够证据回答这个问题...
```

- [ ] **Step 5: Add failing retrieval-only coverage**

Append this test to `SampleShiningForceIIRetrievalGoldenTest`:

```kotlin
    @Test
    fun `sample shining force ii observed asr team building variants resolve team strategy`() = runTest {
        val fixture = loadPack()
        val pipeline = LocalKnowledgeRetrievalPipeline(FixtureKnowledgeRepository(fixture.knowledge))

        listOf(
            "那些角色适合培养",
            "那这些角色适合培养",
            "那些人物适合培养",
            "哪些人物适合培养",
            "那些队员适合培养",
        ).forEach { question ->
            val normalized = pipeline.normalizeQuestion(question, "zh")
            val results = pipeline.retrieve(
                RetrievalQuery(
                    gameId = "shining_force_ii_md",
                    normalizedQuery = normalized,
                    language = "zh",
                    progressGate = "start",
                    spoilerLevel = SpoilerLevel.LIGHT,
                    limit = 5,
                )
            )

            assertTrue(
                "question=<$question> normalized=<$normalized> got ${results.map { it.entityId }}",
                results.any { it.entityId == "strategy.team-build-general" },
            )
            val sources = results.flatMap { result -> result.evidence.map { it.sourceId } }
            assertTrue(
                "question=<$question> sources=$sources",
                sources.contains("sf2.project_mechanics"),
            )
        }
    }
```

- [ ] **Step 6: Run the retrieval test and verify it fails**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest --tests com.retrosprite.app.data.retrieval.SampleShiningForceIIRetrievalGoldenTest
```

Expected before implementation:

```text
FAILED
question=<那些角色适合培养> normalized=<那些角色适合培养> got []
```

- [ ] **Step 7: Commit the failing tests**

```bash
git add \
  app/src/test/kotlin/com/retrosprite/app/domain/intent/QuestionIntentClassifierTest.kt \
  app/src/test/kotlin/com/retrosprite/app/domain/SampleShiningForceIIQuestionPipelineTest.kt \
  app/src/test/kotlin/com/retrosprite/app/data/retrieval/SampleShiningForceIIRetrievalGoldenTest.kt
git commit -m "test: cover hotkey voice team-building asr variants"
```

---

### Task 2: Normalize Observed Team-Building ASR Confusions

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/intent/NaturalQuestionFrame.kt`

- [ ] **Step 1: Replace the ASR confusion normalizer**

Replace `normalizeObservedAsrConfusions()` with:

```kotlin
internal fun String.normalizeObservedAsrConfusions(): String =
    replace("轉職", "转职")
        .replace("转直", "转职")
        .replace("软直", "转职")
        .replace("专职", "转职")
        .replace("接受他几部", "什么时候转职")
        .replace("那这些角色", "哪些角色")
        .replace("那这些人物", "哪些角色")
        .replace("那这些队员", "哪些角色")
        .replace("那些角色", "哪些角色")
        .replace("那些人物", "哪些角色")
        .replace("那些队员", "哪些角色")
        .replace("哪些人物", "哪些角色")
        .replace("哪些队员", "哪些角色")
```

Rationale:

- Keep the fix deterministic and local to query normalization.
- Avoid changing GKP factual rows for an ASR engine artifact.
- Normalize before intent classification, retrieval, and answer-template matching.
- Do not map generic `那些` globally, because phrases like `那些道具` can be legitimate non-question references.

- [ ] **Step 2: Run focused tests**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest \
  --tests com.retrosprite.app.domain.intent.QuestionIntentClassifierTest \
  --tests com.retrosprite.app.data.retrieval.SampleShiningForceIIRetrievalGoldenTest \
  --tests com.retrosprite.app.domain.SampleShiningForceIIQuestionPipelineTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Commit the normalization fix**

```bash
git add app/src/main/kotlin/com/retrosprite/app/domain/intent/NaturalQuestionFrame.kt
git commit -m "fix: normalize hotkey voice team-building variants"
```

---

### Task 3: Add Durable GKP Golden Cases For The Real-Device Variants

**Files:**
- Modify: `app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl`

- [ ] **Step 1: Append golden cases**

Append these JSONL rows to `qa_goldens.jsonl`:

```jsonl
{"qa_id":"qa.sf2.asr.team-build-na-xie.zh","language":"zh","question":"那些角色适合培养","game_id":"shining_force_ii_md","spoiler_level":"light","progress_gate":"start","expected_entity_ids":["strategy.team-build-general"],"expected_answer_contains":["通用原则","治疗"],"source_refs":["sf2.project_mechanics"]}
{"qa_id":"qa.sf2.asr.team-build-na-zhexie.zh","language":"zh","question":"那这些角色适合培养","game_id":"shining_force_ii_md","spoiler_level":"light","progress_gate":"start","expected_entity_ids":["strategy.team-build-general"],"expected_answer_contains":["通用原则","治疗"],"source_refs":["sf2.project_mechanics"]}
{"qa_id":"qa.sf2.asr.team-build-renwu.zh","language":"zh","question":"那些人物适合培养","game_id":"shining_force_ii_md","spoiler_level":"light","progress_gate":"start","expected_entity_ids":["strategy.team-build-general"],"expected_answer_contains":["通用原则","治疗"],"source_refs":["sf2.project_mechanics"]}
```

- [ ] **Step 2: Run the GKP fixture and golden tests**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest \
  --tests com.retrosprite.app.gkp.GkpV0FixtureLintTest \
  --tests com.retrosprite.app.data.retrieval.SampleShiningForceIIRetrievalGoldenTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Commit the golden cases**

```bash
git add app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl
git commit -m "test: add shining force voice asr golden cases"
```

---

### Task 4: Make The Answer HUD Text Capacity Explicit

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/overlay/AndroidHotkeyVoiceOverlayRenderer.kt`
- Modify: `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceOverlayRendererTest.kt`

- [ ] **Step 1: Replace renderer sizing tests**

Replace `HotkeyVoiceOverlayRendererTest` with:

```kotlin
package com.retrosprite.app.ui.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HotkeyVoiceOverlayRendererTest {

    @Test
    fun `no evidence answer card has room for suggested questions`() {
        val normal = HotkeyVoiceOverlayPhase.NoEvidence.answerCardSpec(fontScale = 1.0f)
        assertEquals(6, normal.maxLines)
        assertTrue(normal.heightDp >= 184)

        val largeText = HotkeyVoiceOverlayPhase.NoEvidence.answerCardSpec(fontScale = 1.35f)
        assertEquals(6, largeText.maxLines)
        assertTrue(largeText.heightDp > normal.heightDp)
    }

    @Test
    fun `regular answer card remains compact but can wrap chinese short answers`() {
        val normal = HotkeyVoiceOverlayPhase.Speaking.answerCardSpec(fontScale = 1.0f)
        assertEquals(3, normal.maxLines)
        assertTrue(normal.heightDp >= 124)

        val largeText = HotkeyVoiceOverlayPhase.Speaking.answerCardSpec(fontScale = 1.35f)
        assertEquals(3, largeText.maxLines)
        assertTrue(largeText.heightDp > normal.heightDp)
    }

    @Test
    fun `error answer card stays short`() {
        val normal = HotkeyVoiceOverlayPhase.Error.answerCardSpec(fontScale = 1.0f)
        assertEquals(1, normal.maxLines)
        assertEquals(112, normal.heightDp)
    }
}
```

- [ ] **Step 2: Run the renderer test and verify it fails**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest --tests com.retrosprite.app.ui.overlay.HotkeyVoiceOverlayRendererTest
```

Expected before implementation:

```text
FAILED
Unresolved reference: answerCardSpec
```

- [ ] **Step 3: Add an answer-card spec and use it in layout**

In `AndroidHotkeyVoiceOverlayRenderer.kt`, add this data class near the current answer sizing functions:

```kotlin
internal data class HotkeyVoiceAnswerCardSpec(
    val heightDp: Int,
    val maxLines: Int,
)
```

Change `showAnswerWindow` to accept the full render state:

```kotlin
    override fun render(state: HotkeyVoiceOverlayRenderState) {
        waveView.render(state)
        if (state.answerText.isNullOrBlank()) {
            hideAnswerWindow()
            return
        }
        answerView.render(state)
        showAnswerWindow(state)
    }

    private fun showAnswerWindow(state: HotkeyVoiceOverlayRenderState) {
        if (!isShown) return
        val spec = state.phase.answerCardSpec(
            fontScale = appContext.resources.configuration.fontScale,
        )
        answerParams.width = answerWidthPx()
        answerParams.height = appContext.dp(spec.heightDp)
        if (isAnswerShown) {
            runCatching {
                windowManager.updateViewLayout(answerView, answerParams)
            }.onFailure { error ->
                Log.w(TAG, "Unable to update hotkey answer overlay", error)
            }
            return
        }
        runCatching {
            windowManager.addView(answerView, answerParams)
            isAnswerShown = true
        }.onFailure { error ->
            Log.w(TAG, "Unable to show hotkey answer overlay", error)
        }
    }
```

Replace the old `answerOverlayHeightDp()` and `answerTextMaxLines()` functions with:

```kotlin
internal fun HotkeyVoiceOverlayPhase.answerCardSpec(fontScale: Float): HotkeyVoiceAnswerCardSpec {
    val safeFontScale = fontScale.coerceIn(1.0f, 1.6f)
    return when (this) {
        HotkeyVoiceOverlayPhase.NoEvidence -> HotkeyVoiceAnswerCardSpec(
            heightDp = (184 * safeFontScale).toInt().coerceAtMost(260),
            maxLines = 6,
        )

        HotkeyVoiceOverlayPhase.Speaking,
        HotkeyVoiceOverlayPhase.Thinking -> HotkeyVoiceAnswerCardSpec(
            heightDp = (124 * safeFontScale).toInt().coerceAtMost(188),
            maxLines = 3,
        )

        HotkeyVoiceOverlayPhase.Error -> HotkeyVoiceAnswerCardSpec(
            heightDp = 112,
            maxLines = 1,
        )

        HotkeyVoiceOverlayPhase.Wake,
        HotkeyVoiceOverlayPhase.Listening,
        HotkeyVoiceOverlayPhase.Muted -> HotkeyVoiceAnswerCardSpec(
            heightDp = 112,
            maxLines = 2,
        )
    }
}
```

Update `drawAnswerText` to use the spec:

```kotlin
    private fun drawAnswerText(canvas: Canvas, answer: String, phase: HotkeyVoiceOverlayPhase, w: Float) {
        val spec = phase.answerCardSpec(context.resources.configuration.fontScale)
        val left = context.dp(74f)
        val top = context.dp(18f)
        val textWidth = (w - left - context.dp(22f)).toInt().coerceAtLeast(1)
        val maxLines = spec.maxLines
        val availableHeight = (height - top - context.dp(18f)).toInt().coerceAtLeast(1)
        val layout = buildFittedAnswerLayout(
            answer = answer,
            textWidth = textWidth,
            maxLines = maxLines,
            availableHeight = availableHeight,
        )
        canvas.save()
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restore()
    }
```

Add this helper inside `HotkeyVoiceAnswerView`:

```kotlin
    private fun buildFittedAnswerLayout(
        answer: String,
        textWidth: Int,
        maxLines: Int,
        availableHeight: Int,
    ): StaticLayout {
        val originalSize = answerTextPaint.textSize
        val sizes = floatArrayOf(
            context.sp(18),
            context.sp(17),
            context.sp(16),
            context.sp(15),
        )
        var selected = buildAnswerLayout(answer, textWidth, maxLines)
        for (size in sizes) {
            answerTextPaint.textSize = size
            val candidate = buildAnswerLayout(answer, textWidth, maxLines)
            selected = candidate
            if (candidate.height <= availableHeight) break
        }
        answerTextPaint.textSize = originalSize
        return selected
    }

    private fun buildAnswerLayout(
        answer: String,
        textWidth: Int,
        maxLines: Int,
    ): StaticLayout =
        StaticLayout.Builder
            .obtain(answer, 0, answer.length, answerTextPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .setLineSpacing(context.dp(1.5f), 1.10f)
            .setMaxLines(maxLines)
            .build()
```

- [ ] **Step 4: Run renderer tests**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest --tests com.retrosprite.app.ui.overlay.HotkeyVoiceOverlayRendererTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit the HUD sizing fix**

```bash
git add \
  app/src/main/kotlin/com/retrosprite/app/ui/overlay/AndroidHotkeyVoiceOverlayRenderer.kt \
  app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceOverlayRendererTest.kt
git commit -m "fix: expand hotkey answer hud text capacity"
```

---

### Task 5: Keep Voice Overlay Semantics Stable

**Files:**
- Modify: `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionControllerTest.kt`

- [ ] **Step 1: Strengthen no-evidence overlay assertions**

Update the existing `hotkey voice no evidence shows suggested questions in answer card` test by adding these assertions after `val noEvidenceState = ...`:

```kotlin
        assertEquals(HotkeyVoiceOverlayPhase.NoEvidence, noEvidenceState.phase)
        assertEquals("NO RELIABLE EVIDENCE", noEvidenceState.message)
        assertEquals(noEvidenceDetail, noEvidenceState.answerText)
        assertEquals(emptyList<String>(), noEvidenceState.sourceIds)
```

This preserves the current behavior: no-evidence detail text, including suggested questions, is passed to the answer card; only the card sizing/rendering changes.

- [ ] **Step 2: Run the voice overlay tests**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest --tests com.retrosprite.app.ui.overlay.HotkeyVoiceQuestionControllerTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Commit the semantic guard**

```bash
git add app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionControllerTest.kt
git commit -m "test: guard no-evidence voice overlay text"
```

---

### Task 6: Run The Full Local Regression Set

**Files:**
- No code changes.

- [ ] **Step 1: Run focused QA-related JVM tests**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest \
  --tests com.retrosprite.app.domain.intent.QuestionIntentClassifierTest \
  --tests com.retrosprite.app.data.retrieval.SampleShiningForceIIRetrievalGoldenTest \
  --tests com.retrosprite.app.domain.SampleShiningForceIIQuestionPipelineTest \
  --tests com.retrosprite.app.ui.overlay.HotkeyVoiceOverlayRendererTest \
  --tests com.retrosprite.app.ui.overlay.HotkeyVoiceQuestionControllerTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 2: Run the app test suite**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Build the APK**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 4: Commit after regression**

```bash
git status --short
git add docs/superpowers/plans/2026-05-23-hotkey-voice-qa-hud-reliability.md
git commit -m "docs: plan hotkey voice qa hud reliability fix"
```

Commit code changes only if they are not already committed in earlier tasks:

```bash
git add app/src/main app/src/test app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl
git commit -m "fix: improve hotkey voice qa reliability"
```

---

### Task 7: Install And Verify On RG476H

**Files:**
- No repo changes.

- [ ] **Step 1: Confirm device connection**

Run:

```bash
adb devices -l
```

Expected device:

```text
RG476H01077813 device
```

- [ ] **Step 2: Install the debug APK**

Run:

```bash
adb -s RG476H01077813 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s RG476H01077813 shell am start -n com.retrosprite.app/.MainActivity
```

Expected:

```text
Performing Streamed Install
Success
Starting: Intent { cmp=com.retrosprite.app/.MainActivity }
```

- [ ] **Step 3: Confirm endpoint health**

Run:

```bash
adb -s RG476H01077813 forward tcp:4404 tcp:4404
curl -sS --max-time 5 http://127.0.0.1:4404/health
```

Expected:

```json
{"status":"ok","version":"0.1.0"}
```

- [ ] **Step 4: Verify the exact ASR variant via debug endpoint**

Run:

```bash
curl -sS --max-time 5 -X POST 'http://127.0.0.1:4404/debug/ask?output=text' \
  -H 'Content-Type: application/json' \
  --data '{"image":"","label":"mega_drive__光明力量2","question":"那些角色适合培养","state":{"paused":1}}'
```

Expected response contains:

```text
通用原则
来源：sf2.project_mechanics
```

- [ ] **Step 5: Manual hotkey voice verification**

On the device:

1. Launch RetroArch with `mega_drive__光明力量2`.
2. Trigger the RetroArch AI Service hotkey.
3. Ask: `哪些角色适合培养`.
4. Also ask naturally: `那些角色适合培养`.

Then run:

```bash
curl -sS --max-time 5 http://127.0.0.1:4404/debug/latest-request | jq .
```

Expected latest hotkey voice result:

```json
{
  "ok": true,
  "question_source": "hotkey_voice",
  "answer_type": "team_build",
  "pipeline_stage": "evidence",
  "llm_status": "skipped",
  "source_ids": ["sf2.project_mechanics"]
}
```

The visible bottom-left HUD should show a readable answer phrase. For no-evidence cases it should show the no-evidence message plus suggested questions without the text area being cut off at the bottom of the card.

- [ ] **Step 6: Capture screenshot evidence**

Run immediately while the HUD is visible:

```bash
adb -s RG476H01077813 shell screencap -p /sdcard/retrosprite_hotkey_hud.png
adb -s RG476H01077813 pull /sdcard/retrosprite_hotkey_hud.png /tmp/retrosprite_hotkey_hud.png
```

Expected:

```text
/tmp/retrosprite_hotkey_hud.png
```

Review the screenshot manually. The answer text must not be clipped by the answer-card bottom edge.

---

## Acceptance Criteria

- `那些角色适合培养` resolves to `strategy.team-build-general` and cites `sf2.project_mechanics`.
- `那这些角色适合培养` resolves to the same evidence.
- `哪些角色适合培养` continues to work.
- `什么时候转职`, `接受他几部这个角色`, and `游戏怎么玩` continue to work.
- The answer remains zero-LLM for these local evidence cases.
- `debug/latest-request` for the team-building hotkey question reports `pipeline_stage=evidence`.
- The bottom-left HUD answer card is readable on `1280x960`, density `280`, landscape orientation.
- No new permissions, network dependencies, ROM access, or RetroArch core changes are introduced.

## Self-Review

- Spec coverage: ASR transcript correctness is handled in Tasks 1-3; answer correctness is handled in Tasks 1, 2, 3, 6, and 7; HUD text hiding is handled in Tasks 4, 5, and 7.
- Placeholder scan: The plan contains concrete paths, code snippets, commands, and expected outputs.
- Type consistency: New `HotkeyVoiceAnswerCardSpec` is consumed by `answerCardSpec(fontScale)` and tested by `HotkeyVoiceOverlayRendererTest`; `normalizeObservedAsrConfusions()` remains the single production entry point for observed ASR replacements.
