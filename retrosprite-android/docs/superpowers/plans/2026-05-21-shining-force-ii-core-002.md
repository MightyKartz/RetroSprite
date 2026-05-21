# Shining Force II Core 002 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand the bundled Shining Force II Mega Drive GKP from a narrow M9.1 slice into `sf2-core-002`, covering production facts, early characters, consumables, hidden-content overview, and voice-like golden questions while preserving low-spoiler behavior.

**Architecture:** Keep `gkp.v0` unchanged. Add new JSONL knowledge rows and citation rows under the existing `shining-force-ii-md` pack, bump `pack_version`, extend aliases and golden Q&A, then verify through the existing retrieval and question-pipeline tests.

**Tech Stack:** Kotlin/JVM tests, Room-free fixture repositories, JSONL GKP assets, Gradle `testDebugUnitTest`.

---

### Task 1: RED Tests For Expanded SF2 Coverage

**Files:**
- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/domain/SampleShiningForceIIQuestionPipelineTest.kt`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl`

- [ ] **Step 1: Add failing question-pipeline tests**

Add tests that expect local answers for: developer/publisher facts, Sarah role, early consumable item use, hidden-content overview, and ASR-like promotion phrasing.

- [ ] **Step 2: Add failing golden Q&A rows**

Append golden rows for the same coverage plus no-evidence and spoiler-downgrade behavior.

- [ ] **Step 3: Run targeted tests and verify RED**

Run:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew testDebugUnitTest \
  --tests com.retrosprite.app.data.retrieval.SampleShiningForceIIRetrievalGoldenTest \
  --tests com.retrosprite.app.domain.SampleShiningForceIIQuestionPipelineTest
```

Expected: failures mention missing expected entities or answers lacking the new expected phrases.

### Task 2: GREEN GKP Content Expansion

**Files:**
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/manifest.json`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/sources/citations.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/aliases.json`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/entities.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/items.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/quests.jsonl`
- Create: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/production.jsonl`
- Create: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/strategies.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/changelog.md`

- [ ] **Step 1: Add source-backed rows**

Add original short rows for production facts, Sarah/Chester/Jaha/Kazin safe roles, Medical Herb/Healing Seed/Angel Wing/boosters, hidden-content overview, and early safe strategy.

- [ ] **Step 2: Update manifest and aliases**

Bump `pack_version` to `0.2.0`, include new knowledge files in `contents.knowledge`, and add spoken/ASR aliases.

- [ ] **Step 3: Run targeted tests and verify GREEN**

Run the same command from Task 1. Expected: both targeted test classes pass.

### Task 3: Full Local Verification

**Files:**
- No additional source changes unless verification exposes a real defect.

- [ ] **Step 1: Run all JVM tests**

Run:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew testDebugUnitTest
```

Expected: all JVM tests pass.

- [ ] **Step 2: Review diff for copyright and spoiler boundaries**

Confirm all prose is original, no ROM/manual/guide long text is copied, no hidden exact locations leak under `LIGHT`, and no unsupported facts are introduced.

