import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/gkp_patch_proposal_audit.py"


def load_module():
    spec = importlib.util.spec_from_file_location("gkp_patch_proposal_audit", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class GkpPatchProposalAuditTest(unittest.TestCase):

    def test_current_hotkey_voice_proposals_pass_preflight(self):
        module = load_module()

        rows = module.audit_proposals(
            ROOT / "docs/qa-feedback/gkp-patch-proposals-20260601-hotkey-voice.md",
            ROOT / "docs/qa-feedback/gkp-quality-backlog.md",
            ROOT / "app/src/main/assets/gkp",
        )

        self.assertEqual(7, len(rows))
        self.assertEqual({"pass"}, {row.status for row in rows})
        self.assertTrue(all(row.proposal.observed_asr for row in rows))
        observed_terms = {row.proposal.observed_asr for row in rows}
        self.assertIn("麦尔是谁", observed_terms)
        self.assertIn("五十系统是什么", observed_terms)
        self.assertIn("气巧和技能有什么区别", observed_terms)

    def test_audit_flags_unknown_source_entity_and_missing_backlog_match(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            gkp_root = tmp_path / "gkp"
            create_pack(gkp_root / "pack")
            proposals = tmp_path / "proposals.md"
            proposals.write_text(
                "\n".join(
                    [
                        "| Pack | Observed ASR | Canonical | Entity | Source |",
                        "|---|---|---|---|---|",
                        "| `community.test` | `heard wrong` | `canonical term` | `missing.entity` | `missing.source` |",
                    ]
                ),
                encoding="utf-8",
            )
            backlog = tmp_path / "backlog.md"
            backlog.write_text("no matching terms here", encoding="utf-8")

            rows = module.audit_proposals(proposals, backlog, gkp_root)

            self.assertEqual("fail", rows[0].status)
            self.assertIn("source_id not found", rows[0].detail)
            self.assertIn("entity_id not found", rows[0].detail)
            self.assertIn("observed_asr not found", rows[0].detail)
            self.assertIn("canonical term not found", rows[0].detail)

    def test_render_markdown_includes_status_summary(self):
        module = load_module()
        proposal = module.ProposalRow(
            pack_id="community.test",
            observed_asr="heard",
            canonical="canonical",
            entity_id="entity.ok",
            source_id="source.ok",
        )
        audit_row = module.ProposalAuditRow(
            proposal=proposal,
            pack_dir="app/src/main/assets/gkp/test",
            status="pass",
            detail="ready",
        )

        markdown = module.render_markdown([audit_row], Path("p.md"), Path("b.md"))

        self.assertIn("# GKP Patch Proposal Audit", markdown)
        self.assertIn("- Status: pass=1, fail=0", markdown)
        self.assertIn("community.test", markdown)


def create_pack(pack: Path) -> None:
    (pack / "sources").mkdir(parents=True)
    (pack / "knowledge").mkdir()
    (pack / "manifest.json").write_text(
        json.dumps({"pack_id": "community.test"}),
        encoding="utf-8",
    )
    (pack / "sources/citations.jsonl").write_text(
        json.dumps({"source_id": "source.ok"}) + "\n",
        encoding="utf-8",
    )
    (pack / "knowledge/entities.jsonl").write_text(
        json.dumps({"entity_id": "entity.ok"}) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    unittest.main()
