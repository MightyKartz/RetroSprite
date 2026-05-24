# Shining Force II Localized Terms GKP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand `shining-force-ii-md` so Chinese localized names for key terms, characters, items, locations, enemies, bosses, classes, spells, and mechanics become first-class GKP retrieval entry points.

**Architecture:** Keep `gkp.v0` unchanged and treat localized names as source-backed GKP data, not runtime code or LLM knowledge. Add a reviewable localized-term inventory first, then update knowledge rows, `aliases.json`, ASR hotword tests, golden tests, and coverage-contract tests in small batches. Use exact proper names as high-confidence aliases, keep broad role/question words low-weight or out of aliases, and keep ASR misrecognitions in `GameTermNormalizer` tests instead of pretending they are official aliases.

**Tech Stack:** Kotlin/JVM tests, JSON/JSONL GKP v0 assets, `GkpV0Parser`, `LocalKnowledgeRetrievalPipeline`, `GkpAsrHotwordExtractor`, `GameTermNormalizer`, Gradle `testDebugUnitTest`, repo-local Markdown inventory.

---

## Why This Plan Exists

The current `shining-force-ii-md` pack has useful seed coverage, but it does not yet feel like a complete Chinese-player GKP. A player using a well-known Chinese patch will naturally ask with localized terms rather than English names. If those names are missing, retrieval, template documents, FTS, ASR hotwords, and ASR normalization all start from a weaker input.

Current observed baseline:

- Knowledge rows: 41.
- NPC rows: 11.
- Item rows: 10.
- Location rows: 3.
- Good existing localized examples: `修伊`, `佳佳`, `卡森`, `吉布`, `皮特`, `气合之玉`, `米斯里鲁银`, `精灵森林`.
- Main gap: coverage is limited to early party members, a small set of special items, and very few locations. Bosses, enemies, spells, classes, later characters, route places, and many item/equipment names are still thin.

This plan upgrades localized names from "nice aliases" to a required GKP content lane.

## Non-Negotiable Rules

- Do not copy ROM text, patch script text, long guide prose, or large tables into GKP.
- Do not use LLM output as a factual source.
- Every accepted localized name must be tied to a source id, preferably existing `sf2.yzzl_chinese_patch`, `sf2.chinese_translation_names`, manual/manual-translation sources, official sources, or project-authored notes.
- Proper names can be high-confidence aliases. Generic words such as `角色`, `道具`, `在哪`, `怎么拿`, `怎么用`, `骑士`, `法师`, `前排`, and `村庄` must not become high-confidence aliases by themselves.
- ASR mistakes such as `修医` for `修伊` belong in `GameTermNormalizerTest` or ASR QA docs, not in `aliases.json` as official names.
- Names that imply spoilers must respect `progress_gate` and `spoiler_level`.
- Answer text must remain short, original, low-spoiler by default, and source-cited.

## Target Coverage

This is a term-coverage plan, not a full walkthrough-content plan. It should integrate with `2026-05-23-shining-force-ii-gkp-1-0.md`, but it can ship as an independent data improvement.

Target minimums:

| Lane | Minimum accepted localized terms | Minimum GKP entities covered | Minimum golden questions |
| --- | ---: | ---: | ---: |
| Playable characters and major NPCs | 40 | 25 | 12 |
| Items, equipment, key items, materials | 90 | 45 | 18 |
| Places, route names, towns, dungeons, battle areas | 60 | 35 | 12 |
| Classes, spells, mechanics, status terms | 45 | 25 | 8 |
| Enemies and bosses | 60 | 35 | 12 |
| ASR-prone proper nouns | 20 variants | 15 intended entities | 8 endpoint/normalizer cases |

Pack-level target after execution:

- `aliases.json`: at least 260 entries, with at least 160 source-backed Chinese localized proper-name aliases.
- `qa_goldens.jsonl`: at least 120 rows, with at least 50 questions that use localized Chinese names without English names in the question.
- `knowledge/*.jsonl`: enough rows that every accepted high-value localized proper noun points to a real entity, not a generic bucket, unless the term is intentionally bucketed with a documented reason.
- `GkpAsrHotwordExtractorTest`: proves high-value localized terms are hotword candidates.
- `GameTermNormalizerTest`: proves observed homophone or near-homophone ASR variants normalize only when unambiguous.

## File Map

**GKP assets**

- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/manifest.json`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/aliases.json`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/changelog.md`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/sources/citations.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/sources/licenses.md`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/entities.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/items.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/locations.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/mechanics.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/quests.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/strategies.jsonl`
- Create if not already created by the 1.0 plan: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/bosses.jsonl`
- Create if not already created by the 1.0 plan: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/enemies.jsonl`

**Audit docs**

- Create: `retrosprite-android/docs/gkp/shining-force-ii-md-localized-term-inventory.md`
- Create: `retrosprite-android/docs/gkp/shining-force-ii-md-localized-term-review-log.md`

**Tests**

- Create: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/gkp/ShiningForceIILocalizedTermCoverageTest.kt`
- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/gkp/GkpV0FixtureLintTest.kt`
- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/data/retrieval/SampleShiningForceIIRetrievalGoldenTest.kt`
- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/domain/SampleShiningForceIIQuestionPipelineTest.kt`
- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/voice/asr/GkpAsrHotwordExtractorTest.kt`
- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/domain/normalization/GameTermNormalizerTest.kt`

## Data Model Policy

Use existing GKP fields.

Knowledge row pattern:

```jsonl
{"entity_id":"npc.example","entity_type":"npc","canonical_name":"English / 常用汉化名","language":"zh","aliases":["English","常用汉化名","另一汉化名"],"description_short":"一句原创短说明。","description_long":"低剧透原创说明；不复制攻略或脚本。","progress_gate":"start","spoiler_level":"light","source_refs":["sf2.chinese_translation_names"],"confidence":"community","answer_templates":[{"template_id":"template.sf2.example.zh","language":"zh","question_patterns":["常用汉化名是谁","常用汉化名怎么用"],"answer":"短答案。","source_refs":["sf2.chinese_translation_names"],"spoiler_level":"light"}]}
```

Alias row pattern:

```json
{"term":"常用汉化名","entity_id":"npc.example","weight":1.0}
```

ASR variant policy:

```kotlin
// GameTermNormalizerTest only, not aliases.json
assertEquals("常用汉化名是谁", normalizer.normalize("同音误听是谁", rows).normalizedQuestion)
```

## Term Acceptance Rubric

Accept a localized term when all of these are true:

- It names a specific in-game entity, class, spell, place, boss, enemy, item, mechanic, or route concept.
- It has a cited source or a clearly project-authored source note.
- It is likely to appear in a Chinese player question.
- It maps to one intended `entity_id`, or the ambiguity is documented and covered by a clarifying answer.

Reject or downgrade when any of these are true:

- The term is only a generic question scaffold such as `在哪`, `怎么用`, `怎么拿`.
- The term is a broad role word that can match many entities, such as `骑士`, `战士`, `法师`, `牧师`, unless it is used only as low-weight context or part of a longer phrase.
- The source is only an uncited memory of a patch name.
- The name would reveal late-game or hidden content under `LIGHT` when the entity itself should be gated.

## Task 1: Build Localized Term Inventory

**Files:**

- Create: `retrosprite-android/docs/gkp/shining-force-ii-md-localized-term-inventory.md`
- Create: `retrosprite-android/docs/gkp/shining-force-ii-md-localized-term-review-log.md`
- Inspect: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/sources/citations.jsonl`
- Inspect: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/*.jsonl`
- Inspect: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/aliases.json`

- [ ] Create `shining-force-ii-md-localized-term-inventory.md` with these sections:
  - `Accepted Terms`
  - `Needs Source`
  - `Rejected Generic Terms`
  - `ASR Variants`
  - `Ambiguous Terms`
- [ ] Add an initial baseline section containing the current counts:
  - knowledge rows: 41
  - npc rows: 11
  - item rows: 10
  - location rows: 3
  - accepted existing examples: `修伊`, `佳佳`, `卡森`, `吉布`, `皮特`, `气合之玉`, `米斯里鲁银`, `精灵森林`
- [ ] Add this Markdown table under `Accepted Terms` and keep every row reviewable:

```markdown
| term | category | target_entity_id | source_refs | confidence | alias_weight | spoiler_gate | action |
| --- | --- | --- | --- | --- | ---: | --- | --- |
| 修伊 | character | npc.chester | sf2.chinese_translation_names | community | 1.0 | start | already_present |
| 气合之玉 | item | item.vigor-ball | sf2.chinese_translation_names | community | 1.0 | start | already_present |
| 精灵森林 | location | location.secret-villages | sf2.chinese_translation_names | community | 1.0 | new_granseal | already_present |
```

- [ ] Add every newly accepted term to this inventory before editing GKP assets.
- [ ] Put ASR mistakes such as `修医`, `气和之玉`, and `一路也` under `ASR Variants`, not `Accepted Terms`.
- [ ] Record every rejected broad word such as `角色`, `道具`, `在哪`, `怎么拿`, `骑士`, `法师`, `村庄` under `Rejected Generic Terms`.

Verification:

```bash
cd /Users/kartz/Development/Sprite
rg -n "Accepted Terms|ASR Variants|Rejected Generic Terms|修伊|气合之玉|精灵森林" retrosprite-android/docs/gkp/shining-force-ii-md-localized-term-inventory.md
```

Expected: all headings and seed rows are present.

## Task 2: Add Coverage Contract Tests

**Files:**

- Create: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/gkp/ShiningForceIILocalizedTermCoverageTest.kt`
- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/gkp/GkpV0FixtureLintTest.kt`

- [ ] Create `ShiningForceIILocalizedTermCoverageTest.kt` with this test skeleton:

```kotlin
package com.retrosprite.app.gkp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ShiningForceIILocalizedTermCoverageTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `localized term coverage reaches real game pilot floor`() {
        val pack = moduleRoot().resolve("src/main/assets/gkp/shining-force-ii-md")
        val manifest = readObject(pack.resolve("manifest.json"))
        val contents = manifest.obj("contents")
        val knowledge = contents.array("knowledge").flatMap { path ->
            readJsonl(pack.resolve(path.jsonPrimitive.content))
        }
        val aliases = readObject(pack.resolve(contents.string("aliases"))).array("aliases")
        val goldens = readJsonl(pack.resolve(contents.string("qa_goldens")))

        val chineseProperAliases = aliases
            .map { it.jsonObject.string("term") }
            .filter { term -> term.any { it in '\u4e00'..'\u9fff' } }
            .filterNot { it in GENERIC_TERMS }

        val localizedGoldens = goldens.filter { qa ->
            qa.string("question").any { it in '\u4e00'..'\u9fff' } &&
                qa.array("expected_entity_ids").isNotEmpty()
        }

        assertTrue("chineseProperAliases=${chineseProperAliases.size}", chineseProperAliases.size >= 160)
        assertTrue("localizedGoldens=${localizedGoldens.size}", localizedGoldens.size >= 50)
        assertTrue("npc rows=${knowledge.count { it.string("entity_type") == "npc" }}", knowledge.count { it.string("entity_type") == "npc" } >= 25)
        assertTrue("item rows=${knowledge.count { it.string("entity_type") == "item" }}", knowledge.count { it.string("entity_type") == "item" } >= 45)
        assertTrue("location rows=${knowledge.count { it.string("entity_type") == "location" }}", knowledge.count { it.string("entity_type") == "location" } >= 35)
        assertTrue("boss rows=${knowledge.count { it.string("entity_type") == "boss" }}", knowledge.count { it.string("entity_type") == "boss" } >= 12)
        assertTrue("enemy rows=${knowledge.count { it.string("entity_type") == "enemy" }}", knowledge.count { it.string("entity_type") == "enemy" } >= 20)
    }

    private fun moduleRoot(): Path {
        var current = Paths.get("").toAbsolutePath().normalize()
        repeat(8) {
            if (Files.isDirectory(current.resolve("src/main/assets"))) return current
            if (Files.isDirectory(current.resolve("app/src/main/assets"))) return current.resolve("app")
            current = current.parent ?: current
        }
        error("Could not locate Android app module")
    }

    private fun readObject(path: Path): JsonObject =
        json.parseToJsonElement(Files.readString(path)).jsonObject

    private fun readJsonl(path: Path): List<JsonObject> =
        Files.readAllLines(path)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { json.parseToJsonElement(it).jsonObject }

    private fun JsonObject.obj(name: String): JsonObject =
        this[name]?.jsonObject ?: error("Missing object field $name")

    private fun JsonObject.array(name: String): JsonArray =
        this[name]?.jsonArray ?: error("Missing array field $name")

    private fun JsonObject.string(name: String): String =
        this[name]?.jsonPrimitive?.content ?: error("Missing string field $name")

    private companion object {
        val GENERIC_TERMS = setOf("角色", "道具", "在哪", "在哪里", "怎么拿", "怎么用", "骑士", "法师", "战士", "牧师", "村庄")
    }
}
```

- [ ] Run the new test and confirm it fails on the current baseline:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.gkp.ShiningForceIILocalizedTermCoverageTest
```

Expected: FAIL with counts below the target floor.

- [ ] Do not lower the thresholds to make the baseline pass. The failing test is the work queue.

## Task 3: Expand Character And NPC Localized Names

**Files:**

- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/entities.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/aliases.json`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl`
- Modify: `retrosprite-android/docs/gkp/shining-force-ii-md-localized-term-inventory.md`
- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/domain/SampleShiningForceIIQuestionPipelineTest.kt`

- [ ] Add or expand rows for playable characters and major NPCs until `npc` rows reach at least 25.
- [ ] For every accepted character name, add:
  - row-level alias in `entities.jsonl`
  - matching alias in `aliases.json`
  - source refs including `sf2.chinese_translation_names` when applicable
  - one short `answer_template` for `是谁` or `怎么用`
- [ ] Keep role words like `骑士`, `牧师`, `法师`, `前排` as low-confidence context only when already proven useful; do not rely on them as primary aliases.
- [ ] Add at least 12 character/NPC localized-name goldens.

Golden examples to include:

```jsonl
{"qa_id":"qa.sf2.localized.character.xiuyi.zh","language":"zh","question":"修伊怎么用？","game_id":"shining_force_ii_md","spoiler_level":"light","progress_gate":"start","expected_entity_ids":["npc.chester"],"expected_answer_contains":["Chester"],"source_refs":["sf2.manual_translation","sf2.chinese_translation_names"]}
{"qa_id":"qa.sf2.localized.character.jibu.zh","language":"zh","question":"吉布是谁？","game_id":"shining_force_ii_md","spoiler_level":"light","progress_gate":"start","expected_entity_ids":["npc.slade"],"expected_answer_contains":["Slade"],"source_refs":["sf2.characters","sf2.chinese_translation_names"]}
```

Verification:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest \
  --tests com.retrosprite.app.gkp.ShiningForceIILocalizedTermCoverageTest \
  --tests com.retrosprite.app.data.retrieval.SampleShiningForceIIRetrievalGoldenTest \
  --tests com.retrosprite.app.domain.SampleShiningForceIIQuestionPipelineTest
```

Expected after this task: character/NPC coverage assertions pass; item/location/boss/enemy floors can still fail.

## Task 4: Expand Items, Equipment, Key Items, Materials, Classes, And Spells

**Files:**

- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/items.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/mechanics.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/aliases.json`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl`
- Modify: `retrosprite-android/docs/gkp/shining-force-ii-md-localized-term-inventory.md`

- [ ] Expand item rows until `item` rows reach at least 45.
- [ ] Cover these categories:
  - recovery items
  - status recovery items
  - permanent stat boosters
  - special promotion items
  - key route items
  - mithril and forge-related terms
  - representative weapons and equipment categories
  - class names and spell names when players use them as item/build questions
- [ ] Use layered templates for location-sensitive items:
  - `answer_light`: low-spoiler purpose or broad stage.
  - `answer_clear`: chapter/area hint.
  - `answer_direct`: exact location only when source-backed and spoiler-allowed.
- [ ] Add at least 18 item/equipment localized-name goldens.
- [ ] Add ASR-prone transliteration variants only to `GameTermNormalizerTest` when they are misrecognitions, not accepted names.

Verification:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest \
  --tests com.retrosprite.app.gkp.GkpV0FixtureLintTest \
  --tests com.retrosprite.app.gkp.ShiningForceIILocalizedTermCoverageTest \
  --tests com.retrosprite.app.data.retrieval.SampleShiningForceIIRetrievalGoldenTest
```

Expected after this task: item coverage assertions pass; full coverage can still fail on locations, bosses, and enemies.

## Task 5: Expand Locations, Route Places, And Battle Areas

**Files:**

- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/locations.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/quests.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/spoiler_graph.json`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/aliases.json`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl`
- Modify: `retrosprite-android/docs/gkp/shining-force-ii-md-localized-term-inventory.md`

- [ ] Expand location rows until `location` rows reach at least 35.
- [ ] Cover towns, castles, towers, caves, shrines, route regions, secret villages, and battle areas using low-spoiler descriptions.
- [ ] Add progress gates when a place name itself is a spoiler.
- [ ] For every major route location, add aliases for:
  - English name
  - common Chinese localized name
  - alternate Chinese localized name when source-backed
  - short player phrase when specific and safe
- [ ] Add at least 12 localized location goldens.

Golden examples:

```jsonl
{"qa_id":"qa.sf2.localized.location.elven-forest.zh","language":"zh","question":"精灵森林是什么？","game_id":"shining_force_ii_md","spoiler_level":"light","progress_gate":"new_granseal","expected_entity_ids":["location.secret-villages"],"expected_answer_contains":["Elven Town"],"source_refs":["sf2.secrets","sf2.chinese_translation_names"]}
{"qa_id":"qa.sf2.localized.location.granseal.zh","language":"zh","question":"古兰西尔是哪？","game_id":"shining_force_ii_md","spoiler_level":"none","progress_gate":"start","expected_entity_ids":["location.granseal"],"expected_answer_contains":["Granseal"],"source_refs":["sf2.early_route","sf2.chinese_translation_names"]}
```

Verification:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest \
  --tests com.retrosprite.app.gkp.GkpV0FixtureLintTest \
  --tests com.retrosprite.app.gkp.ShiningForceIILocalizedTermCoverageTest \
  --tests com.retrosprite.app.data.retrieval.SampleShiningForceIIRetrievalGoldenTest
```

Expected after this task: location coverage assertions pass; full coverage can still fail on bosses and enemies.

## Task 6: Add Boss And Enemy Localized Names

**Files:**

- Create if missing: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/bosses.jsonl`
- Create if missing: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/enemies.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/manifest.json`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/aliases.json`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl`
- Modify: `retrosprite-android/docs/gkp/shining-force-ii-md-localized-term-inventory.md`
- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/gkp/GkpV0FixtureLintTest.kt` if `boss` and `enemy` are not yet allowed entity types.

- [ ] Add `knowledge/bosses.jsonl` and `knowledge/enemies.jsonl` to `manifest.contents.knowledge`.
- [ ] Add at least 12 boss rows and 20 enemy rows.
- [ ] Use low-spoiler default answers:
  - early visible role/threat
  - broad counterplay
  - no late-game reveal unless progress-gated
- [ ] Add aliases for localized names, English names, and safe short labels.
- [ ] Add at least 12 boss/enemy localized-name goldens.
- [ ] If `GkpV0FixtureLintTest` rejects `boss` or `enemy`, update the allowed entity type set in that test and in any preflight validator that enforces the same list.

Verification:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest \
  --tests com.retrosprite.app.gkp.GkpV0FixtureLintTest \
  --tests com.retrosprite.app.gkp.ShiningForceIILocalizedTermCoverageTest \
  --tests com.retrosprite.app.data.retrieval.SampleShiningForceIIRetrievalGoldenTest
```

Expected after this task: boss and enemy coverage assertions pass.

## Task 7: Add ASR Hotword And Normalization Coverage

**Files:**

- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/voice/asr/GkpAsrHotwordExtractorTest.kt`
- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/domain/normalization/GameTermNormalizerTest.kt`
- Modify: `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/domain/normalization/GameTermNormalizer.kt` only when a new observed ASR variant needs a deterministic local pinyin/edit-distance rule.
- Modify: `retrosprite-android/docs/gkp/shining-force-ii-md-localized-term-review-log.md`

- [ ] Add hotword extraction tests proving these categories export high-value hotwords:
  - character localized names
  - item localized names
  - location localized names
  - boss/enemy localized names
- [ ] Add at least 20 ASR variant cases to `GameTermNormalizerTest`.
- [ ] Keep ASR variants current-game scoped and ambiguity-aware.
- [ ] Record each ASR variant in `shining-force-ii-md-localized-term-review-log.md` with:
  - raw transcript
  - intended term
  - intended entity
  - whether it is a normalizer case or a rejected alias

Verification:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest \
  --tests com.retrosprite.app.voice.asr.GkpAsrHotwordExtractorTest \
  --tests com.retrosprite.app.domain.normalization.GameTermNormalizerTest \
  --tests com.retrosprite.app.domain.SampleShiningForceIIQuestionPipelineTest
```

Expected: all ASR hotword and normalizer cases pass without adding fake ASR terms to `aliases.json`.

## Task 8: Add Golden Coverage For Localized Player Questions

**Files:**

- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl`
- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/data/retrieval/SampleShiningForceIIRetrievalGoldenTest.kt`
- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/domain/SampleShiningForceIIQuestionPipelineTest.kt`

- [ ] Add at least 50 localized-name golden questions across all categories.
- [ ] Include these question shapes:
  - `<localized character> 是谁`
  - `<localized character> 怎么用`
  - `<localized item> 给谁用`
  - `<localized item> 在哪`
  - `<localized place> 是哪`
  - `<localized boss> 怎么打`
  - `<localized enemy> 怕什么`
  - `<localized spell/class/mechanic> 是什么`
- [ ] Include negative goldens for unsupported or ambiguous localized names.
- [ ] Use `expected_normalized_question` only when the row represents an ASR transcript case.

Verification:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest \
  --tests com.retrosprite.app.data.retrieval.SampleShiningForceIIRetrievalGoldenTest \
  --tests com.retrosprite.app.domain.SampleShiningForceIIQuestionPipelineTest
```

Expected: every localized-name golden resolves to the intended entity with source-cited evidence and no required LLM call.

## Task 9: Guard Against Alias Overmatching

**Files:**

- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/data/retrieval/LocalKnowledgeRetrievalPipelineTest.kt`
- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/gkp/ShiningForceIILocalizedTermCoverageTest.kt`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/aliases.json` only to remove or downgrade risky aliases found by tests.

- [ ] Add tests that prove generic words do not resolve as high-confidence entity hits:

```kotlin
@Test
fun `generic localized words do not overmatch specific entities`() = runTest {
    val pipeline = LocalKnowledgeRetrievalPipeline(FixtureKnowledgeRepository(loadShiningForceRows()))
    listOf("角色", "道具", "在哪", "怎么拿", "村庄", "骑士").forEach { question ->
        val results = pipeline.retrieve(
            RetrievalQuery(
                gameId = "shining_force_ii_md",
                normalizedQuery = pipeline.normalizeQuestion(question, "zh"),
                language = "zh",
                progressGate = "start",
                spoilerLevel = SpoilerLevel.LIGHT,
                limit = 5,
            )
        )
        assertTrue("question=<$question> got ${results.map { it.entityId }}", results.isEmpty())
    }
}
```

- [ ] If the test fails because a broad alias is too strong, remove it from `aliases.json` or keep it only inside a longer `question_patterns` entry.
- [ ] Keep real proper names such as `修伊`, `气合之玉`, and `精灵森林` as high-confidence aliases.

Verification:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest \
  --tests com.retrosprite.app.data.retrieval.LocalKnowledgeRetrievalPipelineTest \
  --tests com.retrosprite.app.gkp.ShiningForceIILocalizedTermCoverageTest
```

Expected: generic words do not retrieve unrelated specific entities.

## Task 10: Version, Changelog, And Final Verification

**Files:**

- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/manifest.json`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/changelog.md`
- Modify: `retrosprite-android/docs/gkp/shining-force-ii-md-localized-term-review-log.md`

- [ ] Bump `pack_version` to the next content version chosen for this slice. Use `0.3.0` if this ships before the full 1.0 content expansion, or `1.0.0` if it ships with the 1.0 pilot.
- [ ] Add a changelog entry summarizing:
  - localized character names added
  - localized item/equipment names added
  - localized location names added
  - boss/enemy names added
  - ASR normalizer cases added
  - localized golden count after the change
- [ ] Run fixture lint:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.gkp.GkpV0FixtureLintTest
```

- [ ] Run localized coverage tests:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.gkp.ShiningForceIILocalizedTermCoverageTest
```

- [ ] Run retrieval and pipeline goldens:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest \
  --tests com.retrosprite.app.data.retrieval.SampleShiningForceIIRetrievalGoldenTest \
  --tests com.retrosprite.app.domain.SampleShiningForceIIQuestionPipelineTest
```

- [ ] Run ASR tests:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest \
  --tests com.retrosprite.app.voice.asr.GkpAsrHotwordExtractorTest \
  --tests com.retrosprite.app.domain.normalization.GameTermNormalizerTest
```

- [ ] Run the broad unit suite:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest
```

Expected: all commands pass.

## Manual QA Set

Use text/debug first, then true hotkey voice if a device is available.

Character questions:

- `修伊怎么用？`
- `佳佳值得练吗？`
- `卡森是谁？`
- `吉布怎么练？`

Item questions:

- `气合之玉给谁用？`
- `奥义之书有什么用？`
- `米斯里鲁银在哪里？`
- `疾风的鸡肉要给谁？`

Location questions:

- `精灵森林在哪？`
- `古兰西尔是哪？`
- `古代之塔是什么？`

Enemy and boss questions:

- `<localized boss> 怎么打？`
- `<localized enemy> 怕什么？`
- `<localized encounter> 要不要练级？`

ASR transcript questions:

- `修医是谁`
- `气和之玉怎么用`
- `一路也有什么用`

## Done Definition

- The pack has a reviewable localized-term inventory and review log.
- High-value Chinese localized names for characters, items, equipment, spells, classes, locations, bosses, enemies, and mechanics are represented in GKP rows and aliases.
- At least 160 source-backed Chinese proper-name aliases are present.
- At least 50 localized-name goldens pass.
- At least 20 ASR-prone proper-noun variants are tested through `GameTermNormalizerTest` or endpoint pipeline tests.
- Generic words do not become high-confidence entity aliases.
- `GkpV0FixtureLintTest`, `ShiningForceIILocalizedTermCoverageTest`, Shining Force II retrieval goldens, ASR tests, and broad `:app:testDebugUnitTest` pass.
- No GKP schema change, runtime internet dependency, copied script text, or LLM-generated fact source is introduced.
