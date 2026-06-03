import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/gkp_eval_report.py"


def load_module():
    spec = importlib.util.spec_from_file_location("gkp_eval_report", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class GkpEvalReportTest(unittest.TestCase):

    def test_valid_lite_pack_reports_no_failed_required_lanes(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            gkp_dir = Path(tmp)
            create_pack(gkp_dir / "valid-pack")

            report = module.evaluate_gkp_dir(gkp_dir)[0]
            pass_lanes = [item for item in report.lanes if item.status == "pass"]

            self.assertEqual("community.valid-pack", report.pack_id)
            self.assertEqual("valid_game", report.game_id)
            self.assertEqual(7, report.row_count)
            self.assertEqual(1, report.golden_count)
            self.assertEqual(2, report.observed_asr_count)
            self.assertFalse([lane for lane in report.lanes if lane.status == "fail"])
            self.assertFalse([lane for lane in pass_lanes if lane.detail.startswith("missing ")])

    def test_missing_menu_terms_reports_warn_not_fail(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            pack_dir = Path(tmp) / "valid-pack"
            create_pack(pack_dir)

            report = module.evaluate_pack(pack_dir)
            menu_lane = lane(report, "menu_terms")

            self.assertEqual("warn", menu_lane.status)
            self.assertIn("menu/status/equipment terms", menu_lane.detail)

    def test_missing_citations_reports_fail(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            pack_dir = Path(tmp) / "bad-pack"
            create_pack(pack_dir)
            (pack_dir / "sources/citations.jsonl").unlink()

            report = module.evaluate_pack(pack_dir)
            citations_lane = lane(report, "citations_and_licenses")

            self.assertEqual("fail", citations_lane.status)
            self.assertIn("sources/citations.jsonl", citations_lane.detail)

    def test_observed_asr_variants_are_counted_separately(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            pack_dir = Path(tmp) / "valid-pack"
            create_pack(pack_dir)

            report = module.evaluate_pack(pack_dir)

            self.assertEqual(2, report.observed_asr_count)
            self.assertEqual(3, report.asr_variant_count)
            asr_lane = lane(report, "observed_asr_variants")
            self.assertEqual("pass", asr_lane.status)
            self.assertIn("2 observed ASR aliases", asr_lane.detail)

    def test_markdown_includes_pack_counts_and_lane_summary(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            gkp_dir = Path(tmp)
            create_pack(gkp_dir / "valid-pack")

            report = module.evaluate_gkp_dir(gkp_dir)
            markdown = module.render_markdown(report, gkp_dir)

            self.assertIn("community.valid-pack", markdown)
            self.assertIn("valid_game", markdown)
            self.assertIn("Knowledge rows: 7", markdown)
            self.assertIn("QA goldens: 1", markdown)
            self.assertIn("identity=pass", markdown)


def lane(report, name):
    return next(item for item in report.lanes if item.name == name)


def create_pack(pack_dir: Path) -> None:
    (pack_dir / "knowledge").mkdir(parents=True)
    (pack_dir / "sources").mkdir()
    write_json(
        pack_dir / "manifest.json",
        {
            "schema_version": "gkp.v0",
            "pack_id": "community.valid-pack",
            "game": {
                "game_id": "valid_game",
                "title": "Valid Game",
                "retroarch_labels": ["test__Valid Game"],
            },
            "contents": {
                "knowledge": ["knowledge/main.jsonl"],
                "citations": "sources/citations.jsonl",
                "aliases": "aliases.json",
                "qa_goldens": "qa_goldens.jsonl",
            },
        },
    )
    write_json(
        pack_dir / "aliases.json",
        {
            "language": "zh",
            "aliases": [
                {"term": "core loop", "entity_id": "note.core-gameplay"},
                {"term": "core loup", "entity_id": "note.core-gameplay", "kind": "asr_variant"},
                {"term": "valid gane", "entity_id": "note.identity", "kind": "observed_asr"},
                {"term": "valid gaem", "entity_id": "note.identity", "kind": "observed_asr"},
            ],
        },
    )
    write_jsonl(
        pack_dir / "sources/citations.jsonl",
        [{"source_id": "valid.source", "title": "Valid source"}],
    )
    (pack_dir / "sources/licenses.md").write_text(
        "Linked-source factual references only. No copied guide prose.",
        encoding="utf-8",
    )
    write_jsonl(
        pack_dir / "knowledge/main.jsonl",
        [
            row("note.identity", "note", "Identity", ["identity"]),
            row("note.core-gameplay", "note", "Core gameplay", ["core gameplay"]),
            row("strategy.beginner-direction", "strategy", "First hour", ["first hour"]),
            row("mechanic.basic", "mechanic", "Mechanic", ["mechanic"]),
            row("item.herb", "item", "Item", ["item"]),
            row("npc.hero", "npc", "Hero", ["hero"]),
            row("strategy.lite-boundary", "strategy", "No evidence boundary", ["no_evidence"]),
        ],
    )
    write_jsonl(
        pack_dir / "qa_goldens.jsonl",
        [
            {
                "qa_id": "qa.valid.identity",
                "game_id": "valid_game",
                "question": "What is this game?",
                "expected_entity_ids": ["note.identity"],
                "expected_intent": "game_overview",
                "source_refs": ["valid.source"],
            }
        ],
    )


def row(entity_id, entity_type, name, aliases):
    return {
        "entity_id": entity_id,
        "entity_type": entity_type,
        "canonical_name": name,
        "aliases": aliases,
        "description_short": name,
        "description_long": name,
        "spoiler_level": "light",
        "source_refs": ["valid.source"],
    }


def write_json(path: Path, value) -> None:
    path.write_text(json.dumps(value), encoding="utf-8")


def write_jsonl(path: Path, rows) -> None:
    path.write_text(
        "\n".join(json.dumps(row) for row in rows) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    unittest.main()
