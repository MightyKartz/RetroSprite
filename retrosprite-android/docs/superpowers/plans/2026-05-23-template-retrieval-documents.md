# Template Retrieval Documents Implementation Plan

> 生成日期：2026-05-23
> 范围：把 GKP `answer_templates` 作为一等可检索文档参与本地问答检索，并明确它和 ASR 热词/术语归一化链路的边界。优先解决“模板相近但不是准确模板问法”的玩家语音问题，例如模板是“这游戏怎么玩？”，玩家问“这游戏玩什么？”。

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:test-driven-development` for each code task and `retrosprite-dev` for product/architecture constraints. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用一个瘦身 MVP 让 GKP `answer_templates` 和关键名称别名能被近似口语问法稳定命中；玩家说“这游戏玩什么？”或使用知名汉化版角色/道具/地点名提问时，应命中已有 GKP 事实，而不是要求作者枚举所有同义 `question_patterns`。

**Architecture:** 不改 GKP schema、不改 Room/FTS schema、不引入向量库或 LLM。ASR 层先用 GKP 热词和 `GameTermNormalizer` 处理真实语音 transcript；检索层只消费归一化后的 question。先抽出当前模板答案选择逻辑，再新增一个轻量 `TemplateDocumentMatcher`，同时把最常见汉化版专有名词作为有来源的 GKP aliases/name mapping 数据补齐。在 `LocalKnowledgeRetrievalPipeline` 中保留“name mapping -> 精确模板 -> 模板文档近似匹配 -> alias/entity -> FTS”的顺序检索。独立 `TemplateRetrievalDocumentBuilder`、模板检索 trace 和 SQLite template FTS 都延后，除非 MVP 测试证明需要。

**Tech Stack:** Kotlin/JVM, existing GKP JSONL, existing Room/SQLite FTS, existing unit test stack, existing ASR hotword and `GameTermNormalizer` path. MVP 不新增云服务、不新增向量数据库、不新增运行时 LLM 依赖。

---

## Current Baseline

当前项目已有以下能力：

- `QuestionIntentClassifier.kt` 已支持 `game_overview`，但关键词集中在“怎么玩 / 主要玩什么 / 核心玩法”等表达，尚未覆盖“这游戏玩什么 / 玩法是什么 / 主要干什么”等口语变体。
- `LocalKnowledgeRetrievalPipeline.kt` 已解析 `answerTemplates`，但模板匹配主要依赖 normalized substring：
  - `question_patterns` 包含 query，或 query 包含 pattern。
  - template `intent` 若存在，必须等于分类后的 query intent。
  - `row.matchingTerm(normalizedQuery)` 仍是别名/实体词级匹配，不是模板文档级检索。
- `KnowledgeFtsSchema.kt` 的 `knowledge_fts` 只索引 `canonical_name`、`aliases`、`description_short`、`description_long`，不索引 `answer_templates_json`。
- Shining Force II GKP 已有 `note.core-gameplay-loop`，其 aliases/templates 覆盖“这游戏怎么玩 / 核心玩法 / 主要玩什么”，但 golden 里没有“这游戏玩什么？”这个回归样本。
- Shining Force II GKP 已经开始记录部分汉化名来源，例如 `sf2.yzzl_chinese_patch` 和 `sf2.chinese_translation_names`，并在实体中包含“修伊 / 佳佳 / 卡森 / 气合之玉 / 精灵森林”等别名；但当前计划还没有把“知名汉化版名称覆盖率”作为明确验收项。
- ASR 相关代码已经引入两层真实语音修正：
  - `GkpAsrHotwordExtractor` 从当前游戏 GKP 的 `canonicalName`、`aliases` 和模板 pattern 中提取 sherpa-onnx 热词，优先提升角色/道具/地点等汉化专有名。
  - `GameTermNormalizer` 在 `hotkey_voice` 请求进入 query pipeline 前，用当前游戏 GKP 术语做保守同音/一字差归一化，例如 `修医是谁` -> `修伊是谁`、`气和之玉怎么用` -> `气合之玉怎么用`。
- endpoint diagnostics / request log 已经有 raw/normalized question 相关字段；这些属于 ASR 归一化可观测性，不等同于模板检索 trace。

因此，“这游戏玩什么？”这种近似问法可能因为 classifier 变体不足、模板 substring 不成立、FTS 不索引模板文本而漏掉正确模板；“修伊怎么用 / 气合之玉给谁 / 精灵森林在哪”这类中文玩家常用名也可能因为别名覆盖不完整而漏召回。

## Design Decision

MVP 采用“运行时轻量模板文档检索”，而不是马上做 DB schema migration 或完整检索子系统：

- GKP 的单游戏知识规模小，运行时从 `listByGame(gameId)` 生成模板文档成本低。
- 不改 Room FTS schema，避免迁移和线上数据风险。
- 可以把匹配算法集中在一个可单测的 Kotlin 类里。
- 先解决 5-8 个真实漏召回问法；不要为了一个问题一次性搭出大型 ranker。
- 后续若模板数量增长，再把 `answer_templates_json` 或独立 `template_documents_fts` 加入 SQLite FTS。

核心原则：

- 模板仍然必须来自 GKP evidence，不能由 LLM 生成事实。
- 汉化版名称只能作为有来源的 alias/name mapping，不应无来源地改写事实或替换 canonical identity。
- ASR 归一化只能把 transcript 改成当前 GKP 中已有的高置信术语，不能把低证据问题改成“看起来能回答”的问题。
- 模板文档命中只能选择已有模板答案，不能自由编写答案。
- fuzzy/概念匹配只决定“找哪条模板”，不改变答案内容。
- 相似度低于阈值时继续走现有 fallback 或拒答，不能猜答。

## Recommended MVP Scope

这份方案适合后续 GKP 开发，但第一版必须收窄：

1. 扩 `QuestionIntentClassifier` 的少量自然问法，避免明显意图漏判。
2. 抽出当前模板选择逻辑，确保 exact template 和 template document 使用同一套 spoiler-tier 选择规则。
3. 新增 `TemplateConceptExtractor` 和 `TemplateDocumentMatcher`，只做概念重叠、pattern 相似度和 intent 兼容性。
4. 只覆盖 Shining Force II 的核心玩法、乐趣、新手、队伍、练级、道具用途、地点这几类真实问法。
5. 补齐最知名汉化版的高频专有名词别名：先做 20-30 个角色、关键道具、核心地点，不做全量翻译考据。
6. 复用已经存在的 ASR 热词和 `GameTermNormalizer`；模板检索本身不再新增 ASR 字典、endpoint 字段或 Room migration。
7. 不新增 UI、不新增向量模型。

## Research Notes

本计划只借鉴成熟检索系统的结构，不把大型 RAG 框架直接塞进 Android 运行时。

- BM25 的经验：把可回答单元当成 document，使用多字段加权排序，比单个 substring 判断更稳。
- Sentence-BERT / semantic textual similarity 的经验：相似问法最好比较句子级语义；但 Android MVP 先不用模型，先用概念标签和轻量 lexical scoring 逼近。
- FAISS 的经验：向量检索适合后续大规模语义检索；当前 GKP 小规模、离线、可解释，暂不引入。
- Haystack / LangChain / LlamaIndex 的共同模式：Document + metadata + retriever + ranker + answer composer。RetroSprite 只采用这层架构，不采用其云模型和复杂依赖。
- RapidFuzz 的经验：fuzzy matching 应该是可解释、可阈值控制的局部能力。Kotlin MVP 用 char n-gram / Dice / concept overlap 自实现，避免引入 Python/C++ 运行时。

2026-05-23 通过 GitHub API 抽样查看的项目热度：

| Project | Stars | 本计划借鉴点 |
| --- | ---: | --- |
| `langchain-ai/langchain` | 137449 | document/retriever/ranker/composer 分层 |
| `run-llama/llama_index` | 49606 | 把原始资料切成可检索节点 |
| `facebookresearch/faiss` | 40112 | 后续语义向量检索方向 |
| `deepset-ai/haystack` | 25348 | pipeline 化检索与元数据过滤 |
| `rapidfuzz/RapidFuzz` | 3918 | 可解释字符串相似度和阈值控制 |

## Target Retrieval Flow

```text
ASR/Text Question
  -> ASR hotword biasing, when voice path has current-game context
  -> GameTermNormalizer, only for hotkey_voice transcript and current-game GKP rows
  -> normalize natural question
  -> QuestionIntentClassifier
  -> list GKP rows for current game
  -> name mapping match
  -> exact template match
  -> template document retrieval
       build lightweight TemplateRetrievalDocument list in memory
       extract query concept tags
       score patterns + concept tags + aliases + intent
       threshold and rank
  -> alias/entity match
  -> SQLite FTS fallback
  -> local RetrievalResult/AnswerResult with GKP source ids; LLM remains skipped if that trace is exposed
```

## Template Document Model

Create an internal data model that represents each valid `answer_template` as a retrievable in-memory document. Keep it in `TemplateDocumentMatcher.kt` for MVP; split it out only if the file becomes hard to read.

```kotlin
internal data class TemplateRetrievalDocument(
    val documentId: String,
    val entityId: String,
    val canonicalName: String,
    val entityType: String,
    val intent: String?,
    val questionPatterns: List<String>,
    val aliases: List<String>,
    val conceptTags: Set<TemplateConceptTag>,
    val selectedAnswer: String,
    val sourceRefs: List<String>,
    val spoilerLevel: SpoilerLevel,
    val sourceOrder: Int,
    val searchText: String,
)
```

`KnowledgeChunkDomain` already carries `gameId`; the matcher should still receive only rows for the current game from `LocalKnowledgeRetrievalPipeline`.

Stable `documentId` rules:

- If template JSON has `template_id`, use `${entityId}#${template_id}`.
- Else if template has `intent`, use `${entityId}#${intent}#${index}`.
- Else use `${entityId}#template#${index}`.

`searchText` joins normalized:

- `canonicalName`
- row aliases
- source-backed Chinese localization aliases from the row and `aliases.json`
- template `question_patterns`
- template `intent`
- selected template answer with lower scoring weight

## Concept Tags

Add a very small local concept vocabulary. This is not a general ontology; it is only a bridge from common player phrasing to existing template topics.

```kotlin
internal enum class TemplateConceptTag {
    GameplayLoop,
    FunFactor,
    BeginnerStart,
    TeamBuild,
    Leveling,
    Location,
    ItemUsage,
    Mechanic,
    Production,
    SpoilerEscalation,
}
```

Initial phrase mapping:

| Concept | Query/template phrases |
| --- | --- |
| `GameplayLoop` | `怎么玩`, `玩什么`, `主要玩什么`, `玩法是什么`, `核心玩法`, `主要干什么`, `游戏循环` |
| `FunFactor` | `好玩在哪`, `乐趣`, `爽点`, `有意思在哪` |
| `BeginnerStart` | `新手`, `开局`, `刚开始`, `先干什么`, `入门` |
| `TeamBuild` | `培养`, `值得练`, `谁强`, `阵容`, `队伍` |
| `Leveling` | `经验`, `练级`, `升级`, `刷级`, `追等级` |
| `Location` | `在哪`, `哪里`, `怎么拿`, `去哪里` |
| `ItemUsage` | `干嘛`, `有什么用`, `给谁用`, `怎么用` |
| `Mechanic` | `转职`, `复活`, `属性`, `机制`, `规则` |
| `Production` | `谁做的`, `开发`, `发行`, `哪一年`, `平台` |
| `SpoilerEscalation` | `直接告诉我`, `不怕剧透`, `具体位置`, `详细说` |

This directly covers the target case:

- User query: “这游戏玩什么？”
- Extracted concept: `GameplayLoop`
- Existing template patterns: “这游戏怎么玩 / 核心玩法 / 主要玩什么”
- Template document concepts: `GameplayLoop`
- Result: template document score passes threshold even without exact substring.

## Chinese Localization Alias Policy

The template-document work should also make sure famous Chinese localization names are retrievable. This belongs in GKP data and tests, not in a hard-coded Kotlin synonym table.

Use this policy for Shining Force II and later GKP packs:

- Keep `canonical_name` stable and readable, usually `English / common zh`.
- Put well-known Chinese patch names in row `aliases` and `aliases.json`, not only in prose.
- Cite name sources with existing source ids such as `sf2.yzzl_chinese_patch` and `sf2.chinese_translation_names`; add new source rows only when the existing citations do not cover the name family.
- Prefer exact proper-name aliases with weight `1.0`: character names, unique item names, unique locations.
- Use lower weights for broad role/class words like “骑士”, “治疗”, “法师”, “前排”, because they can match multiple entities.
- Do not add generic question fragments such as “在哪”, “怎么拿”, “怎么用”, “道具”, or “角色” as standalone aliases.
- If multiple Chinese names are common, include them as aliases and mention the most useful one in `description_short` only when it helps the player recognize the entity.
- Add goldens for real player phrasing that uses the Chinese localization name, especially when the English/Japanese name is absent from the question.

First-pass Shining Force II alias buckets:

| Bucket | Examples | Expected entity style |
| --- | --- | --- |
| Early characters | `修伊`, `佳佳`, `卡森`, `吉布`, `皮特`, `玛琪露达`, `盖鲁哈特`, `鲁德` | `npc.*` |
| Promotion items | `战士的荣耀`, `天马之翼`, `气合之玉`, `奥义之书`, `银色战车` | `item.*` |
| Common consumables/materials | `医疗草`, `草药`, `治疗种子`, `回复种子`, `秘银`, `米斯里鲁银` | `item.*` |
| Key places | `格兰西尔`, `古兰西尔`, `古代之塔`, `精灵森林`, `矮人村`, `隐藏村庄` | `location.*` |

## ASR Integration Contract

Template retrieval should not become a second ASR correction system. The ASR changes create a clear handoff:

- `GkpAsrHotwordExtractor` improves recognition before decoding by biasing current-game terms.
- `GameTermNormalizer` repairs high-confidence transcript mistakes before `QueryPipelineResponseGenerator` calls the domain pipeline.
- `LocalKnowledgeRetrievalPipeline` and `TemplateDocumentMatcher` receive the post-ASR normalized question and should treat it as the query.
- Template matching may still handle natural phrasing like “玩什么 / 干嘛的 / 好玩在哪”, but it should not contain a separate pinyin or homophone table.
- ASR correction examples belong in `GameTermNormalizerTest`, `GkpAsrHotwordExtractorTest`, and hotkey voice pipeline tests.
- Template retrieval examples belong in `TemplateDocumentMatcherTest`, `LocalKnowledgeRetrievalPipelineTest`, and GKP golden tests.

Cross-layer regression cases:

| Raw voice transcript | Expected normalized query | Expected retrieval |
| --- | --- | --- |
| `修医是谁` | `修伊是谁` | `npc.chester` via name/entity evidence |
| `气和之玉怎么用` | `气合之玉怎么用` | `item.vigor-ball` via item template/evidence |
| `精灵森林是什么` | unchanged or exact normalized | `location.secret-villages` |
| `这游戏玩什么` | unchanged | `note.core-gameplay-loop` via template document retrieval |

## Scoring Contract

The matcher returns ranked candidates with explainable score parts.

```kotlin
internal data class TemplateDocumentScore(
    val document: TemplateRetrievalDocument,
    val score: Double,
    val exactPattern: Boolean,
    val patternSimilarity: Double,
    val conceptOverlap: Double,
    val aliasSimilarity: Double,
    val intentCompatible: Boolean,
)
```

Initial scoring:

- Exact normalized pattern match: `1.00`
- High-confidence concept overlap: up to `0.35`
- Max char bigram/trigram Dice similarity against `questionPatterns`: up to `0.35`
- Alias/canonical similarity: up to `0.12`
- Intent match bonus: `+0.12`
- Intent mismatch penalty: `-0.25` when both query intent and template intent are known and different
- Selected answer text similarity: up to `0.08`, only as tie breaker
- Final score is `coerceIn(0.0, 1.0)` after bonuses and penalties.

Thresholds:

- `>= 0.72`: accept as direct template document match.
- `0.45..0.72`: accept only if intent is compatible and at least one concept overlaps.
- `< 0.45`: reject and continue to existing fallback.

The exact numbers are starting constants. Keep them in one file and adjust only with tests.

## Implementation Tasks

### Task 1: Add Regression Coverage for the Missed Question

**Files:**

- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/domain/intent/QuestionIntentClassifierTest.kt`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl`
- Modify or create pipeline golden test if needed:
  - `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/domain/SampleShiningForceIIQuestionPipelineTest.kt`

- [ ] Add a classifier test for “这游戏玩什么？” expecting `game_overview`.
- [ ] Add a pipeline/golden case for “这游戏玩什么？” expecting entity `note.core-gameplay-loop` and source refs `sf2.official_overview` / `sf2.project_mechanics`.
- [ ] Do not add the exact phrase “这游戏玩什么” to Shining Force II template patterns before the matcher test. The test should prove template document retrieval, not manual pattern expansion.
- [ ] Assert the final answer remains local:
  - `answerResult.answerShort` is non-empty, or endpoint JSON `answer_short` is non-empty if the test is endpoint-level.
  - sources include `sf2.official_overview` or `sf2.project_mechanics`.
  - LLM trace/status is skipped if the test exposes it.

Acceptance:

- The new test fails before Tasks 2-4.
- Existing “这游戏怎么玩？” and “核心玩法是什么？” cases remain passing.

### Task 2: Expand GameOverview Classifier Phrases

**Files:**

- Modify: `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/domain/intent/QuestionIntentClassifier.kt`
- Modify: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/domain/intent/QuestionIntentClassifierTest.kt`

- [ ] Add overview phrases:
  - `这游戏玩什么`
  - `这个游戏玩什么`
  - `游戏玩什么`
  - `玩法是什么`
  - `主要干什么`
  - `玩点是什么`
- [ ] Keep production/name/location rules ahead of broad overview rules.
- [ ] Add negative tests:
  - “有没有恋爱系统” should remain unknown/out-of-scope unless there is evidence.
- [ ] Put entity-context questions such as “这个道具怎么玩” in pipeline tests, not classifier-only tests. The classifier does not know the current entity by itself.

Acceptance:

- “这游戏玩什么？” classifies as `GameOverview`.
- Broad game overview coverage improves without swallowing unrelated entity questions.

### Task 3: Extract Template Answer Selection And Concept Tags

**Files:**

- Create: `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/data/retrieval/TemplateAnswerSelector.kt`
- Create: `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/data/retrieval/TemplateConceptExtractor.kt`
- Create: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/data/retrieval/TemplateAnswerSelectorTest.kt`
- Create: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/data/retrieval/TemplateConceptExtractorTest.kt`
- Modify: `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/data/retrieval/LocalKnowledgeRetrievalPipeline.kt`

- [ ] Move the existing private tiered-template answer selection out of `LocalKnowledgeRetrievalPipeline` into `TemplateAnswerSelector`.
- [ ] Keep exact template matching and template document matching on the same answer-selection rules.
- [ ] Add `TemplateConceptExtractor` with only the concept tags listed in this plan.
- [ ] Use existing `normalizeNaturalQuestion()` for phrase matching; do not introduce a new `QuestionNormalizer` type.
- [ ] Do not create `TemplateRetrievalDocumentBuilder` in MVP. The matcher can build lightweight documents from rows internally.

Selector sketch:

```kotlin
internal data class SelectedTemplateAnswer(
    val text: String,
    val spoilerLevel: String,
)

internal object TemplateAnswerSelector {
    fun select(template: JsonObject, tolerance: SpoilerLevel): SelectedTemplateAnswer? {
        val tiered = when (tolerance) {
            SpoilerLevel.LIGHT -> template.stringOrNull("answer_light")?.let {
                SelectedTemplateAnswer(it, template.stringOrNull("spoiler_light") ?: "light")
            }
            SpoilerLevel.CLEAR -> template.stringOrNull("answer_clear")?.let {
                SelectedTemplateAnswer(it, template.stringOrNull("spoiler_clear") ?: "medium")
            } ?: template.stringOrNull("answer_light")?.let {
                SelectedTemplateAnswer(it, template.stringOrNull("spoiler_light") ?: "light")
            }
            SpoilerLevel.FULL -> template.stringOrNull("answer_direct")?.let {
                SelectedTemplateAnswer(it, template.stringOrNull("spoiler_direct") ?: "heavy")
            } ?: template.stringOrNull("answer_clear")?.let {
                SelectedTemplateAnswer(it, template.stringOrNull("spoiler_clear") ?: "medium")
            } ?: template.stringOrNull("answer_light")?.let {
                SelectedTemplateAnswer(it, template.stringOrNull("spoiler_light") ?: "light")
            }
        }
        return tiered?.takeIf { it.text.isNotBlank() }
            ?: template.stringOrNull("answer")?.takeIf { it.isNotBlank() }?.let {
                SelectedTemplateAnswer(it, template.stringOrNull("spoiler_level") ?: "light")
            }
    }
}
```

Acceptance:

- Existing exact template tests still pass after `LocalKnowledgeRetrievalPipeline` switches to `TemplateAnswerSelector`.
- `TemplateAnswerSelectorTest` proves `LIGHT`, `CLEAR`, and `FULL` choose the same tiered answers as the old private logic.
- `TemplateConceptExtractorTest` proves “这游戏玩什么？” maps to `GameplayLoop`, “好玩在哪？” maps to `FunFactor`, and “有没有恋爱系统？” maps to no helpful template concept.

### Task 4: Create TemplateDocumentMatcher

**Files:**

- Create: `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/data/retrieval/TemplateDocumentMatcher.kt`
- Create: `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/data/retrieval/TemplateDocumentMatcherTest.kt`

- [ ] Normalize query and template fields with the same normalizer used by existing pipeline.
- [ ] Extract query concept tags.
- [ ] Implement char bigram/trigram Dice similarity.
- [ ] Implement intent compatibility:
  - exact same intent: bonus
  - query unknown and template known: no penalty
  - query known and template unknown: no penalty
  - both known and different: penalty
- [ ] Rank documents by total score, then by exact pattern, then by source order.
- [ ] Return no match below threshold.
- [ ] Include score breakdown in tests for debuggability.
- [ ] Build lightweight `TemplateRetrievalDocument` objects inside this matcher from `KnowledgeChunkDomain` rows and valid `answerTemplates`.
- [ ] Use `KnowledgeChunkDomain.entityType` when carrying the row type into the template document.
- [ ] Use template `template_id`, not `id`, when building stable document ids.
- [ ] Assign a stable `sourceOrder` from row/template iteration order so equal scores sort deterministically.

Matcher sketch:

```kotlin
internal class TemplateDocumentMatcher {
    fun bestMatch(
        query: String,
        queryIntent: AnswerType,
        rows: List<KnowledgeChunkDomain>,
        tolerance: SpoilerLevel,
    ): TemplateDocumentScore? {
        val queryConcepts = TemplateConceptExtractor.extract(query)
        val documents = buildDocuments(rows, tolerance)
        return documents
            .map { score(query, queryIntent, queryConcepts, it) }
            .filter { it.passesThreshold() }
            .maxWithOrNull(
                compareBy<TemplateDocumentScore> { it.score }
                    .thenBy { it.exactPattern }
                    .thenByDescending { -it.document.sourceOrder }
            )
    }
}
```

Required tests:

- “这游戏玩什么？” matches the core gameplay document built from patterns like “这游戏怎么玩 / 主要玩什么 / 核心玩法”.
- “这个游戏主要是干嘛的？” matches the same document.
- “好玩在哪？” matches the same document through `FunFactor`.
- “有没有恋爱系统？” does not match core gameplay just because it contains “游戏”.
- If query intent is `production`, a `game_overview` template is penalized below threshold.
- A fixture whose patterns do not include “这游戏玩什么” still matches that query with `exactPattern = false`.
- A row with `entityType = "note"` carries that value into the document.
- A template with `template_id = "template.sf2.core-gameplay.zh"` produces a document id ending in `#template.sf2.core-gameplay.zh`.

Acceptance:

- The matcher can explain why it matched, using `conceptOverlap` and `patternSimilarity`.
- No LLM or network is involved.

### Task 5: Wire Template Documents into LocalKnowledgeRetrievalPipeline

**Files:**

- Modify: `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/data/retrieval/LocalKnowledgeRetrievalPipeline.kt`
- Modify related tests:
  - `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/data/retrieval/LocalKnowledgeRetrievalPipelineTest.kt`
  - `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/domain/SampleShiningForceIIQuestionPipelineTest.kt`

- [ ] Keep existing exact template matching as the first template route.
- [ ] Add `templateDocumentMatches(...)` immediately after exact template matching and before alias/entity fallback.
- [ ] Build documents only for rows already loaded for the current `gameId`.
- [ ] Convert a document match into the same `RetrievalResult` shape as exact templates.
- [ ] Preserve `sourceRefs`, `spoilerLevel`, selected answer, confidence, and answer type.
- [ ] Apply the same selected-template spoiler-level checks as exact template matching.
- [ ] Respect row `progressGate` for medium/heavy selected template answers; allow `none`/`light` selected answers to provide safe low-spoiler guidance even when the row's full topic is gated later.
- [ ] Ensure a document match does not call LLM composer.

Pipeline sketch:

```kotlin
val candidates =
    nameMappingMatches(rows, normalizedQuery) +
    templateMatches(rows, normalizedQuery, query) +
    templateDocumentMatches(rows, normalizedQuery, query, queryIntent) +
    aliasAndEntityMatches(rows, normalizedQuery) +
    ftsMatches(gameId, normalizedQuery, query, queryIntent)
```

Implementation note:

- If current pipeline uses ordered fallback instead of additive candidates, preserve that style and insert the template document route in the equivalent position.
- Confidence for accepted template document matches should be slightly below exact template matches and above generic alias fallback.
- Entity-context questions such as “这个道具怎么玩” should be covered at pipeline level, because entity resolution and current context do not belong to classifier-only tests.

Acceptance:

- “这游戏玩什么？” returns the core gameplay template answer.
- “这游戏怎么玩？” still uses exact template path or returns an equal/better score.
- Existing alias/entity and FTS tests still pass.
- Existing spoiler-tier item tests still pass because both exact and document template routes call `TemplateAnswerSelector`, and low-spoiler selected answers are not blocked just because the row also has later-game detail.

### Task 6: Add Source-Backed Chinese Localization Alias Coverage

**Files:**

- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/aliases.json`
- Modify as needed:
  - `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/entities.jsonl`
  - `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/items.jsonl`
  - `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/knowledge/locations.jsonl`
  - `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/sources/citations.jsonl`
- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl`
- Modify related tests:
  - `retrosprite-android/app/src/test/kotlin/com/retrosprite/app/domain/SampleShiningForceIIQuestionPipelineTest.kt`

- [ ] Audit existing source-backed Chinese names before adding new aliases. Current relevant sources include `sf2.yzzl_chinese_patch` and `sf2.chinese_translation_names`.
- [ ] Add missing high-frequency proper-name aliases to both the relevant knowledge row and `aliases.json`.
- [ ] Keep broad class/role aliases lower weight than proper names.
- [ ] Do not add standalone generic terms such as “在哪”, “怎么拿”, “怎么用”, “角色”, or “道具” to `aliases.json`.
- [ ] Keep answer text original and short; do not copy patch script, guide prose, or long name tables.
- [ ] Add golden questions that use Chinese localization names without the English name in the query.
- [ ] Verify important Chinese proper-name aliases are also eligible ASR hotword candidates through `GkpAsrHotwordExtractor`.
- [ ] Verify ASR-prone homophones are tested in `GameTermNormalizerTest` instead of being added as fake GKP aliases.

Example alias rows:

```json
{"term": "修伊", "entity_id": "npc.chester", "weight": 1.0}
{"term": "气合之玉", "entity_id": "item.vigor-ball", "weight": 1.0}
{"term": "精灵森林", "entity_id": "location.secret-villages", "weight": 1.0}
```

Example golden rows:

```jsonl
{"qa_id":"qa.sf2.localized.chester.xiuyi.zh","language":"zh","question":"修伊怎么用？","game_id":"shining_force_ii_md","spoiler_level":"light","progress_gate":"start","expected_entity_ids":["npc.chester"],"expected_answer_contains":["骑士","前排"],"source_refs":["sf2.manual_translation","sf2.chinese_translation_names"]}
{"qa_id":"qa.sf2.localized.vigor-ball.qihe.zh","language":"zh","question":"气合之玉给谁用？","game_id":"shining_force_ii_md","spoiler_level":"light","progress_gate":"start","expected_entity_ids":["item.vigor-ball"],"expected_answer_contains":["Priest","Master Monk"],"source_refs":["sf2.promotion","sf2.chinese_translation_names"]}
{"qa_id":"qa.sf2.localized.elven-town.forest.zh","language":"zh","question":"精灵森林在哪？","game_id":"shining_force_ii_md","spoiler_level":"light","progress_gate":"new_granseal","expected_entity_ids":["location.secret-villages"],"expected_answer_contains":["秘密村庄"],"source_refs":["sf2.secrets","sf2.chinese_translation_names"]}
```

Acceptance:

- At least 20 source-backed Chinese localization aliases are present across characters, items, and locations.
- At least 6 goldens use Chinese localization names and pass without English names in the query.
- Name mapping resolves proper names before template-document retrieval.
- `GkpAsrHotwordExtractorTest` confirms proper Chinese aliases such as `修伊`, `气合之玉`, `精灵森林`, and `米斯里鲁银` are exported as high-value hotwords.
- `GameTermNormalizerTest` covers observed ASR substitutions such as `修医` -> `修伊` and `气和之玉` -> `气合之玉`.
- Broad aliases do not introduce false positives for unrelated entity questions.

### Task 7: Add Shining Force II Golden Case

**Files:**

- Modify: `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl`
- Modify any fixture lint/golden test that enumerates this file.

- [ ] Add a golden row for “这游戏玩什么？”.
- [ ] Expected entity should be `note.core-gameplay-loop`.
- [ ] Source refs should include `sf2.official_overview` or `sf2.project_mechanics`.
- [ ] Expected answer should mention the actual gameplay loop, for example team movement, grid/tactical battles, story progression, or role growth.
- [ ] Do not make this golden pass by adding an exact template pattern first. Let template document retrieval do the work.
- [ ] Route proof belongs in `TemplateDocumentMatcherTest`: use a fixture without exact “这游戏玩什么” pattern and assert `exactPattern = false`. The golden only proves the end-to-end answer.

Example expected golden shape, adapted to the existing file schema:

```jsonl
{"qa_id":"qa.sf2.core-gameplay.play-what.zh","language":"zh","question":"这游戏玩什么？","game_id":"shining_force_ii_md","spoiler_level":"none","progress_gate":"start","expected_entity_ids":["note.core-gameplay-loop"],"expected_answer_contains":["队伍","战斗"],"source_refs":["sf2.official_overview","sf2.project_mechanics"]}
```

Acceptance:

- The golden passes locally through template document retrieval.
- Existing goldens remain unchanged except for this added case.

### Task 8: Keep Template Retrieval Diagnostics Out Of The MVP

**Files:**

- No source changes for template retrieval diagnostics in this MVP.
- Future files only if a later task adds retrieval trace support:
  - `retrosprite-android/app/src/main/kotlin/com/retrosprite/app/domain/models/AnswerResult.kt`
  - endpoint/diagnostics mapper tests

- [ ] Do not add template-retrieval request-log columns, endpoint fields, UI labels, or diagnostics cards for this MVP.
- [ ] Keep existing ASR raw/normalized question diagnostics as ASR observability; do not use those fields as a template retrieval trace.
- [ ] Rely on `TemplateDocumentMatcherTest` score breakdown for developer visibility.
- [ ] If follow-up work adds retrieval traces, include:
  - `retrieval_route = template_document`
  - `template_document_id`
  - `template_score`
  - `template_score_reasons`
- [ ] Do not add visible in-app UI just for this feature.

Acceptance:

- Developers can tell why a template document matched from unit test score breakdowns.
- Developers can tell whether ASR normalization happened from existing `rawQuestion`, `normalizedQuestion`, `questionNormalizationReason`, and matched-term diagnostics.
- Player-facing HUD/TTS remains unchanged.

### Task 9: Verification Pass

Run targeted tests first:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest \
  --tests com.retrosprite.app.domain.intent.QuestionIntentClassifierTest \
  --tests com.retrosprite.app.data.retrieval.TemplateAnswerSelectorTest \
  --tests com.retrosprite.app.data.retrieval.TemplateConceptExtractorTest \
  --tests com.retrosprite.app.data.retrieval.TemplateDocumentMatcherTest \
  --tests com.retrosprite.app.data.retrieval.LocalKnowledgeRetrievalPipelineTest \
  --tests com.retrosprite.app.domain.normalization.GameTermNormalizerTest \
  --tests com.retrosprite.app.voice.asr.GkpAsrHotwordExtractorTest \
  --tests com.retrosprite.app.domain.SampleShiningForceIIQuestionPipelineTest
```

Then run the broader unit suite:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest
```

Manual QA questions:

- “这游戏怎么玩？”
- “这游戏玩什么？”
- “这个游戏主要是干嘛的？”
- “核心玩法是什么？”
- “好玩在哪？”
- “修伊怎么用？”
- “修医是谁？” as hotkey voice transcript
- “气合之玉给谁用？”
- “气和之玉怎么用？” as hotkey voice transcript
- “精灵森林在哪？”
- “有没有恋爱系统？”
- “这个道具怎么玩？” with a concrete known item/entity context

Acceptance:

- Overview variants answer from GKP core gameplay template.
- Hotkey voice ASR normalizer rewrites only high-confidence current-game terms and exposes raw/normalized diagnostics.
- Chinese localization names for high-frequency characters, items, and locations resolve through name mapping or entity aliases.
- Unsupported/out-of-scope questions do not receive fabricated answers.
- No runtime network call is required.
- LLM trace/status is skipped for supported template document answers if that trace is exposed by the tested layer.

## Future Phase: FTS or Embedding Index

Only consider this after the MVP has stable tests.

Option A: Extend existing SQLite FTS.

- Add a generated/searchable template text field to `knowledge_fts`, or add a dedicated `template_documents_fts`.
- Migration risk is moderate because Room schema and asset import code must change.
- Best if GKP grows to hundreds or thousands of templates per game.

Option B: Local embedding model.

- Add a tiny multilingual sentence embedding model only if Android package size, latency, battery, and offline constraints are acceptable.
- Use template document IDs as vector payload metadata.
- Keep evidence and answer selection deterministic; embeddings only retrieve candidates.

Option C: Authoring-time expansion.

- Generate template concept tags and paraphrase hints during GKP build/lint.
- Runtime stays simple and deterministic.
- This is attractive for shipped GKP packages because it keeps Android logic small.

## Risks and Guardrails

- Overmatching risk: Broad phrases like “游戏” must not match every overview document. Require concept overlap or intent compatibility.
- Alias overmatching risk: Proper Chinese names are good aliases; generic words like “道具”, “角色”, “在哪”, “怎么拿”, and broad roles must not become high-confidence entity aliases.
- ASR overcorrection risk: `GameTermNormalizer` must stay current-game scoped, high-confidence, and ambiguity-aware; otherwise a noisy transcript could be rewritten into an unrelated GKP term.
- Hotword bloat risk: `GkpAsrHotwordExtractor` should prioritize proper names and bounded template terms, not full question strings or generic role words.
- Intent gate risk: Classifier errors should not fully block retrieval unless intent mismatch is explicit and high confidence.
- Spoiler risk: Template document matching must preserve existing spoiler-level answer selection.
- Content drift risk: If GKP authors add broad aliases, tests should catch unexpected matches.
- Maintainability risk: Keep all thresholds and phrase maps in one file with tests.

## Done Definition

- “这游戏玩什么？” retrieves entity `note.core-gameplay-loop` and answers with source ids such as `sf2.official_overview` / `sf2.project_mechanics`.
- High-frequency Chinese localization names for characters, items, and locations resolve to the intended GKP entities with source-backed aliases.
- Observed voice transcripts such as `修医是谁` and `气和之玉怎么用` are handled by ASR normalization before retrieval, with diagnostics preserving raw and normalized questions.
- The implementation adds template document retrieval without changing the GKP schema.
- The route is local-only and deterministic.
- Unsupported questions still refuse or clarify instead of guessing.
- Targeted and broad unit tests pass.
