#!/usr/bin/env python3
"""Preview or update one screen translation result in the M18 receipt JSON.

This helper records a tester-provided result only. It does not decide whether a
screen passed, update the device matrix, edit GKP assets, or close any release
checklist item.
"""

from __future__ import annotations

import argparse
import importlib.util
import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_INTAKE = ROOT / "docs/qa-feedback/m18-manual-gate-intake.json"
DEFAULT_RECEIPT = ROOT / "docs/qa-feedback/m18-manual-gate-receipt.json"
DEFAULT_SCREEN_CASES = ROOT / "scripts/screen_translation_eval_cases.tsv"
RECEIPT_CHECK_SCRIPT = ROOT / "scripts/m18_manual_gate_receipt_check.py"


@dataclass(frozen=True)
class ReceiptUpdate:
    case_id: str
    status: str
    result: str
    receipt_status: str
    changed: bool


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--case-id", required=True, help="Case id from scripts/screen_translation_eval_cases.tsv.")
    parser.add_argument("--result", required=True, help="Result note, for example `Pass: evidence build/... checklist=...`.")
    parser.add_argument("--notes", default="", help="Optional tester note for this case.")
    parser.add_argument("--intake", type=Path, default=DEFAULT_INTAKE)
    parser.add_argument("--receipt", type=Path, default=DEFAULT_RECEIPT)
    parser.add_argument("--screen-cases", type=Path, default=DEFAULT_SCREEN_CASES)
    parser.add_argument("--output", type=Path, help="Write the updated receipt JSON to this path for review.")
    parser.add_argument("--apply", action="store_true", help="Update the receipt file in place.")
    args = parser.parse_args()

    if args.apply and args.output:
        print("FAIL use either --apply or --output, not both", file=sys.stderr)
        return 1

    try:
        updated, update = update_receipt_data(
            intake_path=args.intake,
            receipt_path=args.receipt,
            screen_cases_path=args.screen_cases,
            case_id=args.case_id,
            result=args.result,
            notes=args.notes,
        )
    except ValueError as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1

    output_text = json.dumps(updated, ensure_ascii=False, indent=2) + "\n"
    if args.apply:
        args.receipt.parent.mkdir(parents=True, exist_ok=True)
        args.receipt.write_text(output_text, encoding="utf-8")
        destination = display_path(args.receipt)
    elif args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(output_text, encoding="utf-8")
        destination = display_path(args.output)
    else:
        print(output_text, end="")
        destination = "stdout"

    print(
        "OK screen translation receipt update: "
        f"case={update.case_id}, status={update.status}, changed={'yes' if update.changed else 'no'}, "
        f"receipt_status={update.receipt_status}, destination={destination}"
    )
    return 0


def update_receipt_data(
    intake_path: Path,
    receipt_path: Path,
    screen_cases_path: Path,
    case_id: str,
    result: str,
    notes: str = "",
) -> tuple[dict[str, Any], ReceiptUpdate]:
    check = load_receipt_check_module()
    intake = check.load_json(intake_path, "manual gate intake")
    screen_cases = check.load_screen_cases(screen_cases_path)
    template = check.build_template(intake, screen_cases)
    template_screen_rows = template.get("screen_translation_results")
    if not isinstance(template_screen_rows, list):
        raise ValueError("screen translation manual gate is not ready in the current intake packet")

    case_by_id = {case.case_id: case for case in screen_cases}
    if case_id not in case_by_id:
        raise ValueError(f"unknown screen translation case id: {case_id}")

    original_data = load_existing_or_template(receipt_path, template)
    original_text = json.dumps(original_data, ensure_ascii=False, sort_keys=True)
    data = json.loads(json.dumps(original_data, ensure_ascii=False))
    rows = data.get("screen_translation_results")
    if not isinstance(rows, list):
        rows = json.loads(json.dumps(template_screen_rows, ensure_ascii=False))
        data["screen_translation_results"] = rows

    template_by_id = {
        str(entry.get("case_id")): entry
        for entry in template_screen_rows
        if isinstance(entry, dict)
    }
    normalize_unfilled_template_rows(rows, template_by_id)
    update_screen_row(rows, template_by_id[case_id], case_id, result.strip(), notes.strip())
    validate_screen_update(check, data, screen_cases, screen_cases_path, case_id)
    receipt_check = check.build_check(intake, write_virtual_receipt(data), screen_cases, screen_cases_path)
    updated_text = json.dumps(data, ensure_ascii=False, sort_keys=True)
    target_status = status_for_case(check, data, screen_cases, screen_cases_path, case_id)
    return data, ReceiptUpdate(
        case_id=case_id,
        status=target_status,
        result=result.strip(),
        receipt_status=receipt_check.status,
        changed=updated_text != original_text,
    )


def load_existing_or_template(receipt_path: Path, template: dict[str, Any]) -> dict[str, Any]:
    if not receipt_path.is_file():
        data = json.loads(json.dumps(template, ensure_ascii=False))
        for entry in data.get("screen_translation_results", []):
            if isinstance(entry, dict):
                entry["result"] = "Pending"
                entry["notes"] = ""
        return data
    parsed = json.loads(receipt_path.read_text(encoding="utf-8"))
    if not isinstance(parsed, dict):
        raise ValueError("manual gate receipt must be a JSON object")
    return parsed


def normalize_unfilled_template_rows(rows: list[Any], template_by_id: dict[str, dict[str, Any]]) -> None:
    seen: set[str] = set()
    for entry in rows:
        if not isinstance(entry, dict):
            raise ValueError("screen_translation_results entries must be objects")
        row_case_id = str(entry.get("case_id", "")).strip()
        if not row_case_id:
            raise ValueError("screen_translation_results entry is missing case_id")
        if row_case_id in seen:
            raise ValueError(f"duplicate screen translation receipt case_id: {row_case_id}")
        seen.add(row_case_id)
        template_entry = template_by_id.get(row_case_id)
        if template_entry is None:
            raise ValueError(f"unknown screen translation receipt case_id: {row_case_id}")
        if str(entry.get("result", "")).strip() == str(template_entry.get("result", "")).strip():
            entry["result"] = "Pending"


def update_screen_row(
    rows: list[Any],
    template_entry: dict[str, Any],
    case_id: str,
    result: str,
    notes: str,
) -> None:
    for index, entry in enumerate(rows):
        if isinstance(entry, dict) and str(entry.get("case_id")) == case_id:
            rows[index] = screen_row_from_template(template_entry, result, notes)
            return
    rows.append(screen_row_from_template(template_entry, result, notes))


def screen_row_from_template(template_entry: dict[str, Any], result: str, notes: str) -> dict[str, Any]:
    row = {
        key: value
        for key, value in template_entry.items()
        if key not in {"result", "notes"}
    }
    row["result"] = result
    row["notes"] = notes
    return row


def validate_screen_update(
    check: Any,
    data: dict[str, Any],
    screen_cases: tuple[Any, ...],
    screen_cases_path: Path,
    case_id: str,
) -> None:
    items = check.check_screen_results(data.get("screen_translation_results"), screen_cases, screen_cases_path)
    target_items = [item for item in items if item.item_id == case_id]
    if not target_items:
        raise ValueError(f"receipt check did not return item for case: {case_id}")
    target = target_items[-1]
    if target.status == "fail":
        raise ValueError(f"invalid result note for {case_id}: {target.detail}")
    if target.status == "open":
        raise ValueError(f"result for {case_id} is still pending: {target.detail}")


def status_for_case(
    check: Any,
    data: dict[str, Any],
    screen_cases: tuple[Any, ...],
    screen_cases_path: Path,
    case_id: str,
) -> str:
    items = check.check_screen_results(data.get("screen_translation_results"), screen_cases, screen_cases_path)
    for item in reversed(items):
        if item.item_id == case_id:
            return item.detail.split(":", 1)[0]
    return "missing"


def write_virtual_receipt(data: dict[str, Any]) -> Path:
    import tempfile

    path = Path(tempfile.mkdtemp(prefix="retrosprite-receipt-update-")) / "receipt.json"
    path.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    return path


def load_receipt_check_module() -> Any:
    spec = importlib.util.spec_from_file_location("m18_manual_gate_receipt_check", RECEIPT_CHECK_SCRIPT)
    if spec is None or spec.loader is None:
        raise ValueError(f"cannot load receipt check script: {RECEIPT_CHECK_SCRIPT}")
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


if __name__ == "__main__":
    raise SystemExit(main())
