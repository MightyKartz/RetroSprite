# Retro JRPG/SRPG Pack Coverage Report

> Status: Phase 1 Chinese Lite packs generated, reviewed, and validated.
> Counts reflect the 2026-05-24 Task 9 validation pass.

| Pack | Coverage tier | Knowledge rows | Golden rows | Source rows | Localized aliases | Source-backed localized aliases | Pure Chinese goldens | Core gameplay goldens | No-evidence/clarification goldens | Known gaps |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| `golden-sun-gba-zh` | `lite` | 41 | 33 | 6 | 75 | 42 | 33 | 4 | 4 | Lite first-support only; no full walkthrough, all-Djinn locations, complete item list, or final Boss details. Ambiguous `灯塔` and full Djinn location requests stay clarification/no-evidence. |
| `phantasy-star-iv-md-zh` | `lite` | 35 | 30 | 7 | 69 | 44 | 30 | 5 | 4 | Lite first-support only; no full route, complete combo list, full equipment list, or late Boss details. Full late planet route requests now resolve to a Lite boundary row. |
| `langrisser-ii-md-zh` | `lite` | 36 | 32 | 7 | 80 | 44 | 31 | 5 | 4 | Lite first-support only; no full scenario walkthrough, complete route-entry conditions, or full class/item tables. Route terms such as `光辉线` remain version/progress gated. |
| `chrono-trigger-snes-zh` | `lite` | 35 | 32 | 7 | 70 | 46 | 32 | 4 | 5 | Lite first-support only; no all-endings list, complete era route map, full Tech table, or final Boss strategy. Broad era questions such as `未来在哪里` route to clarification/no-evidence. |
| `final-fantasy-vi-snes-zh` | `lite` | 39 | 34 | 7 | 75 | 50 | 32 | 4 | 3 | Lite first-support only; no complete World of Ruin route, all Espers/Relics, Rage/Lore lists, or full boss strategies. `幻兽` versus generic monster wording is guarded against unsafe normalization. |

## Validation Notes

- `audit_gkp_pack.js`: all five packs reported `errors: 0`.
- `check_localized_terms.js`: all five packs reported `warnings: 0`.
- `coverage_report.js --pilot knowledge_rows=20 goldens=20 localized_aliases=40 localized_goldens=20`: all five packs pass core Lite minimums; deep-style entity-count warnings are expected and deferred to expanded/deep phases.
- `coverage_report.js --pilot knowledge_rows=20 goldens=20 localized_aliases=40 localized_goldens=20 npc=6 item=5 location=5 boss=3 enemy=2`: all five packs reported `failed: 0` for the Phase 1 Lite entity distribution target.
- Android targeted tests passed for fixture lint, Lite coverage, shared retrieval goldens, ASR hotwords, and term normalization.
- Placeholder scan found no `__REPLACE_WITH_REVIEWED_GKP_DATA__` markers in the five pack directories.

## Update Rules

- Update this table immediately after each pack passes coverage lint.
- Keep `Known gaps` specific: uncertain localized names, spoiler-gated route
  content omitted, ASR variants rejected, or source reliability limitations.
- Do not mark a pack as supported while placeholder markers remain.
