# M18 Eval Lab Status Report

| Area | Status | Evidence | Detail |
|---|---|---|---|
| GKP coverage | `pass` | `docs/qa-feedback/m18-eval-report.md` | packs=6; fail_lanes=0; warn_lanes=0 |
| GKP backlog | `pass` | `docs/qa-feedback/gkp-quality-backlog.md` | items=10; triage_items=10; triage_status=covered_by_applied_patch=7, covered_by_device_rerun=1, covered_by_existing_golden=2; triage_categories=asr_patch_applied=7, device_rerun_passed=1, policy_golden_existing=2; review_packet_rows=7; triage_open=0; manual_asr_approval_required=no; raw_tags=voice_lifecycle_gap=1, coverage_gap=7, alias_gap=5, ranking_gap=2, asr_variant=6, spoiler_gate_gap=2 |
| GKP patch proposals | `pass` | `docs/qa-feedback/gkp-patch-proposal-audit.md` | rows=7; pass=7; fail=0 |
| GKP patch review packet | `pass` | `docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md` | rows=7; ready=0; applied=7; blocked=0; assets_edited=no |
| GKP patch apply dry-run | `pass` | `docs/qa-feedback/gkp-patch-apply-dry-run-20260601-hotkey-voice.md` | rows=7; ready=0; applied=7; blocked=0; mode=dry_run; assets_edited=no |
| GKP asset mutation guard | `pass` | `docs/qa-feedback/gkp-asset-mutation-guard.md` | guard=pass; mode=approved_patch; dirty=10; expected=10; unexpected=0; apply_report=yes; apply_mode=apply |
| GKP ASR voice replay handoff | `pass` | `docs/qa-feedback/gkp-asr-patch-voice-replay-handoff.md` | patch_rows=7; voice_cases=7; apply_report=applied; assets_edited=no; apply_command=yes; replay_command=yes; pass_criteria=yes |
| Hotkey voice matrix | `pass` | `docs/qa-feedback/hotkey-voice-matrix-report.md` | gate=observational; total=7; observed_rows=7; strict_pass=no; pass=4; fail=3; blocked=0; not_run=0; missing=0; categories=asr_variant=1, source_mismatch=2; results=build/hotkey-voice-qa/20260603-200807/results.tsv |
| Command contract audit | `pass` | `docs/qa-feedback/m18-command-contract-audit.md` | inputs=18; pass=54; fail=0; missing=0 |
| M18 quality loop handoff | `pass` | `docs/qa-feedback/m18-quality-loop-handoff.json` | loop_status=ready_for_ongoing_rc_cycle; ready=none; blocked=none; open_areas=none; missing_fragments=0 |

## Open Work

- All aggregate M18 rows are passing.
