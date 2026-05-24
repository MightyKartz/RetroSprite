# Entity First Question Suggestions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make RetroSprite answer noisy voice questions by anchoring on game entities first, then guide players with non-duplicated nearby questions from the current GKP.

**Architecture:** Keep the current local-first Q&A pipeline. Add entity-first template matching inside local retrieval, add a retrieval-side question suggestion API backed by `answer_templates.question_patterns`, and carry structured follow-up questions through policy/composer so spoken answers stay short while text/HUD can show next questions.

**Status:** Implemented on 2026-05-24. Retrieval, answer policy, endpoint diagnostics, debug latest request, Room request logs, UI log mapping, and hotkey voice overlay now carry structured suggested questions.

**Tech Stack:** Kotlin, RetroSprite Android, GKP answer templates, local retrieval tests, answer policy/composer tests.

---

## Evidence And Scope

Observed real-device ASR can produce tails such as `怎么又` or `怎么也有` even when the key entity term is recognizable. The better fix is not more broad fallback; it is:

- Find the strongest entity mention first, preferring longest aliases like `米斯里鲁银` over shorter aliases like `米斯里鲁`.
- If the entity is clear and the question tail is noisy, use compatible templates for that entity.
- If no answer is found, suggest nearby answerable template questions from the same GKP.
- If an answer is found, show useful follow-up questions from the same entity/template set.

Out of scope:

- No cloud ASR or cloud search fallback.
- No ungrounded LLM suggestions.
- No broad generic chatbot recommendations.
- Schema migration is included because request logs and debug/latest need to preserve structured suggested questions.

## Files

- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/retrieval/RetrievalPipeline.kt`
  - Add a default `suggestQuestions(...)` hook.
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/models/SessionContext.kt`
  - Carry structured `suggestedQuestions`.
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/models/AnswerDecision.kt`
  - Carry structured suggestions on answer decisions.
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/models/AnswerResult.kt`
  - Render follow-up suggestions in text output while keeping `answerShort` clean.
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/DefaultQueryPipeline.kt`
  - Ask retrieval for suggestions after retrieval and before policy.
- Modify: `app/src/main/kotlin/com/retrosprite/app/data/retrieval/LocalKnowledgeRetrievalPipeline.kt`
  - Prefer longest entity aliases.
  - Allow entity-anchored noisy usage queries to hit compatible templates.
  - Generate ranked, non-duplicated suggested questions from GKP templates.
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/policy/EvidenceAnswerPolicy.kt`
  - Use retrieval-provided suggestions for no-evidence and successful answers.
- Modify: `app/src/main/kotlin/com/retrosprite/app/endpoint/model/RetroArchResponse.kt`
  - Carry `suggestedQuestions` diagnostics and `suggested_questions` in debug/latest output.
- Modify: `app/src/main/kotlin/com/retrosprite/app/endpoint/RequestLogger.kt`
  - Log structured suggested questions.
- Modify: `app/src/main/kotlin/com/retrosprite/app/endpoint/QueryPipelineResponseGenerator.kt`
  - Copy `AnswerResult.suggestedQuestions` into response diagnostics.
- Modify: `app/src/main/kotlin/com/retrosprite/app/endpoint/RetroArchEndpointServer.kt`
  - Expose suggested questions in `/debug/latest-request`.
- Modify: `app/src/main/kotlin/com/retrosprite/app/endpoint/RoomBackedRequestLogSink.kt`
  - Round-trip suggested questions through persistent request logs.
- Modify: `app/src/main/kotlin/com/retrosprite/app/data/db/RetroSpriteDatabase.kt`
  - Add migration 8 -> 9 for `request_logs.suggested_questions`.
- Modify: `app/src/main/kotlin/com/retrosprite/app/data/db/entity/RequestLogEntity.kt`
  - Add the persisted column.
- Modify: `app/src/main/kotlin/com/retrosprite/app/data/models/DomainModels.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/data/models/Mappers.kt`
  - Map the persisted list through the data layer.
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/integration/UiModelMappers.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/viewmodel/UiContracts.kt`
  - Surface suggested questions to the UI log model/detail JSON.
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionController.kt`
  - Show follow-up questions in the hotkey overlay while TTS still speaks only `answerShort`.
- Test: `app/src/test/kotlin/com/retrosprite/app/data/retrieval/LocalKnowledgeRetrievalPipelineTest.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/domain/policy/EvidenceAnswerPolicyTest.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/domain/policy/AnswerComposerTest.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/domain/DefaultQueryPipelineTest.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/endpoint/QueryPipelineResponseGeneratorTest.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/endpoint/RetroArchEndpointServerTest.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/endpoint/RoomBackedRequestLogSinkTest.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/ui/integration/UiModelMappersTest.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionControllerTest.kt`

## Acceptance Criteria

- `气合之玉怎么又` answers from the `气合之玉怎么用` usage template when the entity is present.
- `米斯里鲁银有什么用` uses the longest alias/entity match and answers the Mithril usage template.
- No-evidence text uses nearby GKP template questions when available instead of repeating fixed generic suggestions.
- Suggestions exclude the current question and near-duplicates.
- Successful answers can include follow-up questions in text output.
- `answerShort` remains the spoken short answer and does not include follow-up questions.

## Tasks

### Task 1: Retrieval API And Entity-First Template Matching

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/retrieval/RetrievalPipeline.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/data/retrieval/LocalKnowledgeRetrievalPipeline.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/data/retrieval/LocalKnowledgeRetrievalPipelineTest.kt`

- [x] **Step 1: Write failing retrieval tests**

Add tests for:

```kotlin
pipeline.retrieve(query("气合之玉怎么又")).first().entityId == "item.vigor-ball"
pipeline.retrieve(query("米斯里鲁银有什么用")).first().entityId == "item.mithril"
```

- [x] **Step 2: Verify red**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest --tests com.retrosprite.app.data.retrieval.LocalKnowledgeRetrievalPipelineTest
```

Expected: new tests fail because noisy tails and longest alias priority are not guaranteed.

- [x] **Step 3: Implement entity-first matching**

Sort entity terms longest-first, reuse that for `matchingTerm`, and allow entity-anchored noisy usage queries to use compatible usage templates.

- [x] **Step 4: Verify green**

Run the same test command and expect pass.

### Task 2: GKP-Backed Suggested Questions

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/retrieval/RetrievalPipeline.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/data/retrieval/LocalKnowledgeRetrievalPipeline.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/data/retrieval/LocalKnowledgeRetrievalPipelineTest.kt`

- [x] **Step 1: Write failing suggestion tests**

Add tests for:

```kotlin
pipeline.suggestQuestions(query("气合之欲怎么又"), emptyList()).contains("气合之玉怎么用？")
pipeline.suggestQuestions(query("气合之玉怎么用"), results).doesNotContain("气合之玉怎么用？")
```

- [x] **Step 2: Verify red**

Run the retrieval test command. Expected: missing API or empty suggestions.

- [x] **Step 3: Implement suggestions**

Extract `question_patterns` from allowed templates, score by phrase similarity, entity scope from current hits when present, remove current-question duplicates, and return top 3.

- [x] **Step 4: Verify green**

Run the retrieval test command and expect pass.

### Task 3: Policy And Answer Rendering

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/models/SessionContext.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/models/AnswerDecision.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/models/AnswerResult.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/DefaultQueryPipeline.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/policy/EvidenceAnswerPolicy.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/domain/policy/EvidenceAnswerPolicyTest.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/domain/policy/AnswerComposerTest.kt`

- [x] **Step 1: Write failing policy/composer tests**

Add tests that no-evidence uses `context.suggestedQuestions`, and direct answers render follow-ups while `answerShort` stays clean.

- [x] **Step 2: Verify red**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest --tests com.retrosprite.app.domain.policy.EvidenceAnswerPolicyTest --tests com.retrosprite.app.domain.policy.AnswerComposerTest
```

- [x] **Step 3: Implement structured suggestions**

Carry suggestions through `SessionContext -> AnswerDecision -> AnswerResult`. Render them as:

```text
你还可以问：
· 气合之玉在哪里？
· 谁适合转 Master Monk？
```

- [x] **Step 4: Verify green**

Run the same policy/composer test command and expect pass.

### Task 4: Endpoint, Logs, And Overlay Visibility

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/endpoint/model/RetroArchResponse.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/endpoint/RequestLogger.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/endpoint/QueryPipelineResponseGenerator.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/endpoint/RetroArchEndpointServer.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/endpoint/RoomBackedRequestLogSink.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/data/db/RetroSpriteDatabase.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/data/db/entity/RequestLogEntity.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/data/models/DomainModels.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/data/models/Mappers.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/integration/UiModelMappers.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/viewmodel/UiContracts.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionController.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/endpoint/QueryPipelineResponseGeneratorTest.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/endpoint/RetroArchEndpointServerTest.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/endpoint/RoomBackedRequestLogSinkTest.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/ui/integration/UiModelMappersTest.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionControllerTest.kt`

- [x] **Step 1: Write failing endpoint/overlay tests**

Add tests that diagnostics include suggestions, debug/latest exposes them, Room-backed logs round-trip them, and the hotkey overlay displays them without speaking them.

- [x] **Step 2: Implement visible structured suggestions**

Carry suggestions through endpoint diagnostics, request logging, persistent logs, UI log mapping, and hotkey overlay answer cards.

- [x] **Step 3: Verify green**

Run endpoint/overlay/UI mapper tests and expect pass.

### Task 5: Integration Verification

**Files:**
- No additional files.

- [x] **Step 1: Run target tests**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest --tests com.retrosprite.app.data.retrieval.LocalKnowledgeRetrievalPipelineTest --tests com.retrosprite.app.domain.policy.EvidenceAnswerPolicyTest --tests com.retrosprite.app.domain.policy.AnswerComposerTest
```

- [x] **Step 2: Build**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug
```

- [x] **Step 3: Diff hygiene**

```bash
git diff --check -- app/src/main/kotlin app/src/test/kotlin docs/superpowers/plans/2026-05-24-entity-first-question-suggestions.md
```

Expected: all commands succeed.
