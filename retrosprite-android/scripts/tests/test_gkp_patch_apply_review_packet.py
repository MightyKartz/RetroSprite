import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/gkp_patch_apply_review_packet.py"


def load_module():
    spec = importlib.util.spec_from_file_location("gkp_patch_apply_review_packet", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class GkpPatchApplyReviewPacketTest(unittest.TestCase):

    def test_default_output_splits_dry_run_and_apply_artifacts(self):
        module = load_module()

        self.assertEqual(
            ROOT / "docs/qa-feedback/gkp-patch-apply-dry-run-20260601-hotkey-voice.md",
            module.default_output(False),
        )
        self.assertEqual(
            ROOT / "docs/qa-feedback/gkp-patch-apply-result-20260601-hotkey-voice.md",
            module.default_output(True),
        )

    def test_current_packet_dry_run_has_no_blocked_rows_and_does_not_edit_assets(self):
        module = load_module()
        aliases_path = ROOT / "app/src/main/assets/gkp/shining-force-ii-md/aliases.json"
        before = aliases_path.read_text(encoding="utf-8")

        rows = module.build_apply_rows(
            ROOT / "docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md",
            ROOT / "app/src/main/assets/gkp",
        )
        markdown = module.render_markdown(
            rows,
            ROOT / "docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md",
            ROOT / "app/src/main/assets/gkp",
            "dry_run",
            0,
        )

        self.assertEqual(7, len(rows))
        self.assertFalse({row.status for row in rows} - {"ready", "applied"})
        self.assertEqual(before, aliases_path.read_text(encoding="utf-8"))
        self.assertIn("Assets edited: no", markdown)
        self.assertIn("契河之域怎么用", markdown)
        self.assertIn("依凡士不是一晚", markdown)
        self.assertIn("麦尔是谁", markdown)
        self.assertIn("五十系统是什么", markdown)
        self.assertIn("气巧和技能有什么区别", markdown)

    def test_duplicate_alias_blocks_apply_row(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir) / "gkp"
            pack = create_pack(root, aliases=[{"term": "错听词", "entity_id": "npc.test"}])
            packet = create_packet(Path(temp_dir), pack, alias_term="错听词")

            rows = module.build_apply_rows(packet, root)

            self.assertEqual("blocked", rows[0].status)
            self.assertIn("alias already exists", rows[0].detail)
            with self.assertRaisesRegex(ValueError, "cannot apply blocked rows"):
                module.apply_rows(rows, module.REQUIRED_APPROVAL)

    def test_apply_requires_exact_approval_phrase(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir) / "gkp"
            pack = create_pack(root)
            packet = create_packet(Path(temp_dir), pack)
            rows = module.build_apply_rows(packet, root)

            with self.assertRaisesRegex(ValueError, "requires approval phrase"):
                module.apply_rows(rows, "approved")

            aliases = json.loads((pack / "aliases.json").read_text(encoding="utf-8"))
            self.assertEqual([], aliases["aliases"])

    def test_apply_writes_alias_and_golden_to_fixture_pack(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir) / "gkp"
            pack = create_pack(root)
            packet = create_packet(Path(temp_dir), pack)
            rows = module.build_apply_rows(packet, root)

            edited = module.apply_rows(rows, module.REQUIRED_APPROVAL)

            self.assertEqual(2, edited)
            aliases = json.loads((pack / "aliases.json").read_text(encoding="utf-8"))
            self.assertEqual("错听词", aliases["aliases"][0]["term"])
            golden_lines = (pack / "qa_goldens.jsonl").read_text(encoding="utf-8").splitlines()
            self.assertEqual(1, len(golden_lines))
            golden = json.loads(golden_lines[0])
            self.assertEqual("qa.test.asr.0001.zh", golden["qa_id"])

    def test_already_applied_packet_rows_are_idempotent(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir) / "gkp"
            pack = create_pack(root)
            packet = create_packet(Path(temp_dir), pack)
            rows = module.build_apply_rows(packet, root)
            module.apply_rows(rows, module.REQUIRED_APPROVAL)

            applied_rows = module.build_apply_rows(packet, root)
            edited = module.apply_rows(applied_rows, module.REQUIRED_APPROVAL)

            self.assertEqual({"applied"}, {row.status for row in applied_rows})
            self.assertEqual(0, edited)


def create_pack(root: Path, aliases=None) -> Path:
    pack = root / "test-pack"
    pack.mkdir(parents=True)
    (pack / "aliases.json").write_text(
        json.dumps({"language": "zh", "aliases": aliases or []}, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    (pack / "qa_goldens.jsonl").write_text("", encoding="utf-8")
    return pack


def create_packet(temp_root: Path, pack: Path, alias_term: str = "错听词") -> Path:
    packet = temp_root / "packet.md"
    packet.write_text(
        "\n".join(
            [
                "# GKP Patch Review Packet - Fixture",
                "",
                "## community.test-pack",
                "",
                f"- Pack dir: `{pack}`",
                "- Status: `ready`",
                "- Detail: alias + golden rows ready for human approval",
                "",
                "### aliases.json row",
                "",
                "```json",
                json.dumps(
                    {
                        "term": alias_term,
                        "entity_id": "npc.test",
                        "weight": 0.78,
                        "kind": "observed_asr",
                        "source": "observed_asr",
                        "canonical_term": "测试角色是谁",
                    },
                    ensure_ascii=False,
                    sort_keys=True,
                ),
                "```",
                "",
                "### qa_goldens.jsonl row",
                "",
                "```json",
                json.dumps(
                    {
                        "qa_id": "qa.test.asr.0001.zh",
                        "game_id": "test_game",
                        "language": "zh",
                        "question": alias_term,
                        "expected_normalized_question": "测试角色是谁",
                        "expected_entity_ids": ["npc.test"],
                        "expected_intent": "name_mapping",
                        "progress_gate": "start",
                        "spoiler_level": "none",
                        "source_refs": ["test.source"],
                    },
                    ensure_ascii=False,
                    sort_keys=True,
                ),
                "```",
                "",
                "## Approval Boundary",
                "",
            ]
        ),
        encoding="utf-8",
    )
    return packet


if __name__ == "__main__":
    unittest.main()
