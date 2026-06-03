import importlib.util
import io
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/gkp_asset_mutation_guard.py"


def load_module():
    spec = importlib.util.spec_from_file_location("gkp_asset_mutation_guard", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class GkpAssetMutationGuardTest(unittest.TestCase):

    def test_current_workspace_gkp_assets_have_no_unapproved_edits(self):
        module = load_module()

        summary = module.build_summary(
            ROOT / "app/src/main/assets/gkp",
            ROOT / "docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md",
            ROOT / "docs/qa-feedback/gkp-patch-apply-result-20260601-hotkey-voice.md",
        )

        self.assertEqual("pass", summary.status)
        self.assertIn(summary.mode, {"clean", "approved_patch"})
        self.assertEqual((), summary.unexpected_paths)
        self.assertGreaterEqual(len(summary.expected_paths), 4)

    def test_parse_porcelain_handles_modified_added_and_renamed_paths(self):
        module = load_module()

        paths = module.parse_porcelain(
            "\n".join(
                [
                    " M app/src/main/assets/gkp/a/aliases.json",
                    "?? app/src/main/assets/gkp/b/qa_goldens.jsonl",
                    "R  old/path -> app/src/main/assets/gkp/c/aliases.json",
                ]
            )
        )

        self.assertEqual(
            [
                "app/src/main/assets/gkp/a/aliases.json",
                "app/src/main/assets/gkp/b/qa_goldens.jsonl",
                "app/src/main/assets/gkp/c/aliases.json",
            ],
            paths,
        )

    def test_dirty_assets_without_apply_report_fail(self):
        module = load_module()
        with mock.patch.object(
            module,
            "current_dirty_paths",
            return_value=[module.display_path(ROOT / "app/src/main/assets/gkp/shining-force-ii-md/aliases.json")],
        ):
            summary = module.build_summary(
                ROOT / "app/src/main/assets/gkp",
                ROOT / "docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md",
                Path("/tmp/missing-apply-report.md"),
            )

        self.assertEqual("fail", summary.status)
        self.assertEqual("unapproved_dirty", summary.mode)

    def test_dirty_expected_assets_with_apply_report_pass(self):
        module = load_module()
        expected = [
            module.display_path(ROOT / "app/src/main/assets/gkp/shining-force-ii-md/aliases.json"),
            module.display_path(ROOT / "app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl"),
        ]
        with tempfile.TemporaryDirectory() as tmp:
            report = Path(tmp) / "apply.md"
            report.write_text(
                "\n".join(
                    [
                        "# Apply",
                        "- Mode: `apply`",
                        "- Assets edited: 4",
                    ]
                ),
                encoding="utf-8",
            )
            with mock.patch.object(module, "current_dirty_paths", return_value=expected):
                summary = module.build_summary(
                    ROOT / "app/src/main/assets/gkp",
                    ROOT / "docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md",
                    report,
                )

        self.assertEqual("pass", summary.status)
        self.assertEqual("approved_patch", summary.mode)
        self.assertEqual((), summary.unexpected_paths)

    def test_dirty_unexpected_assets_with_apply_report_fail(self):
        module = load_module()
        dirty = [
            module.display_path(ROOT / "app/src/main/assets/gkp/shining-force-ii-md/aliases.json"),
            module.display_path(ROOT / "app/src/main/assets/gkp/chrono-trigger-snes-zh/knowledge/dialogue_notes.jsonl"),
        ]
        with tempfile.TemporaryDirectory() as tmp:
            report = Path(tmp) / "apply.md"
            report.write_text(
                "\n".join(
                    [
                        "# Apply",
                        "- Mode: `apply`",
                        "- Assets edited: 4",
                    ]
                ),
                encoding="utf-8",
            )
            with mock.patch.object(module, "current_dirty_paths", return_value=dirty):
                summary = module.build_summary(
                    ROOT / "app/src/main/assets/gkp",
                    ROOT / "docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md",
                    report,
                )

        self.assertEqual("fail", summary.status)
        self.assertEqual("unexpected_dirty", summary.mode)
        self.assertEqual(
            (module.display_path(ROOT / "app/src/main/assets/gkp/chrono-trigger-snes-zh/knowledge/dialogue_notes.jsonl"),),
            summary.unexpected_paths,
        )

    def test_main_writes_report_and_strict_passes_when_clean(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "guard.md"
            old_argv = sys.argv
            try:
                sys.argv = [
                    "gkp_asset_mutation_guard.py",
                    "--output",
                    str(output),
                    "--strict",
                ]
                with redirect_stdout(io.StringIO()):
                    result = module.main()
            finally:
                sys.argv = old_argv
            text = output.read_text(encoding="utf-8")

        self.assertEqual(0, result)
        self.assertIn("Guard status: `pass`", text)


if __name__ == "__main__":
    unittest.main()
