# Phase 0 Test Coverage

This document inventories every test that ships with Phase 0 of RetroSprite and
explains where it lives, what it covers, and how to run it.

The split is strict:

- **JVM unit tests** (`app/src/test/`) — run on plain JDK 17, no Android SDK or
  emulator needed. Use these for protocol, domain, and ktor integration.
- **Android instrumented tests** (`app/src/androidTest/`) — require a connected
  device or emulator (API 26+) because they exercise Room (SQLite + FTS5) and
  Jetpack Compose UI.

## How to run

```bash
# Pure JVM tests (fast, no emulator)
./gradlew testDebugUnitTest

# Android instrumented tests (needs device/emulator)
./gradlew connectedDebugAndroidTest

# Single test class
./gradlew testDebugUnitTest --tests com.retrosprite.app.EndToEndPipelineTest

# Optional JaCoCo coverage report (see comments in app/build.gradle.kts)
# Uncomment the jacoco block first.
./gradlew testDebugUnitTest jacocoTestReport
open app/build/reports/jacoco/jacocoTestReport/html/index.html
```

For ad-hoc protocol verification against a running device:

```bash
# bash / zsh
./scripts/test_endpoint.sh

# fish
./scripts/test_endpoint.fish

# Configurable via env vars: HOST, PORT, STRESS, NO_COLOR
PORT=8081 STRESS=200 ./scripts/test_endpoint.sh
```

## JVM unit tests (`app/src/test/kotlin/com/retrosprite/app/`)

| Test class | Owner | Coverage |
|---|---|---|
| `EndToEndPipelineTest` | Task 7 | Full HTTP → endpoint → domain → policy → logger walk via ktor `testApplication`. Verifies valid text responses, that exactly one log entry is produced per request, and that `/health` does not log. |
| `endpoint.LabelParserTest` | Task 2 | 8 cases for `LabelParser.parse`: standard, multi-delimiter, no delimiter, trailing/leading delimiter, null, blank, single underscore. |
| `endpoint.RetroArchModelTest` | Task 2/M6.9 | DTO round-trip: default state, full payload, optional debug `question` and `spoiler_level`, partial state + unknown keys, response factory output, null-field decoding. |
| `endpoint.RetroArchEndpointServerTest` | Task 2/M3.2/M7.1/M10.0 | Ktor route behavior: `/health`, happy POST + log entry, real RetroArch POST notifying the hotkey overlay listener with label/output/image bytes/paused state, debug ask not notifying the hotkey listener, RetroArch-style form Content-Type + JSON body, `/debug/ask` question route, `/debug/latest-request` latest-summary route including question metadata, malformed JSON → HTTP 200 + error, partial state defaults, missing `output` param defaults to `text`, generator failure path, `decodedBase64Length` math. |
| `endpoint.EndpointForegroundServiceTypesTest` | M10.3 | Endpoint foreground-service type policy: keeps `dataSync` only when `RECORD_AUDIO` is missing, and adds `microphone` when the app has record-audio permission so hotkey overlay voice can record while RetroArch is foreground. |
| `endpoint.HotkeyWakeResponseGeneratorTest` | M10.3 | Hotkey wake response wrapper: blank-question RetroArch requests return a silent text response so Narrator Mode does not read a no-evidence answer, while explicit questions still reach the real delegate. |
| `endpoint.EndpointEdgeCaseTest` | Task 7 | **New.** Oversized 5 MB Base64 image, non-JSON Content-Type with JSON body, empty body, exotic `output=text|sound` query param. |
| `gkp.GkpV0FixtureLintTest` | M2.1/M5.1/M5.3/M9.1 | Bundled `sample-2048`, `sample-relay-station`, and `shining-force-ii-md` GKP lint: manifest paths, JSONL parsing, id uniqueness, citations, aliases, spoiler gates, answer template refs, and minimum golden Q&A rows. |
| `gkp.GkpV0ParserTest` | M2.2/M5.3/M5.8 | Maps bundled GKP v0 files into `GameDomain` and `KnowledgeChunkDomain`, including pack id/provenance/signature defaults, answer template preservation, schema-version rejection, and the second `sample-relay-station` fixture. |
| `gkp.GkpV0PreflightValidatorTest` | M5.5/M5.8 | External GKP read-only preflight: valid sample pack passes with unsigned-pack metadata and content digest, missing license blocks, ROM/script-like files are rejected even when undeclared, and unknown source refs fail before install. |
| `gkp.ExternalGkpInstallerTest` | M5.6/M5.8 | External GKP install planning and persistence guard: creates overwrite risk summaries, records external provenance/signature/content digest, revalidates before write, replaces game knowledge rows, and rejects failed preflight input. |
| `data.resolver.RepositoryGameResolverTest` | M2.3/M5.9/M5.10 | Resolves installed enabled GKP games from RetroArch labels, normalized title/id candidates, and ROM hashes; reports disabled label/hash matches as `gkp_disabled` with no `gameId`; falls back safely when no repository match exists. |
| `data.retrieval.LocalKnowledgeRetrievalPipelineTest` | M2.3 | Covers template-first retrieval, entity/alias lookup, FTS fallback, spoiler tolerance filtering, progress-gate filtering, and empty-query short-circuiting. |
| `data.retrieval.Sample2048RetrievalGoldenTest` | M2.3/M5.1 | Runs bundled `sample-2048` `qa_goldens.jsonl` through the real parser and local retrieval funnel, including expanded opening/main-direction/full-board/undo/target/edge-chain questions, no-evidence behavior, and medium-spoiler hiding under `LIGHT`. |
| `data.retrieval.SampleRelayStationRetrievalGoldenTest` | M5.3 | Runs bundled self-authored `sample-relay-station` `qa_goldens.jsonl` through the real parser and local retrieval funnel, covering state, item, location, progress-gated, low-spoiler, medium-spoiler, and no-evidence questions. |
| `data.retrieval.SampleShiningForceIIRetrievalGoldenTest` | M9.1 | Runs bundled `shining-force-ii-md` `qa_goldens.jsonl` through the real parser and local retrieval funnel, covering game identity, early low-spoiler direction, battle/revive/promotion basics, special promotion item facts, spoiler-gated item locations, and no-evidence behavior. |
| `endpoint.QueryPipelineResponseGeneratorTest` | Task 8/M4.6/M4.7/M6.9 | Endpoint↔domain bridge: full pipeline returns ack, empty image+label tolerated, mixed output mode, request `spoiler_level` override vs Settings default provider, `RetroArchState.toFlagMap` semantics, and non-serialized LLM success/failure diagnostics propagation. |
| `endpoint.PendingQuestionResponseGeneratorTest` | M7.0 | Pending hotkey bridge: matching empty-question RetroArch requests consume the prepared question and spoiler override; explicit App/debug questions bypass the queue; mismatched labels leave pending state intact. |
| `endpoint.RoomBackedRequestLogSinkTest` | Task 8/M5.2/M7.1 | Repository-backed sink with in-memory fake: append flows through, stable request key is preserved, question/question_source and feedback fields round-trip, null system → empty string, clear empties the flow, domain/endpoint mapping symmetry. |
| `domain.DefaultQueryPipelineTest` | Task 5 | Pipeline end-to-end with all Phase 0 collaborators: typical request, empty label, null state. |
| `domain.Sample2048QuestionPipelineTest` | M2.5/M5.1/M5.10 | Full sample GKP question path without Android: `2048__` + text question returns local evidence with source id, undo/restart questions resolve locally, no-evidence questions do not call LLM, and disabled packs explain the disabled state without retrieval or LLM use. |
| `domain.SampleRelayStationQuestionPipelineTest` | M5.3 | Full second sample GKP question path without Android: `relay_station__` + item/route/no-evidence questions resolve through `RepositoryGameResolver` and local retrieval, preserving source ids and skipping LLM when evidence is deterministic. |
| `domain.SampleShiningForceIIQuestionPipelineTest` | M9.1/M9.2 | Full first real-game GKP question path without Android: `md__Shining Force II`, true-device playlist label `md__光明力量2`, and true-device RetroArch AI Service label `mega_drive__光明力量2` resolve through `RepositoryGameResolver` and local retrieval; promotion / low-spoiler route / unknown questions preserve source ids and skip LLM for deterministic evidence. |
| `domain.resolver.LabelGameResolverTest` | Task 5 | Resolver behavior for `system__game` labels. |
| `domain.policy.FixedTextAnswerPolicyTest` | Task 5 | Fixed acknowledgement text is always emitted. |
| `domain.policy.EvidenceAnswerPolicyTest` | M2.4/M2.5/M5.1/M5.10 | Local evidence policy: no-evidence uncertainty, unknown-game guard, disabled-GKP explanation, direct evidence answer, exact-template priority over LLM, source propagation, low-spoiler downgrade, and LLM composition only for multiple admissible source-backed evidence snippets. |
| `domain.policy.AnswerComposerTest` | M2.4/M2.5/M4.6/M4.7 | Direct answers append distinct source ids, source-free answers remain unchanged, evidence-backed LLM prompts include source snippets, empty-evidence compose decisions do not call LLM, LLM success/failure produce safe diagnostics, and configurable max-token budgets are applied/clamped. |
| `domain.retrieval.NoOpRetrievalPipelineTest` | Task 5 | No-op retrieval returns empty results. |
| `llm.MockLlmAdapterTest` | Task 5/M2.5 | Mock adapter returns the deterministic stub answer without evidence, and deterministic evidence summaries with citation ids when evidence is present. |
| `ui.integration.UiModelMappersTest` | Task 8/M3.1/M4.6/M5.2/M5.10/M7.1/M7.2 | Domain ↔ UI model mappers, including debug request flags, question/question_source detail JSON metadata, source id extraction, `gkp_disabled` pipeline stage, LLM status, provider/model/budget/latency diagnostics, feedback mapping, and full response text preservation for persisted conversation restore. |
| `ui.integration.RealGkpLibraryProviderTest` | M5.4/M5.7/M5.8/M5.9 | Real Packs provider mapping: combines installed games with knowledge row/source counts, maps bundled import status into UI state, displays provenance/signature/availability labels, toggles enabled state without removing knowledge rows, preserves installed packs when a bundled import reports failures, creates delete confirmation plans, and confirms delete by clearing game/knowledge rows in a transaction. |
| `ui.screens.home.HomeViewModelTest` | M4.2/M4.3/M5.2/M6.1/M6.3/M6.6/M6.7/M6.8/M6.9/M7.0/M7.1/M7.2 | Home 提问上下文：从 request log 自动采用最近真实 RetroArch label，排除 `app:*`/`debug:*`/diagnostic/失败请求，保留时间/paused/GKP evidence 摘要和 consumed question metadata，用户手动 label 可一键恢复到最近上下文；真实 RetroArch / pending hotkey 且带问题的成功日志会恢复进 conversation tray，点选记录可恢复 label/question/result 并继续追问，同时过滤 App/debug/diagnostic/失败/无问题日志；已知样例 label 会生成快捷问题草稿，点选草稿只填入输入框不提交；最近上下文可生成 host 侧 `/debug/ask` curl；回答反馈可提交到 request log provider；最近问答会进入内存 conversation tray，点选记录可恢复 label/question/result；追问草稿只填入输入框，不提交；“更明确/直接答案”追问分别设置单次 `Clear/Direct` 剧透级别 override；“直接答案”追问会设置剧透升级提示，手动编辑问题会清除提示和 override；pending hotkey 准备问题不会提交 App 问答，且会携带单次剧透 override。 |
| `ui.screens.diagnostics.DiagnosticsSourceFilterTest` | M7.3 | Diagnostics 来源分类：普通 RetroArch、pending hotkey、App 内提问、debug ask 的分类优先级；来源计数和按来源过滤函数。 |
| `ui.integration.RealPlayerQuestionProviderTest` | M4.1/M4.6/M5.2/M6.9/M7.1 | App 内文字提问入口 adapter：验证 label/question 清洗、`ResponseGenerator` 调用、`app:text` 日志写入、question_source=`app`、request log id 回传、source id/pipeline stage/LLM diagnostics 回传、单次 spoiler override 写入请求模型，以及空问题和 generator failure 错误路径。 |
| `ui.integration.RealPendingQuestionProviderTest` | M7.0 | Pending question UI adapter：验证 label/question 清洗、Settings 默认剧透级别、单次剧透 override、created timestamp 和空问题清队列。 |
| `ui.overlay.HotkeyVoiceOverlayCoordinatorTest` | M10.0 | Hotkey Wake Voice Overlay coordinator：有 overlay 权限时显示 listening overlay，缺少权限时进入 permission-required 状态且不显示，自动关闭时隐藏 overlay 并回到 idle。 |
| `ui.overlay.HotkeyVoiceQuestionControllerTest` | M10.3 | Hotkey voice loop orchestrator：热键触发后启动一次语音输入，最终转写进入 `ResponseGenerator`，答案写入 `hotkey_voice:text` / `question_source=hotkey_voice` request log 并调用 TTS；缺少 overlay 权限时不会启动录音；活动 session 中重复热键请求会被忽略；无 final transcript 超时会取消录音并隐藏 overlay。 |
| `ui.screens.settings.SettingsViewModelTest` | M10.1 | Settings overlay onboarding：刷新 overlay 权限状态、打开系统授权页动作都委托到 `OverlayPermissionProvider`。 |
| `ui.integration.SpeechOutputTextTest` | M8.1 | App 内 TTS 文本裁剪：短答案朗读只取第一句、移除 `来源：` 后的引用段，并对过长答案做长度上限截断。 |
| `ui.integration.SherpaOnnxAsrModelTest` | M8.2 | sherpa-onnx 本地 ASR 资源契约：默认模型使用中文 14M int8 streaming Zipformer assets，模型缺失时返回明确的本地模型缺失错误，且不提示系统语音或云 ASR fallback。 |
| `ui.integration.RealLlmConfigTestProviderTest` | M4.8 | Settings-only LLM smoke tester: blank API key fails without creating an adapter, successful smoke uses configured timeout/max token, and provider errors redact API keys. |
| `ui.settings.UiLlmConfigMapperTest` | M1.5/M4.7/M6.9 | Settings → runtime mapper: blank key disables real provider, DeepSeek/custom defaults are normalized, timeout seconds are passed through with conservative clamp bounds, and UI spoiler levels map to domain `LIGHT/CLEAR/FULL`. |

## Android instrumented tests (`app/src/androidTest/kotlin/com/retrosprite/app/`)

These require an emulator or device (API 26+, x86_64 system image recommended).

| Test class | Owner | Coverage |
|---|---|---|
| `data.RetroSpriteDatabaseTest` | Task 4 | Database boots with FTS5, migrations are a no-op for v1, DAOs are reachable. |
| `data.RequestLogDaoTest` | Task 4/M5.2/M7.1 | Insert, observeRecent ordering by timestamp DESC, question/question_source persistence, count, clear, and local feedback update by stable request key. |
| `data.GameDaoTest` | Task 4/M5.9 | CRUD + lookup by rom hash / label, plus reversible enable/disable persistence. |
| `data.KnowledgeDaoTest` | Task 4 | Knowledge entry CRUD + spoiler-level filter. |
| `data.KnowledgeFtsDaoTest` | Task 4 | FTS5 MATCH queries — verifies tokenizer + ranking. |
| `data.RetroSpriteDatabaseMigrationTest` | M5.8/M5.9/M7.1 | Room v3→v5 migration: backfills legacy bundled sample `pack_id`/`provenance`, marks unknown legacy packs as external, sets default unsigned signature state, and defaults packs to enabled with no disabled timestamp. Room v5→v6 migration adds nullable request-log `question` and `question_source` columns without disturbing existing rows. |
| `ui.integration.SherpaOnnxRecognizerAndroidTest` | M8.2 | Device/AVD sherpa-onnx native smoke: initializes the bundled default ASR model from APK assets through `libsherpa-onnx-jni.so` + `libonnxruntime.so`, then releases the recognizer. Catches missing native libs or missing model assets before manual voice QA. |
| `ui.RetroSpriteAppSmokeTest` | Task 3/M4.4/M4.5/M4.7/M4.8/M5.2/M5.4/M5.5/M5.6/M5.7/M5.8/M5.9/M5.10/M6.1/M6.2/M6.3/M6.4/M6.5/M6.6/M6.7/M6.8/M7.0/M7.2/M7.3/M8.1/M8.3/M10.1 | Compose smoke test: four bottom tabs visible, Diagnostics / Packs / Settings each renders its distinctive headline. Packs now verifies import status, external preflight result, install/overwrite plan, confirmation button, delete confirmation plan, provenance/signature display, enable/disable controls, plus bundled `sample.2048` and `Relay Station` rows. Also covers Home text question flow, App 内 fake 语音输入填充问题框、短答朗读按钮渲染, 本地 ASR 首次模型加载提示、空识别提示, quick question drafts filling the input without auto-submit, pending hotkey prepare/cancel without writing request log, pending hotkey request-log restore into Home conversation tray, Diagnostics source filter count bar rendering, recent-context action buttons, disabled-GKP Home/Diagnostics explanation, input source status, submit, show answer/source, conversation tray record/restore, conversation follow-up draft filling without a new request log, direct-answer spoiler escalation notice, recovery hint for LLM failure and its Settings navigation action, GKP-disabled recovery navigation to Packs, request-error recovery navigation to Diagnostics, submit answer feedback, switch to Diagnostics and verify the `APP` request log and feedback tags, fake LLM timeout surfacing in Home and Diagnostics provider/model/error diagnostics, Settings LLM self-test result without request-log writes, and Settings overlay permission onboarding section rendering. |

The UI smoke test now includes the M4.4 Home → Diagnostics flow, the M4.7
fake timeout failure path, the M4.8 Settings self-test result path, the M5.2
local feedback path, the M5.4 real Packs tab state, the M5.5 external GKP
preflight result, the M5.6 install confirmation plan, and the M5.7 delete
confirmation plan, the M5.8 provenance/signature labels, the M5.9 enable/disable controls, the M5.10 disabled-GKP explanation path, the M6.1 quick question draft path, the M6.2 recovery hint path, the M6.3 recent-context action bar, the M6.4 recovery action navigation path, the M6.5 all-target recovery smoke, the M6.6 in-memory conversation tray, the M6.7 follow-up draft buttons, the M6.8 direct-answer spoiler escalation notice, the M7.0 pending hotkey prepare/cancel path, the M7.2 persisted pending-hotkey conversation restore path, the M7.3 Diagnostics source count bar path, the M8.1 fake speech input/TTS affordance path, and the M8.3 local-ASR status polish path for first model load and empty recognition, using injected test doubles so it stays deterministic and
does not require the real endpoint, DeepSeek, microphone, or sherpa-onnx runtime. M6.9's strategy-level spoiler
override and M7.0's endpoint consumption wrapper are covered by the JVM
unit/integration tests listed above.

## Manual device records

- **2026-05-20, RG 476H true-device M8.3 voice loop:** tapped Home “语音输入”, said “两个 2 怎么合并？”, sherpa-onnx filled the question box with “两个二怎么合并”; tapping “提问” returned the correct `sample-2048` GKP answer. This validates that ASR text normalization is not needed for the current 2048 path.
- **2026-05-20, RG 476H true-device M8.4 voice regression:** all 5 manual voice questions passed: 2048 cold start, 2048 warm start, empty-recognition prompt, Relay Station sample, and a final 2048 regression. This confirms the local sherpa-onnx voice input path is stable enough to move on to real-game GKP content work without adding ASR text normalization.
- **2026-05-20, RetroSprite_API_34 M9.1 runtime GKP smoke:** installed the Debug APK with bundled `community.shining-force-ii-md`, then `/debug/ask` with `label="md__Shining Force II"` returned local evidence answers for “什么时候转职？” (`sf2.promotion`) and “不要剧透下一步去哪？” (`sf2.early_route`).
- **2026-05-20, RG 476H M9.2 RetroArch label preflight/runtime smoke:** read the loaded MD/Genesis playlist entry from the device: ROM path `/storage/4A21-0000/Roms/MD/光明力量2.md`, `label=光明力量2`, core `Genesis Plus GX`. Installed the updated Debug APK and verified `/debug/ask` with `label="md__光明力量2"` returns local evidence for “什么时候转职？” (`sf2.promotion`) and “不要剧透下一步去哪？” (`sf2.early_route`). A later true-device AI Service request arrived as `label="mega_drive__光明力量2"`, so that label was added to the GKP regression. A per-core/config-file edit experiment was restored to defaults. Current product default is RetroArch's AI Service URL `http://localhost:4404`; final acceptance still requires reloading the game and pressing the physical hotkey.
- **2026-05-20, M9.3 Settings RetroArch setup helper:** removed the in-app cfg writer after true-device EACCES confirmed Android ownership/scoped-storage risk. Settings now only shows the RetroArch path/recommended values and the RetroArch default AI Service URL `http://localhost:4404`; no cfg write path, advanced cfg snippet, hotkey mutation, or broad storage permission remains.
- **2026-05-20, M10.0 Hotkey Wake Voice Overlay scaffold:** endpoint and coordinator JVM tests now prove real RetroArch `POST /` requests emit hotkey events and can drive a short overlay cue; debug requests do not trigger the overlay path. True-device acceptance still needs Android overlay permission and physical hotkey validation on RG 476H.
- **2026-05-20, M10.1/M10.3 Hotkey voice loop scaffold:** Settings now exposes overlay permission onboarding; hotkey voice orchestration is covered by JVM tests for ASR transcript → GKP pipeline call → `hotkey_voice` log → TTS. True-device acceptance still needs loading Shining Force II in RetroArch, pressing the physical hotkey, speaking a question, and confirming `sf2.promotion` in Diagnostics.
- **2026-05-20, RG 476H M10.3 hotkey overlay bugfix smoke:** pressing AI Service hotkey previously showed the waveform but did not answer and stayed visible. Logcat root cause: background `AudioRecord` was silenced by foreground-only `RECORD_AUDIO` AppOps, and the timeout path waited forever on an uncancelled final-transcript coroutine. Debug APK now declares/uses foreground-service `microphone` type when record-audio is granted, ignores repeated wake requests while a session is active, and cancels the final-transcript waiter on timeout. Host-side background POST smoke confirmed zero `silencing record` / `Operation not started RECORD_AUDIO` lines and zero RetroSprite overlay windows after the timeout. Full spoken-answer acceptance still needs the user to press the physical RetroArch hotkey and speak a Shining Force II question.
- **2026-05-20, RG 476H M10.2 hotkey voice true-device pass #1:** in RetroArch with MD/Genesis Shining Force II loaded, the user pressed the configured AI Service hotkey and said “什么时候转职？”. The top-right waveform appeared, the voice loop completed, and TTS spoke the result. `/debug/latest-request` confirmed a real `hotkey_voice:text` request with `label="mega_drive__光明力量2"` and `question_source="hotkey_voice"`. The latest ASR transcript was “接受他几部这个角色”, so the pipeline correctly returned `no_evidence`; this validates the hotkey/overlay/ASR/TTS mechanics but leaves the `sf2.promotion` evidence-hit acceptance open for one clearer repeat phrase.

## End-to-end shell scripts (`scripts/`)

| Script | Shell | What it does |
|---|---|---|
| `test_endpoint.sh` | bash 3.2+ (macOS-safe) | 4 checks: `/health` probe, happy POST asserting `text` and no `error`, malformed JSON expects HTTP 200 + error, 100-shot stress test reporting avg latency. Colored PASS/FAIL summary. Exit `0` (all pass) / `1` (any fail) / `2` (curl missing). |
| `test_endpoint.fish` | fish | Functional equivalent of `.sh` version. |
| `android_avd_smoke.sh` | bash 3.2+ (macOS-safe) | Device/AVD smoke: verifies adb online, optionally builds, auto-installs missing Debug APK, starts `com.retrosprite.app`, forwards host/device ports, runs `test_endpoint.sh`, posts `sample-2048` and `sample-relay-station` `/debug/ask` probes, then checks `/debug/latest-request` after each probe for `pipeline_stage=evidence`, `llm_status=skipped`, and the expected source id. This is the host-side counterpart to Home's generated debug curl path. |
| `sample_payload.json` | n/a | Reference request body for manual `curl`. |

## CI placeholder (`.github/workflows/ci.yml`)

Three jobs are wired but the workflow only runs once a GitHub remote exists:

- **lint** — placeholder, ktlint is commented out until the plugin is added.
- **unit-test** — `./gradlew testDebugUnitTest`, uploads HTML report.
- **build** — `./gradlew assembleDebug`, uploads the debug APK.

Instrumented tests are intentionally not wired — they need an emulator that is
slow/flaky on stock GitHub runners. Add a separate job with
`reactivecircus/android-emulator-runner` when ready.

## Known limitations

- Use Android Studio's bundled JBR if the system Java version is older than the
  Android Gradle Plugin expects:
  `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- `test_endpoint.sh` and `test_endpoint.fish` assume the endpoint is already
  running — start the app on a device/emulator and run
  `adb forward tcp:4404 tcp:4404` before invoking them. Use
  `scripts/android_avd_smoke.sh` when you want the script to start the app and
  create the port forward for you.
- Automated tests do not hit the real DeepSeek API. Use
  `scripts/deepseek_live_smoke.sh` manually when validating BYOK credentials.
