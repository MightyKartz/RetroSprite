#!/usr/bin/env fish
# -----------------------------------------------------------------------------
# scripts/test_endpoint.fish
#
# Fish-shell equivalent of test_endpoint.sh — kept in sync feature-for-feature
# (health probe + happy-path POST + malformed body + N-shot stress test).
#
# Usage:
#   ./scripts/test_endpoint.fish
#   set -x PORT 8081;  ./scripts/test_endpoint.fish
#   set -x HOST 10.0.2.2; ./scripts/test_endpoint.fish
#   set -x STRESS 200; ./scripts/test_endpoint.fish
#   set -x NO_COLOR 1; ./scripts/test_endpoint.fish
#
# Exit codes match the sh version: 0=ok, 1=any failure, 2=missing curl.
# -----------------------------------------------------------------------------

set -q HOST;    or set HOST 127.0.0.1
set -q PORT;    or set PORT 8080
set -q STRESS;  or set STRESS 100
set BASE "http://$HOST:$PORT"

# ---- Color helpers ---------------------------------------------------------
if status is-interactive; and not set -q NO_COLOR
    set C_RESET (set_color normal)
    set C_GREEN (set_color green)
    set C_RED   (set_color red)
    set C_YELLOW (set_color yellow)
    set C_DIM   (set_color brblack)
    set C_BOLD  (set_color --bold white)
else
    set C_RESET ""; set C_GREEN ""; set C_RED ""
    set C_YELLOW ""; set C_DIM ""; set C_BOLD ""
end

set -g PASS_COUNT 0
set -g FAIL_COUNT 0

function pass
    printf "  %sPASS%s %s\n" "$C_GREEN" "$C_RESET" "$argv"
    set -g PASS_COUNT (math $PASS_COUNT + 1)
end
function fail
    printf "  %sFAIL%s %s\n" "$C_RED" "$C_RESET" "$argv"
    set -g FAIL_COUNT (math $FAIL_COUNT + 1)
end
function info
    printf "%s%s%s\n" "$C_DIM" "$argv" "$C_RESET"
end
function hdr
    printf "\n%s%s%s\n" "$C_BOLD" "$argv" "$C_RESET"
end

# ---- Prereqs ---------------------------------------------------------------
if not command -q curl
    printf "%scurl is required but not installed.%s\n" "$C_RED" "$C_RESET" >&2
    exit 2
end

set PNG_BASE64 "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="

# ---- Test 1: /health -------------------------------------------------------
hdr "[1/4] GET $BASE/health"
set HEALTH_BODY (curl -sS -m 5 "$BASE/health" 2>/dev/null; or echo "")
info "  body: $HEALTH_BODY"
if string match -q "*\"status\":\"ok\"*" -- "$HEALTH_BODY"
    pass "/health returns status=ok"
else if test -z "$HEALTH_BODY"
    fail "/health did not respond (is the endpoint running on $BASE?)"
else
    fail "/health body missing \"status\":\"ok\""
end

# ---- Test 2: full POST -----------------------------------------------------
hdr "[2/4] POST $BASE/?output=text  (paused snapshot)"
set POST_PAYLOAD "{\"image\":\"$PNG_BASE64\",\"label\":\"snes__super_mario_world\",\"state\":{\"paused\":1,\"a\":0,\"b\":0,\"x\":0,\"y\":0,\"select\":0,\"start\":1,\"up\":0,\"down\":0,\"left\":0,\"right\":0,\"l\":0,\"r\":0,\"l2\":0,\"r2\":0,\"l3\":0,\"r3\":0}}"
set POST_BODY (curl -sS -m 10 -X POST "$BASE/?output=text" \
    -H 'Content-Type: application/json' \
    --data "$POST_PAYLOAD" 2>/dev/null; or echo "")
info "  body: $POST_BODY"
if string match -q "*\"text\"*" -- "$POST_BODY"
    pass "POST / returns a text field"
else
    fail "POST / response missing text field"
end
if string match -q "*RetroSprite*" -- "$POST_BODY"
    pass "Response text mentions RetroSprite (Phase 0 ack)"
else
    fail "Response text does not mention RetroSprite"
end

# ---- Test 3: malformed JSON ------------------------------------------------
hdr "[3/4] POST $BASE/  (malformed body — expect HTTP 200 + error)"
set TMPFILE (mktemp -t retrosprite_malformed.XXXXXX)
# `%{http_code}` always prints a 3-digit code (000 if curl never connected).
set MALFORMED_CODE (curl -sS -o "$TMPFILE" -w "%{http_code}" \
    -m 10 -X POST "$BASE/?output=text" \
    -H 'Content-Type: application/json' \
    --data '{not valid json' 2>/dev/null)
set MALFORMED_BODY (cat "$TMPFILE" 2>/dev/null; or echo "")
info "  status: $MALFORMED_CODE  body: $MALFORMED_BODY"
if test "$MALFORMED_CODE" = "200"
    pass "Malformed JSON still returns HTTP 200 (RetroArch contract)"
else
    fail "Malformed JSON returned HTTP $MALFORMED_CODE, expected 200"
end
if string match -q "*\"error\"*" -- "$MALFORMED_BODY"
    pass "Malformed JSON response includes error field"
else
    fail "Malformed JSON response missing error field"
end
rm -f "$TMPFILE"

# ---- Test 4: stress test ---------------------------------------------------
hdr "[4/4] Stress test: $STRESS sequential POSTs"
set TOTAL_MS 0
set ERRORS 0
set START_EPOCH (date +%s)
set STRESS_PAYLOAD "{\"image\":\"$PNG_BASE64\",\"label\":\"snes__stress\",\"state\":{\"paused\":0}}"
for i in (seq 1 $STRESS)
    # Combine http_code + time_total in one -w format so we can distinguish
    # "no connection" (code=000) from "real response" (code=2xx).
    set RESULT (curl -sS -o /dev/null -m 5 -X POST "$BASE/?output=text" \
        -H 'Content-Type: application/json' \
        --data "$STRESS_PAYLOAD" \
        -w "%{http_code} %{time_total}" 2>/dev/null; or echo "000 0")
    set CODE (echo $RESULT | awk '{print $1}')
    set T_SEC (echo $RESULT | awk '{print $2}')
    if test "$CODE" != "200"; or test -z "$T_SEC"
        set ERRORS (math $ERRORS + 1)
    else
        set T_MS (awk -v t="$T_SEC" 'BEGIN { printf "%d", t * 1000 }')
        set TOTAL_MS (math $TOTAL_MS + $T_MS)
    end
end
set END_EPOCH (date +%s)
set WALL_S (math $END_EPOCH - $START_EPOCH)
set SUCCESS (math $STRESS - $ERRORS)
if test $SUCCESS -gt 0
    set AVG_MS (math -s0 $TOTAL_MS / $SUCCESS)
else
    set AVG_MS 0
end
info "  wall_clock=$WALL_S""s  ok=$SUCCESS  errors=$ERRORS  avg_latency=$AVG_MS""ms"
if test $ERRORS -eq 0
    pass "All $STRESS stress requests returned successfully"
else
    fail "$ERRORS/$STRESS stress requests failed"
end
if test $AVG_MS -lt 500
    pass "Average latency $AVG_MS""ms < 500ms threshold"
else
    printf "  %sWARN%s Average latency %sms ≥ 500ms (informational only)\n" \
        "$C_YELLOW" "$C_RESET" "$AVG_MS"
end

# ---- Roll-up ---------------------------------------------------------------
set TOTAL (math $PASS_COUNT + $FAIL_COUNT)
hdr "Summary"
printf "  %s%d passed%s   %s%d failed%s   (of %d checks)\n" \
    "$C_GREEN" "$PASS_COUNT" "$C_RESET" \
    "$C_RED" "$FAIL_COUNT" "$C_RESET" \
    "$TOTAL"

if test $FAIL_COUNT -gt 0
    exit 1
end
exit 0
