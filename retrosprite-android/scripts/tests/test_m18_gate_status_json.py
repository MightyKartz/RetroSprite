import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/m18_gate_status_json.py"


def load_module():
    spec = importlib.util.spec_from_file_location("m18_gate_status_json", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class M18GateStatusJsonTest(unittest.TestCase):

    def test_current_summary_is_machine_readable_and_pass(self):
        module = load_module()

        summary = module.build_summary()

        self.assertEqual(1, summary["schema_version"])
        self.assertEqual("M18 Eval Lab + GKP Quality Loop", summary["objective"])
        self.assertEqual("pass", summary["overall_status"])
        self.assertFalse(summary["assets_edited_by_report"])
        self.assertFalse(summary["requires_human_or_device_evidence"])
        self.assertEqual(10, len(summary["rows"]))
        self.assertEqual(10, summary["counts"]["pass"])
        self.assertNotIn("open", summary["counts"])
        self.assertIn("Command contract audit", [row["area"] for row in summary["rows"]])
        self.assertIn("M18 quality loop handoff", [row["area"] for row in summary["rows"]])
        self.assertNotIn("Screen translation matrix", [row["area"] for row in summary["rows"]])
        self.assertNotIn("Release checklist", [row["area"] for row in summary["rows"]])
        self.assertEqual([], summary["open_areas"])

    def test_main_writes_json_and_strict_passes_when_observational_voice_is_recorded(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "status.json"
            old_argv = sys.argv
            try:
                sys.argv = ["m18_gate_status_json.py", "--output", str(output), "--strict"]
                result = module.main()
            finally:
                sys.argv = old_argv

            self.assertEqual(0, result)
            data = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual("pass", data["overall_status"])
            self.assertIn("open_areas", data)


if __name__ == "__main__":
    unittest.main()
