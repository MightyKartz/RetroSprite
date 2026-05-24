# Retro JRPG/SRPG Pack Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first five legal, short-text, testable Chinese `coverage_tier: lite` RetroSprite GKP packs for the `Retro JRPG/SRPG Pack` set, using a language-neutral entity skeleton that can later produce expanded Chinese packs and English, Japanese, and Korean surfaces.

**Architecture:** Split each game into a stable language-neutral entity skeleton and one language-specific GKP Lite package generated from a standard scaffold. Phase 1 first defines `tools/gkp-builder/templates/gkp-lite/`, then uses that profile to create only `*-zh` packages with Chinese/Hanzi aliases, Chinese answer templates, Chinese ASR variants, 20-60 reviewed knowledge rows, and 20-40 Chinese golden Q&A rows; future `*-en`, `*-ja`, and `*-ko` packages reuse the same `game_id`, `entity_id`, source ids, spoiler gates, and coverage taxonomy. Reuse the `shining-force-ii-md` 0.3.0 pack as a high-water-mark reference, not as the minimum first-support bar or a folder-copy template. RAG-Anything or other document tools may be used only as offline candidate extraction into the scaffold; final GKP rows must be reviewed and rewritten.

**Tech Stack:** RetroSprite Android assets, GKP v0 JSON/JSONL, `tools/gkp-builder/templates/gkp-lite/`, `profile.yaml`, Markdown skeleton/inventory docs, Kotlin/JVM tests, `GkpV0Parser`, `LocalKnowledgeRetrievalPipeline`, `GkpAsrHotwordExtractor`, `GameTermNormalizer`, `retrosprite-gkp-production` skill scripts, Gradle `testDebugUnitTest`.

---

## Pack Set

The first batch is named `Retro JRPG/SRPG Pack`.

| Order | Phase 1 zh pack id | Stable game id | Game | Platform | Type | Why first batch |
| ---: | --- | --- | --- | --- | --- |
| 1 | `community.golden-sun-gba-zh` | `golden_sun_gba` | Golden Sun / 黄金太阳 | GBA | JRPG | Official manual exists, rich systems, strong ASR/alias value around Djinn/Psynergy/灯塔 names. |
| 2 | `community.phantasy-star-iv-md-zh` | `phantasy_star_iv_md` | Phantasy Star IV / 梦幻之星 IV | MD/Genesis | JRPG | Sega official source, MD representative, manageable scope, strong sci-fi proper-noun coverage. |
| 3 | `community.langrisser-ii-md-zh` | `langrisser_ii_md` | Langrisser II / 梦幻模拟战 II | MD | SRPG | Chinese-player value, commanders/classes/routes/兵种 fit GKP well. |
| 4 | `community.chrono-trigger-snes-zh` | `chrono_trigger_snes` | Chrono Trigger / 时空之轮 | SNES/SFC | JRPG | Highest recognition, excellent benchmark for spoiler gates and time-era location aliases. |
| 5 | `community.final-fantasy-vi-snes-zh` | `final_fantasy_vi_snes` | Final Fantasy VI / 最终幻想 VI | SNES/SFC | JRPG | Large role/Esper/Boss/world-split coverage; best stress test for broad pack scale. |

## Seed Source Plan

Use these as seed sources only. They justify game identity, major systems, and source discovery. Do not copy guide prose into GKP.

| Game | Seed sources |
| --- | --- |
| Golden Sun | Nintendo official manual PDF: `https://www.nintendo.com/eu/media/downloads/games_8/emanuals/game_boy_advance_8/Manual_GameBoyAdvance_GoldenSun_EN_DE_FR_ES_IT.pdf`; Golden Sun Wiki pages for Psynergy/Djinn/class factual cross-checks; Nintendo Life/GameSpot/RPGFan reviews for popularity context. |
| Phantasy Star IV | Sega Mega Drive Mini official page: `https://www.sega.jp/mdmini/soft/phantasy-star4.html`; MobyGames and community wiki pages for metadata and roster cross-checks. |
| Langrisser II | Sega Mega Drive Mini official Chinese page: `https://asia.sega.com/mdmini/cht/soft/langrisser2.html`; Sega-16 review and Langrisser community references for factual cross-checks. |
| Chrono Trigger | Square Enix official page: `https://www.jp.square-enix.com/game/detail/chronotrigger/`; Nintendo Life and community wiki pages for popularity and factual cross-checks. |
| Final Fantasy VI | Square Enix official FFVI/Pixel Remaster pages; Nintendo/Square manuals where available; community wiki pages for roster/Esper/location cross-checks. |

## Direction Inputs

This plan follows the 2026-05-24 GKP Lite direction:

- `retrosprite-android/docs/GKP_LITE_OPTIONAL_LLM_DIRECTION.md`
- `retrosprite-android/docs/REAL_GAME_GKP_EXPANSION_TEMPLATE.md`
- `retrosprite-android/docs/GKP_V0_SCHEMA.md`
- `retrosprite-android/docs/NEXT_IMPLEMENTATION_PLAN.md`

The practical rule is: generate every first-support pack from the same GKP Lite
scaffold, ship reviewed Lite packs first, preserve evidence and localized names,
and move 100+ row/100+ golden goals to an explicit later expanded/deep phase.

## Non-Negotiable Scope

- Do not expand ASR/LLM/model runtime for this project.
- Do not add RAG-Anything to Android runtime.
- Do not create new pack directories by copying `shining-force-ii-md` or another existing game pack. Use the GKP Lite scaffold/profile.
- Do not include ROM data, scripts, manual scans, copied walkthrough paragraphs, or large copied tables.
- Do not generate answers from LLM memory as facts.
- Every knowledge row with factual claims must have `source_refs`.
- Every high-value localized term must resolve through GKP rows and aliases, not hard-coded Kotlin synonyms.
- Every Phase 1 pack must ship as a Chinese GKP Lite package with source refs, spoiler levels, aliases, ASR-prone variants, and 20-40 Chinese golden questions.
- Do not require complete walkthrough coverage, full item lists, all bosses, all enemies, all route branches, or all hidden locations in Phase 1.
- Do not fork entity ids by language. `npc.isaac`, `mechanic.djinn`, and `location.mercury-lighthouse` must mean the same thing in future `zh`, `en`, `ja`, and `ko` packs.
- Do not mix answer languages inside one shipped pack. Chinese packages may include English canonical names and source names, but answer templates and goldens should be Chinese unless they are explicit name-mapping cases.

## Multilingual Pack Strategy

The product direction is one shared game skeleton and multiple language packages.

```text
docs/gkp/skeletons/golden-sun-gba-entity-skeleton.md
app/src/main/assets/gkp/golden-sun-gba-zh/
app/src/main/assets/gkp/golden-sun-gba-en/   # later
app/src/main/assets/gkp/golden-sun-gba-ja/   # later
app/src/main/assets/gkp/golden-sun-gba-ko/   # later
```

Phase 1 creates only the `*-zh` packages. Later language packs must reuse:

- the same stable `game_id`
- the same `entity_id` names
- the same `source_id` meanings where sources overlap
- the same `spoiler_graph` gate ids
- the same content lanes: production, mechanics, entities, items, locations, bosses, enemies, strategies

Language-specific files may differ in:

- `pack_id`, such as `community.golden-sun-gba-zh` or `community.golden-sun-gba-en`
- `default_language`
- `knowledge/*.jsonl` text and `language`
- aliases and ASR variants
- answer templates
- `qa_goldens.jsonl`
- localized-name source rows

Current runtime note: the app mostly resolves by `game_id`, and the endpoint currently defaults answer language to `zh`. Phase 1 can use stable base `game_id` because only zh packs are shipped. Before installing multiple languages for the same game at once, add runtime pack-language selection so `golden_sun_gba` can choose `zh`, `en`, `ja`, or `ko` without overwriting rows.

## Entity Skeleton Rules

Each game gets a language-neutral skeleton doc before GKP package data is written. The skeleton is the contract that later English, Japanese, and Korean packs will reuse.

Each skeleton row must include:

```markdown
| entity_id | entity_type | canonical_en | canonical_zh | spoiler_gate | spoiler_level | source_refs | notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
```

Skeleton rules:

- `entity_id` must be language-neutral and stable.
- Use lowercase English slugs for ids: `npc.isaac`, `item.herb`, `location.mercury-lighthouse`, not `npc.伊萨克`.
- `canonical_en` is the source-facing anchor when available.
- `canonical_zh` is the Phase 1 display anchor when source-backed.
- `spoiler_gate` and `spoiler_level` must be language-independent.
- If a later language has a different official/localized name, add it as that language pack's alias, not as a new entity id.

## Shining Force II Lessons To Reuse

The `shining-force-ii-md` 0.3.0 pack proved that a GKP becomes useful for Chinese voice Q&A only when the player-facing localized name layer is designed before the entity rows are considered complete. For this pack set, each game must start with a localized-name baseline instead of treating Chinese names as late aliases.

Apply these lessons to all five packs:

- **Localized names are content, not decoration.** Famous Chinese titles, fan/localization names, item names, location names, boss names, class names, and mechanic terms need source refs, aliases, and goldens.
- **Do not copy localization text.** Only absorb the name system and short factual labels from known Chinese versions or community references. All descriptions and answers remain RetroSprite-authored short summaries.
- **Keep English canonical names and Chinese names together.** Use `canonical_name` like `Crono / 克罗诺` or `Psynergy / 精神力` when the Chinese name is stable; use aliases for alternate names such as `超时空之钥` or `太空战士VI`.
- **Test pure Chinese questions.** A pack that only passes `Crono 是谁` but fails `克罗诺是谁` is not ready.
- **ASR variants must be conservative.** Add hotword aliases for likely player speech, but put risky homophones in `GameTermNormalizerTest` only when the rewrite is unambiguous.
- **Ambiguity is a first-class outcome.** If a name maps to multiple versions, characters, or routes, add a clarification/no-evidence golden instead of forcing a false hit.

## Coverage Tier Strategy

Phase 1 is a first-support Lite release, not a complete guide. The pack should
answer common in-play questions offline, route harder questions to clarification
or no-evidence, and record gaps for later expansion.

| Tier | Use in this plan | Typical size |
| --- | --- | --- |
| `lite` | Phase 1 required target for all five Chinese packs | 20-60 rows, 20-40 goldens |
| `expanded` | Deferred Phase 2 after no-evidence/failure data exists | 60-150 rows, 40-100 goldens |
| `deep` | Mature per-game pilot, similar to or beyond Shining Force II 0.3.x | 150+ rows, 100+ goldens |

## Minimum Per-Pack Lite Targets

These are intentionally lower than the older 100+ row/100+ golden target and
higher than a toy sample. They should be enforced for Phase 1.

| Metric | JRPG Lite target | SRPG Lite target | Deferred expanded target |
| --- | ---: | ---: | ---: |
| Coverage tier | `lite` | `lite` | `expanded` or `deep` |
| Knowledge rows | 20-60 | 20-60 | 60-150+ |
| Golden Q&A rows | 20-40 | 20-40 | 40-100+ |
| Source rows | 5+ | 5+ | 10+ |
| Localized/CJK aliases | 40+ | 40+ | 120+ |
| Source-backed localized proper-name aliases | 25+ | 25+ | 80+ |
| Pure Chinese/localized-name golden questions | 12+ | 12+ | 40+ |
| ASR-prone alias/normalizer variants | 10+ | 10+ | 20+ |
| Character/NPC rows | 6-12 | 8-14 commanders/NPCs | 20-25+ |
| Item/equipment/key-term rows | 5-10 | 5-10 | 35+ |
| Location/route rows | 5-10 | 5-10 | 25+ |
| Boss/enemy rows | 4-8 combined | 4-8 combined | 25-30+ combined |
| Core gameplay/fun-hook goldens | 4+ | 4+ | 8+ |
| No-evidence/clarification goldens | 3+ | 3+ | 5+ |

## Shared File Map

The scaffold contract is created before any game pack:

- Create: `tools/gkp-builder/templates/gkp-lite/profile.yaml`
- Create: `tools/gkp-builder/templates/gkp-lite/manifest.template.json`
- Create: `tools/gkp-builder/templates/gkp-lite/aliases.template.json`
- Create: `tools/gkp-builder/templates/gkp-lite/spoiler_graph.template.json`
- Create: `tools/gkp-builder/templates/gkp-lite/qa_goldens.template.jsonl`
- Create: `tools/gkp-builder/templates/gkp-lite/changelog.template.md`
- Create: `tools/gkp-builder/templates/gkp-lite/sources/citations.template.jsonl`
- Create: `tools/gkp-builder/templates/gkp-lite/sources/licenses.template.md`
- Create: `tools/gkp-builder/templates/gkp-lite/knowledge/production.template.jsonl`
- Create: `tools/gkp-builder/templates/gkp-lite/knowledge/mechanics.template.jsonl`
- Create: `tools/gkp-builder/templates/gkp-lite/knowledge/strategies.template.jsonl`
- Create: `tools/gkp-builder/templates/gkp-lite/knowledge/entities.template.jsonl`
- Create: `tools/gkp-builder/templates/gkp-lite/knowledge/items.template.jsonl`
- Create: `tools/gkp-builder/templates/gkp-lite/knowledge/locations.template.jsonl`
- Create: `tools/gkp-builder/templates/gkp-lite/knowledge/bosses.template.jsonl`
- Create: `tools/gkp-builder/templates/gkp-lite/knowledge/enemies.template.jsonl`

Each Phase 1 Chinese pack is generated from that scaffold and then filled with reviewed content:

- Generate: `retrosprite-android/app/src/main/assets/gkp/<pack-slug>-zh/manifest.json`
- Generate: `retrosprite-android/app/src/main/assets/gkp/<pack-slug>-zh/spoiler_graph.json`
- Generate: `retrosprite-android/app/src/main/assets/gkp/<pack-slug>-zh/aliases.json`
- Generate: `retrosprite-android/app/src/main/assets/gkp/<pack-slug>-zh/qa_goldens.jsonl`
- Generate: `retrosprite-android/app/src/main/assets/gkp/<pack-slug>-zh/changelog.md`
- Generate: `retrosprite-android/app/src/main/assets/gkp/<pack-slug>-zh/sources/citations.jsonl`
- Generate: `retrosprite-android/app/src/main/assets/gkp/<pack-slug>-zh/sources/licenses.md`
- Generate: `retrosprite-android/app/src/main/assets/gkp/<pack-slug>-zh/knowledge/mechanics.jsonl`
- Generate: `retrosprite-android/app/src/main/assets/gkp/<pack-slug>-zh/knowledge/entities.jsonl`
- Generate: `retrosprite-android/app/src/main/assets/gkp/<pack-slug>-zh/knowledge/items.jsonl`
- Generate: `retrosprite-android/app/src/main/assets/gkp/<pack-slug>-zh/knowledge/locations.jsonl`
- Generate: `retrosprite-android/app/src/main/assets/gkp/<pack-slug>-zh/knowledge/bosses.jsonl`
- Generate: `retrosprite-android/app/src/main/assets/gkp/<pack-slug>-zh/knowledge/enemies.jsonl`
- Generate: `retrosprite-android/app/src/main/assets/gkp/<pack-slug>-zh/knowledge/strategies.jsonl`
- Generate: `retrosprite-android/app/src/main/assets/gkp/<pack-slug>-zh/knowledge/production.jsonl`

Shared tests/tools:

- Modify or extend: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/gkp/GkpV0FixtureLintTest.kt`
- Create: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/gkp/RetroJrpgSrpgPackCoverageTest.kt`
- Create: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/data/retrieval/RetroJrpgSrpgPackRetrievalGoldenTest.kt`
- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/voice/asr/GkpAsrHotwordExtractorTest.kt`
- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/domain/normalization/GameTermNormalizerTest.kt`

Optional audit docs:

- Create: `retrosprite-android/docs/gkp/retro-jrpg-srpg-pack-source-register.md`
- Create: `retrosprite-android/docs/gkp/retro-jrpg-srpg-pack-coverage-report.md`
- Create: `retrosprite-android/docs/gkp/<pack-id>-localized-term-inventory.md`
- Create: `retrosprite-android/docs/gkp/skeletons/<pack-slug>-entity-skeleton.md`

Each localized-term inventory must use this structure:

```markdown
# <Game> Localized Term Inventory

## Accepted Terms

| term | category | target_entity_id | source_refs | confidence | alias_weight | spoiler_gate | action |
| --- | --- | --- | --- | --- | ---: | --- | --- |

## Needs Source

| term | category | suspected_entity | reason_needed |
| --- | --- | --- | --- |

## Rejected Generic Terms

| term | reason |
| --- | --- |

## ASR Variants

| heard_or_typed | intended_term | target_entity_id | safe_as_alias | test_location |
| --- | --- | --- | --- | --- |

## Ambiguous Terms

| term | possible_targets | resolution |
| --- | --- | --- |
```

## GKP Lite Scaffold Contract

The scaffold is a reusable generator profile, not a finished pack. It should
emit valid file paths and intentionally invalid placeholder content so authors
must replace the placeholder markers before bundling.

`tools/gkp-builder/templates/gkp-lite/profile.yaml` must define:

```yaml
profile_id: gkp-lite
coverage_tier: lite
minimums:
  knowledge_rows_min: 20
  knowledge_rows_max: 60
  golden_rows_min: 20
  golden_rows_max: 40
  source_rows_min: 5
  localized_aliases_min: 40
  source_backed_localized_aliases_min: 25
  localized_goldens_min: 20
  pure_localized_goldens_min: 12
  core_gameplay_goldens_min: 4
  no_evidence_goldens_min: 3
required_lanes:
  - identity
  - core_gameplay
  - beginner_direction
  - mechanics
  - key_terms
  - common_stuck_points
  - spoiler_gates
  - sources
  - goldens
placeholder_marker: "__REPLACE_WITH_REVIEWED_GKP_DATA__"
source_policy:
  require_source_refs: true
  forbid_copied_walkthrough_prose: true
  forbid_rom_or_script_dump: true
runtime_policy:
  llm_required_for_required_goldens: false
  no_evidence_must_not_call_llm_for_facts: true
```

Every template file should use variable markers with this shape:

```text
{{game_slug}}
{{game_id}}
{{pack_id}}
{{display_title}}
{{platform}}
{{region}}
{{language}}
{{coverage_tier}}
{{generated_at}}
```

The generated starter rows should include the placeholder marker in user-facing
fields until an author replaces them with source-backed content. Coverage lint
must fail if any generated pack still contains
`__REPLACE_WITH_REVIEWED_GKP_DATA__`.

Expected generator command shape:

```bash
tools/gkp-builder/bin/gkp-builder new \
  --profile lite \
  --game-id golden_sun_gba \
  --pack-id community.golden-sun-gba-zh \
  --game "Golden Sun / 黄金太阳" \
  --platform gba \
  --language zh \
  --out retrosprite-android/app/src/main/assets/gkp/golden-sun-gba-zh
```

This plan requires the minimal scaffold generator before any of the five game
packs are authored. The broader RAG-Anything builder can still arrive later,
but Phase 1 pack directories should already be generated through the same Lite
profile.

## Generated Shared Chinese Manifest Shape

Use this shape for every Phase 1 Chinese `manifest.json`, replacing values per game. The `pack_id` and directory carry `-zh`; `game.game_id` stays language-neutral.

```json
{
  "schema_version": "gkp.v0",
  "pack_id": "community.golden-sun-gba-zh",
  "pack_version": "0.1.0",
  "coverage_tier": "lite",
  "default_language": "zh",
  "game": {
    "game_id": "golden_sun_gba",
    "title": "Golden Sun / 黄金太阳",
    "platform": "gba",
    "region": null,
    "languages": ["zh"],
    "retroarch_system_ids": ["gba", "game_boy_advance"],
    "retroarch_labels": ["gba__Golden Sun", "gba__黄金太阳"],
    "rom_identity": { "crc32": null, "sha1": null }
  },
  "trust_level": "community",
  "min_app_version": "0.1.0",
  "generated_at": "2026-05-24T00:00:00Z",
  "contents": {
    "knowledge": [
      "knowledge/production.jsonl",
      "knowledge/mechanics.jsonl",
      "knowledge/entities.jsonl",
      "knowledge/items.jsonl",
      "knowledge/locations.jsonl",
      "knowledge/bosses.jsonl",
      "knowledge/enemies.jsonl",
      "knowledge/strategies.jsonl"
    ],
    "citations": "sources/citations.jsonl",
    "aliases": "aliases.json",
    "spoiler_graph": "spoiler_graph.json",
    "qa_goldens": "qa_goldens.jsonl"
  }
}
```

## Task -1: Build GKP Lite Scaffold Contract

**Files:**

- Create: `tools/gkp-builder/templates/gkp-lite/profile.yaml`
- Create: `tools/gkp-builder/templates/gkp-lite/manifest.template.json`
- Create: `tools/gkp-builder/templates/gkp-lite/aliases.template.json`
- Create: `tools/gkp-builder/templates/gkp-lite/spoiler_graph.template.json`
- Create: `tools/gkp-builder/templates/gkp-lite/qa_goldens.template.jsonl`
- Create: `tools/gkp-builder/templates/gkp-lite/changelog.template.md`
- Create: `tools/gkp-builder/templates/gkp-lite/sources/citations.template.jsonl`
- Create: `tools/gkp-builder/templates/gkp-lite/sources/licenses.template.md`
- Create: `tools/gkp-builder/templates/gkp-lite/knowledge/production.template.jsonl`
- Create: `tools/gkp-builder/templates/gkp-lite/knowledge/mechanics.template.jsonl`
- Create: `tools/gkp-builder/templates/gkp-lite/knowledge/strategies.template.jsonl`
- Create: `tools/gkp-builder/templates/gkp-lite/knowledge/entities.template.jsonl`
- Create: `tools/gkp-builder/templates/gkp-lite/knowledge/items.template.jsonl`
- Create: `tools/gkp-builder/templates/gkp-lite/knowledge/locations.template.jsonl`
- Create: `tools/gkp-builder/templates/gkp-lite/knowledge/bosses.template.jsonl`
- Create: `tools/gkp-builder/templates/gkp-lite/knowledge/enemies.template.jsonl`
- Create: `tools/gkp-builder/README.md`
- Create: `tools/gkp-builder/bin/gkp-builder`
- Create: `tools/gkp-builder/scripts/gkp_builder_new.py`

- [x] Create the `gkp-lite` template directory and add `profile.yaml` using the exact minimums in `GKP Lite Scaffold Contract`.
- [x] Add `manifest.template.json` so it renders `schema_version`, `pack_id`, `pack_version`, `coverage_tier`, `default_language`, `game`, `trust_level`, `min_app_version`, `generated_at`, and `contents`.
- [x] Add `aliases.template.json` with an empty aliases list plus one placeholder alias object that contains `__REPLACE_WITH_REVIEWED_GKP_DATA__` and must be deleted before bundling.
- [x] Add `spoiler_graph.template.json` with coarse Lite gates: `start`, `early_game`, `mid_game`, `late_game`, `optional`, and `postgame_or_spoiler`.
- [x] Add `sources/citations.template.jsonl` with one placeholder citation row for `{{game_id}}.project_notes`.
- [x] Add `sources/licenses.template.md` with sections for official sources, community sources, localized-name sources, rejected sources, and reviewer notes.
- [x] Add knowledge templates with one placeholder row per required Lite lane:
  - `production.template.jsonl`: identity and production facts.
  - `mechanics.template.jsonl`: core loop, core gameplay, beginner direction.
  - `strategies.template.jsonl`: first-hour direction, low-spoiler route hint, no-evidence boundary.
  - `entities.template.jsonl`: key characters/NPCs or commanders.
  - `items.template.jsonl`: key items/equipment/system terms.
  - `locations.template.jsonl`: early locations and coarse route labels.
  - `bosses.template.jsonl`: early or representative boss concepts.
  - `enemies.template.jsonl`: common enemy buckets.
- [x] Add `qa_goldens.template.jsonl` with at least:
  - 4 core gameplay/fun-hook template goldens.
  - 3 no-evidence or clarification template goldens.
  - 1 spoiler downgrade template golden.
  - 4 localized-name/name-mapping template goldens.
- [x] Add `changelog.template.md` with `Coverage tier: {{coverage_tier}}`, generated scaffold note, known gaps, verification, and reviewer notes.
- [x] Ensure every generated placeholder row uses `__REPLACE_WITH_REVIEWED_GKP_DATA__` so coverage lint can reject unfinished packs.
- [x] Add `tools/gkp-builder/scripts/gkp_builder_new.py` as the minimal generator for this plan. It must:
  - accept the `new` subcommand;
  - require `--profile lite`, `--game-id`, `--pack-id`, `--game`, `--platform`, `--language`, and `--out`;
  - read templates from `tools/gkp-builder/templates/gkp-lite/`;
  - substitute the variables listed in `GKP Lite Scaffold Contract`;
  - write the rendered pack tree under `--out`;
  - refuse to overwrite an existing non-empty output directory unless `--force` is passed.
- [x] Add `tools/gkp-builder/bin/gkp-builder` as a thin launcher for the script so later tasks can run `tools/gkp-builder/bin/gkp-builder new ...` without relying on shell aliases.
- [x] Add `tools/gkp-builder/README.md` with the same `new --profile lite` command used by the five pack tasks and a note that generated placeholders must fail coverage lint before review.

## Task -1 Validation

Run these checks before Task 0:

```bash
test -f tools/gkp-builder/templates/gkp-lite/profile.yaml
find tools/gkp-builder/templates/gkp-lite -type f | sort
rg -n "__REPLACE_WITH_REVIEWED_GKP_DATA__|{{game_id}}|{{pack_id}}|{{coverage_tier}}" tools/gkp-builder/templates/gkp-lite
python3 tools/gkp-builder/scripts/gkp_builder_new.py new --help
tools/gkp-builder/bin/gkp-builder new --help
git diff --check -- tools/gkp-builder retrosprite-android/docs/superpowers/plans/2026-05-24-retro-jrpg-srpg-pack.md
```

Expected: all scaffold files exist, the template variables are present in the
template files, placeholder markers exist only in scaffold templates, and the
diff check passes.

## Task 0: Localized Name Baseline

**Files:**

- Create: `retrosprite-android/docs/gkp/skeletons/golden-sun-gba-entity-skeleton.md`
- Create: `retrosprite-android/docs/gkp/skeletons/phantasy-star-iv-md-entity-skeleton.md`
- Create: `retrosprite-android/docs/gkp/skeletons/langrisser-ii-md-entity-skeleton.md`
- Create: `retrosprite-android/docs/gkp/skeletons/chrono-trigger-snes-entity-skeleton.md`
- Create: `retrosprite-android/docs/gkp/skeletons/final-fantasy-vi-snes-entity-skeleton.md`
- Create: `retrosprite-android/docs/gkp/golden-sun-gba-localized-term-inventory.md`
- Create: `retrosprite-android/docs/gkp/phantasy-star-iv-md-localized-term-inventory.md`
- Create: `retrosprite-android/docs/gkp/langrisser-ii-md-localized-term-inventory.md`
- Create: `retrosprite-android/docs/gkp/chrono-trigger-snes-localized-term-inventory.md`
- Create: `retrosprite-android/docs/gkp/final-fantasy-vi-snes-localized-term-inventory.md`
- Create: `retrosprite-android/docs/gkp/retro-jrpg-srpg-pack-source-register.md`

- [x] Create all five entity skeleton files using the skeleton table above.
- [x] For each skeleton, define stable language-neutral rows for the minimum Phase 1 coverage:
  - 6-12 JRPG character/NPC rows or 8-14 SRPG commander/NPC rows.
  - 5-10 item/equipment/key-item/key-term rows.
  - 5-10 location/route rows.
  - 4-8 JRPG boss/enemy rows or 4-8 SRPG boss/enemy rows.
  - 4-8 core mechanics, beginner direction, and strategy buckets needed for broad player-intent questions.
  - a `future_expansion_backlog` note for omitted bosses, late locations, optional items, and route details.
- [x] Ensure every later `*-zh` knowledge row uses an `entity_id` already present in the skeleton, except temporary project-note rows that are explicitly added back to the skeleton in the same task.
- [x] Create all five localized-term inventory files using the inventory template above.
- [x] For each game, fill `Accepted Terms` before writing GKP rows:
  - title aliases
  - playable character aliases
  - major NPC aliases
  - item/equipment/key-item aliases
  - location/route aliases
  - boss/enemy aliases
  - class/spell/mechanic aliases
- [x] For each game, fill `Needs Source` with plausible Chinese/fan/localization names that should not ship yet.
- [x] For each game, fill `Rejected Generic Terms` with overbroad words such as `角色`, `道具`, `在哪里`, `怎么打`, `路线`, `法师`, and explain whether a longer phrase is acceptable.
- [x] For each game, fill `ASR Variants` with at least 20 candidates and mark whether each should be:
  - direct `aliases.json` entry
  - `GkpAsrHotwordExtractorTest` fixture only
  - `GameTermNormalizerTest` rewrite
  - rejected because ambiguous
- [x] For each game, fill `Ambiguous Terms` for version/title conflicts:
  - Golden Sun: `精灵` can mean Djinn broadly or a specific creature depending on context.
  - Phantasy Star IV: transliterated names can collide across party members and planets.
  - Langrisser II: route words such as `光辉线` and `帝国线` should not imply an exact scenario without progress context.
  - Chrono Trigger: `时空之轮` and `超时空之钥` are title aliases; `魔王` may be a character name and a generic role word.
  - Final Fantasy VI: `最终幻想VI`, `太空战士VI`, and `FFIII` can refer to version/region naming; `魔石` and `幻兽` need separate concepts.
- [x] Add source-register sections for localized-name sources:
  - known Chinese patch pages where available
  - Chinese wiki/name-list pages where licensing permits factual reference
  - project-authored localized-name audit notes when no single reliable external list exists
  - rejected sources such as ROM script dumps, full walkthrough text dumps, or copied manual scans
- [x] Add at least 12 planned pure-Chinese golden questions per inventory before implementing pack data, with 20 planned if source coverage is already strong. These are planning rows, not final `qa_goldens.jsonl` yet.
- [x] Add a `Future Language Packs` section to each skeleton with expected later package ids:
  - `<pack-slug>-en`
  - `<pack-slug>-ja`
  - `<pack-slug>-ko`
  and note that they must reuse the same `entity_id` values.

## Task 0 Validation

Run these checks before Task 1:

```bash
rg -n "entity_id \\| entity_type \\| canonical_en \\| canonical_zh" retrosprite-android/docs/gkp/skeletons/*-entity-skeleton.md
rg -n "## Accepted Terms|## ASR Variants|## Ambiguous Terms" retrosprite-android/docs/gkp/*-localized-term-inventory.md
rg -n "在哪里|怎么打|角色|道具" retrosprite-android/docs/gkp/*-localized-term-inventory.md
git diff --check -- retrosprite-android/docs/gkp
```

Expected: every skeleton has the required table; every inventory has the required sections; broad terms appear only under `Rejected Generic Terms`, `ASR Variants`, or explanatory notes.

## Task 1: Build Shared Coverage Contract

**Files:**

- Create: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/gkp/RetroJrpgSrpgPackCoverageTest.kt`
- Modify: `retrosprite-android/docs/gkp/retro-jrpg-srpg-pack-source-register.md`
- Create: `retrosprite-android/docs/gkp/retro-jrpg-srpg-pack-coverage-report.md`

- [x] Create a test that loads each of the five Phase 1 Chinese pack dirs and enforces minimums:
  - pack dirs: `golden-sun-gba-zh`, `phantasy-star-iv-md-zh`, `langrisser-ii-md-zh`, `chrono-trigger-snes-zh`, `final-fantasy-vi-snes-zh`
  - `pack_id` ends with `-zh`
  - `coverage_tier == "lite"` when the field exists, and missing `coverage_tier` is reported as a warning until schema support is finalized
  - `default_language == "zh"`
  - `game.game_id` is the stable language-neutral id from the pack table
  - every knowledge row `language` is omitted or equals `zh`
  - knowledge rows >= 20 and <= 60 unless `coverage_tier` is explicitly promoted to `expanded`
  - golden rows >= 20 and <= 40 unless `coverage_tier` is explicitly promoted to `expanded`
  - source rows >= 5
  - localized aliases >= 40
  - source-backed localized proper-name aliases >= 25
  - localized goldens >= 20
  - pure Chinese/localized-name goldens >= 12
  - core gameplay/fun-hook goldens >= 4
  - no-evidence/clarification goldens >= 3
  - no file under the pack contains `__REPLACE_WITH_REVIEWED_GKP_DATA__`
  - no dangling source refs
  - no dangling aliases
  - no dangling golden entity ids
- [x] Run the new test before any packs exist.

Run:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.gkp.RetroJrpgSrpgPackCoverageTest
```

Expected: fail because the five pack directories do not exist yet.

- [x] Add `retro-jrpg-srpg-pack-source-register.md` with one section per game:
  - official sources
  - community factual cross-check sources
  - localized-name sources
  - rejected or unsafe sources
- [x] Add `retro-jrpg-srpg-pack-coverage-report.md` with an empty per-pack metric table that will be updated after each pack.
- [x] Add a skeleton consistency check to `RetroJrpgSrpgPackCoverageTest`:
  - load the matching `docs/gkp/skeletons/<pack-slug>-entity-skeleton.md`
  - parse the first column values that look like `entity_id`
  - assert every GKP `entity_id` appears in the skeleton
  - assert no skeleton `entity_id` uses CJK characters

## Task 2: Golden Sun / 黄金太阳 Pack

**Files:**

- Generate scaffolded files under: `retrosprite-android/app/src/main/assets/gkp/golden-sun-gba-zh/`
- Consume: `retrosprite-android/docs/gkp/skeletons/golden-sun-gba-entity-skeleton.md`
- Consume: `retrosprite-android/docs/gkp/golden-sun-gba-localized-term-inventory.md`
- Add test coverage through shared coverage and retrieval tests.

- [x] Generate the pack from the GKP Lite scaffold before editing content:

```bash
tools/gkp-builder/bin/gkp-builder new \
  --profile lite \
  --game-id golden_sun_gba \
  --pack-id community.golden-sun-gba-zh \
  --game "Golden Sun / 黄金太阳" \
  --platform gba \
  --language zh \
  --out retrosprite-android/app/src/main/assets/gkp/golden-sun-gba-zh
```

- [x] Replace every `__REPLACE_WITH_REVIEWED_GKP_DATA__` marker with reviewed, source-backed content before validation.
- [x] Add sources:
  - Nintendo official manual PDF.
  - Golden Sun Wiki pages for `Psynergy`, `Djinn`, `Class`, character roster, locations, bosses.
  - Project notes source for original low-spoiler tactical summaries.
  - Localized-name source or project-maintained localized-name audit for Chinese terms.
- [x] Convert accepted localized inventory terms into GKP rows and aliases before writing broad gameplay rows:
  - title aliases: `黄金太阳`, `Golden Sun`, Japanese title variants if source-backed.
  - system aliases: `Djinn`, `精灵`, `Psynergy`, `精神力`, `念力`, `职业`, `召唤`.
  - character aliases: `伊萨克`, `加雷特`, `伊万`, `米娅`, plus source-backed alternates.
  - location aliases: `索尔神殿`, `水星灯塔`, `金星灯塔`, and other source-backed names.
  - reject or bucket ambiguous `精灵` questions unless the question clearly means Djinn as a mechanic.
- [x] Use only skeleton-defined entity ids such as `npc.isaac`, `mechanic.djinn`, `mechanic.psynergy`, `location.mercury-lighthouse`, and `boss.saturos`; update the skeleton in the same task if a new entity is required.
- [x] Create knowledge rows:
  - Production identity and platform labels.
  - Core gameplay: turn-based battle, Psynergy, Djinn, summons, classes, puzzle Psynergy.
  - Characters: Isaac, Garet, Ivan, Mia, Jenna, Felix, Kraden, Saturos, Menardi, Alex, key NPCs.
  - Items/equipment: common healing items, Psynergy-granting items, key items, Djinn-related concepts, weapons/armor buckets.
  - Locations/routes: Vale, Sol Sanctum, Vault, Kolima, Mercury Lighthouse, Venus Lighthouse, key towns/dungeons.
  - Bosses/enemies: early bosses, lighthouse bosses, mimic/chest threats, common enemy archetypes.
  - Strategies: beginner direction, Djinn assignment basics, class confusion, puzzle spell reminders, no-spoiler route hints.
- [x] Add aliases:
  - English names, Chinese names, Japanese title variants where useful.
  - `Djinn`, `精灵`, `金星精灵`, `火星精灵`, `Psynergy`, `精神力`, `念力`, `伊萨克`, `加雷特`, `伊万`, `米娅`.
  - ASR-prone variants such as `迪金`, `金太阳`, `黄金太郎`, `精神利`, `赛能量` only when they are observed/plausible and unambiguous.
- [x] Add 24-40 Lite goldens:
  - 8+ system/mechanics questions.
  - 4+ core gameplay/fun-hook or "适合谁玩" questions.
  - 4+ character questions.
  - 4+ location/route questions.
  - 3+ item/equipment questions.
  - 3+ boss/enemy questions.
  - 3+ no-evidence/clarification questions.
  - At least 12 questions must be pure Chinese/localized-name questions with no English proper nouns.
- [x] Validate:

```bash
node /Users/kartz/.codex/skills/retrosprite-gkp-production/scripts/audit_gkp_pack.js app/src/main/assets/gkp/golden-sun-gba-zh
node /Users/kartz/.codex/skills/retrosprite-gkp-production/scripts/check_localized_terms.js app/src/main/assets/gkp/golden-sun-gba-zh
node /Users/kartz/.codex/skills/retrosprite-gkp-production/scripts/coverage_report.js app/src/main/assets/gkp/golden-sun-gba-zh --pilot knowledge_rows=20 goldens=20 localized_aliases=40 localized_goldens=20
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.gkp.GkpV0FixtureLintTest --tests com.retrosprite.app.gkp.RetroJrpgSrpgPackCoverageTest
```

## Task 3: Phantasy Star IV / 梦幻之星 IV Pack

**Files:**

- Generate scaffolded files under: `retrosprite-android/app/src/main/assets/gkp/phantasy-star-iv-md-zh/`
- Consume: `retrosprite-android/docs/gkp/skeletons/phantasy-star-iv-md-entity-skeleton.md`
- Consume: `retrosprite-android/docs/gkp/phantasy-star-iv-md-localized-term-inventory.md`

- [x] Generate the pack from the GKP Lite scaffold before editing content:

```bash
tools/gkp-builder/bin/gkp-builder new \
  --profile lite \
  --game-id phantasy_star_iv_md \
  --pack-id community.phantasy-star-iv-md-zh \
  --game "Phantasy Star IV / 梦幻之星 IV" \
  --platform md \
  --language zh \
  --out retrosprite-android/app/src/main/assets/gkp/phantasy-star-iv-md-zh
```

- [x] Replace every `__REPLACE_WITH_REVIEWED_GKP_DATA__` marker with reviewed, source-backed content before validation.
- [x] Add sources:
  - Sega official Mega Drive Mini page.
  - MobyGames or equivalent metadata source.
  - Phantasy Star community wiki pages for characters, techniques, planets, enemies, bosses.
  - Project source for Chinese localized-name audit.
- [x] Convert accepted localized inventory terms into GKP rows and aliases first:
  - title aliases: `梦幻之星IV`, `梦幻之星4`, `千年纪的终结`, `千年纪`.
  - character aliases: `查兹`, `艾莉丝`, `莱卡`, `鲁恩`, `瑞卡`, `弗伦`, plus source-backed alternates.
  - planet/location aliases: `莫塔维亚`, `德佐利斯`, and source-backed town/dungeon names.
  - mechanic aliases: `Technique`, `技巧`, `技能`, `宏命令`, `组合`.
  - put uncertain transliterations in `Needs Source`, not `aliases.json`.
- [x] Use only skeleton-defined entity ids such as `npc.chaz`, `npc.alys`, `mechanic.techniques`, `location.motavia`, and `location.dezolis`; update the skeleton in the same task if a new entity is required.
- [x] Create knowledge rows:
  - Identity, MD labels, Japanese/English title aliases.
  - Core systems: turn-based combat, Techniques, Skills, Macros, vehicles, party roles.
  - Characters: Chaz, Alys, Hahn, Rune, Rika, Wren, Demi, Raja, Gryz, Kyra, Seth, key NPCs.
  - Locations: Motavia, Dezolis, towns, dungeons, major route gates.
  - Items/equipment: healing items, technique-restoring items, weapons/armor buckets, key items.
  - Bosses/enemies: major boss names, biological/mechanical enemy buckets.
  - Strategies: early party guidance, macro basics, technique use, low-spoiler route questions.
- [x] Add Chinese aliases and ASR variants for:
  - `梦幻之星`, `千年纪`, `查兹`, `艾莉丝`, `莱卡`, `鲁恩`, `莫塔维亚`, `德佐利斯`.
- [x] Add 24-40 Lite goldens with emphasis on natural Chinese player questions and sci-fi proper nouns; at least 12 must use only Chinese/localized names, and at least 3 must be no-evidence/clarification cases for uncertain transliterations or planet names.
- [x] Validate with the same scripts and shared tests.

## Task 4: Langrisser II / 梦幻模拟战 II Pack

**Files:**

- Generate scaffolded files under: `retrosprite-android/app/src/main/assets/gkp/langrisser-ii-md-zh/`
- Consume: `retrosprite-android/docs/gkp/skeletons/langrisser-ii-md-entity-skeleton.md`
- Consume: `retrosprite-android/docs/gkp/langrisser-ii-md-localized-term-inventory.md`

- [x] Generate the pack from the GKP Lite scaffold before editing content:

```bash
tools/gkp-builder/bin/gkp-builder new \
  --profile lite \
  --game-id langrisser_ii_md \
  --pack-id community.langrisser-ii-md-zh \
  --game "Langrisser II / 梦幻模拟战 II" \
  --platform md \
  --language zh \
  --out retrosprite-android/app/src/main/assets/gkp/langrisser-ii-md-zh
```

- [x] Replace every `__REPLACE_WITH_REVIEWED_GKP_DATA__` marker with reviewed, source-backed content before validation.
- [x] Add sources:
  - Sega official Chinese Mega Drive Mini page.
  - Sega-16 article for genre/context cross-checking.
  - Langrisser community wiki/source pages for commanders, classes, items, stages.
  - Localized-name audit source for Chinese terms.
- [x] Convert accepted localized inventory terms into GKP rows and aliases first:
  - title aliases: `梦幻模拟战II`, `梦幻模拟战2`, `兰古利萨II`, `Langrisser II`.
  - commander aliases: `艾尔文`, `海恩`, `莉亚娜`, `雪莉`, `雷昂`, `伯恩哈特`, `波赞鲁`, `杰西卡`.
  - route aliases: `光辉线`, `帝国线`, `独立线`, `黑暗线` only as route concepts with spoiler gates, not direct scenario answers.
  - system aliases: `佣兵`, `指挥范围`, `转职`, `兵种相克`, `地形`.
- [x] Use only skeleton-defined entity ids such as `npc.elwin`, `npc.heine`, `mechanic.mercenaries`, `route.light`, and `route.empire`; update the skeleton in the same task if a new entity is required.
- [x] Create knowledge rows:
  - Identity, MD labels, Chinese title aliases.
  - Core systems: commanders, mercenaries, command range, class change, terrain, route choices.
  - Characters/commanders: Elwin, Hein, Liana, Cherie, Leon, Bernhardt, Boser, Jessica, key allies/enemies.
  - Items/equipment: class-change items, weapons, defensive gear, consumables/buckets.
  - Locations/routes: early scenarios, kingdoms, route branches, castles, battle maps.
  - Bosses/enemies: enemy commanders, monster groups, named antagonists.
  - Strategies: beginner deployment, mercenary matching, formation, route-spoiler handling.
- [x] Add Chinese aliases and ASR variants for:
  - `梦幻模拟战`, `兰古利萨`, `艾尔文`, `海恩`, `莉亚娜`, `雪莉`, `雷昂`, `帝国线`, `光辉线`.
- [x] Add 24-40 Lite goldens:
  - At least 6 commander/class questions.
  - At least 5 stage/route questions with spoiler levels.
  - At least 6 mercenary/system questions.
  - At least 4 core gameplay/fun-hook questions.
  - At least 3 route-choice clarification/no-evidence cases.
  - At least 12 questions must use Chinese/localized names only.
- [x] Validate with scripts and shared tests.

## Task 5: Chrono Trigger / 时空之轮 Pack

**Files:**

- Generate scaffolded files under: `retrosprite-android/app/src/main/assets/gkp/chrono-trigger-snes-zh/`
- Consume: `retrosprite-android/docs/gkp/skeletons/chrono-trigger-snes-entity-skeleton.md`
- Consume: `retrosprite-android/docs/gkp/chrono-trigger-snes-localized-term-inventory.md`

- [x] Generate the pack from the GKP Lite scaffold before editing content:

```bash
tools/gkp-builder/bin/gkp-builder new \
  --profile lite \
  --game-id chrono_trigger_snes \
  --pack-id community.chrono-trigger-snes-zh \
  --game "Chrono Trigger / 时空之轮" \
  --platform snes \
  --language zh \
  --out retrosprite-android/app/src/main/assets/gkp/chrono-trigger-snes-zh
```

- [x] Replace every `__REPLACE_WITH_REVIEWED_GKP_DATA__` marker with reviewed, source-backed content before validation.
- [x] Add sources:
  - Square Enix official Chrono Trigger page.
  - Community wiki pages for characters, eras, techs, bosses, locations.
  - Localized-name audit source for Chinese names.
- [x] Convert accepted localized inventory terms into GKP rows and aliases first:
  - title aliases: `时空之轮`, `超时空之钥`, `Chrono Trigger`.
  - character aliases: `克罗诺`, `玛尔`, `露卡`, `青蛙`, `罗伯`, `艾拉`, `魔王`.
  - era/location aliases: `中世`, `未来`, `原始`, `古代`, `时间尽头` and source-backed variants.
  - mechanic aliases: `技`, `双人技`, `三人技`, `时间旅行`, `多结局`, with spoiler gates.
  - `魔王` must stay specific only when the row/question context points to the character, not generic demon/boss language.
- [x] Use only skeleton-defined entity ids such as `npc.crono`, `npc.marle`, `mechanic.techs`, `location.end-of-time`, and `npc.magus`; update the skeleton in the same task if a new entity is required.
- [x] Create knowledge rows:
  - Identity, SNES/SFC labels, title variants.
  - Core systems: active time battle, Techs, Dual/Triple Techs, time travel, New Game+ as spoiler-gated.
  - Characters: Crono, Marle, Lucca, Frog, Robo, Ayla, Magus, key NPCs.
  - Locations/eras: 1000 AD, 600 AD, 2300 AD, 65,000,000 BC, Antiquity, End of Time, major towns/dungeons.
  - Items/equipment: healing, tabs, key items, weapon buckets.
  - Bosses/enemies: major bosses, common enemy archetypes, optional boss content gated.
  - Strategies: beginner route, tech pairing, boss difficulty, low-spoiler exploration.
- [x] Add Chinese aliases and ASR variants:
  - `时空之轮`, `超时空之钥`, `克罗诺`, `玛尔`, `露卡`, `青蛙`, `机器人`, `魔王`.
- [x] Add 24-40 Lite goldens with strict spoiler layers for late characters, endings, and optional content; at least 12 must use Chinese/localized names only, and late/endgame questions should prefer clarification or no-evidence unless the Lite row is deliberately included.
- [x] Validate with scripts and shared tests.

## Task 6: Final Fantasy VI / 最终幻想 VI Pack

**Files:**

- Generate scaffolded files under: `retrosprite-android/app/src/main/assets/gkp/final-fantasy-vi-snes-zh/`
- Consume: `retrosprite-android/docs/gkp/skeletons/final-fantasy-vi-snes-entity-skeleton.md`
- Consume: `retrosprite-android/docs/gkp/final-fantasy-vi-snes-localized-term-inventory.md`

- [x] Generate the pack from the GKP Lite scaffold before editing content:

```bash
tools/gkp-builder/bin/gkp-builder new \
  --profile lite \
  --game-id final_fantasy_vi_snes \
  --pack-id community.final-fantasy-vi-snes-zh \
  --game "Final Fantasy VI / 最终幻想 VI" \
  --platform snes \
  --language zh \
  --out retrosprite-android/app/src/main/assets/gkp/final-fantasy-vi-snes-zh
```

- [x] Replace every `__REPLACE_WITH_REVIEWED_GKP_DATA__` marker with reviewed, source-backed content before validation.
- [x] Add sources:
  - Square Enix official FFVI/Pixel Remaster pages.
  - Official manuals where available.
  - Community wiki pages for characters, Espers, commands, locations, bosses.
  - Localized-name audit source for Chinese names.
- [x] Convert accepted localized inventory terms into GKP rows and aliases first:
  - title aliases: `最终幻想VI`, `最终幻想6`, `太空战士VI`, `太空战士6`, `FFVI`, `FFIII`.
  - character aliases: `蒂娜`, `洛克`, `艾德加`, `马修`, `塞丽丝`, `凯夫卡`, plus source-backed alternates.
  - system aliases: `魔石`, `幻兽`, `饰品`, `特技`, `必杀`, `青魔法`, `狂暴`.
  - route/world aliases: `平衡世界`, `崩坏世界` must be spoiler-gated.
  - `FFIII` must route to a naming clarification row, not silently assume all versions.
- [x] Use only skeleton-defined entity ids such as `npc.terra`, `npc.locke`, `mechanic.magicite`, `mechanic.relics`, and `topic.ffiii-naming`; update the skeleton in the same task if a new entity is required.
- [x] Create knowledge rows:
  - Identity, SNES/SFC labels, FFIII/FFVI naming clarification.
  - Core systems: ATB, Magicite/Espers, character commands, relics, status effects.
  - Characters: Terra, Locke, Edgar, Sabin, Celes, Cyan, Gau, Setzer, Shadow, Relm, Strago, Mog, Gogo, Umaro, Kefka, key NPCs.
  - Items/equipment: relic buckets, recovery items, Magicite, weapons/armor buckets, key items.
  - Locations/routes: Narshe, Figaro, South Figaro, Returners, Vector, World of Balance/Ruin gated terms.
  - Bosses/enemies: early bosses, Imperial enemies, Esper-related bosses, late-game names gated.
  - Strategies: character role guidance, relic basics, Magicite teaching, low-spoiler route direction.
- [x] Add Chinese aliases and ASR variants:
  - `最终幻想6`, `太空战士6`, `蒂娜`, `洛克`, `艾德加`, `马修`, `塞丽丝`, `凯夫卡`, `魔石`, `幻兽`.
- [x] Add 24-40 Lite goldens:
  - At least 6 character/role questions.
  - At least 6 Magicite/relic/mechanic questions.
  - At least 4 location/route questions.
  - At least 4 boss/enemy/item questions.
  - At least 4 core gameplay/fun-hook questions.
  - At least 3 spoiler/no-evidence/FFIII naming clarification cases.
  - At least 12 questions must use Chinese/localized names only.
- [x] Validate with scripts and shared tests.

## Task 7: Shared Retrieval Golden Test

**Files:**

- Create: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/data/retrieval/RetroJrpgSrpgPackRetrievalGoldenTest.kt`

- [x] Implement a parameterized-style test that loops through the five pack dirs.
- [x] For each pack, parse `manifest.json` and all `knowledge/*.jsonl` through `GkpV0Parser`.
- [x] Load `qa_goldens.jsonl`.
- [x] Normalize question with existing `LocalKnowledgeRetrievalPipeline.normalizeQuestion`.
- [x] Run local retrieval and assert every non-empty `expected_entity_ids` set is present in the top 5 results.
- [x] For empty expected ids, assert no evidence or a NoEvidence policy path, matching existing Shining Force II test style.
- [x] Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.data.retrieval.RetroJrpgSrpgPackRetrievalGoldenTest
```

Expected: pass after each pack's goldens are internally consistent.

## Task 8: ASR Hotword And Normalization Coverage

**Files:**

- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/voice/asr/GkpAsrHotwordExtractorTest.kt`
- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/domain/normalization/GameTermNormalizerTest.kt`

- [x] Add one hotword fixture per pack with 5-8 high-value localized names.
- [x] Assert localized proper nouns score above English canonical terms when both exist.
- [x] Add normalizer tests only for unambiguous ASR mistakes:
  - Golden Sun: `精灵`/`迪金` only if mapped to a broad Djinn concept, not a specific Djinn.
  - Phantasy Star IV: transliterated names only when no competing entity exists.
  - Langrisser II: route words such as `光辉线` should not rewrite into a specific route unless explicit.
  - Chrono Trigger: `克罗诺` variants should not rewrite to `Chrono Trigger` title row.
  - FFVI: `魔石`/`魔兽` ambiguity must be guarded.
- [x] Run ASR tests:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.voice.asr.GkpAsrHotwordExtractorTest --tests com.retrosprite.app.domain.normalization.GameTermNormalizerTest
```

## Task 9: Batch Validation And Release Readiness

**Files:**

- Modify: `retrosprite-android/docs/gkp/retro-jrpg-srpg-pack-coverage-report.md`
- Modify if needed: `retrosprite-android/docs/GKP_V0_SCHEMA.md`
- Modify if needed: `retrosprite-android/docs/NEXT_IMPLEMENTATION_PLAN.md`

- [x] Run all skill scripts for each pack:

```bash
for pack in golden-sun-gba-zh phantasy-star-iv-md-zh langrisser-ii-md-zh chrono-trigger-snes-zh final-fantasy-vi-snes-zh; do
  node /Users/kartz/.codex/skills/retrosprite-gkp-production/scripts/audit_gkp_pack.js "app/src/main/assets/gkp/$pack"
  node /Users/kartz/.codex/skills/retrosprite-gkp-production/scripts/check_localized_terms.js "app/src/main/assets/gkp/$pack"
  node /Users/kartz/.codex/skills/retrosprite-gkp-production/scripts/coverage_report.js "app/src/main/assets/gkp/$pack" --pilot knowledge_rows=20 goldens=20 localized_aliases=40 localized_goldens=20
  ! rg -n "__REPLACE_WITH_REVIEWED_GKP_DATA__" "app/src/main/assets/gkp/$pack"
done
```

- [x] Run Android tests:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest \
  --tests com.retrosprite.app.gkp.GkpV0FixtureLintTest \
  --tests com.retrosprite.app.gkp.RetroJrpgSrpgPackCoverageTest \
  --tests com.retrosprite.app.data.retrieval.RetroJrpgSrpgPackRetrievalGoldenTest \
  --tests com.retrosprite.app.voice.asr.GkpAsrHotwordExtractorTest \
  --tests com.retrosprite.app.domain.normalization.GameTermNormalizerTest
```

- [x] Run whitespace check:

```bash
git diff --check -- retrosprite-android/app/src/main/assets/gkp retrosprite-android/app/src/test/kotlin/com/retrosprite/app retrosprite-android/docs
```

- [x] Update the coverage report with actual counts for each pack.
- [x] Record known gaps honestly, especially:
  - uncertain localized names
  - spoiler-gated route content intentionally omitted
  - ASR variants that remain unsafe to normalize
  - sources that are only community reliability
  - Chinese aliases that were rejected for being too broad
  - pure Chinese golden questions that intentionally expect clarification or no evidence

## Definition Of Done

- `tools/gkp-builder/templates/gkp-lite/` exists with `profile.yaml`, manifest, alias, spoiler graph, source, knowledge, golden, and changelog templates.
- Five Phase 1 Chinese pack directories were generated from the GKP Lite scaffold and are referenced by bundled asset discovery without schema changes: `golden-sun-gba-zh`, `phantasy-star-iv-md-zh`, `langrisser-ii-md-zh`, `chrono-trigger-snes-zh`, and `final-fantasy-vi-snes-zh`.
- Each game has a language-neutral entity skeleton that future `en`, `ja`, and `ko` packs can reuse.
- Each Chinese pack uses the stable base `game_id` without a language suffix.
- Each Chinese pack has `pack_id` ending in `-zh`, `default_language = "zh"`, and only Chinese answer templates/goldens except explicit name-mapping cases.
- Each Chinese pack is marked or reported as `coverage_tier: lite`.
- Each Chinese pack has 20-60 reviewed knowledge rows, unless it is intentionally promoted to `expanded`.
- Each Chinese pack has 20-40 golden questions, unless it is intentionally promoted to `expanded`.
- Each Chinese pack has a localized-term inventory reviewed before GKP rows are written.
- Each Chinese pack has source refs, spoiler levels, aliases, localized terms, and ASR-prone variants.
- Each Chinese pack has at least 25 source-backed localized proper-name aliases or a documented, user-approved exception.
- Each Chinese pack has at least 12 pure Chinese/localized-name golden questions.
- Each Chinese pack has at least 4 core gameplay/fun-hook goldens and at least 3 no-evidence/clarification goldens.
- No shipped pack contains `__REPLACE_WITH_REVIEWED_GKP_DATA__`.
- Shared coverage, fixture lint, retrieval golden, and ASR hotword tests pass.
- Every Chinese pack has `changelog.md` with `0.1.0`.
- `retro-jrpg-srpg-pack-coverage-report.md` lists final counts and known gaps.
- No Android runtime model expansion, no runtime RAG-Anything dependency, and no copied guide/manual prose.
- The plan explicitly defers multi-language simultaneous installation until runtime pack-language selection exists.

## Execution Recommendation

Implement in this order:

1. GKP Lite scaffold/profile/templates.
2. Language-neutral entity skeletons and localized name baseline inventories.
3. Shared tests and docs.
4. Golden Sun.
5. Phantasy Star IV.
6. Langrisser II.
7. Chrono Trigger.
8. Final Fantasy VI.
9. Batch validation.

This deliberately starts with a system-rich but manageable GBA JRPG, then one MD JRPG, then one MD SRPG, and only then moves to the two largest SNES benchmark packs.

## Deferred Phase: Expanded Chinese Packs

Do not block Phase 1 on these targets. Promote a pack from `lite` to `expanded`
only after the Lite release has real no-evidence/failure data or a user-approved
content slice.

Expanded candidates:

- add 60-150 reviewed knowledge rows per active game;
- raise golden coverage to 40-100 rows;
- add 120+ localized/CJK aliases only when the source inventory can support them;
- expand boss/enemy coverage, route branches, hidden/optional item locations,
  and late-game chapters as separate spoiler-gated slices;
- preserve the same `game_id`, `entity_id`, `source_id`, and spoiler gates from
  the Lite skeleton;
- update `coverage_tier` and `changelog.md` when a pack is promoted.

## Deferred Phase: EN/JA/KO Packs

Do not implement these in this plan, but preserve the skeleton contract so the next plans can do them cleanly:

- `Retro JRPG/SRPG Pack EN`: English answer templates, English intent/golden coverage, English ASR variants, same entity ids.
- `Retro JRPG/SRPG Pack JA`: Japanese names, kana/kanji aliases, Japanese question patterns, same entity ids.
- `Retro JRPG/SRPG Pack KO`: Korean names, Hangul aliases, Korean question patterns, same entity ids.

Before shipping more than one language pack for the same `game_id`, implement runtime language selection:

- user answer-language setting separate from UI language
- resolver chooses pack by `game_id + language`
- repository/index can hold multiple language packs without destructive overwrite
- retrieval filters row/template language
- answer composer emits language-appropriate no-evidence and clarification text
