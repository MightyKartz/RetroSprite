# RetroSprite

[中文（默认）](./README.md) | English

RetroSprite is an Android in-game Q&A companion for **RetroArch**. A player presses
the RetroArch AI Service hotkey, asks a short question by voice, and receives a
low-spoiler answer grounded in local Game Knowledge Packs (GKP).

```text
RetroArch AI Service hotkey
  -> RetroSprite localhost endpoint
  -> short in-game voice overlay
  -> local sherpa-onnx Paraformer ASR
  -> current-game GKP retrieval + AnswerPolicy
  -> short answer + Android TTS
```

RetroSprite is local-first and evidence-first. Optional BYOK LLM providers can help
with synthesis or phrasing, but they are not the default fact source and are not
allowed to answer game-specific facts without local evidence.

## Status

RetroSprite is in a preview / release-candidate testing phase. The hotkey voice
overlay, local ASR, GKP retrieval, short-answer TTS, diagnostics, and six bundled
real game packs are the current focus.

This is not a universal walkthrough bot. Use it only with the supported games below
unless you are developing or testing new GKPs.

## Supported Games

RetroSprite currently supports exactly **six** bundled real games:

| Game | Platform | GKP pack |
| --- | --- | --- |
| **Shining Force II / 光明力量2** | Sega Mega Drive / Genesis | `community.shining-force-ii-md` |
| **Golden Sun / 黄金太阳** | Game Boy Advance | `community.golden-sun-gba-zh` |
| **Phantasy Star IV / 梦幻之星 IV** | Sega Mega Drive / Genesis | `community.phantasy-star-iv-md-zh` |
| **Langrisser II / 梦幻模拟战 II** | Sega Mega Drive / Genesis | `community.langrisser-ii-md-zh` |
| **Chrono Trigger / 时空之轮** | Super Nintendo / Super Famicom | `community.chrono-trigger-snes-zh` |
| **Final Fantasy VI / 最终幻想 VI** | Super Nintendo / Super Famicom | `community.final-fantasy-vi-snes-zh` |

The old `sample-2048` and `sample-relay-station` demo packs are no longer bundled.

## Installation

Requirements:

- Android 8.0+ (API 26+) device or emulator.
- RetroArch Android with AI Service support.
- A supported game from the list above.

Download the latest preview APK from GitHub Releases:

[https://github.com/MightyKartz/RetroSprite/releases](https://github.com/MightyKartz/RetroSprite/releases)

Install with adb:

```bash
adb install -r app-debug.apk
```

Or open the APK on the Android device and allow installation from the current
browser or file manager. Preview APKs are debug-signed and intended for testing,
not production distribution.

On first launch, allow the permissions RetroSprite asks for:

- Microphone: local voice questions.
- Display over other apps: in-game voice and answer overlay.
- Notification / foreground service: local endpoint service.

RetroSprite does not modify RetroArch cores, does not require broad storage access,
and does not rewrite `retroarch.cfg`.

## RetroArch Setup

In RetroArch Android, open:

```text
Settings -> Accessibility -> AI Service
```

Recommended values:

| RetroArch field | Value |
| --- | --- |
| AI Service | `ON` |
| AI Service URL | `http://localhost:4404` |
| AI Service Output | `Narrator Mode` |
| Pause During Translation | `ON` |

Then bind the AI Service hotkey:

```text
Settings -> Input -> Hotkeys -> AI Service
```

## How to Use

1. Open RetroSprite and confirm that the local endpoint is running.
2. Open RetroArch and load one of the supported games.
3. Press the AI Service hotkey.
4. When the RetroSprite voice overlay appears, ask one short question.
5. RetroSprite resolves the current game, runs local ASR, searches the current
   GKP, and returns a short low-spoiler answer with evidence.
6. If local evidence is missing, RetroSprite says it cannot answer reliably
   instead of guessing.

Example questions:

- `修伊怎么用？`
- `角色什么时候转职？`
- `黄金太阳刚开始练谁？`
- `梦幻模拟战 II 转职怎么选？`
- `克拉肯怎么过？`
- `不要剧透，下一步去哪？`

## App Screens

- **Home**: endpoint status, text questions, recent context, and debug actions.
- **Packs**: bundled and external GKP management.
- **Settings**: RetroArch setup helper, endpoint port, overlay permission, spoiler level, and BYOK LLM settings.
- **Diagnostics**: latest request, pipeline stage, source ids, LLM status, latency, feedback, and errors.

## Development

Use a clean build before creating any APK intended for release or testing, especially
after ASR model or large asset changes:

```bash
cd retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:clean :app:testDebugUnitTest :app:assembleDebug
```

Install the debug build:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Host-side endpoint check:

```bash
adb forward tcp:18080 tcp:4404
curl -fsS http://127.0.0.1:18080/health
```

Debug ask example:

```bash
curl -fsS -X POST 'http://127.0.0.1:18080/debug/ask?output=text' \
  -H 'Content-Type: application/json' \
  --data '{"label":"mega_drive__光明力量2","question":"角色什么时候转职？","spoiler_level":"light","state":{}}'
```

## Repository Layout

```text
.
├── README.md                       # Default Chinese README
├── README.en.md                    # English README
├── retrosprite-android/            # Main Android app
│   ├── app/src/main/kotlin/        # Kotlin source
│   ├── app/src/main/assets/        # Bundled GKP + local ASR assets
│   ├── app/src/test/               # JVM unit tests
│   ├── app/src/androidTest/        # Android / Compose tests
│   ├── docs/                       # Protocol, GKP, setup, QA, and planning docs
│   └── scripts/                    # Endpoint and device smoke scripts
├── tools/gkp-builder/              # GKP Lite builder and templates
├── RetroSprite_Development_Plan.md
├── README_AI_SERVICE_RESEARCH.md
└── RetroArch_AI_Service_Protocol_Specification.txt
```

## Useful Docs

- [Android app guide](./retrosprite-android/README.md)
- [RetroArch AI Service setup](./retrosprite-android/docs/RETROARCH_SETUP.md)
- [Architecture and product tiers](./retrosprite-android/docs/ARCHITECTURE_AND_PRODUCT_TIERS.md)
- [GKP v0 schema](./retrosprite-android/docs/GKP_V0_SCHEMA.md)
- [Protocol reference](./retrosprite-android/docs/PROTOCOL_REFERENCE.md)
- [Test coverage](./retrosprite-android/docs/TEST_COVERAGE.md)

## Boundaries

RetroSprite deliberately avoids:

- RetroArch core modifications.
- Continuous screen capture through MediaProjection.
- Accessibility Service as the main integration path.
- Broad storage permissions or automatic `retroarch.cfg` rewriting.
- ROMs, commercial guide text dumps, executable code, or long copyrighted excerpts inside GKP content.
- LLM bare-answer fallback when local evidence is unavailable.

## License and Notices

RetroSprite's project license is TBD.

RetroSprite uses and may bundle third-party open-source ASR components:

- `sherpa-onnx` by k2-fsa for local offline speech recognition.
  Source: <https://github.com/k2-fsa/sherpa-onnx>
  License: Apache License 2.0.
- `csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en` ONNX Paraformer int8 model files.
  Source: <https://huggingface.co/csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en>
  Release package: <https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-paraformer-bilingual-zh-en.tar.bz2>
  License: Apache License 2.0.
