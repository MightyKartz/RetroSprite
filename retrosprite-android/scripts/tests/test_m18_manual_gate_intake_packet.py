import importlib.util
import io
import json
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/m18_manual_gate_intake_packet.py"


def load_module():
    spec = importlib.util.spec_from_file_location("m18_manual_gate_intake_packet", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class M18ManualGateIntakePacketTest(unittest.TestCase):

    def test_current_packet_renders_done_section_when_no_manual_gate_is_ready(self):
        module = load_module()

        sections = module.build_sections(
            ROOT / "docs/qa-feedback/m18-next-action-queue.json",
            ROOT / "scripts/screen_translation_eval_cases.tsv",
        )
        markdown = module.render_markdown(sections)
        counts = module.status_counts(sections)

        self.assertEqual(1, len(sections))
        self.assertEqual(1, counts["done"])
        self.assertEqual("manual-gates-complete", sections[0].section_id)
        self.assertEqual("done", sections[0].status)
        self.assertIn("No ready manual gate input is required", markdown)
        self.assertIn("GKP assets edited by this packet: no", markdown)
        self.assertNotIn("--section asr-patch-approval", markdown)
        self.assertNotIn("--section content-rights-human-review", markdown)

    def test_no_ready_actions_render_done_section(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            queue = tmp_path / "queue.json"
            cases = tmp_path / "cases.tsv"
            queue.write_text(
                json.dumps(
                    {
                        "actions": [
                            {"id": "approve-asr-patch", "status": "done"},
                            {"id": "run-screen-translation-matrix", "status": "done"},
                            {"id": "complete-content-rights-review", "status": "done"},
                        ]
                    }
                ),
                encoding="utf-8",
            )
            cases.write_text(
                "id\tgame_label\tscreen_type\ttrigger_phrase\texpected_layout\texpected_language\tnumber_policy\tevidence_required\n",
                encoding="utf-8",
            )

            sections = module.build_sections(queue, cases)

            self.assertEqual(1, len(sections))
            self.assertEqual("manual-gates-complete", sections[0].section_id)
            self.assertEqual("done", sections[0].status)

    def test_ready_frontier_prefers_action_ids_by_status(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            queue = tmp_path / "queue.json"
            cases = tmp_path / "cases.tsv"
            queue.write_text(
                json.dumps(
                    {
                        "action_ids_by_status": {
                            "ready": ["approve-asr-patch"],
                            "blocked": [],
                            "done": [],
                        },
                        "actions": [
                            {"id": "approve-asr-patch", "status": "ready", "owner": "human"},
                            {"id": "run-screen-translation-matrix", "status": "ready", "owner": "human/device"},
                            {"id": "complete-content-rights-review", "status": "ready", "owner": "human"},
                        ],
                    }
                ),
                encoding="utf-8",
            )
            cases.write_text(
                "id\tgame_label\tscreen_type\ttrigger_phrase\texpected_layout\texpected_language\tnumber_policy\tevidence_required\n",
                encoding="utf-8",
            )

            sections = module.build_sections(queue, cases)

            self.assertEqual(["asr-patch-approval"], [section.section_id for section in sections])

    def test_ready_frontier_status_mismatch_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            queue = tmp_path / "queue.json"
            cases = tmp_path / "cases.tsv"
            queue.write_text(
                json.dumps(
                    {
                        "action_ids_by_status": {
                            "ready": ["approve-asr-patch"],
                            "blocked": [],
                            "done": [],
                        },
                        "actions": [
                            {"id": "approve-asr-patch", "status": "blocked", "owner": "human"},
                        ],
                    }
                ),
                encoding="utf-8",
            )
            cases.write_text(
                "id\tgame_label\tscreen_type\ttrigger_phrase\texpected_layout\texpected_language\tnumber_policy\tevidence_required\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(ValueError, "action_ids_by_status.ready"):
                module.build_sections(queue, cases)

    def test_ready_frontier_unknown_action_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            queue = tmp_path / "queue.json"
            cases = tmp_path / "cases.tsv"
            queue.write_text(
                json.dumps(
                    {
                        "action_ids_by_status": {
                            "ready": ["missing-action"],
                            "blocked": [],
                            "done": [],
                        },
                        "actions": [
                            {"id": "approve-asr-patch", "status": "ready", "owner": "human"},
                        ],
                    }
                ),
                encoding="utf-8",
            )
            cases.write_text(
                "id\tgame_label\tscreen_type\ttrigger_phrase\texpected_layout\texpected_language\tnumber_policy\tevidence_required\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(ValueError, "missing from actions"):
                module.build_sections(queue, cases)

    def test_main_writes_markdown_and_json_and_strict_passes_when_no_manual_gate_is_ready(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "intake.md"
            json_output = Path(tmp) / "intake.json"
            old_argv = sys.argv
            try:
                sys.argv = [
                    "m18_manual_gate_intake_packet.py",
                    "--output",
                    str(output),
                    "--json-output",
                    str(json_output),
                    "--strict",
                ]
                with redirect_stdout(io.StringIO()):
                    result = module.main()
            finally:
                sys.argv = old_argv

            self.assertEqual(0, result)
            self.assertTrue(output.is_file())
            self.assertTrue(json_output.is_file())
            data = json.loads(json_output.read_text(encoding="utf-8"))
            self.assertEqual(1, data["schema_version"])
            self.assertEqual(1, len(data["sections"]))
            self.assertEqual({"done": 1}, data["counts"])
            self.assertFalse(data["assets_edited_by_report"])

    def test_device_voice_rerun_section_renders_when_action_ready(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            queue = tmp_path / "queue.json"
            cases = tmp_path / "cases.tsv"
            queue.write_text(
                json.dumps(
                    {
                        "actions": [
                            {
                                "id": "rerun-device-lifecycle-row",
                                "status": "ready",
                                "owner": "human/device",
                                "evidence": [
                                    "docs/qa-feedback/gkp-backlog-triage-report.md",
                                    "build/hotkey-voice-qa/20260602-051126/results.tsv",
                                ],
                                "command": "RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 \\\nCASE_FILTER=chrono_trigger_core_gameplay \\\nSTRICT=0 \\\n./scripts/hotkey_voice_qa_batch.sh",
                                "acceptance": "The rerun records a fresh hotkey_voice request or fresh overlay audio diagnostics for the device_rerun_needed row; do not edit GKP assets from this action.",
                            }
                        ]
                    }
                ),
                encoding="utf-8",
            )
            cases.write_text(
                "id\tgame_label\tscreen_type\ttrigger_phrase\texpected_layout\texpected_language\tnumber_policy\tevidence_required\n",
                encoding="utf-8",
            )

            sections = module.build_sections(queue, cases)
            markdown = module.render_markdown(sections)

            self.assertEqual(1, len(sections))
            self.assertEqual("device-voice-lifecycle-rerun", sections[0].section_id)
            self.assertIn("Device voice lifecycle rerun input", markdown)
            self.assertIn("CASE_FILTER=chrono_trigger_core_gameplay", markdown)
            self.assertIn("STRICT=0", markdown)
            self.assertIn("muted_recovery", markdown)
            self.assertIn("build/hotkey-voice-qa/20260602-051126/results.tsv", markdown)


if __name__ == "__main__":
    unittest.main()
