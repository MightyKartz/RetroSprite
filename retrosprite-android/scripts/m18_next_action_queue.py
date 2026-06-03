#!/usr/bin/env python3
"""Generate a concrete next-action queue for the remaining M18 gates."""

from __future__ import annotations

import argparse
import csv
import importlib.util
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
GATE_STATUS_SCRIPT = ROOT / "scripts/m18_gate_status_json.py"
DEFAULT_ASR_HANDOFF = ROOT / "docs/qa-feedback/gkp-asr-patch-voice-replay-handoff.md"
DEFAULT_TRIAGE_REPORT = ROOT / "docs/qa-feedback/gkp-backlog-triage-report.md"
DEFAULT_HOTKEY_CASES = ROOT / "scripts/hotkey_voice_qa_cases.tsv"
DEFAULT_MARKDOWN = ROOT / "docs/qa-feedback/m18-next-action-queue.md"
DEFAULT_JSON = ROOT / "docs/qa-feedback/m18-next-action-queue.json"


@dataclass(frozen=True)
class ActionItem:
    action_id: str
    title: str
    owner: str
    status: str
    blockers: tuple[str, ...]
    evidence: tuple[str, ...]
    command: str
    acceptance: str


@dataclass(frozen=True)
class DeviceRerun:
    case_filter: str
    questions: tuple[str, ...]
    evidence: tuple[str, ...]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT_MARKDOWN)
    parser.add_argument("--json-output", type=Path, default=DEFAULT_JSON)
    parser.add_argument("--asr-handoff", type=Path, default=DEFAULT_ASR_HANDOFF)
    parser.add_argument("--triage-report", type=Path, default=DEFAULT_TRIAGE_REPORT)
    parser.add_argument("--hotkey-cases", type=Path, default=DEFAULT_HOTKEY_CASES)
    parser.add_argument(
        "--strict",
        action="store_true",
        help="Exit nonzero while any action is ready or blocked.",
    )
    args = parser.parse_args()

    try:
        queue = build_queue(
            asr_handoff_path=args.asr_handoff,
            triage_report_path=args.triage_report,
            hotkey_cases_path=args.hotkey_cases,
        )
    except ValueError as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(render_markdown(queue), encoding="utf-8")
    args.json_output.parent.mkdir(parents=True, exist_ok=True)
    args.json_output.write_text(json.dumps(render_json(queue), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    counts = status_counts(queue)
    print(
        "OK M18 next action queue: "
        f"done={counts.get('done', 0)}, "
        f"ready={counts.get('ready', 0)}, "
        f"blocked={counts.get('blocked', 0)}"
    )
    if args.strict and any(item.status != "done" for item in queue):
        return 1
    return 0


def build_queue(
    gate_summary: dict[str, Any] | None = None,
    *,
    asr_handoff_path: Path = DEFAULT_ASR_HANDOFF,
    triage_report_path: Path = DEFAULT_TRIAGE_REPORT,
    hotkey_cases_path: Path = DEFAULT_HOTKEY_CASES,
) -> tuple[ActionItem, ...]:
    if gate_summary is None:
        gate_summary = load_gate_status_module().build_summary()
    rows = {str(row["area"]): row for row in gate_summary.get("rows", [])}
    open_areas = {str(row["area"]) for row in gate_summary.get("open_areas", [])}

    hotkey_open = "Hotkey voice matrix" in open_areas
    gkp_backlog_open = "GKP backlog" in open_areas
    completion_open = bool(gate_summary.get("requires_human_or_device_evidence"))
    device_rerun = load_device_rerun(triage_report_path, hotkey_cases_path) if gkp_backlog_open else None

    queue = [
        ActionItem(
            action_id="rerun-device-lifecycle-row",
            title="Rerun the non-patch device lifecycle voice row",
            owner="human/device",
            status="ready" if device_rerun is not None else "done",
            blockers=() if device_rerun is not None else ("No open device_rerun_needed backlog row is present.",),
            evidence=device_rerun.evidence if device_rerun is not None else ("docs/qa-feedback/gkp-backlog-triage-report.md",),
            command=device_rerun_command(device_rerun) if device_rerun is not None else "No device lifecycle rerun is required.",
            acceptance=(
                "The rerun records a fresh hotkey_voice request or fresh overlay audio diagnostics for the device_rerun_needed row; "
                "do not edit GKP assets from this action."
            ),
        ),
        ActionItem(
            action_id="replay-full-voice-matrix",
            title="Review the observational hotkey voice matrix",
            owner="human/device",
            status="ready" if hotkey_open else "done",
            blockers=() if hotkey_open else ("Hotkey voice matrix is already closed.",),
            evidence=("docs/qa-feedback/hotkey-voice-matrix-report.md",),
            command="\n".join(
                [
                    "RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 \\",
                    "CASE_FILTER=sf2_vigor_ball_observed,golden_sun_ivan_observed,chrono_marle_observed,chrono_atb_observed,ff6_magicite_observed,langrisser_commander_smoke,phantasy_star_tech_skill_smoke \\",
                    "VOICE=Tingting SAY_RATE=96 PRE_SPEAK_SECONDS=3 POST_CASE_SECONDS=10 \\",
                    "POLL_ATTEMPTS=40 POLL_INTERVAL_SECONDS=2 READY_ATTEMPTS=20 READY_INTERVAL_SECONDS=1 STRICT=1 \\",
                    "./scripts/hotkey_voice_qa_batch.sh",
                ]
            ),
            acceptance="A fresh run updates `docs/qa-feedback/hotkey-voice-matrix-report.md`; repeated misses become backlog evidence, not an M18 manual approval gate.",
        ),
        ActionItem(
            action_id="final-m18-offline-gate",
            title="Run the final M18 offline quality gate",
            owner="agent",
            status="blocked" if completion_open else "done",
            blockers=("Completion audit is still open.",) if completion_open else (),
            evidence=("docs/qa-feedback/m18-completion-audit.md",),
            command="./scripts/m18_offline_quality_gate.sh",
            acceptance="Strict aggregate probes, script tests, release audit, diff check, approved GKP regression, and relevant real-device evidence pass.",
        ),
    ]
    return tuple(queue)


def render_markdown(queue: tuple[ActionItem, ...]) -> str:
    counts = status_counts(queue)
    lines = [
        "# M18 Next Action Queue",
        "",
        f"- Action counts: done={counts.get('done', 0)}, ready={counts.get('ready', 0)}, blocked={counts.get('blocked', 0)}",
        "- GKP assets edited by this queue: no",
        "",
        "## Queue",
        "",
        "| ID | Owner | Status | Title | Blockers | Evidence |",
        "|---|---|---|---|---|---|",
    ]
    for item in queue:
        blockers = "; ".join(item.blockers) or "-"
        evidence = "<br>".join(f"`{path}`" for path in item.evidence) or "-"
        lines.append(
            f"| `{item.action_id}` | {escape_cell(item.owner)} | `{item.status}` | "
            f"{escape_cell(item.title)} | {escape_cell(blockers)} | {evidence} |"
        )
    lines.extend(["", "## Commands And Acceptance", ""])
    for item in queue:
        lines.extend(
            [
                f"### {item.action_id}",
                "",
                f"- Status: `{item.status}`",
                f"- Owner: `{item.owner}`",
                f"- Acceptance: {item.acceptance}",
                "",
                "```bash",
                item.command,
                "```",
                "",
            ]
        )
    return "\n".join(lines)


def render_json(queue: tuple[ActionItem, ...]) -> dict[str, Any]:
    return {
        "schema_version": 1,
        "objective": "M18 Eval Lab + GKP Quality Loop",
        "counts": status_counts(queue),
        "action_ids_by_status": action_ids_by_status(queue),
        "assets_edited_by_report": False,
        "actions": [
            {
                "id": item.action_id,
                "title": item.title,
                "owner": item.owner,
                "status": item.status,
                "blockers": list(item.blockers),
                "evidence": list(item.evidence),
                "command": item.command,
                "acceptance": item.acceptance,
            }
            for item in queue
        ],
    }


def action_ids_by_status(queue: tuple[ActionItem, ...]) -> dict[str, list[str]]:
    grouped: dict[str, list[str]] = {"ready": [], "blocked": [], "done": []}
    for item in queue:
        grouped.setdefault(item.status, []).append(item.action_id)
    return grouped


def status_counts(queue: tuple[ActionItem, ...]) -> dict[str, int]:
    counts: dict[str, int] = {"done": 0, "ready": 0, "blocked": 0}
    for item in queue:
        counts[item.status] = counts.get(item.status, 0) + 1
    return counts


def load_gate_status_module():
    spec = importlib.util.spec_from_file_location("m18_gate_status_json", GATE_STATUS_SCRIPT)
    if spec is None or spec.loader is None:
        raise ValueError(f"cannot load gate status script: {GATE_STATUS_SCRIPT}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def load_asr_replay_case_filter(path: Path) -> str:
    if not path.is_file():
        raise ValueError(f"ASR handoff not found: {path}")
    text = path.read_text(encoding="utf-8")
    match = re.search(r"(?:^|\s)CASE_FILTER=([^\s\\]+)", text)
    if not match:
        raise ValueError(f"ASR handoff missing replay CASE_FILTER: {path}")
    return match.group(1).strip("\"'")


def load_device_rerun(triage_report_path: Path, hotkey_cases_path: Path) -> DeviceRerun | None:
    if not triage_report_path.is_file():
        return None
    triage_rows = parse_device_rerun_rows(triage_report_path.read_text(encoding="utf-8"))
    if not triage_rows:
        return None
    case_ids_by_question = load_hotkey_case_ids_by_question(hotkey_cases_path)
    case_ids: list[str] = []
    questions: list[str] = []
    evidence: list[str] = [display_path(triage_report_path)]
    for question, source in triage_rows:
        case_id = case_ids_by_question.get(normalize_question(question))
        if case_id is None:
            raise ValueError(f"device_rerun_needed question has no hotkey voice case: {question}")
        if case_id not in case_ids:
            case_ids.append(case_id)
        if question not in questions:
            questions.append(question)
        if source and source not in evidence:
            evidence.append(source)
    return DeviceRerun(
        case_filter=",".join(case_ids),
        questions=tuple(questions),
        evidence=tuple(evidence),
    )


def parse_device_rerun_rows(text: str) -> list[tuple[str, str]]:
    rows: list[tuple[str, str]] = []
    for line in text.splitlines():
        if "| `device_rerun_needed` | `open` |" not in line:
            continue
        cells = split_markdown_row(line)
        if len(cells) < 8:
            continue
        question = cells[1].strip()
        source = strip_code(cells[7].strip())
        rows.append((question, source))
    return rows


def load_hotkey_case_ids_by_question(path: Path) -> dict[str, str]:
    if not path.is_file():
        raise ValueError(f"hotkey voice cases not found: {path}")
    rows: dict[str, str] = {}
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        for row in reader:
            case_id = str(row.get("case_name") or row.get("id") or "").strip()
            question = normalize_question(str(row.get("spoken_prompt") or row.get("spoken_text") or ""))
            if case_id and question and question not in rows:
                rows[question] = case_id
    return rows


def device_rerun_command(rerun: DeviceRerun) -> str:
    return "\n".join(
        [
            "RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 \\",
            f"CASE_FILTER={rerun.case_filter} \\",
            "VOICE=Tingting SAY_RATE=96 PRE_SPEAK_SECONDS=3 POST_CASE_SECONDS=10 \\",
            "POLL_ATTEMPTS=40 POLL_INTERVAL_SECONDS=2 READY_ATTEMPTS=20 READY_INTERVAL_SECONDS=1 STRICT=0 \\",
            "./scripts/hotkey_voice_qa_batch.sh",
        ]
    )


def split_markdown_row(line: str) -> list[str]:
    stripped = line.strip()
    if stripped.startswith("|"):
        stripped = stripped[1:]
    if stripped.endswith("|"):
        stripped = stripped[:-1]
    cells: list[str] = []
    current: list[str] = []
    escaped = False
    for char in stripped:
        if escaped:
            current.append("|" if char == "|" else "\\" + char)
            escaped = False
        elif char == "\\":
            escaped = True
        elif char == "|":
            cells.append("".join(current).strip())
            current = []
        else:
            current.append(char)
    cells.append("".join(current).strip())
    return cells


def strip_code(value: str) -> str:
    value = value.strip()
    if value.startswith("`") and value.endswith("`"):
        return value[1:-1]
    return value


def normalize_question(value: str) -> str:
    return value.strip().rstrip("?.!？。")


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
