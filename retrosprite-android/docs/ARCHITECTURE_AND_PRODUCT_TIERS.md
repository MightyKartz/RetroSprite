# RetroSprite Architecture And Product Tiers

> Date: 2026-06-01
> Status: current documentation source of truth
> Scope: main Android app, GKP builder, GKP Lite, expanded/deep GKP coverage, Pro commercial tier, and optional LLM boundaries.

## 1. Current Product Shape

RetroSprite is a RetroArch in-game Q&A companion. It is not a general chatbot,
not an always-on screen reader, and not an LLM-first guide generator.

The player flow is:

```text
RetroArch AI Service hotkey
  -> RetroSprite local endpoint
  -> hotkey voice overlay
  -> local ASR transcript
  -> normal question: game resolver / local GKP retrieval / evidence and spoiler policy
  -> screen translation command: current screenshot BYOK API translation
  -> short answer with optional TTS, or complete translated text HUD
```

The project direction is:

```text
offline GKP Lite baseline
  + optional player-configured LLM assistance
  + source ids and diagnostics
  + later expanded/deep GKP coverage
  + future Virtual Spirit Pro commercial features
```

The important boundary: an enabled LLM can make answers more natural, translate,
rewrite noisy questions, or synthesize evidence, but it must not become the
factual source for game-specific claims.

Current engineering focus is **M17.1 / M18 quality-loop closure**. The product
shape above is already broad enough for a preview release; the next milestone
should harden the existing hotkey voice loop, six bundled GKP packs,
diagnostics, and BYOK screen translation instead of adding new runtime
features. The active execution frontier is the machine-checkable M18 loop:
refresh reports, replay the seven-row hotkey voice matrix, convert repeatable
misses into backlog or scoped patch proposals, run GKP regression, then replay
on RG476H. See
`docs/superpowers/plans/2026-06-01-m18-eval-lab-gkp-quality-loop.md` and
`docs/qa-feedback/m18-next-action-queue.md`.

## 2. Main Program

The main program lives in `retrosprite-android/`.

Its job is to run the player experience:

| Area | Responsibility |
| --- | --- |
| Endpoint | Receive RetroArch AI Service requests and expose debug routes. |
| Hotkey overlay | Wake on RetroArch requests, record one short question, show answer state. |
| Voice | Use local ASR for input and Android TTS for short answer output. |
| Screen translation | On explicit voice intent, send the current screenshot to the user-configured BYOK screen translation API and show Chinese translation without TTS. |
| Game resolver | Map label/hash/title/platform to the current GKP game id. |
| Retrieval | Search templates, aliases/entities, and FTS-style local knowledge rows. |
| Answer policy | Enforce evidence, spoiler, disabled-pack, and no-evidence boundaries. |
| LLM adapter | Optional BYOK/OpenAI-compatible composer after evidence exists. |
| Packs UI | Import, preflight, enable/disable, delete, and diagnose GKP packs. |
| Diagnostics | Show request source, pipeline stage, source ids, LLM status, timing, and feedback. |

The runtime must stay useful when:

- no LLM key is configured;
- no screen translation API key is configured;
- the device is offline;
- a GKP is disabled;
- retrieval finds no evidence;
- the player asks for a spoiler above the current setting.

## 3. GKP Builder

The GKP builder lives in `tools/gkp-builder/`. It is developer-side tooling,
not Android runtime code.

Current commands:

```bash
tools/gkp-builder/bin/gkp-builder new --profile lite ...
tools/gkp-builder/bin/gkp-builder coverage <pack-dir>
```

Current builder contract:

| Piece | Purpose |
| --- | --- |
| `templates/gkp-lite/profile.yaml` | Machine-readable Lite thresholds and policies. |
| `templates/gkp-lite/*.template.*` | Standard scaffold for new first-support packs. |
| `scripts/gkp_builder_new.py` | Generates a pack skeleton from template variables. |
| `scripts/gkp_lite_coverage.py` | Checks Lite coverage, source refs, goldens, and placeholder markers. |
| `bin/gkp-builder` | Stable CLI wrapper for builder commands. |

Builder tooling may later call RAG-Anything, LightRAG, GraphRAG, LlamaIndex, or
LLM-assisted drafting tools. Those tools should produce candidate rows only.
Android should receive reviewed plain GKP data, not raw model output,
executables, ROM data, save data, or copied guide text.

The intended production line is:

```text
source inventory
  -> rights/provenance check
  -> optional extraction or drafting workbench
  -> candidate GKP rows
  -> human review and rewrite
  -> coverage lint
  -> Android runtime goldens
  -> bundled import or registry publish
```

## 4. GKP Package Tiers

`coverage_tier` should remain the machine-readable field. The stable internal
values are:

```text
lite
expanded
deep
```

These are content coverage tiers, not app pricing tiers. Do not introduce
`plus` or `pro` as schema values unless there is a separate parser, preflight,
test, and migration update. Use **Pro** for the paid product tier described in
`VIRTUAL_SPIRIT_COMMERCIALIZATION_DISCUSSION.md`, not for GKP coverage.

| Tier | User-facing name | Meaning | Typical size | Support promise |
| --- | --- | --- | ---: | --- |
| `lite` | GKP Lite | First supported package. Anchors identity, aliases, core loop, common mechanics, beginner direction, key terms, spoiler gates, sources, and goldens. | 20-60 rows, 20-40 goldens typical; mild overage warns | The game is answerable for common safe questions, not complete. |
| `expanded` | GKP Expanded | Broader reviewed pack for active users. Adds more characters, items, bosses, locations, routes, ASR variants, and progress gates. | 60-150 rows, 40-100 goldens | The game feels substantially useful beyond first support. |
| `deep` | GKP Deep | Mature pack with many optional details and tighter progress gates. | 150+ rows, 100+ goldens | Detailed helper for repeated play, still low-spoiler by default. |

All tiers share the same rule: local rows need source ids, spoiler gates, and
goldens. Higher tiers expand coverage; they do not loosen evidence policy.

## 5. What GKP Lite Must Cover

GKP Lite is the minimum support contract for a real game.

Required lanes:

- identity, platform, region/version, and observed RetroArch labels;
- Chinese/English/localized aliases and ASR-prone variants;
- core gameplay, fun hook, and who the game suits;
- beginner first-hour direction;
- core mechanics and resources;
- key terms for characters, items, locations, systems, enemies, or bosses;
- a small number of high-frequency stuck points;
- coarse spoiler gates and layered answers;
- stable source ids and reliability labels;
- natural text/voice goldens, no-evidence goldens, and spoiler regressions.

Lite intentionally does not promise:

- full walkthrough routes;
- all hidden items;
- all boss details;
- complete class/build tables;
- all endings;
- late-game twist explanations;
- exhaustive regional or port differences.

When a Lite pack cannot answer safely, the correct behavior is clarification or
no-evidence, not a model guess.

## 6. What Expanded GKP Adds

Expanded GKP should be an expansion of a reviewed Lite pack, not a replacement
for it.

Good expanded lanes:

- broader character and party advice by progress gate;
- more item usage and selected locations;
- common boss and enemy buckets;
- more route hints, without becoming a copied walkthrough;
- region or translation variants that players actually encounter;
- more ASR aliases and normalizer regressions;
- more no-evidence boundaries for broad or spoiler-heavy requests;
- runtime smoke cases for realistic RetroArch labels.

Expanded packs should still avoid:

- copied guide prose;
- raw script dumps;
- ROM/save data;
- unreviewed LLM facts;
- making every possible late-game detail visible under the default spoiler level.

## 7. Optional LLM And Pro Boundary

There are separate concepts:

| Concept | What it means |
| --- | --- |
| `coverage_tier=expanded` | A richer reviewed knowledge pack. This is not a paid app tier. |
| LLM assistance | A player-enabled runtime feature for query rewrite, translation, or evidence synthesis. |
| Virtual Spirit Pro | A paid player-facing product tier for advanced runtime value such as richer menu/dialogue recognition, screen-aware hints, verified pack auto-update, pack collections, sync, and richer history. |
| Creator Pro / GKP Studio Pro | A paid creator tooling line for batch import, source/license risk checks, golden generation, regression reports, signing, registry submission, and analytics. |

They should not be coupled. A player can use GKP Lite with no LLM and no Pro
license. A player can enable an LLM while still using a Lite pack. An expanded
pack should still work offline for its deterministic goldens.

Pro should not lock the basic app, GKP import, the GKP file format, the free
GKP generator, basic linting, BYOK LLM configuration, or basic English
localization. Basic on-demand current-screen translation also remains part of
the core app. Those remain part of the open/community loop.

Allowed LLM jobs:

- clean up noisy ASR transcripts before retrieval;
- map cross-language terms to canonical GKP entities;
- compose multiple source-backed rows into a short answer;
- translate or rephrase source-backed answers;
- write clearer clarification questions.

Disallowed LLM jobs:

- invent game-specific facts without evidence;
- bypass spoiler gates;
- answer exact routes, hidden items, boss weaknesses, endings, or story outcomes
  from model memory;
- hide that an answer is generic or unsupported.

## 8. Current Repository State

As of this document:

- The main Android app has a working local-first Q&A pipeline.
- There are six bundled real GKP packs.
- Five Retro JRPG/SRPG Chinese packs declare `coverage_tier: lite`.
- `shining-force-ii-md` is broader than Lite and should be classified as
  `expanded` or kept as legacy until Packs UI and preflight fully surface tiers.
- `tools/gkp-builder` has a Lite scaffold and coverage command.
- Six-pack runtime `/debug/ask` smoke passed on RG476H through the M17 device
  smoke gate.
- Multi-pack hotkey voice QA tooling now records real AudioRecord counters.
  The current seven-row RG476H hotkey voice matrix reaches fresh
  `hotkey_voice` submissions for every row and is narrowed to 5/7 pass with
  two repeatable failures: one `source_mismatch` and one `asr_variant`.
- M18 quality tooling exists for GKP coverage, gap backlog, patch proposals,
  patch dry-run, asset mutation guard, ASR replay handoff, hotkey voice matrix
  reporting, command-contract audit, quality-loop handoff, and aggregate status
  reports. Manual ASR approval, the five-row screen translation manual matrix,
  and human content-rights confirmation are not M18 aggregate gates.

## 9. Documentation Map

Use this file for the current architecture and tier vocabulary.

| Document | Use it for |
| --- | --- |
| `README.md` / `README.zh-CN.md` | Public project summary and exact supported games. |
| `retrosprite-android/README.md` | Android build, setup, and module orientation. |
| `docs/GKP_V0_SCHEMA.md` | GKP file format and parser-facing schema. |
| `docs/GKP_LITE_OPTIONAL_LLM_DIRECTION.md` | Product policy for Lite plus optional LLM. |
| `docs/REAL_GAME_GKP_EXPANSION_TEMPLATE.md` | Authoring template for real-game packs. |
| `tools/gkp-builder/README.md` | Builder command usage. |
| `../../VIRTUAL_SPIRIT_COMMERCIALIZATION_DISCUSSION.md` | Commercial split: free/community loop, Virtual Spirit Pro, Creator Pro, registry, and OEM paths. |
| `docs/TEST_COVERAGE.md` | What is actually validated. |
| `docs/NEXT_IMPLEMENTATION_PLAN.md` | Historical roadmap and implementation task board. |
| `docs/superpowers/plans/2026-06-01-release-candidate-hardening.md` | M17 release-candidate hardening plan. |
| `docs/superpowers/plans/2026-06-01-m18-approval-gated-quality-loop.md` | Superseded historical approval-gated plan. |
| `docs/qa-feedback/m18-status-report.md` | Current open/pass status for machine-checkable M18 GKP/eval/voice/quality-loop gates. |

When documents disagree, prefer:

```text
current code/tests
  -> TEST_COVERAGE
  -> this architecture/tier document
  -> README support scope
  -> older roadmap docs
```

## 10. Next Implementation Order

Recommended order:

1. Refresh M18 reports with `./scripts/m18_offline_quality_gate.sh`.
2. Use hotkey voice matrix misses as evidence for backlog rows or scoped patch
   proposals; do not wait on an M18 manual ASR approval gate.
3. Keep bundled GKP assets clean unless the user explicitly approves an exact
   patch, then run focused GKP regression and release audit.
4. Use `docs/qa-feedback/m18-next-action-queue.md` and
   `docs/qa-feedback/m18-remaining-gate-handoff.md` for the current
   machine/device frontier.
5. Only after the RC gate is green, resume product improvements such as
   `coverage_tier` surfacing, richer no-evidence inboxes, multilingual answer
   surfaces, and optional LLM assist as an evidence enhancer.

This keeps the project anchored: the app experience improves, the content
pipeline scales, expanded means reviewed coverage, and Pro means paid
screen-aware/trust/convenience value rather than model dependency.
