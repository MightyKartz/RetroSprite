#!/usr/bin/env python3
"""Guard the final M17/M18 release checklist checkboxes."""

from __future__ import annotations

import argparse
import importlib.util
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
STATUS_SCRIPT = ROOT / "scripts/m18_status_report.py"
DEFAULT_CHECKLIST = ROOT / "docs/RELEASE_CANDIDATE_CHECKLIST.md"
DEFAULT_HOTKEY_VOICE_REPORT = ROOT / "docs/qa-feedback/hotkey-voice-matrix-report.md"
DEFAULT_SCREEN_REPORT = ROOT / "docs/qa-feedback/screen-translation-eval-report.md"
DEFAULT_CONTENT_RIGHTS_PACKET = ROOT / "docs/qa-feedback/gkp-content-rights-manual-packet.md"
DEFAULT_OUTPUT = ROOT / "docs/qa-feedback/m18-release-checklist-guard.md"

CONTENT_RIGHTS_APPROVAL = "I confirm gkp content rights human spot check"

HOTKEY_ITEM = "Hotkey voice matrix real playback passes on a connected test device."
SCREEN_ITEM = (
    "Screen translation matrix covers dialogue, menu, status, equipment, "
    "numbers, English leakage, and 10-second paging."
)
RIGHTS_ITEM = (
    "Human spot-check confirms no commercial guidebook prose, long walkthrough copy, "
    "full script dump, or copied fan translation is bundled."
)


@dataclass(frozen=True)
class ChecklistState:
    label: str
    checked: bool | None

    @property
    def status(self) -> str:
        if self.checked is None:
            return "missing"
        return "checked" if self.checked else "open"


@dataclass(frozen=True)
class GuardItem:
    key: str
    label: str
    checklist_state: str
    evidence_status: str
    evidence_detail: str
    ready_to_check: bool
    unsafe_checked: bool
    action: str


@dataclass(frozen=True)
class GuardSummary:
    items: tuple[GuardItem, ...]
    approval_present: bool
    applied: bool

    @property
    def unsafe_count(self) -> int:
        return sum(1 for item in self.items if item.unsafe_checked)

    @property
    def ready_count(self) -> int:
        return sum(1 for item in self.items if item.ready_to_check)

    @property
    def guard_status(self) -> str:
        return "pass" if self.unsafe_count == 0 else "fail"

    @property
    def closure_status(self) -> str:
        if self.guard_status != "pass":
            return "fail"
        return "pass" if all(item.checklist_state == "checked" for item in self.items) else "open"

    @property
    def apply_allowed(self) -> bool:
        return self.approval_present and all(item.ready_to_check for item in self.items)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checklist", type=Path, default=DEFAULT_CHECKLIST)
    parser.add_argument("--hotkey-voice-report", type=Path, default=DEFAULT_HOTKEY_VOICE_REPORT)
    parser.add_argument("--screen-report", type=Path, default=DEFAULT_SCREEN_REPORT)
    parser.add_argument("--content-rights-packet", type=Path, default=DEFAULT_CONTENT_RIGHTS_PACKET)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--apply", action="store_true", help="Check the three guarded release checklist items.")
    parser.add_argument(
        "--content-rights-approval",
        default="",
        help=f"Required exact phrase for apply mode: {CONTENT_RIGHTS_APPROVAL!r}",
    )
    parser.add_argument("--strict", action="store_true", help="Exit nonzero unless closure status is pass.")
    args = parser.parse_args()

    try:
        summary = build_summary(
            checklist_path=args.checklist,
            hotkey_voice_report=args.hotkey_voice_report,
            screen_report=args.screen_report,
            content_rights_packet=args.content_rights_packet,
            approval=args.content_rights_approval,
            applied=False,
        )
        if args.apply:
            if not summary.apply_allowed:
                raise ValueError(apply_block_reason(summary))
            apply_checklist(args.checklist, [HOTKEY_ITEM, SCREEN_ITEM, RIGHTS_ITEM])
            summary = build_summary(
                checklist_path=args.checklist,
                hotkey_voice_report=args.hotkey_voice_report,
                screen_report=args.screen_report,
                content_rights_packet=args.content_rights_packet,
                approval=args.content_rights_approval,
                applied=True,
            )
        markdown = render_markdown(
            summary,
            checklist_path=args.checklist,
            hotkey_voice_report=args.hotkey_voice_report,
            screen_report=args.screen_report,
            content_rights_packet=args.content_rights_packet,
        )
    except ValueError as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(markdown, encoding="utf-8")
    print(
        "OK M18 release checklist guard: "
        f"guard={summary.guard_status}, closure={summary.closure_status}, "
        f"ready={summary.ready_count}/{len(summary.items)}, unsafe={summary.unsafe_count}, "
        f"apply_allowed={'yes' if summary.apply_allowed else 'no'}, applied={'yes' if summary.applied else 'no'}"
    )
    if args.strict and summary.closure_status != "pass":
        return 1
    return 0


def build_summary(
    *,
    checklist_path: Path,
    hotkey_voice_report: Path,
    screen_report: Path,
    content_rights_packet: Path,
    approval: str,
    applied: bool,
) -> GuardSummary:
    checklist = load_checklist(checklist_path)
    status = load_status_module()
    voice = status.summarize_hotkey_voice_matrix(hotkey_voice_report)
    screen = status.summarize_screen_translation(screen_report)
    rights = status.summarize_content_rights_packet(content_rights_packet)
    approval_present = approval == CONTENT_RIGHTS_APPROVAL
    items = (
        build_item(
            key="hotkey_voice",
            label=HOTKEY_ITEM,
            state=checklist.get(HOTKEY_ITEM, ChecklistState(HOTKEY_ITEM, None)),
            evidence_status=voice.status,
            evidence_detail=voice.detail,
            approval_required=False,
            approval_present=approval_present,
        ),
        build_item(
            key="screen_translation",
            label=SCREEN_ITEM,
            state=checklist.get(SCREEN_ITEM, ChecklistState(SCREEN_ITEM, None)),
            evidence_status=screen.status,
            evidence_detail=screen.detail,
            approval_required=False,
            approval_present=approval_present,
        ),
        build_item(
            key="content_rights",
            label=RIGHTS_ITEM,
            state=checklist.get(RIGHTS_ITEM, ChecklistState(RIGHTS_ITEM, None)),
            evidence_status=rights.status,
            evidence_detail=rights.detail,
            approval_required=True,
            approval_present=approval_present,
        ),
    )
    return GuardSummary(items=items, approval_present=approval_present, applied=applied)


def build_item(
    *,
    key: str,
    label: str,
    state: ChecklistState,
    evidence_status: str,
    evidence_detail: str,
    approval_required: bool,
    approval_present: bool,
) -> GuardItem:
    evidence_ready = evidence_status == "pass"
    approval_ready = approval_present or not approval_required
    ready_to_check = evidence_ready and approval_ready
    unsafe_checked = state.checked is True and not ready_to_check
    if unsafe_checked and not evidence_ready:
        action = "uncheck until evidence passes"
    elif unsafe_checked and approval_required and not approval_present:
        action = "uncheck until human approval phrase is supplied"
    elif state.checked is True:
        action = "already checked"
    elif ready_to_check:
        action = "ready to check via guarded apply"
    elif evidence_ready and approval_required:
        action = "waiting for human approval phrase"
    else:
        action = "keep unchecked"
    return GuardItem(
        key=key,
        label=label,
        checklist_state=state.status,
        evidence_status=evidence_status,
        evidence_detail=evidence_detail,
        ready_to_check=ready_to_check,
        unsafe_checked=unsafe_checked,
        action=action,
    )


def load_checklist(path: Path) -> dict[str, ChecklistState]:
    if not path.is_file():
        raise ValueError(f"release checklist not found: {path}")
    states: dict[str, ChecklistState] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        match = re.match(r"^- \[([ xX])\]\s+(.+)$", line)
        if not match:
            continue
        checked = match.group(1).lower() == "x"
        label = match.group(2).strip()
        states[label] = ChecklistState(label=label, checked=checked)
    return states


def apply_checklist(path: Path, labels: list[str]) -> None:
    text = path.read_text(encoding="utf-8")
    for label in labels:
        open_line = f"- [ ] {label}"
        checked_line = f"- [x] {label}"
        if checked_line in text:
            continue
        if open_line not in text:
            raise ValueError(f"checklist item not found for apply: {label}")
        text = text.replace(open_line, checked_line, 1)
    path.write_text(text, encoding="utf-8")


def apply_block_reason(summary: GuardSummary) -> str:
    blockers: list[str] = []
    if not summary.approval_present:
        blockers.append(f"missing exact content-rights approval phrase: {CONTENT_RIGHTS_APPROVAL}")
    for item in summary.items:
        if not item.ready_to_check:
            blockers.append(f"{item.key} not ready ({item.evidence_status}; {item.action})")
    return "; ".join(blockers) if blockers else "apply blocked"


def render_markdown(
    summary: GuardSummary,
    *,
    checklist_path: Path,
    hotkey_voice_report: Path,
    screen_report: Path,
    content_rights_packet: Path,
) -> str:
    lines = [
        "# M18 Release Checklist Guard",
        "",
        f"- Release checklist: `{display_path(checklist_path)}`",
        f"- Hotkey voice report: `{display_path(hotkey_voice_report)}`",
        f"- Screen translation report: `{display_path(screen_report)}`",
        f"- Content-rights packet: `{display_path(content_rights_packet)}`",
        f"- Guard status: `{summary.guard_status}`",
        f"- Closure status: `{summary.closure_status}`",
        f"- Ready items: {summary.ready_count}/{len(summary.items)}",
        f"- Unsafe checked items: {summary.unsafe_count}",
        f"- Content-rights approval phrase: `{'present' if summary.approval_present else 'missing'}`",
        f"- Apply allowed: `{'yes' if summary.apply_allowed else 'no'}`",
        f"- Applied changes: `{'yes' if summary.applied else 'no'}`",
        "- GKP assets edited by this guard: no",
        "",
        "| Item | Checklist | Evidence | Ready | Unsafe Checked | Action | Evidence Detail |",
        "|---|---|---|---|---|---|---|",
    ]
    for item in summary.items:
        lines.append(
            f"| {escape_cell(item.label)} | `{item.checklist_state}` | `{item.evidence_status}` | "
            f"`{'yes' if item.ready_to_check else 'no'}` | "
            f"`{'yes' if item.unsafe_checked else 'no'}` | "
            f"{escape_cell(item.action)} | {escape_cell(item.evidence_detail)} |"
        )
    lines.extend(
        [
            "",
            "## Apply Rule",
            "",
            "The guarded apply path checks all three release checklist items only when hotkey voice "
            "matrix evidence passes, screen translation matrix evidence passes, the content-rights "
            "machine packet passes, and the exact human approval phrase is supplied.",
            "",
            "```bash",
            "python3 scripts/m18_release_checklist_guard.py \\",
            "  --apply \\",
            f"  --content-rights-approval \"{CONTENT_RIGHTS_APPROVAL}\"",
            "```",
        ]
    )
    return "\n".join(lines) + "\n"


def load_status_module() -> Any:
    spec = importlib.util.spec_from_file_location("m18_status_report", STATUS_SCRIPT)
    if spec is None or spec.loader is None:
        raise ValueError(f"cannot load status script: {STATUS_SCRIPT}")
    module = importlib.util.module_from_spec(spec)
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
