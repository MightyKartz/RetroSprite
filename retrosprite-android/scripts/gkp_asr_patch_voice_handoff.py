#!/usr/bin/env python3
"""Generate the approval and voice-replay handoff for current GKP ASR patches."""

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
DEFAULT_PACKET = ROOT / "docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md"
DEFAULT_APPLY_REPORT = ROOT / "docs/qa-feedback/gkp-patch-apply-dry-run-20260601-hotkey-voice.md"
DEFAULT_VOICE_CASES = ROOT / "scripts/hotkey_voice_qa_cases.tsv"
DEFAULT_OUTPUT = ROOT / "docs/qa-feedback/gkp-asr-patch-voice-replay-handoff.md"
DEFAULT_JSON_OUTPUT = ROOT / "docs/qa-feedback/gkp-asr-patch-voice-replay-handoff.json"
APPLY_SCRIPT = ROOT / "scripts/gkp_patch_apply_review_packet.py"


@dataclass(frozen=True)
class VoiceReplayCase:
    case_name: str
    pack_id: str
    label: str
    spoken_prompt: str
    expected_stage: str
    expected_answer_type: str
    expected_source: str
    notes: str


@dataclass(frozen=True)
class VoiceHandoff:
    patch_rows: list
    voice_cases: list[VoiceReplayCase]
    apply_report_status: str
    apply_report_assets_edited: str


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--packet", type=Path, default=DEFAULT_PACKET)
    parser.add_argument("--apply-report", type=Path, default=DEFAULT_APPLY_REPORT)
    parser.add_argument("--voice-cases", type=Path, default=DEFAULT_VOICE_CASES)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--json-output", type=Path, default=DEFAULT_JSON_OUTPUT)
    args = parser.parse_args()

    try:
        handoff = build_handoff(args.packet, args.apply_report, args.voice_cases)
        markdown = render_markdown(handoff, args.packet, args.apply_report, args.voice_cases)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(markdown, encoding="utf-8")
        json_data = render_json(handoff, args.packet, args.apply_report, args.voice_cases, args.output)
        args.json_output.parent.mkdir(parents=True, exist_ok=True)
        args.json_output.write_text(json.dumps(json_data, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    except ValueError as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1

    print(
        "OK GKP ASR patch voice handoff: "
        f"patch_rows={len(handoff.patch_rows)}, voice_cases={len(handoff.voice_cases)}, "
        f"apply_report={handoff.apply_report_status}, assets_edited={handoff.apply_report_assets_edited}, "
        f"json={display_path(args.json_output)}"
    )
    return 0


def build_handoff(packet_path: Path, apply_report_path: Path, voice_cases_path: Path) -> VoiceHandoff:
    apply_module = load_apply_module()
    patch_rows = apply_module.build_apply_rows(packet_path, ROOT / "app/src/main/assets/gkp")
    if not patch_rows:
        raise ValueError("no patch rows found")
    blocked = [row for row in patch_rows if row.status not in {"ready", "applied"}]
    if blocked:
        details = "; ".join(f"{row.item.pack_id}: {row.detail}" for row in blocked)
        raise ValueError(f"patch rows are blocked: {details}")
    status, assets_edited = summarize_apply_report(apply_report_path)
    voice_cases = infer_voice_cases(voice_cases_path, patch_rows)
    return VoiceHandoff(
        patch_rows=patch_rows,
        voice_cases=voice_cases,
        apply_report_status=status,
        apply_report_assets_edited=assets_edited,
    )


def summarize_apply_report(path: Path) -> tuple[str, str]:
    if not path.is_file():
        return "missing", "unknown"
    text = path.read_text(encoding="utf-8")
    ready = int_match(r"ready=(\d+)", text)
    applied = int_match(r"applied=(\d+)", text)
    blocked = int_match(r"blocked=(\d+)", text)
    if blocked:
        status = "needs_review"
    elif applied > 0 and ready == 0:
        status = "applied"
    elif ready > 0 or applied > 0:
        status = "ready"
    else:
        status = "needs_review"
    assets_edited = "no" if "Assets edited: no" in text else "yes"
    return status, assets_edited


def load_voice_cases(path: Path, case_names: list[str]) -> list[VoiceReplayCase]:
    rows = load_all_voice_cases(path)
    missing = [name for name in case_names if name not in rows]
    if missing:
        raise ValueError(f"voice replay cases missing: {missing}")
    return [rows[name] for name in case_names]


def infer_voice_cases(path: Path, patch_rows: list) -> list[VoiceReplayCase]:
    rows = load_all_voice_cases(path)
    selected: list[VoiceReplayCase] = []
    selected_names: set[str] = set()
    for row in patch_rows:
        pack_slug = str(row.item.pack_id).removeprefix("community.")
        source_refs = set(row.item.golden_row.get("source_refs") or [])
        candidates = [
            case
            for case in rows.values()
            if case.pack_id == pack_slug and case.expected_source in source_refs
        ]
        match = next(
            (
                case
                for case in candidates
                if "observed" in case.case_name and case.case_name not in selected_names
            ),
            None,
        )
        if match is None:
            match = next((case for case in candidates if case.case_name not in selected_names), None)
        if match is None and candidates:
            match = candidates[0]
        if match is None:
            raise ValueError(f"voice replay case not found for {row.item.pack_id} / {sorted(source_refs)}")
        selected.append(match)
        selected_names.add(match.case_name)
    return selected


def load_all_voice_cases(path: Path) -> dict[str, VoiceReplayCase]:
    if not path.is_file():
        raise ValueError(f"voice cases file not found: {path}")
    rows: dict[str, VoiceReplayCase] = {}
    with path.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        for row in reader:
            case_name = row.get("case_name", "")
            rows[case_name] = VoiceReplayCase(
                case_name=case_name,
                pack_id=row.get("pack_id", ""),
                label=row.get("label", ""),
                spoken_prompt=row.get("spoken_prompt", ""),
                expected_stage=row.get("expected_stage", ""),
                expected_answer_type=row.get("expected_answer_type", ""),
                expected_source=row.get("expected_source", ""),
                notes=row.get("notes", ""),
            )
    return rows


def render_markdown(
    handoff: VoiceHandoff,
    packet_path: Path,
    apply_report_path: Path,
    voice_cases_path: Path,
) -> str:
    apply_module = load_apply_module()
    case_filter = ",".join(case.case_name for case in handoff.voice_cases)
    lines = [
        "# GKP ASR Patch And Voice Replay Handoff",
        "",
        f"- Review packet: `{display_path(packet_path)}`",
        f"- Apply dry-run: `{display_path(apply_report_path)}`",
        f"- Voice cases: `{display_path(voice_cases_path)}`",
        f"- Patch rows: {len(handoff.patch_rows)}",
        f"- Voice replay cases: {len(handoff.voice_cases)}",
        f"- Apply report status: `{handoff.apply_report_status}`",
        f"- Apply report assets edited: `{handoff.apply_report_assets_edited}`",
        "",
        "## Approval Boundary",
        "",
        "Do not apply GKP asset changes until a human reviewer approves the exact alias and golden rows in the review packet. If these rows are already `applied`, skip the apply command and use this handoff only for device replay. This handoff does not edit GKP assets and does not play audio.",
        "",
        "Exact apply command after approval:",
        "",
        "```bash",
        "python3 scripts/gkp_patch_apply_review_packet.py \\",
        "  --packet docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md \\",
        "  --output docs/qa-feedback/gkp-patch-apply-result-20260601-hotkey-voice.md \\",
        "  --apply \\",
        f"  --approval \"{apply_module.REQUIRED_APPROVAL}\" \\",
        "  --strict",
        "```",
        "",
        "## Patch Rows",
        "",
        "| Pack | Alias Term | Canonical | Entity | Golden QA | Status |",
        "|---|---|---|---|---|---|",
    ]
    for row in handoff.patch_rows:
        alias = row.item.alias_row
        golden = row.item.golden_row
        lines.append(
            f"| `{escape_cell(row.item.pack_id)}` | {escape_cell(str(alias.get('term') or ''))} | "
            f"{escape_cell(str(alias.get('canonical_term') or ''))} | "
            f"`{escape_cell(str(alias.get('entity_id') or ''))}` | "
            f"`{escape_cell(str(golden.get('qa_id') or ''))}` | `{escape_cell(row.status)}` |"
        )
    lines.extend(
        [
            "",
            "## Regression Commands",
            "",
            "Run the local post-approval gate after applying rows:",
            "",
            "```bash",
            "RUN_REPORTS=1 ./scripts/gkp_patch_regression_gate.sh",
            "```",
            "",
            f"After installing the patched Debug APK on RG476H and loading the target games, replay the {len(handoff.voice_cases)} failed voice row(s):",
            "",
            "```bash",
            "RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 \\",
            f"CASE_FILTER={case_filter} \\",
            "VOICE=Tingting SAY_RATE=96 PRE_SPEAK_SECONDS=3 POST_CASE_SECONDS=10 \\",
            "POLL_ATTEMPTS=40 POLL_INTERVAL_SECONDS=2 READY_ATTEMPTS=20 READY_INTERVAL_SECONDS=1 STRICT=1 \\",
            "./scripts/hotkey_voice_qa_batch.sh",
            "```",
            "",
            "Capture evidence after replay:",
            "",
            "```bash",
            "./scripts/rc_device_evidence.sh",
            "python3 scripts/m18_status_report.py --output docs/qa-feedback/m18-status-report.md",
            "```",
            "",
            "## Voice Replay Cases",
            "",
            "| Case | Label | Spoken Prompt | Expected Stage | Expected Answer Type | Expected Source | Notes |",
            "|---|---|---|---|---|---|---|",
        ]
    )
    for case in handoff.voice_cases:
        lines.append(
            f"| `{escape_cell(case.case_name)}` | `{escape_cell(case.label)}` | "
            f"{escape_cell(case.spoken_prompt)} | `{escape_cell(case.expected_stage)}` | "
            f"`{escape_cell(case.expected_answer_type)}` | `{escape_cell(case.expected_source)}` | "
            f"{escape_cell(case.notes)} |"
        )
    lines.extend(
        [
            "",
            "## Pass Criteria",
            "",
            "- Each replay row submits a fresh `hotkey_voice` request.",
            "- `pipeline_stage=evidence` for each replay row.",
            "- `llm_status=skipped` for each replay row.",
            "- Source ids match the expected source for each replay row.",
            "- `docs/qa-feedback/gkp-quality-backlog.md` is regenerated from the new evidence and no longer lists these ASR variants as open.",
            "",
        ]
    )
    return "\n".join(lines)


def render_json(
    handoff: VoiceHandoff,
    packet_path: Path,
    apply_report_path: Path,
    voice_cases_path: Path,
    markdown_output_path: Path,
) -> dict[str, Any]:
    apply_module = load_apply_module()
    case_filter = ",".join(case.case_name for case in handoff.voice_cases)
    status = (
        "ready"
        if handoff.apply_report_status in {"ready", "applied"}
        and handoff.apply_report_assets_edited == "no"
        and len(handoff.patch_rows) == len(handoff.voice_cases)
        else "needs_review"
    )
    return {
        "schema_version": 1,
        "status": status,
        "assets_edited_by_handoff": False,
        "paths": {
            "markdown_output": display_path(markdown_output_path),
            "review_packet": display_path(packet_path),
            "apply_report": display_path(apply_report_path),
            "voice_cases": display_path(voice_cases_path),
        },
        "counts": {
            "patch_rows": len(handoff.patch_rows),
            "voice_cases": len(handoff.voice_cases),
        },
        "apply_report": {
            "status": handoff.apply_report_status,
            "assets_edited": handoff.apply_report_assets_edited,
        },
        "case_filter": case_filter,
        "approval": {
            "required": True,
            "required_phrase": apply_module.REQUIRED_APPROVAL,
        },
        "patch_rows": [
            {
                "pack_id": row.item.pack_id,
                "pack_dir": row.item.pack_dir_text,
                "status": row.status,
                "detail": row.detail,
                "alias_row": row.item.alias_row,
                "golden_row": row.item.golden_row,
            }
            for row in handoff.patch_rows
        ],
        "voice_cases": [
            {
                "case_name": case.case_name,
                "pack_id": case.pack_id,
                "label": case.label,
                "spoken_prompt": case.spoken_prompt,
                "expected_stage": case.expected_stage,
                "expected_answer_type": case.expected_answer_type,
                "expected_source": case.expected_source,
                "notes": case.notes,
            }
            for case in handoff.voice_cases
        ],
    }


def load_apply_module():
    spec = importlib.util.spec_from_file_location("gkp_patch_apply_review_packet", APPLY_SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def int_match(pattern: str, text: str) -> int:
    match = re.search(pattern, text)
    return int(match.group(1)) if match else 0


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
