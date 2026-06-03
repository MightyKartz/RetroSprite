#!/usr/bin/env python3
"""Create dry-run, rights-safe GKP patch proposals for M18 backlog items."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]

ROM_PATH_PATTERN = re.compile(r"(/storage/|\\Roms\\|/Roms/|\.sfc\b|\.smc\b|\.gba\b|\.gbc\b|\.md\b|\.bin\b)", re.I)
PATCH_FILE_PATTERN = re.compile(r"\.(ips|bps|ups|xdelta|ppf)\b", re.I)
FAN_TRANSLATION_PATTERN = re.compile(r"(fan translation|fan-translation|汉化补丁|汉化文本|网友汉化|完整汉化)", re.I)
SCRIPT_BLOCK_PATTERN = re.compile(r"(^|\n)\s*(speaker|角色|npc|dialogue|对白)\s*[:：]", re.I)
LONG_PROSE_LIMIT = 700


@dataclass(frozen=True)
class Proposal:
    pack_id: str
    game_id: str
    question: str
    tag: str
    source_id: str
    entity_id: str
    alias_term: str
    canonical_term: str
    knowledge_file: str
    existing_entity: bool


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pack", type=Path, required=True)
    parser.add_argument("--question", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--source-id", default="")
    parser.add_argument("--entity-id", default="")
    parser.add_argument("--observed-asr", default="")
    parser.add_argument("--canonical-term", default="")
    parser.add_argument("--answer", default="")
    parser.add_argument("--content", default="")
    args = parser.parse_args()

    try:
        proposal = build_proposal(
            pack=args.pack,
            question=args.question,
            tag=args.tag,
            source_id=args.source_id,
            entity_id=args.entity_id,
            observed_asr=args.observed_asr,
            canonical_term=args.canonical_term,
            answer=args.answer,
            content=args.content,
        )
    except ValueError as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1

    print(render_proposal(proposal))
    return 0


def build_proposal(
    pack: Path,
    question: str,
    tag: str,
    source_id: str,
    entity_id: str = "",
    observed_asr: str = "",
    canonical_term: str = "",
    answer: str = "",
    content: str = "",
) -> Proposal:
    if not pack.is_dir():
        raise ValueError(f"pack directory not found: {pack}")

    combined_content = "\n".join(part for part in (question, answer, content) if part)
    check_rights_safe_content(combined_content)

    manifest = load_json(pack / "manifest.json")
    citations = load_citation_ids(pack / "sources/citations.jsonl")
    pack_id = str(manifest.get("pack_id") or pack.name)
    game = manifest.get("game") if isinstance(manifest.get("game"), dict) else {}
    game_id = str(game.get("game_id") or "")

    if not source_id:
        raise ValueError("missing source_refs for factual claims: pass --source-id")
    if source_id not in citations:
        raise ValueError(f"unknown source id for pack {pack_id}: {source_id}")

    normalized_tag = tag.strip() or "coverage_gap"
    resolved_entity_id = entity_id.strip() or default_entity_id(normalized_tag, question)
    existing_entity = bool(entity_id.strip())
    resolved_alias = (observed_asr if normalized_tag == "asr_variant" and observed_asr else question)
    resolved_canonical = canonical_term.strip() or question.strip().rstrip("?.!？。")
    knowledge_file = default_knowledge_file(normalized_tag)

    return Proposal(
        pack_id=pack_id,
        game_id=game_id,
        question=question.strip(),
        tag=normalized_tag,
        source_id=source_id.strip(),
        entity_id=resolved_entity_id,
        alias_term=resolved_alias.strip().rstrip("?.!？。"),
        canonical_term=resolved_canonical,
        knowledge_file=knowledge_file,
        existing_entity=existing_entity,
    )


def check_rights_safe_content(text: str) -> None:
    if ROM_PATH_PATTERN.search(text):
        raise ValueError("rights check failed: ROM path or ROM filename appears in proposed content")
    if PATCH_FILE_PATTERN.search(text):
        raise ValueError("rights check failed: patch filename appears in proposed content")
    if FAN_TRANSLATION_PATTERN.search(text):
        raise ValueError("rights check failed: fan translation text/patch reference appears in proposed content")
    if SCRIPT_BLOCK_PATTERN.search(text):
        raise ValueError("rights check failed: dialogue/script block marker appears in proposed content")
    if len(text) > LONG_PROSE_LIMIT:
        raise ValueError("rights check failed: proposed prose is too long for a safe GKP summary")


def default_entity_id(tag: str, question: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", question.lower()).strip("-")
    if not slug:
        slug = "pending-gap"
    if tag == "asr_variant":
        return f"alias.{slug[:40]}"
    if tag == "translation_gap":
        return f"screen.{slug[:40]}"
    if tag == "ranking_gap":
        return f"retrieval.{slug[:40]}"
    return f"note.{slug[:40]}"


def default_knowledge_file(tag: str) -> str:
    if tag in {"alias_gap", "asr_variant"}:
        return "knowledge/entities.jsonl"
    if tag == "translation_gap":
        return "screen_translation_glossary"
    if tag == "spoiler_gate_gap":
        return "spoiler_graph.json"
    return "knowledge/strategies.jsonl"


def render_proposal(proposal: Proposal) -> str:
    alias_row = {
        "term": proposal.alias_term,
        "entity_id": proposal.entity_id,
        "weight": 0.9,
        "kind": "observed_asr" if proposal.tag == "asr_variant" else "display_alias",
        "source": "m18_gap_backlog",
    }
    if proposal.tag == "asr_variant":
        alias_row["canonical_term"] = proposal.canonical_term
        alias_row["notes"] = f"Observed hotkey voice transcript for {proposal.canonical_term}."
    knowledge_row = {
        "entity_id": proposal.entity_id,
        "entity_type": "note",
        "canonical_name": proposal.canonical_term,
        "language": "zh",
        "aliases": sorted({proposal.alias_term, proposal.canonical_term}),
        "description_short": "Add an original short RetroSprite summary here after human review.",
        "description_long": "Keep this original, concise, low-spoiler, and source-backed.",
        "progress_gate": "start",
        "spoiler_level": "light",
        "source_refs": [proposal.source_id],
        "confidence": "community",
        "answer_templates": [
            {
                "template_id": f"template.{proposal.entity_id}.zh",
                "language": "zh",
                "intent": "strategy",
                "question_patterns": [proposal.question],
                "answer": "Add an original short answer here after human review.",
                "source_refs": [proposal.source_id],
                "spoiler_level": "light",
            }
        ],
    }
    golden_row = {
        "qa_id": f"qa.{proposal.entity_id}.zh",
        "game_id": proposal.game_id,
        "language": "zh",
        "question": proposal.question,
        "expected_entity_ids": [proposal.entity_id],
        "expected_intent": "strategy",
        "progress_gate": "start",
        "spoiler_level": "light",
        "source_refs": [proposal.source_id],
        "notes": f"M18 {proposal.tag} regression.",
    }

    lines = [
            "# M18 GKP Patch Proposal",
            "",
            "- dry_run=true",
            f"- Pack: `{proposal.pack_id}`",
            f"- Game: `{proposal.game_id}`",
            f"- Tag: `{proposal.tag}`",
            f"- Source: `{proposal.source_id}`",
            "",
            "## Suggested aliases.json addition",
            "",
            "```json",
            json.dumps(alias_row, ensure_ascii=False, sort_keys=True),
            "```",
            "",
    ]
    if proposal.tag == "asr_variant" and proposal.existing_entity:
        lines.extend(
            [
                "## Suggested knowledge change",
                "",
                f"No new knowledge row is required if `{proposal.entity_id}` already exists. Review the existing row and add only the observed ASR alias plus regression golden.",
                "",
            ]
        )
    else:
        lines.extend(
            [
                f"## Suggested {proposal.knowledge_file} addition",
                "",
                "```json",
                json.dumps(knowledge_row, ensure_ascii=False, sort_keys=True),
                "```",
                "",
            ]
        )
    lines.extend(
        [
            "## Suggested qa_goldens.jsonl addition",
            "",
            "```json",
            json.dumps(golden_row, ensure_ascii=False, sort_keys=True),
            "```",
            "",
            "Apply nothing automatically. A human must replace placeholder summary/answer text, verify rights, then run GKP lint and retrieval goldens.",
            "",
        ]
    )
    return "\n".join(lines)


def load_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def load_citation_ids(path: Path) -> set[str]:
    ids: set[str] = set()
    if not path.is_file():
        return ids
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip():
            row = json.loads(line)
            source_id = row.get("source_id")
            if source_id:
                ids.add(str(source_id))
    return ids


if __name__ == "__main__":
    raise SystemExit(main())
