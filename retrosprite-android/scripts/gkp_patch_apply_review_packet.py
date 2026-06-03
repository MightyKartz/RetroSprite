#!/usr/bin/env python3
"""Dry-run or apply a human-approved GKP patch review packet.

The default mode is dry-run and never edits bundled GKP assets. Apply mode
requires an explicit approval phrase so review packets cannot be written by
accident.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_PACKET = ROOT / "docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md"
DEFAULT_GKP_ROOT = ROOT / "app/src/main/assets/gkp"
DEFAULT_DRY_RUN_OUTPUT = ROOT / "docs/qa-feedback/gkp-patch-apply-dry-run-20260601-hotkey-voice.md"
DEFAULT_APPLY_OUTPUT = ROOT / "docs/qa-feedback/gkp-patch-apply-result-20260601-hotkey-voice.md"
REQUIRED_APPROVAL = "I approve gkp patch review packet 20260601 hotkey voice"


@dataclass(frozen=True)
class PacketItem:
    pack_id: str
    pack_dir_text: str
    pack_dir: Path
    packet_status: str
    alias_row: dict[str, Any]
    golden_row: dict[str, Any]


@dataclass(frozen=True)
class ApplyRow:
    item: PacketItem
    alias_path: Path
    golden_path: Path
    status: str
    detail: str


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--packet", type=Path, default=DEFAULT_PACKET)
    parser.add_argument("--gkp-root", type=Path, default=DEFAULT_GKP_ROOT)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--apply", action="store_true", help="Write approved rows to GKP assets.")
    parser.add_argument("--approval", default="", help="Required exact approval phrase for --apply.")
    parser.add_argument("--strict", action="store_true", help="Exit nonzero when any row is blocked.")
    args = parser.parse_args()

    try:
        rows = build_apply_rows(args.packet, args.gkp_root)
        assets_edited = 0
        mode = "apply" if args.apply else "dry_run"
        if args.apply:
            assets_edited = apply_rows(rows, args.approval)
        markdown = render_markdown(rows, args.packet, args.gkp_root, mode, assets_edited)
        output_path = args.output or default_output(args.apply)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(markdown, encoding="utf-8")
    except ValueError as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1

    counts = count_statuses(rows)
    print(
        "OK GKP patch apply packet: "
        + ", ".join(f"{key}={value}" for key, value in sorted(counts.items()))
        + f", mode={mode}, assets_edited={assets_edited}"
    )
    if args.strict and any(row.status not in {"ready", "applied"} for row in rows):
        return 1
    return 0


def default_output(apply_mode: bool) -> Path:
    return DEFAULT_APPLY_OUTPUT if apply_mode else DEFAULT_DRY_RUN_OUTPUT


def build_apply_rows(packet_path: Path, gkp_root: Path) -> list[ApplyRow]:
    items = parse_packet(packet_path, gkp_root)
    if not items:
        raise ValueError(f"no packet rows found: {packet_path}")
    return [validate_item(item, gkp_root) for item in items]


def parse_packet(packet_path: Path, gkp_root: Path) -> list[PacketItem]:
    text = read_required(packet_path)
    matches = list(re.finditer(r"^## (community\.[^\n]+)$", text, re.MULTILINE))
    items: list[PacketItem] = []
    for index, match in enumerate(matches):
        pack_id = match.group(1).strip()
        section_end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        section = text[match.end() : section_end]
        pack_dir_text = extract_required(section, r"^- Pack dir: `([^`]+)`", "pack dir", pack_id)
        packet_status = extract_required(section, r"^- Status: `([^`]+)`", "packet status", pack_id)
        alias_row = extract_json_block(section, "aliases.json row", pack_id)
        golden_row = extract_json_block(section, "qa_goldens.jsonl row", pack_id)
        items.append(
            PacketItem(
                pack_id=pack_id,
                pack_dir_text=pack_dir_text,
                pack_dir=resolve_pack_dir(pack_dir_text, gkp_root),
                packet_status=packet_status,
                alias_row=alias_row,
                golden_row=golden_row,
            )
        )
    return items


def validate_item(item: PacketItem, gkp_root: Path) -> ApplyRow:
    issues: list[str] = []
    alias_path = item.pack_dir / "aliases.json"
    golden_path = item.pack_dir / "qa_goldens.jsonl"

    if item.packet_status not in {"ready", "applied"}:
        issues.append(f"packet status is {item.packet_status}")
    if not is_under(item.pack_dir, gkp_root):
        issues.append("pack dir is outside gkp root")
    if not item.pack_dir.is_dir():
        issues.append("pack dir not found")
    if not alias_path.is_file():
        issues.append("aliases.json not found")
    if not golden_path.is_file():
        issues.append("qa_goldens.jsonl not found")

    alias_term = str(item.alias_row.get("term") or "").strip()
    alias_entity = str(item.alias_row.get("entity_id") or "").strip()
    golden_id = str(item.golden_row.get("qa_id") or "").strip()
    golden_question = str(item.golden_row.get("question") or "").strip()

    if not alias_term or not alias_entity:
        issues.append("alias row missing term or entity_id")
    if not golden_id or not golden_question:
        issues.append("golden row missing qa_id or question")

    alias_present = alias_path.is_file() and alias_exists(alias_path, alias_term, alias_entity)
    qa_id_present = golden_path.is_file() and qa_id_exists(golden_path, golden_id)
    question_present = golden_path.is_file() and question_exists(golden_path, golden_question)
    already_applied = alias_present and qa_id_present and question_present

    if alias_present and not already_applied:
        issues.append("alias already exists")
    if qa_id_present and not already_applied:
        issues.append("qa_id already exists")
    if question_present and not already_applied:
        issues.append("golden question already exists")

    if issues:
        status = "blocked"
        detail = "; ".join(issues)
    elif already_applied:
        status = "applied"
        detail = "already applied to bundled GKP assets"
    else:
        status = "ready"
        detail = "ready to apply after explicit approval"

    return ApplyRow(
        item=item,
        alias_path=alias_path,
        golden_path=golden_path,
        status=status,
        detail=detail,
    )


def apply_rows(rows: list[ApplyRow], approval: str) -> int:
    if approval != REQUIRED_APPROVAL:
        raise ValueError(f"--apply requires approval phrase: {REQUIRED_APPROVAL}")
    blocked = [row for row in rows if row.status not in {"ready", "applied"}]
    if blocked:
        details = "; ".join(f"{row.item.pack_id}: {row.detail}" for row in blocked)
        raise ValueError(f"cannot apply blocked rows: {details}")

    edited_paths: set[Path] = set()
    for row in rows:
        if row.status == "applied":
            continue
        append_alias(row.alias_path, row.item.alias_row)
        edited_paths.add(row.alias_path)
        append_golden(row.golden_path, row.item.golden_row)
        edited_paths.add(row.golden_path)
    return len(edited_paths)


def append_alias(path: Path, alias_row: dict[str, Any]) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError(f"aliases.json is not an object: {path}")
    aliases = data.setdefault("aliases", [])
    if not isinstance(aliases, list):
        raise ValueError(f"aliases field is not a list: {path}")
    aliases.append(alias_row)
    path.write_text(render_aliases_json(data), encoding="utf-8")


def render_aliases_json(data: dict[str, Any]) -> str:
    aliases = data.get("aliases")
    if not isinstance(aliases, list):
        raise ValueError("aliases field is not a list")

    lines = ["{", f'  "language": {json.dumps(data.get("language", "zh"), ensure_ascii=False)},', '  "aliases": [']
    for index, alias in enumerate(aliases):
        suffix = "," if index < len(aliases) - 1 else ""
        lines.append(f"    {json.dumps(alias, ensure_ascii=False)}{suffix}")
    lines.extend(["  ]", "}", ""])
    return "\n".join(lines)


def append_golden(path: Path, golden_row: dict[str, Any]) -> None:
    text = path.read_text(encoding="utf-8") if path.is_file() else ""
    if text and not text.endswith("\n"):
        text += "\n"
    text += json.dumps(golden_row, ensure_ascii=False, sort_keys=True) + "\n"
    path.write_text(text, encoding="utf-8")


def render_markdown(
    rows: list[ApplyRow],
    packet_path: Path,
    gkp_root: Path,
    mode: str,
    assets_edited: int,
) -> str:
    counts = count_statuses(rows)
    title = "Apply Result" if mode == "apply" else "Dry Run"
    lines = [
        f"# GKP Patch Apply {title} - Hotkey Voice ASR Variants",
        "",
        f"- Packet: `{display_path(packet_path)}`",
        f"- GKP root: `{display_path(gkp_root)}`",
        f"- Mode: `{mode}`",
        f"- Assets edited: {'no' if assets_edited == 0 else assets_edited}",
        f"- Rows: {len(rows)}",
        "- Status: " + ", ".join(f"{key}={counts.get(key, 0)}" for key in ["ready", "applied", "blocked"]),
        "",
        "| Pack | Alias Term | Entity | Golden QA | Status | Detail |",
        "|---|---|---|---|---|---|",
    ]
    for row in rows:
        item = row.item
        lines.append(
            f"| `{escape_cell(item.pack_id)}` | {escape_cell(str(item.alias_row.get('term') or ''))} | "
            f"`{escape_cell(str(item.alias_row.get('entity_id') or ''))}` | "
            f"`{escape_cell(str(item.golden_row.get('qa_id') or ''))}` | "
            f"`{escape_cell(row.status)}` | {escape_cell(row.detail)} |"
        )
    lines.extend(
        [
            "",
            "## Planned File Edits",
            "",
        ]
    )
    for row in rows:
        lines.extend(
            [
                f"### {row.item.pack_id}",
                "",
                f"- Pack dir: `{display_path(row.item.pack_dir)}`",
                f"- aliases.json: `{display_path(row.alias_path)}`",
                f"- qa_goldens.jsonl: `{display_path(row.golden_path)}`",
                f"- Status: `{row.status}`",
                "",
                "#### aliases.json append row",
                "",
                "```json",
                json.dumps(row.item.alias_row, ensure_ascii=False, sort_keys=True),
                "```",
                "",
                "#### qa_goldens.jsonl append row",
                "",
                "```json",
                json.dumps(row.item.golden_row, ensure_ascii=False, sort_keys=True),
                "```",
                "",
            ]
        )
    lines.extend(
        [
            "## Approval Boundary",
            "",
            "This report is a dry-run artifact unless it was generated with `--apply` and the exact approval phrase. Do not edit bundled GKP assets from this packet until the exact JSON rows have been approved by a human reviewer.",
            "",
        ]
    )
    return "\n".join(lines)


def extract_required(section: str, pattern: str, field_name: str, pack_id: str) -> str:
    match = re.search(pattern, section, re.MULTILINE)
    if not match:
        raise ValueError(f"{field_name} missing for {pack_id}")
    return match.group(1).strip()


def extract_json_block(section: str, label: str, pack_id: str) -> dict[str, Any]:
    label_index = section.find(f"### {label}")
    if label_index < 0:
        raise ValueError(f"{label} section missing for {pack_id}")
    match = re.search(r"```json\s*(.*?)\s*```", section[label_index:], re.DOTALL)
    if not match:
        raise ValueError(f"{label} JSON block missing for {pack_id}")
    try:
        row = json.loads(match.group(1))
    except json.JSONDecodeError as exc:
        raise ValueError(f"{label} JSON invalid for {pack_id}: {exc}") from exc
    if not isinstance(row, dict):
        raise ValueError(f"{label} JSON is not an object for {pack_id}")
    return row


def resolve_pack_dir(pack_dir_text: str, gkp_root: Path) -> Path:
    path = Path(pack_dir_text)
    if path.is_absolute():
        return path.resolve()
    root_relative = (ROOT / path).resolve()
    if root_relative.exists() or str(path).startswith("app/"):
        return root_relative
    return (gkp_root / path).resolve()


def alias_exists(path: Path, term: str, entity_id: str) -> bool:
    data = json.loads(path.read_text(encoding="utf-8"))
    aliases = data.get("aliases") if isinstance(data, dict) else []
    return any(
        isinstance(row, dict) and row.get("term") == term and row.get("entity_id") == entity_id
        for row in aliases
    )


def qa_id_exists(path: Path, qa_id: str) -> bool:
    return any(row.get("qa_id") == qa_id for row in read_jsonl(path))


def question_exists(path: Path, question: str) -> bool:
    normalized = normalize_question(question)
    return any(normalize_question(str(row.get("question") or "")) == normalized for row in read_jsonl(path))


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    if not path.is_file():
        return rows
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip():
            rows.append(json.loads(line))
    return rows


def normalize_question(value: str) -> str:
    return value.strip().rstrip("？?")


def count_statuses(rows: list[ApplyRow]) -> dict[str, int]:
    counts = {"ready": 0, "applied": 0, "blocked": 0}
    for row in rows:
        counts[row.status] = counts.get(row.status, 0) + 1
    return counts


def is_under(path: Path, root: Path) -> bool:
    try:
        path.resolve().relative_to(root.resolve())
        return True
    except ValueError:
        return False


def read_required(path: Path) -> str:
    if not path.is_file():
        raise ValueError(f"file not found: {path}")
    return path.read_text(encoding="utf-8")


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
