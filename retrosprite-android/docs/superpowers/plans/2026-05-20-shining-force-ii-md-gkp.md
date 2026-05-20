# Shining Force II MD GKP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a small, source-cited, low-spoiler Mega Drive `Shining Force II` GKP that proves real-game knowledge can flow through RetroSprite's existing local retrieval and answer pipeline.

**Architecture:** Add one bundled GKP pack under `app/src/main/assets/gkp/shining-force-ii-md`, modeled after the existing sample packs. The pack stays narrow: game identity, early low-spoiler direction, battle basics, promotion basics, special promotion items, and a few aliases. Tests parse the pack, run golden retrieval, and verify direct Q&A answers do not call the LLM.

**Tech Stack:** Kotlin/JVM tests, JSON/JSONL GKP v0 assets, `GkpV0Parser`, `LocalKnowledgeRetrievalPipeline`, `RepositoryGameResolver`, `EvidenceAnswerPolicy`, bundled asset importer.

---

### Task 1: Failing Tests

**Files:**
- Create: `app/src/test/kotlin/com/retrosprite/app/data/retrieval/SampleShiningForceIIRetrievalGoldenTest.kt`
- Create: `app/src/test/kotlin/com/retrosprite/app/domain/SampleShiningForceIIQuestionPipelineTest.kt`

- [ ] **Step 1: Write retrieval golden test**

Add a test that loads `src/main/assets/gkp/shining-force-ii-md`, parses every knowledge file from `manifest.json`, reads `qa_goldens.jsonl`, and asserts expected entity/source hits. Include one medium-spoiler promotion-item location question hidden under `LIGHT`.

- [ ] **Step 2: Write pipeline test**

Add tests for:
- `md__Shining Force II` + `什么时候转职？` returns a local answer with `来源：sf2.promotion` and does not call LLM.
- `md__Shining Force II` + `不要剧透下一步去哪？` returns a low-spoiler early direction with `来源：sf2.official_overview` or `sf2.early_route`.
- `md__Shining Force II` + unknown trading-system question returns uncertainty and does not call LLM.

- [ ] **Step 3: Run tests to verify RED**

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew testDebugUnitTest --tests com.retrosprite.app.data.retrieval.SampleShiningForceIIRetrievalGoldenTest \
  --tests com.retrosprite.app.domain.SampleShiningForceIIQuestionPipelineTest
```

Expected: fail because the `shining-force-ii-md` pack does not exist yet.

### Task 2: Add Small GKP Pack

**Files:**
- Create: `app/src/main/assets/gkp/shining-force-ii-md/manifest.json`
- Create: `app/src/main/assets/gkp/shining-force-ii-md/knowledge/mechanics.jsonl`
- Create: `app/src/main/assets/gkp/shining-force-ii-md/knowledge/items.jsonl`
- Create: `app/src/main/assets/gkp/shining-force-ii-md/knowledge/locations.jsonl`
- Create: `app/src/main/assets/gkp/shining-force-ii-md/knowledge/quests.jsonl`
- Create: `app/src/main/assets/gkp/shining-force-ii-md/aliases.json`
- Create: `app/src/main/assets/gkp/shining-force-ii-md/spoiler_graph.json`
- Create: `app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl`
- Create: `app/src/main/assets/gkp/shining-force-ii-md/sources/citations.jsonl`
- Create: `app/src/main/assets/gkp/shining-force-ii-md/sources/licenses.md`
- Create: `app/src/main/assets/gkp/shining-force-ii-md/changelog.md`

- [ ] **Step 1: Add manifest**

Use `pack_id=community.shining-force-ii-md`, `game_id=shining_force_ii_md`, `platform=md`, languages `zh/en`, and labels including `md__Shining Force II`, `genesis__Shining Force II`, `megadrive__Shining Force II`, and Chinese title variants.

- [ ] **Step 2: Add original short knowledge rows**

Keep entries concise and original. Cite source ids, but do not copy manual or guide prose. Include rows for:
- Tactical battle basics.
- Character defeat/revive basics.
- Promotion level basics.
- Special promotion item usage.
- Warrior Pride / Pegasus Wing / Vigor Ball / Secret Book / Silver Tank summary.
- Granseal / early low-spoiler direction.
- Bowie and Astral identity basics.

- [ ] **Step 3: Add aliases and goldens**

Add Chinese and English aliases for title, Bowie, Granseal, promotion, revive, special classes, and promotion item names. Add about 12 QA goldens covering known and unknown questions.

### Task 3: Bundle Importer and Docs

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/data/gkp/BundledGkpImporter.kt`
- Modify: `docs/NEXT_IMPLEMENTATION_PLAN.md`
- Modify: `docs/TEST_COVERAGE.md`

- [ ] **Step 1: Add bundled path**

Append `gkp/shining-force-ii-md` to `BUNDLED_PACK_PATHS`.

- [ ] **Step 2: Update docs**

Record M9.1 as active/done depending on verification. Note that the pack is intentionally small, source-cited, and not a copied walkthrough.

### Task 4: Verification

**Commands:**

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew testDebugUnitTest --tests com.retrosprite.app.data.retrieval.SampleShiningForceIIRetrievalGoldenTest \
  --tests com.retrosprite.app.domain.SampleShiningForceIIQuestionPipelineTest

JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew testDebugUnitTest --tests com.retrosprite.app.gkp.GkpV0FixtureLintTest

JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:assembleDebug

git diff --check
```

Expected: all commands pass.

### Self-Review

- Spec coverage: covers real-game GKP scope, low-spoiler defaults, local retrieval, answer pipeline, bundled importer, and docs.
- Placeholder scan: no TODO/TBD placeholders.
- Scope check: this is one focused GKP content slice, not a full walkthrough or registry project.
