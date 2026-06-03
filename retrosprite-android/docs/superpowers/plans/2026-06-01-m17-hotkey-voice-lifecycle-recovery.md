# M17.1 Hotkey Voice Lifecycle Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recover the RG476H real-playback hotkey voice path so a RetroArch hotkey session reliably captures speech, submits a fresh `hotkey_voice` request, and reaches GKP evidence answers again.

**Architecture:** Keep the product route unchanged: RetroArch AI Service hotkey -> RetroSprite overlay -> short foreground microphone session -> local Paraformer ASR -> GKP retrieval -> AnswerPolicy -> overlay/TTS. The fix should be evidence-first: preserve the failing playback evidence, add capture diagnostics, reproduce with one row, then patch the smallest lifecycle or audio-input bug needed.

**Tech Stack:** Kotlin coroutines, `AudioRecord`, sherpa-onnx Paraformer, `SherpaOnnxVoiceInputProvider`, `SherpaEndpointCommitGate`, `HotkeyVoiceQuestionController`, `HotkeyVoiceOverlayCoordinator`, adb/logcat, `scripts/hotkey_voice_qa_batch.sh`, and existing JVM/script tests.

---

## Research Basis

- RetroArch AI Service is still the correct trigger: Libretro documents that the hotkey sends the current game screen to the configured AI Service endpoint, which matches RetroSprite's bounded, user-initiated flow: https://docs.libretro.com/guides/ai-service/
- Android microphone capture is sensitive to foreground-service state and audio-focus/input sharing. Android documentation says microphone/background capture depends on foreground app or foreground-service behavior, and active audio capture can affect other apps: https://developer.android.com/media/platform/sharing-audio-input
- Android 14 foreground services require the correct declared service type and permissions, including microphone-specific foreground service handling: https://developer.android.com/about/versions/14/changes/fgs-types-required

## Current Evidence

- `RUN_DEVICE=1 RUN_VOICE=0 ./scripts/rc_hardening_check.sh` passed on RG476H at 2026-06-01 14:06 CST, so endpoint protocol and bundled GKP `/debug/ask` are healthy.
- `RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 ... ./scripts/hotkey_voice_qa_batch.sh` failed at 2026-06-01 14:49 CST across 7 selected cases.
- Evidence directory: `build/hotkey-voice-qa/20260601-144348/`
- Failure signature: `mic_live=true` during ready, then `finish_reason=muted_recovery`, `asr_commit_reason=blank_partial`, `asr_endpoint_armed=false`, and no fresh `/debug/latest-request`.
- Follow-up diagnostic evidence: `build/hotkey-voice-qa/20260601-153730/` reproduced the failure at Mac output volume 13 with `reads=1988`, `read_errors=0`, and very low `peak=0.0060507967`; `build/hotkey-voice-qa/20260601-153855/` passed the same one-case probe at Mac output volume 90.
- Current backlog: `docs/qa-feedback/gkp-quality-backlog.md` now records 3 scoped `asr_variant` items from `build/hotkey-voice-qa/20260601-153946/`, not voice lifecycle gaps.

## Files To Touch

- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/viewmodel/UiContracts.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxVoiceInputProvider.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceOverlayCoordinator.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/endpoint/model/RetroArchResponse.kt`
- Modify: `app/src/test/kotlin/com/retrosprite/app/ui/integration/SherpaEndpointCommitGateTest.kt`
- Modify: `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceOverlayCoordinatorTest.kt`
- Modify: `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionControllerTest.kt`
- Modify: `app/src/test/kotlin/com/retrosprite/app/endpoint/RetroArchEndpointServerTest.kt`
- Modify: `scripts/hotkey_voice_qa_batch.sh`
- Modify: `scripts/tests/test_hotkey_voice_qa_cases.py`
- Modify: `docs/qa-feedback/rc-device-matrix.md`
- Modify: `docs/RELEASE_CANDIDATE_CHECKLIST.md`
- Modify: `docs/TEST_COVERAGE.md`

## Task 1: Preserve And Classify The Failure

**Files:**
- Modify: `docs/qa-feedback/rc-device-matrix.md`
- Modify: `docs/qa-feedback/gkp-quality-backlog.md`
- Modify: `docs/RELEASE_CANDIDATE_CHECKLIST.md`

- [x] **Step 1: Record the 14:49 playback failure**

Evidence already captured:

```text
build/hotkey-voice-qa/20260601-144348/results.tsv
build/hotkey-voice-qa/20260601-144348/*.overlay.ready.json
build/hotkey-voice-qa/20260601-144348/*.overlay.after.json
build/hotkey-voice-qa/20260601-144348/*.latest.json
```

Expected: docs state that the playback failure is a `voice_lifecycle_gap`, not a GKP content failure.

- [x] **Step 2: Capture one post-failure device evidence folder**

Run with the same device still connected:

```bash
./scripts/rc_device_evidence.sh
```

Expected: a new `build/rc-device-evidence/<timestamp>/` folder includes `/debug/hotkey-voice-overlay`, `/debug/latest-request`, AppOps, package, and window snapshots.

2026-06-01 15:03 CST note: captured `build/rc-device-evidence/20260601-150333/`. The overlay snapshot still reports `finish_reason=muted_recovery`, `asr_commit_reason=blank_partial`, and `asr_endpoint_armed=false`; `/debug/latest-request` is an older debug request, not a fresh playback submission.

## Task 2: Add Audio Capture Diagnostics To The Overlay Debug Snapshot

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/viewmodel/UiContracts.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxVoiceInputProvider.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceOverlayCoordinator.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/endpoint/model/RetroArchResponse.kt`
- Modify: `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceOverlayCoordinatorTest.kt`
- Modify: `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionControllerTest.kt`
- Modify: `app/src/test/kotlin/com/retrosprite/app/endpoint/RetroArchEndpointServerTest.kt`

- [x] **Step 1: Add failing tests for capture counters**

Add assertions that `/debug/hotkey-voice-overlay` can expose these nullable fields:

```kotlin
assertEquals(48000L, snapshot.asr_sample_count)
assertEquals(12, snapshot.asr_audio_read_count)
assertEquals(0, snapshot.asr_audio_read_error_count)
assertEquals(0.18f, snapshot.asr_peak_amplitude)
assertEquals(0.04f, snapshot.asr_last_frame_amplitude)
```

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests com.retrosprite.app.ui.overlay.HotkeyVoiceOverlayCoordinatorTest \
  --tests com.retrosprite.app.endpoint.RetroArchEndpointServerTest
```

Expected before implementation: tests fail because the fields do not exist.

- [x] **Step 2: Publish counters from `SherpaOnnxVoiceInputProvider`**

Update `UiVoiceInputState` and sample processing so every read updates:

```kotlin
asrSampleCount = previous.asrSampleCount + read
asrAudioReadCount = previous.asrAudioReadCount + 1
asrAudioReadErrorCount = previous.asrAudioReadErrorCount
asrPeakAmplitude = maxOf(previous.asrPeakAmplitude ?: 0f, amplitude)
asrLastFrameAmplitude = amplitude
```

Expected: if playback fails again, evidence distinguishes `AudioRecord` silence from recognizer/endpoint failure.

- [x] **Step 3: Surface counters through overlay diagnostics**

Thread the new fields through `HotkeyVoiceQuestionController -> HotkeyVoiceOverlayCoordinator -> HotkeyVoiceOverlayDebugSnapshot`.

Expected: `scripts/hotkey_voice_qa_batch.sh` can capture the counters from `*.overlay.after.json`.

2026-06-01 implementation note: `UiVoiceInputState`, `SherpaOnnxVoiceInputProvider`, `HotkeyVoiceQuestionController`, `HotkeyVoiceOverlayCoordinator`, and `/debug/hotkey-voice-overlay` now carry `asr_sample_count`, `asr_audio_read_count`, `asr_audio_read_error_count`, `asr_peak_amplitude`, and `asr_last_frame_amplitude`. Focused JVM verification passed with:

```bash
JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home" \
./gradlew :app:testDebugUnitTest \
  --tests com.retrosprite.app.ui.overlay.HotkeyVoiceOverlayCoordinatorTest \
  --tests com.retrosprite.app.ui.overlay.HotkeyVoiceQuestionControllerTest \
  --tests com.retrosprite.app.endpoint.RetroArchEndpointServerTest \
  --no-daemon --console=plain
```

## Task 3: Make The Playback Runner Wait For ASR Decode Readiness

**Files:**
- Modify: `scripts/hotkey_voice_qa_batch.sh`
- Modify: `scripts/tests/test_hotkey_voice_qa_cases.py`

- [x] **Step 1: Extend ready polling beyond `mic_live=true`**

Teach the script to treat the overlay as playback-ready only when either:

```text
mic_live=true and asr_audio_read_count > 0
```

or, if counters are unavailable:

```text
mic_live=true
```

Expected: older APKs still run, newer APKs avoid speaking before the ASR loop has started reading samples.

- [x] **Step 2: Store capture counters in `results.tsv`**

Add columns:

```text
asr_sample_count	asr_audio_read_count	asr_audio_read_error_count	asr_peak_amplitude	asr_last_frame_amplitude
```

Expected: failed rows show whether the device heard audio.

- [x] **Step 3: Update script tests**

Run:

```bash
python3 -m unittest scripts/tests/test_hotkey_voice_qa_cases.py
```

Expected: script contract tests pass and verify the new columns are present.

2026-06-01 implementation note: `scripts/hotkey_voice_qa_batch.sh` now waits for `asr_audio_read_count > 0` when the field is present, records all five capture counters in `results.tsv`, and remains backward compatible with older APKs that do not expose the counters. Verification passed with:

```bash
bash -n scripts/hotkey_voice_qa_batch.sh
SELF_TEST=1 scripts/hotkey_voice_qa_batch.sh
DRY_RUN=1 scripts/hotkey_voice_qa_batch.sh
python3 -m unittest discover scripts/tests
python3 scripts/rc_release_audit.py
git diff --check
```

## Task 4: Patch The Smallest Proven Runtime Cause

**Files:**
- Modify only the file identified by Task 2/3 evidence.
- Candidate files:
  - `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxVoiceInputProvider.kt`
  - `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaEndpointCommitGate.kt`
  - `app/src/main/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionController.kt`

Current status: the runtime lifecycle is no longer the leading blocker. The new diagnostics showed that low Mac playback volume produced near-silent capture, while Mac output volume 90 allowed fresh `hotkey_voice` submission and evidence answers. The remaining 7-case failures are GKP scoped ASR variants.

- [ ] **Step 1: If audio read count is zero, fix microphone lifecycle**

Likely checks:

```text
record.recordingState == AudioRecord.RECORDSTATE_RECORDING
foreground service started before recording
AppOps still allows RECORD_AUDIO during overlay session
```

Expected: playback evidence shows nonzero read count before trying ASR tuning.

- [x] **Step 2: If audio read count is nonzero but peak amplitude is near zero, fix playback/test calibration**

Likely changes:

```text
increase PRE_SPEAK_SECONDS only if readiness lag is proven
increase QA playback gain or use generated wav backend
record recommended device/speaker placement in rc-device-matrix
```

Expected: `asr_peak_amplitude` rises above the commit gate threshold during the spoken phrase.

2026-06-01 note: low-volume evidence at `build/hotkey-voice-qa/20260601-153730/` showed nonzero reads but `peak=0.0060507967`; rerunning at Mac output volume 90 passed `golden_sun_ivan_observed` with fresh evidence at `build/hotkey-voice-qa/20260601-153855/`. Record Mac output volume in future playback runs and keep the RG476H microphone close to the MacBook speaker.

- [ ] **Step 3: If audio and amplitude are healthy but partial text stays blank, tune ASR commit/flush only**

Write a failing unit test before changing thresholds:

```kotlin
@Test
fun `non blank audio without partial keeps listening until timeout diagnostics explain recognizer blank`() {
    val decision = gate.evaluate(
        nowMillis = 2_000L,
        endpointDetected = false,
        partialText = "",
        frameAmplitude = 0.08f,
    )
    assertEquals(SherpaEndpointCommitState.KeepListening, decision.state)
    assertEquals("blank_partial_with_voice_activity", decision.reason)
}
```

Expected: diagnostics become more precise; do not fake or complete missing words.

## Task 5: Re-run The Release-Blocking Voice Gate

**Files:**
- Modify: `docs/qa-feedback/rc-device-matrix.md`
- Modify: `docs/RELEASE_CANDIDATE_CHECKLIST.md`
- Modify: `docs/qa-feedback/gkp-quality-backlog.md`

- [x] **Step 1: Run a one-case recovery probe**

```bash
RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 \
CASE_FILTER=golden_sun_ivan_observed \
VOICE=Tingting SAY_RATE=96 PRE_SPEAK_SECONDS=3 POST_CASE_SECONDS=10 \
POLL_ATTEMPTS=40 POLL_INTERVAL_SECONDS=2 READY_ATTEMPTS=20 READY_INTERVAL_SECONDS=1 STRICT=1 \
./scripts/hotkey_voice_qa_batch.sh
```

Expected: one row submits a fresh `/debug/latest-request` with `question_source=hotkey_voice`.

2026-06-01 note: `golden_sun_ivan_observed` passed at Mac output volume 90. Evidence: `build/hotkey-voice-qa/20260601-153855/`.

- [ ] **Step 2: Run the 7-case playback matrix**

```bash
RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 \
CASE_FILTER=sf2_vigor_ball_observed,golden_sun_ivan_observed,chrono_marle_observed,chrono_atb_observed,ff6_magicite_observed,langrisser_commander_smoke,phantasy_star_tech_skill_smoke \
VOICE=Tingting SAY_RATE=96 PRE_SPEAK_SECONDS=3 POST_CASE_SECONDS=10 POLL_ATTEMPTS=40 POLL_INTERVAL_SECONDS=2 READY_ATTEMPTS=20 READY_INTERVAL_SECONDS=1 STRICT=1 \
./scripts/hotkey_voice_qa_batch.sh
```

Expected: each row either reaches `pipeline_stage=evidence` with expected source ids or records a narrower ASR/GKP failure that is no longer `muted_recovery` / `blank_partial`.

2026-06-01 historical note: the 7-case matrix at Mac output volume 90 reached fresh `hotkey_voice` submissions for all rows and passed 4/7. Remaining failures at that checkpoint were narrower GKP scoped ASR variants: `纳尔士`, `核实系统是什么`, and `只挥官是什么`. Evidence: `build/hotkey-voice-qa/20260601-153946/`. Do not use that older 3-case scope for the current replay gate; current replay scope is generated by `docs/qa-feedback/gkp-asr-patch-voice-replay-handoff.md`.

- [ ] **Step 3: Update release docs**

Only if Step 2 passes, check the release item:

```markdown
- [x] Hotkey voice matrix real playback passes on a connected test device.
```

Expected: M17 preview release remains blocked until real playback passes.

## Task 6: Verification

**Files:**
- No new files unless the implementation changes require focused tests.

- [ ] **Step 1: Run focused JVM tests**

```bash
./gradlew :app:testDebugUnitTest \
  --tests com.retrosprite.app.ui.integration.SherpaEndpointCommitGateTest \
  --tests com.retrosprite.app.ui.integration.SherpaOnnxRecognizerFactoryTest \
  --tests com.retrosprite.app.ui.integration.VoiceSampleFanOutTest \
  --tests com.retrosprite.app.ui.overlay.HotkeyVoiceQuestionControllerTest \
  --tests com.retrosprite.app.ui.overlay.HotkeyVoiceOverlayCoordinatorTest \
  --tests com.retrosprite.app.endpoint.RetroArchEndpointServerTest
```

Expected: all focused tests pass.

- [ ] **Step 2: Run script tests and release audit**

```bash
python3 -m unittest discover scripts/tests
python3 scripts/rc_release_audit.py
git diff --check
```

Expected: script tests, release audit, and whitespace check pass.

- [ ] **Step 3: Run device smoke after voice recovery**

```bash
JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home" \
RUN_DEVICE=1 RUN_VOICE=0 ./scripts/rc_hardening_check.sh
```

Expected: endpoint/GKP smoke still passes after voice lifecycle changes.

## Self-Review

- Spec coverage: This plan targets the current release blocker shown by real RG476H evidence: hotkey voice playback sessions fail before any question reaches GKP retrieval.
- Placeholder scan: No task is left as `TBD`; each task names files, commands, and expected outputs.
- Boundary check: The plan does not add cloud ASR, new models, Accessibility Service, MediaProjection, text guessing, new games, or bundled copyrighted content.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-01-m17-hotkey-voice-lifecycle-recovery.md`.

Two execution options:

1. **Subagent-Driven (recommended)** - dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** - execute tasks in this session using executing-plans, batch execution with checkpoints.
