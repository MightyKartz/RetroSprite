#!/usr/bin/env python3
"""Profile-aware coverage checks for reviewed GKP Lite packs.

This is developer-side tooling. It intentionally stays dependency-free so it can
run on a clean macOS/CI Python without PyYAML.
"""

from __future__ import annotations

import argparse
import copy
import json
import re
import sys
from pathlib import Path
from typing import Any


SCRIPT_PATH = Path(__file__).resolve()
BUILDER_ROOT = SCRIPT_PATH.parents[1]
DEFAULT_PROFILE_PATH = BUILDER_ROOT / "templates" / "gkp-lite" / "profile.yaml"
PROPER_NAME_ENTITY_TYPES = {"npc", "item", "location", "boss", "enemy"}
CJK = re.compile(r"[\u3400-\u9fff]")
ASCII_LETTER = re.compile(r"[A-Za-z]")
PROFILE_PRESETS: dict[str, dict[str, Any]] = {
    "lite": {
        "profile_id": "gkp-lite",
        "coverage_tier": "lite",
        "minimums": {
            "knowledge_rows_min": 20,
            "golden_rows_min": 20,
            "source_rows_min": 5,
            "localized_aliases_min": 40,
            "source_backed_localized_aliases_min": 25,
            "localized_goldens_min": 20,
            "pure_localized_goldens_min": 12,
            "core_gameplay_goldens_min": 4,
            "no_evidence_goldens_min": 3,
        },
        "recommended_maximums": {
            "knowledge_rows_max": 60,
            "golden_rows_max": 40,
        },
    },
    "expanded": {
        "profile_id": "gkp-expanded",
        "coverage_tier": "expanded",
        "minimums": {
            "knowledge_rows_min": 80,
            "golden_rows_min": 80,
            "source_rows_min": 8,
            "localized_aliases_min": 80,
            "source_backed_localized_aliases_min": 50,
            "localized_goldens_min": 60,
            "pure_localized_goldens_min": 40,
            "core_gameplay_goldens_min": 4,
            "no_evidence_goldens_min": 2,
        },
        "recommended_maximums": {
            "knowledge_rows_max": 240,
            "golden_rows_max": 180,
        },
    },
    "deep": {
        "profile_id": "gkp-deep",
        "coverage_tier": "deep",
        "minimums": {
            "knowledge_rows_min": 150,
            "golden_rows_min": 100,
            "source_rows_min": 12,
            "localized_aliases_min": 120,
            "source_backed_localized_aliases_min": 80,
            "localized_goldens_min": 90,
            "pure_localized_goldens_min": 60,
            "core_gameplay_goldens_min": 4,
            "no_evidence_goldens_min": 3,
        },
        "recommended_maximums": {},
    },
}


def load_profile(path: Path = DEFAULT_PROFILE_PATH) -> dict[str, Any]:
    if str(path) in PROFILE_PRESETS:
        return copy.deepcopy(PROFILE_PRESETS[str(path)])
    text = path.read_text(encoding="utf-8")
    profile: dict[str, Any] = {"minimums": {}, "recommended_maximums": {}}
    section: str | None = None
    for raw_line in text.splitlines():
        line = raw_line.split("#", 1)[0].rstrip()
        if not line.strip():
            continue
        if not raw_line.startswith(" ") and line.endswith(":"):
            section = line.strip()[:-1]
            if section not in profile:
                profile[section] = [] if section.endswith("lanes") else {}
            continue
        if not raw_line.startswith(" ") and ":" in line:
            key, value = line.split(":", 1)
            profile[key.strip()] = parse_scalar(value.strip())
            section = None
            continue
        if section in {"minimums", "recommended_maximums"} and ":" in line:
            key, value = line.strip().split(":", 1)
            profile[section][key.strip()] = int(value.strip())
            continue
        if section and line.strip().startswith("- "):
            profile.setdefault(section, []).append(line.strip()[2:].strip())
    return profile


def parse_scalar(value: str) -> Any:
    if value.startswith('"') and value.endswith('"'):
        return value[1:-1]
    if value in {"true", "false"}:
        return value == "true"
    if value.isdigit():
        return int(value)
    return value


def resolve_profile(profile_path: Path | str, coverage_tier: Any) -> dict[str, Any]:
    if str(profile_path) in PROFILE_PRESETS:
        return load_profile(Path(str(profile_path)))
    path = Path(profile_path)
    if path == DEFAULT_PROFILE_PATH and isinstance(coverage_tier, str) and coverage_tier in PROFILE_PRESETS:
        return load_profile(Path(coverage_tier))
    return load_profile(path)


def evaluate_pack(pack_dir: Path | str, profile_path: Path | str = DEFAULT_PROFILE_PATH) -> dict[str, Any]:
    pack_dir = Path(pack_dir)
    manifest = read_json(pack_dir / "manifest.json")
    profile = resolve_profile(profile_path, manifest.get("coverage_tier"))
    minimums: dict[str, int] = profile["minimums"]
    recommended_maximums: dict[str, int] = profile.get("recommended_maximums", {})
    placeholder_marker = str(profile.get("placeholder_marker", "__REPLACE_WITH_REVIEWED_GKP_DATA__"))

    contents = manifest.get("contents", {})
    knowledge_paths = contents.get("knowledge", [])
    knowledge = [
        {**row, "_path": relative}
        for relative in knowledge_paths
        for row in read_jsonl(pack_dir / relative)
    ]
    sources = read_jsonl(pack_dir / contents.get("citations", "sources/citations.jsonl"))
    aliases = read_aliases(pack_dir / contents.get("aliases", "aliases.json"))
    goldens = read_jsonl(pack_dir / contents.get("qa_goldens", "qa_goldens.jsonl"))

    default_language = manifest.get("default_language") or first_language(manifest)
    entity_types = {row.get("entity_id"): row.get("entity_type") for row in knowledge}
    source_backed_entities = {
        row.get("entity_id")
        for row in knowledge
        if row.get("entity_id") and row.get("source_refs")
    }

    metrics = {
        "knowledge_rows": len(knowledge),
        "golden_rows": len(goldens),
        "source_rows": len(sources),
        "localized_aliases": count_localized_aliases(aliases),
        "source_backed_localized_aliases": count_source_backed_localized_aliases(
            aliases,
            entity_types,
            source_backed_entities,
        ),
        "localized_goldens": sum(1 for qa in goldens if qa.get("language") == default_language),
        "pure_localized_goldens": count_pure_localized_goldens(goldens),
        "core_gameplay_goldens": count_core_gameplay_goldens(goldens),
        "no_evidence_goldens": count_no_evidence_goldens(goldens),
    }

    checks: list[dict[str, Any]] = []
    add_equals_check(checks, "coverage_tier", manifest.get("coverage_tier"), profile.get("coverage_tier"))
    add_range_check(
        checks,
        metric="knowledge_rows",
        actual=metrics["knowledge_rows"],
        minimum=minimums["knowledge_rows_min"],
    )
    add_recommended_max_check(
        checks,
        "knowledge_rows",
        metrics["knowledge_rows"],
        recommended_maximums.get("knowledge_rows_max"),
    )
    add_range_check(
        checks,
        metric="golden_rows",
        actual=metrics["golden_rows"],
        minimum=minimums["golden_rows_min"],
    )
    add_recommended_max_check(
        checks,
        "golden_rows",
        metrics["golden_rows"],
        recommended_maximums.get("golden_rows_max"),
    )
    add_min_check(checks, "source_rows", metrics["source_rows"], minimums["source_rows_min"])
    add_min_check(checks, "localized_aliases", metrics["localized_aliases"], minimums["localized_aliases_min"])
    add_min_check(
        checks,
        "source_backed_localized_aliases",
        metrics["source_backed_localized_aliases"],
        minimums["source_backed_localized_aliases_min"],
    )
    add_min_check(checks, "localized_goldens", metrics["localized_goldens"], minimums["localized_goldens_min"])
    add_min_check(
        checks,
        "pure_localized_goldens",
        metrics["pure_localized_goldens"],
        minimums["pure_localized_goldens_min"],
    )
    add_min_check(
        checks,
        "core_gameplay_goldens",
        metrics["core_gameplay_goldens"],
        minimums["core_gameplay_goldens_min"],
    )
    add_min_check(
        checks,
        "no_evidence_goldens",
        metrics["no_evidence_goldens"],
        minimums["no_evidence_goldens_min"],
    )
    add_boolean_check(
        checks,
        code="placeholder_marker",
        pass_value=not pack_contains(pack_dir, placeholder_marker),
        message=f"pack must not contain {placeholder_marker}",
    )

    failed_checks = [check for check in checks if not check["pass"] and check.get("severity", "error") == "error"]
    warning_checks = [check for check in checks if not check["pass"] and check.get("severity") == "warning"]
    return {
        "ok": not failed_checks,
        "pack_dir": str(pack_dir),
        "pack_id": manifest.get("pack_id"),
        "game_id": manifest.get("game", {}).get("game_id"),
        "coverage_tier": manifest.get("coverage_tier"),
        "profile_id": profile.get("profile_id"),
        "metrics": metrics,
        "checks": checks,
        "failed_checks": failed_checks,
        "warning_checks": warning_checks,
    }


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    rows: list[dict[str, Any]] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line:
            rows.append(json.loads(line))
    return rows


def read_aliases(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    raw = read_json(path)
    aliases = raw.get("aliases", [])
    if isinstance(aliases, list):
        return [alias for alias in aliases if isinstance(alias, dict)]
    return []


def first_language(manifest: dict[str, Any]) -> str | None:
    languages = manifest.get("game", {}).get("languages", [])
    return languages[0] if languages else None


def has_cjk(value: str) -> bool:
    return bool(CJK.search(value))


def count_localized_aliases(aliases: list[dict[str, Any]]) -> int:
    return sum(1 for alias in aliases if has_cjk(str(alias.get("term", ""))))


def count_source_backed_localized_aliases(
    aliases: list[dict[str, Any]],
    entity_types: dict[str | None, str | None],
    source_backed_entities: set[str | None],
) -> int:
    count = 0
    for alias in aliases:
        term = str(alias.get("term", ""))
        entity_id = alias.get("entity_id")
        if (
            has_cjk(term)
            and entity_id in source_backed_entities
            and entity_types.get(entity_id) in PROPER_NAME_ENTITY_TYPES
        ):
            count += 1
    return count


def count_pure_localized_goldens(goldens: list[dict[str, Any]]) -> int:
    count = 0
    for golden in goldens:
        question = str(golden.get("question", ""))
        if has_cjk(question) and not ASCII_LETTER.search(question):
            count += 1
    return count


def count_core_gameplay_goldens(goldens: list[dict[str, Any]]) -> int:
    cues = ("主要玩什么", "好玩", "核心玩法", "适合")
    count = 0
    for golden in goldens:
        question = str(golden.get("question", ""))
        expected = golden.get("expected_entity_ids", [])
        if "note.core-gameplay" in expected or any(cue in question for cue in cues):
            count += 1
    return count


def count_no_evidence_goldens(goldens: list[dict[str, Any]]) -> int:
    cues = ("不确定", "会剧透", "直接告诉")
    count = 0
    for golden in goldens:
        question = str(golden.get("question", ""))
        expected = golden.get("expected_entity_ids", [])
        if not expected or any(cue in question for cue in cues):
            count += 1
    return count


def pack_contains(pack_dir: Path, marker: str) -> bool:
    for path in pack_dir.rglob("*"):
        if path.is_file() and marker in path.read_text(encoding="utf-8"):
            return True
    return False


def add_equals_check(checks: list[dict[str, Any]], metric: str, actual: Any, expected: Any) -> None:
    checks.append(
        {
            "code": metric,
            "metric": metric,
            "actual": actual,
            "expected": expected,
            "pass": actual == expected,
            "severity": "error",
        }
    )


def add_range_check(
    checks: list[dict[str, Any]],
    metric: str,
    actual: int,
    minimum: int,
) -> None:
    checks.append(
        {
            "code": f"{metric}_min",
            "metric": metric,
            "actual": actual,
            "expected_min": minimum,
            "pass": actual >= minimum,
            "severity": "error",
        }
    )


def add_recommended_max_check(
    checks: list[dict[str, Any]],
    metric: str,
    actual: int,
    maximum: int | None,
) -> None:
    if maximum is None:
        return
    checks.append(
        {
            "code": f"{metric}_max",
            "metric": metric,
            "actual": actual,
            "expected_max": maximum,
            "pass": actual <= maximum,
            "severity": "warning",
        }
    )


def add_min_check(checks: list[dict[str, Any]], metric: str, actual: int, minimum: int) -> None:
    checks.append(
        {
            "code": f"{metric}_min",
            "metric": metric,
            "actual": actual,
            "expected_min": minimum,
            "pass": actual >= minimum,
            "severity": "error",
        }
    )


def add_boolean_check(checks: list[dict[str, Any]], code: str, pass_value: bool, message: str) -> None:
    checks.append(
        {
            "code": code,
            "message": message,
            "pass": pass_value,
            "severity": "error",
        }
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Check a GKP pack against a coverage profile.")
    parser.add_argument("pack_dir", type=Path)
    parser.add_argument("--profile", type=Path, default=DEFAULT_PROFILE_PATH)
    parser.add_argument("--json", action="store_true", help="Emit machine-readable JSON.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    report = evaluate_pack(args.pack_dir, args.profile)
    if args.json:
        print(json.dumps(report, ensure_ascii=False, indent=2))
    else:
        status = "PASS" if report["ok"] else "FAIL"
        print(f"{status} {report['pack_id']} ({report['coverage_tier']})")
        for check in report["failed_checks"]:
            print(json.dumps(check, ensure_ascii=False))
        for check in report.get("warning_checks", []):
            print("WARNING " + json.dumps(check, ensure_ascii=False))
    return 0 if report["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
