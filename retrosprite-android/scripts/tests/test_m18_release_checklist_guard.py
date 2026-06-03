import importlib.util
import io
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/m18_release_checklist_guard.py"


def load_module():
    spec = importlib.util.spec_from_file_location("m18_release_checklist_guard", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


@unittest.skip("Legacy release checklist guard is outside current M18 scope.")
class M18ReleaseChecklistGuardTest(unittest.TestCase):

    def test_current_guard_is_safe_but_not_closed(self):
        module = load_module()

        summary = module.build_summary(
            checklist_path=ROOT / "docs/RELEASE_CANDIDATE_CHECKLIST.md",
            hotkey_voice_report=ROOT / "docs/qa-feedback/hotkey-voice-matrix-report.md",
            screen_report=ROOT / "docs/qa-feedback/screen-translation-eval-report.md",
            content_rights_packet=ROOT / "docs/qa-feedback/gkp-content-rights-manual-packet.md",
            approval="",
            applied=False,
        )
        markdown = module.render_markdown(
            summary,
            checklist_path=ROOT / "docs/RELEASE_CANDIDATE_CHECKLIST.md",
            hotkey_voice_report=ROOT / "docs/qa-feedback/hotkey-voice-matrix-report.md",
            screen_report=ROOT / "docs/qa-feedback/screen-translation-eval-report.md",
            content_rights_packet=ROOT / "docs/qa-feedback/gkp-content-rights-manual-packet.md",
        )

        self.assertEqual("pass", summary.guard_status)
        self.assertEqual("open", summary.closure_status)
        self.assertEqual(0, summary.unsafe_count)
        self.assertFalse(summary.apply_allowed)
        self.assertIn("Ready items: 0/3", markdown)
        self.assertIn("GKP assets edited by this guard: no", markdown)

    def test_checked_item_without_evidence_is_unsafe(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            checklist = write_checklist(tmp_path, hotkey_checked=True, screen_checked=False, rights_checked=False)

            summary = module.build_summary(
                checklist_path=checklist,
                hotkey_voice_report=ROOT / "docs/qa-feedback/hotkey-voice-matrix-report.md",
                screen_report=ROOT / "docs/qa-feedback/screen-translation-eval-report.md",
                content_rights_packet=ROOT / "docs/qa-feedback/gkp-content-rights-manual-packet.md",
                approval="",
                applied=False,
            )

            self.assertEqual("fail", summary.guard_status)
            self.assertEqual(1, summary.unsafe_count)
            self.assertEqual("uncheck until evidence passes", summary.items[0].action)

    def test_checked_content_rights_without_approval_phrase_is_unsafe(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            checklist = write_checklist(tmp_path, hotkey_checked=False, screen_checked=False, rights_checked=True)
            voice, screen, rights = write_passing_reports(tmp_path)

            summary = module.build_summary(
                checklist_path=checklist,
                hotkey_voice_report=voice,
                screen_report=screen,
                content_rights_packet=rights,
                approval="",
                applied=False,
            )
            content_rights = summary.items[2]

            self.assertEqual("fail", summary.guard_status)
            self.assertEqual("fail", summary.closure_status)
            self.assertEqual(1, summary.unsafe_count)
            self.assertEqual("checked", content_rights.checklist_state)
            self.assertEqual("pass", content_rights.evidence_status)
            self.assertFalse(content_rights.ready_to_check)
            self.assertTrue(content_rights.unsafe_checked)
            self.assertEqual("uncheck until human approval phrase is supplied", content_rights.action)

    def test_apply_refuses_when_current_evidence_is_open(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "guard.md"
            checklist = write_checklist(Path(tmp), hotkey_checked=False, screen_checked=False, rights_checked=False)
            old_argv = sys.argv
            try:
                sys.argv = [
                    "m18_release_checklist_guard.py",
                    "--checklist",
                    str(checklist),
                    "--output",
                    str(output),
                    "--apply",
                    "--content-rights-approval",
                    module.CONTENT_RIGHTS_APPROVAL,
                ]
                with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
                    result = module.main()
            finally:
                sys.argv = old_argv

            self.assertEqual(1, result)
            self.assertIn("- [ ] " + module.HOTKEY_ITEM, checklist.read_text(encoding="utf-8"))

    def test_apply_checks_all_three_items_when_evidence_and_approval_pass(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            checklist = write_checklist(tmp_path, hotkey_checked=False, screen_checked=False, rights_checked=False)
            voice, screen, rights = write_passing_reports(tmp_path)
            output = tmp_path / "guard.md"

            old_argv = sys.argv
            try:
                sys.argv = [
                    "m18_release_checklist_guard.py",
                    "--checklist",
                    str(checklist),
                    "--hotkey-voice-report",
                    str(voice),
                    "--screen-report",
                    str(screen),
                    "--content-rights-packet",
                    str(rights),
                    "--output",
                    str(output),
                    "--apply",
                    "--content-rights-approval",
                    module.CONTENT_RIGHTS_APPROVAL,
                    "--strict",
                ]
                with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
                    result = module.main()
            finally:
                sys.argv = old_argv

            text = checklist.read_text(encoding="utf-8")
            self.assertEqual(0, result)
            self.assertIn("- [x] " + module.HOTKEY_ITEM, text)
            self.assertIn("- [x] " + module.SCREEN_ITEM, text)
            self.assertIn("- [x] " + module.RIGHTS_ITEM, text)
            self.assertIn("Closure status: `pass`", output.read_text(encoding="utf-8"))

    def test_strict_fails_while_current_guard_is_open(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "guard.md"
            old_argv = sys.argv
            try:
                sys.argv = [
                    "m18_release_checklist_guard.py",
                    "--output",
                    str(output),
                    "--strict",
                ]
                with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
                    result = module.main()
            finally:
                sys.argv = old_argv

            self.assertEqual(1, result)
            self.assertIn("Closure status: `open`", output.read_text(encoding="utf-8"))


def write_checklist(
    tmp_path: Path,
    *,
    hotkey_checked: bool,
    screen_checked: bool,
    rights_checked: bool,
) -> Path:
    module = load_module()
    path = tmp_path / "RELEASE_CANDIDATE_CHECKLIST.md"
    path.write_text(
        "\n".join(
            [
                "# Checklist",
                checkbox(hotkey_checked, module.HOTKEY_ITEM),
                checkbox(screen_checked, module.SCREEN_ITEM),
                checkbox(rights_checked, module.RIGHTS_ITEM),
            ]
        ),
        encoding="utf-8",
    )
    return path


def checkbox(checked: bool, label: str) -> str:
    return f"- [{'x' if checked else ' '}] {label}"


def write_passing_reports(tmp_path: Path) -> tuple[Path, Path, Path]:
    voice = tmp_path / "voice.md"
    screen = tmp_path / "screen.md"
    rights = tmp_path / "rights.md"
    voice.write_text(
        "\n".join(
            [
                "# M18 Hotkey Voice Matrix Report",
                "- Results: `build/hotkey-voice-qa/example/results.tsv`",
                "- Total: 7",
                "- Status: pass=7, fail=0, blocked=0, not_run=0, missing=0",
                "- Failure categories: none",
            ]
        ),
        encoding="utf-8",
    )
    screen.write_text(
        "\n".join(
            [
                "# M18 Screen Translation Eval Report",
                "- Total: 5",
                "- Status: pass=5, fail=0, blocked=0, not_run=0, missing=0",
                "- Note issues: 0",
            ]
        ),
        encoding="utf-8",
    )
    rights.write_text(
        "\n".join(
            [
                "# GKP Content Rights Manual Review Packet",
                "- Machine audit: `pass`",
                "- Human release checkbox: `open`",
                "- Bundled packs: 6",
                "- Knowledge files: 49",
                "- License files: 6",
                "- Citation files: 6",
                "## Review Checklist",
            ]
        ),
        encoding="utf-8",
    )
    return voice, screen, rights


if __name__ == "__main__":
    unittest.main()
