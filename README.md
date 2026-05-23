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
  -> local sherpa-onnx ASR
  -> GKP resolver + local retrieval + AnswerPolicy
  -> short answer + Android TTS
```

External LLMs are optional BYOK composers. They are not the default fact source
and are skipped when local evidence is missing, disabled, or over the selected
spoiler level.

## Current Status

RetroSprite is in the M10/M11 track: Hotkey Voice Overlay plus Zero-LLM GKP.

### Supported Games

RetroSprite currently has one real supported game pack:
**Shining Force II / 光明力量2** for Sega Mega Drive / Genesis. The bundled
`sample-2048` and `sample-relay-station` packs are test/demo packs for
development and smoke testing; they do not represent broader production game
support.

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
- Bundled demo packs for `sample-2048`, `sample-relay-station`, plus the first
  real supported game pack, `community.shining-force-ii-md`.
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

TBD.
