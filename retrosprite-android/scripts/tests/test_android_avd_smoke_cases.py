import csv
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CASE_FILE = ROOT / "scripts/gkp_debug_cases.tsv"
SMOKE_SCRIPT = ROOT / "scripts/android_avd_smoke.sh"


class AndroidAvdSmokeCasesTest(unittest.TestCase):

    def test_debug_case_file_covers_all_bundled_real_packs(self):
        rows = read_cases()

        base_cases = [
            "shining-force-ii-md",
            "golden-sun-gba-zh",
            "phantasy-star-iv-md-zh",
            "langrisser-ii-md-zh",
            "chrono-trigger-snes-zh",
            "final-fantasy-vi-snes-zh",
        ]
        self.assertEqual(base_cases, [row["case_name"] for row in rows[: len(base_cases)]])
        self.assertTrue(
            {
                "sf2-vigor-ball-observed",
                "golden-sun-ivan-observed",
                "chrono-marle-observed",
                "chrono-atb-observed",
                "ff6-magicite-observed",
                "langrisser-mercenary-observed",
                "phantasy-star-combo-observed",
            }.issubset({row["case_name"] for row in rows})
        )
        for row in rows:
            self.assertTrue(row["label"], row)
            self.assertTrue(row["question"], row)
            self.assertIn(row["output_mode"], {"text", "hotkey_voice:text"}, row)
            self.assertEqual("evidence", row["expected_stage"], row)
            self.assertEqual("skipped", row["expected_llm_status"], row)
            self.assertIn(".", row["expected_source"], row)
            if row["case_name"].endswith("-observed"):
                self.assertEqual("hotkey_voice:text", row["output_mode"], row)
                self.assertTrue(row["expected_question"], row)

    def test_smoke_script_reads_shared_debug_case_file(self):
        script = SMOKE_SCRIPT.read_text(encoding="utf-8")

        self.assertIn("GKP_DEBUG_CASES_FILE", script)
        self.assertIn("gkp_debug_cases.tsv", script)
        self.assertIn("CASE_OUTPUT_MODE", script)
        self.assertIn("CASE_EXPECT_QUESTION", script)

    def test_smoke_script_validates_sources_from_latest_request(self):
        script = SMOKE_SCRIPT.read_text(encoding="utf-8")
        debug_post_block = script.split('info "    /debug/ask: ${DEBUG_BODY}"', maxsplit=1)[0]
        debug_post_block = debug_post_block.split('DEBUG_BODY=""', maxsplit=1)[1]

        self.assertNotIn("CASE_EXPECT_SOURCE", debug_post_block)
        self.assertIn("/debug/latest-request missing source", script)

    def test_smoke_script_polls_latest_request_until_current_case(self):
        script = SMOKE_SCRIPT.read_text(encoding="utf-8")

        self.assertIn('while [ "$latest_attempt" -le "$DEBUG_ATTEMPTS" ]; do', script)
        self.assertIn('\\"label\\":\\"${CASE_LABEL}\\"', script)
        self.assertIn('\\"question\\":\\"${CASE_EXPECT_QUESTION}\\"', script)


def read_cases():
    with CASE_FILE.open(encoding="utf-8", newline="") as handle:
        rows = [
            row
            for row in csv.DictReader(
                (line for line in handle if line.strip() and not line.startswith("#")),
                delimiter="\t",
            )
        ]
    return rows


if __name__ == "__main__":
    unittest.main()
