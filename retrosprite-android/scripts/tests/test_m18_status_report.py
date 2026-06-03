import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/m18_status_report.py"


def load_module():
    spec = importlib.util.spec_from_file_location("m18_status_report", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class M18StatusReportTest(unittest.TestCase):

    def test_summarizes_current_reports_as_open_but_gkp_coverage_pass(self):
        module = load_module()

        rows = [
            module.summarize_gkp_eval(ROOT / "docs/qa-feedback/m18-eval-report.md"),
            module.summarize_gap_backlog(ROOT / "docs/qa-feedback/gkp-quality-backlog.md"),
            module.summarize_patch_proposal_audit(ROOT / "docs/qa-feedback/gkp-patch-proposal-audit.md"),
            module.summarize_patch_review_packet(ROOT / "docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md"),
            module.summarize_patch_apply_dry_run(
                ROOT / "docs/qa-feedback/gkp-patch-apply-dry-run-20260601-hotkey-voice.md"
            ),
            module.summarize_gkp_asset_mutation_guard(ROOT / "docs/qa-feedback/gkp-asset-mutation-guard.md"),
            module.summarize_asr_voice_handoff(ROOT / "docs/qa-feedback/gkp-asr-patch-voice-replay-handoff.md"),
            module.summarize_hotkey_voice_matrix(ROOT / "docs/qa-feedback/hotkey-voice-matrix-report.md"),
            module.summarize_command_contract_audit(ROOT / "docs/qa-feedback/m18-command-contract-audit.md"),
            module.summarize_quality_loop_handoff(ROOT / "docs/qa-feedback/m18-quality-loop-handoff.md"),
        ]
        by_area = {row.area: row for row in rows}

        self.assertEqual("pass", by_area["GKP coverage"].status)
        self.assertEqual("pass", by_area["GKP backlog"].status)
        self.assertIn("items=", by_area["GKP backlog"].detail)
        self.assertIn("triage_items=10", by_area["GKP backlog"].detail)
        self.assertIn("review_packet_rows=7", by_area["GKP backlog"].detail)
        self.assertIn("triage_open=0", by_area["GKP backlog"].detail)
        self.assertIn("manual_asr_approval_required=no", by_area["GKP backlog"].detail)
        self.assertIn("device_rerun_passed=1", by_area["GKP backlog"].detail)
        self.assertIn("policy_golden_existing=2", by_area["GKP backlog"].detail)
        self.assertIn("asr_patch_applied=7", by_area["GKP backlog"].detail)
        self.assertIn("raw_tags=", by_area["GKP backlog"].detail)
        self.assertRegex(by_area["GKP backlog"].detail, r"(voice_lifecycle_gap|asr_variant)")
        self.assertEqual("pass", by_area["GKP patch proposals"].status)
        self.assertIn("pass=7", by_area["GKP patch proposals"].detail)
        self.assertEqual("pass", by_area["GKP patch review packet"].status)
        self.assertIn("rows=7", by_area["GKP patch review packet"].detail)
        self.assertIn("applied=7", by_area["GKP patch review packet"].detail)
        self.assertIn("assets_edited=no", by_area["GKP patch review packet"].detail)
        self.assertEqual("pass", by_area["GKP patch apply dry-run"].status)
        self.assertIn("rows=7", by_area["GKP patch apply dry-run"].detail)
        self.assertIn("mode=dry_run", by_area["GKP patch apply dry-run"].detail)
        self.assertIn("assets_edited=no", by_area["GKP patch apply dry-run"].detail)
        self.assertEqual("pass", by_area["GKP asset mutation guard"].status)
        self.assertRegex(by_area["GKP asset mutation guard"].detail, r"mode=(clean|approved_patch)")
        self.assertIn("dirty=10", by_area["GKP asset mutation guard"].detail)
        self.assertIn("unexpected=0", by_area["GKP asset mutation guard"].detail)
        self.assertEqual("pass", by_area["GKP ASR voice replay handoff"].status)
        self.assertIn("patch_rows=7", by_area["GKP ASR voice replay handoff"].detail)
        self.assertIn("voice_cases=7", by_area["GKP ASR voice replay handoff"].detail)
        self.assertEqual("pass", by_area["Hotkey voice matrix"].status)
        self.assertIn("total=7", by_area["Hotkey voice matrix"].detail)
        self.assertIn("pass=4", by_area["Hotkey voice matrix"].detail)
        self.assertIn("fail=3", by_area["Hotkey voice matrix"].detail)
        self.assertIn("gate=observational", by_area["Hotkey voice matrix"].detail)
        self.assertIn("categories=asr_variant=1, source_mismatch=2", by_area["Hotkey voice matrix"].detail)
        self.assertNotIn("Screen translation manual packet", by_area)
        self.assertNotIn("Screen translation matrix", by_area)
        self.assertNotIn("GKP content rights packet", by_area)
        self.assertNotIn("Release checklist guard", by_area)
        self.assertNotIn("Release checklist", by_area)
        self.assertEqual("pass", by_area["Command contract audit"].status)
        self.assertIn("fail=0", by_area["Command contract audit"].detail)
        self.assertIn("missing=0", by_area["Command contract audit"].detail)
        self.assertEqual("pass", by_area["M18 quality loop handoff"].status)
        self.assertIn("loop_status=ready_for_ongoing_rc_cycle", by_area["M18 quality loop handoff"].detail)
        self.assertIn("open_areas=none", by_area["M18 quality loop handoff"].detail)
        self.assertIn("missing_fragments=0", by_area["M18 quality loop handoff"].detail)

    def test_quality_loop_handoff_prefers_sibling_json(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            handoff = tmp_path / "m18-quality-loop-handoff.md"
            handoff_json = tmp_path / "m18-quality-loop-handoff.json"
            handoff.write_text("- Loop status: `unknown`\n", encoding="utf-8")
            handoff_json.write_text(
                json.dumps(
                    {
                        "loop_status": "open_until_current_rc_gates_close",
                        "overall_status": "open",
                        "paths": {
                            "gate_status": "gate.json",
                            "action_queue": "queue.json",
                            "backlog": "docs/qa-feedback/gkp-quality-backlog.md",
                            "manual_notes_template": "docs/qa-feedback/gkp-manual-notes-template.tsv",
                        },
                        "open_areas": ["Hotkey voice matrix"],
                        "action_ids_by_status": {
                            "ready": ["replay-full-voice-matrix"],
                            "blocked": ["final-m18-offline-gate"],
                            "done": ["rerun-device-lifecycle-row"],
                        },
                        "current_loop_state": {
                            "gkp_backlog": "items=1",
                            "hotkey_voice_matrix": "pass=4; fail=3",
                        },
                        "preview_backlog_commands": [
                            {
                                "id": "latest_request",
                                "merge_existing_backlog": "docs/qa-feedback/gkp-quality-backlog.md",
                            },
                            {
                                "id": "voice_qa",
                                "merge_existing_backlog": "docs/qa-feedback/gkp-quality-backlog.md",
                            },
                            {"id": "manual_notes_template"},
                            {
                                "id": "manual_notes_preview",
                                "merge_existing_backlog": "docs/qa-feedback/gkp-quality-backlog.md",
                            },
                            {
                                "id": "manual_notes_apply_after_review",
                                "merge_existing_backlog": "docs/qa-feedback/gkp-quality-backlog.md",
                            },
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

            row = module.summarize_quality_loop_handoff(handoff)

            self.assertEqual("pass", row.status)
            self.assertEqual("m18-quality-loop-handoff.json", Path(row.evidence).name)
            self.assertIn("ready=replay-full-voice-matrix", row.detail)
            self.assertIn("missing_fragments=0", row.detail)

    def test_screen_translation_status_stays_open_when_pass_notes_are_missing(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            report = Path(tmp) / "screen.md"
            report.write_text(
                "\n".join(
                    [
                        "# M18 Screen Translation Eval Report",
                        "",
                        "- Total: 1",
                        "- Status: pass=1, fail=0, blocked=0, not_run=0, missing=0",
                        "- Note issues: 1",
                    ]
                ),
                encoding="utf-8",
            )

            row = module.summarize_screen_translation(report)

            self.assertEqual("open", row.status)
            self.assertIn("note_issues=1", row.detail)

    def test_backlog_summary_uses_triage_to_separate_pending_and_covered_rows(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            backlog = tmp_path / "backlog.md"
            triage = tmp_path / "triage.md"
            backlog.write_text(
                "\n".join(
                    [
                        "# M18 GKP Quality Backlog",
                        "",
                        "- Items: 3",
                        "",
                        "| Label | Question | Tags | Suggested Area | Regression Target | Details | Source |",
                        "|---|---|---|---|---|---|---|",
                        "| `label` | question | `asr_variant` | area | target | details | `source` |",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )
            triage.write_text(
                "\n".join(
                    [
                        "# M18 GKP Backlog Triage Report",
                        "",
                        "- Items: 3",
                        "- Categories: asr_patch_ready=1, device_rerun_passed=1, policy_golden_existing=1",
                        "- Status: covered_by_device_rerun=1, covered_by_existing_golden=1, covered_by_review_packet=1",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            row = module.summarize_gap_backlog(backlog, triage)

            self.assertEqual("pass", row.status)
            self.assertIn("triage_status=covered_by_device_rerun=1, covered_by_existing_golden=1, covered_by_review_packet=1", row.detail)
            self.assertIn("review_packet_rows=1", row.detail)
            self.assertIn("triage_open=0", row.detail)

    def test_backlog_summary_passes_when_only_covered_triage_rows_remain(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            backlog = tmp_path / "backlog.md"
            triage = tmp_path / "triage.md"
            backlog.write_text("- Items: 2\n", encoding="utf-8")
            triage.write_text(
                "\n".join(
                    [
                        "- Items: 2",
                        "- Categories: device_rerun_passed=1, policy_golden_existing=1",
                        "- Status: covered_by_device_rerun=1, covered_by_existing_golden=1",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            row = module.summarize_gap_backlog(backlog, triage)

            self.assertEqual("pass", row.status)
            self.assertIn("review_packet_rows=0", row.detail)
            self.assertIn("triage_open=0", row.detail)

    def test_render_markdown_lists_open_work(self):
        module = load_module()
        rows = [
            module.StatusRow("GKP coverage", "pass", "gkp.md", "packs=6"),
            module.StatusRow("GKP backlog", "open", "backlog.md", "items=1"),
        ]

        markdown = module.render_markdown(rows)

        self.assertIn("# M18 Eval Lab Status Report", markdown)
        self.assertIn("| GKP coverage | `pass` |", markdown)
        self.assertIn("`GKP backlog` is `open`", markdown)
        self.assertIn("## Next Actions", markdown)
        self.assertIn("manual ASR approval is no longer an M18 gate", markdown)

    def test_hotkey_voice_action_does_not_assume_seven_row_report(self):
        module = load_module()

        action = module.recommended_action(
            module.StatusRow(
                "Hotkey voice matrix",
                "open",
                "docs/qa-feedback/hotkey-voice-matrix-report.md",
                "total=3; pass=1; fail=2",
            )
        )

        self.assertIn("current playback report", action)
        self.assertNotIn("seven-row", action)

    def test_missing_files_are_reported_as_missing(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            missing = Path(tmp) / "missing.md"

            row = module.summarize_gkp_eval(missing)

            self.assertEqual("missing", row.status)
            self.assertIn("not found", row.detail)

    def test_quality_loop_handoff_requires_preview_first_imports_and_acceptance_rules(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            incomplete = Path(tmp) / "quality.md"
            incomplete.write_text(
                "\n".join(
                    [
                        "# M18 Quality Loop Handoff",
                        "",
                        "- Loop status: `open_until_current_rc_gates_close`",
                        "- Ready actions: replay-full-voice-matrix",
                        "- Blocked actions: final-m18-offline-gate",
                        "- Open areas: Hotkey voice matrix",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            row = module.summarize_quality_loop_handoff(incomplete)

            self.assertEqual("open", row.status)
            self.assertNotIn("missing_fragments=0", row.detail)

    def test_recommended_actions_cover_open_m18_areas(self):
        module = load_module()

        self.assertIn(
            "gkp-backlog-triage-report.md",
            module.recommended_action(module.StatusRow("GKP backlog", "open", "backlog.md", "items=1")),
        )
        self.assertIn(
            "policy_golden_existing",
            module.recommended_action(module.StatusRow("GKP backlog", "open", "backlog.md", "items=1")),
        )
        self.assertIn(
            "do not add duplicate policy goldens",
            module.recommended_action(module.StatusRow("GKP backlog", "open", "backlog.md", "items=1")),
        )
        self.assertIn(
            "manual ASR approval is no longer an M18 gate",
            module.recommended_action(module.StatusRow("GKP backlog", "open", "backlog.md", "items=1")),
        )
        self.assertIn(
            "hotkey-voice-matrix-report.md",
            module.recommended_action(module.StatusRow("Hotkey voice matrix", "open", "voice.md", "fail=3")),
        )
        self.assertIn(
            "gkp_asset_mutation_guard.py --strict",
            module.recommended_action(module.StatusRow("GKP asset mutation guard", "fail", "guard.md", "unexpected=1")),
        )
        self.assertIn(
            "m18_command_contract_audit.py --strict",
            module.recommended_action(module.StatusRow("Command contract audit", "fail", "audit.md", "fail=1")),
        )

    def test_patch_review_packet_fails_if_assets_were_edited(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            packet = Path(tmp) / "packet.md"
            packet.write_text(
                "\n".join(
                    [
                        "# Packet",
                        "- Assets edited: yes",
                        "- Rows: 1",
                        "- Status: ready=1, blocked=0",
                    ]
                ),
                encoding="utf-8",
            )

            row = module.summarize_patch_review_packet(packet)

            self.assertEqual("fail", row.status)
            self.assertIn("assets_edited=yes", row.detail)

    def test_patch_apply_dry_run_fails_if_assets_were_edited(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            report = Path(tmp) / "apply.md"
            report.write_text(
                "\n".join(
                    [
                        "# Apply",
                        "- Mode: `apply`",
                        "- Assets edited: 2",
                        "- Rows: 1",
                        "- Status: ready=1, blocked=0",
                    ]
                ),
                encoding="utf-8",
            )

            row = module.summarize_patch_apply_dry_run(report)

            self.assertEqual("fail", row.status)
            self.assertIn("mode=apply", row.detail)
            self.assertIn("assets_edited=yes", row.detail)

    def test_gkp_asset_mutation_guard_fails_for_unexpected_edits(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            report = Path(tmp) / "guard.md"
            report.write_text(
                "\n".join(
                    [
                        "# M18 GKP Asset Mutation Guard",
                        "",
                        "- Guard status: `fail`",
                        "- Mode: `unexpected_dirty`",
                        "- Dirty GKP assets: 1",
                        "- Expected patch assets: 6",
                        "- Unexpected dirty assets: 1",
                        "- Apply report present: `yes`",
                        "- Apply report mode: `apply`",
                    ]
                ),
                encoding="utf-8",
            )

            row = module.summarize_gkp_asset_mutation_guard(report)

            self.assertEqual("fail", row.status)
            self.assertIn("unexpected=1", row.detail)

    def test_hotkey_voice_matrix_is_observational_after_real_run_exists(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            report = Path(tmp) / "voice.md"
            report.write_text(
                "\n".join(
                    [
                        "# M18 Hotkey Voice Matrix Report",
                        "",
                        "- Results: `build/hotkey-voice-qa/example/results.tsv`",
                        "- Total: 2",
                        "- Status: pass=1, fail=1, blocked=0, not_run=0, missing=0",
                        "- Failure categories: asr_variant=1",
                    ]
                ),
                encoding="utf-8",
            )

            row = module.summarize_hotkey_voice_matrix(report)

            self.assertEqual("pass", row.status)
            self.assertIn("gate=observational", row.detail)
            self.assertIn("categories=asr_variant=1", row.detail)

            report.write_text(
                "\n".join(
                    [
                        "# M18 Hotkey Voice Matrix Report",
                        "",
                        "- Results: `build/hotkey-voice-qa/example/results.tsv`",
                        "- Total: 2",
                        "- Status: pass=2, fail=0, blocked=0, not_run=0, missing=0",
                        "- Failure categories: none",
                    ]
                ),
                encoding="utf-8",
            )

            row = module.summarize_hotkey_voice_matrix(report)

            self.assertEqual("pass", row.status)
            self.assertIn("strict_pass=yes", row.detail)

    def test_release_checklist_guard_fails_when_unsafe_items_exist(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            report = Path(tmp) / "guard.md"
            report.write_text(
                "\n".join(
                    [
                        "# M18 Release Checklist Guard",
                        "",
                        "- Guard status: `fail`",
                        "- Closure status: `fail`",
                        "- Ready items: 0/3",
                        "- Unsafe checked items: 1",
                        "- Apply allowed: `no`",
                    ]
                ),
                encoding="utf-8",
            )

            row = module.summarize_release_checklist_guard(report)

            self.assertEqual("fail", row.status)
            self.assertIn("unsafe=1", row.detail)

    def test_manual_packet_summarizers_stay_open_for_incomplete_packets(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            handoff_packet = Path(tmp) / "handoff.md"
            handoff_packet.write_text(
                "\n".join(
                    [
                        "# Handoff",
                        "- Patch rows: 2",
                        "- Voice replay cases: 2",
                        "CASE_FILTER=sf2_vigor_ball_observed,ff6_magicite_observed",
                        "- Apply report status: `ready`",
                        "- Apply report assets edited: `no`",
                    ]
                ),
                encoding="utf-8",
            )
            screen_packet = Path(tmp) / "screen.md"
            screen_packet.write_text(
                "\n".join(
                    [
                        "# Screen Translation Manual QA Packet",
                        "- Rows: 5",
                        "- Current status: not_run=5",
                        "## Test Protocol",
                    ]
                ),
                encoding="utf-8",
            )
            rights_packet = Path(tmp) / "rights.md"
            rights_packet.write_text(
                "\n".join(
                    [
                        "# GKP Content Rights Manual Review Packet",
                        "- Machine audit: `pass`",
                        "- Human release checkbox: `open`",
                        "- Bundled packs: 6",
                        "- Knowledge files: 49",
                        "- License files: 6",
                        "- Citation files: 6",
                    ]
                ),
                encoding="utf-8",
            )

            self.assertEqual("open", module.summarize_asr_voice_handoff(handoff_packet).status)
            self.assertEqual("open", module.summarize_screen_manual_packet(screen_packet).status)
            self.assertEqual("open", module.summarize_content_rights_packet(rights_packet).status)

    def test_command_contract_audit_summarizer_fails_on_bad_commands(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            report = Path(tmp) / "audit.md"
            report.write_text(
                "\n".join(
                    [
                        "# M18 Command Contract Audit",
                        "",
                        "- Inputs: 8",
                        "- Status counts: pass=16, fail=1, missing=0",
                    ]
                ),
                encoding="utf-8",
            )

            row = module.summarize_command_contract_audit(report)

            self.assertEqual("fail", row.status)
            self.assertIn("fail=1", row.detail)


if __name__ == "__main__":
    unittest.main()
