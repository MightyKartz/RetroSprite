#!/usr/bin/env python3
"""Generate a human-review packet for bundled GKP content-rights checks."""

from __future__ import annotations

import argparse
import importlib.util
import json
import sys
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_GKP_ROOT = ROOT / "app/src/main/assets/gkp"
DEFAULT_CHECKLIST = ROOT / "docs/RELEASE_CANDIDATE_CHECKLIST.md"
DEFAULT_OUTPUT = ROOT / "docs/qa-feedback/gkp-content-rights-manual-packet.md"
AUDIT_SCRIPT = ROOT / "scripts/rc_release_audit.py"


@dataclass(frozen=True)
class PackRightsSummary:
    pack_dir: str
    pack_id: str
    game_title: str
    knowledge_files: int
    knowledge_rows: int
    qa_goldens: int
    citations: int
    license_file: str


@dataclass(frozen=True)
class RightsPacket:
    machine_audit_status: str
    machine_audit_errors: list[str]
    human_checkbox_status: str
    packs: list[PackRightsSummary]
    knowledge_files: int
    license_files: int
    citation_files: int


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gkp-root", type=Path, default=DEFAULT_GKP_ROOT)
    parser.add_argument("--checklist", type=Path, default=DEFAULT_CHECKLIST)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    try:
        packet = build_packet(args.gkp_root, args.checklist)
        markdown = render_markdown(packet, args.gkp_root, args.checklist)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(markdown, encoding="utf-8")
    except ValueError as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1

    print(
        "OK GKP content rights manual packet: "
        f"audit={packet.machine_audit_status}, packs={len(packet.packs)}, "
        f"human_checkbox={packet.human_checkbox_status}"
    )
    return 0


def build_packet(gkp_root: Path, checklist_path: Path) -> RightsPacket:
    if not gkp_root.is_dir():
        raise ValueError(f"GKP root not found: {gkp_root}")
    packs = [summarize_pack(path) for path in sorted(path for path in gkp_root.iterdir() if path.is_dir())]
    if not packs:
        raise ValueError(f"no GKP packs found: {gkp_root}")
    machine_errors = run_machine_audit()
    return RightsPacket(
        machine_audit_status="pass" if not machine_errors else "fail",
        machine_audit_errors=machine_errors,
        human_checkbox_status=human_checkbox_status(checklist_path),
        packs=packs,
        knowledge_files=len(list(gkp_root.glob("*/knowledge/*.jsonl"))),
        license_files=len(list(gkp_root.glob("*/sources/licenses.md"))),
        citation_files=len(list(gkp_root.glob("*/sources/citations.jsonl"))),
    )


def summarize_pack(pack_dir: Path) -> PackRightsSummary:
    manifest_path = pack_dir / "manifest.json"
    if not manifest_path.is_file():
        raise ValueError(f"manifest missing: {pack_dir}")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    game = manifest.get("game") if isinstance(manifest.get("game"), dict) else {}
    knowledge_paths = sorted((pack_dir / "knowledge").glob("*.jsonl"))
    return PackRightsSummary(
        pack_dir=display_path(pack_dir),
        pack_id=str(manifest.get("pack_id") or ""),
        game_title=str(game.get("title") or game.get("game_id") or ""),
        knowledge_files=len(knowledge_paths),
        knowledge_rows=sum(count_jsonl_rows(path) for path in knowledge_paths),
        qa_goldens=count_jsonl_rows(pack_dir / "qa_goldens.jsonl"),
        citations=count_jsonl_rows(pack_dir / "sources/citations.jsonl"),
        license_file=display_path(pack_dir / "sources/licenses.md"),
    )


def run_machine_audit() -> list[str]:
    audit = load_audit_module()
    errors: list[str] = []
    audit.check_bundled_gkp_scope(errors)
    audit.check_bundled_gkp_files(errors)
    audit.check_no_accidental_api_keys(errors)
    audit.check_screen_translation_defaults(errors)
    audit.check_current_route_docs(errors)
    return errors


def human_checkbox_status(path: Path) -> str:
    text = path.read_text(encoding="utf-8") if path.is_file() else ""
    target = "Human spot-check confirms no commercial guidebook prose"
    for line in text.splitlines():
        if target in line:
            if line.startswith("- [x]"):
                return "checked"
            if line.startswith("- [ ]"):
                return "open"
    return "missing"


def render_markdown(packet: RightsPacket, gkp_root: Path, checklist_path: Path) -> str:
    lines = [
        "# GKP Content Rights Manual Review Packet",
        "",
        f"- GKP root: `{display_path(gkp_root)}`",
        f"- Release checklist: `{display_path(checklist_path)}`",
        f"- Machine audit: `{packet.machine_audit_status}`",
        f"- Human release checkbox: `{packet.human_checkbox_status}`",
        f"- Bundled packs: {len(packet.packs)}",
        f"- Knowledge files: {packet.knowledge_files}",
        f"- License files: {packet.license_files}",
        f"- Citation files: {packet.citation_files}",
        "",
    ]
    if packet.machine_audit_errors:
        lines.append("## Machine Audit Errors")
        lines.append("")
        for error in packet.machine_audit_errors:
            lines.append(f"- {error}")
        lines.append("")
    lines.extend(
        [
            "## Human Review Scope",
            "",
            "This packet is not legal advice and does not replace human release review. Use it to confirm the bundled GKP assets remain short, source-cited, rights-conscious data rather than copied long-form content.",
            "",
            "Reject the release if any bundled GKP contains:",
            "",
            "- ROM, BIOS, save, screenshot dump, patch file, executable file, or generated binary.",
            "- Commercial guidebook prose, long walkthrough copy, full route text, full table dump, or copied FAQ prose.",
            "- Full dialogue/script dumps or copied fan-translation text.",
            "- API keys, private paths, user personal data, or screenshots that reveal private data.",
            "- Unsourced factual claims in knowledge rows, answer templates, or QA goldens.",
            "",
            "## Pack Inventory",
            "",
            "| Pack | Game | Knowledge Files | Knowledge Rows | QA Goldens | Citations | License |",
            "|---|---|---:|---:|---:|---:|---|",
        ]
    )
    for pack in packet.packs:
        lines.append(
            f"| `{escape_cell(pack.pack_id)}` | {escape_cell(pack.game_title)} | "
            f"{pack.knowledge_files} | {pack.knowledge_rows} | {pack.qa_goldens} | "
            f"{pack.citations} | `{escape_cell(pack.license_file)}` |"
        )
    lines.extend(
        [
            "",
            "## Review Checklist",
            "",
            "- [ ] Run `python3 scripts/rc_release_audit.py` and confirm it passes.",
            "- [ ] Open each `sources/licenses.md` and confirm the no-ROM/no-copy/no-long-form-source boundary is stated.",
            "- [ ] Spot-check representative `knowledge/*.jsonl` rows from every pack for short original summaries, aliases, terms, metadata, and source refs only.",
            "- [ ] Spot-check `qa_goldens.jsonl` rows for short test questions and expected ids, not copied walkthrough/dialogue content.",
            "- [ ] Confirm `sources/citations.jsonl` contains citations only, not copied source prose.",
            "- [ ] Confirm no GKP directory contains ROMs, BIOS files, saves, patch files, screenshots, executables, or model/API credentials.",
            "- [ ] If all checks pass, preview the exact approval phrase with `scripts/m18_manual_gate_receipt_update.py --section content-rights-human-review`; do not hand-edit release checklist checkboxes directly.",
            "",
            "## Commands",
            "",
            "```bash",
            "python3 scripts/rc_release_audit.py",
            "find app/src/main/assets/gkp -path '*/knowledge/*.jsonl' -print",
            "find app/src/main/assets/gkp -path '*/sources/licenses.md' -print",
            "find app/src/main/assets/gkp -path '*/sources/citations.jsonl' -print",
            "```",
            "",
            "After the reviewer completes the spot-check, preview the receipt update first:",
            "",
            "```bash",
            "python3 scripts/m18_manual_gate_receipt_update.py \\",
            "  --section content-rights-human-review \\",
            "  --decision approved \\",
            "  --approval-phrase \"I confirm gkp content rights human spot check\" \\",
            "  --reviewer \"<reviewer>\" \\",
            "  --output /tmp/retrosprite-m18-content-rights-receipt-preview.json",
            "```",
            "",
            "After reviewing the preview, rerun the same command with `--apply`, then validate and plan guarded follow-ups:",
            "",
            "```bash",
            "python3 scripts/m18_manual_gate_receipt_check.py \\",
            "  --output docs/qa-feedback/m18-manual-gate-receipt-check.md \\",
            "  --json-output docs/qa-feedback/m18-manual-gate-receipt-check.json \\",
            "  --template-output docs/qa-feedback/m18-manual-gate-receipt-template.json",
            "python3 scripts/m18_manual_gate_receipt_plan.py \\",
            "  --output docs/qa-feedback/m18-manual-gate-receipt-plan.md \\",
            "  --json-output docs/qa-feedback/m18-manual-gate-receipt-plan.json",
            "```",
            "",
        ]
    )
    return "\n".join(lines)


def count_jsonl_rows(path: Path) -> int:
    if not path.is_file():
        return 0
    return sum(1 for line in path.read_text(encoding="utf-8").splitlines() if line.strip())


def load_audit_module():
    spec = importlib.util.spec_from_file_location("rc_release_audit", AUDIT_SCRIPT)
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


def escape_cell(value: str) -> str:
    return value.replace("|", "\\|").replace("\n", " ")


if __name__ == "__main__":
    raise SystemExit(main())
