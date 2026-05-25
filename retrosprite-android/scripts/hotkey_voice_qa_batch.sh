#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# scripts/hotkey_voice_qa_batch.sh
#
# Multi-pack hotkey voice QA for RetroSprite.
#
# Safe default:
#   ./scripts/hotkey_voice_qa_batch.sh
#
# Real MacBook-speaker-to-device run:
#   RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 ./scripts/hotkey_voice_qa_batch.sh
#
# Stable local sherpa-onnx Mandarin TTS source:
#   TTS_BACKEND=sherpa_onnx RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 ./scripts/hotkey_voice_qa_batch.sh
#
# Useful filters:
#   PACK_FILTER=shining-force-ii-md RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 ./scripts/hotkey_voice_qa_batch.sh
#   CASE_FILTER=sf2_localized_term RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 ./scripts/hotkey_voice_qa_batch.sh
#
# By default the script triggers the app hotkey listener through the local
# RetroArch endpoint. Set TRIGGER_HOTKEY=0 when a tester will press the real
# RetroArch hotkey manually, then press Enter after the overlay is listening.
# -----------------------------------------------------------------------------
set -u

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ADB:-adb}"
HOST_PORT="${HOST_PORT:-18080}"
DEVICE_PORT="${DEVICE_PORT:-4404}"
CASES_FILE="${CASES_FILE:-${ROOT_DIR}/scripts/hotkey_voice_qa_cases.tsv}"
VOICE="${VOICE:-Tingting}"
SAY_RATE="${SAY_RATE:-112}"
TTS_BACKEND="${TTS_BACKEND:-macos_say}"
SHERPA_TTS_PYTHON="${SHERPA_TTS_PYTHON:-${HOME}/.local/share/retrosprite/sherpa-onnx-tts/venv/bin/python}"
SHERPA_TTS_SCRIPT="${SHERPA_TTS_SCRIPT:-${ROOT_DIR}/scripts/sherpa_zh_tts.py}"
SHERPA_TTS_MODEL_DIR="${SHERPA_TTS_MODEL_DIR:-${HOME}/.local/share/retrosprite/sherpa-onnx-tts/models/sherpa-onnx-vits-zh-ll}"
SHERPA_TTS_SID="${SHERPA_TTS_SID:-0}"
SHERPA_TTS_SPEED="${SHERPA_TTS_SPEED:-1.0}"
SHERPA_TTS_NUM_THREADS="${SHERPA_TTS_NUM_THREADS:-2}"
RUN_PLAYBACK="${RUN_PLAYBACK:-0}"
CONFIRM_PLAYBACK="${CONFIRM_PLAYBACK:-0}"
DRY_RUN="${DRY_RUN:-0}"
SELF_TEST="${SELF_TEST:-0}"
TRIGGER_HOTKEY="${TRIGGER_HOTKEY:-1}"
REQUIRE_ACTIVE_OVERLAY="${REQUIRE_ACTIVE_OVERLAY:-1}"
READY_ATTEMPTS="${READY_ATTEMPTS:-16}"
READY_INTERVAL_SECONDS="${READY_INTERVAL_SECONDS:-1}"
PRE_SPEAK_SECONDS="${PRE_SPEAK_SECONDS:-2}"
POST_CASE_SECONDS="${POST_CASE_SECONDS:-6}"
POLL_ATTEMPTS="${POLL_ATTEMPTS:-24}"
POLL_INTERVAL_SECONDS="${POLL_INTERVAL_SECONDS:-2}"
WAIT_ATTEMPTS="${WAIT_ATTEMPTS:-20}"
STRICT="${STRICT:-1}"
PACK_FILTER="${PACK_FILTER:-}"
CASE_FILTER="${CASE_FILTER:-}"
RESULTS_DIR="${RESULTS_DIR:-${ROOT_DIR}/build/hotkey-voice-qa/$(date +%Y%m%d-%H%M%S)}"

fail() {
  printf "FAIL %s\n" "$1" >&2
  exit 1
}

info() {
  printf "%s\n" "$1"
}

selected_by_filter() {
  VALUE="$1"
  FILTER="$2"
  [ -z "$FILTER" ] && return 0
  case ",$FILTER," in
    *",$VALUE,"*) return 0 ;;
    *) return 1 ;;
  esac
}

selected_case() {
  selected_by_filter "$1" "$CASE_FILTER" || return 1
  selected_by_filter "$2" "$PACK_FILTER" || return 1
  return 0
}

tsv_clean() {
  printf "%s" "${1:-}" | tr '\011\015\012' '   '
}

load_case_fields() {
  CASE_LINE="$1" python3 - <<'PY'
import os
import shlex

names = [
    "case_name",
    "pack_id",
    "category",
    "label",
    "spoken_prompt",
    "expected_question_source",
    "expected_stage",
    "expected_answer_type",
    "expected_llm_status",
    "expected_source",
    "expected_matched_term",
    "expected_entity_id",
    "notes",
]
fields = os.environ.get("CASE_LINE", "").rstrip("\n").split("\t")
fields.extend([""] * (len(names) - len(fields)))
for name, value in zip(names, fields[: len(names)]):
    print(f"{name}={shlex.quote(value)}")
PY
}

json_field() {
  FIELD="$1"
  JSON_INPUT="$(cat)"
  JSON_INPUT="$JSON_INPUT" python3 - "$FIELD" <<'PY'
import json
import os
import sys

field = sys.argv[1]
try:
    value = json.loads(os.environ.get("JSON_INPUT", ""))
except Exception:
    sys.exit(2)

for part in field.split("."):
    if isinstance(value, dict):
        value = value.get(part)
    else:
        value = None
        break

if value is None:
    print("")
elif isinstance(value, bool):
    print("true" if value else "false")
elif isinstance(value, list):
    print(",".join(str(item) for item in value))
else:
    print(value)
PY
}

json_array_contains() {
  FIELD="$1"
  NEEDLE="$2"
  JSON_INPUT="$(cat)"
  JSON_INPUT="$JSON_INPUT" python3 - "$FIELD" "$NEEDLE" <<'PY'
import json
import os
import sys

field = sys.argv[1]
needle = sys.argv[2]
try:
    value = json.loads(os.environ.get("JSON_INPUT", ""))
except Exception:
    sys.exit(2)

for part in field.split("."):
    if isinstance(value, dict):
        value = value.get(part)
    else:
        value = None
        break

if isinstance(value, list) and needle in [str(item) for item in value]:
    sys.exit(0)
sys.exit(1)
PY
}

payload_for_label() {
  LABEL="$1"
  python3 - "$LABEL" <<'PY'
import json
import sys

print(json.dumps({"label": sys.argv[1], "state": {"paused": 1}}, ensure_ascii=False))
PY
}

self_test() {
  SAMPLE='{"has_entry":true,"source_ids":["sample.source"],"nested":{"value":"ok"}}'
  VALUE="$(printf "%s" "$SAMPLE" | json_field "has_entry" 2>/dev/null || true)"
  [ "$VALUE" = "true" ] || fail "json_field self-test failed"
  VALUE="$(printf "%s" "$SAMPLE" | json_field "nested.value" 2>/dev/null || true)"
  [ "$VALUE" = "ok" ] || fail "nested json_field self-test failed"
  printf "%s" "$SAMPLE" | json_array_contains "source_ids" "sample.source" >/dev/null 2>&1 \
    || fail "json_array_contains self-test failed"
  SAMPLE_CASE="$(printf "sample_case\tpack\tcategory\tlabel\tprompt\thotkey_voice\tevidence\tusage\tskipped\tsource\t\t\tNotes with empty expected term")"
  eval "$(load_case_fields "$SAMPLE_CASE")"
  [ "$case_name" = "sample_case" ] || fail "load_case_fields case_name self-test failed"
  [ "$expected_source" = "source" ] || fail "load_case_fields expected_source self-test failed"
  [ -z "$expected_matched_term" ] || fail "load_case_fields empty expected_matched_term self-test failed"
  [ -z "$expected_entity_id" ] || fail "load_case_fields empty expected_entity_id self-test failed"
  [ "$notes" = "Notes with empty expected term" ] || fail "load_case_fields notes self-test failed"
  info "SELF TEST OK"
}

curl_get() {
  PATH_PART="$1"
  curl -fsS -m 5 "http://127.0.0.1:${HOST_PORT}${PATH_PART}"
}

print_selected_cases() {
  info "DRY RUN hotkey voice QA cases:"
  [ -f "$CASES_FILE" ] || fail "case file not found: ${CASES_FILE}"

  count=0
  while IFS= read -r case_line || [ -n "${case_line:-}" ]; do
    case "$case_line" in
      ""|\#*) continue ;;
    esac
    eval "$(load_case_fields "$case_line")"
    case "$case_name" in
      case_name) continue ;;
    esac
    selected_case "$case_name" "$pack_id" || continue
    count=$((count + 1))
    printf "  %s\t%s\t%s\t%s\t%s\n" \
      "$case_name" "$pack_id" "$category" "$label" "$spoken_prompt"
  done < "$CASES_FILE"

  [ "$count" -gt 0 ] || fail "no selected cases"
  info "Set RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 to run MacBook-speaker voice QA."
}

preflight() {
  command -v python3 >/dev/null 2>&1 || fail "python3 not found"
  command -v curl >/dev/null 2>&1 || fail "curl not found"
  command -v "$ADB" >/dev/null 2>&1 || fail "adb not found"
  [ -f "$CASES_FILE" ] || fail "case file not found: ${CASES_FILE}"

  case "$TTS_BACKEND" in
    macos_say)
      command -v say >/dev/null 2>&1 || fail "macOS say command not found"
      if ! say -v '?' 2>/dev/null | grep -i "$VOICE" >/dev/null 2>&1; then
        info "WARN voice '${VOICE}' was not found in 'say -v ?'; continuing because macOS voice names vary."
      fi
      ;;
    sherpa_onnx)
      command -v afplay >/dev/null 2>&1 || fail "afplay not found"
      [ -x "$SHERPA_TTS_PYTHON" ] || fail "sherpa TTS python not executable: ${SHERPA_TTS_PYTHON}"
      [ -f "$SHERPA_TTS_SCRIPT" ] || fail "sherpa TTS script not found: ${SHERPA_TTS_SCRIPT}"
      [ -f "${SHERPA_TTS_MODEL_DIR}/model.onnx" ] || fail "sherpa TTS model not found: ${SHERPA_TTS_MODEL_DIR}"
      ;;
    *)
      fail "unknown TTS_BACKEND: ${TTS_BACKEND}"
      ;;
  esac

  STATE="$("$ADB" get-state 2>/dev/null || true)"
  [ "$STATE" = "device" ] || fail "no online adb device (current state: ${STATE:-none})"

  "$ADB" forward "tcp:${HOST_PORT}" "tcp:${DEVICE_PORT}" >/dev/null \
    || fail "adb forward tcp:${HOST_PORT} -> tcp:${DEVICE_PORT} failed"

  attempt=1
  while [ "$attempt" -le "$WAIT_ATTEMPTS" ]; do
    if curl_get "/health" >/dev/null 2>&1; then
      break
    fi
    if [ "$attempt" -eq "$WAIT_ATTEMPTS" ]; then
      fail "endpoint did not become healthy on host port ${HOST_PORT}"
    fi
    sleep 1
    attempt=$((attempt + 1))
  done

  mkdir -p "$RESULTS_DIR" || fail "could not create results dir: ${RESULTS_DIR}"
  RESULTS_FILE="${RESULTS_DIR}/results.tsv"
  printf "timestamp\tcase_name\tpack_id\tcategory\tlabel\tspoken_prompt\ttts_backend\tvoice\ttts_artifact\toverlay_transcript\toverlay_normalized_transcript\toverlay_matched_term\traw_question\tnormalized_question\tmatched_term\tmatched_entity_id\tanswer_type\tanswer_confidence\tpipeline_stage\tllm_status\tsource_ids\toverlay_phase\tfinish_reason\tasr_commit_reason\tasr_last_partial\tasr_final_text\tasr_selected_transcript\tasr_post_voice_silence_ms\tasr_partial_stable_ms\tasr_required_stable_ms\tasr_endpoint_armed\tasr_final_flush_ms\tresult\tnotes\n" > "$RESULTS_FILE"
  info "Results: ${RESULTS_FILE}"
}

speak_prompt() {
  SAFE_CASE="$1"
  PROMPT="$2"
  case "$TTS_BACKEND" in
    macos_say)
      say -v "$VOICE" -r "$SAY_RATE" "$PROMPT" || fail "${SAFE_CASE} say playback failed"
      TTS_ARTIFACT=""
      ;;
    sherpa_onnx)
      TTS_ARTIFACT="${RESULTS_DIR}/${SAFE_CASE}.tts.wav"
      TTS_METADATA="${RESULTS_DIR}/${SAFE_CASE}.tts.json"
      "$SHERPA_TTS_PYTHON" "$SHERPA_TTS_SCRIPT" \
        --model-dir "$SHERPA_TTS_MODEL_DIR" \
        --sid "$SHERPA_TTS_SID" \
        --speed "$SHERPA_TTS_SPEED" \
        --num-threads "$SHERPA_TTS_NUM_THREADS" \
        --output "$TTS_ARTIFACT" \
        --metadata-output "$TTS_METADATA" \
        "$PROMPT" > "${RESULTS_DIR}/${SAFE_CASE}.tts.stdout.json" \
        || fail "${SAFE_CASE} sherpa-onnx TTS generation failed"
      afplay "$TTS_ARTIFACT" || fail "${SAFE_CASE} sherpa-onnx TTS playback failed"
      ;;
  esac
}

trigger_hotkey_if_needed() {
  LABEL="$1"
  if [ "$TRIGGER_HOTKEY" = "1" ]; then
    PAYLOAD="$(payload_for_label "$LABEL")"
    curl -fsS -m 5 -X POST "http://127.0.0.1:${HOST_PORT}/?output=text" \
      -H 'Content-Type: application/json' \
      --data "$PAYLOAD" >/dev/null \
      || fail "failed to trigger hotkey listener through endpoint"
  else
    info "  Press the RetroArch hotkey now. Press Enter after the overlay is listening."
    read -r _
  fi
}

wait_for_overlay_completion() {
  attempt=1
  LAST_OVERLAY=""
  while [ "$attempt" -le "$POLL_ATTEMPTS" ]; do
    LAST_OVERLAY="$(curl_get "/debug/hotkey-voice-overlay" 2>/dev/null || true)"
    PHASE="$(printf "%s" "$LAST_OVERLAY" | json_field "lifecycle_phase" 2>/dev/null || true)"
    ACTIVE="$(printf "%s" "$LAST_OVERLAY" | json_field "is_active" 2>/dev/null || true)"
    ANSWER_VISIBLE="$(printf "%s" "$LAST_OVERLAY" | json_field "answer_visible" 2>/dev/null || true)"
    if [ "$PHASE" = "finished" ]; then
      printf "%s" "$LAST_OVERLAY"
      return 0
    fi
    if [ "$PHASE" = "idle" ] && [ "$ACTIVE" = "false" ] && [ "$ANSWER_VISIBLE" != "true" ]; then
      printf "%s" "$LAST_OVERLAY"
      return 0
    fi
    sleep "$POLL_INTERVAL_SECONDS"
    attempt=$((attempt + 1))
  done
  printf "%s" "$LAST_OVERLAY"
  return 1
}

wait_for_overlay_listening() {
  EXPECTED_LABEL="$1"
  attempt=1
  LAST_OVERLAY=""
  while [ "$attempt" -le "$READY_ATTEMPTS" ]; do
    LAST_OVERLAY="$(curl_get "/debug/hotkey-voice-overlay" 2>/dev/null || true)"
    PHASE="$(printf "%s" "$LAST_OVERLAY" | json_field "lifecycle_phase" 2>/dev/null || true)"
    RENDER_PHASE="$(printf "%s" "$LAST_OVERLAY" | json_field "render_phase" 2>/dev/null || true)"
    ACTIVE="$(printf "%s" "$LAST_OVERLAY" | json_field "is_active" 2>/dev/null || true)"
    VISIBLE="$(printf "%s" "$LAST_OVERLAY" | json_field "is_visible" 2>/dev/null || true)"
    LABEL="$(printf "%s" "$LAST_OVERLAY" | json_field "label" 2>/dev/null || true)"
    MIC_LIVE="$(printf "%s" "$LAST_OVERLAY" | json_field "mic_live" 2>/dev/null || true)"
    if [ "$PHASE" = "listening" ] &&
       [ "$RENDER_PHASE" = "listening" ] &&
       [ "$ACTIVE" = "true" ] &&
       [ "$VISIBLE" = "true" ] &&
       [ "$LABEL" = "$EXPECTED_LABEL" ] &&
       [ "$MIC_LIVE" = "true" ]; then
      printf "%s" "$LAST_OVERLAY"
      return 0
    fi
    sleep "$READY_INTERVAL_SECONDS"
    attempt=$((attempt + 1))
  done
  printf "%s" "$LAST_OVERLAY"
  return 1
}

check_equals() {
  NAME="$1"
  EXPECTED="$2"
  ACTUAL="$3"
  [ -z "$EXPECTED" ] && return 0
  if [ "$EXPECTED" = "$ACTUAL" ]; then
    return 0
  fi
  VALIDATION_NOTES="${VALIDATION_NOTES}${NAME}: expected '${EXPECTED}' actual '${ACTUAL}'; "
  return 1
}

run_case() {
  case_name="$1"
  pack_id="$2"
  category="$3"
  label="$4"
  spoken_prompt="$5"
  expected_question_source="$6"
  expected_stage="$7"
  expected_answer_type="$8"
  expected_llm_status="$9"
  expected_source="${10}"
  expected_matched_term="${11}"
  expected_entity_id="${12}"
  notes="${13}"

  info "[${case_name}] ${label} / ${spoken_prompt}"
  safe_case="$(printf "%s" "$case_name" | tr -c 'A-Za-z0-9_.-' '_')"

  overlay_before="$(wait_for_overlay_completion || true)"
  if [ -z "$overlay_before" ]; then
    overlay_before="$(curl_get "/debug/hotkey-voice-overlay" 2>/dev/null || true)"
  fi
  printf "%s" "$overlay_before" > "${RESULTS_DIR}/${safe_case}.overlay.before.json"
  latest_before="$(curl_get "/debug/latest-request" 2>/dev/null || true)"
  printf "%s" "$latest_before" > "${RESULTS_DIR}/${safe_case}.latest.before.json"
  latest_before_timestamp="$(printf "%s" "$latest_before" | json_field "timestamp" 2>/dev/null || true)"

  trigger_hotkey_if_needed "$label"

  overlay_ready="$(wait_for_overlay_listening "$label" || true)"
  sleep "$PRE_SPEAK_SECONDS"
  overlay_ready="$(curl_get "/debug/hotkey-voice-overlay" 2>/dev/null || printf "%s" "$overlay_ready")"
  printf "%s" "$overlay_ready" > "${RESULTS_DIR}/${safe_case}.overlay.ready.json"
  ready_phase="$(printf "%s" "$overlay_ready" | json_field "lifecycle_phase" 2>/dev/null || true)"
  ready_render_phase="$(printf "%s" "$overlay_ready" | json_field "render_phase" 2>/dev/null || true)"
  ready_active="$(printf "%s" "$overlay_ready" | json_field "is_active" 2>/dev/null || true)"
  ready_visible="$(printf "%s" "$overlay_ready" | json_field "is_visible" 2>/dev/null || true)"
  ready_label="$(printf "%s" "$overlay_ready" | json_field "label" 2>/dev/null || true)"
  ready_mic_live="$(printf "%s" "$overlay_ready" | json_field "mic_live" 2>/dev/null || true)"
  if [ "$REQUIRE_ACTIVE_OVERLAY" = "1" ] &&
     { [ "$ready_phase" != "listening" ] ||
       [ "$ready_render_phase" != "listening" ] ||
       [ "$ready_active" != "true" ] ||
       [ "$ready_visible" != "true" ] ||
       [ "$ready_label" != "$label" ] ||
       [ "$ready_mic_live" != "true" ]; }; then
    fail "${case_name} overlay was not armed for microphone input with expected label before playback; refusing to speak"
  fi

  TTS_ARTIFACT=""
  speak_prompt "$safe_case" "$spoken_prompt"

  overlay_after="$(wait_for_overlay_completion || true)"
  printf "%s" "$overlay_after" > "${RESULTS_DIR}/${safe_case}.overlay.after.json"
  latest_raw="$(curl_get "/debug/latest-request" 2>/dev/null || true)"
  printf "%s" "$latest_raw" > "${RESULTS_DIR}/${safe_case}.latest.raw.json"
  latest="$latest_raw"
  latest_timestamp="$(printf "%s" "$latest_raw" | json_field "timestamp" 2>/dev/null || true)"
  stale_latest_note=""
  if [ -n "$latest_before_timestamp" ] &&
     [ -n "$latest_timestamp" ] &&
     [ "$latest_before_timestamp" = "$latest_timestamp" ]; then
    stale_latest_note="latest request timestamp unchanged; no request submitted for this case; "
    latest='{"has_entry":false}'
  fi
  printf "%s" "$latest" > "${RESULTS_DIR}/${safe_case}.latest.json"

  has_entry="$(printf "%s" "$latest" | json_field "has_entry" 2>/dev/null || true)"
  latest_label="$(printf "%s" "$latest" | json_field "label" 2>/dev/null || true)"
  question_source="$(printf "%s" "$latest" | json_field "question_source" 2>/dev/null || true)"
  raw_question="$(printf "%s" "$latest" | json_field "raw_question" 2>/dev/null || true)"
  normalized_question="$(printf "%s" "$latest" | json_field "normalized_question" 2>/dev/null || true)"
  matched_term="$(printf "%s" "$latest" | json_field "normalized_question_matched_term" 2>/dev/null || true)"
  matched_entity="$(printf "%s" "$latest" | json_field "normalized_question_matched_entity_id" 2>/dev/null || true)"
  answer_type="$(printf "%s" "$latest" | json_field "answer_type" 2>/dev/null || true)"
  answer_confidence="$(printf "%s" "$latest" | json_field "answer_confidence" 2>/dev/null || true)"
  pipeline_stage="$(printf "%s" "$latest" | json_field "pipeline_stage" 2>/dev/null || true)"
  llm_status="$(printf "%s" "$latest" | json_field "llm_status" 2>/dev/null || true)"
  source_ids="$(printf "%s" "$latest" | json_field "source_ids" 2>/dev/null || true)"
  overlay_transcript="$(printf "%s" "$overlay_after" | json_field "transcript" 2>/dev/null || true)"
  overlay_normalized_transcript="$(printf "%s" "$overlay_after" | json_field "normalized_transcript" 2>/dev/null || true)"
  overlay_matched_term="$(printf "%s" "$overlay_after" | json_field "transcript_matched_term" 2>/dev/null || true)"
  overlay_phase="$(printf "%s" "$overlay_after" | json_field "lifecycle_phase" 2>/dev/null || true)"
  finish_reason="$(printf "%s" "$overlay_after" | json_field "finish_reason" 2>/dev/null || true)"
  asr_commit_reason="$(printf "%s" "$overlay_after" | json_field "asr_commit_reason" 2>/dev/null || true)"
  asr_last_partial="$(printf "%s" "$overlay_after" | json_field "asr_last_partial" 2>/dev/null || true)"
  asr_final_text="$(printf "%s" "$overlay_after" | json_field "asr_final_text" 2>/dev/null || true)"
  asr_selected_transcript="$(printf "%s" "$overlay_after" | json_field "asr_selected_transcript" 2>/dev/null || true)"
  asr_post_voice_silence_ms="$(printf "%s" "$overlay_after" | json_field "asr_post_voice_silence_ms" 2>/dev/null || true)"
  asr_partial_stable_ms="$(printf "%s" "$overlay_after" | json_field "asr_partial_stable_ms" 2>/dev/null || true)"
  asr_required_stable_ms="$(printf "%s" "$overlay_after" | json_field "asr_required_stable_ms" 2>/dev/null || true)"
  asr_endpoint_armed="$(printf "%s" "$overlay_after" | json_field "asr_endpoint_armed" 2>/dev/null || true)"
  asr_final_flush_ms="$(printf "%s" "$overlay_after" | json_field "asr_final_flush_ms" 2>/dev/null || true)"

  VALIDATION_NOTES="$stale_latest_note"
  result="PASS"
  check_equals "has_entry" "true" "$has_entry" || result="FAIL"
  check_equals "label" "$label" "$latest_label" || result="FAIL"
  check_equals "expected_question_source" "$expected_question_source" "$question_source" || result="FAIL"
  check_equals "expected_stage" "$expected_stage" "$pipeline_stage" || result="FAIL"
  check_equals "expected_answer_type" "$expected_answer_type" "$answer_type" || result="FAIL"
  check_equals "expected_llm_status" "$expected_llm_status" "$llm_status" || result="FAIL"
  check_equals "expected_matched_term" "$expected_matched_term" "$matched_term" || result="FAIL"
  check_equals "normalized_question_matched_entity_id" "$expected_entity_id" "$matched_entity" || result="FAIL"
  if [ -n "$expected_source" ]; then
    if ! printf "%s" "$latest" | json_array_contains "source_ids" "$expected_source" >/dev/null 2>&1; then
      VALIDATION_NOTES="${VALIDATION_NOTES}source_ids missing '${expected_source}' from '${source_ids}'; "
      result="FAIL"
    fi
  fi

  timestamp="$(date +%Y-%m-%dT%H:%M:%S%z)"
  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" \
    "$(tsv_clean "$timestamp")" \
    "$(tsv_clean "$case_name")" \
    "$(tsv_clean "$pack_id")" \
    "$(tsv_clean "$category")" \
    "$(tsv_clean "$label")" \
    "$(tsv_clean "$spoken_prompt")" \
    "$(tsv_clean "$TTS_BACKEND")" \
    "$(tsv_clean "$VOICE")" \
    "$(tsv_clean "$TTS_ARTIFACT")" \
    "$(tsv_clean "$overlay_transcript")" \
    "$(tsv_clean "$overlay_normalized_transcript")" \
    "$(tsv_clean "$overlay_matched_term")" \
    "$(tsv_clean "$raw_question")" \
    "$(tsv_clean "$normalized_question")" \
    "$(tsv_clean "$matched_term")" \
    "$(tsv_clean "$matched_entity")" \
    "$(tsv_clean "$answer_type")" \
    "$(tsv_clean "$answer_confidence")" \
    "$(tsv_clean "$pipeline_stage")" \
    "$(tsv_clean "$llm_status")" \
    "$(tsv_clean "$source_ids")" \
    "$(tsv_clean "$overlay_phase")" \
    "$(tsv_clean "$finish_reason")" \
    "$(tsv_clean "$asr_commit_reason")" \
    "$(tsv_clean "$asr_last_partial")" \
    "$(tsv_clean "$asr_final_text")" \
    "$(tsv_clean "$asr_selected_transcript")" \
    "$(tsv_clean "$asr_post_voice_silence_ms")" \
    "$(tsv_clean "$asr_partial_stable_ms")" \
    "$(tsv_clean "$asr_required_stable_ms")" \
    "$(tsv_clean "$asr_endpoint_armed")" \
    "$(tsv_clean "$asr_final_flush_ms")" \
    "$(tsv_clean "$result")" \
    "$(tsv_clean "${VALIDATION_NOTES}${notes}")" >> "$RESULTS_FILE"

  info "  ${result}: stage=${pipeline_stage} answer_type=${answer_type} llm=${llm_status} sources=${source_ids}"
  if [ "$POST_CASE_SECONDS" != "0" ]; then
    sleep "$POST_CASE_SECONDS"
  fi
  [ "$result" = "PASS" ]
}

if [ "$SELF_TEST" = "1" ]; then
  self_test
  exit 0
fi

if [ "$DRY_RUN" = "1" ] || [ "$RUN_PLAYBACK" != "1" ]; then
  print_selected_cases
  exit 0
fi

[ "$CONFIRM_PLAYBACK" = "1" ] || fail "refusing to play voice; set CONFIRM_PLAYBACK=1 with RUN_PLAYBACK=1"

preflight

case_count=0
fail_count=0
while IFS= read -r case_line || [ -n "${case_line:-}" ]; do
  case "$case_line" in
    ""|\#*) continue ;;
  esac
  eval "$(load_case_fields "$case_line")"
  case "$case_name" in
    case_name) continue ;;
  esac
  selected_case "$case_name" "$pack_id" || continue
  case_count=$((case_count + 1))
  if ! run_case \
    "$case_name" \
    "$pack_id" \
    "$category" \
    "$label" \
    "$spoken_prompt" \
    "$expected_question_source" \
    "$expected_stage" \
    "$expected_answer_type" \
    "$expected_llm_status" \
    "$expected_source" \
    "$expected_matched_term" \
    "$expected_entity_id" \
    "$notes"; then
    fail_count=$((fail_count + 1))
  fi
done < "$CASES_FILE"

[ "$case_count" -gt 0 ] || fail "no selected cases"

info "Completed ${case_count} hotkey voice QA case(s), failures=${fail_count}."
info "Evidence files: ${RESULTS_DIR}"
if [ "$fail_count" -gt 0 ] && [ "$STRICT" = "1" ]; then
  exit 1
fi
exit 0
