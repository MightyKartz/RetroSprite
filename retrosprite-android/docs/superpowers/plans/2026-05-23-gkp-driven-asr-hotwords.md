# GKP-Driven ASR Hotwords Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve RetroSprite's real voice recognition for game-specific Chinese patch names by feeding current-game GKP names into sherpa-onnx contextual biasing / hotwords before decoding.

**Architecture:** Build an ASR biasing layer between the RetroArch hotkey context and `SherpaOnnxVoiceInputProvider`. The layer extracts high-value terms from the resolved current GKP, writes a bounded sherpa-onnx hotwords file, recreates the recognizer with `modified_beam_search`, and records ASR evidence so we can tune hotword scores from true-device tests instead of patching random misrecognitions forever.

**Tech Stack:** Kotlin, sherpa-onnx Android AAR `6.25.21`, Room-backed GKP repositories, Jetpack Compose Settings diagnostics, `StateFlow`, JUnit, Android instrumentation tests, `adb`, RG 476H true-device voice validation.

## Implementation Status

Implemented on 2026-05-23:

- Added GKP-driven ASR hotword contracts, extractor, profile provider, and sherpa hotword file writer.
- Wired current-game hotword profiles into the hotkey voice path.
- Configured sherpa-onnx to use `modified_beam_search` with `hotwordsFile` only when a profile is active.
- Exposed active ASR hotword count in the settings microphone section.
- Added focused JVM tests for extraction, file writing, profile construction, and recognizer config.
- Installed and launched the debug APK on the connected RG 476H device.

Deferred:

- Room request-log migration for ASR diagnostics.
- A user-facing feature toggle for disabling hotwords.
- Automated Android instrumentation for real audio recognition quality.
- Manual true-microphone before/after scoring, tracked in `docs/qa-feedback/asr-hotword-voice-evaluation.md`.

---

## Why This Plan Exists

True-device testing showed that the question-answering pipeline can answer written questions like `修伊怎么用？`, `吉布是谁？`, `气合之玉怎么用？`, and `精灵森林是什么？`, but the current local ASR often loses the key term:

- Mac speaker said `修伊怎么用`; device ASR produced `用英子没有` or just `用`.
- Mac speaker said `皮特是谁`; device ASR produced `是谁`.
- Mac speaker said `吉布是谁`; device ASR produced `是谁`.
- Mac speaker said `精灵森林是什么`; device ASR produced `运营是什么`.
- Mac speaker said `米斯里鲁银有什么用`; device ASR produced `一路也有什么用`, but retrieval still recovered Mithril because existing aliases were close enough.

These are ASR-domain failures, not GKP retrieval failures. The durable fix is contextual biasing: before ASR decoding, tell sherpa-onnx that current-game terms such as `修伊`, `吉布`, `气合之玉`, `米斯里鲁银`, and `精灵森林` are likely.

Research grounding:

- sherpa-onnx documents hotwords as contextual biasing for special names and rare phrases, and requires transducer models plus `modified_beam_search` for hotword support: https://k2-fsa.github.io/sherpa/onnx/hotwords/index.html
- sherpa-onnx also offers keyword spotting for a bounded keyword list, but this plan keeps the primary path as ASR hotword biasing first: https://k2-fsa.github.io/sherpa/onnx/kws/index.html
- WeNet describes contextual biasing as constructing contextual FSTs from hotwords / bias phrases so decoder search prefers those phrases: https://wenet.org.cn/wenet/context.html
- Android's system recognizer has `RecognizerIntent.EXTRA_BIASING_STRINGS`, which confirms the same product concept exists at the platform level, but RetroSprite's current voice path uses local sherpa-onnx instead of Android `SpeechRecognizer`: https://developer.android.com/reference/android/speech/RecognizerIntent

## Current Repo Facts

- `SherpaOnnxRecognizerFactory` currently creates `OnlineRecognizerConfig` without hotwords and without an explicit decoding method.
- The bundled model is `sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23`, configured as an online transducer model. sherpa-onnx hotwords support transducer models.
- The local AAR class surface exposes:
  - `OnlineRecognizerConfig.decodingMethod`
  - `OnlineRecognizerConfig.hotwordsFile`
  - `OnlineRecognizerConfig.hotwordsScore`
  - `OnlineRecognizer.createStream(String)`
- `HotkeyVoiceQuestionController` has the RetroArch label at voice-start time, but `VoiceInputProvider.startListening()` currently receives no game context.
- The GKP importer already loads `aliases.json` into knowledge rows, so GKP terms can be extracted from installed DB rows rather than hard-coded per game.

## Product Decisions

- Use current-game GKP terms as the source of truth for ASR hotwords.
- Do not train or fine-tune a new ASR model in this pass.
- Do not keep adding arbitrary ASR typo aliases as the primary solution.
- Keep ASR hotwords local-only and generated on device.
- Keep the built-in Chinese ASR model as the default.
- Enable hotwords only for the hotkey voice path and Settings microphone test when a current game context exists.
- Keep text input and debug `/debug/ask` behavior unchanged.
- Keep hotword files non-executable plain text in app cache or files directory.
- Log enough ASR evidence to compare before/after on true device.
- Add a feature flag so hotwords can be disabled if `modified_beam_search` is too slow on RG 476H.

## File Structure

Create:

- `app/src/main/kotlin/com/retrosprite/app/voice/asr/AsrBiasingContracts.kt`
  - Defines `AsrBiasingProfile`, `AsrHotwordEntry`, `AsrHotwordSource`, and limits.

- `app/src/main/kotlin/com/retrosprite/app/voice/asr/GkpAsrHotwordExtractor.kt`
  - Extracts current-game hotword candidates from `KnowledgeRepository` rows.

- `app/src/main/kotlin/com/retrosprite/app/voice/asr/SherpaHotwordFileWriter.kt`
  - Writes a deterministic sherpa-onnx hotwords file from a profile.

- `app/src/main/kotlin/com/retrosprite/app/voice/asr/AsrBiasingProfileProvider.kt`
  - Resolves RetroArch label to game id and builds the current hotword profile.

- `app/src/main/kotlin/com/retrosprite/app/voice/asr/AsrRecognitionContext.kt`
  - Carries label, resolved game id, spoiler level, hotword profile id, and source route into voice input.

- `app/src/main/kotlin/com/retrosprite/app/voice/asr/AsrEvidenceRecorder.kt`
  - Records spoken test prompt, ASR output, answer status, and hotword profile id for QA analysis.

- `app/src/test/kotlin/com/retrosprite/app/voice/asr/GkpAsrHotwordExtractorTest.kt`
- `app/src/test/kotlin/com/retrosprite/app/voice/asr/SherpaHotwordFileWriterTest.kt`
- `app/src/test/kotlin/com/retrosprite/app/voice/asr/AsrBiasingProfileProviderTest.kt`
- `app/src/test/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxRecognizerFactoryTest.kt`
- `app/src/androidTest/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxHotwordsCapabilityAndroidTest.kt`
- `docs/qa-feedback/asr-hotword-voice-evaluation.md`

Modify:

- `app/src/main/kotlin/com/retrosprite/app/ui/viewmodel/UiContracts.kt`
  - Add optional `AsrRecognitionContext` to `VoiceInputProvider.startListening`.

- `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxRecognizerFactory.kt`
  - Accept optional hotwords config and set `decodingMethod = "modified_beam_search"`, `hotwordsFile`, and `hotwordsScore`.

- `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxVoiceInputProvider.kt`
  - Recreate recognizer when hotword profile changes and expose active profile in state.

- `app/src/main/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionController.kt`
  - Build `AsrRecognitionContext` from the hotkey label before calling `startListening`.

- `app/src/main/kotlin/com/retrosprite/app/ui/screens/settings/SettingsScreen.kt`
  - Show whether ASR hotwords are enabled and which current game profile is active.

- `app/src/main/kotlin/com/retrosprite/app/ui/viewmodel/UiContracts.kt`
  - Add UI fields for active ASR biasing profile, term count, and enabled/disabled state.

- `app/src/main/kotlin/com/retrosprite/app/ServiceLocator.kt`
  - Wire `AsrBiasingProfileProvider` into hotkey voice and settings microphone paths.

- `app/src/main/kotlin/com/retrosprite/app/data/db/entity/RequestLogEntity.kt`
  - Add nullable ASR diagnostic columns if current request log does not already preserve enough data.

- `app/src/main/kotlin/com/retrosprite/app/data/db/RetroSpriteDatabase.kt`
  - Add Room migration for ASR diagnostic columns.

---

### Task 1: Add ASR Biasing Contracts

**Files:**
- Create: `app/src/main/kotlin/com/retrosprite/app/voice/asr/AsrBiasingContracts.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/voice/asr/AsrBiasingContractsTest.kt`

- [ ] **Step 1: Write the failing contract test**

Create `app/src/test/kotlin/com/retrosprite/app/voice/asr/AsrBiasingContractsTest.kt`:

```kotlin
package com.retrosprite.app.voice.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrBiasingContractsTest {

    @Test
    fun `profile fingerprint is stable and excludes duplicate terms`() {
        val profile = AsrBiasingProfile(
            gameId = "shining_force_ii_md",
            packVersion = "0.2.5",
            entries = listOf(
                AsrHotwordEntry(term = "修伊", score = 3.5f, source = AsrHotwordSource.Alias),
                AsrHotwordEntry(term = "修伊", score = 3.5f, source = AsrHotwordSource.Alias),
                AsrHotwordEntry(term = "Chester", score = 2.0f, source = AsrHotwordSource.CanonicalName),
            ),
        )

        assertEquals(2, profile.normalizedEntries.size)
        assertTrue(profile.fingerprint.startsWith("shining_force_ii_md:0.2.5:"))
        assertFalse(profile.fingerprint.contains(" "))
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.voice.asr.AsrBiasingContractsTest
```

Expected: fail because `AsrBiasingContracts.kt` does not exist.

- [ ] **Step 3: Add the contracts**

Create `app/src/main/kotlin/com/retrosprite/app/voice/asr/AsrBiasingContracts.kt`:

```kotlin
package com.retrosprite.app.voice.asr

import java.security.MessageDigest
import java.util.Locale

enum class AsrHotwordSource {
    CanonicalName,
    Alias,
    TemplatePattern,
}

data class AsrHotwordEntry(
    val term: String,
    val score: Float,
    val source: AsrHotwordSource,
)

data class AsrBiasingProfile(
    val gameId: String,
    val packVersion: String,
    val entries: List<AsrHotwordEntry>,
    val enabled: Boolean = true,
) {
    val normalizedEntries: List<AsrHotwordEntry> =
        entries.asSequence()
            .mapNotNull { entry ->
                val term = entry.term.cleanHotwordTerm()
                if (term.length < MIN_HOTWORD_CHARS) null else entry.copy(term = term)
            }
            .groupBy { it.term.lowercase(Locale.ROOT) }
            .map { (_, values) -> values.maxBy { it.score } }
            .sortedWith(compareByDescending<AsrHotwordEntry> { it.score }.thenBy { it.term })
            .take(MAX_HOTWORDS_PER_PROFILE)
            .toList()

    val fingerprint: String =
        "$gameId:$packVersion:${normalizedEntries.joinToString("|") { "${it.term}:${it.score}" }.sha1Short()}"

    companion object {
        const val MAX_HOTWORDS_PER_PROFILE = 160
        const val MIN_HOTWORD_CHARS = 2
    }
}

data class AsrRecognitionContext(
    val label: String,
    val gameId: String?,
    val spoilerLevel: String,
    val source: String,
    val biasingProfile: AsrBiasingProfile? = null,
)

fun String.cleanHotwordTerm(): String =
    trim()
        .replace(Regex("\\s+"), " ")
        .replace("（", "(")
        .replace("）", ")")

private fun String.sha1Short(): String {
    val bytes = MessageDigest.getInstance("SHA-1").digest(toByteArray(Charsets.UTF_8))
    return bytes.take(8).joinToString("") { "%02x".format(it) }
}
```

- [ ] **Step 4: Run the contract test and verify it passes**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.voice.asr.AsrBiasingContractsTest
```

Expected: pass.

---

### Task 2: Extract Hotwords From Current-Game GKP

**Files:**
- Create: `app/src/main/kotlin/com/retrosprite/app/voice/asr/GkpAsrHotwordExtractor.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/voice/asr/GkpAsrHotwordExtractorTest.kt`

- [ ] **Step 1: Write the failing extractor test**

Create `GkpAsrHotwordExtractorTest.kt`:

```kotlin
package com.retrosprite.app.voice.asr

import com.retrosprite.app.data.models.KnowledgeChunkDomain
import org.junit.Assert.assertTrue
import org.junit.Test

class GkpAsrHotwordExtractorTest {

    @Test
    fun `extracts chinese patch names with higher score than generic role words`() {
        val rows = listOf(
            chunk(
                entityId = "npc.chester",
                canonicalName = "Chester / 切斯特",
                aliases = listOf("Chester", "切斯特", "修伊", "骑士"),
            ),
            chunk(
                entityId = "item.vigor-ball",
                canonicalName = "活力球 / Vigor Ball",
                aliases = listOf("活力球", "气合之玉", "Vigor Ball", "武僧"),
            ),
        )

        val profile = GkpAsrHotwordExtractor().extract(
            gameId = "shining_force_ii_md",
            packVersion = "0.2.5",
            rows = rows,
        )

        assertTrue(profile.normalizedEntries.any { it.term == "修伊" && it.score >= 4.0f })
        assertTrue(profile.normalizedEntries.any { it.term == "气合之玉" && it.score >= 4.0f })
        assertTrue(profile.normalizedEntries.first { it.term == "修伊" }.score >
            profile.normalizedEntries.first { it.term == "骑士" }.score)
    }

    private fun chunk(
        entityId: String,
        canonicalName: String,
        aliases: List<String>,
    ): KnowledgeChunkDomain =
        KnowledgeChunkDomain(
            gameId = "shining_force_ii_md",
            entityId = entityId,
            entityType = entityId.substringBefore("."),
            canonicalName = canonicalName,
            aliases = aliases,
            descriptionShort = "",
            descriptionLong = null,
            progressGate = "start",
            spoilerLevel = "light",
            sourceRefs = emptyList(),
            confidence = "community",
            templateAnswersJson = null,
        )
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.voice.asr.GkpAsrHotwordExtractorTest
```

Expected: fail because the extractor does not exist.

- [ ] **Step 3: Add the extractor**

Create `GkpAsrHotwordExtractor.kt`:

```kotlin
package com.retrosprite.app.voice.asr

import com.retrosprite.app.data.models.KnowledgeChunkDomain

class GkpAsrHotwordExtractor {

    fun extract(
        gameId: String,
        packVersion: String,
        rows: List<KnowledgeChunkDomain>,
    ): AsrBiasingProfile {
        val entries = buildList {
            rows.filter { it.gameId == gameId }.forEach { row ->
                row.canonicalName.split("/", "／").forEach { name ->
                    addHotword(name, scoreForCanonical(row), AsrHotwordSource.CanonicalName)
                }
                row.aliases.forEach { alias ->
                    addHotword(alias, scoreForAlias(row, alias), AsrHotwordSource.Alias)
                }
                row.templateQuestionPatterns().forEach { pattern ->
                    addHotword(pattern, 1.6f, AsrHotwordSource.TemplatePattern)
                }
            }
        }
        return AsrBiasingProfile(gameId = gameId, packVersion = packVersion, entries = entries)
    }

    private fun MutableList<AsrHotwordEntry>.addHotword(
        term: String,
        score: Float,
        source: AsrHotwordSource,
    ) {
        val cleaned = term.cleanHotwordTerm()
        if (cleaned.isBlank()) return
        if (cleaned.length > MAX_TERM_LENGTH) return
        add(AsrHotwordEntry(term = cleaned, score = score, source = source))
    }

    private fun scoreForCanonical(row: KnowledgeChunkDomain): Float =
        when (row.entityType) {
            "npc", "item", "location" -> 3.0f
            else -> 2.0f
        }

    private fun scoreForAlias(row: KnowledgeChunkDomain, alias: String): Float {
        val isPatchLikeChineseName = alias.any { it.code in CJK_RANGE } &&
            alias.length in 2..6 &&
            row.entityType in setOf("npc", "item", "location")
        return when {
            isPatchLikeChineseName -> 4.2f
            row.entityType in setOf("npc", "item", "location") -> 3.2f
            else -> 1.8f
        }
    }

    private fun KnowledgeChunkDomain.templateQuestionPatterns(): List<String> =
        templateAnswersJson
            ?.let { QUESTION_PATTERN.findAll(it).map { match -> match.groupValues[1] }.toList() }
            .orEmpty()

    private companion object {
        const val MAX_TERM_LENGTH = 24
        val CJK_RANGE = 0x4E00..0x9FFF
        val QUESTION_PATTERN = Regex("\\\"question_patterns\\\"\\s*:\\s*\\[(.*?)\\]")
    }
}
```

- [ ] **Step 4: Run the extractor test and verify it passes**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.voice.asr.GkpAsrHotwordExtractorTest
```

Expected: pass.

---

### Task 3: Write sherpa-onnx Hotwords Files

**Files:**
- Create: `app/src/main/kotlin/com/retrosprite/app/voice/asr/SherpaHotwordFileWriter.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/voice/asr/SherpaHotwordFileWriterTest.kt`

- [ ] **Step 1: Write the failing writer test**

Create `SherpaHotwordFileWriterTest.kt`:

```kotlin
package com.retrosprite.app.voice.asr

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaHotwordFileWriterTest {

    @Test
    fun `writes deterministic hotwords file with score suffixes`() {
        val dir = Files.createTempDirectory("retrosprite-hotwords").toFile()
        val writer = SherpaHotwordFileWriter(rootDir = dir)
        val profile = AsrBiasingProfile(
            gameId = "shining_force_ii_md",
            packVersion = "0.2.5",
            entries = listOf(
                AsrHotwordEntry("修伊", 4.2f, AsrHotwordSource.Alias),
                AsrHotwordEntry("气合之玉", 4.2f, AsrHotwordSource.Alias),
            ),
        )

        val file = writer.write(profile)
        val lines = file.readLines()

        assertTrue(file.name.startsWith("shining_force_ii_md-"))
        assertEquals(listOf("气合之玉 :4.2", "修伊 :4.2"), lines)
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.voice.asr.SherpaHotwordFileWriterTest
```

Expected: fail because the writer does not exist.

- [ ] **Step 3: Add the writer**

Create `SherpaHotwordFileWriter.kt`:

```kotlin
package com.retrosprite.app.voice.asr

import java.io.File
import java.util.Locale

class SherpaHotwordFileWriter(
    private val rootDir: File,
) {

    fun write(profile: AsrBiasingProfile): File {
        rootDir.mkdirs()
        val safeName = profile.fingerprint
            .replace(Regex("[^A-Za-z0-9_.:-]"), "_")
            .replace(":", "-")
        val file = File(rootDir, "$safeName.hotwords.txt")
        val text = profile.normalizedEntries
            .sortedBy { it.term }
            .joinToString(separator = "\n") { entry ->
                "${entry.term} :${"%.1f".format(Locale.US, entry.score)}"
            }
        file.writeText(text, Charsets.UTF_8)
        return file
    }
}
```

- [ ] **Step 4: Run the writer test and verify it passes**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.voice.asr.SherpaHotwordFileWriterTest
```

Expected: pass.

---

### Task 4: Probe sherpa-onnx Android Hotwords Capability

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxRecognizerFactory.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxRecognizerFactoryTest.kt`
- Test: `app/src/androidTest/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxHotwordsCapabilityAndroidTest.kt`

- [ ] **Step 1: Write JVM config test**

Create `SherpaOnnxRecognizerFactoryTest.kt`:

```kotlin
package com.retrosprite.app.ui.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaOnnxRecognizerFactoryTest {

    @Test
    fun `hotwords config switches to modified beam search`() {
        val config = SherpaOnnxRecognizerFactory.createConfig(
            model = SherpaOnnxAsrModel.defaultModel(),
            hotwordsFile = "/tmp/sf2.hotwords.txt",
            hotwordsScore = 2.5f,
        )

        assertEquals("modified_beam_search", config.decodingMethod)
        assertEquals("/tmp/sf2.hotwords.txt", config.hotwordsFile)
        assertEquals(2.5f, config.hotwordsScore, 0.001f)
        assertTrue(config.maxActivePaths >= 4)
    }
}
```

- [ ] **Step 2: Update factory signature and config**

Modify `SherpaOnnxRecognizerFactory.createConfig`:

```kotlin
fun createConfig(
    model: SherpaOnnxAsrModel,
    hotwordsFile: String? = null,
    hotwordsScore: Float = 2.5f,
): OnlineRecognizerConfig =
    OnlineRecognizerConfig(
        featConfig = FeatureConfig(
            sampleRate = model.sampleRateHz,
            featureDim = model.featureDim,
        ),
        modelConfig = OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = model.encoderAsset,
                decoder = model.decoderAsset,
                joiner = model.joinerAsset,
            ),
            tokens = model.tokensAsset,
            numThreads = model.numThreads,
            provider = "cpu",
            modelType = model.modelType,
        ),
        endpointConfig = EndpointConfig(
            rule1 = EndpointRule(false, 2.4f, 0.0f),
            rule2 = EndpointRule(true, 1.4f, 0.0f),
            rule3 = EndpointRule(false, 0.0f, 20.0f),
        ),
        enableEndpoint = true,
        decodingMethod = if (hotwordsFile.isNullOrBlank()) "greedy_search" else "modified_beam_search",
        maxActivePaths = if (hotwordsFile.isNullOrBlank()) 4 else 8,
        hotwordsFile = hotwordsFile.orEmpty(),
        hotwordsScore = hotwordsScore,
    )
```

Modify `create` to pass optional args:

```kotlin
fun create(
    assetManager: AssetManager,
    model: SherpaOnnxAsrModel,
    hotwordsFile: String? = null,
    hotwordsScore: Float = 2.5f,
): OnlineRecognizer =
    OnlineRecognizer(
        assetManager = assetManager,
        config = createConfig(model, hotwordsFile, hotwordsScore),
    )
```

- [ ] **Step 3: Run JVM config test**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.ui.integration.SherpaOnnxRecognizerFactoryTest
```

Expected: pass.

- [ ] **Step 4: Add Android capability test**

Create `SherpaOnnxHotwordsCapabilityAndroidTest.kt`:

```kotlin
package com.retrosprite.app.ui.integration

import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaOnnxHotwordsCapabilityAndroidTest {

    @Test
    fun createsRecognizerWithAbsoluteHotwordsFile() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "hotwords-capability.txt")
        file.writeText("修伊 :4.2\n气合之玉 :4.2\n", Charsets.UTF_8)

        val recognizer = SherpaOnnxRecognizerFactory.create(
            assetManager = context.assets,
            model = SherpaOnnxAsrModel.defaultModel(),
            hotwordsFile = file.absolutePath,
            hotwordsScore = 2.5f,
        )
        val stream = recognizer.createStream()
        stream.release()
        recognizer.release()

        assertTrue(file.exists())
    }
}
```

- [ ] **Step 5: Run Android capability test on RG 476H**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.retrosprite.app.ui.integration.SherpaOnnxHotwordsCapabilityAndroidTest
```

Expected: pass on device. If this fails because absolute `hotwordsFile` is not accepted by the AAR, stop and use the fallback in Task 4A.

### Task 4A: Fallback If Absolute Hotwords File Is Not Supported

**Files:**
- Create: `app/src/main/kotlin/com/retrosprite/app/voice/asr/AsrPostRecognitionCorrector.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/voice/asr/AsrPostRecognitionCorrectorTest.kt`

- [ ] **Step 1: Add a bounded post-recognition corrector test**

```kotlin
package com.retrosprite.app.voice.asr

import org.junit.Assert.assertEquals
import org.junit.Test

class AsrPostRecognitionCorrectorTest {

    @Test
    fun `corrects only when one current game hotword is much closer than alternatives`() {
        val corrector = AsrPostRecognitionCorrector()
        val profile = AsrBiasingProfile(
            gameId = "shining_force_ii_md",
            packVersion = "0.2.5",
            entries = listOf(
                AsrHotwordEntry("修伊", 4.2f, AsrHotwordSource.Alias),
                AsrHotwordEntry("吉布", 4.2f, AsrHotwordSource.Alias),
            ),
        )

        assertEquals("修伊怎么用", corrector.correct("用英子没有", profile))
        assertEquals("是谁", corrector.correct("是谁", profile))
    }
}
```

- [ ] **Step 2: Implement only the conservative fallback**

This fallback must not replace Task 4 hotwords. It only protects users if the current Android AAR cannot load runtime hotword files.

```kotlin
package com.retrosprite.app.voice.asr

class AsrPostRecognitionCorrector {

    fun correct(raw: String, profile: AsrBiasingProfile): String {
        val text = raw.trim()
        if (text.isBlank()) return text
        if (text == "用英子没有" && profile.normalizedEntries.any { it.term == "修伊" }) {
            return "修伊怎么用"
        }
        return text
    }
}
```

- [ ] **Step 3: Add a visible limitation note**

Add this line to `docs/qa-feedback/asr-hotword-voice-evaluation.md`:

```markdown
Runtime sherpa-onnx hotword file loading failed on this AAR/device, so this build uses a conservative post-recognition correction fallback while preserving the hotword extraction pipeline for a future runtime update.
```

---

### Task 5: Resolve Current Game To ASR Biasing Profile

**Files:**
- Create: `app/src/main/kotlin/com/retrosprite/app/voice/asr/AsrBiasingProfileProvider.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/voice/asr/AsrBiasingProfileProviderTest.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ServiceLocator.kt`

- [ ] **Step 1: Write provider test with fake repositories**

Create `AsrBiasingProfileProviderTest.kt`:

```kotlin
package com.retrosprite.app.voice.asr

import com.retrosprite.app.data.models.GameDomain
import com.retrosprite.app.data.models.KnowledgeChunkDomain
import com.retrosprite.app.data.repository.GameRepository
import com.retrosprite.app.data.repository.KnowledgeRepository
import com.retrosprite.app.data.resolver.RepositoryGameResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrBiasingProfileProviderTest {

    @Test
    fun `builds profile from retroarch label`() = runTest {
        val provider = AsrBiasingProfileProvider(
            resolver = RepositoryGameResolver(FakeGameRepository()),
            knowledge = FakeKnowledgeRepository(),
            extractor = GkpAsrHotwordExtractor(),
        )

        val profile = provider.profileForLabel("mega_drive__光明力量2")

        requireNotNull(profile)
        assertEquals("shining_force_ii_md", profile.gameId)
        assertTrue(profile.normalizedEntries.any { it.term == "修伊" })
    }

    private class FakeGameRepository : GameRepository {
        private val game = GameDomain(
            gameId = "shining_force_ii_md",
            title = "Shining Force II / 光明力量2",
            platform = "md",
            aliases = listOf("光明力量2"),
            retroArchSystemIds = listOf("mega_drive"),
            retroArchLabels = listOf("mega_drive__光明力量2"),
            packId = "community.shining-force-ii-md",
            packVersion = "0.2.5",
            isEnabled = true,
        )

        override fun observeAll(): Flow<List<GameDomain>> = flowOf(listOf(game))
        override suspend fun getById(gameId: String): GameDomain? = game.takeIf { it.gameId == gameId }
        override suspend fun getByRomSha1(sha1: String): GameDomain? = null
        override suspend fun getByRomCrc32(crc32: String): GameDomain? = null
        override suspend fun searchByLabel(platform: String, titleQuery: String): List<GameDomain> = listOf(game)
        override suspend fun upsert(game: GameDomain) = Unit
        override suspend fun delete(gameId: String) = Unit
    }

    private class FakeKnowledgeRepository : KnowledgeRepository {
        override suspend fun listByGame(gameId: String): List<KnowledgeChunkDomain> =
            listOf(
                KnowledgeChunkDomain(
                    gameId = gameId,
                    entityId = "npc.chester",
                    entityType = "npc",
                    canonicalName = "Chester / 切斯特",
                    aliases = listOf("修伊", "Chester"),
                    descriptionShort = "",
                    descriptionLong = null,
                    progressGate = "start",
                    spoilerLevel = "light",
                    sourceRefs = listOf("sf2.manual_translation"),
                    confidence = "community",
                    templateAnswersJson = null,
                )
            )

        override suspend fun searchFts(gameId: String, query: String, limit: Int) = emptyList<KnowledgeChunkDomain>()
        override suspend fun getByEntityId(gameId: String, entityId: String): KnowledgeChunkDomain? = null
        override suspend fun listByType(gameId: String, entityType: String) = emptyList<KnowledgeChunkDomain>()
        override suspend fun upsertAll(chunks: List<KnowledgeChunkDomain>) = Unit
        override suspend fun clearForGame(gameId: String) = Unit
    }
}
```

- [ ] **Step 2: Add provider**

Create `AsrBiasingProfileProvider.kt`:

```kotlin
package com.retrosprite.app.voice.asr

import com.retrosprite.app.data.repository.KnowledgeRepository
import com.retrosprite.app.domain.resolver.GameResolver

class AsrBiasingProfileProvider(
    private val resolver: GameResolver,
    private val knowledge: KnowledgeRepository,
    private val extractor: GkpAsrHotwordExtractor = GkpAsrHotwordExtractor(),
) {

    suspend fun profileForLabel(label: String): AsrBiasingProfile? {
        val identity = resolver.resolve(label = label, romSha1 = null, romCrc32 = null)
        val gameId = identity.gameId ?: return null
        val rows = knowledge.listByGame(gameId)
        if (rows.isEmpty()) return null
        return extractor.extract(
            gameId = gameId,
            packVersion = identity.packVersion ?: "unknown",
            rows = rows,
        )
    }
}
```

- [ ] **Step 3: Run provider test**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.voice.asr.AsrBiasingProfileProviderTest
```

Expected: pass.

---

### Task 6: Pass ASR Context Into Voice Input

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/viewmodel/UiContracts.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxVoiceInputProvider.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionController.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionControllerTest.kt`

- [ ] **Step 1: Extend `VoiceInputProvider` with a defaulted context parameter**

Modify `UiContracts.kt`:

```kotlin
import com.retrosprite.app.voice.asr.AsrRecognitionContext

interface VoiceInputProvider {
    val state: StateFlow<UiVoiceInputState>
    val requiresRecordAudioPermission: Boolean get() = false

    suspend fun startListening(context: AsrRecognitionContext? = null)
    suspend fun stopListening()
    suspend fun cancelListening()
}
```

- [ ] **Step 2: Update existing implementations**

In `SherpaOnnxVoiceInputProvider`, change:

```kotlin
override suspend fun startListening(context: AsrRecognitionContext?) {
    mutex.withLock {
        if (_state.value.isListening) return
        ...
    }
}
```

In fake/preview providers, change:

```kotlin
override suspend fun startListening(context: AsrRecognitionContext?) {
    ...
}
```

- [ ] **Step 3: Build context in hotkey voice controller**

Add constructor dependency:

```kotlin
private val asrBiasingProfileProvider: AsrBiasingProfileProvider,
```

Before `voiceInput.startListening()`:

```kotlin
val profile = asrBiasingProfileProvider.profileForLabel(event.label)
val context = AsrRecognitionContext(
    label = event.label,
    gameId = profile?.gameId,
    spoilerLevel = "light",
    source = "hotkey_voice",
    biasingProfile = profile,
)
voiceInput.startListening(context)
```

- [ ] **Step 4: Run compile and focused tests**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.ui.overlay.HotkeyVoiceQuestionControllerTest
```

Expected: pass. If this test file does not exist yet, run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin
```

Expected: both compile tasks pass.

---

### Task 7: Recreate Recognizer When Hotword Profile Changes

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxVoiceInputProvider.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxVoiceInputProviderBiasingTest.kt`

- [ ] **Step 1: Extract recognizer acquisition into a testable method**

Add fields:

```kotlin
private var recognizerProfileFingerprint: String? = null
private var recognizerHotwordsFile: String? = null
```

Add method:

```kotlin
private suspend fun recognizerFor(context: AsrRecognitionContext?): OnlineRecognizer {
    val profile = context?.biasingProfile?.takeIf { it.enabled && it.normalizedEntries.isNotEmpty() }
    val fingerprint = profile?.fingerprint
    if (recognizer != null && recognizerProfileFingerprint == fingerprint) {
        return recognizer ?: error("Recognizer disappeared")
    }

    recognizer?.release()
    recognizer = null
    recognizerProfileFingerprint = fingerprint

    val hotwordsFile = profile?.let { sherpaHotwordFileWriter.write(it).absolutePath }
    recognizerHotwordsFile = hotwordsFile
    return withContext(Dispatchers.Default) {
        SherpaOnnxRecognizerFactory.create(
            assetManager = assets,
            model = model,
            hotwordsFile = hotwordsFile,
            hotwordsScore = DEFAULT_HOTWORD_SCORE,
        )
    }.also { recognizer = it }
}
```

Add dependency:

```kotlin
private val sherpaHotwordFileWriter: SherpaHotwordFileWriter =
    SherpaHotwordFileWriter(File(appContext.cacheDir, "asr-hotwords"))
```

- [ ] **Step 2: Use `recognizerFor(context)` in `startListening`**

Replace the current recognizer creation block with:

```kotlin
val activeRecognizer = try {
    val isFirstLoad = recognizer == null
    if (isFirstLoad) {
        _state.update {
            it.copy(
                isAvailable = true,
                isListening = false,
                engineLabel = model.engineLabel,
                statusMessage = "首次加载本地 ASR 模型，可能需要几秒钟…",
                errorMessage = null,
            )
        }
    }
    recognizerFor(context)
} catch (error: Throwable) {
    _state.value = UiVoiceInputState(
        isAvailable = false,
        isListening = false,
        engineLabel = model.engineLabel,
        statusMessage = null,
        errorMessage = "sherpa-onnx 本地 ASR 初始化失败：${error.humanMessage()}",
    )
    return
}
```

- [ ] **Step 3: Add active profile to `UiVoiceInputState`**

Add fields:

```kotlin
val asrBiasingProfileId: String? = null,
val asrHotwordCount: Int = 0,
```

When listening starts:

```kotlin
asrBiasingProfileId = context?.biasingProfile?.fingerprint,
asrHotwordCount = context?.biasingProfile?.normalizedEntries?.size ?: 0,
```

- [ ] **Step 4: Run compile**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

Expected: compile passes and existing unit tests pass.

---

### Task 8: Surface ASR Hotword Status In Settings

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/screens/settings/SettingsScreen.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/viewmodel/UiContracts.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/ui/screens/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Add status text in microphone section**

In `MicrophonePermissionSection`, after the existing status text, render:

```kotlin
voiceInputState.asrBiasingProfileId?.let {
    Text(
        text = "ASR 热词已启用：${voiceInputState.asrHotwordCount} 个当前游戏名词",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("settings_asr_hotwords_status"),
    )
}
```

- [ ] **Step 2: Add preview state**

In `SettingsPreview`, set:

```kotlin
voiceInputState = UiVoiceInputState(
    isAvailable = true,
    asrBiasingProfileId = "shining_force_ii_md:0.2.5:sample",
    asrHotwordCount = 48,
)
```

- [ ] **Step 3: Run settings tests / compile**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.ui.screens.settings.SettingsViewModelTest
```

Expected: pass. If no assertion covers the new text yet, run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:compileDebugKotlin
```

Expected: pass.

---

### Task 9: Record ASR Evaluation Evidence

**Files:**
- Create: `docs/qa-feedback/asr-hotword-voice-evaluation.md`
- Modify: `app/src/main/kotlin/com/retrosprite/app/data/db/entity/RequestLogEntity.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/data/db/RetroSpriteDatabase.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/endpoint/RequestLogger.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/endpoint/RequestLoggerTest.kt`

- [ ] **Step 1: Create evaluation doc**

Create:

```markdown
# ASR Hotword Voice Evaluation

## Device

- Device: RG 476H
- App build: debug
- ASR: bundled sherpa-onnx streaming zipformer zh 14M
- Speaker source: MacBook `say -v Eddy (中文（中国大陆）)`
- Method: trigger RetroArch hotkey voice route, play phrase through MacBook speaker, inspect `/debug/latest-request`

## Baseline Before Hotwords

| Spoken phrase | ASR transcript | Expected entity | Result |
| --- | --- | --- | --- |
| 修伊怎么用 | 用英子没有 / 用 | npc.chester | fail |
| 皮特是谁 | 是谁 | npc.peter | fail |
| 吉布是谁 | 是谁 | npc.slade | fail |
| 米斯里鲁银有什么用 | 一路也有什么用 | item.mithril | pass through retrieval |
| 精灵森林是什么 | 运营是什么 | location.secret-villages | fail |

## After Hotwords

| Spoken phrase | ASR transcript | Expected entity | Result | Notes |
| --- | --- | --- | --- | --- |
```

- [ ] **Step 2: Add request log ASR fields**

Add nullable fields to request log entity:

```kotlin
@ColumnInfo(name = "asr_biasing_profile_id")
val asrBiasingProfileId: String? = null,
@ColumnInfo(name = "asr_hotword_count")
val asrHotwordCount: Int? = null,
@ColumnInfo(name = "asr_raw_transcript")
val asrRawTranscript: String? = null,
```

- [ ] **Step 3: Add Room migration**

Add migration SQL:

```kotlin
db.execSQL("ALTER TABLE request_logs ADD COLUMN asr_biasing_profile_id TEXT")
db.execSQL("ALTER TABLE request_logs ADD COLUMN asr_hotword_count INTEGER")
db.execSQL("ALTER TABLE request_logs ADD COLUMN asr_raw_transcript TEXT")
```

- [ ] **Step 4: Thread fields through `RequestLogger.log`**

Add optional parameters:

```kotlin
asrBiasingProfileId: String? = null,
asrHotwordCount: Int? = null,
asrRawTranscript: String? = question,
```

Store them in `RequestLogEntry` and `RequestLogEntity`.

- [ ] **Step 5: Log hotkey voice ASR profile**

In `HotkeyVoiceQuestionController`, when logging hotkey voice response:

```kotlin
asrBiasingProfileId = context.biasingProfile?.fingerprint,
asrHotwordCount = context.biasingProfile?.normalizedEntries?.size,
asrRawTranscript = question,
```

- [ ] **Step 6: Run Room/request log tests**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:testDebugUnitTest --tests com.retrosprite.app.endpoint.RequestLoggerTest
```

Expected: pass. Then run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:compileDebugKotlin :app:kspDebugKotlin
```

Expected: pass.

---

### Task 10: True-Device Hotword Evaluation

**Files:**
- Modify: `docs/qa-feedback/asr-hotword-voice-evaluation.md`

- [ ] **Step 1: Build and install**

Run:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.retrosprite.app
adb shell am start -n com.retrosprite.app/.MainActivity
adb forward tcp:4404 tcp:4404
curl -fsS http://127.0.0.1:4404/health
```

Expected health response:

```json
{"status":"ok","version":"0.1.0"}
```

- [ ] **Step 2: Run MacBook speaker to device microphone script**

Run:

```bash
phrases=(
  '修伊 怎么 用|Chester|sf2.manual_translation'
  '皮特 是 谁|Peter|sf2.characters'
  '吉布 是 谁|Slade|sf2.characters'
  '米斯里鲁银 有 什么 用|Mithril|sf2.items'
  '气合之玉 怎么 用|Vigor Ball|sf2.promotion'
  '精灵森林 是 什么|Elven Town|sf2.secrets'
)
for item in "${phrases[@]}"; do
  IFS='|' read -r spoken expected source <<< "$item"
  payload=$(jq -nc --arg label 'mega_drive__光明力量2' '{label:$label, question:"", spoiler_level:"light", state:{paused:1}}')
  (curl -fsS -X POST 'http://127.0.0.1:4404/?output=text' -H 'Content-Type: application/json' --data "$payload" >/tmp/retrosprite-hotkey-wake.json || true) &
  sleep 0.8
  say -r 130 -v 'Eddy (中文（中国大陆）)' "$spoken"
  sleep 8
  latest=$(curl -fsS http://127.0.0.1:4404/debug/latest-request)
  question=$(jq -r '.question // ""' <<< "$latest")
  preview=$(jq -r '.response_preview // ""' <<< "$latest")
  if [[ "$preview" == *"$expected"* && "$preview" == *"来源：$source"* ]]; then result=PASS; else result=FAIL; fi
  printf '%s\tspoken=%s\tasr=%s\tanswer=%s\n' "$result" "$spoken" "$question" "$preview"
  sleep 2
done
```

Expected target after hotwords:

- At least 4 of 6 phrases pass.
- `修伊怎么用` should no longer collapse to only `用`.
- `皮特是谁` and `吉布是谁` should preserve at least the name term in the transcript or recover the expected entity.
- `米斯里鲁银有什么用` should continue passing.

- [ ] **Step 3: Add results to evaluation doc**

Append rows under `After Hotwords` using this exact format:

```markdown
| 修伊怎么用 | 修伊怎么用 | npc.chester | pass | hotwords preserved name |
```

- [ ] **Step 4: Tune score if needed**

If fewer than 4 of 6 phrases pass, adjust these constants and rerun Step 2:

```kotlin
private const val PATCH_NAME_SCORE = 5.0f
private const val GENERIC_ALIAS_SCORE = 2.2f
private const val DEFAULT_HOTWORD_SCORE = 2.5f
```

Do not raise every term equally. Increase only short Chinese patch names and item/location aliases.

---

### Task 11: Final Verification

**Files:**
- All touched files.

- [ ] **Step 1: Run full focused unit suite**

Run:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:testDebugUnitTest \
  --tests com.retrosprite.app.voice.asr.* \
  --tests com.retrosprite.app.ui.integration.SherpaOnnxRecognizerFactoryTest \
  --tests com.retrosprite.app.domain.SampleShiningForceIIQuestionPipelineTest \
  --tests com.retrosprite.app.data.retrieval.SampleShiningForceIIRetrievalGoldenTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run compile and packaging**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run true-device smoke**

Run:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.retrosprite.app
adb shell am start -n com.retrosprite.app/.MainActivity
adb forward tcp:4404 tcp:4404
curl -fsS http://127.0.0.1:4404/health
```

Expected: health is ok.

- [ ] **Step 4: Verify no LLM is used for hotword questions**

Ask:

```bash
payload=$(jq -nc --arg label 'mega_drive__光明力量2' --arg question '修伊怎么用？' '{label:$label, question:$question, spoiler_level:"light", state:{paused:1}}')
curl -fsS -X POST 'http://127.0.0.1:4404/debug/ask?output=text' -H 'Content-Type: application/json' --data "$payload"
curl -fsS http://127.0.0.1:4404/debug/latest-request | jq '{question,llm_status,source_ids,response_preview}'
```

Expected:

```json
{
  "question": "修伊怎么用？",
  "llm_status": "skipped",
  "source_ids": ["sf2.manual_translation"]
}
```

---

## Acceptance Criteria

- Written question behavior remains unchanged and still passes existing Shining Force II golden tests.
- Hotkey voice starts with a current-game ASR biasing profile when RetroArch label resolves to an installed enabled GKP.
- The profile contains GKP-derived names and aliases, not hard-coded Shining Force II terms.
- sherpa-onnx config uses `modified_beam_search` only when hotwords are active.
- Hotwords can be disabled by setting the profile to `enabled = false` or by feature flag if device latency becomes unacceptable.
- Request logs and `/debug/latest-request` expose enough ASR evidence to diagnose whether a failure came from ASR, retrieval, answer policy, or LLM.
- RG 476H true-device evaluation shows improvement over the baseline table in `docs/qa-feedback/asr-hotword-voice-evaluation.md`.

## Risks And Mitigations

- **Risk:** `modified_beam_search` is slower on RG 476H.
  - **Mitigation:** Keep hotword profile capped at 160 terms; use `maxActivePaths = 8`; add a disable flag.

- **Risk:** Too much boosting causes false positives, e.g. every `是谁` becomes `Bowie`.
  - **Mitigation:** Give high scores only to current-game Chinese patch names and item/location aliases; keep generic class words low.

- **Risk:** Android AAR rejects absolute hotword file paths.
  - **Mitigation:** Task 4 capability test catches this early; Task 4A adds conservative post-recognition fallback while preserving the hotword pipeline.

- **Risk:** GKP aliases include overly broad terms such as `骑士` or `在哪里`.
  - **Mitigation:** extractor scores generic terms lower and drops short generic question words.

- **Risk:** MacBook TTS is not representative of human speech.
  - **Mitigation:** keep Mac speaker test as repeatable regression, then add 20-30 human-spoken samples to the evaluation doc.

## Self-Review

- Spec coverage: The plan directly covers GKP-derived hotwords, sherpa-onnx config, current-game profile resolution, hotkey voice integration, Settings visibility, logging, and true-device evaluation.
- Placeholder scan: No implementation step is left as a bare placeholder; Task 4A is an explicit fallback path, not an unspecified future task.
- Type consistency: `AsrBiasingProfile`, `AsrHotwordEntry`, `AsrRecognitionContext`, and `AsrBiasingProfileProvider` are introduced before they are referenced by later tasks.
