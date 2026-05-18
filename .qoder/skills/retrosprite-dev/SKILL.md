---
name: retrosprite-dev
description: Develops RetroSprite, the RetroArch in-game AI Q&A companion. Use when working on RetroSprite product strategy, architecture, Android/Kotlin implementation, RetroArch AI Service integration, Game Knowledge Pack (GKP) schema and tooling, local-first retrieval (SQLite FTS5, aliases, BM25), low-spoiler answer policy, BYOK LLM cost control, golden tests, docs, issues, or phase planning. Use when the user mentions RetroSprite, RetroArch AI Service, GKP, game knowledge pack, low-spoiler, in-game Q&A companion, or files under /Users/kartz/Development/Sprite.
---

# RetroSprite Dev

## Core Direction

Build RetroSprite as a RetroArch in-game Q&A companion, not a generic Android AI overlay and not a passive walkthrough hint bot.

Default product promise:

> The player presses a RetroArch hotkey, asks a game-specific question by text or voice, and receives a short, accurate, low-spoiler answer grounded in the current game and trusted local knowledge.

When making product, architecture, or implementation choices, prefer:

1. RetroArch AI Service integration first.
2. Game-grounded Q&A over generic chatbot behavior.
3. Local Game Knowledge Packs over live web search.
4. Local retrieval, templates, aliases, and cache before LLM calls.
5. Evidence-grounded answers with sources.
6. Low-spoiler defaults with explicit escalation.
7. Safe, inspectable data packages over executable plugin code.

## Orientation Workflow

When starting work in the RetroSprite project:

1. Check the current workspace and read local docs if present:
   - `/Users/kartz/Development/Sprite/RetroSprite_Development_Plan.md`
   - `/Users/kartz/Development/Sprite/RetroSprite_Strategy_Brief.html`
   - `/Users/kartz/Development/Sprite/RetroSprite_Proposal.html`
2. Identify the current phase: protocol validation, Q&A MVP, GKP standard, voice/cache polish, registry/community, or advanced features.
3. Keep changes scoped to that phase unless the user explicitly asks to expand.
4. If code exists, inspect the repo patterns before proposing architecture.
5. For implementation work, finish with concrete verification: tests, static checks, device checklists, or documented limitations.

## Non-Negotiable Guardrails

Do not steer the project toward these paths unless the user explicitly overrides the direction:

- Do not start with Android `MediaProjection`, Accessibility Service, or global floating-window capture as the primary integration.
- Do not require modifying RetroArch cores or emulator internals for MVP.
- Do not use LLM output as an ungrounded factual source.
- Do not answer high-uncertainty game questions by guessing.
- Do not make real-time internet search the default knowledge path.
- Do not include ROMs, commercial guidebook text dumps, or copyrighted long-form walkthrough copies in GKP.
- Do not allow GKP packages to execute code.
- Do not make Live2D, skins, pet animation, or mascot polish a blocker for the core Q&A loop.
- Do not build AI-controlled save/load/input automation before the Q&A product is reliable.

## Preferred Architecture

Use this mental model unless the existing codebase has a clearly better equivalent:

```text
RetroArch Hotkey / AI Service
  -> Android local endpoint
  -> Session Context Builder
  -> Game Resolver
  -> Query Understanding
  -> Local Retrieval Pipeline
  -> Answer Policy
  -> Template Answer or Evidence-Grounded LLM Composer
  -> Overlay Text / optional TTS
```

Primary modules:

- **RetroArch Endpoint**: receive AI Service requests, screenshots, labels, and diagnostics.
- **Game Resolver**: map label, file name, CRC/SHA1, platform, region, and user selection to a GKP.
- **Context Builder**: combine player question, current game, OCR, recent session memory, progress gate, and spoiler setting.
- **Retriever**: query only the current game's GKP by aliases, FAQ/templates, SQLite FTS5/BM25, metadata filters, and optional vector retrieval.
- **Answer Policy**: choose direct answer, LLM composition, clarifying question, low-spoiler downgrade, or refusal.
- **LLM Adapter**: BYOK, OpenAI-compatible, used only after retrieval has evidence.
- **GKP Manager**: install, validate, index, update, and remove knowledge packs.

## Product Interaction Rules

Design around player flow:

- The player triggers RetroSprite manually.
- Default answer length is 1-3 sentences.
- Voice output should be one short answer; long content stays in text.
- Always offer escalation instead of dumping spoilers:
  - `轻提示`
  - `更明确`
  - `直接答案`
- Offer `查看来源` and `这不对` feedback where practical.
- If the answer depends on game version, region, chapter, or location, ask a short clarifying question.

## GKP Standard Rules

Treat Game Knowledge Pack as RetroSprite's durable core asset.

GKP should be:

- Standardized.
- Local-first.
- Searchable.
- Source-cited.
- Low-spoiler aware.
- Versioned.
- Testable.
- Signed when distributed through a registry.

Recommended structure:

```text
pack-id.gkp
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
├─ spoiler_graph.json
├─ aliases.json
├─ qa_goldens.jsonl
├─ changelog.md
└─ index/
   └─ optional_prebuilt_index
```

Require these concepts in schema or tooling:

- `pack_id`, `schema_version`, `game_title`, `platform`, `region`, `languages`.
- ROM identity where possible: CRC/SHA1/hash and region.
- `entity_id`, `entity_type`, `canonical_name`, `aliases`.
- `progress_gate` and `spoiler_level`.
- `source_refs` and source reliability.
- `confidence`: verified, community, uncertain.
- `answer_templates` for zero-LLM answers.
- `qa_goldens` for regression testing.

## Retrieval And LLM Policy

Use a funnel, not a firehose:

1. Normalize question, language, spelling, and aliases.
2. Filter to the current game, platform, region, pack version, progress gate, and spoiler level.
3. Try FAQ/template answers.
4. Try entity/alias lookup.
5. Try SQLite FTS5/BM25.
6. Optionally use local embeddings or reranking for fuzzy questions.
7. Call LLM only when evidence exists and the answer needs synthesis, translation, explanation, or style conversion.

Answer decisions:

- High-confidence template/entity hit: answer without LLM.
- Multiple consistent evidence chunks: compose with LLM or deterministic template.
- Conflicting evidence: ask a clarifying question.
- Low evidence: say uncertainty and ask for version/location/chapter.
- Higher spoiler than allowed: downgrade answer or ask for confirmation.

Cache keys should include:

- game id
- pack version
- normalized question
- detected location or progress gate
- spoiler level
- language

## Android Implementation Preferences

For Android work, prefer:

- Kotlin.
- Jetpack Compose.
- Local HTTP endpoint for RetroArch AI Service.
- SQLite/Room with FTS5 for local retrieval.
- WorkManager for background index/update tasks.
- Android TextToSpeech and SpeechRecognizer only after text MVP works.
- Minimal permissions for MVP.
- A diagnostics screen for endpoint status, last request, GKP match, index status, and provider errors.

Avoid requiring:

- Accessibility Service.
- Continuous screen recording.
- Emulator core changes.
- Always-on cloud backend.

## Phase Discipline

Use these phase boundaries:

1. **Phase 0: Protocol validation**
   - Prove RetroArch AI Service can call the Android local endpoint.
   - Return fixed text first.
   - Log request shape and compatibility issues.

2. **Phase 1: Q&A MVP**
   - Text input.
   - Game resolver.
   - Built-in sample GKP.
   - SQLite FTS5 and aliases.
   - Source-cited low-spoiler answers.
   - BYOK LLM adapter.

3. **Phase 2: GKP v0**
   - Schema.
   - Lint.
   - Build index.
   - Golden tests.
   - Three representative sample games or fixtures.

4. **Phase 3: Voice, cache, polish**
   - ASR/TTS.
   - Semantic/exact cache.
   - Session memory.
   - Feedback buttons.
   - Horizontal handheld UI polish.

5. **Phase 4: Registry/community**
   - Pack metadata service.
   - Signing/checksums.
   - Trust levels.
   - Update/diff.
   - Upload/review/report flow.

6. **Phase 5: Advanced**
   - Optional Vision LLM.
   - Offline models.
   - Embeddings.
   - RetroArch UDP helper actions.
   - Mascot/animation.

## Testing Expectations

For RetroSprite changes, prefer tests that protect product truth, not just code paths:

- Endpoint integration tests or manual RetroArch checklist for Phase 0/1.
- Golden questions for every GKP.
- Spoiler-level regression tests.
- Alias and multilingual name tests.
- Retrieval confidence tests.
- LLM prompt tests that verify answers cite provided evidence and do not invent facts.
- Cost tests tracking local-hit rate, LLM-call rate, cache-hit rate, and latency.
- Android UI checks for landscape handheld use.

Minimum quality targets for MVP planning:

- Known-game resolver accuracy: 95%+.
- Golden question pass rate: 85%+.
- LLM call rate: below 30% for common questions.
- Default local answer latency: around 2 seconds or less.
- Low-spoiler leakage rate: under 5% in curated tests.

## Documentation And Issue Writing

When writing docs, specs, or issues:

- State the phase.
- State the user-facing value.
- State non-goals.
- Include acceptance criteria.
- Include test or verification plan.
- Include privacy/copyright/spoiler considerations where relevant.
- For GKP tasks, include schema impact and golden test impact.

Good issue shape:

```text
Title: Phase 1 - Add SQLite FTS5 retrieval for installed GKP

Goal:
Enable zero-LLM answers for entity and FAQ-like game questions.

Non-goals:
No vector search, no live web search, no registry download.

Acceptance:
- Builds FTS index from sample GKP.
- Returns top-k chunks with source_refs.
- Applies game_id, region, progress_gate, and spoiler_level filters.
- Includes tests for aliases and BM25 ranking.
```
