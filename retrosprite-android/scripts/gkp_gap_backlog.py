#!/usr/bin/env python3
"""Convert debug/evidence request JSON into an M18 GKP quality backlog."""

from __future__ import annotations

import argparse
import csv
import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_GKP_DIR = ROOT / "app/src/main/assets/gkp"
DEFAULT_SCREEN_CASES = ROOT / "scripts/screen_translation_eval_cases.tsv"
MANUAL_NOTES_TEMPLATE_COLUMNS = (
    "game_label",
    "question",
    "raw_question",
    "normalized_question",
    "issue_type",
    "feedback",
    "output_mode",
    "pipeline_stage",
    "answer_type",
    "source_ids",
    "expected",
    "actual",
    "evidence",
    "notes",
)
MANUAL_NOTES_TEMPLATE_ROWS = (
    {
        "game_label": "super_nintendo__Final Fantasy VI (USA)",
        "question": "翻译",
        "issue_type": "example_translation_gap",
        "feedback": "example",
        "output_mode": "screen_translation",
        "expected": "Replace with the expected readable Chinese or bilingual menu behavior.",
        "actual": "Replace with what the tester saw.",
        "evidence": "build/rc-device-evidence/<timestamp>",
        "notes": "Replace this example row before importing real notes.",
    },
    {
        "game_label": "md__Shining Force II",
        "question": "气合之玉怎么用？",
        "issue_type": "example_ranking_gap",
        "feedback": "example",
        "pipeline_stage": "evidence",
        "source_ids": "sf2.characters",
        "expected": "sf2.promotion",
        "actual": "Answer used an unrelated source.",
        "notes": "Replace this example row before importing real notes.",
    },
    {
        "game_label": "gba__黄金太阳",
        "question": "这个地方要去哪",
        "issue_type": "example_coverage_gap",
        "feedback": "example",
        "expected": "A grounded low-spoiler next step.",
        "actual": "No useful answer yet.",
        "notes": "Replace this example row before importing real notes.",
    },
)


@dataclass(frozen=True)
class BacklogItem:
    label: str
    question: str
    tags: list[str]
    suggested_area: str
    regression_target: str
    details: str
    source: str


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--gkp-dir", type=Path, default=DEFAULT_GKP_DIR)
    parser.add_argument(
        "--merge-existing-backlog",
        type=Path,
        help="Merge new items with an existing backlog markdown instead of replacing it.",
    )
    parser.add_argument(
        "--manual-notes-template-output",
        type=Path,
        help="Write a TSV template for manual tester notes without importing it.",
    )
    args = parser.parse_args()

    if args.manual_notes_template_output:
        write_manual_notes_template(args.manual_notes_template_output)
        print(f"OK manual notes template: {args.manual_notes_template_output}")

    if args.input is None:
        if args.manual_notes_template_output:
            return 0
        print("FAIL --input is required unless --manual-notes-template-output is used", file=sys.stderr)
        return 1

    try:
        alias_index = load_alias_index(args.gkp_dir)
        records = load_records(args.input)
        items = build_backlog(records, alias_index)
        if args.merge_existing_backlog:
            existing_items = load_backlog_markdown(args.merge_existing_backlog)
            items = merge_backlog_items(existing_items, items)
    except ValueError as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1

    input_label = str(args.input)
    if args.merge_existing_backlog:
        input_label = f"{args.merge_existing_backlog} + {args.input}"
    markdown = render_markdown(items, input_label)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(markdown, encoding="utf-8")
    else:
        print(markdown)

    print(f"OK GKP gap backlog: {len(items)} items")
    return 0


def write_manual_notes_template(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=list(MANUAL_NOTES_TEMPLATE_COLUMNS),
            delimiter="\t",
            extrasaction="ignore",
        )
        writer.writeheader()
        for row in MANUAL_NOTES_TEMPLATE_ROWS:
            writer.writerow(row)


def load_records(input_path: Path) -> list[dict[str, Any]]:
    if not input_path.exists():
        raise ValueError(f"input path not found: {input_path}")

    files: list[Path]
    if input_path.is_dir():
        files = sorted(
            path
            for path in input_path.rglob("*")
            if path.is_file()
            and (
                path.name == "latest-request.json"
                or path.name == "m18-manual-gate-receipt.json"
                or path.name == "results.tsv"
                or is_manual_notes_tsv(path)
                or path.suffix.lower() == ".jsonl"
            )
        )
    else:
        files = [input_path]

    records: list[dict[str, Any]] = []
    for path in files:
        for record in parse_json_records(path):
            if "has_entry" in record and record.get("has_entry") is False:
                continue
            record["_source_file"] = str(path)
            validate_record(record, path)
            records.append(record)
    return records


def parse_json_records(path: Path) -> list[dict[str, Any]]:
    if path.name == "results.tsv":
        return parse_results_tsv(path)
    if path.suffix.lower() == ".tsv":
        return parse_manual_notes_tsv(path)

    text = path.read_text(encoding="utf-8", errors="ignore").strip()
    if not text:
        return []

    records: list[dict[str, Any]] = []
    if path.suffix.lower() == ".jsonl":
        for index, line in enumerate(text.splitlines(), start=1):
            if not line.strip():
                continue
            try:
                parsed = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"invalid JSONL {path}:{index}: {exc}") from exc
            records.extend(records_from_parsed(parsed, path))
        return records

    decoder = json.JSONDecoder()
    for start, char in enumerate(text):
        if char not in "[{":
            continue
        try:
            parsed, _ = decoder.raw_decode(text[start:])
        except json.JSONDecodeError:
            continue
        records.extend(records_from_parsed(parsed, path))
        if records:
            return records
    return []


def records_from_parsed(parsed: Any, path: Path) -> list[dict[str, Any]]:
    receipt_records = parse_manual_gate_receipt_records(parsed, path)
    if receipt_records is not None:
        return receipt_records
    return as_records(parsed)


def parse_manual_gate_receipt_records(parsed: Any, path: Path) -> list[dict[str, Any]] | None:
    if not isinstance(parsed, dict) or "screen_translation_results" not in parsed:
        return None
    screen_cases = load_screen_case_index(DEFAULT_SCREEN_CASES)
    records: list[dict[str, Any]] = []
    for entry in parsed.get("screen_translation_results") or []:
        if not isinstance(entry, dict):
            continue
        case_id = str(entry.get("case_id") or "").strip()
        result = str(entry.get("result") or "").strip()
        status = result_status(result)
        if status != "fail":
            continue
        case = screen_cases.get(case_id, {})
        note = result_note(result)
        notes = str(entry.get("notes") or "").strip()
        actual_parts = [part for part in (note, notes) if part]
        records.append(
            {
                "label": case.get("game_label") or case_id or "screen_translation",
                "question": case.get("trigger_phrase") or "翻译",
                "issue_type": f"screen_translation:{case_id or 'unknown'}:{note or 'fail'}",
                "feedback": "wrong",
                "output_mode": "screen_translation",
                "expected": format_screen_case_expected(case),
                "actual": "; ".join(actual_parts) if actual_parts else "screen translation failed",
                "evidence": first_evidence_reference(result),
                "notes": notes,
                "receipt_case_id": case_id,
            }
        )
    return records


def result_status(result: str) -> str:
    lowered = result.strip().lower()
    if lowered.startswith("pass"):
        return "pass"
    if lowered.startswith("fail"):
        return "fail"
    if lowered.startswith("blocked"):
        return "blocked"
    return ""


def result_note(result: str) -> str:
    if ":" not in result:
        return ""
    return result.split(":", 1)[1].strip()


def first_evidence_reference(result: str) -> str:
    for token in result.split():
        if token.startswith("build/rc-device-evidence/"):
            return token.rstrip(".,;")
    return ""


def load_screen_case_index(path: Path) -> dict[str, dict[str, str]]:
    if not path.is_file():
        return {}
    rows: dict[str, dict[str, str]] = {}
    with path.open("r", encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle, delimiter="\t"):
            case_id = str(row.get("id") or "").strip()
            if case_id:
                rows[case_id] = {key: str(value or "") for key, value in row.items()}
    return rows


def format_screen_case_expected(case: dict[str, str]) -> str:
    if not case:
        return "screen translation case policy"
    return (
        f"layout={case.get('expected_layout', '')}; "
        f"language={case.get('expected_language', '')}; "
        f"numbers={case.get('number_policy', '')}"
    )


def is_manual_notes_tsv(path: Path) -> bool:
    if path.suffix.lower() != ".tsv" or path.name == "results.tsv":
        return False
    lowered = path.name.lower()
    return "note" in lowered or "manual" in lowered or "tester" in lowered or "qa" in lowered


def parse_results_tsv(path: Path) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    with path.open(encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle, delimiter="\t"):
            if not row:
                continue
            records.append(
                {
                    "label": row.get("label", ""),
                    "question": row.get("spoken_prompt", ""),
                    "overlay_transcript": row.get("overlay_transcript", ""),
                    "overlay_normalized_transcript": row.get("overlay_normalized_transcript", ""),
                    "overlay_matched_term": row.get("overlay_matched_term", ""),
                    "raw_question": row.get("raw_question", ""),
                    "normalized_question": row.get("normalized_question", ""),
                    "matched_term": row.get("matched_term", ""),
                    "matched_entity_id": row.get("matched_entity_id", ""),
                    "pipeline_stage": row.get("pipeline_stage", ""),
                    "llm_status": row.get("llm_status", ""),
                    "source_ids": split_csv_cell(row.get("source_ids", "")),
                    "answer_type": row.get("answer_type", ""),
                    "output_mode": "hotkey_voice_qa",
                    "feedback": "wrong" if row.get("result") == "FAIL" else "",
                    "result": row.get("result", ""),
                    "case_name": row.get("case_name", ""),
                    "pack_id": row.get("pack_id", ""),
                    "finish_reason": row.get("finish_reason", ""),
                    "asr_commit_reason": row.get("asr_commit_reason", ""),
                    "asr_endpoint_armed": row.get("asr_endpoint_armed", ""),
                    "asr_sample_count": row.get("asr_sample_count", ""),
                    "asr_audio_read_count": row.get("asr_audio_read_count", ""),
                    "asr_audio_read_error_count": row.get("asr_audio_read_error_count", ""),
                    "asr_peak_amplitude": row.get("asr_peak_amplitude", ""),
                    "asr_last_frame_amplitude": row.get("asr_last_frame_amplitude", ""),
                    "notes": row.get("notes", ""),
                }
            )
    return records


def parse_manual_notes_tsv(path: Path) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    with path.open(encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle, delimiter="\t"):
            if not row:
                continue
            records.append(
                {
                    "label": first_present(row, "label", "game_label", "retroarch_label"),
                    "question": first_present(row, "question", "prompt", "spoken_prompt", "trigger_phrase"),
                    "raw_question": first_present(row, "raw_question", "asr_transcript", "transcript"),
                    "normalized_question": first_present(row, "normalized_question", "normalized_transcript"),
                    "pipeline_stage": first_present(row, "pipeline_stage", "stage"),
                    "llm_status": first_present(row, "llm_status"),
                    "source_ids": split_csv_cell(first_present(row, "source_ids")),
                    "answer_type": first_present(row, "answer_type", "type"),
                    "output_mode": first_present(row, "output_mode", "mode", "surface"),
                    "feedback": first_present(row, "feedback", "result", "verdict"),
                    "issue_type": first_present(row, "issue_type", "issue", "tag", "category"),
                    "expected": first_present(row, "expected", "expected_answer", "expected_result"),
                    "actual": first_present(row, "actual", "actual_answer", "actual_result"),
                    "notes": first_present(row, "notes", "note", "tester_notes", "failure_notes"),
                    "evidence": first_present(row, "evidence", "evidence_path", "source_file"),
                }
            )
    return records


def first_present(row: dict[str, str | None], *keys: str) -> str:
    for key in keys:
        value = row.get(key)
        if value is not None and str(value).strip():
            return str(value).strip()
    return ""


def split_csv_cell(value: str) -> list[str]:
    return [
        item.strip()
        for item in value.split(",")
        if item.strip()
    ]


def as_records(parsed: Any) -> list[dict[str, Any]]:
    if isinstance(parsed, list):
        return [item for item in parsed if isinstance(item, dict)]
    if isinstance(parsed, dict):
        return [parsed]
    return []


def validate_record(record: dict[str, Any], path: Path) -> None:
    if not record.get("label"):
        raise ValueError(f"record missing label: {path}")
    if not record.get("question"):
        raise ValueError(f"record missing question: {path}")


def build_backlog(records: list[dict[str, Any]], alias_index: dict[str, set[str]]) -> list[BacklogItem]:
    items: list[BacklogItem] = []
    seen: set[tuple[str, str, tuple[str, ...], str]] = set()
    for record in records:
        if str(record.get("result") or "").upper() == "PASS":
            continue
        tags = classify_record(record, alias_index)
        if tags == ["asr_variant"] and record.get("ok", True) and record.get("pipeline_stage") == "evidence":
            continue
        if not tags:
            continue
        label = str(record.get("label") or "")
        question = str(record.get("question") or "")
        item = BacklogItem(
            label=label,
            question=question,
            tags=tags,
            suggested_area=suggest_area(tags),
            regression_target=suggest_regression(tags, label, question),
            details=summarize_details(record),
            source=str(record.get("_source_file") or "manual"),
        )
        key = backlog_item_key(item)
        if key in seen:
            continue
        seen.add(key)
        items.append(item)
    return items


def load_backlog_markdown(path: Path) -> list[BacklogItem]:
    if not path.is_file():
        return []
    items: list[BacklogItem] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.startswith("| `"):
            continue
        cells = split_markdown_row(line)
        if len(cells) != 7:
            continue
        label, question, tags, suggested_area, regression_target, details, source = cells
        items.append(
            BacklogItem(
                label=strip_backticks(unescape_cell(label)),
                question=unescape_cell(question),
                tags=parse_tags_cell(tags),
                suggested_area=unescape_cell(suggested_area),
                regression_target=unescape_cell(regression_target),
                details=unescape_cell(details),
                source=strip_backticks(unescape_cell(source)),
            )
        )
    return items


def merge_backlog_items(existing: list[BacklogItem], new_items: list[BacklogItem]) -> list[BacklogItem]:
    merged: list[BacklogItem] = []
    seen: set[tuple[str, str, tuple[str, ...], str]] = set()
    for item in [*existing, *new_items]:
        key = backlog_item_key(item)
        if key in seen:
            continue
        seen.add(key)
        merged.append(item)
    return merged


def backlog_item_key(item: BacklogItem) -> tuple[str, str, tuple[str, ...], str]:
    return (
        item.label,
        item.question,
        tuple(item.tags),
        asr_transcript_signature(item) if "asr_variant" in item.tags else "",
    )


def asr_transcript_signature(item: BacklogItem) -> str:
    for part in item.details.split(";"):
        key, _, value = part.strip().partition("=")
        if key == "asr_transcript":
            return value.strip()
    return ""


def split_markdown_row(line: str) -> list[str]:
    stripped = line.strip()
    if stripped.startswith("|"):
        stripped = stripped[1:]
    if stripped.endswith("|"):
        stripped = stripped[:-1]
    cells: list[str] = []
    current: list[str] = []
    escaped = False
    for char in stripped:
        if escaped:
            current.append("\\" + char if char != "|" else "|")
            escaped = False
            continue
        if char == "\\":
            escaped = True
            continue
        if char == "|":
            cells.append("".join(current).strip())
            current = []
        else:
            current.append(char)
    if escaped:
        current.append("\\")
    cells.append("".join(current).strip())
    return cells


def parse_tags_cell(value: str) -> list[str]:
    text = strip_backticks(unescape_cell(value))
    return [tag.strip() for tag in text.split(",") if tag.strip()]


def strip_backticks(value: str) -> str:
    if value.startswith("`") and value.endswith("`"):
        return value[1:-1]
    return value


def unescape_cell(value: str) -> str:
    return value.replace("\\|", "|")


def classify_record(record: dict[str, Any], alias_index: dict[str, set[str]]) -> list[str]:
    tags: list[str] = []
    raw_question = str(record.get("raw_question") or "")
    normalized_question = str(record.get("normalized_question") or "")
    question = str(record.get("question") or "")
    stage = str(record.get("pipeline_stage") or "")
    answer_type = str(record.get("answer_type") or "")
    output_mode = str(record.get("output_mode") or "")
    feedback = str(record.get("feedback") or record.get("feedback_value") or "").lower()
    source_ids = record.get("source_ids") or []
    normalization_reason = str(record.get("question_normalization_reason") or "")
    result = str(record.get("result") or "")
    finish_reason = str(record.get("finish_reason") or "")
    asr_commit_reason = str(record.get("asr_commit_reason") or "")
    notes = str(record.get("notes") or "")
    issue_type = str(record.get("issue_type") or "").lower()
    overlay_transcript = str(record.get("overlay_transcript") or "")
    overlay_normalized_transcript = str(record.get("overlay_normalized_transcript") or "")

    is_no_evidence = stage == "no_evidence" or answer_type == "no_evidence"
    has_sources = bool(source_ids)

    if raw_question and normalized_question and raw_question != normalized_question:
        tags.append("asr_variant")
    elif "observed_asr" in normalization_reason or "asr" in normalization_reason:
        tags.append("asr_variant")
    elif (
        output_mode == "hotkey_voice_qa"
        and result == "FAIL"
        and is_no_evidence
        and overlay_transcript
        and overlay_transcript != question
    ):
        tags.append("asr_variant")
    elif (
        output_mode == "hotkey_voice_qa"
        and result == "FAIL"
        and is_no_evidence
        and overlay_normalized_transcript
        and overlay_normalized_transcript != overlay_transcript
    ):
        tags.append("asr_variant")
    elif output_mode == "hotkey_voice_qa" and result == "FAIL" and "Observed ASR" in notes:
        tags.append("asr_variant")

    if is_no_evidence and known_term_in_question(record, alias_index):
        tags.append("alias_gap")

    if is_no_evidence and not has_sources:
        tags.append("coverage_gap")

    if (
        output_mode == "hotkey_voice_qa"
        and result == "FAIL"
        and not has_sources
        and not is_no_evidence
        and (stage == "unknown" or answer_type == "route_hint")
    ):
        tags.append("coverage_gap")
        if "直接告诉" in question or "Lite boundary" in notes or "boundary" in notes:
            tags.append("spoiler_gate_gap")

    if has_sources and feedback in {"wrong", "bad", "negative", "this_is_wrong", "incorrect"}:
        tags.append("ranking_gap")

    if has_sources and ("spoiler" in stage or "spoiler" in answer_type or "剧透" in str(record)):
        tags.append("spoiler_gate_gap")

    if (
        "translation" in output_mode
        or "screen_translation" in answer_type
        or "translation" in issue_type
        or "screen" in issue_type
        or "翻译" in question
    ):
        if is_no_evidence or feedback in {"wrong", "bad", "negative", "incorrect"} or not record.get("ok", True):
            tags.append("translation_gap")

    if output_mode == "hotkey_voice_qa" and result == "FAIL":
        no_submission = "no request submitted" in notes or not stage
        blank_audio = finish_reason == "muted_recovery" or asr_commit_reason == "blank_partial"
        if no_submission or blank_audio:
            tags.append("voice_lifecycle_gap")

    return sorted(dict.fromkeys(tags))


def known_term_in_question(record: dict[str, Any], alias_index: dict[str, set[str]]) -> bool:
    label = str(record.get("label") or "")
    question = str(record.get("question") or "")
    terms = alias_index.get(label) or set().union(*alias_index.values()) if alias_index else set()
    question_lower = question.lower()
    for term in terms:
        cleaned = term.strip()
        if len(cleaned) < 2:
            continue
        if cleaned.lower() in question_lower:
            return True
    return False


def suggest_area(tags: list[str]) -> str:
    if "translation_gap" in tags:
        return "screen_translation_eval_cases.tsv / glossary / formatter"
    if "voice_lifecycle_gap" in tags:
        return "hotkey voice ASR capture / endpoint microphone lifecycle"
    if "asr_variant" in tags:
        return "aliases.json observed_asr"
    if "alias_gap" in tags:
        return "aliases.json and matching knowledge row"
    if "ranking_gap" in tags:
        return "retrieval ranking / qa_goldens.jsonl"
    if "spoiler_gate_gap" in tags:
        return "spoiler_graph.json / progress_gate metadata"
    return "knowledge/*.jsonl and qa_goldens.jsonl"


def suggest_regression(tags: list[str], label: str, question: str) -> str:
    if "translation_gap" in tags:
        return "add or update screen_translation_eval_cases.tsv row"
    if "voice_lifecycle_gap" in tags:
        return "rerun hotkey_voice_qa_batch.sh row after inspecting overlay JSON"
    if "asr_variant" in tags:
        return "add observed_asr alias plus hotkey_voice_qa_cases.tsv row"
    if "ranking_gap" in tags:
        return "add retrieval golden expecting the corrected source id"
    return f"add qa_goldens.jsonl row for {label}: {question}"


def summarize_details(record: dict[str, Any]) -> str:
    details: list[str] = []
    result = str(record.get("result") or "")
    finish_reason = str(record.get("finish_reason") or "")
    asr_commit_reason = str(record.get("asr_commit_reason") or "")
    asr_endpoint_armed = str(record.get("asr_endpoint_armed") or "")
    asr_sample_count = str(record.get("asr_sample_count") or "")
    asr_audio_read_count = str(record.get("asr_audio_read_count") or "")
    asr_audio_read_error_count = str(record.get("asr_audio_read_error_count") or "")
    asr_peak_amplitude = str(record.get("asr_peak_amplitude") or "")
    asr_last_frame_amplitude = str(record.get("asr_last_frame_amplitude") or "")
    raw_question = str(record.get("raw_question") or "")
    normalized_question = str(record.get("normalized_question") or "")
    overlay_transcript = str(record.get("overlay_transcript") or "")
    overlay_normalized_transcript = str(record.get("overlay_normalized_transcript") or "")
    overlay_matched_term = str(record.get("overlay_matched_term") or "")
    matched_term = str(record.get("matched_term") or "")
    matched_entity_id = str(record.get("matched_entity_id") or "")
    stage = str(record.get("pipeline_stage") or "")
    answer_type = str(record.get("answer_type") or "")
    llm_status = str(record.get("llm_status") or "")
    issue_type = str(record.get("issue_type") or "")
    expected = str(record.get("expected") or "")
    actual = str(record.get("actual") or "")
    evidence = str(record.get("evidence") or "")
    notes = str(record.get("notes") or "")

    if result:
        details.append(f"result={result}")
    if issue_type:
        details.append(f"issue={issue_type}")
    if expected:
        details.append(f"expected={expected}")
    if actual:
        details.append(f"actual={actual}")
    if evidence:
        details.append(f"evidence={evidence}")
    if finish_reason:
        details.append(f"finish={finish_reason}")
    if asr_commit_reason:
        details.append(f"asr_commit={asr_commit_reason}")
    if asr_endpoint_armed:
        details.append(f"endpoint_armed={asr_endpoint_armed}")
    if asr_sample_count:
        details.append(f"samples={asr_sample_count}")
    if asr_audio_read_count:
        details.append(f"reads={asr_audio_read_count}")
    if asr_audio_read_error_count:
        details.append(f"read_errors={asr_audio_read_error_count}")
    if asr_peak_amplitude:
        details.append(f"peak={asr_peak_amplitude}")
    if asr_last_frame_amplitude:
        details.append(f"last_amp={asr_last_frame_amplitude}")
    if "latest request timestamp unchanged" in notes:
        details.append("latest=stale")
    if "no request submitted" in notes:
        details.append("submission=missing")
    if overlay_transcript:
        details.append(f"asr_transcript={overlay_transcript}")
    if overlay_normalized_transcript:
        details.append(f"overlay_normalized={overlay_normalized_transcript}")
    if overlay_matched_term:
        details.append(f"overlay_matched={overlay_matched_term}")
    if raw_question and normalized_question and raw_question != normalized_question:
        details.append(f"normalized={raw_question}->{normalized_question}")
    if matched_term:
        details.append(f"matched={matched_term}")
    if matched_entity_id:
        details.append(f"entity={matched_entity_id}")
    if stage:
        details.append(f"stage={stage}")
    if answer_type:
        details.append(f"answer_type={answer_type}")
    if llm_status:
        details.append(f"llm={llm_status}")
    return "; ".join(details) if details else "-"


def load_alias_index(gkp_dir: Path) -> dict[str, set[str]]:
    if not gkp_dir.is_dir():
        return {}
    index: dict[str, set[str]] = {}
    for pack_dir in sorted(path for path in gkp_dir.iterdir() if path.is_dir()):
        manifest = load_json(pack_dir / "manifest.json")
        aliases = load_json(pack_dir / "aliases.json").get("aliases", [])
        terms = {
            str(alias.get("term") or "")
            for alias in aliases
            if isinstance(alias, dict) and alias.get("term")
        }
        game = manifest.get("game") if isinstance(manifest.get("game"), dict) else {}
        for label in game.get("retroarch_labels", []) or []:
            index[str(label)] = terms
    return index


def load_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def render_markdown(items: list[BacklogItem], input_path: Path | str) -> str:
    lines = [
        "# M18 GKP Quality Backlog",
        "",
        f"- Input: `{input_path}`",
        f"- Items: {len(items)}",
        "",
    ]
    if not items:
        lines.append("No backlog items found in the provided evidence.")
        return "\n".join(lines) + "\n"

    lines.extend([
        "| Label | Question | Tags | Suggested Area | Regression Target | Details | Source |",
        "|---|---|---|---|---|---|---|",
    ])
    for item in items:
        lines.append(
            f"| `{escape_cell(item.label)}` | {escape_cell(item.question)} | "
            f"`{', '.join(item.tags)}` | {escape_cell(item.suggested_area)} | "
            f"{escape_cell(item.regression_target)} | {escape_cell(item.details)} | "
            f"`{escape_cell(item.source)}` |"
        )
    return "\n".join(lines) + "\n"


def escape_cell(value: str) -> str:
    return value.replace("|", "\\|").replace("\n", " ")


if __name__ == "__main__":
    raise SystemExit(main())
