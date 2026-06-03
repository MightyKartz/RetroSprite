#!/usr/bin/env python3
"""Generate the ongoing M18 quality-loop handoff."""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_GATE_STATUS = ROOT / "docs/qa-feedback/m18-gate-status.json"
DEFAULT_ACTION_QUEUE = ROOT / "docs/qa-feedback/m18-next-action-queue.json"
DEFAULT_BACKLOG = ROOT / "docs/qa-feedback/gkp-quality-backlog.md"
DEFAULT_MANUAL_NOTES_TEMPLATE = ROOT / "docs/qa-feedback/gkp-manual-notes-template.tsv"
DEFAULT_OUTPUT = ROOT / "docs/qa-feedback/m18-quality-loop-handoff.md"
DEFAULT_JSON_OUTPUT = ROOT / "docs/qa-feedback/m18-quality-loop-handoff.json"


@dataclass(frozen=True)
class QualityLoopSummary:
    overall_status: str
    open_areas: tuple[str, ...]
    ready_actions: tuple[str, ...]
    blocked_actions: tuple[str, ...]
    done_actions: tuple[str, ...]
    backlog_detail: str
    voice_detail: str

    @property
    def loop_status(self) -> str:
        if self.overall_status == "pass" and not self.open_areas:
            return "ready_for_ongoing_rc_cycle"
        return "open_until_current_rc_gates_close"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gate-status", type=Path, default=DEFAULT_GATE_STATUS)
    parser.add_argument("--action-queue", type=Path, default=DEFAULT_ACTION_QUEUE)
    parser.add_argument("--backlog", type=Path, default=DEFAULT_BACKLOG)
    parser.add_argument("--manual-notes-template", type=Path, default=DEFAULT_MANUAL_NOTES_TEMPLATE)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--json-output", type=Path, default=DEFAULT_JSON_OUTPUT)
    args = parser.parse_args()

    try:
        summary = build_summary(args.gate_status, args.action_queue)
    except ValueError as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1

    markdown = render_markdown(
        summary,
        gate_status=args.gate_status,
        action_queue=args.action_queue,
        backlog=args.backlog,
        manual_notes_template=args.manual_notes_template,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(markdown, encoding="utf-8")
    args.json_output.parent.mkdir(parents=True, exist_ok=True)
    args.json_output.write_text(
        json.dumps(
            render_json(
                summary,
                gate_status=args.gate_status,
                action_queue=args.action_queue,
                backlog=args.backlog,
                manual_notes_template=args.manual_notes_template,
            ),
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    print(
        "OK M18 quality loop handoff: "
        f"status={summary.loop_status}, "
        f"ready={len(summary.ready_actions)}, "
        f"blocked={len(summary.blocked_actions)}"
    )
    return 0


def build_summary(gate_status_path: Path, action_queue_path: Path) -> QualityLoopSummary:
    gate_status = load_json(gate_status_path)
    action_queue = load_json(action_queue_path)

    rows = gate_status.get("rows") if isinstance(gate_status, dict) else None
    if not isinstance(rows, list):
        raise ValueError(f"gate status rows missing: {gate_status_path}")
    ready_actions, blocked_actions, done_actions = action_ids_from_queue(action_queue, action_queue_path)

    return QualityLoopSummary(
        overall_status=str(gate_status.get("overall_status") or "unknown"),
        open_areas=tuple(
            str(area.get("area") or "")
            for area in gate_status.get("open_areas", [])
            if isinstance(area, dict) and area.get("area")
        ),
        ready_actions=ready_actions,
        blocked_actions=blocked_actions,
        done_actions=done_actions,
        backlog_detail=row_detail(rows, "GKP backlog"),
        voice_detail=row_detail(rows, "Hotkey voice matrix"),
    )


def action_ids_from_queue(action_queue: Any, path: Path) -> tuple[tuple[str, ...], tuple[str, ...], tuple[str, ...]]:
    if not isinstance(action_queue, dict):
        raise ValueError(f"action queue JSON object missing: {path}")

    grouped = action_queue.get("action_ids_by_status")
    if isinstance(grouped, dict):
        return (
            grouped_action_ids(grouped, "ready"),
            grouped_action_ids(grouped, "blocked"),
            grouped_action_ids(grouped, "done"),
        )

    actions = action_queue.get("actions")
    if isinstance(actions, list):
        return (
            action_ids(actions, "ready"),
            action_ids(actions, "blocked"),
            action_ids(actions, "done"),
        )

    raise ValueError(f"action queue actions missing: {path}")


def grouped_action_ids(grouped: dict[str, Any], status: str) -> tuple[str, ...]:
    ids = grouped.get(status)
    if not isinstance(ids, list):
        return ()
    return tuple(str(action_id) for action_id in ids if str(action_id).strip())


def render_markdown(
    summary: QualityLoopSummary,
    *,
    gate_status: Path,
    action_queue: Path,
    backlog: Path,
    manual_notes_template: Path,
) -> str:
    open_areas = ", ".join(summary.open_areas) if summary.open_areas else "none"
    ready_actions = ", ".join(summary.ready_actions) if summary.ready_actions else "none"
    blocked_actions = ", ".join(summary.blocked_actions) if summary.blocked_actions else "none"
    done_actions = ", ".join(summary.done_actions) if summary.done_actions else "none"

    lines = [
        "# M18 Quality Loop Handoff",
        "",
        f"- Gate status: `{display_path(gate_status)}`",
        f"- Action queue: `{display_path(action_queue)}`",
        f"- Backlog: `{display_path(backlog)}`",
        f"- Manual notes template: `{display_path(manual_notes_template)}`",
        f"- Loop status: `{summary.loop_status}`",
        f"- Overall gate status: `{summary.overall_status}`",
        f"- Open areas: {open_areas}",
        f"- Ready actions: {ready_actions}",
        f"- Blocked actions: {blocked_actions}",
        f"- Done actions: {done_actions}",
        "- GKP assets edited by this handoff: no",
        "",
        "## Current Loop State",
        "",
        "| Area | Detail |",
        "|---|---|",
        f"| GKP backlog | `{summary.backlog_detail or 'unknown'}` |",
        f"| Hotkey voice matrix | `{summary.voice_detail or 'unknown'}` |",
        "",
        "## Safe Import Sources",
        "",
        "| Source | Use For | Safety Rule |",
        "|---|---|---|",
        "| `/debug/latest-request` JSON | One-off no-evidence or wrong-answer captures | Preview merged backlog first; do not replace active backlog blindly. |",
        "| `build/hotkey-voice-qa/<run>/results.tsv` | ASR, source mismatch, or voice lifecycle misses | Keep raw transcript, normalized question, source ids, finish reason, and audio counters. |",
        "| Manual tester notes TSV | Human-observed wrong answers, translation layout issues, or missing coverage | Start from the generated template, replace example rows, and merge with the active backlog. |",
        "",
        "## Preview-First Backlog Commands",
        "",
        "Preview a new latest-request capture:",
        "",
        "```bash",
        "python3 scripts/gkp_gap_backlog.py \\",
        "  --input build/rc-device-evidence/YYYYMMDD-HHMMSS/latest-request.json \\",
        "  --merge-existing-backlog docs/qa-feedback/gkp-quality-backlog.md \\",
        "  --output build/m18-latest-request-backlog-preview.md",
        "```",
        "",
        "Preview a voice QA run:",
        "",
        "```bash",
        "python3 scripts/gkp_gap_backlog.py \\",
        "  --input build/hotkey-voice-qa/YYYYMMDD-HHMMSS/results.tsv \\",
        "  --merge-existing-backlog docs/qa-feedback/gkp-quality-backlog.md \\",
        "  --output build/m18-voice-backlog-preview.md",
        "```",
        "",
        "Create the manual notes TSV template:",
        "",
        "```bash",
        "python3 scripts/gkp_gap_backlog.py \\",
        "  --manual-notes-template-output docs/qa-feedback/gkp-manual-notes-template.tsv",
        "```",
        "",
        "After replacing example rows with real tester observations, preview the merged backlog:",
        "",
        "```bash",
        "python3 scripts/gkp_gap_backlog.py \\",
        "  --input docs/qa-feedback/gkp-manual-notes-template.tsv \\",
        "  --merge-existing-backlog docs/qa-feedback/gkp-quality-backlog.md \\",
        "  --output build/m18-manual-notes-backlog-preview.md",
        "```",
        "",
        "After reviewing a preview, write the active backlog with merge protection:",
        "",
        "```bash",
        "python3 scripts/gkp_gap_backlog.py \\",
        "  --input docs/qa-feedback/gkp-manual-notes-template.tsv \\",
        "  --merge-existing-backlog docs/qa-feedback/gkp-quality-backlog.md \\",
        "  --output docs/qa-feedback/gkp-quality-backlog.md",
        "```",
        "",
        "Refresh triage and aggregate status after any reviewed import:",
        "",
        "```bash",
        "python3 scripts/gkp_backlog_triage_report.py \\",
        "  --output docs/qa-feedback/gkp-backlog-triage-report.md \\",
        "  --strict",
        "python3 scripts/m18_status_report.py \\",
        "  --output docs/qa-feedback/m18-status-report.md",
        "python3 scripts/m18_next_action_queue.py \\",
        "  --output docs/qa-feedback/m18-next-action-queue.md \\",
        "  --json-output docs/qa-feedback/m18-next-action-queue.json",
        "```",
        "",
        "## Fix Acceptance Rules",
        "",
        "- Every accepted GKP fix needs source ids and a regression target.",
        "- Alias or ASR fixes must be scoped to the current game pack and add or update at least one golden.",
        "- Translation failures should first become reproducible screen matrix rows or backlog rows before UI/model changes.",
        "- Voice-originated fixes require real-device replay after local regression.",
        "- Do not add new game content until the current six bundled packs complete one full green RC pass.",
        "- Prefer retrieval, aliases, ASR variants, screen grouping, diagnostics, and evidence quality before adding provider/model surface area.",
        "",
        "## Verification",
        "",
        "```bash",
        "./scripts/m18_offline_quality_gate.sh",
        "```",
        "",
        "Run `./scripts/m18_offline_quality_gate.sh` for the final offline verification pass.",
    ]
    return "\n".join(lines) + "\n"


def render_json(
    summary: QualityLoopSummary,
    *,
    gate_status: Path,
    action_queue: Path,
    backlog: Path,
    manual_notes_template: Path,
) -> dict[str, Any]:
    preview_commands = [
        {
            "id": "latest_request",
            "input": "build/rc-device-evidence/YYYYMMDD-HHMMSS/latest-request.json",
            "output": "build/m18-latest-request-backlog-preview.md",
            "merge_existing_backlog": display_path(backlog),
            "command": "\n".join(
                [
                    "python3 scripts/gkp_gap_backlog.py \\",
                    "  --input build/rc-device-evidence/YYYYMMDD-HHMMSS/latest-request.json \\",
                    f"  --merge-existing-backlog {display_path(backlog)} \\",
                    "  --output build/m18-latest-request-backlog-preview.md",
                ]
            ),
        },
        {
            "id": "voice_qa",
            "input": "build/hotkey-voice-qa/YYYYMMDD-HHMMSS/results.tsv",
            "output": "build/m18-voice-backlog-preview.md",
            "merge_existing_backlog": display_path(backlog),
            "command": "\n".join(
                [
                    "python3 scripts/gkp_gap_backlog.py \\",
                    "  --input build/hotkey-voice-qa/YYYYMMDD-HHMMSS/results.tsv \\",
                    f"  --merge-existing-backlog {display_path(backlog)} \\",
                    "  --output build/m18-voice-backlog-preview.md",
                ]
            ),
        },
        {
            "id": "manual_notes_template",
            "output": display_path(manual_notes_template),
            "command": "\n".join(
                [
                    "python3 scripts/gkp_gap_backlog.py \\",
                    f"  --manual-notes-template-output {display_path(manual_notes_template)}",
                ]
            ),
        },
        {
            "id": "manual_notes_preview",
            "input": display_path(manual_notes_template),
            "output": "build/m18-manual-notes-backlog-preview.md",
            "merge_existing_backlog": display_path(backlog),
            "command": "\n".join(
                [
                    "python3 scripts/gkp_gap_backlog.py \\",
                    f"  --input {display_path(manual_notes_template)} \\",
                    f"  --merge-existing-backlog {display_path(backlog)} \\",
                    "  --output build/m18-manual-notes-backlog-preview.md",
                ]
            ),
        },
        {
            "id": "manual_notes_apply_after_review",
            "input": display_path(manual_notes_template),
            "output": display_path(backlog),
            "merge_existing_backlog": display_path(backlog),
            "command": "\n".join(
                [
                    "python3 scripts/gkp_gap_backlog.py \\",
                    f"  --input {display_path(manual_notes_template)} \\",
                    f"  --merge-existing-backlog {display_path(backlog)} \\",
                    f"  --output {display_path(backlog)}",
                ]
            ),
        },
    ]
    return {
        "schema_version": 1,
        "objective": "M18 Eval Lab + GKP Quality Loop",
        "loop_status": summary.loop_status,
        "overall_status": summary.overall_status,
        "paths": {
            "gate_status": display_path(gate_status),
            "action_queue": display_path(action_queue),
            "backlog": display_path(backlog),
            "manual_notes_template": display_path(manual_notes_template),
        },
        "open_areas": list(summary.open_areas),
        "action_ids_by_status": {
            "ready": list(summary.ready_actions),
            "blocked": list(summary.blocked_actions),
            "done": list(summary.done_actions),
        },
        "counts": {
            "open_areas": len(summary.open_areas),
            "ready": len(summary.ready_actions),
            "blocked": len(summary.blocked_actions),
            "done": len(summary.done_actions),
        },
        "current_loop_state": {
            "gkp_backlog": summary.backlog_detail,
            "hotkey_voice_matrix": summary.voice_detail,
        },
        "safe_import_sources": [
            "/debug/latest-request JSON",
            "build/hotkey-voice-qa/<run>/results.tsv",
            "Manual tester notes TSV",
        ],
        "preview_backlog_commands": preview_commands,
        "fix_acceptance_rules": [
            "Every accepted GKP fix needs source ids and a regression target.",
            "Alias or ASR fixes must be scoped to the current game pack and add or update at least one golden.",
            "Translation failures should first become reproducible screen matrix rows or backlog rows before UI/model changes.",
            "Voice-originated fixes require real-device replay after local regression.",
            "Do not add new game content until the current six bundled packs complete one full green RC pass.",
            "Prefer retrieval, aliases, ASR variants, screen grouping, diagnostics, and evidence quality before adding provider/model surface area.",
        ],
        "contract": {
            "preview_first_backlog_imports": True,
            "merge_existing_backlog": True,
            "latest_request_preview": True,
            "voice_qa_preview": True,
            "manual_notes_preview": True,
            "fix_acceptance_rules": True,
            "voice_replay_required": True,
            "no_new_games_until_green_rc": True,
        },
        "verification": {
            "safe_default": "./scripts/m18_offline_quality_gate.sh",
            "final_quality_gate": "./scripts/m18_offline_quality_gate.sh",
        },
        "assets_edited_by_handoff": False,
    }


def load_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        raise ValueError(f"required file not found: {path}")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise ValueError(f"invalid JSON in {path}: {exc}") from exc
    if not isinstance(data, dict):
        raise ValueError(f"expected JSON object in {path}")
    return data


def action_ids(actions: list[Any], status: str) -> tuple[str, ...]:
    return tuple(
        str(action.get("id") or "")
        for action in actions
        if isinstance(action, dict) and action.get("status") == status and action.get("id")
    )


def row_detail(rows: list[Any], area: str) -> str:
    for row in rows:
        if isinstance(row, dict) and row.get("area") == area:
            return str(row.get("detail") or "")
    return ""


def display_path(path: Path) -> str:
    try:
        return str(path.resolve().relative_to(ROOT))
    except ValueError:
        return str(path)


if __name__ == "__main__":
    raise SystemExit(main())
