#!/usr/bin/env python3
"""Generate input templates for the ready M18 human/manual gates."""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_QUEUE_JSON = ROOT / "docs/qa-feedback/m18-next-action-queue.json"
DEFAULT_SCREEN_CASES = ROOT / "scripts/screen_translation_eval_cases.tsv"
DEFAULT_OUTPUT = ROOT / "docs/qa-feedback/m18-manual-gate-intake.md"
DEFAULT_JSON_OUTPUT = ROOT / "docs/qa-feedback/m18-manual-gate-intake.json"
DEFAULT_ASR_REVIEW_PACKET = ROOT / "docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md"

ASR_APPROVAL_PHRASE = "I approve gkp patch review packet 20260601 hotkey voice"
CONTENT_RIGHTS_APPROVAL_PHRASE = "I confirm gkp content rights human spot check"


@dataclass(frozen=True)
class IntakeSection:
    section_id: str
    title: str
    status: str
    owner: str
    required_input: tuple[str, ...]
    evidence: tuple[str, ...]
    command_templates: tuple[str, ...]
    acceptance: str


@dataclass(frozen=True)
class ScreenCase:
    case_id: str
    game_label: str
    screen_type: str
    trigger_phrase: str
    expected_layout: str
    expected_language: str
    number_policy: str


@dataclass(frozen=True)
class AsrReviewRow:
    pack_id: str
    observed_asr: str
    canonical_term: str
    entity_id: str
    source_refs: tuple[str, ...]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--queue-json", type=Path, default=DEFAULT_QUEUE_JSON)
    parser.add_argument("--screen-cases", type=Path, default=DEFAULT_SCREEN_CASES)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--json-output", type=Path, default=DEFAULT_JSON_OUTPUT)
    parser.add_argument(
        "--strict",
        action="store_true",
        help="Exit nonzero while any manual intake section is ready.",
    )
    args = parser.parse_args()

    try:
        sections = build_sections(args.queue_json, args.screen_cases)
    except ValueError as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(render_markdown(sections), encoding="utf-8")
    args.json_output.parent.mkdir(parents=True, exist_ok=True)
    args.json_output.write_text(json.dumps(render_json(sections), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    counts = status_counts(sections)
    print(
        "OK M18 manual gate intake packet: "
        f"ready={counts.get('ready', 0)}, "
        f"done={counts.get('done', 0)}, "
        f"blocked={counts.get('blocked', 0)}"
    )
    if args.strict and any(section.status == "ready" for section in sections):
        return 1
    return 0


def build_sections(queue_json: Path, screen_cases_path: Path) -> tuple[IntakeSection, ...]:
    queue = load_queue(queue_json)
    actions = action_map_from_queue(queue)
    ready_actions = ready_action_ids(queue, actions)
    sections: list[IntakeSection] = []

    if "approve-asr-patch" in ready_actions:
        sections.append(asr_approval_section(actions["approve-asr-patch"]))
    if "rerun-device-lifecycle-row" in ready_actions:
        sections.append(device_voice_rerun_section(actions["rerun-device-lifecycle-row"]))
    if "run-screen-translation-matrix" in ready_actions:
        screen_cases = load_screen_cases(screen_cases_path)
        sections.append(screen_translation_section(actions["run-screen-translation-matrix"], screen_cases))
    if "complete-content-rights-review" in ready_actions:
        sections.append(content_rights_section(actions["complete-content-rights-review"]))

    if not sections:
        sections.append(
            IntakeSection(
                section_id="manual-gates-complete",
                title="No ready manual gate input is required",
                status="done",
                owner="agent",
                required_input=("All manual gate intake sections are closed.",),
                evidence=(display_path(queue_json),),
                command_templates=("EXPECT_ALL_PASS=1 ./scripts/m18_offline_quality_gate.sh",),
                acceptance="The next action queue reports no ready manual gates.",
            )
        )
    return tuple(sections)


def device_voice_rerun_section(action: dict[str, Any]) -> IntakeSection:
    return IntakeSection(
        section_id="device-voice-lifecycle-rerun",
        title="Device voice lifecycle rerun input",
        status="ready",
        owner=str(action.get("owner", "human/device")),
        required_input=(
            "Connect RG476H, confirm RetroSprite endpoint health, and keep overlay/microphone permissions enabled.",
            "Run the queued Chrono Trigger single-case command exactly as shown.",
            "Record the new `build/hotkey-voice-qa/<timestamp>/results.tsv` path and whether the row submitted a fresh `hotkey_voice` request or still ended in `muted_recovery`.",
            "Do not edit GKP assets from this action; this row is a device lifecycle diagnostic, not a content patch.",
        ),
        evidence=tuple(str(item) for item in action.get("evidence", [])),
        command_templates=(str(action.get("command", "")),),
        acceptance=str(action.get("acceptance", "")),
    )


def asr_approval_section(action: dict[str, Any]) -> IntakeSection:
    packet = asr_review_packet_path(action)
    rows = load_asr_review_rows(packet)
    row_inputs = ["Review the current ASR patch packet rows:"]
    if rows:
        for row in rows:
            sources = ", ".join(row.source_refs) if row.source_refs else "missing source_refs"
            row_inputs.append(
                (
                    f"{row.pack_id}: `{row.observed_asr}` -> `{row.canonical_term}`; "
                    f"entity=`{row.entity_id}`; source_refs=`{sources}`"
                )
            )
    else:
        row_inputs.append(f"Review the exact alias/golden rows listed in `{display_path(packet)}`.")
    row_inputs.extend(
        (
            f"Approve with exactly: {ASR_APPROVAL_PHRASE}",
            "Reject by saying which row is unsafe or incorrect; do not edit GKP assets while rejected.",
            "Preferred path: record the reviewer decision with `scripts/m18_manual_gate_receipt_update.py --section asr-patch-approval --output /tmp/retrosprite-m18-asr-receipt-preview.json`, review the JSON preview, then rerun the same command with `--apply` if it is correct.",
        )
    )
    return IntakeSection(
        section_id="asr-patch-approval",
        title="ASR patch approval input",
        status="ready",
        owner=str(action.get("owner", "human")),
        required_input=tuple(row_inputs),
        evidence=tuple(str(item) for item in action.get("evidence", [])),
        command_templates=(
            "\n".join(
                [
                    "python3 scripts/m18_manual_gate_receipt_update.py \\",
                    "  --section asr-patch-approval \\",
                    "  --decision approved \\",
                    f"  --approval-phrase \"{ASR_APPROVAL_PHRASE}\" \\",
                    "  --reviewer \"<reviewer>\" \\",
                    "  --output /tmp/retrosprite-m18-asr-receipt-preview.json",
                ]
            ),
            "\n".join(
                [
                    "python3 scripts/gkp_patch_apply_review_packet.py \\",
                    "  --packet docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md \\",
                    "  --output docs/qa-feedback/gkp-patch-apply-result-20260601-hotkey-voice.md \\",
                    "  --apply \\",
                    f"  --approval \"{ASR_APPROVAL_PHRASE}\" \\",
                    "  --strict",
                ]
            ),
        ),
        acceptance="A human approves only the currently listed scoped ASR rows; the GKP asset mutation guard is still clean before apply.",
    )


def asr_review_packet_path(action: dict[str, Any]) -> Path:
    for item in action.get("evidence", []):
        item_path = Path(str(item))
        if "gkp-patch-review-packet" in item_path.name:
            return item_path if item_path.is_absolute() else ROOT / item_path
    return DEFAULT_ASR_REVIEW_PACKET


def load_asr_review_rows(path: Path) -> tuple[AsrReviewRow, ...]:
    if not path.is_file():
        return ()
    text = path.read_text(encoding="utf-8")
    section_pattern = re.compile(r"(?ms)^## (?P<pack>community\.[^\n]+)\n(?P<body>.*?)(?=^## |\Z)")
    rows: list[AsrReviewRow] = []
    for match in section_pattern.finditer(text):
        pack_id = match.group("pack").strip()
        body = match.group("body")
        alias_row = extract_json_after_heading(body, "aliases.json row")
        golden_row = extract_json_after_heading(body, "qa_goldens.jsonl row")
        if not alias_row:
            continue
        source_refs = golden_row.get("source_refs") if isinstance(golden_row, dict) else []
        if not isinstance(source_refs, list):
            source_refs = []
        rows.append(
            AsrReviewRow(
                pack_id=pack_id,
                observed_asr=str(alias_row.get("term") or ""),
                canonical_term=str(alias_row.get("canonical_term") or ""),
                entity_id=str(alias_row.get("entity_id") or ""),
                source_refs=tuple(str(item) for item in source_refs),
            )
        )
    return tuple(rows)


def extract_json_after_heading(section_body: str, heading: str) -> dict[str, Any]:
    pattern = re.compile(
        rf"(?ms)^### {re.escape(heading)}\n\n```json\n(?P<json>.*?)\n```"
    )
    match = pattern.search(section_body)
    if not match:
        return {}
    try:
        data = json.loads(match.group("json"))
    except json.JSONDecodeError:
        return {}
    return data if isinstance(data, dict) else {}


def screen_translation_section(action: dict[str, Any], cases: tuple[ScreenCase, ...]) -> IntakeSection:
    commands: list[str] = []
    inputs: list[str] = []
    for case in cases:
        inputs.append(
            (
                f"{case.case_id}: label={case.game_label}; screen={case.screen_type}; "
                f"say={case.trigger_phrase}; layout={case.expected_layout}; "
                f"language={case.expected_language}; numbers={case.number_policy}"
            )
        )
        commands.append(
            "\n".join(
                [
                    "# Replace <timestamp> with the unique real evidence directory under build/rc-device-evidence/ captured for this case, then review the preview.",
                    "python3 scripts/screen_translation_matrix_update.py \\",
                    "  --cases scripts/screen_translation_eval_cases.tsv \\",
                    f"  --case-id {case.case_id} \\",
                    f"  --result \"Pass: evidence build/rc-device-evidence/<timestamp> checklist={screen_pass_checklist(case)}\" \\",
                    f"  --output /tmp/retrosprite-screen-matrix-{case.case_id}.md",
                ]
            )
        )
    inputs.extend(
        (
            "Use `Fail: <category>` for numeric_corruption, language_leakage, layout_grouping, paging_duration, or ocr_api.",
            "Use `Blocked: <reason>` for missing BYOK key or cannot reproduce screen.",
            "Use the generated `checklist=` tokens for Pass rows; they prove the tester checked layout, language, numbers, or paging for that case.",
            "Preferred path: record each result with `scripts/screen_translation_receipt_update.py --case-id <case_id> --result \"...\" --apply`, then run the receipt checker and planner.",
            "The receipt planner emits a preview command with `--output` before any matching `--apply`; do not apply placeholder evidence paths.",
            "Direct updater fallback: run the preview command first, replace `<timestamp>` with a unique real directory, review the generated preview, then apply only the same non-placeholder result.",
        )
    )
    commands.extend(
        (
            "python3 scripts/screen_translation_receipt_update.py --case-id ff6_dialogue --result \"Pass: evidence build/rc-device-evidence/<timestamp> checklist=layout_ok,language_ok,no_english_source\" --output /tmp/retrosprite-m18-receipt-preview.json",
            "python3 scripts/m18_manual_gate_receipt_check.py --output docs/qa-feedback/m18-manual-gate-receipt-check.md --template-output docs/qa-feedback/m18-manual-gate-receipt-template.json",
            "python3 scripts/m18_manual_gate_receipt_plan.py --output docs/qa-feedback/m18-manual-gate-receipt-plan.md",
            "python3 scripts/screen_translation_eval_report.py --output docs/qa-feedback/screen-translation-eval-report.md --strict",
            "python3 scripts/m18_status_report.py --output docs/qa-feedback/m18-status-report.md",
        )
    )
    return IntakeSection(
        section_id="screen-translation-manual-results",
        title="Screen translation manual result input",
        status="ready",
        owner=str(action.get("owner", "human/device")),
        required_input=tuple(inputs),
        evidence=tuple(str(item) for item in action.get("evidence", [])),
        command_templates=tuple(commands),
        acceptance="All five rows pass with evidence notes and `screen_translation_eval_report.py --strict` passes.",
    )


def screen_pass_checklist(case: ScreenCase) -> str:
    tokens = ["layout_ok", "language_ok"]
    if case.expected_layout == "chinese_only":
        tokens.append("no_english_source")
    if case.expected_layout in {"bilingual_rows", "grouped_labels"}:
        tokens.append("grouping_ok")
    if case.expected_language == "en_zh":
        tokens.append("bilingual_ok")
    if "preserve" in case.number_policy:
        tokens.append("numbers_ok")
    if case.expected_layout == "paged_overlay" or case.number_policy == "ten_seconds_per_page":
        tokens.append("paging_10s")
    return ",".join(tokens)


def content_rights_section(action: dict[str, Any]) -> IntakeSection:
    return IntakeSection(
        section_id="content-rights-human-review",
        title="GKP content-rights human review input",
        status="ready",
        owner=str(action.get("owner", "human")),
        required_input=(
            "Run `python3 scripts/rc_release_audit.py` and confirm it passes.",
            "Spot-check every bundled pack for short original summaries, aliases, terms, metadata, and source refs only.",
            "Confirm no ROMs, BIOS, saves, screenshots, executables, patch files, copied guide prose, full scripts, or copied fan translations are bundled.",
            "Record the approval with `scripts/m18_manual_gate_receipt_update.py --section content-rights-human-review` so the generated `content_rights_review.review_scope` is preserved; do not update release checklist checkboxes directly.",
            f"When all checks pass, use exactly: {CONTENT_RIGHTS_APPROVAL_PHRASE}",
        ),
        evidence=tuple(str(item) for item in action.get("evidence", [])),
        command_templates=(
            "python3 scripts/rc_release_audit.py",
            "\n".join(
                [
                    "python3 scripts/m18_manual_gate_receipt_update.py \\",
                    "  --section content-rights-human-review \\",
                    "  --decision approved \\",
                    f"  --approval-phrase \"{CONTENT_RIGHTS_APPROVAL_PHRASE}\" \\",
                    "  --reviewer \"<reviewer>\" \\",
                    "  --output /tmp/retrosprite-m18-content-rights-receipt-preview.json",
                ]
            ),
            "\n".join(
                [
                    "python3 scripts/m18_manual_gate_receipt_check.py \\",
                    "  --output docs/qa-feedback/m18-manual-gate-receipt-check.md \\",
                    "  --template-output docs/qa-feedback/m18-manual-gate-receipt-template.json",
                    "python3 scripts/m18_manual_gate_receipt_plan.py \\",
                    "  --output docs/qa-feedback/m18-manual-gate-receipt-plan.md",
                ]
            ),
        ),
        acceptance="The human reviewer records the exact approval phrase in the manual receipt, and the receipt checker validates the current content-rights review_scope.",
    )


def render_markdown(sections: tuple[IntakeSection, ...]) -> str:
    counts = status_counts(sections)
    lines = [
        "# M18 Manual Gate Intake Packet",
        "",
        f"- Intake counts: ready={counts.get('ready', 0)}, done={counts.get('done', 0)}, blocked={counts.get('blocked', 0)}",
        "- GKP assets edited by this packet: no",
        "",
        "## Intake Sections",
        "",
        "| ID | Owner | Status | Title | Evidence |",
        "|---|---|---|---|---|",
    ]
    for section in sections:
        evidence = "<br>".join(f"`{item}`" for item in section.evidence) or "-"
        lines.append(
            f"| `{section.section_id}` | {escape_cell(section.owner)} | `{section.status}` | "
            f"{escape_cell(section.title)} | {evidence} |"
        )
    for section in sections:
        lines.extend(
            [
                "",
                f"## {section.title}",
                "",
                f"- Section id: `{section.section_id}`",
                f"- Status: `{section.status}`",
                f"- Owner: `{section.owner}`",
                f"- Acceptance: {section.acceptance}",
                "",
                "Required input:",
                "",
            ]
        )
        for item in section.required_input:
            lines.append(f"- {item}")
        lines.extend(["", "Command templates:", ""])
        for command in section.command_templates:
            lines.extend(["```bash", command, "```", ""])
    return "\n".join(lines).rstrip() + "\n"


def render_json(sections: tuple[IntakeSection, ...]) -> dict[str, Any]:
    return {
        "schema_version": 1,
        "objective": "M18 Eval Lab + GKP Quality Loop",
        "counts": status_counts(sections),
        "assets_edited_by_report": False,
        "sections": [
            {
                "id": section.section_id,
                "title": section.title,
                "status": section.status,
                "owner": section.owner,
                "required_input": list(section.required_input),
                "evidence": list(section.evidence),
                "command_templates": list(section.command_templates),
                "acceptance": section.acceptance,
            }
            for section in sections
        ],
    }


def load_queue(path: Path) -> dict[str, Any]:
    if not path.is_file():
        raise ValueError(f"next action queue JSON not found: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def action_map_from_queue(queue: dict[str, Any]) -> dict[str, dict[str, Any]]:
    actions = queue.get("actions")
    if not isinstance(actions, list):
        raise ValueError("next action queue JSON must include actions[]")
    mapped: dict[str, dict[str, Any]] = {}
    for index, action in enumerate(actions):
        if not isinstance(action, dict):
            raise ValueError(f"next action queue actions[{index}] must be an object")
        action_id = str(action.get("id") or "").strip()
        if not action_id:
            raise ValueError(f"next action queue actions[{index}] is missing id")
        if action_id in mapped:
            raise ValueError(f"next action queue duplicate action id: {action_id}")
        mapped[action_id] = action
    return mapped


def ready_action_ids(queue: dict[str, Any], actions: dict[str, dict[str, Any]]) -> set[str]:
    grouped = queue.get("action_ids_by_status")
    if not isinstance(grouped, dict):
        return {
            action_id
            for action_id, action in actions.items()
            if str(action.get("status", "")).strip() == "ready"
        }

    ready = grouped.get("ready")
    if not isinstance(ready, list):
        raise ValueError("next action queue action_ids_by_status.ready must be a list")
    ready_ids: set[str] = set()
    for raw_action_id in ready:
        action_id = str(raw_action_id).strip()
        action = actions.get(action_id)
        if not action:
            raise ValueError(f"next action queue ready action missing from actions[]: {action_id}")
        status = str(action.get("status") or "").strip()
        if status != "ready":
            raise ValueError(
                f"next action queue action_ids_by_status.ready lists {action_id} but actions[] status is {status or 'missing'}"
            )
        ready_ids.add(action_id)
    return ready_ids


def load_screen_cases(path: Path) -> tuple[ScreenCase, ...]:
    if not path.is_file():
        raise ValueError(f"screen translation cases not found: {path}")
    rows: list[ScreenCase] = []
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        for row in reader:
            rows.append(
                ScreenCase(
                    case_id=row["id"],
                    game_label=row["game_label"],
                    screen_type=row["screen_type"],
                    trigger_phrase=row["trigger_phrase"],
                    expected_layout=row["expected_layout"],
                    expected_language=row["expected_language"],
                    number_policy=row["number_policy"],
                )
            )
    if not rows:
        raise ValueError(f"no screen translation cases found: {path}")
    return tuple(rows)


def action_status(actions: dict[str, dict[str, Any]], action_id: str) -> str:
    action = actions.get(action_id)
    if not action:
        return "missing"
    return str(action.get("status", "missing"))


def status_counts(sections: tuple[IntakeSection, ...]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for section in sections:
        counts[section.status] = counts.get(section.status, 0) + 1
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
