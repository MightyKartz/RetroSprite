#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# scripts/android_avd_smoke.sh
#
# Device/AVD smoke test for the RetroSprite Android app.
#
# This intentionally verifies the RetroSprite side of the integration:
#   adb device online -> app package present -> endpoint activity/service starts
#   -> adb forward -> /health -> simulated RetroArch POST.
#
# It does NOT claim to verify that official RetroArch Android triggered the
# AI Service hotkey. That remains a separate manual/debug-build gate documented
# in docs/RETROARCH_ANDROID_AI_SERVICE_FINDINGS.md.
#
# Usage:
#   ./scripts/android_avd_smoke.sh
#   HOST_PORT=18080 DEVICE_PORT=8080 STRESS=5 ./scripts/android_avd_smoke.sh
#   INSTALL=1 ./scripts/android_avd_smoke.sh
#   WAIT_ATTEMPTS=30 ./scripts/android_avd_smoke.sh
# -----------------------------------------------------------------------------
set -u

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ADB:-adb}"
APP_ID="${APP_ID:-com.retrosprite.app}"
RETROARCH_ID="${RETROARCH_ID:-com.retroarch.aarch64}"
HOST_PORT="${HOST_PORT:-18080}"
DEVICE_PORT="${DEVICE_PORT:-8080}"
STRESS="${STRESS:-5}"
INSTALL="${INSTALL:-0}"
APK_PATH="${APK_PATH:-${ROOT_DIR}/app/build/outputs/apk/debug/app-debug.apk}"
WAIT_ATTEMPTS="${WAIT_ATTEMPTS:-20}"

fail() {
  printf "FAIL %s\n" "$1" >&2
  exit 1
}

info() {
  printf "%s\n" "$1"
}

command -v "$ADB" >/dev/null 2>&1 || fail "adb not found"
command -v curl >/dev/null 2>&1 || fail "curl not found"

STATE="$("$ADB" get-state 2>/dev/null || true)"
[ "$STATE" = "device" ] || fail "no online adb device (current state: ${STATE:-none})"

info "[1/5] adb device"
"$ADB" devices -l

if [ "$INSTALL" = "1" ]; then
  [ -f "$APK_PATH" ] || fail "APK not found: $APK_PATH"
  info "[2/5] installing RetroSprite APK"
  "$ADB" install -r "$APK_PATH" >/dev/null || fail "adb install failed"
else
  info "[2/5] checking installed packages"
  "$ADB" shell pm list packages | grep -q "package:${APP_ID}" \
    || fail "${APP_ID} is not installed; rerun with INSTALL=1"
fi

if "$ADB" shell pm list packages | grep -q "package:${RETROARCH_ID}"; then
  info "  RetroArch package present: ${RETROARCH_ID}"
else
  info "  WARN RetroArch package not present: ${RETROARCH_ID}"
fi

info "[3/5] starting RetroSprite"
"$ADB" shell am start -n "${APP_ID}/.MainActivity" >/dev/null \
  || fail "failed to start ${APP_ID}/.MainActivity"

info "[4/5] adb forward tcp:${HOST_PORT} -> tcp:${DEVICE_PORT}"
"$ADB" forward "tcp:${HOST_PORT}" "tcp:${DEVICE_PORT}" >/dev/null \
  || fail "adb forward failed"

info "[5/5] endpoint smoke"
attempt=1
while [ "$attempt" -le "$WAIT_ATTEMPTS" ]; do
  if curl -fsS -m 2 "http://127.0.0.1:${HOST_PORT}/health" >/dev/null 2>&1; then
    break
  fi
  if [ "$attempt" -eq "$WAIT_ATTEMPTS" ]; then
    fail "endpoint did not become healthy on host port ${HOST_PORT}"
  fi
  sleep 1
  attempt=$((attempt + 1))
done

HOST=127.0.0.1 PORT="$HOST_PORT" STRESS="$STRESS" "${ROOT_DIR}/scripts/test_endpoint.sh"
