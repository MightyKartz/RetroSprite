import importlib.util
import io
import json
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/gkp_gap_backlog.py"


def load_module():
    spec = importlib.util.spec_from_file_location("gkp_gap_backlog", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class GkpGapBacklogTest(unittest.TestCase):

    def test_parses_latest_request_file_with_curl_prefix(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "latest-request.json"
            path.write_text(
                '$ curl -fsS http://127.0.0.1:18080/debug/latest-request\n\n'
                + json.dumps({"label": "gba__Golden Sun", "question": "Where now?", "pipeline_stage": "evidence"}),
                encoding="utf-8",
            )

            records = module.load_records(path)

            self.assertEqual(1, len(records))
            self.assertEqual("gba__Golden Sun", records[0]["label"])

    def test_missing_label_or_question_is_validation_error(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "bad.json"
            path.write_text(json.dumps({"question": "Where now?"}), encoding="utf-8")

            with self.assertRaises(ValueError) as error:
                module.load_records(path)

            self.assertIn("missing label", str(error.exception))

    def test_classifies_no_evidence_alias_and_coverage_gap(self):
        module = load_module()
        record = {
            "label": "gba__Golden Sun",
            "question": "Ivan where?",
            "pipeline_stage": "no_evidence",
            "answer_type": "no_evidence",
            "source_ids": [],
        }

        tags = module.classify_record(record, {"gba__Golden Sun": {"Ivan"}})

        self.assertEqual(["alias_gap", "coverage_gap"], tags)

    def test_classifies_asr_ranking_spoiler_and_translation_gaps(self):
        module = load_module()

        asr_tags = module.classify_record(
            {
                "label": "gba__Golden Sun",
                "question": "Ivan?",
                "raw_question": "I ban?",
                "normalized_question": "Ivan?",
                "pipeline_stage": "evidence",
            },
            {},
        )
        ranking_tags = module.classify_record(
            {
                "label": "gba__Golden Sun",
                "question": "Ivan?",
                "pipeline_stage": "evidence",
                "source_ids": ["wrong.source"],
                "feedback": "wrong",
            },
            {},
        )
        spoiler_tags = module.classify_record(
            {
                "label": "gba__Golden Sun",
                "question": "Final boss?",
                "pipeline_stage": "spoiler_gate",
                "answer_type": "spoiler_gate",
                "source_ids": ["valid.source"],
            },
            {},
        )
        translation_tags = module.classify_record(
            {
                "label": "snes__Final Fantasy VI",
                "question": "翻译",
                "output_mode": "screen_translation",
                "ok": False,
            },
            {},
        )

        self.assertIn("asr_variant", asr_tags)
        self.assertIn("ranking_gap", ranking_tags)
        self.assertIn("spoiler_gate_gap", spoiler_tags)
        self.assertIn("translation_gap", translation_tags)

    def test_renders_markdown_backlog_with_suggested_regression(self):
        module = load_module()
        records = [
            {
                "label": "gba__Golden Sun",
                "question": "Ivan where?",
                "pipeline_stage": "no_evidence",
                "answer_type": "no_evidence",
                "source_ids": [],
            }
        ]

        items = module.build_backlog(records, {"gba__Golden Sun": {"Ivan"}})
        markdown = module.render_markdown(items, Path("evidence"))

        self.assertIn("alias_gap, coverage_gap", markdown)
        self.assertIn("aliases.json", markdown)
        self.assertIn("qa_goldens.jsonl", markdown)

    def test_successful_asr_normalization_is_not_a_gap_backlog_item(self):
        module = load_module()
        records = [
            {
                "label": "genesis__Phantasy_Star_IV",
                "question": "组合技要不要一开始研究",
                "raw_question": "组合计要不要一开始研究",
                "normalized_question": "组合技要不要一开始研究",
                "pipeline_stage": "evidence",
                "ok": True,
                "source_ids": ["ps4.project_notes"],
            }
        ]

        items = module.build_backlog(records, {})

        self.assertEqual([], items)

    def test_successful_hotkey_voice_no_evidence_boundary_is_not_backlog_item(self):
        module = load_module()
        records = [
            {
                "label": "md__Shining Force II",
                "question": "这个游戏有没有恋爱系统？",
                "pipeline_stage": "no_evidence",
                "answer_type": "no_evidence",
                "source_ids": [],
                "output_mode": "hotkey_voice_qa",
                "result": "PASS",
            }
        ]

        items = module.build_backlog(records, {"md__Shining Force II": {"恋爱系统"}})

        self.assertEqual([], items)

    def test_failed_route_hint_boundary_without_sources_enters_backlog(self):
        module = load_module()
        records = [
            {
                "label": "super_nintendo__Final Fantasy VI (USA)",
                "question": "直接告诉我崩坏世界完整路线。",
                "pipeline_stage": "unknown",
                "answer_type": "route_hint",
                "source_ids": [],
                "output_mode": "hotkey_voice_qa",
                "result": "FAIL",
                "notes": "Lite boundary should avoid a heavy-spoiler route dump.",
            }
        ]

        items = module.build_backlog(records, {})

        self.assertEqual(["coverage_gap", "spoiler_gate_gap"], items[0].tags)
        self.assertIn("spoiler_graph", items[0].suggested_area)

    def test_parses_hotkey_voice_results_tsv_as_voice_lifecycle_gap(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "results.tsv"
            path.write_text(
                "\t".join(
                    [
                        "timestamp",
                        "case_name",
                        "pack_id",
                        "category",
                        "label",
                        "spoken_prompt",
                        "tts_backend",
                        "voice",
                        "tts_artifact",
                        "overlay_transcript",
                        "overlay_normalized_transcript",
                        "overlay_matched_term",
                        "raw_question",
                        "normalized_question",
                        "matched_term",
                        "matched_entity_id",
                        "answer_type",
                        "answer_confidence",
                        "pipeline_stage",
                        "llm_status",
                        "source_ids",
                        "overlay_phase",
                        "finish_reason",
                        "asr_commit_reason",
                        "asr_last_partial",
                        "asr_final_text",
                        "asr_selected_transcript",
                        "asr_post_voice_silence_ms",
                        "asr_partial_stable_ms",
                        "asr_required_stable_ms",
                        "asr_endpoint_armed",
                        "asr_final_flush_ms",
                        "asr_sample_count",
                        "asr_audio_read_count",
                        "asr_audio_read_error_count",
                        "asr_peak_amplitude",
                        "asr_last_frame_amplitude",
                        "result",
                        "notes",
                    ]
                )
                + "\n"
                + "\t".join(
                    [
                        "2026-06-01T14:44:15+0800",
                        "sf2_case",
                        "shining-force-ii-md",
                        "localized_term",
                        "md__Shining Force II",
                        "气合之玉怎么用？",
                        "macos_say",
                        "Tingting",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "finished",
                        "muted_recovery",
                        "blank_partial",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "false",
                        "",
                        "48000",
                        "12",
                        "0",
                        "0.18",
                        "0.04",
                        "FAIL",
                        "latest request timestamp unchanged; no request submitted for this case;",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            records = module.load_records(path)
            items = module.build_backlog(records, {})
            markdown = module.render_markdown(items, path)

            self.assertEqual(["voice_lifecycle_gap"], items[0].tags)
            self.assertIn("finish=muted_recovery", items[0].details)
            self.assertIn("asr_commit=blank_partial", items[0].details)
            self.assertIn("endpoint_armed=false", items[0].details)
            self.assertIn("samples=48000", items[0].details)
            self.assertIn("reads=12", items[0].details)
            self.assertIn("peak=0.18", items[0].details)
            self.assertIn("submission=missing", items[0].details)
            self.assertIn("hotkey voice ASR capture", markdown)
            self.assertIn("rerun hotkey_voice_qa_batch.sh", markdown)
            self.assertIn("finish=muted_recovery", markdown)

    def test_parses_hotkey_voice_results_tsv_as_asr_variant_gap(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "results.tsv"
            path.write_text(
                "\t".join(
                    [
                        "timestamp",
                        "case_name",
                        "pack_id",
                        "category",
                        "label",
                        "spoken_prompt",
                        "tts_backend",
                        "voice",
                        "tts_artifact",
                        "overlay_transcript",
                        "overlay_normalized_transcript",
                        "overlay_matched_term",
                        "raw_question",
                        "normalized_question",
                        "matched_term",
                        "matched_entity_id",
                        "answer_type",
                        "answer_confidence",
                        "pipeline_stage",
                        "llm_status",
                        "source_ids",
                        "overlay_phase",
                        "finish_reason",
                        "asr_commit_reason",
                        "asr_last_partial",
                        "asr_final_text",
                        "asr_selected_transcript",
                        "asr_post_voice_silence_ms",
                        "asr_partial_stable_ms",
                        "asr_required_stable_ms",
                        "asr_endpoint_armed",
                        "asr_final_flush_ms",
                        "asr_sample_count",
                        "asr_audio_read_count",
                        "asr_audio_read_error_count",
                        "asr_peak_amplitude",
                        "asr_last_frame_amplitude",
                        "result",
                        "notes",
                    ]
                )
                + "\n"
                + "\t".join(
                    [
                        "2026-06-01T15:41:03+0800",
                        "chrono_marle_observed",
                        "chrono-trigger-snes-zh",
                        "localized_term",
                        "sfc__Chrono Trigger (USA)",
                        "玛尔是谁？",
                        "macos_say",
                        "Tingting",
                        "",
                        "纳尔士",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "no_evidence",
                        "low",
                        "no_evidence",
                        "skipped",
                        "",
                        "finished",
                        "answer_completed",
                        "soft_stop_after_silence_and_stable_partial",
                        "纳尔士",
                        "纳尔士",
                        "纳尔士",
                        "2360",
                        "2399",
                        "650",
                        "true",
                        "2000",
                        "135840",
                        "849",
                        "0",
                        "0.017",
                        "0.0006",
                        "FAIL",
                        "Observed ASR should normalize 纳尔 variants when the recognizer produces that transcript.",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            records = module.load_records(path)
            items = module.build_backlog(records, {"sfc__Chrono Trigger (USA)": {"玛尔"}})
            markdown = module.render_markdown(items, path)

            self.assertIn("asr_variant", items[0].tags)
            self.assertEqual("aliases.json observed_asr", items[0].suggested_area)
            self.assertIn("add observed_asr alias", items[0].regression_target)
            self.assertIn("asr_transcript=纳尔士", items[0].details)
            self.assertIn("asr_transcript=纳尔士", markdown)

    def test_merge_keeps_same_question_with_distinct_asr_transcripts(self):
        module = load_module()
        existing = [
            module.BacklogItem(
                label="super_nintendo__Final Fantasy VI (USA)",
                question="魔石系统是什么？",
                tags=["alias_gap", "asr_variant", "coverage_gap"],
                suggested_area="aliases.json observed_asr",
                regression_target="add observed_asr alias plus hotkey_voice_qa_cases.tsv row",
                details="result=FAIL; asr_transcript=我时系统是什么; stage=no_evidence",
                source="old/results.tsv",
            )
        ]
        new_items = [
            module.BacklogItem(
                label="super_nintendo__Final Fantasy VI (USA)",
                question="魔石系统是什么？",
                tags=["alias_gap", "asr_variant", "coverage_gap"],
                suggested_area="aliases.json observed_asr",
                regression_target="add observed_asr alias plus hotkey_voice_qa_cases.tsv row",
                details="result=FAIL; asr_transcript=五十系统是什么; stage=no_evidence",
                source="new/results.tsv",
            ),
            module.BacklogItem(
                label="super_nintendo__Final Fantasy VI (USA)",
                question="魔石系统是什么？",
                tags=["alias_gap", "asr_variant", "coverage_gap"],
                suggested_area="aliases.json observed_asr",
                regression_target="add observed_asr alias plus hotkey_voice_qa_cases.tsv row",
                details="result=FAIL; asr_transcript=五十系统是什么; stage=no_evidence",
                source="duplicate/results.tsv",
            ),
        ]

        merged = module.merge_backlog_items(existing, new_items)

        self.assertEqual(2, len(merged))
        self.assertEqual(
            ["我时系统是什么", "五十系统是什么"],
            [module.asr_transcript_signature(item) for item in merged],
        )

    def test_parses_manual_tester_notes_tsv_as_translation_gap(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "manual-tester-notes.tsv"
            path.write_text(
                "\t".join(
                    [
                        "game_label",
                        "trigger_phrase",
                        "issue_type",
                        "feedback",
                        "output_mode",
                        "expected",
                        "actual",
                        "evidence",
                        "notes",
                    ]
                )
                + "\n"
                + "\t".join(
                    [
                        "super_nintendo__Final Fantasy VI (USA)",
                        "翻译",
                        "screen_translation",
                        "wrong",
                        "screen_translation",
                        "menu rows stay bilingual and preserve numbers",
                        "numbers were translated as prose",
                        "build/rc-device-evidence/20260601-000000",
                        "FF6 menu grouped poorly",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            records = module.load_records(path)
            items = module.build_backlog(records, {})
            markdown = module.render_markdown(items, path)

            self.assertEqual(1, len(records))
            self.assertEqual(["translation_gap"], items[0].tags)
            self.assertIn("screen_translation_eval_cases.tsv", items[0].suggested_area)
            self.assertIn("issue=screen_translation", items[0].details)
            self.assertIn("expected=menu rows stay bilingual", items[0].details)
            self.assertIn("actual=numbers were translated", items[0].details)
            self.assertIn("evidence=build/rc-device-evidence/20260601-000000", markdown)

    def test_parses_manual_tester_notes_tsv_as_ranking_gap(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "qa-notes.tsv"
            path.write_text(
                "\t".join(
                    [
                        "label",
                        "question",
                        "feedback",
                        "pipeline_stage",
                        "source_ids",
                        "answer_type",
                        "expected",
                        "actual",
                    ]
                )
                + "\n"
                + "\t".join(
                    [
                        "md__Shining Force II",
                        "气合之玉怎么用？",
                        "wrong",
                        "evidence",
                        "sf2.characters",
                        "usage",
                        "sf2.promotion",
                        "character answer",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            records = module.load_records(path)
            items = module.build_backlog(records, {})

            self.assertEqual(["ranking_gap"], items[0].tags)
            self.assertIn("retrieval ranking", items[0].suggested_area)
            self.assertIn("expected=sf2.promotion", items[0].details)
            self.assertIn("actual=character answer", items[0].details)

    def test_parses_manual_gate_receipt_screen_failure_as_translation_gap(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-manual-gate-receipt.json"
            path.write_text(
                json.dumps(
                    {
                        "schema_version": 1,
                        "objective": "M18 Eval Lab + GKP Quality Loop",
                        "screen_translation_results": [
                            {
                                "case_id": "ff6_status",
                                "result": "Fail: numeric_corruption",
                                "notes": "HP value was translated as prose.",
                            },
                            {
                                "case_id": "ff6_dialogue",
                                "result": "Pass: evidence build/rc-device-evidence/20260601-000000",
                                "notes": "",
                            },
                            {
                                "case_id": "multi_page_any",
                                "result": "Blocked: cannot reproduce screen",
                                "notes": "",
                            },
                        ],
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )

            records = module.load_records(path)
            items = module.build_backlog(records, {})

            self.assertEqual(1, len(records))
            self.assertEqual(["translation_gap"], items[0].tags)
            self.assertEqual("super_nintendo__Final Fantasy VI (USA)", items[0].label)
            self.assertEqual("翻译", items[0].question)
            self.assertIn("screen_translation_eval_cases.tsv", items[0].suggested_area)
            self.assertIn("issue=screen_translation:ff6_status:numeric_corruption", items[0].details)
            self.assertIn("expected=layout=grouped_labels; language=zh; numbers=preserve_hp_mp_level_exp", items[0].details)
            self.assertIn("actual=numeric_corruption; HP value was translated as prose.", items[0].details)

    def test_scans_manual_gate_receipt_in_directory(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "ignored.json").write_text(
                json.dumps({"label": "ignored", "question": "ignored"}),
                encoding="utf-8",
            )
            (root / "m18-manual-gate-receipt.json").write_text(
                json.dumps(
                    {
                        "schema_version": 1,
                        "objective": "M18 Eval Lab + GKP Quality Loop",
                        "screen_translation_results": [
                            {
                                "case_id": "chrono_equipment",
                                "result": "Fail: layout_grouping",
                                "notes": "Rows were hard to match.",
                            }
                        ],
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )

            records = module.load_records(root)
            items = module.build_backlog(records, {})

            self.assertEqual(1, len(records))
            self.assertEqual("sfc__Chrono Trigger (USA)", items[0].label)
            self.assertIn("layout_grouping", items[0].details)

    def test_writes_manual_tester_notes_template_without_input(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "gkp-manual-notes-template.tsv"
            old_argv = sys.argv
            try:
                sys.argv = [
                    "gkp_gap_backlog.py",
                    "--manual-notes-template-output",
                    str(output),
                ]
                with redirect_stdout(io.StringIO()) as stdout:
                    result = module.main()
            finally:
                sys.argv = old_argv

            self.assertEqual(0, result)
            self.assertTrue(output.is_file())
            self.assertIn("OK manual notes template", stdout.getvalue())
            text = output.read_text(encoding="utf-8")
            self.assertIn("game_label\tquestion\traw_question", text)
            self.assertIn("expected\tactual\tevidence\tnotes", text)

            records = module.load_records(output)
            items = module.build_backlog(records, {})

            self.assertGreaterEqual(len(records), 3)
            self.assertEqual([], items)

    def test_loads_existing_backlog_markdown_with_escaped_cells(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "backlog.md"
            path.write_text(
                "\n".join(
                    [
                        "# M18 GKP Quality Backlog",
                        "",
                        "| Label | Question | Tags | Suggested Area | Regression Target | Details | Source |",
                        "|---|---|---|---|---|---|---|",
                        "| `md__Shining Force II` | 气合之玉怎么用？ | `asr_variant, ranking_gap` | aliases.json observed_asr | add observed_asr alias | actual=a\\|b | `build/results.tsv` |",
                        "",
                    ]
                ),
                encoding="utf-8",
            )

            items = module.load_backlog_markdown(path)

            self.assertEqual(1, len(items))
            self.assertEqual("md__Shining Force II", items[0].label)
            self.assertEqual(["asr_variant", "ranking_gap"], items[0].tags)
            self.assertEqual("actual=a|b", items[0].details)

    def test_merge_existing_backlog_preserves_existing_and_adds_new_items(self):
        module = load_module()
        existing = [
            module.BacklogItem(
                label="md__Shining Force II",
                question="气合之玉怎么用？",
                tags=["asr_variant", "ranking_gap"],
                suggested_area="aliases.json observed_asr",
                regression_target="add observed_asr alias",
                details="existing",
                source="build/results.tsv",
            )
        ]
        duplicate = module.BacklogItem(
            label="md__Shining Force II",
            question="气合之玉怎么用？",
            tags=["asr_variant", "ranking_gap"],
            suggested_area="aliases.json observed_asr",
            regression_target="add observed_asr alias",
            details="new duplicate",
            source="manual",
        )
        new_item = module.BacklogItem(
            label="super_nintendo__Final Fantasy VI (USA)",
            question="翻译",
            tags=["translation_gap"],
            suggested_area="screen_translation_eval_cases.tsv / glossary / formatter",
            regression_target="add or update screen_translation_eval_cases.tsv row",
            details="new translation",
            source="manual",
        )

        merged = module.merge_backlog_items(existing, [duplicate, new_item])

        self.assertEqual(2, len(merged))
        self.assertEqual("existing", merged[0].details)
        self.assertEqual("new translation", merged[1].details)

    def test_cli_merge_existing_backlog_writes_combined_output(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            existing = tmp_path / "existing.md"
            notes = tmp_path / "manual-tester-notes.tsv"
            output = tmp_path / "merged.md"
            existing.write_text(
                "\n".join(
                    [
                        "# M18 GKP Quality Backlog",
                        "",
                        "- Input: `old`",
                        "- Items: 1",
                        "",
                        "| Label | Question | Tags | Suggested Area | Regression Target | Details | Source |",
                        "|---|---|---|---|---|---|---|",
                        "| `md__Shining Force II` | 气合之玉怎么用？ | `asr_variant, ranking_gap` | aliases.json observed_asr | add observed_asr alias | existing | `build/results.tsv` |",
                        "",
                    ]
                ),
                encoding="utf-8",
            )
            notes.write_text(
                "\t".join(
                    [
                        "game_label",
                        "trigger_phrase",
                        "issue_type",
                        "feedback",
                        "output_mode",
                        "expected",
                        "actual",
                    ]
                )
                + "\n"
                + "\t".join(
                    [
                        "super_nintendo__Final Fantasy VI (USA)",
                        "翻译",
                        "screen_translation",
                        "wrong",
                        "screen_translation",
                        "bilingual menu rows",
                        "numeric corruption",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )
            old_argv = sys.argv
            try:
                sys.argv = [
                    "gkp_gap_backlog.py",
                    "--input",
                    str(notes),
                    "--merge-existing-backlog",
                    str(existing),
                    "--output",
                    str(output),
                ]
                with redirect_stdout(io.StringIO()) as stdout:
                    result = module.main()
            finally:
                sys.argv = old_argv

            markdown = output.read_text(encoding="utf-8")

            self.assertEqual(0, result)
            self.assertIn("OK GKP gap backlog: 2 items", stdout.getvalue())
            self.assertIn("Items: 2", markdown)
            self.assertIn("md__Shining Force II", markdown)
            self.assertIn("super_nintendo__Final Fantasy VI", markdown)

    def test_requires_input_when_not_only_generating_template(self):
        module = load_module()
        old_argv = sys.argv
        try:
            sys.argv = ["gkp_gap_backlog.py"]
            with redirect_stderr(io.StringIO()):
                result = module.main()
        finally:
            sys.argv = old_argv

        self.assertEqual(1, result)


if __name__ == "__main__":
    unittest.main()
