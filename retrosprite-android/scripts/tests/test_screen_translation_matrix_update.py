import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/screen_translation_matrix_update.py"
CASES = ROOT / "scripts/screen_translation_eval_cases.tsv"
EVIDENCE_ROOT = ROOT / "build/rc-device-evidence"


def load_module():
    spec = importlib.util.spec_from_file_location("screen_translation_matrix_update", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def matrix_text():
    return "\n".join(
        [
            "# Matrix",
            "",
            "## Screen Translation Matrix",
            "",
            "| Game/screen | Trigger phrase | Expected display | Result |",
            "|---|---|---|---|",
            "| FF6 dialogue | 翻译 | Chinese-only dialogue, no English source | Not run |",
            "| FF6 main menu | 翻译 | Bilingual lookup rows, English source + Chinese translation | Not run |",
            "| FF6 status page | 翻译 | Labels translated, HP/MP/Level/Exp numbers preserved | Not run |",
            "| Chrono Trigger equipment | 翻译 | Equipment slots and item names grouped, numbers preserved | Not run |",
            "| Any multi-page result | 翻译 | Every page stays visible for 10 seconds | Not run |",
            "",
            "## Evidence To Capture",
        ]
    )


class ScreenTranslationMatrixUpdateTest(unittest.TestCase):

    def test_updates_one_case_with_valid_pass_evidence(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            evidence = make_evidence_dir(Path(tmp) / "evidence" / "ff6_dialogue")
            matrix = Path(tmp) / "matrix.md"
            matrix.write_text(matrix_text(), encoding="utf-8")

            updated, result = module.update_matrix_text(
                CASES,
                matrix,
                "ff6_dialogue",
                pass_result(evidence, "ff6_dialogue"),
            )

            self.assertEqual("ff6_dialogue", result.case_id)
            self.assertEqual("pass", result.status)
            self.assertTrue(result.changed)
            self.assertIn(
                f"| FF6 dialogue | 翻译 | Chinese-only dialogue, no English source | {pass_result(evidence, 'ff6_dialogue')} |",
                updated,
            )
            self.assertIn("| FF6 main menu | 翻译 | Bilingual lookup rows, English source + Chinese translation | Not run |", updated)

    def test_rejects_pass_without_evidence(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            matrix = Path(tmp) / "matrix.md"
            matrix.write_text(matrix_text(), encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "missing_evidence_note"):
                module.update_matrix_text(CASES, matrix, "ff6_dialogue", "Pass")

    def test_rejects_fail_without_category(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            matrix = Path(tmp) / "matrix.md"
            matrix.write_text(matrix_text(), encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "missing_failure_category"):
                module.update_matrix_text(CASES, matrix, "ff6_main_menu", "Fail")

    def test_accepts_blocked_with_reason(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            matrix = Path(tmp) / "matrix.md"
            matrix.write_text(matrix_text(), encoding="utf-8")

            updated, result = module.update_matrix_text(
                CASES,
                matrix,
                "ff6_status",
                "Blocked: missing BYOK key",
            )

            self.assertEqual("blocked", result.status)
            self.assertIn("Blocked: missing BYOK key", updated)

    def test_rejects_unknown_case_id(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            matrix = Path(tmp) / "matrix.md"
            matrix.write_text(matrix_text(), encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "unknown screen translation case id"):
                module.update_matrix_text(CASES, matrix, "unknown_case", "Blocked: cannot reproduce screen")

    def test_rejects_reusing_pass_evidence_from_another_case(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            evidence = make_evidence_dir(Path(tmp) / "evidence" / "shared", case_id="ff6_main_menu")
            matrix = Path(tmp) / "matrix.md"
            matrix.write_text(
                matrix_text().replace(
                    "| FF6 main menu | 翻译 | Bilingual lookup rows, English source + Chinese translation | Not run |",
                    f"| FF6 main menu | 翻译 | Bilingual lookup rows, English source + Chinese translation | {pass_result(evidence, 'ff6_main_menu')} |",
                ),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(ValueError, "evidence_metadata_case_mismatch:ff6_main_menu"):
                module.update_matrix_text(
                    CASES,
                    matrix,
                    "ff6_dialogue",
                    pass_result(evidence, "ff6_dialogue"),
                )

    def test_rejects_endpoint_smoke_evidence_for_screen_translation_pass(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            evidence = make_evidence_dir(Path(tmp) / "evidence" / "ff6_dialogue", gate="endpoint_smoke")
            matrix = Path(tmp) / "matrix.md"
            matrix.write_text(matrix_text(), encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "evidence_metadata_gate_mismatch:endpoint_smoke"):
                module.update_matrix_text(
                    CASES,
                    matrix,
                    "ff6_dialogue",
                    pass_result(evidence, "ff6_dialogue"),
                )

    def test_rejects_pass_evidence_outside_rc_device_root(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            evidence = make_raw_evidence_dir(Path(tmp) / "outside-evidence")
            matrix = Path(tmp) / "matrix.md"
            matrix.write_text(matrix_text(), encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "evidence_path_outside_rc_device_root"):
                module.update_matrix_text(
                    CASES,
                    matrix,
                    "ff6_dialogue",
                    pass_result(evidence, "ff6_dialogue"),
                )

    def test_main_apply_updates_file_in_place(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            matrix = Path(tmp) / "matrix.md"
            matrix.write_text(matrix_text(), encoding="utf-8")
            old_argv = sys.argv
            try:
                sys.argv = [
                    "screen_translation_matrix_update.py",
                    "--matrix",
                    str(matrix),
                    "--case-id",
                    "chrono_equipment",
                    "--result",
                    "Fail: layout_grouping",
                    "--apply",
                ]
                result = module.main()
            finally:
                sys.argv = old_argv

            self.assertEqual(0, result)
            self.assertIn("Fail: layout_grouping", matrix.read_text(encoding="utf-8"))


def make_evidence_dir(path: Path, gate: str = "screen_translation", case_id=None) -> Path:
    metadata_case_id = case_id or path.name
    EVIDENCE_ROOT.mkdir(parents=True, exist_ok=True)
    try:
        path.resolve().relative_to(EVIDENCE_ROOT.resolve())
    except ValueError:
        path = Path(tempfile.mkdtemp(prefix=f"test-{path.name}-", dir=EVIDENCE_ROOT))
    return make_raw_evidence_dir(path, gate=gate, case_id=metadata_case_id)


def pass_result(evidence: Path, case_id: str) -> str:
    checklist_by_case = {
        "ff6_dialogue": "layout_ok,language_ok,no_english_source",
        "ff6_main_menu": "layout_ok,language_ok,grouping_ok,bilingual_ok,numbers_ok",
    }
    return f"Pass: evidence {evidence} checklist={checklist_by_case[case_id]}"


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
