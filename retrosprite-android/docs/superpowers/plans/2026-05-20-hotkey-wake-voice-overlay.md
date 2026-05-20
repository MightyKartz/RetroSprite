# Hotkey Wake Voice Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make RetroArch AI Service hotkey wake RetroSprite in-game voice UI, then evolve it into a short voice Q&A loop.

**Architecture:** Keep RetroArch AI Service as the only game-context trigger. The endpoint emits a typed hotkey event, a small overlay coordinator decides whether an overlay can be shown, and Android rendering stays behind a `HotkeyVoiceOverlayRenderer` boundary. ASR/TTS will be attached after the hotkey-to-overlay wake path is stable.

**Tech Stack:** Kotlin, Android WindowManager `TYPE_APPLICATION_OVERLAY`, Ktor endpoint, sherpa-onnx ASR, Android TextToSpeech, JVM unit tests, RG 476H true-device validation.

---

### Task 1: Endpoint Hotkey Event Boundary

**Files:**
- Create: `app/src/main/kotlin/com/retrosprite/app/endpoint/RetroArchHotkeyListener.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/endpoint/RetroArchEndpointServer.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/endpoint/EndpointController.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/endpoint/RetroArchEndpointServerTest.kt`

- [x] Write a failing test proving `POST /?output=text` notifies a hotkey listener with label, output mode, image bytes, and paused state.
- [x] Write a failing test proving `/debug/ask` does not notify the hotkey listener.
- [x] Add `RetroArchHotkeyEvent`, `RetroArchHotkeyListener`, and `NoopRetroArchHotkeyListener`.
- [x] Pass the listener through `RetroArchEndpointServer`, `retroArchModule`, and `EndpointController`.
- [x] Notify the listener after successful real RetroArch request parsing and before response generation.
- [x] Run the targeted endpoint tests and confirm green.

### Task 2: Minimal Overlay Coordinator

**Files:**
- Create: `app/src/main/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceOverlayCoordinator.kt`
- Create: `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceOverlayCoordinatorTest.kt`

- [x] Write tests for permission-granted display, permission-missing state, and scheduled auto-hide.
- [x] Implement `HotkeyVoiceOverlayState`, `HotkeyVoiceOverlayRenderer`, and `HotkeyVoiceOverlayCoordinator`.
- [x] Keep the coordinator independent of Android `WindowManager` so behavior stays JVM-testable.
- [x] Run the targeted coordinator tests and confirm green.

### Task 3: Android Overlay Renderer

**Files:**
- Create: `app/src/main/kotlin/com/retrosprite/app/ui/overlay/AndroidHotkeyVoiceOverlayController.kt`
- Create: `app/src/main/kotlin/com/retrosprite/app/ui/overlay/AndroidHotkeyVoiceOverlayRenderer.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ServiceLocator.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/RetroSpriteApp.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [x] Add `SYSTEM_ALERT_WINDOW` with a narrow comment explaining the hotkey-triggered overlay purpose.
- [x] Wire a process-wide `AndroidHotkeyVoiceOverlayController` through `ServiceLocator`.
- [x] Install the controller into `EndpointController` before endpoint service startup.
- [x] Render a non-touchable top-right colorful waveform overlay for a short timeout when permission is granted.
- [x] Add Settings onboarding to request overlay permission from the user.
- [x] Add true microphone amplitude to the waveform instead of the current animated listening cue.

### Task 4: Voice Q&A Loop

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxVoiceInputProvider.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceOverlayCoordinator.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/endpoint/QueryPipelineResponseGenerator.kt`
- Add tests under `app/src/test/kotlin/com/retrosprite/app/ui/overlay/`

- [x] Expose microphone level from the sherpa-onnx recording loop as a UI-friendly amplitude stream.
- [x] On hotkey, start one-shot ASR inside the overlay instead of relying on Home pending question.
- [x] Submit final transcript to the existing `ResponseGenerator → QueryPipeline → GKP/AnswerPolicy` chain.
- [x] Speak only the short answer through `SpeechOutputProvider`.
- [x] Hide the overlay after TTS finishes or after a short error message timeout.
- [x] Record `question_source=hotkey_voice` in request logs.

### Task 5: True-Device Validation

**Files:**
- Modify: `docs/NEXT_IMPLEMENTATION_PLAN.md`
- Modify: `docs/TEST_COVERAGE.md`

- [x] Grant overlay permission on RG 476H.
- [x] Load RetroArch with Shining Force II and press the configured AI Service hotkey.
- [x] Confirm the waveform appears at the top-right and does not block game input.
- [x] Confirm `/debug/latest-request` records label `mega_drive__光明力量2`.
- [ ] After Task 4, ask “什么时候转职？” by voice and confirm source `sf2.promotion`.

**2026-05-20 true-device note:** the first user-run hotkey voice pass completed the visible overlay and TTS loop. Latest request confirmed `output_mode=hotkey_voice:text` and `question_source=hotkey_voice`, but ASR transcribed the phrase as “接受他几部这个角色”, so the answer correctly stayed in `no_evidence`. Repeat with a clearer phrase such as “角色什么时候转职” before marking the `sf2.promotion` source check done.
