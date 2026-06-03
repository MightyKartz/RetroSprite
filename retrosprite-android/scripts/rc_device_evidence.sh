#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# scripts/rc_device_evidence.sh
#
# Capture real-device evidence for the M17 RC manual gates.
#
# This helper does not mark a gate as passed. It gathers repeatable artifacts
# after a tester has connected a device, launched RetroSprite, and exercised
# hotkey voice or screen translation.
# -----------------------------------------------------------------------------
set -u

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ADB:-adb}"
APP_ID="${APP_ID:-com.retrosprite.app}"
HOST_PORT="${HOST_PORT:-18080}"
DEVICE_PORT="${DEVICE_PORT:-4404}"
OUT_DIR="${OUT_DIR:-${ROOT_DIR}/build/rc-device-evidence/$(date +%Y%m%d-%H%M%S)}"
GATE="${GATE:-manual}"
CASE_ID="${CASE_ID:-}"
INCLUDE_SCREENSHOT="${INCLUDE_SCREENSHOT:-0}"

fail() {
  printf "FAIL %s\n" "$1" >&2
  exit 1
}

info() {
  printf "%s\n" "$1"
}

usage() {
  cat <<'EOF'
Usage: ./scripts/rc_device_evidence.sh [--gate manual|endpoint_smoke|hotkey_voice|screen_translation] [--case-id CASE_ID] [--include-screenshot]

Examples:
  ./scripts/rc_device_evidence.sh
  ./scripts/rc_device_evidence.sh --gate screen_translation --case-id ff6_dialogue --include-screenshot
EOF
}

capture() {
  NAME="$1"
  shift
  FILE="${OUT_DIR}/${NAME}"
  {
    printf '%s' '$'
    for part in "$@"; do
      printf " %s" "$part"
    done
    printf "\n\n"
    "$@"
  } > "$FILE" 2>&1 || {
    printf "WARN capture failed: %s\n" "$NAME" >&2
    return 0
  }
}

capture_curl() {
  NAME="$1"
  PATH_PART="$2"
  capture "$NAME" curl -fsS -m 5 "http://127.0.0.1:${HOST_PORT}${PATH_PART}"
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --gate)
      [ "$#" -ge 2 ] || fail "--gate requires a value"
      GATE="$2"
      shift 2
      ;;
    --case-id)
      [ "$#" -ge 2 ] || fail "--case-id requires a value"
      CASE_ID="$2"
      shift 2
      ;;
    --include-screenshot)
      INCLUDE_SCREENSHOT=1
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      fail "unknown argument: $1"
      ;;
  esac
done

case "$GATE" in
  manual|endpoint_smoke|hotkey_voice|screen_translation) ;;
  *) fail "unsupported gate: $GATE" ;;
esac

case "$CASE_ID" in
  *[!A-Za-z0-9_.-]*)
    fail "case id may only contain letters, numbers, underscore, dot, or dash"
    ;;
esac

if [ "$GATE" = "screen_translation" ] && [ -z "$CASE_ID" ]; then
  fail "--case-id is required when --gate screen_translation is used"
fi

if [ "$GATE" = "screen_translation" ] && [ "$INCLUDE_SCREENSHOT" != "1" ]; then
  fail "--include-screenshot is required when --gate screen_translation is used"
fi

command -v "$ADB" >/dev/null 2>&1 || fail "adb not found"
command -v curl >/dev/null 2>&1 || fail "curl not found"

DEVICE_STATE="$("$ADB" get-state 2>/dev/null || true)"
[ "$DEVICE_STATE" = "device" ] \
  || fail "requires one online adb device (current state: ${DEVICE_STATE:-none})"

mkdir -p "$OUT_DIR" || fail "cannot create output directory: $OUT_DIR"

info "Capturing M17 RC device evidence into: $OUT_DIR"

capture adb-devices.txt "$ADB" devices -l
capture package-list.txt "$ADB" shell pm list packages
capture app-package.txt "$ADB" shell dumpsys package "$APP_ID"
capture appops-record-audio.txt "$ADB" shell appops get "$APP_ID" RECORD_AUDIO
capture appops-overlay.txt "$ADB" shell appops get "$APP_ID" SYSTEM_ALERT_WINDOW
capture appops-foreground-service.txt "$ADB" shell appops get "$APP_ID" START_FOREGROUND
capture windows.txt "$ADB" shell dumpsys window windows

"$ADB" forward "tcp:${HOST_PORT}" "tcp:${DEVICE_PORT}" >/dev/null \
  || fail "adb forward tcp:${HOST_PORT} -> tcp:${DEVICE_PORT} failed"

capture_curl health.json "/health"
capture_curl latest-request.json "/debug/latest-request"
capture_curl hotkey-voice-overlay.json "/debug/hotkey-voice-overlay"

if [ "$INCLUDE_SCREENSHOT" = "1" ]; then
  "$ADB" exec-out screencap -p > "${OUT_DIR}/screenshot.png" \
    || fail "cannot capture screenshot"
fi

{
  printf '%s\n' "{"
  printf '  "schema_version": 1,\n'
  printf '  "gate": "%s",\n' "$GATE"
  printf '  "case_id": "%s",\n' "$CASE_ID"
  printf '  "screenshot_included": %s,\n' "$INCLUDE_SCREENSHOT"
  printf '  "app_id": "%s",\n' "$APP_ID"
  printf '  "host_port": "%s",\n' "$HOST_PORT"
  printf '  "device_port": "%s",\n' "$DEVICE_PORT"
  printf '  "captured_at": "%s"\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  printf '%s\n' "}"
} > "${OUT_DIR}/metadata.json" || fail "cannot write evidence metadata"

{
  printf '%s\n\n' "# M17 RC Device Evidence"
  printf '%s%s%s\n' "- Gate: \`" "$GATE" "\`"
  printf '%s%s%s\n' "- Case id: \`" "${CASE_ID:-none}" "\`"
  printf '%s%s%s\n' "- App package: \`" "$APP_ID" "\`"
  printf '%s%s%s\n' "- Host endpoint: \`http://127.0.0.1:" "$HOST_PORT" "\`"
  printf '%s%s%s\n' "- Device endpoint: \`127.0.0.1:" "$DEVICE_PORT" "\`"
  printf '%s%s%s\n\n' "- Captured at: \`" "$(date '+%Y-%m-%d %H:%M:%S %Z')" "\`"
  printf '%s\n\n' "## Files"
  printf '%s\n' "- \`adb-devices.txt\`"
  printf '%s\n' "- \`package-list.txt\`"
  printf '%s\n' "- \`app-package.txt\`"
  printf '%s\n' "- \`appops-record-audio.txt\`"
  printf '%s\n' "- \`appops-overlay.txt\`"
  printf '%s\n' "- \`appops-foreground-service.txt\`"
  printf '%s\n' "- \`windows.txt\`"
  printf '%s\n' "- \`health.json\`"
  printf '%s\n' "- \`latest-request.json\`"
  printf '%s\n\n' "- \`hotkey-voice-overlay.json\`"
  if [ "$INCLUDE_SCREENSHOT" = "1" ]; then
    printf '%s\n\n' "- \`screenshot.png\`"
  fi
  printf '%s\n\n' "- \`metadata.json\`"
  printf '%s\n' "Use these files to fill \`docs/qa-feedback/rc-device-matrix.md\` after manual hotkey voice or screen translation testing."
} > "${OUT_DIR}/README.md" || fail "cannot write evidence README"

info "OK evidence captured: $OUT_DIR"
