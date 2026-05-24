# GKP Lite And Optional LLM Direction

> Date: 2026-05-24
> Status: product and architecture direction
> Scope: next-stage RetroSprite development after the Hotkey Voice Overlay and zero-LLM GKP milestones.

## 1. Decision

RetroSprite should move from "full GKP for every supported game" to:

```text
GKP Lite local baseline
  + optional player-configured LLM enhancement
  + evidence gate and low-spoiler policy
  + offline fallback that remains useful without LLM
```

The project should keep the current LLM settings and BYOK model. Players choose
whether to use an LLM, which provider to use, and which model/key/base URL to
configure. The app must remain useful when no LLM is configured, no network is
available, or the player disables the provider.

The durable product promise becomes:

> RetroSprite works offline from a lightweight local game knowledge anchor. If
> the player enables an LLM, the LLM makes answers more natural, multilingual,
> and context-aware, but it does not become the factual source for game-specific
> claims.

## 2. Why The Direction Changes

The earlier "GKP as complete game knowledge package" framing does not scale.
Retro games have too many regions, ports, translations, routes, hidden items,
bosses, characters, and community conventions. Requiring a complete GKP for
every game would turn RetroSprite into a content-production project before the
core assistant experience is proven.

The better split is:

- GKP Lite supplies trusted anchors: identity, aliases, canonical terms, core
  loop, common mechanics, beginner direction, spoiler gates, and source ids.
- Local retrieval remains the default answer path for deterministic questions.
- LLM is an optional composer and language bridge, used only inside a policy
  boundary.
- Offline builder tooling can generate GKP Lite candidates, but the Android app
  ships only reviewed plain data.

This keeps RetroSprite reliable and inspectable while making it feel more
intelligent when the player chooses to enable a model.

## 3. Runtime Modes

### Mode A: No LLM Configured

This is the required baseline.

```text
Hotkey / text question
  -> language and term normalization
  -> game resolver
  -> GKP Lite template / alias / entity / FTS retrieval
  -> AnswerPolicy
  -> direct answer, local summary, clarification, or no-evidence response
```

Behavior:

- high-confidence template/entity hits answer locally;
- local answers include source ids when available;
- multi-evidence answers use deterministic local summarization;
- no-evidence questions do not guess;
- the user can still ask by text or voice when offline.

Good for:

- "什么时候转职？"
- "这个道具有什么用？"
- "这游戏主要玩什么？"
- "新手先干什么？"
- "不要剧透，下一步给我方向。"

### Mode B: LLM Enabled By Player

The LLM makes the assistant more flexible but remains evidence-gated.

```text
Hotkey / text question
  -> query understanding / rewrite
  -> GKP Lite retrieval
  -> evidence quality gate
  -> optional LLM composition / translation / clarification
  -> answer with source ids and diagnostics
```

Allowed LLM jobs:

- rewrite noisy voice transcripts into a cleaner query;
- map English, Chinese, romaji, abbreviations, and localized names to canonical
  GKP terms;
- synthesize multiple evidence rows into a short spoken answer;
- translate or localize the answer language;
- turn safe evidence into a lower-spoiler hint;
- ask a better clarification question when progress/version/location is missing.

Disallowed LLM jobs:

- invent exact game facts without GKP or another trusted source;
- bypass spoiler policy;
- answer hidden item locations, routes, boss weaknesses, endings, or character
  outcomes from model memory;
- silently turn generic advice into a game-specific claim.

### Mode C: No GKP For The Current Game

If the current game has no GKP Lite, RetroSprite may offer a clearly marked
generic mode only when the player enables an LLM or generic help setting.

Allowed generic help:

- explain how to ask a better question;
- summarize the kind of info RetroSprite needs, such as current area, chapter,
  item name, or party;
- give genre-level advice without claiming game-specific certainty;
- help with menu wording, translation, or controller terms when evidence is not
  required.

Disallowed generic help:

- exact route steps;
- hidden item locations;
- boss-specific weaknesses;
- story facts;
- missable-content warnings that are not grounded in the current game.

Generic mode must be visibly labeled in UI/diagnostics, for example
`pipeline_stage=generic_ungrounded`.

## 4. GKP Lite Definition

GKP Lite is not a full walkthrough. It is a small, source-cited, testable game
anchor that lets RetroSprite answer common in-play questions and safely route
harder ones.

Minimum useful GKP Lite coverage:

| Lane | Required Content |
| --- | --- |
| Identity | title, platform/core, region/version note, observed RetroArch labels |
| Aliases | Chinese/English title variants, abbreviations, ASR-prone variants |
| Core loop | what the player mainly does, why it is fun, who it suits |
| Beginner direction | first-hour goals, safe low-spoiler route hints |
| Core mechanics | combat/movement/progression/resource basics |
| Key terms | localized names, English names, item/character/system glossary |
| Common stuck points | a few high-frequency early questions, not a full route |
| Spoiler gates | coarse progress gates plus light/clear/direct answer levels |
| Sources | stable source ids and reliability labels |
| Goldens | natural voice/text questions, no-evidence cases, spoiler regressions |

Recommended first slice size:

- 20 to 60 reviewed knowledge rows;
- 20 to 40 golden questions;
- 1 to 3 source ids per row;
- 0 copied guide paragraphs;
- no ROM data, save data, executable code, or commercial guidebook text.

Deep packs are still allowed later. They should be incremental expansions on
top of GKP Lite, not the requirement for first support.

## 5. Language Strategy

Do not duplicate a whole GKP for every player language.

Use a language-neutral game skeleton plus localized surfaces:

```text
game_id: shining_force_ii_md
entity_id: mechanic.promotion
source_id: sf2.project_mechanics
spoiler_gate: early_game

localized surfaces:
  zh aliases/templates/goldens
  en aliases/templates/goldens
  ja/romaji aliases later if needed
```

Near-term product requirements:

- separate UI language from answer language;
- add an answer-language setting later, independent from app UI locale;
- let retrieval filter row/template language where possible;
- let LLM translate or compose in the selected answer language when evidence is
  available;
- keep ASR models as language packs, not per-game assets;
- let GKP contribute hotwords/aliases, not full speech models.

English-player support should mean:

- English ASR or multilingual ASR is available;
- English answer language is selectable;
- English aliases and glossary exist for the game;
- LLM can bridge English question wording to canonical GKP terms when enabled.

It should not mean writing a separate full English walkthrough GKP for every
game.

## 6. LLM Product Policy

Settings should continue to expose:

- provider enabled/disabled;
- OpenAI-compatible base URL;
- provider preset such as DeepSeek or custom;
- model name;
- API key or local endpoint configuration;
- timeout and max token budget;
- low-cost config self-test.

Answer policy should use this decision ladder:

| Situation | LLM Off | LLM On |
| --- | --- | --- |
| Exact template/entity hit | direct local answer | direct local answer unless user asks for rephrase/translation |
| Multiple consistent evidence rows | local summary | LLM synthesis allowed |
| Noisy ASR or cross-language query | local alias/normalizer only | query rewrite and term mapping allowed |
| Evidence exists but answer language differs | local answer if localized template exists | LLM translation/composition allowed |
| Evidence conflicts | ask clarification | ask clarification, optionally LLM-written |
| No evidence for game-specific fact | no-evidence response | no-evidence response, no bare answer |
| No GKP for game | unknown game / install GKP prompt | optional generic mode, clearly labeled |

Every LLM-used answer should preserve diagnostics:

- provider/model;
- latency;
- token budget;
- whether evidence was present;
- source ids used;
- pipeline stage;
- generic/ungrounded flag if applicable.

## 7. Builder And Content Workflow

The GKP builder direction remains valid, but its target output changes from
"large game knowledge pack" to "reviewed GKP Lite baseline first".

GKP Lite should become a standardized scaffold, not only a prose guideline. The
same template should support hand-authored packs, RAG-assisted extraction,
LLM-assisted drafting, and future registry publishing.

Recommended builder pipeline:

```text
source inventory
  -> rights and provenance check
  -> parser / RAG workbench / optional multimodal extraction
  -> candidate GKP Lite rows
  -> source and spoiler validation
  -> human review
  -> GKP v0 export
  -> lint + golden tests
  -> Android import or registry publish
```

The builder may use RAG-Anything, LightRAG, GraphRAG, LlamaIndex, or another
offline workbench, but the Android runtime should still install reviewed plain
data only.

### Standardized GKP Lite Template

Add a machine-readable template profile under the builder tooling:

```text
tools/gkp-builder/
├─ templates/
│  └─ gkp-lite/
│     ├─ profile.yaml
│     ├─ manifest.template.json
│     ├─ aliases.template.json
│     ├─ spoiler_graph.template.json
│     ├─ qa_goldens.template.jsonl
│     ├─ changelog.template.md
│     ├─ sources/
│     │  ├─ citations.template.jsonl
│     │  └─ licenses.template.md
│     └─ knowledge/
│        ├─ production.template.jsonl
│        ├─ mechanics.template.jsonl
│        ├─ strategies.template.jsonl
│        ├─ entities.template.jsonl
│        ├─ items.template.jsonl
│        └─ locations.template.jsonl
└─ schemas/
   └─ gkp-lite-profile.schema.json
```

`profile.yaml` should define the generation contract:

```yaml
profile_id: gkp-lite.v1
coverage_tier: lite
required_lanes:
  - identity
  - aliases
  - core_gameplay
  - beginner_direction
  - core_mechanics
  - key_terms
  - spoiler_gates
  - sources
  - goldens
minimums:
  knowledge_rows: 20
  golden_questions: 20
  no_evidence_goldens: 3
  spoiler_downgrade_goldens: 3
  core_gameplay_goldens: 4
policies:
  require_source_refs: true
  forbid_executable_files: true
  forbid_rom_or_save_data: true
  require_llm_disabled_goldens: true
  allow_llm_only_for_evidence_composition: true
languages:
  canonical_ids_are_language_neutral: true
  localized_surfaces:
    - aliases
    - answer_templates
    - qa_goldens
```

The intended CLI shape:

```bash
gkp-builder new \
  --profile lite \
  --game "Chrono Trigger" \
  --platform snes \
  --language zh \
  --out workspaces/chrono-trigger-snes-zh

gkp-builder lint workspaces/chrono-trigger-snes-zh/out/community.chrono-trigger-snes-zh
gkp-builder test-goldens workspaces/chrono-trigger-snes-zh/out/community.chrono-trigger-snes-zh
```

Scaffold output should include placeholder rows for every required lane, TODO
markers that fail lint until replaced, and a starter `qa_goldens.jsonl` with
identity, core gameplay, no-evidence, and spoiler-downgrade cases.

### Template Validation Rules

The template should create packs that can be validated in three layers:

| Layer | Purpose |
| --- | --- |
| Shape lint | required files, JSON/JSONL validity, allowed ids, no dangerous files |
| Coverage lint | required GKP Lite lanes, minimum goldens, source coverage, coverage tier |
| Runtime goldens | Android parser/retrieval/AnswerPolicy results, including LLM-disabled cases |

Suggested rule severity:

- error: missing manifest, invalid JSONL, executable file, ROM/save data,
  missing `source_refs`, unknown `progress_gate`;
- error: no no-evidence golden or no spoiler-downgrade golden;
- warning: missing recommended lane, low alias count, no English aliases for
  globally known titles, fewer than target goldens;
- info: expanded/deep opportunities found but intentionally deferred.

## 8. Suggested Milestones

### M12: GKP Lite Contract

Goal: formalize the smaller first-support package.

Deliverables:

- update real-game template to define GKP Lite lanes;
- add `tools/gkp-builder/templates/gkp-lite/` scaffold templates and
  `profile.yaml`;
- add a `gkp-builder new --profile lite` command shape or equivalent script;
- add preflight warnings for packs that miss identity/core-loop/beginner/golden
  coverage;
- add coverage lint for required Lite lanes, source refs, no-evidence goldens,
  and spoiler-downgrade goldens;
- tag packs by coverage tier: `lite`, `expanded`, `deep`;
- update Packs UI copy to avoid implying every pack is a complete guide.

Exit criteria:

- one real game can be considered supported with a reviewed Lite pack;
- a new game pack can be scaffolded from the same template without copying an
  existing game folder by hand;
- docs explain what Lite can and cannot answer;
- no-evidence behavior remains explicit.

### M13: Optional LLM Intelligence Layer

Goal: make enabled LLMs useful without making them the factual source.

Deliverables:

- add an explicit runtime setting for LLM answer assistance;
- enable evidence-backed multi-row composition;
- add query rewrite and term-mapping before retrieval when LLM is enabled;
- add diagnostics for evidence gate decisions;
- add tests for "no evidence never calls LLM for game facts".

Exit criteria:

- deterministic local goldens still pass with LLM disabled;
- LLM-enabled goldens improve synthesis/translation without changing source
  truth;
- failures fall back to local answer or clear LLM error state.

### M14: Answer Language And Multilingual Support

Goal: support English players without duplicating entire packs.

Deliverables:

- add answer-language setting, separate from app UI language;
- add runtime pack/row/template language selection;
- add English no-evidence, clarification, and source text;
- add an English or multilingual ASR option as a language model pack;
- add bilingual glossary and name-mapping goldens for Shining Force II.

Exit criteria:

- English text questions can hit the same canonical game rows;
- English answers can be generated from evidence;
- Chinese and English pack surfaces do not overwrite each other.

### M15: Generic Mode And Failure Inbox

Goal: make unsupported games feel handled without pretending to know facts.

Deliverables:

- add optional generic mode for unsupported games;
- mark generic answers clearly in UI and diagnostics;
- add a local unanswered-question inbox grouped by game label and intent;
- feed high-frequency failures back into GKP Lite builder tasks;
- add metrics for no-evidence rate, generic-mode rate, local hit rate, and LLM
  evidence-backed rate.

Exit criteria:

- unsupported games produce helpful next steps, not fake game facts;
- failure data directly informs new Lite pack coverage;
- the product can scale game support by priority instead of by exhaustive manual
  authoring.

## 9. Metrics

Track product health with:

| Metric | Target Direction |
| --- | --- |
| `local_hit_rate` | increase over time |
| `llm_evidence_backed_rate` | increase among LLM calls |
| `llm_bare_fact_rate` | must stay zero |
| `no_evidence_rate` | decrease for supported Lite games |
| `generic_mode_rate` | acceptable for unsupported games, visible to user |
| `wrong_answer_feedback_rate` | decrease |
| `language_bridge_success_rate` | increase for English/Chinese cross-language questions |
| `pack_authoring_hours_per_game` | decrease |

## 10. Non-Goals

- Do not remove the existing BYOK LLM settings.
- Do not require LLM for the base RetroSprite experience.
- Do not let LLM answer game-specific facts without evidence.
- Do not turn GKP Lite into a complete walkthrough requirement.
- Do not store or ship ROM data, copyrighted guide text dumps, or executable
  pack code.
- Do not make English support depend on rewriting a complete English pack for
  every game.
- Do not use live web search as the default in-game knowledge path.
