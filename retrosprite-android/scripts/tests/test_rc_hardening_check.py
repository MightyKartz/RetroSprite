import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RC_SCRIPT = ROOT / "scripts/rc_hardening_check.sh"


class RcHardeningCheckTest(unittest.TestCase):

    def test_script_is_bash_syntax_valid(self):
        subprocess.run(
            ["bash", "-n", str(RC_SCRIPT)],
            cwd=ROOT,
            check=True,
        )

    def test_safe_default_includes_voice_matrix_self_test_and_dry_run(self):
        script = RC_SCRIPT.read_text(encoding="utf-8")

        self.assertIn("[4/8] release checklist audit", script)
        self.assertIn("python3 ./scripts/rc_release_audit.py", script)
        self.assertIn("[5/8] shell script unit checks", script)
        self.assertIn("python3 -m unittest discover scripts/tests", script)
        self.assertIn("[6/8] hotkey voice matrix self-test and dry-run", script)
        self.assertIn("SELF_TEST=1 ./scripts/hotkey_voice_qa_batch.sh", script)
        self.assertIn("DRY_RUN=1 ./scripts/hotkey_voice_qa_batch.sh", script)
        self.assertIn("RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 ./scripts/hotkey_voice_qa_batch.sh", script)

    def test_device_gate_has_fast_adb_preflight(self):
        script = RC_SCRIPT.read_text(encoding="utf-8")

        self.assertIn("[0/8] adb device preflight", script)
        self.assertIn('DEVICE_STATE="$("$ADB" get-state 2>/dev/null || true)"', script)
        self.assertIn("RUN_DEVICE=1 requires one online adb device", script)


if __name__ == "__main__":
    unittest.main()
