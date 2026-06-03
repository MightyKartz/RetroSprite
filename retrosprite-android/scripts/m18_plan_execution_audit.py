#!/usr/bin/env python3
"""Audit M18 implementation-plan progress against the aggregate status report."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_PLAN = ROOT / "docs/superpowers/plans/2026-06-01-m18-eval-lab-gkp-quality-loop.md"
DEFAULT_PLANS = (DEFAULT_PLAN,)
DEFAULT_STATUS = ROOT / "docs/qa-feedback/m18-status-report.md"
DEFAULT_OUTPUT = ROOT / "docs/qa-feedback/m18-plan-execution-audit.md"
DEFAULT_JSON_OUTPUT = ROOT / "docs/qa-feedback/m18-plan-execution-audit.json"


@dataclass(frozen=True)
class CheckboxItem:
    text: str
    checked: bool


@dataclass(frozen=True)
class PlanTask:
    title: str
    items: tuple[CheckboxItem, ...]

    @property
    def checked_count(self) -> int:
        return sum(1 for item in self.items if item.checked)

    @property
    def unchecked_count(self) -> int:
        return sum(1 for item in self.items if not item.checked)


@dataclass(frozen=True)
class StatusRow:
    area: str
    status: str
    detail: str


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--plan",
        type=Path,
        action="append",
        help="Plan file to audit. Repeat to include linked implementation plans.",
    )
    parser.add_argument("--status-report", type=Path, default=DEFAULT_STATUS)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--json-output", type=Path, default=DEFAULT_JSON_OUTPUT)
    parser.add_argument(
        "--strict",
        action="store_true",
        help="Exit nonzero while any plan checkbox is open or any aggregate status is not pass.",
    )
    args = parser.parse_args()
    plan_paths = tuple(args.plan) if args.plan else DEFAULT_PLANS

    try:
        tasks = load_plan_tasks(plan_paths)
        status_rows = load_status_rows(args.status_report)
    except ValueError as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1

    markdown = render_markdown(tasks, status_rows, plan_paths, args.status_report)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(markdown, encoding="utf-8")
    args.json_output.parent.mkdir(parents=True, exist_ok=True)
    args.json_output.write_text(
        json.dumps(render_json(tasks, status_rows, plan_paths, args.status_report), ensure_ascii=False, indent=2)
        + "\n",
        encoding="utf-8",
    )

    checked = sum(task.checked_count for task in tasks)
    unchecked = sum(task.unchecked_count for task in tasks)
    open_status = [row for row in status_rows if row.status != "pass"]
    print(
        f"OK M18 plan execution audit: checked={checked}, "
        f"unchecked={unchecked}, open_status={len(open_status)}"
    )
    if args.strict and (unchecked or open_status):
        return 1
    return 0


def load_plan_tasks(path_or_paths: Path | tuple[Path, ...] | list[Path]) -> list[PlanTask]:
    if isinstance(path_or_paths, Path):
        return load_plan_task_file(path_or_paths, include_plan_prefix=False)
    tasks: list[PlanTask] = []
    for path in path_or_paths:
        tasks.extend(load_plan_task_file(path, include_plan_prefix=True))
    if not tasks:
        raise ValueError("no Task sections found in plans")
    return tasks


def load_plan_task_file(path: Path, *, include_plan_prefix: bool) -> list[PlanTask]:
    if not path.is_file():
        raise ValueError(f"plan file not found: {path}")
    tasks: list[PlanTask] = []
    current_title: str | None = None
    current_items: list[CheckboxItem] = []
    in_fence = False

    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip().startswith("```"):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        task_match = re.match(r"^## Task \d+:\s+(.+)$", line)
        if task_match:
            if current_title is not None:
                tasks.append(PlanTask(current_title, tuple(current_items)))
            title = task_match.group(1).strip()
            current_title = f"{display_path(path)} / {title}" if include_plan_prefix else title
            current_items = []
            continue

        if current_title is None:
            continue
        if line.startswith("## ") and not line.startswith("## Task "):
            tasks.append(PlanTask(current_title, tuple(current_items)))
            current_title = None
            current_items = []
            continue

        item_match = re.match(r"^- \[([ xX])\]\s+(.+)$", line)
        if item_match:
            checked = item_match.group(1).lower() == "x"
            current_items.append(CheckboxItem(strip_inline_markdown(item_match.group(2)), checked))

    if current_title is not None:
        tasks.append(PlanTask(current_title, tuple(current_items)))
    if not tasks:
        raise ValueError(f"no Task sections found in plan: {path}")
    return tasks


def load_status_rows(path: Path) -> list[StatusRow]:
    if not path.is_file():
        raise ValueError(f"status report not found: {path}")
    rows: list[StatusRow] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.startswith("|"):
            continue
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if len(cells) != 4:
            continue
        if cells[0] in {"Area", "---"} or set(cells[0]) == {"-"}:
            continue
        rows.append(
            StatusRow(
                area=remove_backticks(cells[0]),
                status=remove_backticks(cells[1]),
                detail=remove_backticks(cells[3]),
            )
        )
    if not rows:
        raise ValueError(f"no status rows found in report: {path}")
    return rows


def render_markdown(
    tasks: list[PlanTask],
    status_rows: list[StatusRow],
    plan_paths: Path | tuple[Path, ...] | list[Path],
    status_path: Path,
) -> str:
    paths = (plan_paths,) if isinstance(plan_paths, Path) else tuple(plan_paths)
    checked = sum(task.checked_count for task in tasks)
    unchecked = sum(task.unchecked_count for task in tasks)
    open_status = [row for row in status_rows if row.status != "pass"]
    lines = [
        "# M18 Plan Execution Audit",
        "",
        f"- Plans: {', '.join(f'`{display_path(path)}`' for path in paths)}",
        f"- Status report: `{display_path(status_path)}`",
        f"- Plan checkboxes: checked={checked}, unchecked={unchecked}",
        f"- Aggregate status: pass={len(status_rows) - len(open_status)}, open={len(open_status)}",
        "- Open blocker categories: " + format_category_counts(open_blocker_categories(tasks, open_status)),
        "- GKP assets edited by this audit: no",
        "",
        "## Open Gates",
        "",
    ]
    if unchecked == 0 and not open_status:
        lines.append("- None. The M18 plan and aggregate status report are both green.")
    else:
        for task in tasks:
            for item in task.items:
                if not item.checked:
                    category = classify_open_item(item.text, task.title)
                    lines.append(f"- `{category}` `{task.title}`: {item.text}.")
        for row in open_status:
            category = classify_open_status(row)
            lines.append(f"- `{category}` `{row.area}` is `{row.status}`: {row.detail}.")

    category_counts = open_blocker_categories(tasks, open_status)
    lines.extend(["", "## Open Blocker Categories", ""])
    if not category_counts:
        lines.append("- None.")
    else:
        lines.extend(["| Category | Count |", "|---|---:|"])
        for category, count in sorted(category_counts.items()):
            lines.append(f"| `{category}` | {count} |")

    lines.extend(
        [
            "",
            "## Plan Tasks",
            "",
            "| Task | Checked | Unchecked | Open Items |",
            "|---|---:|---:|---|",
        ]
    )
    for task in tasks:
        open_items = "; ".join(item.text for item in task.items if not item.checked) or "-"
        lines.append(
            f"| {escape_cell(task.title)} | {task.checked_count} | "
            f"{task.unchecked_count} | {escape_cell(open_items)} |"
        )

    lines.extend(
        [
            "",
            "## Aggregate Status",
            "",
            "| Area | Status | Detail |",
            "|---|---|---|",
        ]
    )
    for row in status_rows:
        lines.append(f"| {escape_cell(row.area)} | `{escape_cell(row.status)}` | {escape_cell(row.detail)} |")

    next_actions = recommended_next_actions(tasks, open_status)
    if next_actions:
        lines.extend(["", "## Next Actions", ""])
        for action in next_actions:
            lines.append(f"- {action}")
    return "\n".join(lines) + "\n"


def render_json(
    tasks: list[PlanTask],
    status_rows: list[StatusRow],
    plan_paths: Path | tuple[Path, ...] | list[Path],
    status_path: Path,
) -> dict:
    paths = (plan_paths,) if isinstance(plan_paths, Path) else tuple(plan_paths)
    checked = sum(task.checked_count for task in tasks)
    unchecked = sum(task.unchecked_count for task in tasks)
    open_status = [row for row in status_rows if row.status != "pass"]
    category_counts = open_blocker_categories(tasks, open_status)
    plan_open_gates = [
        {
            "kind": "plan_item",
            "category": classify_open_item(item.text, task.title),
            "task": task.title,
            "text": item.text,
        }
        for task in tasks
        for item in task.items
        if not item.checked
    ]
    status_open_gates = [
        {
            "kind": "aggregate_status",
            "category": classify_open_status(row),
            "area": row.area,
            "status": row.status,
            "detail": row.detail,
        }
        for row in open_status
    ]
    return {
        "schema_version": 1,
        "objective": "M18 Eval Lab + GKP Quality Loop",
        "status": "pass" if unchecked == 0 and not open_status else "open",
        "plans": [display_path(path) for path in paths],
        "status_report": display_path(status_path),
        "plan_checked": checked,
        "plan_unchecked": unchecked,
        "counts": {
            "plan_checked": checked,
            "plan_unchecked": unchecked,
            "aggregate_pass": len(status_rows) - len(open_status),
            "aggregate_open": len(open_status),
            "open_gates": len(plan_open_gates) + len(status_open_gates),
        },
        "open_blocker_categories": category_counts,
        "assets_edited_by_report": False,
        "tasks": [
            {
                "title": task.title,
                "checked": task.checked_count,
                "unchecked": task.unchecked_count,
                "open_items": [
                    {
                        "text": item.text,
                        "category": classify_open_item(item.text, task.title),
                    }
                    for item in task.items
                    if not item.checked
                ],
            }
            for task in tasks
        ],
        "aggregate_status": [
            {
                "area": row.area,
                "status": row.status,
                "detail": row.detail,
                "category": classify_open_status(row) if row.status != "pass" else "pass",
            }
            for row in status_rows
        ],
        "open_gates": plan_open_gates + status_open_gates,
        "next_actions": recommended_next_actions(tasks, open_status),
    }


def open_blocker_categories(tasks: list[PlanTask], open_status: list[StatusRow]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for task in tasks:
        for item in task.items:
            if item.checked:
                continue
            category = classify_open_item(item.text, task.title)
            counts[category] = counts.get(category, 0) + 1
    for row in open_status:
        category = classify_open_status(row)
        counts[category] = counts.get(category, 0) + 1
    return counts


def classify_open_status(row: StatusRow) -> str:
    area = row.area.lower()
    if "gkp backlog" in area:
        return "gkp_backlog"
    if "hotkey voice" in area:
        return "device_voice"
    return "aggregate_status"


def classify_open_item(text: str, task_title: str = "") -> str:
    value = f"{task_title} {text}".lower()
    if "ongoing m18 quality loop" in value:
        return "ongoing_policy"
    if "regression" in value or "release audit" in value or "regenerate m18 reports" in value:
        return "post_approval_regression"
    if "rg476h" in value or "voice" in value or "playback" in value or "patched debug apk" in value:
        return "device_voice"
    if "local rc gate" in value or "device endpoint/gkp smoke" in value:
        return "release_verification"
    return "plan_open"


def format_category_counts(counts: dict[str, int]) -> str:
    if not counts:
        return "none"
    return ", ".join(f"{category}={count}" for category, count in sorted(counts.items()))


def recommended_next_actions(tasks: list[PlanTask], open_status: list[StatusRow]) -> list[str]:
    actions: list[str] = []
    open_areas = {row.area for row in open_status}
    if "GKP backlog" in open_areas:
        actions.append(
            "Review `docs/qa-feedback/gkp-backlog-triage-report.md`; ASR review-packet rows are non-blocking M18 artifacts."
        )
    if "Hotkey voice matrix" in open_areas:
        actions.append(
            "Use `docs/qa-feedback/hotkey-voice-matrix-report.md` to inspect the current playback rows; "
            "rerun rows only when it is useful device evidence, then regenerate the matrix report."
        )
    if any(task.unchecked_count for task in tasks) and not actions:
        actions.append("Finish the unchecked plan tasks, then regenerate this audit.")
    return actions


def strip_inline_markdown(text: str) -> str:
    text = re.sub(r"^\*\*(.+?)\*\*", r"\1", text)
    text = text.replace("`", "")
    return text.strip().rstrip(".")


def remove_backticks(text: str) -> str:
    return text.strip().strip("`")


def display_path(path: Path) -> str:
    resolved = path.resolve()
    try:
        return resolved.relative_to(ROOT).as_posix()
    except ValueError:
        return str(path)


def escape_cell(value: str) -> str:
    return value.replace("|", "\\|").replace("\n", " ")


if __name__ == "__main__":
    raise SystemExit(main())
