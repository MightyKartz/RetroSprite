#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# scripts/m18_offline_quality_gate.sh
#
# Offline M18 Eval Lab refresh and verification gate.
#
# Safe default:
#   ./scripts/m18_offline_quality_gate.sh
#
# Refreshes M18 reports, handoff packets, completion audit, action queue,
# and command-contract audit; runs script tests, release audit, and whitespace
# checks. It does not edit GKP assets, require adb, play audio, or mark
# device gates as passed.
#
# Hotkey voice matrix playback is refreshed as observational evidence; one-off
# ASR drift does not block this offline gate.
# -----------------------------------------------------------------------------
set -u

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RUN_SCRIPT_TESTS="${RUN_SCRIPT_TESTS:-1}"
RUN_RELEASE_AUDIT="${RUN_RELEASE_AUDIT:-1}"
RUN_DIFF_CHECK="${RUN_DIFF_CHECK:-1}"
BACKLOG_INPUT="${BACKLOG_INPUT:-}"

fail() {
  printf "FAIL %s\n" "$1" >&2
  exit 1
}

info() {
  printf "%s\n" "$1"
}

run_step() {
  info "$1"
  shift
  "$@" || fail "$1"
}

cd "$ROOT_DIR" || fail "cannot enter repo root"

info "[1/20] refresh GKP coverage report"
python3 scripts/gkp_eval_report.py \
  --gkp-dir app/src/main/assets/gkp \
  --output docs/qa-feedback/m18-eval-report.md \
  || fail "GKP eval report failed"

info "[2/20] refresh GKP backlog only when BACKLOG_INPUT is provided"
if [ -n "$BACKLOG_INPUT" ]; then
  python3 scripts/gkp_gap_backlog.py \
    --input "$BACKLOG_INPUT" \
    --merge-existing-backlog docs/qa-feedback/gkp-quality-backlog.md \
    --output docs/qa-feedback/gkp-quality-backlog.md \
    || fail "GKP gap backlog refresh failed"
else
  info "BACKLOG_INPUT not set; keeping current docs/qa-feedback/gkp-quality-backlog.md"
fi

run_step "[2b/20] refresh manual tester notes template" \
  python3 scripts/gkp_gap_backlog.py \
    --manual-notes-template-output docs/qa-feedback/gkp-manual-notes-template.tsv

run_step "[2c/20] refresh GKP backlog triage report" \
  python3 scripts/gkp_backlog_triage_report.py \
    --output docs/qa-feedback/gkp-backlog-triage-report.md \
    --strict

run_step "[3/20] refresh GKP patch proposal audit" \
  python3 scripts/gkp_patch_proposal_audit.py --strict

run_step "[4/20] refresh GKP patch review packet" \
  python3 scripts/gkp_patch_review_packet.py \
    --json-output docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.json \
    --strict

run_step "[5/20] refresh GKP patch apply dry-run" \
  python3 scripts/gkp_patch_apply_review_packet.py \
    --output docs/qa-feedback/gkp-patch-apply-dry-run-20260601-hotkey-voice.md \
    --strict

run_step "[6/20] refresh GKP asset mutation guard" \
  python3 scripts/gkp_asset_mutation_guard.py \
    --output docs/qa-feedback/gkp-asset-mutation-guard.md \
    --strict

run_step "[7/20] refresh ASR patch voice replay handoff" \
  python3 scripts/gkp_asr_patch_voice_handoff.py \
    --output docs/qa-feedback/gkp-asr-patch-voice-replay-handoff.md \
    --json-output docs/qa-feedback/gkp-asr-patch-voice-replay-handoff.json

run_step "[8/20] refresh hotkey voice matrix report" \
  python3 scripts/hotkey_voice_matrix_report.py \
    --output docs/qa-feedback/hotkey-voice-matrix-report.md

run_step "[9/20] refresh aggregate M18 status report" \
  python3 scripts/m18_status_report.py \
    --output docs/qa-feedback/m18-status-report.md

run_step "[10/20] refresh machine-readable M18 gate status" \
  python3 scripts/m18_gate_status_json.py \
    --output docs/qa-feedback/m18-gate-status.json

run_step "[11/20] refresh M18 plan execution audit" \
  python3 scripts/m18_plan_execution_audit.py \
    --output docs/qa-feedback/m18-plan-execution-audit.md \
    --json-output docs/qa-feedback/m18-plan-execution-audit.json

run_step "[12/20] refresh M18 remaining gate handoff" \
  python3 scripts/m18_remaining_gate_packet.py \
    --output docs/qa-feedback/m18-remaining-gate-handoff.md \
    --json-output docs/qa-feedback/m18-remaining-gate-handoff.json

run_step "[13/20] refresh M18 completion audit" \
  python3 scripts/m18_completion_audit.py \
    --output docs/qa-feedback/m18-completion-audit.md \
    --json-output docs/qa-feedback/m18-completion-audit.json

run_step "[14/20] refresh M18 next action queue" \
  python3 scripts/m18_next_action_queue.py \
    --output docs/qa-feedback/m18-next-action-queue.md \
    --json-output docs/qa-feedback/m18-next-action-queue.json

run_step "[15/20] refresh M18 quality loop handoff" \
  python3 scripts/m18_quality_loop_handoff.py \
    --output docs/qa-feedback/m18-quality-loop-handoff.md \
    --json-output docs/qa-feedback/m18-quality-loop-handoff.json

run_step "[16/20] refresh M18 command contract audit" \
  python3 scripts/m18_command_contract_audit.py \
    --output docs/qa-feedback/m18-command-contract-audit.md \
    --strict

run_step "[16b/20] refresh final aggregate status after command audit" \
  python3 scripts/m18_status_report.py \
    --output docs/qa-feedback/m18-status-report.md

run_step "[16c/20] refresh final machine-readable gate status after command audit" \
  python3 scripts/m18_gate_status_json.py \
    --output docs/qa-feedback/m18-gate-status.json

run_step "[16d/20] refresh final plan execution audit after command audit" \
  python3 scripts/m18_plan_execution_audit.py \
    --output docs/qa-feedback/m18-plan-execution-audit.md \
    --json-output docs/qa-feedback/m18-plan-execution-audit.json

run_step "[16e/20] refresh final completion audit after command audit" \
  python3 scripts/m18_completion_audit.py \
    --output docs/qa-feedback/m18-completion-audit.md \
    --json-output docs/qa-feedback/m18-completion-audit.json

info "[17/20] strict completion probes"
run_step "[17a/20] M18 status strict" \
  python3 scripts/m18_status_report.py \
    --output /tmp/retrosprite-m18-status-strict.md \
    --strict
run_step "[17b/20] M18 gate status JSON strict" \
  python3 scripts/m18_gate_status_json.py \
    --output /tmp/retrosprite-m18-gate-status-strict.json \
    --strict
run_step "[17c/20] M18 plan execution audit strict" \
  python3 scripts/m18_plan_execution_audit.py \
    --output /tmp/retrosprite-m18-plan-execution-audit-strict.md \
    --json-output /tmp/retrosprite-m18-plan-execution-audit-strict.json \
    --strict
run_step "[17d/20] M18 remaining gate handoff strict" \
  python3 scripts/m18_remaining_gate_packet.py \
    --output /tmp/retrosprite-m18-remaining-gate-handoff-strict.md \
    --json-output /tmp/retrosprite-m18-remaining-gate-handoff-strict.json \
    --strict
run_step "[17e/20] M18 completion audit strict" \
  python3 scripts/m18_completion_audit.py \
    --output /tmp/retrosprite-m18-completion-audit-strict.md \
    --json-output /tmp/retrosprite-m18-completion-audit-strict.json \
    --strict
run_step "[17f/20] M18 next action queue strict" \
  python3 scripts/m18_next_action_queue.py \
    --output /tmp/retrosprite-m18-next-action-queue-strict.md \
    --json-output /tmp/retrosprite-m18-next-action-queue-strict.json \
    --strict

if [ "$RUN_SCRIPT_TESTS" = "1" ]; then
  run_step "[18/20] scripts unittest discovery" \
    python3 -m unittest discover scripts/tests
else
  info "[18/20] script tests skipped; set RUN_SCRIPT_TESTS=1 to run"
fi

if [ "$RUN_RELEASE_AUDIT" = "1" ]; then
  run_step "[19/20] release audit" \
    python3 scripts/rc_release_audit.py
else
  info "[19/20] release audit skipped; set RUN_RELEASE_AUDIT=1 to run"
fi

if [ "$RUN_DIFF_CHECK" = "1" ]; then
  git diff --check || fail "git diff whitespace check failed"
else
  info "git diff whitespace check skipped; set RUN_DIFF_CHECK=1 to run"
fi

info "OK M18 offline quality gate completed"
