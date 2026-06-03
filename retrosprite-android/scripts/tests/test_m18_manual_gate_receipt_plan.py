import importlib.util
import io
import json
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/m18_manual_gate_receipt_plan.py"
CHECK_SCRIPT = ROOT / "scripts/m18_manual_gate_receipt_check.py"
COMMAND_AUDIT_SCRIPT = ROOT / "scripts/m18_command_contract_audit.py"
EVIDENCE_ROOT = ROOT / "build/rc-device-evidence"


def load_module():
    spec = importlib.util.spec_from_file_location("m18_manual_gate_receipt_plan", SCRIPT)
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


def load_command_audit_module():
    spec = importlib.util.spec_from_file_location("m18_command_contract_audit", COMMAND_AUDIT_SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


@unittest.skip("Legacy manual receipt flow is outside current M18 scope.")
class M18ManualGateReceiptPlanTest(unittest.TestCase):

    def test_missing_receipt_outputs_open_plan_without_commands(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            output = tmp_path / "plan.md"
            json_output = tmp_path / "plan.json"

            old_argv = sys.argv
            try:
                sys.argv = [
                    "m18_manual_gate_receipt_plan.py",
                    "--receipt",
                    str(tmp_path / "missing.json"),
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
            markdown = output.read_text(encoding="utf-8")
            plan_json = json.loads(json_output.read_text(encoding="utf-8"))
            self.assertIn("Plan status: `open`", markdown)
            self.assertIn("Commands executed by this planner: no", markdown)
            self.assertNotIn("## Ready Commands", markdown)
            self.assertEqual("open", plan_json["status"])
            self.assertEqual("open", plan_json["receipt_check_status"])
            self.assertFalse(plan_json["receipt_present"])
            self.assertEqual({"ready": 0, "open": 3, "blocked": 0}, plan_json["counts"])
            self.assertFalse(plan_json["commands_executed_by_planner"])
            self.assertFalse(plan_json["assets_edited_by_planner"])

    def test_valid_receipt_generates_ready_guarded_commands(self):
        module = load_module()
        check = load_check_module()
        with tempfile.TemporaryDirectory() as tmp:
            receipt = Path(tmp) / "receipt.json"
            receipt.write_text(json.dumps(valid_receipt(check, Path(tmp) / "evidence")), encoding="utf-8")

            plan = module.build_plan(
                ROOT / "docs/qa-feedback/m18-manual-gate-intake.json",
                receipt,
                ROOT / "scripts/screen_translation_eval_cases.tsv",
            )

            self.assertEqual("pass", plan.status)
            self.assertEqual(7, len(plan.actions))
            self.assertTrue(all(action.status == "ready" for action in plan.actions))
            commands = "\n".join(action.command for action in plan.actions)
            self.assertIn("gkp_patch_apply_review_packet.py", commands)
            self.assertIn("screen_translation_matrix_update.py", commands)
            self.assertIn("--cases scripts/screen_translation_eval_cases.tsv", commands)
            self.assertIn("--output build/m18-screen-matrix-previews/ff6_dialogue.md", commands)
            self.assertIn("m18_release_checklist_guard.py", commands)
            plan_json = module.render_json(
                plan,
                ROOT / "docs/qa-feedback/m18-manual-gate-intake.json",
                receipt,
            )
            self.assertEqual("pass", plan_json["status"])
            self.assertEqual({"ready": 7, "open": 0, "blocked": 0}, plan_json["counts"])
            self.assertTrue(plan_json["receipt_present"])
            self.assertFalse(plan_json["commands_executed_by_planner"])
            self.assertFalse(plan_json["assets_edited_by_planner"])
            self.assertEqual(
                "apply-approved-asr-patch",
                plan_json["actions"][0]["id"],
            )

    def test_screen_failure_receipt_adds_backlog_preview_action(self):
        module = load_module()
        check = load_check_module()
        with tempfile.TemporaryDirectory() as tmp:
            receipt = Path(tmp) / "receipt.json"
            payload = valid_receipt(check, Path(tmp) / "evidence")
            payload["screen_translation_results"][1]["result"] = "Fail: numeric_corruption"
            payload["screen_translation_results"][1]["notes"] = "Menu numbers were translated as prose."
            receipt.write_text(json.dumps(payload), encoding="utf-8")

            plan = module.build_plan(
                ROOT / "docs/qa-feedback/m18-manual-gate-intake.json",
                receipt,
                ROOT / "scripts/screen_translation_eval_cases.tsv",
            )

            self.assertEqual("pass", plan.status)
            self.assertEqual(8, len(plan.actions))
            backlog_actions = [
                action
                for action in plan.actions
                if action.action_id == "preview-screen-failure-backlog"
            ]
            self.assertEqual(1, len(backlog_actions))
            self.assertIn("gkp_gap_backlog.py", backlog_actions[0].command)
            self.assertIn("--input docs/qa-feedback/m18-manual-gate-receipt.json", backlog_actions[0].command)
            self.assertIn("--output build/m18-receipt-backlog-preview.md", backlog_actions[0].command)
            self.assertIn("without overwriting docs/qa-feedback/gkp-quality-backlog.md", backlog_actions[0].detail)
            self.assertIn("ff6_main_menu", backlog_actions[0].detail)

    def test_custom_screen_cases_propagate_to_receipt_plan(self):
        module = load_module()
        check = load_check_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            intake = tmp_path / "intake.json"
            receipt = tmp_path / "receipt.json"
            cases = tmp_path / "screen-cases.tsv"
            evidence = make_evidence_dir(tmp_path / "evidence" / "custom_menu")
            intake.write_text(
                json.dumps(
                    {
                        "sections": [
                            {
                                "id": "screen-translation-manual-results",
                                "status": "ready",
                            }
                        ]
                    }
                ),
                encoding="utf-8",
            )
            cases.write_text(custom_screen_cases_tsv(), encoding="utf-8")
            receipt.write_text(
                json.dumps(
                    {
                        "schema_version": 1,
                        "objective": check.RECEIPT_OBJECTIVE,
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

            plan = module.build_plan(intake, receipt, cases)

            self.assertEqual("pass", plan.status)
            self.assertEqual(1, len(plan.actions))
            self.assertEqual("update-screen-matrix-custom_menu", plan.actions[0].action_id)
            self.assertIn(f"--cases {cases}", plan.actions[0].command)
            self.assertIn("--case-id custom_menu", plan.actions[0].command)
            self.assertIn("--output build/m18-screen-matrix-previews/custom_menu.md", plan.actions[0].command)

    def test_valid_receipt_plan_renders_auditable_ready_commands(self):
        module = load_module()
        check = load_check_module()
        audit = load_command_audit_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            receipt = tmp_path / "receipt.json"
            output = tmp_path / "m18-manual-gate-receipt-plan.md"
            receipt.write_text(json.dumps(valid_receipt(check, tmp_path / "evidence")), encoding="utf-8")

            plan = module.build_plan(
                ROOT / "docs/qa-feedback/m18-manual-gate-intake.json",
                receipt,
                ROOT / "scripts/screen_translation_eval_cases.tsv",
            )
            output.write_text(
                module.render_markdown(
                    plan,
                    ROOT / "docs/qa-feedback/m18-manual-gate-intake.json",
                    receipt,
                ),
                encoding="utf-8",
            )

            markdown = output.read_text(encoding="utf-8")
            findings = audit.audit_paths((output,))

            self.assertIn("python3 scripts/m18_command_contract_audit.py --strict", markdown)
            self.assertTrue(all(finding.status == "pass" for finding in findings), findings)
            self.assertTrue(
                any(finding.rule == "gkp_patch_apply_approval_flag" for finding in findings),
                findings,
            )
            self.assertTrue(
                any(finding.rule == "placeholder_screen_translation_apply" for finding in findings),
                findings,
            )
            self.assertTrue(
                any(finding.rule == "receipt_plan_screen_preview_before_apply" for finding in findings),
                findings,
            )

    def test_placeholder_evidence_blocks_plan_commands(self):
        module = load_module()
        check = load_check_module()
        with tempfile.TemporaryDirectory() as tmp:
            receipt = Path(tmp) / "receipt.json"
            payload = valid_receipt(check, Path(tmp) / "evidence")
            payload["screen_translation_results"][0]["result"] = (
                "Pass: evidence build/rc-device-evidence/<timestamp> checklist=layout_ok,language_ok,no_english_source"
            )
            receipt.write_text(json.dumps(payload), encoding="utf-8")

            plan = module.build_plan(
                ROOT / "docs/qa-feedback/m18-manual-gate-intake.json",
                receipt,
                ROOT / "scripts/screen_translation_eval_cases.tsv",
            )

            self.assertEqual("fail", plan.status)
            self.assertIn("blocked", {action.status for action in plan.actions})
            self.assertFalse(any(action.command for action in plan.actions))

    def test_wrong_approval_phrase_blocks_plan_commands(self):
        module = load_module()
        check = load_check_module()
        with tempfile.TemporaryDirectory() as tmp:
            receipt = Path(tmp) / "receipt.json"
            payload = valid_receipt(check, Path(tmp) / "evidence")
            payload["asr_patch_approval"]["approval_phrase"] = "wrong"
            receipt.write_text(json.dumps(payload), encoding="utf-8")

            plan = module.build_plan(
                ROOT / "docs/qa-feedback/m18-manual-gate-intake.json",
                receipt,
                ROOT / "scripts/screen_translation_eval_cases.tsv",
            )

            self.assertEqual("fail", plan.status)
            self.assertFalse(any(action.command for action in plan.actions))


def valid_receipt(check, evidence_root: Path):
    evidence_root.mkdir(parents=True, exist_ok=True)
    evidence_by_case = {}
    for case in check.load_screen_cases(ROOT / "scripts/screen_translation_eval_cases.tsv"):
        path = make_evidence_dir(evidence_root / case.case_id)
        evidence_by_case[case.case_id] = path
    intake = check.load_json(ROOT / "docs/qa-feedback/m18-manual-gate-intake.json", "manual gate intake")
    asr_review_rows = [
        check.asr_review_row_to_json(row)
        for row in check.asr_review_rows_from_intake(intake)
    ]
    return {
        "schema_version": 1,
        "objective": check.RECEIPT_OBJECTIVE,
        "asr_patch_approval": {
            "decision": "approved",
            "approval_phrase": check.ASR_APPROVAL_PHRASE,
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
            for case in check.load_screen_cases(ROOT / "scripts/screen_translation_eval_cases.tsv")
        ],
        "content_rights_review": {
            "decision": "approved",
            "approval_phrase": check.CONTENT_RIGHTS_APPROVAL_PHRASE,
            "reviewer": "tester",
            "review_scope": check.build_content_rights_review_scope(),
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


def make_evidence_dir(path: Path) -> Path:
    case_id = path.name
    EVIDENCE_ROOT.mkdir(parents=True, exist_ok=True)
    try:
        path.resolve().relative_to(EVIDENCE_ROOT.resolve())
    except ValueError:
        path = Path(tempfile.mkdtemp(prefix=f"test-{path.name}-", dir=EVIDENCE_ROOT))
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
