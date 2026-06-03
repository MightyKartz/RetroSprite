import csv
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CASES = ROOT / "scripts/screen_translation_eval_cases.tsv"
REPORT_SCRIPT = ROOT / "scripts/screen_translation_eval_report.py"
EVIDENCE_ROOT = ROOT / "build/rc-device-evidence"


def load_report_module():
    spec = importlib.util.spec_from_file_location("screen_translation_eval_report", REPORT_SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class ScreenTranslationEvalCasesTest(unittest.TestCase):

    def test_screen_translation_cases_have_expected_columns_and_rows(self):
        rows = read_rows()

        self.assertEqual(
            [
                "id",
                "game_label",
                "screen_type",
                "trigger_phrase",
                "expected_layout",
                "expected_language",
                "number_policy",
                "evidence_required",
            ],
            list(rows[0].keys()),
        )
        self.assertEqual(
            {
                "ff6_dialogue",
                "ff6_main_menu",
                "ff6_status",
                "chrono_equipment",
                "multi_page_any",
            },
            {row["id"] for row in rows},
        )

    def test_dialogue_menu_numbers_and_paging_rules_are_separate(self):
        rows = {row["id"]: row for row in read_rows()}

        self.assertEqual("chinese_only", rows["ff6_dialogue"]["expected_layout"])
        self.assertEqual("bilingual_rows", rows["ff6_main_menu"]["expected_layout"])
        self.assertEqual("preserve_hp_mp_level_exp", rows["ff6_status"]["number_policy"])
        self.assertEqual("ten_seconds_per_page", rows["multi_page_any"]["number_policy"])

    def test_report_marks_current_manual_matrix_as_not_run(self):
        module = load_report_module()

        cases = module.load_cases(CASES)
        matrix = module.load_matrix_results(ROOT / "docs/qa-feedback/rc-device-matrix.md")
        statuses = module.build_statuses(cases, matrix)
        markdown = module.render_markdown(
            statuses,
            Path("scripts/screen_translation_eval_cases.tsv"),
            Path("docs/qa-feedback/rc-device-matrix.md"),
        )

        self.assertEqual({"not_run"}, {status.status for status in statuses})
        self.assertIn("not_run=5", markdown)
        self.assertIn("- Note issues: 0", markdown)
        self.assertIn("`ff6_main_menu`", markdown)
        self.assertIn("`bilingual_rows`", markdown)

    def test_report_parses_pass_fail_blocked_and_missing_rows(self):
        module = load_report_module()
        with tempfile.TemporaryDirectory() as tmp:
            evidence = make_evidence_dir(Path(tmp) / "evidence" / "ff6_dialogue")
            matrix = Path(tmp) / "matrix.md"
            matrix.write_text(
                "\n".join(
                    [
                        "## Screen Translation Matrix",
                        "",
                        "| Game/screen | Trigger phrase | Expected display | Result |",
                        "|---|---|---|---|",
                        f"| FF6 dialogue | 翻译 | Chinese-only dialogue, no English source | {pass_result(evidence, 'ff6_dialogue')} |",
                        "| FF6 main menu | 翻译 | Bilingual lookup rows | Fail: numeric_corruption |",
                        "| FF6 status page | 翻译 | Numbers preserved | Blocked: missing BYOK key |",
                        "| Chrono Trigger equipment | 翻译 | Equipment rows | Not run |",
                    ]
                ),
                encoding="utf-8",
            )

            cases = module.load_cases(CASES)
            statuses = module.build_statuses(cases, module.load_matrix_results(matrix))

            by_id = {status.case.case_id: status.status for status in statuses}
            self.assertEqual("pass", by_id["ff6_dialogue"])
            self.assertEqual("fail", by_id["ff6_main_menu"])
            self.assertEqual("blocked", by_id["ff6_status"])
            self.assertEqual("not_run", by_id["chrono_equipment"])
            self.assertEqual("missing", by_id["multi_page_any"])

            by_note = {status.case.case_id: status.result_note for status in statuses}
            self.assertIn("evidence", by_note["ff6_dialogue"])
            self.assertEqual("numeric_corruption", by_note["ff6_main_menu"])
            self.assertEqual("missing BYOK key", by_note["ff6_status"])

            by_issue = {status.case.case_id: status.note_issue for status in statuses}
            self.assertEqual("-", by_issue["ff6_dialogue"])
            self.assertEqual("-", by_issue["ff6_main_menu"])
            self.assertEqual("-", by_issue["ff6_status"])

    def test_report_flags_pass_without_evidence_and_fail_without_category(self):
        module = load_report_module()
        with tempfile.TemporaryDirectory() as tmp:
            matrix = Path(tmp) / "matrix.md"
            matrix.write_text(
                "\n".join(
                    [
                        "## Screen Translation Matrix",
                        "",
                        "| Game/screen | Trigger phrase | Expected display | Result |",
                        "|---|---|---|---|",
                        "| FF6 dialogue | 翻译 | Chinese-only dialogue, no English source | Pass |",
                        "| FF6 main menu | 翻译 | Bilingual lookup rows | Fail |",
                    ]
                ),
                encoding="utf-8",
            )

            cases = module.load_cases(CASES)
            statuses = module.build_statuses(cases, module.load_matrix_results(matrix))

            by_issue = {status.case.case_id: status.note_issue for status in statuses}
            self.assertEqual("missing_evidence_note", by_issue["ff6_dialogue"])
            self.assertEqual("missing_failure_category", by_issue["ff6_main_menu"])
            self.assertEqual(2, module.count_note_issues(statuses))

    def test_report_rejects_placeholder_evidence_for_pass(self):
        module = load_report_module()
        with tempfile.TemporaryDirectory() as tmp:
            matrix = Path(tmp) / "matrix.md"
            matrix.write_text(
                "\n".join(
                    [
                        "## Screen Translation Matrix",
                        "",
                        "| Game/screen | Trigger phrase | Expected display | Result |",
                        "|---|---|---|---|",
                        "| FF6 dialogue | 翻译 | Chinese-only dialogue, no English source | Pass: evidence build/rc-device-evidence/<timestamp> checklist=layout_ok,language_ok,no_english_source |",
                    ]
                ),
                encoding="utf-8",
            )

            cases = module.load_cases(CASES)
            statuses = module.build_statuses(cases, module.load_matrix_results(matrix))

            by_issue = {status.case.case_id: status.note_issue for status in statuses}
            self.assertEqual("placeholder_evidence_path", by_issue["ff6_dialogue"])

    def test_report_rejects_missing_evidence_path_for_pass(self):
        module = load_report_module()
        with tempfile.TemporaryDirectory() as tmp:
            matrix = Path(tmp) / "matrix.md"
            missing = Path(tmp) / "missing-evidence" / "ff6_dialogue"
            matrix.write_text(
                "\n".join(
                    [
                        "## Screen Translation Matrix",
                        "",
                        "| Game/screen | Trigger phrase | Expected display | Result |",
                        "|---|---|---|---|",
                        f"| FF6 dialogue | 翻译 | Chinese-only dialogue, no English source | {pass_result(missing, 'ff6_dialogue')} |",
                    ]
                ),
                encoding="utf-8",
            )

            cases = module.load_cases(CASES)
            statuses = module.build_statuses(cases, module.load_matrix_results(matrix))

            by_issue = {status.case.case_id: status.note_issue for status in statuses}
            self.assertEqual("evidence_path_not_found", by_issue["ff6_dialogue"])

    def test_report_rejects_pass_evidence_without_checklist(self):
        module = load_report_module()
        with tempfile.TemporaryDirectory() as tmp:
            matrix = Path(tmp) / "matrix.md"
            evidence = make_evidence_dir(Path(tmp) / "evidence" / "ff6_dialogue")
            matrix.write_text(
                "\n".join(
                    [
                        "## Screen Translation Matrix",
                        "",
                        "| Game/screen | Trigger phrase | Expected display | Result |",
                        "|---|---|---|---|",
                        f"| FF6 dialogue | 翻译 | Chinese-only dialogue, no English source | Pass: evidence {evidence} |",
                    ]
                ),
                encoding="utf-8",
            )

            cases = module.load_cases(CASES)
            statuses = module.build_statuses(cases, module.load_matrix_results(matrix))

            by_issue = {status.case.case_id: status.note_issue for status in statuses}
            self.assertEqual("missing_checklist_note", by_issue["ff6_dialogue"])

    def test_report_rejects_incomplete_evidence_directory_for_pass(self):
        module = load_report_module()
        with tempfile.TemporaryDirectory() as tmp:
            matrix = Path(tmp) / "matrix.md"
            EVIDENCE_ROOT.mkdir(parents=True, exist_ok=True)
            incomplete = Path(tempfile.mkdtemp(prefix="test-incomplete-", dir=EVIDENCE_ROOT))
            (incomplete / "README.md").write_text("# Evidence\n", encoding="utf-8")
            matrix.write_text(
                "\n".join(
                    [
                        "## Screen Translation Matrix",
                        "",
                        "| Game/screen | Trigger phrase | Expected display | Result |",
                        "|---|---|---|---|",
                        f"| FF6 dialogue | 翻译 | Chinese-only dialogue, no English source | {pass_result(incomplete, 'ff6_dialogue')} |",
                    ]
                ),
                encoding="utf-8",
            )

            cases = module.load_cases(CASES)
            statuses = module.build_statuses(cases, module.load_matrix_results(matrix))

            by_issue = {status.case.case_id: status.note_issue for status in statuses}
            self.assertIn("evidence_files_missing:", by_issue["ff6_dialogue"])

    def test_report_rejects_manual_screenshot_evidence_without_screenshot_file(self):
        module = load_report_module()
        with tempfile.TemporaryDirectory() as tmp:
            matrix = Path(tmp) / "matrix.md"
            evidence = make_evidence_dir(Path(tmp) / "evidence" / "ff6_dialogue")
            (evidence / "screenshot.png").unlink()
            matrix.write_text(
                "\n".join(
                    [
                        "## Screen Translation Matrix",
                        "",
                        "| Game/screen | Trigger phrase | Expected display | Result |",
                        "|---|---|---|---|",
                        f"| FF6 dialogue | 翻译 | Chinese-only dialogue, no English source | {pass_result(evidence, 'ff6_dialogue')} |",
                    ]
                ),
                encoding="utf-8",
            )

            cases = module.load_cases(CASES)
            statuses = module.build_statuses(cases, module.load_matrix_results(matrix))

            by_issue = {status.case.case_id: status.note_issue for status in statuses}
            self.assertEqual("evidence_screenshot_missing:screenshot.png", by_issue["ff6_dialogue"])

    def test_report_rejects_non_screen_translation_metadata_for_pass(self):
        module = load_report_module()
        with tempfile.TemporaryDirectory() as tmp:
            matrix = Path(tmp) / "matrix.md"
            evidence = make_evidence_dir(Path(tmp) / "evidence" / "ff6_dialogue", gate="endpoint_smoke")
            matrix.write_text(
                "\n".join(
                    [
                        "## Screen Translation Matrix",
                        "",
                        "| Game/screen | Trigger phrase | Expected display | Result |",
                        "|---|---|---|---|",
                        f"| FF6 dialogue | 翻译 | Chinese-only dialogue, no English source | {pass_result(evidence, 'ff6_dialogue')} |",
                    ]
                ),
                encoding="utf-8",
            )

            cases = module.load_cases(CASES)
            statuses = module.build_statuses(cases, module.load_matrix_results(matrix))

            by_issue = {status.case.case_id: status.note_issue for status in statuses}
            self.assertEqual("evidence_metadata_gate_mismatch:endpoint_smoke", by_issue["ff6_dialogue"])

    def test_report_rejects_wrong_case_metadata_for_pass(self):
        module = load_report_module()
        with tempfile.TemporaryDirectory() as tmp:
            matrix = Path(tmp) / "matrix.md"
            evidence = make_evidence_dir(Path(tmp) / "evidence" / "ff6_dialogue", case_id="ff6_main_menu")
            matrix.write_text(
                "\n".join(
                    [
                        "## Screen Translation Matrix",
                        "",
                        "| Game/screen | Trigger phrase | Expected display | Result |",
                        "|---|---|---|---|",
                        f"| FF6 dialogue | 翻译 | Chinese-only dialogue, no English source | {pass_result(evidence, 'ff6_dialogue')} |",
                    ]
                ),
                encoding="utf-8",
            )

            cases = module.load_cases(CASES)
            statuses = module.build_statuses(cases, module.load_matrix_results(matrix))

            by_issue = {status.case.case_id: status.note_issue for status in statuses}
            self.assertEqual("evidence_metadata_case_mismatch:ff6_main_menu", by_issue["ff6_dialogue"])

    def test_report_rejects_evidence_path_outside_rc_device_root(self):
        module = load_report_module()
        with tempfile.TemporaryDirectory() as tmp:
            matrix = Path(tmp) / "matrix.md"
            evidence = make_raw_evidence_dir(Path(tmp) / "outside-evidence")
            matrix.write_text(
                "\n".join(
                    [
                        "## Screen Translation Matrix",
                        "",
                        "| Game/screen | Trigger phrase | Expected display | Result |",
                        "|---|---|---|---|",
                        f"| FF6 dialogue | 翻译 | Chinese-only dialogue, no English source | {pass_result(evidence, 'ff6_dialogue')} |",
                    ]
                ),
                encoding="utf-8",
            )

            cases = module.load_cases(CASES)
            statuses = module.build_statuses(cases, module.load_matrix_results(matrix))

            by_issue = {status.case.case_id: status.note_issue for status in statuses}
            self.assertEqual("evidence_path_outside_rc_device_root", by_issue["ff6_dialogue"])

    def test_report_rejects_duplicate_pass_evidence_paths(self):
        module = load_report_module()
        with tempfile.TemporaryDirectory() as tmp:
            evidence = make_evidence_dir(Path(tmp) / "evidence" / "shared", case_id="ff6_dialogue")
            matrix = Path(tmp) / "matrix.md"
            matrix.write_text(
                "\n".join(
                    [
                        "## Screen Translation Matrix",
                        "",
                        "| Game/screen | Trigger phrase | Expected display | Result |",
                        "|---|---|---|---|",
                        f"| FF6 dialogue | 翻译 | Chinese-only dialogue, no English source | {pass_result(evidence, 'ff6_dialogue')} |",
                        f"| FF6 main menu | 翻译 | Bilingual lookup rows | {pass_result(evidence, 'ff6_main_menu')} |",
                    ]
                ),
                encoding="utf-8",
            )

            cases = module.load_cases(CASES)
            statuses = module.build_statuses(cases, module.load_matrix_results(matrix))

            by_issue = {status.case.case_id: status.note_issue for status in statuses}
            self.assertEqual("-", by_issue["ff6_dialogue"])
            self.assertEqual("evidence_metadata_case_mismatch:ff6_dialogue", by_issue["ff6_main_menu"])


def read_rows():
    with CASES.open(encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle, delimiter="\t"))


def pass_result(evidence: Path, case_id: str) -> str:
    checklist_by_case = {
        "ff6_dialogue": "layout_ok,language_ok,no_english_source",
        "ff6_main_menu": "layout_ok,language_ok,grouping_ok,bilingual_ok,numbers_ok",
        "ff6_status": "layout_ok,language_ok,grouping_ok,numbers_ok",
        "chrono_equipment": "layout_ok,language_ok,grouping_ok,bilingual_ok,numbers_ok",
        "multi_page_any": "layout_ok,language_ok,paging_10s",
    }
    return f"Pass: evidence {evidence} checklist={checklist_by_case[case_id]}"


def make_evidence_dir(path: Path, gate: str = "screen_translation", case_id=None) -> Path:
    metadata_case_id = case_id or path.name
    EVIDENCE_ROOT.mkdir(parents=True, exist_ok=True)
    try:
        path.resolve().relative_to(EVIDENCE_ROOT.resolve())
    except ValueError:
        path = Path(tempfile.mkdtemp(prefix=f"test-{path.name}-", dir=EVIDENCE_ROOT))
    return make_raw_evidence_dir(path, gate=gate, case_id=metadata_case_id)


def make_raw_evidence_dir(path: Path, gate=None, case_id=None) -> Path:
    path.mkdir(parents=True, exist_ok=True)
    for name in ["README.md", "health.json", "latest-request.json", "hotkey-voice-overlay.json"]:
        (path / name).write_text("{}\n", encoding="utf-8")
    (path / "screenshot.png").write_bytes(b"\x89PNG\r\n\x1a\n")
    if gate is not None:
        (path / "metadata.json").write_text(
            json.dumps(
                {
                    "schema_version": 1,
                    "gate": gate,
                    "case_id": case_id or path.name,
                },
                ensure_ascii=False,
            )
            + "\n",
            encoding="utf-8",
        )
    return path


if __name__ == "__main__":
    unittest.main()
