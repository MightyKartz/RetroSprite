#!/usr/bin/env python3
"""Report M18 screen translation QA status from the TSV case source."""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CASES = ROOT / "scripts/screen_translation_eval_cases.tsv"
DEFAULT_MATRIX = ROOT / "docs/qa-feedback/rc-device-matrix.md"
EVIDENCE_ROOT = ROOT / "build/rc-device-evidence"

REQUIRED_COLUMNS = [
    "id",
    "game_label",
    "screen_type",
    "trigger_phrase",
    "expected_layout",
    "expected_language",
    "number_policy",
    "evidence_required",
]

STATUS_ORDER = {
    "pass": 0,
    "fail": 1,
    "blocked": 2,
    "not_run": 3,
    "missing": 4,
}

REQUIRED_EVIDENCE_FILES = (
    "README.md",
    "health.json",
    "latest-request.json",
    "hotkey-voice-overlay.json",
    "metadata.json",
)

SCREENSHOT_EVIDENCE_FILE = "screenshot.png"


@dataclass(frozen=True)
class ScreenTranslationCase:
    case_id: str
    display_name: str
    trigger_phrase: str
    expected_layout: str
    expected_language: str
    number_policy: str
    evidence_required: str


@dataclass(frozen=True)
class MatrixResult:
    display_name: str
    trigger_phrase: str
    expected_display: str
    result: str


@dataclass(frozen=True)
class CaseStatus:
    case: ScreenTranslationCase
    status: str
    expected_display: str
    matrix_result: str
    result_note: str
    note_issue: str


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cases", type=Path, default=DEFAULT_CASES)
    parser.add_argument("--matrix", type=Path, default=DEFAULT_MATRIX)
    parser.add_argument("--output", type=Path)
    parser.add_argument(
        "--strict",
        action="store_true",
        help="Exit nonzero when any case is missing, not run, blocked, or failed.",
    )
    args = parser.parse_args()

    try:
        cases = load_cases(args.cases)
        matrix = load_matrix_results(args.matrix)
        statuses = build_statuses(cases, matrix)
    except ValueError as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1

    markdown = render_markdown(statuses, args.cases, args.matrix)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(markdown, encoding="utf-8")
    else:
        print(markdown)

    counts = count_statuses(statuses)
    print(
        "OK screen translation eval: "
        + ", ".join(f"{key}={counts.get(key, 0)}" for key in sorted(STATUS_ORDER, key=STATUS_ORDER.get))
    )
    if args.strict and (
        any(status.status != "pass" for status in statuses) or
        any(status.note_issue != "-" for status in statuses)
    ):
        return 1
    return 0


def load_cases(path: Path) -> list[ScreenTranslationCase]:
    if not path.is_file():
        raise ValueError(f"cases file not found: {path}")
    with path.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        if reader.fieldnames != REQUIRED_COLUMNS:
            raise ValueError(f"unexpected TSV columns in {path}: {reader.fieldnames}")
        cases = [
            ScreenTranslationCase(
                case_id=row["id"],
                display_name=display_name_for(row["id"]),
                trigger_phrase=row["trigger_phrase"],
                expected_layout=row["expected_layout"],
                expected_language=row["expected_language"],
                number_policy=row["number_policy"],
                evidence_required=row["evidence_required"],
            )
            for row in reader
        ]
    if not cases:
        raise ValueError(f"no screen translation cases found: {path}")
    return cases


def display_name_for(case_id: str) -> str:
    explicit = {
        "ff6_dialogue": "FF6 dialogue",
        "ff6_main_menu": "FF6 main menu",
        "ff6_status": "FF6 status page",
        "chrono_equipment": "Chrono Trigger equipment",
        "multi_page_any": "Any multi-page result",
    }
    if case_id in explicit:
        return explicit[case_id]
    return case_id.replace("_", " ")


def load_matrix_results(path: Path) -> dict[str, MatrixResult]:
    if not path.is_file():
        raise ValueError(f"matrix file not found: {path}")
    lines = path.read_text(encoding="utf-8").splitlines()
    table_lines = extract_screen_translation_table(lines)
    results: dict[str, MatrixResult] = {}
    for line in table_lines:
        cells = parse_markdown_row(line)
        if len(cells) != 4:
            continue
        if cells[0] == "Game/screen" or set(cells[0]) == {"-"}:
            continue
        result = MatrixResult(
            display_name=cells[0],
            trigger_phrase=cells[1],
            expected_display=cells[2],
            result=cells[3],
        )
        results[result.display_name.lower()] = result
    return results


def extract_screen_translation_table(lines: list[str]) -> list[str]:
    in_section = False
    table: list[str] = []
    for line in lines:
        if line.startswith("## "):
            if in_section:
                break
            in_section = line.strip() == "## Screen Translation Matrix"
            continue
        if not in_section:
            continue
        if line.startswith("|"):
            table.append(line)
    return table


def parse_markdown_row(line: str) -> list[str]:
    return [
        cell.strip().replace("\\|", "|")
        for cell in line.strip().strip("|").split("|")
    ]


def build_statuses(
    cases: list[ScreenTranslationCase],
    matrix: dict[str, MatrixResult],
) -> list[CaseStatus]:
    statuses: list[CaseStatus] = []
    for case in cases:
        result = matrix.get(case.display_name.lower())
        if result is None:
            statuses.append(
                CaseStatus(
                    case=case,
                    status="missing",
                    expected_display="-",
                    matrix_result="-",
                    result_note="-",
                    note_issue="-",
                )
            )
            continue
        status = normalize_result(result.result)
        result_note = extract_result_note(result.result)
        statuses.append(
            CaseStatus(
                case=case,
                status=status,
                expected_display=result.expected_display,
                matrix_result=result.result,
                result_note=result_note,
                note_issue=validate_result_note(case, status, result_note),
            )
        )
    return mark_duplicate_evidence(statuses)


def mark_duplicate_evidence(statuses: list[CaseStatus]) -> list[CaseStatus]:
    refs_by_status_index: dict[int, tuple[str, ...]] = {}
    indexes_by_ref: dict[str, list[int]] = {}
    for index, item in enumerate(statuses):
        if item.status != "pass" or item.note_issue != "-":
            continue
        refs = tuple(normalize_evidence_reference(ref) for ref in evidence_references(item.result_note))
        refs_by_status_index[index] = refs
        for ref in refs:
            indexes_by_ref.setdefault(ref, []).append(index)

    duplicate_refs = {
        ref: indexes
        for ref, indexes in indexes_by_ref.items()
        if len(indexes) > 1
    }
    if not duplicate_refs:
        return statuses

    updated: list[CaseStatus] = []
    for index, item in enumerate(statuses):
        duplicate_ref = next(
            (
                ref
                for ref in refs_by_status_index.get(index, ())
                if ref in duplicate_refs
            ),
            None,
        )
        if duplicate_ref is None:
            updated.append(item)
            continue
        updated.append(
            CaseStatus(
                case=item.case,
                status=item.status,
                expected_display=item.expected_display,
                matrix_result=item.matrix_result,
                result_note=item.result_note,
                note_issue=f"duplicate_evidence_path:{duplicate_ref}",
            )
        )
    return updated


def normalize_evidence_reference(reference: str) -> str:
    path = Path(reference)
    if not path.is_absolute():
        path = ROOT / path
    return str(path.resolve())


def normalize_result(value: str) -> str:
    clean = value.strip().lower()
    head = status_head(clean)
    if head in {"pass", "passed"}:
        return "pass"
    if head in {"fail", "failed"}:
        return "fail"
    if head in {"blocked", "block"}:
        return "blocked"
    if head in {"not run", "not_run", "todo", "pending"}:
        return "not_run"
    return "missing" if not clean else "blocked"


def status_head(clean: str) -> str:
    for separator in [":", "-", ";", ","]:
        if separator in clean:
            return clean.split(separator, 1)[0].strip()
    return clean


def extract_result_note(value: str) -> str:
    clean = value.strip()
    for separator in [":", "-", ";", ","]:
        if separator in clean:
            note = clean.split(separator, 1)[1].strip()
            return note or "-"
    return "-"


def validate_result_note(
    case: ScreenTranslationCase,
    status: str,
    result_note: str,
) -> str:
    if status == "pass":
        if result_note == "-":
            return "missing_evidence_note"
        if case.evidence_required == "manual_screenshot" and not has_evidence_reference(result_note):
            return "missing_evidence_path"
        if has_unresolved_placeholder(result_note):
            return "placeholder_evidence_path"
        if case.evidence_required == "manual_screenshot":
            evidence_issue = validate_evidence_reference(case, result_note)
            if evidence_issue != "-":
                return evidence_issue
        checklist_issue = validate_checklist_note(case, result_note)
        if checklist_issue != "-":
            return checklist_issue
        return "-"
    if status in {"fail", "blocked"}:
        if result_note == "-":
            return "missing_failure_category"
        return "-"
    return "-"


def has_evidence_reference(note: str) -> bool:
    return bool(evidence_references(note))


def validate_checklist_note(case: ScreenTranslationCase, note: str) -> str:
    tokens = set(checklist_tokens(note))
    if not tokens:
        return "missing_checklist_note"
    missing = [token for token in required_checklist_tokens(case) if token not in tokens]
    if missing:
        return "checklist_missing:" + ",".join(missing)
    return "-"


def required_checklist_tokens(case: ScreenTranslationCase) -> tuple[str, ...]:
    required = ["layout_ok", "language_ok"]
    if case.expected_layout == "chinese_only":
        required.append("no_english_source")
    if case.expected_layout in {"bilingual_rows", "grouped_labels"}:
        required.append("grouping_ok")
    if case.expected_language == "en_zh":
        required.append("bilingual_ok")
    if "preserve" in case.number_policy:
        required.append("numbers_ok")
    if case.expected_layout == "paged_overlay" or case.number_policy == "ten_seconds_per_page":
        required.append("paging_10s")
    return tuple(required)


def checklist_tokens(note: str) -> tuple[str, ...]:
    tokens: list[str] = []
    for raw in note.split():
        token = raw.strip().strip("`'\".;")
        if not token.startswith("checklist="):
            continue
        values = token.split("=", 1)[1]
        tokens.extend(
            item.strip()
            for item in values.split(",")
            if item.strip()
        )
    return tuple(tokens)


def validate_evidence_reference(case: ScreenTranslationCase, note: str) -> str:
    last_issue = "missing_evidence_path"
    for reference in evidence_references(note):
        path = Path(reference)
        if not path.is_absolute():
            path = ROOT / path
        if not path.exists():
            last_issue = "evidence_path_not_found"
            continue
        if not path.is_dir():
            last_issue = "evidence_path_not_directory"
            continue
        if not is_relative_to(path.resolve(), EVIDENCE_ROOT.resolve()):
            last_issue = "evidence_path_outside_rc_device_root"
            continue
        missing = [name for name in REQUIRED_EVIDENCE_FILES if not (path / name).is_file()]
        if missing:
            last_issue = "evidence_files_missing:" + ",".join(missing)
            continue
        if case.evidence_required == "manual_screenshot" and not (path / SCREENSHOT_EVIDENCE_FILE).is_file():
            last_issue = f"evidence_screenshot_missing:{SCREENSHOT_EVIDENCE_FILE}"
            continue
        metadata_issue = validate_evidence_metadata(path, case)
        if metadata_issue != "-":
            last_issue = metadata_issue
            continue
        return "-"
    return last_issue


def validate_evidence_metadata(path: Path, case: ScreenTranslationCase) -> str:
    metadata_path = path / "metadata.json"
    try:
        parsed = json.loads(metadata_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return "evidence_metadata_invalid_json"
    if not isinstance(parsed, dict):
        return "evidence_metadata_not_object"
    gate = str(parsed.get("gate", "")).strip()
    if gate != "screen_translation":
        return f"evidence_metadata_gate_mismatch:{gate or 'missing'}"
    case_id = str(parsed.get("case_id", "")).strip()
    if case_id != case.case_id:
        return f"evidence_metadata_case_mismatch:{case_id or 'missing'}"
    return "-"


def is_relative_to(path: Path, parent: Path) -> bool:
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False


def evidence_references(note: str) -> tuple[str, ...]:
    clean = note.strip()
    if not clean or clean == "-":
        return ()
    references: list[str] = []
    for raw in clean.split():
        token = raw.strip().strip("`'\".,;")
        if token.startswith("evidence="):
            token = token.split("=", 1)[1].strip()
        if (
            token.startswith("build/") or
            token.startswith("docs/qa-feedback/") or
            token.startswith("/")
        ):
            references.append(token)
    return tuple(references)


def has_unresolved_placeholder(note: str) -> bool:
    clean = note.strip().lower()
    if not clean:
        return False
    return bool(re.search(r"<[^>\s]+>", clean)) or any(
        marker in clean
        for marker in [
            "todo",
            "tbd",
            "placeholder",
            "replace-me",
        ]
    )


def render_markdown(
    statuses: list[CaseStatus],
    cases_path: Path,
    matrix_path: Path,
) -> str:
    counts = count_statuses(statuses)
    note_issues = count_note_issues(statuses)
    lines = [
        "# M18 Screen Translation Eval Report",
        "",
        f"- Cases: `{cases_path}`",
        f"- Matrix: `{matrix_path}`",
        f"- Total: {len(statuses)}",
        "- Status: "
        + ", ".join(f"{key}={counts.get(key, 0)}" for key in sorted(STATUS_ORDER, key=STATUS_ORDER.get)),
        f"- Note issues: {note_issues}",
        "",
        "| Case | Status | Trigger | Expected Layout | Language | Number Policy | Evidence | Matrix Expected Display | Matrix Result | Result Note | Note Check |",
        "|---|---|---|---|---|---|---|---|---|---|---|",
    ]
    for item in statuses:
        case = item.case
        lines.append(
            f"| `{escape_cell(case.case_id)}` | `{item.status}` | "
            f"{escape_cell(case.trigger_phrase)} | `{escape_cell(case.expected_layout)}` | "
            f"`{escape_cell(case.expected_language)}` | `{escape_cell(case.number_policy)}` | "
            f"`{escape_cell(case.evidence_required)}` | {escape_cell(item.expected_display)} | "
            f"{escape_cell(item.matrix_result)} | {escape_cell(item.result_note)} | "
            f"{escape_cell(item.note_issue)} |"
        )
    return "\n".join(lines) + "\n"


def count_statuses(statuses: list[CaseStatus]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for status in statuses:
        counts[status.status] = counts.get(status.status, 0) + 1
    return counts


def count_note_issues(statuses: list[CaseStatus]) -> int:
    return sum(1 for status in statuses if status.note_issue != "-")


def escape_cell(value: str) -> str:
    return value.replace("|", "\\|").replace("\n", " ")


if __name__ == "__main__":
    raise SystemExit(main())
