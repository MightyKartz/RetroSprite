# ASR/TTS Model Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep RetroSprite's current Chinese sherpa-onnx ASR path simple and stable, then add optional user-managed English ASR and sherpa-onnx TTS models without bloating the base APK.

**Architecture:** Treat voice models as data packages stored in app-private storage. The built-in Chinese ASR model remains the immutable default and fallback. Optional ASR/TTS models are installed, selected, tested, and deleted through Settings only after the sherpa-onnx Android runtime exposes the required file-backed ASR and OfflineTts APIs.

**Tech Stack:** Kotlin, Jetpack Compose, DataStore Preferences, Android Storage Access Framework, sherpa-onnx Android JNI/AAR, Android `TextToSpeech`, `StateFlow`, JVM tests, Compose instrumentation smoke tests, RG 476H true-device validation.

---

## Product Decisions

- Keep the bundled `sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23` ASR model as the default voice input path.
- Do not add English ASR to the base APK.
- Do not delete the built-in Chinese ASR model.
- Let users install optional English ASR models into app-private storage.
- Let users switch ASR models only from Settings, never during an active hotkey recording session.
- Add answer-language policy before English ASR becomes user-visible: `中文`, `English`, and `跟随语音识别模型`.
- Keep Android `TextToSpeech` as the default speech output provider until sherpa-onnx TTS runtime support is validated.
- Add sherpa-onnx TTS model management after ASR model management is stable.
- Do not treat ASR models as TTS models. ASR and TTS have separate model lists, providers, tests, and deletion rules.
- Model download can require network. Model recognition and synthesis must work offline after installation.

## Current Runtime Constraints

- `SherpaOnnxVoiceInputProvider` currently accepts one `SherpaOnnxAsrModel` at construction time.
- `SherpaOnnxRecognizerFactory` currently creates `OnlineRecognizer(assetManager, config)`, so the current path is asset-backed.
- The local `lib-sherpa-onnx-6.25.21.aar` exposes ASR classes but does not expose public `OfflineTts` Kotlin classes.
- The current `OnlineRecognizer` Java surface exposes an `AssetManager` constructor; file-backed model loading needs a public runtime API or a maintained wrapper before user-installed ASR models can work.
- `QueryPipelineResponseGenerator.defaultLanguage` is currently `"zh"`.
- `AnswerComposer.systemPromptFor()` currently always returns the Simplified Chinese prompt.
- `AndroidSpeechOutputProvider` currently uses Android system `TextToSpeech` and `Locale.getDefault()`.

## File Structure

Create:

- `app/src/main/kotlin/com/retrosprite/app/voice/VoiceModelContracts.kt`
  - Shared model ids, language enum, kind enum, install state, package manifest model, and built-in model descriptors.

- `app/src/main/kotlin/com/retrosprite/app/voice/VoiceModelPackageValidator.kt`
  - Validates imported ASR/TTS model package manifests and rejects unsafe filenames, unsupported kinds, unsupported languages, and oversized packages.

- `app/src/main/kotlin/com/retrosprite/app/voice/AndroidVoiceModelStore.kt`
  - Lists built-in and installed models, copies user-selected packages into app-private storage, stores active ASR/TTS model ids, and deletes non-built-in models.

- `app/src/main/kotlin/com/retrosprite/app/voice/SpeechLanguagePolicy.kt`
  - Resolves answer language from Settings and active ASR model.

- `app/src/main/kotlin/com/retrosprite/app/ui/integration/SelectableSherpaOnnxVoiceInputProvider.kt`
  - Wraps sherpa-onnx ASR with active-model selection and recognizer release on model changes.

- `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxTtsSpeechOutputProvider.kt`
  - Added only after Task 9 proves `OfflineTts` is callable. Provides file-backed local TTS using the same `SpeechOutputProvider` interface.

- `docs/VOICE_MODEL_PACKAGES.md`
  - Documents the model package manifest, storage policy, install/delete behavior, and true-device validation checklist.

Modify:

- `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxAsrModel.kt`
  - Add `id`, `language`, `origin`, and file-backed path support.

- `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxRecognizerFactory.kt`
  - Add a file-backed creation path once runtime support is available.

- `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxVoiceInputProvider.kt`
  - Keep the current built-in model path stable; share recognition code with the selectable provider.

- `app/src/main/kotlin/com/retrosprite/app/ui/integration/AndroidSpeechOutputProvider.kt`
  - Keep as default TTS fallback and expose clearer engine/readiness state when Settings adds TTS model testing.

- `app/src/main/kotlin/com/retrosprite/app/ui/viewmodel/UiContracts.kt`
  - Add UI contracts for voice model lists, install states, active ASR/TTS model ids, and answer language.

- `app/src/main/kotlin/com/retrosprite/app/ui/viewmodel/UiDependencies.kt`
  - Add `voiceModelStore` provider after contracts exist.

- `app/src/main/kotlin/com/retrosprite/app/ui/settings/UiSettingsStore.kt`
  - Persist answer language, active ASR model id, and active TTS model id.

- `app/src/main/kotlin/com/retrosprite/app/ui/screens/settings/SettingsViewModel.kt`
  - Add Settings actions for answer language, ASR model selection/install/delete/test, and TTS model selection/install/delete/test.

- `app/src/main/kotlin/com/retrosprite/app/ui/screens/settings/SettingsScreen.kt`
  - Add progressive sections: current Chinese ASR status, optional ASR model management, answer language, and local TTS model management.

- `app/src/main/kotlin/com/retrosprite/app/ServiceLocator.kt`
  - Wire `AndroidVoiceModelStore`, answer language policy, selectable ASR provider, and later TTS provider.

- `app/src/main/kotlin/com/retrosprite/app/endpoint/QueryPipelineResponseGenerator.kt`
  - Replace fixed `"zh"` with answer-language provider.

- `app/src/main/kotlin/com/retrosprite/app/domain/policy/AnswerComposer.kt`
  - Add English system prompt and English fallback strings.

Test:

- `app/src/test/kotlin/com/retrosprite/app/voice/VoiceModelContractsTest.kt`
- `app/src/test/kotlin/com/retrosprite/app/voice/VoiceModelPackageValidatorTest.kt`
- `app/src/test/kotlin/com/retrosprite/app/voice/SpeechLanguagePolicyTest.kt`
- `app/src/test/kotlin/com/retrosprite/app/ui/screens/settings/SettingsViewModelTest.kt`
- `app/src/test/kotlin/com/retrosprite/app/endpoint/QueryPipelineResponseGeneratorTest.kt`
- `app/src/test/kotlin/com/retrosprite/app/domain/policy/AnswerComposerTest.kt`
- `app/src/androidTest/kotlin/com/retrosprite/app/ui/RetroSpriteAppSmokeTest.kt`
- `app/src/androidTest/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxRuntimeCapabilityAndroidTest.kt`

---

### Task 1: Lock Built-In Chinese ASR As The Stable Default

**Files:**
- Create: `app/src/main/kotlin/com/retrosprite/app/voice/VoiceModelContracts.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxAsrModel.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/voice/VoiceModelContractsTest.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxAsrModelTest.kt`

- [ ] **Step 1: Add the voice model contracts test**

Create `VoiceModelContractsTest.kt`:

```kotlin
package com.retrosprite.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceModelContractsTest {

    @Test
    fun `built in chinese asr is default and cannot be deleted`() {
        val model = BuiltInVoiceModels.ChineseAsr

        assertEquals("asr.zh.builtin.streaming_zipformer_14m", model.id)
        assertEquals(VoiceModelKind.Asr, model.kind)
        assertEquals(VoiceModelLanguage.ZhCn, model.language)
        assertEquals(VoiceModelOrigin.BuiltInAsset, model.origin)
        assertTrue(model.isInstalled)
        assertTrue(model.isDefault)
        assertFalse(model.isDeleteAllowed)
    }
}
```

- [ ] **Step 2: Run the new contract test and verify it fails**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest --tests com.retrosprite.app.voice.VoiceModelContractsTest
```

Expected: fail because `VoiceModelContracts.kt` does not exist.

- [ ] **Step 3: Add `VoiceModelContracts.kt`**

Create:

```kotlin
package com.retrosprite.app.voice

enum class VoiceModelKind(val id: String, val displayName: String) {
    Asr("asr", "语音识别"),
    Tts("tts", "语音输出"),
}

enum class VoiceModelLanguage(val id: String, val displayName: String) {
    ZhCn("zh-CN", "中文"),
    EnUs("en-US", "English"),
}

enum class VoiceModelOrigin {
    BuiltInAsset,
    UserInstalled,
}

data class VoiceModelDescriptor(
    val id: String,
    val kind: VoiceModelKind,
    val language: VoiceModelLanguage,
    val displayName: String,
    val engine: String,
    val modelType: String,
    val origin: VoiceModelOrigin,
    val isInstalled: Boolean,
    val isDefault: Boolean,
    val isDeleteAllowed: Boolean,
    val estimatedBytes: Long? = null,
)

object BuiltInVoiceModels {
    const val CHINESE_ASR_ID = "asr.zh.builtin.streaming_zipformer_14m"

    val ChineseAsr = VoiceModelDescriptor(
        id = CHINESE_ASR_ID,
        kind = VoiceModelKind.Asr,
        language = VoiceModelLanguage.ZhCn,
        displayName = "sherpa-onnx 中文 ASR",
        engine = "sherpa-onnx",
        modelType = "zipformer",
        origin = VoiceModelOrigin.BuiltInAsset,
        isInstalled = true,
        isDefault = true,
        isDeleteAllowed = false,
        estimatedBytes = 30L * 1024L * 1024L,
    )
}
```

- [ ] **Step 4: Extend `SherpaOnnxAsrModel` without changing current assets**

Modify `SherpaOnnxAsrModel` constructor fields:

```kotlin
data class SherpaOnnxAsrModel(
    val id: String,
    val languageTag: String,
    val assetDir: String,
    val encoderAsset: String,
    val decoderAsset: String,
    val joinerAsset: String,
    val tokensAsset: String,
    val modelType: String,
    val engineLabel: String,
    val sampleRateHz: Int = 16_000,
    val featureDim: Int = 80,
    val numThreads: Int = 2,
)
```

Update `defaultModel()` to set:

```kotlin
id = BuiltInVoiceModels.CHINESE_ASR_ID,
languageTag = VoiceModelLanguage.ZhCn.id,
```

Keep the existing `assetDir`, `encoderAsset`, `decoderAsset`, `joinerAsset`, and `tokensAsset` values unchanged.

- [ ] **Step 5: Update `SherpaOnnxAsrModelTest`**

Add assertions:

```kotlin
assertEquals("asr.zh.builtin.streaming_zipformer_14m", model.id)
assertEquals("zh-CN", model.languageTag)
```

- [ ] **Step 6: Run ASR contract tests**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest \
  --tests com.retrosprite.app.voice.VoiceModelContractsTest \
  --tests com.retrosprite.app.ui.integration.SherpaOnnxAsrModelTest
```

Expected: both tests pass.

- [ ] **Step 7: Commit the default model contract**

```bash
git add \
  app/src/main/kotlin/com/retrosprite/app/voice/VoiceModelContracts.kt \
  app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxAsrModel.kt \
  app/src/test/kotlin/com/retrosprite/app/voice/VoiceModelContractsTest.kt \
  app/src/test/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxAsrModelTest.kt
git commit -m "feat: define voice model contracts"
```

---

### Task 2: Add Answer Language Policy Before English ASR UI

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/viewmodel/UiContracts.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/settings/UiSettingsStore.kt`
- Create: `app/src/main/kotlin/com/retrosprite/app/voice/SpeechLanguagePolicy.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/voice/SpeechLanguagePolicyTest.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/ui/screens/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Add policy tests**

Create `SpeechLanguagePolicyTest.kt`:

```kotlin
package com.retrosprite.app.voice

import com.retrosprite.app.ui.viewmodel.UiAnswerLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechLanguagePolicyTest {

    @Test
    fun `explicit chinese answer language returns zh`() {
        assertEquals(
            "zh",
            SpeechLanguagePolicy.resolveAnswerLanguage(
                setting = UiAnswerLanguage.Chinese,
                activeAsrLanguage = VoiceModelLanguage.EnUs,
            )
        )
    }

    @Test
    fun `explicit english answer language returns en`() {
        assertEquals(
            "en",
            SpeechLanguagePolicy.resolveAnswerLanguage(
                setting = UiAnswerLanguage.English,
                activeAsrLanguage = VoiceModelLanguage.ZhCn,
            )
        )
    }

    @Test
    fun `follow asr maps english asr to en`() {
        assertEquals(
            "en",
            SpeechLanguagePolicy.resolveAnswerLanguage(
                setting = UiAnswerLanguage.FollowAsr,
                activeAsrLanguage = VoiceModelLanguage.EnUs,
            )
        )
    }

    @Test
    fun `follow asr maps chinese asr to zh`() {
        assertEquals(
            "zh",
            SpeechLanguagePolicy.resolveAnswerLanguage(
                setting = UiAnswerLanguage.FollowAsr,
                activeAsrLanguage = VoiceModelLanguage.ZhCn,
            )
        )
    }
}
```

- [ ] **Step 2: Run the policy test and verify it fails**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest --tests com.retrosprite.app.voice.SpeechLanguagePolicyTest
```

Expected: fail because `UiAnswerLanguage` and `SpeechLanguagePolicy` do not exist.

- [ ] **Step 3: Add `UiAnswerLanguage` and settings fields**

Add to `UiContracts.kt` near `UiSpoilerLevel`:

```kotlin
enum class UiAnswerLanguage(
    val id: String,
    val displayName: String,
) {
    Chinese("zh", "中文"),
    English("en", "English"),
    FollowAsr("follow_asr", "跟随语音识别模型"),
}
```

Add fields to `UiSettings`:

```kotlin
val answerLanguage: UiAnswerLanguage = UiAnswerLanguage.Chinese,
val activeAsrModelId: String = BuiltInVoiceModels.CHINESE_ASR_ID,
val activeTtsModelId: String = "tts.android.system",
```

Add methods to `SettingsStore`:

```kotlin
suspend fun updateAnswerLanguage(language: UiAnswerLanguage)
suspend fun updateActiveAsrModel(modelId: String)
suspend fun updateActiveTtsModel(modelId: String)
```

- [ ] **Step 4: Add `SpeechLanguagePolicy.kt`**

```kotlin
package com.retrosprite.app.voice

import com.retrosprite.app.ui.viewmodel.UiAnswerLanguage

object SpeechLanguagePolicy {
    fun resolveAnswerLanguage(
        setting: UiAnswerLanguage,
        activeAsrLanguage: VoiceModelLanguage,
    ): String =
        when (setting) {
            UiAnswerLanguage.Chinese -> "zh"
            UiAnswerLanguage.English -> "en"
            UiAnswerLanguage.FollowAsr -> when (activeAsrLanguage) {
                VoiceModelLanguage.ZhCn -> "zh"
                VoiceModelLanguage.EnUs -> "en"
            }
        }
}
```

- [ ] **Step 5: Persist answer/model settings**

In `UiSettingsStore`, add keys:

```kotlin
val ANSWER_LANGUAGE = stringPreferencesKey("answer_language")
val ACTIVE_ASR_MODEL_ID = stringPreferencesKey("active_asr_model_id")
val ACTIVE_TTS_MODEL_ID = stringPreferencesKey("active_tts_model_id")
```

Map preferences into `UiSettings`:

```kotlin
answerLanguage = (prefs[Keys.ANSWER_LANGUAGE] ?: UiAnswerLanguage.Chinese.id).toAnswerLanguage(),
activeAsrModelId = prefs[Keys.ACTIVE_ASR_MODEL_ID] ?: BuiltInVoiceModels.CHINESE_ASR_ID,
activeTtsModelId = prefs[Keys.ACTIVE_TTS_MODEL_ID] ?: "tts.android.system",
```

Add methods:

```kotlin
override suspend fun updateAnswerLanguage(language: UiAnswerLanguage) {
    context.uiSettingsDataStore.edit { it[Keys.ANSWER_LANGUAGE] = language.id }
}

override suspend fun updateActiveAsrModel(modelId: String) {
    context.uiSettingsDataStore.edit { it[Keys.ACTIVE_ASR_MODEL_ID] = modelId }
}

override suspend fun updateActiveTtsModel(modelId: String) {
    context.uiSettingsDataStore.edit { it[Keys.ACTIVE_TTS_MODEL_ID] = modelId }
}
```

Add mapper:

```kotlin
private fun String.toAnswerLanguage(): UiAnswerLanguage =
    UiAnswerLanguage.values().firstOrNull { it.id == this } ?: UiAnswerLanguage.Chinese
```

- [ ] **Step 6: Add SettingsViewModel actions**

Add methods:

```kotlin
fun applyAnswerLanguage(language: UiAnswerLanguage) {
    viewModelScope.launch { store.updateAnswerLanguage(language) }
}

fun applyActiveAsrModel(modelId: String) {
    viewModelScope.launch { store.updateActiveAsrModel(modelId) }
}

fun applyActiveTtsModel(modelId: String) {
    viewModelScope.launch { store.updateActiveTtsModel(modelId) }
}
```

- [ ] **Step 7: Update SettingsViewModel fake store tests**

Add a test:

```kotlin
@Test
fun `applyAnswerLanguage persists answer language`() = runTest(mainDispatcherRule.dispatcher) {
    val store = FakeSettingsStore()
    val viewModel = SettingsViewModel(
        store = store,
        endpoint = FakeEndpointStatusProvider(),
        llmConfigTest = FakeLlmConfigTestProvider(),
        overlayPermission = FakeOverlayPermissionProvider(UiOverlayPermissionState()),
        about = UiAboutInfo(),
    )

    viewModel.applyAnswerLanguage(UiAnswerLanguage.English)
    advanceUntilIdle()

    assertEquals(UiAnswerLanguage.English, store.current.answerLanguage)
}
```

Expose `current` from `FakeSettingsStore`:

```kotlin
val current: UiSettings get() = state.value
```

Implement new fake store methods:

```kotlin
override suspend fun updateAnswerLanguage(language: UiAnswerLanguage) {
    state.value = state.value.copy(answerLanguage = language)
}

override suspend fun updateActiveAsrModel(modelId: String) {
    state.value = state.value.copy(activeAsrModelId = modelId)
}

override suspend fun updateActiveTtsModel(modelId: String) {
    state.value = state.value.copy(activeTtsModelId = modelId)
}
```

- [ ] **Step 8: Run policy and Settings tests**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest \
  --tests com.retrosprite.app.voice.SpeechLanguagePolicyTest \
  --tests com.retrosprite.app.ui.screens.settings.SettingsViewModelTest
```

Expected: pass.

- [ ] **Step 9: Commit answer language foundation**

```bash
git add \
  app/src/main/kotlin/com/retrosprite/app/ui/viewmodel/UiContracts.kt \
  app/src/main/kotlin/com/retrosprite/app/ui/settings/UiSettingsStore.kt \
  app/src/main/kotlin/com/retrosprite/app/voice/SpeechLanguagePolicy.kt \
  app/src/test/kotlin/com/retrosprite/app/voice/SpeechLanguagePolicyTest.kt \
  app/src/test/kotlin/com/retrosprite/app/ui/screens/settings/SettingsViewModelTest.kt
git commit -m "feat: add answer language policy"
```

---

### Task 3: Make Response Generation Respect Answer Language

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/endpoint/QueryPipelineResponseGenerator.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/domain/policy/AnswerComposer.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/endpoint/QueryPipelineResponseGeneratorTest.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/domain/policy/AnswerComposerTest.kt`

- [ ] **Step 1: Add response generator language provider test**

Add to `QueryPipelineResponseGeneratorTest`:

```kotlin
@Test
fun `generator uses dynamic answer language provider`() = runTest {
    val pipeline = CapturingPipeline()
    val generator = QueryPipelineResponseGenerator(
        pipeline = pipeline,
        answerLanguageProvider = { "en" },
    )

    generator.generate(
        request = RetroArchRequest(label = "2048__", question = "How do tiles merge?"),
        outputMode = "text",
    )

    assertEquals("en", pipeline.lastLanguage)
}
```

- [ ] **Step 2: Change `QueryPipelineResponseGenerator` constructor**

Replace:

```kotlin
private val defaultLanguage: String = "zh",
```

with:

```kotlin
private val answerLanguageProvider: () -> String = { "zh" },
```

Replace:

```kotlin
language = defaultLanguage,
```

with:

```kotlin
language = answerLanguageProvider(),
```

- [ ] **Step 3: Add English composer tests**

Add to `AnswerComposerTest`:

```kotlin
@Test
fun `english context asks llm to answer in english`() = runTest {
    val llm = CapturingLlmAdapter("Short English answer.")
    val composer = AnswerComposer()
    val decision = AnswerDecision.ComposeWithLlm(
        evidence = listOf(evidence("sample.2048.rules", "Tiles with the same number merge.")),
        confidence = AnswerConfidence.Medium,
        spoilerLevel = SpoilerLevel.Light,
    )

    composer.composeDetailed(decision, ctx(language = "en"), llm)

    assertTrue(llm.lastRequest.systemPrompt.contains("Answer in English"))
}

@Test
fun `english refusal uses english text`() = runTest {
    val answer = AnswerComposer().composeDetailed(
        decision = AnswerDecision.Refuse,
        context = ctx(language = "en"),
        llm = FakeLlmAdapter(),
    )

    assertTrue(answer.text.startsWith("Sorry,"))
}
```

Update the local `ctx` helper to accept:

```kotlin
language: String = "zh",
```

and pass it into `SessionContext`.

- [ ] **Step 4: Add English prompt and fallback strings**

In `AnswerComposer`, add:

```kotlin
private const val SYSTEM_PROMPT_EN: String =
    "You are RetroSprite, an in-game Q&A companion for retro games. " +
        "Answer in English using only the provided evidence. Keep the answer to at most 3 sentences, avoid spoilers, and avoid unrelated chat."

private const val POLITE_REFUSAL_EN: String =
    "Sorry, I cannot answer that reliably yet. Try a more specific game question."

private const val NO_EVIDENCE_TEXT_EN: String =
    "I do not have enough evidence to answer this yet. Please add the version, location, or a more specific question."
```

Update `systemPromptFor`:

```kotlin
private fun systemPromptFor(context: SessionContext): String =
    when (context.language.lowercase()) {
        "en", "en-us", "en-gb" -> SYSTEM_PROMPT_EN
        else -> SYSTEM_PROMPT_ZH
    }
```

Update refusal and no-evidence paths with helper functions:

```kotlin
private fun politeRefusalFor(context: SessionContext): String =
    when (context.language.lowercase()) {
        "en", "en-us", "en-gb" -> POLITE_REFUSAL_EN
        else -> POLITE_REFUSAL_ZH
    }

private fun noEvidenceTextFor(context: SessionContext): String =
    when (context.language.lowercase()) {
        "en", "en-us", "en-gb" -> NO_EVIDENCE_TEXT_EN
        else -> NO_EVIDENCE_TEXT
    }
```

- [ ] **Step 5: Run language tests**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest \
  --tests com.retrosprite.app.endpoint.QueryPipelineResponseGeneratorTest \
  --tests com.retrosprite.app.domain.policy.AnswerComposerTest
```

Expected: pass. Existing Chinese tests must continue passing.

- [ ] **Step 6: Commit response language support**

```bash
git add \
  app/src/main/kotlin/com/retrosprite/app/endpoint/QueryPipelineResponseGenerator.kt \
  app/src/main/kotlin/com/retrosprite/app/domain/policy/AnswerComposer.kt \
  app/src/test/kotlin/com/retrosprite/app/endpoint/QueryPipelineResponseGeneratorTest.kt \
  app/src/test/kotlin/com/retrosprite/app/domain/policy/AnswerComposerTest.kt
git commit -m "feat: route answer language through pipeline"
```

---

### Task 4: Validate sherpa-onnx Runtime Capability Before User-Installed ASR

**Files:**
- Create: `app/src/androidTest/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxRuntimeCapabilityAndroidTest.kt`
- Modify: `docs/VOICE_MODEL_PACKAGES.md`

- [ ] **Step 1: Add runtime capability instrumentation test**

Create:

```kotlin
package com.retrosprite.app.ui.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SherpaOnnxRuntimeCapabilityAndroidTest {

    @Test
    fun onlineRecognizerRuntimeSurfaceIsDocumented() {
        val methods = com.k2fsa.sherpa.onnx.OnlineRecognizer::class.java
            .declaredMethods
            .map { it.name }
            .toSet()

        assertTrue(methods.contains("newFromAsset"))
        assertTrue(methods.contains("newFromFile"))
    }
}
```

- [ ] **Step 2: Run capability test on device or AVD**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.retrosprite.app.ui.integration.SherpaOnnxRuntimeCapabilityAndroidTest
```

Expected: pass if the runtime has both native entrypoints. This does not prove the current public Kotlin API can load file-backed models; it proves the native runtime contains the file-backed function that the next task must expose safely.

- [ ] **Step 3: Document the API gate**

Create `docs/VOICE_MODEL_PACKAGES.md` with:

```markdown
# Voice Model Packages

RetroSprite ships one built-in ASR model:

- `asr.zh.builtin.streaming_zipformer_14m`
- language: `zh-CN`
- origin: APK assets
- delete allowed: no

User-installed ASR and TTS models live under app-private storage:

- ASR: `files/voice-models/asr/{model_id}/`
- TTS: `files/voice-models/tts/{model_id}/`

Runtime rules:

- Recognition and synthesis must work offline after installation.
- Model packages are data only.
- A package may contain ONNX files, token files, lexicon files, JSON manifests, and dictionary data.
- A package may not contain APKs, shared libraries, executable files, scripts, ROMs, save states, or archives nested inside the package.
- The built-in Chinese ASR model cannot be deleted.
- User-installed ASR and TTS models can be deleted from Settings.
- User-installed ASR cannot be enabled until the sherpa-onnx Android runtime exposes a public file-backed recognizer API.
- User-installed TTS cannot be enabled until the sherpa-onnx Android runtime exposes public OfflineTts APIs.
```

- [ ] **Step 4: Commit runtime gate documentation**

```bash
git add \
  app/src/androidTest/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxRuntimeCapabilityAndroidTest.kt \
  docs/VOICE_MODEL_PACKAGES.md
git commit -m "test: document sherpa runtime model gates"
```

---

### Task 5: Add Voice Model Package Validation

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/voice/VoiceModelContracts.kt`
- Create: `app/src/main/kotlin/com/retrosprite/app/voice/VoiceModelPackageValidator.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/voice/VoiceModelPackageValidatorTest.kt`

- [ ] **Step 1: Add validator tests**

Create `VoiceModelPackageValidatorTest.kt`:

```kotlin
package com.retrosprite.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceModelPackageValidatorTest {

    @Test
    fun `valid english asr package passes`() {
        val manifest = VoiceModelPackageManifest(
            id = "asr.en.user.zipformer_small",
            kind = VoiceModelKind.Asr,
            language = VoiceModelLanguage.EnUs,
            displayName = "sherpa-onnx English ASR",
            engine = "sherpa-onnx",
            modelType = "zipformer",
            files = VoiceModelPackageFiles(
                encoder = "encoder.onnx",
                decoder = "decoder.onnx",
                joiner = "joiner.onnx",
                tokens = "tokens.txt",
            ),
            totalBytes = 96L * 1024L * 1024L,
        )

        assertEquals(emptyList<String>(), VoiceModelPackageValidator.validate(manifest))
    }

    @Test
    fun `package rejects path traversal`() {
        val manifest = validAsrManifest().copy(
            files = validAsrManifest().files.copy(encoder = "../encoder.onnx")
        )

        val errors = VoiceModelPackageValidator.validate(manifest)

        assertTrue(errors.contains("文件名不能包含路径穿越：../encoder.onnx"))
    }

    @Test
    fun `asr package rejects excessive size`() {
        val manifest = validAsrManifest().copy(totalBytes = 301L * 1024L * 1024L)

        val errors = VoiceModelPackageValidator.validate(manifest)

        assertTrue(errors.contains("ASR 模型包不能超过 300 MB"))
    }

    @Test
    fun `tts package rejects excessive size`() {
        val manifest = validTtsManifest().copy(totalBytes = 601L * 1024L * 1024L)

        val errors = VoiceModelPackageValidator.validate(manifest)

        assertTrue(errors.contains("TTS 模型包不能超过 600 MB"))
    }

    private fun validAsrManifest() = VoiceModelPackageManifest(
        id = "asr.en.user.zipformer_small",
        kind = VoiceModelKind.Asr,
        language = VoiceModelLanguage.EnUs,
        displayName = "sherpa-onnx English ASR",
        engine = "sherpa-onnx",
        modelType = "zipformer",
        files = VoiceModelPackageFiles(
            encoder = "encoder.onnx",
            decoder = "decoder.onnx",
            joiner = "joiner.onnx",
            tokens = "tokens.txt",
        ),
        totalBytes = 96L * 1024L * 1024L,
    )

    private fun validTtsManifest() = VoiceModelPackageManifest(
        id = "tts.zh.user.vits_zh_ll",
        kind = VoiceModelKind.Tts,
        language = VoiceModelLanguage.ZhCn,
        displayName = "sherpa-onnx 中文 TTS",
        engine = "sherpa-onnx",
        modelType = "vits",
        files = VoiceModelPackageFiles(
            model = "model.onnx",
            tokens = "tokens.txt",
            lexicon = "lexicon.txt",
        ),
        totalBytes = 130L * 1024L * 1024L,
    )
}
```

- [ ] **Step 2: Extend contracts with package manifest data classes**

Add to `VoiceModelContracts.kt`:

```kotlin
data class VoiceModelPackageManifest(
    val id: String,
    val kind: VoiceModelKind,
    val language: VoiceModelLanguage,
    val displayName: String,
    val engine: String,
    val modelType: String,
    val files: VoiceModelPackageFiles,
    val totalBytes: Long,
)

data class VoiceModelPackageFiles(
    val encoder: String? = null,
    val decoder: String? = null,
    val joiner: String? = null,
    val model: String? = null,
    val acousticModel: String? = null,
    val vocoder: String? = null,
    val tokens: String? = null,
    val lexicon: String? = null,
    val dataDir: String? = null,
)
```

- [ ] **Step 3: Implement validator**

Create `VoiceModelPackageValidator.kt`:

```kotlin
package com.retrosprite.app.voice

object VoiceModelPackageValidator {
    private const val MAX_ASR_BYTES = 300L * 1024L * 1024L
    private const val MAX_TTS_BYTES = 600L * 1024L * 1024L
    private val SAFE_FILE = Regex("^[A-Za-z0-9._/-]+$")

    fun validate(manifest: VoiceModelPackageManifest): List<String> {
        val errors = mutableListOf<String>()
        if (!manifest.id.startsWith("${manifest.kind.id}.")) {
            errors += "模型 id 必须以 ${manifest.kind.id}. 开头"
        }
        if (manifest.engine != "sherpa-onnx") {
            errors += "当前仅支持 sherpa-onnx 模型"
        }
        when (manifest.kind) {
            VoiceModelKind.Asr -> validateAsr(manifest, errors)
            VoiceModelKind.Tts -> validateTts(manifest, errors)
        }
        manifest.files.allNames().forEach { name ->
            if (!SAFE_FILE.matches(name)) errors += "文件名包含非法字符：$name"
            if (name.startsWith("/") || name.contains("..")) {
                errors += "文件名不能包含路径穿越：$name"
            }
        }
        return errors.distinct()
    }

    private fun validateAsr(
        manifest: VoiceModelPackageManifest,
        errors: MutableList<String>,
    ) {
        if (manifest.totalBytes > MAX_ASR_BYTES) errors += "ASR 模型包不能超过 300 MB"
        if (manifest.files.encoder.isNullOrBlank()) errors += "ASR 模型包缺少 encoder"
        if (manifest.files.decoder.isNullOrBlank()) errors += "ASR 模型包缺少 decoder"
        if (manifest.files.joiner.isNullOrBlank()) errors += "ASR 模型包缺少 joiner"
        if (manifest.files.tokens.isNullOrBlank()) errors += "ASR 模型包缺少 tokens"
    }

    private fun validateTts(
        manifest: VoiceModelPackageManifest,
        errors: MutableList<String>,
    ) {
        if (manifest.totalBytes > MAX_TTS_BYTES) errors += "TTS 模型包不能超过 600 MB"
        val hasVits = !manifest.files.model.isNullOrBlank()
        val hasMatcha = !manifest.files.acousticModel.isNullOrBlank() &&
            !manifest.files.vocoder.isNullOrBlank()
        if (!hasVits && !hasMatcha) errors += "TTS 模型包缺少 model 或 acousticModel/vocoder"
        if (manifest.files.tokens.isNullOrBlank()) errors += "TTS 模型包缺少 tokens"
    }

    private fun VoiceModelPackageFiles.allNames(): List<String> =
        listOfNotNull(encoder, decoder, joiner, model, acousticModel, vocoder, tokens, lexicon, dataDir)
}
```

- [ ] **Step 4: Run validator tests**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest --tests com.retrosprite.app.voice.VoiceModelPackageValidatorTest
```

Expected: pass.

- [ ] **Step 5: Commit package validation**

```bash
git add \
  app/src/main/kotlin/com/retrosprite/app/voice/VoiceModelContracts.kt \
  app/src/main/kotlin/com/retrosprite/app/voice/VoiceModelPackageValidator.kt \
  app/src/test/kotlin/com/retrosprite/app/voice/VoiceModelPackageValidatorTest.kt
git commit -m "feat: validate voice model packages"
```

---

### Task 6: Add ASR Model Management UI Without Enabling File Models Yet

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/viewmodel/UiContracts.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/screens/settings/SettingsViewModel.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/screens/settings/SettingsScreen.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/ui/screens/settings/SettingsViewModelTest.kt`
- Test: `app/src/androidTest/kotlin/com/retrosprite/app/ui/RetroSpriteAppSmokeTest.kt`

- [ ] **Step 1: Add UI state contracts**

Add:

```kotlin
data class UiVoiceModelItem(
    val id: String,
    val displayName: String,
    val kindLabel: String,
    val languageLabel: String,
    val isActive: Boolean,
    val isInstalled: Boolean,
    val canDelete: Boolean,
    val statusLabel: String,
)

interface VoiceModelStore {
    val models: StateFlow<List<UiVoiceModelItem>>
    suspend fun selectAsrModel(modelId: String)
    suspend fun deleteModel(modelId: String)
}
```

Add `voiceModelStore` to `UiDependencies`.

- [ ] **Step 2: Add SettingsViewModel model selection actions**

Add constructor parameter:

```kotlin
private val voiceModelStore: VoiceModelStore,
```

Expose:

```kotlin
val voiceModels = voiceModelStore.models

fun selectAsrModel(modelId: String) {
    viewModelScope.launch {
        voiceModelStore.selectAsrModel(modelId)
        store.updateActiveAsrModel(modelId)
    }
}

fun deleteVoiceModel(modelId: String) {
    viewModelScope.launch { voiceModelStore.deleteModel(modelId) }
}
```

- [ ] **Step 3: Add ASR model Settings section**

Add `AsrModelSection` below the microphone permission section:

```kotlin
@Composable
private fun AsrModelSection(
    models: List<UiVoiceModelItem>,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    SectionCard(title = "语音识别模型") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            models.filter { it.kindLabel == "语音识别" }.forEach { model ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_asr_model_${model.id}"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(model.displayName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${model.languageLabel} · ${model.statusLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!model.isActive) {
                        OutlinedButton(onClick = { onSelect(model.id) }) {
                            Text("切换")
                        }
                    }
                    if (model.canDelete) {
                        TextButton(onClick = { onDelete(model.id) }) {
                            Text("删除")
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Add smoke test tags**

In `RetroSpriteAppSmokeTest`, add a Settings smoke that verifies:

```kotlin
composeRule.onNodeWithTag("settings_asr_model_asr.zh.builtin.streaming_zipformer_14m")
    .assertExists()
composeRule.onNodeWithText("sherpa-onnx 中文 ASR")
    .assertExists()
composeRule.onNodeWithText("删除")
    .assertDoesNotExist()
```

- [ ] **Step 5: Run Settings tests**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest --tests com.retrosprite.app.ui.screens.settings.SettingsViewModelTest
```

Expected: pass.

- [ ] **Step 6: Run Compose smoke when an emulator or device is connected**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.retrosprite.app.ui.RetroSpriteAppSmokeTest
```

Expected: Settings renders the built-in Chinese ASR model and does not offer deletion for it.

- [ ] **Step 7: Commit ASR management shell**

```bash
git add \
  app/src/main/kotlin/com/retrosprite/app/ui/viewmodel/UiContracts.kt \
  app/src/main/kotlin/com/retrosprite/app/ui/viewmodel/UiDependencies.kt \
  app/src/main/kotlin/com/retrosprite/app/ui/screens/settings/SettingsViewModel.kt \
  app/src/main/kotlin/com/retrosprite/app/ui/screens/settings/SettingsScreen.kt \
  app/src/test/kotlin/com/retrosprite/app/ui/screens/settings/SettingsViewModelTest.kt \
  app/src/androidTest/kotlin/com/retrosprite/app/ui/RetroSpriteAppSmokeTest.kt
git commit -m "feat: show asr model management in settings"
```

---

### Task 7: Enable User-Installed English ASR After File-Backed Runtime Is Available

**Files:**
- Create: `app/src/main/kotlin/com/retrosprite/app/voice/AndroidVoiceModelStore.kt`
- Create: `app/src/main/kotlin/com/retrosprite/app/ui/integration/SelectableSherpaOnnxVoiceInputProvider.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxRecognizerFactory.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ServiceLocator.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/voice/VoiceModelStoreTest.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/ui/integration/SelectableSherpaOnnxVoiceInputProviderTest.kt`

- [ ] **Step 1: Add store tests**

Create `VoiceModelStoreTest.kt`:

```kotlin
package com.retrosprite.app.voice

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceModelStoreTest {

    @Test
    fun `built in model is always listed first`() = runTest {
        val store = FakeVoiceModelStore()

        val models = store.models.value

        assertEquals(BuiltInVoiceModels.CHINESE_ASR_ID, models.first().id)
        assertTrue(models.first().isActive)
        assertFalse(models.first().canDelete)
    }

    @Test
    fun `english user model can be selected and deleted`() = runTest {
        val store = FakeVoiceModelStore()
        store.installForTest(
            VoiceModelDescriptor(
                id = "asr.en.user.zipformer_small",
                kind = VoiceModelKind.Asr,
                language = VoiceModelLanguage.EnUs,
                displayName = "sherpa-onnx English ASR",
                engine = "sherpa-onnx",
                modelType = "zipformer",
                origin = VoiceModelOrigin.UserInstalled,
                isInstalled = true,
                isDefault = false,
                isDeleteAllowed = true,
            )
        )

        store.selectAsrModel("asr.en.user.zipformer_small")
        assertEquals("asr.en.user.zipformer_small", store.activeAsrModelId)

        store.deleteModel("asr.en.user.zipformer_small")

        assertEquals(BuiltInVoiceModels.CHINESE_ASR_ID, store.activeAsrModelId)
        assertEquals(1, store.models.value.size)
    }
}
```

- [ ] **Step 2: Implement app-private model store**

Implement `AndroidVoiceModelStore` with these storage rules:

```kotlin
private const val ROOT = "voice-models"
private const val ASR_DIR = "asr"
private const val TTS_DIR = "tts"
```

Directory mapping:

```kotlin
filesDir / ROOT / ASR_DIR / modelId
filesDir / ROOT / TTS_DIR / modelId
```

Deletion behavior:

```kotlin
if (model.origin == VoiceModelOrigin.BuiltInAsset) return
deleteRecursively(modelDirectory)
if (deletedModelId == activeAsrModelId) selectAsrModel(BuiltInVoiceModels.CHINESE_ASR_ID)
if (deletedModelId == activeTtsModelId) selectTtsModel("tts.android.system")
```

- [ ] **Step 3: Add file-backed recognizer factory path**

After the runtime exposes a public file-backed constructor, add:

```kotlin
fun createFromFile(model: SherpaOnnxAsrModel): OnlineRecognizer =
    OnlineRecognizer(
        config = createConfig(model),
    )
```

Keep the existing asset-backed method:

```kotlin
fun createFromAssets(
    assetManager: AssetManager,
    model: SherpaOnnxAsrModel,
): OnlineRecognizer =
    OnlineRecognizer(
        assetManager = assetManager,
        config = createConfig(model),
    )
```

- [ ] **Step 4: Add selectable provider behavior**

`SelectableSherpaOnnxVoiceInputProvider` must:

- Use the active model from `AndroidVoiceModelStore`.
- Reuse the existing Chinese provider path for `BuiltInAsset`.
- Release the current recognizer when the active model id changes.
- Refuse model switching while `isListening` is true.
- Update `UiVoiceInputState.engineLabel` with the selected model display name.

- [ ] **Step 5: Wire selectable ASR provider**

In `ServiceLocator`, replace:

```kotlin
val voiceInputProvider: VoiceInputProvider = SherpaOnnxVoiceInputProvider(
    context = appContext,
    scope = ServiceLocator.applicationScope,
)
```

with:

```kotlin
val voiceInputProvider: VoiceInputProvider = SelectableSherpaOnnxVoiceInputProvider(
    context = appContext,
    modelStore = voiceModelStore,
    scope = ServiceLocator.applicationScope,
)
```

- [ ] **Step 6: Run ASR model tests**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest \
  --tests com.retrosprite.app.voice.VoiceModelStoreTest \
  --tests com.retrosprite.app.ui.integration.SelectableSherpaOnnxVoiceInputProviderTest
```

Expected: pass.

- [ ] **Step 7: True-device ASR validation**

On RG 476H:

1. Install a Debug APK containing this task.
2. Open Settings.
3. Confirm Chinese ASR is active by default.
4. Import a known English sherpa-onnx ASR package.
5. Switch to English ASR.
6. Use Settings test recognition and say: `How do tiles merge?`
7. Confirm transcript contains English words rather than Chinese phonetic substitutions.
8. Delete the English model.
9. Confirm active ASR falls back to Chinese.

- [ ] **Step 8: Commit selectable ASR**

```bash
git add \
  app/src/main/kotlin/com/retrosprite/app/voice/AndroidVoiceModelStore.kt \
  app/src/main/kotlin/com/retrosprite/app/ui/integration/SelectableSherpaOnnxVoiceInputProvider.kt \
  app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxRecognizerFactory.kt \
  app/src/main/kotlin/com/retrosprite/app/ServiceLocator.kt \
  app/src/test/kotlin/com/retrosprite/app/voice/VoiceModelStoreTest.kt \
  app/src/test/kotlin/com/retrosprite/app/ui/integration/SelectableSherpaOnnxVoiceInputProviderTest.kt
git commit -m "feat: support selectable asr models"
```

---

### Task 8: Add TTS Model Management UI With Android TTS Fallback

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/viewmodel/UiContracts.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/screens/settings/SettingsScreen.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/screens/settings/SettingsViewModel.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/integration/AndroidSpeechOutputProvider.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/ui/screens/settings/SettingsViewModelTest.kt`
- Test: `app/src/androidTest/kotlin/com/retrosprite/app/ui/RetroSpriteAppSmokeTest.kt`

- [ ] **Step 1: Add Android system TTS model descriptor**

Add to `VoiceModelContracts.kt`:

```kotlin
object BuiltInSpeechOutputs {
    const val ANDROID_SYSTEM_TTS_ID = "tts.android.system"

    val AndroidSystemTts = VoiceModelDescriptor(
        id = ANDROID_SYSTEM_TTS_ID,
        kind = VoiceModelKind.Tts,
        language = VoiceModelLanguage.ZhCn,
        displayName = "Android 系统 TTS",
        engine = "android-text-to-speech",
        modelType = "system",
        origin = VoiceModelOrigin.BuiltInAsset,
        isInstalled = true,
        isDefault = true,
        isDeleteAllowed = false,
    )
}
```

- [ ] **Step 2: Add TTS Settings section**

Add `TtsModelSection`:

```kotlin
@Composable
private fun TtsModelSection(
    models: List<UiVoiceModelItem>,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onTestChinese: () -> Unit,
    onTestEnglish: () -> Unit,
) {
    SectionCard(title = "语音输出模型") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onTestChinese) { Text("测试中文") }
                OutlinedButton(onClick = onTestEnglish) { Text("Test English") }
            }
            models.filter { it.kindLabel == "语音输出" }.forEach { model ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_tts_model_${model.id}"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(model.displayName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${model.languageLabel} · ${model.statusLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!model.isActive) {
                        OutlinedButton(onClick = { onSelect(model.id) }) {
                            Text("切换")
                        }
                    }
                    if (model.canDelete) {
                        TextButton(onClick = { onDelete(model.id) }) {
                            Text("删除")
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Keep system TTS as fallback**

`AndroidSpeechOutputProvider` remains wired as the default `SpeechOutputProvider`. Add two Settings test texts:

```kotlin
private const val CHINESE_TTS_TEST_TEXT = "RetroSprite 语音输出测试。"
private const val ENGLISH_TTS_TEST_TEXT = "RetroSprite speech output test."
```

Use `deps.speechOutput.speak(CHINESE_TTS_TEST_TEXT)` and `deps.speechOutput.speak(ENGLISH_TTS_TEST_TEXT)`.

- [ ] **Step 4: Add smoke assertions**

In `RetroSpriteAppSmokeTest`, assert:

```kotlin
composeRule.onNodeWithTag("settings_tts_model_tts.android.system")
    .assertExists()
composeRule.onNodeWithText("Android 系统 TTS")
    .assertExists()
composeRule.onNodeWithText("测试中文")
    .assertExists()
composeRule.onNodeWithText("Test English")
    .assertExists()
```

- [ ] **Step 5: Run Settings TTS tests**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest --tests com.retrosprite.app.ui.screens.settings.SettingsViewModelTest
```

Expected: pass.

- [ ] **Step 6: Commit TTS management shell**

```bash
git add \
  app/src/main/kotlin/com/retrosprite/app/voice/VoiceModelContracts.kt \
  app/src/main/kotlin/com/retrosprite/app/ui/screens/settings/SettingsScreen.kt \
  app/src/main/kotlin/com/retrosprite/app/ui/screens/settings/SettingsViewModel.kt \
  app/src/main/kotlin/com/retrosprite/app/ui/integration/AndroidSpeechOutputProvider.kt \
  app/src/androidTest/kotlin/com/retrosprite/app/ui/RetroSpriteAppSmokeTest.kt
git commit -m "feat: show tts model management in settings"
```

---

### Task 9: Validate sherpa-onnx OfflineTts Runtime Before Local TTS Models

**Files:**
- Modify: `app/src/androidTest/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxRuntimeCapabilityAndroidTest.kt`
- Modify: `docs/VOICE_MODEL_PACKAGES.md`

- [ ] **Step 1: Add OfflineTts class-surface test**

Append:

```kotlin
@Test
fun offlineTtsRuntimeSurfaceIsAvailableBeforeLocalTtsProvider() {
    val result = runCatching {
        Class.forName("com.k2fsa.sherpa.onnx.OfflineTts")
    }

    assertTrue(
        "Current sherpa-onnx AAR must expose OfflineTts before RetroSprite can load app-private TTS models",
        result.isSuccess,
    )
}
```

- [ ] **Step 2: Run the test**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.retrosprite.app.ui.integration.SherpaOnnxRuntimeCapabilityAndroidTest
```

Expected for the current AAR: the new OfflineTts assertion fails. Treat that failure as the signal to upgrade or replace the sherpa-onnx Android dependency before implementing `SherpaOnnxTtsSpeechOutputProvider`.

- [ ] **Step 3: Update dependency or wrapper**

Make one of these concrete codebase changes:

1. Replace `libs.sherpa.onnx.android` with a version that exposes public `OfflineTts` Kotlin classes.
2. Add a maintained local wrapper module that exposes `OfflineTts`, `OfflineTtsConfig`, `generate(text)`, `sampleRate`, and `release()`.

After the change, rerun Step 2 and require the OfflineTts assertion to pass.

- [ ] **Step 4: Commit runtime TTS gate**

```bash
git add \
  app/src/androidTest/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxRuntimeCapabilityAndroidTest.kt \
  docs/VOICE_MODEL_PACKAGES.md \
  gradle/libs.versions.toml \
  app/build.gradle.kts
git commit -m "test: gate local tts on sherpa offline tts runtime"
```

---

### Task 10: Add Local sherpa-onnx TTS Provider After Runtime Is Ready

**Files:**
- Create: `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxTtsSpeechOutputProvider.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ServiceLocator.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxTtsSpeechOutputProviderTest.kt`

- [ ] **Step 1: Add provider behavior test**

Create:

```kotlin
package com.retrosprite.app.ui.integration

import com.retrosprite.app.ui.viewmodel.UiSpeechOutputState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaOnnxTtsSpeechOutputProviderTest {

    @Test
    fun `provider reports unavailable when selected tts model is missing`() = runTest {
        val provider = FakeSherpaOnnxTtsSpeechOutputProvider(modelExists = false)

        assertFalse(provider.state.value.isAvailable)
        assertEquals("本地 TTS 模型未安装", provider.state.value.errorMessage)
    }

    @Test
    fun `provider marks speaking during synthesis`() = runTest {
        val provider = FakeSherpaOnnxTtsSpeechOutputProvider(modelExists = true)

        provider.speak("RetroSprite 语音输出测试。")

        assertTrue(provider.spokenTexts.contains("RetroSprite 语音输出测试。"))
    }
}
```

- [ ] **Step 2: Implement provider**

`SherpaOnnxTtsSpeechOutputProvider` must:

- Implement `SpeechOutputProvider`.
- Load the active TTS model from app-private storage.
- Return `UiSpeechOutputState(isAvailable = false, isReady = false, errorMessage = "本地 TTS 模型未安装")` when files are missing.
- Use the same `shortSpeechAnswer()` text trimming as `AndroidSpeechOutputProvider`.
- Generate PCM through sherpa-onnx OfflineTts.
- Play PCM through `AudioTrack`.
- Stop playback and release `AudioTrack` in `stop()`.
- Fall back to `AndroidSpeechOutputProvider` when model initialization fails.

- [ ] **Step 3: Wire provider selection**

In `ServiceLocator`, select provider:

```kotlin
val androidSpeechOutputProvider = AndroidSpeechOutputProvider(appContext)
val speechOutputProvider: SpeechOutputProvider =
    if (settingsState.value.activeTtsModelId == BuiltInSpeechOutputs.ANDROID_SYSTEM_TTS_ID) {
        androidSpeechOutputProvider
    } else {
        SherpaOnnxTtsSpeechOutputProvider(
            context = appContext,
            modelStore = voiceModelStore,
            fallback = androidSpeechOutputProvider,
        )
    }
```

- [ ] **Step 4: Run TTS provider tests**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest --tests com.retrosprite.app.ui.integration.SherpaOnnxTtsSpeechOutputProviderTest
```

Expected: pass.

- [ ] **Step 5: True-device TTS validation**

On RG 476H:

1. Install a Debug APK containing local TTS provider support.
2. Import one Chinese sherpa-onnx TTS model.
3. Switch Settings `语音输出模型` to the Chinese TTS model.
4. Turn off Wi-Fi.
5. Tap `测试中文`.
6. Confirm audio plays.
7. Ask a known Chinese game question by hotkey.
8. Confirm short answer is spoken through local TTS.
9. Delete the TTS model.
10. Confirm active output falls back to Android 系统 TTS.

- [ ] **Step 6: Commit local TTS provider**

```bash
git add \
  app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxTtsSpeechOutputProvider.kt \
  app/src/main/kotlin/com/retrosprite/app/ServiceLocator.kt \
  app/src/test/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxTtsSpeechOutputProviderTest.kt
git commit -m "feat: add local sherpa tts provider"
```

---

### Task 11: Update Documentation And Release Checklist

**Files:**
- Modify: `docs/NEXT_IMPLEMENTATION_PLAN.md`
- Modify: `docs/TEST_COVERAGE.md`
- Modify: `docs/VOICE_MODEL_PACKAGES.md`

- [ ] **Step 1: Update roadmap summary**

Add to `docs/NEXT_IMPLEMENTATION_PLAN.md` under M11:

```markdown
- ASR/TTS model management path: keep built-in Chinese sherpa-onnx ASR as the default and immutable fallback. Optional English ASR and sherpa-onnx TTS models are installed into app-private storage, can be switched from Settings, and can be deleted by the user. Answer language is controlled separately from UI language.
```

- [ ] **Step 2: Update test coverage**

Add to `docs/TEST_COVERAGE.md`:

```markdown
| `voice.VoiceModelContractsTest` | ASR/TTS model management | Verifies the built-in Chinese ASR model remains the default, installed, and non-deletable. |
| `voice.VoiceModelPackageValidatorTest` | ASR/TTS model management | Verifies model package manifest validation, safe filenames, required files, and ASR/TTS size caps. |
| `voice.SpeechLanguagePolicyTest` | ASR/TTS model management | Verifies answer language resolution for Chinese, English, and follow-ASR modes. |
| `ui.integration.SherpaOnnxRuntimeCapabilityAndroidTest` | ASR/TTS runtime gates | Verifies the sherpa-onnx Android runtime exposes the required ASR file-loading and OfflineTts APIs before user-installed models are enabled. |
```

- [ ] **Step 3: Add release checklist**

Add to `docs/VOICE_MODEL_PACKAGES.md`:

```markdown
## Release Checklist

- Base APK still includes only the built-in Chinese ASR model.
- Built-in Chinese ASR cannot be deleted.
- English ASR installation is hidden until file-backed ASR loading passes on RG 476H.
- Local sherpa-onnx TTS installation is hidden until OfflineTts passes on RG 476H.
- Runtime recognition and synthesis work with Wi-Fi disabled after model installation.
- Deleting an active ASR model falls back to built-in Chinese ASR.
- Deleting an active TTS model falls back to Android system TTS.
- Answer language is separate from app UI language.
```

- [ ] **Step 4: Run documentation scan**

```bash
rg -n "ASR/TTS model management|VoiceModelContractsTest|VOICE_MODEL_PACKAGES|Answer language is separate" docs
```

Expected: matches appear in `docs/NEXT_IMPLEMENTATION_PLAN.md`, `docs/TEST_COVERAGE.md`, and `docs/VOICE_MODEL_PACKAGES.md`.

- [ ] **Step 5: Commit docs**

```bash
git add \
  docs/NEXT_IMPLEMENTATION_PLAN.md \
  docs/TEST_COVERAGE.md \
  docs/VOICE_MODEL_PACKAGES.md
git commit -m "docs: describe voice model management roadmap"
```

---

## Verification Commands

Run before merging any implementation batch from this plan:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest \
  --tests com.retrosprite.app.voice.VoiceModelContractsTest \
  --tests com.retrosprite.app.voice.VoiceModelPackageValidatorTest \
  --tests com.retrosprite.app.voice.SpeechLanguagePolicyTest \
  --tests com.retrosprite.app.ui.screens.settings.SettingsViewModelTest \
  --tests com.retrosprite.app.endpoint.QueryPipelineResponseGeneratorTest \
  --tests com.retrosprite.app.domain.policy.AnswerComposerTest
```

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew assembleDebug
```

When a device is connected:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.retrosprite.app.ui.RetroSpriteAppSmokeTest
```

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.retrosprite.app/.MainActivity
adb forward tcp:14404 tcp:4404 >/dev/null
curl -s http://127.0.0.1:14404/health
```

Expected:

```text
BUILD SUCCESSFUL
Success
{"status":"ok","version":"0.1.0"}
```

## Self-Review

- Spec coverage: The plan keeps current Chinese ASR as the stable default, adds optional English ASR installation/switch/delete, separates answer language from UI language, keeps Android TTS as fallback, and adds sherpa-onnx Chinese/English TTS installation/switch/delete after runtime validation.
- Runtime honesty: The plan records the current AAR limitations before model-management UI becomes active: file-backed ASR and public OfflineTts support must be proven first.
- Placeholder scan: No `TBD`, `TODO`, broad "handle edge cases", angle-bracket fill-ins, or unnamed tests remain.
- Type consistency: `VoiceModelKind`, `VoiceModelLanguage`, `VoiceModelOrigin`, `VoiceModelDescriptor`, `VoiceModelPackageManifest`, `UiAnswerLanguage`, `VoiceModelStore`, and `SpeechLanguagePolicy` are defined before later tasks reference them.
