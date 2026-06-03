import importlib.util
import io
import json
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/m18_manual_gate_receipt_check.py"
EVIDENCE_ROOT = ROOT / "build/rc-device-evidence"


def load_module():
    spec = importlib.util.spec_from_file_location("m18_manual_gate_receipt_check", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


@unittest.skip("Legacy manual receipt flow is outside current M18 scope.")
class M18ManualGateReceiptCheckTest(unittest.TestCase):

    def test_missing_receipt_generates_open_check_and_template(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            receipt = tmp_path / "missing.json"
            template = tmp_path / "template.json"
            output = tmp_path / "check.md"
            json_output = tmp_path / "check.json"

            old_argv = sys.argv
            try:
                sys.argv = [
                    "m18_manual_gate_receipt_check.py",
                    "--receipt",
                    str(receipt),
                    "--template-output",
                    str(template),
                    "--output",
                    str(output),
                    "--json-output",
                    str(json_output),
                ]
                with redirect_stdout(io.StringIO()):
                    result = module.main()
            finally:
                sys.argv = old_argv

            self.assertEqual(0, result)
            self.assertTrue(template.is_file())
            self.assertTrue(output.is_file())
            self.assertTrue(json_output.is_file())
            data = json.loads(template.read_text(encoding="utf-8"))
            check_data = json.loads(json_output.read_text(encoding="utf-8"))
            self.assertEqual("open", check_data["status"])
            self.assertFalse(check_data["receipt_present"])
            self.assertEqual({"pass": 0, "open": 3, "fail": 0}, check_data["counts"])
            self.assertFalse(check_data["assets_edited_by_report"])
            self.assertEqual(
                ["asr-patch-approval", "content-rights-human-review", "screen-translation-manual-results"],
                [item["id"] for item in check_data["items"]],
            )
            self.assertIn("asr_patch_approval", data)
            self.assertIn("screen_translation_results", data)
            self.assertIn("content_rights_review", data)
            self.assertIn("Do not edit ASR review_rows", data["notes"])
            self.assertIn("content_rights_review.review_scope", data["notes"])
            self.assertIn("--gate screen_translation --case-id <case_id>", data["notes"])
            self.assertIn("checklist= tokens", data["notes"])
            self.assertEqual(5, len(data["asr_patch_approval"]["review_rows"]))
            self.assertEqual("pass", data["content_rights_review"]["review_scope"]["machine_audit_status"])
            self.assertEqual(6, data["content_rights_review"]["review_scope"]["bundled_packs"])
            self.assertEqual(
                "community.chrono-trigger-snes-zh",
                data["content_rights_review"]["review_scope"]["pack_inventory"][0]["pack_id"],
            )
            self.assertEqual(
                "super_nintendo__Final Fantasy VI (USA)",
                data["screen_translation_results"][0]["game_label"],
            )
            self.assertEqual("dialogue", data["screen_translation_results"][0]["screen_type"])
            self.assertEqual("翻译", data["screen_translation_results"][0]["trigger_phrase"])
            self.assertEqual("chinese_only", data["screen_translation_results"][0]["expected_layout"])
            self.assertEqual("zh", data["screen_translation_results"][0]["expected_language"])
            self.assertEqual("no_numbers", data["screen_translation_results"][0]["number_policy"])
            self.assertEqual("manual_screenshot", data["screen_translation_results"][0]["evidence_required"])
            self.assertIn("checklist=layout_ok,language_ok,no_english_source", data["screen_translation_results"][0]["result"])
            self.assertEqual(
                "community.shining-force-ii-md",
                data["asr_patch_approval"]["review_rows"][0]["pack_id"],
            )
            self.assertEqual(
                "契河之域怎么",
                data["asr_patch_approval"]["review_rows"][0]["observed_asr"],
            )
            self.assertEqual(
                "气合之玉怎么用",
                data["asr_patch_approval"]["review_rows"][0]["canonical_term"],
            )
            self.assertIn("Overall status: `open`", output.read_text(encoding="utf-8"))

    def test_valid_receipt_passes_all_current_ready_sections(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            receipt = tmp_path / "receipt.json"
            receipt.write_text(json.dumps(valid_receipt(module, tmp_path / "evidence")), encoding="utf-8")

            check = module.build_check(
                module.load_json(ROOT / "docs/qa-feedback/m18-manual-gate-intake.json", "manual gate intake"),
                receipt,
                module.load_screen_cases(ROOT / "scripts/screen_translation_eval_cases.tsv"),
            )

            self.assertEqual("pass", check.status)
            self.assertTrue(all(item.status == "pass" for item in check.items))
            check_data = module.render_json(check, receipt, tmp_path / "template.json")
            self.assertEqual("pass", check_data["status"])
            self.assertTrue(check_data["receipt_present"])
            self.assertEqual(0, check_data["counts"]["open"])
            self.assertEqual(0, check_data["counts"]["fail"])
            self.assertFalse(check_data["assets_edited_by_report"])

    def test_missing_schema_version_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            receipt = tmp_path / "receipt.json"
            payload = valid_receipt(module, tmp_path / "evidence")
            payload.pop("schema_version")
            receipt.write_text(json.dumps(payload), encoding="utf-8")

            check = module.build_check(
                module.load_json(ROOT / "docs/qa-feedback/m18-manual-gate-intake.json", "manual gate intake"),
                receipt,
                module.load_screen_cases(ROOT / "scripts/screen_translation_eval_cases.tsv"),
            )

            self.assertEqual("fail", check.status)
            failing = {item.item_id: item.detail for item in check.items if item.status == "fail"}
            self.assertEqual("schema_version must be 1", failing["receipt-schema-version"])

    def test_not_run_screen_result_keeps_receipt_open(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            receipt = tmp_path / "receipt.json"
            payload = valid_receipt(module, tmp_path / "evidence")
            payload["screen_translation_results"][0]["result"] = "Not run"
            receipt.write_text(json.dumps(payload), encoding="utf-8")

            check = module.build_check(
                module.load_json(ROOT / "docs/qa-feedback/m18-manual-gate-intake.json", "manual gate intake"),
                receipt,
                module.load_screen_cases(ROOT / "scripts/screen_translation_eval_cases.tsv"),
            )

            self.assertEqual("open", check.status)
            open_items = {item.item_id: item.detail for item in check.items if item.status == "open"}
            self.assertEqual("result is pending or unsupported", open_items["ff6_dialogue"])

    def test_wrong_objective_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            receipt = tmp_path / "receipt.json"
            payload = valid_receipt(module, tmp_path / "evidence")
            payload["objective"] = "M17 Release Candidate Hardening"
            receipt.write_text(json.dumps(payload), encoding="utf-8")

            check = module.build_check(
                module.load_json(ROOT / "docs/qa-feedback/m18-manual-gate-intake.json", "manual gate intake"),
                receipt,
                module.load_screen_cases(ROOT / "scripts/screen_translation_eval_cases.tsv"),
            )

            self.assertEqual("fail", check.status)
            failing = {item.item_id: item.detail for item in check.items if item.status == "fail"}
            self.assertEqual("objective does not match M18", failing["receipt-objective"])

    def test_wrong_asr_phrase_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            receipt = tmp_path / "receipt.json"
            payload = valid_receipt(module, tmp_path / "evidence")
            payload["asr_patch_approval"]["approval_phrase"] = "wrong"
            receipt.write_text(json.dumps(payload), encoding="utf-8")

            check = module.build_check(
                module.load_json(ROOT / "docs/qa-feedback/m18-manual-gate-intake.json", "manual gate intake"),
                receipt,
                module.load_screen_cases(ROOT / "scripts/screen_translation_eval_cases.tsv"),
            )

            self.assertEqual("fail", check.status)
            self.assertIn("asr-patch-approval", [item.item_id for item in check.items if item.status == "fail"])

    def test_approved_asr_without_reviewer_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            receipt = tmp_path / "receipt.json"
            payload = valid_receipt(module, tmp_path / "evidence")
            payload["asr_patch_approval"]["reviewer"] = ""
            receipt.write_text(json.dumps(payload), encoding="utf-8")

            check = module.build_check(
                module.load_json(ROOT / "docs/qa-feedback/m18-manual-gate-intake.json", "manual gate intake"),
                receipt,
                module.load_screen_cases(ROOT / "scripts/screen_translation_eval_cases.tsv"),
            )

            self.assertEqual("fail", check.status)
            failing = {item.item_id: item.detail for item in check.items if item.status == "fail"}
            self.assertEqual("reviewer is required for approved receipt", failing["asr-patch-approval"])

    def test_approved_asr_without_review_rows_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            receipt = tmp_path / "receipt.json"
            payload = valid_receipt(module, tmp_path / "evidence")
            payload["asr_patch_approval"].pop("review_rows")
            receipt.write_text(json.dumps(payload), encoding="utf-8")

            check = module.build_check(
                module.load_json(ROOT / "docs/qa-feedback/m18-manual-gate-intake.json", "manual gate intake"),
                receipt,
                module.load_screen_cases(ROOT / "scripts/screen_translation_eval_cases.tsv"),
            )

            self.assertEqual("fail", check.status)
            failing = {item.item_id: item.detail for item in check.items if item.status == "fail"}
            self.assertEqual(
                "review_rows must match the current ASR review packet rows exactly",
                failing["asr-patch-approval"],
            )

    def test_approved_asr_with_modified_review_row_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            receipt = tmp_path / "receipt.json"
            payload = valid_receipt(module, tmp_path / "evidence")
            payload["asr_patch_approval"]["review_rows"][0]["canonical_term"] = "错误术语"
            receipt.write_text(json.dumps(payload), encoding="utf-8")

            check = module.build_check(
                module.load_json(ROOT / "docs/qa-feedback/m18-manual-gate-intake.json", "manual gate intake"),
                receipt,
                module.load_screen_cases(ROOT / "scripts/screen_translation_eval_cases.tsv"),
            )

            self.assertEqual("fail", check.status)
            failing = {item.item_id: item.detail for item in check.items if item.status == "fail"}
            self.assertEqual(
                "review_rows must match the current ASR review packet rows exactly",
                failing["asr-patch-approval"],
            )

    def test_approved_content_rights_without_reviewer_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            receipt = tmp_path / "receipt.json"
            payload = valid_receipt(module, tmp_path / "evidence")
            payload["content_rights_review"]["reviewer"] = ""
            receipt.write_text(json.dumps(payload), encoding="utf-8")

            check = module.build_check(
                module.load_json(ROOT / "docs/qa-feedback/m18-manual-gate-intake.json", "manual gate intake"),
                receipt,
                module.load_screen_cases(ROOT / "scripts/screen_translation_eval_cases.tsv"),
            )

            self.assertEqual("fail", check.status)
            failing = {item.item_id: item.detail for item in check.items if item.status == "fail"}
            self.assertEqual("reviewer is required for approved receipt", failing["content-rights-human-review"])

    def test_approved_content_rights_without_review_scope_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            receipt = tmp_path / "receipt.json"
            payload = valid_receipt(module, tmp_path / "evidence")
            payload["content_rights_review"].pop("review_scope")
            receipt.write_text(json.dumps(payload), encoding="utf-8")

            check = module.build_check(
                module.load_json(ROOT / "docs/qa-feedback/m18-manual-gate-intake.json", "manual gate intake"),
                receipt,
                module.load_screen_cases(ROOT / "scripts/screen_translation_eval_cases.tsv"),
            )

            self.assertEqual("fail", check.status)
            failing = {item.item_id: item.detail for item in check.items if item.status == "fail"}
            self.assertEqual(
                "review_scope must match the current GKP content-rights packet exactly",
                failing["content-rights-human-review"],
            )

    def test_approved_content_rights_with_modified_review_scope_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            receipt = tmp_path / "receipt.json"
            payload = valid_receipt(module, tmp_path / "evidence")
            payload["content_rights_review"]["review_scope"]["bundled_packs"] = 999
            receipt.write_text(json.dumps(payload), encoding="utf-8")

            check = module.build_check(
                module.load_json(ROOT / "docs/qa-feedback/m18-manual-gate-intake.json", "manual gate intake"),
                receipt,
                module.load_screen_cases(ROOT / "scripts/screen_translation_eval_cases.tsv"),
            )

            self.assertEqual("fail", check.status)
            failing = {item.item_id: item.detail for item in check.items if item.status == "fail"}
            self.assertEqual(
                "review_scope must match the current GKP content-rights packet exactly",
                failing["content-rights-human-review"],
            )

    def test_invalid_screen_result_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            receipt = tmp_path / "receipt.json"
            payload = valid_receipt(module, tmp_path / "evidence")
            payload["screen_translation_results"][0]["result"] = "Pass"
            receipt.write_text(json.dumps(payload), encoding="utf-8")

            check = module.build_check(
                module.load_json(ROOT / "docs/qa-feedback/m18-manual-gate-intake.json", "manual gate intake"),
                receipt,
                module.load_screen_cases(ROOT / "scripts/screen_translation_eval_cases.tsv"),
            )

            self.assertEqual("fail", check.status)
            self.assertIn("ff6_dialogue", [item.item_id for item in check.items if item.status == "fail"])

    def test_placeholder_screen_evidence_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            receipt = tmp_path / "receipt.json"
            payload = valid_receipt(module, tmp_path / "evidence")
            payload["screen_translation_results"][0]["result"] = (
                "Pass: evidence build/rc-device-evidence/<timestamp> checklist=layout_ok,language_ok,no_english_source"
            )
            receipt.write_text(json.dumps(payload), encoding="utf-8")

            check = module.build_check(
                module.load_json(ROOT / "docs/qa-feedback/m18-manual-gate-intake.json", "manual gate intake"),
                receipt,
                module.load_screen_cases(ROOT / "scripts/screen_translation_eval_cases.tsv"),
            )

            self.assertEqual("fail", check.status)
            failing = {item.item_id: item.detail for item in check.items if item.status == "fail"}
            self.assertEqual("pass: placeholder_evidence_path", failing["ff6_dialogue"])

    def test_missing_screen_policy_snapshot_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            receipt = tmp_path / "receipt.json"
            payload = valid_receipt(module, tmp_path / "evidence")
            payload["screen_translation_results"][0].pop("expected_layout")
            receipt.write_text(json.dumps(payload), encoding="utf-8")

            check = module.build_check(
                module.load_json(ROOT / "docs/qa-feedback/m18-manual-gate-intake.json", "manual gate intake"),
                receipt,
                module.load_screen_cases(ROOT / "scripts/screen_translation_eval_cases.tsv"),
            )

            self.assertEqual("fail", check.status)
            failing = {item.item_id: item.detail for item in check.items if item.status == "fail"}
            self.assertIn(
                "case policy snapshot must match screen_translation_eval_cases.tsv",
                failing["ff6_dialogue"],
            )

    def test_modified_screen_policy_snapshot_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            receipt = tmp_path / "receipt.json"
            payload = valid_receipt(module, tmp_path / "evidence")
            payload["screen_translation_results"][1]["number_policy"] = "translate_numbers"
            receipt.write_text(json.dumps(payload), encoding="utf-8")

            check = module.build_check(
                module.load_json(ROOT / "docs/qa-feedback/m18-manual-gate-intake.json", "manual gate intake"),
                receipt,
                module.load_screen_cases(ROOT / "scripts/screen_translation_eval_cases.tsv"),
            )

            self.assertEqual("fail", check.status)
            failing = {item.item_id: item.detail for item in check.items if item.status == "fail"}
            self.assertIn(
                "number_policy='translate_numbers'",
                failing["ff6_main_menu"],
            )

    def test_missing_screen_evidence_path_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            receipt = tmp_path / "receipt.json"
            payload = valid_receipt(module, tmp_path / "evidence")
            payload["screen_translation_results"][0]["result"] = (
                f"Pass: evidence {tmp_path / 'missing' / 'ff6_dialogue'} checklist=layout_ok,language_ok,no_english_source"
            )
            receipt.write_text(json.dumps(payload), encoding="utf-8")

            check = module.build_check(
                module.load_json(ROOT / "docs/qa-feedback/m18-manual-gate-intake.json", "manual gate intake"),
                receipt,
                module.load_screen_cases(ROOT / "scripts/screen_translation_eval_cases.tsv"),
            )

            self.assertEqual("fail", check.status)
            failing = {item.item_id: item.detail for item in check.items if item.status == "fail"}
            self.assertEqual("pass: evidence_path_not_found", failing["ff6_dialogue"])

    def test_screen_pass_without_checklist_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            receipt = tmp_path / "receipt.json"
            payload = valid_receipt(module, tmp_path / "evidence")
            evidence = make_evidence_dir(tmp_path / "no-checklist" / "ff6_dialogue")
            payload["screen_translation_results"][0]["result"] = f"Pass: evidence {evidence}"
            receipt.write_text(json.dumps(payload), encoding="utf-8")

            check = module.build_check(
                module.load_json(ROOT / "docs/qa-feedback/m18-manual-gate-intake.json", "manual gate intake"),
                receipt,
                module.load_screen_cases(ROOT / "scripts/screen_translation_eval_cases.tsv"),
            )

            self.assertEqual("fail", check.status)
            failing = {item.item_id: item.detail for item in check.items if item.status == "fail"}
            self.assertEqual("pass: missing_checklist_note", failing["ff6_dialogue"])

    def test_screen_evidence_path_outside_rc_device_root_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            receipt = tmp_path / "receipt.json"
            payload = valid_receipt(module, tmp_path / "evidence")
            outside = make_raw_evidence_dir(tmp_path / "outside-evidence")
            payload["screen_translation_results"][0]["result"] = f"Pass: evidence {outside} checklist=layout_ok,language_ok,no_english_source"
            receipt.write_text(json.dumps(payload), encoding="utf-8")

            check = module.build_check(
                module.load_json(ROOT / "docs/qa-feedback/m18-manual-gate-intake.json", "manual gate intake"),
                receipt,
                module.load_screen_cases(ROOT / "scripts/screen_translation_eval_cases.tsv"),
            )

            self.assertEqual("fail", check.status)
            failing = {item.item_id: item.detail for item in check.items if item.status == "fail"}
            self.assertEqual("pass: evidence_path_outside_rc_device_root", failing["ff6_dialogue"])

    def test_duplicate_screen_evidence_path_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            receipt = tmp_path / "receipt.json"
            payload = valid_receipt(module, tmp_path / "evidence")
            shared = make_evidence_dir(tmp_path / "shared-evidence", case_id="ff6_dialogue")
            payload["screen_translation_results"][0]["result"] = f"Pass: evidence {shared} checklist=layout_ok,language_ok,no_english_source"
            payload["screen_translation_results"][1]["result"] = f"Pass: evidence {shared} checklist=layout_ok,language_ok,grouping_ok,bilingual_ok,numbers_ok"
            receipt.write_text(json.dumps(payload), encoding="utf-8")

            check = module.build_check(
                module.load_json(ROOT / "docs/qa-feedback/m18-manual-gate-intake.json", "manual gate intake"),
                receipt,
                module.load_screen_cases(ROOT / "scripts/screen_translation_eval_cases.tsv"),
            )

            self.assertEqual("fail", check.status)
            failing = [item.detail for item in check.items if item.status == "fail"]
            self.assertTrue(any("evidence_metadata_case_mismatch:ff6_dialogue" in detail for detail in failing), failing)
            passing_case_ids = {item.item_id for item in check.items if item.status == "pass"}
            self.assertIn("ff6_dialogue", passing_case_ids)
            self.assertNotIn("ff6_main_menu", passing_case_ids)

    def test_unknown_screen_case_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            receipt = tmp_path / "receipt.json"
            payload = valid_receipt(module, tmp_path / "evidence")
            payload["screen_translation_results"].append(
                {
                    "case_id": "unknown_case",
                    "result": "Pass: evidence build/rc-device-evidence/20260601/unknown_case checklist=layout_ok,language_ok",
                    "notes": "",
                }
            )
            receipt.write_text(json.dumps(payload), encoding="utf-8")

            check = module.build_check(
                module.load_json(ROOT / "docs/qa-feedback/m18-manual-gate-intake.json", "manual gate intake"),
                receipt,
                module.load_screen_cases(ROOT / "scripts/screen_translation_eval_cases.tsv"),
            )

            self.assertEqual("fail", check.status)
            failing = {item.item_id: item.detail for item in check.items if item.status == "fail"}
            self.assertEqual("unknown screen translation receipt case_id", failing["unknown_case"])

    def test_duplicate_screen_case_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            receipt = tmp_path / "receipt.json"
            payload = valid_receipt(module, tmp_path / "evidence")
            payload["screen_translation_results"].append(dict(payload["screen_translation_results"][0]))
            receipt.write_text(json.dumps(payload), encoding="utf-8")

            check = module.build_check(
                module.load_json(ROOT / "docs/qa-feedback/m18-manual-gate-intake.json", "manual gate intake"),
                receipt,
                module.load_screen_cases(ROOT / "scripts/screen_translation_eval_cases.tsv"),
            )

            self.assertEqual("fail", check.status)
            failing = {item.item_id: item.detail for item in check.items if item.status == "fail"}
            self.assertEqual("duplicate screen translation receipt case_id", failing["ff6_dialogue"])

    def test_non_object_screen_entry_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            receipt = tmp_path / "receipt.json"
            payload = valid_receipt(module, tmp_path / "evidence")
            payload["screen_translation_results"].append("not a receipt row")
            receipt.write_text(json.dumps(payload), encoding="utf-8")

            check = module.build_check(
                module.load_json(ROOT / "docs/qa-feedback/m18-manual-gate-intake.json", "manual gate intake"),
                receipt,
                module.load_screen_cases(ROOT / "scripts/screen_translation_eval_cases.tsv"),
            )

            self.assertEqual("fail", check.status)
            failing = {item.item_id: item.detail for item in check.items if item.status == "fail"}
            self.assertEqual("screen translation receipt entry must be an object", failing["screen-translation-entry-6"])

    def test_custom_screen_cases_validate_against_passed_path(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            cases = tmp_path / "screen-cases.tsv"
            receipt = tmp_path / "receipt.json"
            evidence = make_evidence_dir(tmp_path / "evidence" / "custom_menu")
            cases.write_text(custom_screen_cases_tsv(), encoding="utf-8")
            receipt.write_text(
                json.dumps(
                    {
                        "schema_version": 1,
                        "objective": module.RECEIPT_OBJECTIVE,
                        "screen_translation_results": [
                            {
                                "case_id": "custom_menu",
                                "game_label": "custom__Game",
                                "screen_type": "menu",
                                "trigger_phrase": "翻译",
                                "expected_layout": "bilingual_rows",
                                "expected_language": "en_zh",
                                "number_policy": "preserve_numbers",
                                "evidence_required": "manual_screenshot",
                                "result": f"Pass: evidence {evidence} checklist=layout_ok,language_ok,grouping_ok,bilingual_ok,numbers_ok",
                                "notes": "",
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )
            intake = {
                "sections": [
                    {
                        "id": "screen-translation-manual-results",
                        "status": "ready",
                    }
                ]
            }

            check = module.build_check(
                intake,
                receipt,
                module.load_screen_cases(cases),
                cases,
            )

            self.assertEqual("pass", check.status)
            self.assertTrue(all(item.status == "pass" for item in check.items))


def valid_receipt(module, evidence_root: Path):
    evidence_root.mkdir(parents=True, exist_ok=True)
    evidence_by_case = {}
    for case in module.load_screen_cases(ROOT / "scripts/screen_translation_eval_cases.tsv"):
        path = make_evidence_dir(evidence_root / case.case_id)
        evidence_by_case[case.case_id] = path
    intake = module.load_json(ROOT / "docs/qa-feedback/m18-manual-gate-intake.json", "manual gate intake")
    asr_review_rows = [
        module.asr_review_row_to_json(row)
        for row in module.asr_review_rows_from_intake(intake)
    ]
    return {
        "schema_version": 1,
        "objective": module.RECEIPT_OBJECTIVE,
        "asr_patch_approval": {
            "decision": "approved",
            "approval_phrase": module.ASR_APPROVAL_PHRASE,
            "reviewer": "tester",
            "review_rows": asr_review_rows,
            "notes": "",
        },
        "screen_translation_results": [
            {
                "case_id": case.case_id,
                "game_label": case.game_label,
                "screen_type": case.screen_type,
                "trigger_phrase": case.trigger_phrase,
                "expected_layout": case.expected_layout,
                "expected_language": case.expected_language,
                "number_policy": case.number_policy,
                "evidence_required": case.evidence_required,
                "result": pass_result(case, evidence_by_case[case.case_id]),
                "notes": "",
            }
            for case in module.load_screen_cases(ROOT / "scripts/screen_translation_eval_cases.tsv")
        ],
        "content_rights_review": {
            "decision": "approved",
            "approval_phrase": module.CONTENT_RIGHTS_APPROVAL_PHRASE,
            "reviewer": "tester",
            "review_scope": module.build_content_rights_review_scope(),
            "notes": "",
        },
    }


def custom_screen_cases_tsv():
    return "\n".join(
        [
            "id\tgame_label\tscreen_type\ttrigger_phrase\texpected_layout\texpected_language\tnumber_policy\tevidence_required",
            "custom_menu\tcustom__Game\tmenu\t翻译\tbilingual_rows\ten_zh\tpreserve_numbers\tmanual_screenshot",
            "",
        ]
    )


def pass_result(case, evidence: Path) -> str:
    tokens = ["layout_ok", "language_ok"]
    if case.expected_layout == "chinese_only":
        tokens.append("no_english_source")
    if case.expected_layout in {"bilingual_rows", "grouped_labels"}:
        tokens.append("grouping_ok")
    if case.expected_language == "en_zh":
        tokens.append("bilingual_ok")
    if "preserve" in case.number_policy:
        tokens.append("numbers_ok")
    if case.expected_layout == "paged_overlay" or case.number_policy == "ten_seconds_per_page":
        tokens.append("paging_10s")
    return f"Pass: evidence {evidence} checklist={','.join(tokens)}"


def make_evidence_dir(path: Path, case_id=None) -> Path:
    metadata_case_id = case_id or path.name
    EVIDENCE_ROOT.mkdir(parents=True, exist_ok=True)
    try:
        path.resolve().relative_to(EVIDENCE_ROOT.resolve())
    except ValueError:
        path = Path(tempfile.mkdtemp(prefix=f"test-{path.name}-", dir=EVIDENCE_ROOT))
    return make_raw_evidence_dir(path, case_id=metadata_case_id)


def make_raw_evidence_dir(path: Path, case_id=None) -> Path:
    path.mkdir(parents=True, exist_ok=True)
    for name in ["README.md", "health.json", "latest-request.json", "hotkey-voice-overlay.json"]:
        (path / name).write_text("{}\n", encoding="utf-8")
    (path / "screenshot.png").write_bytes(b"\x89PNG\r\n\x1a\n")
    if case_id is not None:
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
