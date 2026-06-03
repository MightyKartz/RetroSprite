import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/screen_translation_manual_packet.py"
CASES = ROOT / "scripts/screen_translation_eval_cases.tsv"
EVIDENCE_ROOT = ROOT / "build/rc-device-evidence"


def load_module():
    spec = importlib.util.spec_from_file_location("screen_translation_manual_packet", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class ScreenTranslationManualPacketTest(unittest.TestCase):

    def test_current_packet_contains_all_not_run_cases(self):
        module = load_module()

        cases = module.build_manual_cases(
            CASES,
            ROOT / "docs/qa-feedback/rc-device-matrix.md",
        )
        markdown = module.render_markdown(
            cases,
            Path("scripts/screen_translation_eval_cases.tsv"),
            Path("docs/qa-feedback/rc-device-matrix.md"),
        )

        self.assertEqual(5, len(cases))
        self.assertEqual({"not_run"}, {case.current_status for case in cases})
        self.assertIn("FF6 main menu", markdown)
        self.assertIn("Qwen/Qwen3-VL-8B-Instruct", markdown)
        self.assertIn("Pass: evidence build/rc-device-evidence/<timestamp>", markdown)
        self.assertIn("checklist=layout_ok,language_ok,no_english_source", markdown)
        self.assertIn("checklist=layout_ok,language_ok,grouping_ok,bilingual_ok,numbers_ok", markdown)
        self.assertIn("numeric_corruption", markdown)
        self.assertIn("screen_translation_matrix_update.py", markdown)
        self.assertIn("--output /tmp/retrosprite-screen-matrix-ff6_dialogue.md", markdown)
        self.assertIn("unique real evidence directory under build/rc-device-evidence/", markdown)
        self.assertIn("./scripts/rc_device_evidence.sh --gate screen_translation --case-id ff6_dialogue", markdown)
        self.assertIn("metadata gate `screen_translation`", markdown)
        self.assertIn("Prefer recording each result", markdown)
        self.assertIn("screen_translation_receipt_update.py", markdown)
        self.assertIn("m18_manual_gate_receipt_check.py", markdown)
        self.assertIn("m18_manual_gate_receipt_plan.py", markdown)
        self.assertIn("Do not apply placeholder evidence paths", markdown)
        self.assertNotIn(
            "--result \"Pass: evidence build/rc-device-evidence/<timestamp>\" \\\n  --apply",
            markdown,
        )

    def test_acceptance_checks_separate_dialogue_menu_numbers_and_paging(self):
        module = load_module()
        cases = {
            case.case_id: case
            for case in module.build_manual_cases(
                CASES,
                ROOT / "docs/qa-feedback/rc-device-matrix.md",
            )
        }

        dialogue_checks = "\n".join(module.acceptance_checks(cases["ff6_dialogue"]))
        menu_checks = "\n".join(module.acceptance_checks(cases["ff6_main_menu"]))
        status_checks = "\n".join(module.acceptance_checks(cases["ff6_status"]))
        paging_checks = "\n".join(module.acceptance_checks(cases["multi_page_any"]))

        self.assertIn("English source text is not displayed", dialogue_checks)
        self.assertIn("source labels and Chinese translations together", menu_checks)
        self.assertIn("Numbers and stat values are preserved", status_checks)
        self.assertIn("Each page remains visible for 10 seconds", paging_checks)

    def test_pass_result_template_preserves_existing_evidence(self):
        module = load_module()
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
                        f"| FF6 dialogue | 翻译 | Chinese-only dialogue, no English source | Pass: evidence {evidence} checklist=layout_ok,language_ok,no_english_source |",
                        "| FF6 main menu | 翻译 | Bilingual lookup rows | Not run |",
                        "| FF6 status page | 翻译 | Numbers preserved | Not run |",
                        "| Chrono Trigger equipment | 翻译 | Equipment rows | Not run |",
                        "| Any multi-page result | 翻译 | Paged overlay | Not run |",
                    ]
                ),
                encoding="utf-8",
            )

            cases = module.build_manual_cases(CASES, matrix)
            by_id = {case.case_id: case for case in cases}

            self.assertEqual(
                f"Pass: evidence {evidence} checklist=layout_ok,language_ok,no_english_source",
                module.result_template(by_id["ff6_dialogue"]),
            )
            self.assertEqual(
                "Pass: evidence build/rc-device-evidence/<timestamp> checklist=layout_ok,language_ok,grouping_ok,bilingual_ok,numbers_ok",
                module.result_template(by_id["ff6_main_menu"]),
            )


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
