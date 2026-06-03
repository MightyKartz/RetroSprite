import importlib.util
import io
import json
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/m18_plan_execution_audit.py"


def load_module():
    spec = importlib.util.spec_from_file_location("m18_plan_execution_audit", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class M18PlanExecutionAuditTest(unittest.TestCase):

    def test_current_plan_audit_tracks_only_machine_device_m18_gates(self):
        module = load_module()

        tasks = module.load_plan_tasks(module.DEFAULT_PLANS)
        status_rows = module.load_status_rows(ROOT / "docs/qa-feedback/m18-status-report.md")
        markdown = module.render_markdown(
            tasks,
            status_rows,
            module.DEFAULT_PLANS,
            ROOT / "docs/qa-feedback/m18-status-report.md",
        )
        data = module.render_json(
            tasks,
            status_rows,
            module.DEFAULT_PLANS,
            ROOT / "docs/qa-feedback/m18-status-report.md",
        )

        self.assertGreater(sum(task.checked_count for task in tasks), 0)
        self.assertEqual(0, sum(task.unchecked_count for task in tasks))
        self.assertIn("Close M17 Before Starting Product Changes", markdown)
        self.assertIn("GKP backlog", markdown)
        self.assertIn("Hotkey voice matrix", markdown)
        self.assertNotIn("Screen translation matrix", markdown)
        self.assertIn("GKP assets edited by this audit: no", markdown)
        self.assertIn("GKP ASR voice replay handoff", markdown)
        self.assertIn("Open blocker categories:", markdown)
        self.assertIn("## Open Blocker Categories", markdown)
        self.assertNotIn("device_voice", markdown)
        self.assertNotIn("screen_translation", markdown)
        self.assertNotIn("content_rights", markdown)
        self.assertEqual("pass", data["status"])
        self.assertEqual(sum(task.checked_count for task in tasks), data["plan_checked"])
        self.assertEqual(sum(task.unchecked_count for task in tasks), data["plan_unchecked"])
        self.assertFalse(data["assets_edited_by_report"])
        self.assertEqual(0, data["counts"]["open_gates"])
        self.assertEqual(data["counts"]["open_gates"], len(data["open_gates"]))
        self.assertNotIn("human_approval", data["open_blocker_categories"])

    def test_parses_plan_tasks_and_checkbox_counts(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            plan = Path(tmp) / "plan.md"
            plan.write_text(
                "\n".join(
                    [
                        "# Plan",
                        "## Task 1: First Gate",
                        "- [x] **Step 1: Done**",
                        "- [ ] **Step 2: Open**",
                        "```markdown",
                        "- [x] Example checkbox in a fenced block",
                        "```",
                        "## Task 2: Second Gate",
                        "- [x] **Step 1: Also done**",
                        "## Self-Review",
                    ]
                ),
                encoding="utf-8",
            )

            tasks = module.load_plan_tasks(plan)

            self.assertEqual(2, len(tasks))
            self.assertEqual("First Gate", tasks[0].title)
            self.assertEqual(1, tasks[0].checked_count)
            self.assertEqual(1, tasks[0].unchecked_count)
            self.assertEqual(1, tasks[1].checked_count)

    def test_default_plan_set_uses_only_main_m18_plan(self):
        module = load_module()

        tasks = module.load_plan_tasks(module.DEFAULT_PLANS)
        titles = "\n".join(task.title for task in tasks)

        self.assertIn("m18-eval-lab-gkp-quality-loop.md", titles)
        self.assertNotIn("m18-approval-gated-quality-loop.md", titles)
        self.assertEqual(0, sum(task.unchecked_count for task in tasks))

    def test_parses_status_report_table(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            status = Path(tmp) / "status.md"
            status.write_text(
                "\n".join(
                    [
                        "# Status",
                        "| Area | Status | Evidence | Detail |",
                        "|---|---|---|---|",
                        "| GKP coverage | `pass` | `gkp.md` | packs=6 |",
                        "| Screen translation matrix | `open` | `screen.md` | not_run=5 |",
                    ]
                ),
                encoding="utf-8",
            )

            rows = module.load_status_rows(status)

            self.assertEqual(2, len(rows))
            self.assertEqual("pass", rows[0].status)
            self.assertEqual("Screen translation matrix", rows[1].area)
            self.assertEqual("open", rows[1].status)

    def test_recommended_actions_match_open_status_rows(self):
        module = load_module()
        actions = module.recommended_next_actions(
            [module.PlanTask("Task", (module.CheckboxItem("Open", False),))],
            [
                module.StatusRow("GKP backlog", "open", "items=3"),
                module.StatusRow("Hotkey voice matrix", "open", "fail=3"),
            ],
        )
        text = "\n".join(actions)

        self.assertIn("ASR review-packet rows are non-blocking", text)
        self.assertIn("hotkey-voice-matrix-report.md", text)
        self.assertNotIn("screen-translation-manual-packet.md", text)
        self.assertNotIn("content-rights", text)

    def test_open_blocker_categories_classify_plan_and_status_items(self):
        module = load_module()
        tasks = [
            module.PlanTask(
                "Add Safe GKP Patch Assistant",
                (
                    module.CheckboxItem("Run GKP-focused JVM tests and the release audit", False),
                ),
            ),
            module.PlanTask(
                "Close M17 Before Starting Product Changes",
                (
                    module.CheckboxItem("Run real-device hotkey voice playback matrix", False),
                ),
            ),
            module.PlanTask(
                "Start The Ongoing M18 Quality Loop",
                (
                    module.CheckboxItem("Prefer improving retrieval before adding provider surface area", False),
                ),
            ),
        ]
        rows = [
            module.StatusRow("Hotkey voice matrix", "open", "fail=2"),
            module.StatusRow("Unknown aggregate", "open", "detail=1"),
        ]

        counts = module.open_blocker_categories(tasks, rows)

        self.assertEqual(1, counts["post_approval_regression"])
        self.assertEqual(1, counts["ongoing_policy"])
        self.assertEqual(2, counts["device_voice"])
        self.assertEqual(1, counts["aggregate_status"])

    def test_main_strict_fails_when_plan_or_status_is_open(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            plan = tmp_path / "plan.md"
            status = tmp_path / "status.md"
            output = tmp_path / "audit.md"
            json_output = tmp_path / "audit.json"
            plan.write_text(
                "\n".join(
                    [
                        "## Task 1: Gate",
                        "- [ ] **Step 1: Open**",
                    ]
                ),
                encoding="utf-8",
            )
            status.write_text(
                "\n".join(
                    [
                        "| Area | Status | Evidence | Detail |",
                        "|---|---|---|---|",
                        "| Release checklist | `open` | `check.md` | unchecked=1 |",
                    ]
                ),
                encoding="utf-8",
            )

            old_argv = sys.argv
            try:
                sys.argv = [
                    "m18_plan_execution_audit.py",
                    "--plan",
                    str(plan),
                    "--status-report",
                    str(status),
                    "--output",
                    str(output),
                    "--json-output",
                    str(json_output),
                    "--strict",
                ]
                with redirect_stdout(io.StringIO()):
                    result = module.main()
            finally:
                sys.argv = old_argv

            self.assertEqual(1, result)
            self.assertTrue(output.is_file())
            self.assertTrue(json_output.is_file())
            self.assertEqual("open", json.loads(json_output.read_text(encoding="utf-8"))["status"])


if __name__ == "__main__":
    unittest.main()
