#!/usr/bin/env python3
"""Safely update one row in the screen translation manual matrix."""

from __future__ import annotations

import argparse
import importlib.util
import sys
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CASES = ROOT / "scripts/screen_translation_eval_cases.tsv"
DEFAULT_MATRIX = ROOT / "docs/qa-feedback/rc-device-matrix.md"
REPORT_SCRIPT = ROOT / "scripts/screen_translation_eval_report.py"


@dataclass(frozen=True)
class MatrixUpdate:
    case_id: str
    display_name: str
    status: str
    result: str
    note_issue: str
    changed: bool


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--case-id", required=True, help="Case id from scripts/screen_translation_eval_cases.tsv.")
    parser.add_argument("--result", required=True, help="Result cell, e.g. `Pass: evidence build/...`.")
    parser.add_argument("--cases", type=Path, default=DEFAULT_CASES)
    parser.add_argument("--matrix", type=Path, default=DEFAULT_MATRIX)
    parser.add_argument(
        "--output",
        type=Path,
        help="Write updated markdown to this path. Defaults to stdout unless --apply is set.",
    )
    parser.add_argument("--apply", action="store_true", help="Update the matrix file in place.")
    args = parser.parse_args()

    try:
        updated_text, update = update_matrix_text(args.cases, args.matrix, args.case_id, args.result)
    except ValueError as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1

    if args.apply and args.output:
        print("FAIL use either --apply or --output, not both", file=sys.stderr)
        return 1
    if args.apply:
        args.matrix.write_text(updated_text, encoding="utf-8")
        destination = display_path(args.matrix)
    elif args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(updated_text, encoding="utf-8")
        destination = display_path(args.output)
    else:
        print(updated_text)
        destination = "stdout"

    print(
        "OK screen translation matrix update: "
        f"case={update.case_id}, status={update.status}, changed={'yes' if update.changed else 'no'}, "
        f"destination={destination}"
    )
    return 0


def update_matrix_text(
    cases_path: Path,
    matrix_path: Path,
    case_id: str,
    result: str,
) -> tuple[str, MatrixUpdate]:
    report = load_report_module()
    cases = {case.case_id: case for case in report.load_cases(cases_path)}
    case = cases.get(case_id)
    if case is None:
        raise ValueError(f"unknown screen translation case id: {case_id}")

    status = report.normalize_result(result)
    result_note = report.extract_result_note(result)
    note_issue = report.validate_result_note(case, status, result_note)
    if status == "missing":
        raise ValueError(f"result is not a supported status: {result}")
    if note_issue != "-":
        raise ValueError(f"invalid result note for {case_id}: {note_issue}")

    original = matrix_path.read_text(encoding="utf-8")
    duplicate_issue = duplicate_evidence_issue(report, case.display_name, result, original)
    if duplicate_issue != "-":
        raise ValueError(f"invalid result note for {case_id}: {duplicate_issue}")
    lines = original.splitlines()
    updated_lines: list[str] = []
    in_table = False
    changed = False
    found = False
    for line in lines:
        if line.startswith("## "):
            if in_table:
                in_table = False
            if line.strip() == "## Screen Translation Matrix":
                in_table = True
            updated_lines.append(line)
            continue
        if not in_table or not line.startswith("|"):
            updated_lines.append(line)
            continue
        cells = parse_markdown_row(line)
        if len(cells) != 4 or cells[0] == "Game/screen" or set(cells[0]) == {"-"}:
            updated_lines.append(line)
            continue
        if cells[0].lower() != case.display_name.lower():
            updated_lines.append(line)
            continue
        found = True
        new_line = render_markdown_row([cells[0], cells[1], cells[2], result])
        updated_lines.append(new_line)
        changed = line != new_line

    if not found:
        raise ValueError(f"case row not found in matrix for {case.display_name}")
    return "\n".join(updated_lines) + ("\n" if original.endswith("\n") else ""), MatrixUpdate(
        case_id=case.case_id,
        display_name=case.display_name,
        status=status,
        result=result,
        note_issue=note_issue,
        changed=changed,
    )


def duplicate_evidence_issue(report, display_name: str, result: str, matrix_text: str) -> str:
    status = report.normalize_result(result)
    if status != "pass":
        return "-"
    new_refs = {
        report.normalize_evidence_reference(ref)
        for ref in report.evidence_references(report.extract_result_note(result))
    }
    if not new_refs:
        return "-"
    in_table = False
    for line in matrix_text.splitlines():
        if line.startswith("## "):
            if in_table:
                in_table = False
            if line.strip() == "## Screen Translation Matrix":
                in_table = True
            continue
        if not in_table or not line.startswith("|"):
            continue
        cells = parse_markdown_row(line)
        if len(cells) != 4 or cells[0] == "Game/screen" or set(cells[0]) == {"-"}:
            continue
        if cells[0].lower() == display_name.lower():
            continue
        existing_result = cells[3]
        if report.normalize_result(existing_result) != "pass":
            continue
        existing_refs = {
            report.normalize_evidence_reference(ref)
            for ref in report.evidence_references(report.extract_result_note(existing_result))
        }
        duplicate = sorted(new_refs & existing_refs)
        if duplicate:
            return f"duplicate_evidence_path:{duplicate[0]}"
    return "-"


def parse_markdown_row(line: str) -> list[str]:
    return [cell.strip().replace("\\|", "|") for cell in line.strip().strip("|").split("|")]


def render_markdown_row(cells: list[str]) -> str:
    return "| " + " | ".join(escape_markdown_cell(cell) for cell in cells) + " |"


def escape_markdown_cell(value: str) -> str:
    return value.replace("|", "\\|").replace("\n", " ")


def load_report_module():
    spec = importlib.util.spec_from_file_location("screen_translation_eval_report", REPORT_SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
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
