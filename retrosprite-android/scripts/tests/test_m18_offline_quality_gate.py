import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/m18_offline_quality_gate.sh"


class M18OfflineQualityGateTest(unittest.TestCase):

    def test_script_is_bash_syntax_valid(self):
        subprocess.run(
            ["bash", "-n", str(SCRIPT)],
            cwd=ROOT,
            check=True,
        )

    def test_safe_default_refreshes_all_m18_reports(self):
        script = SCRIPT.read_text(encoding="utf-8")

        self.assertIn("python3 scripts/gkp_eval_report.py", script)
        self.assertIn("--manual-notes-template-output docs/qa-feedback/gkp-manual-notes-template.tsv", script)
        self.assertIn("python3 scripts/gkp_backlog_triage_report.py", script)
        self.assertIn("python3 scripts/gkp_patch_proposal_audit.py --strict", script)
        self.assertIn("python3 scripts/gkp_patch_review_packet.py", script)
        self.assertIn("--json-output docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.json", script)
        self.assertIn("python3 scripts/gkp_patch_apply_review_packet.py", script)
        self.assertIn("python3 scripts/gkp_asset_mutation_guard.py", script)
        self.assertIn("python3 scripts/gkp_asr_patch_voice_handoff.py", script)
        self.assertIn("--json-output docs/qa-feedback/gkp-asr-patch-voice-replay-handoff.json", script)
        self.assertIn("python3 scripts/hotkey_voice_matrix_report.py", script)
        self.assertNotIn("python3 scripts/screen_translation_eval_report.py", script)
        self.assertNotIn("python3 scripts/screen_translation_manual_packet.py", script)
        self.assertNotIn("python3 scripts/gkp_content_rights_manual_packet.py", script)
        self.assertNotIn("python3 scripts/m18_release_checklist_guard.py", script)
        self.assertIn("python3 scripts/m18_status_report.py", script)
        self.assertIn("python3 scripts/m18_gate_status_json.py", script)
        self.assertIn("python3 scripts/m18_plan_execution_audit.py", script)
        self.assertIn("python3 scripts/m18_remaining_gate_packet.py", script)
        self.assertIn("python3 scripts/m18_completion_audit.py", script)
        self.assertIn("python3 scripts/m18_next_action_queue.py", script)
        self.assertIn("python3 scripts/m18_quality_loop_handoff.py", script)
        self.assertNotIn("python3 scripts/m18_manual_gate_intake_packet.py", script)
        self.assertNotIn("python3 scripts/m18_manual_gate_receipt_check.py", script)
        self.assertNotIn("python3 scripts/m18_manual_gate_receipt_plan.py", script)
        self.assertIn("python3 scripts/m18_command_contract_audit.py", script)
        self.assertIn("refresh final aggregate status after command audit", script)
        self.assertIn("refresh final completion audit after command audit", script)

    def test_backlog_refresh_requires_explicit_input(self):
        script = SCRIPT.read_text(encoding="utf-8")

        self.assertIn('BACKLOG_INPUT="${BACKLOG_INPUT:-}"', script)
        self.assertIn('if [ -n "$BACKLOG_INPUT" ]; then', script)
        self.assertIn("--merge-existing-backlog docs/qa-feedback/gkp-quality-backlog.md", script)
        self.assertIn("BACKLOG_INPUT not set; keeping current docs/qa-feedback/gkp-quality-backlog.md", script)

    def test_strict_probes_are_normal_completion_checks(self):
        script = SCRIPT.read_text(encoding="utf-8")

        self.assertNotIn('EXPECT_ALL_PASS="${EXPECT_ALL_PASS:-0}"', script)
        self.assertNotIn("expect_strict_probe", script)
        self.assertNotIn("hotkey_voice_matrix_report.py \\\n    --output /tmp/retrosprite-m18-hotkey-voice-strict.md \\\n    --strict", script)
        self.assertNotIn("expect_strict_probe \"screen translation\"", script)
        self.assertNotIn("expect_strict_probe \"release checklist guard\"", script)
        self.assertIn("/tmp/retrosprite-m18-status-strict.md", script)
        self.assertIn("/tmp/retrosprite-m18-gate-status-strict.json", script)
        self.assertIn("/tmp/retrosprite-m18-plan-execution-audit-strict.md", script)
        self.assertIn("/tmp/retrosprite-m18-remaining-gate-handoff-strict.md", script)
        self.assertIn("/tmp/retrosprite-m18-completion-audit-strict.md", script)
        self.assertIn("/tmp/retrosprite-m18-next-action-queue-strict.md", script)
        self.assertNotIn("expect_strict_probe \"M18 manual gate intake packet\"", script)
        self.assertNotIn("strict probe remains open as expected", script)
        self.assertNotIn("strict probe unexpectedly passed", script)

    def test_no_device_audio_or_gkp_apply_in_safe_default(self):
        script = SCRIPT.read_text(encoding="utf-8")

        self.assertNotIn("RUN_PLAYBACK=1", script)
        self.assertNotIn("CONFIRM_PLAYBACK=1", script)
        self.assertNotIn("RUN_DEVICE=1", script)
        self.assertNotIn("android_avd_smoke.sh", script)
        self.assertNotIn("--apply", script)


if __name__ == "__main__":
    unittest.main()
