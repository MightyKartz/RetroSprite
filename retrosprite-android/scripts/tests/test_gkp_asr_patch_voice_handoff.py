import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/gkp_asr_patch_voice_handoff.py"


def load_module():
    spec = importlib.util.spec_from_file_location("gkp_asr_patch_voice_handoff", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class GkpAsrPatchVoiceHandoffTest(unittest.TestCase):

    def test_current_handoff_has_patch_rows_and_voice_cases(self):
        module = load_module()

        handoff = module.build_handoff(
            ROOT / "docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md",
            ROOT / "docs/qa-feedback/gkp-patch-apply-dry-run-20260601-hotkey-voice.md",
            ROOT / "scripts/hotkey_voice_qa_cases.tsv",
        )
        markdown = module.render_markdown(
            handoff,
            ROOT / "docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md",
            ROOT / "docs/qa-feedback/gkp-patch-apply-dry-run-20260601-hotkey-voice.md",
            ROOT / "scripts/hotkey_voice_qa_cases.tsv",
        )

        self.assertEqual(7, len(handoff.patch_rows))
        self.assertEqual(7, len(handoff.voice_cases))
        self.assertIn(handoff.apply_report_status, {"ready", "applied"})
        self.assertEqual("no", handoff.apply_report_assets_edited)
        self.assertIn(
            "sf2_vigor_ball_observed,sf2_localized_term,golden_sun_ivan_observed,ff6_magicite_observed,chrono_marle_observed,ff6_magicite_observed,phantasy_star_tech_skill_smoke",
            markdown,
        )
        self.assertIn("I approve gkp patch review packet 20260601 hotkey voice", markdown)
        self.assertIn("pipeline_stage=evidence", markdown)

    def test_render_json_contains_review_rows_and_replay_cases(self):
        module = load_module()

        handoff = module.build_handoff(
            ROOT / "docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md",
            ROOT / "docs/qa-feedback/gkp-patch-apply-dry-run-20260601-hotkey-voice.md",
            ROOT / "scripts/hotkey_voice_qa_cases.tsv",
        )
        data = module.render_json(
            handoff,
            ROOT / "docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md",
            ROOT / "docs/qa-feedback/gkp-patch-apply-dry-run-20260601-hotkey-voice.md",
            ROOT / "scripts/hotkey_voice_qa_cases.tsv",
            ROOT / "docs/qa-feedback/gkp-asr-patch-voice-replay-handoff.md",
        )

        self.assertEqual(1, data["schema_version"])
        self.assertEqual("ready", data["status"])
        self.assertFalse(data["assets_edited_by_handoff"])
        self.assertEqual({"patch_rows": 7, "voice_cases": 7}, data["counts"])
        self.assertIn(data["apply_report"]["status"], {"ready", "applied"})
        self.assertEqual("no", data["apply_report"]["assets_edited"])
        self.assertEqual(
            "sf2_vigor_ball_observed,sf2_localized_term,golden_sun_ivan_observed,ff6_magicite_observed,chrono_marle_observed,ff6_magicite_observed,phantasy_star_tech_skill_smoke",
            data["case_filter"],
        )
        self.assertEqual("I approve gkp patch review packet 20260601 hotkey voice", data["approval"]["required_phrase"])
        self.assertIn("麦尔是谁", json.dumps(data, ensure_ascii=False))
        self.assertIn("五十系统是什么", json.dumps(data, ensure_ascii=False))
        self.assertIn("气巧和技能有什么区别", json.dumps(data, ensure_ascii=False))

    def test_voice_cases_are_loaded_in_required_replay_order(self):
        module = load_module()

        cases = module.load_voice_cases(
            ROOT / "scripts/hotkey_voice_qa_cases.tsv",
            ["sf2_vigor_ball_observed", "ff6_magicite_observed"],
        )

        self.assertEqual(
            ["sf2_vigor_ball_observed", "ff6_magicite_observed"],
            [case.case_name for case in cases],
        )
        self.assertEqual(["sf2.promotion", "ff6.magicite_wiki"], [case.expected_source for case in cases])

    def test_missing_voice_case_fails_fast(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            cases = Path(tmp) / "cases.tsv"
            cases.write_text(
                "case_name\tpack_id\tcategory\tlabel\tspoken_prompt\texpected_question_source\texpected_stage\texpected_answer_type\texpected_llm_status\texpected_source\texpected_matched_term\texpected_entity_id\tnotes\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(ValueError, "voice replay cases missing"):
                module.load_voice_cases(cases, ["chrono_marle_observed"])


if __name__ == "__main__":
    unittest.main()
