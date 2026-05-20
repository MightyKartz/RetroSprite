#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# scripts/android_avd_smoke.sh
#
# Device/AVD smoke test for the RetroSprite Android app.
#
# This intentionally verifies the RetroSprite side of the integration:
#   adb device online -> app package present or installed
#   -> endpoint activity/service starts -> adb forward -> /health
#   -> simulated RetroArch POST -> sample GKP debug questions
#   -> /debug/latest-request summary.
#
# It does NOT claim to verify that official RetroArch Android triggered the
# AI Service hotkey. That remains a separate manual/debug-build gate documented
# in docs/RETROARCH_ANDROID_AI_SERVICE_FINDINGS.md.
#
# Usage:
#   ./scripts/android_avd_smoke.sh
#   HOST_PORT=18080 DEVICE_PORT=4404 STRESS=5 ./scripts/android_avd_smoke.sh
#   INSTALL=1 ./scripts/android_avd_smoke.sh      # force reinstall APK
#   INSTALL=0 ./scripts/android_avd_smoke.sh      # only verify package exists
#   BUILD=1 INSTALL=1 ./scripts/android_avd_smoke.sh
#   WAIT_ATTEMPTS=30 ./scripts/android_avd_smoke.sh
#   RUN_DEBUG_ASK=0 ./scripts/android_avd_smoke.sh
#   RUN_RELAY_DEBUG_ASK=0 ./scripts/android_avd_smoke.sh
#   RELAY_DEBUG_QUESTION="蓝色保险丝在哪？" ./scripts/android_avd_smoke.sh
# -----------------------------------------------------------------------------
set -u

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ADB:-adb}"
APP_ID="${APP_ID:-com.retrosprite.app}"
RETROARCH_ID="${RETROARCH_ID:-com.retroarch.aarch64}"
HOST_PORT="${HOST_PORT:-18080}"
DEVICE_PORT="${DEVICE_PORT:-4404}"
STRESS="${STRESS:-5}"
INSTALL="${INSTALL:-auto}"
BUILD="${BUILD:-0}"
APK_PATH="${APK_PATH:-${ROOT_DIR}/app/build/outputs/apk/debug/app-debug.apk}"
WAIT_ATTEMPTS="${WAIT_ATTEMPTS:-20}"
RUN_DEBUG_ASK="${RUN_DEBUG_ASK:-1}"
RUN_RELAY_DEBUG_ASK="${RUN_RELAY_DEBUG_ASK:-1}"
DEBUG_ATTEMPTS="${DEBUG_ATTEMPTS:-10}"
DEBUG_LABEL="${DEBUG_LABEL:-2048__}"
DEBUG_QUESTION="${DEBUG_QUESTION:-两个 2 怎么合并？}"
DEBUG_EXPECT_SOURCE="${DEBUG_EXPECT_SOURCE:-sample.2048.rules}"
DEBUG_EXPECT_STAGE="${DEBUG_EXPECT_STAGE:-evidence}"
DEBUG_EXPECT_LLM_STATUS="${DEBUG_EXPECT_LLM_STATUS:-skipped}"
RELAY_DEBUG_LABEL="${RELAY_DEBUG_LABEL:-relay_station__}"
RELAY_DEBUG_QUESTION="${RELAY_DEBUG_QUESTION:-蓝色保险丝在哪？}"
RELAY_DEBUG_EXPECT_SOURCE="${RELAY_DEBUG_EXPECT_SOURCE:-sample.relay.items}"
RELAY_DEBUG_EXPECT_STAGE="${RELAY_DEBUG_EXPECT_STAGE:-evidence}"
RELAY_DEBUG_EXPECT_LLM_STATUS="${RELAY_DEBUG_EXPECT_LLM_STATUS:-skipped}"

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

info "[1/7] adb device"
"$ADB" devices -l

if [ "$BUILD" = "1" ]; then
  info "[2/7] building RetroSprite debug APK"
  (cd "$ROOT_DIR" && ./gradlew :app:assembleDebug) >/dev/null \
    || fail "Gradle assembleDebug failed"
else
  info "[2/7] build skipped (set BUILD=1 to rebuild APK)"
fi

PACKAGE_PRESENT=0
if "$ADB" shell pm list packages | grep -q "package:${APP_ID}"; then
  PACKAGE_PRESENT=1
fi

install_app() {
  [ -f "$APK_PATH" ] || fail "APK not found: $APK_PATH"
  info "  installing RetroSprite APK: $APK_PATH"
  "$ADB" install -r "$APK_PATH" >/dev/null || fail "adb install failed"
}

info "[3/7] installing or checking RetroSprite package"
case "$INSTALL" in
  1|true|TRUE|yes|YES)
    install_app
    ;;
  auto)
    if [ "$PACKAGE_PRESENT" -eq 1 ]; then
      info "  ${APP_ID} already installed; set INSTALL=1 to force reinstall"
    else
      install_app
    fi
    ;;
  0|false|FALSE|no|NO)
    [ "$PACKAGE_PRESENT" -eq 1 ] \
      || fail "${APP_ID} is not installed; rerun with INSTALL=auto or INSTALL=1"
    info "  ${APP_ID} is installed"
    ;;
  *)
    fail "INSTALL must be auto, 1, or 0"
    ;;
esac

if "$ADB" shell pm list packages | grep -q "package:${RETROARCH_ID}"; then
  info "  RetroArch package present: ${RETROARCH_ID}"
else
  info "  WARN RetroArch package not present: ${RETROARCH_ID}"
fi

info "[4/7] starting RetroSprite"
"$ADB" shell am start -n "${APP_ID}/.MainActivity" >/dev/null \
  || fail "failed to start ${APP_ID}/.MainActivity"

info "[5/7] adb forward tcp:${HOST_PORT} -> tcp:${DEVICE_PORT}"
"$ADB" forward "tcp:${HOST_PORT}" "tcp:${DEVICE_PORT}" >/dev/null \
  || fail "adb forward failed"

info "[6/7] endpoint smoke"
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

HOST=127.0.0.1 PORT="$HOST_PORT" STRESS="$STRESS" "${ROOT_DIR}/scripts/test_endpoint.sh" \
  || fail "scripts/test_endpoint.sh failed"

run_debug_case() {
  CASE_NAME="$1"
  CASE_LABEL="$2"
  CASE_QUESTION="$3"
  CASE_EXPECT_SOURCE="$4"
  CASE_EXPECT_STAGE="$5"
  CASE_EXPECT_LLM_STATUS="$6"

  info "  ${CASE_NAME}: ${CASE_LABEL} / ${CASE_QUESTION}"
  DEBUG_BODY=""
  debug_attempt=1
  while [ "$debug_attempt" -le "$DEBUG_ATTEMPTS" ]; do
    DEBUG_BODY=$(curl -fsS -m 15 -X POST "http://127.0.0.1:${HOST_PORT}/debug/ask?output=text" \
      -H 'Content-Type: application/json' \
      --data @- 2>/dev/null <<JSON
{"label":"${CASE_LABEL}","question":"${CASE_QUESTION}","state":{"paused":1}}
JSON
    ) || DEBUG_BODY=""

    case "$DEBUG_BODY" in
      *"\"text\""*)
        if [ -z "$CASE_EXPECT_SOURCE" ]; then
          break
        fi
        case "$DEBUG_BODY" in
          *"$CASE_EXPECT_SOURCE"*) break ;;
        esac
        ;;
    esac

    if [ "$debug_attempt" -eq "$DEBUG_ATTEMPTS" ]; then
      fail "${CASE_NAME} /debug/ask did not return expected source ${CASE_EXPECT_SOURCE}; last body: ${DEBUG_BODY}"
    fi
    sleep 1
    debug_attempt=$((debug_attempt + 1))
  done

  info "    /debug/ask: ${DEBUG_BODY}"

  LATEST_BODY=$(curl -fsS -m 5 "http://127.0.0.1:${HOST_PORT}/debug/latest-request" 2>/dev/null) \
    || fail "${CASE_NAME} /debug/latest-request failed"
  info "    /debug/latest-request: ${LATEST_BODY}"

  case "$LATEST_BODY" in
    *"\"has_entry\":true"*) ;;
    *) fail "${CASE_NAME} /debug/latest-request did not report has_entry=true" ;;
  esac
  case "$LATEST_BODY" in
    *"\"pipeline_stage\":\"${CASE_EXPECT_STAGE}\""*) ;;
    *) fail "${CASE_NAME} /debug/latest-request missing pipeline_stage=${CASE_EXPECT_STAGE}" ;;
  esac
  case "$LATEST_BODY" in
    *"\"llm_status\":\"${CASE_EXPECT_LLM_STATUS}\""*) ;;
    *) fail "${CASE_NAME} /debug/latest-request missing llm_status=${CASE_EXPECT_LLM_STATUS}" ;;
  esac
  if [ -n "$CASE_EXPECT_SOURCE" ]; then
    case "$LATEST_BODY" in
      *"$CASE_EXPECT_SOURCE"*) ;;
      *) fail "${CASE_NAME} /debug/latest-request missing source ${CASE_EXPECT_SOURCE}" ;;
    esac
  fi
}

info "[7/7] sample GKP debug questions and latest-request checks"
if [ "$RUN_DEBUG_ASK" != "1" ]; then
  info "  skipped because RUN_DEBUG_ASK=${RUN_DEBUG_ASK}"
  info "OK android_avd_smoke completed"
  exit 0
fi

run_debug_case "sample-2048" \
  "$DEBUG_LABEL" \
  "$DEBUG_QUESTION" \
  "$DEBUG_EXPECT_SOURCE" \
  "$DEBUG_EXPECT_STAGE" \
  "$DEBUG_EXPECT_LLM_STATUS"

if [ "$RUN_RELAY_DEBUG_ASK" = "1" ]; then
  run_debug_case "sample-relay-station" \
    "$RELAY_DEBUG_LABEL" \
    "$RELAY_DEBUG_QUESTION" \
    "$RELAY_DEBUG_EXPECT_SOURCE" \
    "$RELAY_DEBUG_EXPECT_STAGE" \
    "$RELAY_DEBUG_EXPECT_LLM_STATUS"
else
  info "  sample-relay-station skipped because RUN_RELAY_DEBUG_ASK=${RUN_RELAY_DEBUG_ASK}"
fi

info "OK android_avd_smoke completed"
