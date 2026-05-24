# Transducer Native Hotword Restore Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore sherpa-onnx native hotword decoding for RetroSprite hotkey voice questions by making the default ASR path a streaming Transducer model again.

**Architecture:** Keep GKP-driven hotword extraction as the source of terms, but route those terms into sherpa-onnx decoding instead of relying on post-ASR normalization. The default hotkey voice model becomes the bundled streaming Zipformer/Transducer, which enables `modified_beam_search`, `cjkchar` modeling, and per-stream hotwords such as `修 伊/气 合 之 玉/米 斯 里 鲁 银`.

**Tech Stack:** Kotlin, Android assets, sherpa-onnx Android API, Gradle unit tests, RetroSprite local `/debug/*` endpoints.

---

## Root Cause

The current default ASR model is `sherpa-onnx-streaming-paraformer-bilingual-zh-en`. In RetroSprite, `SherpaOnnxAsrModel.supportsHotwords` returns true only for `Architecture.Transducer`, so Paraformer forces hotwords off even when a GKP profile is available.

sherpa-onnx upstream documents the same rule: hotwords are supported only by Transducer models, and hotword use requires `modified_beam_search`; `greedy_search` does not support hotwords.

Observed device failures match this root cause:

- `修伊是谁` -> `修衣是`
- `气合之玉怎么用` -> `契合之欲怎么用`
- `米斯里鲁银有什么用` -> `米斯利乳营有什么用`

These are not retrieval failures first; they are ASR proper-noun biasing failures.

## Files

- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxAsrModel.kt`
  - Default to the bundled streaming Zipformer/Transducer assets.
  - Label the engine as native-hotword capable.
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxRecognizerFactory.kt`
  - Keep Transducer hotword config on `modified_beam_search`, `hotwordsScore=2.5`, `modelingUnit=cjkchar`.
  - Keep Paraformer guarded as unsupported.
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxVoiceInputProvider.kt`
  - Publish native hotword diagnostic state from the selected model and active hotword plan.
- Modify: `app/src/main/kotlin/com/retrosprite/app/voice/asr/GkpAsrHotwordExtractor.kt`
  - Promote template question terms so real spoken item names survive the 160-entry hotword cap.
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionController.kt`
  - Treat single-character ASR noise as muted input instead of sending it into retrieval.
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/viewmodel/UiContracts.kt`
  - Add ASR diagnostic fields visible to settings and future debug endpoints.
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/screens/settings/SettingsScreen.kt`
  - Replace the misleading “热词已启用” text with native hotword enabled/disabled status.
- Restore: `app/src/main/assets/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23/*`
  - Restore the previously bundled Transducer assets from the repository.
- Delete: `app/src/main/assets/sherpa-onnx-streaming-paraformer-bilingual-zh-en/*`
  - Remove the untracked Paraformer assets from the app asset tree after verifying they bloat the APK and are not the default native-hotword path.
- Test: `app/src/test/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxAsrModelTest.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxRecognizerFactoryTest.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/voice/asr/SherpaHotwordFileWriterTest.kt`
- Test: relevant UI/overlay tests that instantiate `UiVoiceInputState`.

## Acceptance Criteria

- Default model architecture is `Transducer`.
- Default model required assets include `encoder`, `decoder`, `joiner`, and `tokens`.
- Default hotword-enabled config uses:
  - `decodingMethod = "modified_beam_search"`
  - `modelConfig.modelingUnit = "cjkchar"`
  - `hotwordsScore = 2.5f`
  - no runtime `hotwordsFile` requirement for the primary stream-hotword path.
- Paraformer remains supported only as a non-native-hotword fallback path and reports native hotwords disabled.
- Settings/debug state must distinguish:
  - native hotwords enabled
  - hotword profile exists but native hotwords disabled because the model architecture does not support it
  - no hotword profile active
- The stream hotword string includes diagnostic terms with CJK character spacing:
  - `修 伊`
  - `气 合 之 玉`
  - `米 斯 里 鲁 银`
- Single-character ASR noise such as `心` does not enter the Q&A pipeline.
- APK build includes the restored Transducer assets.

### Device Acceptance

After install on the connected test device:

- `/health` returns `ok`.
- Waking the hotkey overlay enters `listening`.
- `RECORD_AUDIO` is running while listening.
- `/debug/hotkey-voice-overlay` reaches `finished` after answer playback/display ends.
- Raw ASR should improve on these proper nouns, not only post-normalized text:
  - `修伊是谁`
  - `气合之玉怎么用`
  - `米斯里鲁银有什么用`

## Tasks

### Task 1: Lock Default Model Expectations

**Files:**
- Modify: `app/src/test/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxAsrModelTest.kt`

- [ ] **Step 1: Write failing tests**

Update the default-model test so it expects:

```kotlin
assertEquals(SherpaOnnxAsrModel.Architecture.Transducer, model.architecture)
assertEquals("sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23", model.assetDir)
assertTrue(model.supportsHotwords)
assertEquals(4, model.requiredAssetPaths.size)
assertTrue(model.requiredAssetPaths.any { it.endsWith("encoder-epoch-99-avg-1.int8.onnx") })
assertTrue(model.requiredAssetPaths.any { it.endsWith("decoder-epoch-99-avg-1.onnx") })
assertTrue(model.requiredAssetPaths.any { it.endsWith("joiner-epoch-99-avg-1.int8.onnx") })
```

- [ ] **Step 2: Verify red**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest --tests com.retrosprite.app.ui.integration.SherpaOnnxAsrModelTest
```

Expected: fail because the current default is Paraformer.

- [ ] **Step 3: Implement default Transducer model**

Change `SherpaOnnxAsrModel.defaultModel()` to:

```kotlin
val dir = "sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"
return SherpaOnnxAsrModel(
    architecture = Architecture.Transducer,
    assetDir = dir,
    encoderAsset = "$dir/encoder-epoch-99-avg-1.int8.onnx",
    decoderAsset = "$dir/decoder-epoch-99-avg-1.onnx",
    joinerAsset = "$dir/joiner-epoch-99-avg-1.int8.onnx",
    tokensAsset = "$dir/tokens.txt",
    modelType = "zipformer",
    engineLabel = "sherpa-onnx Transducer 本地 ASR",
)
```

- [ ] **Step 4: Verify green**

Run the same test command and expect pass.

### Task 2: Lock Native Hotword Config

**Files:**
- Modify: `app/src/test/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxRecognizerFactoryTest.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxRecognizerFactory.kt`

- [ ] **Step 1: Write failing tests**

Change the default config test to verify the default model uses Transducer fields, not Paraformer fields:

```kotlin
val model = SherpaOnnxAsrModel.defaultModel()
val config = SherpaOnnxRecognizerFactory.createConfig(
    model = model,
    hotwordsScore = 2.5f,
    enableHotwords = true,
)

assertEquals("modified_beam_search", config.decodingMethod)
assertEquals("cjkchar", config.modelConfig.modelingUnit)
assertEquals(2.5f, config.hotwordsScore, 0.001f)
assertEquals("${model.assetDir}/encoder-epoch-99-avg-1.int8.onnx", config.modelConfig.transducer.encoder)
assertEquals("${model.assetDir}/decoder-epoch-99-avg-1.onnx", config.modelConfig.transducer.decoder)
assertEquals("${model.assetDir}/joiner-epoch-99-avg-1.int8.onnx", config.modelConfig.transducer.joiner)
assertEquals("", config.modelConfig.paraformer.encoder)
```

- [ ] **Step 2: Verify red**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest --tests com.retrosprite.app.ui.integration.SherpaOnnxRecognizerFactoryTest
```

Expected: fail before Task 1 implementation, then pass after the default Transducer implementation.

- [ ] **Step 3: Preserve Paraformer guard**

Keep the Paraformer test that proves a Paraformer hotword request stays `greedy_search`, `hotwordsScore=0.0f`, and empty `modelingUnit`.

### Task 3: Surface Native Hotword Diagnostics

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/viewmodel/UiContracts.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/integration/SherpaOnnxVoiceInputProvider.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/screens/settings/SettingsScreen.kt`
- Test: existing unit tests that construct `UiVoiceInputState`

- [ ] **Step 1: Add state fields**

Extend `UiVoiceInputState` with:

```kotlin
val asrArchitecture: String? = null,
val asrDecodingMethod: String? = null,
val asrModelingUnit: String? = null,
val asrNativeHotwordsEnabled: Boolean = false,
val asrNativeHotwordsReason: String? = null,
val asrHotwordMode: String? = null,
val asrHotwordPreview: String? = null,
```

- [ ] **Step 2: Populate diagnostics**

In `SherpaOnnxVoiceInputProvider`, when listening starts, publish:

```kotlin
asrArchitecture = model.architecture.name.lowercase()
asrDecodingMethod = if (hotwordPlan.enabled && model.supportsHotwords) "modified_beam_search" else "greedy_search"
asrModelingUnit = if (hotwordPlan.enabled && model.supportsHotwords) "cjkchar" else null
asrNativeHotwordsEnabled = hotwordPlan.enabled && model.supportsHotwords
asrNativeHotwordsReason = nativeHotwordReason(profile, hotwordPlan)
asrHotwordMode = hotwordMode.name
asrHotwordPreview = hotwordPlan.preview()
```

- [ ] **Step 3: Fix settings copy**

Replace misleading copy with:

```kotlin
val hotwordStatus = when {
    voiceInputState.asrNativeHotwordsEnabled ->
        "ASR 原生热词已进入解码：${voiceInputState.asrHotwordCount} 个，${voiceInputState.asrDecodingMethod}"
    voiceInputState.asrBiasingProfileId != null ->
        "ASR 热词资料已生成，但原生热词未启用：${voiceInputState.asrNativeHotwordsReason ?: "当前模型不支持"}"
    else -> null
}
```

### Task 4: Restore Transducer Assets

**Files:**
- Restore: `app/src/main/assets/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23/README.md`
- Restore: `app/src/main/assets/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23/encoder-epoch-99-avg-1.int8.onnx`
- Restore: `app/src/main/assets/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23/decoder-epoch-99-avg-1.onnx`
- Restore: `app/src/main/assets/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23/joiner-epoch-99-avg-1.int8.onnx`
- Restore: `app/src/main/assets/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23/tokens.txt`
- Delete: `app/src/main/assets/sherpa-onnx-streaming-paraformer-bilingual-zh-en/*`

- [ ] **Step 1: Restore tracked assets**

Restore only this asset directory from `HEAD`.

- [ ] **Step 2: Verify assets exist**

Run:

```bash
find app/src/main/assets/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23 -maxdepth 1 -type f | sort
```

Expected: the five files listed above.

- [ ] **Step 3: Remove non-default Paraformer assets from APK inputs**

Delete the untracked Paraformer asset directory after confirming `app-debug.apk` packaged both models. This keeps the restored Transducer path as the only bundled local ASR model for the hotkey voice flow.

### Task 5: Verify

**Files:**
- No new files unless a verification note is needed.

- [ ] **Step 1: Targeted tests**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest --tests com.retrosprite.app.ui.integration.SherpaOnnxAsrModelTest --tests com.retrosprite.app.ui.integration.SherpaOnnxRecognizerFactoryTest --tests com.retrosprite.app.voice.asr.SherpaHotwordFileWriterTest
```

- [ ] **Step 2: Broader impacted tests**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest --tests com.retrosprite.app.ui.overlay.HotkeyVoiceQuestionControllerTest --tests com.retrosprite.app.ui.overlay.HotkeyVoiceOverlayCoordinatorTest --tests com.retrosprite.app.endpoint.RetroArchEndpointServerTest
```

- [ ] **Step 3: Build APK**

Run:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug
```

- [ ] **Step 4: Device smoke**

Install and check:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.retrosprite.app/.MainActivity
adb forward tcp:4404 tcp:4404
curl -s http://127.0.0.1:4404/health
curl -s http://127.0.0.1:4404/debug/hotkey-voice-overlay
```

Expected: app starts, `/health` is ok, and overlay lifecycle debug remains available.

### Task 6: Fix Device Smoke Findings

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/voice/asr/GkpAsrHotwordExtractor.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionController.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/voice/asr/GkpAsrHotwordExtractorTest.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionControllerTest.kt`

- [ ] **Step 1: Preserve real spoken item terms under cap**

Add a regression test where many same-score aliases would otherwise push out `气合之玉` and `米斯里鲁银`. Then raise template-question extracted terms above generic localized aliases and strip the `有什么用` suffix so `米斯里鲁银有什么用` contributes `米斯里鲁银` as the actual hotword.

- [ ] **Step 2: Reject one-character noise**

Add a controller regression test where ASR returns `心`; expected behavior is muted recovery and no call to the answer generator. Then require at least two non-space transcript characters before entering retrieval.

- [ ] **Step 3: Re-run device smoke**

Wake once without MacBook speaker audio and confirm `/debug/hotkey-voice-overlay` reports:

```json
{
  "asr_architecture": "transducer",
  "asr_decoding_method": "modified_beam_search",
  "asr_modeling_unit": "cjkchar",
  "asr_native_hotwords_enabled": true,
  "asr_hotword_preview": "修 伊/.../气 合 之 玉/.../米 斯 里 鲁 银/..."
}
```

## Out Of Scope

- Do not add more post-ASR fallback replacements as the primary fix.
- Do not keep Paraformer assets in `app/src/main/assets` for this pass; they are not the native-hotword model and inflate the APK when present.
- Do not tune `hotwordsScore` beyond 2.5 in this pass unless the Transducer path is verified stable and a later QA round shows under-biasing.
- Do not claim raw ASR proper-noun success without fresh real-device transcript evidence.
