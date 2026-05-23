# Shining Force II GKP 1.0 Pilot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand the bundled `shining-force-ii-md` Game Knowledge Pack into a real-game 1.0 pilot with 100-150 source-cited knowledge rows, 120+ golden questions, boss/enemy coverage, chapter route guidance, a usable location graph, and layered item-location answers.

**Architecture:** Keep `gkp.v0` unchanged and treat the pack as pure inspectable data. Add coverage-contract tests first, then grow the existing JSON/JSONL assets in small content lanes while preserving the current local `GkpV0Parser -> LocalKnowledgeRetrievalPipeline -> EvidenceAnswerPolicy` runtime path. Every new factual answer must remain short, original, source-backed, low-spoiler by default, and capable of zero-LLM retrieval for required goldens.

**Tech Stack:** Kotlin/JVM tests, JSON/JSONL GKP v0 assets, `GkpV0Parser`, fixture `KnowledgeRepository`, `LocalKnowledgeRetrievalPipeline`, `RepositoryGameResolver`, `EvidenceAnswerPolicy`, Gradle `testDebugUnitTest`, `jq` for content audits.

---

## Current Baseline

Current pack: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md`

- `pack_version`: `0.2.4`
- knowledge rows: 36
- golden questions: 60
- aliases: 93
- citation rows: 12
- progress gates: 5
- present knowledge files: `mechanics`, `items`, `locations`, `quests`, `entities`, `production`, `strategies`
- missing first-class lanes for 1.0: `bosses`, `enemies`, deeper chapter route rows, broader location graph, layered exact item-location rows

Known verification blocker before execution: the current JVM test task can fail before reaching GKP tests because `HotkeyVoiceOverlayRendererTest.kt` references unresolved overlay symbols. Resolve that compile blocker before treating any GKP test result as authoritative.

## 1.0 Target

The 1.0 pilot should land at:

- 100-150 knowledge rows, with 120 as the planned target.
- 120+ golden questions, with 140 as the planned target.
- 180+ aliases, including Chinese, English, romanized, common abbreviations, and ASR-observed variants.
- 20+ citation rows, including official, manual/manual-translation, community index, and project-authored play notes.
- 9-12 progress gates, covering opening, early travel, midgame route beats, late-game return, and endgame.
- 0 required LLM calls for the 120+ golden set.
- 0 copied long-form guide/manual prose.
- 0 exact late-game or hidden-location leakage under default `LIGHT`.

## File Map

**GKP assets**

- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/manifest.json`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/spoiler_graph.json`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/sources/citations.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/sources/licenses.md`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/aliases.json`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/changelog.md`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/entities.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/items.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/locations.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/mechanics.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/production.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/quests.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/strategies.jsonl`
- Create: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/bosses.jsonl`
- Create: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/enemies.jsonl`

**Tests**

- Create: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/gkp/ShiningForceIIGkpCoverageContractTest.kt`
- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/gkp/GkpV0FixtureLintTest.kt`
- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/data/retrieval/SampleShiningForceIIRetrievalGoldenTest.kt`
- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/domain/SampleShiningForceIIQuestionPipelineTest.kt`

**Docs**

- Modify: `retrosprite-android/docs/TEST_COVERAGE.md`
- Modify: `retrosprite-android/docs/NEXT_IMPLEMENTATION_PLAN.md`
- Modify: `retrosprite-android/docs/REAL_GAME_GKP_EXPANSION_TEMPLATE.md` only if the 1.0 pilot reveals a reusable production rule missing from the template.

## Content Budget

Use this target distribution. Stay within 100-150 rows by pruning weak rows rather than adding filler.

| Lane | Target rows | Primary files |
| --- | ---: | --- |
| Identity, production, version scope | 4-6 | `production.jsonl`, `entities.jsonl` |
| Core mechanics and tactics | 12-16 | `mechanics.jsonl`, `strategies.jsonl` |
| Characters and team roles | 16-22 | `entities.jsonl`, `strategies.jsonl` |
| Chapter route and quest beats | 18-24 | `quests.jsonl` |
| Location graph | 16-22 | `locations.jsonl` |
| Items, equipment, and layered locations | 24-32 | `items.jsonl` |
| Bosses and major encounters | 12-18 | `bosses.jsonl` |
| Enemy archetypes and status threats | 8-12 | `enemies.jsonl` |
| Hidden content overview and missable protection | 6-10 | `strategies.jsonl`, `items.jsonl`, `locations.jsonl` |

## Entity Id Conventions

Use these stable id patterns:

- `production.{topic}`
- `mechanic.{topic}`
- `strategy.{topic}`
- `npc.{character-slug}`
- `location.{area-slug}`
- `quest.{route-or-chapter-slug}`
- `item.{item-slug}`
- `item.{item-slug}.location`
- `boss.{boss-or-encounter-slug}`
- `enemy.{enemy-or-archetype-slug}`

Layered location rows should split broad use from exact position:

- `item.mithril`: what Mithril is and when to care.
- `item.mithril.location-early`: low/clear/direct answer tiers for early Mithril locations.
- `item.mithril.location-midgame`: progress-gated medium rows for later locations.

## Progress Gates

Replace the 5-gate graph with a coarse 10-gate graph. Keep names stable once committed.

```json
{
  "default_gate": "start",
  "gates": [
    { "gate_id": "start", "label": "Start / opening", "order": 0 },
    { "gate_id": "granseal_opening", "label": "Granseal opening", "order": 10 },
    { "gate_id": "galam_escape", "label": "Galam and escape", "order": 20 },
    { "gate_id": "new_granseal", "label": "New Granseal", "order": 30 },
    { "gate_id": "north_parmelia", "label": "North Parmelia route", "order": 40 },
    { "gate_id": "hassan_desert", "label": "Hassan and desert route", "order": 50 },
    { "gate_id": "creed_mansion", "label": "Creed's Mansion arc", "order": 60 },
    { "gate_id": "pacalon_moun", "label": "Pacalon and Moun arc", "order": 70 },
    { "gate_id": "mitula_return", "label": "Mitula Shrine and return route", "order": 80 },
    { "gate_id": "endgame", "label": "Endgame", "order": 90 }
  ],
  "edges": [
    { "from": "start", "to": "granseal_opening" },
    { "from": "granseal_opening", "to": "galam_escape" },
    { "from": "galam_escape", "to": "new_granseal" },
    { "from": "new_granseal", "to": "north_parmelia" },
    { "from": "north_parmelia", "to": "hassan_desert" },
    { "from": "hassan_desert", "to": "creed_mansion" },
    { "from": "creed_mansion", "to": "pacalon_moun" },
    { "from": "pacalon_moun", "to": "mitula_return" },
    { "from": "mitula_return", "to": "endgame" }
  ]
}
```

### Task 0: Restore Test Harness Before Content Work

**Files:**
- Inspect: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceOverlayRendererTest.kt`
- Inspect: `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/ui/overlay/AndroidHotkeyVoiceOverlayRenderer.kt`

- [ ] **Step 1: Run the targeted GKP test command**

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew testDebugUnitTest \
  --tests com.retrosprite.app.gkp.GkpV0FixtureLintTest \
  --tests com.retrosprite.app.data.retrieval.SampleShiningForceIIRetrievalGoldenTest \
  --tests com.retrosprite.app.domain.SampleShiningForceIIQuestionPipelineTest
```

Expected before fixing the current workspace: the command may fail during `compileDebugUnitTestKotlin` with unresolved overlay symbols. Do not edit GKP assets until the test task can compile.

- [ ] **Step 2: Clear the unrelated compile blocker**

If the failure mentions `hotkeyWaveWindowSpec`, `hotkeyAnswerWindowSpec`, or `HotkeyVoiceWindowAnchor`, repair the overlay test/source mismatch in the overlay files only. Do not alter GKP files as part of this step.

- [ ] **Step 3: Re-run the targeted GKP test command**

Expected after repair: the command reaches the GKP test classes. Existing GKP tests should either pass on the current baseline or fail only because Task 1 introduces new 1.0 coverage expectations.

### Task 1: Add RED Coverage Contract For GKP 1.0

**Files:**
- Create: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/gkp/ShiningForceIIGkpCoverageContractTest.kt`
- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/gkp/GkpV0FixtureLintTest.kt`

- [ ] **Step 1: Create a coverage-contract test**

Create `ShiningForceIIGkpCoverageContractTest.kt` that loads the pack directly from `src/main/assets/gkp/shining-force-ii-md` and asserts the 1.0 coverage floor:

```kotlin
package com.retrosprite.app.gkp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ShiningForceIIGkpCoverageContractTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `shining force ii gkp meets 1_0 coverage floor`() {
        val pack = moduleRoot().resolve("src/main/assets/gkp/shining-force-ii-md")
        val manifest = readObject(pack.resolve("manifest.json"))
        val contents = manifest.obj("contents")
        val knowledgePaths = contents.array("knowledge").map { it.jsonPrimitive.content }
        val rows = knowledgePaths.flatMap { readJsonl(pack.resolve(it)) }
        val goldens = readJsonl(pack.resolve(contents.string("qa_goldens")))
        val sources = readJsonl(pack.resolve(contents.string("citations")))
        val aliases = readObject(pack.resolve(contents.string("aliases"))).array("aliases")
        val gates = readObject(pack.resolve(contents.string("spoiler_graph"))).array("gates")

        assertEquals("1.0.0", manifest.string("pack_version"))
        assertTrue("knowledge rows=${rows.size}", rows.size in 100..150)
        assertTrue("goldens=${goldens.size}", goldens.size >= 120)
        assertTrue("sources=${sources.size}", sources.size >= 20)
        assertTrue("aliases=${aliases.size}", aliases.size >= 180)
        assertTrue("gates=${gates.size}", gates.size >= 9)

        val entityTypes = rows.groupingBy { it.string("entity_type") }.eachCount()
        assertTrue("boss rows=${entityTypes["boss"] ?: 0}", (entityTypes["boss"] ?: 0) >= 8)
        assertTrue("enemy rows=${entityTypes["enemy"] ?: 0}", (entityTypes["enemy"] ?: 0) >= 6)
        assertTrue("location rows=${entityTypes["location"] ?: 0}", (entityTypes["location"] ?: 0) >= 16)
        assertTrue("quest rows=${entityTypes["quest"] ?: 0}", (entityTypes["quest"] ?: 0) >= 16)
        assertTrue("item rows=${entityTypes["item"] ?: 0}", (entityTypes["item"] ?: 0) >= 24)
        assertTrue("npc rows=${entityTypes["npc"] ?: 0}", (entityTypes["npc"] ?: 0) >= 12)
        assertTrue("strategy rows=${entityTypes["strategy"] ?: 0}", (entityTypes["strategy"] ?: 0) >= 10)

        rows.forEach { row ->
            val entityId = row.string("entity_id")
            assertTrue("$entityId must cite at least one source", row.array("source_refs").isNotEmpty())
            assertTrue("$entityId must have at least two aliases", row.array("aliases").size >= 2)
        }

        val goldenQuestions = goldens.map { it.string("question") }
        listOf(
            "这个 boss 怎么打？",
            "这里的敌人怕什么？",
            "下一章去哪？",
            "这个地方在哪？",
            "Mithril 具体在哪里？",
            "直接告诉我勇者之证的位置",
            "不要剧透给我一个方向",
            "我会错过什么吗？"
        ).forEach { question ->
            assertTrue("missing golden question: $question", goldenQuestions.contains(question))
        }
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
        this[name]?.jsonObject ?: error("Missing object field '$name'")

    private fun JsonObject.array(name: String): JsonArray =
        this[name]?.jsonArray ?: error("Missing array field '$name'")

    private fun JsonObject.string(name: String): String =
        this[name]?.jsonPrimitive?.content ?: error("Missing string field '$name'")
}
```

- [ ] **Step 2: Raise the fixture lint floor**

In `GkpV0FixtureLintTest.kt`, update the `packs` map entry:

```kotlin
"shining-force-ii-md" to 120,
```

- [ ] **Step 3: Run RED coverage tests**

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew testDebugUnitTest \
  --tests com.retrosprite.app.gkp.ShiningForceIIGkpCoverageContractTest \
  --tests com.retrosprite.app.gkp.GkpV0FixtureLintTest
```

Expected: `ShiningForceIIGkpCoverageContractTest` fails on the current `0.2.4` pack because row, source, alias, gate, boss, enemy, location, quest, and item floors are not met.

### Task 2: Source Inventory And Copyright Boundary

**Files:**
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/sources/citations.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/sources/licenses.md`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/changelog.md`

- [ ] **Step 1: Add source rows for 1.0 lanes**

Append citation rows for these source ids. These rows are project-authored review notes with `url: null`; if an execution pass adds direct community URLs, add them as separate citation rows. The `notes` field must describe what the source supports, not copy source prose.

```jsonl
{"source_id":"sf2.route_index","title":"Shining Force II reviewed route and chapter notes","kind":"project_note","url":null,"license":"Project-authored notes; no copied guide text.","reliability":"community","notes":"Supports coarse route order, chapter-like progress gates, and area names after source review."}
{"source_id":"sf2.location_index","title":"Shining Force II reviewed location notes","kind":"project_note","url":null,"license":"Project-authored notes; no copied guide text.","reliability":"community","notes":"Supports town, dungeon, shrine, and travel-node names for the location graph after source review."}
{"source_id":"sf2.item_locations","title":"Shining Force II reviewed item-location notes","kind":"project_note","url":null,"license":"Project-authored notes; no copied guide text.","reliability":"community","notes":"Supports exact item-location rows gated at medium or heavy spoiler levels after source review."}
{"source_id":"sf2.bosses","title":"Shining Force II reviewed boss and encounter notes","kind":"project_note","url":null,"license":"Project-authored notes; no copied guide text.","reliability":"community","notes":"Supports boss names, encounter order, and broad tactical traits after source review."}
{"source_id":"sf2.enemies","title":"Shining Force II reviewed enemy notes","kind":"project_note","url":null,"license":"Project-authored notes; no copied guide text.","reliability":"community","notes":"Supports enemy archetypes, status threats, and broad preparation advice after source review."}
{"source_id":"sf2.project_route_notes","title":"RetroSprite original route notes for Shining Force II","kind":"project_note","url":null,"license":"Project-authored notes; no copied guide text.","reliability":"community","notes":"Original low-spoiler route summaries written from reviewed route references and play-session notes."}
{"source_id":"sf2.project_location_graph","title":"RetroSprite original location graph notes for Shining Force II","kind":"project_note","url":null,"license":"Project-authored notes; no copied guide text.","reliability":"community","notes":"Original location relationship notes for player-facing navigation answers."}
{"source_id":"sf2.project_encounter_notes","title":"RetroSprite original encounter notes for Shining Force II","kind":"project_note","url":null,"license":"Project-authored notes; no copied guide text.","reliability":"community","notes":"Original low-spoiler tactical summaries for boss and enemy questions."}
```

If execution records external URLs for these reviewed notes, keep them in separate citation rows with `kind = "community_note"` and keep the project-note rows above as the source refs used by answer prose.

- [ ] **Step 2: Update license notes**

In `sources/licenses.md`, add a `Shining Force II 1.0 Pilot Sources` section with these bullets:

```markdown
## Shining Force II 1.0 Pilot Sources

- Knowledge rows use original RetroSprite short summaries.
- Community guide/wiki pages are used as fact pointers only.
- No guide walkthrough paragraphs, manual scans, ROM data, script dumps, or commercial guidebook text are copied into the GKP.
- Exact locations and late-game boss details are progress-gated and spoiler-gated.
- Project-authored notes are licensed for RetroSprite sample/community pack use and cite the source rows that informed them.
```

- [ ] **Step 3: Add changelog entry**

Prepend this entry to `changelog.md`:

```markdown
## 1.0.0 - 2026-05-23

- Expanded the pack into the first real-game 1.0 GKP pilot target.
- Added boss/enemy lanes, chapter route guidance, a broader location graph, and layered item-location answers.
- Raised coverage targets to 100-150 knowledge rows and 120+ golden Q&A rows.
- Kept answer prose original, source-cited, and low-spoiler by default.
```

### Task 3: Manifest, Spoiler Graph, And New Knowledge Files

**Files:**
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/manifest.json`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/spoiler_graph.json`
- Create: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/bosses.jsonl`
- Create: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/enemies.jsonl`

- [ ] **Step 1: Update manifest**

Set `pack_version` to `1.0.0`, update `generated_at` to `2026-05-23T00:00:00Z`, and add the two new knowledge files:

```json
"contents": {
  "knowledge": [
    "knowledge/mechanics.jsonl",
    "knowledge/items.jsonl",
    "knowledge/locations.jsonl",
    "knowledge/quests.jsonl",
    "knowledge/entities.jsonl",
    "knowledge/production.jsonl",
    "knowledge/strategies.jsonl",
    "knowledge/bosses.jsonl",
    "knowledge/enemies.jsonl"
  ],
  "citations": "sources/citations.jsonl",
  "aliases": "aliases.json",
  "spoiler_graph": "spoiler_graph.json",
  "qa_goldens": "qa_goldens.jsonl"
}
```

- [ ] **Step 2: Replace spoiler graph with the 10-gate graph**

Use the graph in the `Progress Gates` section of this plan. After editing, every existing row with an old gate must be migrated to the nearest new gate.

- [ ] **Step 3: Add empty JSONL files with one starter row each**

Create `bosses.jsonl` with a starter row:

```json
{"entity_id":"boss.early-major-encounter","entity_type":"boss","canonical_name":"早期强敌战概览","language":"zh","aliases":["早期 boss","第一个难打的敌人","强敌战","boss 怎么打"],"description_short":"早期强敌战先按稳健站位处理：不要让脆弱角色单独吃反击，优先集中火力清掉威胁最高的目标。","description_long":"这是低剧透概览行，只给通用打法原则；具体 boss 名、位置和步骤放在对应 progress_gate 的 medium 行里。","progress_gate":"granseal_opening","spoiler_level":"light","source_refs":["sf2.project_encounter_notes"],"confidence":"community","answer_templates":[{"template_id":"template.sf2.early-major-encounter.zh","language":"zh","intent":"strategy","question_patterns":["这个 boss 怎么打？","早期 boss 怎么打","打不过强敌怎么办"],"answer_light":"先别急着冲上去，保持队伍抱团，优先让肉一点的角色吃接触，治疗角色站在安全距离。需要具体 boss 时告诉我你在哪个地点。","spoiler_light":"light","answer_clear":"如果你愿意说出地点或 boss 名，我可以给当前战斗的站位、优先目标和补给建议。","spoiler_clear":"medium","source_refs":["sf2.project_encounter_notes"]}]}
```

Create `enemies.jsonl` with a starter row:

```json
{"entity_id":"enemy.early-melee-archetype","entity_type":"enemy","canonical_name":"早期近战敌人概览","language":"zh","aliases":["早期敌人","近战敌人","小怪","杂兵","敌人怎么打"],"description_short":"早期近战敌人通常用站位和集火处理：让耐打角色先接触，其他人补刀，不要让治疗角色暴露。","description_long":"这行只记录安全通用原则；具体敌人名称、特殊能力和区域威胁放在后续 progress_gate 行。","progress_gate":"granseal_opening","spoiler_level":"none","source_refs":["sf2.project_encounter_notes"],"confidence":"community","answer_templates":[{"template_id":"template.sf2.early-melee-archetype.zh","language":"zh","intent":"strategy","question_patterns":["这里的敌人怎么打","小怪怎么处理","敌人怕什么"],"answer":"前期先用耐打角色卡位置，再让输出角色集中补刀。治疗和法师别站到敌人下一回合能直接碰到的位置。","source_refs":["sf2.project_encounter_notes"],"spoiler_level":"none"}]}
```

- [ ] **Step 4: Run parser and lint tests**

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew testDebugUnitTest \
  --tests com.retrosprite.app.gkp.GkpV0FixtureLintTest \
  --tests com.retrosprite.app.gkp.GkpV0ParserTest
```

Expected: failures are only from count floors not yet met, not invalid JSON, unknown source refs, unknown gates, or missing files.

### Task 4: Chapter Route And Quest Expansion

**Files:**
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/quests.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl`

- [ ] **Step 1: Add route rows**

Add 18-24 quest rows. Use these entity ids as the minimum route spine:

```text
quest.opening-granseal
quest.after-ancient-tower
quest.galam-escape
quest.reach-new-granseal
quest.new-granseal-first-hour
quest.ribble-and-polca-route
quest.bedoe-volcanon-route
quest.hassan-desert-route
quest.taros-shrine-route
quest.creed-mansion-route
quest.north-cave-route
quest.pacalon-route
quest.moun-route
quest.mitula-shrine-route
quest.return-to-grans
quest.ancient-tower-return
quest.endgame-preparation
quest.low-spoiler-current-direction
```

Each row must include:

- at least 2 aliases, including one voice-like Chinese phrase;
- `answer_templates` with `answer_light` for broad direction and `answer_clear` for explicit next action when useful;
- `source_refs` including `sf2.project_route_notes` plus a factual route source when available;
- `spoiler_level = "none"` or `"light"` for broad direction rows, `"medium"` for explicit route rows.

- [ ] **Step 2: Add route golden questions**

Append at least 24 route goldens, including these exact questions:

```jsonl
{"qa_id":"qa.sf2.route.current.low.zh","language":"zh","question":"不要剧透给我一个方向","game_id":"shining_force_ii_md","spoiler_level":"light","progress_gate":"new_granseal","expected_entity_ids":["quest.low-spoiler-current-direction"],"expected_answer_contains":["低剧透"],"source_refs":["sf2.project_route_notes"]}
{"qa_id":"qa.sf2.route.next-chapter.zh","language":"zh","question":"下一章去哪？","game_id":"shining_force_ii_md","spoiler_level":"light","progress_gate":"north_parmelia","expected_entity_ids":["quest.low-spoiler-current-direction"],"expected_answer_contains":["方向"],"source_refs":["sf2.project_route_notes"]}
{"qa_id":"qa.sf2.route.direct.zh","language":"zh","question":"直接告诉我下一步怎么走","game_id":"shining_force_ii_md","spoiler_level":"medium","progress_gate":"hassan_desert","expected_entity_ids":["quest.hassan-desert-route"],"expected_answer_contains":["下一步"],"source_refs":["sf2.project_route_notes"]}
```

- [ ] **Step 3: Add pipeline tests for route behavior**

In `SampleShiningForceIIQuestionPipelineTest.kt`, add table-driven route checks:

```kotlin
@Test
fun `shining force ii route questions stay low spoiler unless upgraded`() = runTest {
    val fixture = loadSamplePack()
    val llm = CountingLlmAdapter()
    val pipeline = newPipeline(fixture, llm)

    val low = pipeline.answerDetailed(
        label = "mega_drive__光明力量2",
        question = "不要剧透给我一个方向",
        spoilerLevel = SpoilerLevel.LIGHT,
    )
    assertTrue("answer=${low.text}", low.text.contains("低剧透") || low.text.contains("方向"))
    assertEquals(0, llm.callCount)

    val clear = pipeline.answerDetailed(
        label = "mega_drive__光明力量2",
        question = "直接告诉我下一步怎么走",
        spoilerLevel = SpoilerLevel.CLEAR,
    )
    assertTrue("answer=${clear.text}", clear.text.contains("下一步"))
    assertEquals(0, llm.callCount)
}
```

### Task 5: Location Graph Expansion

**Files:**
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/locations.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl`

- [ ] **Step 1: Add location rows**

Grow `locations.jsonl` to at least 16 rows. Use these entity ids as the minimum location graph:

```text
location.granseal
location.ancient-tower
location.yeel
location.galam
location.new-granseal
location.ribble
location.polca
location.bedoe
location.hassan
location.taros-shrine
location.creed-mansion
location.pacalon
location.moun
location.mitula-shrine
location.grans-island
location.zeon-battle-area
```

For each location:

- `description_short` answers "what/where is this" in one sentence;
- `description_long` gives neighboring-route context without full walkthrough prose;
- `progress_gate` matches the first safe point when the player may ask about it;
- `spoiler_level` is `none` for towns/basic hubs, `light` for route landmarks, `medium` or `heavy` for late-game locations.

- [ ] **Step 2: Add location graph goldens**

Append at least 18 location goldens, including:

```jsonl
{"qa_id":"qa.sf2.location.current.zh","language":"zh","question":"这个地方在哪？","game_id":"shining_force_ii_md","spoiler_level":"light","progress_gate":"new_granseal","expected_entity_ids":["location.new-granseal"],"expected_answer_contains":["New Granseal"],"source_refs":["sf2.project_location_graph"]}
{"qa_id":"qa.sf2.location.creed.zh","language":"zh","question":"Creed's Mansion 在哪？","game_id":"shining_force_ii_md","spoiler_level":"medium","progress_gate":"creed_mansion","expected_entity_ids":["location.creed-mansion"],"expected_answer_contains":["Creed"],"source_refs":["sf2.location_index"]}
{"qa_id":"qa.sf2.location.mitula.zh","language":"zh","question":"Mitula Shrine 是哪里？","game_id":"shining_force_ii_md","spoiler_level":"medium","progress_gate":"mitula_return","expected_entity_ids":["location.mitula-shrine"],"expected_answer_contains":["Mitula"],"source_refs":["sf2.location_index"]}
```

- [ ] **Step 3: Run retrieval goldens**

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew testDebugUnitTest \
  --tests com.retrosprite.app.data.retrieval.SampleShiningForceIIRetrievalGoldenTest
```

Expected: route and location goldens either pass or fail with specific missing entity ids that point to alias/template gaps.

### Task 6: Boss And Enemy Coverage

**Files:**
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/bosses.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/enemies.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl`

- [ ] **Step 1: Add boss rows**

Grow `bosses.jsonl` to 12-18 rows. Use these entity ids as the planned boss spine:

```text
boss.galam
boss.taros
boss.kraken
boss.willard
boss.zalbard
boss.cameela
boss.red-baron
boss.geshp
boss.odd-eye
boss.king-galam
boss.zeon
boss.generic-boss-preparation
```

Boss rows must follow these spoiler rules:

- `boss.generic-boss-preparation`: `spoiler_level = "light"`, safe advice only.
- early or already-named encounters: `spoiler_level = "medium"`.
- late-game boss identities or story-connected boss reveals: `spoiler_level = "heavy"`.
- do not expose hidden identity or narrative twists in `description_short`.

- [ ] **Step 2: Add enemy archetype rows**

Grow `enemies.jsonl` to 8-12 rows. Use these entity ids as the planned enemy lane:

```text
enemy.early-melee-archetype
enemy.ranged-threat-archetype
enemy.flying-threat-archetype
enemy.magic-threat-archetype
enemy.status-threat-archetype
enemy.high-defense-archetype
enemy.kraken-parts
enemy.late-demon-archetype
```

Each enemy row should answer "what is dangerous here" and "how should I position" without listing a full map script.

- [ ] **Step 3: Add boss/enemy goldens**

Append at least 24 boss/enemy goldens, including:

```jsonl
{"qa_id":"qa.sf2.boss.generic.light.zh","language":"zh","question":"这个 boss 怎么打？","game_id":"shining_force_ii_md","spoiler_level":"light","progress_gate":"new_granseal","expected_entity_ids":["boss.generic-boss-preparation"],"expected_answer_contains":["站位"],"source_refs":["sf2.project_encounter_notes"]}
{"qa_id":"qa.sf2.enemy.generic.zh","language":"zh","question":"这里的敌人怕什么？","game_id":"shining_force_ii_md","spoiler_level":"light","progress_gate":"north_parmelia","expected_entity_ids":["enemy.early-melee-archetype"],"expected_answer_contains":["位置"],"source_refs":["sf2.project_encounter_notes"]}
{"qa_id":"qa.sf2.boss.late.hidden.zh","language":"zh","question":"最终 boss 是谁？","game_id":"shining_force_ii_md","spoiler_level":"light","progress_gate":"start","expected_entity_ids":[],"expected_answer_contains":["剧透"],"source_refs":[]}
```

- [ ] **Step 4: Add pipeline tests for boss no-leak behavior**

Add a test that asks "最终 boss 是谁？" under `SpoilerLevel.LIGHT` and verifies the answer does not include the late boss name and does not call LLM.

### Task 7: Layered Item Location Answers

**Files:**
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/items.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl`

- [ ] **Step 1: Split item use rows from exact location rows**

For each important item, keep or create a light usage row and add a separate exact-location row. Minimum item-location ids:

```text
item.warrior-pride.location
item.pegasus-wing.location
item.vigor-ball.location
item.secret-book.location
item.silver-tank.location
item.mithril.location-early
item.mithril.location-midgame
item.medical-herb.early-use
item.healing-seed.early-use
item.angel-wing.early-use
item.power-water.use
item.protect-milk.use
item.quick-chicken.use
item.running-pimento.use
item.bright-honey.use
```

Use tiered templates for location rows:

```json
{
  "template_id": "template.sf2.warrior-pride.location.zh",
  "language": "zh",
  "intent": "location",
  "question_patterns": ["勇者之证在哪", "勇者之证具体位置", "直接告诉我勇者之证位置"],
  "answer_light": "低剧透：这是可错过或隐藏倾向的道具，先确认你当前所在区域；我不会默认列具体坐标。",
  "spoiler_light": "light",
  "answer_clear": "更明确：用来源确认后的原创短句写当前 progress_gate 的大致区域，不给逐格路线。",
  "spoiler_clear": "medium",
  "answer_direct": "直接答案：用来源确认后的原创短句写具体地点或触发步骤，控制在 1 到 3 句。",
  "spoiler_direct": "medium",
  "source_refs": ["sf2.item_locations"]
}
```

- [ ] **Step 2: Add item-location goldens**

Append at least 24 item goldens. Include both default low-spoiler and upgraded variants:

```jsonl
{"qa_id":"qa.sf2.item.warrior-pride.light.zh","language":"zh","question":"勇者之证在哪里？","game_id":"shining_force_ii_md","spoiler_level":"light","progress_gate":"new_granseal","expected_entity_ids":["item.warrior-pride.location"],"expected_answer_contains":["低剧透"],"source_refs":["sf2.item_locations"]}
{"qa_id":"qa.sf2.item.warrior-pride.direct.zh","language":"zh","question":"直接告诉我勇者之证的位置","game_id":"shining_force_ii_md","spoiler_level":"medium","progress_gate":"new_granseal","expected_entity_ids":["item.warrior-pride.location"],"expected_answer_contains":["位置"],"source_refs":["sf2.item_locations"]}
{"qa_id":"qa.sf2.item.mithril.direct.zh","language":"zh","question":"Mithril 具体在哪里？","game_id":"shining_force_ii_md","spoiler_level":"medium","progress_gate":"hassan_desert","expected_entity_ids":["item.mithril.location-early"],"expected_answer_contains":["Mithril"],"source_refs":["sf2.item_locations"]}
```

- [ ] **Step 3: Add pipeline tests for layered item answers**

Add tests that ask the same item-location question under `LIGHT` and `CLEAR`, then assert the `LIGHT` answer contains low-spoiler language while `CLEAR` contains a more explicit location phrase and no LLM call.

### Task 8: Character, Team, And Mechanics Depth

**Files:**
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/entities.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/mechanics.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/strategies.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl`

- [ ] **Step 1: Expand character rows**

Reach at least 12 `npc` rows. Minimum safe character ids:

```text
npc.bowie
npc.sarah
npc.chester
npc.jaha
npc.kazin
npc.slade
npc.peter
npc.kiwi
npc.may
npc.gerhalt
npc.luke
npc.rohde
```

Each character row answers identity, safe role, and "worth training?" without revealing late-game story.

- [ ] **Step 2: Expand mechanics and strategy rows**

Add or refine rows for:

```text
mechanic.turn-order
mechanic.terrain-and-movement
mechanic.status-effects
mechanic.spell-targeting
mechanic.weapon-upgrades
mechanic.promotion-tradeoff
strategy.team-build-by-role
strategy.safe-leveling
strategy.boss-preparation
strategy.resource-conservation
strategy.missable-protection
```

- [ ] **Step 3: Add character and mechanics goldens**

Append at least 24 goldens covering:

- "谁值得练？"
- "现在队伍怎么搭配？"
- "某角色怎么用？"
- "地形有什么影响？"
- "转职会不会亏？"
- "怎么打得稳？"
- "我会错过什么吗？"

### Task 9: Alias And ASR Robustness

**Files:**
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/aliases.json`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl`

- [ ] **Step 1: Grow aliases to 180+ terms**

Add aliases by entity, not as free-floating synonyms. Include:

- English names: `Shining Force II`, `Mithril`, `Warrior Pride`, `Pegasus Wing`, `Vigor Ball`, `Secret Book`, `Silver Tank`.
- Chinese names: `光明力量2`, `秘银`, `勇者之证`, `飞马之翼`, `活力球`, `秘传书`, `银战车`.
- Voice variants: `米斯里鲁`, `转职道具在哪`, `这个 boss`, `小怪`, `下一章`, `卡住了`, `直接告诉我`.
- Common intent aliases mapped to strategy/quest rows: `别剧透`, `给个方向`, `怎么搭配`, `练谁`, `打不过`.

- [ ] **Step 2: Add ASR robustness goldens**

Append at least 12 ASR-like goldens. Include the already observed `接受他几部这个角色`, plus variants for Mithril and route questions:

```jsonl
{"qa_id":"qa.sf2.asr.mithril.zh","language":"zh","question":"米斯里鲁在哪里","game_id":"shining_force_ii_md","spoiler_level":"light","progress_gate":"hassan_desert","expected_entity_ids":["item.mithril.location-early"],"expected_answer_contains":["低剧透"],"source_refs":["sf2.item_locations"]}
{"qa_id":"qa.sf2.asr.route.zh","language":"zh","question":"先给我个方向别剧透","game_id":"shining_force_ii_md","spoiler_level":"light","progress_gate":"new_granseal","expected_entity_ids":["quest.low-spoiler-current-direction"],"expected_answer_contains":["方向"],"source_refs":["sf2.project_route_notes"]}
```

### Task 10: Golden Suite To 120+

**Files:**
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl`

- [ ] **Step 1: Balance the golden set**

After Tasks 4-9, audit `qa_goldens.jsonl` and ensure these floors:

| Category | Minimum goldens |
| --- | ---: |
| core gameplay and beginner guidance | 12 |
| route and chapter guidance | 24 |
| location graph | 18 |
| item use and item locations | 24 |
| boss and enemy questions | 24 |
| character and team questions | 18 |
| mechanics and tactics | 14 |
| ASR/voice variants | 12 |
| no-evidence and out-of-scope | 8 |
| spoiler downgrade / no-leak | 8 |

The category total may exceed 120 because some questions count toward multiple categories. Keep the file at 120-150 rows unless a new category is justified by a real test failure.

- [ ] **Step 2: Run retrieval golden tests**

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew testDebugUnitTest \
  --tests com.retrosprite.app.data.retrieval.SampleShiningForceIIRetrievalGoldenTest
```

Expected: every golden with `expected_entity_ids` resolves those entities; every no-evidence golden returns no retrieval result.

### Task 11: Question Pipeline Regression

**Files:**
- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/domain/SampleShiningForceIIQuestionPipelineTest.kt`

- [ ] **Step 1: Add 1.0 top-question pipeline checks**

Add table-driven tests for at least these natural questions:

```kotlin
private val sf2OnePointZeroQuestions = listOf(
    "这个游戏主要是玩什么？" to "来源：sf2.official_overview",
    "不要剧透给我一个方向" to "来源：sf2.project_route_notes",
    "这个地方在哪？" to "来源：sf2.project_location_graph",
    "Mithril 具体在哪里？" to "来源：sf2.item_locations",
    "直接告诉我勇者之证的位置" to "来源：sf2.item_locations",
    "这个 boss 怎么打？" to "来源：sf2.project_encounter_notes",
    "这里的敌人怕什么？" to "来源：sf2.project_encounter_notes",
    "哪些角色适合培养？" to "来源：sf2.project_mechanics",
    "地形有什么影响？" to "来源：sf2.project_mechanics",
    "我会错过什么吗？" to "来源：sf2.secrets",
)
```

For each case, answer through `DefaultQueryPipeline`, assert the expected source string appears, and assert `llm.callCount == 0`.

- [ ] **Step 2: Add spoiler no-leak checks**

Add tests for late boss and exact hidden location questions under `SpoilerLevel.LIGHT`. The answer must either downgrade/ask for escalation or provide only broad protective advice. It must not include late-game boss identity or exact hidden-location steps.

- [ ] **Step 3: Run pipeline tests**

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew testDebugUnitTest \
  --tests com.retrosprite.app.domain.SampleShiningForceIIQuestionPipelineTest
```

Expected: all Shining Force II pipeline checks pass with `llm.callCount == 0`.

### Task 12: Content Audit Commands

**Files:**
- No source changes unless an audit exposes a concrete defect in GKP assets.

- [ ] **Step 1: Count rows, sources, aliases, and gates**

```bash
cd /Users/kartz/Development/Sprite
pack=retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md
printf 'knowledge='; find "$pack/knowledge" -maxdepth 1 -name '*.jsonl' -print0 | xargs -0 cat | wc -l
printf 'goldens='; wc -l < "$pack/qa_goldens.jsonl"
printf 'aliases='; jq -r '.aliases[]?.term' "$pack/aliases.json" | wc -l
printf 'sources='; wc -l < "$pack/sources/citations.jsonl"
printf 'gates='; jq -r '.gates[]?.gate_id' "$pack/spoiler_graph.json" | wc -l
```

Expected:

- `knowledge` between 100 and 150.
- `goldens` at least 120.
- `aliases` at least 180.
- `sources` at least 20.
- `gates` at least 9.

- [ ] **Step 2: Audit entity type distribution**

```bash
cd /Users/kartz/Development/Sprite
jq -r '.entity_type' retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/*.jsonl \
  | sort | uniq -c | sort -nr
```

Expected: output includes `boss`, `enemy`, `location`, `quest`, `item`, `npc`, `strategy`, `mechanic`, and `note`.

- [ ] **Step 3: Audit invalid source references through tests**

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew testDebugUnitTest \
  --tests com.retrosprite.app.gkp.GkpV0FixtureLintTest \
  --tests com.retrosprite.app.gkp.ShiningForceIIGkpCoverageContractTest
```

Expected: both tests pass.

### Task 13: Docs And Product Notes

**Files:**
- Modify: `retrosprite-android/docs/NEXT_IMPLEMENTATION_PLAN.md`
- Modify: `retrosprite-android/docs/TEST_COVERAGE.md`
- Modify: `retrosprite-android/docs/REAL_GAME_GKP_EXPANSION_TEMPLATE.md` if a reusable rule changed

- [ ] **Step 1: Update next implementation plan**

Add a short note under current state:

```markdown
- Shining Force II GKP `1.0.0` is now the first real-game 1.0 pilot: 100-150 knowledge rows, 120+ golden questions, boss/enemy lanes, route and location graph coverage, and layered item-location answers. Required goldens remain zero-LLM and low-spoiler by default.
```

- [ ] **Step 2: Update test coverage doc**

Document the new coverage contract:

```markdown
### Shining Force II GKP 1.0 Coverage

- `ShiningForceIIGkpCoverageContractTest` enforces row, source, alias, gate, and entity-type floors.
- `SampleShiningForceIIRetrievalGoldenTest` runs every `qa_goldens.jsonl` row through the local retrieval pipeline.
- `SampleShiningForceIIQuestionPipelineTest` verifies representative player questions answer through GKP evidence without LLM calls.
```

- [ ] **Step 3: Update expansion template only if needed**

If implementation discovers a reusable rule, add exactly one concise rule to `REAL_GAME_GKP_EXPANSION_TEMPLATE.md`. Examples of valid reusable rules:

- "Layer item-location rows separately from item-use rows."
- "Boss identity rows may be heavy even when tactical preparation rows are light."
- "Route goldens must include both low-spoiler and direct-answer phrasings."

### Task 14: Full Verification

**Files:**
- No additional source changes unless verification exposes a concrete defect.

- [ ] **Step 1: Run targeted GKP verification**

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew testDebugUnitTest \
  --tests com.retrosprite.app.gkp.GkpV0FixtureLintTest \
  --tests com.retrosprite.app.gkp.ShiningForceIIGkpCoverageContractTest \
  --tests com.retrosprite.app.data.retrieval.SampleShiningForceIIRetrievalGoldenTest \
  --tests com.retrosprite.app.domain.SampleShiningForceIIQuestionPipelineTest
```

Expected: all targeted GKP tests pass.

- [ ] **Step 2: Run full JVM test suite**

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew testDebugUnitTest
```

Expected: all JVM tests pass.

- [ ] **Step 3: Build debug APK**

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:assembleDebug
```

Expected: debug APK builds successfully.

- [ ] **Step 4: Run whitespace diff check**

```bash
cd /Users/kartz/Development/Sprite
git diff --check
```

Expected: no whitespace errors.

- [ ] **Step 5: Optional device smoke**

When an AVD or RG 476H is available:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
adb forward tcp:4404 tcp:4404
./scripts/android_avd_smoke.sh
```

Expected: endpoint smoke still passes, and at least one Shining Force II `/debug/ask` or hotkey voice path returns `source_ids` from the 1.0 pack with `llm_status=skipped`.

## Self-Review

- Spec coverage: the plan covers boss/enemy rows, chapter route rows, location graph rows, layered item-location answers, 100-150 knowledge rows, 120+ goldens, aliases, sources, tests, docs, and verification.
- Scope check: this is a single GKP content expansion and test hardening plan. It does not add a new schema, registry, RAG-Anything builder, Android runtime retriever, or LLM dependency.
- Plan scan: implementation steps name concrete files, target counts, entity ids, commands, and expected results. Task 2 uses project-note citation rows with `url: null` so execution can proceed without dangling source fields.
- Risk boundaries: do not copy walkthrough/manual paragraphs, do not include ROM data, do not expose late-game identities under `LIGHT`, and do not rely on LLM output as a factual source.
