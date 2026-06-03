import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/gkp_patch_regression_gate.sh"


class GkpPatchRegressionGateTest(unittest.TestCase):

    def test_script_is_bash_syntax_valid(self):
        subprocess.run(
            ["bash", "-n", str(SCRIPT)],
            cwd=ROOT,
            check=True,
        )

    def test_safe_default_runs_gkp_regression_and_audit(self):
        script = SCRIPT.read_text(encoding="utf-8")

        self.assertIn("[1/6] focused GKP JVM regression", script)
        self.assertIn("--tests \"com.retrosprite.app.gkp.GkpV0FixtureLintTest\"", script)
        self.assertIn("--tests \"com.retrosprite.app.gkp.RetroJrpgSrpgPackCoverageTest\"", script)
        self.assertIn("--tests \"com.retrosprite.app.data.retrieval.RetroJrpgSrpgPackRetrievalGoldenTest\"", script)
        self.assertIn("[2/6] release audit", script)
        self.assertIn("python3 scripts/rc_release_audit.py", script)
        self.assertIn("[3/6] GKP asset mutation guard", script)
        self.assertIn("python3 scripts/gkp_asset_mutation_guard.py", script)
        self.assertIn("--output docs/qa-feedback/gkp-asset-mutation-guard.md", script)
        self.assertIn("--strict", script)
        self.assertIn("git diff --check", script)

    def test_report_refreshes_are_explicit_and_backlog_needs_new_evidence(self):
        script = SCRIPT.read_text(encoding="utf-8")

        self.assertIn('RUN_REPORTS="${RUN_REPORTS:-1}"', script)
        self.assertIn("python3 scripts/gkp_eval_report.py", script)
        self.assertIn("python3 scripts/screen_translation_eval_report.py", script)
        self.assertIn("python3 scripts/m18_status_report.py", script)
        self.assertIn('if [ -n "$BACKLOG_INPUT" ]; then', script)
        self.assertIn("--merge-existing-backlog docs/qa-feedback/gkp-quality-backlog.md", script)
        self.assertIn("BACKLOG_INPUT not set; keeping existing GKP backlog", script)

    def test_voice_replay_is_opt_in_and_targets_current_asr_rows(self):
        script = SCRIPT.read_text(encoding="utf-8")

        self.assertIn('RUN_VOICE="${RUN_VOICE:-0}"', script)
        self.assertIn(
            "sf2_vigor_ball_observed,ff6_magicite_observed",
            script,
        )
        self.assertIn('if [ "$RUN_VOICE" = "1" ]; then', script)
        self.assertIn("RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 CASE_FILTER=\"$VOICE_CASE_FILTER\"", script)
        self.assertIn("hotkey voice replay skipped", script)


if __name__ == "__main__":
    unittest.main()
