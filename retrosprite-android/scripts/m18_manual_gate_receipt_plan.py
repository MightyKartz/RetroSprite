#!/usr/bin/env python3
"""Turn a validated M18 manual-gate receipt into a dry-run execution plan.

This script never runs the generated commands. It exists so a human receipt can
be checked, translated into exact guarded commands, and reviewed before any GKP
asset, QA matrix, or release checklist file is modified.
"""

from __future__ import annotations

import argparse
import importlib.util
import json
import shlex
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_INTAKE = ROOT / "docs/qa-feedback/m18-manual-gate-intake.json"
DEFAULT_RECEIPT = ROOT / "docs/qa-feedback/m18-manual-gate-receipt.json"
DEFAULT_SCREEN_CASES = ROOT / "scripts/screen_translation_eval_cases.tsv"
DEFAULT_OUTPUT = ROOT / "docs/qa-feedback/m18-manual-gate-receipt-plan.md"
DEFAULT_JSON_OUTPUT = ROOT / "docs/qa-feedback/m18-manual-gate-receipt-plan.json"
RECEIPT_CHECK_SCRIPT = ROOT / "scripts/m18_manual_gate_receipt_check.py"


@dataclass(frozen=True)
class ReceiptPlanAction:
    action_id: str
    status: str
    command: str
    detail: str


@dataclass(frozen=True)
class ReceiptExecutionPlan:
    status: str
    receipt_check_status: str
    receipt_present: bool
    actions: tuple[ReceiptPlanAction, ...]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--intake", type=Path, default=DEFAULT_INTAKE)
    parser.add_argument("--receipt", type=Path, default=DEFAULT_RECEIPT)
    parser.add_argument("--screen-cases", type=Path, default=DEFAULT_SCREEN_CASES)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--json-output", type=Path, default=DEFAULT_JSON_OUTPUT)
    parser.add_argument("--strict", action="store_true", help="Exit nonzero unless the receipt plan is ready.")
    args = parser.parse_args()

    try:
        plan = build_plan(args.intake, args.receipt, args.screen_cases)
        markdown = render_markdown(plan, args.intake, args.receipt)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(markdown, encoding="utf-8")
        args.json_output.parent.mkdir(parents=True, exist_ok=True)
        args.json_output.write_text(
            json.dumps(render_json(plan, args.intake, args.receipt), ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
    except ValueError as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1

    counts = status_counts(plan.actions)
    print(
        "OK M18 manual gate receipt plan: "
        f"status={plan.status}, "
        f"receipt_check={plan.receipt_check_status}, "
        f"receipt={'present' if plan.receipt_present else 'missing'}, "
        f"ready={counts.get('ready', 0)}, "
        f"open={counts.get('open', 0)}, "
        f"blocked={counts.get('blocked', 0)}"
    )
    if args.strict and plan.status != "pass":
        return 1
    return 0


def build_plan(intake_path: Path, receipt_path: Path, screen_cases_path: Path) -> ReceiptExecutionPlan:
    receipt_check = load_receipt_check_module()
    intake = receipt_check.load_json(intake_path, "manual gate intake")
    screen_cases = receipt_check.load_screen_cases(screen_cases_path)
    check = receipt_check.build_check(intake, receipt_path, screen_cases, screen_cases_path)
    ready_ids = receipt_check.ready_section_ids(intake)

    if check.status != "pass":
        return ReceiptExecutionPlan(
            status=check.status,
            receipt_check_status=check.status,
            receipt_present=check.receipt_present,
            actions=tuple(
                ReceiptPlanAction(
                    action_id=item.item_id,
                    status="blocked" if item.status == "fail" else "open",
                    command="",
                    detail=item.detail,
                )
                for item in check.items
            ),
        )

    if not ready_ids:
        return ReceiptExecutionPlan(
            status="pass",
            receipt_check_status=check.status,
            receipt_present=check.receipt_present,
            actions=(),
        )
    if not check.receipt_present:
        return ReceiptExecutionPlan(
            status="open",
            receipt_check_status=check.status,
            receipt_present=False,
            actions=(
                ReceiptPlanAction(
                    action_id="manual-gates",
                    status="open",
                    command="",
                    detail="ready manual gates exist but no receipt file is present",
                ),
            ),
        )

    receipt = receipt_check.load_json(receipt_path, "manual gate receipt")
    actions = build_ready_actions(receipt, ready_ids, screen_cases, screen_cases_path)
    return ReceiptExecutionPlan(
        status="pass",
        receipt_check_status=check.status,
        receipt_present=True,
        actions=actions,
    )


def build_ready_actions(
    receipt: dict[str, Any],
    ready_ids: set[str],
    screen_cases: tuple[Any, ...],
    screen_cases_path: Path = DEFAULT_SCREEN_CASES,
) -> tuple[ReceiptPlanAction, ...]:
    actions: list[ReceiptPlanAction] = []
    if "asr-patch-approval" in ready_ids:
        approval = str(receipt.get("asr_patch_approval", {}).get("approval_phrase", ""))
        actions.append(
            ReceiptPlanAction(
                action_id="apply-approved-asr-patch",
                status="ready",
                command="\n".join(
                    [
                        "python3 scripts/gkp_patch_apply_review_packet.py \\",
                        "  --packet docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md \\",
                        "  --output docs/qa-feedback/gkp-patch-apply-result-20260601-hotkey-voice.md \\",
                        "  --apply \\",
                        f"  --approval {shlex.quote(approval)} \\",
                        "  --strict",
                    ]
                ),
                detail="applies only the current human-approved ASR alias/golden rows",
            )
        )
    if "screen-translation-manual-results" in ready_ids:
        screen_results = receipt.get("screen_translation_results", [])
        by_id = {
            str(entry.get("case_id")): str(entry.get("result", "")).strip()
            for entry in screen_results
            if isinstance(entry, dict)
        }
        failed_screen_case_ids = [
            case_id
            for case_id, result in by_id.items()
            if result.lower().startswith("fail")
        ]
        for case in screen_cases:
            result = by_id.get(case.case_id, "")
            actions.append(
                ReceiptPlanAction(
                    action_id=f"update-screen-matrix-{case.case_id}",
                    status="ready",
                    command="\n".join(
                        [
                            "python3 scripts/screen_translation_matrix_update.py \\",
                            f"  --cases {shlex.quote(display_path(screen_cases_path))} \\",
                            f"  --case-id {shlex.quote(case.case_id)} \\",
                            f"  --result {shlex.quote(result)} \\",
                            f"  --output build/m18-screen-matrix-previews/{shlex.quote(case.case_id)}.md",
                            "",
                            "# After reviewing the preview:",
                            "python3 scripts/screen_translation_matrix_update.py \\",
                            f"  --cases {shlex.quote(display_path(screen_cases_path))} \\",
                            f"  --case-id {shlex.quote(case.case_id)} \\",
                            f"  --result {shlex.quote(result)} \\",
                            "  --apply",
                        ]
                    ),
                    detail=f"previews then records validated manual result: {result}",
                )
            )
        if failed_screen_case_ids:
            actions.append(
                ReceiptPlanAction(
                    action_id="preview-screen-failure-backlog",
                    status="ready",
                    command="\n".join(
                        [
                            "python3 scripts/gkp_gap_backlog.py \\",
                            "  --input docs/qa-feedback/m18-manual-gate-receipt.json \\",
                            "  --output build/m18-receipt-backlog-preview.md",
                        ]
                    ),
                    detail=(
                        "previews screen translation Fail rows as backlog items without overwriting "
                        f"docs/qa-feedback/gkp-quality-backlog.md; cases={','.join(sorted(failed_screen_case_ids))}"
                    ),
                )
            )
    if "content-rights-human-review" in ready_ids:
        approval = str(receipt.get("content_rights_review", {}).get("approval_phrase", ""))
        actions.append(
            ReceiptPlanAction(
                action_id="refresh-content-rights-approval",
                status="ready",
                command="\n".join(
                    [
                        "python3 scripts/m18_release_checklist_guard.py \\",
                        "  --output docs/qa-feedback/m18-release-checklist-guard.md \\",
                        f"  --content-rights-approval {shlex.quote(approval)}",
                    ]
                ),
                detail="refreshes the release checklist guard with the human content-rights approval phrase; it does not check release boxes",
            )
        )
    return tuple(actions)


def render_markdown(plan: ReceiptExecutionPlan, intake_path: Path, receipt_path: Path) -> str:
    counts = status_counts(plan.actions)
    lines = [
        "# M18 Manual Gate Receipt Execution Plan",
        "",
        f"- Intake: `{display_path(intake_path)}`",
        f"- Receipt: `{display_path(receipt_path)}`",
        f"- Receipt present: `{'yes' if plan.receipt_present else 'no'}`",
        f"- Receipt check status: `{plan.receipt_check_status}`",
        f"- Plan status: `{plan.status}`",
        f"- Action counts: ready={counts.get('ready', 0)}, open={counts.get('open', 0)}, blocked={counts.get('blocked', 0)}",
        "- Commands executed by this planner: no",
        "- GKP assets edited by this planner: no",
        "",
        "| Action | Status | Detail |",
        "|---|---|---|",
    ]
    if not plan.actions:
        lines.append("| - | `pass` | no ready manual receipt action is required |")
    else:
        for action in plan.actions:
            lines.append(f"| `{escape_cell(action.action_id)}` | `{action.status}` | {escape_cell(action.detail)} |")

    ready_actions = [action for action in plan.actions if action.status == "ready" and action.command]
    if ready_actions:
        lines.extend(
            [
                "",
                "## Ready Commands",
                "",
                "Review these commands before running them. The planner generated them from a validated receipt but did not execute them.",
                "Before running any ready command, refresh the command contract audit:",
                "",
                "```bash",
                "python3 scripts/m18_command_contract_audit.py --strict",
                "```",
                "",
            ]
        )
        for action in ready_actions:
            lines.extend(
                [
                    f"### {action.action_id}",
                    "",
                    "```bash",
                    action.command,
                    "```",
                    "",
                ]
            )
    else:
        lines.extend(
            [
                "",
                "## Next Step",
                "",
                "Resolve the open or blocked receipt items, then rerun this planner.",
                "",
            ]
        )
    return "\n".join(lines).rstrip() + "\n"


def render_json(plan: ReceiptExecutionPlan, intake_path: Path, receipt_path: Path) -> dict[str, Any]:
    counts = status_counts(plan.actions)
    return {
        "schema_version": 1,
        "objective": "M18 Eval Lab + GKP Quality Loop",
        "status": plan.status,
        "receipt_check_status": plan.receipt_check_status,
        "receipt_present": plan.receipt_present,
        "intake": display_path(intake_path),
        "receipt": display_path(receipt_path),
        "counts": {
            "ready": counts.get("ready", 0),
            "open": counts.get("open", 0),
            "blocked": counts.get("blocked", 0),
        },
        "commands_executed_by_planner": False,
        "assets_edited_by_planner": False,
        "actions": [
            {
                "id": action.action_id,
                "status": action.status,
                "detail": action.detail,
                "command": action.command,
            }
            for action in plan.actions
        ],
    }


def load_receipt_check_module() -> Any:
    spec = importlib.util.spec_from_file_location("m18_manual_gate_receipt_check", RECEIPT_CHECK_SCRIPT)
    if spec is None or spec.loader is None:
        raise ValueError(f"cannot load receipt check script: {RECEIPT_CHECK_SCRIPT}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def status_counts(actions: tuple[ReceiptPlanAction, ...]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for action in actions:
        counts[action.status] = counts.get(action.status, 0) + 1
    return counts


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
