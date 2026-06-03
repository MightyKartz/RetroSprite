import importlib.util
import io
import json
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/m18_completion_audit.py"


def load_module():
    spec = importlib.util.spec_from_file_location("m18_completion_audit", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class M18CompletionAuditTest(unittest.TestCase):

    def test_current_audit_is_complete_with_observational_voice_matrix(self):
        module = load_module()

        audit = module.build_audit(ROOT / "docs/qa-feedback/m18-plan-execution-audit.md")
        markdown = module.render_markdown(audit, ROOT / "docs/qa-feedback/m18-plan-execution-audit.md")

        self.assertEqual("pass", audit.overall_status)
        self.assertEqual(14, audit.plan_checked)
        self.assertEqual(0, audit.plan_unchecked)
        self.assertIn("aggregate-hotkey-voice-matrix", markdown)
        self.assertIn("aggregate-command-contract-audit", markdown)
        self.assertIn("aggregate-m18-quality-loop-handoff", markdown)
        self.assertIn("machine-device-evidence", markdown)
        self.assertIn("final-offline-gate", markdown)
        self.assertIn("M18 is complete according to this audit", markdown)
        self.assertIn("GKP assets edited by this audit: no", markdown)
        data = module.render_json(audit, ROOT / "docs/qa-feedback/m18-plan-execution-audit.md")
        self.assertEqual("pass", data["overall_status"])
        self.assertTrue(data["is_complete"])
        self.assertEqual(14, data["plan_checked"])
        self.assertEqual(0, data["plan_unchecked"])
        self.assertFalse(data["assets_edited_by_report"])
        self.assertEqual(
            {"pass": 14, "open": 0, "missing": 0, "fail": 0},
            data["counts"],
        )
        self.assertIn(
            "aggregate-hotkey-voice-matrix",
            [item["id"] for item in data["requirements"]],
        )

    def test_build_audit_can_prove_a_fully_green_fixture(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            plan_audit = Path(tmp) / "audit.md"
            plan_audit.write_text("- Plan checkboxes: checked=2, unchecked=0\n", encoding="utf-8")
            summary = {
                "overall_status": "pass",
                "assets_edited_by_report": False,
                "requires_human_or_device_evidence": False,
                "rows": [
                    {
                        "area": "GKP coverage",
                        "status": "pass",
                        "evidence": "gkp.md",
                        "detail": "packs=6",
                    }
                ],
            }

            audit = module.build_audit(plan_audit, gate_summary=summary)
            data = module.render_json(audit, plan_audit)

            self.assertEqual("pass", audit.overall_status)
            self.assertTrue(audit.is_complete)
            self.assertTrue(all(item.status == "pass" for item in audit.requirements))
            self.assertEqual("pass", data["overall_status"])
            self.assertTrue(data["is_complete"])
            self.assertEqual(0, data["counts"]["open"])
            self.assertEqual(0, data["counts"]["fail"])

    def test_missing_plan_counts_are_not_treated_as_complete(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            plan_audit = Path(tmp) / "audit.md"
            plan_audit.write_text("# no counts\n", encoding="utf-8")
            summary = {
                "overall_status": "pass",
                "assets_edited_by_report": False,
                "requires_human_or_device_evidence": False,
                "rows": [],
            }

            audit = module.build_audit(plan_audit, gate_summary=summary)

            self.assertEqual("fail", audit.overall_status)
            self.assertIn("missing", {item.status for item in audit.requirements})

    def test_plan_counts_prefer_sibling_json_over_markdown(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            plan_audit = Path(tmp) / "audit.md"
            plan_json = Path(tmp) / "audit.json"
            plan_audit.write_text("- Plan checkboxes: checked=2, unchecked=0\n", encoding="utf-8")
            plan_json.write_text(
                json.dumps(
                    {
                        "plan_checked": 5,
                        "plan_unchecked": 1,
                        "counts": {
                            "plan_checked": 5,
                            "plan_unchecked": 1,
                        },
                    }
                ),
                encoding="utf-8",
            )

            checked, unchecked = module.read_plan_counts(plan_audit)

            self.assertEqual(5, checked)
            self.assertEqual(1, unchecked)

    def test_plan_counts_can_read_json_path_directly(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            plan_json = Path(tmp) / "audit.json"
            plan_json.write_text(
                json.dumps(
                    {
                        "counts": {
                            "plan_checked": 8,
                            "plan_unchecked": 3,
                        },
                    }
                ),
                encoding="utf-8",
            )

            checked, unchecked = module.read_plan_counts(plan_json)

            self.assertEqual(8, checked)
            self.assertEqual(3, unchecked)

    def test_main_writes_report_and_strict_passes_when_current_gates_are_complete(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "completion.md"
            json_output = Path(tmp) / "completion.json"
            old_argv = sys.argv
            try:
                sys.argv = [
                    "m18_completion_audit.py",
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
            self.assertIn("Overall status: `pass`", output.read_text(encoding="utf-8"))
            self.assertEqual("pass", json.loads(json_output.read_text(encoding="utf-8"))["overall_status"])


if __name__ == "__main__":
    unittest.main()
