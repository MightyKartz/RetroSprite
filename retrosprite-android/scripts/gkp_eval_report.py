#!/usr/bin/env python3
"""Generate an offline M18 coverage report for bundled GKP packs."""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_GKP_DIR = ROOT / "app/src/main/assets/gkp"

LANES = [
    "identity",
    "core_gameplay",
    "first_hour",
    "mechanics",
    "menu_terms",
    "items_skills_magic",
    "names_aliases",
    "common_blockers",
    "low_spoiler_next_step",
    "no_evidence_boundary",
    "observed_asr_variants",
    "citations_and_licenses",
    "qa_goldens",
]


@dataclass(frozen=True)
class LaneResult:
    name: str
    status: str
    detail: str


@dataclass(frozen=True)
class PackReport:
    directory: str
    pack_id: str
    game_id: str
    row_count: int
    golden_count: int
    alias_count: int
    observed_asr_count: int
    asr_variant_count: int
    lanes: list[LaneResult]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gkp-dir", type=Path, default=DEFAULT_GKP_DIR)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--strict", action="store_true", help="exit non-zero when any lane is fail")
    args = parser.parse_args()

    try:
        reports = evaluate_gkp_dir(args.gkp_dir)
    except ValueError as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1

    markdown = render_markdown(reports, args.gkp_dir)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(markdown, encoding="utf-8")
    else:
        print(markdown)

    failing = [
        f"{report.directory}:{lane.name}:{lane.detail}"
        for report in reports
        for lane in report.lanes
        if lane.status == "fail"
    ]
    if failing:
        for failure in failing:
            print(f"WARN {failure}", file=sys.stderr)
        if args.strict:
            return 1

    print(f"OK GKP eval report: {len(reports)} packs")
    return 0


def evaluate_gkp_dir(gkp_dir: Path) -> list[PackReport]:
    gkp_dir = gkp_dir.resolve()
    if not gkp_dir.is_dir():
        raise ValueError(f"GKP directory not found: {gkp_dir}")

    reports = [
        evaluate_pack(path)
        for path in sorted(gkp_dir.iterdir())
        if path.is_dir()
    ]
    if not reports:
        raise ValueError(f"no GKP packs found in {gkp_dir}")
    return reports


def evaluate_pack(pack_dir: Path) -> PackReport:
    pack_dir = pack_dir.resolve()
    manifest_path = pack_dir / "manifest.json"
    aliases_path = pack_dir / "aliases.json"
    citations_path = pack_dir / "sources/citations.jsonl"
    license_path = pack_dir / "sources/licenses.md"
    qa_path = pack_dir / "qa_goldens.jsonl"

    manifest = load_json(manifest_path)
    aliases_data = load_json(aliases_path)
    aliases = aliases_data.get("aliases", []) if isinstance(aliases_data, dict) else []
    citation_rows = read_jsonl(citations_path)
    qa_rows = read_jsonl(qa_path)
    knowledge_rows = load_knowledge_rows(pack_dir, manifest)

    pack_id = str(manifest.get("pack_id") or pack_dir.name)
    game = manifest.get("game") if isinstance(manifest.get("game"), dict) else {}
    game_id = str(game.get("game_id") or "")

    alias_kinds = [str(row.get("kind") or "") for row in aliases if isinstance(row, dict)]
    observed_asr_count = sum(1 for kind in alias_kinds if kind == "observed_asr")
    asr_variant_count = sum(1 for kind in alias_kinds if kind in {"asr_variant", "observed_asr"})

    context = {
        "pack_dir": pack_dir,
        "manifest": manifest,
        "game": game,
        "aliases": aliases,
        "citations_path": citations_path,
        "citation_rows": citation_rows,
        "license_path": license_path,
        "qa_path": qa_path,
        "qa_rows": qa_rows,
        "knowledge_rows": knowledge_rows,
        "knowledge_text": "\n".join(json.dumps(row, ensure_ascii=False) for row in knowledge_rows),
        "knowledge_files": sorted(path.name for path in (pack_dir / "knowledge").glob("*.jsonl")),
        "entity_ids": {str(row.get("entity_id") or "") for row in knowledge_rows},
        "entity_types": {str(row.get("entity_type") or "") for row in knowledge_rows},
        "qa_intents": {str(row.get("expected_intent") or "") for row in qa_rows},
        "qa_ids": {str(row.get("qa_id") or "") for row in qa_rows},
        "observed_asr_count": observed_asr_count,
        "asr_variant_count": asr_variant_count,
    }

    return PackReport(
        directory=pack_dir.name,
        pack_id=pack_id,
        game_id=game_id,
        row_count=len(knowledge_rows),
        golden_count=len(qa_rows),
        alias_count=len(aliases),
        observed_asr_count=observed_asr_count,
        asr_variant_count=asr_variant_count,
        lanes=[evaluate_lane(name, context) for name in LANES],
    )


def evaluate_lane(name: str, context: dict[str, Any]) -> LaneResult:
    if name == "identity":
        required_missing = []
        warn_missing = []
        manifest = context["manifest"]
        game = context["game"]
        if not manifest.get("pack_id"):
            required_missing.append("manifest.json pack_id")
        if not game.get("game_id"):
            required_missing.append("manifest.json game.game_id")
        if not game.get("title"):
            required_missing.append("manifest.json game.title")
        if not game.get("retroarch_labels"):
            required_missing.append("manifest.json game.retroarch_labels")
        if not has_text(context, ("identity", "what is this game", "这是什么游戏", "是什么游戏", "游戏身份")):
            warn_missing.append("identity knowledge or QA wording")
        if required_missing:
            return lane(name, "fail", required_missing, "identity metadata present")
        if warn_missing:
            return lane(name, "warn", warn_missing, "identity metadata present")
        return LaneResult(name, "pass", "identity metadata and identity wording present")

    if name == "core_gameplay":
        found = has_entity_or_text(context, ("note.core-gameplay",), ("core gameplay", "核心玩法", "主要玩什么"))
        return presence_lane(name, found, "note.core-gameplay or core gameplay aliases", "core gameplay covered")

    if name == "first_hour":
        found = has_entity_or_text(context, ("strategy.beginner-direction",), ("first hour", "第一小时", "新手先", "开局"))
        return presence_lane(name, found, "first-hour / beginner-direction row", "first-hour direction covered")

    if name == "mechanics":
        found = "mechanic" in context["entity_types"] or "mechanics.jsonl" in context["knowledge_files"]
        return presence_lane(name, found, "mechanic rows or mechanics.jsonl", "mechanics rows present")

    if name == "menu_terms":
        found = has_entity_or_text(context, ("menu", "menu_term"), ("menu", "菜单", "status", "equipment", "装备"))
        return presence_lane(name, found, "menu/status/equipment terms", "menu/status/equipment terms covered")

    if name == "items_skills_magic":
        found = bool({"item", "skill", "magic", "technique", "spell"} & context["entity_types"])
        found = found or bool({"items.jsonl", "skills.jsonl", "magic.jsonl", "techniques.jsonl"} & set(context["knowledge_files"]))
        return presence_lane(name, found, "item/skill/magic rows", "items/skills/magic covered")

    if name == "names_aliases":
        alias_count = len(context["aliases"])
        found = alias_count > 0 and bool({"npc", "character", "unit", "commander"} & context["entity_types"])
        return presence_lane(name, found, "aliases plus character/npc/unit rows", f"{alias_count} aliases present")

    if name == "common_blockers":
        found = bool({"strategy", "boss", "enemy"} & context["entity_types"])
        found = found or has_text(context, ("卡住", "怎么打", "blocker", "boss"))
        return presence_lane(name, found, "strategy/boss/enemy blocker rows", "common blocker rows present")

    if name == "low_spoiler_next_step":
        found = any(str(row.get("spoiler_level") or "") in {"none", "light"} for row in context["knowledge_rows"])
        found = found and has_text(context, ("低剧透", "下一步", "route", "hint", "新手", "开局"))
        return presence_lane(name, found, "low-spoiler route/next-step rows", "low-spoiler guidance present")

    if name == "no_evidence_boundary":
        found = has_text(context, ("no_evidence", "lite-boundary", "边界", "不提供", "完整攻略"))
        found = found or any("no_evidence" in item for item in context["qa_intents"] | context["qa_ids"])
        return presence_lane(name, found, "no-evidence boundary golden or row", "no-evidence boundary present")

    if name == "observed_asr_variants":
        observed = context["observed_asr_count"]
        variants = context["asr_variant_count"]
        if observed > 0:
            return LaneResult(name, "pass", f"{observed} observed ASR aliases; {variants} ASR aliases total")
        if variants > 0:
            return LaneResult(name, "warn", f"0 observed ASR aliases; {variants} generated ASR aliases total")
        return LaneResult(name, "warn", "0 observed ASR aliases; 0 ASR aliases total")

    if name == "citations_and_licenses":
        missing = []
        if not context["citation_rows"]:
            missing.append(relative(context["citations_path"]))
        if not context["license_path"].is_file() or context["license_path"].stat().st_size == 0:
            missing.append(relative(context["license_path"]))
        return lane(name, "fail" if missing else "pass", missing, "citations and license file present")

    if name == "qa_goldens":
        missing = []
        weak = []
        if not context["qa_rows"]:
            missing.append(relative(context["qa_path"]))
        else:
            for index, row in enumerate(context["qa_rows"], start=1):
                for field in ("qa_id", "question"):
                    if not row.get(field):
                        missing.append(f"{relative(context['qa_path'])}:{index} missing {field}")
                        break
                if not any(field in row for field in ("expected_entity_ids", "expected_intent", "expected_answer_type", "expected_answer_contains")):
                    weak.append(f"{relative(context['qa_path'])}:{index} missing expected target")
        if missing:
            return lane(name, "fail", missing, f"{len(context['qa_rows'])} goldens present")
        if weak:
            return lane(name, "warn", weak, f"{len(context['qa_rows'])} goldens present")
        return LaneResult(name, "pass", f"{len(context['qa_rows'])} goldens present")

    return LaneResult(name, "fail", "unknown lane")


def lane(name: str, status: str, missing: list[str], ok_detail: str) -> LaneResult:
    if missing:
        return LaneResult(name, status, "missing " + ", ".join(missing))
    return LaneResult(name, status, ok_detail)


def presence_lane(name: str, found: bool, missing: str, ok_detail: str) -> LaneResult:
    if found:
        return LaneResult(name, "pass", ok_detail)
    return LaneResult(name, "warn", f"missing {missing}")


def has_entity_or_text(context: dict[str, Any], entity_ids: tuple[str, ...], needles: tuple[str, ...]) -> bool:
    if any(entity_id in context["entity_ids"] for entity_id in entity_ids):
        return True
    return has_text(context, needles)


def has_text(context: dict[str, Any], needles: tuple[str, ...]) -> bool:
    haystack = context["knowledge_text"].lower()
    alias_text = json.dumps(context["aliases"], ensure_ascii=False).lower()
    qa_text = json.dumps(context["qa_rows"], ensure_ascii=False).lower()
    combined = f"{haystack}\n{alias_text}\n{qa_text}"
    return any(needle.lower() in combined for needle in needles)


def load_knowledge_rows(pack_dir: Path, manifest: dict[str, Any]) -> list[dict[str, Any]]:
    files = manifest.get("contents", {}).get("knowledge", [])
    if not files:
        files = [
            str(path.relative_to(pack_dir))
            for path in sorted((pack_dir / "knowledge").glob("*.jsonl"))
        ]
    rows: list[dict[str, Any]] = []
    for relative in files:
        rows.extend(read_jsonl(pack_dir / relative))
    return rows


def load_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    if not path.is_file():
        return []
    rows: list[dict[str, Any]] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip():
            rows.append(json.loads(line))
    return rows


def relative(path: Path) -> str:
    try:
        return str(path.resolve().relative_to(ROOT))
    except ValueError:
        return str(path)


def render_markdown(reports: list[PackReport], gkp_dir: Path) -> str:
    lines = [
        "# M18 GKP Eval Report",
        "",
        f"- GKP root: `{gkp_dir}`",
        f"- Packs: {len(reports)}",
        "",
        "| Pack | Game | Rows | Goldens | Aliases | Observed ASR | Lane Summary |",
        "|---|---|---:|---:|---:|---:|---|",
    ]
    for report in reports:
        summary = ", ".join(
            f"{lane.name}={lane.status}"
            for lane in report.lanes
        )
        lines.append(
            f"| `{report.pack_id}` | `{report.game_id}` | {report.row_count} | "
            f"{report.golden_count} | {report.alias_count} | {report.observed_asr_count} | {summary} |"
        )

    for report in reports:
        lines.extend(["", f"## {report.pack_id}", ""])
        lines.append(f"- Directory: `{report.directory}`")
        lines.append(f"- Game ID: `{report.game_id}`")
        lines.append(f"- Knowledge rows: {report.row_count}")
        lines.append(f"- QA goldens: {report.golden_count}")
        lines.append(f"- Aliases: {report.alias_count}")
        lines.append(f"- Observed ASR aliases: {report.observed_asr_count}")
        lines.extend(["", "| Lane | Status | Detail |", "|---|---|---|"])
        for lane_result in report.lanes:
            lines.append(f"| `{lane_result.name}` | {lane_result.status} | {lane_result.detail} |")

    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    raise SystemExit(main())
