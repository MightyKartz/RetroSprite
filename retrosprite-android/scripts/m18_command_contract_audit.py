#!/usr/bin/env python3
"""Audit generated M18 command snippets for unsafe or stale CLI contracts."""

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
DEFAULT_OUTPUT = ROOT / "docs/qa-feedback/m18-command-contract-audit.md"
DEFAULT_ASR_REVIEW_PACKET = ROOT / "docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md"
DEFAULT_ASR_REVIEW_PACKET_JSON = ROOT / "docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.json"
DEFAULT_SCREEN_CASES = ROOT / "scripts/screen_translation_eval_cases.tsv"
DEFAULT_BACKLOG = ROOT / "docs/qa-feedback/gkp-quality-backlog.md"
CONTENT_RIGHTS_PACKET_SCRIPT = ROOT / "scripts/gkp_content_rights_manual_packet.py"
DEFAULT_INPUTS = (
    ROOT / "docs/qa-feedback/m18-next-action-queue.md",
    ROOT / "docs/qa-feedback/m18-next-action-queue.json",
    ROOT / "docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.json",
    ROOT / "docs/qa-feedback/gkp-asr-patch-voice-replay-handoff.md",
    ROOT / "docs/qa-feedback/gkp-asr-patch-voice-replay-handoff.json",
    ROOT / "docs/qa-feedback/m18-remaining-gate-handoff.md",
    ROOT / "docs/qa-feedback/m18-remaining-gate-handoff.json",
    ROOT / "docs/qa-feedback/m18-quality-loop-handoff.md",
    ROOT / "docs/qa-feedback/m18-quality-loop-handoff.json",
    ROOT / "docs/qa-feedback/m18-plan-execution-audit.json",
    ROOT / "docs/qa-feedback/m18-completion-audit.json",
    ROOT / "scripts/m18_offline_quality_gate.sh",
    ROOT / "scripts/gkp_patch_regression_gate.sh",
    ROOT / "README.md",
    ROOT / "docs/ARCHITECTURE_AND_PRODUCT_TIERS.md",
    ROOT / "docs/superpowers/plans/2026-06-01-m18-eval-lab-gkp-quality-loop.md",
    ROOT / "docs/NEXT_IMPLEMENTATION_PLAN.md",
    ROOT / "docs/TEST_COVERAGE.md",
)

STALE_ASR_SCOPE_PATTERNS = (
    re.compile(r"\bthree(?: current)? ASR\b", re.IGNORECASE),
    re.compile(r"\bthree-row(?: hotkey voice)? replay\b", re.IGNORECASE),
    re.compile(r"\bthree previously failing rows\b", re.IGNORECASE),
    re.compile(r"\bthree new ASR goldens\b", re.IGNORECASE),
    re.compile(r"\bthree scoped ASR\b", re.IGNORECASE),
    re.compile(r"3\s*个\s*ASR"),
    re.compile(r"3\s*条失败\s*voice rows", re.IGNORECASE),
)
STALE_ASR_REPLAY_FILTERS = (
    "chrono_marle_observed,ff6_magicite_observed,langrisser_commander_smoke",
)
STALE_ASR_INTAKE_PHRASES = (
    "Chrono Trigger, Final Fantasy VI, and Langrisser II",
)


@dataclass(frozen=True)
class AuditFinding:
    path: Path
    rule: str
    status: str
    detail: str


@dataclass(frozen=True)
class AsrReviewRow:
    pack_id: str
    observed_asr: str
    canonical_term: str
    entity_id: str
    source_refs: tuple[str, ...]


@dataclass(frozen=True)
class ScreenCase:
    case_id: str
    game_label: str
    screen_type: str
    trigger_phrase: str
    expected_layout: str
    expected_language: str
    number_policy: str
    evidence_required: str


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", dest="inputs", type=Path, action="append", help="File to audit. Defaults to generated M18 handoff files.")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--strict", action="store_true", help="Exit nonzero when any command contract fails.")
    args = parser.parse_args()

    inputs = tuple(args.inputs) if args.inputs else DEFAULT_INPUTS
    findings = audit_paths(inputs)
    markdown = render_markdown(findings, inputs)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(markdown, encoding="utf-8")
    counts = status_counts(findings)
    print(
        "OK M18 command contract audit: "
        f"pass={counts.get('pass', 0)}, fail={counts.get('fail', 0)}, missing={counts.get('missing', 0)}"
    )
    if args.strict and any(finding.status != "pass" for finding in findings):
        return 1
    return 0


def audit_paths(paths: tuple[Path, ...]) -> tuple[AuditFinding, ...]:
    findings: list[AuditFinding] = []
    for path in paths:
        if not path.is_file():
            findings.append(AuditFinding(path, "required_input", "missing", "required audit input is missing"))
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        snippets = extract_snippets(path, text)
        findings.extend(audit_release_checklist_guard_flags(path, snippets))
        findings.extend(audit_gkp_patch_apply_flags(path, snippets))
        findings.extend(audit_placeholder_screen_apply(path, snippets))
        findings.extend(audit_screen_matrix_update_flags(path, snippets))
        findings.extend(audit_manual_entrypoint_screen_apply(path, snippets))
        findings.extend(audit_manual_intake_content_rights_receipt(path, snippets))
        findings.extend(audit_manual_receipt_update_flags(path, snippets))
        findings.extend(audit_receipt_plan_screen_preview(path, snippets))
        findings.extend(audit_gkp_backlog_import_safety(path, snippets))
        findings.extend(audit_asr_replay_scope(path, text, snippets))
        findings.extend(audit_asr_intake_review_packet_sync(path, text))
        findings.extend(audit_asr_receipt_template_review_packet_sync(path, text))
        findings.extend(audit_screen_receipt_template_case_policy_sync(path, text))
        findings.extend(audit_content_rights_receipt_template_scope_sync(path, text))
        findings.extend(audit_screen_evidence_capture_metadata_contract(path, text))
        findings.extend(audit_gkp_patch_review_packet_json_status_index(path, text))
        findings.extend(audit_gkp_asr_handoff_json_status_index(path, text))
        findings.extend(audit_next_action_queue_json_status_index(path, text))
        findings.extend(audit_manual_gate_intake_json_status_index(path, text))
        findings.extend(audit_manual_gate_receipt_check_json_status_index(path, text))
        findings.extend(audit_manual_gate_receipt_plan_json_status_index(path, text))
        findings.extend(audit_remaining_gate_handoff_json_status_index(path, text))
        findings.extend(audit_quality_loop_handoff_json_status_index(path, text))
        findings.extend(audit_plan_execution_audit_json_status_index(path, text))
        findings.extend(audit_completion_audit_json_status_index(path, text))
        if path.name == "m18_offline_quality_gate.sh":
            findings.append(audit_offline_gate_safe_default(path, text))
    findings.extend(audit_asr_replay_scope_sync_across_generated_docs(paths))
    if not findings:
        findings.append(AuditFinding(ROOT, "command_contracts", "pass", "no auditable command snippets found"))
    return tuple(findings)


def audit_release_checklist_guard_flags(path: Path, snippets: tuple[str, ...]) -> list[AuditFinding]:
    failures: list[str] = []
    passes = 0
    for command in iter_logical_commands(snippets):
        if "m18_release_checklist_guard.py" not in command or "--apply" not in command:
            continue
        if "--content-rights-approval" not in command or has_exact_flag(command, "--approval"):
            failures.append(compact(command))
        else:
            passes += 1
    if failures:
        return [
            AuditFinding(
                path,
                "release_checklist_guard_apply_flag",
                "fail",
                "release checklist guarded apply must use --content-rights-approval, not --approval: " + "; ".join(failures),
            )
        ]
    return [
        AuditFinding(
            path,
            "release_checklist_guard_apply_flag",
            "pass",
            f"checked {passes} guarded apply command(s)",
        )
    ]


def audit_gkp_patch_apply_flags(path: Path, snippets: tuple[str, ...]) -> list[AuditFinding]:
    failures: list[str] = []
    passes = 0
    for command in iter_logical_commands(snippets):
        if "gkp_patch_apply_review_packet.py" not in command or "--apply" not in command:
            continue
        if "--approval" not in command or "--strict" not in command or "--packet" not in command:
            failures.append(compact(command))
        elif "--content-rights-approval" in command:
            failures.append(compact(command))
        else:
            passes += 1
    if failures:
        return [
            AuditFinding(
                path,
                "gkp_patch_apply_approval_flag",
                "fail",
                "GKP patch apply must use --packet, --approval, and --strict, not --content-rights-approval: "
                + "; ".join(failures),
            )
        ]
    if passes:
        return [
            AuditFinding(
                path,
                "gkp_patch_apply_approval_flag",
                "pass",
                f"checked {passes} GKP patch apply command(s)",
            )
        ]
    return []


def audit_placeholder_screen_apply(path: Path, snippets: tuple[str, ...]) -> list[AuditFinding]:
    failures: list[str] = []
    passes = 0
    for command in iter_logical_commands(snippets):
        if "screen_translation_matrix_update.py" not in command:
            continue
        if "<timestamp>" in command and "--apply" in command:
            failures.append(compact(command))
        else:
            passes += 1
    if failures:
        return [
            AuditFinding(
                path,
                "placeholder_screen_translation_apply",
                "fail",
                "screen translation placeholder evidence must be previewed before --apply: " + "; ".join(failures),
            )
        ]
    return [
        AuditFinding(
            path,
            "placeholder_screen_translation_apply",
            "pass",
            f"checked {passes} screen matrix command(s)",
        )
    ]


def audit_screen_matrix_update_flags(path: Path, snippets: tuple[str, ...]) -> list[AuditFinding]:
    failures: list[str] = []
    passes = 0
    for command in iter_logical_commands(snippets):
        if "screen_translation_matrix_update.py" not in command:
            continue
        missing = [
            flag
            for flag in ("--cases", "--case-id", "--result")
            if not has_exact_flag(command, flag)
        ]
        if missing:
            failures.append(f"{compact(command)} missing {', '.join(missing)}")
            continue
        result_value = flag_value(command, "--result") or ""
        if result_value.lower().startswith("pass:") and "checklist=" not in result_value:
            failures.append(f"{compact(command)} Pass result missing checklist=")
        else:
            passes += 1
    if failures:
        return [
            AuditFinding(
                path,
                "screen_matrix_update_required_flags",
                "fail",
                "screen translation matrix update commands must pass explicit case policy and result flags: "
                + "; ".join(failures),
            )
        ]
    if passes:
        return [
            AuditFinding(
                path,
                "screen_matrix_update_required_flags",
                "pass",
                f"checked {passes} screen matrix command(s)",
            )
        ]
    return []


def audit_manual_entrypoint_screen_apply(path: Path, snippets: tuple[str, ...]) -> list[AuditFinding]:
    if path.name not in {
        "m18-next-action-queue.md",
        "m18-next-action-queue.json",
        "m18-manual-gate-intake.md",
        "m18-manual-gate-intake.json",
    }:
        return []

    failures = [
        compact(command)
        for command in iter_logical_commands(snippets)
        if "screen_translation_matrix_update.py" in command and "--apply" in command
    ]
    if failures:
        return [
            AuditFinding(
                path,
                "manual_entrypoint_screen_preview_first",
                "fail",
                "manual entrypoints must route screen matrix updates through receipt planning or preview-only commands: "
                + "; ".join(failures),
            )
        ]
    return [
        AuditFinding(
            path,
            "manual_entrypoint_screen_preview_first",
            "pass",
            "screen matrix apply commands are not exposed directly in this manual entrypoint",
        )
    ]


def audit_manual_intake_content_rights_receipt(path: Path, snippets: tuple[str, ...]) -> list[AuditFinding]:
    if path.name not in {"m18-manual-gate-intake.md", "m18-manual-gate-intake.json"}:
        return []
    failures = [
        compact(command)
        for command in iter_logical_commands(snippets)
        if "m18_release_checklist_guard.py" in command and "--content-rights-approval" in command
    ]
    if failures:
        return [
            AuditFinding(
                path,
                "manual_intake_content_rights_receipt_first",
                "fail",
                "manual content-rights intake must route approval through the receipt checker/planner before release checklist guard: "
                + "; ".join(failures),
            )
        ]
    return [
        AuditFinding(
            path,
            "manual_intake_content_rights_receipt_first",
            "pass",
            "manual content-rights intake does not expose direct release checklist approval commands",
        )
    ]


def audit_manual_receipt_update_flags(path: Path, snippets: tuple[str, ...]) -> list[AuditFinding]:
    failures: list[str] = []
    passes = 0
    preview_first_paths = {
        "m18-next-action-queue.md",
        "m18-next-action-queue.json",
        "m18-manual-gate-intake.md",
        "m18-manual-gate-intake.json",
    }
    for command in iter_logical_commands(snippets):
        if "m18_manual_gate_receipt_update.py" not in command:
            continue
        missing = [
            flag
            for flag in ("--section", "--decision")
            if not has_exact_flag(command, flag)
        ]
        if missing:
            failures.append(f"{compact(command)} missing {', '.join(missing)}")
            continue
        decision = (flag_value(command, "--decision") or "").strip()
        section = (flag_value(command, "--section") or "").strip()
        if decision == "approved":
            for flag in ("--approval-phrase", "--reviewer"):
                if not has_exact_flag(command, flag):
                    failures.append(f"{compact(command)} approved receipt update missing {flag}")
            phrase = flag_value(command, "--approval-phrase") or ""
            if section == "asr-patch-approval" and phrase != "I approve gkp patch review packet 20260601 hotkey voice":
                failures.append("ASR receipt update must use the exact ASR approval phrase")
            if section == "content-rights-human-review" and phrase != "I confirm gkp content rights human spot check":
                failures.append("content-rights receipt update must use the exact content-rights approval phrase")
        if path.name in preview_first_paths:
            if has_exact_flag(command, "--apply"):
                failures.append(f"{compact(command)} manual entrypoints must preview receipt updates before --apply")
            if not has_exact_flag(command, "--output"):
                failures.append(f"{compact(command)} manual entrypoints must include --output for receipt preview")
        passes += 1

    if failures:
        return [
            AuditFinding(
                path,
                "manual_receipt_update_flags",
                "fail",
                "manual receipt update commands must be preview-first and approval-explicit: "
                + "; ".join(failures),
            )
        ]
    if passes:
        return [
            AuditFinding(
                path,
                "manual_receipt_update_flags",
                "pass",
                f"checked {passes} manual receipt update command(s)",
            )
        ]
    return []


def audit_receipt_plan_screen_preview(path: Path, snippets: tuple[str, ...]) -> list[AuditFinding]:
    if path.name != "m18-manual-gate-receipt-plan.md":
        return []

    previews: set[str] = set()
    applies: list[tuple[str, str]] = []
    for command in iter_logical_commands(snippets):
        if "screen_translation_matrix_update.py" not in command:
            continue
        case_id = flag_value(command, "--case-id") or "unknown"
        if "--apply" in command:
            applies.append((case_id, command))
        elif has_exact_flag(command, "--output"):
            previews.add(case_id)

    missing = [compact(command) for case_id, command in applies if case_id not in previews]
    if missing:
        return [
            AuditFinding(
                path,
                "receipt_plan_screen_preview_before_apply",
                "fail",
                "receipt-plan screen matrix apply commands must have a matching preview command with --output first: "
                + "; ".join(missing),
            )
        ]
    return [
        AuditFinding(
            path,
            "receipt_plan_screen_preview_before_apply",
            "pass",
            f"checked {len(applies)} screen matrix apply command(s)",
        )
    ]


def audit_gkp_backlog_import_safety(path: Path, snippets: tuple[str, ...]) -> list[AuditFinding]:
    failures: list[str] = []
    checked = 0
    for command in iter_logical_commands(snippets):
        if "gkp_gap_backlog.py" not in command:
            continue
        input_value = flag_value(command, "--input") or ""
        output_value = flag_value(command, "--output") or ""
        merge_value = flag_value(command, "--merge-existing-backlog") or ""
        writes_active_backlog = output_value == "docs/qa-feedback/gkp-quality-backlog.md"
        merges_active_backlog = merge_value == "docs/qa-feedback/gkp-quality-backlog.md"
        if is_backlog_input_variable(input_value) and writes_active_backlog:
            checked += 1
            if not merges_active_backlog:
                failures.append(
                    compact(command)
                    + " BACKLOG_INPUT imports must use --merge-existing-backlog docs/qa-feedback/gkp-quality-backlog.md when writing the active backlog"
                )
        elif "m18-manual-gate-receipt.json" in input_value:
            checked += 1
            if writes_active_backlog:
                failures.append(
                    compact(command)
                    + " receipt imports must preview backlog rows before any active backlog merge"
                )
        elif is_manual_notes_input(input_value) and writes_active_backlog:
            checked += 1
            if not merges_active_backlog:
                failures.append(
                    compact(command)
                    + " manual notes imports must use --merge-existing-backlog when writing the active backlog"
                )
        elif is_manual_notes_input(input_value):
            checked += 1

    if failures:
        return [
            AuditFinding(
                path,
                "gkp_backlog_import_safety",
                "fail",
                "GKP backlog manual/receipt imports must not drop active backlog rows: "
                + "; ".join(failures),
            )
        ]
    if checked:
        return [
            AuditFinding(
                path,
                "gkp_backlog_import_safety",
                "pass",
                f"checked {checked} manual/receipt backlog import command(s)",
            )
        ]
    return []


def is_manual_notes_input(value: str) -> bool:
    lowered = value.lower()
    return (
        "manual" in lowered
        or "tester" in lowered
        or "qa-notes" in lowered
        or "note" in lowered
    )


def is_backlog_input_variable(value: str) -> bool:
    return "BACKLOG_INPUT" in value


def audit_offline_gate_safe_default(path: Path, text: str) -> AuditFinding:
    if "--apply" in text:
        return AuditFinding(path, "offline_gate_no_apply", "fail", "safe default offline gate must not contain --apply")
    return AuditFinding(path, "offline_gate_no_apply", "pass", "offline gate contains no apply mode")


def audit_asr_replay_scope(path: Path, text: str, snippets: tuple[str, ...]) -> list[AuditFinding]:
    stale_hits: list[str] = []
    for pattern in STALE_ASR_SCOPE_PATTERNS:
        stale_hits.extend(match.group(0) for match in pattern.finditer(text))
    for stale_filter in STALE_ASR_REPLAY_FILTERS:
        if stale_filter in text:
            stale_hits.append(stale_filter)
    if stale_hits:
        return [
            AuditFinding(
                path,
                "gkp_asr_replay_scope",
                "fail",
                "stale ASR patch/replay scope found; use current patch rows and failed replay cases: "
                + ", ".join(sorted(set(stale_hits))),
            )
        ]

    if path.name != "gkp-asr-patch-voice-replay-handoff.md":
        return []

    patch_rows = parse_count_field(text, "Patch rows")
    voice_cases = parse_count_field(text, "Voice replay cases")
    replay_filters = [
        parse_case_filter(command)
        for command in iter_logical_commands(snippets)
        if "hotkey_voice_qa_batch.sh" in command and "CASE_FILTER=" in command
    ]
    replay_filters = [cases for cases in replay_filters if cases]
    if patch_rows is None or voice_cases is None or not replay_filters:
        return [
            AuditFinding(
                path,
                "gkp_asr_replay_scope",
                "fail",
                "ASR handoff must declare patch row count, voice replay case count, and a replay CASE_FILTER",
            )
        ]
    mismatched = [
        ",".join(cases)
        for cases in replay_filters
        if len(cases) != patch_rows or len(cases) != voice_cases
    ]
    if patch_rows != voice_cases or mismatched:
        return [
            AuditFinding(
                path,
                "gkp_asr_replay_scope",
                "fail",
                "ASR handoff patch rows, replay case count, and CASE_FILTER length must match: "
                f"patch_rows={patch_rows}, voice_cases={voice_cases}, filters={mismatched or replay_filters}",
            )
        ]
    return [
        AuditFinding(
            path,
            "gkp_asr_replay_scope",
            "pass",
            f"patch_rows={patch_rows}; replay_cases={voice_cases}; case_filter={','.join(replay_filters[0])}",
        )
    ]


def audit_asr_replay_scope_sync_across_generated_docs(paths: tuple[Path, ...]) -> list[AuditFinding]:
    asr_path = first_path_named(paths, "gkp-asr-patch-voice-replay-handoff.md")
    if asr_path is None or not asr_path.is_file():
        return []

    asr_text = asr_path.read_text(encoding="utf-8", errors="ignore")
    asr_filter = first_replay_case_filter(asr_path, asr_text)
    asr_patch_rows = parse_count_field(asr_text, "Patch rows")
    asr_voice_cases = parse_count_field(asr_text, "Voice replay cases")
    findings: list[AuditFinding] = []
    targets: tuple[tuple[str, str, bool], ...] = ()
    for target_name, rule, require_counts in targets:
        target_path = first_path_named(paths, target_name)
        if target_path is None or not target_path.is_file():
            continue
        target_text = target_path.read_text(encoding="utf-8", errors="ignore")
        findings.append(
            audit_asr_replay_scope_sync_target(
                target_path,
                rule,
                target_text,
                asr_filter,
                asr_patch_rows,
                asr_voice_cases,
                require_counts=require_counts,
            )
        )
    return findings


def audit_asr_replay_scope_sync_target(
    target_path: Path,
    rule: str,
    target_text: str,
    asr_filter: tuple[str, ...],
    asr_patch_rows: int | None,
    asr_voice_cases: int | None,
    *,
    require_counts: bool,
) -> AuditFinding:
    target_filter = first_replay_case_filter(target_path, target_text)
    label = target_path.name
    failures: list[str] = []
    if not asr_filter:
        failures.append("ASR handoff missing replay CASE_FILTER")
    if not target_filter:
        failures.append(f"{label} missing replay CASE_FILTER")
    if asr_filter and target_filter and asr_filter != target_filter:
        failures.append(
            f"{label} CASE_FILTER does not match ASR handoff: "
            f"asr={','.join(asr_filter)}; target={','.join(target_filter)}"
        )
    if require_counts:
        target_counts = parse_remaining_handoff_asr_counts(target_text)
        if target_counts is None:
            failures.append(f"{label} missing patch_rows/voice_cases summary")
        elif asr_patch_rows is None or asr_voice_cases is None:
            failures.append("ASR handoff missing Patch rows or Voice replay cases")
        else:
            patch_rows, voice_cases = target_counts
            if patch_rows != asr_patch_rows or voice_cases != asr_voice_cases:
                failures.append(
                    f"{label} patch_rows/voice_cases do not match ASR handoff: "
                    f"asr={asr_patch_rows}/{asr_voice_cases}; target={patch_rows}/{voice_cases}"
                )

    if failures:
        return AuditFinding(target_path, rule, "fail", "; ".join(failures))
    return AuditFinding(target_path, rule, "pass", f"case_filter={','.join(asr_filter)}")


def audit_asr_intake_review_packet_sync(path: Path, text: str) -> list[AuditFinding]:
    if path.name not in {"m18-manual-gate-intake.md", "m18-manual-gate-intake.json"}:
        return []

    stale_hits = [phrase for phrase in STALE_ASR_INTAKE_PHRASES if phrase in text]
    rows = load_asr_review_rows(DEFAULT_ASR_REVIEW_PACKET)
    missing: list[str] = []
    for row in rows:
        required_fragments = [
            row.pack_id,
            row.observed_asr,
            row.canonical_term,
            row.entity_id,
            *row.source_refs,
        ]
        absent = [fragment for fragment in required_fragments if fragment and fragment not in text]
        if absent:
            missing.append(f"{row.pack_id} missing {', '.join(absent)}")

    if stale_hits or missing:
        detail_parts = []
        if stale_hits:
            detail_parts.append("stale ASR intake prose: " + ", ".join(stale_hits))
        if missing:
            detail_parts.append("review-packet rows not surfaced: " + "; ".join(missing))
        return [
            AuditFinding(
                path,
                "asr_intake_review_packet_sync",
                "fail",
                "; ".join(detail_parts),
            )
        ]

    if rows:
        detail = "; ".join(
            f"{row.pack_id}: {row.observed_asr} -> {row.canonical_term}"
            for row in rows
        )
    else:
        detail = "no ASR review rows found; nothing to sync"
    return [
        AuditFinding(
            path,
            "asr_intake_review_packet_sync",
            "pass",
            detail,
        )
    ]


def audit_asr_receipt_template_review_packet_sync(path: Path, text: str) -> list[AuditFinding]:
    if path.name != "m18-manual-gate-receipt-template.json":
        return []
    rows = load_asr_review_rows(DEFAULT_ASR_REVIEW_PACKET)
    missing: list[str] = []
    for row in rows:
        required_fragments = [
            row.pack_id,
            row.observed_asr,
            row.canonical_term,
            row.entity_id,
            *row.source_refs,
        ]
        absent = [fragment for fragment in required_fragments if fragment and fragment not in text]
        if absent:
            missing.append(f"{row.pack_id} missing {', '.join(absent)}")
    if missing:
        return [
            AuditFinding(
                path,
                "asr_receipt_template_review_packet_sync",
                "fail",
                "receipt template ASR review_rows do not match current review packet: "
                + "; ".join(missing),
            )
        ]
    detail = "; ".join(
        f"{row.pack_id}: {row.observed_asr} -> {row.canonical_term}"
        for row in rows
    ) or "no ASR review rows found; nothing to sync"
    return [
        AuditFinding(
            path,
            "asr_receipt_template_review_packet_sync",
            "pass",
            detail,
        )
    ]


def audit_screen_receipt_template_case_policy_sync(path: Path, text: str) -> list[AuditFinding]:
    if path.name != "m18-manual-gate-receipt-template.json":
        return []
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        return [
            AuditFinding(
                path,
                "screen_receipt_template_case_policy_sync",
                "fail",
                "receipt template is not valid JSON",
            )
        ]
    entries = data.get("screen_translation_results") if isinstance(data, dict) else None
    if not isinstance(entries, list):
        return [
            AuditFinding(
                path,
                "screen_receipt_template_case_policy_sync",
                "fail",
                "screen_translation_results must be a list",
            )
        ]
    entries_by_id = {
        str(entry.get("case_id") or ""): entry
        for entry in entries
        if isinstance(entry, dict)
    }
    failures: list[str] = []
    cases = load_screen_cases(DEFAULT_SCREEN_CASES)
    for case in cases:
        entry = entries_by_id.get(case.case_id)
        if not isinstance(entry, dict):
            failures.append(f"{case.case_id} missing")
            continue
        expected = {
            "game_label": case.game_label,
            "screen_type": case.screen_type,
            "trigger_phrase": case.trigger_phrase,
            "expected_layout": case.expected_layout,
            "expected_language": case.expected_language,
            "number_policy": case.number_policy,
            "evidence_required": case.evidence_required,
        }
        mismatches = [
            key
            for key, value in expected.items()
            if str(entry.get(key, "")).strip() != value
        ]
        if mismatches:
            failures.append(f"{case.case_id} mismatched {', '.join(mismatches)}")
    extra = sorted(set(entries_by_id) - {case.case_id for case in cases} - {""})
    if extra:
        failures.append("extra case ids: " + ", ".join(extra))
    if failures:
        return [
            AuditFinding(
                path,
                "screen_receipt_template_case_policy_sync",
                "fail",
                "receipt template screen case policies do not match TSV: " + "; ".join(failures),
            )
        ]
    return [
        AuditFinding(
            path,
            "screen_receipt_template_case_policy_sync",
            "pass",
            f"checked {len(cases)} screen case policy snapshot(s)",
        )
    ]


def audit_content_rights_receipt_template_scope_sync(path: Path, text: str) -> list[AuditFinding]:
    if path.name != "m18-manual-gate-receipt-template.json":
        return []
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        return [
            AuditFinding(
                path,
                "content_rights_receipt_template_scope_sync",
                "fail",
                "receipt template is not valid JSON",
            )
        ]
    content_rights = data.get("content_rights_review") if isinstance(data, dict) else None
    review_scope = content_rights.get("review_scope") if isinstance(content_rights, dict) else None
    expected_scope = build_content_rights_review_scope()
    if review_scope != expected_scope:
        return [
            AuditFinding(
                path,
                "content_rights_receipt_template_scope_sync",
                "fail",
                "content-rights review_scope does not match current GKP content-rights packet",
            )
        ]
    return [
        AuditFinding(
            path,
            "content_rights_receipt_template_scope_sync",
            "pass",
            f"checked {expected_scope.get('bundled_packs', 0)} pack content-rights scope",
        )
    ]


def audit_screen_evidence_capture_metadata_contract(path: Path, text: str) -> list[AuditFinding]:
    if path.name == "screen-translation-manual-packet.md":
        cases = load_screen_cases(DEFAULT_SCREEN_CASES)
        missing = [
            case.case_id
            for case in cases
            if f"rc_device_evidence.sh --gate screen_translation --case-id {case.case_id} --include-screenshot" not in text
        ]
        if missing:
            return [
                AuditFinding(
                    path,
                    "screen_evidence_capture_metadata",
                    "fail",
                    "screen translation manual packet must include per-case metadata+screenshot capture commands: "
                    + ", ".join(missing),
                )
            ]
        if "checklist=" not in text:
            return [
                AuditFinding(
                    path,
                    "screen_evidence_capture_metadata",
                    "fail",
                    "screen translation manual packet must include generated checklist= tokens for Pass rows",
                )
            ]
        return [
            AuditFinding(
                path,
                "screen_evidence_capture_metadata",
                "pass",
                f"checked {len(cases)} per-case screen evidence capture command(s), screenshot capture, and checklist tokens",
            )
        ]

    if path.name == "m18-manual-gate-receipt-template.json":
        required = (
            "--gate screen_translation --case-id <case_id> --include-screenshot",
            "metadata.json",
            "screenshot.png",
            "checklist=",
        )
        missing = [fragment for fragment in required if fragment not in text]
        if missing:
            return [
                AuditFinding(
                    path,
                    "screen_evidence_capture_metadata",
                    "fail",
                    "receipt template must tell reviewers to capture screen pass evidence with gate/case metadata and screenshot: "
                    + ", ".join(missing),
                )
            ]
        return [
            AuditFinding(
                path,
                "screen_evidence_capture_metadata",
                "pass",
                "receipt template requires screen translation gate/case metadata and screenshot",
            )
        ]

    if path.name == "rc-device-matrix.md":
        required = (
            "--gate screen_translation --case-id <case_id> --include-screenshot",
            "metadata.json",
            "screenshot.png",
            "screen_translation",
            "checklist=",
        )
        missing = [fragment for fragment in required if fragment not in text]
        if missing:
            return [
                AuditFinding(
                    path,
                    "screen_evidence_capture_metadata",
                    "fail",
                    "screen translation matrix docs must describe gate/case metadata and screenshot evidence: "
                    + ", ".join(missing),
                )
            ]
        return [
            AuditFinding(
                path,
                "screen_evidence_capture_metadata",
                "pass",
                "matrix docs require screen translation gate/case metadata and screenshot",
            )
        ]

    return []


def audit_gkp_patch_review_packet_json_status_index(path: Path, text: str) -> list[AuditFinding]:
    if path.name != "gkp-patch-review-packet-20260601-hotkey-voice.json":
        return []

    rule = "gkp_patch_review_packet_json_status_index"
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        return [AuditFinding(path, rule, "fail", "GKP patch review packet is not valid JSON")]
    if not isinstance(data, dict):
        return [AuditFinding(path, rule, "fail", "GKP patch review packet root must be an object")]

    failures: list[str] = []
    if data.get("dry_run") is not True:
        failures.append("dry_run must be true")
    if data.get("assets_edited") is not False:
        failures.append("assets_edited must be false")

    status = str(data.get("status") or "").strip()
    if status not in {"ready", "applied", "blocked"}:
        failures.append(f"status={status!r}, expected ready/applied/blocked")

    counts = data.get("counts")
    rows = data.get("review_rows")
    if not isinstance(counts, dict):
        failures.append("counts must be an object")
        counts = {}
    if not isinstance(rows, list):
        failures.append("review_rows must be a list")
        rows = []

    row_counts: dict[str, int] = {"ready": 0, "applied": 0, "blocked": 0}
    seen_keys: set[tuple[str, str, str]] = set()
    for index, row in enumerate(rows):
        if not isinstance(row, dict):
            failures.append(f"review_rows[{index}] must be an object")
            continue
        pack_id = str(row.get("pack_id") or "").strip()
        row_status = str(row.get("status") or "").strip()
        alias = row.get("alias_row")
        golden = row.get("golden_row")
        if not pack_id:
            failures.append(f"review_rows[{index}] missing pack_id")
        if row_status not in row_counts:
            failures.append(f"{pack_id or f'review_rows[{index}]'} has unsupported status: {row_status!r}")
        else:
            row_counts[row_status] += 1
        if not str(row.get("pack_dir") or "").strip():
            failures.append(f"{pack_id or f'review_rows[{index}]'} missing pack_dir")
        if not str(row.get("detail") or "").strip():
            failures.append(f"{pack_id or f'review_rows[{index}]'} missing detail")
        if not isinstance(alias, dict):
            failures.append(f"{pack_id or f'review_rows[{index}]'} alias_row must be an object")
            alias = {}
        if not isinstance(golden, dict):
            failures.append(f"{pack_id or f'review_rows[{index}]'} golden_row must be an object")
            golden = {}

        for field in ("term", "entity_id", "kind", "source", "canonical_term"):
            if not str(alias.get(field) or "").strip():
                failures.append(f"{pack_id or f'review_rows[{index}]'} alias_row.{field} missing")
        if alias.get("kind") not in {"observed_asr", None}:
            failures.append(f"{pack_id or f'review_rows[{index}]'} alias_row.kind must be observed_asr")
        for field in ("qa_id", "question", "expected_normalized_question"):
            if not str(golden.get(field) or "").strip():
                failures.append(f"{pack_id or f'review_rows[{index}]'} golden_row.{field} missing")
        expected_entities = golden.get("expected_entity_ids")
        if not isinstance(expected_entities, list) or not expected_entities:
            failures.append(f"{pack_id or f'review_rows[{index}]'} golden_row.expected_entity_ids must be a non-empty list")
        source_refs = golden.get("source_refs")
        if not isinstance(source_refs, list) or not source_refs:
            failures.append(f"{pack_id or f'review_rows[{index}]'} golden_row.source_refs must be a non-empty list")

        dedupe_key = (
            pack_id,
            str(alias.get("term") or ""),
            str(alias.get("entity_id") or ""),
        )
        if dedupe_key in seen_keys:
            failures.append(f"duplicate review row: {dedupe_key}")
        seen_keys.add(dedupe_key)

    observed_counts = {str(key): value for key, value in counts.items()}
    for key, expected in {
        "rows": len(rows),
        "ready": row_counts["ready"],
        "applied": row_counts["applied"],
        "blocked": row_counts["blocked"],
    }.items():
        if observed_counts.get(key) != expected:
            failures.append(f"counts.{key}={observed_counts.get(key)!r}, expected {expected}")
    if row_counts["blocked"]:
        expected_status = "blocked"
    elif rows and row_counts["applied"] == len(rows):
        expected_status = "applied"
    elif rows:
        expected_status = "ready"
    else:
        expected_status = "blocked"
    if status in {"ready", "applied", "blocked"} and status != expected_status:
        failures.append(f"status={status!r}, expected {expected_status!r}")

    markdown_rows = load_asr_review_rows(path.with_suffix(".md"))
    if markdown_rows:
        json_rows = tuple(
            AsrReviewRow(
                pack_id=str(row.get("pack_id") or ""),
                observed_asr=str((row.get("alias_row") or {}).get("term") or "") if isinstance(row, dict) else "",
                canonical_term=str((row.get("alias_row") or {}).get("canonical_term") or "") if isinstance(row, dict) else "",
                entity_id=str((row.get("alias_row") or {}).get("entity_id") or "") if isinstance(row, dict) else "",
                source_refs=tuple(
                    str(item)
                    for item in ((row.get("golden_row") or {}).get("source_refs") or [])
                )
                if isinstance(row, dict) and isinstance(row.get("golden_row"), dict)
                else (),
            )
            for row in rows
            if isinstance(row, dict)
        )
        if json_rows != markdown_rows:
            failures.append("JSON review rows must match sibling Markdown review packet rows")

    if failures:
        return [
            AuditFinding(
                path,
                rule,
                "fail",
                "GKP patch review packet JSON drift: " + "; ".join(failures),
            )
        ]
    return [
        AuditFinding(
            path,
            rule,
            f"pass",
            f"checked {len(rows)} ASR review row(s); ready={row_counts['ready']}, "
            f"applied={row_counts['applied']}, blocked={row_counts['blocked']}",
        )
    ]


def audit_gkp_asr_handoff_json_status_index(path: Path, text: str) -> list[AuditFinding]:
    if path.name != "gkp-asr-patch-voice-replay-handoff.json":
        return []

    rule = "gkp_asr_handoff_json_status_index"
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        return [AuditFinding(path, rule, "fail", "GKP ASR handoff is not valid JSON")]
    if not isinstance(data, dict):
        return [AuditFinding(path, rule, "fail", "GKP ASR handoff root must be an object")]

    failures: list[str] = []
    if data.get("assets_edited_by_handoff") is not False:
        failures.append("assets_edited_by_handoff must be false")
    status = str(data.get("status") or "").strip()
    if status not in {"ready", "needs_review"}:
        failures.append(f"status={status!r}, expected ready/needs_review")

    counts = data.get("counts")
    patch_rows = data.get("patch_rows")
    voice_cases = data.get("voice_cases")
    apply_report = data.get("apply_report")
    approval = data.get("approval")
    if not isinstance(counts, dict):
        failures.append("counts must be an object")
        counts = {}
    if not isinstance(patch_rows, list):
        failures.append("patch_rows must be a list")
        patch_rows = []
    if not isinstance(voice_cases, list):
        failures.append("voice_cases must be a list")
        voice_cases = []
    if not isinstance(apply_report, dict):
        failures.append("apply_report must be an object")
        apply_report = {}
    if not isinstance(approval, dict):
        failures.append("approval must be an object")
        approval = {}

    case_filter = str(data.get("case_filter") or "").strip()
    case_filter_names = tuple(name for name in case_filter.split(",") if name)
    voice_case_names: list[str] = []
    for index, case in enumerate(voice_cases):
        if not isinstance(case, dict):
            failures.append(f"voice_cases[{index}] must be an object")
            continue
        case_name = str(case.get("case_name") or "").strip()
        voice_case_names.append(case_name)
        for field in ("case_name", "pack_id", "label", "spoken_prompt", "expected_stage", "expected_answer_type", "expected_source"):
            if not str(case.get(field) or "").strip():
                failures.append(f"{case_name or f'voice_cases[{index}]'} {field} missing")

    for index, row in enumerate(patch_rows):
        if not isinstance(row, dict):
            failures.append(f"patch_rows[{index}] must be an object")
            continue
        pack_id = str(row.get("pack_id") or "").strip()
        if not pack_id:
            failures.append(f"patch_rows[{index}] missing pack_id")
        if str(row.get("status") or "").strip() not in {"ready", "applied"}:
            failures.append(f"{pack_id or f'patch_rows[{index}]'} status must be ready/applied")
        alias = row.get("alias_row")
        golden = row.get("golden_row")
        if not isinstance(alias, dict):
            failures.append(f"{pack_id or f'patch_rows[{index}]'} alias_row must be an object")
            alias = {}
        if not isinstance(golden, dict):
            failures.append(f"{pack_id or f'patch_rows[{index}]'} golden_row must be an object")
            golden = {}
        for field in ("term", "entity_id", "canonical_term"):
            if not str(alias.get(field) or "").strip():
                failures.append(f"{pack_id or f'patch_rows[{index}]'} alias_row.{field} missing")
        if not str(golden.get("qa_id") or "").strip():
            failures.append(f"{pack_id or f'patch_rows[{index}]'} golden_row.qa_id missing")

    observed_counts = {str(key): value for key, value in counts.items()}
    expected_counts = {
        "patch_rows": len(patch_rows),
        "voice_cases": len(voice_cases),
    }
    for key, expected in expected_counts.items():
        if observed_counts.get(key) != expected:
            failures.append(f"counts.{key}={observed_counts.get(key)!r}, expected {expected}")
    if len(patch_rows) != len(voice_cases):
        failures.append("patch_rows length must equal voice_cases length")
    if tuple(voice_case_names) != case_filter_names:
        failures.append(f"case_filter={case_filter_names!r}, expected {tuple(voice_case_names)!r}")

    if apply_report.get("status") not in {"ready", "applied", "needs_review", "missing"}:
        failures.append("apply_report.status must be ready/applied/needs_review/missing")
    if apply_report.get("assets_edited") not in {"no", "yes", "unknown"}:
        failures.append("apply_report.assets_edited must be no/yes/unknown")
    if approval.get("required") is not True:
        failures.append("approval.required must be true")
    if approval.get("required_phrase") != "I approve gkp patch review packet 20260601 hotkey voice":
        failures.append("approval.required_phrase must match the ASR approval phrase")

    expected_status = (
        "ready"
        if apply_report.get("status") in {"ready", "applied"}
        and apply_report.get("assets_edited") == "no"
        and len(patch_rows) == len(voice_cases)
        and tuple(voice_case_names) == case_filter_names
        else "needs_review"
    )
    if status in {"ready", "needs_review"} and status != expected_status:
        failures.append(f"status={status!r}, expected {expected_status!r}")

    review_json_path = path.with_name(DEFAULT_ASR_REVIEW_PACKET_JSON.name)
    if review_json_path.is_file():
        try:
            review_data = json.loads(review_json_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            failures.append("sibling review packet JSON is not valid JSON")
            review_data = {}
        review_rows = review_data.get("review_rows") if isinstance(review_data, dict) else None
        if isinstance(review_rows, list):
            review_projection = [
                (
                    str(row.get("pack_id") or ""),
                    row.get("alias_row"),
                    row.get("golden_row"),
                )
                for row in review_rows
                if isinstance(row, dict)
            ]
            handoff_projection = [
                (
                    str(row.get("pack_id") or ""),
                    row.get("alias_row"),
                    row.get("golden_row"),
                )
                for row in patch_rows
                if isinstance(row, dict)
            ]
            if handoff_projection != review_projection:
                failures.append("handoff patch_rows must match sibling review packet JSON review_rows")
        else:
            failures.append("sibling review packet JSON review_rows must be a list")

    if failures:
        return [
            AuditFinding(
                path,
                rule,
                "fail",
                "GKP ASR handoff JSON drift: " + "; ".join(failures),
            )
        ]
    return [
        AuditFinding(
            path,
            rule,
            "pass",
            f"checked {len(patch_rows)} patch row(s), {len(voice_cases)} replay case(s), case_filter={case_filter}",
        )
    ]


def audit_next_action_queue_json_status_index(path: Path, text: str) -> list[AuditFinding]:
    if path.name != "m18-next-action-queue.json":
        return []

    rule = "next_action_queue_json_status_index"
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        return [AuditFinding(path, rule, "fail", "next action queue is not valid JSON")]
    if not isinstance(data, dict):
        return [AuditFinding(path, rule, "fail", "next action queue root must be an object")]

    actions = data.get("actions")
    counts = data.get("counts")
    grouped = data.get("action_ids_by_status")
    failures: list[str] = []
    if not isinstance(actions, list):
        failures.append("actions must be a list")
        actions = []
    if not isinstance(counts, dict):
        failures.append("counts must be an object")
        counts = {}
    if not isinstance(grouped, dict):
        failures.append("action_ids_by_status must be an object")
        grouped = {}

    ids_by_status: dict[str, list[str]] = {"ready": [], "blocked": [], "done": []}
    seen_ids: set[str] = set()
    for index, action in enumerate(actions):
        if not isinstance(action, dict):
            failures.append(f"actions[{index}] must be an object")
            continue
        action_id = str(action.get("id") or "").strip()
        status = str(action.get("status") or "").strip()
        if not action_id:
            failures.append(f"actions[{index}] missing id")
            continue
        if action_id in seen_ids:
            failures.append(f"duplicate action id: {action_id}")
        seen_ids.add(action_id)
        if not status:
            failures.append(f"{action_id} missing status")
            continue
        ids_by_status.setdefault(status, []).append(action_id)

    expected_counts = {status: len(ids) for status, ids in ids_by_status.items()}
    observed_counts = {str(key): value for key, value in counts.items()}
    for status, expected in expected_counts.items():
        observed = observed_counts.get(status)
        if observed != expected:
            failures.append(f"counts.{status}={observed!r}, expected {expected}")
    for status, ids in ids_by_status.items():
        observed_ids = grouped.get(status)
        if observed_ids != ids:
            failures.append(f"action_ids_by_status.{status}={observed_ids!r}, expected {ids!r}")

    if failures:
        return [
            AuditFinding(
                path,
                rule,
                "fail",
                "next action queue JSON status index drift: " + "; ".join(failures),
            )
        ]
    return [
        AuditFinding(
            path,
            rule,
            "pass",
            f"checked {len(actions)} actions; ready={expected_counts.get('ready', 0)}, "
            f"blocked={expected_counts.get('blocked', 0)}, done={expected_counts.get('done', 0)}",
        )
    ]


def audit_manual_gate_intake_json_status_index(path: Path, text: str) -> list[AuditFinding]:
    if path.name != "m18-manual-gate-intake.json":
        return []

    rule = "manual_gate_intake_json_status_index"
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        return [AuditFinding(path, rule, "fail", "manual gate intake is not valid JSON")]
    if not isinstance(data, dict):
        return [AuditFinding(path, rule, "fail", "manual gate intake root must be an object")]

    sections = data.get("sections")
    counts = data.get("counts")
    failures: list[str] = []
    if data.get("assets_edited_by_report") is not False:
        failures.append("assets_edited_by_report must be false")
    if not isinstance(sections, list):
        failures.append("sections must be a list")
        sections = []
    if not isinstance(counts, dict):
        failures.append("counts must be an object")
        counts = {}

    section_ids: list[str] = []
    status_counts: dict[str, int] = {}
    command_templates: list[str] = []
    seen_ids: set[str] = set()
    for index, section in enumerate(sections):
        if not isinstance(section, dict):
            failures.append(f"sections[{index}] must be an object")
            continue
        section_id = str(section.get("id") or "").strip()
        status = str(section.get("status") or "").strip()
        if not section_id:
            failures.append(f"sections[{index}] missing id")
        elif section_id in seen_ids:
            failures.append(f"duplicate section id: {section_id}")
        else:
            seen_ids.add(section_id)
            section_ids.append(section_id)
        if status not in {"ready", "done", "blocked"}:
            failures.append(f"{section_id or f'sections[{index}]'} has unsupported status: {status!r}")
        else:
            status_counts[status] = status_counts.get(status, 0) + 1
        templates = section.get("command_templates")
        if not isinstance(templates, list):
            failures.append(f"{section_id or f'sections[{index}]'} command_templates must be a list")
            continue
        for template_index, template in enumerate(templates):
            if not isinstance(template, str):
                failures.append(
                    f"{section_id or f'sections[{index}]'} command_templates[{template_index}] must be a string"
                )
                continue
            command_templates.append(template)

    observed_counts = {str(key): value for key, value in counts.items()}
    for status, expected in status_counts.items():
        observed = observed_counts.get(status)
        if observed != expected:
            failures.append(f"counts.{status}={observed!r}, expected {expected}")
    for status, observed in observed_counts.items():
        if status not in status_counts and observed not in (0, "0"):
            failures.append(f"counts.{status}={observed!r}, expected 0")

    expected_sections = expected_manual_intake_section_ids(path, failures)
    if expected_sections is not None and section_ids != expected_sections:
        failures.append(f"sections={section_ids!r}, expected {expected_sections!r}")

    template_text = "\n".join(command_templates)
    if "screen_translation_matrix_update.py" in template_text:
        for command in iter_logical_commands(tuple(command_templates)):
            if "screen_translation_matrix_update.py" not in command:
                continue
            if "--apply" in command:
                failures.append("screen translation matrix commands in intake JSON must be preview-only")
            missing = [
                flag
                for flag in ("--cases", "--case-id", "--result", "--output")
                if not has_exact_flag(command, flag)
            ]
            if missing:
                failures.append(
                    "screen translation matrix preview command missing " + ", ".join(missing)
                )
    if "m18_release_checklist_guard.py" in template_text and "--apply" in template_text:
        failures.append("manual gate intake JSON must not expose release checklist apply commands")
    for command in iter_logical_commands(tuple(command_templates)):
        if "gkp_patch_apply_review_packet.py" not in command or "--apply" not in command:
            continue
        approval = flag_value(command, "--approval")
        if approval != "I approve gkp patch review packet 20260601 hotkey voice":
            failures.append("GKP patch apply command must include the exact ASR approval phrase")
        if not has_exact_flag(command, "--strict"):
            failures.append("GKP patch apply command must include --strict")

    if failures:
        return [
            AuditFinding(
                path,
                rule,
                "fail",
                "manual gate intake JSON drift: " + "; ".join(failures),
            )
        ]
    return [
        AuditFinding(
            path,
            rule,
            "pass",
            f"checked {len(sections)} sections; ready={status_counts.get('ready', 0)}, "
            f"done={status_counts.get('done', 0)}, blocked={status_counts.get('blocked', 0)}",
        )
    ]


def expected_manual_intake_section_ids(path: Path, failures: list[str]) -> list[str] | None:
    queue_path = path.with_name("m18-next-action-queue.json")
    if not queue_path.is_file():
        queue_path = DEFAULT_QUEUE_JSON
    if not queue_path.is_file():
        failures.append("m18-next-action-queue.json is missing; cannot verify ready section frontier")
        return None
    try:
        queue = json.loads(queue_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        failures.append("m18-next-action-queue.json is not valid JSON; cannot verify ready section frontier")
        return None
    if not isinstance(queue, dict):
        failures.append("m18-next-action-queue.json root must be an object")
        return None

    action_to_section = {
        "approve-asr-patch": "asr-patch-approval",
        "rerun-device-lifecycle-row": "device-voice-lifecycle-rerun",
        "run-screen-translation-matrix": "screen-translation-manual-results",
        "complete-content-rights-review": "content-rights-human-review",
    }
    grouped = queue.get("action_ids_by_status")
    if isinstance(grouped, dict) and isinstance(grouped.get("ready"), list):
        ready_actions = [str(action_id) for action_id in grouped["ready"]]
    else:
        actions = queue.get("actions")
        if not isinstance(actions, list):
            failures.append("m18-next-action-queue.json actions must be a list")
            return None
        ready_actions = [
            str(action.get("id"))
            for action in actions
            if isinstance(action, dict) and str(action.get("status") or "") == "ready"
        ]
    expected = [
        action_to_section[action_id]
        for action_id in ready_actions
        if action_id in action_to_section
    ]
    return expected or ["manual-gates-complete"]


def audit_manual_gate_receipt_check_json_status_index(path: Path, text: str) -> list[AuditFinding]:
    if path.name != "m18-manual-gate-receipt-check.json":
        return []

    rule = "manual_gate_receipt_check_json_status_index"
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        return [AuditFinding(path, rule, "fail", "manual gate receipt check is not valid JSON")]
    if not isinstance(data, dict):
        return [AuditFinding(path, rule, "fail", "manual gate receipt check root must be an object")]

    failures: list[str] = []
    if data.get("assets_edited_by_report") is not False:
        failures.append("assets_edited_by_report must be false")
    status = str(data.get("status") or "").strip()
    if status not in {"pass", "open", "fail"}:
        failures.append(f"status={status!r}, expected pass/open/fail")
    if not isinstance(data.get("receipt_present"), bool):
        failures.append("receipt_present must be boolean")

    counts = data.get("counts")
    items = data.get("items")
    if not isinstance(counts, dict):
        failures.append("counts must be an object")
        counts = {}
    if not isinstance(items, list):
        failures.append("items must be a list")
        items = []

    item_counts: dict[str, int] = {"pass": 0, "open": 0, "fail": 0}
    seen_ids: set[str] = set()
    for index, item in enumerate(items):
        if not isinstance(item, dict):
            failures.append(f"items[{index}] must be an object")
            continue
        item_id = str(item.get("id") or "").strip()
        item_status = str(item.get("status") or "").strip()
        if not item_id:
            failures.append(f"items[{index}] missing id")
        elif item_id in seen_ids:
            failures.append(f"duplicate receipt item id: {item_id}")
        else:
            seen_ids.add(item_id)
        if item_status not in item_counts:
            failures.append(f"{item_id or f'items[{index}]'} has unsupported status: {item_status!r}")
        else:
            item_counts[item_status] += 1
        if not str(item.get("detail") or "").strip():
            failures.append(f"{item_id or f'items[{index}]'} missing detail")

    observed_counts = {str(key): value for key, value in counts.items()}
    for item_status, expected in item_counts.items():
        observed = observed_counts.get(item_status)
        if observed != expected:
            failures.append(f"counts.{item_status}={observed!r}, expected {expected}")
    if status == "pass" and (item_counts["open"] or item_counts["fail"]):
        failures.append("status pass cannot include open or fail items")
    if status == "fail" and item_counts["fail"] == 0:
        failures.append("status fail requires at least one failed item")
    if status == "open" and item_counts["fail"] == 0 and item_counts["open"] == 0:
        failures.append("status open requires at least one open or failed item")

    if failures:
        return [
            AuditFinding(
                path,
                rule,
                "fail",
                "manual gate receipt check JSON drift: " + "; ".join(failures),
            )
        ]
    return [
        AuditFinding(
            path,
            rule,
            "pass",
            f"checked {len(items)} receipt item(s); status={status}; "
            f"pass={item_counts['pass']}, open={item_counts['open']}, fail={item_counts['fail']}",
        )
    ]


def audit_manual_gate_receipt_plan_json_status_index(path: Path, text: str) -> list[AuditFinding]:
    if path.name != "m18-manual-gate-receipt-plan.json":
        return []

    rule = "manual_gate_receipt_plan_json_status_index"
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        return [AuditFinding(path, rule, "fail", "manual gate receipt plan is not valid JSON")]
    if not isinstance(data, dict):
        return [AuditFinding(path, rule, "fail", "manual gate receipt plan root must be an object")]

    failures: list[str] = []
    if data.get("commands_executed_by_planner") is not False:
        failures.append("commands_executed_by_planner must be false")
    if data.get("assets_edited_by_planner") is not False:
        failures.append("assets_edited_by_planner must be false")
    status = str(data.get("status") or "").strip()
    receipt_check_status = str(data.get("receipt_check_status") or "").strip()
    if status not in {"pass", "open", "fail"}:
        failures.append(f"status={status!r}, expected pass/open/fail")
    if receipt_check_status not in {"pass", "open", "fail"}:
        failures.append(f"receipt_check_status={receipt_check_status!r}, expected pass/open/fail")
    if not isinstance(data.get("receipt_present"), bool):
        failures.append("receipt_present must be boolean")

    counts = data.get("counts")
    actions = data.get("actions")
    if not isinstance(counts, dict):
        failures.append("counts must be an object")
        counts = {}
    if not isinstance(actions, list):
        failures.append("actions must be a list")
        actions = []

    action_counts: dict[str, int] = {"ready": 0, "open": 0, "blocked": 0}
    seen_ids: set[str] = set()
    for index, action in enumerate(actions):
        if not isinstance(action, dict):
            failures.append(f"actions[{index}] must be an object")
            continue
        action_id = str(action.get("id") or "").strip()
        action_status = str(action.get("status") or "").strip()
        command = str(action.get("command") or "")
        if not action_id:
            failures.append(f"actions[{index}] missing id")
        elif action_id in seen_ids:
            failures.append(f"duplicate receipt plan action id: {action_id}")
        else:
            seen_ids.add(action_id)
        if action_status not in action_counts:
            failures.append(f"{action_id or f'actions[{index}]'} has unsupported status: {action_status!r}")
        else:
            action_counts[action_status] += 1
        if not str(action.get("detail") or "").strip():
            failures.append(f"{action_id or f'actions[{index}]'} missing detail")
        if action_status == "ready" and not command.strip():
            failures.append(f"{action_id or f'actions[{index}]'} ready action missing command")
        if action_status in {"open", "blocked"} and command.strip():
            failures.append(f"{action_id or f'actions[{index}]'} {action_status} action must not include a command")

    observed_counts = {str(key): value for key, value in counts.items()}
    for action_status, expected in action_counts.items():
        observed = observed_counts.get(action_status)
        if observed != expected:
            failures.append(f"counts.{action_status}={observed!r}, expected {expected}")
    if status == "pass" and (action_counts["open"] or action_counts["blocked"]):
        failures.append("status pass cannot include open or blocked actions")
    if status == "fail" and action_counts["blocked"] == 0:
        failures.append("status fail requires at least one blocked action")
    if status == "open" and action_counts["open"] == 0 and action_counts["blocked"] == 0:
        failures.append("status open requires at least one open or blocked action")

    if failures:
        return [
            AuditFinding(
                path,
                rule,
                "fail",
                "manual gate receipt plan JSON drift: " + "; ".join(failures),
            )
        ]
    return [
        AuditFinding(
            path,
            rule,
            "pass",
            f"checked {len(actions)} receipt plan action(s); status={status}; "
            f"ready={action_counts['ready']}, open={action_counts['open']}, blocked={action_counts['blocked']}",
        )
    ]


def audit_remaining_gate_handoff_json_status_index(path: Path, text: str) -> list[AuditFinding]:
    if path.name != "m18-remaining-gate-handoff.json":
        return []

    rule = "remaining_gate_handoff_json_status_index"
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        return [AuditFinding(path, rule, "fail", "remaining gate handoff is not valid JSON")]
    if not isinstance(data, dict):
        return [AuditFinding(path, rule, "fail", "remaining gate handoff root must be an object")]

    failures: list[str] = []
    if data.get("assets_edited_by_handoff") is not False:
        failures.append("assets_edited_by_handoff must be false")
    status = str(data.get("status") or "").strip()
    if status not in {"pass", "open"}:
        failures.append(f"status={status!r}, expected pass/open")
    counts = data.get("counts")
    if not isinstance(counts, dict):
        failures.append("counts must be an object")
        counts = {}
    plan_unchecked = counts.get("plan_unchecked")
    aggregate_open = counts.get("aggregate_open")
    expected_green = plan_unchecked == 0 and aggregate_open == 0
    if isinstance(data.get("is_green"), bool) and data.get("is_green") != expected_green:
        failures.append(f"is_green={data.get('is_green')!r}, expected {expected_green}")
    if status == "pass" and not expected_green:
        failures.append("status pass requires no unchecked plan or aggregate open items")
    if status == "open" and expected_green:
        failures.append("status open requires at least one remaining gate")

    removed = set(str(item) for item in data.get("removed_from_m18_scope", []))
    for item in ("manual_asr_approval", "five_row_screen_translation_manual_matrix", "human_content_rights_confirmation"):
        if item not in removed:
            failures.append(f"removed_from_m18_scope missing {item}")

    gates = data.get("gates")
    if not isinstance(gates, dict):
        failures.append("gates must be an object")
        gates = {}
    if not isinstance(gates.get("hotkey_voice_matrix"), dict):
        failures.append("gates.hotkey_voice_matrix must be an object")

    commands = data.get("commands")
    if not isinstance(commands, list):
        failures.append("commands must be a list")
        commands = []
    command_ids: list[str] = []
    command_map: dict[str, str] = {}
    for index, command in enumerate(commands):
        if not isinstance(command, dict):
            failures.append(f"commands[{index}] must be an object")
            continue
        command_id = str(command.get("id") or "").strip()
        command_text = str(command.get("command") or "").strip()
        if not command_id:
            failures.append(f"commands[{index}] missing id")
            continue
        if not command_text:
            failures.append(f"{command_id} missing command")
        command_ids.append(command_id)
        command_map[command_id] = command_text
    if len(command_ids) != len(set(command_ids)):
        failures.append("commands contains duplicate ids")
    for command_id in ("hotkey_voice_matrix_report", "offline_quality_gate", "m18_completion_audit_strict"):
        if command_id not in command_map:
            failures.append(f"commands missing {command_id}")
    if command_map.get("offline_quality_gate") != "./scripts/m18_offline_quality_gate.sh":
        failures.append("offline_quality_gate must be ./scripts/m18_offline_quality_gate.sh")

    contract = data.get("contract")
    if not isinstance(contract, dict):
        failures.append("contract must be an object")
        contract = {}
    expected_false = (
        "manual_asr_approval_required",
        "screen_translation_manual_matrix_required",
        "content_rights_human_confirmation_required",
    )
    for key in expected_false:
        if contract.get(key) is not False:
            failures.append(f"contract.{key} must be false")
    for key in ("assets_edited_by_handoff", "merge_existing_backlog", "strict_completion_required"):
        expected = False if key == "assets_edited_by_handoff" else True
        if contract.get(key) is not expected:
            failures.append(f"contract.{key} must be {str(expected).lower()}")
    if contract.get("final_quality_gate") != "./scripts/m18_offline_quality_gate.sh":
        failures.append("contract.final_quality_gate is wrong")

    if failures:
        return [AuditFinding(path, rule, "fail", "remaining gate handoff JSON drift: " + "; ".join(failures))]
    return [
        AuditFinding(
            path,
            rule,
            "pass",
            f"status={status}; plan_unchecked={plan_unchecked}; aggregate_open={aggregate_open}; commands={len(commands)}",
        )
    ]


def audit_quality_loop_handoff_json_status_index(path: Path, text: str) -> list[AuditFinding]:
    if path.name != "m18-quality-loop-handoff.json":
        return []

    rule = "quality_loop_handoff_json_status_index"
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        return [AuditFinding(path, rule, "fail", "quality loop handoff is not valid JSON")]
    if not isinstance(data, dict):
        return [AuditFinding(path, rule, "fail", "quality loop handoff root must be an object")]

    failures: list[str] = []
    if data.get("assets_edited_by_handoff") is not False:
        failures.append("assets_edited_by_handoff must be false")
    loop_status = str(data.get("loop_status") or "").strip()
    if loop_status not in {"open_until_current_rc_gates_close", "ready_for_ongoing_rc_cycle"}:
        failures.append(f"loop_status={loop_status!r}, expected open_until_current_rc_gates_close/ready_for_ongoing_rc_cycle")
    overall_status = str(data.get("overall_status") or "").strip()
    if overall_status not in {"pass", "open"}:
        failures.append(f"overall_status={overall_status!r}, expected pass/open")

    paths = data.get("paths")
    if not isinstance(paths, dict):
        failures.append("paths must be an object")
        paths = {}
    for key in ("gate_status", "action_queue", "backlog", "manual_notes_template"):
        if not str(paths.get(key) or "").strip():
            failures.append(f"paths.{key} missing")
    expected_backlog = str(paths.get("backlog") or display_path(DEFAULT_BACKLOG))

    grouped = data.get("action_ids_by_status")
    counts = data.get("counts")
    open_areas = data.get("open_areas")
    current_state = data.get("current_loop_state")
    if not isinstance(grouped, dict):
        failures.append("action_ids_by_status must be an object")
        grouped = {}
    if not isinstance(counts, dict):
        failures.append("counts must be an object")
        counts = {}
    if not isinstance(open_areas, list):
        failures.append("open_areas must be a list")
        open_areas = []
    if not isinstance(current_state, dict):
        failures.append("current_loop_state must be an object")
        current_state = {}

    expected_counts = {"open_areas": len(open_areas)}
    for status_key in ("ready", "blocked", "done"):
        ids = grouped.get(status_key)
        if not isinstance(ids, list):
            failures.append(f"action_ids_by_status.{status_key} must be a list")
            ids = []
        expected_counts[status_key] = len(ids)
        if any(not str(action_id).strip() for action_id in ids):
            failures.append(f"action_ids_by_status.{status_key} contains blank id")
    observed_counts = {str(key): value for key, value in counts.items()}
    for key, expected in expected_counts.items():
        observed = observed_counts.get(key)
        if observed != expected:
            failures.append(f"counts.{key}={observed!r}, expected {expected}")

    for key in ("gkp_backlog", "hotkey_voice_matrix"):
        if not str(current_state.get(key) or "").strip():
            failures.append(f"current_loop_state.{key} missing")

    commands = data.get("preview_backlog_commands")
    if not isinstance(commands, list):
        failures.append("preview_backlog_commands must be a list")
        commands = []
    command_ids: list[str] = []
    for index, command in enumerate(commands):
        if not isinstance(command, dict):
            failures.append(f"preview_backlog_commands[{index}] must be an object")
            continue
        command_id = str(command.get("id") or "").strip()
        if not command_id:
            failures.append(f"preview_backlog_commands[{index}] missing id")
            continue
        command_ids.append(command_id)
        command_text = str(command.get("command") or "").strip()
        if not command_text.startswith("python3 scripts/gkp_gap_backlog.py"):
            failures.append(f"{command_id} command must use gkp_gap_backlog.py")
        if command_id != "manual_notes_template":
            if str(command.get("merge_existing_backlog") or "") != expected_backlog:
                failures.append(f"{command_id} merge_existing_backlog must be {expected_backlog}")
            if "--merge-existing-backlog" not in command_text:
                failures.append(f"{command_id} command missing --merge-existing-backlog")
    if len(command_ids) != len(set(command_ids)):
        failures.append("preview_backlog_commands contains duplicate ids")
    required_command_ids = {
        "latest_request",
        "voice_qa",
        "manual_notes_template",
        "manual_notes_preview",
        "manual_notes_apply_after_review",
    }
    missing_command_ids = sorted(required_command_ids - set(command_ids))
    if missing_command_ids:
        failures.append("preview_backlog_commands missing " + ", ".join(missing_command_ids))

    contract = data.get("contract")
    if not isinstance(contract, dict):
        failures.append("contract must be an object")
        contract = {}
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
            failures.append(f"contract.{key} must be true")

    rules = data.get("fix_acceptance_rules")
    if not isinstance(rules, list):
        failures.append("fix_acceptance_rules must be a list")
        rules = []
    rules_text = "\n".join(str(rule) for rule in rules)
    for fragment in ("source ids", "golden", "real-device replay", "Do not add new game content"):
        if fragment not in rules_text:
            failures.append(f"fix_acceptance_rules missing {fragment!r}")

    if failures:
        return [AuditFinding(path, rule, "fail", "quality loop handoff JSON drift: " + "; ".join(failures))]
    return [
        AuditFinding(
            path,
            rule,
            "pass",
            f"loop_status={loop_status}; ready={expected_counts.get('ready', 0)}, "
            f"blocked={expected_counts.get('blocked', 0)}, done={expected_counts.get('done', 0)}, "
            f"preview_commands={len(commands)}",
        )
    ]


def audit_plan_execution_audit_json_status_index(path: Path, text: str) -> list[AuditFinding]:
    if path.name != "m18-plan-execution-audit.json":
        return []

    rule = "plan_execution_audit_json_status_index"
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        return [AuditFinding(path, rule, "fail", "plan execution audit is not valid JSON")]
    if not isinstance(data, dict):
        return [AuditFinding(path, rule, "fail", "plan execution audit root must be an object")]

    failures: list[str] = []
    if data.get("assets_edited_by_report") is not False:
        failures.append("assets_edited_by_report must be false")
    status = str(data.get("status") or "").strip()
    if status not in {"pass", "open"}:
        failures.append(f"status={status!r}, expected pass/open")

    counts = data.get("counts")
    tasks = data.get("tasks")
    aggregate_status = data.get("aggregate_status")
    open_gates = data.get("open_gates")
    categories = data.get("open_blocker_categories")
    if not isinstance(counts, dict):
        failures.append("counts must be an object")
        counts = {}
    if not isinstance(tasks, list):
        failures.append("tasks must be a list")
        tasks = []
    if not isinstance(aggregate_status, list):
        failures.append("aggregate_status must be a list")
        aggregate_status = []
    if not isinstance(open_gates, list):
        failures.append("open_gates must be a list")
        open_gates = []
    if not isinstance(categories, dict):
        failures.append("open_blocker_categories must be an object")
        categories = {}

    task_checked = 0
    task_unchecked = 0
    task_open_gate_count = 0
    seen_task_titles: set[str] = set()
    for index, task in enumerate(tasks):
        if not isinstance(task, dict):
            failures.append(f"tasks[{index}] must be an object")
            continue
        title = str(task.get("title") or "").strip()
        checked = task.get("checked")
        unchecked = task.get("unchecked")
        open_items = task.get("open_items")
        if not title:
            failures.append(f"tasks[{index}] missing title")
        elif title in seen_task_titles:
            failures.append(f"duplicate task title: {title}")
        else:
            seen_task_titles.add(title)
        if not isinstance(checked, int):
            failures.append(f"{title or f'tasks[{index}]'} checked must be integer")
            checked = 0
        if not isinstance(unchecked, int):
            failures.append(f"{title or f'tasks[{index}]'} unchecked must be integer")
            unchecked = 0
        if not isinstance(open_items, list):
            failures.append(f"{title or f'tasks[{index}]'} open_items must be a list")
            open_items = []
        if isinstance(unchecked, int) and len(open_items) != unchecked:
            failures.append(f"{title or f'tasks[{index}]'} open_items={len(open_items)}, expected {unchecked}")
        for item_index, item in enumerate(open_items):
            if not isinstance(item, dict):
                failures.append(f"{title or f'tasks[{index}]'} open_items[{item_index}] must be an object")
                continue
            if not str(item.get("text") or "").strip():
                failures.append(f"{title or f'tasks[{index}]'} open_items[{item_index}] missing text")
            if not str(item.get("category") or "").strip():
                failures.append(f"{title or f'tasks[{index}]'} open_items[{item_index}] missing category")
        task_checked += checked
        task_unchecked += unchecked
        task_open_gate_count += len(open_items)

    aggregate_pass = 0
    aggregate_open = 0
    for index, row in enumerate(aggregate_status):
        if not isinstance(row, dict):
            failures.append(f"aggregate_status[{index}] must be an object")
            continue
        area = str(row.get("area") or "").strip()
        row_status = str(row.get("status") or "").strip()
        if not area:
            failures.append(f"aggregate_status[{index}] missing area")
        if row_status == "pass":
            aggregate_pass += 1
        elif row_status in {"open", "missing", "fail"}:
            aggregate_open += 1
        else:
            failures.append(f"{area or f'aggregate_status[{index}]'} has unsupported status: {row_status!r}")
        if row_status != "pass" and not str(row.get("detail") or "").strip():
            failures.append(f"{area or f'aggregate_status[{index}]'} missing detail")

    observed_counts = {str(key): value for key, value in counts.items()}
    expected_counts = {
        "plan_checked": task_checked,
        "plan_unchecked": task_unchecked,
        "aggregate_pass": aggregate_pass,
        "aggregate_open": aggregate_open,
        "open_gates": task_open_gate_count + aggregate_open,
    }
    for key, expected in expected_counts.items():
        observed = observed_counts.get(key)
        if observed != expected:
            failures.append(f"counts.{key}={observed!r}, expected {expected}")
    for top_key, count_key in (("plan_checked", "plan_checked"), ("plan_unchecked", "plan_unchecked")):
        observed = data.get(top_key)
        expected = expected_counts[count_key]
        if observed != expected:
            failures.append(f"{top_key}={observed!r}, expected {expected}")

    if len(open_gates) != expected_counts["open_gates"]:
        failures.append(f"open_gates={len(open_gates)}, expected {expected_counts['open_gates']}")
    category_counts: dict[str, int] = {}
    for index, gate in enumerate(open_gates):
        if not isinstance(gate, dict):
            failures.append(f"open_gates[{index}] must be an object")
            continue
        kind = str(gate.get("kind") or "").strip()
        category = str(gate.get("category") or "").strip()
        if kind not in {"plan_item", "aggregate_status"}:
            failures.append(f"open_gates[{index}] kind={kind!r}, expected plan_item/aggregate_status")
        if not category:
            failures.append(f"open_gates[{index}] missing category")
        else:
            category_counts[category] = category_counts.get(category, 0) + 1
    observed_categories = {str(key): value for key, value in categories.items()}
    if observed_categories != category_counts:
        failures.append(f"open_blocker_categories={observed_categories!r}, expected {category_counts!r}")
    if status == "pass" and open_gates:
        failures.append("status pass cannot include open gates")
    if status == "open" and not open_gates:
        failures.append("status open requires at least one open gate")

    if failures:
        return [
            AuditFinding(
                path,
                rule,
                "fail",
                "plan execution audit JSON drift: " + "; ".join(failures),
            )
        ]
    return [
        AuditFinding(
            path,
            rule,
            "pass",
            f"checked {len(tasks)} task(s); status={status}; "
            f"checked={task_checked}, unchecked={task_unchecked}, aggregate_open={aggregate_open}",
        )
    ]


def audit_completion_audit_json_status_index(path: Path, text: str) -> list[AuditFinding]:
    if path.name != "m18-completion-audit.json":
        return []

    rule = "completion_audit_json_status_index"
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        return [AuditFinding(path, rule, "fail", "completion audit is not valid JSON")]
    if not isinstance(data, dict):
        return [AuditFinding(path, rule, "fail", "completion audit root must be an object")]

    failures: list[str] = []
    if data.get("assets_edited_by_report") is not False:
        failures.append("assets_edited_by_report must be false")
    overall = str(data.get("overall_status") or "").strip()
    if overall not in {"pass", "open", "fail"}:
        failures.append(f"overall_status={overall!r}, expected pass/open/fail")
    is_complete = data.get("is_complete")
    if not isinstance(is_complete, bool):
        failures.append("is_complete must be boolean")

    counts = data.get("counts")
    requirements = data.get("requirements")
    if not isinstance(counts, dict):
        failures.append("counts must be an object")
        counts = {}
    if not isinstance(requirements, list):
        failures.append("requirements must be a list")
        requirements = []

    requirement_counts: dict[str, int] = {"pass": 0, "open": 0, "missing": 0, "fail": 0}
    seen_ids: set[str] = set()
    for index, requirement in enumerate(requirements):
        if not isinstance(requirement, dict):
            failures.append(f"requirements[{index}] must be an object")
            continue
        requirement_id = str(requirement.get("id") or "").strip()
        status = str(requirement.get("status") or "").strip()
        if not requirement_id:
            failures.append(f"requirements[{index}] missing id")
        elif requirement_id in seen_ids:
            failures.append(f"duplicate requirement id: {requirement_id}")
        else:
            seen_ids.add(requirement_id)
        if status not in requirement_counts:
            failures.append(f"{requirement_id or f'requirements[{index}]'} has unsupported status: {status!r}")
        else:
            requirement_counts[status] += 1
        for key in ("requirement", "evidence", "detail"):
            if not str(requirement.get(key) or "").strip():
                failures.append(f"{requirement_id or f'requirements[{index}]'} missing {key}")

    observed_counts = {str(key): value for key, value in counts.items()}
    for status, expected in requirement_counts.items():
        observed = observed_counts.get(status)
        if observed != expected:
            failures.append(f"counts.{status}={observed!r}, expected {expected}")
    if overall == "pass" and any(requirement_counts[status] for status in ("open", "missing", "fail")):
        failures.append("overall_status pass cannot include open/missing/fail requirements")
    if overall == "fail" and requirement_counts["fail"] == 0 and requirement_counts["missing"] == 0:
        failures.append("overall_status fail requires at least one fail or missing requirement")
    if overall == "open" and requirement_counts["open"] == 0:
        failures.append("overall_status open requires at least one open requirement")
    if isinstance(is_complete, bool) and is_complete != (overall == "pass"):
        failures.append("is_complete must equal overall_status == pass")

    if failures:
        return [
            AuditFinding(
                path,
                rule,
                "fail",
                "completion audit JSON drift: " + "; ".join(failures),
            )
        ]
    return [
        AuditFinding(
            path,
            rule,
            "pass",
            f"checked {len(requirements)} requirement(s); overall={overall}; "
            f"pass={requirement_counts['pass']}, open={requirement_counts['open']}, "
            f"missing={requirement_counts['missing']}, fail={requirement_counts['fail']}",
        )
    ]


def build_content_rights_review_scope() -> dict[str, Any]:
    module = load_content_rights_packet_module()
    packet = module.build_packet(module.DEFAULT_GKP_ROOT, module.DEFAULT_CHECKLIST)
    return {
        "machine_audit_status": packet.machine_audit_status,
        "machine_audit_errors": list(packet.machine_audit_errors),
        "human_checkbox_status": packet.human_checkbox_status,
        "bundled_packs": len(packet.packs),
        "knowledge_files": packet.knowledge_files,
        "license_files": packet.license_files,
        "citation_files": packet.citation_files,
        "pack_inventory": [
            {
                "pack_id": pack.pack_id,
                "game_title": pack.game_title,
                "knowledge_files": pack.knowledge_files,
                "knowledge_rows": pack.knowledge_rows,
                "qa_goldens": pack.qa_goldens,
                "citations": pack.citations,
                "license_file": pack.license_file,
            }
            for pack in packet.packs
        ],
    }


def load_content_rights_packet_module():
    spec = importlib.util.spec_from_file_location("gkp_content_rights_manual_packet", CONTENT_RIGHTS_PACKET_SCRIPT)
    if spec is None or spec.loader is None:
        return None
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def load_screen_cases(path: Path) -> tuple[ScreenCase, ...]:
    if not path.is_file():
        return ()
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
                    evidence_required=row["evidence_required"],
                )
            )
    return tuple(rows)


def load_asr_review_rows(path: Path) -> tuple[AsrReviewRow, ...]:
    if not path.is_file():
        return ()
    text = path.read_text(encoding="utf-8", errors="ignore")
    section_pattern = re.compile(r"(?ms)^## (?P<pack>community\.[^\n]+)\n(?P<body>.*?)(?=^## |\Z)")
    rows: list[AsrReviewRow] = []
    for match in section_pattern.finditer(text):
        alias_row = extract_json_after_heading(match.group("body"), "aliases.json row")
        golden_row = extract_json_after_heading(match.group("body"), "qa_goldens.jsonl row")
        if not alias_row:
            continue
        source_refs = golden_row.get("source_refs") if isinstance(golden_row, dict) else []
        if not isinstance(source_refs, list):
            source_refs = []
        rows.append(
            AsrReviewRow(
                pack_id=match.group("pack").strip(),
                observed_asr=str(alias_row.get("term") or ""),
                canonical_term=str(alias_row.get("canonical_term") or ""),
                entity_id=str(alias_row.get("entity_id") or ""),
                source_refs=tuple(str(item) for item in source_refs),
            )
        )
    return tuple(rows)


def extract_json_after_heading(section_body: str, heading: str) -> dict[str, Any]:
    match = re.search(
        rf"(?ms)^### {re.escape(heading)}\n\n```json\n(?P<json>.*?)\n```",
        section_body,
    )
    if not match:
        return {}
    try:
        data = json.loads(match.group("json"))
    except json.JSONDecodeError:
        return {}
    return data if isinstance(data, dict) else {}


def extract_snippets(path: Path, text: str) -> tuple[str, ...]:
    snippets: list[str] = []
    if path.suffix == ".json":
        try:
            data = json.loads(text)
        except json.JSONDecodeError:
            data = {}
        snippets.extend(extract_json_strings(data))
    if path.suffix == ".sh":
        snippets.append(text)
    snippets.extend(extract_fenced_command_snippets(text))
    in_fence = False
    for line in text.splitlines():
        stripped = line.strip()
        if stripped.startswith("```"):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        if ("scripts/" in line or ".sh" in line) and "--" in line and looks_like_command_line(stripped):
            snippets.append(line)
    return tuple(snippets)


def extract_fenced_command_snippets(text: str) -> list[str]:
    snippets: list[str] = []
    in_fence = False
    fence_language = ""
    buffer: list[str] = []
    for line in text.splitlines():
        stripped = line.strip()
        if stripped.startswith("```"):
            if not in_fence:
                in_fence = True
                fence_language = stripped[3:].strip().lower()
                buffer = []
            else:
                if fence_language in ("bash", "sh", "shell") or fenced_block_looks_like_commands(buffer):
                    snippets.append("\n".join(buffer))
                in_fence = False
                fence_language = ""
                buffer = []
            continue
        if in_fence:
            buffer.append(line)
    return snippets


def fenced_block_looks_like_commands(lines: list[str]) -> bool:
    for line in lines:
        stripped = line.strip()
        if ("scripts/" in line or ".sh" in line) and looks_like_command_line(stripped):
            return True
    return False


def extract_json_strings(value: Any) -> list[str]:
    if isinstance(value, str):
        stripped = value.strip()
        return [value] if looks_like_command_line(stripped) else []
    if isinstance(value, list):
        strings: list[str] = []
        for item in value:
            strings.extend(extract_json_strings(item))
        return strings
    if isinstance(value, dict):
        strings = []
        for item in value.values():
            strings.extend(extract_json_strings(item))
        return strings
    return []


def iter_logical_commands(snippets: tuple[str, ...]) -> tuple[str, ...]:
    commands: list[str] = []
    for snippet in snippets:
        normalized = snippet.replace("\\n", "\n")
        current: list[str] = []
        for raw_line in normalized.splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#"):
                if current:
                    commands.append(" ".join(current))
                    current = []
                continue
            current.append(line.rstrip("\\").strip())
            if not raw_line.rstrip().endswith("\\"):
                commands.append(" ".join(current))
                current = []
        if current:
            commands.append(" ".join(current))
    return tuple(command for command in commands if command)


def has_exact_flag(command: str, flag: str) -> bool:
    return re.search(rf"(^|\s){re.escape(flag)}(\s|$)", command) is not None


def flag_value(command: str, flag: str) -> str | None:
    match = re.search(rf"(^|\s){re.escape(flag)}\s+(\"[^\"]*\"|'[^']*'|[^\s]+)", command)
    if not match:
        return None
    return match.group(2).strip("\"'")


def looks_like_command_line(line: str) -> bool:
    return line.startswith(
        (
            "$ ",
            "python3 ",
            "./",
            "RUN_",
            "JAVA_HOME=",
            "PORT=",
            "BUILD=",
            "INSTALL=",
            "CASE_FILTER=",
        )
    )


def parse_count_field(text: str, label: str) -> int | None:
    match = re.search(rf"^- {re.escape(label)}:\s*(\d+)\s*$", text, flags=re.MULTILINE)
    return int(match.group(1)) if match else None


def parse_remaining_handoff_asr_counts(text: str) -> tuple[int, int] | None:
    match = re.search(r"patch_rows=(\d+);\s*voice_cases=(\d+)", text)
    if not match:
        return None
    return int(match.group(1)), int(match.group(2))


def parse_case_filter(command: str) -> tuple[str, ...]:
    match = re.search(r"(?:^|\s)CASE_FILTER=([^\s]+)", command)
    if not match:
        return ()
    raw = match.group(1).strip("\"'")
    return tuple(case.strip() for case in raw.split(",") if case.strip())


def first_replay_case_filter(path: Path, text: str) -> tuple[str, ...]:
    snippets = extract_snippets(path, text)
    for command in iter_logical_commands(snippets):
        if "hotkey_voice_qa_batch.sh" in command and "CASE_FILTER=" in command:
            return parse_case_filter(command)
    return ()


def first_path_named(paths: tuple[Path, ...], name: str) -> Path | None:
    for path in paths:
        if path.name == name:
            return path
    return None


def render_markdown(findings: tuple[AuditFinding, ...], inputs: tuple[Path, ...]) -> str:
    counts = status_counts(findings)
    lines = [
        "# M18 Command Contract Audit",
        "",
        f"- Inputs: {len(inputs)}",
        f"- Status counts: pass={counts.get('pass', 0)}, fail={counts.get('fail', 0)}, missing={counts.get('missing', 0)}",
        "- Commands executed by this audit: no",
        "- GKP assets edited by this audit: no",
        "",
        "| File | Rule | Status | Detail |",
        "|---|---|---|---|",
    ]
    for finding in findings:
        lines.append(
            f"| `{escape_cell(display_path(finding.path))}` | `{finding.rule}` | `{finding.status}` | {escape_cell(finding.detail)} |"
        )
    return "\n".join(lines) + "\n"


def status_counts(findings: tuple[AuditFinding, ...]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for finding in findings:
        counts[finding.status] = counts.get(finding.status, 0) + 1
    return counts


def compact(value: str) -> str:
    return " ".join(value.split())[:240]


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
