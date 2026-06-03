#!/usr/bin/env python3
"""Guard bundled GKP assets against unapproved edits."""

from __future__ import annotations

import argparse
import importlib.util
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
APPLY_SCRIPT = ROOT / "scripts/gkp_patch_apply_review_packet.py"
DEFAULT_GKP_ROOT = ROOT / "app/src/main/assets/gkp"
DEFAULT_PACKET = ROOT / "docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md"
DEFAULT_APPLY_REPORT = ROOT / "docs/qa-feedback/gkp-patch-apply-result-20260601-hotkey-voice.md"
DEFAULT_OUTPUT = ROOT / "docs/qa-feedback/gkp-asset-mutation-guard.md"


@dataclass(frozen=True)
class ApplyReport:
    exists: bool
    mode: str
    assets_edited: int


@dataclass(frozen=True)
class GuardSummary:
    status: str
    mode: str
    dirty_paths: tuple[str, ...]
    expected_paths: tuple[str, ...]
    unexpected_paths: tuple[str, ...]
    apply_report: ApplyReport


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gkp-root", type=Path, default=DEFAULT_GKP_ROOT)
    parser.add_argument("--packet", type=Path, default=DEFAULT_PACKET)
    parser.add_argument("--apply-report", type=Path, default=DEFAULT_APPLY_REPORT)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--strict", action="store_true", help="Exit nonzero unless the guard status is pass.")
    args = parser.parse_args()

    try:
        summary = build_summary(args.gkp_root, args.packet, args.apply_report)
        markdown = render_markdown(summary, args.gkp_root, args.packet, args.apply_report)
    except ValueError as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(markdown, encoding="utf-8")
    print(
        "OK GKP asset mutation guard: "
        f"status={summary.status}, mode={summary.mode}, "
        f"dirty={len(summary.dirty_paths)}, unexpected={len(summary.unexpected_paths)}, "
        f"apply_report={'present' if summary.apply_report.exists else 'missing'}"
    )
    if args.strict and summary.status != "pass":
        return 1
    return 0


def build_summary(gkp_root: Path, packet: Path, apply_report: Path) -> GuardSummary:
    dirty_paths = tuple(sorted(current_dirty_paths(gkp_root)))
    expected_paths = tuple(sorted(expected_patch_paths(packet, gkp_root))) if packet.is_file() else ()
    report = parse_apply_report(apply_report)
    dirty_set = set(dirty_paths)
    expected_set = set(expected_paths)
    unexpected = tuple(sorted(dirty_set - expected_set))

    if not dirty_paths:
        status = "pass"
        mode = "clean"
    elif not report.exists or report.mode != "apply" or report.assets_edited <= 0:
        status = "fail"
        mode = "unapproved_dirty"
    elif unexpected:
        status = "fail"
        mode = "unexpected_dirty"
    else:
        status = "pass"
        mode = "approved_patch"

    return GuardSummary(
        status=status,
        mode=mode,
        dirty_paths=dirty_paths,
        expected_paths=expected_paths,
        unexpected_paths=unexpected,
        apply_report=report,
    )


def current_dirty_paths(gkp_root: Path) -> list[str]:
    if not gkp_root.exists():
        raise ValueError(f"GKP root not found: {gkp_root}")
    result = subprocess.run(
        ["git", "status", "--porcelain", "--", str(gkp_root.resolve())],
        cwd=ROOT,
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if result.returncode != 0:
        raise ValueError(f"git status failed: {result.stderr.strip()}")
    return parse_porcelain(result.stdout)


def parse_porcelain(output: str) -> list[str]:
    paths: list[str] = []
    for line in output.splitlines():
        if not line.strip():
            continue
        # Porcelain lines start with two status chars and a space. Renames use
        # "old -> new"; for guard purposes the destination is what matters.
        raw_path = line[3:] if len(line) > 3 else line
        path = raw_path.split(" -> ")[-1].strip()
        if path:
            paths.append(path)
    return paths


def expected_patch_paths(packet: Path, gkp_root: Path) -> set[str]:
    module = load_apply_module()
    rows = module.build_apply_rows(packet, gkp_root)
    paths: set[str] = set()
    for row in rows:
        paths.add(display_path(row.alias_path))
        paths.add(display_path(row.golden_path))
    return paths


def parse_apply_report(path: Path) -> ApplyReport:
    if not path.is_file():
        return ApplyReport(exists=False, mode="missing", assets_edited=0)
    text = path.read_text(encoding="utf-8", errors="ignore")
    mode_match = re.search(r"- Mode:\s*`([^`]+)`", text)
    assets_match = re.search(r"- Assets edited:\s*([^\n]+)", text)
    mode = mode_match.group(1) if mode_match else "unknown"
    assets_text = assets_match.group(1).strip() if assets_match else "0"
    if assets_text == "no":
        assets = 0
    else:
        number_match = re.search(r"\d+", assets_text)
        assets = int(number_match.group(0)) if number_match else 0
    return ApplyReport(exists=True, mode=mode, assets_edited=assets)


def render_markdown(
    summary: GuardSummary,
    gkp_root: Path,
    packet: Path,
    apply_report: Path,
) -> str:
    lines = [
        "# M18 GKP Asset Mutation Guard",
        "",
        f"- GKP root: `{display_path(gkp_root)}`",
        f"- Review packet: `{display_path(packet)}`",
        f"- Apply report: `{display_path(apply_report)}`",
        f"- Guard status: `{summary.status}`",
        f"- Mode: `{summary.mode}`",
        f"- Dirty GKP assets: {len(summary.dirty_paths)}",
        f"- Expected patch assets: {len(summary.expected_paths)}",
        f"- Unexpected dirty assets: {len(summary.unexpected_paths)}",
        f"- Apply report present: `{'yes' if summary.apply_report.exists else 'no'}`",
        f"- Apply report mode: `{summary.apply_report.mode}`",
        f"- Apply report assets edited: {summary.apply_report.assets_edited}",
        "- GKP assets edited by this guard: no",
        "",
        "| Path | Classification |",
        "|---|---|",
    ]
    if summary.dirty_paths:
        expected = set(summary.expected_paths)
        unexpected = set(summary.unexpected_paths)
        for path in summary.dirty_paths:
            if path in unexpected:
                classification = "unexpected_dirty"
            elif path in expected:
                classification = "expected_patch_dirty"
            else:
                classification = "dirty"
            lines.append(f"| `{escape_cell(path)}` | `{classification}` |")
    else:
        lines.append("| - | `clean` |")
    lines.extend(
        [
            "",
            "## Guard Rule",
            "",
            "Bundled GKP assets must stay clean until a human-approved patch apply report exists. "
            "After approval, dirty paths must be limited to the exact `aliases.json` and "
            "`qa_goldens.jsonl` paths described by the current review packet.",
        ]
    )
    return "\n".join(lines) + "\n"


def load_apply_module() -> Any:
    spec = importlib.util.spec_from_file_location("gkp_patch_apply_review_packet", APPLY_SCRIPT)
    if spec is None or spec.loader is None:
        raise ValueError(f"cannot load apply script: {APPLY_SCRIPT}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def display_path(path: Path) -> str:
    resolved = path.resolve()
    try:
        return resolved.relative_to(git_root()).as_posix()
    except ValueError:
        return str(path)


def git_root() -> Path:
    result = subprocess.run(
        ["git", "rev-parse", "--show-toplevel"],
        cwd=ROOT,
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if result.returncode != 0:
        return ROOT
    return Path(result.stdout.strip()).resolve()


def escape_cell(value: str) -> str:
    return value.replace("|", "\\|").replace("\n", " ")


if __name__ == "__main__":
    raise SystemExit(main())
