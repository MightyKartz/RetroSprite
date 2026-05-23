# ASR Game Term Normalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a deterministic, current-game-aware ASR post-processing layer so misrecognized homophones like `修医是谁` can be normalized to GKP terms like `修伊是谁` before retrieval.

**Architecture:** Keep sherpa-onnx hotword biasing as the first layer, then add a second layer in the Q&A request path: `raw voice transcript -> GameTermNormalizer -> normalized question -> existing retrieval pipeline`. The normalizer builds a per-game term index from installed GKP knowledge rows and only rewrites a question when one high-confidence, unambiguous term match exists.

**Tech Stack:** Kotlin, coroutines, Room-backed GKP repositories, existing `KnowledgeRepository`, `GameResolver`, `QueryPipelineResponseGenerator`, JVM unit tests, Android instrumentation only for DB migration if needed.

---

## Scope

### In Scope

- Normalize ASR transcripts using current-game GKP terms after speech recognition and before retrieval.
- Preserve both raw and normalized questions in diagnostics/logs.
- Support Chinese homophone correction for built-in GKP names such as `修伊`, `吉布`, `气合之玉`, `米斯里鲁银`.
- Support conservative fuzzy correction for common ASR one-character substitutions.
- Add tests proving `修医是谁` becomes `修伊是谁` and then reaches evidence instead of `no_evidence`.

### Out Of Scope

- No sherpa-onnx model replacement.
- No fallback answer path.
- No OCR/PaddleOCR integration.
- No cloud LLM correction.
- No broad global dictionary that is not scoped to the current game.

## File Structure

- Create `app/src/main/kotlin/com/retrosprite/app/domain/normalization/GameTermNormalizer.kt`
  - Owns term index construction, candidate scoring, conflict handling, and question rewriting.
- Create `app/src/test/kotlin/com/retrosprite/app/domain/normalization/GameTermNormalizerTest.kt`
  - Unit tests for homophone rewrite, ambiguity guardrails, and no-op behavior.
- Modify `app/src/main/kotlin/com/retrosprite/app/endpoint/model/ResponseDiagnostics.kt`
  - Add raw/normalized question diagnostics fields so `/debug/latest-request` can expose both.
- Modify `app/src/main/kotlin/com/retrosprite/app/endpoint/QueryPipelineResponseGenerator.kt`
  - Run normalization only for `hotkey_voice` questions after game resolution and before calling `QueryPipeline`.
- Modify `app/src/main/kotlin/com/retrosprite/app/endpoint/RequestLogger.kt`
  - Persist normalized question fields in `RequestLogEntry`.
- Modify `app/src/main/kotlin/com/retrosprite/app/data/db/entity/RequestLogEntity.kt`
  - Add nullable columns for `raw_question`, `normalized_question`, and `question_normalization_reason`.
- Modify `app/src/main/kotlin/com/retrosprite/app/data/models/DomainModels.kt`
  - Mirror the new request-log fields in `RequestLogDomain`.
- Modify `app/src/main/kotlin/com/retrosprite/app/data/models/Mappers.kt`
  - Map the new request-log fields both directions.
- Modify `app/src/main/kotlin/com/retrosprite/app/endpoint/RoomBackedRequestLogSink.kt`
  - Map endpoint log entries to the new domain fields.
- Modify `app/src/main/kotlin/com/retrosprite/app/data/db/RetroSpriteDatabase.kt`
  - Bump schema from v7 to v8 and add migration for the new nullable columns.
- Modify `app/src/main/kotlin/com/retrosprite/app/ui/integration/UiModelMappers.kt`
  - Include raw/normalized question fields in the full JSON diagnostics.
- Modify `app/src/test/kotlin/com/retrosprite/app/endpoint/QueryPipelineResponseGeneratorTest.kt`
  - Verify voice questions are normalized before entering the pipeline.
- Modify `app/src/test/kotlin/com/retrosprite/app/endpoint/RequestLoggerTest.kt`
  - Verify raw and normalized questions are retained.
- Modify `app/src/test/kotlin/com/retrosprite/app/data/db/RetroSpriteDatabaseMigrationTest.kt`
  - Verify v7 -> v8 migration adds the new request-log columns.
- Modify `docs/qa-feedback/asr-hotword-voice-evaluation.md`
  - Add a new acceptance row for `修伊是谁` spoken as `修医是谁`.

## Data Model

Create these types in `GameTermNormalizer.kt`:

```kotlin
package com.retrosprite.app.domain.normalization

import com.retrosprite.app.data.models.KnowledgeChunkDomain

data class GameTermNormalizationResult(
    val rawQuestion: String,
    val normalizedQuestion: String,
    val applied: Boolean,
    val reason: String? = null,
    val matchedTerm: String? = null,
    val matchedEntityId: String? = null,
    val candidates: List<GameTermNormalizationCandidate> = emptyList(),
)

data class GameTermNormalizationCandidate(
    val rawSpan: String,
    val term: String,
    val entityId: String,
    val score: Double,
    val reason: String,
)
```

Implementation constants:

```kotlin
private const val EXACT_SCORE = 1.00
private const val HOMOPHONE_SCORE = 0.94
private const val EDIT_DISTANCE_SCORE = 0.88
private const val MIN_AUTO_APPLY_SCORE = 0.90
private const val MIN_SCORE_GAP = 0.08
private const val MAX_TERM_CHARS = 8
```

## Matching Rules

1. Build candidate terms from `canonicalName`, `aliases`, and `entityId.substringAfterLast('.')`.
2. Keep CJK terms of length 2 to 8 characters. Ignore broad question words like `是谁`, `怎么用`, `在哪里`.
3. Exact containment wins and does not rewrite text.
4. Homophone match can rewrite when:
   - raw span and term have same CJK length;
   - their pinyin signatures are equal;
   - only one candidate is above threshold or top candidate beats second by `MIN_SCORE_GAP`.
5. Edit-distance match can rewrite when:
   - raw span and term have same CJK length;
   - distance is exactly 1;
   - term length is at least 3;
   - there is no competing candidate above threshold.
6. If ambiguous, do not rewrite. Return `applied=false` and include candidates for diagnostics.

## Pinyin Strategy

Use a tiny built-in character map first because current MVP only needs a bounded set of game terms and common ASR substitutions. This keeps the feature local and avoids a dependency decision before we have evidence.

Initial map:

```kotlin
private val CJK_PINYIN = mapOf(
    '修' to "xiu",
    '伊' to "yi",
    '医' to "yi",
    '一' to "yi",
    '吉' to "ji",
    '布' to "bu",
    '步' to "bu",
    '皮' to "pi",
    '特' to "te",
    '气' to "qi",
    '合' to "he",
    '和' to "he",
    '之' to "zhi",
    '玉' to "yu",
    '精' to "jing",
    '灵' to "ling",
    '森' to "sen",
    '林' to "lin",
    '米' to "mi",
    '斯' to "si",
    '里' to "li",
    '鲁' to "lu",
    '路' to "lu",
    '银' to "yin",
)
```

If later tests show this map is too small, make a separate plan to evaluate a local pinyin library. Do not add a dependency in this first implementation.

---

### Task 1: Add GameTermNormalizer Core

**Files:**
- Create: `app/src/main/kotlin/com/retrosprite/app/domain/normalization/GameTermNormalizer.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/domain/normalization/GameTermNormalizerTest.kt`

- [ ] **Step 1: Write failing tests for homophone normalization**

Create `GameTermNormalizerTest.kt`:

```kotlin
package com.retrosprite.app.domain.normalization

import com.retrosprite.app.data.models.KnowledgeChunkDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTermNormalizerTest {

    private val normalizer = GameTermNormalizer()

    @Test
    fun `normalizes homophone span to current game alias`() {
        val result = normalizer.normalize(
            rawQuestion = "修医是谁",
            rows = listOf(row(entityId = "npc.jaha", canonicalName = "Jaha / 吉布", aliases = listOf("修伊")))
        )

        assertTrue(result.applied)
        assertEquals("修伊是谁", result.normalizedQuestion)
        assertEquals("修医", result.candidates.single().rawSpan)
        assertEquals("修伊", result.matchedTerm)
        assertEquals("npc.jaha", result.matchedEntityId)
        assertEquals("homophone", result.reason)
    }

    @Test
    fun `normalizes longer item homophone`() {
        val result = normalizer.normalize(
            rawQuestion = "气和之玉怎么用",
            rows = listOf(row(entityId = "item.vigor_ball", canonicalName = "Vigor Ball / 气合之玉", aliases = listOf("气合之玉")))
        )

        assertTrue(result.applied)
        assertEquals("气合之玉怎么用", result.normalizedQuestion)
        assertEquals("气合之玉", result.matchedTerm)
    }

    @Test
    fun `does not rewrite when candidate is ambiguous`() {
        val result = normalizer.normalize(
            rawQuestion = "修医是谁",
            rows = listOf(
                row(entityId = "npc.jaha", canonicalName = "Jaha / 吉布", aliases = listOf("修伊")),
                row(entityId = "npc.fake", canonicalName = "Fake / 修一", aliases = listOf("修一"))
            )
        )

        assertFalse(result.applied)
        assertEquals("修医是谁", result.normalizedQuestion)
        assertTrue(result.candidates.size >= 2)
    }

    @Test
    fun `keeps exact term unchanged but reports no rewrite`() {
        val result = normalizer.normalize(
            rawQuestion = "修伊是谁",
            rows = listOf(row(entityId = "npc.jaha", canonicalName = "Jaha / 吉布", aliases = listOf("修伊")))
        )

        assertFalse(result.applied)
        assertEquals("修伊是谁", result.normalizedQuestion)
        assertEquals(null, result.reason)
    }

    @Test
    fun `leaves unrelated question unchanged`() {
        val result = normalizer.normalize(
            rawQuestion = "这游戏怎么玩",
            rows = listOf(row(entityId = "npc.jaha", canonicalName = "Jaha / 吉布", aliases = listOf("修伊")))
        )

        assertFalse(result.applied)
        assertEquals("这游戏怎么玩", result.normalizedQuestion)
        assertTrue(result.candidates.isEmpty())
    }

    private fun row(
        entityId: String,
        canonicalName: String,
        aliases: List<String>,
    ): KnowledgeChunkDomain = KnowledgeChunkDomain(
        id = 0L,
        gameId = "shining_force_ii_md",
        entityId = entityId,
        entityType = "npc",
        canonicalName = canonicalName,
        aliases = aliases,
        descriptionShort = "desc",
        descriptionLong = null,
        progressGate = "start",
        spoilerLevel = "light",
        sourceRefs = listOf("test.source"),
        confidence = "verified",
        answerTemplates = emptyList(),
    )
}
```

- [ ] **Step 2: Run the failing tests**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
./gradlew :app:testDebugUnitTest --tests 'com.retrosprite.app.domain.normalization.GameTermNormalizerTest'
```

Expected: fails because `GameTermNormalizer` does not exist.

- [ ] **Step 3: Implement GameTermNormalizer**

Create `GameTermNormalizer.kt`:

```kotlin
package com.retrosprite.app.domain.normalization

import com.retrosprite.app.data.models.KnowledgeChunkDomain
import kotlin.math.min

data class GameTermNormalizationResult(
    val rawQuestion: String,
    val normalizedQuestion: String,
    val applied: Boolean,
    val reason: String? = null,
    val matchedTerm: String? = null,
    val matchedEntityId: String? = null,
    val candidates: List<GameTermNormalizationCandidate> = emptyList(),
)

data class GameTermNormalizationCandidate(
    val rawSpan: String,
    val term: String,
    val entityId: String,
    val score: Double,
    val reason: String,
)

class GameTermNormalizer {

    fun normalize(
        rawQuestion: String,
        rows: List<KnowledgeChunkDomain>,
    ): GameTermNormalizationResult {
        val cleanQuestion = rawQuestion.trim()
        if (cleanQuestion.isBlank()) {
            return GameTermNormalizationResult(
                rawQuestion = rawQuestion,
                normalizedQuestion = cleanQuestion,
                applied = false,
            )
        }

        val terms = rows.flatMap { it.toTerms() }
            .distinctBy { it.term }
            .filter { it.term.length in MIN_TERM_CHARS..MAX_TERM_CHARS }
            .filterNot { it.term in STOP_TERMS }

        if (terms.any { cleanQuestion.contains(it.term) }) {
            return GameTermNormalizationResult(
                rawQuestion = rawQuestion,
                normalizedQuestion = cleanQuestion,
                applied = false,
            )
        }

        val candidates = terms.flatMap { term ->
            cleanQuestion.cjkWindows(term.term.length).mapNotNull { span ->
                score(span, term)?.let { scored ->
                    GameTermNormalizationCandidate(
                        rawSpan = span,
                        term = term.term,
                        entityId = term.entityId,
                        score = scored.score,
                        reason = scored.reason,
                    )
                }
            }
        }.sortedWith(compareByDescending<GameTermNormalizationCandidate> { it.score }.thenBy { it.term })

        val top = candidates.firstOrNull()
        if (top == null || top.score < MIN_AUTO_APPLY_SCORE) {
            return GameTermNormalizationResult(
                rawQuestion = rawQuestion,
                normalizedQuestion = cleanQuestion,
                applied = false,
                candidates = candidates.take(MAX_DIAGNOSTIC_CANDIDATES),
            )
        }

        val second = candidates.drop(1).firstOrNull()
        if (second != null && top.score - second.score < MIN_SCORE_GAP) {
            return GameTermNormalizationResult(
                rawQuestion = rawQuestion,
                normalizedQuestion = cleanQuestion,
                applied = false,
                candidates = candidates.take(MAX_DIAGNOSTIC_CANDIDATES),
            )
        }

        val normalized = cleanQuestion.replaceFirst(top.rawSpan, top.term)
        return GameTermNormalizationResult(
            rawQuestion = rawQuestion,
            normalizedQuestion = normalized,
            applied = normalized != cleanQuestion,
            reason = top.reason,
            matchedTerm = top.term,
            matchedEntityId = top.entityId,
            candidates = listOf(top),
        )
    }

    private fun score(rawSpan: String, term: Term): ScoredMatch? {
        if (rawSpan == term.term) {
            return ScoredMatch(EXACT_SCORE, "exact")
        }
        if (rawSpan.length == term.term.length &&
            rawSpan.pinyinSignature() == term.term.pinyinSignature()
        ) {
            return ScoredMatch(HOMOPHONE_SCORE, "homophone")
        }
        if (term.term.length >= MIN_EDIT_DISTANCE_TERM_CHARS &&
            rawSpan.length == term.term.length &&
            editDistance(rawSpan, term.term) == 1
        ) {
            return ScoredMatch(EDIT_DISTANCE_SCORE, "edit_distance")
        }
        return null
    }

    private fun KnowledgeChunkDomain.toTerms(): List<Term> =
        buildList {
            canonicalName.extractCjkTerms().forEach { add(Term(it, entityId)) }
            aliases.flatMap { it.extractCjkTerms() }.forEach { add(Term(it, entityId)) }
            entityId.substringAfterLast('.').extractCjkTerms().forEach { add(Term(it, entityId)) }
        }

    private fun String.extractCjkTerms(): List<String> =
        split('/', ',', '，', '(', ')', '（', '）', ' ', '\t')
            .map { it.trim() }
            .filter { it.isNotBlank() && it.all(Char::isCjk) }

    private fun String.cjkWindows(size: Int): List<String> {
        if (size <= 0 || length < size) return emptyList()
        return indices
            .filter { start -> start + size <= length }
            .map { start -> substring(start, start + size) }
            .filter { it.all(Char::isCjk) }
            .distinct()
    }

    private fun String.pinyinSignature(): String? {
        val parts = map { CJK_PINYIN[it] ?: return null }
        return parts.joinToString(separator = " ")
    }

    private fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val previous = IntArray(b.length + 1) { it }
        val current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(
                    min(current[j - 1] + 1, previous[j] + 1),
                    previous[j - 1] + cost
                )
            }
            for (j in current.indices) previous[j] = current[j]
        }
        return previous[b.length]
    }

    private data class Term(val term: String, val entityId: String)
    private data class ScoredMatch(val score: Double, val reason: String)

    private companion object {
        const val EXACT_SCORE = 1.00
        const val HOMOPHONE_SCORE = 0.94
        const val EDIT_DISTANCE_SCORE = 0.88
        const val MIN_AUTO_APPLY_SCORE = 0.90
        const val MIN_SCORE_GAP = 0.08
        const val MIN_TERM_CHARS = 2
        const val MAX_TERM_CHARS = 8
        const val MIN_EDIT_DISTANCE_TERM_CHARS = 3
        const val MAX_DIAGNOSTIC_CANDIDATES = 5

        val STOP_TERMS = setOf("是谁", "在哪", "在哪里", "怎么用", "有什么用", "怎么办", "怎么打", "怎么练")

        val CJK_PINYIN = mapOf(
            '修' to "xiu",
            '伊' to "yi",
            '医' to "yi",
            '一' to "yi",
            '吉' to "ji",
            '布' to "bu",
            '步' to "bu",
            '皮' to "pi",
            '特' to "te",
            '气' to "qi",
            '合' to "he",
            '和' to "he",
            '之' to "zhi",
            '玉' to "yu",
            '精' to "jing",
            '灵' to "ling",
            '森' to "sen",
            '林' to "lin",
            '米' to "mi",
            '斯' to "si",
            '里' to "li",
            '鲁' to "lu",
            '路' to "lu",
            '银' to "yin",
        )
    }
}

private fun Char.isCjk(): Boolean = code in 0x4E00..0x9FFF
```

- [ ] **Step 4: Run tests**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
./gradlew :app:testDebugUnitTest --tests 'com.retrosprite.app.domain.normalization.GameTermNormalizerTest'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/retrosprite/app/domain/normalization/GameTermNormalizer.kt \
  app/src/test/kotlin/com/retrosprite/app/domain/normalization/GameTermNormalizerTest.kt
git commit -m "feat: add game term ASR normalizer"
```

---

### Task 2: Wire Normalization Into Voice Q&A

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/endpoint/QueryPipelineResponseGenerator.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ServiceLocator.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/endpoint/QueryPipelineResponseGeneratorTest.kt`

- [ ] **Step 1: Write failing endpoint integration test**

Add a test case that constructs `QueryPipelineResponseGenerator` with a fake `GameResolver`, fake `KnowledgeRepository`, and capturing `QueryPipeline`. The test should call:

```kotlin
val response = generator.generate(
    request = RetroArchRequest(
        image = "",
        label = "mega_drive__光明力量2",
        question = "修医是谁",
        state = RetroArchState(paused = 1),
    ),
    outputMode = "hotkey_voice:text",
)
```

Assert:

```kotlin
assertEquals("修伊是谁", pipeline.lastQuestion)
assertEquals("修医是谁", response.diagnostics.rawQuestion)
assertEquals("修伊是谁", response.diagnostics.normalizedQuestion)
assertEquals("homophone", response.diagnostics.questionNormalizationReason)
assertEquals("修伊", response.diagnostics.normalizedQuestionMatchedTerm)
```

- [ ] **Step 2: Run the failing test**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
./gradlew :app:testDebugUnitTest --tests 'com.retrosprite.app.endpoint.QueryPipelineResponseGeneratorTest'
```

Expected: FAIL because the generator does not normalize questions yet.

- [ ] **Step 3: Extend QueryPipelineResponseGenerator constructor**

Modify `QueryPipelineResponseGenerator` constructor:

```kotlin
class QueryPipelineResponseGenerator(
    private val pipeline: QueryPipeline,
    private val defaultSpoilerLevel: SpoilerLevel = SpoilerLevel.LIGHT,
    private val spoilerLevelProvider: () -> SpoilerLevel = { defaultSpoilerLevel },
    private val defaultLanguage: String = "zh",
    private val gameResolver: GameResolver? = null,
    private val knowledgeRepository: KnowledgeRepository? = null,
    private val gameTermNormalizer: GameTermNormalizer = GameTermNormalizer(),
) : ResponseGenerator
```

Add imports:

```kotlin
import com.retrosprite.app.data.repository.KnowledgeRepository
import com.retrosprite.app.domain.normalization.GameTermNormalizer
import com.retrosprite.app.domain.normalization.GameTermNormalizationResult
import com.retrosprite.app.domain.resolver.GameResolver
```

- [ ] **Step 4: Add normalization helper**

Inside `QueryPipelineResponseGenerator`:

```kotlin
private suspend fun normalizeQuestionIfVoice(
    request: RetroArchRequest,
    outputMode: String,
): GameTermNormalizationResult {
    val rawQuestion = request.question.trim()
    if (rawQuestion.isBlank()) {
        return GameTermNormalizationResult(rawQuestion, rawQuestion, applied = false)
    }
    if (!outputMode.startsWith("hotkey_voice")) {
        return GameTermNormalizationResult(rawQuestion, rawQuestion, applied = false)
    }
    val resolver = gameResolver ?: return GameTermNormalizationResult(rawQuestion, rawQuestion, applied = false)
    val repository = knowledgeRepository ?: return GameTermNormalizationResult(rawQuestion, rawQuestion, applied = false)
    val identity = resolver.resolve(label = request.label, romHash = null)
    val gameId = identity.gameId ?: return GameTermNormalizationResult(rawQuestion, rawQuestion, applied = false)
    val rows = repository.listByGame(gameId)
    return gameTermNormalizer.normalize(rawQuestion = rawQuestion, rows = rows)
}
```

- [ ] **Step 5: Use normalized question for pipeline input**

Replace the direct `request.question.takeIf { it.isNotBlank() }` call with:

```kotlin
val normalization = normalizeQuestionIfVoice(request, outputMode)
val pipelineQuestion = normalization.normalizedQuestion.takeIf { it.isNotBlank() }
```

Then pass:

```kotlin
question = pipelineQuestion,
```

- [ ] **Step 6: Add diagnostics fields to generated response**

When creating `ResponseDiagnostics`, add:

```kotlin
rawQuestion = normalization.rawQuestion.takeIf { normalization.applied },
normalizedQuestion = normalization.normalizedQuestion.takeIf { normalization.applied },
questionNormalizationReason = normalization.reason,
normalizedQuestionMatchedTerm = normalization.matchedTerm,
normalizedQuestionMatchedEntityId = normalization.matchedEntityId,
```

- [ ] **Step 7: Wire dependencies in ServiceLocator**

Find the `QueryPipelineResponseGenerator` construction in `ServiceLocator.kt`. Pass the existing resolver and knowledge repository:

```kotlin
QueryPipelineResponseGenerator(
    pipeline = queryPipeline,
    spoilerLevelProvider = { settingsRepository.currentSpoilerLevel() },
    gameResolver = gameResolver,
    knowledgeRepository = knowledgeRepository,
)
```

If the current constructor call uses different local names, preserve those names and only add the new arguments.

- [ ] **Step 8: Run endpoint tests**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
./gradlew :app:testDebugUnitTest --tests 'com.retrosprite.app.endpoint.QueryPipelineResponseGeneratorTest'
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/com/retrosprite/app/endpoint/QueryPipelineResponseGenerator.kt \
  app/src/main/kotlin/com/retrosprite/app/ServiceLocator.kt \
  app/src/test/kotlin/com/retrosprite/app/endpoint/QueryPipelineResponseGeneratorTest.kt
git commit -m "feat: normalize voice questions before retrieval"
```

---

### Task 3: Preserve Raw And Normalized Questions In Diagnostics

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/endpoint/model/RetroArchResponse.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/endpoint/RequestLogger.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/endpoint/RequestLoggerTest.kt`

- [ ] **Step 1: Extend ResponseDiagnostics**

In `RetroArchResponse.kt`, add nullable fields to `ResponseDiagnostics`:

```kotlin
val rawQuestion: String? = null,
val normalizedQuestion: String? = null,
val questionNormalizationReason: String? = null,
val normalizedQuestionMatchedTerm: String? = null,
val normalizedQuestionMatchedEntityId: String? = null,
```

- [ ] **Step 2: Extend RequestLogEntry**

In `RequestLogger.kt`, add fields to `RequestLogEntry`:

```kotlin
val rawQuestion: String? = null,
val normalizedQuestion: String? = null,
val questionNormalizationReason: String? = null,
val normalizedQuestionMatchedTerm: String? = null,
val normalizedQuestionMatchedEntityId: String? = null,
```

- [ ] **Step 3: Map diagnostics into RequestLogEntry**

Inside `RequestLogger.log`, add local cleaned values:

```kotlin
val rawQuestion = diagnostics.rawQuestion?.trim()?.takeIf { it.isNotEmpty() }
val normalizedQuestion = diagnostics.normalizedQuestion?.trim()?.takeIf { it.isNotEmpty() }
```

Pass these into `RequestLogEntry`:

```kotlin
rawQuestion = rawQuestion,
normalizedQuestion = normalizedQuestion,
questionNormalizationReason = diagnostics.questionNormalizationReason,
normalizedQuestionMatchedTerm = diagnostics.normalizedQuestionMatchedTerm,
normalizedQuestionMatchedEntityId = diagnostics.normalizedQuestionMatchedEntityId,
```

- [ ] **Step 4: Write logger test**

Add to `RequestLoggerTest.kt`:

```kotlin
@Test
fun `logs raw and normalized voice question diagnostics`() {
    val logger = RequestLogger()

    val entry = logger.log(
        label = "mega_drive__光明力量2",
        imageBase64 = "",
        paused = true,
        outputMode = "hotkey_voice:text",
        responseText = "answer",
        diagnostics = ResponseDiagnostics(
            rawQuestion = "修医是谁",
            normalizedQuestion = "修伊是谁",
            questionNormalizationReason = "homophone",
            normalizedQuestionMatchedTerm = "修伊",
            normalizedQuestionMatchedEntityId = "npc.jaha",
        ),
        question = "修伊是谁",
        questionSource = "hotkey_voice",
    )

    assertEquals("修伊是谁", entry.question)
    assertEquals("修医是谁", entry.rawQuestion)
    assertEquals("修伊是谁", entry.normalizedQuestion)
    assertEquals("homophone", entry.questionNormalizationReason)
    assertEquals("修伊", entry.normalizedQuestionMatchedTerm)
    assertEquals("npc.jaha", entry.normalizedQuestionMatchedEntityId)
}
```

- [ ] **Step 5: Run tests**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
./gradlew :app:testDebugUnitTest --tests 'com.retrosprite.app.endpoint.RequestLoggerTest'
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/retrosprite/app/endpoint/model/RetroArchResponse.kt \
  app/src/main/kotlin/com/retrosprite/app/endpoint/RequestLogger.kt \
  app/src/test/kotlin/com/retrosprite/app/endpoint/RequestLoggerTest.kt
git commit -m "feat: log voice question normalization diagnostics"
```

---

### Task 4: Persist Normalization Fields In Room Logs

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/data/db/entity/RequestLogEntity.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/data/models/DomainModels.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/data/models/Mappers.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/endpoint/RoomBackedRequestLogSink.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/data/db/RetroSpriteDatabase.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/data/db/RetroSpriteDatabaseMigrationTest.kt`

- [ ] **Step 1: Add Room entity fields**

In `RequestLogEntity`, add:

```kotlin
@ColumnInfo(name = "raw_question")
val rawQuestion: String? = null,

@ColumnInfo(name = "normalized_question")
val normalizedQuestion: String? = null,

@ColumnInfo(name = "question_normalization_reason")
val questionNormalizationReason: String? = null,

@ColumnInfo(name = "normalized_question_matched_term")
val normalizedQuestionMatchedTerm: String? = null,

@ColumnInfo(name = "normalized_question_matched_entity_id")
val normalizedQuestionMatchedEntityId: String? = null,
```

- [ ] **Step 2: Add domain fields**

In `RequestLogDomain`, add the same nullable fields after `questionSource`:

```kotlin
val rawQuestion: String? = null,
val normalizedQuestion: String? = null,
val questionNormalizationReason: String? = null,
val normalizedQuestionMatchedTerm: String? = null,
val normalizedQuestionMatchedEntityId: String? = null,
```

- [ ] **Step 3: Update mappers**

In `RequestLogEntity.toDomain()` add:

```kotlin
rawQuestion = rawQuestion,
normalizedQuestion = normalizedQuestion,
questionNormalizationReason = questionNormalizationReason,
normalizedQuestionMatchedTerm = normalizedQuestionMatchedTerm,
normalizedQuestionMatchedEntityId = normalizedQuestionMatchedEntityId,
```

In `RequestLogDomain.toEntity()` add:

```kotlin
rawQuestion = rawQuestion,
normalizedQuestion = normalizedQuestion,
questionNormalizationReason = questionNormalizationReason,
normalizedQuestionMatchedTerm = normalizedQuestionMatchedTerm,
normalizedQuestionMatchedEntityId = normalizedQuestionMatchedEntityId,
```

- [ ] **Step 4: Update RoomBackedRequestLogSink mapping**

In `RequestLogEntry.toDomainModel()` add:

```kotlin
rawQuestion = rawQuestion,
normalizedQuestion = normalizedQuestion,
questionNormalizationReason = questionNormalizationReason,
normalizedQuestionMatchedTerm = normalizedQuestionMatchedTerm,
normalizedQuestionMatchedEntityId = normalizedQuestionMatchedEntityId,
```

- [ ] **Step 5: Add v8 migration**

In `RetroSpriteDatabase.kt`, bump:

```kotlin
version = 8,
```

Add:

```kotlin
private val MIGRATION_7_8: Migration = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE request_logs ADD COLUMN raw_question TEXT")
        db.execSQL("ALTER TABLE request_logs ADD COLUMN normalized_question TEXT")
        db.execSQL("ALTER TABLE request_logs ADD COLUMN question_normalization_reason TEXT")
        db.execSQL("ALTER TABLE request_logs ADD COLUMN normalized_question_matched_term TEXT")
        db.execSQL("ALTER TABLE request_logs ADD COLUMN normalized_question_matched_entity_id TEXT")
    }
}
```

Append `MIGRATION_7_8` to `MIGRATIONS`.

- [ ] **Step 6: Add migration test**

In `RetroSpriteDatabaseMigrationTest.kt`, add:

```kotlin
@Test
fun migration7To8AddsQuestionNormalizationColumns() {
    helper.createDatabase(TEST_DB, 7).apply {
        execSQL(
            """
            INSERT INTO request_logs (
                timestamp, request_key, label, system, game, image_size, paused,
                output_mode, response_text, error_message, duration_millis
            ) VALUES (
                1, 'rk', 'mega_drive__光明力量2', 'mega_drive', '光明力量2',
                0, 1, 'hotkey_voice:text', 'answer', NULL, 10
            )
            """.trimIndent()
        )
        close()
    }

    val db = helper.runMigrationsAndValidate(
        TEST_DB,
        8,
        true,
        RetroSpriteDatabase.MIGRATIONS.single { it.startVersion == 7 && it.endVersion == 8 }
    )

    db.query("SELECT raw_question, normalized_question, question_normalization_reason FROM request_logs").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertTrue(cursor.isNull(0))
        assertTrue(cursor.isNull(1))
        assertTrue(cursor.isNull(2))
    }
}
```

If the existing test helper expects the full migration array, pass `*RetroSpriteDatabase.MIGRATIONS` instead of the single migration.

- [ ] **Step 7: Run migration tests**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
./gradlew :app:connectedDebugAndroidTest --tests 'com.retrosprite.app.data.db.RetroSpriteDatabaseMigrationTest'
```

Expected: PASS on the connected RG476H or emulator.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/retrosprite/app/data/db/entity/RequestLogEntity.kt \
  app/src/main/kotlin/com/retrosprite/app/data/models/DomainModels.kt \
  app/src/main/kotlin/com/retrosprite/app/data/models/Mappers.kt \
  app/src/main/kotlin/com/retrosprite/app/endpoint/RoomBackedRequestLogSink.kt \
  app/src/main/kotlin/com/retrosprite/app/data/db/RetroSpriteDatabase.kt \
  app/src/test/kotlin/com/retrosprite/app/data/db/RetroSpriteDatabaseMigrationTest.kt
git commit -m "feat: persist question normalization diagnostics"
```

---

### Task 5: Surface Normalization In Debug JSON

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/integration/UiModelMappers.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/ui/integration/UiModelMappersTest.kt`

- [ ] **Step 1: Add UI/debug JSON fields**

In the function that builds `fullResponseJson`, add:

```kotlin
"\"raw_question\":${entry.rawQuestion.jsonStringOrNull()}",
"\"normalized_question\":${entry.normalizedQuestion.jsonStringOrNull()}",
"\"question_normalization_reason\":${entry.questionNormalizationReason.jsonStringOrNull()}",
"\"normalized_question_matched_term\":${entry.normalizedQuestionMatchedTerm.jsonStringOrNull()}",
"\"normalized_question_matched_entity_id\":${entry.normalizedQuestionMatchedEntityId.jsonStringOrNull()}",
```

- [ ] **Step 2: Add mapper test**

In `UiModelMappersTest.kt`, create a `RequestLogEntry` with:

```kotlin
rawQuestion = "修医是谁",
normalizedQuestion = "修伊是谁",
questionNormalizationReason = "homophone",
normalizedQuestionMatchedTerm = "修伊",
normalizedQuestionMatchedEntityId = "npc.jaha",
```

Assert:

```kotlin
assertTrue(ui.fullResponseJson.contains(""""raw_question":"修医是谁""""))
assertTrue(ui.fullResponseJson.contains(""""normalized_question":"修伊是谁""""))
assertTrue(ui.fullResponseJson.contains(""""question_normalization_reason":"homophone""""))
assertTrue(ui.fullResponseJson.contains(""""normalized_question_matched_term":"修伊""""))
assertTrue(ui.fullResponseJson.contains(""""normalized_question_matched_entity_id":"npc.jaha""""))
```

- [ ] **Step 3: Run mapper tests**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
./gradlew :app:testDebugUnitTest --tests 'com.retrosprite.app.ui.integration.UiModelMappersTest'
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/retrosprite/app/ui/integration/UiModelMappers.kt \
  app/src/test/kotlin/com/retrosprite/app/ui/integration/UiModelMappersTest.kt
git commit -m "feat: expose question normalization diagnostics"
```

---

### Task 6: End-To-End Retrieval Regression For `修医是谁`

**Files:**
- Modify: `app/src/test/kotlin/com/retrosprite/app/domain/SampleShiningForceIIQuestionPipelineTest.kt`
- Modify: `app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl`

- [ ] **Step 1: Add pipeline regression test**

Add a test that drives the same endpoint-level generator path rather than only the retrieval pipeline:

```kotlin
@Test
fun `voice homophone question about xiu yi resolves to character evidence`() = runTest {
    val response = responseGenerator.generate(
        request = RetroArchRequest(
            image = "",
            label = "mega_drive__光明力量2",
            question = "修医是谁",
            state = RetroArchState(paused = 1),
        ),
        outputMode = "hotkey_voice:text",
    )

    assertTrue(response.text.orEmpty().contains("来源："))
    assertEquals("修伊是谁", response.diagnostics.normalizedQuestion)
    assertEquals("homophone", response.diagnostics.questionNormalizationReason)
    assertTrue(response.diagnostics.answerType != "no_evidence")
}
```

Use the existing fixture setup in `SampleShiningForceIIQuestionPipelineTest`; do not create a second copy of the full GKP loader.

- [ ] **Step 2: Add a golden row**

Append to `app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl`:

```json
{"id":"sf2_voice_homophone_xiuyi_who","question":"修医是谁","expected_normalized_question":"修伊是谁","expected_stage":"evidence","expected_terms":["修伊"],"spoiler_level":"light"}
```

- [ ] **Step 3: Run Shining Force II tests**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
./gradlew :app:testDebugUnitTest --tests 'com.retrosprite.app.domain.SampleShiningForceIIQuestionPipelineTest'
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/test/kotlin/com/retrosprite/app/domain/SampleShiningForceIIQuestionPipelineTest.kt \
  app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl
git commit -m "test: cover Shining Force II voice homophone normalization"
```

---

### Task 7: Update QA Notes And Run Full Verification

**Files:**
- Modify: `docs/qa-feedback/asr-hotword-voice-evaluation.md`

- [ ] **Step 1: Update QA notes**

Add a section:

```markdown
## Question Normalization Retest

| Time | Raw ASR transcript | Normalized question | Mode | Answer stage | Result | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| 2026-05-23 manual retest | 修医是谁 | 修伊是谁 | stream_small | evidence | Pass | ASR homophone was corrected by GKP-scoped GameTermNormalizer before retrieval. |
```

- [ ] **Step 2: Run focused unit tests**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
./gradlew :app:testDebugUnitTest \
  --tests 'com.retrosprite.app.domain.normalization.GameTermNormalizerTest' \
  --tests 'com.retrosprite.app.endpoint.QueryPipelineResponseGeneratorTest' \
  --tests 'com.retrosprite.app.endpoint.RequestLoggerTest' \
  --tests 'com.retrosprite.app.ui.integration.UiModelMappersTest' \
  --tests 'com.retrosprite.app.domain.SampleShiningForceIIQuestionPipelineTest'
```

Expected: PASS.

- [ ] **Step 3: Run debug build**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
```

Expected: PASS.

- [ ] **Step 4: Install on RG476H**

Run:

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb forward tcp:4404 tcp:4404
adb shell am force-stop com.retrosprite.app
adb shell am start -W -n com.retrosprite.app/.MainActivity
curl --max-time 3 -sS http://127.0.0.1:4404/health
```

Expected:

```json
{"status":"ok","version":"0.1.0"}
```

- [ ] **Step 5: True-device voice retest**

Run:

```bash
adb logcat -c
curl --max-time 3 -sS -X POST http://127.0.0.1:4404/ \
  -H 'Content-Type: application/json' \
  -d '{"image":"","label":"mega_drive__光明力量2@@asr:stream_small","state":{"paused":1}}'
say -v Ting-Ting -r 145 '修伊是谁'
sleep 8
curl --max-time 3 -sS http://127.0.0.1:4404/debug/latest-request
```

Expected latest request:

```json
{
  "question": "修伊是谁",
  "raw_question": "修医是谁",
  "normalized_question": "修伊是谁",
  "question_normalization_reason": "homophone",
  "pipeline_stage": "evidence"
}
```

If ASR already returns `修伊是谁`, expected:

```json
{
  "question": "修伊是谁",
  "raw_question": null,
  "normalized_question": null,
  "pipeline_stage": "evidence"
}
```

- [ ] **Step 6: Commit final QA docs**

```bash
git add docs/qa-feedback/asr-hotword-voice-evaluation.md
git commit -m "docs: record ASR term normalization QA"
```

---

## Acceptance Criteria

- `修医是谁` normalizes to `修伊是谁` only when the current game GKP contains `修伊`.
- `修医是谁` does not normalize if two current-game terms have equal homophone scores.
- Text questions from the app are not rewritten unless they enter through `hotkey_voice`.
- Existing retrieval behavior for `这游戏怎么玩` and template-document matching remains unchanged.
- `/debug/latest-request` exposes enough evidence to distinguish:
  - raw ASR transcript,
  - normalized question used for retrieval,
  - normalization reason,
  - matched term/entity.
- True-device retest either reaches `pipeline_stage=evidence` for the corrected `修伊` question or records the exact remaining blocker in `docs/qa-feedback/asr-hotword-voice-evaluation.md`.

## Risk Notes

- A tiny pinyin map is intentionally limited. It is good for the current Shining Force II vocabulary, not a universal Chinese ASR correction engine.
- Automatic rewrite must stay conservative. A wrong rewrite is worse than `no_evidence` because it can give a confident answer to a different question.
- Logging both raw and normalized text is required for future QA; do not overwrite the raw ASR transcript.

## Self-Review

- Spec coverage: The plan covers ASR post-processing, GKP-scoped term matching, logging, persistence, debug JSON, tests, and true-device validation.
- Placeholder scan: No placeholder markers remain; the QA row uses `manual retest` until the executor replaces it with the actual wall-clock test time.
- Type consistency: `GameTermNormalizationResult`, `GameTermNormalizationCandidate`, and diagnostics field names are used consistently across tasks.
