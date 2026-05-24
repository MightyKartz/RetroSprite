#!/usr/bin/env python3
"""Minimal GKP Lite scaffold generator.

This intentionally stays small and dependency-free. The broader RAG-Anything
builder can grow around the same templates later.
"""

from __future__ import annotations

import argparse
import json
import shutil
from datetime import datetime, timezone
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve()
BUILDER_ROOT = SCRIPT_PATH.parents[1]
TEMPLATE_ROOT = BUILDER_ROOT / "templates" / "gkp-lite"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(prog="gkp-builder")
    subparsers = parser.add_subparsers(dest="command", required=True)

    new_parser = subparsers.add_parser("new", help="Create a GKP Lite pack from the standard scaffold")
    new_parser.add_argument("--profile", required=True, choices=["lite"])
    new_parser.add_argument("--game-id", required=True)
    new_parser.add_argument("--pack-id", required=True)
    new_parser.add_argument("--game", required=True, dest="display_title")
    new_parser.add_argument("--platform", required=True)
    new_parser.add_argument("--language", required=True)
    new_parser.add_argument("--out", required=True, type=Path)
    new_parser.add_argument("--region", default="")
    new_parser.add_argument("--generated-at", default="")
    new_parser.add_argument("--force", action="store_true")
    return parser.parse_args()


def destination_for(template_path: Path, out_dir: Path) -> Path | None:
    rel = template_path.relative_to(TEMPLATE_ROOT)
    if rel.name == "profile.yaml":
        return None
    parts = list(rel.parts)
    name = parts[-1]
    name = name.replace(".template.jsonl", ".jsonl")
    name = name.replace(".template.json", ".json")
    name = name.replace(".template.md", ".md")
    parts[-1] = name
    return out_dir.joinpath(*parts)


def json_string(value: str) -> str:
    return json.dumps(value, ensure_ascii=False)[1:-1]


def render_text(text: str, replacements: dict[str, str]) -> str:
    for key, value in replacements.items():
        text = text.replace("{{" + key + "}}", value)
    return text


def build_replacements(args: argparse.Namespace) -> dict[str, str]:
    generated_at = args.generated_at or datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    game_slug = args.out.name
    display_title = args.display_title
    label = f"{args.platform}__{display_title}"

    replacements = {
        "game_slug": json_string(game_slug),
        "game_id": json_string(args.game_id),
        "pack_id": json_string(args.pack_id),
        "display_title": json_string(display_title),
        "platform": json_string(args.platform),
        "region": json_string(args.region),
        "language": json_string(args.language),
        "coverage_tier": "lite",
        "generated_at": json_string(generated_at),
        "generated_date": json_string(generated_at[:10]),
        "region_json": json.dumps(args.region or None, ensure_ascii=False),
        "retroarch_labels_json": json.dumps([label], ensure_ascii=False),
    }
    return replacements


def validate_output_dir(out_dir: Path, force: bool) -> None:
    if out_dir.exists() and any(out_dir.iterdir()):
        if not force:
            raise SystemExit(f"Output directory is not empty: {out_dir}. Use --force to replace generated files.")
        shutil.rmtree(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)


def create_pack(args: argparse.Namespace) -> None:
    if not TEMPLATE_ROOT.exists():
        raise SystemExit(f"Template root not found: {TEMPLATE_ROOT}")

    out_dir = args.out.resolve()
    validate_output_dir(out_dir, args.force)
    replacements = build_replacements(args)

    for template_path in sorted(TEMPLATE_ROOT.rglob("*")):
        if not template_path.is_file():
            continue
        dest = destination_for(template_path, out_dir)
        if dest is None:
            continue
        dest.parent.mkdir(parents=True, exist_ok=True)
        text = template_path.read_text(encoding="utf-8")
        dest.write_text(render_text(text, replacements), encoding="utf-8")

    print(f"Generated GKP Lite scaffold at {out_dir}")


def main() -> None:
    args = parse_args()
    if args.command == "new":
        create_pack(args)


if __name__ == "__main__":
    main()
