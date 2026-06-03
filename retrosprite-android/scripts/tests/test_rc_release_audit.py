import subprocess
import importlib.util
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
AUDIT_SCRIPT = ROOT / "scripts/rc_release_audit.py"


def load_audit_module():
    spec = importlib.util.spec_from_file_location("rc_release_audit", AUDIT_SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class RcReleaseAuditTest(unittest.TestCase):

    def test_release_audit_passes_current_tree(self):
        result = subprocess.run(
            ["python3", str(AUDIT_SCRIPT)],
            cwd=ROOT,
            check=True,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )

        self.assertIn("OK release audit", result.stdout)

    def test_release_audit_checks_expected_boundaries(self):
        script = AUDIT_SCRIPT.read_text(encoding="utf-8")

        self.assertIn("EXPECTED_GKP", script)
        self.assertIn("community.final-fantasy-vi-snes-zh", script)
        self.assertIn("RECOMMENDED_MODEL = \"Qwen/Qwen3-VL-8B-Instruct\"", script)
        self.assertIn("SECRET_PATTERN", script)
        self.assertIn("DeepSeek-OCR", script)
        self.assertIn("ML Kit", script)
        self.assertIn("ALLOWED_GKP_SUFFIXES", script)
        self.assertIn("sources/licenses.md", script)
        self.assertIn("sources/citations.jsonl", script)
        self.assertIn("MAX_GKP_TEXT_CHARS", script)
        self.assertIn("COPYRIGHT_RISK_TERMS", script)

    def test_release_audit_flags_long_or_risky_gkp_text(self):
        module = load_audit_module()
        path = ROOT / "app/src/main/assets/gkp/example/knowledge/entities.jsonl"

        errors = []
        module.check_string_content_boundary(
            path,
            1,
            "description_long",
            "x" * (module.MAX_GKP_TEXT_CHARS + 1),
            errors,
        )
        module.check_string_content_boundary(
            path,
            2,
            "description_long",
            "This row contains a full script dump marker.",
            errors,
        )

        self.assertEqual(2, len(errors))
        self.assertIn("possible long-form copied text", errors[0])
        self.assertIn("rights-risk term", errors[1])


if __name__ == "__main__":
    unittest.main()
