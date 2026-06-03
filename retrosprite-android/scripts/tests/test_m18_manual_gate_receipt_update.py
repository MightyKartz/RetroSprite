import importlib.util
import io
import json
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/m18_manual_gate_receipt_update.py"
CHECK_SCRIPT = ROOT / "scripts/m18_manual_gate_receipt_check.py"
INTAKE = ROOT / "docs/qa-feedback/m18-manual-gate-intake.json"
CASES = ROOT / "scripts/screen_translation_eval_cases.tsv"


def load_module():
    spec = importlib.util.spec_from_file_location("m18_manual_gate_receipt_update", SCRIPT)
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


@unittest.skip("Legacy manual receipt flow is outside current M18 scope.")
class M18ManualGateReceiptUpdateTest(unittest.TestCase):

    def test_missing_receipt_records_asr_approval_and_keeps_other_rows_pending(self):
        module = load_module()
        check = load_check_module()
        with tempfile.TemporaryDirectory() as tmp:
            receipt = Path(tmp) / "receipt.json"

            data, update = module.update_receipt_data(
                intake_path=INTAKE,
                receipt_path=receipt,
                screen_cases_path=CASES,
                section_id="asr-patch-approval",
                decision="approved",
                approval_phrase=check.ASR_APPROVAL_PHRASE,
                reviewer="qa-reviewer",
                notes="Rows reviewed against packet.",
            )

            self.assertEqual("asr-patch-approval", update.section_id)
            self.assertEqual("pass", update.status)
            self.assertEqual("open", update.receipt_status)
            self.assertTrue(update.changed)
            self.assertEqual("approved", data["asr_patch_approval"]["decision"])
            self.assertEqual(check.ASR_APPROVAL_PHRASE, data["asr_patch_approval"]["approval_phrase"])
            self.assertEqual("qa-reviewer", data["asr_patch_approval"]["reviewer"])
            self.assertEqual(5, len(data["asr_patch_approval"]["review_rows"]))
            self.assertTrue(all(row["result"] == "Pending" for row in data["screen_translation_results"]))
            self.assertEqual("pending", data["content_rights_review"]["decision"])

    def test_wrong_asr_approval_phrase_is_rejected(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaisesRegex(ValueError, "approval phrase"):
                module.update_receipt_data(
                    intake_path=INTAKE,
                    receipt_path=Path(tmp) / "receipt.json",
                    screen_cases_path=CASES,
                    section_id="asr-patch-approval",
                    decision="approved",
                    approval_phrase="wrong",
                    reviewer="qa-reviewer",
                    notes="",
                )

    def test_content_rights_approval_preserves_current_review_scope(self):
        module = load_module()
        check = load_check_module()
        with tempfile.TemporaryDirectory() as tmp:
            data, update = module.update_receipt_data(
                intake_path=INTAKE,
                receipt_path=Path(tmp) / "receipt.json",
                screen_cases_path=CASES,
                section_id="content-rights-human-review",
                decision="approved",
                approval_phrase=check.CONTENT_RIGHTS_APPROVAL_PHRASE,
                reviewer="release-reviewer",
                notes="Spot-check completed.",
            )

            self.assertEqual("content-rights-human-review", update.section_id)
            self.assertEqual("pass", update.status)
            self.assertEqual("open", update.receipt_status)
            self.assertEqual("approved", data["content_rights_review"]["decision"])
            self.assertEqual(check.CONTENT_RIGHTS_APPROVAL_PHRASE, data["content_rights_review"]["approval_phrase"])
            self.assertEqual("pass", data["content_rights_review"]["review_scope"]["machine_audit_status"])
            self.assertEqual(6, data["content_rights_review"]["review_scope"]["bundled_packs"])
            self.assertEqual("pending", data["asr_patch_approval"]["decision"])

    def test_rejected_decision_records_open_receipt_without_exact_phrase(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            data, update = module.update_receipt_data(
                intake_path=INTAKE,
                receipt_path=Path(tmp) / "receipt.json",
                screen_cases_path=CASES,
                section_id="asr-patch-approval",
                decision="rejected",
                approval_phrase="",
                reviewer="",
                notes="Reject row 2.",
            )

            self.assertEqual("open", update.status)
            self.assertEqual("rejected", data["asr_patch_approval"]["decision"])
            self.assertEqual("Reject row 2.", data["asr_patch_approval"]["notes"])

    def test_main_preview_writes_output_file(self):
        module = load_module()
        check = load_check_module()
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "receipt-preview.json"
            old_argv = sys.argv
            try:
                sys.argv = [
                    "m18_manual_gate_receipt_update.py",
                    "--section",
                    "content-rights-human-review",
                    "--decision",
                    "approved",
                    "--approval-phrase",
                    check.CONTENT_RIGHTS_APPROVAL_PHRASE,
                    "--reviewer",
                    "release-reviewer",
                    "--output",
                    str(output),
                ]
                with redirect_stdout(io.StringIO()):
                    result = module.main()
            finally:
                sys.argv = old_argv

            self.assertEqual(0, result)
            self.assertTrue(output.is_file())
            data = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual("approved", data["content_rights_review"]["decision"])


if __name__ == "__main__":
    unittest.main()
