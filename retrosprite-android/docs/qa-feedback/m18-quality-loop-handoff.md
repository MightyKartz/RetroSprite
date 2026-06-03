# M18 Quality Loop Handoff

- Gate status: `docs/qa-feedback/m18-gate-status.json`
- Action queue: `docs/qa-feedback/m18-next-action-queue.json`
- Backlog: `docs/qa-feedback/gkp-quality-backlog.md`
- Manual notes template: `docs/qa-feedback/gkp-manual-notes-template.tsv`
- Loop status: `ready_for_ongoing_rc_cycle`
- Overall gate status: `pass`
- Open areas: none
- Ready actions: none
- Blocked actions: none
- Done actions: rerun-device-lifecycle-row, replay-full-voice-matrix, final-m18-offline-gate
- GKP assets edited by this handoff: no

## Current Loop State

| Area | Detail |
|---|---|
| GKP backlog | `items=10; triage_items=10; triage_status=covered_by_applied_patch=7, covered_by_device_rerun=1, covered_by_existing_golden=2; triage_categories=asr_patch_applied=7, device_rerun_passed=1, policy_golden_existing=2; review_packet_rows=7; triage_open=0; manual_asr_approval_required=no; raw_tags=voice_lifecycle_gap=1, coverage_gap=7, alias_gap=5, ranking_gap=2, asr_variant=6, spoiler_gate_gap=2` |
| Hotkey voice matrix | `gate=observational; total=7; observed_rows=7; strict_pass=no; pass=4; fail=3; blocked=0; not_run=0; missing=0; categories=asr_variant=1, source_mismatch=2; results=build/hotkey-voice-qa/20260603-200807/results.tsv` |

## Safe Import Sources

| Source | Use For | Safety Rule |
|---|---|---|
| `/debug/latest-request` JSON | One-off no-evidence or wrong-answer captures | Preview merged backlog first; do not replace active backlog blindly. |
| `build/hotkey-voice-qa/<run>/results.tsv` | ASR, source mismatch, or voice lifecycle misses | Keep raw transcript, normalized question, source ids, finish reason, and audio counters. |
| Manual tester notes TSV | Human-observed wrong answers, translation layout issues, or missing coverage | Start from the generated template, replace example rows, and merge with the active backlog. |

## Preview-First Backlog Commands

Preview a new latest-request capture:

```bash
python3 scripts/gkp_gap_backlog.py \
  --input build/rc-device-evidence/YYYYMMDD-HHMMSS/latest-request.json \
  --merge-existing-backlog docs/qa-feedback/gkp-quality-backlog.md \
  --output build/m18-latest-request-backlog-preview.md
```

Preview a voice QA run:

```bash
python3 scripts/gkp_gap_backlog.py \
  --input build/hotkey-voice-qa/YYYYMMDD-HHMMSS/results.tsv \
  --merge-existing-backlog docs/qa-feedback/gkp-quality-backlog.md \
  --output build/m18-voice-backlog-preview.md
```

Create the manual notes TSV template:

```bash
python3 scripts/gkp_gap_backlog.py \
  --manual-notes-template-output docs/qa-feedback/gkp-manual-notes-template.tsv
```

After replacing example rows with real tester observations, preview the merged backlog:

```bash
python3 scripts/gkp_gap_backlog.py \
  --input docs/qa-feedback/gkp-manual-notes-template.tsv \
  --merge-existing-backlog docs/qa-feedback/gkp-quality-backlog.md \
  --output build/m18-manual-notes-backlog-preview.md
```

After reviewing a preview, write the active backlog with merge protection:

```bash
python3 scripts/gkp_gap_backlog.py \
  --input docs/qa-feedback/gkp-manual-notes-template.tsv \
  --merge-existing-backlog docs/qa-feedback/gkp-quality-backlog.md \
  --output docs/qa-feedback/gkp-quality-backlog.md
```

Refresh triage and aggregate status after any reviewed import:

```bash
python3 scripts/gkp_backlog_triage_report.py \
  --output docs/qa-feedback/gkp-backlog-triage-report.md \
  --strict
python3 scripts/m18_status_report.py \
  --output docs/qa-feedback/m18-status-report.md
python3 scripts/m18_next_action_queue.py \
  --output docs/qa-feedback/m18-next-action-queue.md \
  --json-output docs/qa-feedback/m18-next-action-queue.json
```

## Fix Acceptance Rules

- Every accepted GKP fix needs source ids and a regression target.
- Alias or ASR fixes must be scoped to the current game pack and add or update at least one golden.
- Translation failures should first become reproducible screen matrix rows or backlog rows before UI/model changes.
- Voice-originated fixes require real-device replay after local regression.
- Do not add new game content until the current six bundled packs complete one full green RC pass.
- Prefer retrieval, aliases, ASR variants, screen grouping, diagnostics, and evidence quality before adding provider/model surface area.

## Verification

```bash
./scripts/m18_offline_quality_gate.sh
```

Run `./scripts/m18_offline_quality_gate.sh` for the final offline verification pass.
