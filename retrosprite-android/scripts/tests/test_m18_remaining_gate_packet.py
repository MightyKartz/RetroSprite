import importlib.util
import io
import json
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/m18_remaining_gate_packet.py"


def load_module():
    spec = importlib.util.spec_from_file_location("m18_remaining_gate_packet", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class M18RemainingGatePacketTest(unittest.TestCase):

    def test_current_packet_summarizes_observational_voice_and_green_gates(self):
        module = load_module()

        summary = module.build_summary(
            ROOT / "docs/qa-feedback/m18-plan-execution-audit.md",
            ROOT / "docs/qa-feedback/hotkey-voice-matrix-report.md",
        )
        markdown = module.render_markdown(
            summary,
            audit=ROOT / "docs/qa-feedback/m18-plan-execution-audit.md",
            hotkey_voice_report=ROOT / "docs/qa-feedback/hotkey-voice-matrix-report.md",
        )
        data = module.render_json(
            summary,
            audit=ROOT / "docs/qa-feedback/m18-plan-execution-audit.md",
            hotkey_voice_report=ROOT / "docs/qa-feedback/hotkey-voice-matrix-report.md",
        )

        self.assertEqual(0, summary.plan_unchecked)
        self.assertEqual(0, summary.aggregate_open)
        self.assertEqual(7, summary.hotkey_voice_total)
        self.assertEqual(4, summary.hotkey_voice_pass)
        self.assertEqual(3, summary.hotkey_voice_fail)
        self.assertEqual("asr_variant=1, source_mismatch=2", summary.hotkey_voice_categories)
        self.assertIn("Removed from M18 scope", markdown)
        self.assertIn("Hotkey voice matrix", markdown)
        self.assertIn("hotkey_voice_matrix_report.py", markdown)
        self.assertIn("m18_offline_quality_gate.sh", markdown)
        self.assertIn("hotkey_voice_matrix_report.py \\", markdown)
        self.assertIn("m18_completion_audit.py \\", markdown)
        self.assertIn("  --strict", markdown)
        self.assertNotIn("EXPECT_ALL_PASS=1", markdown)
        self.assertIn("GKP assets edited by this handoff: no", markdown)
        self.assertIn("gkp-manual-notes-template.tsv", markdown)
        self.assertIn("--manual-notes-template-output", markdown)
        self.assertIn("After replacing the example rows with real tester observations, preview the merged backlog first", markdown)
        self.assertIn("--merge-existing-backlog docs/qa-feedback/gkp-quality-backlog.md", markdown)
        self.assertIn("build/m18-manual-notes-backlog-preview.md", markdown)
        self.assertNotIn("screen_translation_matrix_update.py", markdown)
        self.assertNotIn("m18_manual_gate_receipt_check.py", markdown)
        self.assertNotIn("content-rights review_scope", markdown)
        self.assertNotIn("<timestamp>", markdown)
        self.assertEqual("pass", data["status"])
        self.assertTrue(data["is_green"])
        self.assertFalse(data["assets_edited_by_handoff"])
        self.assertEqual(0, data["counts"]["plan_unchecked"])
        self.assertEqual(0, data["counts"]["aggregate_open"])
        self.assertEqual(0, data["counts"]["open_gates"])
        self.assertEqual(
            [
                "manual_asr_approval",
                "five_row_screen_translation_manual_matrix",
                "human_content_rights_confirmation",
            ],
            data["removed_from_m18_scope"],
        )
        command_ids = [command["id"] for command in data["commands"]]
        self.assertIn("hotkey_voice_matrix_report", command_ids)
        self.assertIn("offline_quality_gate", command_ids)
        self.assertNotIn("apply_asr_patch", command_ids)
        self.assertNotIn("screen_matrix_preview", command_ids)
        self.assertFalse(data["contract"]["manual_asr_approval_required"])
        self.assertFalse(data["contract"]["screen_translation_manual_matrix_required"])
        self.assertFalse(data["contract"]["content_rights_human_confirmation_required"])

    def test_build_summary_fails_when_required_inputs_are_missing(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            missing = Path(tmp) / "missing.md"

            with self.assertRaisesRegex(ValueError, "required file not found"):
                module.build_summary(missing, missing)

    def test_strict_mode_passes_when_current_gates_are_green(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "packet.md"
            json_output = Path(tmp) / "packet.json"
            old_argv = sys.argv
            try:
                sys.argv = [
                    "m18_remaining_gate_packet.py",
                    "--output",
                    str(output),
                    "--json-output",
                    str(json_output),
                    "--strict",
                ]
                with redirect_stdout(io.StringIO()):
                    result = module.main()
            finally:
                sys.argv = old_argv

            self.assertEqual(0, result)
            self.assertTrue(output.is_file())
            self.assertTrue(json_output.is_file())
            self.assertEqual("pass", json.loads(json_output.read_text(encoding="utf-8"))["status"])

    def test_release_open_items_parser_reads_unchecked_items(self):
        module = load_module()
        text = "\n".join(
            [
                "- [x] Done item.",
                "- [ ] First open item.",
                "- [ ] Second open item.",
            ]
        )

        self.assertEqual(
            ("First open item", "Second open item"),
            tuple(module.extract_release_open_items(text)),
        )


if __name__ == "__main__":
    unittest.main()
