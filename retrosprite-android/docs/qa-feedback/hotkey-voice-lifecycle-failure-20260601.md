# Hotkey Voice Lifecycle Failure - 2026-06-01

## Summary

- Device: RG476H `RG476H01077813`
- Evidence: `build/hotkey-voice-qa/20260601-144348/`
- Command:

```bash
RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 \
CASE_FILTER=sf2_vigor_ball_observed,golden_sun_ivan_observed,chrono_marle_observed,chrono_atb_observed,ff6_magicite_observed,langrisser_commander_smoke,phantasy_star_tech_skill_smoke \
VOICE=Tingting SAY_RATE=96 PRE_SPEAK_SECONDS=3 POST_CASE_SECONDS=10 POLL_ATTEMPTS=40 POLL_INTERVAL_SECONDS=2 READY_ATTEMPTS=20 READY_INTERVAL_SECONDS=1 STRICT=0 \
./scripts/hotkey_voice_qa_batch.sh
```

Result: all 7 playback cases failed before a player question was submitted to the normal GKP pipeline.

## Observed Pattern

Each selected row reached the overlay listening state and reported `mic_live=true` in `*.overlay.ready.json`. Each row later ended with:

```text
finish_reason=muted_recovery
asr_commit_reason=blank_partial
asr_endpoint_armed=false
latest request timestamp unchanged
```

The generated `latest.json` files were `{"has_entry":false}`, so no fresh `/debug/latest-request` existed for the case. This means the failure happened before `ResponseGenerator`, retrieval, AnswerPolicy, or TTS answer playback.

## Interpretation

This is a hotkey voice capture / ASR lifecycle failure, not a GKP coverage failure.

Most likely causes to verify next:

1. The device is opening `AudioRecord`, but the MacBook speaker audio is not reaching the RG476H mic loudly or clearly enough.
2. The foreground microphone session is active from Android's point of view, but captured frames are silent or near-silent.
3. The recognizer receives frames, but Paraformer never emits a partial transcript or endpoint for this playback setup.
4. The QA script starts playback after `mic_live=true`, but the ASR path still needs a more explicit "ready to decode samples" diagnostic before audio playback begins.

Current evidence does not point to:

- GKP alias coverage.
- Source ranking.
- Low-spoiler policy.
- Qwen/BYOK screen translation.
- `/health`, endpoint protocol, or bundled GKP `/debug/ask` regressions.

## Next Plan

Immediate next development should follow:

- `docs/superpowers/plans/2026-06-01-m17-hotkey-voice-lifecycle-recovery.md`

M18 GKP quality work can continue for tooling and reports, but preview-release readiness is blocked until the real playback hotkey voice matrix can submit fresh `hotkey_voice` requests on the connected device.

## Follow-up Evidence

2026-06-01 15:37-15:43 CST follow-up runs used a new diagnostic APK with AudioRecord counters in `/debug/hotkey-voice-overlay`.

- Low Mac output volume reproduced the failure without pointing at Android lifecycle: `golden_sun_ivan_observed` at volume 13 reported `reads=1988`, `read_errors=0`, and `peak=0.0060507967`, then ended with `muted_recovery`.
- The same one-case probe at Mac output volume 90 passed with a fresh `hotkey_voice` request, `pipeline_stage=evidence`, `answer_type=name_mapping`, `llm_status=skipped`, and source `gs.localized_name_audit`.
- The 7-case matrix at volume 90 submitted fresh requests for every row and passed 4/7. The remaining 3 failures reached `answer_completed` but produced no evidence because of scoped ASR variants: `纳尔士` for `玛尔是谁`, `核实系统是什么` for `魔石系统是什么`, and `只挥官是什么` for `指挥官是什么`.

Updated interpretation: the original 14:49 failure was caused by playback/receiver calibration, not an unfixed AudioRecord lifecycle bug. M17 voice release readiness is still open, but the remaining work has moved to GKP scoped `observed_asr` aliases and regression goldens. Current evidence: `build/hotkey-voice-qa/20260601-153946/`.
