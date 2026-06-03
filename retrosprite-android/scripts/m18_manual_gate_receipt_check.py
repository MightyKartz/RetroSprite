#!/usr/bin/env python3
"""Create and validate manual-gate receipt input for M18."""

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
DEFAULT_INTAKE = ROOT / "docs/qa-feedback/m18-manual-gate-intake.json"
DEFAULT_RECEIPT = ROOT / "docs/qa-feedback/m18-manual-gate-receipt.json"
DEFAULT_TEMPLATE = ROOT / "docs/qa-feedback/m18-manual-gate-receipt-template.json"
DEFAULT_OUTPUT = ROOT / "docs/qa-feedback/m18-manual-gate-receipt-check.md"
DEFAULT_JSON_OUTPUT = ROOT / "docs/qa-feedback/m18-manual-gate-receipt-check.json"
DEFAULT_SCREEN_CASES = ROOT / "scripts/screen_translation_eval_cases.tsv"
SCREEN_REPORT_SCRIPT = ROOT / "scripts/screen_translation_eval_report.py"
CONTENT_RIGHTS_PACKET_SCRIPT = ROOT / "scripts/gkp_content_rights_manual_packet.py"

ASR_APPROVAL_PHRASE = "I approve gkp patch review packet 20260601 hotkey voice"
CONTENT_RIGHTS_APPROVAL_PHRASE = "I confirm gkp content rights human spot check"
RECEIPT_SCHEMA_VERSION = 1
RECEIPT_OBJECTIVE = "M18 Eval Lab + GKP Quality Loop"


@dataclass(frozen=True)
class ReceiptItem:
    item_id: str
    status: str
    detail: str


@dataclass(frozen=True)
class ReceiptCheck:
    status: str
    receipt_present: bool
    items: tuple[ReceiptItem, ...]


@dataclass(frozen=True)
class ScreenCase:
    case_id: str
    display_name: str
    game_label: str
    screen_type: str
    trigger_phrase: str
    expected_layout: str
    expected_language: str
    number_policy: str
    evidence_required: str


@dataclass(frozen=True)
class AsrReviewRow:
    pack_id: str
    observed_asr: str
    canonical_term: str
    entity_id: str
    source_refs: tuple[str, ...]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--intake", type=Path, default=DEFAULT_INTAKE)
    parser.add_argument("--receipt", type=Path, default=DEFAULT_RECEIPT)
    parser.add_argument("--screen-cases", type=Path, default=DEFAULT_SCREEN_CASES)
    parser.add_argument("--template-output", type=Path, default=DEFAULT_TEMPLATE)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--json-output", type=Path, default=DEFAULT_JSON_OUTPUT)
    parser.add_argument("--strict", action="store_true", help="Exit nonzero unless the receipt is valid and complete.")
    args = parser.parse_args()

    try:
        intake = load_json(args.intake, "manual gate intake")
        screen_cases = load_screen_cases(args.screen_cases)
        template = build_template(intake, screen_cases)
        check = build_check(intake, args.receipt, screen_cases, args.screen_cases)
    except ValueError as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1

    args.template_output.parent.mkdir(parents=True, exist_ok=True)
    args.template_output.write_text(json.dumps(template, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(render_markdown(check, args.receipt, args.template_output), encoding="utf-8")
    args.json_output.parent.mkdir(parents=True, exist_ok=True)
    args.json_output.write_text(
        json.dumps(render_json(check, args.receipt, args.template_output), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    counts = status_counts(check.items)
    print(
        "OK M18 manual gate receipt check: "
        f"status={check.status}, "
        f"receipt={'present' if check.receipt_present else 'missing'}, "
        f"pass={counts.get('pass', 0)}, "
        f"open={counts.get('open', 0)}, "
        f"fail={counts.get('fail', 0)}"
    )
    if args.strict and check.status != "pass":
        return 1
    return 0


def build_template(intake: dict[str, Any], screen_cases: tuple[ScreenCase, ...]) -> dict[str, Any]:
    ready_ids = ready_section_ids(intake)
    asr_review_rows = asr_review_rows_from_intake(intake)
    template: dict[str, Any] = {
        "schema_version": 1,
        "objective": "M18 Eval Lab + GKP Quality Loop",
        "notes": "Fill only the sections that are present. Approved sections require reviewer. Do not edit ASR review_rows or content_rights_review.review_scope; they must match the current packets. Replace placeholder evidence paths such as <timestamp> with unique directories under build/rc-device-evidence/ before saving the receipt. Screen translation Pass evidence must be captured with ./scripts/rc_device_evidence.sh --gate screen_translation --case-id <case_id> --include-screenshot so metadata.json matches the row and screenshot.png is present, and Pass results must keep the generated checklist= tokens. Do not add unknown screen case ids or duplicate rows.",
    }
    if "asr-patch-approval" in ready_ids:
        template["asr_patch_approval"] = {
            "decision": "pending",
            "approval_phrase": "",
            "reviewer": "",
            "review_rows": [asr_review_row_to_json(row) for row in asr_review_rows],
            "notes": "",
        }
    if "screen-translation-manual-results" in ready_ids:
        template["screen_translation_results"] = [
            {
                "case_id": case.case_id,
                "game_label": case.game_label,
                "screen_type": case.screen_type,
                "trigger_phrase": case.trigger_phrase,
                "expected_layout": case.expected_layout,
                "expected_language": case.expected_language,
                "number_policy": case.number_policy,
                "evidence_required": case.evidence_required,
                "result": screen_pass_result_template(case),
                "notes": "",
            }
            for case in screen_cases
        ]
    if "content-rights-human-review" in ready_ids:
        template["content_rights_review"] = {
            "decision": "pending",
            "approval_phrase": "",
            "reviewer": "",
            "review_scope": build_content_rights_review_scope(),
            "notes": "",
        }
    return template


def build_check(
    intake: dict[str, Any],
    receipt_path: Path,
    screen_cases: tuple[ScreenCase, ...],
    screen_cases_path: Path = DEFAULT_SCREEN_CASES,
) -> ReceiptCheck:
    ready_ids = ready_section_ids(intake)
    if not receipt_path.is_file():
        items = tuple(
            ReceiptItem(section_id, "open", "receipt file missing; use the generated template")
            for section_id in sorted(ready_ids)
        )
        return ReceiptCheck(status="open" if items else "pass", receipt_present=False, items=items)

    receipt = load_json(receipt_path, "manual gate receipt")
    asr_review_rows = asr_review_rows_from_intake(intake)
    items: list[ReceiptItem] = list(check_receipt_metadata(receipt))
    if "asr-patch-approval" in ready_ids:
        items.append(check_asr_approval(receipt.get("asr_patch_approval"), asr_review_rows))
    if "screen-translation-manual-results" in ready_ids:
        items.extend(check_screen_results(receipt.get("screen_translation_results"), screen_cases, screen_cases_path))
    if "content-rights-human-review" in ready_ids:
        items.append(check_content_rights_approval(receipt.get("content_rights_review")))
    status = "pass"
    if any(item.status == "fail" for item in items):
        status = "fail"
    elif any(item.status != "pass" for item in items):
        status = "open"
    return ReceiptCheck(status=status, receipt_present=True, items=tuple(items))


def check_receipt_metadata(receipt: dict[str, Any]) -> tuple[ReceiptItem, ...]:
    items: list[ReceiptItem] = []
    schema_version = receipt.get("schema_version")
    objective = str(receipt.get("objective", "")).strip()
    if schema_version != RECEIPT_SCHEMA_VERSION:
        items.append(
            ReceiptItem(
                "receipt-schema-version",
                "fail",
                f"schema_version must be {RECEIPT_SCHEMA_VERSION}",
            )
        )
    else:
        items.append(ReceiptItem("receipt-schema-version", "pass", "schema_version matches"))
    if objective != RECEIPT_OBJECTIVE:
        items.append(ReceiptItem("receipt-objective", "fail", "objective does not match M18"))
    else:
        items.append(ReceiptItem("receipt-objective", "pass", "objective matches M18"))
    return tuple(items)


def check_approval(item_id: str, payload: Any, required_phrase: str) -> ReceiptItem:
    if not isinstance(payload, dict):
        return ReceiptItem(item_id, "open", "receipt section missing")
    decision = str(payload.get("decision", "pending")).strip().lower()
    phrase = str(payload.get("approval_phrase", "")).strip()
    if decision == "pending" or not decision:
        return ReceiptItem(item_id, "open", "decision is pending")
    if decision == "rejected":
        return ReceiptItem(item_id, "open", f"rejected: {payload.get('notes', '')}".strip())
    if decision != "approved":
        return ReceiptItem(item_id, "fail", f"unsupported decision: {decision}")
    if phrase != required_phrase:
        return ReceiptItem(item_id, "fail", "approval phrase does not match the required exact phrase")
    if not str(payload.get("reviewer", "")).strip():
        return ReceiptItem(item_id, "fail", "reviewer is required for approved receipt")
    return ReceiptItem(item_id, "pass", "approved with exact phrase")


def check_asr_approval(payload: Any, expected_rows: tuple[AsrReviewRow, ...]) -> ReceiptItem:
    base = check_approval("asr-patch-approval", payload, ASR_APPROVAL_PHRASE)
    if base.status != "pass":
        return base
    if not isinstance(payload, dict):
        return base
    actual_rows = asr_review_rows_from_payload(payload.get("review_rows"))
    if tuple(asr_review_row_to_json(row) for row in actual_rows) != tuple(
        asr_review_row_to_json(row) for row in expected_rows
    ):
        return ReceiptItem(
            "asr-patch-approval",
            "fail",
            "review_rows must match the current ASR review packet rows exactly",
        )
    return ReceiptItem(
        "asr-patch-approval",
        "pass",
        f"approved with exact phrase and {len(expected_rows)} review row(s)",
    )


def check_content_rights_approval(payload: Any) -> ReceiptItem:
    base = check_approval("content-rights-human-review", payload, CONTENT_RIGHTS_APPROVAL_PHRASE)
    if base.status != "pass":
        return base
    if not isinstance(payload, dict):
        return base
    expected_scope = build_content_rights_review_scope()
    if payload.get("review_scope") != expected_scope:
        return ReceiptItem(
            "content-rights-human-review",
            "fail",
            "review_scope must match the current GKP content-rights packet exactly",
        )
    if expected_scope.get("machine_audit_status") != "pass":
        return ReceiptItem(
            "content-rights-human-review",
            "fail",
            "machine audit must pass before content-rights approval can be accepted",
        )
    return ReceiptItem(
        "content-rights-human-review",
        "pass",
        f"approved with exact phrase and {expected_scope.get('bundled_packs', 0)} pack scope",
    )


def check_screen_results(
    payload: Any,
    screen_cases: tuple[ScreenCase, ...],
    screen_cases_path: Path = DEFAULT_SCREEN_CASES,
) -> tuple[ReceiptItem, ...]:
    if not isinstance(payload, list):
        return tuple(ReceiptItem(case.case_id, "open", "screen translation receipt list missing") for case in screen_cases)
    by_id: dict[str, Any] = {}
    items: list[ReceiptItem] = []
    expected_ids = {case.case_id for case in screen_cases}
    seen_ids: set[str] = set()
    for index, entry in enumerate(payload, start=1):
        if not isinstance(entry, dict):
            items.append(ReceiptItem(f"screen-translation-entry-{index}", "fail", "screen translation receipt entry must be an object"))
            continue
        case_id = str(entry.get("case_id", "")).strip()
        if not case_id:
            items.append(ReceiptItem(f"screen-translation-entry-{index}", "fail", "screen translation receipt entry is missing case_id"))
            continue
        if case_id in seen_ids:
            items.append(ReceiptItem(case_id, "fail", "duplicate screen translation receipt case_id"))
            continue
        seen_ids.add(case_id)
        if case_id not in expected_ids:
            items.append(ReceiptItem(case_id, "fail", "unknown screen translation receipt case_id"))
            continue
        by_id[case_id] = entry
    report = load_screen_report_module()
    cases_by_id = {case.case_id: case for case in report.load_cases(screen_cases_path)}
    evidence_refs_by_case: dict[str, tuple[str, ...]] = {}
    pending_pass_items: dict[str, ReceiptItem] = {}
    for case in screen_cases:
        entry = by_id.get(case.case_id)
        if not isinstance(entry, dict):
            items.append(ReceiptItem(case.case_id, "open", "receipt result missing"))
            continue
        snapshot_issue = validate_screen_case_snapshot(case, entry)
        if snapshot_issue:
            items.append(ReceiptItem(case.case_id, "fail", snapshot_issue))
            continue
        result = str(entry.get("result", "")).strip()
        status = report.normalize_result(result)
        note = report.extract_result_note(result)
        report_case = cases_by_id.get(case.case_id)
        if report_case is None:
            items.append(ReceiptItem(case.case_id, "fail", "case missing from eval report cases"))
            continue
        note_issue = report.validate_result_note(report_case, status, note)
        if status in {"missing", "not_run"}:
            items.append(ReceiptItem(case.case_id, "open", "result is pending or unsupported"))
        elif note_issue != "-":
            items.append(ReceiptItem(case.case_id, "fail", f"{status}: {note_issue}"))
        elif status == "pass":
            pending_pass_items[case.case_id] = ReceiptItem(case.case_id, "pass", f"{status}: {note or '-'}")
            evidence_refs_by_case[case.case_id] = tuple(
                report.normalize_evidence_reference(ref)
                for ref in report.evidence_references(note)
            )
        else:
            items.append(ReceiptItem(case.case_id, "pass", f"{status}: {note or '-'}"))
    duplicate_cases_by_ref: dict[str, list[str]] = {}
    for case_id, refs in evidence_refs_by_case.items():
        for ref in refs:
            duplicate_cases_by_ref.setdefault(ref, []).append(case_id)
    duplicate_case_ids: set[str] = set()
    for ref, case_ids in duplicate_cases_by_ref.items():
        if len(case_ids) <= 1:
            continue
        for case_id in case_ids:
            duplicate_case_ids.add(case_id)
            items.append(ReceiptItem(case_id, "fail", f"pass: duplicate_evidence_path:{ref}"))
    for case_id, item in pending_pass_items.items():
        if case_id not in duplicate_case_ids:
            items.append(item)
    return tuple(items)


def render_markdown(check: ReceiptCheck, receipt_path: Path, template_path: Path) -> str:
    counts = status_counts(check.items)
    lines = [
        "# M18 Manual Gate Receipt Check",
        "",
        f"- Receipt: `{display_path(receipt_path)}`",
        f"- Template: `{display_path(template_path)}`",
        f"- Receipt present: `{'yes' if check.receipt_present else 'no'}`",
        f"- Overall status: `{check.status}`",
        f"- Item counts: pass={counts.get('pass', 0)}, open={counts.get('open', 0)}, fail={counts.get('fail', 0)}",
        "- GKP assets edited by this check: no",
        "",
        "| Item | Status | Detail |",
        "|---|---|---|",
    ]
    if not check.items:
        lines.append("| - | `pass` | no ready manual receipt input is required |")
    else:
        for item in check.items:
            lines.append(f"| `{escape_cell(item.item_id)}` | `{item.status}` | {escape_cell(item.detail)} |")
    lines.extend(["", "## How To Use", ""])
    if check.status == "pass":
        lines.append("- The receipt is valid. Run `python3 scripts/m18_manual_gate_receipt_plan.py` to generate the guarded command plan before applying anything.")
    else:
        lines.append("- Fill `docs/qa-feedback/m18-manual-gate-receipt-template.json` and save it as `docs/qa-feedback/m18-manual-gate-receipt.json`, then rerun this check.")
    return "\n".join(lines) + "\n"


def render_json(check: ReceiptCheck, receipt_path: Path, template_path: Path) -> dict[str, Any]:
    counts = status_counts(check.items)
    return {
        "schema_version": 1,
        "objective": RECEIPT_OBJECTIVE,
        "status": check.status,
        "receipt_present": check.receipt_present,
        "receipt": display_path(receipt_path),
        "template": display_path(template_path),
        "counts": {
            "pass": counts.get("pass", 0),
            "open": counts.get("open", 0),
            "fail": counts.get("fail", 0),
        },
        "assets_edited_by_report": False,
        "items": [
            {
                "id": item.item_id,
                "status": item.status,
                "detail": item.detail,
            }
            for item in check.items
        ],
    }


def ready_section_ids(intake: dict[str, Any]) -> set[str]:
    return {
        str(section.get("id"))
        for section in intake.get("sections", [])
        if str(section.get("status")) == "ready"
    }


def validate_screen_case_snapshot(case: ScreenCase, entry: dict[str, Any]) -> str | None:
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
        f"{key}={entry.get(key)!r}"
        for key, value in expected.items()
        if str(entry.get(key, "")).strip() != value
    ]
    if mismatches:
        return "case policy snapshot must match screen_translation_eval_cases.tsv: " + ", ".join(mismatches)
    return None


def screen_pass_result_template(case: ScreenCase) -> str:
    return "Pass: evidence build/rc-device-evidence/<timestamp> checklist=" + ",".join(screen_pass_checklist_tokens(case))


def screen_pass_checklist_tokens(case: ScreenCase) -> tuple[str, ...]:
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
    return tuple(tokens)


def asr_review_rows_from_intake(intake: dict[str, Any]) -> tuple[AsrReviewRow, ...]:
    rows: list[AsrReviewRow] = []
    for section in intake.get("sections", []):
        if not isinstance(section, dict) or section.get("id") != "asr-patch-approval":
            continue
        for item in section.get("required_input", []):
            if not isinstance(item, str):
                continue
            row = parse_asr_review_row(item)
            if row is not None:
                rows.append(row)
    return tuple(rows)


def parse_asr_review_row(value: str) -> AsrReviewRow | None:
    match = re.search(
        r"^(?P<pack>community\.[^:]+):\s*`(?P<observed>[^`]+)`\s*->\s*`(?P<canonical>[^`]+)`;\s*"
        r"entity=`(?P<entity>[^`]+)`;\s*source_refs=`(?P<sources>[^`]*)`",
        value.strip(),
    )
    if not match:
        return None
    return AsrReviewRow(
        pack_id=match.group("pack").strip(),
        observed_asr=match.group("observed").strip(),
        canonical_term=match.group("canonical").strip(),
        entity_id=match.group("entity").strip(),
        source_refs=tuple(
            source.strip()
            for source in match.group("sources").split(",")
            if source.strip()
        ),
    )


def asr_review_rows_from_payload(payload: Any) -> tuple[AsrReviewRow, ...]:
    if not isinstance(payload, list):
        return ()
    rows: list[AsrReviewRow] = []
    for entry in payload:
        if not isinstance(entry, dict):
            return ()
        source_refs = entry.get("source_refs")
        if not isinstance(source_refs, list):
            return ()
        rows.append(
            AsrReviewRow(
                pack_id=str(entry.get("pack_id") or ""),
                observed_asr=str(entry.get("observed_asr") or ""),
                canonical_term=str(entry.get("canonical_term") or ""),
                entity_id=str(entry.get("entity_id") or ""),
                source_refs=tuple(str(item) for item in source_refs),
            )
        )
    return tuple(rows)


def asr_review_row_to_json(row: AsrReviewRow) -> dict[str, Any]:
    return {
        "pack_id": row.pack_id,
        "observed_asr": row.observed_asr,
        "canonical_term": row.canonical_term,
        "entity_id": row.entity_id,
        "source_refs": list(row.source_refs),
    }


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
                    display_name=row["id"].replace("_", " "),
                    game_label=row["game_label"],
                    screen_type=row["screen_type"],
                    trigger_phrase=row["trigger_phrase"],
                    expected_layout=row["expected_layout"],
                    expected_language=row["expected_language"],
                    number_policy=row["number_policy"],
                    evidence_required=row["evidence_required"],
                )
            )
    if not rows:
        raise ValueError(f"no screen translation cases found: {path}")
    return tuple(rows)


def load_json(path: Path, label: str) -> dict[str, Any]:
    if not path.is_file():
        raise ValueError(f"{label} not found: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def status_counts(items: tuple[ReceiptItem, ...]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for item in items:
        counts[item.status] = counts.get(item.status, 0) + 1
    return counts


def load_screen_report_module():
    spec = importlib.util.spec_from_file_location("screen_translation_eval_report", SCREEN_REPORT_SCRIPT)
    if spec is None or spec.loader is None:
        raise ValueError(f"cannot load screen translation report script: {SCREEN_REPORT_SCRIPT}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def load_content_rights_packet_module():
    spec = importlib.util.spec_from_file_location("gkp_content_rights_manual_packet", CONTENT_RIGHTS_PACKET_SCRIPT)
    if spec is None or spec.loader is None:
        raise ValueError(f"cannot load content rights packet script: {CONTENT_RIGHTS_PACKET_SCRIPT}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


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
