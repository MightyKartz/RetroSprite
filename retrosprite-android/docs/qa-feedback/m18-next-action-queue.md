# M18 Next Action Queue

- Action counts: done=3, ready=0, blocked=0
- GKP assets edited by this queue: no

## Queue

| ID | Owner | Status | Title | Blockers | Evidence |
|---|---|---|---|---|---|
| `rerun-device-lifecycle-row` | human/device | `done` | Rerun the non-patch device lifecycle voice row | No open device_rerun_needed backlog row is present. | `docs/qa-feedback/gkp-backlog-triage-report.md` |
| `replay-full-voice-matrix` | human/device | `done` | Review the observational hotkey voice matrix | Hotkey voice matrix is already closed. | `docs/qa-feedback/hotkey-voice-matrix-report.md` |
| `final-m18-offline-gate` | agent | `done` | Run the final M18 offline quality gate | - | `docs/qa-feedback/m18-completion-audit.md` |

## Commands And Acceptance

### rerun-device-lifecycle-row

- Status: `done`
- Owner: `human/device`
- Acceptance: The rerun records a fresh hotkey_voice request or fresh overlay audio diagnostics for the device_rerun_needed row; do not edit GKP assets from this action.

```bash
No device lifecycle rerun is required.
```

### replay-full-voice-matrix

- Status: `done`
- Owner: `human/device`
- Acceptance: A fresh run updates `docs/qa-feedback/hotkey-voice-matrix-report.md`; repeated misses become backlog evidence, not an M18 manual approval gate.

```bash
RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 \
CASE_FILTER=sf2_vigor_ball_observed,golden_sun_ivan_observed,chrono_marle_observed,chrono_atb_observed,ff6_magicite_observed,langrisser_commander_smoke,phantasy_star_tech_skill_smoke \
VOICE=Tingting SAY_RATE=96 PRE_SPEAK_SECONDS=3 POST_CASE_SECONDS=10 \
POLL_ATTEMPTS=40 POLL_INTERVAL_SECONDS=2 READY_ATTEMPTS=20 READY_INTERVAL_SECONDS=1 STRICT=1 \
./scripts/hotkey_voice_qa_batch.sh
```

### final-m18-offline-gate

- Status: `done`
- Owner: `agent`
- Acceptance: Strict aggregate probes, script tests, release audit, diff check, approved GKP regression, and relevant real-device evidence pass.

```bash
./scripts/m18_offline_quality_gate.sh
```
