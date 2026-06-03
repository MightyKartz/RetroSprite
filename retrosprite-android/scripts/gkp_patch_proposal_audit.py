#!/usr/bin/env python3
"""Audit dry-run GKP patch proposals against the current backlog and assets."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_PROPOSALS = ROOT / "docs/qa-feedback/gkp-patch-proposals-20260601-hotkey-voice.md"
DEFAULT_BACKLOG = ROOT / "docs/qa-feedback/gkp-quality-backlog.md"
DEFAULT_OUTPUT = ROOT / "docs/qa-feedback/gkp-patch-proposal-audit.md"
DEFAULT_GKP_ROOT = ROOT / "app/src/main/assets/gkp"


@dataclass(frozen=True)
class ProposalRow:
    pack_id: str
    observed_asr: str
    canonical: str
    entity_id: str
    source_id: str


@dataclass(frozen=True)
class ProposalAuditRow:
    proposal: ProposalRow
    pack_dir: str
    status: str
    detail: str


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--proposals", type=Path, default=DEFAULT_PROPOSALS)
    parser.add_argument("--backlog", type=Path, default=DEFAULT_BACKLOG)
    parser.add_argument("--gkp-root", type=Path, default=DEFAULT_GKP_ROOT)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--strict", action="store_true", help="Exit nonzero when any proposal does not pass.")
    args = parser.parse_args()

    try:
        rows = audit_proposals(args.proposals, args.backlog, args.gkp_root)
    except ValueError as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1

    markdown = render_markdown(rows, args.proposals, args.backlog)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(markdown, encoding="utf-8")
    counts = count_statuses(rows)
    print("OK GKP patch proposal audit: " + ", ".join(f"{key}={value}" for key, value in sorted(counts.items())))
    if args.strict and any(row.status != "pass" for row in rows):
        return 1
    return 0


def audit_proposals(proposals_path: Path, backlog_path: Path, gkp_root: Path) -> list[ProposalAuditRow]:
    proposals = load_proposal_rows(proposals_path)
    if not proposals:
        raise ValueError(f"no proposal rows found: {proposals_path}")
    backlog_text = read_required(backlog_path)
    pack_dirs = index_pack_dirs(gkp_root)
    return [audit_row(row, backlog_text, pack_dirs) for row in proposals]


def load_proposal_rows(path: Path) -> list[ProposalRow]:
    text = read_required(path)
    rows: list[ProposalRow] = []
    in_table = False
    for line in text.splitlines():
        if line.startswith("| Pack | Observed ASR | Canonical | Entity | Source |"):
            in_table = True
            continue
        if not in_table:
            continue
        if not line.startswith("|"):
            break
        cells = parse_markdown_row(line)
        if len(cells) != 5 or set(cells[0]) == {"-"}:
            continue
        rows.append(
            ProposalRow(
                pack_id=strip_code(cells[0]),
                observed_asr=strip_code(cells[1]),
                canonical=strip_code(cells[2]),
                entity_id=strip_code(cells[3]),
                source_id=strip_code(cells[4]),
            )
        )
    return rows


def audit_row(
    row: ProposalRow,
    backlog_text: str,
    pack_dirs: dict[str, Path],
) -> ProposalAuditRow:
    failures: list[str] = []
    pack_dir = pack_dirs.get(row.pack_id)
    if pack_dir is None:
        failures.append("pack_id not found in bundled manifests")
        return ProposalAuditRow(row, "-", "fail", "; ".join(failures))

    citation_ids = load_citation_ids(pack_dir / "sources/citations.jsonl")
    if row.source_id not in citation_ids:
        failures.append("source_id not found in citations")

    entity_ids = load_entity_ids(pack_dir / "knowledge")
    if row.entity_id not in entity_ids:
        failures.append("entity_id not found in knowledge rows")

    if not row.observed_asr or not row.canonical:
        failures.append("observed_asr/canonical is blank")

    if row.observed_asr and row.observed_asr not in backlog_text:
        failures.append("observed_asr not found in backlog details")
    if row.canonical and row.canonical not in backlog_text:
        failures.append("canonical term not found in backlog questions")

    status = "pass" if not failures else "fail"
    detail = "ready for human approval; alias/golden only" if not failures else "; ".join(failures)
    return ProposalAuditRow(row, display_path(pack_dir), status, detail)


def index_pack_dirs(gkp_root: Path) -> dict[str, Path]:
    if not gkp_root.is_dir():
        raise ValueError(f"GKP root not found: {gkp_root}")
    result: dict[str, Path] = {}
    for pack_dir in sorted(path for path in gkp_root.iterdir() if path.is_dir()):
        manifest = pack_dir / "manifest.json"
        if not manifest.is_file():
            continue
        row = json.loads(manifest.read_text(encoding="utf-8"))
        pack_id = row.get("pack_id")
        if pack_id:
            result[str(pack_id)] = pack_dir
    return result


def load_citation_ids(path: Path) -> set[str]:
    ids: set[str] = set()
    if not path.is_file():
        return ids
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        row = json.loads(line)
        source_id = row.get("source_id")
        if source_id:
            ids.add(str(source_id))
    return ids


def load_entity_ids(knowledge_dir: Path) -> set[str]:
    ids: set[str] = set()
    for path in sorted(knowledge_dir.glob("*.jsonl")):
        for line in path.read_text(encoding="utf-8").splitlines():
            if not line.strip():
                continue
            row = json.loads(line)
            entity_id = row.get("entity_id")
            if entity_id:
                ids.add(str(entity_id))
    return ids


def render_markdown(rows: list[ProposalAuditRow], proposals_path: Path, backlog_path: Path) -> str:
    counts = count_statuses(rows)
    lines = [
        "# GKP Patch Proposal Audit",
        "",
        f"- Proposals: `{display_path(proposals_path)}`",
        f"- Backlog: `{display_path(backlog_path)}`",
        f"- Rows: {len(rows)}",
        "- Status: " + ", ".join(f"{key}={counts.get(key, 0)}" for key in ["pass", "fail"]),
        "",
        "| Pack | Observed ASR | Canonical | Entity | Source | Pack Dir | Status | Detail |",
        "|---|---|---|---|---|---|---|---|",
    ]
    for row in rows:
        proposal = row.proposal
        lines.append(
            f"| `{escape_cell(proposal.pack_id)}` | {escape_cell(proposal.observed_asr)} | "
            f"{escape_cell(proposal.canonical)} | `{escape_cell(proposal.entity_id)}` | "
            f"`{escape_cell(proposal.source_id)}` | `{escape_cell(row.pack_dir)}` | "
            f"`{escape_cell(row.status)}` | {escape_cell(row.detail)} |"
        )
    return "\n".join(lines) + "\n"


def count_statuses(rows: list[ProposalAuditRow]) -> dict[str, int]:
    counts = {"pass": 0, "fail": 0}
    for row in rows:
        counts[row.status] = counts.get(row.status, 0) + 1
    return counts


def read_required(path: Path) -> str:
    if not path.is_file():
        raise ValueError(f"file not found: {path}")
    return path.read_text(encoding="utf-8")


def parse_markdown_row(line: str) -> list[str]:
    return [cell.strip() for cell in line.strip().strip("|").split("|")]


def strip_code(value: str) -> str:
    return re.sub(r"^`|`$", "", value.strip())


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
