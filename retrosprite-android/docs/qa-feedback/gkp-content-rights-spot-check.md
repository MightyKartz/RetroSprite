# GKP Content Rights Spot Check

> Date: 2026-06-01 14:49 CST
> Scope: bundled GKP assets under `app/src/main/assets/gkp`.

This is an engineering/content QA spot-check, not legal advice. The M17 release checklist still requires a final human release review before marking the content-rights checkbox complete.

## Files Reviewed

- Knowledge JSONL files: 49
- License files: 6
- Citation JSONL files: 6
- Bundled packs: 6

## Commands

```bash
find app/src/main/assets/gkp -path '*/knowledge/*.jsonl' -print
find app/src/main/assets/gkp -path '*/sources/licenses.md' -print
find app/src/main/assets/gkp -path '*/sources/citations.jsonl' -print
python3 scripts/rc_release_audit.py
python3 scripts/gkp_eval_report.py --gkp-dir app/src/main/assets/gkp --output docs/qa-feedback/m18-eval-report.md
```

## Findings

- `scripts/rc_release_audit.py` passed: six expected GKP packs, required license/citation files, safe GKP file types, resolved knowledge source refs, no bundled `sk-*` key, no stale runtime OCR route, and Qwen3-VL recommendation intact.
- `scripts/rc_release_audit.py` also checks GKP knowledge/golden text boundaries: unusually long string fields fail the audit, and explicit risk markers such as full script dumps, fan-translation text, or commercial guidebook prose are rejected before human review.
- `scripts/gkp_eval_report.py` passed for all six packs, including citations/licenses and QA goldens lanes.
- `rg` hits for `ROM`, `walkthrough`, `script`, `fan-translation`, and similar terms were reviewed as boundary/citation language or gameplay terms. The reviewed hits did not show bundled ROM/BIOS/save files, patch files, copied fan-translation scripts, full script dumps, commercial guidebook excerpts, or long copied walkthrough prose.
- The GKP content remains short summary, alias, term, metadata, source-ref, and golden-test data rather than executable code or full source-text dumps.

## Release Note

Do not mark the M17 human content-rights checkbox complete until a human release reviewer confirms this spot-check and any product/legal release requirements.
