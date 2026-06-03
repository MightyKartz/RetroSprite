#!/usr/bin/env python3
"""Generate exact human-review rows for audited GKP patch proposals.

This script does not modify bundled GKP assets. It converts audited ASR alias
proposals into the exact alias and golden JSON rows a reviewer can approve.
"""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_PROPOSALS = ROOT / "docs/qa-feedback/gkp-patch-proposals-20260601-hotkey-voice.md"
DEFAULT_BACKLOG = ROOT / "docs/qa-feedback/gkp-quality-backlog.md"
DEFAULT_GKP_ROOT = ROOT / "app/src/main/assets/gkp"
DEFAULT_OUTPUT = ROOT / "docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md"
DEFAULT_JSON_OUTPUT = ROOT / "docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.json"
AUDIT_SCRIPT = ROOT / "scripts/gkp_patch_proposal_audit.py"


@dataclass(frozen=True)
class ReviewItem:
    pack_id: str
    pack_dir: str
    alias_row: dict[str, Any]
    golden_row: dict[str, Any]
    status: str
    detail: str


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--proposals", type=Path, default=DEFAULT_PROPOSALS)
    parser.add_argument("--backlog", type=Path, default=DEFAULT_BACKLOG)
    parser.add_argument("--gkp-root", type=Path, default=DEFAULT_GKP_ROOT)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--json-output", type=Path, default=DEFAULT_JSON_OUTPUT)
    parser.add_argument("--strict", action="store_true", help="Exit nonzero when any review item is not ready.")
    args = parser.parse_args()

    try:
        items = build_review_items(args.proposals, args.backlog, args.gkp_root)
    except ValueError as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1

    markdown = render_markdown(items, args.proposals, args.backlog)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(markdown, encoding="utf-8")
    json_data = render_json(items, args.proposals, args.backlog, args.gkp_root, args.output)
    args.json_output.parent.mkdir(parents=True, exist_ok=True)
    args.json_output.write_text(json.dumps(json_data, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    counts = count_statuses(items)
    print(
        "OK GKP patch review packet: "
        + ", ".join(f"{key}={value}" for key, value in sorted(counts.items()))
        + f", json={display_path(args.json_output)}"
    )
    if args.strict and any(item.status not in {"ready", "applied"} for item in items):
        return 1
    return 0


def build_review_items(proposals_path: Path, backlog_path: Path, gkp_root: Path) -> list[ReviewItem]:
    audit = load_audit_module()
    audit_rows = audit.audit_proposals(proposals_path, backlog_path, gkp_root)
    evidence_source = extract_evidence_source(backlog_path)
    items: list[ReviewItem] = []
    for audit_row in audit_rows:
        if audit_row.status != "pass":
            proposal = audit_row.proposal
            items.append(
                ReviewItem(
                    pack_id=proposal.pack_id,
                    pack_dir=audit_row.pack_dir,
                    alias_row={},
                    golden_row={},
                    status="blocked",
                    detail=f"proposal audit failed: {audit_row.detail}",
                )
            )
            continue
        pack_dir = ROOT / audit_row.pack_dir
        items.append(build_review_item(audit_row.proposal, pack_dir, evidence_source))
    if not items:
        raise ValueError("no review items generated")
    return items


def build_review_item(proposal, pack_dir: Path, evidence_source: str | None = None) -> ReviewItem:
    entity = load_entity(pack_dir / "knowledge", proposal.entity_id)
    if entity is None:
        return empty_blocked(proposal, pack_dir, "entity row missing")

    alias_row = build_alias_row(proposal)
    golden_row = build_golden_row(proposal, entity, pack_dir, evidence_source)
    issues = []

    alias_present = alias_exists(pack_dir / "aliases.json", proposal.observed_asr, proposal.entity_id)
    qa_id_present = qa_id_exists(pack_dir / "qa_goldens.jsonl", golden_row["qa_id"])
    question_present = question_exists(pack_dir / "qa_goldens.jsonl", proposal.observed_asr)
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
        detail = "alias + golden rows already applied to bundled GKP assets"
    else:
        status = "ready"
        detail = "alias + golden rows ready for human approval"

    return ReviewItem(
        pack_id=proposal.pack_id,
        pack_dir=display_path(pack_dir),
        alias_row=alias_row,
        golden_row=golden_row,
        status=status,
        detail=detail,
    )


def build_alias_row(proposal) -> dict[str, Any]:
    return {
        "term": proposal.observed_asr,
        "entity_id": proposal.entity_id,
        "weight": 0.78,
        "kind": "observed_asr",
        "source": "observed_asr",
        "canonical_term": proposal.canonical,
        "notes": f"Observed RG476H hotkey voice transcript for {proposal.canonical}.",
    }


def build_golden_row(
    proposal,
    entity: dict[str, Any],
    pack_dir: Path,
    evidence_source: str | None = None,
) -> dict[str, Any]:
    manifest = json.loads((pack_dir / "manifest.json").read_text(encoding="utf-8"))
    game = manifest.get("game") if isinstance(manifest.get("game"), dict) else {}
    evidence_note = evidence_source or "docs/qa-feedback/gkp-quality-backlog.md"
    return {
        "qa_id": unique_qa_id(pack_dir, proposal.entity_id, proposal.observed_asr),
        "game_id": str(game.get("game_id") or ""),
        "language": "zh",
        "question": proposal.observed_asr,
        "expected_normalized_question": proposal.canonical,
        "expected_entity_ids": [proposal.entity_id],
        "expected_intent": infer_intent(entity),
        "progress_gate": str(entity.get("progress_gate") or "start"),
        "spoiler_level": str(entity.get("spoiler_level") or "light"),
        "source_refs": [proposal.source_id],
        "notes": (
            f"Observed RG476H hotkey voice transcript for {proposal.canonical}; "
            f"evidence {evidence_note}."
        ),
    }


def unique_qa_id(pack_dir: Path, entity_id: str, observed_asr: str) -> str:
    prefix = infer_qa_prefix(pack_dir / "qa_goldens.jsonl")
    entity_slug = re.sub(r"[^a-z0-9]+", "-", entity_id.lower()).strip("-")
    digest = hashlib.sha1(observed_asr.encode("utf-8")).hexdigest()[:8]
    return f"qa.{prefix}.asr.{entity_slug}.{digest}.zh"


def infer_qa_prefix(path: Path) -> str:
    if not path.is_file():
        return "gkp"
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        row = json.loads(line)
        qa_id = str(row.get("qa_id") or "")
        match = re.match(r"qa\.([a-z0-9]+)\.", qa_id)
        if match:
            return match.group(1)
    return "gkp"


def infer_intent(entity: dict[str, Any]) -> str:
    templates = entity.get("answer_templates")
    if isinstance(templates, list):
        for template in templates:
            if isinstance(template, dict) and template.get("intent"):
                return str(template["intent"])
    entity_type = str(entity.get("entity_type") or "")
    if entity_type == "npc":
        return "name_mapping"
    if entity_type == "mechanic":
        return "mechanic"
    return "strategy"


def load_entity(knowledge_dir: Path, entity_id: str) -> dict[str, Any] | None:
    for path in sorted(knowledge_dir.glob("*.jsonl")):
        for line in path.read_text(encoding="utf-8").splitlines():
            if not line.strip():
                continue
            row = json.loads(line)
            if row.get("entity_id") == entity_id:
                return row
    return None


def alias_exists(path: Path, term: str, entity_id: str) -> bool:
    if not path.is_file():
        return False
    data = json.loads(path.read_text(encoding="utf-8"))
    aliases = data.get("aliases") if isinstance(data, dict) else []
    return any(row.get("term") == term and row.get("entity_id") == entity_id for row in aliases if isinstance(row, dict))


def qa_id_exists(path: Path, qa_id: str) -> bool:
    return any(row.get("qa_id") == qa_id for row in read_jsonl(path))


def question_exists(path: Path, question: str) -> bool:
    normalized = question.strip().rstrip("？?")
    return any(str(row.get("question") or "").strip().rstrip("？?") == normalized for row in read_jsonl(path))


def extract_evidence_source(backlog_path: Path) -> str | None:
    if not backlog_path.is_file():
        return None
    text = backlog_path.read_text(encoding="utf-8")
    match = re.search(r"- Input: `([^`]+)`", text)
    if match:
        value = match.group(1).strip()
        if value:
            if "results.tsv" in value:
                return value
            return value.rstrip("/") + "/results.tsv"
    match = re.search(r"\| `([^`]+/results\.tsv)` \|", text)
    if match:
        return match.group(1).strip()
    return None


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    if not path.is_file():
        return rows
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip():
            rows.append(json.loads(line))
    return rows


def empty_blocked(proposal, pack_dir: Path, detail: str) -> ReviewItem:
    return ReviewItem(
        pack_id=proposal.pack_id,
        pack_dir=display_path(pack_dir),
        alias_row={},
        golden_row={},
        status="blocked",
        detail=detail,
    )


def load_audit_module():
    spec = importlib.util.spec_from_file_location("gkp_patch_proposal_audit", AUDIT_SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def render_markdown(items: list[ReviewItem], proposals_path: Path, backlog_path: Path) -> str:
    counts = count_statuses(items)
    lines = [
        "# GKP Patch Review Packet - Hotkey Voice ASR Variants",
        "",
        "- dry_run=true",
        "- Assets edited: no",
        f"- Proposals: `{display_path(proposals_path)}`",
        f"- Backlog: `{display_path(backlog_path)}`",
        f"- Rows: {len(items)}",
        "- Status: " + ", ".join(f"{key}={counts.get(key, 0)}" for key in ["ready", "applied", "blocked"]),
        "",
        "## Review Summary",
        "",
        "| Pack | Status | Detail |",
        "|---|---|---|",
    ]
    for item in items:
        lines.append(f"| `{escape_cell(item.pack_id)}` | `{item.status}` | {escape_cell(item.detail)} |")
    for item in items:
        lines.extend(
            [
                "",
                f"## {item.pack_id}",
                "",
                f"- Pack dir: `{item.pack_dir}`",
                f"- Status: `{item.status}`",
                f"- Detail: {item.detail}",
                "",
                "### aliases.json row",
                "",
                "```json",
                json.dumps(item.alias_row, ensure_ascii=False, sort_keys=True) if item.alias_row else "{}",
                "```",
                "",
                "### qa_goldens.jsonl row",
                "",
                "```json",
                json.dumps(item.golden_row, ensure_ascii=False, sort_keys=True) if item.golden_row else "{}",
                "```",
            ]
        )
    lines.extend(
        [
            "",
            "## Approval Boundary",
            "",
            "Do not apply these rows until a human approves the exact JSON above. After approval, append only these alias/golden rows, run GKP lint/retrieval goldens, `scripts/rc_release_audit.py`, then rerun the failed hotkey voice rows.",
            "",
        ]
    )
    return "\n".join(lines)


def render_json(
    items: list[ReviewItem],
    proposals_path: Path,
    backlog_path: Path,
    gkp_root: Path,
    markdown_output_path: Path,
) -> dict[str, Any]:
    counts = count_statuses(items)
    if counts.get("blocked", 0):
        status = "blocked"
    elif items and counts.get("applied", 0) == len(items):
        status = "applied"
    elif items:
        status = "ready"
    else:
        status = "blocked"
    return {
        "schema_version": 1,
        "status": status,
        "dry_run": True,
        "assets_edited": False,
        "paths": {
            "markdown_output": display_path(markdown_output_path),
            "proposals": display_path(proposals_path),
            "backlog": display_path(backlog_path),
            "gkp_root": display_path(gkp_root),
        },
        "counts": {
            "rows": len(items),
            "ready": counts.get("ready", 0),
            "applied": counts.get("applied", 0),
            "blocked": counts.get("blocked", 0),
        },
        "review_rows": [
            {
                "pack_id": item.pack_id,
                "pack_dir": item.pack_dir,
                "status": item.status,
                "detail": item.detail,
                "alias_row": item.alias_row,
                "golden_row": item.golden_row,
            }
            for item in items
        ],
        "approval": {
            "required": True,
            "scope": "exact alias_row and golden_row values in review_rows",
        },
    }


def count_statuses(items: list[ReviewItem]) -> dict[str, int]:
    counts = {"ready": 0, "applied": 0, "blocked": 0}
    for item in items:
        counts[item.status] = counts.get(item.status, 0) + 1
    return counts


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
