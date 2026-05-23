# Structured Game Q&A Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade RetroSprite Q&A from string-only answers into short, accurate, low-spoiler, evidence-backed game answers with structured diagnostics and rule-based question intent.

**Architecture:** Keep RetroArch and current UI contracts compatible while adding a structured domain result behind them. `DefaultQueryPipeline` will produce `AnswerResult`, endpoint diagnostics will expose intent/confidence/spoiler/source metadata, and GKP templates will progressively support `intent`, `answer_short`, `answer_detail`, and spoiler-tiered answers. This plan intentionally excludes OCR/PaddleOCR and QuestionInbox.

**Tech Stack:** Kotlin/JVM, kotlinx.serialization JSON, Room migrations, existing GKP JSONL assets, Gradle unit tests.

---

## Implementation Status

- Completed in this pass: structured `AnswerResult`, rule-based intent classification, diagnostics/log persistence fields, low-spoiler GKP template tiers, and Shining Force II golden coverage.
- Still intentionally out of scope: OCR/PaddleOCR, screenshot entity extraction, and QuestionInbox.
- Verification: `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest`

---

## Explicit Non-Goals

- Do not add OCR, PaddleOCR, MediaProjection, screen entity extraction, or screenshot OCR.
- Do not add QuestionInbox, author inbox UI, or automatic failed-question ingestion.
- Do not allow LLM output to become a factual source.
- Do not change RetroArch protocol fields beyond optional diagnostics carried internally by RetroSprite.
- Do not rewrite Home/Packs/Settings UI except for diagnostics metadata that already has surfaces.

## File Structure

- Create `app/src/main/kotlin/com/retrosprite/app/domain/models/AnswerResult.kt`
  - Owns `answerShort`, `answerDetail`, `sources`, `confidence`, `answerType`, `spoilerLevelUsed`, and `nextActions`.
- Create `app/src/main/kotlin/com/retrosprite/app/domain/intent/QuestionIntent.kt`
  - Owns the enum values and rule-based classifier.
- Modify `app/src/main/kotlin/com/retrosprite/app/domain/models/AnswerDecision.kt`
  - Adds intent, confidence, answer detail, and spoiler metadata to decisions.
- Modify `app/src/main/kotlin/com/retrosprite/app/domain/QueryPipeline.kt`
  - Adds `answerResult` to `QueryPipelineResult` while keeping `text` compatibility.
- Modify `app/src/main/kotlin/com/retrosprite/app/domain/DefaultQueryPipeline.kt`
  - Classifies intent before retrieval, passes it through `SessionContext`, and returns structured results.
- Modify `app/src/main/kotlin/com/retrosprite/app/domain/models/SessionContext.kt`
  - Adds `questionIntent`.
- Modify `app/src/main/kotlin/com/retrosprite/app/domain/policy/EvidenceAnswerPolicy.kt`
  - Chooses structured direct/no-evidence/spoiler answers and applies intent-aware answer type.
- Modify `app/src/main/kotlin/com/retrosprite/app/domain/policy/AnswerComposer.kt`
  - Produces `ComposedAnswer` with `AnswerResult`, preserving current string output.
- Modify `app/src/main/kotlin/com/retrosprite/app/data/retrieval/LocalKnowledgeRetrievalPipeline.kt`
  - Reads new template fields and chooses intent/spoiler-tiered answer text where present.
- Modify `app/src/main/kotlin/com/retrosprite/app/endpoint/model/RetroArchResponse.kt`
  - Adds structured metadata to `ResponseDiagnostics`.
- Modify `app/src/main/kotlin/com/retrosprite/app/endpoint/QueryPipelineResponseGenerator.kt`
  - Maps `AnswerResult` metadata into diagnostics.
- Modify `app/src/main/kotlin/com/retrosprite/app/endpoint/RequestLogger.kt`
  - Stores answer type, confidence, spoiler level used, answer short/detail, and next actions.
- Modify Room request-log types:
  - `app/src/main/kotlin/com/retrosprite/app/data/db/entity/RequestLogEntity.kt`
  - `app/src/main/kotlin/com/retrosprite/app/data/models/DomainModels.kt`
  - `app/src/main/kotlin/com/retrosprite/app/data/models/Mappers.kt`
  - `app/src/main/kotlin/com/retrosprite/app/data/db/RetroSpriteDatabase.kt`
  - `app/src/main/kotlin/com/retrosprite/app/endpoint/RoomBackedRequestLogSink.kt`
- Modify UI diagnostics mapper:
  - `app/src/main/kotlin/com/retrosprite/app/ui/integration/UiModelMappers.kt`
  - `app/src/main/kotlin/com/retrosprite/app/ui/viewmodel/UiContracts.kt`
- Modify Shining Force II GKP rows:
  - `app/src/main/assets/gkp/shining-force-ii-md/knowledge/items.jsonl`
  - `app/src/main/assets/gkp/shining-force-ii-md/knowledge/mechanics.jsonl`
- Tests:
  - `app/src/test/kotlin/com/retrosprite/app/domain/intent/QuestionIntentClassifierTest.kt`
  - `app/src/test/kotlin/com/retrosprite/app/domain/policy/EvidenceAnswerPolicyTest.kt`
  - `app/src/test/kotlin/com/retrosprite/app/domain/SampleShiningForceIIQuestionPipelineTest.kt`
  - `app/src/test/kotlin/com/retrosprite/app/endpoint/QueryPipelineResponseGeneratorTest.kt`
  - `app/src/test/kotlin/com/retrosprite/app/endpoint/RoomBackedRequestLogSinkTest.kt`
  - `app/src/test/kotlin/com/retrosprite/app/ui/integration/UiModelMappersTest.kt`

## Task 1: Add Structured Answer Models

**Files:**
- Create: `app/src/main/kotlin/com/retrosprite/app/domain/models/AnswerResult.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/QueryPipeline.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/domain/DefaultQueryPipelineTest.kt`

- [ ] **Step 1: Write failing tests**

Add a test asserting `answerDetailed` exposes `answerResult.answerShort`, sources, confidence, type, spoiler level, and next actions while `answer()` still returns the current string.

- [ ] **Step 2: Verify RED**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.domain.DefaultQueryPipelineTest
```

Expected: compile failure because `answerResult` and `AnswerResult` do not exist.

- [ ] **Step 3: Implement models**

Create:

```kotlin
package com.retrosprite.app.domain.models

enum class AnswerConfidence(val wireName: String) {
    High("high"),
    Medium("medium"),
    Low("low"),
}

enum class AnswerType(val wireName: String) {
    NameMapping("name_mapping"),
    Location("location"),
    Usage("usage"),
    Mechanic("mechanic"),
    RouteHint("route_hint"),
    Strategy("strategy"),
    Production("production"),
    NoEvidence("no_evidence"),
    UnknownOrOutOfScope("unknown_or_out_of_scope"),
}

enum class AnswerNextAction(val label: String) {
    MoreSpecific("更明确"),
    DirectAnswer("直接答案"),
    ViewSources("查看来源"),
    MarkIncorrect("这不对"),
}

data class AnswerResult(
    val answerShort: String,
    val answerDetail: String,
    val sources: List<String> = emptyList(),
    val confidence: AnswerConfidence = AnswerConfidence.Low,
    val answerType: AnswerType = AnswerType.UnknownOrOutOfScope,
    val spoilerLevelUsed: SpoilerLevel = SpoilerLevel.LIGHT,
    val nextActions: List<AnswerNextAction> = emptyList(),
) {
    val textWithSources: String
        get() = if (sources.isEmpty()) {
            answerDetail
        } else {
            "$answerDetail\n来源：${sources.distinct().joinToString(", ")}"
        }
}
```

Update `QueryPipelineResult`:

```kotlin
data class QueryPipelineResult(
    val text: String,
    val llmTrace: LlmCallTrace = LlmCallTrace(),
    val answerResult: AnswerResult = AnswerResult(
        answerShort = text,
        answerDetail = text,
    ),
)
```

- [ ] **Step 4: Verify GREEN**

Run the same test command; expected PASS.

## Task 2: Add Rule-Based Question Intent

**Files:**
- Create: `app/src/main/kotlin/com/retrosprite/app/domain/intent/QuestionIntent.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/models/SessionContext.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/DefaultQueryPipeline.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/domain/intent/QuestionIntentClassifierTest.kt`

- [ ] **Step 1: Write failing classifier tests**

Cover these examples:

```kotlin
"勇者之证英文叫什么" -> NameMapping
"Medical Herb 怎么用" -> Usage
"Mithril 在哪里" -> Location
"下一步去哪" -> RouteHint
"怎么复活" -> Mechanic
"Sarah 值得练吗" -> Strategy
"谁开发的" -> Production
"有没有交易系统" -> UnknownOrOutOfScope
```

- [ ] **Step 2: Verify RED**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.domain.intent.QuestionIntentClassifierTest
```

Expected: compile failure because classifier does not exist.

- [ ] **Step 3: Implement classifier**

Use ordered rules. Keep ambiguous "有没有..." questions unknown unless they match strong production/mechanic cues.

- [ ] **Step 4: Wire into session context**

Add `questionIntent: AnswerType = AnswerType.UnknownOrOutOfScope` to `SessionContext` and set it in `DefaultQueryPipeline` from `QuestionIntentClassifier.classify(question.orEmpty())`.

- [ ] **Step 5: Verify GREEN**

Run the classifier test and `DefaultQueryPipelineTest`.

## Task 3: Return Structured Answers from Policy and Composer

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/models/AnswerDecision.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/policy/EvidenceAnswerPolicy.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/policy/AnswerComposer.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/domain/policy/EvidenceAnswerPolicyTest.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/domain/policy/AnswerComposerTest.kt`

- [ ] **Step 1: Write failing tests**

Assert:
- no evidence returns `AnswerType.NoEvidence`, `AnswerConfidence.Low`, no sources;
- direct evidence returns context intent as answer type;
- exact template/high score returns `AnswerConfidence.High`;
- multi-evidence LLM answer keeps sources and uses context intent.

- [ ] **Step 2: Verify RED**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.domain.policy.EvidenceAnswerPolicyTest --tests com.retrosprite.app.domain.policy.AnswerComposerTest
```

Expected: compile/test failure because decisions do not carry metadata.

- [ ] **Step 3: Implement metadata in decisions**

Add fields to `DirectAnswer` and `ComposeWithLlm`:

```kotlin
val answerDetail: String = text
val answerType: AnswerType
val confidence: AnswerConfidence
val nextActions: List<AnswerNextAction>
```

- [ ] **Step 4: Build `ComposedAnswer.answerResult`**

`AnswerComposer.composeDetailed` must return both the compatibility text and structured `AnswerResult`.

- [ ] **Step 5: Verify GREEN**

Run the policy/composer tests.

## Task 4: Expose Structured Diagnostics

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/endpoint/model/RetroArchResponse.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/endpoint/QueryPipelineResponseGenerator.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/endpoint/RequestLogger.kt`
- Modify: Room/data/mappers listed in File Structure.
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/integration/UiModelMappers.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/viewmodel/UiContracts.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/endpoint/QueryPipelineResponseGeneratorTest.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/endpoint/RoomBackedRequestLogSinkTest.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/ui/integration/UiModelMappersTest.kt`

- [ ] **Step 1: Write failing diagnostics tests**

Assert `ResponseDiagnostics` and `/debug/latest-request` expose:

```text
answer_short
answer_detail
answer_type
confidence
spoiler_level_used
next_actions
```

- [ ] **Step 2: Verify RED**

Run endpoint and mapper tests; expect compile/test failures for missing fields.

- [ ] **Step 3: Implement diagnostics fields**

Use nullable/string-list fields so old callers remain compatible.

- [ ] **Step 4: Add Room migration**

Bump database version from 6 to 7 and add nullable columns:

```sql
ALTER TABLE request_logs ADD COLUMN answer_short TEXT;
ALTER TABLE request_logs ADD COLUMN answer_detail TEXT;
ALTER TABLE request_logs ADD COLUMN answer_type TEXT;
ALTER TABLE request_logs ADD COLUMN answer_confidence TEXT;
ALTER TABLE request_logs ADD COLUMN spoiler_level_used TEXT;
ALTER TABLE request_logs ADD COLUMN next_actions TEXT;
```

Store `next_actions` as a JSON-ish comma-separated string in v7 to avoid adding a new converter path.

- [ ] **Step 5: Verify GREEN**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.endpoint.QueryPipelineResponseGeneratorTest --tests com.retrosprite.app.endpoint.RoomBackedRequestLogSinkTest --tests com.retrosprite.app.ui.integration.UiModelMappersTest
```

## Task 5: Support Intent and Spoiler-Tiered GKP Templates

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/data/retrieval/LocalKnowledgeRetrievalPipeline.kt`
- Modify: `app/src/main/assets/gkp/shining-force-ii-md/knowledge/items.jsonl`
- Modify: `app/src/main/assets/gkp/shining-force-ii-md/knowledge/mechanics.jsonl`
- Test: `app/src/test/kotlin/com/retrosprite/app/domain/SampleShiningForceIIQuestionPipelineTest.kt`

- [ ] **Step 1: Write failing Shining Force II tests**

Add tests:
- `Warrior Pride 英文叫什么` returns name mapping, low spoiler;
- `Warrior Pride 有什么用` returns usage without location details;
- `Warrior Pride 在哪里` at light spoiler does not include the direct location;
- `Warrior Pride 在哪里` at direct spoiler includes New Granseal/tactical base;
- `隐藏物品怎么找` uses a direct template and skips LLM.

- [ ] **Step 2: Verify RED**

Run the Shining Force II pipeline tests; expected failures for missing template handling.

- [ ] **Step 3: Implement template selection**

When a template JSON has `intent`, only match it when the classified intent matches. When it has `answer_light`, `answer_clear`, and `answer_direct`, choose by requested spoiler level. When it has `answer_short`/`answer_detail`, prefer `answer_detail` for normal text and keep `answer_short` for diagnostics.

- [ ] **Step 4: Update sample GKP rows**

Add intent-aware templates for `Warrior Pride`, `Medical Herb`, hidden content, and core gameplay.

- [ ] **Step 5: Verify GREEN**

Run the Shining Force II pipeline tests.

## Task 6: Golden and Negative Acceptance

**Files:**
- Modify: `app/src/test/kotlin/com/retrosprite/app/data/retrieval/SampleShiningForceIIRetrievalGoldenTest.kt`
- Modify: `app/src/test/kotlin/com/retrosprite/app/domain/SampleShiningForceIIQuestionPipelineTest.kt`
- Optional modify: `app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl`

- [ ] **Step 1: Add acceptance cases**

Minimum cases:
- 汉化名对应英文名
- 角色值不值得练
- 道具用途
- 位置低剧透拦截
- 下一步低剧透提示
- ASR 误听样本
- 没有证据的问题

- [ ] **Step 2: Verify**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.domain.SampleShiningForceIIQuestionPipelineTest --tests com.retrosprite.app.data.retrieval.SampleShiningForceIIRetrievalGoldenTest
```

Expected: PASS.

## Task 7: Final Verification

- [ ] **Step 1: Static diff check**

```bash
git diff --check
```

- [ ] **Step 2: Unit tests**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest
```

- [ ] **Step 3: Android test compile**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:compileDebugAndroidTestKotlin
```

- [ ] **Step 4: Manual device notes**

Do not install unless explicitly requested. If installed later, verify:
- HUD/TTS reads `answer_short`;
- App/Diagnostics shows `answer_detail`, sources, answer type, confidence, and spoiler level;
- no-evidence answers still refuse instead of guessing.

## Self-Review

- Spec coverage: covers structured answers, rule intent, template upgrade, low-spoiler tiers, diagnostics, retrieval/answer/negative tests. OCR/PaddleOCR and QuestionInbox are explicitly excluded.
- Placeholder scan: no implementation task contains "TBD" or "implement later".
- Type consistency: `AnswerResult`, `AnswerType`, `AnswerConfidence`, and `AnswerNextAction` are introduced before endpoint/UI usage.
