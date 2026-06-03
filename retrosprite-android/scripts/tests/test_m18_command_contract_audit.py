import importlib.util
import io
import json
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/m18_command_contract_audit.py"


def load_module():
    spec = importlib.util.spec_from_file_location("m18_command_contract_audit", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class M18CommandContractAuditTest(unittest.TestCase):

    def test_current_generated_commands_pass_contracts(self):
        module = load_module()

        findings = module.audit_paths(module.DEFAULT_INPUTS)

        self.assertTrue(findings)
        self.assertIn(
            ROOT / "docs/qa-feedback/m18-quality-loop-handoff.md",
            module.DEFAULT_INPUTS,
        )
        self.assertIn(
            ROOT / "docs/qa-feedback/m18-quality-loop-handoff.json",
            module.DEFAULT_INPUTS,
        )
        self.assertIn(
            ROOT / "docs/qa-feedback/m18-remaining-gate-handoff.json",
            module.DEFAULT_INPUTS,
        )
        self.assertIn(
            ROOT / "docs/qa-feedback/gkp-asr-patch-voice-replay-handoff.md",
            module.DEFAULT_INPUTS,
        )
        self.assertIn(
            ROOT / "docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.json",
            module.DEFAULT_INPUTS,
        )
        self.assertIn(
            ROOT / "docs/qa-feedback/gkp-asr-patch-voice-replay-handoff.json",
            module.DEFAULT_INPUTS,
        )
        self.assertIn(
            ROOT / "scripts/gkp_patch_regression_gate.sh",
            module.DEFAULT_INPUTS,
        )
        self.assertIn(
            ROOT / "docs/qa-feedback/m18-plan-execution-audit.json",
            module.DEFAULT_INPUTS,
        )
        self.assertIn(
            ROOT / "docs/qa-feedback/m18-completion-audit.json",
            module.DEFAULT_INPUTS,
        )
        self.assertNotIn(
            ROOT / "docs/qa-feedback/m18-manual-gate-receipt-plan.md",
            module.DEFAULT_INPUTS,
        )
        self.assertNotIn(
            ROOT / "docs/qa-feedback/m18-manual-gate-receipt-template.json",
            module.DEFAULT_INPUTS,
        )
        self.assertNotIn(
            ROOT / "docs/qa-feedback/m18-manual-gate-receipt-check.json",
            module.DEFAULT_INPUTS,
        )
        self.assertNotIn(
            ROOT / "docs/qa-feedback/m18-manual-gate-receipt-plan.json",
            module.DEFAULT_INPUTS,
        )
        self.assertNotIn(
            ROOT / "docs/superpowers/plans/2026-06-01-m18-approval-gated-quality-loop.md",
            module.DEFAULT_INPUTS,
        )
        self.assertIn(
            ROOT / "docs/TEST_COVERAGE.md",
            module.DEFAULT_INPUTS,
        )
        self.assertIn(
            ROOT / "docs/NEXT_IMPLEMENTATION_PLAN.md",
            module.DEFAULT_INPUTS,
        )
        self.assertIn(
            ROOT / "README.md",
            module.DEFAULT_INPUTS,
        )
        self.assertIn(
            ROOT / "docs/ARCHITECTURE_AND_PRODUCT_TIERS.md",
            module.DEFAULT_INPUTS,
        )
        self.assertTrue(all(finding.status == "pass" for finding in findings), findings)
        self.assertTrue(
            any(finding.rule == "gkp_patch_apply_approval_flag" for finding in findings),
            findings,
        )
        self.assertTrue(
            any(finding.rule == "gkp_asr_replay_scope" for finding in findings),
            findings,
        )
        self.assertTrue(
            any(finding.rule == "gkp_backlog_import_safety" for finding in findings),
            findings,
        )
        self.assertTrue(
            any(finding.rule == "next_action_queue_json_status_index" for finding in findings),
            findings,
        )
        self.assertTrue(
            any(finding.rule == "remaining_gate_handoff_json_status_index" for finding in findings),
            findings,
        )
        self.assertTrue(
            any(finding.rule == "quality_loop_handoff_json_status_index" for finding in findings),
            findings,
        )
        self.assertTrue(
            any(finding.rule == "plan_execution_audit_json_status_index" for finding in findings),
            findings,
        )
        self.assertTrue(
            any(finding.rule == "completion_audit_json_status_index" for finding in findings),
            findings,
        )
        self.assertTrue(
            any(finding.rule == "gkp_patch_review_packet_json_status_index" for finding in findings),
            findings,
        )
        self.assertTrue(
            any(finding.rule == "gkp_asr_handoff_json_status_index" for finding in findings),
            findings,
        )

    def test_placeholder_screen_apply_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "bad.md"
            path.write_text(
                "\n".join(
                    [
                        "```bash",
                        "python3 scripts/screen_translation_matrix_update.py \\",
                        "  --cases scripts/screen_translation_eval_cases.tsv \\",
                        "  --case-id ff6_dialogue \\",
                        "  --result \"Pass: evidence build/rc-device-evidence/<timestamp> checklist=layout_ok,language_ok,no_english_source\" \\",
                        "  --apply",
                        "```",
                    ]
                ),
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["placeholder_screen_translation_apply"], [finding.rule for finding in failed])

    def test_screen_matrix_update_without_cases_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "bad.md"
            path.write_text(
                "```bash\n"
                "python3 scripts/screen_translation_matrix_update.py "
                "--case-id ff6_dialogue --result \"Fail: numeric_corruption\" --apply\n"
                "```\n",
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["screen_matrix_update_required_flags"], [finding.rule for finding in failed])

    def test_screen_matrix_update_pass_without_checklist_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "bad.md"
            path.write_text(
                "```bash\n"
                "python3 scripts/screen_translation_matrix_update.py "
                "--cases scripts/screen_translation_eval_cases.tsv "
                "--case-id ff6_dialogue "
                "--result \"Pass: evidence build/rc-device-evidence/20260601-000000\" "
                "--output /tmp/preview.md\n"
                "```\n",
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["screen_matrix_update_required_flags"], [finding.rule for finding in failed])
            self.assertIn("checklist=", failed[0].detail)

    def test_release_guard_wrong_approval_flag_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "bad.md"
            path.write_text(
                "```bash\n"
                "python3 scripts/m18_release_checklist_guard.py --apply --approval \"phrase\" --strict\n"
                "```\n",
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["release_checklist_guard_apply_flag"], [finding.rule for finding in failed])

    def test_gkp_patch_apply_without_approval_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "bad.md"
            path.write_text(
                "```bash\n"
                "python3 scripts/gkp_patch_apply_review_packet.py --packet packet.md --apply --strict\n"
                "```\n",
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["gkp_patch_apply_approval_flag"], [finding.rule for finding in failed])

    def test_gkp_patch_apply_with_content_rights_flag_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "bad.md"
            path.write_text(
                "```bash\n"
                "python3 scripts/gkp_patch_apply_review_packet.py --packet packet.md --apply --content-rights-approval \"phrase\" --strict\n"
                "```\n",
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["gkp_patch_apply_approval_flag"], [finding.rule for finding in failed])

    def test_manual_receipt_update_apply_in_intake_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-manual-gate-intake.md"
            path.write_text(
                "```bash\n"
                "python3 scripts/m18_manual_gate_receipt_update.py "
                "--section asr-patch-approval "
                "--decision approved "
                "--approval-phrase \"I approve gkp patch review packet 20260601 hotkey voice\" "
                "--reviewer reviewer "
                "--apply\n"
                "```\n",
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertIn("manual_receipt_update_flags", [finding.rule for finding in failed])

    def test_manual_receipt_update_approved_without_reviewer_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-next-action-queue.md"
            path.write_text(
                "```bash\n"
                "python3 scripts/m18_manual_gate_receipt_update.py "
                "--section content-rights-human-review "
                "--decision approved "
                "--approval-phrase \"I confirm gkp content rights human spot check\" "
                "--output /tmp/receipt.json\n"
                "```\n",
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertIn("manual_receipt_update_flags", [finding.rule for finding in failed])

    def test_asr_handoff_case_filter_count_mismatch_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "gkp-asr-patch-voice-replay-handoff.md"
            path.write_text(
                "\n".join(
                    [
                        "# GKP ASR Patch And Voice Replay Handoff",
                        "- Patch rows: 2",
                        "- Voice replay cases: 2",
                        "```bash",
                        "RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 \\",
                        "CASE_FILTER=sf2_vigor_ball_observed,ff6_magicite_observed,langrisser_commander_smoke \\",
                        "./scripts/hotkey_voice_qa_batch.sh",
                        "```",
                    ]
                ),
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["gkp_asr_replay_scope"], [finding.rule for finding in failed])

    def test_asr_review_packet_json_count_mismatch_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "gkp-patch-review-packet-20260601-hotkey-voice.json"
            path.write_text(
                json.dumps(
                    {
                        "schema_version": 1,
                        "status": "ready",
                        "dry_run": True,
                        "assets_edited": False,
                        "counts": {"rows": 2, "ready": 1, "blocked": 0},
                        "review_rows": [
                            {
                                "pack_id": "community.test",
                                "pack_dir": "app/src/main/assets/gkp/test",
                                "status": "ready",
                                "detail": "ready",
                                "alias_row": {
                                    "term": "测试",
                                    "entity_id": "npc.test",
                                    "kind": "observed_asr",
                                    "source": "observed_asr",
                                    "canonical_term": "测试是谁",
                                },
                                "golden_row": {
                                    "qa_id": "qa.test",
                                    "question": "测试",
                                    "expected_normalized_question": "测试是谁",
                                    "expected_entity_ids": ["npc.test"],
                                    "source_refs": ["test.source"],
                                },
                            }
                        ],
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["gkp_patch_review_packet_json_status_index"], [finding.rule for finding in failed])

    def test_asr_handoff_json_case_filter_mismatch_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "gkp-asr-patch-voice-replay-handoff.json"
            path.write_text(
                json.dumps(
                    {
                        "schema_version": 1,
                        "status": "ready",
                        "assets_edited_by_handoff": False,
                        "counts": {"patch_rows": 1, "voice_cases": 1},
                        "apply_report": {"status": "ready", "assets_edited": "no"},
                        "case_filter": "wrong_case",
                        "approval": {
                            "required": True,
                            "required_phrase": "I approve gkp patch review packet 20260601 hotkey voice",
                        },
                        "patch_rows": [
                            {
                                "pack_id": "community.test",
                                "status": "ready",
                                "detail": "ready",
                                "alias_row": {
                                    "term": "测试",
                                    "entity_id": "npc.test",
                                    "canonical_term": "测试是谁",
                                },
                                "golden_row": {"qa_id": "qa.test"},
                            }
                        ],
                        "voice_cases": [
                            {
                                "case_name": "right_case",
                                "pack_id": "test",
                                "label": "test",
                                "spoken_prompt": "测试是谁",
                                "expected_stage": "evidence",
                                "expected_answer_type": "direct",
                                "expected_source": "test.source",
                            }
                        ],
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["gkp_asr_handoff_json_status_index"], [finding.rule for finding in failed])

    def test_stale_three_row_asr_scope_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "handoff.md"
            path.write_text(
                "After approval, run the three-row replay command for the current ASR patch.\n",
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["gkp_asr_replay_scope"], [finding.rule for finding in failed])

    def test_stale_chinese_three_asr_scope_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "README.md"
            path.write_text(
                "接下来等人工批准 3 个 ASR 变体，再重放 3 条失败 voice rows。\n",
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["gkp_asr_replay_scope"], [finding.rule for finding in failed])

    def test_remaining_handoff_asr_case_filter_mismatch_is_not_current_m18_gate(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            asr = tmp_path / "gkp-asr-patch-voice-replay-handoff.md"
            remaining = tmp_path / "m18-remaining-gate-handoff.md"
            asr.write_text(
                "\n".join(
                    [
                        "# GKP ASR Patch And Voice Replay Handoff",
                        "- Patch rows: 4",
                        "- Voice replay cases: 4",
                        "```bash",
                        "RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 \\",
                        "CASE_FILTER=sf2_vigor_ball_observed,sf2_localized_term,golden_sun_ivan_observed,ff6_magicite_observed \\",
                        "./scripts/hotkey_voice_qa_batch.sh",
                        "```",
                    ]
                ),
                encoding="utf-8",
            )
            remaining.write_text(
                "\n".join(
                    [
                        "# M18 Remaining Gate Handoff",
                        "| GKP ASR patch + voice replay | `patch_rows=4; voice_cases=4; apply=dry_run` | Replay. |",
                        "```bash",
                        "RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 \\",
                        "CASE_FILTER=sf2_vigor_ball_observed,ff6_magicite_observed \\",
                        "./scripts/hotkey_voice_qa_batch.sh",
                        "```",
                    ]
                ),
                encoding="utf-8",
            )

            findings = module.audit_paths((asr, remaining))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertNotIn("remaining_handoff_asr_replay_scope", [finding.rule for finding in failed])

    def test_next_action_queue_asr_case_filter_mismatch_is_not_current_m18_gate(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            asr = tmp_path / "gkp-asr-patch-voice-replay-handoff.md"
            queue = tmp_path / "m18-next-action-queue.md"
            asr.write_text(
                "\n".join(
                    [
                        "# GKP ASR Patch And Voice Replay Handoff",
                        "- Patch rows: 4",
                        "- Voice replay cases: 4",
                        "```bash",
                        "RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 \\",
                        "CASE_FILTER=sf2_vigor_ball_observed,sf2_localized_term,golden_sun_ivan_observed,ff6_magicite_observed \\",
                        "./scripts/hotkey_voice_qa_batch.sh",
                        "```",
                    ]
                ),
                encoding="utf-8",
            )
            queue.write_text(
                "\n".join(
                    [
                        "# M18 Next Action Queue",
                        "```bash",
                        "RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 \\",
                        "CASE_FILTER=sf2_vigor_ball_observed,ff6_magicite_observed \\",
                        "./scripts/hotkey_voice_qa_batch.sh",
                        "```",
                    ]
                ),
                encoding="utf-8",
            )

            findings = module.audit_paths((asr, queue))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertNotIn("next_action_queue_asr_replay_scope", [finding.rule for finding in failed])

    def test_approval_plan_asr_case_filter_mismatch_is_not_current_m18_gate(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            asr = tmp_path / "gkp-asr-patch-voice-replay-handoff.md"
            plan = tmp_path / "2026-06-01-m18-approval-gated-quality-loop.md"
            asr.write_text(
                "\n".join(
                    [
                        "# GKP ASR Patch And Voice Replay Handoff",
                        "- Patch rows: 4",
                        "- Voice replay cases: 4",
                        "```bash",
                        "RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 \\",
                        "CASE_FILTER=sf2_vigor_ball_observed,sf2_localized_term,golden_sun_ivan_observed,ff6_magicite_observed \\",
                        "./scripts/hotkey_voice_qa_batch.sh",
                        "```",
                    ]
                ),
                encoding="utf-8",
            )
            plan.write_text(
                "\n".join(
                    [
                        "# Approval Gated Quality Loop",
                        "```bash",
                        "RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 \\",
                        "CASE_FILTER=sf2_vigor_ball_observed,ff6_magicite_observed \\",
                        "./scripts/hotkey_voice_qa_batch.sh",
                        "```",
                    ]
                ),
                encoding="utf-8",
            )

            findings = module.audit_paths((asr, plan))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertNotIn("approval_plan_asr_replay_scope", [finding.rule for finding in failed])

    def test_stale_asr_intake_scope_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-manual-gate-intake.md"
            path.write_text(
                "Review the exact alias/golden rows for Chrono Trigger, Final Fantasy VI, and Langrisser II.\n",
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["asr_intake_review_packet_sync"], [finding.rule for finding in failed])

    def test_asr_intake_missing_current_review_rows_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-manual-gate-intake.md"
            path.write_text(
                "Review the current ASR patch packet rows, but the row details are accidentally omitted.\n",
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["asr_intake_review_packet_sync"], [finding.rule for finding in failed])

    def test_asr_receipt_template_missing_current_review_rows_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-manual-gate-receipt-template.json"
            path.write_text(
                '{"asr_patch_approval":{"decision":"pending","review_rows":[]}}\n',
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertIn(
                "asr_receipt_template_review_packet_sync",
                [finding.rule for finding in failed],
            )

    def test_screen_receipt_template_case_policy_mismatch_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-manual-gate-receipt-template.json"
            path.write_text(
                """{
  "asr_patch_approval": {
    "review_rows": [
      {
        "pack_id": "community.shining-force-ii-md",
        "observed_asr": "契河之域怎么用",
        "canonical_term": "气合之玉怎么用",
        "entity_id": "item.vigor-ball",
        "source_refs": ["sf2.promotion"]
      },
      {
        "pack_id": "community.final-fantasy-vi-snes-zh",
        "observed_asr": "无十系统是什",
        "canonical_term": "魔石系统是什么",
        "entity_id": "mechanic.magicite",
        "source_refs": ["ff6.magicite_wiki"]
      }
    ]
  },
  "screen_translation_results": [
    {
      "case_id": "ff6_dialogue",
      "game_label": "super_nintendo__Final Fantasy VI (USA)",
      "screen_type": "dialogue",
      "trigger_phrase": "翻译",
      "expected_layout": "wrong_layout",
      "expected_language": "zh",
      "number_policy": "no_numbers",
      "evidence_required": "manual_screenshot"
    }
  ]
}
""",
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertIn(
                "screen_receipt_template_case_policy_sync",
                [finding.rule for finding in failed],
            )

    def test_content_rights_receipt_template_scope_missing_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-manual-gate-receipt-template.json"
            path.write_text(
                """{
  "asr_patch_approval": {
    "review_rows": [
      {
        "pack_id": "community.shining-force-ii-md",
        "observed_asr": "契河之域怎么用",
        "canonical_term": "气合之玉怎么用",
        "entity_id": "item.vigor-ball",
        "source_refs": ["sf2.promotion"]
      },
      {
        "pack_id": "community.final-fantasy-vi-snes-zh",
        "observed_asr": "无十系统是什",
        "canonical_term": "魔石系统是什么",
        "entity_id": "mechanic.magicite",
        "source_refs": ["ff6.magicite_wiki"]
      }
    ]
  },
  "screen_translation_results": [
    {
      "case_id": "ff6_dialogue",
      "game_label": "super_nintendo__Final Fantasy VI (USA)",
      "screen_type": "dialogue",
      "trigger_phrase": "翻译",
      "expected_layout": "chinese_only",
      "expected_language": "zh",
      "number_policy": "no_numbers",
      "evidence_required": "manual_screenshot"
    },
    {
      "case_id": "ff6_main_menu",
      "game_label": "super_nintendo__Final Fantasy VI (USA)",
      "screen_type": "menu",
      "trigger_phrase": "翻译",
      "expected_layout": "bilingual_rows",
      "expected_language": "en_zh",
      "number_policy": "preserve_numbers",
      "evidence_required": "manual_screenshot"
    },
    {
      "case_id": "ff6_status",
      "game_label": "super_nintendo__Final Fantasy VI (USA)",
      "screen_type": "status",
      "trigger_phrase": "翻译",
      "expected_layout": "grouped_labels",
      "expected_language": "zh",
      "number_policy": "preserve_hp_mp_level_exp",
      "evidence_required": "manual_screenshot"
    },
    {
      "case_id": "chrono_equipment",
      "game_label": "sfc__Chrono Trigger (USA)",
      "screen_type": "equipment",
      "trigger_phrase": "翻译",
      "expected_layout": "bilingual_rows",
      "expected_language": "en_zh",
      "number_policy": "preserve_numbers",
      "evidence_required": "manual_screenshot"
    },
    {
      "case_id": "multi_page_any",
      "game_label": "any",
      "screen_type": "mixed",
      "trigger_phrase": "翻译",
      "expected_layout": "paged_overlay",
      "expected_language": "zh_or_en_zh",
      "number_policy": "ten_seconds_per_page",
      "evidence_required": "manual_screenshot"
    }
  ],
  "content_rights_review": {
    "review_scope": {"bundled_packs": 0}
  }
}
""",
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertIn(
                "content_rights_receipt_template_scope_sync",
                [finding.rule for finding in failed],
            )

    def test_screen_manual_packet_missing_case_capture_command_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "screen-translation-manual-packet.md"
            path.write_text(
                "\n".join(
                    [
                        "# Screen Translation Manual QA Packet",
                        "./scripts/rc_device_evidence.sh --gate screen_translation --case-id ff6_dialogue",
                    ]
                ),
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["screen_evidence_capture_metadata"], [finding.rule for finding in failed])
            self.assertIn("ff6_main_menu", failed[0].detail)

    def test_receipt_template_missing_screen_metadata_instruction_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-manual-gate-receipt-template.json"
            path.write_text(
                """{
  "notes": "Replace placeholder evidence paths before saving the receipt.",
  "asr_patch_approval": {
    "review_rows": [
      {
        "pack_id": "community.shining-force-ii-md",
        "observed_asr": "契河之域怎么用",
        "canonical_term": "气合之玉怎么用",
        "entity_id": "item.vigor-ball",
        "source_refs": ["sf2.promotion"]
      },
      {
        "pack_id": "community.final-fantasy-vi-snes-zh",
        "observed_asr": "无十系统是什",
        "canonical_term": "魔石系统是什么",
        "entity_id": "mechanic.magicite",
        "source_refs": ["ff6.magicite_wiki"]
      }
    ]
  },
  "screen_translation_results": [
    {
      "case_id": "ff6_dialogue",
      "game_label": "super_nintendo__Final Fantasy VI (USA)",
      "screen_type": "dialogue",
      "trigger_phrase": "翻译",
      "expected_layout": "chinese_only",
      "expected_language": "zh",
      "number_policy": "no_numbers",
      "evidence_required": "manual_screenshot"
    },
    {
      "case_id": "ff6_main_menu",
      "game_label": "super_nintendo__Final Fantasy VI (USA)",
      "screen_type": "menu",
      "trigger_phrase": "翻译",
      "expected_layout": "bilingual_rows",
      "expected_language": "en_zh",
      "number_policy": "preserve_numbers",
      "evidence_required": "manual_screenshot"
    },
    {
      "case_id": "ff6_status",
      "game_label": "super_nintendo__Final Fantasy VI (USA)",
      "screen_type": "status",
      "trigger_phrase": "翻译",
      "expected_layout": "grouped_labels",
      "expected_language": "zh",
      "number_policy": "preserve_hp_mp_level_exp",
      "evidence_required": "manual_screenshot"
    },
    {
      "case_id": "chrono_equipment",
      "game_label": "sfc__Chrono Trigger (USA)",
      "screen_type": "equipment",
      "trigger_phrase": "翻译",
      "expected_layout": "bilingual_rows",
      "expected_language": "en_zh",
      "number_policy": "preserve_numbers",
      "evidence_required": "manual_screenshot"
    },
    {
      "case_id": "multi_page_any",
      "game_label": "any",
      "screen_type": "mixed",
      "trigger_phrase": "翻译",
      "expected_layout": "paged_overlay",
      "expected_language": "zh_or_en_zh",
      "number_policy": "ten_seconds_per_page",
      "evidence_required": "manual_screenshot"
    }
  ],
  "content_rights_review": {
    "review_scope": {"bundled_packs": 0}
  }
}
""",
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed_rules = [finding.rule for finding in findings if finding.status == "fail"]
            self.assertIn("screen_evidence_capture_metadata", failed_rules)

    def test_receipt_template_missing_screen_screenshot_instruction_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-manual-gate-receipt-template.json"
            path.write_text(
                """{
  "notes": "Screen translation Pass evidence must be captured with ./scripts/rc_device_evidence.sh --gate screen_translation --case-id <case_id> so metadata.json matches the row, and Pass results must keep the generated checklist= tokens.",
  "asr_patch_approval": {
    "review_rows": [
      {
        "pack_id": "community.shining-force-ii-md",
        "observed_asr": "契河之域怎么",
        "canonical_term": "气合之玉怎么用",
        "entity_id": "item.vigor-ball",
        "source_refs": ["sf2.promotion"]
      },
      {
        "pack_id": "community.shining-force-ii-md",
        "observed_asr": "契河之域怎么用",
        "canonical_term": "气合之玉怎么用",
        "entity_id": "item.vigor-ball",
        "source_refs": ["sf2.promotion"]
      },
      {
        "pack_id": "community.golden-sun-gba-zh",
        "observed_asr": "依凡士不是一晚",
        "canonical_term": "伊凡是不是伊万",
        "entity_id": "npc.ivan",
        "source_refs": ["gs.localized_name_audit"]
      },
      {
        "pack_id": "community.final-fantasy-vi-snes-zh",
        "observed_asr": "我时系统是什么",
        "canonical_term": "魔石系统是什么",
        "entity_id": "mechanic.magicite",
        "source_refs": ["ff6.magicite_wiki"]
      }
    ]
  },
  "screen_translation_results": [
    {
      "case_id": "ff6_dialogue",
      "game_label": "super_nintendo__Final Fantasy VI (USA)",
      "screen_type": "dialogue",
      "trigger_phrase": "翻译",
      "expected_layout": "chinese_only",
      "expected_language": "zh",
      "number_policy": "no_numbers",
      "evidence_required": "manual_screenshot"
    },
    {
      "case_id": "ff6_main_menu",
      "game_label": "super_nintendo__Final Fantasy VI (USA)",
      "screen_type": "menu",
      "trigger_phrase": "翻译",
      "expected_layout": "bilingual_rows",
      "expected_language": "en_zh",
      "number_policy": "preserve_numbers",
      "evidence_required": "manual_screenshot"
    },
    {
      "case_id": "ff6_status",
      "game_label": "super_nintendo__Final Fantasy VI (USA)",
      "screen_type": "status",
      "trigger_phrase": "翻译",
      "expected_layout": "grouped_labels",
      "expected_language": "zh",
      "number_policy": "preserve_hp_mp_level_exp",
      "evidence_required": "manual_screenshot"
    },
    {
      "case_id": "chrono_equipment",
      "game_label": "sfc__Chrono Trigger (USA)",
      "screen_type": "equipment",
      "trigger_phrase": "翻译",
      "expected_layout": "bilingual_rows",
      "expected_language": "en_zh",
      "number_policy": "preserve_numbers",
      "evidence_required": "manual_screenshot"
    },
    {
      "case_id": "multi_page_any",
      "game_label": "any",
      "screen_type": "mixed",
      "trigger_phrase": "翻译",
      "expected_layout": "paged_overlay",
      "expected_language": "zh_or_en_zh",
      "number_policy": "ten_seconds_per_page",
      "evidence_required": "manual_screenshot"
    }
  ],
  "content_rights_review": {
    "review_scope": {"bundled_packs": 0}
  }
}
""",
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            failed_rules = [finding.rule for finding in failed]
            self.assertIn("screen_evidence_capture_metadata", failed_rules)
            self.assertTrue(
                any("screenshot.png" in finding.detail for finding in failed),
                failed,
            )

    def test_rc_device_matrix_missing_screen_metadata_policy_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "rc-device-matrix.md"
            path.write_text(
                "Pass evidence must be under build/rc-device-evidence/ and contain health.json.\n",
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["screen_evidence_capture_metadata"], [finding.rule for finding in failed])

    def test_rc_device_matrix_missing_include_screenshot_policy_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "rc-device-matrix.md"
            path.write_text(
                "Pass evidence uses ./scripts/rc_device_evidence.sh --gate screen_translation --case-id <case_id> "
                "and contains metadata.json, screenshot.png, screen_translation, and checklist= tokens.\n",
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["screen_evidence_capture_metadata"], [finding.rule for finding in failed])
            self.assertIn("--include-screenshot", failed[0].detail)

    def test_next_plan_missing_screen_screenshot_policy_is_not_current_m18_gate(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "NEXT_IMPLEMENTATION_PLAN.md"
            path.write_text(
                "Screen Pass evidence must be under build/rc-device-evidence/ and include health.json only.\n",
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertNotIn("screen_evidence_capture_metadata", [finding.rule for finding in failed])

    def test_prose_mentions_are_not_treated_as_commands(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "notes.md"
            path.write_text(
                "The docs mention `scripts/m18_release_checklist_guard.py --apply` in prose only.\n",
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            self.assertTrue(all(finding.status == "pass" for finding in findings), findings)

    def test_receipt_plan_screen_apply_without_preview_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-manual-gate-receipt-plan.md"
            path.write_text(
                "\n".join(
                    [
                        "```bash",
                        "python3 scripts/screen_translation_matrix_update.py \\",
                        "  --cases scripts/screen_translation_eval_cases.tsv \\",
                        "  --case-id ff6_dialogue \\",
                        "  --result 'Fail: numeric_corruption' \\",
                        "  --apply",
                        "```",
                    ]
                ),
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(
                ["receipt_plan_screen_preview_before_apply"],
                [finding.rule for finding in failed],
            )

    def test_manual_entrypoint_direct_screen_apply_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-next-action-queue.md"
            path.write_text(
                "\n".join(
                    [
                        "```bash",
                        "python3 scripts/screen_translation_matrix_update.py \\",
                        "  --cases scripts/screen_translation_eval_cases.tsv \\",
                        "  --case-id ff6_dialogue \\",
                        "  --result 'Fail: numeric_corruption' \\",
                        "  --apply",
                        "```",
                    ]
                ),
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(
                ["manual_entrypoint_screen_preview_first"],
                [finding.rule for finding in failed],
            )

    def test_manual_intake_direct_content_rights_guard_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-manual-gate-intake.md"
            path.write_text(
                "\n".join(
                    [
                        "```bash",
                        "python3 scripts/m18_release_checklist_guard.py \\",
                        "  --output docs/qa-feedback/m18-release-checklist-guard.md \\",
                        "  --content-rights-approval \"I confirm gkp content rights human spot check\"",
                        "```",
                    ]
                ),
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertIn(
                "manual_intake_content_rights_receipt_first",
                [finding.rule for finding in failed],
            )

    def test_receipt_plan_screen_apply_with_preview_passes(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-manual-gate-receipt-plan.md"
            path.write_text(
                "\n".join(
                    [
                        "```bash",
                        "python3 scripts/screen_translation_matrix_update.py \\",
                        "  --cases scripts/screen_translation_eval_cases.tsv \\",
                        "  --case-id ff6_dialogue \\",
                        "  --result 'Fail: numeric_corruption' \\",
                        "  --output build/m18-screen-matrix-previews/ff6_dialogue.md",
                        "",
                        "python3 scripts/screen_translation_matrix_update.py \\",
                        "  --cases scripts/screen_translation_eval_cases.tsv \\",
                        "  --case-id ff6_dialogue \\",
                        "  --result 'Fail: numeric_corruption' \\",
                        "  --apply",
                        "```",
                    ]
                ),
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            self.assertTrue(all(finding.status == "pass" for finding in findings), findings)

    def test_manual_notes_backlog_import_without_merge_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "bad.md"
            path.write_text(
                "\n".join(
                    [
                        "```bash",
                        "python3 scripts/gkp_gap_backlog.py \\",
                        "  --input docs/qa-feedback/gkp-manual-notes-template.tsv \\",
                        "  --output docs/qa-feedback/gkp-quality-backlog.md",
                        "```",
                    ]
                ),
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["gkp_backlog_import_safety"], [finding.rule for finding in failed])

    def test_backlog_input_active_import_without_merge_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "bad.sh"
            path.write_text(
                "\n".join(
                    [
                        "#!/usr/bin/env bash",
                        "python3 scripts/gkp_gap_backlog.py \\",
                        '  --input "$BACKLOG_INPUT" \\',
                        "  --output docs/qa-feedback/gkp-quality-backlog.md",
                    ]
                ),
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["gkp_backlog_import_safety"], [finding.rule for finding in failed])

    def test_backlog_input_active_import_with_merge_passes(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "good.sh"
            path.write_text(
                "\n".join(
                    [
                        "#!/usr/bin/env bash",
                        "python3 scripts/gkp_gap_backlog.py \\",
                        '  --input "$BACKLOG_INPUT" \\',
                        "  --merge-existing-backlog docs/qa-feedback/gkp-quality-backlog.md \\",
                        "  --output docs/qa-feedback/gkp-quality-backlog.md",
                    ]
                ),
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            self.assertTrue(all(finding.status == "pass" for finding in findings), findings)
            self.assertTrue(
                any(finding.rule == "gkp_backlog_import_safety" for finding in findings),
                findings,
            )

    def test_manual_notes_backlog_import_with_merge_passes(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "good.md"
            path.write_text(
                "\n".join(
                    [
                        "```bash",
                        "python3 scripts/gkp_gap_backlog.py \\",
                        "  --input docs/qa-feedback/gkp-manual-notes-template.tsv \\",
                        "  --merge-existing-backlog docs/qa-feedback/gkp-quality-backlog.md \\",
                        "  --output docs/qa-feedback/gkp-quality-backlog.md",
                        "```",
                    ]
                ),
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            self.assertTrue(all(finding.status == "pass" for finding in findings), findings)
            self.assertTrue(
                any(finding.rule == "gkp_backlog_import_safety" for finding in findings),
                findings,
            )

    def test_receipt_backlog_import_to_active_backlog_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "bad.md"
            path.write_text(
                "\n".join(
                    [
                        "```bash",
                        "python3 scripts/gkp_gap_backlog.py \\",
                        "  --input docs/qa-feedback/m18-manual-gate-receipt.json \\",
                        "  --output docs/qa-feedback/gkp-quality-backlog.md",
                        "```",
                    ]
                ),
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["gkp_backlog_import_safety"], [finding.rule for finding in failed])

    def test_next_action_queue_json_status_index_mismatch_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-next-action-queue.json"
            path.write_text(
                json.dumps(
                    {
                        "counts": {"ready": 2, "blocked": 0, "done": 0},
                        "action_ids_by_status": {
                            "ready": ["approve-asr-patch", "wrong-id"],
                            "blocked": [],
                            "done": [],
                        },
                        "actions": [
                            {"id": "approve-asr-patch", "status": "ready"},
                        ],
                    }
                ),
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["next_action_queue_json_status_index"], [finding.rule for finding in failed])
            self.assertIn("counts.ready", failed[0].detail)
            self.assertIn("wrong-id", failed[0].detail)

    def test_next_action_queue_json_status_index_passes_when_consistent(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-next-action-queue.json"
            path.write_text(
                json.dumps(
                    {
                        "counts": {"ready": 1, "blocked": 1, "done": 1},
                        "action_ids_by_status": {
                            "ready": ["approve-asr-patch"],
                            "blocked": ["apply-approved-asr-patch"],
                            "done": ["rerun-device-lifecycle-row"],
                        },
                        "actions": [
                            {"id": "approve-asr-patch", "status": "ready"},
                            {"id": "apply-approved-asr-patch", "status": "blocked"},
                            {"id": "rerun-device-lifecycle-row", "status": "done"},
                        ],
                    }
                ),
                encoding="utf-8",
            )

            findings = module.audit_paths((path,))

            self.assertTrue(all(finding.status == "pass" for finding in findings), findings)
            self.assertTrue(
                any(finding.rule == "next_action_queue_json_status_index" for finding in findings),
                findings,
            )

    def test_manual_gate_intake_json_status_index_mismatch_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            queue = tmp_path / "m18-next-action-queue.json"
            intake = tmp_path / "m18-manual-gate-intake.json"
            queue.write_text(
                json.dumps(
                    {
                        "counts": {"ready": 1, "blocked": 0, "done": 0},
                        "action_ids_by_status": {
                            "ready": ["run-screen-translation-matrix"],
                            "blocked": [],
                            "done": [],
                        },
                        "actions": [
                            {"id": "run-screen-translation-matrix", "status": "ready"},
                        ],
                    }
                ),
                encoding="utf-8",
            )
            intake.write_text(
                json.dumps(
                    {
                        "counts": {"ready": 2},
                        "assets_edited_by_report": False,
                        "sections": [
                            {
                                "id": "screen-translation-manual-results",
                                "status": "ready",
                                "command_templates": [
                                    "python3 scripts/screen_translation_matrix_update.py "
                                    "--cases scripts/screen_translation_eval_cases.tsv "
                                    "--case-id ff6_dialogue --result \"Fail: numeric_corruption\" "
                                    "--output /tmp/preview.md"
                                ],
                            },
                        ],
                    }
                ),
                encoding="utf-8",
            )

            findings = module.audit_manual_gate_intake_json_status_index(intake, intake.read_text(encoding="utf-8"))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["manual_gate_intake_json_status_index"], [finding.rule for finding in failed])
            self.assertIn("counts.ready", failed[0].detail)

    def test_manual_gate_intake_json_ready_frontier_mismatch_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            queue = tmp_path / "m18-next-action-queue.json"
            intake = tmp_path / "m18-manual-gate-intake.json"
            queue.write_text(
                json.dumps(
                    {
                        "counts": {"ready": 1, "blocked": 0, "done": 0},
                        "action_ids_by_status": {
                            "ready": ["run-screen-translation-matrix"],
                            "blocked": [],
                            "done": [],
                        },
                        "actions": [
                            {"id": "run-screen-translation-matrix", "status": "ready"},
                        ],
                    }
                ),
                encoding="utf-8",
            )
            intake.write_text(
                json.dumps(
                    {
                        "counts": {"ready": 1},
                        "assets_edited_by_report": False,
                        "sections": [
                            {
                                "id": "content-rights-human-review",
                                "status": "ready",
                                "command_templates": [
                                    "python3 scripts/m18_manual_gate_receipt_check.py "
                                    "--output docs/qa-feedback/m18-manual-gate-receipt-check.md"
                                ],
                            },
                        ],
                    }
                ),
                encoding="utf-8",
            )

            findings = module.audit_manual_gate_intake_json_status_index(intake, intake.read_text(encoding="utf-8"))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["manual_gate_intake_json_status_index"], [finding.rule for finding in failed])
            self.assertIn("screen-translation-manual-results", failed[0].detail)

    def test_manual_gate_intake_json_direct_screen_apply_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            queue = tmp_path / "m18-next-action-queue.json"
            intake = tmp_path / "m18-manual-gate-intake.json"
            queue.write_text(
                json.dumps(
                    {
                        "counts": {"ready": 1, "blocked": 0, "done": 0},
                        "action_ids_by_status": {
                            "ready": ["run-screen-translation-matrix"],
                            "blocked": [],
                            "done": [],
                        },
                        "actions": [
                            {"id": "run-screen-translation-matrix", "status": "ready"},
                        ],
                    }
                ),
                encoding="utf-8",
            )
            intake.write_text(
                json.dumps(
                    {
                        "counts": {"ready": 1},
                        "assets_edited_by_report": False,
                        "sections": [
                            {
                                "id": "screen-translation-manual-results",
                                "status": "ready",
                                "command_templates": [
                                    "python3 scripts/screen_translation_matrix_update.py "
                                    "--cases scripts/screen_translation_eval_cases.tsv "
                                    "--case-id ff6_dialogue --result \"Fail: numeric_corruption\" "
                                    "--apply"
                                ],
                            },
                        ],
                    }
                ),
                encoding="utf-8",
            )

            findings = module.audit_manual_gate_intake_json_status_index(intake, intake.read_text(encoding="utf-8"))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["manual_gate_intake_json_status_index"], [finding.rule for finding in failed])
            self.assertIn("preview-only", failed[0].detail)

    def test_manual_gate_intake_json_status_index_passes_when_consistent(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            queue = tmp_path / "m18-next-action-queue.json"
            intake = tmp_path / "m18-manual-gate-intake.json"
            queue.write_text(
                json.dumps(
                    {
                        "counts": {"ready": 1, "blocked": 0, "done": 0},
                        "action_ids_by_status": {
                            "ready": ["run-screen-translation-matrix"],
                            "blocked": [],
                            "done": [],
                        },
                        "actions": [
                            {"id": "run-screen-translation-matrix", "status": "ready"},
                        ],
                    }
                ),
                encoding="utf-8",
            )
            intake.write_text(
                json.dumps(
                    {
                        "counts": {"ready": 1},
                        "assets_edited_by_report": False,
                        "sections": [
                            {
                                "id": "screen-translation-manual-results",
                                "status": "ready",
                                "command_templates": [
                                    "python3 scripts/screen_translation_matrix_update.py "
                                    "--cases scripts/screen_translation_eval_cases.tsv "
                                    "--case-id ff6_dialogue --result \"Fail: numeric_corruption\" "
                                    "--output /tmp/preview.md"
                                ],
                            },
                        ],
                    }
                ),
                encoding="utf-8",
            )

            findings = module.audit_manual_gate_intake_json_status_index(intake, intake.read_text(encoding="utf-8"))

            self.assertTrue(all(finding.status == "pass" for finding in findings), findings)
            self.assertTrue(
                any(finding.rule == "manual_gate_intake_json_status_index" for finding in findings),
                findings,
            )

    def test_manual_gate_receipt_check_json_status_index_mismatch_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-manual-gate-receipt-check.json"
            path.write_text(
                json.dumps(
                    {
                        "status": "open",
                        "receipt_present": False,
                        "counts": {"pass": 0, "open": 2, "fail": 0},
                        "assets_edited_by_report": False,
                        "items": [
                            {"id": "asr-patch-approval", "status": "open", "detail": "receipt missing"},
                        ],
                    }
                ),
                encoding="utf-8",
            )

            findings = module.audit_manual_gate_receipt_check_json_status_index(path, path.read_text(encoding="utf-8"))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["manual_gate_receipt_check_json_status_index"], [finding.rule for finding in failed])
            self.assertIn("counts.open", failed[0].detail)

    def test_manual_gate_receipt_check_json_status_index_passes_when_consistent(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-manual-gate-receipt-check.json"
            path.write_text(
                json.dumps(
                    {
                        "status": "open",
                        "receipt_present": False,
                        "counts": {"pass": 0, "open": 1, "fail": 0},
                        "assets_edited_by_report": False,
                        "items": [
                            {"id": "asr-patch-approval", "status": "open", "detail": "receipt missing"},
                        ],
                    }
                ),
                encoding="utf-8",
            )

            findings = module.audit_manual_gate_receipt_check_json_status_index(path, path.read_text(encoding="utf-8"))

            self.assertTrue(all(finding.status == "pass" for finding in findings), findings)
            self.assertTrue(
                any(finding.rule == "manual_gate_receipt_check_json_status_index" for finding in findings),
                findings,
            )

    def test_manual_gate_receipt_plan_json_status_index_mismatch_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-manual-gate-receipt-plan.json"
            path.write_text(
                json.dumps(
                    {
                        "status": "open",
                        "receipt_check_status": "open",
                        "receipt_present": False,
                        "counts": {"ready": 0, "open": 2, "blocked": 0},
                        "commands_executed_by_planner": False,
                        "assets_edited_by_planner": False,
                        "actions": [
                            {"id": "asr-patch-approval", "status": "open", "detail": "receipt missing", "command": ""},
                        ],
                    }
                ),
                encoding="utf-8",
            )

            findings = module.audit_manual_gate_receipt_plan_json_status_index(path, path.read_text(encoding="utf-8"))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["manual_gate_receipt_plan_json_status_index"], [finding.rule for finding in failed])
            self.assertIn("counts.open", failed[0].detail)

    def test_manual_gate_receipt_plan_json_ready_action_without_command_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-manual-gate-receipt-plan.json"
            path.write_text(
                json.dumps(
                    {
                        "status": "pass",
                        "receipt_check_status": "pass",
                        "receipt_present": True,
                        "counts": {"ready": 1, "open": 0, "blocked": 0},
                        "commands_executed_by_planner": False,
                        "assets_edited_by_planner": False,
                        "actions": [
                            {"id": "apply-approved-asr-patch", "status": "ready", "detail": "apply patch", "command": ""},
                        ],
                    }
                ),
                encoding="utf-8",
            )

            findings = module.audit_manual_gate_receipt_plan_json_status_index(path, path.read_text(encoding="utf-8"))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["manual_gate_receipt_plan_json_status_index"], [finding.rule for finding in failed])
            self.assertIn("ready action missing command", failed[0].detail)

    def test_manual_gate_receipt_plan_json_status_index_passes_when_consistent(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-manual-gate-receipt-plan.json"
            path.write_text(
                json.dumps(
                    {
                        "status": "pass",
                        "receipt_check_status": "pass",
                        "receipt_present": True,
                        "counts": {"ready": 1, "open": 0, "blocked": 0},
                        "commands_executed_by_planner": False,
                        "assets_edited_by_planner": False,
                        "actions": [
                            {
                                "id": "apply-approved-asr-patch",
                                "status": "ready",
                                "detail": "apply patch",
                                "command": "python3 scripts/gkp_patch_apply_review_packet.py --packet docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md --apply --approval 'I approve gkp patch review packet 20260601 hotkey voice' --strict",
                            },
                        ],
                    }
                ),
                encoding="utf-8",
            )

            findings = module.audit_manual_gate_receipt_plan_json_status_index(path, path.read_text(encoding="utf-8"))

            self.assertTrue(all(finding.status == "pass" for finding in findings), findings)
            self.assertTrue(
                any(finding.rule == "manual_gate_receipt_plan_json_status_index" for finding in findings),
                findings,
            )

    def test_completion_audit_json_status_index_mismatch_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-completion-audit.json"
            path.write_text(
                json.dumps(
                    {
                        "overall_status": "open",
                        "is_complete": False,
                        "counts": {"pass": 1, "open": 2, "missing": 0, "fail": 0},
                        "assets_edited_by_report": False,
                        "requirements": [
                            {
                                "id": "plan-checkboxes",
                                "requirement": "Plan checkboxes are closed.",
                                "status": "open",
                                "evidence": "plan.md",
                                "detail": "unchecked=1",
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )

            findings = module.audit_completion_audit_json_status_index(path, path.read_text(encoding="utf-8"))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["completion_audit_json_status_index"], [finding.rule for finding in failed])
            self.assertIn("counts.open", failed[0].detail)

    def test_completion_audit_json_is_complete_drift_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-completion-audit.json"
            path.write_text(
                json.dumps(
                    {
                        "overall_status": "open",
                        "is_complete": True,
                        "counts": {"pass": 0, "open": 1, "missing": 0, "fail": 0},
                        "assets_edited_by_report": False,
                        "requirements": [
                            {
                                "id": "plan-checkboxes",
                                "requirement": "Plan checkboxes are closed.",
                                "status": "open",
                                "evidence": "plan.md",
                                "detail": "unchecked=1",
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )

            findings = module.audit_completion_audit_json_status_index(path, path.read_text(encoding="utf-8"))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["completion_audit_json_status_index"], [finding.rule for finding in failed])
            self.assertIn("is_complete", failed[0].detail)

    def test_completion_audit_json_status_index_passes_when_consistent(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-completion-audit.json"
            path.write_text(
                json.dumps(
                    {
                        "overall_status": "pass",
                        "is_complete": True,
                        "counts": {"pass": 1, "open": 0, "missing": 0, "fail": 0},
                        "assets_edited_by_report": False,
                        "requirements": [
                            {
                                "id": "plan-checkboxes",
                                "requirement": "Plan checkboxes are closed.",
                                "status": "pass",
                                "evidence": "plan.md",
                                "detail": "unchecked=0",
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )

            findings = module.audit_completion_audit_json_status_index(path, path.read_text(encoding="utf-8"))

            self.assertTrue(all(finding.status == "pass" for finding in findings), findings)
            self.assertTrue(
                any(finding.rule == "completion_audit_json_status_index" for finding in findings),
                findings,
            )

    def test_remaining_gate_handoff_json_status_index_mismatch_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-remaining-gate-handoff.json"
            path.write_text(
                json.dumps(
                    {
                        "status": "open",
                        "is_green": False,
                        "counts": {
                            "plan_unchecked": 1,
                            "aggregate_open": 1,
                            "open_gates": 2,
                            "release_open": 0,
                        },
                        "gates": {
                            "asr_patch_voice_replay": {
                                "patch_rows": 4,
                                "voice_cases": 4,
                                "case_filter": "sf2_vigor_ball_observed",
                                "summary": "patch_rows=4; voice_cases=4",
                            },
                            "release_checklist": {"open": 0},
                        },
                        "open_gates": ["one"],
                        "release_open_items": [],
                        "commands": [],
                        "contract": {},
                        "assets_edited_by_handoff": False,
                    }
                ),
                encoding="utf-8",
            )

            findings = module.audit_remaining_gate_handoff_json_status_index(path, path.read_text(encoding="utf-8"))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["remaining_gate_handoff_json_status_index"], [finding.rule for finding in failed])
            self.assertIn("removed_from_m18_scope missing manual_asr_approval", failed[0].detail)
            self.assertIn("gates.hotkey_voice_matrix", failed[0].detail)
            self.assertIn("commands missing", failed[0].detail)

    def test_remaining_gate_handoff_json_status_index_passes_when_consistent(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-remaining-gate-handoff.json"
            path.write_text(
                json.dumps(
                    {
                        "status": "open",
                        "is_green": False,
                        "counts": {
                            "plan_unchecked": 1,
                            "aggregate_open": 1,
                            "open_gates": 1,
                        },
                        "gates": {
                            "hotkey_voice_matrix": {
                                "total": 7,
                                "pass": 5,
                                "fail": 2,
                                "blocked": 0,
                                "not_run": 0,
                                "missing": 0,
                                "categories": "asr_variant=1, source_mismatch=1",
                            },
                        },
                        "open_gates": ["voice row"],
                        "removed_from_m18_scope": [
                            "manual_asr_approval",
                            "five_row_screen_translation_manual_matrix",
                            "human_content_rights_confirmation",
                        ],
                        "commands": [
                            {
                                "id": "hotkey_voice_matrix_report",
                                "command": "python3 scripts/hotkey_voice_matrix_report.py --output docs/qa-feedback/hotkey-voice-matrix-report.md",
                            },
                            {
                                "id": "manual_notes_backlog_preview",
                                "command": "python3 scripts/gkp_gap_backlog.py --merge-existing-backlog docs/qa-feedback/gkp-quality-backlog.md",
                            },
                            {
                                "id": "offline_quality_gate",
                                "command": "./scripts/m18_offline_quality_gate.sh",
                            },
                            {
                                "id": "m18_completion_audit_strict",
                                "command": "python3 scripts/m18_completion_audit.py --strict",
                            },
                        ],
                        "contract": {
                            "assets_edited_by_handoff": False,
                            "manual_asr_approval_required": False,
                            "screen_translation_manual_matrix_required": False,
                            "content_rights_human_confirmation_required": False,
                            "merge_existing_backlog": True,
                            "strict_completion_required": True,
                            "final_quality_gate": "./scripts/m18_offline_quality_gate.sh",
                        },
                        "assets_edited_by_handoff": False,
                    }
                ),
                encoding="utf-8",
            )

            findings = module.audit_remaining_gate_handoff_json_status_index(path, path.read_text(encoding="utf-8"))

            self.assertTrue(all(finding.status == "pass" for finding in findings), findings)
            self.assertTrue(
                any(finding.rule == "remaining_gate_handoff_json_status_index" for finding in findings),
                findings,
            )

    def test_quality_loop_handoff_json_status_index_mismatch_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-quality-loop-handoff.json"
            path.write_text(
                json.dumps(
                    {
                        "loop_status": "open_until_current_rc_gates_close",
                        "overall_status": "open",
                        "paths": {
                            "gate_status": "docs/qa-feedback/m18-gate-status.json",
                            "action_queue": "docs/qa-feedback/m18-next-action-queue.json",
                            "backlog": "docs/qa-feedback/gkp-quality-backlog.md",
                            "manual_notes_template": "docs/qa-feedback/gkp-manual-notes-template.tsv",
                        },
                        "open_areas": ["Hotkey voice matrix"],
                        "action_ids_by_status": {
                            "ready": ["replay-full-voice-matrix"],
                            "blocked": [],
                            "done": [],
                        },
                        "counts": {"open_areas": 1, "ready": 2, "blocked": 0, "done": 0},
                        "current_loop_state": {
                            "gkp_backlog": "items=1",
                            "hotkey_voice_matrix": "pass=4; fail=3",
                        },
                        "preview_backlog_commands": [],
                        "fix_acceptance_rules": [],
                        "contract": {"preview_first_backlog_imports": False},
                        "assets_edited_by_handoff": False,
                    }
                ),
                encoding="utf-8",
            )

            findings = module.audit_quality_loop_handoff_json_status_index(path, path.read_text(encoding="utf-8"))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["quality_loop_handoff_json_status_index"], [finding.rule for finding in failed])
            self.assertIn("counts.ready", failed[0].detail)
            self.assertIn("preview_backlog_commands missing", failed[0].detail)

    def test_quality_loop_handoff_json_status_index_passes_when_consistent(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-quality-loop-handoff.json"
            backlog = "docs/qa-feedback/gkp-quality-backlog.md"
            command = (
                "python3 scripts/gkp_gap_backlog.py \\\n"
                "  --input build/source.json \\\n"
                f"  --merge-existing-backlog {backlog} \\\n"
                "  --output build/preview.md"
            )
            path.write_text(
                json.dumps(
                    {
                        "loop_status": "open_until_current_rc_gates_close",
                        "overall_status": "open",
                        "paths": {
                            "gate_status": "docs/qa-feedback/m18-gate-status.json",
                            "action_queue": "docs/qa-feedback/m18-next-action-queue.json",
                            "backlog": backlog,
                            "manual_notes_template": "docs/qa-feedback/gkp-manual-notes-template.tsv",
                        },
                        "open_areas": ["Hotkey voice matrix"],
                        "action_ids_by_status": {
                            "ready": ["replay-full-voice-matrix"],
                            "blocked": ["final-m18-offline-gate"],
                            "done": ["rerun-device-lifecycle-row"],
                        },
                        "counts": {"open_areas": 1, "ready": 1, "blocked": 1, "done": 1},
                        "current_loop_state": {
                            "gkp_backlog": "items=1",
                            "hotkey_voice_matrix": "pass=4; fail=3",
                        },
                        "preview_backlog_commands": [
                            {
                                "id": "latest_request",
                                "merge_existing_backlog": backlog,
                                "command": command,
                            },
                            {
                                "id": "voice_qa",
                                "merge_existing_backlog": backlog,
                                "command": command,
                            },
                            {
                                "id": "manual_notes_template",
                                "command": "python3 scripts/gkp_gap_backlog.py --manual-notes-template-output docs/qa-feedback/gkp-manual-notes-template.tsv",
                            },
                            {
                                "id": "manual_notes_preview",
                                "merge_existing_backlog": backlog,
                                "command": command,
                            },
                            {
                                "id": "manual_notes_apply_after_review",
                                "merge_existing_backlog": backlog,
                                "command": command,
                            },
                        ],
                        "fix_acceptance_rules": [
                            "Every accepted GKP fix needs source ids and a regression target.",
                            "Alias or ASR fixes add at least one golden.",
                            "Voice-originated fixes require real-device replay after local regression.",
                            "Do not add new game content until the current six bundled packs complete one full green RC pass.",
                        ],
                        "contract": {
                            "preview_first_backlog_imports": True,
                            "merge_existing_backlog": True,
                            "latest_request_preview": True,
                            "voice_qa_preview": True,
                            "manual_notes_preview": True,
                            "fix_acceptance_rules": True,
                            "voice_replay_required": True,
                            "no_new_games_until_green_rc": True,
                        },
                        "assets_edited_by_handoff": False,
                    }
                ),
                encoding="utf-8",
            )

            findings = module.audit_quality_loop_handoff_json_status_index(path, path.read_text(encoding="utf-8"))

            self.assertTrue(all(finding.status == "pass" for finding in findings), findings)
            self.assertTrue(
                any(finding.rule == "quality_loop_handoff_json_status_index" for finding in findings),
                findings,
            )

    def test_plan_execution_audit_json_status_index_mismatch_fails(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-plan-execution-audit.json"
            path.write_text(
                json.dumps(
                    {
                        "status": "open",
                        "plan_checked": 1,
                        "plan_unchecked": 1,
                        "counts": {
                            "plan_checked": 1,
                            "plan_unchecked": 2,
                            "aggregate_pass": 1,
                            "aggregate_open": 1,
                            "open_gates": 2,
                        },
                        "assets_edited_by_report": False,
                        "open_blocker_categories": {"screen_translation": 1},
                        "tasks": [
                            {
                                "title": "Task",
                                "checked": 1,
                                "unchecked": 1,
                                "open_items": [
                                    {"text": "Fill screen matrix", "category": "screen_translation"}
                                ],
                            }
                        ],
                        "aggregate_status": [
                            {"area": "GKP coverage", "status": "pass", "detail": "ok"},
                            {"area": "Screen translation matrix", "status": "open", "detail": "not_run=5"},
                        ],
                        "open_gates": [
                            {"kind": "plan_item", "category": "screen_translation", "task": "Task", "text": "Fill screen matrix"},
                            {
                                "kind": "aggregate_status",
                                "category": "screen_translation",
                                "area": "Screen translation matrix",
                                "status": "open",
                                "detail": "not_run=5",
                            },
                        ],
                    }
                ),
                encoding="utf-8",
            )

            findings = module.audit_plan_execution_audit_json_status_index(path, path.read_text(encoding="utf-8"))

            failed = [finding for finding in findings if finding.status == "fail"]
            self.assertEqual(["plan_execution_audit_json_status_index"], [finding.rule for finding in failed])
            self.assertIn("counts.plan_unchecked", failed[0].detail)

    def test_plan_execution_audit_json_status_index_passes_when_consistent(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "m18-plan-execution-audit.json"
            path.write_text(
                json.dumps(
                    {
                        "status": "open",
                        "plan_checked": 1,
                        "plan_unchecked": 1,
                        "counts": {
                            "plan_checked": 1,
                            "plan_unchecked": 1,
                            "aggregate_pass": 1,
                            "aggregate_open": 1,
                            "open_gates": 2,
                        },
                        "assets_edited_by_report": False,
                        "open_blocker_categories": {"screen_translation": 2},
                        "tasks": [
                            {
                                "title": "Task",
                                "checked": 1,
                                "unchecked": 1,
                                "open_items": [
                                    {"text": "Fill screen matrix", "category": "screen_translation"}
                                ],
                            }
                        ],
                        "aggregate_status": [
                            {"area": "GKP coverage", "status": "pass", "detail": "ok"},
                            {"area": "Screen translation matrix", "status": "open", "detail": "not_run=5"},
                        ],
                        "open_gates": [
                            {"kind": "plan_item", "category": "screen_translation", "task": "Task", "text": "Fill screen matrix"},
                            {
                                "kind": "aggregate_status",
                                "category": "screen_translation",
                                "area": "Screen translation matrix",
                                "status": "open",
                                "detail": "not_run=5",
                            },
                        ],
                    }
                ),
                encoding="utf-8",
            )

            findings = module.audit_plan_execution_audit_json_status_index(path, path.read_text(encoding="utf-8"))

            self.assertTrue(all(finding.status == "pass" for finding in findings), findings)
            self.assertTrue(
                any(finding.rule == "plan_execution_audit_json_status_index" for finding in findings),
                findings,
            )

    def test_main_writes_report_and_strict_fails_for_bad_input(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            bad = tmp_path / "bad.md"
            output = tmp_path / "audit.md"
            bad.write_text(
                "```bash\n"
                "python3 scripts/screen_translation_matrix_update.py --cases scripts/screen_translation_eval_cases.tsv --case-id x --result \"Pass: evidence build/rc-device-evidence/<timestamp>\" --apply\n"
                "```\n",
                encoding="utf-8",
            )

            old_argv = sys.argv
            try:
                sys.argv = [
                    "m18_command_contract_audit.py",
                    "--input",
                    str(bad),
                    "--output",
                    str(output),
                    "--strict",
                ]
                with redirect_stdout(io.StringIO()):
                    result = module.main()
            finally:
                sys.argv = old_argv

            self.assertEqual(1, result)
            self.assertTrue(output.is_file())
            self.assertIn("placeholder_screen_translation_apply", output.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
