#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# scripts/gkp_patch_regression_gate.sh
#
# Post-approval regression gate for scoped GKP patches.
#
# Safe default:
#   ./scripts/gkp_patch_regression_gate.sh
#
# Runs focused GKP JVM regressions, release audit, asset mutation guard, and
# report refreshes without editing assets, requiring a device, or playing audio.
#
# Optional real-device replay for the current ASR-variant rows:
#   RUN_VOICE=1 ./scripts/gkp_patch_regression_gate.sh
# -----------------------------------------------------------------------------
set -u

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home}"
RUN_REPORTS="${RUN_REPORTS:-1}"
RUN_VOICE="${RUN_VOICE:-0}"
VOICE_CASE_FILTER="${VOICE_CASE_FILTER:-sf2_vigor_ball_observed,ff6_magicite_observed}"
VOICE_STRICT="${VOICE_STRICT:-1}"
BACKLOG_INPUT="${BACKLOG_INPUT:-}"

fail() {
  printf "FAIL %s\n" "$1" >&2
  exit 1
}

info() {
  printf "%s\n" "$1"
}

cd "$ROOT_DIR" || fail "cannot enter repo root"

info "[1/6] focused GKP JVM regression"
JAVA_HOME="$JAVA_HOME" ./gradlew :app:testDebugUnitTest \
  --tests "com.retrosprite.app.gkp.GkpV0FixtureLintTest" \
  --tests "com.retrosprite.app.gkp.RetroJrpgSrpgPackCoverageTest" \
  --tests "com.retrosprite.app.data.retrieval.RetroJrpgSrpgPackRetrievalGoldenTest" \
  || fail "focused GKP JVM regression failed"

info "[2/6] release audit"
python3 scripts/rc_release_audit.py \
  || fail "release audit failed"

info "[3/6] GKP asset mutation guard"
python3 scripts/gkp_asset_mutation_guard.py \
  --output docs/qa-feedback/gkp-asset-mutation-guard.md \
  --strict \
  || fail "GKP asset mutation guard failed"

if [ "$RUN_REPORTS" = "1" ]; then
  info "[4/6] refresh GKP coverage and status reports"
  python3 scripts/gkp_eval_report.py \
    --gkp-dir app/src/main/assets/gkp \
    --output docs/qa-feedback/m18-eval-report.md \
    || fail "GKP eval report failed"
  if [ -n "$BACKLOG_INPUT" ]; then
    python3 scripts/gkp_gap_backlog.py \
      --input "$BACKLOG_INPUT" \
      --merge-existing-backlog docs/qa-feedback/gkp-quality-backlog.md \
      --output docs/qa-feedback/gkp-quality-backlog.md \
      || fail "GKP backlog refresh failed"
  else
    info "BACKLOG_INPUT not set; keeping existing GKP backlog until new device evidence is captured"
  fi
  python3 scripts/screen_translation_eval_report.py \
    --output docs/qa-feedback/screen-translation-eval-report.md \
    || fail "screen translation report refresh failed"
  python3 scripts/m18_status_report.py \
    --output docs/qa-feedback/m18-status-report.md \
    || fail "M18 status report refresh failed"
else
  info "[4/6] report refresh skipped; set RUN_REPORTS=1 to regenerate M18 reports"
fi

if [ "$RUN_VOICE" = "1" ]; then
  info "[5/6] real-device hotkey voice replay for patched rows"
  RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 CASE_FILTER="$VOICE_CASE_FILTER" STRICT="$VOICE_STRICT" \
    ./scripts/hotkey_voice_qa_batch.sh \
    || fail "hotkey voice replay failed"
else
  info "[5/6] hotkey voice replay skipped; set RUN_VOICE=1 after installing the patched APK"
fi

info "[6/6] git diff whitespace check"
git diff --check \
  || fail "git diff whitespace check failed"

info "OK GKP patch regression gate completed"
