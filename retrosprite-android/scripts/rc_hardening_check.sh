#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# scripts/rc_hardening_check.sh
#
# Local release-candidate hardening gate for RetroSprite M17.
#
# Safe default:
#   ./scripts/rc_hardening_check.sh
# Runs JVM tests, assembleDebug, APK/assets/GKP snapshots, release checklist
# audit, script unit checks, and hotkey voice matrix self-test/dry-run without
# requiring a device or playing audio.
#
# Optional device gate:
#   RUN_DEVICE=1 ./scripts/rc_hardening_check.sh
#
# Optional MacBook-speaker voice QA:
#   RUN_VOICE=1 ./scripts/rc_hardening_check.sh
# -----------------------------------------------------------------------------
set -u

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home}"
ADB="${ADB:-adb}"
RUN_DEVICE="${RUN_DEVICE:-0}"
RUN_VOICE="${RUN_VOICE:-0}"
RUN_TRANSLATION_LIVE="${RUN_TRANSLATION_LIVE:-0}"

fail() {
  printf "FAIL %s\n" "$1" >&2
  exit 1
}

info() {
  printf "%s\n" "$1"
}

cd "$ROOT_DIR" || fail "cannot enter repo root"

if [ "$RUN_DEVICE" = "1" ]; then
  info "[0/8] adb device preflight"
  command -v "$ADB" >/dev/null 2>&1 || fail "adb not found"
  DEVICE_STATE="$("$ADB" get-state 2>/dev/null || true)"
  [ "$DEVICE_STATE" = "device" ] \
    || fail "RUN_DEVICE=1 requires one online adb device (current state: ${DEVICE_STATE:-none})"
fi

info "[1/8] JVM tests and debug assemble"
JAVA_HOME="$JAVA_HOME" ./gradlew :app:testDebugUnitTest :app:assembleDebug \
  || fail "unit tests or assembleDebug failed"

info "[2/8] APK size snapshot"
ls -lh app/build/outputs/apk/debug/app-debug.apk \
  || fail "debug APK missing"
du -sh app/src/main/assets app/src/main/assets/sherpa-onnx-streaming-paraformer-bilingual-zh-en app/src/main/assets/gkp \
  || fail "asset size snapshot failed"

info "[3/8] GKP snapshot"
printf "packs: "
find app/src/main/assets/gkp -mindepth 1 -maxdepth 1 -type d | wc -l
printf "knowledge rows: "
find app/src/main/assets/gkp -path "*/knowledge/*.jsonl" -print0 | xargs -0 cat | wc -l
printf "qa goldens: "
find app/src/main/assets/gkp -name qa_goldens.jsonl -print0 | xargs -0 cat | wc -l

info "[4/8] release checklist audit"
python3 ./scripts/rc_release_audit.py \
  || fail "release checklist audit failed"

info "[5/8] shell script unit checks"
python3 -m unittest discover scripts/tests \
  || fail "script unit checks failed"

info "[6/8] hotkey voice matrix self-test and dry-run"
SELF_TEST=1 ./scripts/hotkey_voice_qa_batch.sh \
  || fail "hotkey voice QA helper self-test failed"
DRY_RUN=1 ./scripts/hotkey_voice_qa_batch.sh \
  || fail "hotkey voice QA dry-run failed"

if [ "$RUN_DEVICE" = "1" ]; then
  info "[7/8] device endpoint and GKP smoke"
  BUILD=1 INSTALL=1 ./scripts/android_avd_smoke.sh \
    || fail "android device smoke failed"
else
  info "[7/8] device smoke skipped; set RUN_DEVICE=1 to run it"
fi

if [ "$RUN_VOICE" = "1" ]; then
  info "[8/8] hotkey voice matrix"
  RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 ./scripts/hotkey_voice_qa_batch.sh \
    || fail "hotkey voice QA failed"
elif [ "$RUN_TRANSLATION_LIVE" = "1" ]; then
  info "[8/8] live translation smoke is manual; use docs/qa-feedback/rc-device-matrix.md"
else
  info "[8/8] voice playback and live translation skipped; set RUN_VOICE=1 for voice QA"
fi

info "OK M17 RC hardening gate completed"
