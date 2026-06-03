#!/usr/bin/env python3
"""Report M18 hotkey voice QA status from a real-device results TSV."""

from __future__ import annotations

import argparse
import csv
import sys
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CASES = ROOT / "scripts/hotkey_voice_qa_cases.tsv"
DEFAULT_RESULTS_ROOT = ROOT / "build/hotkey-voice-qa"
DEFAULT_OUTPUT = ROOT / "docs/qa-feedback/hotkey-voice-matrix-report.md"

DEFAULT_MATRIX_CASES = (
    "sf2_vigor_ball_observed",
    "golden_sun_ivan_observed",
    "chrono_marle_observed",
    "chrono_atb_observed",
    "ff6_magicite_observed",
    "langrisser_commander_smoke",
    "phantasy_star_tech_skill_smoke",
)

CASE_COLUMNS = [
    "case_name",
    "pack_id",
    "category",
    "label",
    "spoken_prompt",
    "expected_question_source",
    "expected_stage",
    "expected_answer_type",
    "expected_llm_status",
    "expected_source",
    "expected_matched_term",
    "expected_entity_id",
    "notes",
]

STATUS_ORDER = {
    "pass": 0,
    "fail": 1,
    "blocked": 2,
    "not_run": 3,
    "missing": 4,
}


@dataclass(frozen=True)
class VoiceCase:
    case_name: str
    pack_id: str
    category: str
    label: str
    spoken_prompt: str
    expected_question_source: str
    expected_stage: str
    expected_answer_type: str
    expected_llm_status: str
    expected_source: str
    expected_matched_term: str
    expected_entity_id: str
    notes: str


@dataclass(frozen=True)
class VoiceStatus:
    case: VoiceCase
    status: str
    result: str
    failure_category: str
    evidence: str
    transcript: str
    normalized_transcript: str
    pipeline_stage: str
    answer_type: str
    llm_status: str
    source_ids: str
    finish_reason: str
    asr_commit_reason: str
    audio_read_count: str
    peak_amplitude: str
    note: str
    contract_issues: tuple[str, ...]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cases", type=Path, default=DEFAULT_CASES)
    parser.add_argument("--results", type=Path)
    parser.add_argument("--results-root", type=Path, default=DEFAULT_RESULTS_ROOT)
    parser.add_argument(
        "--case-filter",
        default=",".join(DEFAULT_MATRIX_CASES),
        help="Comma-separated case ids to report, in display order.",
    )
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument(
        "--strict",
        action="store_true",
        help="Exit nonzero unless every selected voice row is passing.",
    )
    args = parser.parse_args()

    try:
        case_ids = parse_case_filter(args.case_filter)
        cases = load_cases(args.cases, case_ids)
        results_path = args.results or best_results_path(args.results_root, case_ids)
        results = load_results(results_path) if results_path else {}
        statuses = build_statuses(cases, results, results_path)
    except ValueError as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1

    markdown = render_markdown(statuses, args.cases, results_path)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(markdown, encoding="utf-8")

    counts = count_statuses(statuses)
    print(
        "OK hotkey voice matrix: "
        + ", ".join(f"{key}={counts.get(key, 0)}" for key in sorted(STATUS_ORDER, key=STATUS_ORDER.get))
    )
    if args.strict and any(status.status != "pass" for status in statuses):
        return 1
    return 0


def parse_case_filter(value: str) -> tuple[str, ...]:
    case_ids = tuple(part.strip() for part in value.split(",") if part.strip())
    if not case_ids:
        raise ValueError("case filter selected no cases")
    return case_ids


def load_cases(path: Path, selected_case_ids: tuple[str, ...]) -> list[VoiceCase]:
    if not path.is_file():
        raise ValueError(f"case file not found: {path}")
    with path.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(
            (line for line in handle if line.strip() and not line.startswith("#")),
            delimiter="\t",
        )
        if reader.fieldnames != CASE_COLUMNS:
            raise ValueError(f"unexpected TSV columns in {path}: {reader.fieldnames}")
        rows = {
            row["case_name"]: VoiceCase(**{column: row.get(column, "") for column in CASE_COLUMNS})
            for row in reader
        }
    missing = [case_id for case_id in selected_case_ids if case_id not in rows]
    if missing:
        raise ValueError(f"case file missing selected rows: {', '.join(missing)}")
    return [rows[case_id] for case_id in selected_case_ids]


def latest_results_path(results_root: Path) -> Path | None:
    if not results_root.is_dir():
        return None
    candidates = sorted(
        path for path in results_root.glob("*/results.tsv") if path.is_file()
    )
    return candidates[-1] if candidates else None


def best_results_path(results_root: Path, selected_case_ids: tuple[str, ...]) -> Path | None:
    if not results_root.is_dir():
        return None
    candidates = sorted(path for path in results_root.glob("*/results.tsv") if path.is_file())
    if not candidates:
        return None
    selected = set(selected_case_ids)
    ranked = [
        (results_case_coverage(path, selected), display_path(path.parent), path)
        for path in candidates
    ]
    ranked.sort(key=lambda item: (item[0], item[1]))
    return ranked[-1][2]


def results_case_coverage(path: Path, selected_case_ids: set[str]) -> int:
    try:
        results = load_results(path)
    except ValueError:
        return 0
    return sum(1 for case_id in selected_case_ids if case_id in results)


def load_results(path: Path) -> dict[str, dict[str, str]]:
    if not path.is_file():
        raise ValueError(f"results file not found: {path}")
    with path.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        if not reader.fieldnames or "case_name" not in reader.fieldnames:
            raise ValueError(f"results file missing case_name column: {path}")
        results: dict[str, dict[str, str]] = {}
        for row in reader:
            case_name = row.get("case_name", "")
            if case_name:
                results[case_name] = {key: value or "" for key, value in row.items()}
    return results


def build_statuses(
    cases: list[VoiceCase],
    results: dict[str, dict[str, str]],
    results_path: Path | None,
) -> list[VoiceStatus]:
    statuses: list[VoiceStatus] = []
    evidence = display_path(results_path.parent) if results_path else "-"
    for case in cases:
        row = results.get(case.case_name)
        if row is None:
            statuses.append(empty_status(case, "not_run", evidence))
            continue
        contract_issues = validate_contract(case, row)
        result = row_value(row, "result").upper()
        if result == "PASS" and not contract_issues:
            status = "pass"
        elif result == "":
            status = "blocked"
        else:
            status = "fail"
        failure_category = "-" if status == "pass" else classify_failure(case, row, contract_issues)
        statuses.append(
            VoiceStatus(
                case=case,
                status=status,
                result=result or "-",
                failure_category=failure_category,
                evidence=evidence,
                transcript=first_value(row, "overlay_transcript", "raw_question", "asr_selected_transcript"),
                normalized_transcript=first_value(row, "overlay_normalized_transcript", "normalized_question"),
                pipeline_stage=row_value(row, "pipeline_stage"),
                answer_type=row_value(row, "answer_type"),
                llm_status=row_value(row, "llm_status"),
                source_ids=row_value(row, "source_ids"),
                finish_reason=row_value(row, "finish_reason"),
                asr_commit_reason=row_value(row, "asr_commit_reason"),
                audio_read_count=row_value(row, "asr_audio_read_count"),
                peak_amplitude=row_value(row, "asr_peak_amplitude"),
                note=row_value(row, "notes"),
                contract_issues=tuple(contract_issues),
            )
        )
    return statuses


def empty_status(case: VoiceCase, status: str, evidence: str) -> VoiceStatus:
    return VoiceStatus(
        case=case,
        status=status,
        result="-",
        failure_category="-",
        evidence=evidence,
        transcript="-",
        normalized_transcript="-",
        pipeline_stage="-",
        answer_type="-",
        llm_status="-",
        source_ids="-",
        finish_reason="-",
        asr_commit_reason="-",
        audio_read_count="-",
        peak_amplitude="-",
        note="-",
        contract_issues=(),
    )


def validate_contract(case: VoiceCase, row: dict[str, str]) -> list[str]:
    issues: list[str] = []
    compare_field(issues, "label", case.label, row_value(row, "label"))
    compare_field(issues, "pipeline_stage", case.expected_stage, row_value(row, "pipeline_stage"))
    compare_field(issues, "answer_type", case.expected_answer_type, row_value(row, "answer_type"))
    compare_field(issues, "llm_status", case.expected_llm_status, row_value(row, "llm_status"))
    if case.expected_source and case.expected_source not in split_sources(row_value(row, "source_ids")):
        issues.append(f"source_ids missing {case.expected_source}")
    if case.expected_matched_term:
        actual_terms = {
            row_value(row, "matched_term"),
            row_value(row, "overlay_matched_term"),
            row_value(row, "normalized_question_matched_term"),
        }
        if case.expected_matched_term not in actual_terms:
            issues.append(f"matched_term expected {case.expected_matched_term}")
    if case.expected_entity_id:
        actual_entities = {
            row_value(row, "matched_entity_id"),
            row_value(row, "normalized_question_matched_entity_id"),
        }
        if case.expected_entity_id not in actual_entities:
            issues.append(f"matched_entity_id expected {case.expected_entity_id}")
    return issues


def compare_field(issues: list[str], name: str, expected: str, actual: str) -> None:
    if expected and actual != expected:
        issues.append(f"{name} expected {expected} actual {actual or '-'}")


def classify_failure(
    case: VoiceCase,
    row: dict[str, str],
    contract_issues: list[str],
) -> str:
    note = row_value(row, "notes").lower()
    finish_reason = row_value(row, "finish_reason")
    commit_reason = row_value(row, "asr_commit_reason")
    endpoint_armed = row_value(row, "asr_endpoint_armed")
    pipeline_stage = row_value(row, "pipeline_stage")
    transcript = first_value(row, "overlay_transcript", "raw_question", "asr_selected_transcript")
    if (
        "latest request timestamp unchanged" in note
        or "no request submitted" in note
        or finish_reason == "muted_recovery"
        or commit_reason == "blank_partial"
        or (endpoint_armed == "false" and not pipeline_stage)
    ):
        return "voice_lifecycle_gap"
    if case.expected_stage == "evidence" and pipeline_stage == "no_evidence" and transcript:
        return "asr_variant"
    if any(issue.startswith("source_ids missing") for issue in contract_issues):
        return "source_mismatch"
    if any(issue.startswith("label expected") for issue in contract_issues):
        return "label_mismatch"
    if contract_issues:
        return "contract_mismatch"
    return "qa_result_fail"


def render_markdown(
    statuses: list[VoiceStatus],
    cases_path: Path,
    results_path: Path | None,
) -> str:
    counts = count_statuses(statuses)
    categories = count_failure_categories(statuses)
    lines = [
        "# M18 Hotkey Voice Matrix Report",
        "",
        f"- Cases: `{display_path(cases_path)}`",
        f"- Results: `{display_path(results_path) if results_path else 'none'}`",
        f"- Evidence root: `{display_path(results_path.parent) if results_path else '-'}`",
        f"- Total: {len(statuses)}",
        "- Status: "
        + ", ".join(f"{key}={counts.get(key, 0)}" for key in sorted(STATUS_ORDER, key=STATUS_ORDER.get)),
        "- Failure categories: "
        + (", ".join(f"{key}={value}" for key, value in sorted(categories.items())) if categories else "none"),
        f"- Strict pass: `{'yes' if statuses and all(item.status == 'pass' for item in statuses) else 'no'}`",
        "",
        "| Case | Status | Pack | Label | Prompt | Transcript | Stage | Answer | LLM | Sources | Finish | ASR Commit | Audio Reads | Peak | Failure Category | Contract Check | Evidence |",
        "|---|---|---|---|---|---|---|---|---|---|---|---|---:|---:|---|---|---|",
    ]
    for item in statuses:
        lines.append(
            f"| `{escape_cell(item.case.case_name)}` | `{item.status}` | "
            f"`{escape_cell(item.case.pack_id)}` | `{escape_cell(item.case.label)}` | "
            f"{escape_cell(item.case.spoken_prompt)} | {escape_cell(item.transcript or '-')} | "
            f"`{escape_cell(item.pipeline_stage or '-')}` | `{escape_cell(item.answer_type or '-')}` | "
            f"`{escape_cell(item.llm_status or '-')}` | {escape_cell(item.source_ids or '-')} | "
            f"`{escape_cell(item.finish_reason or '-')}` | `{escape_cell(item.asr_commit_reason or '-')}` | "
            f"{escape_cell(item.audio_read_count or '-')} | {escape_cell(item.peak_amplitude or '-')} | "
            f"`{escape_cell(item.failure_category)}` | {escape_cell(contract_summary(item.contract_issues))} | "
            f"`{escape_cell(item.evidence)}` |"
        )
    return "\n".join(lines) + "\n"


def count_statuses(statuses: list[VoiceStatus]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for status in statuses:
        counts[status.status] = counts.get(status.status, 0) + 1
    return counts


def count_failure_categories(statuses: list[VoiceStatus]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for status in statuses:
        if status.failure_category == "-":
            continue
        counts[status.failure_category] = counts.get(status.failure_category, 0) + 1
    return counts


def contract_summary(issues: tuple[str, ...]) -> str:
    return "-" if not issues else "; ".join(issues)


def row_value(row: dict[str, str], key: str) -> str:
    return (row.get(key) or "").strip()


def first_value(row: dict[str, str], *keys: str) -> str:
    for key in keys:
        value = row_value(row, key)
        if value:
            return value
    return "-"


def split_sources(value: str) -> set[str]:
    return {part.strip() for part in value.split(",") if part.strip()}


def display_path(path: Path | None) -> str:
    if path is None:
        return "none"
    resolved = path.resolve()
    try:
        return resolved.relative_to(ROOT).as_posix()
    except ValueError:
        return str(path)


def escape_cell(value: str) -> str:
    return value.replace("|", "\\|").replace("\n", " ")


if __name__ == "__main__":
    raise SystemExit(main())
