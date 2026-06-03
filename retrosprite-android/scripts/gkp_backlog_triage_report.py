#!/usr/bin/env python3
"""Triage M18 GKP backlog rows against the current review packet.

This report does not edit bundled GKP assets. It answers a narrower operational
question: which backlog rows are already covered by the current approval-gated
ASR patch packet, and which rows still need a separate device, policy, or GKP
follow-up.
"""

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
DEFAULT_BACKLOG = ROOT / "docs/qa-feedback/gkp-quality-backlog.md"
DEFAULT_REVIEW_PACKET = ROOT / "docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md"
DEFAULT_GKP_ROOT = ROOT / "app/src/main/assets/gkp"
DEFAULT_HOTKEY_RESULTS_ROOT = ROOT / "build/hotkey-voice-qa"
DEFAULT_OUTPUT = ROOT / "docs/qa-feedback/gkp-backlog-triage-report.md"


@dataclass(frozen=True)
class BacklogRow:
    label: str
    question: str
    tags: tuple[str, ...]
    suggested_area: str
    regression_target: str
    details: str
    source: str


@dataclass(frozen=True)
class PatchRow:
    pack_id: str
    alias_term: str
    canonical_term: str
    entity_id: str
    source_refs: tuple[str, ...]
    status: str = "ready"


@dataclass(frozen=True)
class ExistingGolden:
    pack_dir: str
    qa_id: str
    question: str
    expected_intent: str
    source_refs: tuple[str, ...]


@dataclass(frozen=True)
class DeviceRerunPass:
    label: str
    question: str
    evidence: str
    sort_key: str
    finish_reason: str
    source_ids: str


@dataclass(frozen=True)
class TriageRow:
    backlog: BacklogRow
    category: str
    status: str
    patch_match: str
    next_step: str
    evidence: str


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--backlog", type=Path, default=DEFAULT_BACKLOG)
    parser.add_argument("--review-packet", type=Path, default=DEFAULT_REVIEW_PACKET)
    parser.add_argument("--gkp-root", type=Path, default=DEFAULT_GKP_ROOT)
    parser.add_argument("--hotkey-results-root", type=Path, default=DEFAULT_HOTKEY_RESULTS_ROOT)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument(
        "--strict",
        action="store_true",
        help="Exit nonzero when any backlog row is unclassified.",
    )
    args = parser.parse_args()

    try:
        backlog_rows = load_backlog(args.backlog)
        patch_rows = load_patch_rows(args.review_packet)
        existing_policy_goldens = load_existing_policy_goldens(args.gkp_root)
        device_rerun_passes = load_device_rerun_passes(args.hotkey_results_root)
        triage_rows = build_triage(
            backlog_rows,
            patch_rows,
            existing_policy_goldens,
            device_rerun_passes,
        )
    except ValueError as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        render_markdown(triage_rows, args.backlog, args.review_packet),
        encoding="utf-8",
    )
    counts = count_by_category(triage_rows)
    print(
        "OK GKP backlog triage: "
        f"items={len(triage_rows)}, "
        + ", ".join(f"{key}={counts[key]}" for key in sorted(counts))
    )
    if args.strict and any(row.category == "unclassified" for row in triage_rows):
        return 1
    return 0


def load_backlog(path: Path) -> list[BacklogRow]:
    if not path.is_file():
        raise ValueError(f"backlog file not found: {path}")
    rows: list[BacklogRow] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.startswith("| `"):
            continue
        cells = split_markdown_row(line)
        if len(cells) != 7:
            continue
        label, question, tags, suggested_area, regression_target, details, source = cells
        rows.append(
            BacklogRow(
                label=strip_code(unescape_cell(label)),
                question=unescape_cell(question),
                tags=tuple(parse_tags(tags)),
                suggested_area=unescape_cell(suggested_area),
                regression_target=unescape_cell(regression_target),
                details=unescape_cell(details),
                source=strip_code(unescape_cell(source)),
            )
        )
    return rows


def load_patch_rows(path: Path) -> list[PatchRow]:
    if not path.is_file():
        return []
    text = path.read_text(encoding="utf-8")
    sections = list(re.finditer(r"^## (community\.[^\n]+)$", text, re.MULTILINE))
    rows: list[PatchRow] = []
    for index, match in enumerate(sections):
        pack_id = match.group(1).strip()
        section_end = sections[index + 1].start() if index + 1 < len(sections) else len(text)
        section = text[match.end() : section_end]
        status_match = re.search(r"^- Status:\s*`([^`]+)`", section, re.MULTILINE)
        status = status_match.group(1).strip() if status_match else ""
        if status not in {"ready", "applied"}:
            continue
        json_blocks = re.findall(r"```json\s*(.*?)\s*```", section, re.DOTALL)
        if len(json_blocks) < 2:
            continue
        try:
            alias = json.loads(json_blocks[0])
            golden = json.loads(json_blocks[1])
        except json.JSONDecodeError as exc:
            raise ValueError(f"invalid JSON block in review packet {pack_id}: {exc}") from exc
        rows.append(
            PatchRow(
                pack_id=pack_id,
                alias_term=str(alias.get("term") or ""),
                canonical_term=str(alias.get("canonical_term") or golden.get("expected_normalized_question") or ""),
                entity_id=str(alias.get("entity_id") or ""),
                source_refs=tuple(str(value) for value in golden.get("source_refs") or []),
                status=status,
            )
        )
    return rows


def load_existing_policy_goldens(gkp_root: Path) -> list[ExistingGolden]:
    if not gkp_root.is_dir():
        return []
    rows: list[ExistingGolden] = []
    for path in sorted(gkp_root.glob("*/qa_goldens.jsonl")):
        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
            if not line.strip():
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"invalid qa_goldens JSON in {path}:{line_number}: {exc}") from exc
            expected_intent = str(row.get("expected_intent") or "")
            if expected_intent not in {"no_evidence", "unknown_or_out_of_scope"}:
                continue
            question = str(row.get("question") or "").strip()
            if not question:
                continue
            source_refs = row.get("source_refs") or []
            if not isinstance(source_refs, list):
                source_refs = []
            rows.append(
                ExistingGolden(
                    pack_dir=display_path(path.parent),
                    qa_id=str(row.get("qa_id") or ""),
                    question=question,
                    expected_intent=expected_intent,
                    source_refs=tuple(str(value) for value in source_refs),
                )
            )
    return rows


def build_triage(
    backlog_rows: list[BacklogRow],
    patch_rows: list[PatchRow],
    existing_policy_goldens: list[ExistingGolden] | None = None,
    device_rerun_passes: list[DeviceRerunPass] | None = None,
) -> list[TriageRow]:
    existing_policy_goldens = existing_policy_goldens or []
    device_rerun_passes = device_rerun_passes or []
    return [triage_row(row, patch_rows, existing_policy_goldens, device_rerun_passes) for row in backlog_rows]


def triage_row(
    row: BacklogRow,
    patch_rows: list[PatchRow],
    existing_policy_goldens: list[ExistingGolden],
    device_rerun_passes: list[DeviceRerunPass],
) -> TriageRow:
    patch = matching_patch(row, patch_rows)
    if patch is not None:
        if patch.status == "applied":
            return TriageRow(
                backlog=row,
                category="asr_patch_applied",
                status="covered_by_applied_patch",
                patch_match=f"{patch.pack_id}: {patch.alias_term} -> {patch.canonical_term}",
                next_step="Patch rows are already applied; keep the GKP regression result and replay this voice row on device.",
                evidence=row.source,
            )
        return TriageRow(
            backlog=row,
            category="asr_patch_ready",
            status="covered_by_review_packet",
            patch_match=f"{patch.pack_id}: {patch.alias_term} -> {patch.canonical_term}",
            next_step="Wait for human approval, apply the review packet, run GKP regression, then replay this voice row.",
            evidence=row.source,
        )
    if "voice_lifecycle_gap" in row.tags:
        device_pass = matching_device_rerun_pass(row, device_rerun_passes)
        if device_pass is not None:
            return TriageRow(
                backlog=row,
                category="device_rerun_passed",
                status="covered_by_device_rerun",
                patch_match=device_pass.evidence,
                next_step=(
                    "No GKP asset change is needed; keep the fresh voice evidence and reopen only if a later "
                    f"matrix run regresses. finish={device_pass.finish_reason}; sources={device_pass.source_ids}"
                ),
                evidence=device_pass.evidence,
            )
        return TriageRow(
            backlog=row,
            category="device_rerun_needed",
            status="open",
            patch_match="-",
            next_step="Rerun the single voice row with stable volume and inspect overlay audio counters before proposing content changes.",
            evidence=row.source,
        )
    if "translation_gap" in row.tags:
        return TriageRow(
            backlog=row,
            category="screen_translation_followup",
            status="open",
            patch_match="-",
            next_step="Keep this in the screen translation manual matrix and add/verify a focused screen case before code or glossary changes.",
            evidence=row.source,
        )
    if "spoiler_gate_gap" in row.tags:
        existing_golden = matching_policy_golden(row, existing_policy_goldens)
        if existing_golden is not None:
            return TriageRow(
                backlog=row,
                category="policy_golden_existing",
                status="covered_by_existing_golden",
                patch_match=f"{existing_golden.pack_dir}: {existing_golden.qa_id}",
                next_step="Do not add a duplicate golden; inspect runtime/latest-request stage and replay this boundary row if the voice result still fails.",
                evidence=row.source,
            )
        return TriageRow(
            backlog=row,
            category="policy_golden_needed",
            status="open",
            patch_match="-",
            next_step="Add a low-spoiler refusal or route-boundary golden after human review; do not answer full route/script content.",
            evidence=row.source,
        )
    if "ranking_gap" in row.tags:
        return TriageRow(
            backlog=row,
            category="retrieval_golden_needed",
            status="open",
            patch_match="-",
            next_step="Add a retrieval golden for the expected source id before changing ranking or aliases.",
            evidence=row.source,
        )
    if "alias_gap" in row.tags or "coverage_gap" in row.tags:
        return TriageRow(
            backlog=row,
            category="gkp_triage_needed",
            status="open",
            patch_match="-",
            next_step="Create a dry-run GKP patch proposal with source refs and a regression golden, then require human approval.",
            evidence=row.source,
        )
    return TriageRow(
        backlog=row,
        category="unclassified",
        status="open",
        patch_match="-",
        next_step="Inspect the evidence manually and extend triage rules before applying any patch.",
        evidence=row.source,
    )


def matching_patch(row: BacklogRow, patch_rows: list[PatchRow]) -> PatchRow | None:
    normalized_question = normalize_question(row.question)
    for patch in patch_rows:
        if not patch.alias_term:
            continue
        canonical_matches = normalize_question(patch.canonical_term) == normalized_question
        if patch.alias_term in row.details and canonical_matches:
            return patch
        if (
            "asr_variant" in row.tags
            and patch.alias_term in row.question
            and canonical_matches
        ):
            return patch
    return None


def matching_policy_golden(row: BacklogRow, existing_policy_goldens: list[ExistingGolden]) -> ExistingGolden | None:
    normalized_question = normalize_question(row.question)
    for golden in existing_policy_goldens:
        if normalize_question(golden.question) == normalized_question:
            return golden
    return None


def load_device_rerun_passes(results_root: Path) -> list[DeviceRerunPass]:
    if not results_root.is_dir():
        return []
    passes: list[DeviceRerunPass] = []
    for path in sorted(results_root.glob("*/results.tsv")):
        with path.open("r", encoding="utf-8", newline="") as handle:
            reader = csv.DictReader(handle, delimiter="\t")
            for row in reader:
                if str(row.get("result") or "").strip().upper() != "PASS":
                    continue
                label = str(row.get("label") or "").strip()
                question = str(row.get("spoken_prompt") or "").strip()
                if not label or not question:
                    continue
                passes.append(
                    DeviceRerunPass(
                        label=label,
                        question=question,
                        evidence=display_path(path),
                        sort_key=hotkey_evidence_sort_key(path),
                        finish_reason=str(row.get("finish_reason") or ""),
                        source_ids=str(row.get("source_ids") or ""),
                    )
                )
    return passes


def matching_device_rerun_pass(row: BacklogRow, passes: list[DeviceRerunPass]) -> DeviceRerunPass | None:
    source_key = hotkey_evidence_sort_key(Path(row.source))
    matches = [
        item
        for item in passes
        if item.label == row.label
        and normalize_question(item.question) == normalize_question(row.question)
        and item.sort_key > source_key
    ]
    return sorted(matches, key=lambda item: item.sort_key)[-1] if matches else None


def hotkey_evidence_sort_key(path: Path) -> str:
    parts = path.parts
    for part in reversed(parts):
        if re.fullmatch(r"\d{8}-\d{6}", part):
            return part
    try:
        return f"{path.stat().st_mtime:020.6f}"
    except OSError:
        return ""


def normalize_question(value: str) -> str:
    return value.strip().rstrip("?.!？。")


def render_markdown(rows: list[TriageRow], backlog_path: Path, review_packet_path: Path) -> str:
    counts = count_by_category(rows)
    status_counts = count_by_status(rows)
    lines = [
        "# M18 GKP Backlog Triage Report",
        "",
        f"- Backlog: `{display_path(backlog_path)}`",
        f"- Review packet: `{display_path(review_packet_path)}`",
        f"- Items: {len(rows)}",
        "- Categories: " + (", ".join(f"{key}={counts[key]}" for key in sorted(counts)) or "-"),
        "- Status: " + (", ".join(f"{key}={status_counts[key]}" for key in sorted(status_counts)) or "-"),
        "- GKP assets edited: no",
        "",
        "| Label | Question | Tags | Category | Status | Patch Match | Next Step | Evidence |",
        "|---|---|---|---|---|---|---|---|",
    ]
    for row in rows:
        backlog = row.backlog
        lines.append(
            f"| `{escape_cell(backlog.label)}` | {escape_cell(backlog.question)} | "
            f"`{escape_cell(', '.join(backlog.tags))}` | `{escape_cell(row.category)}` | "
            f"`{escape_cell(row.status)}` | {escape_cell(row.patch_match)} | "
            f"{escape_cell(row.next_step)} | `{escape_cell(row.evidence)}` |"
        )
    return "\n".join(lines) + "\n"


def count_by_category(rows: list[TriageRow]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for row in rows:
        counts[row.category] = counts.get(row.category, 0) + 1
    return counts


def count_by_status(rows: list[TriageRow]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for row in rows:
        counts[row.status] = counts.get(row.status, 0) + 1
    return counts


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
            current.append("|" if char == "|" else "\\" + char)
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


def parse_tags(value: str) -> list[str]:
    text = strip_code(unescape_cell(value))
    return [part.strip() for part in text.split(",") if part.strip()]


def strip_code(value: str) -> str:
    value = value.strip()
    if value.startswith("`") and value.endswith("`"):
        return value[1:-1]
    return value


def unescape_cell(value: str) -> str:
    return value.replace("\\|", "|")


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
