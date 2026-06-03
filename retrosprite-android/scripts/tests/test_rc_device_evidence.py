import os
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
EVIDENCE_SCRIPT = ROOT / "scripts/rc_device_evidence.sh"


class RcDeviceEvidenceTest(unittest.TestCase):

    def test_script_is_bash_syntax_valid(self):
        subprocess.run(
            ["bash", "-n", str(EVIDENCE_SCRIPT)],
            cwd=ROOT,
            check=True,
        )

    def test_script_captures_manual_gate_debug_artifacts(self):
        script = EVIDENCE_SCRIPT.read_text(encoding="utf-8")

        self.assertIn("adb-devices.txt", script)
        self.assertIn("appops-record-audio.txt", script)
        self.assertIn("appops-overlay.txt", script)
        self.assertIn("health.json", script)
        self.assertIn("/debug/latest-request", script)
        self.assertIn("/debug/hotkey-voice-overlay", script)
        self.assertIn("metadata.json", script)
        self.assertIn("--gate screen_translation --case-id", script)
        self.assertIn("build/rc-device-evidence", script)
        self.assertIn("requires one online adb device", script)

    def test_script_renders_readme_without_shell_expansion_errors(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            fake_bin = tmp_path / "bin"
            out_dir = tmp_path / "evidence"
            fake_bin.mkdir()

            adb = fake_bin / "adb"
            adb.write_text(
                textwrap.dedent(
                    """\
                    #!/usr/bin/env bash
                    case "$1" in
                      get-state)
                        echo device
                        ;;
                      devices)
                        echo "RG476H01077813 device usb:1-2 product:ums9620_2h10_native"
                        ;;
                      forward)
                        exit 0
                        ;;
                      exec-out)
                        printf '\\211PNG\\r\\n\\032\\n'
                        ;;
                      shell)
                        shift
                        case "$*" in
                          "pm list packages")
                            echo "package:com.retrosprite.app"
                            ;;
                          dumpsys\\ package*)
                            echo "Package [com.retrosprite.app]"
                            ;;
                          appops\\ get*)
                            echo "No operations."
                            ;;
                          "dumpsys window windows")
                            echo "Window #0"
                            ;;
                          *)
                            echo "$*"
                            ;;
                        esac
                        ;;
                    esac
                    """
                ),
                encoding="utf-8",
            )
            adb.chmod(0o755)

            curl = fake_bin / "curl"
            curl.write_text(
                textwrap.dedent(
                    """\
                    #!/usr/bin/env bash
                    url="${@: -1}"
                    case "$url" in
                      */health)
                        echo '{"status":"ok","version":"0.1.0"}'
                        ;;
                      */debug/latest-request)
                        echo '{"has_entry":true,"pipeline_stage":"evidence"}'
                        ;;
                      */debug/hotkey-voice-overlay)
                        echo '{"visible":false}'
                        ;;
                    esac
                    """
                ),
                encoding="utf-8",
            )
            curl.chmod(0o755)

            env = os.environ.copy()
            env["PATH"] = f"{fake_bin}{os.pathsep}{env['PATH']}"
            env["OUT_DIR"] = str(out_dir)
            env["ADB"] = "adb"

            result = subprocess.run(
                ["bash", str(EVIDENCE_SCRIPT)],
                cwd=ROOT,
                env=env,
                text=True,
                capture_output=True,
                check=True,
            )

            self.assertNotIn("command not found", result.stderr)
            self.assertNotIn("invalid option", result.stderr)

            readme = (out_dir / "README.md").read_text(encoding="utf-8")
            self.assertIn("- App package: `com.retrosprite.app`", readme)
            self.assertIn("- Gate: `manual`", readme)
            self.assertIn("- `latest-request.json`", readme)
            self.assertTrue((out_dir / "metadata.json").is_file())
            self.assertIn("docs/qa-feedback/rc-device-matrix.md", readme)

    def test_screen_translation_gate_requires_case_id(self):
        result = subprocess.run(
            ["bash", str(EVIDENCE_SCRIPT), "--gate", "screen_translation"],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("--case-id is required", result.stderr)

    def test_screen_translation_gate_requires_include_screenshot(self):
        result = subprocess.run(
            ["bash", str(EVIDENCE_SCRIPT), "--gate", "screen_translation", "--case-id", "ff6_dialogue"],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("--include-screenshot is required", result.stderr)

    def test_screen_translation_capture_includes_screenshot_metadata(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            fake_bin = tmp_path / "bin"
            out_dir = tmp_path / "screen-evidence"
            fake_bin.mkdir()

            adb = fake_bin / "adb"
            adb.write_text(
                textwrap.dedent(
                    """\
                    #!/usr/bin/env bash
                    case "$1" in
                      get-state)
                        echo device
                        ;;
                      devices)
                        echo "RG476H01077813 device usb:1-2 product:ums9620_2h10_native"
                        ;;
                      forward)
                        exit 0
                        ;;
                      exec-out)
                        printf '\\211PNG\\r\\n\\032\\n'
                        ;;
                      shell)
                        echo "$*"
                        ;;
                    esac
                    """
                ),
                encoding="utf-8",
            )
            adb.chmod(0o755)

            curl = fake_bin / "curl"
            curl.write_text(
                textwrap.dedent(
                    """\
                    #!/usr/bin/env bash
                    echo '{}'
                    """
                ),
                encoding="utf-8",
            )
            curl.chmod(0o755)

            env = os.environ.copy()
            env["PATH"] = f"{fake_bin}{os.pathsep}{env['PATH']}"
            env["OUT_DIR"] = str(out_dir)
            env["ADB"] = "adb"

            subprocess.run(
                [
                    "bash",
                    str(EVIDENCE_SCRIPT),
                    "--gate",
                    "screen_translation",
                    "--case-id",
                    "ff6_dialogue",
                    "--include-screenshot",
                ],
                cwd=ROOT,
                env=env,
                text=True,
                capture_output=True,
                check=True,
            )

            self.assertTrue((out_dir / "screenshot.png").is_file())
            self.assertIn("- `screenshot.png`", (out_dir / "README.md").read_text(encoding="utf-8"))
            metadata = (out_dir / "metadata.json").read_text(encoding="utf-8")
            self.assertIn('"gate": "screen_translation"', metadata)
            self.assertIn('"case_id": "ff6_dialogue"', metadata)
            self.assertIn('"screenshot_included": 1', metadata)


if __name__ == "__main__":
    unittest.main()
