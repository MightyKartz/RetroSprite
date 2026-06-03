#!/usr/bin/env python3
"""Generate a handoff packet for the remaining machine/device M18 gates."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_AUDIT = ROOT / "docs/qa-feedback/m18-plan-execution-audit.md"
DEFAULT_HOTKEY_VOICE_REPORT = ROOT / "docs/qa-feedback/hotkey-voice-matrix-report.md"
DEFAULT_OUTPUT = ROOT / "docs/qa-feedback/m18-remaining-gate-handoff.md"
DEFAULT_JSON_OUTPUT = ROOT / "docs/qa-feedback/m18-remaining-gate-handoff.json"


@dataclass(frozen=True)
class RemainingGateSummary:
    plan_checked: int
    plan_unchecked: int
    aggregate_pass: int
    aggregate_open: int
    open_gates: tuple[str, ...]
    hotkey_voice_total: int
    hotkey_voice_pass: int
    hotkey_voice_fail: int
    hotkey_voice_blocked: int
    hotkey_voice_not_run: int
    hotkey_voice_missing: int
    hotkey_voice_categories: str

    @property
    def is_green(self) -> bool:
        return self.plan_unchecked == 0 and self.aggregate_open == 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--audit", type=Path, default=DEFAULT_AUDIT)
    parser.add_argument("--hotkey-voice-report", type=Path, default=DEFAULT_HOTKEY_VOICE_REPORT)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--json-output", type=Path, default=DEFAULT_JSON_OUTPUT)
    parser.add_argument("--strict", action="store_true", help="Exit nonzero while any remaining M18 gate is open.")
    args = parser.parse_args()

    try:
        summary = build_summary(args.audit, args.hotkey_voice_report)
    except ValueError as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1

    markdown = render_markdown(summary, audit=args.audit, hotkey_voice_report=args.hotkey_voice_report)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(markdown, encoding="utf-8")
    args.json_output.parent.mkdir(parents=True, exist_ok=True)
    args.json_output.write_text(
        json.dumps(render_json(summary, audit=args.audit, hotkey_voice_report=args.hotkey_voice_report), ensure_ascii=False, indent=2)
        + "\n",
        encoding="utf-8",
    )
    print(
        "OK M18 remaining gate handoff: "
        f"plan_unchecked={summary.plan_unchecked}, aggregate_open={summary.aggregate_open}"
    )
    if args.strict and not summary.is_green:
        return 1
    return 0


def build_summary(audit_path: Path, hotkey_voice_report_path: Path) -> RemainingGateSummary:
    audit = read_required(audit_path)
    hotkey_voice = read_required(hotkey_voice_report_path)

    checked, unchecked = int_pair(r"- Plan checkboxes:\s*checked=(\d+),\s*unchecked=(\d+)", audit, "plan checkboxes")
    aggregate_pass, aggregate_open = int_pair(
        r"- Aggregate status:\s*pass=(\d+),\s*open=(\d+)",
        audit,
        "aggregate status",
    )
    return RemainingGateSummary(
        plan_checked=checked,
        plan_unchecked=unchecked,
        aggregate_pass=aggregate_pass,
        aggregate_open=aggregate_open,
        open_gates=tuple(extract_open_gates(audit)),
        hotkey_voice_total=int_value(r"- Total:\s*(\d+)", hotkey_voice, "hotkey voice total"),
        hotkey_voice_pass=int_value(r"pass=(\d+)", hotkey_voice, "hotkey voice pass"),
        hotkey_voice_fail=int_value(r"fail=(\d+)", hotkey_voice, "hotkey voice fail"),
        hotkey_voice_blocked=int_value(r"blocked=(\d+)", hotkey_voice, "hotkey voice blocked"),
        hotkey_voice_not_run=int_value(r"not_run=(\d+)", hotkey_voice, "hotkey voice not_run"),
        hotkey_voice_missing=int_value(r"missing=(\d+)", hotkey_voice, "hotkey voice missing"),
        hotkey_voice_categories=text_value(r"- Failure categories:\s*([^\n]+)", hotkey_voice, "hotkey voice categories").strip(),
    )


def render_markdown(summary: RemainingGateSummary, *, audit: Path, hotkey_voice_report: Path) -> str:
    commands = command_contract()
    lines = [
        "# M18 Remaining Gate Handoff",
        "",
        f"- Plan audit: `{display_path(audit)}`",
        f"- Hotkey voice matrix: `{display_path(hotkey_voice_report)}`",
        f"- Plan checkboxes: checked={summary.plan_checked}, unchecked={summary.plan_unchecked}",
        f"- Aggregate status: pass={summary.aggregate_pass}, open={summary.aggregate_open}",
        "- Removed from M18 scope: manual ASR approval, five-row screen translation manual matrix, and human content-rights confirmation.",
        "- GKP assets edited by this handoff: no",
        "",
        "## Gate Summary",
        "",
        "| Gate | Current State | Next Action |",
        "|---|---|---|",
        (
            "| Hotkey voice matrix | "
            f"`total={summary.hotkey_voice_total}; pass={summary.hotkey_voice_pass}; "
            f"fail={summary.hotkey_voice_fail}; blocked={summary.hotkey_voice_blocked}; "
            f"not_run={summary.hotkey_voice_not_run}; missing={summary.hotkey_voice_missing}; "
            f"categories={summary.hotkey_voice_categories}` | "
            "Rerun the matrix when device conditions or GKP coverage changes; convert repeated misses into backlog rows. |"
        ),
        "",
        "## Required Order",
        "",
        "1. Keep GKP eval, backlog triage, patch proposal audit, asset guard, and command-contract audit green.",
        "2. Review or rerun the hotkey voice matrix only when it is useful device evidence.",
        "3. Convert repeated hotkey misses into reviewed backlog rows or scoped patch proposals.",
        "4. Regenerate M18 reports and run the offline quality gate.",
        "",
        "## Commands",
        "",
        "Refresh the observational hotkey voice matrix report:",
        "",
        "```bash",
        *commands["hotkey_voice_matrix_report"],
        "```",
        "",
        "Prepare a manual tester notes TSV for backlog import:",
        "",
        "```bash",
        *commands["manual_notes_template"],
        "",
        "# After replacing the example rows with real tester observations, preview the merged backlog first:",
        *commands["manual_notes_backlog_preview"],
        "",
        "# After reviewing the preview:",
        *commands["manual_notes_backlog_apply_after_review"],
        "```",
        "",
        "Run aggregate checks:",
        "",
        "```bash",
        *commands["offline_quality_gate"],
        "```",
        "",
        "Or run strict checks individually:",
        "",
        "```bash",
        *commands["m18_status_report_strict"],
        *commands["m18_plan_execution_audit_strict"],
        *commands["m18_completion_audit_strict"],
        "```",
    ]
    if summary.open_gates:
        lines.extend(["", "## Open Gates", ""])
        for gate in summary.open_gates:
            lines.append(f"- {gate}")
    lines.extend(
        [
            "",
            "## Completion Rule",
            "",
            "This handoff is complete only when M18 aggregate status, plan execution audit, completion audit, "
            "`./scripts/m18_offline_quality_gate.sh`, release audit, diff check, and relevant real-device evidence pass.",
        ]
    )
    return "\n".join(lines) + "\n"


def render_json(summary: RemainingGateSummary, *, audit: Path, hotkey_voice_report: Path) -> dict:
    commands = command_contract()
    return {
        "schema_version": 1,
        "objective": "M18 Eval Lab + GKP Quality Loop",
        "status": "pass" if summary.is_green else "open",
        "is_green": summary.is_green,
        "paths": {
            "plan_audit": display_path(audit),
            "hotkey_voice_report": display_path(hotkey_voice_report),
        },
        "counts": {
            "plan_checked": summary.plan_checked,
            "plan_unchecked": summary.plan_unchecked,
            "aggregate_pass": summary.aggregate_pass,
            "aggregate_open": summary.aggregate_open,
            "open_gates": len(summary.open_gates),
        },
        "gates": {
            "hotkey_voice_matrix": {
                "total": summary.hotkey_voice_total,
                "pass": summary.hotkey_voice_pass,
                "fail": summary.hotkey_voice_fail,
                "blocked": summary.hotkey_voice_blocked,
                "not_run": summary.hotkey_voice_not_run,
                "missing": summary.hotkey_voice_missing,
                "categories": summary.hotkey_voice_categories,
            },
        },
        "open_gates": list(summary.open_gates),
        "removed_from_m18_scope": [
            "manual_asr_approval",
            "five_row_screen_translation_manual_matrix",
            "human_content_rights_confirmation",
        ],
        "required_order": [
            "Keep machine-generated GKP/eval/audit reports green.",
            "Review or rerun the hotkey voice matrix only when it is useful device evidence.",
            "Convert repeated misses into backlog rows or scoped patch proposals.",
            "Run the final offline quality gate.",
        ],
        "commands": [
            {"id": command_id, "command": "\n".join(lines)}
            for command_id, lines in commands.items()
        ],
        "contract": {
            "assets_edited_by_handoff": False,
            "manual_asr_approval_required": False,
            "screen_translation_manual_matrix_required": False,
            "content_rights_human_confirmation_required": False,
            "merge_existing_backlog": True,
            "strict_completion_required": True,
            "final_quality_gate": "./scripts/m18_offline_quality_gate.sh",
        },
        "assets_edited_by_handoff": False,
    }


def command_contract() -> dict[str, list[str]]:
    return {
        "hotkey_voice_matrix_report": [
            "python3 scripts/hotkey_voice_matrix_report.py \\",
            "  --output docs/qa-feedback/hotkey-voice-matrix-report.md",
        ],
        "manual_notes_template": [
            "python3 scripts/gkp_gap_backlog.py \\",
            "  --manual-notes-template-output docs/qa-feedback/gkp-manual-notes-template.tsv",
        ],
        "manual_notes_backlog_preview": [
            "python3 scripts/gkp_gap_backlog.py \\",
            "  --input docs/qa-feedback/gkp-manual-notes-template.tsv \\",
            "  --merge-existing-backlog docs/qa-feedback/gkp-quality-backlog.md \\",
            "  --output build/m18-manual-notes-backlog-preview.md",
        ],
        "manual_notes_backlog_apply_after_review": [
            "python3 scripts/gkp_gap_backlog.py \\",
            "  --input docs/qa-feedback/gkp-manual-notes-template.tsv \\",
            "  --merge-existing-backlog docs/qa-feedback/gkp-quality-backlog.md \\",
            "  --output docs/qa-feedback/gkp-quality-backlog.md",
        ],
        "offline_quality_gate": ["./scripts/m18_offline_quality_gate.sh"],
        "m18_status_report_strict": [
            "python3 scripts/m18_status_report.py \\",
            "  --output docs/qa-feedback/m18-status-report.md \\",
            "  --strict",
        ],
        "m18_plan_execution_audit_strict": [
            "python3 scripts/m18_plan_execution_audit.py \\",
            "  --output docs/qa-feedback/m18-plan-execution-audit.md \\",
            "  --json-output docs/qa-feedback/m18-plan-execution-audit.json \\",
            "  --strict",
        ],
        "m18_completion_audit_strict": [
            "python3 scripts/m18_completion_audit.py \\",
            "  --output docs/qa-feedback/m18-completion-audit.md \\",
            "  --json-output docs/qa-feedback/m18-completion-audit.json \\",
            "  --strict",
        ],
    }


def extract_open_gates(text: str) -> list[str]:
    return [
        gate for gate in extract_bullets_between(text, "## Open Gates", "## Plan Tasks")
        if not gate.lower().startswith("none")
    ]


def extract_release_open_items(text: str) -> list[str]:
    return [line.removeprefix("- [ ] ").strip().rstrip(".") for line in text.splitlines() if line.startswith("- [ ] ")]


def extract_bullets_between(text: str, start: str, end: str) -> list[str]:
    in_section = False
    bullets: list[str] = []
    for line in text.splitlines():
        if line.strip() == start:
            in_section = True
            continue
        if in_section and line.strip() == end:
            break
        if in_section and line.startswith("- "):
            bullets.append(line.removeprefix("- ").strip().rstrip("."))
    return bullets


def read_required(path: Path) -> str:
    if not path.is_file():
        raise ValueError(f"required file not found: {path}")
    return path.read_text(encoding="utf-8")


def int_pair(pattern: str, text: str, label: str) -> tuple[int, int]:
    match = re.search(pattern, text)
    if not match:
        raise ValueError(f"missing {label}")
    return int(match.group(1)), int(match.group(2))


def int_value(pattern: str, text: str, label: str) -> int:
    return int(text_value(pattern, text, label))


def text_value(pattern: str, text: str, label: str) -> str:
    match = re.search(pattern, text)
    if not match:
        raise ValueError(f"missing {label}")
    return match.group(1)


def display_path(path: Path) -> str:
    resolved = path.resolve()
    try:
        return resolved.relative_to(ROOT).as_posix()
    except ValueError:
        return str(path)


if __name__ == "__main__":
    raise SystemExit(main())
