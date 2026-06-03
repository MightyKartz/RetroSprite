#!/usr/bin/env python3
"""Generate a manual QA packet for the M18 screen translation matrix."""

from __future__ import annotations

import argparse
import importlib.util
import sys
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CASES = ROOT / "scripts/screen_translation_eval_cases.tsv"
DEFAULT_MATRIX = ROOT / "docs/qa-feedback/rc-device-matrix.md"
DEFAULT_OUTPUT = ROOT / "docs/qa-feedback/screen-translation-manual-packet.md"
REPORT_SCRIPT = ROOT / "scripts/screen_translation_eval_report.py"


@dataclass(frozen=True)
class ManualCase:
    case_id: str
    display_name: str
    game_label: str
    screen_type: str
    trigger_phrase: str
    expected_layout: str
    expected_language: str
    number_policy: str
    evidence_required: str
    current_status: str
    current_result: str
    note_issue: str


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cases", type=Path, default=DEFAULT_CASES)
    parser.add_argument("--matrix", type=Path, default=DEFAULT_MATRIX)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    try:
        cases = build_manual_cases(args.cases, args.matrix)
        markdown = render_markdown(cases, args.cases, args.matrix)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(markdown, encoding="utf-8")
    except ValueError as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1

    counts = count_statuses(cases)
    print(
        "OK screen translation manual packet: "
        + ", ".join(f"{key}={value}" for key, value in sorted(counts.items()))
    )
    return 0


def build_manual_cases(cases_path: Path, matrix_path: Path) -> list[ManualCase]:
    report = load_report_module()
    cases = report.load_cases(cases_path)
    case_rows = load_case_rows(cases_path)
    matrix = report.load_matrix_results(matrix_path)
    statuses = report.build_statuses(cases, matrix)
    rows_by_id = {row["id"]: row for row in case_rows}
    manual_cases: list[ManualCase] = []
    for status in statuses:
        row = rows_by_id.get(status.case.case_id)
        if row is None:
            raise ValueError(f"case row missing for {status.case.case_id}")
        manual_cases.append(
            ManualCase(
                case_id=status.case.case_id,
                display_name=status.case.display_name,
                game_label=row["game_label"],
                screen_type=row["screen_type"],
                trigger_phrase=status.case.trigger_phrase,
                expected_layout=status.case.expected_layout,
                expected_language=status.case.expected_language,
                number_policy=status.case.number_policy,
                evidence_required=status.case.evidence_required,
                current_status=status.status,
                current_result=status.matrix_result,
                note_issue=status.note_issue,
            )
        )
    return manual_cases


def load_case_rows(path: Path) -> list[dict[str, str]]:
    report = load_report_module()
    import csv

    if not path.is_file():
        raise ValueError(f"cases file not found: {path}")
    with path.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        if reader.fieldnames != report.REQUIRED_COLUMNS:
            raise ValueError(f"unexpected TSV columns in {path}: {reader.fieldnames}")
        return list(reader)


def render_markdown(cases: list[ManualCase], cases_path: Path, matrix_path: Path) -> str:
    counts = count_statuses(cases)
    lines = [
        "# Screen Translation Manual QA Packet",
        "",
        f"- Cases: `{display_path(cases_path)}`",
        f"- Matrix: `{display_path(matrix_path)}`",
        f"- Rows: {len(cases)}",
        "- Current status: " + ", ".join(f"{key}={value}" for key, value in sorted(counts.items())),
        "",
        "## Test Protocol",
        "",
        "1. Install and launch the current Debug APK.",
        "2. Configure RetroArch AI Service URL as `http://localhost:4404`.",
        "3. Configure a BYOK screen translation provider with the recommended `Qwen/Qwen3-VL-8B-Instruct` model.",
        "4. Load the target game/screen, trigger the hotkey voice overlay, and say the trigger phrase exactly.",
        "5. Capture evidence after each attempt with `./scripts/rc_device_evidence.sh --gate screen_translation --case-id <case_id> --include-screenshot`; use the unique timestamped directory under `build/rc-device-evidence/` for that case. Capture only when no API key, personal data, ROM path, or long copyrighted text is visible.",
        "6. Prefer recording each result with `scripts/screen_translation_receipt_update.py --case-id <case_id> --result \"...\" --apply`, then run the receipt checker/planner so matrix updates are previewed before apply.",
        "7. Direct updater fallback: use `scripts/screen_translation_matrix_update.py` with `--output` first, then apply only after reviewing the preview and replacing every placeholder evidence path.",
        "",
            "Accepted failure categories: `numeric_corruption`, `language_leakage`, `layout_grouping`, `paging_duration`, `ocr_api`, `missing BYOK key`, `cannot reproduce screen`.",
            "Pass rows must include `checklist=` tokens proving the visible UI checks were performed.",
        "",
        "## Case Checklist",
        "",
    ]
    for case in cases:
        lines.extend(render_case(case))
    lines.extend(
        [
            "## Follow-Up Commands",
            "",
            "```bash",
            "# Replace <timestamp> with the unique real evidence directory under build/rc-device-evidence/ captured for this case, then review the preview.",
            "python3 scripts/screen_translation_matrix_update.py \\",
            "  --cases scripts/screen_translation_eval_cases.tsv \\",
            "  --case-id ff6_dialogue \\",
            "  --result \"Pass: evidence build/rc-device-evidence/<timestamp> checklist=layout_ok,language_ok,no_english_source\" \\",
            "  --output /tmp/retrosprite-screen-matrix-ff6_dialogue.md",
            "",
            "# Preferred after all cases are recorded in docs/qa-feedback/m18-manual-gate-receipt.json:",
            "python3 scripts/screen_translation_receipt_update.py \\",
            "  --case-id ff6_dialogue \\",
            "  --result \"Pass: evidence build/rc-device-evidence/<timestamp> checklist=layout_ok,language_ok,no_english_source\" \\",
            "  --output /tmp/retrosprite-m18-receipt-preview.json",
            "python3 scripts/m18_manual_gate_receipt_check.py \\",
            "  --output docs/qa-feedback/m18-manual-gate-receipt-check.md \\",
            "  --template-output docs/qa-feedback/m18-manual-gate-receipt-template.json",
            "python3 scripts/m18_manual_gate_receipt_plan.py \\",
            "  --output docs/qa-feedback/m18-manual-gate-receipt-plan.md",
            "",
            "# The receipt planner emits matching preview and apply commands. Do not apply placeholder evidence paths.",
            "python3 scripts/screen_translation_eval_report.py \\",
            "  --output docs/qa-feedback/screen-translation-eval-report.md \\",
            "  --strict",
            "python3 scripts/m18_status_report.py \\",
            "  --output docs/qa-feedback/m18-status-report.md",
            "```",
            "",
        ]
    )
    return "\n".join(lines)


def render_case(case: ManualCase) -> list[str]:
    lines = [
        f"### {case.display_name}",
        "",
        f"- Case id: `{case.case_id}`",
        f"- Game label: `{case.game_label}`",
        f"- Screen type: `{case.screen_type}`",
        f"- Say: `{case.trigger_phrase}`",
        f"- Expected layout: `{case.expected_layout}`",
        f"- Expected language: `{case.expected_language}`",
        f"- Number policy: `{case.number_policy}`",
        f"- Evidence required: `{case.evidence_required}`",
        f"- Current matrix result: {case.current_result}",
        f"- Current status: `{case.current_status}`",
    ]
    if case.note_issue != "-":
        lines.append(f"- Note issue: `{case.note_issue}`")
    lines.extend(
        [
            "",
            "Acceptance checks:",
        ]
    )
    lines.extend(f"- [ ] {check}" for check in acceptance_checks(case))
    lines.extend(
        [
            "",
            "Matrix result template:",
            "",
            "```text",
            result_template(case),
            "```",
            "",
            "Evidence capture command:",
            "",
            "```bash",
            f"./scripts/rc_device_evidence.sh --gate screen_translation --case-id {case.case_id} --include-screenshot",
            "```",
            "",
        ]
    )
    return lines


def acceptance_checks(case: ManualCase) -> list[str]:
    checks = [
        "Trigger phrase starts a screen translation request, not a normal Q&A answer.",
        "The result is readable within the overlay and does not overlap existing UI text.",
    ]
    if case.expected_layout == "chinese_only":
        checks.append("Only translated Chinese is shown; the English source text is not displayed.")
    if case.expected_layout in {"bilingual_rows", "grouped_labels"}:
        checks.append("Rows keep source labels and Chinese translations together in compact groups.")
    if case.expected_layout == "paged_overlay":
        checks.append("Each page remains visible for 10 seconds, including the last page.")
    if "preserve" in case.number_policy:
        checks.append("Numbers and stat values are preserved exactly and are not translated as prose.")
    if case.expected_language == "en_zh":
        checks.append("English source and Chinese translation are both visible for menu-like UI.")
    if case.expected_language == "zh":
        checks.append("The visible answer language is Chinese.")
    checks.append("Evidence path is recorded in the matrix row, exists under build/rc-device-evidence/, includes `screenshot.png`, has metadata gate `screen_translation` with this case id, and is unique to this case.")
    checks.append("The result note includes the required checklist tokens: `" + ",".join(pass_checklist_tokens(case)) + "`.")
    return checks


def result_template(case: ManualCase) -> str:
    if case.current_status == "pass" and case.note_issue == "-":
        return case.current_result
    return "Pass: evidence build/rc-device-evidence/<timestamp> checklist=" + ",".join(pass_checklist_tokens(case))


def pass_checklist_tokens(case: ManualCase) -> tuple[str, ...]:
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


def count_statuses(cases: list[ManualCase]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for case in cases:
        counts[case.current_status] = counts.get(case.current_status, 0) + 1
    return counts


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
