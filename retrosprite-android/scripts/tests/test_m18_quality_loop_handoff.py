import importlib.util
import io
import json
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/m18_quality_loop_handoff.py"


def load_module():
    spec = importlib.util.spec_from_file_location("m18_quality_loop_handoff", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class M18QualityLoopHandoffTest(unittest.TestCase):

    def test_current_handoff_summarizes_ready_loop_and_safe_imports(self):
        module = load_module()

        summary = module.build_summary(
            ROOT / "docs/qa-feedback/m18-gate-status.json",
            ROOT / "docs/qa-feedback/m18-next-action-queue.json",
        )
        markdown = module.render_markdown(
            summary,
            gate_status=ROOT / "docs/qa-feedback/m18-gate-status.json",
            action_queue=ROOT / "docs/qa-feedback/m18-next-action-queue.json",
            backlog=ROOT / "docs/qa-feedback/gkp-quality-backlog.md",
            manual_notes_template=ROOT / "docs/qa-feedback/gkp-manual-notes-template.tsv",
        )
        data = module.render_json(
            summary,
            gate_status=ROOT / "docs/qa-feedback/m18-gate-status.json",
            action_queue=ROOT / "docs/qa-feedback/m18-next-action-queue.json",
            backlog=ROOT / "docs/qa-feedback/gkp-quality-backlog.md",
            manual_notes_template=ROOT / "docs/qa-feedback/gkp-manual-notes-template.tsv",
        )

        self.assertEqual("pass", summary.overall_status)
        self.assertEqual("ready_for_ongoing_rc_cycle", summary.loop_status)
        self.assertEqual((), summary.ready_actions)
        self.assertIn("review_packet_rows=7", summary.backlog_detail)
        self.assertIn("triage_open=0", summary.backlog_detail)
        self.assertIn("manual_asr_approval_required=no", summary.backlog_detail)
        self.assertIn("pass=4; fail=3", summary.voice_detail)
        self.assertIn("gate=observational", summary.voice_detail)
        self.assertIn("GKP assets edited by this handoff: no", markdown)
        self.assertIn("Preview-First Backlog Commands", markdown)
        self.assertIn("build/m18-latest-request-backlog-preview.md", markdown)
        self.assertIn("build/m18-voice-backlog-preview.md", markdown)
        self.assertIn("build/m18-manual-notes-backlog-preview.md", markdown)
        self.assertIn("--merge-existing-backlog docs/qa-feedback/gkp-quality-backlog.md", markdown)
        self.assertIn("--manual-notes-template-output docs/qa-feedback/gkp-manual-notes-template.tsv", markdown)
        self.assertIn("--output docs/qa-feedback/gkp-quality-backlog.md", markdown)
        self.assertIn("Do not add new game content until the current six bundled packs", markdown)
        self.assertIn("./scripts/m18_offline_quality_gate.sh", markdown)
        self.assertNotIn("EXPECT_ALL_PASS=1", markdown)
        self.assertNotIn("--apply", markdown)
        self.assertEqual("ready_for_ongoing_rc_cycle", data["loop_status"])
        self.assertFalse(data["assets_edited_by_handoff"])
        self.assertEqual(0, data["counts"]["ready"])
        self.assertEqual(0, data["counts"]["blocked"])
        self.assertIn("latest_request", [command["id"] for command in data["preview_backlog_commands"]])
        self.assertNotIn("receipt_failures", [command["id"] for command in data["preview_backlog_commands"]])
        self.assertTrue(data["contract"]["preview_first_backlog_imports"])
        self.assertTrue(data["contract"]["no_new_games_until_green_rc"])

    def test_green_summary_reports_ready_for_ongoing_cycle(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            gate_status = tmp_path / "gate.json"
            queue = tmp_path / "queue.json"
            gate_status.write_text(
                json.dumps(
                    {
                        "overall_status": "pass",
                        "open_areas": [],
                        "rows": [
                            {"area": "GKP backlog", "detail": "items=0"},
                            {"area": "Hotkey voice matrix", "detail": "pass=7; fail=0"},
                        ],
                    }
                ),
                encoding="utf-8",
            )
            queue.write_text(
                json.dumps(
                    {
                        "actions": [
                            {"id": "final-m18-offline-gate", "status": "done"},
                        ]
                    }
                ),
                encoding="utf-8",
            )

            summary = module.build_summary(gate_status, queue)

            self.assertEqual("ready_for_ongoing_rc_cycle", summary.loop_status)
            self.assertEqual(("final-m18-offline-gate",), summary.done_actions)

    def test_build_summary_prefers_grouped_action_ids(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            gate_status = tmp_path / "gate.json"
            queue = tmp_path / "queue.json"
            gate_status.write_text(
                json.dumps(
                    {
                        "overall_status": "open",
                        "open_areas": [{"area": "GKP backlog"}],
                        "rows": [
                            {"area": "GKP backlog", "detail": "items=1"},
                            {"area": "Hotkey voice matrix", "detail": "pass=0"},
                        ],
                    }
                ),
                encoding="utf-8",
            )
            queue.write_text(
                json.dumps(
                    {
                        "action_ids_by_status": {
                            "ready": ["grouped-ready"],
                            "blocked": ["grouped-blocked"],
                            "done": ["grouped-done"],
                        },
                        "actions": [
                            {"id": "legacy-ready", "status": "ready"},
                            {"id": "legacy-blocked", "status": "blocked"},
                            {"id": "legacy-done", "status": "done"},
                        ],
                    }
                ),
                encoding="utf-8",
            )

            summary = module.build_summary(gate_status, queue)

            self.assertEqual(("grouped-ready",), summary.ready_actions)
            self.assertEqual(("grouped-blocked",), summary.blocked_actions)
            self.assertEqual(("grouped-done",), summary.done_actions)

    def test_build_summary_accepts_grouped_action_ids_without_actions_array(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            gate_status = tmp_path / "gate.json"
            queue = tmp_path / "queue.json"
            gate_status.write_text(
                json.dumps(
                    {
                        "overall_status": "open",
                        "open_areas": [],
                        "rows": [
                            {"area": "GKP backlog", "detail": "items=0"},
                            {"area": "Hotkey voice matrix", "detail": "pass=7"},
                        ],
                    }
                ),
                encoding="utf-8",
            )
            queue.write_text(
                json.dumps(
                    {
                        "action_ids_by_status": {
                            "ready": [],
                            "blocked": [],
                            "done": ["final-m18-offline-gate"],
                        }
                    }
                ),
                encoding="utf-8",
            )

            summary = module.build_summary(gate_status, queue)

            self.assertEqual((), summary.ready_actions)
            self.assertEqual((), summary.blocked_actions)
            self.assertEqual(("final-m18-offline-gate",), summary.done_actions)

    def test_build_summary_fails_when_required_inputs_are_missing(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            missing = Path(tmp) / "missing.json"

            with self.assertRaisesRegex(ValueError, "required file not found"):
                module.build_summary(missing, missing)

    def test_main_writes_handoff(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "handoff.md"
            json_output = Path(tmp) / "handoff.json"
            old_argv = sys.argv
            try:
                sys.argv = [
                    "m18_quality_loop_handoff.py",
                    "--output",
                    str(output),
                    "--json-output",
                    str(json_output),
                ]
                with redirect_stdout(io.StringIO()) as stdout:
                    result = module.main()
            finally:
                sys.argv = old_argv

            self.assertEqual(0, result)
            self.assertTrue(output.is_file())
            self.assertTrue(json_output.is_file())
            self.assertIn("OK M18 quality loop handoff", stdout.getvalue())
            self.assertEqual(
                "ready_for_ongoing_rc_cycle",
                json.loads(json_output.read_text(encoding="utf-8"))["loop_status"],
            )


if __name__ == "__main__":
    unittest.main()
