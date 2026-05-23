# Zero-LLM Natural Language Q&A Implementation Plan

> 生成日期：2026-05-22
> 范围：不接入 LLM、不接 OCR/PaddleOCR、不做 QuestionInbox。目标是在本地 GKP、规则意图、别名、检索和模板基础上支持玩家自然语言问法。

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:test-driven-development` for each code task and `retrosprite-dev` for product/architecture constraints. Keep changes small, testable, and local-first.

## Goal

让玩家可以用自然语言问游戏内问题，例如：

- “这游戏怎么玩？”
- “现在哪些角色适合培养？”
- “怎么玩经验高？”
- “新手先干什么？”
- “谁值得练？”
- “卡住了下一步去哪？”

系统必须在没有 LLM key、没有网络、LLM provider 关闭时仍能稳定回答。回答只来自当前游戏 GKP 的本地 evidence / `answer_templates` / aliases / deterministic retrieval，不让 LLM 作为事实来源，也不让无证据问题被猜答。

## Product Contract

- 热键语音回答默认 1-3 句话，TTS 只读 `answer_short`。
- App 详情页可以显示 `answer_detail`、来源、置信度、问题类型、剧透级别和可选追问动作。
- “自然语言”只表示玩家可以口语化提问，不表示系统可以泛聊。
- 遇到“现在”“当前队伍”“我卡住了”这类需要进度上下文的问题，若没有可靠 `progress_gate`，默认追问进度，而不是编。
- 位置、路线、隐藏物品和培养建议默认低剧透，支持“更明确 / 直接答案”升级。
- 无 evidence 时明确说“不确定 / 需要更多上下文”，并给出可执行补充问题。

## Current Baseline

项目已经具备以下基础：

- `AnswerResult`：结构化短答、详情、来源、置信度、类型、剧透级别和 next actions。
- `QuestionIntentClassifier`：规则识别 `name_mapping / location / usage / mechanic / route_hint / strategy / production`。
- `LocalKnowledgeRetrievalPipeline`：template match、name mapping、alias/entity match、FTS fallback。
- `EvidenceAnswerPolicy`：空证据拒答、剧透降级、多证据可选 LLM composer。
- Shining Force II GKP 已有 `mechanics / strategies / production / items / locations`、`answer_templates` 和 golden tests。

当前缺口：

- 自然概览类问题没有一等意图，例如“这游戏怎么玩 / 好玩在哪 / 新手先做什么”。
- 培养类和练级类被混在 `strategy`，Diagnostics 不够清晰，检索也无法精准加权。
- “现在 / 当前阶段 / 队伍里”这类上下文词没有 slot 提取，容易给过宽泛答案。
- 多条 evidence 时默认可能进入 LLM composer；本计划需要一条完全 deterministic composer 路线。
- GKP 内容还需要用玩家口语问题组织，而不是只放攻略事实点。

## Explicit Non-Goals

- 不接入 LLM，也不新增云端模型调用。
- 不接 OCR/PaddleOCR，不从截图识别当前地点、角色或菜单。
- 不做 QuestionInbox、失败样本自动入库或作者后台。
- 不做通用聊天、剧情陪聊或百科式开放问答。
- 不根据 ROM 内容、截图或音频保存隐私数据。
- 不复制攻略长文、手册长段或商业内容进 GKP。

## Target Architecture

```text
ASR/Text Question
  -> QuestionNormalizer
  -> NaturalQuestionFrame
       intent
       subject terms
       progress/context cues
       spoiler request
       ambiguity flags
  -> Local GKP Retrieval
       template patterns
       aliases/glossary
       intent/entity type boosts
       FTS fallback
  -> ZeroLlmAnswerPolicy
       direct template answer
       deterministic evidence summary
       clarification
       no evidence
       spoiler downgrade
  -> AnswerResult
       short/detail/sources/confidence/type/actions
  -> HUD/TTS + App Detail
```

The LLM adapter remains in the codebase for optional future composer use, but this milestone must be verifiable with `llmTrace.status = skipped` for all supported natural-language cases.

## Natural Intent Taxonomy

Extend the question model so Diagnostics and retrieval can distinguish natural game questions:

| Intent | Wire name | Examples | Default behavior |
| --- | --- | --- | --- |
| GameOverview | `game_overview` | “这游戏怎么玩？” “主要玩什么？” “好玩在哪？” | Answer from core gameplay template |
| BeginnerGuide | `beginner_guide` | “新手怎么玩？” “开局先干什么？” | Low-spoiler first-hour advice |
| TeamBuild | `team_build` | “哪些角色适合培养？” “谁值得练？” | If no progress, answer general + ask phase |
| Leveling | `leveling` | “怎么练级快？” “怎么玩经验高？” | Explain local leveling mechanics |
| RouteHint | `route_hint` | “下一步去哪？” “卡住了” | Ask progress if missing, low-spoiler hint if known |
| Strategy | `strategy` | “怎么打得稳？” “这个 boss 怎么打？” | Evidence-backed tactics |
| Mechanic | `mechanic` | “怎么转职？” “为什么不能复活？” | Deterministic mechanics answer |
| Usage | `usage` | “这个道具干嘛？” “给谁用？” | Item/entity template answer |
| Location | `location` | “在哪里？” “怎么拿？” | Spoiler-tiered answer |
| NameMapping | `name_mapping` | “勇者之证英文叫什么？” | Direct mapping |
| Production | `production` | “谁开发的？” “什么时候发售？” | Non-spoiler fact answer |
| Unknown | `unknown_or_out_of_scope` | “有没有恋爱系统？” with no evidence | Refuse/clarify, do not guess |

Implementation option:

- Preferred: add enum values to `AnswerType` for `GameOverview`, `BeginnerGuide`, `TeamBuild`, and `Leveling`.
- Conservative fallback: keep wire compatibility by mapping them to `Strategy`, but add `NaturalQuestionFrame.intentDetail`. Preferred is clearer for diagnostics and golden tests.

## GKP Content Model

No schema-breaking change is required. Use existing knowledge rows plus richer `answer_templates`.

Recommended row lanes for each real game GKP:

| Lane | Entity type | Required topics |
| --- | --- | --- |
| Core gameplay | `note` or `strategy` | “主要玩什么 / 游戏循环 / 乐趣 / 适合谁” |
| Beginner guide | `strategy` | “前 30 分钟 / 第一章 / 新手别踩坑” |
| Team build | `strategy` | “早期角色 / 中期角色 / 后期角色 / 替补原则” |
| Leveling | `mechanic` or `strategy` | “经验机制 / 补刀 / 治疗经验 / 低等级追赶” |
| Progress hints | `quest` or `strategy` | “下一步去哪 / 卡住时看什么 / 低剧透方向” |
| Mechanics | `mechanic` | “转职 / 复活 / 属性 / 装备 / 金钱” |
| Glossary | existing row aliases + `aliases.json` | 汉化名、英文名、民间译名、ASR 误听 |

Template example:

```json
{
  "intent": "leveling",
  "question_patterns": ["怎么玩经验高", "怎么练级快", "经验怎么刷", "低等级怎么追"],
  "answer": "想稳定拿经验，让低等级角色补最后一击；治疗和辅助行动也能帮部分角色追等级。别让高等级主力吃掉所有击杀。",
  "source_refs": ["sf2.project_mechanics"],
  "spoiler_level": "light"
}
```

For progress-sensitive templates:

```json
{
  "intent": "team_build",
  "question_patterns": ["现在哪些角色适合培养", "谁值得练", "培养谁"],
  "answer_light": "如果还没提供进度，我只能给通用原则：优先练当前能稳定出场、移动和生存不拖队伍、能补足治疗或远程输出的位置。",
  "answer_clear": "告诉我你现在到哪一章或刚收了哪些角色，我可以按当前阶段给更具体的培养名单。",
  "spoiler_light": "light",
  "spoiler_clear": "light",
  "source_refs": ["sf2.project_mechanics"]
}
```

## Implementation Tasks

### Task 1: Add Zero-LLM Natural Question Frame

**Files:**

- Create: `app/src/main/kotlin/com/retrosprite/app/domain/intent/NaturalQuestionFrame.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/DefaultQueryPipeline.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/models/SessionContext.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/domain/intent/NaturalQuestionFrameParserTest.kt`

- [ ] Add `NaturalQuestionFrame` with:
  - `answerType`
  - `intentDetail`
  - `subjectTerms`
  - `asksCurrentProgress`
  - `asksSpoilerEscalation`
  - `needsProgressContext`
  - `normalizedQuestion`
- [ ] Parse common cues:
  - current/progress: “现在 / 当前 / 目前 / 我这队 / 刚到 / 卡住”
  - overview: “怎么玩 / 主要玩什么 / 好玩在哪 / 乐趣”
  - team build: “培养 / 值得练 / 谁强 / 阵容 / 队伍”
  - leveling: “经验 / 练级 / 升级 / 刷级”
  - spoiler escalation: “直接告诉我 / 具体位置 / 不怕剧透”
- [ ] Keep ASR confusion normalization in one place.
- [ ] Store frame in `SessionContext`.

Acceptance:

- “这游戏怎么玩？” -> `GameOverview`, no progress required.
- “现在哪些角色适合培养？” -> `TeamBuild`, `asksCurrentProgress = true`, `needsProgressContext = true`.
- “怎么玩经验高？” -> `Leveling`, no progress required.
- “直接告诉我 Mithril 在哪” -> `Location`, `asksSpoilerEscalation = true`.

### Task 2: Expand Answer Types and Classifier Rules

**Files:**

- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/models/AnswerResult.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/intent/QuestionIntentClassifier.kt`
- Modify tests:
  - `QuestionIntentClassifierTest`
  - endpoint diagnostics mapper tests if enum display is asserted

- [ ] Add answer types:
  - `GameOverview("game_overview")`
  - `BeginnerGuide("beginner_guide")`
  - `TeamBuild("team_build")`
  - `Leveling("leveling")`
- [ ] Order classifier rules from specific to broad:
  - production/name/location before general “怎么玩”
  - leveling before general strategy
  - team build before general strategy
  - overview/beginner before unknown
- [ ] Add negative protection:
  - “有没有 X 系统” remains unknown unless GKP evidence matches X.
  - “怎么玩” with a known item/entity subject can become `usage` or `mechanic`, not always `game_overview`.

Acceptance:

- Existing tests still pass.
- New natural questions show meaningful `answer_type` in Diagnostics.

### Task 3: Add Intent-Aware Retrieval Boosting

**Files:**

- Modify: `app/src/main/kotlin/com/retrosprite/app/data/retrieval/LocalKnowledgeRetrievalPipeline.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/data/retrieval/LocalKnowledgeRetrievalPipelineTest.kt`

- [ ] Apply intent/entity-type boosts:
  - `game_overview` -> `note`, `strategy`, canonical aliases containing “核心玩法 / overview / gameplay”
  - `beginner_guide` -> `strategy`, `quest`, early `progress_gate`
  - `team_build` -> `strategy`, `npc`, `character`
  - `leveling` -> `mechanic`, `strategy`
  - `location` -> `location`, `item`, `quest`
- [ ] Keep template matches highest priority.
- [ ] Use `question_patterns` and `intent` as high-confidence exact matches.
- [ ] Down-rank broad FTS hits that only match stopwords like “系统 / 游戏 / 怎么”.
- [ ] Add close-candidate ambiguity detection for entity questions when top scores are close.

Acceptance:

- “这游戏怎么玩？” should hit core gameplay row before generic mechanics rows.
- “怎么玩经验高？” should hit leveling row before unrelated strategy rows.
- “哪些角色适合培养？” should hit team-building strategy row.

### Task 4: Add Deterministic Multi-Evidence Composer

**Files:**

- Create: `app/src/main/kotlin/com/retrosprite/app/domain/policy/LocalEvidenceSummarizer.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/policy/EvidenceAnswerPolicy.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/policy/AnswerComposer.kt`
- Test:
  - `EvidenceAnswerPolicyTest`
  - `AnswerComposerTest`
  - new `LocalEvidenceSummarizerTest`

- [ ] Add local summarizer that never calls LLM:
  - one evidence: return snippet
  - two to three evidence rows: join short snippets with de-duplication
  - cap `answer_short` to one sentence
  - put extra bullets/details only in `answer_detail`
- [ ] Add config path for zero-LLM natural Q&A:
  - `composeWithLlmForMultipleEvidence = false`, or
  - new `AnswerDecision.LocalSummary`
- [ ] Ensure supported natural questions produce `llmTrace.status = skipped`.
- [ ] Preserve source ids.
- [ ] If evidence conflicts, ask clarification instead of merging.

Acceptance:

- With multiple evidence rows and LLM disabled, answer remains useful and cited.
- No successful natural-language golden requires `LlmCallTrace.STATUS_USED`.

### Task 5: Add Clarification Policy for “Current” Questions

**Files:**

- Modify: `EvidenceAnswerPolicy.kt`
- Modify: `SessionContext.kt`
- Test: `EvidenceAnswerPolicyTest`

- [ ] If `NaturalQuestionFrame.needsProgressContext` and `progressGate == null`:
  - allow a safe general answer if a `light` template explicitly exists;
  - append or return a short clarification: “告诉我你现在到哪一章/刚收了哪些角色，我可以更具体。”
- [ ] For route hints, if no progress gate and no exact evidence:
  - ask “你现在在哪个城镇/刚打完哪场战斗？”
- [ ] For team build, if no progress gate:
  - answer general principles, not specific late-game names.
- [ ] For location/hidden item:
  - respect spoiler level and escalate only via next action.

Acceptance:

- “现在哪些角色适合培养？” without progress does not dump a late-game tier list.
- “下一步去哪？” without progress asks for location/chapter.
- “怎么练级快？” can answer without progress if GKP has mechanics evidence.

### Task 6: Upgrade Shining Force II GKP Natural Question Coverage

**Files:**

- Modify:
  - `app/src/main/assets/gkp/shining-force-ii-md/knowledge/strategies.jsonl`
  - `app/src/main/assets/gkp/shining-force-ii-md/knowledge/mechanics.jsonl`
  - `app/src/main/assets/gkp/shining-force-ii-md/aliases.json`
  - `app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl`
  - `app/src/main/assets/gkp/shining-force-ii-md/changelog.md`
- Test:
  - `SampleShiningForceIIQuestionPipelineTest`
  - `SampleShiningForceIIRetrievalGoldenTest`
  - `GkpV0FixtureLintTest`

- [ ] Add/expand rows:
  - `sf2.core_gameplay_loop`
  - `sf2.beginner_first_hour`
  - `sf2.team_build_general`
  - `sf2.leveling_general`
  - `sf2.route_hint_general`
- [ ] Add answer templates for:
  - “这游戏怎么玩？”
  - “新手先干什么？”
  - “现在哪些角色适合培养？”
  - “谁值得练？”
  - “怎么玩经验高？”
  - “卡住了下一步去哪？”
- [ ] Add ASR-style aliases:
  - “经验高 / 练经验 / 升级快”
  - “培养谁 / 谁值得连 / 谁值得练”
  - “这游戏怎么玩 / 主要玩什么 / 好玩在哪”
- [ ] Keep every answer original, short, and source-cited.

Acceptance:

- At least 12 new natural-language golden cases pass.
- Golden cases assert:
  - expected source ids
  - expected `answer_type`
  - `llm_status = skipped`
  - no heavy spoilers under `LIGHT`

### Task 7: Diagnostics and UX Copy

**Files:**

- Modify diagnostics mapper/UI only if current fields are insufficient:
  - `app/src/main/kotlin/com/retrosprite/app/ui/integration/UiModelMappers.kt`
  - `app/src/main/kotlin/com/retrosprite/app/ui/viewmodel/UiContracts.kt`
  - Diagnostics Compose screen files
- Test UI mappers as needed.

- [ ] Display natural intent labels:
  - `game_overview` -> “核心玩法”
  - `beginner_guide` -> “新手建议”
  - `team_build` -> “角色培养”
  - `leveling` -> “练级经验”
- [ ] Show when the answer is “本地模板 / 本地检索摘要 / 需要补充进度 / 无证据”.
- [ ] Ensure HUD still only shows short answer and optional source hint; detailed explanation stays in App.

Acceptance:

- Diagnostics can explain why “现在哪些角色适合培养？” returned a general answer or clarification.
- HUD remains compact.

### Task 8: Update GKP Authoring Docs

**Files:**

- Modify:
  - `docs/GKP_V0_SCHEMA.md`
  - `docs/REAL_GAME_GKP_EXPANSION_TEMPLATE.md`
  - optional: `docs/NEXT_IMPLEMENTATION_PLAN.md`

- [ ] Add a “Natural Question Coverage” checklist for every real GKP.
- [ ] Document required natural-language lanes:
  - core gameplay
  - beginner guide
  - team build
  - leveling
  - route hint
  - mechanics
  - name mapping
- [ ] Add examples of `answer_templates.intent`.
- [ ] Add golden test expectations for no-LLM support.

Acceptance:

- A new GKP author can see exactly what content is needed for natural questions.

## Test Plan

Run targeted tests while implementing:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.domain.intent.QuestionIntentClassifierTest
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.data.retrieval.LocalKnowledgeRetrievalPipelineTest
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.domain.SampleShiningForceIIQuestionPipelineTest
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.data.retrieval.SampleShiningForceIIRetrievalGoldenTest
```

Final verification:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest
```

Manual test questions on Shining Force II:

| Question | Expected behavior |
| --- | --- |
| 这游戏怎么玩？ | short core-loop answer, source cited, no LLM |
| 这个游戏主要玩什么？ | same lane, no generic chat |
| 新手先干什么？ | low-spoiler beginner guide |
| 现在哪些角色适合培养？ | general safe answer + ask for chapter/current roster |
| 谁值得练？ | team-building principles, no late spoiler dump |
| 怎么玩经验高？ | leveling mechanics answer |
| 经验怎么刷？ | same leveling row |
| 卡住了下一步去哪？ | ask for current location/chapter if progress unknown |
| 勇者之证英文叫什么？ | direct name mapping |
| Mithril 在哪？ | low-spoiler answer or spoiler downgrade under LIGHT |

## Acceptance Criteria

- All listed natural-language questions can be handled without LLM.
- `llmTrace.status` is `skipped` for supported natural-language golden cases.
- Empty evidence never calls LLM and never guesses.
- “现在 / 当前 / 我这队” questions do not pretend to know progress without context.
- Shining Force II has at least 12 natural-language golden cases.
- Diagnostics exposes answer type, confidence, source ids, and local/no-LLM decision path.
- No OCR/PaddleOCR/QuestionInbox code is introduced.

## Recommended Execution Order

1. Add `NaturalQuestionFrame` and expanded intent tests.
2. Extend `AnswerType` and classifier rules.
3. Add intent-aware retrieval boosts.
4. Add deterministic local summarizer / disable multi-evidence LLM for this path.
5. Add clarification policy for current/progress-sensitive questions.
6. Upgrade Shining Force II GKP templates and golden tests.
7. Polish Diagnostics labels.
8. Update GKP authoring docs.

This keeps the product promise tight: RetroSprite becomes more natural to ask, but stays local-first, evidence-grounded, low-spoiler, and usable without LLM.
