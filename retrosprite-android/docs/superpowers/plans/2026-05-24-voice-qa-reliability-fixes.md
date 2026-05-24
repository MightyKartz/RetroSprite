# Voice QA Reliability Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the real-device voice QA regressions where ASR drops trailing words, follow-up questions are visually truncated, noisy entity questions miss templates, no-evidence suggestions are not structured, and finished overlay diagnostics still look active.

**Architecture:** Keep speech as a thin shell over the text-first local GKP pipeline. Add a tail-grace gate around Sherpa endpoint detection, make overlay answer text preserve structured follow-up questions, repair the bundled GKP intent metadata, and keep diagnostics truthful and machine-readable.

**Tech Stack:** Kotlin, Android AudioRecord, sherpa-onnx streaming recognizer, RetroSprite GKP assets, Room request logs, JUnit4, kotlinx-coroutines-test.

**Implementation Status:** Implemented and verified on 2026-05-24 with focused regression tests, full `testDebugUnitTest`, `:app:assembleDebug`, and `git diff --check`.

---

## Evidence And Root Causes

- Real-device ASR finalized `气合之玉怎么用` as `气合之` and `米斯里鲁银有什么用` as `米斯里鲁`, which points to early finalization after Sherpa endpoint detection.
- `SherpaOnnxVoiceInputProvider.decodeSamples()` currently stops recording immediately when `recognizer.isEndpoint(stream)` is true and the partial text is nonblank.
- `HotkeyVoiceQuestionController.toOverlayAnswerText()` truncates successful answers to 96 characters before appending follow-up questions, and `AndroidHotkeyVoiceOverlayRenderer` can also ellipsize to `...` if max lines are too small.
- The bundled `item.vigor-ball` template lacks `intent:"usage"`, so the real GKP path does not use the entity-anchored noisy-tail matching that unit fixtures already cover.
- No-evidence suggested questions are rendered inside `answer_detail`, but `ResponseDiagnostics.suggestedQuestions` can stay empty, causing `/debug/latest-request` to report `suggested_questions=null`.
- `HotkeyVoiceOverlayCoordinator.debugSnapshot()` correctly reports `lifecycle_phase=finished`, but leaves `render_phase=speaking` and message `正在朗读答案`, which is confusing during QA.

## Files

- Create: `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaEndpointCommitGate.kt`
  - Owns endpoint tail-grace timing and protects against early finalization.
- Test: `app/src/test/kotlin/com/retrosprite/app/ui/integration/SherpaEndpointCommitGateTest.kt`
  - Verifies endpoint does not commit until the tail grace expires and resets when partial text grows.
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxVoiceInputProvider.kt`
  - Use `SherpaEndpointCommitGate` and extend final silence padding.
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionController.kt`
  - Preserve the short answer and full follow-up questions separately instead of truncating the whole answer block first.
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/overlay/AndroidHotkeyVoiceOverlayRenderer.kt`
  - Give successful answer cards enough lines for follow-up questions and avoid renderer ellipsis for the expected follow-up block.
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceOverlayCoordinator.kt`
  - Normalize finished debug snapshot fields to `render_phase=finished` and `message=已结束`.
- Modify: `app/src/main/assets/gkp/shining-force-ii-md/knowledge/items.jsonl`
  - Add `intent:"usage"` to the Vigor Ball template.
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/models/AnswerResult.kt`
  - Ensure no-evidence fallback suggestions stay structured when already present in text.
- Modify: `app/src/main/kotlin/com/retrosprite/app/data/retrieval/LocalKnowledgeRetrievalPipeline.kt`
  - Deduplicate suggestion groups by entity + intent + question semantics so alias variants do not consume all three slots after a hit.
- Tests:
  - `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionControllerTest.kt`
  - `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceOverlayRendererTest.kt`
  - `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceOverlayCoordinatorTest.kt`
  - `app/src/test/kotlin/com/retrosprite/app/domain/SampleShiningForceIIQuestionPipelineTest.kt`
  - `app/src/test/kotlin/com/retrosprite/app/domain/policy/AnswerComposerTest.kt`
  - `app/src/test/kotlin/com/retrosprite/app/data/retrieval/LocalKnowledgeRetrievalPipelineTest.kt`

## Acceptance Criteria

- Sherpa endpoint detection waits for a tail-grace window before final transcript publication.
- Successful overlay answers can show:

```text
Vigor Ball ...

你还可以问：
· 气合之玉在哪里？
· 谁适合转 Master Monk？
```

without `...` truncating the follow-up lines.
- Bundled GKP text query `气合之玉怎么又` reaches `item.vigor-ball` instead of no-evidence.
- No-evidence answers expose `suggestedQuestions` as structured diagnostics, not only embedded text.
- Suggestions after a successful hit prefer next-step questions over equivalent alias variants.
- Finished overlay debug snapshots read as finished, not speaking.

## Tasks

### Task 1: Sherpa Endpoint Tail Grace

- [ ] **Step 1: Write failing gate tests**

Add `SherpaEndpointCommitGateTest` cases:

```kotlin
val gate = SherpaEndpointCommitGate(tailGraceMillis = 650L)
assertFalse(gate.shouldCommit(nowMillis = 1_000L, endpointDetected = true, partialText = "气合之"))
assertFalse(gate.shouldCommit(nowMillis = 1_500L, endpointDetected = true, partialText = "气合之玉"))
assertTrue(gate.shouldCommit(nowMillis = 2_200L, endpointDetected = true, partialText = "气合之玉"))
```

- [ ] **Step 2: Verify red**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest --tests com.retrosprite.app.ui.integration.SherpaEndpointCommitGateTest
```

- [ ] **Step 3: Implement gate and wire provider**

Create the gate, replace immediate `break` in `decodeSamples()`, and increase `finishStream()` silence padding to `0.8s`.

- [ ] **Step 4: Verify green**

Run the same test and `SherpaOnnxRecognizerFactoryTest`.

### Task 2: Full Follow-Up Visibility In Overlay

- [ ] **Step 1: Write failing overlay tests**

Extend `HotkeyVoiceQuestionControllerTest` and `HotkeyVoiceOverlayRendererTest` so successful answers with three follow-ups preserve all follow-up lines and card capacity is large enough.

- [ ] **Step 2: Verify red**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest --tests com.retrosprite.app.ui.overlay.HotkeyVoiceQuestionControllerTest --tests com.retrosprite.app.ui.overlay.HotkeyVoiceOverlayRendererTest
```

- [ ] **Step 3: Implement overlay text preservation**

Do not pre-truncate the whole successful answer block. Keep the spoken short answer clean, append up to three full follow-up questions, and increase successful answer max lines/height only when follow-ups are present.

- [ ] **Step 4: Verify green**

Run the same overlay tests.

### Task 3: Vigor Ball Noisy Entity Hit In Bundled GKP

- [ ] **Step 1: Write failing bundled pipeline test**

Add a Shining Force II pipeline test asserting `气合之玉怎么又` returns evidence from `sf2.promotion`.

- [ ] **Step 2: Verify red**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest --tests com.retrosprite.app.domain.SampleShiningForceIIQuestionPipelineTest
```

- [ ] **Step 3: Add missing template intent**

Add `intent:"usage"` to `template.sf2.vigor-ball.zh`.

- [ ] **Step 4: Verify green**

Run the same test.

### Task 4: Structured No-Evidence Suggestions And Better Related Questions

- [ ] **Step 1: Write failing tests**

Add tests that no-evidence keeps `suggestedQuestions` structured and that equivalent alias variants do not consume all successful-hit suggestions.

- [ ] **Step 2: Verify red**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest --tests com.retrosprite.app.domain.policy.AnswerComposerTest --tests com.retrosprite.app.data.retrieval.LocalKnowledgeRetrievalPipelineTest
```

- [ ] **Step 3: Implement structured extraction and semantic suggestion grouping**

Extract no-evidence `你可以这样问` lines into `AnswerResult.suggestedQuestions` when structured suggestions are empty, and group suggestions by entity/intent/usage cue so alias-only variants collapse.

- [ ] **Step 4: Verify green**

Run the same tests.

### Task 5: Finished Overlay Diagnostics

- [ ] **Step 1: Write failing coordinator test**

Update `HotkeyVoiceOverlayCoordinatorTest` to expect `render_phase=finished` and `message=已结束` after `finishVoiceSession()`.

- [ ] **Step 2: Verify red**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest --tests com.retrosprite.app.ui.overlay.HotkeyVoiceOverlayCoordinatorTest
```

- [ ] **Step 3: Implement debug snapshot normalization**

When state is finished, return a finished render/message while retaining transcript, sources, ASR diagnostics, and finish reason.

- [ ] **Step 4: Verify green**

Run the same test.

### Task 6: Final Verification

- [ ] **Step 1: Run focused regression tests**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest --tests com.retrosprite.app.ui.integration.SherpaEndpointCommitGateTest --tests com.retrosprite.app.ui.integration.SherpaOnnxRecognizerFactoryTest --tests com.retrosprite.app.ui.overlay.HotkeyVoiceQuestionControllerTest --tests com.retrosprite.app.ui.overlay.HotkeyVoiceOverlayRendererTest --tests com.retrosprite.app.ui.overlay.HotkeyVoiceOverlayCoordinatorTest --tests com.retrosprite.app.domain.SampleShiningForceIIQuestionPipelineTest --tests com.retrosprite.app.domain.policy.AnswerComposerTest --tests com.retrosprite.app.data.retrieval.LocalKnowledgeRetrievalPipelineTest
```

- [ ] **Step 2: Build APK**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug
```

- [ ] **Step 3: Diff hygiene**

```bash
git diff --check -- app/src/main app/src/test docs/superpowers/plans/2026-05-24-voice-qa-reliability-fixes.md
```
