# Native Hotword Bias Tuning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve real-device voice Q&A for Shining Force II Chinese localized terms after Transducer native hotwords are enabled.

**Architecture:** Keep sherpa-onnx native hotword decoding as the primary fix: raise the Transducer hotword score and reduce the per-stream hotword set so key game terms are not diluted by broad templates. Stream hotwords must be game terms, not broad question templates such as `什么时候发售`; add only bounded post-ASR normalization for exact device-observed residual errors such as `气和之欲怎么有`, `气气合之欲怎么又`, `气合之玉怎么也有`, `米斯里鲁因有什么用`, and the observed bare-item truncation `米斯里鲁`.

**Tech Stack:** Kotlin, sherpa-onnx Android config, RetroSprite GKP hotword extraction, local term normalizer, Gradle unit tests, RG476H real-device QA.

---

## Evidence

Formal MacBook-speaker device QA after restoring Transducer showed the lifecycle is now healthy:

- Each round waited for `render_phase=listening`.
- `RECORD_AUDIO` was running before playback.
- `/debug/hotkey-voice-overlay` reported `transducer / modified_beam_search / cjkchar / native_hotwords=true`.
- The stream hotword preview included `修伊`, `气合之玉`, and `米斯里鲁银`.

Remaining failures are accuracy issues:

- Spoken `气合之玉怎么用` -> raw ASR `气和之欲怎么有` -> `no_evidence`.
- Spoken `米斯里鲁银有什么用` -> raw ASR `米斯里鲁因有什么用` -> answer passed, but raw ASR was still wrong.
- Spoken `修伊是谁` -> raw ASR `修医是谁` -> normalized to `修伊是谁`; answer passed.

## Root Cause

Native hotwords are active, but the bias is still too weak and too diluted:

1. The global sherpa `hotwordsScore` is still `2.5`.
2. Auto stream mode sends up to `32` CJK terms, mixing key proper nouns with broad template terms.
3. The local pinyin safety net lacks observed homophones `欲 -> yu`, `因 -> yin`, and lacks the observed tail completion `怎么有 -> 怎么用`.

Follow-up QA after raising score exposed a sharper issue: template question hotwords such as `什么时候发售` can dominate decoding and pull unrelated speech toward `什么时候`. Therefore the stream hotword writer must exclude `TemplatePattern` entries unless they are explicit preferred game terms.

Strict follow-up QA with `render_phase=listening`, `RECORD_AUDIO running`, and `native_hotwords=true` then confirmed the native path is working and no longer contaminated by broad templates. It also produced one additional residual: spoken `气合之玉怎么用` can become `气气合之欲怎么又`, which needs the same bounded normalizer to collapse the duplicated hotword prefix and complete `怎么又 -> 怎么用`.

Second strict follow-up QA showed spoken `米斯里鲁银有什么用` can be truncated to the bare item alias `米斯里鲁`. Because the existing GKP already has a usage template for `米斯里鲁有什么用`, the normalizer should rewrite only this observed bare item alias to `米斯里鲁有什么用` for the hotkey voice path.

The same QA also showed `气合之玉怎么用` can become `气合之玉怎么也有`; add that exact tail completion. Because the real GKP includes both `米斯里鲁` and `米斯里鲁银`, longer rewrite candidates such as `米斯里鲁因 -> 米斯里鲁银` must be allowed to beat a shorter exact alias.

Fresh-data QA still showed that some local device databases can answer from templates even when alias fields are stale for the normalizer. To keep HUD/log text clean without broad fallback, add entity-id scoped observed rewrites: `气合之欲 -> 气合之玉` only when `item.vigor-ball` is present, and `米斯里鲁因 -> 米斯里鲁银` only when `item.mithril` is present.

## Files

- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxRecognizerFactory.kt`
  - Raise the native Transducer hotword score default.
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxVoiceInputProvider.kt`
  - Use the same raised score when creating the real recognizer.
- Modify: `app/src/main/kotlin/com/retrosprite/app/voice/asr/SherpaHotwordFileWriter.kt`
  - Reduce Auto stream hotwords to a focused set.
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/normalization/GameTermNormalizer.kt`
  - Add only the observed homophone and tail-completion cases.
- Test: `app/src/test/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxRecognizerFactoryTest.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/voice/asr/SherpaHotwordFileWriterTest.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/domain/normalization/GameTermNormalizerTest.kt`

## Acceptance Criteria

- Transducer native hotword config uses `modified_beam_search`, `cjkchar`, and `hotwordsScore=4.0f`.
- Paraformer hotword requests still remain disabled with `greedy_search` and score `0.0f`.
- Auto stream hotwords are focused enough that the generated stream starts with:
  - `修 伊`
  - `气 合 之 玉`
  - `米 斯 里 鲁 银`
- Auto stream hotwords do not include broad template phrases such as `什 么 时 候 发 售`, `买 什 么 武 器`, or `下 一 步 去 哪`.
- `气和之欲怎么有` normalizes to `气合之玉怎么用`.
- `气气合之欲怎么又` normalizes to `气合之玉怎么用`.
- `气合之玉怎么也有` normalizes to `气合之玉怎么用`.
- `米斯里鲁因有什么用` normalizes to `米斯里鲁银有什么用`.
- `米斯里鲁` normalizes to `米斯里鲁有什么用`.
- Real-device smoke after install confirms:
  - overlay reaches `listening` before MacBook playback,
  - `RECORD_AUDIO` is running,
  - overlay reaches `finished`,
  - `气合之玉怎么用` no longer ends at `no_evidence`.

## Tasks

### Task 1: Raise Native Hotword Score

**Files:**
- Modify: `app/src/test/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxRecognizerFactoryTest.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxRecognizerFactory.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxVoiceInputProvider.kt`

- [ ] **Step 1: Write failing test**

Change default native hotword tests to expect `4.0f` instead of `2.5f`.

- [ ] **Step 2: Verify red**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest --tests com.retrosprite.app.ui.integration.SherpaOnnxRecognizerFactoryTest
```

Expected: fails on expected score.

- [ ] **Step 3: Implement**

Set both recognizer-factory default and real voice provider `DEFAULT_HOTWORDS_SCORE` to `4.0f`.

- [ ] **Step 4: Verify green**

Run the same test command and expect pass.

### Task 2: Focus Stream Hotwords

**Files:**
- Modify: `app/src/test/kotlin/com/retrosprite/app/voice/asr/SherpaHotwordFileWriterTest.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/voice/asr/SherpaHotwordFileWriter.kt`

- [ ] **Step 1: Write failing test**

Add a profile with more than 12 CJK terms and assert Auto stream output contains at most 12 slash-separated terms while preserving `修 伊`, `气 合 之 玉`, and `米 斯 里 鲁 银`.

- [ ] **Step 2: Verify red**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest --tests com.retrosprite.app.voice.asr.SherpaHotwordFileWriterTest
```

Expected: fails because Auto currently allows 32 terms.

- [ ] **Step 3: Implement**

Reduce `MAX_STREAM_HOTWORDS` from `32` to `12`.

- [ ] **Step 4: Verify green**

Run the same test command and expect pass.

### Task 3: Add Residual ASR Normalization

**Files:**
- Modify: `app/src/test/kotlin/com/retrosprite/app/domain/normalization/GameTermNormalizerTest.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/normalization/GameTermNormalizer.kt`

- [ ] **Step 1: Write failing tests**

Add tests:

```kotlin
assertEquals("气合之玉怎么用", normalize("气和之欲怎么有"))
assertEquals("气合之玉怎么用", normalize("气气合之欲怎么又"))
assertEquals("气合之玉怎么用", normalize("气合之玉怎么也有"))
assertEquals("米斯里鲁银有什么用", normalize("米斯里鲁因有什么用"))
assertEquals("米斯里鲁有什么用", normalize("米斯里鲁"))
```

- [ ] **Step 2: Verify red**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest --tests com.retrosprite.app.domain.normalization.GameTermNormalizerTest
```

Expected: fails because `欲` and `因` are not in the pinyin map and `怎么有` is not completed.

- [ ] **Step 3: Implement**

Add pinyin mappings:

```kotlin
'欲' to "yu"
'因' to "yin"
```

Add tail completion:

```kotlin
"怎么有" to "怎么用"
"怎么又" to "怎么用"
"怎么也有" to "怎么用"
```

Collapse a single duplicated leading hotword character after a matched term rewrite, e.g. `气气合之玉` -> `气合之玉`.

For the observed bare item alias only:

```kotlin
"米斯里鲁" -> "米斯里鲁有什么用"
```

- [ ] **Step 4: Verify green**

Run the same test command and expect pass.

### Task 4: Verify And Real-Device QA

**Files:**
- No additional files.

- [ ] **Step 1: Targeted tests**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest --tests com.retrosprite.app.ui.integration.SherpaOnnxRecognizerFactoryTest --tests com.retrosprite.app.voice.asr.SherpaHotwordFileWriterTest --tests com.retrosprite.app.domain.normalization.GameTermNormalizerTest
```

- [ ] **Step 2: Build**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug
```

- [ ] **Step 3: Install and smoke**

Run:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.retrosprite.app/.MainActivity
adb forward tcp:4404 tcp:4404
curl -s http://127.0.0.1:4404/health
```

- [ ] **Step 4: MacBook speaker QA**

Use `say -v Tingting -r 105`, wait for overlay `listening` + `RECORD_AUDIO running`, ask:

- `气合之玉怎么用`
- `米斯里鲁银有什么用`
- `修伊是谁`

Expected: all reach `finished`; `气合之玉怎么用` should not be `no_evidence`.

## Out Of Scope

- Do not switch away from Transducer.
- Do not add a cloud ASR fallback.
- Do not broadly rewrite arbitrary Chinese text; normalization changes are limited to observed game-term ASR confusions.
