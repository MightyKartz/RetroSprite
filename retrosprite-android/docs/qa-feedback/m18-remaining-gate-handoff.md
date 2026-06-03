# M18 Remaining Gate Handoff

- Plan audit: `docs/qa-feedback/m18-plan-execution-audit.md`
- Hotkey voice matrix: `docs/qa-feedback/hotkey-voice-matrix-report.md`
- Plan checkboxes: checked=14, unchecked=0
- Aggregate status: pass=10, open=0
- Removed from M18 scope: manual ASR approval, five-row screen translation manual matrix, and human content-rights confirmation.
- GKP assets edited by this handoff: no

## Gate Summary

| Gate | Current State | Next Action |
|---|---|---|
| Hotkey voice matrix | `total=7; pass=4; fail=3; blocked=0; not_run=0; missing=0; categories=asr_variant=1, source_mismatch=2` | Rerun the matrix when device conditions or GKP coverage changes; convert repeated misses into backlog rows. |

## Required Order

1. Keep GKP eval, backlog triage, patch proposal audit, asset guard, and command-contract audit green.
2. Review or rerun the hotkey voice matrix only when it is useful device evidence.
3. Convert repeated hotkey misses into reviewed backlog rows or scoped patch proposals.
4. Regenerate M18 reports and run the offline quality gate.

## Commands

Refresh the observational hotkey voice matrix report:

```bash
python3 scripts/hotkey_voice_matrix_report.py \
  --output docs/qa-feedback/hotkey-voice-matrix-report.md
```

Prepare a manual tester notes TSV for backlog import:

```bash
python3 scripts/gkp_gap_backlog.py \
  --manual-notes-template-output docs/qa-feedback/gkp-manual-notes-template.tsv

# After replacing the example rows with real tester observations, preview the merged backlog first:
python3 scripts/gkp_gap_backlog.py \
  --input docs/qa-feedback/gkp-manual-notes-template.tsv \
  --merge-existing-backlog docs/qa-feedback/gkp-quality-backlog.md \
  --output build/m18-manual-notes-backlog-preview.md

# After reviewing the preview:
python3 scripts/gkp_gap_backlog.py \
  --input docs/qa-feedback/gkp-manual-notes-template.tsv \
  --merge-existing-backlog docs/qa-feedback/gkp-quality-backlog.md \
  --output docs/qa-feedback/gkp-quality-backlog.md
```

Run aggregate checks:

```bash
./scripts/m18_offline_quality_gate.sh
```

Or run strict checks individually:

```bash
python3 scripts/m18_status_report.py \
  --output docs/qa-feedback/m18-status-report.md \
  --strict
python3 scripts/m18_plan_execution_audit.py \
  --output docs/qa-feedback/m18-plan-execution-audit.md \
  --json-output docs/qa-feedback/m18-plan-execution-audit.json \
  --strict
python3 scripts/m18_completion_audit.py \
  --output docs/qa-feedback/m18-completion-audit.md \
  --json-output docs/qa-feedback/m18-completion-audit.json \
  --strict
```

## Completion Rule

This handoff is complete only when M18 aggregate status, plan execution audit, completion audit, `./scripts/m18_offline_quality_gate.sh`, release audit, diff check, and relevant real-device evidence pass.
