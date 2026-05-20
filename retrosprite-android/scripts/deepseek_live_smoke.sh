#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# scripts/deepseek_live_smoke.sh
#
# M1.6 live DeepSeek smoke for a connected debug device/AVD.
#
# The API key is read from stdin so it does not appear in shell history or argv:
#   printf '%s\n' "$DEEPSEEK_API_KEY" | ./scripts/deepseek_live_smoke.sh
#
# The key is staged into app-private storage with `run-as`, read once by the
# instrumentation test, immediately deleted, then persisted through the app's
# Android Keystore-backed Settings path.
# -----------------------------------------------------------------------------
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ADB:-adb}"
APP_ID="${APP_ID:-com.retrosprite.app}"
KEY_FILE="${KEY_FILE:-deepseek_live_smoke.key}"
TEST_CLASS="${TEST_CLASS:-com.retrosprite.app.llm.DeepSeekLiveSmokeTest}"
JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
INSTALL="${INSTALL:-1}"

fail() {
  printf "FAIL %s\n" "$1" >&2
  exit 1
}

info() {
  printf "%s\n" "$1"
}

command -v "$ADB" >/dev/null 2>&1 || fail "adb not found"

if [ -t 0 ]; then
  printf "DeepSeek API key: " >&2
  stty -echo
  read -r API_KEY
  stty echo
  printf "\n" >&2
else
  IFS= read -r API_KEY
fi

[ -n "${API_KEY:-}" ] || fail "empty DeepSeek API key"

STATE="$("$ADB" get-state 2>/dev/null || true)"
[ "$STATE" = "device" ] || fail "no online adb device (current state: ${STATE:-none})"

if [ "$INSTALL" = "1" ]; then
  info "[1/4] installing debug APK"
  (cd "$ROOT_DIR" && JAVA_HOME="$JAVA_HOME" ./gradlew :app:installDebug >/dev/null)
else
  info "[1/4] using currently installed debug APK"
fi

info "[2/4] staging key into app-private storage"
"$ADB" shell "run-as ${APP_ID} mkdir -p files" >/dev/null
"$ADB" shell "run-as ${APP_ID} sh -c 'cat > files/${KEY_FILE}'" <<<"$API_KEY"
unset API_KEY
cleanup() {
  "$ADB" shell "run-as ${APP_ID} rm -f files/${KEY_FILE}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

info "[3/4] running live DeepSeek instrumentation smoke"
(
  cd "$ROOT_DIR"
  JAVA_HOME="$JAVA_HOME" ./gradlew connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class="$TEST_CLASS"
)

info "[4/4] cleaning staged key file"
cleanup

info "PASS DeepSeek live smoke completed"
