# M17 Release Candidate Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Freeze RetroSprite's current feature surface and harden the existing hotkey voice, GKP, BYOK screen translation, diagnostics, and release documentation paths into a preview-ready release candidate.

**Architecture:** Do not add new product capabilities. Treat the existing Android app as the release surface: RetroArch endpoint, hotkey voice overlay, local Paraformer ASR, GKP retrieval, AnswerPolicy, Android TTS, BYOK screen translation, Settings, Packs, Diagnostics, and scripts. Any debug-only hook must be explicit, tested, and documented so it cannot be mistaken for normal player behavior.

**Tech Stack:** Kotlin, Jetpack Compose, Room/SQLite FTS5, Ktor CIO local endpoint, sherpa-onnx Paraformer ASR, Android TextToSpeech, OkHttp OpenAI-compatible VLM API, Gradle/JUnit4/Robolectric, bash/adb/curl.

---

## Current Truth

- Local verification on 2026-06-01 passed: `./gradlew :app:testDebugUnitTest :app:assembleDebug`.
- Bundled real GKP scope is six games: Shining Force II, Golden Sun, Phantasy Star IV, Langrisser II, Chrono Trigger, and Final Fantasy VI.
- Current bundled data snapshot is about 347 knowledge rows and 337 QA goldens.
- Current debug APK is about 251 MB; bundled Paraformer ASR assets are about 226 MB.
- The release candidate should keep screen translation API-only and BYOK. The recommended model text remains `Qwen/Qwen3-VL-8B-Instruct`; no API key is bundled.
- GKP content must remain original short summaries, aliases, term metadata, source refs, and goldens. Do not bundle ROM data, commercial guidebook prose, full scripts, copied fan translations, or patch text.

## Files To Touch

- Modify: `app/src/main/kotlin/com/retrosprite/app/endpoint/RetroArchHotkeyListener.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionController.kt`
- Modify: `app/src/test/kotlin/com/retrosprite/app/endpoint/RetroArchEndpointServerTest.kt`
- Modify: `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionControllerTest.kt`
- Create: `scripts/rc_hardening_check.sh`
- Create: `docs/qa-feedback/rc-device-matrix.md`
- Create: `docs/RELEASE_CANDIDATE_CHECKLIST.md`
- Modify: `docs/TEST_COVERAGE.md`
- Modify: `README.md`

## Task 1: Classify And Contain Hotkey Question Injection

**Files:**
- Modify: `app/src/main/kotlin/com/retrosprite/app/endpoint/RetroArchHotkeyListener.kt`
- Modify: `app/src/main/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionController.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/endpoint/RetroArchEndpointServerTest.kt`
- Test: `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionControllerTest.kt`

- [x] **Step 1: Write endpoint tests proving normal hotkey voice output cannot inject a question**

Add tests beside the existing hotkey listener route tests:

```kotlin
@Test
fun `regular hotkey voice output ignores request question injection`() = testApplication {
    val listener = CapturingHotkeyListener()
    application {
        retroArchModule(
            responseGenerator = PlaceholderResponseGenerator(),
            requestLogger = RequestLogger(),
            hotkeyListener = listener,
        )
    }

    client.post("/?output=hotkey_voice:text") {
        contentType(ContentType.Application.Json)
        setBody("""{"label":"snes__Final Fantasy VI","question":"翻译","image":"abcd","state":{"paused":1}}""")
    }

    assertEquals("", listener.events.single().injectedQuestion)
}

@Test
fun `debug hotkey voice output carries an injected question for qa only`() = testApplication {
    val listener = CapturingHotkeyListener()
    application {
        retroArchModule(
            responseGenerator = PlaceholderResponseGenerator(),
            requestLogger = RequestLogger(),
            hotkeyListener = listener,
        )
    }

    client.post("/?output=hotkey_voice_debug:text") {
        contentType(ContentType.Application.Json)
        setBody("""{"label":"snes__Final Fantasy VI","question":"翻译","image":"abcd","state":{"paused":1}}""")
    }

    assertEquals("翻译", listener.events.single().injectedQuestion)
}
```

- [x] **Step 2: Run tests to verify the current behavior fails the first test**

Run:

```bash
JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home" \
./gradlew :app:testDebugUnitTest --tests "com.retrosprite.app.endpoint.RetroArchEndpointServerTest"
```

Expected before the fix: the regular `hotkey_voice:text` case carries `翻译`, proving the injection path is too broad.

- [x] **Step 3: Restrict injected questions to an explicit QA output mode**

Update `RetroArchHotkeyListener.kt` so only `hotkey_voice_debug` output modes carry an injected question:

```kotlin
private fun String.allowsDebugInjectedQuestion(): Boolean =
    startsWith("hotkey_voice_debug")
```

Use it inside `RetroArchRequest.toHotkeyEvent`:

```kotlin
injectedQuestion = question
    .trim()
    .takeIf { outputMode.allowsDebugInjectedQuestion() }
    .orEmpty()
```

- [x] **Step 4: Add controller test for the QA-only path**

Add a controller test that constructs a `RetroArchHotkeyEvent` with `injectedQuestion = "翻译"` and verifies the microphone provider is not started while the screen translation pipeline is called:

```kotlin
@Test
fun `debug injected translation question bypasses microphone and translates screenshot`() = runTest {
    val renderer = FakeRenderer()
    val coordinator = HotkeyVoiceOverlayCoordinator(
        renderer = renderer,
        canDrawOverlays = { true },
        scheduleAutoHide = {},
        cancelAutoHide = {},
    )
    val voice = FakeVoiceInputProvider("should not be used")
    val translationPipeline = RecordingScreenTranslationPipeline(
        ScreenTranslationResult(
            translatedText = "菜单\nITEM 道具",
            pages = listOf("菜单\nITEM 道具"),
            providerName = "fake-api",
            model = "fake-model",
        )
    )
    val controller = HotkeyVoiceQuestionController(
        coordinator = coordinator,
        voiceInput = voice,
        responseGenerator = CapturingGenerator("normal answer"),
        screenTranslationPipeline = translationPipeline,
        screenTranslationIntentClassifier = ScreenTranslationIntentClassifier(),
        speechOutput = FakeSpeechOutputProvider(),
        loggerProvider = { RequestLogger() },
        scope = this,
    )

    controller.onHotkey(event(imageBase64 = "menu_image").copy(injectedQuestion = "翻译"))
    advanceUntilIdle()

    assertEquals(0, voice.startCount)
    assertEquals(1, translationPipeline.callCount)
    assertEquals("菜单\nITEM 道具", lastTranslationText(renderer))
}
```

- [x] **Step 5: Verify endpoint and controller tests pass**

Run:

```bash
JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home" \
./gradlew :app:testDebugUnitTest \
  --tests "com.retrosprite.app.endpoint.RetroArchEndpointServerTest" \
  --tests "com.retrosprite.app.ui.overlay.HotkeyVoiceQuestionControllerTest"
```

Expected: both test classes pass.

## Task 2: Add One Command RC Hardening Gate

**Files:**
- Create: `scripts/rc_hardening_check.sh`
- Modify: `docs/TEST_COVERAGE.md`

- [x] **Step 1: Create the RC gate script**

Create `scripts/rc_hardening_check.sh`:

```bash
#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home}"
RUN_DEVICE="${RUN_DEVICE:-0}"
RUN_VOICE="${RUN_VOICE:-0}"
RUN_TRANSLATION_LIVE="${RUN_TRANSLATION_LIVE:-0}"

fail() {
  printf "FAIL %s\n" "$1" >&2
  exit 1
}

info() {
  printf "%s\n" "$1"
}

cd "$ROOT_DIR" || fail "cannot enter repo root"

info "[1/5] JVM tests and debug assemble"
JAVA_HOME="$JAVA_HOME" ./gradlew :app:testDebugUnitTest :app:assembleDebug \
  || fail "unit tests or assembleDebug failed"

info "[2/5] APK size snapshot"
ls -lh app/build/outputs/apk/debug/app-debug.apk \
  || fail "debug APK missing"
du -sh app/src/main/assets app/src/main/assets/sherpa-onnx-streaming-paraformer-bilingual-zh-en app/src/main/assets/gkp \
  || fail "asset size snapshot failed"

info "[3/5] GKP snapshot"
printf "packs: "
find app/src/main/assets/gkp -mindepth 1 -maxdepth 1 -type d | wc -l
printf "knowledge rows: "
find app/src/main/assets/gkp -path "*/knowledge/*.jsonl" -print0 | xargs -0 cat | wc -l
printf "qa goldens: "
find app/src/main/assets/gkp -name qa_goldens.jsonl -print0 | xargs -0 cat | wc -l

if [ "$RUN_DEVICE" = "1" ]; then
  info "[4/5] device endpoint and GKP smoke"
  BUILD=1 INSTALL=1 ./scripts/android_avd_smoke.sh \
    || fail "android device smoke failed"
else
  info "[4/5] device smoke skipped; set RUN_DEVICE=1 to run it"
fi

if [ "$RUN_VOICE" = "1" ]; then
  info "[5/5] hotkey voice matrix"
  RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 ./scripts/hotkey_voice_qa_batch.sh \
    || fail "hotkey voice QA failed"
elif [ "$RUN_TRANSLATION_LIVE" = "1" ]; then
  info "[5/5] live translation smoke is manual; use docs/qa-feedback/rc-device-matrix.md"
else
  info "[5/5] voice and live translation skipped; set RUN_VOICE=1 for voice QA"
fi

info "OK M17 RC hardening gate completed"
```

- [x] **Step 2: Make it executable and run syntax check**

Run:

```bash
chmod +x scripts/rc_hardening_check.sh
bash -n scripts/rc_hardening_check.sh
```

Expected: no syntax errors.

- [x] **Step 3: Run the local-only gate**

Run:

```bash
JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home" \
RUN_DEVICE=0 RUN_VOICE=0 ./scripts/rc_hardening_check.sh
```

Expected: JVM tests pass, debug APK exists, and the script prints GKP/APK snapshots.

## Task 3: Lock Screen Translation Release Behavior

**Files:**
- Modify: `app/src/test/kotlin/com/retrosprite/app/screen/translation/ScreenTranslationStructuredResponseParserTest.kt`
- Modify: `app/src/test/kotlin/com/retrosprite/app/screen/translation/OpenAiCompatibleScreenTranslationProviderTest.kt`
- Modify: `app/src/test/kotlin/com/retrosprite/app/ui/overlay/HotkeyVoiceQuestionControllerTest.kt`
- Modify: `docs/TEST_COVERAGE.md`

- [x] **Step 1: Ensure dialogue JSON renders Chinese only**

Add or keep this parser test:

```kotlin
@Test
fun `dialogue mode renders translated text without English source`() {
    val parser = ScreenTranslationStructuredResponseParser()

    val result = parser.parse(
        rawText = """{"mode":"dialogue","text":"蒂娜：我不明白。"}""",
        glossary = null,
    )

    assertEquals("蒂娜：我不明白。", result)
    assertEquals(false, result.orEmpty().contains("TERRA"))
}
```

- [x] **Step 2: Ensure menu JSON renders bilingual lookup rows and preserves numbers**

Add or keep this parser test:

```kotlin
@Test
fun `menu mode renders bilingual rows and preserves numeric values`() {
    val parser = ScreenTranslationStructuredResponseParser()

    val result = parser.parse(
        rawText = """
            {
              "mode":"menu",
              "entries":[
                {"source":"ITEM","translation":"道具","value":"","type":"menu"},
                {"source":"Level 12","translation":"等级","value":"","type":"stat"},
                {"source":"HP 344/344","translation":"生命值","value":"","type":"stat"}
              ]
            }
        """.trimIndent(),
        glossary = null,
    )

    assertTrue(result.orEmpty().contains("ITEM 道具"))
    assertTrue(result.orEmpty().contains("Level 等级 12"))
    assertTrue(result.orEmpty().contains("HP 生命值 344/344"))
}
```

- [x] **Step 3: Ensure untranslated English retry remains covered**

Keep provider tests that assert the repair prompt is used when the first model response is mostly Latin and contains no Chinese:

```kotlin
@Test
fun `provider retries when first response looks like untranslated English OCR`() = runTest {
    val server = MockWebServer()
    server.enqueue(
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "ITEM\nSKILL\nEQUIP"
                      }
                    }
                  ]
                }
                """.trimIndent()
            )
    )
    server.enqueue(
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "{\"mode\":\"menu\",\"entries\":[{\"source\":\"ITEM\",\"translation\":\"道具\",\"value\":\"\",\"type\":\"menu\"}]}"
                      }
                    }
                  ]
                }
                """.trimIndent()
            )
    )
    server.start()
    val provider = OpenAiCompatibleScreenTranslationProvider(
        providerName = "fake",
        baseUrl = server.url("/v1").toString().trimEnd('/'),
        apiKey = "test-key",
        model = "Qwen/Qwen3-VL-8B-Instruct",
    )

    val result = provider.translateScreenshotToChinese("base64-image")

    assertEquals(true, result.contains("道具"))
    assertNotNull(server.takeRequest())
    assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
    server.shutdown()
}
```

- [x] **Step 4: Keep 10-second overlay paging test green**

Run the already added paging regression:

```bash
JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home" \
./gradlew :app:testDebugUnitTest \
  --tests "com.retrosprite.app.ui.overlay.HotkeyVoiceQuestionControllerTest.screen translation keeps every result page visible for ten seconds"
```

Expected: pass.

## Task 4: Build The Real Device RC Matrix

**Files:**
- Create: `docs/qa-feedback/rc-device-matrix.md`
- Modify: `scripts/hotkey_voice_qa_cases.tsv`
- Modify: `docs/TEST_COVERAGE.md`

- [x] **Step 1: Create the manual device matrix document**

Create `docs/qa-feedback/rc-device-matrix.md`:

```markdown
# M17 RC Device Matrix

> Date: 2026-06-01
> Device target: RG476H or equivalent Android device with RetroArch AI Service configured to `http://localhost:4404`.

## Required Setup

- Install current debug APK with `./gradlew :app:installDebug`.
- Launch `com.retrosprite.app/.MainActivity`.
- Confirm overlay permission and microphone permission are granted.
- Configure RetroArch AI Service URL to `http://localhost:4404`.
- Configure BYOK screen translation provider only with a user-provided key.
- Use `Qwen/Qwen3-VL-8B-Instruct` as the recommended screen translation model.

## Voice Q&A Matrix

| Game | Label example | Question | Expected source/stage | Result |
|---|---|---|---|---|
| Shining Force II | `mega_drive__光明力量2` | 角色什么时候转职？ | `sf2.promotion`, `evidence`, `skipped` | Not run |
| Golden Sun | `gba__黄金太阳` | 伊凡是谁？ | Golden Sun entity evidence, `evidence`, `skipped` | Not run |
| Phantasy Star IV | `mega_drive__梦幻之星 IV` | 技能和魔法有什么区别？ | PS4 mechanics evidence, `evidence`, `skipped` | Not run |
| Langrisser II | `mega_drive__梦幻模拟战 II` | 指挥官怎么用？ | Langrisser mechanics/entity evidence, `evidence`, `skipped` | Not run |
| Chrono Trigger | `snes__时空之轮` | 玛尔是谁？ | Chrono Trigger entity evidence, `evidence`, `skipped` | Not run |
| Final Fantasy VI | `snes__最终幻想 VI` | 魔石有什么用？ | FF6 mechanics/item evidence, `evidence`, `skipped` | Not run |

## Screen Translation Matrix

| Game/screen | Trigger phrase | Expected display | Result |
|---|---|---|---|
| FF6 dialogue | 翻译 | Chinese-only dialogue, no English source | Not run |
| FF6 main menu | 翻译 | Bilingual lookup rows, English source + Chinese translation | Not run |
| FF6 status page | 翻译 | Labels translated, HP/MP/Level/Exp numbers preserved | Not run |
| Chrono Trigger equipment | 翻译 | Equipment slots and item names grouped, numbers preserved | Not run |
| Any multi-page result | 翻译 | Every page stays visible for 10 seconds | Not run |

## Evidence To Capture

- `/debug/hotkey-voice-overlay`
- `/debug/latest-request`
- API provider/model shown in Settings or Diagnostics for live screen translation
- Screenshot or photo only when it contains no ROM path, personal data, API key, or long copyrighted text
```

- [x] **Step 2: Add one RC case per bundled GKP if missing**

Update `scripts/hotkey_voice_qa_cases.tsv` so each bundled pack has at least one core answer case and one boundary/no-evidence case. Keep the existing TSV shape and add rows using the existing source ids from the current pack goldens.

- [x] **Step 3: Run dry-run parsing before real playback**

Run:

```bash
DRY_RUN=1 ./scripts/hotkey_voice_qa_batch.sh
SELF_TEST=1 ./scripts/hotkey_voice_qa_batch.sh
```

Expected: TSV parsing succeeds and the self-test prints `SELF TEST OK`.

## Task 5: Add Release Candidate Checklist

**Files:**
- Create: `docs/RELEASE_CANDIDATE_CHECKLIST.md`
- Modify: `README.md`
- Modify: `docs/NEXT_IMPLEMENTATION_PLAN.md`

- [x] **Step 1: Create the checklist**

Create `docs/RELEASE_CANDIDATE_CHECKLIST.md`:

```markdown
# RetroSprite Release Candidate Checklist

> Current target: M17 Release Candidate Hardening.

## Must Pass

- [ ] `./gradlew :app:testDebugUnitTest :app:assembleDebug`
- [ ] `BUILD=1 INSTALL=1 ./scripts/android_avd_smoke.sh`
- [ ] Hotkey voice matrix covers all six bundled GKP packs.
- [ ] Screen translation matrix covers dialogue, menu, status, equipment, numbers, English leakage, and 10-second paging.
- [ ] Settings has no bundled API key and recommends `Qwen/Qwen3-VL-8B-Instruct` only as a model suggestion.
- [ ] Diagnostics explains ASR, GKP, BYOK API, screenshot, timeout, no-key, and permission failures.
- [ ] README, `docs/NEXT_IMPLEMENTATION_PLAN.md`, and `docs/TEST_COVERAGE.md` describe the same default routes.

## Content And Rights

- [ ] No ROM, BIOS, save, screenshot dump, or patch file is bundled.
- [ ] No commercial guidebook prose, long walkthrough copy, full script dump, or copied fan translation is bundled.
- [ ] GKP knowledge rows are original short summaries with source ids.
- [ ] License and citation files exist for every bundled GKP.

## Release Notes

- [ ] Supported games list is exactly six bundled games unless the code changes.
- [ ] APK size and local ASR asset size are stated.
- [ ] BYOK screen translation provider setup is described without implying a bundled key.
- [ ] Known limitations include Lite GKP coverage boundaries and possible ASR recognition drift.
```

- [x] **Step 2: Link the checklist from README**

Add one row to the README docs table:

```markdown
| [docs/RELEASE_CANDIDATE_CHECKLIST.md](./docs/RELEASE_CANDIDATE_CHECKLIST.md) | M17 preview release 出包前检查清单 |
```

- [x] **Step 3: Verify docs have no stale default-route claims**

Run:

```bash
rg -n "DeepSeek-OCR|ML Kit|sample-2048|sample-relay-station|默认.*DeepSeek|默认.*ML Kit" README.md docs
```

Expected: either no matches or matches clearly marked as historical reference, not current default behavior.

## Task 6: Final RC Verification

**Files:**
- No code file changes expected.
- Evidence files may be added under `docs/qa-feedback/` after real-device runs.

- [x] **Step 1: Run local RC gate**

Run:

```bash
JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home" \
RUN_DEVICE=0 RUN_VOICE=0 ./scripts/rc_hardening_check.sh
```

Expected: pass.

- [x] **Step 2: Run device endpoint/GKP gate**

Run with a connected test device:

```bash
JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home" \
RUN_DEVICE=1 RUN_VOICE=0 ./scripts/rc_hardening_check.sh
```

Expected: app installs, endpoint is healthy, and bundled GKP debug asks pass.

2026-06-01 14:06 CST note: RG476H `RG476H01077813` passed the full device endpoint/GKP gate with `RUN_DEVICE=1 RUN_VOICE=0 ./scripts/rc_hardening_check.sh`. The run installed the current Debug APK, verified `/health`, passed endpoint smoke 7/7, and refreshed `/debug/latest-request` for the bundled GKP debug cases with `pipeline_stage=evidence` and `llm_status=skipped`. Device evidence was captured under `build/rc-device-evidence/20260601-140652/`.

2026-06-02 06:22 CST note: RG476H `RG476H01077813` passed the same device endpoint/GKP gate again with the current Debug APK. The run included 219 script tests, APK/assets/GKP snapshots, `/health`, endpoint smoke 7/7, and 23 GKP debug probes with `pipeline_stage=evidence` and `llm_status=skipped`; device evidence was captured under `build/rc-device-evidence/20260602-062203/`.

2026-06-02 06:46 CST note: RG476H `RG476H01077813` passed the device endpoint/GKP gate after the latest M18 QA-tooling updates. The run included 229 script tests, APK/assets/GKP snapshots, `/health`, endpoint smoke 7/7, and 23 GKP debug probes with `pipeline_stage=evidence` and `llm_status=skipped`; endpoint-smoke evidence was captured under `build/rc-device-evidence/20260602-064607/`.

2026-06-02 07:22 CST note: RG476H `RG476H01077813` passed the device endpoint/GKP gate after the backlog import safety hardening. The run included 239 script tests, APK/assets/GKP snapshots, `/health`, endpoint smoke 7/7, and 24 GKP debug probes with `pipeline_stage=evidence` and `llm_status=skipped`; endpoint-smoke evidence was captured under `build/rc-device-evidence/20260602-072253/`.

2026-06-02 08:04 CST note: RG476H `RG476H01077813` passed the device endpoint/GKP gate after the latest M18 QA-tooling updates. The run included 258 script tests, APK/assets/GKP snapshots, `/health`, endpoint smoke 7/7, and 23 GKP debug probes with `pipeline_stage=evidence` and `llm_status=skipped`; endpoint-smoke evidence was captured under `build/rc-device-evidence/20260602-080403/`.

2026-06-02 08:29 CST note: RG476H `RG476H01077813` passed the device endpoint/GKP gate after the current M18 handoff/tooling edits. The run included 267 script tests, APK/assets/GKP snapshots, `/health`, endpoint smoke 7/7, and 23 GKP debug probes with `pipeline_stage=evidence` and `llm_status=skipped`; endpoint-smoke evidence was captured under `build/rc-device-evidence/20260602-082904/`.

2026-06-02 09:49 CST note: RG476H `RG476H01077813` passed the device endpoint/GKP gate after adding machine-readable ASR review/handoff JSON artifacts. The run included 271 script tests, APK/assets/GKP snapshots, `/health`, endpoint smoke 7/7, and 23 GKP debug probes with `pipeline_stage=evidence` and `llm_status=skipped`; endpoint-smoke evidence was captured under `build/rc-device-evidence/20260602-094928/`.

- [ ] **Step 3: Run hotkey voice QA gate**

Run only after endpoint and overlay are confirmed ready:

```bash
RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 ./scripts/hotkey_voice_qa_batch.sh
```

Expected: every selected row writes a fresh evidence line under `build/hotkey-voice-qa/`.

2026-06-02 08:31 CST note: RG476H `RG476H01077813` ran the seven-row hotkey voice matrix after the endpoint/GKP smoke passed. Mac voice was `Tingting`; output volume was temporarily raised from 13 to 90 and restored afterward. Result is still partial: 5/7 pass, with evidence under `build/hotkey-voice-qa/20260602-083111/` and report `docs/qa-feedback/hotkey-voice-matrix-report.md`. Passing rows were Golden Sun, Chrono Trigger ATB, FF6, Langrisser II, and Phantasy Star IV. Remaining failures are Shining Force II `契河之域怎么用` source mismatch (`sf2.characters` instead of `sf2.promotion`) and Chrono Trigger `麦尔是谁` no-evidence ASR variant. Step 3 remains unchecked until `hotkey_voice_matrix_report.py --strict` passes.

- [ ] **Step 4: Record manual screen translation evidence**

Fill the `Result` column in `docs/qa-feedback/rc-device-matrix.md` for each screen translation row. For every failure, include whether the cause is API error, untranslated English, bad layout, numeric corruption, missing screenshot, no key, timeout, or overlay duration.

- [ ] **Step 5: Commit the RC hardening changes**

After all gates pass:

```bash
git add README.md docs/NEXT_IMPLEMENTATION_PLAN.md docs/TEST_COVERAGE.md docs/ARCHITECTURE_AND_PRODUCT_TIERS.md docs/superpowers/plans/2026-06-01-release-candidate-hardening.md docs/RELEASE_CANDIDATE_CHECKLIST.md docs/qa-feedback/rc-device-matrix.md scripts/rc_hardening_check.sh
git commit -m "docs: define M17 release candidate hardening plan"
```

## Self-Review

- Spec coverage: This plan covers the analysis output: function freeze, debug hook classification, RC local gate, real-device matrix, screen translation quality matrix, GKP/content rights guardrails, APK size visibility, and documentation synchronization.
- Placeholder scan: The plan contains concrete files, commands, expected outcomes, and code snippets for every implementation task.
- Type consistency: The plan uses current project names: `RetroArchHotkeyEvent.injectedQuestion`, `HotkeyVoiceQuestionController`, `ScreenTranslationStructuredResponseParser`, `OpenAiCompatibleScreenTranslationProvider`, `scripts/android_avd_smoke.sh`, and `scripts/hotkey_voice_qa_batch.sh`.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-01-release-candidate-hardening.md`.

Two execution options:

1. **Subagent-Driven (recommended)** - dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** - execute tasks in this session using executing-plans, batch execution with checkpoints.
