# GKP v0 Schema

> Status: v0 frozen for the Phase 1 MVP sample pack.
> Scope: local, inspectable Game Knowledge Packs for RetroSprite. A GKP contains metadata, plain-text knowledge, citations, aliases, spoiler gates, and golden Q&A tests. It must not contain ROM data, executable code, scripts, binaries, or copied long-form guidebook text.

## Design Goals

- Local-first retrieval before LLM calls.
- Evidence-backed answers with stable source ids.
- Low-spoiler defaults and explicit escalation.
- Deterministic linting before a pack can be imported.
- Direct mapping to the current Room `games` and `knowledge` tables.

## GKP Lite Profile

`gkp.v0` supports both small and deep packs. The next product direction uses
**GKP Lite** as the first-support profile for real games: a lightweight,
reviewed, source-cited package that anchors game identity, aliases, core
gameplay, beginner direction, common mechanics, key terms, spoiler gates, and
golden Q&A tests.

GKP Lite is not a complete walkthrough. A game can be initially supported with a
Lite pack and later grow into an expanded/deep pack through additional reviewed
slices. LLM integration remains optional and evidence-gated: when enabled, it
may rewrite questions, bridge languages, synthesize multiple evidence rows, or
translate answers, but it must not become the factual source for game-specific
claims without GKP evidence.

Coverage tier vocabulary:

| `coverage_tier` | Product label | Meaning |
| --- | --- | --- |
| `lite` | GKP Lite | first support, common safe questions |
| `expanded` | GKP Expanded | broader reviewed pack for active users |
| `deep` | GKP Deep | mature detailed pack with stronger progress gates |

`expanded` is a GKP coverage tier, not a paid app tier. Do not use `plus` or
`pro` as `coverage_tier` values; Pro is reserved for the commercial product
line.

See `GKP_LITE_OPTIONAL_LLM_DIRECTION.md` and
`REAL_GAME_GKP_EXPANSION_TEMPLATE.md` for the product policy and production
template.

## Package Layout

```text
pack-id/
├─ manifest.json
├─ knowledge/
│  ├─ entities.jsonl
│  ├─ locations.jsonl
│  ├─ quests.jsonl
│  ├─ mechanics.jsonl
│  ├─ bosses.jsonl
│  └─ dialogue_notes.jsonl
├─ sources/
│  ├─ citations.jsonl
│  └─ licenses.md
├─ aliases.json
├─ spoiler_graph.json
├─ qa_goldens.jsonl
└─ changelog.md
```

Only `manifest.json`, `sources/citations.jsonl`, `aliases.json`, `spoiler_graph.json`, `qa_goldens.jsonl`, and at least one `knowledge/*.jsonl` file are required for v0. Empty optional knowledge files may be omitted.

## Identifiers

| Field | Rule |
| --- | --- |
| `pack_id` | Reverse-DNS-ish lowercase id, e.g. `sample.2048` |
| `game_id` | Stable app id used for DB joins, e.g. `2048` |
| `entity_id` | Unique within `game_id`, lowercase dotted id, e.g. `mechanic.tile-merge` |
| `source_id` | Unique within pack, lowercase dotted id, e.g. `sample.2048.rules` |
| `qa_id` | Unique within pack, lowercase dotted id, e.g. `qa.2048.merge.zh` |

Allowed identifier characters: lowercase letters, digits, dots, underscores, and hyphens. Identifiers must start with a lowercase letter or digit.

## Manifest

`manifest.json` defines the game header and import contract.

```json
{
  "schema_version": "gkp.v0",
  "pack_id": "sample.2048",
  "pack_version": "0.1.0",
  "default_language": "zh",
  "game": {
    "game_id": "2048",
    "title": "2048",
    "platform": "libretro",
    "region": null,
    "languages": ["zh", "en"],
    "retroarch_system_ids": ["2048"],
    "retroarch_labels": ["2048__"],
    "rom_identity": {
      "crc32": null,
      "sha1": null
    }
  },
  "trust_level": "sample",
  "min_app_version": "0.1.0",
  "generated_at": "2026-05-19T00:00:00Z",
  "signature": null,
  "contents": {
    "knowledge": ["knowledge/mechanics.jsonl", "knowledge/entities.jsonl"],
    "citations": "sources/citations.jsonl",
    "aliases": "aliases.json",
    "spoiler_graph": "spoiler_graph.json",
    "qa_goldens": "qa_goldens.jsonl"
  }
}
```

### Manifest Fields

| Field | Required | Notes |
| --- | --- | --- |
| `schema_version` | yes | Must be `gkp.v0` |
| `pack_id` | yes | Pack identity, independent of game id |
| `pack_version` | yes | Semver-like string |
| `default_language` | yes | BCP-47-ish language tag used when a row omits language |
| `game.game_id` | yes | Maps to Room `games.game_id` |
| `game.title` | yes | Display title |
| `game.platform` | yes | Platform/core family, e.g. `snes`, `gba`, `libretro` |
| `game.region` | no | Region tag when known |
| `game.languages` | yes | Non-empty array |
| `game.retroarch_system_ids` | no | System ids observed from RetroArch labels |
| `game.retroarch_labels` | no | Exact labels observed from RetroArch, useful for empty-game labels like `2048__` |
| `game.rom_identity.crc32` | no | Optional ROM/content identity. Never store ROM content. |
| `game.rom_identity.sha1` | no | Optional ROM/content identity. Never store ROM content. |
| `trust_level` | yes | `official`, `community`, `personal`, or `sample` |
| `signature` | no | Optional signature declaration for registry packages. Local imports may be unsigned. |
| `contents` | yes | Relative paths inside this pack |

Optional signature declaration shape:

```json
{
  "signature": {
    "algorithm": "ed25519",
    "key_id": "publisher.example",
    "digest_sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    "signature": "base64-signature"
  }
}
```

M5.8 stores this as metadata only: `unsigned` when absent, `declared` when
present and syntactically valid. It does not yet verify publisher keys.
Install-time provenance is assigned by RetroSprite (`bundled`, `external`, or
future `registry`) rather than trusted from the manifest.

## Knowledge Rows

Knowledge files are JSONL. Each line is one plain-text knowledge row.

```json
{
  "entity_id": "mechanic.tile-merge",
  "entity_type": "mechanic",
  "canonical_name": "Tile merge",
  "language": "en",
  "aliases": ["merge", "combine tiles"],
  "description_short": "Two tiles with the same value merge into one tile with double the value.",
  "description_long": "A move slides every tile in one direction. Adjacent equal tiles in that direction merge once per move.",
  "progress_gate": null,
  "spoiler_level": "none",
  "source_refs": ["sample.2048.rules"],
  "confidence": "verified",
  "answer_templates": [
    {
      "template_id": "template.2048.merge.en",
      "language": "en",
      "question_patterns": ["how do tiles merge", "combine tiles"],
      "answer": "Slide toward a matching tile. Two equal tiles merge into one tile with double the value, and each tile can merge only once per move.",
      "source_refs": ["sample.2048.rules"],
      "spoiler_level": "none"
    }
  ]
}
```

### Knowledge Fields

| Field | Required | Notes |
| --- | --- | --- |
| `entity_id` | yes | Unique within the pack |
| `entity_type` | yes | One of `mechanic`, `item`, `enemy`, `boss`, `location`, `quest`, `npc`, `dialogue`, `strategy`, `faq`, `note` |
| `canonical_name` | yes | Human-readable name |
| `language` | no | Defaults to manifest `default_language` |
| `aliases` | yes | Array of localized lookup strings |
| `description_short` | yes | Brief retrievable snippet, target <= 240 chars |
| `description_long` | no | Longer plain-text support, target <= 1200 chars |
| `progress_gate` | no | Null or a gate id from `spoiler_graph.json` |
| `spoiler_level` | yes | `none`, `light`, `medium`, or `heavy` |
| `source_refs` | yes | Non-empty source ids that must exist in `sources/citations.jsonl` |
| `confidence` | yes | `verified`, `community`, or `uncertain` |
| `answer_templates` | no | Zero-LLM answers. If present, every template must cite source ids. |

### Answer Templates

`answer_templates` are the preferred zero-LLM path for natural language
questions. A template may use a flat `answer` or spoiler-tiered answers:

```json
{
  "template_id": "template.game.leveling.zh",
  "language": "zh",
  "intent": "leveling",
  "question_patterns": ["怎么玩经验高", "怎么练级快", "经验怎么刷"],
  "answer": "让低等级角色补最后一击；治疗和辅助行动也能帮助部分角色追经验。",
  "source_refs": ["game.project.mechanics"],
  "spoiler_level": "none"
}
```

```json
{
  "template_id": "template.game.location.zh",
  "language": "zh",
  "intent": "location",
  "question_patterns": ["道具在哪里", "怎么拿这个道具"],
  "answer_light": "先别查完整位置清单；这属于中期探索相关内容。",
  "answer_clear": "到达相关城镇后，优先检查战术基地和可疑角落。",
  "answer_direct": "具体位置写在这里，但只应在直接答案级别显示。",
  "spoiler_light": "light",
  "spoiler_clear": "medium",
  "spoiler_direct": "heavy",
  "source_refs": ["game.community.items"]
}
```

Allowed `intent` values:

- `game_overview`
- `beginner_guide`
- `team_build`
- `leveling`
- `name_mapping`
- `location`
- `usage`
- `mechanic`
- `route_hint`
- `strategy`
- `production`
- `no_evidence`
- `unknown_or_out_of_scope`

Natural-language real-game packs should include templates for:

- core gameplay: “这游戏怎么玩 / 主要玩什么 / 好玩在哪”
- beginner guide: “新手怎么玩 / 开局先干什么”
- team build: “哪些角色适合培养 / 谁值得练”
- leveling: “怎么玩经验高 / 怎么练级快”
- route hints: “卡住了下一步去哪”
- mechanics, item usage, name mapping, and production facts

### Spoiler Semantics

| GKP level | Meaning | Default UI tolerance mapping |
| --- | --- | --- |
| `none` | Basic controls, mechanics, UI, non-spoiler facts | Always allowed |
| `light` | Early-game hints and mild contextual advice | Allowed by `LIGHT` |
| `medium` | Explicit puzzle/route/boss instructions | Allowed by `CLEAR` |
| `heavy` | Endings, late-game twists, hidden outcomes | Allowed by `FULL` |

## Citations

`sources/citations.jsonl` is JSONL. Source ids are stable citation anchors, not long copied text.

```json
{
  "source_id": "sample.2048.rules",
  "title": "2048 sample rules summary",
  "kind": "project_note",
  "url": null,
  "license": "CC0-1.0",
  "reliability": "verified",
  "notes": "Short original summary written for RetroSprite sample data."
}
```

Allowed `kind`: `manual`, `official_site`, `project_note`, `community_note`, `wiki`, `transcript`, `other`.

Allowed `reliability`: `verified`, `community`, `uncertain`.

## Aliases

`aliases.json` maps user-facing strings to entity ids. It is intentionally redundant with row-level aliases so import tooling can build fast lookup tables.

```json
{
  "language": "zh",
  "aliases": [
    {
      "term": "合并",
      "entity_id": "mechanic.tile-merge",
      "weight": 1.0
    }
  ]
}
```

`weight` is optional and defaults to `1.0`.

### Game-Scoped ASR Name Variants

Game-specific names are part of the GKP contract, not something RetroSprite
should expect a general ASR model to know. Proper nouns such as character names,
localized item names, boss names, route names, and transliterated English terms
must be scoped to the current game pack and used only after the current game has
been resolved. A pack should not contribute global speech rewrites.

`aliases.json` may carry ASR-oriented entries alongside normal lookup aliases.
These entries use the same required `term` and `entity_id` fields and may add
optional metadata so builder tooling can distinguish them from source-backed
display aliases:

Operational rule: Paraformer does not receive GKP terms as native hotwords.
GKP `asr_variant` and `observed_asr` entries are post-ASR, current-game
normalization data. They must include `canonical_term`, avoid generic
fragments, and be covered by a golden or real-device QA case.

```json
{
  "term": "密营",
  "entity_id": "item.mithril",
  "weight": 0.72,
  "kind": "asr_variant",
  "source": "observed_asr",
  "canonical_term": "秘银",
  "notes": "Observed microphone ASR variant for 秘银."
}
```

Recommended `kind` values:

| `kind` | Meaning | Runtime use |
| --- | --- | --- |
| `display_alias` | official, localized, fan, abbreviation, or English name | retrieval and user-facing lookup |
| `asr_variant` | likely speech-recognition confusion for a known display alias | current-game ASR normalization only |
| `observed_asr` | transcript captured from device QA or user feedback | current-game ASR normalization with diagnostics |

Recommended `source` values:

| `source` | Meaning |
| --- | --- |
| `official` | official localized name or manual term |
| `community` | common fan translation or community usage |
| `generated_phonetic` | generated from pinyin, kana, romanization, or number variants |
| `observed_asr` | seen in real microphone logs or reproducible QA |
| `manual_review` | explicitly approved by a pack author after review |

ASR variants must stay narrow:

- Prefer proper nouns and durable game terms: characters, recruitable units,
  items, spells, locations, bosses, systems, and route names.
- Do not add broad scaffolding words such as `在哪里`, `怎么用`, `角色`, `道具`,
  `下一步`, or `怎么玩` as high-confidence ASR variants.
- Do not globally rewrite common words such as `你的` to a character name.
  If a risky variant is useful, apply it only when the current GKP contains the
  target entity and surrounding intent strongly suggests that entity class.
- Keep generated variants lower weight than official/localized aliases until a
  real transcript proves they are useful.
- Store repeated real term failures as `observed_asr` entries. Keep scoped
  full-question transformations in GKP as `term -> canonical_term` aliases when
  they cannot be represented as a single proper noun repair, then add golden
  questions that prove the repaired transcript resolves to the intended entity.

For example, a Shining Force II pack can map `秘银`, `Mithril`, `米斯里鲁`,
and `米斯里鲁银` as display aliases for `item.mithril`, while `密营`,
`密影`, `米斯林鲁`, or `以斯列鲁` should be treated as ASR variants. These
variants should influence only the current game's normalization step before
retrieval; they should not become generic cross-game dictionary entries.

## Spoiler Graph

`spoiler_graph.json` declares known progress gates. v0 uses a simple directed order; later versions may add richer prerequisites.

```json
{
  "default_gate": "start",
  "gates": [
    {
      "gate_id": "start",
      "label": "Start",
      "order": 0
    }
  ],
  "edges": []
}
```

Knowledge rows may use `progress_gate = null` for always-available knowledge.

## Golden Q&A

`qa_goldens.jsonl` contains regression examples for the retrieval and answer policy.

```json
{
  "qa_id": "qa.2048.merge.zh",
  "language": "zh",
  "question": "两个 2 怎么合并？",
  "game_id": "2048",
  "spoiler_level": "none",
  "progress_gate": "start",
  "expected_entity_ids": ["mechanic.tile-merge"],
  "expected_answer_contains": ["相同数字", "翻倍"],
  "source_refs": ["sample.2048.rules"]
}
```

## Lint Rules

A v0 pack is valid when all of the following pass:

1. `manifest.json` parses and uses `schema_version = gkp.v0`.
2. Every path listed in `manifest.contents` exists.
3. Every JSONL file has at least one non-empty JSON object.
4. `entity_id`, `source_id`, and `qa_id` are unique in their scopes.
5. Every knowledge `source_refs` entry exists in citations.
6. Every `answer_templates[*].source_refs` entry exists in citations.
7. Every alias `entity_id` exists in knowledge.
8. Every QA `expected_entity_ids` entry exists in knowledge.
9. Every QA `source_refs` entry exists in citations.
10. Every non-null `progress_gate` exists in `spoiler_graph.gates`.
11. `spoiler_level`, `confidence`, `trust_level`, and `entity_type` values are in the allowed sets.
12. No files outside the declared pack tree are referenced.
13. If `signature` is present, it must declare `algorithm = ed25519`, non-blank
    `key_id`, 64-hex `digest_sha256`, and non-blank `signature`.

The JVM test `GkpV0FixtureLintTest` enforces these rules for the bundled sample packs.

## External Preflight

Before an external GKP is installed, RetroSprite runs a read-only preflight pass.
This pass does not write to Room and adds stricter safety checks for third-party
content:

- `sources/licenses.md` must exist and must not be blank.
- Each citation row must declare a non-blank `license`.
- ROMs, archives, executable files, native libraries, scripts, APKs, and other
  binary package types are blocked even when they are not referenced by
  `manifest.contents`.
- Unknown extra files are ignored by v0 and reported as warnings.
- Unsigned packs are allowed for local import and reported as informational
  `unsigned_pack`; registry distribution will require verification later.
- A deterministic SHA-256 content digest is computed over normalized readable
  pack files and stored with the installed game header.

## External Installation

External installation is enabled only after preflight passes. The app must show
an explicit confirmation plan before writing:

- target `game_id`
- current installed pack version, when the game already exists
- new pack version
- coverage tier (`GKP Lite`, `GKP Expanded`, or `GKP Deep`)
- current and new knowledge row counts
- source and golden Q&A counts
- install provenance (`external` for folder imports)
- signature status and content digest

On confirmation, RetroSprite re-runs preflight against the same selected tree and
then replaces the target `games` / `knowledge` rows in one Room transaction.
Failed preflight input is never written.

## Local Removal

Installed packs should normally be disabled before they are physically removed.
Disabling is reversible and keeps the `games` / `knowledge` rows intact while
removing the pack from game resolution and retrieval.

Disable / enable state:

- `enabled = true`: pack can be selected by `RepositoryGameResolver`.
- `enabled = false`: pack remains visible in Packs but is ignored by runtime
  game resolution.
- `disabled_at`: local timestamp for diagnostics/UI only.

If a disabled pack matches the current RetroArch label or ROM hash,
`RepositoryGameResolver` may return a `gkp_disabled` identity with no `gameId`.
This lets Home and Diagnostics explain "pack exists but is disabled" while
still preventing retrieval and LLM composition from reading that pack's
knowledge rows.

Installed packs can be removed only after a confirmation plan is shown. The plan
must include:

- target `pack_id`
- target `game_id`
- installed pack version
- knowledge row count
- source count

Removal clears the target `games` / `knowledge` rows in one local transaction.
Bundled sample packs may be restored by the startup importer after app restart;
the UI warns when `provenance = bundled`. If a bundled pack is disabled, the
startup importer must preserve that disabled state. If a user installs an
external pack with the same `game_id`, the bundled importer must not overwrite it.

## Current Room Mapping

| GKP field | Room target |
| --- | --- |
| `game.game_id` | `games.game_id` |
| `pack_id` | `games.pack_id` |
| `game.title` | `games.title` |
| `game.platform` | `games.platform` |
| `game.region` | `games.region` |
| `game.languages` | `games.languages` JSON array |
| `game.rom_identity.crc32` | `games.rom_crc32` |
| `game.rom_identity.sha1` | `games.rom_sha1` |
| `coverage_tier` | `games.coverage_tier` |
| `pack_version` | `games.pack_version` |
| `schema_version` | `games.schema_version` |
| `trust_level` | `games.trust_level` |
| Install source | `games.provenance` (`bundled`, `external`, `registry`, `unknown`) |
| Signature status | `games.signature_status` (`unsigned`, `declared`, `verified`, `failed`, `unknown`) |
| Signature key id | `games.signature_key_id` |
| Content digest | `games.content_digest` |
| Local enable state | `games.enabled` |
| Local disable timestamp | `games.disabled_at` |
| `entity_id` | `knowledge.entity_id` |
| `entity_type` | `knowledge.entity_type` |
| `canonical_name` | `knowledge.canonical_name` |
| `aliases` | `knowledge.aliases_json` |
| `description_short` | `knowledge.description_short` and FTS |
| `description_long` | `knowledge.description_long` and FTS |
| `progress_gate` | `knowledge.progress_gate` |
| `spoiler_level` | `knowledge.spoiler_level` |
| `source_refs` | `knowledge.source_refs_json` |
| `confidence` | `knowledge.confidence` |
| `answer_templates` | `knowledge.answer_templates_json` |

## v0 Non-Goals

- No executable plugins.
- No ROM files, save states, BIOS files, screenshots, or copyrighted guide dumps.
- No live web lookup during answer generation.
- No vector index requirement.
- No cryptographic registry verification yet. v0 stores signature declaration
  metadata and content digests so verification can be added without changing
  knowledge rows.
