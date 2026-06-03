import csv
import os
import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CASE_FILE = ROOT / "scripts/hotkey_voice_qa_cases.tsv"
VOICE_SCRIPT = ROOT / "scripts/hotkey_voice_qa_batch.sh"


class HotkeyVoiceQaCasesTest(unittest.TestCase):

    def test_case_file_covers_all_bundled_packs_and_required_voice_lanes(self):
        rows = read_cases()
        self.assertGreaterEqual(len(rows), 17)
        rows_by_name = {row["case_name"]: row for row in rows}
        self.assertIn("sf2_boss_term", rows_by_name)
        self.assertEqual("sf2.enemy_boss_notes", rows_by_name["sf2_boss_term"]["expected_source"])
        self.assertEqual("strategy", rows_by_name["sf2_boss_term"]["expected_answer_type"])
        for case_name in {
            "sf2_vigor_ball_observed",
            "golden_sun_ivan_observed",
            "chrono_marle_observed",
            "chrono_atb_observed",
            "ff6_magicite_observed",
            "langrisser_commander_smoke",
            "phantasy_star_tech_skill_smoke",
        }:
            self.assertIn(case_name, rows_by_name)

        by_pack = {}
        for row in rows:
            by_pack.setdefault(row["pack_id"], set()).add(row["category"])

            self.assertTrue(row["case_name"], row)
            self.assertTrue(row["label"], row)
            self.assertIn("__", row["label"], row)
            self.assertTrue(row["spoken_prompt"], row)
            self.assertEqual("hotkey_voice", row["expected_question_source"], row)
            self.assertIn(row["expected_stage"], {"evidence", "no_evidence"}, row)
            self.assertTrue(row["expected_answer_type"], row)
            self.assertEqual("skipped", row["expected_llm_status"], row)

        required_categories = {
            "core_gameplay",
            "localized_term",
            "no_evidence_boundary",
        }
        required_packs = {
            "shining-force-ii-md",
            "golden-sun-gba-zh",
            "chrono-trigger-snes-zh",
            "final-fantasy-vi-snes-zh",
            "langrisser-ii-md-zh",
            "phantasy-star-iv-md-zh",
        }
        self.assertTrue(required_packs.issubset(by_pack.keys()), by_pack.keys())
        for pack_id in {"shining-force-ii-md", "golden-sun-gba-zh", "chrono-trigger-snes-zh"}:
            categories = by_pack[pack_id]
            self.assertTrue(
                required_categories.issubset(categories),
                f"{pack_id} missing {required_categories - categories}",
            )

    def test_voice_script_has_safe_default_and_debug_evidence_checks(self):
        script = VOICE_SCRIPT.read_text(encoding="utf-8")

        self.assertIn('VOICE="${VOICE:-Tingting}"', script)
        self.assertIn('SAY_RATE="${SAY_RATE:-112}"', script)
        self.assertIn('TTS_BACKEND="${TTS_BACKEND:-macos_say}"', script)
        self.assertIn("sherpa_onnx)", script)
        self.assertIn("speak_prompt", script)
        self.assertIn("tts_artifact", script)
        self.assertIn('RUN_PLAYBACK="${RUN_PLAYBACK:-0}"', script)
        self.assertIn('READY_ATTEMPTS="${READY_ATTEMPTS:-16}"', script)
        self.assertIn("wait_for_overlay_listening", script)
        self.assertIn('EXPECTED_LABEL="$1"', script)
        self.assertIn('RENDER_PHASE="$(printf "%s" "$LAST_OVERLAY" | json_field "render_phase"', script)
        self.assertIn('LABEL="$(printf "%s" "$LAST_OVERLAY" | json_field "label"', script)
        self.assertIn('[ "$LABEL" = "$EXPECTED_LABEL" ]', script)
        self.assertIn('MIC_LIVE="$(printf "%s" "$LAST_OVERLAY" | json_field "mic_live"', script)
        self.assertIn('[ "$MIC_LIVE" = "true" ]', script)
        self.assertIn('AUDIO_READ_COUNT="$(printf "%s" "$LAST_OVERLAY" | json_field "asr_audio_read_count"', script)
        self.assertIn('[ "$AUDIO_READ_COUNT" -gt 0 ]', script)
        self.assertIn('[ "$AUDIO_READY" = "true" ]', script)
        self.assertIn("overlay_before=\"$(wait_for_overlay_completion || true)\"", script)
        self.assertIn('ready_label="$(printf "%s" "$overlay_ready" | json_field "label"', script)
        self.assertIn('[ "$ready_label" != "$label" ]', script)
        self.assertIn('ready_mic_live="$(printf "%s" "$overlay_ready" | json_field "mic_live"', script)
        self.assertIn("overlay was not armed for microphone input with expected label before playback", script)
        self.assertIn('POST_CASE_SECONDS="${POST_CASE_SECONDS:-6}"', script)
        self.assertIn("load_case_fields", script)
        self.assertIn("/debug/hotkey-voice-overlay", script)
        self.assertIn("/debug/latest-request", script)
        self.assertIn("expected_question_source", script)
        self.assertIn("expected_answer_type", script)
        self.assertIn("normalized_question_matched_entity_id", script)
        self.assertIn("source_ids", script)
        self.assertIn("overlay_transcript", script)
        self.assertIn("overlay_normalized_transcript", script)
        self.assertIn("overlay_matched_term", script)
        self.assertIn("normalized_question", script)
        self.assertIn("latest_before_timestamp", script)
        self.assertIn("latest request timestamp unchanged", script)
        self.assertIn("asr_commit_reason", script)
        self.assertIn("asr_last_partial", script)
        self.assertIn("asr_final_text", script)
        self.assertIn("asr_selected_transcript", script)
        self.assertIn("asr_post_voice_silence_ms", script)
        self.assertIn("asr_partial_stable_ms", script)
        self.assertIn("asr_required_stable_ms", script)
        self.assertIn("asr_endpoint_armed", script)
        self.assertIn("asr_final_flush_ms", script)
        self.assertIn("asr_sample_count", script)
        self.assertIn("asr_audio_read_count", script)
        self.assertIn("asr_audio_read_error_count", script)
        self.assertIn("asr_peak_amplitude", script)
        self.assertIn("asr_last_frame_amplitude", script)

    def test_voice_script_dry_run_does_not_require_device(self):
        env = dict(os.environ)
        env["DRY_RUN"] = "1"
        result = subprocess.run(
            [str(VOICE_SCRIPT)],
            cwd=ROOT,
            env=env,
            check=True,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )

        self.assertIn("DRY RUN", result.stdout)
        self.assertIn("shining-force-ii-md", result.stdout)
        self.assertIn("golden-sun-gba-zh", result.stdout)

    def test_voice_script_json_helpers_parse_piped_debug_json(self):
        env = dict(os.environ)
        env["SELF_TEST"] = "1"
        result = subprocess.run(
            [str(VOICE_SCRIPT)],
            cwd=ROOT,
            env=env,
            check=True,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )

        self.assertIn("SELF TEST OK", result.stdout)


def read_cases():
    with CASE_FILE.open(encoding="utf-8", newline="") as handle:
        return [
            row
            for row in csv.DictReader(
                (line for line in handle if line.strip() and not line.startswith("#")),
                delimiter="\t",
            )
        ]


if __name__ == "__main__":
    unittest.main()
