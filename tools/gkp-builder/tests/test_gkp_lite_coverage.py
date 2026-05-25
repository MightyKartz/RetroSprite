import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[3]
SCRIPT_PATH = REPO_ROOT / "tools/gkp-builder/scripts/gkp_lite_coverage.py"
BUILDER_PATH = REPO_ROOT / "tools/gkp-builder/scripts/gkp_builder_new.py"
BUILDER_BIN = REPO_ROOT / "tools/gkp-builder/bin/gkp-builder"


def load_module():
    spec = importlib.util.spec_from_file_location("gkp_lite_coverage", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class GkpLiteCoverageTest(unittest.TestCase):

    def test_valid_lite_pack_passes_profile_thresholds(self):
        coverage = load_module()
        pack_dir = REPO_ROOT / "retrosprite-android/app/src/main/assets/gkp/golden-sun-gba-zh"

        report = coverage.evaluate_pack(pack_dir)

        self.assertTrue(report["ok"], json.dumps(report, ensure_ascii=False, indent=2))
        self.assertEqual("lite", report["coverage_tier"])
        self.assertEqual(42, report["metrics"]["knowledge_rows"])
        self.assertEqual(42, report["metrics"]["golden_rows"])
        self.assertEqual(0, len(report["failed_checks"]))
        self.assertEqual({"golden_rows_max"}, {check["code"] for check in report["warning_checks"]})

    def test_expanded_pack_uses_expanded_profile_by_default(self):
        coverage = load_module()
        pack_dir = REPO_ROOT / "retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md"

        report = coverage.evaluate_pack(pack_dir)

        self.assertTrue(report["ok"], json.dumps(report, ensure_ascii=False, indent=2))
        self.assertEqual("expanded", report["coverage_tier"])
        self.assertEqual("gkp-expanded", report["profile_id"])
        self.assertEqual(0, len(report["failed_checks"]))

    def test_generated_scaffold_fails_placeholders_and_lite_minimums(self):
        coverage = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            pack_dir = Path(tmp) / "test-game-zh"
            subprocess.run(
                [
                    "python3",
                    str(BUILDER_PATH),
                    "new",
                    "--profile",
                    "lite",
                    "--game-id",
                    "test_game",
                    "--pack-id",
                    "community.test-game-zh",
                    "--game",
                    "Test Game / 测试游戏",
                    "--platform",
                    "gba",
                    "--language",
                    "zh",
                    "--out",
                    str(pack_dir),
                ],
                check=True,
                stdout=subprocess.PIPE,
                text=True,
            )

            report = coverage.evaluate_pack(pack_dir)

        self.assertFalse(report["ok"])
        failed_codes = {check["code"] for check in report["failed_checks"]}
        self.assertIn("placeholder_marker", failed_codes)
        self.assertIn("knowledge_rows_min", failed_codes)
        self.assertIn("golden_rows_min", failed_codes)

    def test_builder_cli_runs_profile_coverage(self):
        pack_dir = REPO_ROOT / "retrosprite-android/app/src/main/assets/gkp/golden-sun-gba-zh"

        result = subprocess.run(
            [str(BUILDER_BIN), "coverage", str(pack_dir), "--json"],
            check=True,
            stdout=subprocess.PIPE,
            text=True,
        )
        report = json.loads(result.stdout)

        self.assertTrue(report["ok"])
        self.assertEqual("community.golden-sun-gba-zh", report["pack_id"])


if __name__ == "__main__":
    unittest.main()
