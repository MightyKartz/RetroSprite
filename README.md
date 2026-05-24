# RetroSprite

[中文说明](./README.zh-CN.md) | English

RetroSprite is an Android companion for RetroArch that lets a player press the
RetroArch AI Service hotkey, ask a short in-game question, and receive a
low-spoiler answer grounded in local Game Knowledge Packs.

The project is currently focused on a local-first, evidence-first loop:

```text
RetroArch AI Service hotkey
  -> RetroSprite localhost endpoint
  -> short in-game voice overlay
  -> local sherpa-onnx Paraformer ASR
  -> GKP resolver + local retrieval + AnswerPolicy
  -> short answer + Android TTS
```

External LLMs are optional BYOK composers. They are not the default fact source
and are skipped when local evidence is missing, disabled, or over the selected
spoiler level.

The next development direction is **GKP Lite plus optional BYOK LLM assistance**:
each game's first support package should be a lightweight, source-cited,
testable knowledge anchor rather than a complete walkthrough. When enabled by
the player, an LLM can improve query understanding, cross-language mapping,
evidence synthesis, translation, and phrasing, but it must not bare-answer
game-specific facts without local evidence.

## Current Status

RetroSprite is in the M10/M11 track: Hotkey Voice Overlay plus Zero-LLM GKP.

### Supported Games

RetroSprite currently supports exactly **six** bundled real games:

- **Shining Force II / 光明力量2** (Sega Mega Drive / Genesis) —
  `community.shining-force-ii-md`
- **Golden Sun / 黄金太阳** (Game Boy Advance) —
  `community.golden-sun-gba-zh`
- **Phantasy Star IV / 梦幻之星 IV** (Sega Mega Drive / Genesis) —
  `community.phantasy-star-iv-md-zh`
- **Langrisser II / 梦幻模拟战 II** (Sega Mega Drive / Genesis) —
  `community.langrisser-ii-md-zh`
- **Chrono Trigger / 时空之轮** (Super Nintendo / Super Famicom) —
  `community.chrono-trigger-snes-zh`
- **Final Fantasy VI / 最终幻想 VI** (Super Nintendo / Super Famicom) —
  `community.final-fantasy-vi-snes-zh`

This is the full current game support surface. The former `sample-2048` and
`sample-relay-station` demo packs have been removed from bundled assets; smoke
checks should use real GKP packs.

Implemented pieces include:

- Android app in Kotlin with Jetpack Compose and Material 3.
- Foreground-service-hosted Ktor endpoint bound to `127.0.0.1:4404`.
- RetroArch-compatible `POST /?output=text`, `GET /health`,
  `POST /debug/ask`, and `GET /debug/latest-request` routes.
- Hotkey-triggered overlay flow using Android overlay permission, one-shot
  local ASR, GKP answer generation, request logging, and short-answer TTS.
- Local Room database with request logs, games, knowledge rows, GKP metadata,
  enable/disable state, and migration schemas.
- Game Knowledge Pack v0 parser, bundled importer, external-pack preflight,
  install/replace confirmation, and Packs management UI.
- Six bundled real GKP packs: `community.shining-force-ii-md` plus five Retro
  JRPG/SRPG Chinese Lite packs.
- Local retrieval through template, alias/entity, and FTS-style matching with
  spoiler gating and source IDs.
- Settings for RetroArch setup guidance, endpoint port, overlay permission,
  default spoiler level, and BYOK OpenAI-compatible/DeepSeek LLM config.
- Diagnostics surfaces for request source, pipeline stage, LLM status, latency,
  token budget, feedback, and latest request replay.

## Repository Layout

```text
.
├── retrosprite-android/        # Main Android application
│   ├── app/src/main/kotlin/    # Kotlin source
│   ├── app/src/main/assets/    # Bundled GKP + local ASR model assets
│   ├── app/src/test/           # JVM unit tests
│   ├── app/src/androidTest/    # Instrumented Android and Compose tests
│   ├── docs/                   # Protocol, GKP, setup, QA, and planning docs
│   └── scripts/                # Endpoint and AVD/device smoke scripts
├── RetroSprite_Development_Plan.md
├── README_AI_SERVICE_RESEARCH.md
└── RetroArch_AI_Service_Protocol_Specification.txt
```

The most detailed Android-specific guide lives in
[`retrosprite-android/README.md`](./retrosprite-android/README.md).

## Quick Start

Requirements:

- Android Studio or a local Android SDK.
- JDK 17. Android Studio's bundled JBR is a safe default.
- A device or emulator running Android API 26+.
- RetroArch Android with AI Service enabled for the full hotkey path.

Build the debug APK:

```bash
cd retrosprite-android
./gradlew assembleDebug
```

Install it:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For host-side endpoint testing against a running device app:

```bash
adb forward tcp:4404 tcp:4404
./scripts/test_endpoint.sh
```

For the fuller AVD/device smoke:

```bash
./scripts/android_avd_smoke.sh
```

## RetroArch Setup

In RetroArch Android, open:

```text
Settings -> Accessibility -> AI Service
```

Use RetroSprite's default endpoint:

```text
http://localhost:4404
```

Then bind and press the RetroArch AI Service hotkey. RetroSprite treats that
hotkey as a wake signal, shows a short overlay, records one local question, and
answers through the same GKP/evidence pipeline used by app-side text questions.

Full setup and troubleshooting are documented in
[`retrosprite-android/docs/RETROARCH_SETUP.md`](./retrosprite-android/docs/RETROARCH_SETUP.md).

## Development

Run JVM tests:

```bash
cd retrosprite-android
./gradlew testDebugUnitTest
```

Run instrumented tests on a connected device or emulator:

```bash
cd retrosprite-android
./gradlew connectedDebugAndroidTest
```

Useful documentation:

- [`retrosprite-android/docs/GKP_V0_SCHEMA.md`](./retrosprite-android/docs/GKP_V0_SCHEMA.md)
- [`retrosprite-android/docs/GKP_LITE_OPTIONAL_LLM_DIRECTION.md`](./retrosprite-android/docs/GKP_LITE_OPTIONAL_LLM_DIRECTION.md)
- [`retrosprite-android/docs/REAL_GAME_GKP_EXPANSION_TEMPLATE.md`](./retrosprite-android/docs/REAL_GAME_GKP_EXPANSION_TEMPLATE.md)
- [`retrosprite-android/docs/PROTOCOL_REFERENCE.md`](./retrosprite-android/docs/PROTOCOL_REFERENCE.md)
- [`retrosprite-android/docs/TEST_COVERAGE.md`](./retrosprite-android/docs/TEST_COVERAGE.md)
- [`retrosprite-android/docs/NEXT_IMPLEMENTATION_PLAN.md`](./retrosprite-android/docs/NEXT_IMPLEMENTATION_PLAN.md)

## Design Boundaries

RetroSprite deliberately avoids several tempting shortcuts:

- No RetroArch core modifications.
- No continuous screen capture through MediaProjection.
- No Accessibility Service as the main integration path.
- No broad storage permission or automatic `retroarch.cfg` rewriting.
- No ROMs, commercial guide text, executable code, or long copyrighted excerpts
  inside GKP content.
- No LLM bare-answer fallback when local evidence is unavailable.

## License

RetroSprite's project license is TBD.

### Third-Party Notices

RetroSprite uses and may bundle third-party open-source ASR components:

- `sherpa-onnx` by k2-fsa for local offline speech recognition.
  Source: <https://github.com/k2-fsa/sherpa-onnx>
  License: Apache License 2.0.
- `csukuangfj/streaming-paraformer-zh` ONNX Paraformer model files for
  local Chinese ASR.
  Source: <https://huggingface.co/csukuangfj/streaming-paraformer-zh>
  License: Apache License 2.0.
- The Paraformer model is converted from the ModelScope Paraformer ASR model.
  Source:
  <https://modelscope.cn/models/iic/speech_paraformer-large_asr_nat-zh-cn-16k-common-vocab8404-online-onnx>
  License: Apache License 2.0.

Apache-2.0 permits commercial use, modification, and redistribution, provided
the applicable license and attribution notices are preserved.
