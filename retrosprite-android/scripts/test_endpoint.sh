#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# scripts/test_endpoint.sh
#
# End-to-end smoke + light stress test for the RetroSprite local endpoint.
# Targets bash 3.2+ so it runs on stock macOS without Homebrew bash.
#
# Usage:
#   ./scripts/test_endpoint.sh                # POST against http://127.0.0.1:4404
#   PORT=8081 ./scripts/test_endpoint.sh      # override port
#   STRESS=200 ./scripts/test_endpoint.sh     # change stress request count
#   NO_COLOR=1 ./scripts/test_endpoint.sh     # disable ANSI colors
#   # From host to AVD / device (recommended):
#   #   adb forward tcp:4404 tcp:4404
#   #   HOST=127.0.0.1 ./scripts/test_endpoint.sh
#
# Notes:
#  * The endpoint binds to 127.0.0.1 on device. To reach it from the dev host
#    (works for both AVDs and physical devices), use `adb forward` as above.
#  * RetroArch always uses HTTP 200 — even for malformed input — so failure
#    modes surface in the JSON `error` field, not via curl --fail.
#
# Exit codes:
#   0  every check passed
#   1  at least one check failed (per-test PASS/FAIL printed inline)
#   2  prerequisite missing (e.g. curl)
# -----------------------------------------------------------------------------
set -u
# Note: deliberately NOT using `set -e` because we want to keep running every
# check and surface a roll-up PASS/FAIL report at the end.

HOST="${HOST:-127.0.0.1}"
PORT="${PORT:-4404}"
STRESS="${STRESS:-100}"
BASE="http://${HOST}:${PORT}"

# ---- Color helpers (bash 3.2 compatible) -----------------------------------
if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
  C_RESET=$'\033[0m'
  C_GREEN=$'\033[32m'
  C_RED=$'\033[31m'
  C_YELLOW=$'\033[33m'
  C_DIM=$'\033[2m'
  C_BOLD=$'\033[1m'
else
  C_RESET=""; C_GREEN=""; C_RED=""; C_YELLOW=""; C_DIM=""; C_BOLD=""
fi

PASS_COUNT=0
FAIL_COUNT=0

pass() { printf "  ${C_GREEN}PASS${C_RESET} %s\n" "$1"; PASS_COUNT=$((PASS_COUNT + 1)); }
fail() { printf "  ${C_RED}FAIL${C_RESET} %s\n" "$1"; FAIL_COUNT=$((FAIL_COUNT + 1)); }
info() { printf "${C_DIM}%s${C_RESET}\n" "$1"; }
hdr()  { printf "\n${C_BOLD}%s${C_RESET}\n" "$1"; }

# ---- Prereqs ---------------------------------------------------------------
if ! command -v curl >/dev/null 2>&1; then
  printf "${C_RED}curl is required but not installed.${C_RESET}\n" >&2
  exit 2
fi

# Smallest valid PNG (1x1 transparent) Base64-encoded.
PNG_BASE64="iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="

# ---- Test 1: /health -------------------------------------------------------
hdr "[1/4] GET ${BASE}/health"
HEALTH_BODY=$(curl -sS -m 5 "${BASE}/health" 2>/dev/null || echo "")
info "  body: ${HEALTH_BODY}"
case "$HEALTH_BODY" in
  *"\"status\":\"ok\""*) pass "/health returns status=ok" ;;
  "")                    fail "/health did not respond (is the endpoint running on ${BASE}?)" ;;
  *)                     fail "/health body missing \"status\":\"ok\"" ;;
esac

# ---- Test 2: full POST -----------------------------------------------------
hdr "[2/4] POST ${BASE}/?output=text  (paused snapshot)"
POST_BODY=$(curl -sS -m 10 -X POST "${BASE}/?output=text" \
  -H 'Content-Type: application/json' \
  --data @- 2>/dev/null <<JSON
{
  "image": "${PNG_BASE64}",
  "label": "snes__super_mario_world",
  "state": {
    "paused": 1,
    "a": 0, "b": 0, "x": 0, "y": 0,
    "select": 0, "start": 1,
    "up": 0, "down": 0, "left": 0, "right": 0,
    "l": 0, "r": 0, "l2": 0, "r2": 0, "l3": 0, "r3": 0
  }
}
JSON
)
info "  body: ${POST_BODY}"
case "$POST_BODY" in
  *"\"text\""*) pass "POST / returns a text field" ;;
  *)            fail "POST / response missing text field" ;;
esac
case "$POST_BODY" in
  *"\"error\""*) fail "POST / returned an error field for a valid request" ;;
  *)             pass "POST / valid request does not return an error field" ;;
esac

# ---- Test 3: malformed JSON ------------------------------------------------
hdr "[3/4] POST ${BASE}/  (malformed body — expect HTTP 200 + error)"
# `%{http_code}` always prints a 3-digit code (000 if curl never connected),
# so we don't need an `|| echo` fallback here.
MALFORMED_CODE=$(curl -sS -o /tmp/retrosprite_malformed.json -w "%{http_code}" \
  -m 10 -X POST "${BASE}/?output=text" \
  -H 'Content-Type: application/json' \
  --data '{not valid json' 2>/dev/null)
MALFORMED_BODY=$(cat /tmp/retrosprite_malformed.json 2>/dev/null || echo "")
info "  status: ${MALFORMED_CODE}  body: ${MALFORMED_BODY}"
if [ "$MALFORMED_CODE" = "200" ]; then
  pass "Malformed JSON still returns HTTP 200 (RetroArch contract)"
else
  fail "Malformed JSON returned HTTP ${MALFORMED_CODE}, expected 200"
fi
case "$MALFORMED_BODY" in
  *"\"error\""*) pass "Malformed JSON response includes error field" ;;
  *)             fail "Malformed JSON response missing error field" ;;
esac
rm -f /tmp/retrosprite_malformed.json

# ---- Test 4: stress test ---------------------------------------------------
hdr "[4/4] Stress test: ${STRESS} sequential POSTs"
TOTAL_MS=0
ERRORS=0
START_EPOCH=$(date +%s)
PAYLOAD="{\"image\":\"${PNG_BASE64}\",\"label\":\"snes__stress\",\"state\":{\"paused\":0}}"
i=0
while [ "$i" -lt "$STRESS" ]; do
  # Combine http_code + time_total in a single -w format so we can distinguish
  # "no connection" (code=000) from "real response" (code=2xx).
  RESULT=$(curl -sS -o /dev/null -m 5 -X POST "${BASE}/?output=text" \
    -H 'Content-Type: application/json' \
    --data "$PAYLOAD" \
    -w "%{http_code} %{time_total}" 2>/dev/null || echo "000 0")
  CODE=$(printf "%s" "$RESULT" | awk '{print $1}')
  T_SEC=$(printf "%s" "$RESULT" | awk '{print $2}')
  if [ "$CODE" != "200" ] || [ -z "$T_SEC" ]; then
    ERRORS=$((ERRORS + 1))
  else
    # Convert seconds to integer milliseconds without bc (bash 3.2 friendly).
    T_MS=$(awk -v t="$T_SEC" 'BEGIN { printf "%d", t * 1000 }')
    TOTAL_MS=$((TOTAL_MS + T_MS))
  fi
  i=$((i + 1))
done
END_EPOCH=$(date +%s)
WALL_S=$((END_EPOCH - START_EPOCH))
SUCCESS=$((STRESS - ERRORS))
if [ "$SUCCESS" -gt 0 ]; then
  AVG_MS=$((TOTAL_MS / SUCCESS))
else
  AVG_MS=0
fi
info "  wall_clock=${WALL_S}s  ok=${SUCCESS}  errors=${ERRORS}  avg_latency=${AVG_MS}ms"
if [ "$ERRORS" -eq 0 ]; then
  pass "All ${STRESS} stress requests returned successfully"
else
  fail "${ERRORS}/${STRESS} stress requests failed"
fi
if [ "$AVG_MS" -lt 500 ]; then
  pass "Average latency ${AVG_MS}ms < 500ms threshold"
else
  printf "  ${C_YELLOW}WARN${C_RESET} Average latency ${AVG_MS}ms ≥ 500ms (informational only)\n"
fi

# ---- Roll-up ---------------------------------------------------------------
TOTAL=$((PASS_COUNT + FAIL_COUNT))
hdr "Summary"
printf "  ${C_GREEN}%d passed${C_RESET}   ${C_RED}%d failed${C_RESET}   (of %d checks)\n" \
  "$PASS_COUNT" "$FAIL_COUNT" "$TOTAL"

if [ "$FAIL_COUNT" -gt 0 ]; then
  exit 1
fi
exit 0
