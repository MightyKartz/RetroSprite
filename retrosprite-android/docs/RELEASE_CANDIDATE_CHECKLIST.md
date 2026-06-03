# RetroSprite Release Candidate Checklist

> Current target: M17 Release Candidate Hardening.

## Must Pass

- [x] `./gradlew :app:testDebugUnitTest :app:assembleDebug`
- [x] `BUILD=1 INSTALL=1 ./scripts/android_avd_smoke.sh`
- [x] Hotkey voice matrix definition covers all six bundled GKP packs.
- [ ] Hotkey voice matrix real playback passes on a connected test device.
- [ ] Screen translation matrix covers dialogue, menu, status, equipment, numbers, English leakage, and 10-second paging.
- [x] Settings has no bundled API key and recommends `Qwen/Qwen3-VL-8B-Instruct` only as a model suggestion.
- [x] Diagnostics explains ASR, GKP, BYOK API, screenshot, timeout, no-key, and permission failures.
- [x] README, `docs/NEXT_IMPLEMENTATION_PLAN.md`, and `docs/TEST_COVERAGE.md` describe the same default routes.

## Content And Rights

- [x] No ROM, BIOS, save, screenshot dump, or patch file is bundled.
- [ ] Human spot-check confirms no commercial guidebook prose, long walkthrough copy, full script dump, or copied fan translation is bundled.
- [x] GKP knowledge rows have source ids that resolve to bundled citations.
- [x] License and citation files exist for every bundled GKP.

## Release Notes

- [x] Supported games list is exactly six bundled games unless the code changes.
- [x] APK size and local ASR asset size are stated.
- [x] BYOK screen translation provider setup is described without implying a bundled key.
- [x] Known limitations include Lite GKP coverage boundaries and possible ASR recognition drift.

## Current Evidence

- `RUN_DEVICE=0 RUN_VOICE=0 ./scripts/rc_hardening_check.sh` passes the local 8-step gate.
- `scripts/rc_release_audit.py` verifies the six bundled GKP packs, required license/citation files, safe GKP file types, resolved knowledge source refs, no bundled `sk-*` API key, no stale runtime OCR route, and the Qwen3-VL recommendation.
- `DRY_RUN=1 ./scripts/hotkey_voice_qa_batch.sh` parses 20 hotkey voice matrix rows without device or audio playback.
- `scripts/rc_device_evidence.sh` is available for connected-device evidence capture and writes adb/debug snapshots under `build/rc-device-evidence/`.
- `RUN_DEVICE=1 RUN_VOICE=0 ./scripts/rc_hardening_check.sh` passed on RG476H `RG476H01077813`; evidence captured under `build/rc-device-evidence/20260601-140652/`.
- `RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 ./scripts/hotkey_voice_qa_batch.sh` failed the 2026-06-01 7-case playback matrix before GKP retrieval: overlay reached `mic_live=true`, then ended as `muted_recovery` / `blank_partial` with stale `/debug/latest-request`. Evidence: `build/hotkey-voice-qa/20260601-144348/`.
- Post-failure device state was captured under `build/rc-device-evidence/20260601-150333/`; overlay still reports `muted_recovery` / `blank_partial`, while latest-request is older debug evidence rather than a fresh playback submission.
- Immediate next plan is `docs/superpowers/plans/2026-06-01-m17-hotkey-voice-lifecycle-recovery.md`; failure triage is recorded in `docs/qa-feedback/hotkey-voice-lifecycle-failure-20260601.md`.
- M17.1 diagnostic APK installed on RG476H at 2026-06-01 15:37 CST; `PORT=18080 ./scripts/test_endpoint.sh` passed 7/7.
- One-case voice recovery proved the original lifecycle failure was playback calibration sensitive: at Mac volume 13, `golden_sun_ivan_observed` read audio but peaked at only `0.0060507967` and did not submit; at Mac volume 90 the same case passed with `pipeline_stage=evidence` and source `gs.localized_name_audit`.
- Seven-case hotkey voice playback matrix at Mac volume 90 reached fresh `hotkey_voice` submissions for all rows and passed 4/7. Remaining failures are GKP scoped ASR variants, not lifecycle gaps: Chrono `纳尔士`, FF6 `核实系统是什么`, Langrisser II `只挥官是什么`. Evidence: `build/hotkey-voice-qa/20260601-153946/`.
- Diagnostics request details now surface failure explanations for ASR, GKP disabled/no-evidence, BYOK API, missing screenshot, timeout, missing key, and permission cases. Verification: `./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.ui.screens.diagnostics.DiagnosticsSourceFilterTest --no-daemon --console=plain`.
- Agent-assisted GKP content-rights spot-check is recorded in `docs/qa-feedback/gkp-content-rights-spot-check.md`; the final human release checkbox remains unchecked.
