#!/usr/bin/env python3
"""Aggregate M18 Eval Lab reports into one status snapshot."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_GKP_REPORT = ROOT / "docs/qa-feedback/m18-eval-report.md"
DEFAULT_BACKLOG = ROOT / "docs/qa-feedback/gkp-quality-backlog.md"
DEFAULT_BACKLOG_TRIAGE = ROOT / "docs/qa-feedback/gkp-backlog-triage-report.md"
DEFAULT_PATCH_AUDIT = ROOT / "docs/qa-feedback/gkp-patch-proposal-audit.md"
DEFAULT_PATCH_PACKET = ROOT / "docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md"
DEFAULT_PATCH_APPLY_REPORT = ROOT / "docs/qa-feedback/gkp-patch-apply-dry-run-20260601-hotkey-voice.md"
DEFAULT_GKP_ASSET_GUARD = ROOT / "docs/qa-feedback/gkp-asset-mutation-guard.md"
DEFAULT_ASR_VOICE_HANDOFF = ROOT / "docs/qa-feedback/gkp-asr-patch-voice-replay-handoff.md"
DEFAULT_HOTKEY_VOICE_REPORT = ROOT / "docs/qa-feedback/hotkey-voice-matrix-report.md"
DEFAULT_COMMAND_CONTRACT_AUDIT = ROOT / "docs/qa-feedback/m18-command-contract-audit.md"
DEFAULT_QUALITY_LOOP_HANDOFF = ROOT / "docs/qa-feedback/m18-quality-loop-handoff.md"


@dataclass(frozen=True)
class StatusRow:
    area: str
    status: str
    evidence: str
    detail: str


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gkp-report", type=Path, default=DEFAULT_GKP_REPORT)
    parser.add_argument("--backlog", type=Path, default=DEFAULT_BACKLOG)
    parser.add_argument("--backlog-triage", type=Path, default=DEFAULT_BACKLOG_TRIAGE)
    parser.add_argument("--patch-audit", type=Path, default=DEFAULT_PATCH_AUDIT)
    parser.add_argument("--patch-packet", type=Path, default=DEFAULT_PATCH_PACKET)
    parser.add_argument("--patch-apply-report", type=Path, default=DEFAULT_PATCH_APPLY_REPORT)
    parser.add_argument("--gkp-asset-guard", type=Path, default=DEFAULT_GKP_ASSET_GUARD)
    parser.add_argument("--asr-voice-handoff", type=Path, default=DEFAULT_ASR_VOICE_HANDOFF)
    parser.add_argument("--hotkey-voice-report", type=Path, default=DEFAULT_HOTKEY_VOICE_REPORT)
    parser.add_argument("--command-contract-audit", type=Path, default=DEFAULT_COMMAND_CONTRACT_AUDIT)
    parser.add_argument("--quality-loop-handoff", type=Path, default=DEFAULT_QUALITY_LOOP_HANDOFF)
    parser.add_argument("--output", type=Path)
    parser.add_argument(
        "--strict",
        action="store_true",
        help="Exit nonzero unless every aggregate row is pass.",
    )
    args = parser.parse_args()

    rows = [
        summarize_gkp_eval(args.gkp_report),
        summarize_gap_backlog(args.backlog, args.backlog_triage),
        summarize_patch_proposal_audit(args.patch_audit),
        summarize_patch_review_packet(args.patch_packet),
        summarize_patch_apply_dry_run(args.patch_apply_report),
        summarize_gkp_asset_mutation_guard(args.gkp_asset_guard),
        summarize_asr_voice_handoff(args.asr_voice_handoff),
        summarize_hotkey_voice_matrix(args.hotkey_voice_report),
        summarize_command_contract_audit(args.command_contract_audit),
        summarize_quality_loop_handoff(args.quality_loop_handoff),
    ]
    markdown = render_markdown(rows)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(markdown, encoding="utf-8")
    else:
        print(markdown)

    print("OK M18 status report: " + ", ".join(f"{row.area}={row.status}" for row in rows))
    if args.strict and any(row.status != "pass" for row in rows):
        return 1
    return 0


def summarize_gkp_eval(path: Path) -> StatusRow:
    text = read_optional(path)
    if text is None:
        return missing_row("GKP coverage", path)
    packs = int_match(r"- Packs:\s*(\d+)", text)
    fail_count = len(re.findall(r"=fail\b|\|\s*`[^`]+`\s*\|\s*fail\s*\|", text))
    warn_count = len(re.findall(r"=warn\b|\|\s*`[^`]+`\s*\|\s*warn\s*\|", text))
    if fail_count:
        status = "fail"
    elif warn_count:
        status = "warn"
    else:
        status = "pass"
    return StatusRow(
        area="GKP coverage",
        status=status,
        evidence=display_path(path),
        detail=f"packs={packs}; fail_lanes={fail_count}; warn_lanes={warn_count}",
    )


def summarize_gap_backlog(path: Path, triage_path: Path = DEFAULT_BACKLOG_TRIAGE) -> StatusRow:
    text = read_optional(path)
    if text is None:
        return missing_row("GKP backlog", path)
    items = int_match(r"- Items:\s*(\d+)", text)
    tag_counts = {
        tag: len(re.findall(rf"\b{re.escape(tag)}\b", text))
        for tag in [
            "voice_lifecycle_gap",
            "translation_gap",
            "coverage_gap",
            "alias_gap",
            "ranking_gap",
            "asr_variant",
            "spoiler_gate_gap",
        ]
    }
    open_tags = ", ".join(
        f"{tag}={count}"
        for tag, count in tag_counts.items()
        if count
    )
    raw_tag_detail = f"raw_tags={open_tags}" if open_tags else "raw_tags=none"
    triage_detail = summarize_backlog_triage(triage_path)
    if triage_detail:
        detail = f"items={items}; {triage_detail}; {raw_tag_detail}"
        status = "pass" if items == 0 or triage_is_closed(triage_detail) else "open"
    else:
        detail = f"items={items}; {raw_tag_detail}"
        status = "pass" if items == 0 else "open"
    return StatusRow(
        area="GKP backlog",
        status=status,
        evidence=display_path(path),
        detail=detail,
    )


def summarize_backlog_triage(path: Path) -> str:
    text = read_optional(path)
    if text is None:
        return ""
    items = int_match(r"- Items:\s*(\d+)", text)
    categories = summary_line_value(text, "Categories")
    statuses = summary_line_value(text, "Status")
    status_counts = parse_count_list(statuses)
    review_packet_rows = (
        status_counts.get("covered_by_review_packet", 0)
        + status_counts.get("covered_by_applied_patch", 0)
    )
    triage_open = status_counts.get("open", 0)
    parts = [
        f"triage_items={items}",
        f"triage_status={statuses or 'none'}",
        f"triage_categories={categories or 'none'}",
        f"review_packet_rows={review_packet_rows}",
        f"triage_open={triage_open}",
        "manual_asr_approval_required=no",
    ]
    return "; ".join(parts)


def triage_is_closed(detail: str) -> bool:
    open_items = int_match(r"triage_open=(\d+)", detail)
    return open_items == 0


def summary_line_value(text: str, label: str) -> str:
    match = re.search(rf"^- {re.escape(label)}:\s*([^\n]+)", text, flags=re.MULTILINE)
    return match.group(1).strip() if match else ""


def parse_count_list(value: str) -> dict[str, int]:
    counts: dict[str, int] = {}
    for part in value.split(","):
        key, _, count = part.strip().partition("=")
        if not key or not count:
            continue
        try:
            counts[key] = int(count)
        except ValueError:
            continue
    return counts


def summarize_patch_proposal_audit(path: Path) -> StatusRow:
    text = read_optional(path)
    if text is None:
        return missing_row("GKP patch proposals", path)
    rows = int_match(r"- Rows:\s*(\d+)", text)
    passed = int_match(r"pass=(\d+)", text)
    failed = int_match(r"fail=(\d+)", text)
    if failed:
        status = "fail"
    elif rows > 0 and passed == rows:
        status = "pass"
    else:
        status = "open"
    return StatusRow(
        area="GKP patch proposals",
        status=status,
        evidence=display_path(path),
        detail=f"rows={rows}; pass={passed}; fail={failed}",
    )


def summarize_patch_review_packet(path: Path) -> StatusRow:
    text = read_optional(path)
    if text is None:
        return missing_row("GKP patch review packet", path)
    rows = int_match(r"- Rows:\s*(\d+)", text)
    ready = int_match(r"ready=(\d+)", text)
    applied = int_match(r"applied=(\d+)", text)
    blocked = int_match(r"blocked=(\d+)", text)
    assets_edited = "Assets edited: no" not in text
    if blocked or assets_edited:
        status = "fail"
    elif rows > 0 and ready + applied == rows:
        status = "pass"
    else:
        status = "open"
    detail = (
        f"rows={rows}; ready={ready}; applied={applied}; blocked={blocked}; "
        f"assets_edited={'yes' if assets_edited else 'no'}"
    )
    return StatusRow(
        area="GKP patch review packet",
        status=status,
        evidence=display_path(path),
        detail=detail,
    )


def summarize_patch_apply_dry_run(path: Path) -> StatusRow:
    text = read_optional(path)
    if text is None:
        return missing_row("GKP patch apply dry-run", path)
    rows = int_match(r"- Rows:\s*(\d+)", text)
    ready = int_match(r"ready=(\d+)", text)
    applied = int_match(r"applied=(\d+)", text)
    blocked = int_match(r"blocked=(\d+)", text)
    mode_match = re.search(r"- Mode:\s*`([^`]+)`", text)
    mode = mode_match.group(1) if mode_match else "unknown"
    assets_edited = "Assets edited: no" not in text
    if blocked or assets_edited or mode != "dry_run":
        status = "fail"
    elif rows > 0 and ready + applied == rows:
        status = "pass"
    else:
        status = "open"
    detail = (
        f"rows={rows}; ready={ready}; applied={applied}; blocked={blocked}; "
        f"mode={mode}; assets_edited={'yes' if assets_edited else 'no'}"
    )
    return StatusRow(
        area="GKP patch apply dry-run",
        status=status,
        evidence=display_path(path),
        detail=detail,
    )


def summarize_gkp_asset_mutation_guard(path: Path) -> StatusRow:
    text = read_optional(path)
    if text is None:
        return missing_row("GKP asset mutation guard", path)
    guard_match = re.search(r"- Guard status:\s*`([^`]+)`", text)
    mode_match = re.search(r"- Mode:\s*`([^`]+)`", text)
    dirty = int_match(r"- Dirty GKP assets:\s*(\d+)", text)
    expected = int_match(r"- Expected patch assets:\s*(\d+)", text)
    unexpected = int_match(r"- Unexpected dirty assets:\s*(\d+)", text)
    report_present_match = re.search(r"- Apply report present:\s*`([^`]+)`", text)
    report_mode_match = re.search(r"- Apply report mode:\s*`([^`]+)`", text)
    guard = guard_match.group(1) if guard_match else "unknown"
    mode = mode_match.group(1) if mode_match else "unknown"
    report_present = report_present_match.group(1) if report_present_match else "unknown"
    report_mode = report_mode_match.group(1) if report_mode_match else "unknown"
    status = "pass" if guard == "pass" else "fail"
    detail = (
        f"guard={guard}; mode={mode}; dirty={dirty}; expected={expected}; "
        f"unexpected={unexpected}; apply_report={report_present}; apply_mode={report_mode}"
    )
    return StatusRow(
        area="GKP asset mutation guard",
        status=status,
        evidence=display_path(path),
        detail=detail,
    )


def summarize_asr_voice_handoff(path: Path) -> StatusRow:
    text = read_optional(path)
    if text is None:
        return missing_row("GKP ASR voice replay handoff", path)
    patch_rows = int_match(r"- Patch rows:\s*(\d+)", text)
    voice_cases = int_match(r"- Voice replay cases:\s*(\d+)", text)
    apply_status_match = re.search(r"- Apply report status:\s*`([^`]+)`", text)
    assets_match = re.search(r"- Apply report assets edited:\s*`([^`]+)`", text)
    apply_status = apply_status_match.group(1) if apply_status_match else "unknown"
    assets_edited = assets_match.group(1) if assets_match else "unknown"
    has_apply_command = "gkp_patch_apply_review_packet.py" in text and "--apply" in text
    has_replay_command = "hotkey_voice_qa_batch.sh" in text and re.search(r"CASE_FILTER=[^\s]+", text) is not None
    has_pass_criteria = "## Pass Criteria" in text
    status = (
        "pass"
        if patch_rows > 0 and voice_cases == patch_rows and apply_status in {"ready", "applied"} and assets_edited == "no"
        and has_apply_command and has_replay_command and has_pass_criteria
        else "open"
    )
    detail = (
        f"patch_rows={patch_rows}; voice_cases={voice_cases}; apply_report={apply_status}; "
        f"assets_edited={assets_edited}; apply_command={'yes' if has_apply_command else 'no'}; "
        f"replay_command={'yes' if has_replay_command else 'no'}; pass_criteria={'yes' if has_pass_criteria else 'no'}"
    )
    return StatusRow(
        area="GKP ASR voice replay handoff",
        status=status,
        evidence=display_path(path),
        detail=detail,
    )


def summarize_hotkey_voice_matrix(path: Path) -> StatusRow:
    text = read_optional(path)
    if text is None:
        return missing_row("Hotkey voice matrix", path)
    total = int_match(r"- Total:\s*(\d+)", text)
    counts = {
        key: int_match(rf"{key}=(\d+)", text)
        for key in ["pass", "fail", "blocked", "not_run", "missing"]
    }
    category_match = re.search(r"- Failure categories:\s*([^\n]+)", text)
    categories = category_match.group(1).strip() if category_match else "unknown"
    results_match = re.search(r"- Results:\s*`([^`]+)`", text)
    results = results_match.group(1) if results_match else "unknown"
    strict_match = re.search(r"- Strict pass:\s*`([^`]+)`", text)
    strict_pass = (
        strict_match.group(1)
        if strict_match
        else ("yes" if total > 0 and counts.get("pass", 0) == total else "no")
    )
    observed_rows = counts.get("pass", 0) + counts.get("fail", 0)
    status = "pass" if total > 0 and observed_rows > 0 and results not in {"none", "unknown"} else "open"
    detail = (
        f"gate=observational; total={total}; observed_rows={observed_rows}; strict_pass={strict_pass}; "
        + "; ".join(f"{key}={counts[key]}" for key in ["pass", "fail", "blocked", "not_run", "missing"])
        + f"; categories={categories}; results={results}"
    )
    return StatusRow(
        area="Hotkey voice matrix",
        status=status,
        evidence=display_path(path),
        detail=detail,
    )


def summarize_screen_translation(path: Path) -> StatusRow:
    text = read_optional(path)
    if text is None:
        return missing_row("Screen translation matrix", path)
    total = int_match(r"- Total:\s*(\d+)", text)
    counts = {
        key: int_match(rf"{key}=(\d+)", text)
        for key in ["pass", "fail", "blocked", "not_run", "missing"]
    }
    note_issues = int_match(r"- Note issues:\s*(\d+)", text)
    status = "pass" if total > 0 and counts.get("pass", 0) == total and note_issues == 0 else "open"
    detail = "; ".join(f"{key}={counts[key]}" for key in ["pass", "fail", "blocked", "not_run", "missing"])
    detail += f"; note_issues={note_issues}"
    return StatusRow(
        area="Screen translation matrix",
        status=status,
        evidence=display_path(path),
        detail=f"total={total}; {detail}",
    )


def summarize_screen_manual_packet(path: Path) -> StatusRow:
    text = read_optional(path)
    if text is None:
        return missing_row("Screen translation manual packet", path)
    rows = int_match(r"- Rows:\s*(\d+)", text)
    current_match = re.search(r"- Current status:\s*([^\n]+)", text)
    current = current_match.group(1).strip() if current_match else "unknown"
    has_protocol = "## Test Protocol" in text
    has_checklist = "## Case Checklist" in text
    has_followup = "## Follow-Up Commands" in text
    status = "pass" if rows > 0 and has_protocol and has_checklist and has_followup else "open"
    detail = (
        f"rows={rows}; current={current}; protocol={'yes' if has_protocol else 'no'}; "
        f"checklist={'yes' if has_checklist else 'no'}; followup={'yes' if has_followup else 'no'}"
    )
    return StatusRow(
        area="Screen translation manual packet",
        status=status,
        evidence=display_path(path),
        detail=detail,
    )


def summarize_release_checklist(path: Path) -> StatusRow:
    text = read_optional(path)
    if text is None:
        return missing_row("Release checklist", path)
    checked = len(re.findall(r"^- \[x\] ", text, flags=re.MULTILINE))
    unchecked = len(re.findall(r"^- \[ \] ", text, flags=re.MULTILINE))
    unchecked_labels = [
        line.removeprefix("- [ ] ").strip().rstrip(".")
        for line in text.splitlines()
        if line.startswith("- [ ] ")
    ]
    label_summary = "; ".join(unchecked_labels[:4])
    if len(unchecked_labels) > 4:
        label_summary += f"; +{len(unchecked_labels) - 4} more"
    return StatusRow(
        area="Release checklist",
        status="pass" if unchecked == 0 else "open",
        evidence=display_path(path),
        detail=f"checked={checked}; unchecked={unchecked}" + (f"; open={label_summary}" if label_summary else ""),
    )


def summarize_content_rights_packet(path: Path) -> StatusRow:
    text = read_optional(path)
    if text is None:
        return missing_row("GKP content rights packet", path)
    machine_match = re.search(r"- Machine audit:\s*`([^`]+)`", text)
    human_match = re.search(r"- Human release checkbox:\s*`([^`]+)`", text)
    machine = machine_match.group(1) if machine_match else "unknown"
    human = human_match.group(1) if human_match else "unknown"
    packs = int_match(r"- Bundled packs:\s*(\d+)", text)
    knowledge_files = int_match(r"- Knowledge files:\s*(\d+)", text)
    license_files = int_match(r"- License files:\s*(\d+)", text)
    citation_files = int_match(r"- Citation files:\s*(\d+)", text)
    has_review_checklist = "## Review Checklist" in text
    status = (
        "pass"
        if machine == "pass" and packs > 0 and knowledge_files > 0 and license_files > 0
        and citation_files > 0 and has_review_checklist
        else "open"
    )
    detail = (
        f"machine={machine}; human_checkbox={human}; packs={packs}; "
        f"knowledge_files={knowledge_files}; license_files={license_files}; "
        f"citation_files={citation_files}; checklist={'yes' if has_review_checklist else 'no'}"
    )
    return StatusRow(
        area="GKP content rights packet",
        status=status,
        evidence=display_path(path),
        detail=detail,
    )


def summarize_release_checklist_guard(path: Path) -> StatusRow:
    text = read_optional(path)
    if text is None:
        return missing_row("Release checklist guard", path)
    guard_match = re.search(r"- Guard status:\s*`([^`]+)`", text)
    closure_match = re.search(r"- Closure status:\s*`([^`]+)`", text)
    ready_match = re.search(r"- Ready items:\s*([^\n]+)", text)
    unsafe = int_match(r"- Unsafe checked items:\s*(\d+)", text)
    apply_match = re.search(r"- Apply allowed:\s*`([^`]+)`", text)
    guard = guard_match.group(1) if guard_match else "unknown"
    closure = closure_match.group(1) if closure_match else "unknown"
    ready = ready_match.group(1).strip() if ready_match else "unknown"
    apply_allowed = apply_match.group(1) if apply_match else "unknown"
    status = "pass" if guard == "pass" and unsafe == 0 else "fail"
    return StatusRow(
        area="Release checklist guard",
        status=status,
        evidence=display_path(path),
        detail=f"guard={guard}; closure={closure}; ready={ready}; unsafe={unsafe}; apply_allowed={apply_allowed}",
    )


def summarize_command_contract_audit(path: Path) -> StatusRow:
    text = read_optional(path)
    if text is None:
        return missing_row("Command contract audit", path)
    counts_match = re.search(
        r"- Status counts:\s*pass=(\d+),\s*fail=(\d+),\s*missing=(\d+)",
        text,
    )
    passed = int(counts_match.group(1)) if counts_match else 0
    failed = int(counts_match.group(2)) if counts_match else 0
    missing = int(counts_match.group(3)) if counts_match else 0
    inputs = int_match(r"- Inputs:\s*(\d+)", text)
    if failed:
        status = "fail"
    elif missing:
        status = "missing"
    elif passed > 0:
        status = "pass"
    else:
        status = "open"
    return StatusRow(
        area="Command contract audit",
        status=status,
        evidence=display_path(path),
        detail=f"inputs={inputs}; pass={passed}; fail={failed}; missing={missing}",
    )


def summarize_quality_loop_handoff(path: Path) -> StatusRow:
    json_path = path if path.suffix == ".json" else path.with_suffix(".json")
    if json_path.is_file():
        return summarize_quality_loop_handoff_json(json_path)

    text = read_optional(path)
    if text is None:
        return missing_row("M18 quality loop handoff", path)
    loop_match = re.search(r"- Loop status:\s*`([^`]+)`", text)
    ready_match = re.search(r"- Ready actions:\s*([^\n]+)", text)
    blocked_match = re.search(r"- Blocked actions:\s*([^\n]+)", text)
    open_match = re.search(r"- Open areas:\s*([^\n]+)", text)
    loop_status = loop_match.group(1) if loop_match else "unknown"
    ready = ready_match.group(1).strip() if ready_match else "unknown"
    blocked = blocked_match.group(1).strip() if blocked_match else "unknown"
    open_areas = open_match.group(1).strip() if open_match else "unknown"
    required_fragments = (
        "## Preview-First Backlog Commands",
        "--merge-existing-backlog docs/qa-feedback/gkp-quality-backlog.md",
        "--output build/m18-latest-request-backlog-preview.md",
        "--output build/m18-voice-backlog-preview.md",
        "--output build/m18-manual-notes-backlog-preview.md",
        "## Fix Acceptance Rules",
        "Every accepted GKP fix needs source ids",
        "Voice-originated fixes require real-device replay",
        "Do not add new game content until the current six bundled packs",
        "GKP assets edited by this handoff: no",
    )
    missing = [fragment for fragment in required_fragments if fragment not in text]
    status = "pass" if not missing and loop_status != "unknown" else "open"
    detail = (
        f"loop_status={loop_status}; ready={ready}; blocked={blocked}; "
        f"open_areas={open_areas}; missing_fragments={len(missing)}"
    )
    return StatusRow(
        area="M18 quality loop handoff",
        status=status,
        evidence=display_path(path),
        detail=detail,
    )


def summarize_quality_loop_handoff_json(path: Path) -> StatusRow:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        return StatusRow(
            area="M18 quality loop handoff",
            status="fail",
            evidence=display_path(path),
            detail=f"invalid_json={exc}",
        )
    if not isinstance(data, dict):
        return StatusRow(
            area="M18 quality loop handoff",
            status="fail",
            evidence=display_path(path),
            detail="json_root=not_object",
        )

    loop_status = str(data.get("loop_status") or "unknown")
    grouped = data.get("action_ids_by_status")
    ready = joined_json_ids(grouped, "ready")
    blocked = joined_json_ids(grouped, "blocked")
    open_areas = joined_json_list(data.get("open_areas"))
    contract = data.get("contract")
    missing = quality_loop_json_missing_contracts(data)
    status = "pass" if not missing and loop_status != "unknown" else "open"
    detail = (
        f"loop_status={loop_status}; ready={ready}; blocked={blocked}; "
        f"open_areas={open_areas}; missing_fragments={len(missing)}"
    )
    if not isinstance(contract, dict):
        detail += "; contract=missing"
    return StatusRow(
        area="M18 quality loop handoff",
        status=status,
        evidence=display_path(path),
        detail=detail,
    )


def quality_loop_json_missing_contracts(data: dict[str, Any]) -> list[str]:
    missing: list[str] = []
    if data.get("assets_edited_by_handoff") is not False:
        missing.append("assets_edited_by_handoff=false")
    grouped = data.get("action_ids_by_status")
    if not isinstance(grouped, dict):
        missing.append("action_ids_by_status")
    else:
        for status in ("ready", "blocked", "done"):
            if not isinstance(grouped.get(status), list):
                missing.append(f"action_ids_by_status.{status}")
    open_areas = data.get("open_areas")
    if not isinstance(open_areas, list):
        missing.append("open_areas")
    state = data.get("current_loop_state")
    if not isinstance(state, dict):
        missing.append("current_loop_state")
    else:
        for key in ("gkp_backlog", "hotkey_voice_matrix"):
            if not str(state.get(key) or "").strip():
                missing.append(f"current_loop_state.{key}")
    commands = data.get("preview_backlog_commands")
    paths = data.get("paths")
    expected_backlog = (
        str(paths.get("backlog"))
        if isinstance(paths, dict) and str(paths.get("backlog") or "").strip()
        else display_path(DEFAULT_BACKLOG)
    )
    if not isinstance(commands, list):
        missing.append("preview_backlog_commands")
    else:
        command_ids = {
            str(command.get("id") or "")
            for command in commands
            if isinstance(command, dict)
        }
        for command_id in ("latest_request", "voice_qa", "manual_notes_preview"):
            if command_id not in command_ids:
                missing.append(f"preview_backlog_commands.{command_id}")
        for command in commands:
            if not isinstance(command, dict):
                continue
            command_id = str(command.get("id") or "")
            if command_id == "manual_notes_template":
                continue
            if str(command.get("merge_existing_backlog") or "") != expected_backlog:
                missing.append(f"{command_id}.merge_existing_backlog")
    contract = data.get("contract")
    if not isinstance(contract, dict):
        missing.append("contract")
    else:
        for key in (
            "preview_first_backlog_imports",
            "merge_existing_backlog",
            "latest_request_preview",
            "voice_qa_preview",
            "manual_notes_preview",
            "fix_acceptance_rules",
            "voice_replay_required",
            "no_new_games_until_green_rc",
        ):
            if contract.get(key) is not True:
                missing.append(f"contract.{key}")
    return missing


def joined_json_ids(grouped: Any, status: str) -> str:
    if not isinstance(grouped, dict):
        return "unknown"
    return joined_json_list(grouped.get(status))


def joined_json_list(value: Any) -> str:
    if not isinstance(value, list):
        return "unknown"
    items = [str(item) for item in value if str(item).strip()]
    return ", ".join(items) if items else "none"


def missing_row(area: str, path: Path) -> StatusRow:
    return StatusRow(
        area=area,
        status="missing",
        evidence=display_path(path),
        detail="report file not found",
    )


def read_optional(path: Path) -> str | None:
    if not path.is_file():
        return None
    return path.read_text(encoding="utf-8", errors="ignore")


def int_match(pattern: str, text: str) -> int:
    match = re.search(pattern, text)
    return int(match.group(1)) if match else 0


def display_path(path: Path) -> str:
    resolved = path.resolve()
    try:
        return resolved.relative_to(ROOT).as_posix()
    except ValueError:
        return str(path)


def render_markdown(rows: list[StatusRow]) -> str:
    lines = [
        "# M18 Eval Lab Status Report",
        "",
        "| Area | Status | Evidence | Detail |",
        "|---|---|---|---|",
    ]
    for row in rows:
        lines.append(
            f"| {escape_cell(row.area)} | `{escape_cell(row.status)}` | "
            f"`{escape_cell(row.evidence)}` | {escape_cell(row.detail)} |"
        )
    blockers = [row for row in rows if row.status != "pass"]
    lines.append("")
    lines.append("## Open Work")
    if not blockers:
        lines.append("")
        lines.append("- All aggregate M18 rows are passing.")
    else:
        lines.append("")
        for row in blockers:
            lines.append(f"- `{row.area}` is `{row.status}`: {row.detail}.")
    actions = [recommended_action(row) for row in blockers]
    actions = [action for action in actions if action]
    if actions:
        lines.append("")
        lines.append("## Next Actions")
        lines.append("")
        for action in actions:
            lines.append(f"- {action}")
    return "\n".join(lines) + "\n"


def recommended_action(row: StatusRow) -> str:
    if row.area == "GKP backlog":
        return (
            "Review `docs/qa-feedback/gkp-backlog-triage-report.md` first to separate ASR rows already "
            "covered by non-blocking review-packet artifacts from device rerun rows and policy boundaries that already have goldens; "
            "do not add duplicate policy goldens when the triage report shows `policy_golden_existing`; "
            "manual ASR approval is no longer an M18 gate."
        )
    if row.area == "GKP patch apply dry-run":
        return (
            "Regenerate `docs/qa-feedback/gkp-patch-apply-dry-run-20260601-hotkey-voice.md` "
            "with `scripts/gkp_patch_apply_review_packet.py` and keep `Assets edited: no` until approval."
        )
    if row.area == "GKP asset mutation guard":
        return (
            "Run `python3 scripts/gkp_asset_mutation_guard.py --strict`; if it fails, revert or approve "
            "the unexpected GKP asset edits before continuing."
        )
    if row.area == "Hotkey voice matrix":
        return (
            "Use `docs/qa-feedback/hotkey-voice-matrix-report.md` to inspect the current playback report; "
            "rerun rows when device conditions change or convert repeated misses into backlog entries."
        )
    if row.area == "Command contract audit":
        return (
            "Run `python3 scripts/m18_command_contract_audit.py --strict`; fix any generated command "
            "that applies placeholder screen evidence or uses stale release-checklist guard flags."
        )
    if row.status == "missing":
        return f"Generate or restore `{row.evidence}`."
    return ""


def escape_cell(value: str) -> str:
    return value.replace("|", "\\|").replace("\n", " ")


if __name__ == "__main__":
    raise SystemExit(main())
