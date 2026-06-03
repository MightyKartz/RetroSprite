#!/usr/bin/env python3
"""Emit a machine-readable M18 gate status summary."""

from __future__ import annotations

import argparse
import importlib.util
import json
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
STATUS_SCRIPT = ROOT / "scripts/m18_status_report.py"
DEFAULT_OUTPUT = ROOT / "docs/qa-feedback/m18-gate-status.json"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument(
        "--strict",
        action="store_true",
        help="Exit nonzero unless every aggregate row is pass.",
    )
    args = parser.parse_args()

    try:
        summary = build_summary()
    except ValueError as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(
        "OK M18 gate status JSON: "
        f"overall={summary['overall_status']}, "
        f"pass={summary['counts']['pass']}, "
        f"open={summary['counts'].get('open', 0)}, "
        f"missing={summary['counts'].get('missing', 0)}, "
        f"fail={summary['counts'].get('fail', 0)}"
    )
    if args.strict and summary["overall_status"] != "pass":
        return 1
    return 0


def build_summary() -> dict[str, Any]:
    status = load_status_module()
    rows = [
        status.summarize_gkp_eval(status.DEFAULT_GKP_REPORT),
        status.summarize_gap_backlog(status.DEFAULT_BACKLOG, status.DEFAULT_BACKLOG_TRIAGE),
        status.summarize_patch_proposal_audit(status.DEFAULT_PATCH_AUDIT),
        status.summarize_patch_review_packet(status.DEFAULT_PATCH_PACKET),
        status.summarize_patch_apply_dry_run(status.DEFAULT_PATCH_APPLY_REPORT),
        status.summarize_gkp_asset_mutation_guard(status.DEFAULT_GKP_ASSET_GUARD),
        status.summarize_asr_voice_handoff(status.DEFAULT_ASR_VOICE_HANDOFF),
        status.summarize_hotkey_voice_matrix(status.DEFAULT_HOTKEY_VOICE_REPORT),
        status.summarize_command_contract_audit(status.DEFAULT_COMMAND_CONTRACT_AUDIT),
        status.summarize_quality_loop_handoff(status.DEFAULT_QUALITY_LOOP_HANDOFF),
    ]
    counts: dict[str, int] = {}
    for row in rows:
        counts[row.status] = counts.get(row.status, 0) + 1
    open_rows = [row for row in rows if row.status != "pass"]
    return {
        "schema_version": 1,
        "objective": "M18 Eval Lab + GKP Quality Loop",
        "overall_status": "pass" if not open_rows else "open",
        "counts": counts,
        "assets_edited_by_report": False,
        "requires_human_or_device_evidence": bool(open_rows),
        "rows": [
            {
                "area": row.area,
                "status": row.status,
                "evidence": row.evidence,
                "detail": row.detail,
            }
            for row in rows
        ],
        "open_areas": [
            {
                "area": row.area,
                "status": row.status,
                "evidence": row.evidence,
                "detail": row.detail,
            }
            for row in open_rows
        ],
    }


def load_status_module():
    spec = importlib.util.spec_from_file_location("m18_status_report", STATUS_SCRIPT)
    if spec is None or spec.loader is None:
        raise ValueError(f"cannot load status script: {STATUS_SCRIPT}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


if __name__ == "__main__":
    raise SystemExit(main())
