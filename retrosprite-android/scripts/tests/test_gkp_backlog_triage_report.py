import importlib.util
import io
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/gkp_backlog_triage_report.py"


def load_module():
    spec = importlib.util.spec_from_file_location("gkp_backlog_triage_report", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class GkpBacklogTriageReportTest(unittest.TestCase):

    def test_current_backlog_is_triaged_against_review_packet(self):
        module = load_module()

        backlog = module.load_backlog(ROOT / "docs/qa-feedback/gkp-quality-backlog.md")
        patch_rows = module.load_patch_rows(
            ROOT / "docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md"
        )
        existing_policy_goldens = module.load_existing_policy_goldens(ROOT / "app/src/main/assets/gkp")
        device_rerun_passes = module.load_device_rerun_passes(ROOT / "build/hotkey-voice-qa")
        rows = module.build_triage(backlog, patch_rows, existing_policy_goldens, device_rerun_passes)
        categories = module.count_by_category(rows)
        markdown = module.render_markdown(
            rows,
            ROOT / "docs/qa-feedback/gkp-quality-backlog.md",
            ROOT / "docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md",
        )

        self.assertEqual(10, len(rows))
        self.assertEqual(7, categories.get("asr_patch_ready", 0) + categories.get("asr_patch_applied", 0))
        self.assertEqual(1, categories["device_rerun_passed"])
        self.assertEqual(2, categories["policy_golden_existing"])
        self.assertNotIn("device_rerun_needed", categories)
        self.assertNotIn("gkp_triage_needed", categories)
        self.assertNotIn("retrieval_golden_needed", categories)
        self.assertNotIn("policy_golden_needed", categories)
        self.assertNotIn("unclassified", categories)
        self.assertRegex(markdown, r"covered_by_(review_packet|applied_patch)")
        self.assertIn("covered_by_device_rerun", markdown)
        self.assertIn("covered_by_existing_golden", markdown)
        self.assertIn("玛尔是谁", markdown)
        self.assertIn("麦尔是谁 -> 玛尔是谁", markdown)
        self.assertIn("build/hotkey-voice-qa/20260602-083111/results.tsv", markdown)
        self.assertIn("build/hotkey-voice-qa/20260602-060413/results.tsv", markdown)
        self.assertIn("build/hotkey-voice-qa/20260603-073814/results.tsv", markdown)
        self.assertIn("契河之域怎么 -> 气合之玉怎么用", markdown)
        self.assertIn("五十系统是什么 -> 魔石系统是什么", markdown)
        self.assertIn("气巧和技能有什么区别 -> 技巧和技能有什么区别", markdown)
        self.assertIn("Do not add a duplicate golden", markdown)

    def test_fixture_triage_classifies_patch_lifecycle_and_spoiler_rows(self):
        module = load_module()
        backlog_rows = [
            module.BacklogRow(
                label="gba__黄金太阳",
                question="伊凡是不是伊万？",
                tags=("alias_gap", "asr_variant", "coverage_gap"),
                suggested_area="aliases.json observed_asr",
                regression_target="add observed_asr alias",
                details="asr_transcript=依凡士不是一晚; stage=no_evidence",
                source="results.tsv",
            ),
            module.BacklogRow(
                label="sfc__Chrono Trigger (USA)",
                question="时空之轮主要玩什么？",
                tags=("voice_lifecycle_gap",),
                suggested_area="hotkey voice",
                regression_target="rerun row",
                details="finish=muted_recovery; submission=missing",
                source="results.tsv",
            ),
            module.BacklogRow(
                label="md__Langrisser II (Japan)",
                question="直接告诉我所有路线条件。",
                tags=("coverage_gap", "spoiler_gate_gap"),
                suggested_area="spoiler_graph",
                regression_target="add golden",
                details="answer_type=route_hint",
                source="results.tsv",
            ),
        ]
        patch_rows = [
            module.PatchRow(
                pack_id="community.golden-sun-gba-zh",
                alias_term="依凡士不是一晚",
                canonical_term="伊凡是不是伊万",
                entity_id="npc.ivan",
                source_refs=("gs.localized_name_audit",),
            )
        ]

        rows = module.build_triage(backlog_rows, patch_rows)

        self.assertEqual(
            ["asr_patch_ready", "device_rerun_needed", "policy_golden_needed"],
            [row.category for row in rows],
        )
        self.assertEqual("covered_by_review_packet", rows[0].status)
        self.assertEqual("open", rows[1].status)
        self.assertEqual("open", rows[2].status)

    def test_device_rerun_pass_closes_lifecycle_followup_without_asset_patch(self):
        module = load_module()
        backlog_rows = [
            module.BacklogRow(
                label="sfc__Chrono Trigger (USA)",
                question="时空之轮主要玩什么？",
                tags=("voice_lifecycle_gap",),
                suggested_area="hotkey voice",
                regression_target="rerun row",
                details="finish=muted_recovery; submission=missing",
                source="build/hotkey-voice-qa/20260602-051126/results.tsv",
            )
        ]
        device_passes = [
            module.DeviceRerunPass(
                label="sfc__Chrono Trigger (USA)",
                question="时空之轮主要玩什么？",
                evidence="build/hotkey-voice-qa/20260602-060413/results.tsv",
                sort_key="20260602-060413",
                finish_reason="answer_completed",
                source_ids="ct.square_enix",
            )
        ]

        rows = module.build_triage(backlog_rows, [], [], device_passes)

        self.assertEqual("device_rerun_passed", rows[0].category)
        self.assertEqual("covered_by_device_rerun", rows[0].status)
        self.assertEqual("build/hotkey-voice-qa/20260602-060413/results.tsv", rows[0].evidence)
        self.assertIn("No GKP asset change is needed", rows[0].next_step)

    def test_existing_policy_golden_prevents_duplicate_golden_followup(self):
        module = load_module()
        backlog_rows = [
            module.BacklogRow(
                label="md__Langrisser II (Japan)",
                question="直接告诉我所有路线条件。",
                tags=("coverage_gap", "spoiler_gate_gap"),
                suggested_area="spoiler_graph",
                regression_target="add golden",
                details="answer_type=route_hint",
                source="results.tsv",
            )
        ]
        existing_goldens = [
            module.ExistingGolden(
                pack_dir="app/src/main/assets/gkp/langrisser-ii-md-zh",
                qa_id="qa.l2.no-all-routes.zh",
                question="直接告诉我所有路线条件。",
                expected_intent="no_evidence",
                source_refs=("l2.project_notes",),
            )
        ]

        rows = module.build_triage(backlog_rows, [], existing_goldens)

        self.assertEqual("policy_golden_existing", rows[0].category)
        self.assertEqual("covered_by_existing_golden", rows[0].status)
        self.assertIn("qa.l2.no-all-routes.zh", rows[0].patch_match)

    def test_strict_mode_fails_for_unclassified_rows(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            backlog = tmp_path / "backlog.md"
            packet = tmp_path / "packet.md"
            output = tmp_path / "triage.md"
            backlog.write_text(
                "\n".join(
                    [
                        "# M18 GKP Quality Backlog",
                        "",
                        "| Label | Question | Tags | Suggested Area | Regression Target | Details | Source |",
                        "|---|---|---|---|---|---|---|",
                        "| `label` | question | `mystery_gap` | area | target | details | `source` |",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )
            packet.write_text("# empty\n", encoding="utf-8")
            old_argv = sys.argv
            try:
                sys.argv = [
                    "gkp_backlog_triage_report.py",
                    "--backlog",
                    str(backlog),
                    "--review-packet",
                    str(packet),
                    "--output",
                    str(output),
                    "--strict",
                ]
                with redirect_stdout(io.StringIO()):
                    result = module.main()
            finally:
                sys.argv = old_argv

            self.assertEqual(1, result)
            self.assertIn("unclassified", output.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
