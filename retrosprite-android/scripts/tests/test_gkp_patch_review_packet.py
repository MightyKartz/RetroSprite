import importlib.util
import json
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/gkp_patch_review_packet.py"


def load_module():
    spec = importlib.util.spec_from_file_location("gkp_patch_review_packet", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class GkpPatchReviewPacketTest(unittest.TestCase):

    def test_current_review_packet_has_no_blocked_rows_and_does_not_edit_assets(self):
        module = load_module()
        aliases_path = ROOT / "app/src/main/assets/gkp/shining-force-ii-md/aliases.json"
        before = aliases_path.read_text(encoding="utf-8")

        items = module.build_review_items(
            ROOT / "docs/qa-feedback/gkp-patch-proposals-20260601-hotkey-voice.md",
            ROOT / "docs/qa-feedback/gkp-quality-backlog.md",
            ROOT / "app/src/main/assets/gkp",
        )

        self.assertEqual(7, len(items))
        self.assertFalse({item.status for item in items} - {"ready", "applied"})
        self.assertEqual(before, aliases_path.read_text(encoding="utf-8"))
        self.assertTrue(all(item.alias_row["kind"] == "observed_asr" for item in items))
        self.assertTrue(all("expected_normalized_question" in item.golden_row for item in items))
        alias_terms = {item.alias_row["term"] for item in items}
        self.assertIn("麦尔是谁", alias_terms)
        self.assertIn("五十系统是什么", alias_terms)
        self.assertIn("气巧和技能有什么区别", alias_terms)

    def test_review_packet_uses_existing_entity_metadata(self):
        module = load_module()

        items = module.build_review_items(
            ROOT / "docs/qa-feedback/gkp-patch-proposals-20260601-hotkey-voice.md",
            ROOT / "docs/qa-feedback/gkp-quality-backlog.md",
            ROOT / "app/src/main/assets/gkp",
        )
        by_entity = {item.golden_row["expected_entity_ids"][0]: item.golden_row for item in items}

        self.assertEqual("usage", by_entity["item.vigor-ball"]["expected_intent"])
        self.assertEqual("name_mapping", by_entity["npc.ivan"]["expected_intent"])
        self.assertEqual("name_mapping", by_entity["npc.marle"]["expected_intent"])
        self.assertEqual("mechanic", by_entity["mechanic.magicite"]["expected_intent"])
        self.assertEqual("mechanic", by_entity["mechanic.techniques"]["expected_intent"])
        self.assertEqual("elven_town", by_entity["item.vigor-ball"]["progress_gate"])
        self.assertEqual("early_game", by_entity["npc.ivan"]["progress_gate"])
        self.assertEqual("start", by_entity["npc.marle"]["progress_gate"])
        self.assertEqual("early_game", by_entity["mechanic.magicite"]["progress_gate"])
        self.assertEqual("start", by_entity["mechanic.techniques"]["progress_gate"])

    def test_render_markdown_contains_exact_json_rows(self):
        module = load_module()
        items = module.build_review_items(
            ROOT / "docs/qa-feedback/gkp-patch-proposals-20260601-hotkey-voice.md",
            ROOT / "docs/qa-feedback/gkp-quality-backlog.md",
            ROOT / "app/src/main/assets/gkp",
        )

        markdown = module.render_markdown(items, Path("p.md"), Path("b.md"))

        self.assertIn("dry_run=true", markdown)
        self.assertIn("Assets edited: no", markdown)
        self.assertIn("契河之域怎么用", markdown)
        self.assertIn("依凡士不是一晚", markdown)
        self.assertIn("麦尔是谁", markdown)
        self.assertIn("五十系统是什么", markdown)
        self.assertIn("气巧和技能有什么区别", markdown)
        self.assertIn("build/hotkey-voice-qa/", markdown)
        self.assertIn("results.tsv", markdown)
        self.assertNotIn("results.tsv/results.tsv", markdown)
        self.assertIn("qa_goldens.jsonl row", markdown)
        self.assertIn("Approval Boundary", markdown)

    def test_render_json_is_machine_readable_review_index(self):
        module = load_module()
        items = module.build_review_items(
            ROOT / "docs/qa-feedback/gkp-patch-proposals-20260601-hotkey-voice.md",
            ROOT / "docs/qa-feedback/gkp-quality-backlog.md",
            ROOT / "app/src/main/assets/gkp",
        )

        data = module.render_json(
            items,
            Path("docs/qa-feedback/gkp-patch-proposals-20260601-hotkey-voice.md"),
            Path("docs/qa-feedback/gkp-quality-backlog.md"),
            Path("app/src/main/assets/gkp"),
            Path("docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md"),
        )

        self.assertEqual(1, data["schema_version"])
        self.assertIn(data["status"], {"ready", "applied"})
        self.assertTrue(data["dry_run"])
        self.assertFalse(data["assets_edited"])
        self.assertEqual(7, data["counts"]["rows"])
        self.assertEqual(7, data["counts"]["ready"] + data["counts"]["applied"])
        self.assertEqual(0, data["counts"]["blocked"])
        self.assertEqual(7, len(data["review_rows"]))
        alias_terms = {row["alias_row"]["term"] for row in data["review_rows"]}
        self.assertIn("麦尔是谁", alias_terms)
        self.assertIn("五十系统是什么", alias_terms)
        self.assertIn("气巧和技能有什么区别", alias_terms)
        self.assertIn("ct.project_notes", str(data))
        self.assertNotIn("results.tsv/results.tsv", json.dumps(data, ensure_ascii=False))

    def test_duplicate_alias_blocks_review_item(self):
        module = load_module()
        audit = module.load_audit_module()
        proposal = audit.ProposalRow(
            pack_id="community.chrono-trigger-snes-zh",
            observed_asr="纳尔是谁",
            canonical="玛尔是谁",
            entity_id="npc.marle",
            source_id="ct.project_notes",
        )

        item = module.build_review_item(proposal, ROOT / "app/src/main/assets/gkp/chrono-trigger-snes-zh")

        self.assertEqual("blocked", item.status)
        self.assertIn("alias already exists", item.detail)


if __name__ == "__main__":
    unittest.main()
