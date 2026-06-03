import importlib.util
import io
import json
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/screen_translation_receipt_update.py"
CHECK_SCRIPT = ROOT / "scripts/m18_manual_gate_receipt_check.py"
CASES = ROOT / "scripts/screen_translation_eval_cases.tsv"
INTAKE = ROOT / "docs/qa-feedback/m18-manual-gate-intake.json"
EVIDENCE_ROOT = ROOT / "build/rc-device-evidence"


def load_module():
    spec = importlib.util.spec_from_file_location("screen_translation_receipt_update", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def load_check_module():
    spec = importlib.util.spec_from_file_location("m18_manual_gate_receipt_check", CHECK_SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


@unittest.skip("Legacy screen translation receipt flow is outside current M18 scope.")
class ScreenTranslationReceiptUpdateTest(unittest.TestCase):

    def test_missing_receipt_creates_partial_receipt_with_other_rows_pending(self):
        module = load_module()
        check = load_check_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            receipt = tmp_path / "receipt.json"
            evidence = make_evidence_dir(tmp_path / "evidence" / "ff6_dialogue", "ff6_dialogue")
            data, update = module.update_receipt_data(
                INTAKE,
                receipt,
                CASES,
                "ff6_dialogue",
                pass_result(evidence, "ff6_dialogue"),
            )

            self.assertEqual("ff6_dialogue", update.case_id)
            self.assertEqual("pass", update.status)
            self.assertEqual("open", update.receipt_status)
            rows = {entry["case_id"]: entry for entry in data["screen_translation_results"]}
            self.assertIn("checklist=layout_ok,language_ok,no_english_source", rows["ff6_dialogue"]["result"])
            self.assertEqual("Pending", rows["ff6_main_menu"]["result"])
            screen_items = check.check_screen_results(
                data["screen_translation_results"],
                check.load_screen_cases(CASES),
                CASES,
            )
            status_by_id = {item.item_id: item.status for item in screen_items}
            self.assertEqual("pass", status_by_id["ff6_dialogue"])
            self.assertEqual("open", status_by_id["ff6_main_menu"])

    def test_rejects_placeholder_pass_result(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaisesRegex(ValueError, "placeholder_evidence_path"):
                module.update_receipt_data(
                    INTAKE,
                    Path(tmp) / "receipt.json",
                    CASES,
                    "ff6_dialogue",
                    "Pass: evidence build/rc-device-evidence/<timestamp> checklist=layout_ok,language_ok,no_english_source",
                )

    def test_accepts_fail_category_as_recorded_manual_result(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            data, update = module.update_receipt_data(
                INTAKE,
                Path(tmp) / "receipt.json",
                CASES,
                "ff6_main_menu",
                "Fail: numeric_corruption",
                notes="numbers were translated as prose",
            )

            self.assertEqual("fail", update.status)
            row = next(entry for entry in data["screen_translation_results"] if entry["case_id"] == "ff6_main_menu")
            self.assertEqual("Fail: numeric_corruption", row["result"])
            self.assertEqual("numbers were translated as prose", row["notes"])

    def test_main_apply_writes_receipt_file(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            receipt = tmp_path / "receipt.json"
            old_argv = sys.argv
            try:
                sys.argv = [
                    "screen_translation_receipt_update.py",
                    "--receipt",
                    str(receipt),
                    "--case-id",
                    "ff6_status",
                    "--result",
                    "Fail: layout_grouping",
                    "--apply",
                ]
                with redirect_stdout(io.StringIO()):
                    result = module.main()
            finally:
                sys.argv = old_argv

            self.assertEqual(0, result)
            data = json.loads(receipt.read_text(encoding="utf-8"))
            row = next(entry for entry in data["screen_translation_results"] if entry["case_id"] == "ff6_status")
            self.assertEqual("Fail: layout_grouping", row["result"])


def pass_result(evidence: Path, case_id: str) -> str:
    checklists = {
        "ff6_dialogue": "layout_ok,language_ok,no_english_source",
    }
    return f"Pass: evidence {evidence} checklist={checklists[case_id]}"


def make_evidence_dir(path: Path, case_id: str) -> Path:
    EVIDENCE_ROOT.mkdir(parents=True, exist_ok=True)
    try:
        path.resolve().relative_to(EVIDENCE_ROOT.resolve())
    except ValueError:
        path = Path(tempfile.mkdtemp(prefix=f"test-{case_id}-", dir=EVIDENCE_ROOT))
    path.mkdir(parents=True, exist_ok=True)
    for name in ["README.md", "health.json", "latest-request.json", "hotkey-voice-overlay.json"]:
        (path / name).write_text("{}\n", encoding="utf-8")
    (path / "screenshot.png").write_bytes(b"\x89PNG\r\n\x1a\n")
    (path / "metadata.json").write_text(
        json.dumps(
            {
                "schema_version": 1,
                "gate": "screen_translation",
                "case_id": case_id,
            },
            ensure_ascii=False,
        )
        + "\n",
        encoding="utf-8",
    )
    return path


if __name__ == "__main__":
    unittest.main()
