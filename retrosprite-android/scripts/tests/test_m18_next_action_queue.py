import importlib.util
import io
import json
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/m18_next_action_queue.py"


def load_module():
    spec = importlib.util.spec_from_file_location("m18_next_action_queue", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class M18NextActionQueueTest(unittest.TestCase):

    def test_current_queue_marks_observational_voice_frontier_done(self):
        module = load_module()

        queue = module.build_queue()
        markdown = module.render_markdown(queue)
        counts = module.status_counts(queue)

        self.assertEqual(3, len(queue))
        self.assertEqual(3, counts["done"])
        self.assertEqual(0, counts["ready"])
        self.assertEqual(0, counts["blocked"])
        self.assertEqual(set(), {item.action_id for item in queue if item.status == "ready"})
        self.assertIn("./scripts/m18_offline_quality_gate.sh", markdown)
        self.assertNotIn("EXPECT_ALL_PASS=1", markdown)
        self.assertIn("GKP assets edited by this queue: no", markdown)
        self.assertIn(
            "CASE_FILTER=sf2_vigor_ball_observed,golden_sun_ivan_observed,chrono_marle_observed,chrono_atb_observed,ff6_magicite_observed,langrisser_commander_smoke,phantasy_star_tech_skill_smoke",
            markdown,
        )
        self.assertNotIn("approve-asr-patch", markdown)
        self.assertNotIn("run-screen-translation-matrix", markdown)
        self.assertNotIn("complete-content-rights-review", markdown)
        self.assertNotIn("m18_manual_gate_receipt_update.py", markdown)
        self.assertNotIn("--content-rights-approval", markdown)
        device_rerun_action = next(item for item in queue if item.action_id == "rerun-device-lifecycle-row")
        voice_matrix_action = next(item for item in queue if item.action_id == "replay-full-voice-matrix")
        self.assertEqual("done", device_rerun_action.status)
        self.assertEqual("No device lifecycle rerun is required.", device_rerun_action.command)
        self.assertTrue(
            any(item.endswith("gkp-backlog-triage-report.md") for item in device_rerun_action.evidence)
        )
        self.assertEqual("done", voice_matrix_action.status)
        self.assertIn("repeated misses become backlog evidence", voice_matrix_action.acceptance)

    def test_green_summary_marks_queue_done(self):
        module = load_module()
        summary = {
            "requires_human_or_device_evidence": False,
            "open_areas": [],
            "rows": [
                {"area": "GKP backlog", "status": "pass", "detail": ""},
                {"area": "Hotkey voice matrix", "status": "pass", "detail": ""},
                {"area": "GKP patch review packet", "status": "pass", "detail": ""},
                {"area": "GKP asset mutation guard", "status": "pass", "detail": ""},
            ],
        }

        queue = module.build_queue(summary)

        self.assertTrue(all(item.status == "done" for item in queue))

    def test_main_writes_markdown_and_json_and_strict_passes_when_green(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "queue.md"
            json_output = Path(tmp) / "queue.json"
            old_argv = sys.argv
            try:
                sys.argv = [
                    "m18_next_action_queue.py",
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
            self.assertEqual(3, len(data["actions"]))
            self.assertFalse(data["assets_edited_by_report"])
            self.assertEqual([], data["action_ids_by_status"]["ready"])
            self.assertEqual(
                ["rerun-device-lifecycle-row", "replay-full-voice-matrix", "final-m18-offline-gate"],
                data["action_ids_by_status"]["done"],
            )
            self.assertEqual([], data["action_ids_by_status"]["blocked"])
            replay_full = next(
                action
                for action in data["actions"]
                if action["id"] == "replay-full-voice-matrix"
            )
            self.assertIn(
                "CASE_FILTER=sf2_vigor_ball_observed,golden_sun_ivan_observed,chrono_marle_observed,chrono_atb_observed,ff6_magicite_observed,langrisser_commander_smoke,phantasy_star_tech_skill_smoke",
                replay_full["command"],
            )
            device_rerun = next(
                action
                for action in data["actions"]
                if action["id"] == "rerun-device-lifecycle-row"
            )
            self.assertEqual("done", device_rerun["status"])
            self.assertEqual("No device lifecycle rerun is required.", device_rerun["command"])

    def test_device_rerun_action_is_ready_for_open_triage_row(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            triage = tmp_path / "triage.md"
            cases = tmp_path / "cases.tsv"
            triage.write_text(
                "\n".join(
                    [
                        "| Label | Question | Tags | Category | Status | Patch Match | Next Step | Evidence |",
                        "|---|---|---|---|---|---|---|---|",
                        "| `sfc__Chrono Trigger (USA)` | 时空之轮主要玩什么？ | `voice_lifecycle_gap` | `device_rerun_needed` | `open` | - | Rerun. | `build/hotkey-voice-qa/old/results.tsv` |",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )
            cases.write_text(
                "\n".join(
                    [
                        "case_name\tpack_id\tcategory\tlabel\tspoken_prompt\texpected_question_source\texpected_stage\texpected_answer_type\texpected_llm_status\texpected_source\texpected_matched_term\texpected_entity_id\tnotes",
                        "chrono_trigger_core_gameplay\tchrono-trigger-snes-zh\tcore_gameplay\tsfc__Chrono Trigger (USA)\t时空之轮主要玩什么？\thotkey_voice\tevidence\tgame_overview\tskipped\tct.square_enix\t\t\tfixture",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )
            summary = {
                "requires_human_or_device_evidence": True,
                "open_areas": [{"area": "GKP backlog", "status": "open", "detail": ""}],
                "rows": [
                    {"area": "GKP patch review packet", "status": "pass", "detail": ""},
                    {"area": "GKP asset mutation guard", "status": "pass", "detail": ""},
                ],
            }

            queue = module.build_queue(
                summary,
                triage_report_path=triage,
                hotkey_cases_path=cases,
            )

            device_rerun = next(item for item in queue if item.action_id == "rerun-device-lifecycle-row")
            self.assertEqual("ready", device_rerun.status)
            self.assertIn("CASE_FILTER=chrono_trigger_core_gameplay", device_rerun.command)
            self.assertIn("STRICT=0", device_rerun.command)


if __name__ == "__main__":
    unittest.main()
