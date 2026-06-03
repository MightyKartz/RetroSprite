import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/gkp_content_rights_manual_packet.py"


def load_module():
    spec = importlib.util.spec_from_file_location("gkp_content_rights_manual_packet", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class GkpContentRightsManualPacketTest(unittest.TestCase):

    def test_current_packet_summarizes_six_packs_and_open_human_checkbox(self):
        module = load_module()

        packet = module.build_packet(
            ROOT / "app/src/main/assets/gkp",
            ROOT / "docs/RELEASE_CANDIDATE_CHECKLIST.md",
        )
        markdown = module.render_markdown(
            packet,
            ROOT / "app/src/main/assets/gkp",
            ROOT / "docs/RELEASE_CANDIDATE_CHECKLIST.md",
        )

        self.assertEqual("pass", packet.machine_audit_status)
        self.assertEqual("open", packet.human_checkbox_status)
        self.assertEqual(6, len(packet.packs))
        self.assertEqual(49, packet.knowledge_files)
        self.assertEqual(6, packet.license_files)
        self.assertEqual(6, packet.citation_files)
        self.assertIn("Reject the release if any bundled GKP contains", markdown)
        lower_markdown = markdown.lower()
        self.assertIn("commercial guidebook", lower_markdown)
        self.assertIn("full dialogue/script dumps", lower_markdown)
        self.assertIn("community.final-fantasy-vi-snes-zh", markdown)

    def test_human_checkbox_status_detects_open_checked_and_missing(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "checklist.md"
            path.write_text(
                "- [ ] Human spot-check confirms no commercial guidebook prose, long walkthrough copy, full script dump, or copied fan translation is bundled.\n",
                encoding="utf-8",
            )
            self.assertEqual("open", module.human_checkbox_status(path))

            path.write_text(
                "- [x] Human spot-check confirms no commercial guidebook prose, long walkthrough copy, full script dump, or copied fan translation is bundled.\n",
                encoding="utf-8",
            )
            self.assertEqual("checked", module.human_checkbox_status(path))

            path.write_text("- [ ] Different item\n", encoding="utf-8")
            self.assertEqual("missing", module.human_checkbox_status(path))

    def test_render_markdown_includes_review_commands_and_pack_inventory(self):
        module = load_module()
        packet = module.build_packet(
            ROOT / "app/src/main/assets/gkp",
            ROOT / "docs/RELEASE_CANDIDATE_CHECKLIST.md",
        )

        markdown = module.render_markdown(
            packet,
            Path("app/src/main/assets/gkp"),
            Path("docs/RELEASE_CANDIDATE_CHECKLIST.md"),
        )

        self.assertIn("## Pack Inventory", markdown)
        self.assertIn("python3 scripts/rc_release_audit.py", markdown)
        self.assertIn("find app/src/main/assets/gkp -path '*/knowledge/*.jsonl' -print", markdown)
        self.assertNotIn("m18-manual-gate-receipt.json", markdown)
        self.assertIn("I confirm gkp content rights human spot check", markdown)
        self.assertIn("m18_manual_gate_receipt_check.py", markdown)
        self.assertIn("m18_manual_gate_receipt_plan.py", markdown)
        self.assertIn("do not hand-edit release checklist checkboxes directly", markdown)
        self.assertNotIn("update only the human content-rights checkbox", markdown)


if __name__ == "__main__":
    unittest.main()
