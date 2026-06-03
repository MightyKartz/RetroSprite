#!/usr/bin/env python3
"""Offline release-candidate audit for RetroSprite M17.

This script checks the parts of the release checklist that can be proven from
the repository alone: bundled GKP scope, source/license files, dangerous asset
types, BYOK model defaults, stale OCR routes, and accidental API keys.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GKP_ROOT = ROOT / "app/src/main/assets/gkp"

EXPECTED_GKP = {
    "chrono-trigger-snes-zh": "community.chrono-trigger-snes-zh",
    "final-fantasy-vi-snes-zh": "community.final-fantasy-vi-snes-zh",
    "golden-sun-gba-zh": "community.golden-sun-gba-zh",
    "langrisser-ii-md-zh": "community.langrisser-ii-md-zh",
    "phantasy-star-iv-md-zh": "community.phantasy-star-iv-md-zh",
    "shining-force-ii-md": "community.shining-force-ii-md",
}

RECOMMENDED_MODEL = "Qwen/Qwen3-VL-8B-Instruct"
ALLOWED_GKP_SUFFIXES = {".json", ".jsonl", ".md"}
SECRET_PATTERN = re.compile(r"\bsk-[A-Za-z0-9]{20,}\b")
STALE_RUNTIME_ROUTES = ("DeepSeek-OCR", "ML Kit")
MAX_GKP_TEXT_CHARS = 600
COPYRIGHT_RISK_TERMS = (
    "full script dump",
    "full dialogue dump",
    "fan translation text",
    "commercial guidebook",
    "完整脚本",
    "完整台词",
    "网友汉化文本",
    "商业攻略正文",
)


def main() -> int:
    errors: list[str] = []
    check_bundled_gkp_scope(errors)
    check_bundled_gkp_files(errors)
    check_no_accidental_api_keys(errors)
    check_screen_translation_defaults(errors)
    check_current_route_docs(errors)

    if errors:
        for error in errors:
            print(f"FAIL {error}", file=sys.stderr)
        return 1

    print("OK release audit: six GKP packs, licenses/citations, BYOK defaults, and stale routes checked")
    return 0


def check_bundled_gkp_scope(errors: list[str]) -> None:
    if not GKP_ROOT.is_dir():
        errors.append(f"missing GKP asset root: {GKP_ROOT}")
        return

    actual_dirs = sorted(path.name for path in GKP_ROOT.iterdir() if path.is_dir())
    expected_dirs = sorted(EXPECTED_GKP)
    if actual_dirs != expected_dirs:
        errors.append(f"bundled GKP dirs mismatch: expected {expected_dirs}, got {actual_dirs}")


def check_bundled_gkp_files(errors: list[str]) -> None:
    for pack_dir, expected_pack_id in EXPECTED_GKP.items():
        root = GKP_ROOT / pack_dir
        required_files = [
            "manifest.json",
            "aliases.json",
            "spoiler_graph.json",
            "qa_goldens.jsonl",
            "changelog.md",
            "sources/citations.jsonl",
            "sources/licenses.md",
        ]
        for relative in required_files:
            require_non_empty(root / relative, errors)

        manifest = load_json(root / "manifest.json", errors)
        if manifest:
            pack_id = manifest.get("pack_id")
            if pack_id != expected_pack_id:
                errors.append(f"{pack_dir}/manifest.json pack_id mismatch: expected {expected_pack_id}, got {pack_id}")
            for relative in manifest.get("contents", {}).get("knowledge", []):
                require_non_empty(root / relative, errors)

        citation_ids = load_citation_ids(root / "sources/citations.jsonl", errors)
        check_jsonl_sources(root, citation_ids, errors)
        check_gkp_text_boundaries(root, errors)
        check_gkp_asset_types(root, errors)
        check_license_boundary(root / "sources/licenses.md", errors)


def require_non_empty(path: Path, errors: list[str]) -> None:
    if not path.is_file():
        errors.append(f"missing required file: {relative(path)}")
    elif path.stat().st_size == 0:
        errors.append(f"empty required file: {relative(path)}")


def load_json(path: Path, errors: list[str]) -> dict | None:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:  # noqa: BLE001 - include path in audit failure
        errors.append(f"invalid JSON {relative(path)}: {exc}")
        return None


def load_citation_ids(path: Path, errors: list[str]) -> set[str]:
    ids: set[str] = set()
    if not path.is_file():
        return ids
    for index, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip():
            continue
        try:
            row = json.loads(line)
        except json.JSONDecodeError as exc:
            errors.append(f"invalid citation JSONL {relative(path)}:{index}: {exc}")
            continue
        source_id = row.get("source_id")
        if not source_id:
            errors.append(f"citation missing source_id: {relative(path)}:{index}")
        else:
            ids.add(source_id)
    if not ids:
        errors.append(f"no citations found: {relative(path)}")
    return ids


def check_jsonl_sources(root: Path, citation_ids: set[str], errors: list[str]) -> None:
    for path in sorted((root / "knowledge").glob("*.jsonl")):
        for index, row in read_jsonl(path, errors):
            source_refs = row.get("source_refs")
            if not source_refs:
                errors.append(f"knowledge row missing source_refs: {relative(path)}:{index}")
                continue
            for source_ref in source_refs:
                if source_ref not in citation_ids:
                    errors.append(f"unknown knowledge source_ref {source_ref}: {relative(path)}:{index}")

            for template in row.get("answer_templates", []) or []:
                for source_ref in template.get("source_refs", []) or []:
                    if source_ref not in citation_ids:
                        errors.append(f"unknown template source_ref {source_ref}: {relative(path)}:{index}")

    qa_path = root / "qa_goldens.jsonl"
    for index, row in read_jsonl(qa_path, errors):
        for source_ref in row.get("source_refs", []) or []:
            if source_ref not in citation_ids:
                errors.append(f"unknown QA source_ref {source_ref}: {relative(qa_path)}:{index}")


def read_jsonl(path: Path, errors: list[str]) -> list[tuple[int, dict]]:
    rows: list[tuple[int, dict]] = []
    if not path.is_file():
        return rows
    for index, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip():
            continue
        try:
            rows.append((index, json.loads(line)))
        except json.JSONDecodeError as exc:
            errors.append(f"invalid JSONL {relative(path)}:{index}: {exc}")
    return rows


def check_gkp_text_boundaries(root: Path, errors: list[str]) -> None:
    jsonl_paths = sorted((root / "knowledge").glob("*.jsonl"))
    jsonl_paths.append(root / "qa_goldens.jsonl")
    for path in jsonl_paths:
        for index, row in read_jsonl(path, errors):
            for field, value in iter_string_values(row):
                check_string_content_boundary(path, index, field, value, errors)


def iter_string_values(value, field: str = "$"):
    if isinstance(value, str):
        yield field, value
    elif isinstance(value, dict):
        for key, child in value.items():
            child_field = f"{field}.{key}" if field != "$" else str(key)
            yield from iter_string_values(child, child_field)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            yield from iter_string_values(child, f"{field}[{index}]")


def check_string_content_boundary(
    path: Path,
    index: int,
    field: str,
    value: str,
    errors: list[str],
) -> None:
    if len(value) > MAX_GKP_TEXT_CHARS:
        errors.append(
            f"possible long-form copied text in {relative(path)}:{index} field {field} "
            f"({len(value)} chars > {MAX_GKP_TEXT_CHARS})"
        )
    lower = value.lower()
    for term in COPYRIGHT_RISK_TERMS:
        if term.lower() in lower:
            errors.append(f"rights-risk term '{term}' in {relative(path)}:{index} field {field}")


def check_gkp_asset_types(root: Path, errors: list[str]) -> None:
    for path in root.rglob("*"):
        if path.is_file() and path.suffix.lower() not in ALLOWED_GKP_SUFFIXES:
            errors.append(f"disallowed bundled GKP file type: {relative(path)}")


def check_license_boundary(path: Path, errors: list[str]) -> None:
    if not path.is_file():
        return
    text = path.read_text(encoding="utf-8").lower()
    required_groups = {
        "rom boundary": ("rom",),
        "copy boundary": ("copy", "copied"),
        "long-form content boundary": ("walkthrough", "guide", "script", "table", "route"),
        "source-use boundary": ("linked-source", "factual", "references"),
    }
    missing = [
        name
        for name, options in required_groups.items()
        if not any(option in text for option in options)
    ]
    if missing:
        errors.append(f"license boundary text missing {missing}: {relative(path)}")


def check_no_accidental_api_keys(errors: list[str]) -> None:
    scan_roots = [
        ROOT / "README.md",
        ROOT / "docs",
        ROOT / "app/src/main",
        ROOT / "scripts",
    ]
    for path in iter_text_files(scan_roots):
        text = path.read_text(encoding="utf-8", errors="ignore")
        for match in SECRET_PATTERN.finditer(text):
            errors.append(f"possible bundled API key {match.group(0)[:8]}... in {relative(path)}")


def check_screen_translation_defaults(errors: list[str]) -> None:
    contracts = ROOT / "app/src/main/kotlin/com/retrosprite/app/ui/viewmodel/UiContracts.kt"
    text = contracts.read_text(encoding="utf-8")
    if f'RECOMMENDED_SCREEN_TRANSLATION_MODEL: String = "{RECOMMENDED_MODEL}"' not in text:
        errors.append("recommended screen translation model constant changed or missing")
    if "defaultModel = RECOMMENDED_SCREEN_TRANSLATION_MODEL" not in text:
        errors.append("screen translation providers are not using the recommended model constant")

    runtime_text = "\n".join(
        path.read_text(encoding="utf-8", errors="ignore")
        for path in iter_text_files([ROOT / "app/src/main"])
    )
    for stale in STALE_RUNTIME_ROUTES:
        if stale in runtime_text:
            errors.append(f"stale runtime OCR route still present in app/src/main: {stale}")


def check_current_route_docs(errors: list[str]) -> None:
    expected_docs = [
        ROOT / "README.md",
        ROOT / "docs/NEXT_IMPLEMENTATION_PLAN.md",
        ROOT / "docs/TEST_COVERAGE.md",
        ROOT / "docs/ARCHITECTURE_AND_PRODUCT_TIERS.md",
    ]
    for path in expected_docs:
        if not path.is_file():
            errors.append(f"missing current-route doc: {relative(path)}")

    readme = (ROOT / "README.md").read_text(encoding="utf-8")
    if "当前真实支持游戏：仅 6 个" not in readme:
        errors.append("README no longer states the six-game support boundary")
    if RECOMMENDED_MODEL not in readme:
        errors.append("README no longer documents the recommended screen translation model")
    if "App 不内置任何 API Key" not in readme:
        errors.append("README no longer states BYOK/no bundled API key")


def iter_text_files(paths: list[Path]):
    for root in paths:
        if root.is_file():
            if is_text_candidate(root):
                yield root
            continue
        for path in root.rglob("*"):
            if path.is_file() and is_text_candidate(path):
                yield path


def is_text_candidate(path: Path) -> bool:
    return path.suffix.lower() in {
        ".kt",
        ".kts",
        ".java",
        ".xml",
        ".json",
        ".jsonl",
        ".md",
        ".txt",
        ".toml",
        ".gradle",
        ".sh",
        ".py",
        ".tsv",
    }


def relative(path: Path) -> str:
    try:
        return str(path.relative_to(ROOT))
    except ValueError:
        return str(path)


if __name__ == "__main__":
    sys.exit(main())
