#!/usr/bin/env python3
"""Preview or update ASR/content-rights approval sections in the M18 receipt.

This helper records reviewer input only. It does not apply GKP patches, update
the screen translation matrix, edit bundled GKP assets, or close release
checklist items.
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

SECTION_TO_KEY = {
    "asr-patch-approval": "asr_patch_approval",
    "content-rights-human-review": "content_rights_review",
}


@dataclass(frozen=True)
class ApprovalUpdate:
    section_id: str
    status: str
    decision: str
    receipt_status: str
    changed: bool


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--section", required=True, choices=sorted(SECTION_TO_KEY))
    parser.add_argument("--decision", required=True, choices=("approved", "rejected", "pending"))
    parser.add_argument("--approval-phrase", default="", help="Exact phrase required when --decision approved.")
    parser.add_argument("--reviewer", default="", help="Required reviewer name/id when --decision approved.")
    parser.add_argument("--notes", default="", help="Optional reviewer notes.")
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
            section_id=args.section,
            decision=args.decision,
            approval_phrase=args.approval_phrase,
            reviewer=args.reviewer,
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
        "OK M18 manual gate receipt update: "
        f"section={update.section_id}, decision={update.decision}, status={update.status}, "
        f"changed={'yes' if update.changed else 'no'}, receipt_status={update.receipt_status}, "
        f"destination={destination}"
    )
    return 0


def update_receipt_data(
    *,
    intake_path: Path,
    receipt_path: Path,
    screen_cases_path: Path,
    section_id: str,
    decision: str,
    approval_phrase: str,
    reviewer: str,
    notes: str = "",
) -> tuple[dict[str, Any], ApprovalUpdate]:
    if section_id not in SECTION_TO_KEY:
        raise ValueError(f"unsupported receipt section: {section_id}")
    check = load_receipt_check_module()
    intake = check.load_json(intake_path, "manual gate intake")
    screen_cases = check.load_screen_cases(screen_cases_path)
    template = check.build_template(intake, screen_cases)
    payload_key = SECTION_TO_KEY[section_id]
    template_payload = template.get(payload_key)
    if not isinstance(template_payload, dict):
        raise ValueError(f"receipt section is not ready in the current intake packet: {section_id}")

    original_data = load_existing_or_template(receipt_path, template)
    original_text = json.dumps(original_data, ensure_ascii=False, sort_keys=True)
    data = json.loads(json.dumps(original_data, ensure_ascii=False))
    preserve_template_sections(data, template)
    update_approval_payload(
        data,
        template_payload,
        payload_key,
        decision.strip().lower(),
        approval_phrase.strip(),
        reviewer.strip(),
        notes.strip(),
    )

    receipt_check = check.build_check(intake, write_virtual_receipt(data), screen_cases, screen_cases_path)
    target = receipt_item(receipt_check, section_id)
    if target is None:
        raise ValueError(f"receipt check did not return item for section: {section_id}")
    if target.status == "fail":
        raise ValueError(f"invalid receipt update for {section_id}: {target.detail}")
    if decision.strip().lower() == "approved" and target.status != "pass":
        raise ValueError(f"approved receipt update for {section_id} is not passing: {target.detail}")

    updated_text = json.dumps(data, ensure_ascii=False, sort_keys=True)
    return data, ApprovalUpdate(
        section_id=section_id,
        status=target.status,
        decision=decision.strip().lower(),
        receipt_status=receipt_check.status,
        changed=updated_text != original_text,
    )


def load_existing_or_template(receipt_path: Path, template: dict[str, Any]) -> dict[str, Any]:
    if receipt_path.is_file():
        parsed = json.loads(receipt_path.read_text(encoding="utf-8"))
        if not isinstance(parsed, dict):
            raise ValueError("manual gate receipt must be a JSON object")
        return parsed
    data = json.loads(json.dumps(template, ensure_ascii=False))
    for entry in data.get("screen_translation_results", []):
        if isinstance(entry, dict):
            entry["result"] = "Pending"
            entry["notes"] = ""
    return data


def preserve_template_sections(data: dict[str, Any], template: dict[str, Any]) -> None:
    data.setdefault("schema_version", template.get("schema_version"))
    data.setdefault("objective", template.get("objective"))
    for key in SECTION_TO_KEY.values():
        if key not in data and key in template:
            data[key] = json.loads(json.dumps(template[key], ensure_ascii=False))
    if "screen_translation_results" not in data and "screen_translation_results" in template:
        rows = json.loads(json.dumps(template["screen_translation_results"], ensure_ascii=False))
        for entry in rows:
            if isinstance(entry, dict):
                entry["result"] = "Pending"
                entry["notes"] = ""
        data["screen_translation_results"] = rows


def update_approval_payload(
    data: dict[str, Any],
    template_payload: dict[str, Any],
    payload_key: str,
    decision: str,
    approval_phrase: str,
    reviewer: str,
    notes: str,
) -> None:
    if decision not in {"approved", "rejected", "pending"}:
        raise ValueError(f"unsupported decision: {decision}")
    payload = json.loads(json.dumps(template_payload, ensure_ascii=False))
    payload["decision"] = decision
    payload["approval_phrase"] = approval_phrase
    payload["reviewer"] = reviewer
    payload["notes"] = notes
    data[payload_key] = payload


def receipt_item(check: Any, item_id: str) -> Any | None:
    for item in check.items:
        if item.item_id == item_id:
            return item
    return None


def write_virtual_receipt(data: dict[str, Any]) -> Path:
    import tempfile

    path = Path(tempfile.mkdtemp(prefix="retrosprite-receipt-approval-update-")) / "receipt.json"
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
