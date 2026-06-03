import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/hotkey_voice_matrix_report.py"


def load_module():
    spec = importlib.util.spec_from_file_location("hotkey_voice_matrix_report", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class HotkeyVoiceMatrixReportTest(unittest.TestCase):

    def test_default_matrix_cases_are_loaded_in_release_gate_order(self):
        module = load_module()

        cases = module.load_cases(ROOT / "scripts/hotkey_voice_qa_cases.tsv", module.DEFAULT_MATRIX_CASES)

        self.assertEqual(7, len(cases))
        self.assertEqual("sf2_vigor_ball_observed", cases[0].case_name)
        self.assertEqual("phantasy_star_tech_skill_smoke", cases[-1].case_name)
        self.assertEqual({"hotkey_voice"}, {case.expected_question_source for case in cases})

    def test_report_builds_pass_fail_and_not_run_statuses(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            results = Path(tmp) / "results.tsv"
            results.write_text(
                "\n".join(
                    [
                        "case_name\tlabel\tpipeline_stage\tanswer_type\tllm_status\tsource_ids\toverlay_transcript\tfinish_reason\tasr_commit_reason\tasr_audio_read_count\tasr_peak_amplitude\tresult\tnotes",
                        "sf2_vigor_ball_observed\tmd__Shining Force II\tevidence\tusage\tskipped\tsf2.promotion\t气合之欲怎么用\tanswer_completed\tsoft_stop_after_silence_and_stable_partial\t969\t0.031\tPASS\tok",
                        "chrono_marle_observed\tsfc__Chrono Trigger (USA)\tno_evidence\tno_evidence\tskipped\t\t纳尔士\tanswer_completed\tsoft_stop_after_silence_and_stable_partial\t849\t0.017\tFAIL\texpected_stage mismatch",
                    ]
                ),
                encoding="utf-8",
            )

            cases = module.load_cases(
                ROOT / "scripts/hotkey_voice_qa_cases.tsv",
                ("sf2_vigor_ball_observed", "chrono_marle_observed", "ff6_magicite_observed"),
            )
            statuses = module.build_statuses(cases, module.load_results(results), results)
            by_case = {status.case.case_name: status for status in statuses}

            self.assertEqual("pass", by_case["sf2_vigor_ball_observed"].status)
            self.assertEqual("fail", by_case["chrono_marle_observed"].status)
            self.assertEqual("asr_variant", by_case["chrono_marle_observed"].failure_category)
            self.assertEqual("not_run", by_case["ff6_magicite_observed"].status)

            markdown = module.render_markdown(statuses, ROOT / "scripts/hotkey_voice_qa_cases.tsv", results)
            self.assertIn("pass=1", markdown)
            self.assertIn("fail=1", markdown)
            self.assertIn("not_run=1", markdown)
            self.assertIn("asr_variant=1", markdown)

    def test_default_results_picker_prefers_matrix_coverage_over_latest_single_case(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            older = root / "20260602-010000"
            newer = root / "20260602-020000"
            older.mkdir()
            newer.mkdir()
            header = "case_name\tlabel\tpipeline_stage\tanswer_type\tllm_status\tsource_ids\tresult\tnotes"
            (older / "results.tsv").write_text(
                "\n".join(
                    [
                        header,
                        "sf2_vigor_ball_observed\tmd__Shining Force II\tevidence\tusage\tskipped\tsf2.promotion\tPASS\tok",
                        "golden_sun_ivan_observed\tgba__黄金太阳\tevidence\tcharacter\tskipped\tgs.characters\tPASS\tok",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )
            (newer / "results.tsv").write_text(
                "\n".join(
                    [
                        header,
                        "chrono_trigger_core_gameplay\tsfc__Chrono Trigger (USA)\tevidence\tgame_overview\tskipped\tct.square_enix\tPASS\tok",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            selected = ("sf2_vigor_ball_observed", "golden_sun_ivan_observed")

            self.assertEqual(older / "results.tsv", module.best_results_path(root, selected))

    def test_pass_result_with_contract_mismatch_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            results = Path(tmp) / "results.tsv"
            results.write_text(
                "\n".join(
                    [
                        "case_name\tlabel\tpipeline_stage\tanswer_type\tllm_status\tsource_ids\tresult\tnotes",
                        "ff6_magicite_observed\tsuper_nintendo__Final Fantasy VI (USA)\tevidence\tmechanic\tskipped\twrong.source\tPASS\tbad pass",
                    ]
                ),
                encoding="utf-8",
            )

            case = module.load_cases(
                ROOT / "scripts/hotkey_voice_qa_cases.tsv",
                ("ff6_magicite_observed",),
            )
            status = module.build_statuses(case, module.load_results(results), results)[0]

            self.assertEqual("fail", status.status)
            self.assertEqual("source_mismatch", status.failure_category)
            self.assertIn("source_ids missing ff6.magicite_wiki", status.contract_issues)

    def test_voice_lifecycle_failures_are_classified_from_stale_request_notes(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            results = Path(tmp) / "results.tsv"
            results.write_text(
                "\n".join(
                    [
                        "case_name\tlabel\tpipeline_stage\tanswer_type\tllm_status\tsource_ids\tfinish_reason\tasr_commit_reason\tasr_endpoint_armed\tresult\tnotes",
                        "golden_sun_ivan_observed\t\t\t\t\t\tmuted_recovery\tblank_partial\tfalse\tFAIL\tlatest request timestamp unchanged; no request submitted for this case",
                    ]
                ),
                encoding="utf-8",
            )

            case = module.load_cases(
                ROOT / "scripts/hotkey_voice_qa_cases.tsv",
                ("golden_sun_ivan_observed",),
            )
            status = module.build_statuses(case, module.load_results(results), results)[0]

            self.assertEqual("fail", status.status)
            self.assertEqual("voice_lifecycle_gap", status.failure_category)

    def test_main_strict_fails_when_any_selected_case_is_open(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "voice.md"
            old_argv = sys.argv
            try:
                sys.argv = [
                    "hotkey_voice_matrix_report.py",
                    "--results-root",
                    str(Path(tmp) / "missing-results"),
                    "--case-filter",
                    "sf2_vigor_ball_observed",
                    "--output",
                    str(output),
                    "--strict",
                ]
                result = module.main()
            finally:
                sys.argv = old_argv

            self.assertEqual(1, result)
            self.assertIn("not_run=1", output.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
