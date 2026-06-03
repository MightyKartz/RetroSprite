#!/usr/bin/env python3
"""Audit whether the full M18 objective is actually complete."""

from __future__ import annotations

import argparse
import importlib.util
import json
import sys
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
GATE_STATUS_SCRIPT = ROOT / "scripts/m18_gate_status_json.py"
DEFAULT_PLAN_AUDIT = ROOT / "docs/qa-feedback/m18-plan-execution-audit.md"
DEFAULT_OUTPUT = ROOT / "docs/qa-feedback/m18-completion-audit.md"
DEFAULT_JSON_OUTPUT = ROOT / "docs/qa-feedback/m18-completion-audit.json"


@dataclass(frozen=True)
class CompletionRequirement:
    requirement_id: str
    requirement: str
    status: str
    evidence: str
    detail: str


@dataclass(frozen=True)
class CompletionAudit:
    overall_status: str
    requirements: tuple[CompletionRequirement, ...]
    plan_checked: int | None
    plan_unchecked: int | None

    @property
    def is_complete(self) -> bool:
        return self.overall_status == "pass"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plan-audit", type=Path, default=DEFAULT_PLAN_AUDIT)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--json-output", type=Path, default=DEFAULT_JSON_OUTPUT)
    parser.add_argument(
        "--strict",
        action="store_true",
        help="Exit nonzero unless every M18 completion requirement is proven pass.",
    )
    args = parser.parse_args()

    try:
        audit = build_audit(args.plan_audit)
    except ValueError as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1

    markdown = render_markdown(audit, args.plan_audit)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(markdown, encoding="utf-8")
    args.json_output.parent.mkdir(parents=True, exist_ok=True)
    args.json_output.write_text(
        json.dumps(render_json(audit, args.plan_audit), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    counts = status_counts(audit.requirements)
    print(
        "OK M18 completion audit: "
        f"overall={audit.overall_status}, "
        f"pass={counts.get('pass', 0)}, "
        f"open={counts.get('open', 0)}, "
        f"missing={counts.get('missing', 0)}, "
        f"fail={counts.get('fail', 0)}"
    )
    if args.strict and not audit.is_complete:
        return 1
    return 0


def build_audit(
    plan_audit_path: Path,
    *,
    gate_summary: dict[str, Any] | None = None,
) -> CompletionAudit:
    if gate_summary is None:
        gate_summary = load_gate_status_module().build_summary()
    plan_checked, plan_unchecked = read_plan_counts(plan_audit_path)

    requirements: list[CompletionRequirement] = []
    if plan_checked is None or plan_unchecked is None:
        requirements.append(
            CompletionRequirement(
                requirement_id="plan-checkboxes",
                requirement="M18 implementation-plan checkboxes are all closed.",
                status="missing",
                evidence=display_path(plan_audit_path),
                detail="Plan audit is missing checkbox counts.",
            )
        )
    else:
        requirements.append(
            CompletionRequirement(
                requirement_id="plan-checkboxes",
                requirement="M18 implementation-plan checkboxes are all closed.",
                status="pass" if plan_unchecked == 0 else "open",
                evidence=display_path(plan_audit_path),
                detail=f"checked={plan_checked}; unchecked={plan_unchecked}",
            )
        )

    assets_edited = bool(gate_summary.get("assets_edited_by_report"))
    requirements.append(
        CompletionRequirement(
            requirement_id="report-asset-safety",
            requirement="M18 status and audit tooling did not edit bundled GKP assets.",
            status="fail" if assets_edited else "pass",
            evidence="docs/qa-feedback/m18-gate-status.json",
            detail=f"assets_edited_by_report={str(assets_edited).lower()}",
        )
    )

    for row in gate_summary.get("rows", []):
        area = str(row.get("area", "unknown"))
        row_status = normalize_status(str(row.get("status", "missing")))
        requirements.append(
            CompletionRequirement(
                requirement_id=f"aggregate-{slug(area)}",
                requirement=f"Aggregate gate `{area}` is pass.",
                status=row_status,
                evidence=str(row.get("evidence", "")),
                detail=str(row.get("detail", "")),
            )
        )

    requires_human_or_device = bool(gate_summary.get("requires_human_or_device_evidence"))
    requirements.append(
        CompletionRequirement(
            requirement_id="machine-device-evidence",
            requirement="No remaining M18 machine or real-device evidence is required.",
            status="open" if requires_human_or_device else "pass",
            evidence="docs/qa-feedback/m18-gate-status.json",
            detail=f"requires_machine_or_device_evidence={str(requires_human_or_device).lower()}",
        )
    )

    aggregate_overall = str(gate_summary.get("overall_status", "missing"))
    final_status = "pass"
    if aggregate_overall != "pass" or (plan_unchecked is not None and plan_unchecked != 0):
        final_status = "open"
    if plan_checked is None or plan_unchecked is None:
        final_status = "missing"
    requirements.append(
        CompletionRequirement(
            requirement_id="final-offline-gate",
            requirement="Final `./scripts/m18_offline_quality_gate.sh` is eligible to pass.",
            status=final_status,
            evidence="./scripts/m18_offline_quality_gate.sh",
            detail=f"gate_status={aggregate_overall}; plan_unchecked={plan_unchecked if plan_unchecked is not None else 'missing'}",
        )
    )

    overall_status = "pass"
    if any(item.status in {"fail", "missing"} for item in requirements):
        overall_status = "fail"
    elif any(item.status != "pass" for item in requirements):
        overall_status = "open"

    return CompletionAudit(
        overall_status=overall_status,
        requirements=tuple(requirements),
        plan_checked=plan_checked,
        plan_unchecked=plan_unchecked,
    )


def render_markdown(audit: CompletionAudit, plan_audit_path: Path) -> str:
    counts = status_counts(audit.requirements)
    open_items = [item for item in audit.requirements if item.status != "pass"]
    lines = [
        "# M18 Completion Audit",
        "",
        f"- Plan audit: `{display_path(plan_audit_path)}`",
        f"- Overall status: `{audit.overall_status}`",
        f"- Requirement counts: pass={counts.get('pass', 0)}, open={counts.get('open', 0)}, missing={counts.get('missing', 0)}, fail={counts.get('fail', 0)}",
        f"- Plan checkboxes: checked={audit.plan_checked if audit.plan_checked is not None else 'missing'}, unchecked={audit.plan_unchecked if audit.plan_unchecked is not None else 'missing'}",
        "- GKP assets edited by this audit: no",
        "",
        "## Requirements",
        "",
        "| ID | Requirement | Status | Evidence | Detail |",
        "|---|---|---|---|---|",
    ]
    for item in audit.requirements:
        lines.append(
            f"| {escape_cell(item.requirement_id)} | {escape_cell(item.requirement)} | "
            f"`{escape_cell(item.status)}` | `{escape_cell(item.evidence)}` | {escape_cell(item.detail)} |"
        )

    lines.extend(["", "## Open Or Unproven", ""])
    if not open_items:
        lines.append("- None. Every M18 completion requirement is proven pass.")
    else:
        for item in open_items:
            lines.append(f"- `{item.requirement_id}` is `{item.status}`: {item.detail}")

    lines.extend(
        [
            "",
            "## Completion Decision",
            "",
        ]
    )
    if audit.is_complete:
        lines.append("M18 is complete according to this audit.")
    else:
        lines.append(
            "M18 is not complete yet. Do not mark the goal complete until every requirement above is `pass` "
            "and `./scripts/m18_offline_quality_gate.sh` succeeds."
        )
    return "\n".join(lines) + "\n"


def render_json(audit: CompletionAudit, plan_audit_path: Path) -> dict[str, Any]:
    counts = status_counts(audit.requirements)
    return {
        "schema_version": 1,
        "objective": "M18 Eval Lab + GKP Quality Loop",
        "overall_status": audit.overall_status,
        "is_complete": audit.is_complete,
        "plan_audit": display_path(plan_audit_path),
        "plan_checked": audit.plan_checked,
        "plan_unchecked": audit.plan_unchecked,
        "counts": {
            "pass": counts.get("pass", 0),
            "open": counts.get("open", 0),
            "missing": counts.get("missing", 0),
            "fail": counts.get("fail", 0),
        },
        "assets_edited_by_report": False,
        "requirements": [
            {
                "id": item.requirement_id,
                "requirement": item.requirement,
                "status": item.status,
                "evidence": item.evidence,
                "detail": item.detail,
            }
            for item in audit.requirements
        ],
    }


def read_plan_counts(path: Path) -> tuple[int | None, int | None]:
    json_path = path if path.suffix == ".json" else path.with_suffix(".json")
    if json_path.is_file():
        return read_plan_counts_json(json_path)
    if not path.is_file():
        return None, None
    text = path.read_text(encoding="utf-8")
    match = re.search(r"- Plan checkboxes:\s*checked=(\d+),\s*unchecked=(\d+)", text)
    if not match:
        return None, None
    return int(match.group(1)), int(match.group(2))


def read_plan_counts_json(path: Path) -> tuple[int | None, int | None]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return None, None
    if not isinstance(data, dict):
        return None, None
    checked = data.get("plan_checked")
    unchecked = data.get("plan_unchecked")
    if isinstance(checked, int) and isinstance(unchecked, int):
        return checked, unchecked
    counts = data.get("counts")
    if not isinstance(counts, dict):
        return None, None
    checked = counts.get("plan_checked")
    unchecked = counts.get("plan_unchecked")
    if isinstance(checked, int) and isinstance(unchecked, int):
        return checked, unchecked
    return None, None


def normalize_status(status: str) -> str:
    if status in {"pass", "open", "missing", "fail"}:
        return status
    return "open" if status else "missing"


def status_counts(requirements: tuple[CompletionRequirement, ...]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for item in requirements:
        counts[item.status] = counts.get(item.status, 0) + 1
    return counts


def load_gate_status_module():
    spec = importlib.util.spec_from_file_location("m18_gate_status_json", GATE_STATUS_SCRIPT)
    if spec is None or spec.loader is None:
        raise ValueError(f"cannot load gate status script: {GATE_STATUS_SCRIPT}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def slug(value: str) -> str:
    cleaned = re.sub(r"[^a-zA-Z0-9]+", "-", value.strip().lower()).strip("-")
    return cleaned or "unknown"


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
