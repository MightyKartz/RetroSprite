import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/gkp_patch_assistant.py"


def load_module():
    spec = importlib.util.spec_from_file_location("gkp_patch_assistant", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class GkpPatchAssistantTest(unittest.TestCase):

    def test_generates_dry_run_patch_proposal_without_writing_assets(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            pack = create_pack(Path(tmp) / "pack")
            before = (pack / "aliases.json").read_text(encoding="utf-8")

            proposal = module.build_proposal(
                pack=pack,
                question="Ivan where?",
                tag="alias_gap",
                source_id="valid.source",
            )
            output = module.render_proposal(proposal)

            self.assertIn("dry_run=true", output)
            self.assertIn("Suggested aliases.json addition", output)
            self.assertIn("Suggested qa_goldens.jsonl addition", output)
            self.assertIn("valid.source", output)
            self.assertEqual(before, (pack / "aliases.json").read_text(encoding="utf-8"))

    def test_rejects_rights_unsafe_content(self):
        module = load_module()
        unsafe_values = [
            "/storage/4A21-0000/Roms/GBA/Game.gba",
            "translation_patch.ips",
            "fan translation script text",
            "NPC: long dialogue line",
            "x" * 701,
        ]

        for value in unsafe_values:
            with self.subTest(value=value[:24]):
                with self.assertRaises(ValueError):
                    module.check_rights_safe_content(value)

    def test_requires_known_source_id(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            pack = create_pack(Path(tmp) / "pack")

            with self.assertRaises(ValueError) as missing:
                module.build_proposal(pack=pack, question="Ivan?", tag="alias_gap", source_id="")
            with self.assertRaises(ValueError) as unknown:
                module.build_proposal(pack=pack, question="Ivan?", tag="alias_gap", source_id="missing.source")

            self.assertIn("missing source_refs", str(missing.exception))
            self.assertIn("unknown source id", str(unknown.exception))

    def test_asr_variant_uses_observed_asr_alias_kind(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            pack = create_pack(Path(tmp) / "pack")

            proposal = module.build_proposal(
                pack=pack,
                question="I ban",
                tag="asr_variant",
                source_id="valid.source",
            )
            output = module.render_proposal(proposal)

            self.assertIn('"kind": "observed_asr"', output)
            self.assertIn("knowledge/entities.jsonl", output)

    def test_asr_variant_can_target_existing_entity_and_canonical_term(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            pack = create_pack(Path(tmp) / "pack")

            proposal = module.build_proposal(
                pack=pack,
                question="玛尔是谁？",
                tag="asr_variant",
                source_id="valid.source",
                entity_id="npc.marle",
                observed_asr="纳尔士",
                canonical_term="玛尔是谁",
            )
            output = module.render_proposal(proposal)

            self.assertIn('"term": "纳尔士"', output)
            self.assertIn('"canonical_term": "玛尔是谁"', output)
            self.assertIn('"entity_id": "npc.marle"', output)
            self.assertIn("Observed hotkey voice transcript", output)
            self.assertIn("No new knowledge row is required", output)
            self.assertNotIn("Add an original short answer here", output)


def create_pack(pack: Path) -> Path:
    (pack / "sources").mkdir(parents=True)
    (pack / "knowledge").mkdir()
    (pack / "manifest.json").write_text(
        json.dumps(
            {
                "pack_id": "community.valid-pack",
                "game": {
                    "game_id": "valid_game",
                    "title": "Valid Game",
                },
            }
        ),
        encoding="utf-8",
    )
    (pack / "sources/citations.jsonl").write_text(
        json.dumps({"source_id": "valid.source", "title": "Valid source"}) + "\n",
        encoding="utf-8",
    )
    (pack / "aliases.json").write_text('{"aliases":[]}\n', encoding="utf-8")
    return pack


if __name__ == "__main__":
    unittest.main()
