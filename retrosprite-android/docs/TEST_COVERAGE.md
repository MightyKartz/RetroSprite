# RetroSprite Test Coverage

This document inventories the current automated and manual test gates for RetroSprite and
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

# Current RC local gate
./gradlew :app:testDebugUnitTest :app:assembleDebug

# Current RC scripted gate with APK/GKP snapshots
./scripts/rc_hardening_check.sh

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

## M17 release-candidate gate

The active next milestone is **M17 Release Candidate Hardening**. A build is not
ready for preview release unless these gates are green:

| Gate | Command or evidence | Pass condition |
|---|---|---|
| JVM + build | `./gradlew :app:testDebugUnitTest :app:assembleDebug` | All JVM tests pass and `app-debug.apk` is produced. |
| Endpoint smoke | `BUILD=1 INSTALL=1 ./scripts/android_avd_smoke.sh` | `/health`, protocol smoke, and all bundled GKP debug asks pass. |
| Hotkey voice matrix | `RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 ./scripts/hotkey_voice_qa_batch.sh` | Each selected case records `finish_reason=answer_completed` or an expected no-evidence boundary, with correct `question_source`, `pipeline_stage`, `llm_status`, and `source_ids`. |
| Screen translation matrix | Manual or scripted hotkey requests using real screenshots | Dialogue shows only Chinese translation; menu/status/equipment/inventory shows bilingual lookup rows; numbers remain unchanged; every page stays visible for 10 seconds. |
| BYOK screen translation | Real SiliconFlow/OpenRouter/self-hosted compatible API test with user-provided key | Settings template works, API key is not logged, failures surface as actionable overlay text. |
| GKP copyright/provenance | Review `app/src/main/assets/gkp/*/sources/licenses.md` and `sources/citations.jsonl` | No ROM, BIOS, copied guidebook text, full script dumps, copied fan-translation scripts, or bundled patch text. |
| Documentation sync | README, `NEXT_IMPLEMENTATION_PLAN.md`, this file, and release notes | Current default route is local Paraformer ASR + GKP + optional BYOK Qwen3-VL-style screen translation; removed routes are not described as defaults. |

Latest local verification:

- 2026-06-01: `JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home" ./gradlew :app:testDebugUnitTest :app:assembleDebug` passed.
- Current bundled content snapshot: 6 GKP packs, about 347 knowledge rows and 337 QA goldens.
- Current debug APK snapshot: about 251 MB; bundled Paraformer ASR assets account for about 226 MB.
- 2026-06-01 RG476H device endpoint/GKP smoke passed, but real hotkey voice playback failed before GKP retrieval with `muted_recovery` / `blank_partial`. Evidence: `build/hotkey-voice-qa/20260601-144348/`; triage: `docs/qa-feedback/hotkey-voice-lifecycle-failure-20260601.md`.
- 2026-06-01 M17.1 diagnostic patch verification passed: focused JVM tests for hotkey overlay/debug endpoint audio counters, focused `DiagnosticsSourceFilterTest` coverage for ASR/GKP/BYOK API/screenshot/timeout/no-key/permission explanations, `./gradlew :app:assembleDebug`, `python3 -m unittest discover scripts/tests`, `python3 scripts/rc_release_audit.py`, and `git diff --check`.
- 2026-06-01 RG476H M17.1 recovery follow-up: current Debug APK installed and `PORT=18080 ./scripts/test_endpoint.sh` passed 7/7. `golden_sun_ivan_observed` failed at Mac output volume 13 with `peak=0.0060507967`, then passed at volume 90. The 7-case playback matrix at volume 90 reached fresh `hotkey_voice` submissions for every row and first narrowed the failure from lifecycle to scoped GKP/ASR issues.
- 2026-06-02 RG476H endpoint/GKP smoke recheck passed with the current Debug APK: `RUN_DEVICE=1 RUN_VOICE=0 ./scripts/rc_hardening_check.sh` completed `OK M17 RC hardening gate completed`, script tests were 232/232, endpoint smoke was 7/7, 23 GKP debug probes refreshed `/debug/latest-request`, and evidence was captured in `build/rc-device-evidence/20260602-065909/`.
- 2026-06-02 M18 hotkey voice matrix status: the selected seven-row RG476H playback matrix is 5/7 pass. The two remaining rows are `sf2_vigor_ball_observed` (`source_mismatch`) and `chrono_marle_observed` (`asr_variant`), recorded in `docs/qa-feedback/hotkey-voice-matrix-report.md` with evidence under `build/hotkey-voice-qa/20260602-083111/`.

## M17.1 hotkey voice lifecycle recovery gate

M17.1 is the immediate release-blocking follow-up to the 2026-06-01 playback failure. It must recover the real-device hotkey voice path before M17 preview release can be checked off.

| Gate | Command or evidence | Pass condition |
|---|---|---|
| Failure evidence preserved | `docs/qa-feedback/hotkey-voice-lifecycle-failure-20260601.md` | The 7-case `muted_recovery` / `blank_partial` failure is classified as voice lifecycle, not GKP coverage. |
| Audio capture diagnostics | `/debug/hotkey-voice-overlay` during playback | Snapshot includes sample/read counters, peak amplitude, last-frame amplitude, and read error count so silence vs recognizer blank can be separated. |
| One-case recovery probe | `RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 CASE_FILTER=golden_sun_ivan_observed ... ./scripts/hotkey_voice_qa_batch.sh` | A fresh `/debug/latest-request` is submitted with `question_source=hotkey_voice`. |
| Seven-case playback matrix | `RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 CASE_FILTER=sf2_vigor_ball_observed,golden_sun_ivan_observed,chrono_marle_observed,chrono_atb_observed,ff6_magicite_observed,langrisser_commander_smoke,phantasy_star_tech_skill_smoke STRICT=1 ./scripts/hotkey_voice_qa_batch.sh` | Every case reaches expected evidence or a narrower ASR/GKP failure; no row remains `muted_recovery` / `blank_partial` with stale latest-request. |
| Regression checks | Focused JVM tests, `python3 -m unittest discover scripts/tests`, `python3 scripts/rc_release_audit.py`, `git diff --check` | Tests and audit pass after the lifecycle fix. |

Current implementation status: audio capture diagnostics and the QA readiness wait are implemented and regression-tested locally. Real-device recovery advanced from lifecycle failure to a 5/7 hotkey voice matrix with two scoped GKP/ASR quality failures; the gate remains open until those rows are fixed, replayed on device, and the 7-case playback matrix passes.

## M18 planned quality gates

M18 starts after the M17 preview-release gate is complete. Its purpose is to
measure and improve answer quality without expanding the product surface.

| Gate | Command or evidence | Pass condition |
|---|---|---|
| GKP coverage report | `python3 scripts/gkp_eval_report.py --gkp-dir app/src/main/assets/gkp --output docs/qa-feedback/m18-eval-report.md` | All six bundled packs appear with row/golden counts and lane status for identity, core gameplay, first hour, mechanics, menu terms, items/skills/magic, names/aliases, common blockers, low-spoiler next step, no-evidence boundary, ASR variants, and citations. |
| Gap backlog export | `python3 scripts/gkp_gap_backlog.py --input build/rc-device-evidence --output docs/qa-feedback/gkp-quality-backlog.md`; `python3 scripts/gkp_gap_backlog.py --manual-notes-template-output docs/qa-feedback/gkp-manual-notes-template.tsv` | No-evidence, wrong-answer, ASR variant, ranking, spoiler-gate, voice-lifecycle, and translation failures are grouped with suggested GKP areas and regression targets. Current M18 inputs are `/debug/latest-request` JSON, voice QA `results.tsv`, JSONL exports, and manual tester notes TSV files. The template command creates a safe starter TSV with example rows that do not become backlog items until testers replace them with real failed observations. Active backlog imports from `BACKLOG_INPUT` or manual notes must use `--merge-existing-backlog docs/qa-feedback/gkp-quality-backlog.md` so new observations are merged without dropping active ASR backlog items. |
| GKP backlog triage report | `python3 scripts/gkp_backlog_triage_report.py --output docs/qa-feedback/gkp-backlog-triage-report.md --strict` | Splits current backlog rows into non-blocking ASR review-packet coverage, device rerun, device rerun passed, screen translation follow-up, existing policy golden, new policy golden, retrieval golden, or GKP triage buckets. Existing low-spoiler boundary goldens are detected from bundled `qa_goldens.jsonl`, and later passing hotkey voice results close non-patch lifecycle rerun rows without proposing GKP asset edits. Strict mode fails if a backlog row is unclassified, so new failure types must gain an explicit next step before the M18 offline gate passes. |
| GKP patch safety | `python3 scripts/gkp_patch_assistant.py ...` plus GKP JVM tests | The assistant defaults to dry-run proposal output, rejects rights-unsafe content, and every approved patch adds or updates a golden before being accepted. |
| GKP patch dry-run gate | `python3 scripts/gkp_patch_apply_review_packet.py --strict` | Patch review packets are dry-run applied first, duplicate-checked, and keep `Assets edited: no`; applying a patch still requires explicit user approval, but manual ASR approval is no longer an M18 gate. |
| GKP asset mutation guard | `python3 scripts/gkp_asset_mutation_guard.py --output docs/qa-feedback/gkp-asset-mutation-guard.md --strict` | Bundled GKP assets remain clean by default. If the user explicitly approves an exact patch, dirty paths must be limited to the exact `aliases.json` and `qa_goldens.jsonl` files listed in the review packet. |
| GKP patch regression gate | `RUN_REPORTS=1 ./scripts/gkp_patch_regression_gate.sh` | After an approved patch is applied, focused GKP JVM tests, release audit, GKP asset mutation guard, report refreshes, and `git diff --check` pass. Real-device replay is opt-in with `RUN_VOICE=1`. When `BACKLOG_INPUT` is supplied during report refresh, it merges into the active backlog instead of replacing existing rows. |
| GKP ASR voice replay handoff | `python3 scripts/gkp_asr_patch_voice_handoff.py --output docs/qa-feedback/gkp-asr-patch-voice-replay-handoff.md` | Generates the dry-run/apply/regression/voice-replay handoff for failed ASR variants without editing GKP assets or playing audio. Review-packet rows are non-blocking M18 artifacts unless the user explicitly approves an exact GKP patch. |
| Hotkey voice matrix report | `python3 scripts/hotkey_voice_matrix_report.py --output docs/qa-feedback/hotkey-voice-matrix-report.md` | Converts the selected seven-row real-device voice playback `results.tsv` into a pass/fail matrix with transcripts, source ids, ASR counters, and failure categories; default result selection prefers the file covering the most selected matrix rows over newer single-case diagnostics, and strict mode stays nonzero until all selected rows pass. |
| M18 script tests | `python3 -m unittest discover scripts/tests` | All release, evidence, eval, backlog, and patch-assistant script tests pass without Android, adb, or network. |
| M18 status report | `python3 scripts/m18_status_report.py --output docs/qa-feedback/m18-status-report.md` | Aggregates machine-checkable GKP coverage, triage-aware backlog status, patch proposal/review/apply dry-run, asset guard, ASR handoff, hotkey voice, command-contract, and quality-loop handoff status. ASR review-packet rows are non-blocking artifacts; screen translation manual matrix, content-rights human confirmation, release checklist, and release checklist guard are not M18 aggregate rows. |
| M18 gate status JSON | `python3 scripts/m18_gate_status_json.py --output docs/qa-feedback/m18-gate-status.json` | Emits machine-readable M18 gate status with the same aggregate rows as `m18_status_report.py`, including quality-loop handoff readiness, counts, open areas, and an overall pass/open state for CI or automation consumers. |
| M18 plan execution audit | `python3 scripts/m18_plan_execution_audit.py --output docs/qa-feedback/m18-plan-execution-audit.md --json-output docs/qa-feedback/m18-plan-execution-audit.json` | Cross-checks the active main M18 implementation plan against `m18-status-report.md`, skips fenced checkbox examples, writes machine-readable checkbox / aggregate / open-gate counts, and keeps strict mode failing while any plan checkbox or aggregate status row is open. The superseded approval-gated plan is no longer part of the default audit. |
| M18 remaining gate handoff | `python3 scripts/m18_remaining_gate_packet.py --output docs/qa-feedback/m18-remaining-gate-handoff.md` | Produces a single handoff for remaining machine/device M18 gates, currently centered on hotkey voice matrix evidence and aggregate completion. It explicitly records that manual ASR approval, the five-row screen translation manual matrix, and human content-rights confirmation are removed from M18 scope. |
| M18 completion audit | `python3 scripts/m18_completion_audit.py --output docs/qa-feedback/m18-completion-audit.md --json-output docs/qa-feedback/m18-completion-audit.json` | Audits the full objective requirement-by-requirement and writes machine-readable status: plan checkbox closure, every aggregate gate, GKP asset safety, remaining machine/device evidence, and final `EXPECT_ALL_PASS=1` eligibility. It reads the sibling plan execution JSON before falling back to Markdown checkbox counts. Strict mode fails until completion is proven. |
| M18 next action queue | `python3 scripts/m18_next_action_queue.py --output docs/qa-feedback/m18-next-action-queue.md --json-output docs/qa-feedback/m18-next-action-queue.json` | Converts open machine/device gates into concrete action IDs with owner, ready/blocked status, blockers, evidence, commands, and acceptance criteria. JSON includes `action_ids_by_status`; current M18 queue no longer emits human ASR approval, screen translation manual QA, or content-rights review actions. |
| M18 quality loop handoff | `python3 scripts/m18_quality_loop_handoff.py --output docs/qa-feedback/m18-quality-loop-handoff.md --json-output docs/qa-feedback/m18-quality-loop-handoff.json` | Generates the ongoing quality-loop handoff from `m18-gate-status.json` and `m18-next-action-queue.json`. It records the current open/ready state, preview-first backlog import commands for latest-request JSON, hotkey voice `results.tsv`, and manual notes TSV, plus fix acceptance rules requiring source ids, goldens, release audit, and real-device replay when applicable. It edits no GKP assets and is refreshed by the offline gate. |
| M18 command contract audit | `python3 scripts/m18_command_contract_audit.py --output docs/qa-feedback/m18-command-contract-audit.md --strict` | Audits current generated M18 command snippets and docs, including next-action queue, ASR replay handoff, quality-loop JSON, plan execution JSON, completion audit JSON, remaining handoff, offline gate, GKP patch regression gate, `README.md`, Architecture, the M18 main plan, `NEXT_IMPLEMENTATION_PLAN.md`, and this file. It checks action queue consistency, quality-loop JSON consistency, remaining handoff removed-scope flags, active-backlog merge protection, GKP patch apply safety, stale ASR wording, completion JSON consistency, and the offline gate safe default containing no apply mode. |
| M18 offline quality gate | `./scripts/m18_offline_quality_gate.sh` | Refreshes M18 machine-checkable reports and handoff packets, runs script tests, release audit, and whitespace checks, and confirms strict completion probes remain open unless `EXPECT_ALL_PASS=1` is set after remaining machine/device gates are complete. It no longer refreshes or probes the removed manual ASR approval, screen translation manual matrix, or human content-rights gates. |

## JVM unit tests (`app/src/test/kotlin/com/retrosprite/app/`)

| Test class | Owner | Coverage |
|---|---|---|
| `EndToEndPipelineTest` | Task 7 | Full HTTP → endpoint → domain → policy → logger walk via ktor `testApplication`. Verifies valid text responses, that exactly one log entry is produced per request, and that `/health` does not log. |
| `endpoint.LabelParserTest` | Task 2 | 8 cases for `LabelParser.parse`: standard, multi-delimiter, no delimiter, trailing/leading delimiter, null, blank, single underscore. |
| `endpoint.RetroArchModelTest` | Task 2/M6.9 | DTO round-trip: default state, full payload, optional debug `question` and `spoiler_level`, partial state + unknown keys, response factory output, null-field decoding. |
| `endpoint.RetroArchEndpointServerTest` | Task 2/M3.2/M7.1/M10.0/M17.1 | Ktor route behavior: `/health`, happy POST + log entry, real RetroArch POST notifying the hotkey overlay listener with label/output/image bytes/paused state, debug ask not notifying the hotkey listener, RetroArch-style form Content-Type + JSON body, `/debug/ask` question route, `/debug/latest-request` latest-summary route including question metadata, `/debug/hotkey-voice-overlay` audio capture counters, malformed JSON → HTTP 200 + error, partial state defaults, missing `output` param defaults to `text`, generator failure path, `decodedBase64Length` math. |
| `endpoint.EndpointForegroundServiceTypesTest` | M10.3 | Endpoint foreground-service type policy: keeps `dataSync` only when `RECORD_AUDIO` is missing, and adds `microphone` when the app has record-audio permission so hotkey overlay voice can record while RetroArch is foreground. |
| `endpoint.HotkeyWakeResponseGeneratorTest` | M10.3 | Hotkey wake response wrapper: blank-question RetroArch requests return a silent text response so Narrator Mode does not read a no-evidence answer, while explicit questions still reach the real delegate. |
| `endpoint.EndpointEdgeCaseTest` | Task 7 | **New.** Oversized 5 MB Base64 image, non-JSON Content-Type with JSON body, empty body, exotic `output=text|sound` query param. |
| `gkp.GkpV0FixtureLintTest` | M2.1/M5.1/M5.3/M9.1 | Bundled real GKP lint for `shining-force-ii-md` plus the Retro JRPG/SRPG Chinese Lite packs: manifest paths, JSONL parsing, id uniqueness, citations, aliases, spoiler gates, answer template refs, and minimum golden Q&A rows. |
| `gkp.GkpV0ParserTest` | M2.2/M5.3/M5.8 | Maps bundled GKP v0 files into `GameDomain` and `KnowledgeChunkDomain`, including pack id/provenance/signature defaults, answer template preservation, schema-version rejection, `shining-force-ii-md`, and `golden-sun-gba-zh`. |
| `gkp.GkpV0PreflightValidatorTest` | M5.5/M5.8 | External GKP read-only preflight: valid Golden Sun Lite pack passes with unsigned-pack metadata and content digest, missing license blocks, ROM/script-like files are rejected even when undeclared, and unknown source refs fail before install. |
| `gkp.ExternalGkpInstallerTest` | M5.6/M5.8 | External GKP install planning and persistence guard: creates overwrite risk summaries, records external provenance/signature/content digest, revalidates before write, replaces game knowledge rows, and rejects failed preflight input. |
| `data.resolver.RepositoryGameResolverTest` | M2.3/M5.9/M5.10 | Resolves installed enabled GKP games from RetroArch labels, normalized title/id candidates, and ROM hashes; reports disabled label/hash matches as `gkp_disabled` with no `gameId`; falls back safely when no repository match exists. |
| `data.retrieval.LocalKnowledgeRetrievalPipelineTest` | M2.3 | Covers template-first retrieval, entity/alias lookup, FTS fallback, spoiler tolerance filtering, progress-gate filtering, and empty-query short-circuiting. |
| `data.retrieval.SampleShiningForceIIRetrievalGoldenTest` | M9.1 | Runs bundled `shining-force-ii-md` `qa_goldens.jsonl` through the real parser and local retrieval funnel, covering game identity, early low-spoiler direction, battle/revive/promotion basics, special promotion item facts, spoiler-gated item locations, and no-evidence behavior. |
| `data.retrieval.RetroJrpgSrpgPackRetrievalGoldenTest` | M11.0 | Runs the five Retro JRPG/SRPG Chinese Lite packs through the real parser and local retrieval funnel, covering low-spoiler identity, core mechanics, localized names, item/location terms, boss/enemy buckets, and no-evidence boundaries. |
| `endpoint.QueryPipelineResponseGeneratorTest` | Task 8/M4.6/M4.7/M6.9 | Endpoint↔domain bridge: full pipeline returns ack, empty image+label tolerated, mixed output mode, request `spoiler_level` override vs Settings default provider, `RetroArchState.toFlagMap` semantics, and non-serialized LLM success/failure diagnostics propagation. |
| `endpoint.PendingQuestionResponseGeneratorTest` | M7.0 | Pending hotkey bridge: matching empty-question RetroArch requests consume the prepared question and spoiler override; explicit App/debug questions bypass the queue; mismatched labels leave pending state intact. |
| `endpoint.RoomBackedRequestLogSinkTest` | Task 8/M5.2/M7.1 | Repository-backed sink with in-memory fake: append flows through, stable request key is preserved, question/question_source and feedback fields round-trip, null system → empty string, clear empties the flow, domain/endpoint mapping symmetry. |
| `domain.DefaultQueryPipelineTest` | Task 5 | Pipeline end-to-end with all Phase 0 collaborators: typical request, empty label, null state. |
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
| `ui.screens.diagnostics.DiagnosticsSourceFilterTest` | M7.3/M17 | Diagnostics 来源分类：普通 RetroArch、pending hotkey、App 内提问、debug ask 的分类优先级；来源计数和按来源过滤函数；失败解释覆盖 ASR、GKP disabled/no-evidence、BYOK API、截图缺失、超时、No-key 和权限问题。 |
| `ui.integration.RealPlayerQuestionProviderTest` | M4.1/M4.6/M5.2/M6.9/M7.1 | App 内文字提问入口 adapter：验证 label/question 清洗、`ResponseGenerator` 调用、`app:text` 日志写入、question_source=`app`、request log id 回传、source id/pipeline stage/LLM diagnostics 回传、单次 spoiler override 写入请求模型，以及空问题和 generator failure 错误路径。 |
| `ui.integration.RealPendingQuestionProviderTest` | M7.0 | Pending question UI adapter：验证 label/question 清洗、Settings 默认剧透级别、单次剧透 override、created timestamp 和空问题清队列。 |
| `ui.overlay.HotkeyVoiceOverlayCoordinatorTest` | M10.0/M17.1 | Hotkey Wake Voice Overlay coordinator：有 overlay 权限时显示 listening overlay，缺少权限时进入 permission-required 状态且不显示，自动关闭时隐藏 overlay 并回到 idle；debug snapshot 保留 ASR commit 状态和 AudioRecord sample/read/error/peak/last-frame amplitude counters。 |
| `ui.overlay.HotkeyVoiceQuestionControllerTest` | M10.3/M17/M17.1 | Hotkey voice loop orchestrator：热键触发后启动一次语音输入，最终转写进入 `ResponseGenerator`，答案写入 `hotkey_voice:text` / `question_source=hotkey_voice` request log 并调用 TTS；缺少 overlay 权限时不会启动录音；活动 session 中重复热键请求会被忽略；无 final transcript 超时会取消录音并隐藏 overlay；短语“翻译”/“翻译一下”进入 screen translation 路径；screen translation 多页结果每页停留 10 秒后再切换或结束；语音 session 的 AudioRecord capture counters 会进入 overlay debug snapshot。 |
| `ui.screens.settings.SettingsViewModelTest` | M10.1 | Settings overlay onboarding：刷新 overlay 权限状态、打开系统授权页动作都委托到 `OverlayPermissionProvider`。 |
| `ui.integration.SpeechOutputTextTest` | M8.1 | App 内 TTS 文本裁剪：短答案朗读只取第一句、移除 `来源：` 后的引用段，并对过长答案做长度上限截断。 |
| `ui.integration.SherpaOnnxAsrModelTest` | M8.2 | sherpa-onnx 本地 ASR 资源契约：默认模型使用 `sherpa-onnx-streaming-paraformer-bilingual-zh-en` Paraformer int8 assets，模型缺失时返回明确的本地模型缺失错误，且不提示系统语音或云 ASR fallback。 |
| `ui.integration.SherpaOnnxRecognizerFactoryTest` | M8.2/M16 | sherpa-onnx Paraformer recognizer config contract: default decoding is `greedy_search`, native hotword fields stay empty, Paraformer encoder/decoder/tokens point at the bundled model, Transducer paths stay empty, endpoint trailing silence is explicit, and final flush silence remains enabled. |
| `ui.integration.SherpaEndpointCommitGateTest` | M16 | Hotkey voice endpoint commit strategy: waits for sherpa endpoint detection, voice inactivity, and stable partial text before submitting; continued speech and growing partial text delay commit; blank/non-endpoint audio does not commit; incomplete question tails receive a small extra wait without inventing missing words. |
| `ui.integration.SherpaFinalTranscriptSelectorTest` | M16 | Final/partial transcript selection: keeps complete final text, prefers a longer compatible question-shaped partial when the final drops a tail character, rejects unrelated partials, and deliberately does not complete clipped text such as `是什` or `玩什`. |
| `ui.integration.VoiceSampleFanOutTest` | M10/M16 | Voice sample fan-out contract for the shared ASR/waveform path: multiple consumers receive the same sample stream without coupling the live waveform display to ASR capture behavior. |
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
| `ui.RetroSpriteAppSmokeTest` | Task 3/M4.4/M4.5/M4.7/M4.8/M5.2/M5.4/M5.5/M5.6/M5.7/M5.8/M5.9/M5.10/M6.1/M6.2/M6.3/M6.4/M6.5/M6.6/M6.7/M6.8/M7.0/M7.2/M7.3/M8.1/M8.3/M10.1 | Compose smoke test: four bottom tabs visible, Diagnostics / Packs / Settings each renders its distinctive headline. Packs verifies import status, external preflight result, install/overwrite plan, confirmation button, delete confirmation plan, provenance/signature display, enable/disable controls, and bundled real-GKP rows. Also covers Home text question flow, App 内 fake 语音输入填充问题框、短答朗读按钮渲染, 本地 ASR 首次模型加载提示、空识别提示, quick question drafts filling the input without auto-submit, pending hotkey prepare/cancel without writing request log, pending hotkey request-log restore into Home conversation tray, Diagnostics source filter count bar rendering, recent-context action buttons, disabled-GKP Home/Diagnostics explanation, input source status, submit, show answer/source, conversation tray record/restore, conversation follow-up draft filling without a new request log, direct-answer spoiler escalation notice, recovery hint for LLM failure and its Settings navigation action, GKP-disabled recovery navigation to Packs, request-error recovery navigation to Diagnostics, submit answer feedback, switch to Diagnostics and verify the `APP` request log and feedback tags, fake LLM timeout surfacing in Home and Diagnostics provider/model/error diagnostics, Settings LLM self-test result without request-log writes, and Settings overlay permission onboarding section rendering. |

The UI smoke test now includes the M4.4 Home → Diagnostics flow, the M4.7
fake timeout failure path, the M4.8 Settings self-test result path, the M5.2
local feedback path, the M5.4 real Packs tab state, the M5.5 external GKP
preflight result, the M5.6 install confirmation plan, and the M5.7 delete
confirmation plan, the M5.8 provenance/signature labels, the M5.9 enable/disable controls, the M5.10 disabled-GKP explanation path, the M6.1 quick question draft path, the M6.2 recovery hint path, the M6.3 recent-context action bar, the M6.4 recovery action navigation path, the M6.5 all-target recovery smoke, the M6.6 in-memory conversation tray, the M6.7 follow-up draft buttons, the M6.8 direct-answer spoiler escalation notice, the M7.0 pending hotkey prepare/cancel path, the M7.2 persisted pending-hotkey conversation restore path, the M7.3 Diagnostics source count bar path, the M8.1 fake speech input/TTS affordance path, and the M8.3 local-ASR status polish path for first model load and empty recognition, using injected test doubles so it stays deterministic and
does not require the real endpoint, DeepSeek, microphone, or sherpa-onnx runtime. M6.9's strategy-level spoiler
override and M7.0's endpoint consumption wrapper are covered by the JVM
unit/integration tests listed above.

## Manual device records

- **2026-05-20, RG 476H true-device M8.3 voice loop:** tapped Home “语音输入”, said “两个 2 怎么合并？”, sherpa-onnx filled the question box with “两个二怎么合并”; tapping “提问” returned the correct historical `sample-2048` GKP answer. This validated that ASR text normalization was not needed for that early sample path; M17 release scope now uses the six real bundled GKP packs.
- **2026-05-20, RG 476H true-device M8.4 voice regression:** all 5 manual voice questions passed: 2048 cold start, 2048 warm start, empty-recognition prompt, Relay Station sample, and a final 2048 regression. This confirms the local sherpa-onnx voice input path is stable enough to move on to real-game GKP content work without adding ASR text normalization.
- **2026-05-20, RetroSprite_API_34 M9.1 runtime GKP smoke:** installed the Debug APK with bundled `community.shining-force-ii-md`, then `/debug/ask` with `label="md__Shining Force II"` returned local evidence answers for “什么时候转职？” (`sf2.promotion`) and “不要剧透下一步去哪？” (`sf2.early_route`).
- **2026-05-20, RG 476H M9.2 RetroArch label preflight/runtime smoke:** read the loaded MD/Genesis playlist entry from the device: ROM path `/storage/4A21-0000/Roms/MD/光明力量2.md`, `label=光明力量2`, core `Genesis Plus GX`. Installed the updated Debug APK and verified `/debug/ask` with `label="md__光明力量2"` returns local evidence for “什么时候转职？” (`sf2.promotion`) and “不要剧透下一步去哪？” (`sf2.early_route`). A later true-device AI Service request arrived as `label="mega_drive__光明力量2"`, so that label was added to the GKP regression. A per-core/config-file edit experiment was restored to defaults. Current product default is RetroArch's AI Service URL `http://localhost:4404`; final acceptance still requires reloading the game and pressing the physical hotkey.
- **2026-05-20, M9.3 Settings RetroArch setup helper:** removed the in-app cfg writer after true-device EACCES confirmed Android ownership/scoped-storage risk. Settings now only shows the RetroArch path/recommended values and the RetroArch default AI Service URL `http://localhost:4404`; no cfg write path, advanced cfg snippet, hotkey mutation, or broad storage permission remains.
- **2026-05-20, M10.0 Hotkey Wake Voice Overlay scaffold:** endpoint and coordinator JVM tests now prove real RetroArch `POST /` requests emit hotkey events and can drive a short overlay cue; debug requests do not trigger the overlay path. True-device acceptance still needs Android overlay permission and physical hotkey validation on RG 476H.
- **2026-05-20, M10.1/M10.3 Hotkey voice loop scaffold:** Settings now exposes overlay permission onboarding; hotkey voice orchestration is covered by JVM tests for ASR transcript → GKP pipeline call → `hotkey_voice` log → TTS. True-device acceptance still needs loading Shining Force II in RetroArch, pressing the physical hotkey, speaking a question, and confirming `sf2.promotion` in Diagnostics.
- **2026-05-20, RG 476H M10.3 hotkey overlay bugfix smoke:** pressing AI Service hotkey previously showed the waveform but did not answer and stayed visible. Logcat root cause: background `AudioRecord` was silenced by foreground-only `RECORD_AUDIO` AppOps, and the timeout path waited forever on an uncancelled final-transcript coroutine. Debug APK now declares/uses foreground-service `microphone` type when record-audio is granted, ignores repeated wake requests while a session is active, and cancels the final-transcript waiter on timeout. Host-side background POST smoke confirmed zero `silencing record` / `Operation not started RECORD_AUDIO` lines and zero RetroSprite overlay windows after the timeout. Full spoken-answer acceptance still needs the user to press the physical RetroArch hotkey and speak a Shining Force II question.
- **2026-05-20, RG 476H M10.2 hotkey voice true-device pass #1:** in RetroArch with MD/Genesis Shining Force II loaded, the user pressed the configured AI Service hotkey and said “什么时候转职？”. The top-right waveform appeared, the voice loop completed, and TTS spoke the result. `/debug/latest-request` confirmed a real `hotkey_voice:text` request with `label="mega_drive__光明力量2"` and `question_source="hotkey_voice"`. The latest ASR transcript was “接受他几部这个角色”, so the pipeline correctly returned `no_evidence`; this validates the hotkey/overlay/ASR/TTS mechanics but leaves the `sf2.promotion` evidence-hit acceptance open for one clearer repeat phrase.
- **2026-05-24, RG 476H M12.8 six-pack runtime smoke:** ran `BUILD=1 INSTALL=1 STRESS=1 ./scripts/android_avd_smoke.sh`, then reran `BUILD=0 INSTALL=0 STRESS=1 ./scripts/android_avd_smoke.sh` after correcting the Chrono Trigger expected source. The final pass validated `/health`, endpoint POST/malformed/stress checks, and all six real GKP `/debug/ask` cases from `scripts/gkp_debug_cases.tsv`: Shining Force II, Golden Sun, Phantasy Star IV, Langrisser II, Chrono Trigger, and Final Fantasy VI all reported `pipeline_stage=evidence`, `llm_status=skipped`, and the expected source id in `/debug/latest-request`.
- **2026-05-24, M16 multi-pack hotkey voice QA tooling:** added `scripts/hotkey_voice_qa_cases.tsv` and `scripts/hotkey_voice_qa_batch.sh` for a reusable Shining Force II / Golden Sun / Chrono Trigger voice matrix. Initial `DRY_RUN=1` and script/unit checks passed; later 2026-05-25 runs added real MacBook-speaker evidence.
- **2026-05-25, all-GKP Paraformer ASR variant voice QA:** MacBook-speaker runs under `build/hotkey-voice-qa/20260525-*` covered all six bundled GKP packs with Tingting. Current strict evidence includes successful Golden Sun, Chrono Trigger, Langrisser II, Phantasy Star IV, Final Fantasy VI, and several Shining Force II rows after scoped `observed_asr` fixes. The latest capture/commit retest at `build/hotkey-voice-qa/20260525-113514/results.tsv` was 3/4: Golden Sun, Chrono Trigger, and Langrisser II passed with overlay `finished`; Shining Force II `气合之玉怎么用？` was heard as `气河之欲怎么用` and returned `sf2.characters` instead of expected `sf2.promotion`, so it remains an ASR variant/source-ranking issue rather than a lifecycle failure.

## End-to-end shell scripts (`scripts/`)

| Script | Shell | What it does |
|---|---|---|
| `test_endpoint.sh` | bash 3.2+ (macOS-safe) | 4 checks: `/health` probe, happy POST asserting `text` and no `error`, malformed JSON expects HTTP 200 + error, 100-shot stress test reporting avg latency. Colored PASS/FAIL summary. Exit `0` (all pass) / `1` (any fail) / `2` (curl missing). |
| `test_endpoint.fish` | fish | Functional equivalent of `.sh` version. |
| `rc_release_audit.py` | Python 3 | Offline release checklist audit: verifies the bundled GKP set is exactly the six supported real games, required manifest/license/citation files exist, GKP asset file types are safe data files, knowledge source refs resolve to citations, no `sk-*` style API key is bundled, GKP knowledge/golden text stays below long-form thresholds and avoids explicit full-script/fan-translation/commercial-guide risk terms, runtime code does not mention stale OCR routes, and the screen translation recommendation remains `Qwen/Qwen3-VL-8B-Instruct`. |
| `rc_hardening_check.sh` | bash 3.2+ (macOS-safe) | M17 release-candidate gate. Safe default runs JVM tests, `assembleDebug`, APK size snapshot, ASR/GKP asset size snapshot, bundled GKP row/golden counts, `rc_release_audit.py`, `scripts/tests` unittest discovery, and `hotkey_voice_qa_batch.sh` self-test/dry-run without playing audio. `RUN_DEVICE=1` first checks for an online adb device, then chains into `android_avd_smoke.sh`; `RUN_VOICE=1` runs the real MacBook-speaker voice matrix. |
| `rc_device_evidence.sh` | bash 3.2+ (macOS-safe) | Real-device evidence collector for manual RC gates. Requires one online adb device, forwards the endpoint, then captures adb device/package/AppOps/window snapshots plus `/health`, `/debug/latest-request`, `/debug/hotkey-voice-overlay`, and `metadata.json` into `build/rc-device-evidence/<timestamp>/`. For screen translation pass evidence, run it with `--gate screen_translation --case-id <case_id> --include-screenshot`; the script requires that flag for screen translation rows and writes `screenshot.png`, so later matrix checks can reject endpoint-smoke evidence or JSON-only evidence reused for a screen case. It does not mark a manual test as passed; testers use the artifacts to fill `docs/qa-feedback/rc-device-matrix.md`. |
| `gkp_eval_report.py` | Python 3 | M18 offline GKP coverage report. Reads bundled GKP manifests, knowledge rows, aliases, citations, licenses, and QA goldens; reports lane status for identity, core gameplay, first hour, mechanics, menu terms, items/skills/magic, names/aliases, common blockers, low-spoiler next step, no-evidence boundary, observed ASR variants, citations, and goldens. |
| `gkp_gap_backlog.py` | Python 3 | M18 evidence-to-backlog helper. Reads `/debug/latest-request` JSON, manual JSON/JSONL exports, `hotkey_voice_qa_batch.sh` `results.tsv` files, and manual tester notes TSV files named with `manual`, `tester`, `qa`, or `note`; classifies no-evidence, alias, coverage, ranking, spoiler-gate, translation, ASR-variant, and voice-lifecycle gaps, then writes `docs/qa-feedback/gkp-quality-backlog.md` with compact failure details such as finish reason, ASR commit reason, endpoint armed state, stale latest-request status, voice transcript, expected/actual tester notes, evidence path, and audio capture counters. It can also write `docs/qa-feedback/gkp-manual-notes-template.tsv` as a starter file for testers; the example rows are marked as examples and do not create backlog items until replaced with real observations. `--merge-existing-backlog` preserves active rows while adding new unique label/question/tag items, which protects the current ASR backlog during manual-note imports. Successful ASR normalizations are not treated as gaps. |
| `gkp_backlog_triage_report.py` | Python 3 | M18 backlog routing helper. Reads `docs/qa-feedback/gkp-quality-backlog.md`, the current ASR review packet, bundled `qa_goldens.jsonl`, and local hotkey voice result folders; marks rows already covered by approval-gated ASR alias/golden rows, existing low-spoiler policy goldens, or later passing device reruns; and routes remaining rows to device rerun, new policy golden, retrieval golden, screen translation follow-up, or GKP triage buckets. It writes `docs/qa-feedback/gkp-backlog-triage-report.md`, edits no assets, and strict mode fails on unclassified rows. |
| `gkp_patch_assistant.py` | Python 3 | M18 dry-run GKP patch proposal helper. Produces suggested alias, knowledge, and golden additions for human review, rejects rights-unsafe content, requires known source refs, supports `--observed-asr` / `--canonical-term` for scoped ASR variants, avoids suggesting duplicate knowledge rows when targeting an existing entity, and never writes GKP assets automatically. |
| `gkp_patch_proposal_audit.py` | Python 3 | M18 patch proposal preflight. Reads dry-run proposal markdown and the current GKP backlog, then verifies each proposed `observed_asr` alias maps to a bundled pack manifest, existing source id, existing entity id, and matching backlog transcript/question before human approval. |
| `gkp_patch_review_packet.py` | Python 3 | M18 human approval packet generator. Converts audited ASR proposals into exact `aliases.json` and `qa_goldens.jsonl` rows, derives intent/progress/spoiler metadata from existing entities, checks duplicate alias/question/id conditions, and writes a review markdown without editing GKP assets. |
| `gkp_patch_apply_review_packet.py` | Python 3 | M18 approval-gated patch application helper. Parses a human review packet, validates duplicate alias/question/QA ids, writes a dry-run report by default with `Assets edited: no`, and refuses `--apply` unless the exact approval phrase is supplied. Tests cover dry-run safety, duplicate blocking, approval enforcement, and apply mode on temporary fixture packs only. |
| `gkp_asset_mutation_guard.py` | Python 3 | M18 bundled-GKP mutation guard. Reads `git status --porcelain -- app/src/main/assets/gkp`, writes `docs/qa-feedback/gkp-asset-mutation-guard.md`, passes while assets are clean, and after an approved apply report permits only the exact expected alias/golden paths from the review packet. It never edits assets. |
| `gkp_patch_regression_gate.sh` | bash 3.2+ (macOS-safe) | M18 post-approval GKP patch regression gate. Safe default runs focused GKP JVM tests, `rc_release_audit.py`, `gkp_asset_mutation_guard.py --strict`, M18 report refreshes, and `git diff --check` without device or audio playback. `RUN_VOICE=1` replays the current failed ASR-variant rows after a patched APK is installed. |
| `gkp_asr_patch_voice_handoff.py` | Python 3 | M18 GKP ASR patch and real-device replay handoff generator. Reads the approved-row packet, dry-run apply report, and hotkey voice TSV, then writes exact approval, local regression, scoped voice replay, evidence capture, and pass criteria steps without touching assets. |
| `hotkey_voice_matrix_report.py` | Python 3 | M18 hotkey voice matrix status checker. Reads `scripts/hotkey_voice_qa_cases.tsv` plus a real-device `build/hotkey-voice-qa/<timestamp>/results.tsv`, reports the selected seven playback rows, verifies result contract fields, preserves ASR transcripts/counters, and classifies failures such as `voice_lifecycle_gap`, `asr_variant`, or source mismatch. When `--results` is not provided, it chooses the results file with the best selected-case coverage before using timestamp recency, so focused single-case diagnostics do not replace the matrix report. |
| `screen_translation_manual_packet.py` | Python 3 | M18 screen translation manual QA packet generator. Reads `screen_translation_eval_cases.tsv` and `rc-device-matrix.md`, then writes a tester-facing checklist with trigger phrase, expected layout/language/number policy, acceptance checks, per-case evidence capture command, and matrix result templates. |
| `screen_translation_matrix_update.py` | Python 3 | M18 screen translation matrix row updater. Reads `screen_translation_eval_cases.tsv`, validates one `Pass`/`Fail`/`Blocked` result note with the same rules as `screen_translation_eval_report.py`, then updates or previews the corresponding row in `docs/qa-feedback/rc-device-matrix.md`. M18-generated commands include `--cases` explicitly, and `Pass` rows require an existing `rc_device_evidence.sh`-style evidence directory under `build/rc-device-evidence/` that is not already used by another passing row, whose `metadata.json` says `gate=screen_translation` plus the matching case id, whose directory includes `screenshot.png`, and whose result note includes required `checklist=` tokens. |
| `screen_translation_eval_report.py` | Python 3 | M18 screen translation QA status checker. Reads `scripts/screen_translation_eval_cases.tsv` and the Screen Translation Matrix in `docs/qa-feedback/rc-device-matrix.md`, then writes a per-case report with `pass`, `fail`, `blocked`, `not_run`, or `missing` status. Result cells may include notes such as `Fail: numeric_corruption` or `Blocked: missing BYOK key`, which are preserved in the report while still counting the status correctly. Passing manual rows require a unique existing evidence directory under `build/rc-device-evidence/` containing `README.md`, `health.json`, `latest-request.json`, `hotkey-voice-overlay.json`, `screenshot.png`, matching `metadata.json`, and case-specific `checklist=` tokens; failed/blocked rows require a failure category. `--strict` exits nonzero until all cases pass with no note issues. |
| `screen_translation_receipt_update.py` | Python 3 | Legacy/general screen translation receipt helper, outside current M18 gates. It can preview or apply one screen result into an old manual receipt JSON using `screen_translation_eval_cases.tsv`, but current M18 offline gate does not refresh or require those receipts. |
| `gkp_content_rights_manual_packet.py` | Python 3 | M17/M18 content-rights handoff generator. Reuses `rc_release_audit.py` machine checks, summarizes all bundled packs, lists GKP file counts and source/license inventory, and writes the manual review checklist without marking the human release checkbox complete. |
| `m18_release_checklist_guard.py` | Python 3 | Legacy M17/RC release checklist guard, outside current M18 aggregate status and offline gate. It can still inspect voice/screen/content-rights evidence before guarded release checklist changes, but M18 no longer waits on this script. |
| `m18_status_report.py` | Python 3 | M18 aggregate status helper. Reads M18 coverage/backlog/patch/asset/hotkey/command-contract/quality-loop reports, writes `docs/qa-feedback/m18-status-report.md`, includes next actions for each open area, reports non-blocking review-packet rows plus `triage_open`, and exits nonzero in `--strict` mode while any aggregate row is not passing. |
| `m18_gate_status_json.py` | Python 3 | Machine-readable M18 gate status emitter. Reuses `m18_status_report.py` summarizers and writes `docs/qa-feedback/m18-gate-status.json` with schema version, row list, counts, open areas, quality-loop handoff readiness, and overall state. |
| `m18_plan_execution_audit.py` | Python 3 | M18 plan execution auditor. Reads the active main M18 implementation plan and `docs/qa-feedback/m18-status-report.md`; skips fenced checkbox examples, counts checked/open plan steps, classifies open blockers, and writes `docs/qa-feedback/m18-plan-execution-audit.md` plus `.json` without touching GKP assets or device state. |
| `m18_remaining_gate_packet.py` | Python 3 | M18 remaining-gate handoff generator. Reads the plan audit and hotkey voice matrix report, then writes `docs/qa-feedback/m18-remaining-gate-handoff.md` for remaining machine/device gates. It records that manual ASR approval, screen translation manual matrix, and human content-rights confirmation are outside M18 scope. |
| `m18_completion_audit.py` | Python 3 | M18 objective completion auditor. Reuses the machine-readable gate summary plus the plan execution audit JSON, with Markdown fallback, to write `docs/qa-feedback/m18-completion-audit.md` and `.json`; strict mode fails until plan checkboxes, aggregate rows, machine/device evidence, asset safety, and final offline gate eligibility are all proven. |
| `m18_next_action_queue.py` | Python 3 | M18 action queue generator. Reads the current machine-readable gate status, GKP backlog triage report, and hotkey voice cases TSV, then writes Markdown plus JSON action queues with stable action ids, owners, blockers, evidence files, commands, acceptance criteria, counts, and `action_ids_by_status`. Current M18 queue does not emit manual ASR approval, screen translation manual QA, or content-rights review actions. |
| `m18_quality_loop_handoff.py` | Python 3 | M18 ongoing quality-loop handoff generator. Reads `m18-gate-status.json` and `m18-next-action-queue.json`, writes `docs/qa-feedback/m18-quality-loop-handoff.md` plus `.json`, and captures preview-first backlog import recipes for `/debug/latest-request`, hotkey voice `results.tsv`, and manual notes TSV. It records fix acceptance rules and edits no GKP assets. |
| `m18_manual_gate_intake_packet.py` | Python 3 | Legacy/general manual-input packet generator, outside current M18 aggregate status, next-action queue, and offline gate. Existing tests keep it from drifting while it remains in the repo, but current M18 does not require manual ASR approval, a five-row screen translation matrix, or human content-rights confirmation. |
| `m18_manual_gate_receipt_check.py` | Python 3 | Legacy/general manual-input receipt checker, outside current M18 completion. It validates old receipt JSON structures without applying changes. |
| `m18_manual_gate_receipt_plan.py` | Python 3 | Legacy/general manual-input receipt planner, outside current M18 completion. It can still emit reviewable guarded commands for old manual receipts, but the current M18 offline gate does not refresh or require it. |
| `m18_command_contract_audit.py` | Python 3 | M18 generated-command contract audit. Scans generated M18 command snippets and current plan/test docs for stale or unsafe command snippets, action queue consistency, quality-loop JSON consistency, plan execution JSON consistency, completion audit JSON consistency, GKP patch apply safety, active-backlog merge protection, and old manual-gate scope drift. |
| `m18_offline_quality_gate.sh` | bash 3.2+ (macOS-safe) | M18 offline report refresh gate. Safe default regenerates machine-checkable coverage, patch proposal/review/apply dry-run, GKP asset mutation guard, ASR replay handoff, hotkey voice matrix report, aggregate status, plan audit Markdown/JSON, remaining-gate handoff, completion audit, next action queue, quality-loop handoff Markdown/JSON, and command-contract audit; then runs strict probes, script tests, `rc_release_audit.py`, and `git diff --check` without adb, audio playback, GKP apply mode, or removed manual gate refreshes. |
| `android_avd_smoke.sh` | bash 3.2+ (macOS-safe) | Device/AVD smoke: verifies adb online, optionally builds, auto-installs missing Debug APK, starts `com.retrosprite.app`, forwards host/device ports, runs `test_endpoint.sh`, then reads `scripts/gkp_debug_cases.tsv` and posts real GKP `/debug/ask` probes for all six bundled real packs. Each case checks `/debug/latest-request` for the expected label, question, `pipeline_stage=evidence`, `llm_status=skipped`, and source id. This is the host-side counterpart to Home's generated debug curl path. |
| `gkp_debug_cases.tsv` | TSV | Data-driven real-GKP smoke matrix for Shining Force II, Golden Sun, Phantasy Star IV, Langrisser II, Chrono Trigger, and Final Fantasy VI. Each row declares the label, low-spoiler question, expected source id, expected pipeline stage, and expected LLM status used by `android_avd_smoke.sh`. |
| `hotkey_voice_qa_batch.sh` | bash 3.2+ (macOS-safe) | Multi-pack hotkey voice QA runner. Safe default is dry-run; actual MacBook speaker playback requires `RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1`. Captures `/debug/hotkey-voice-overlay` and `/debug/latest-request`, records raw/normalized transcript, matched term/entity, answer type, pipeline stage, LLM status, source ids, overlay phase, finish reason, and ASR sample/read/error/peak/last-frame amplitude counters. On newer APKs it waits for `asr_audio_read_count > 0` before speaking, while remaining compatible with older APKs that do not expose the field. It supports `TTS_BACKEND=sherpa_onnx` for a reproducible local Mandarin wav source via `scripts/sherpa_zh_tts.py`. |
| `hotkey_voice_qa_cases.tsv` | TSV | Data-driven M16 voice QA matrix for all six bundled GKP packs. Shining Force II, Golden Sun, and Chrono Trigger retain core/localized/no-evidence lanes; all six packs also include all-GKP Paraformer voice smoke or observed-ASR lanes. |
| `screen_translation_eval_cases.tsv` | TSV | M18 data source for manual screen translation QA. Separates dialogue Chinese-only display from menu/status/equipment bilingual rows, numeric preservation, and 10-second paging evidence. |
| `sample_payload.json` | n/a | Reference request body for manual `curl`. |

All-GKP ASR variant validation:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest \
  --tests com.retrosprite.app.gkp.GkpV0FixtureLintTest \
  --tests com.retrosprite.app.gkp.GkpV0ParserTest \
  --tests com.retrosprite.app.gkp.GkpV0PreflightValidatorTest \
  --tests com.retrosprite.app.gkp.RetroJrpgSrpgPackCoverageTest \
  --tests com.retrosprite.app.data.retrieval.RetroJrpgSrpgPackRetrievalGoldenTest \
  --tests com.retrosprite.app.domain.normalization.GkpAsrVariantIndexTest \
  --tests com.retrosprite.app.domain.normalization.GameTermNormalizerTest \
  --tests com.retrosprite.app.endpoint.QueryPipelineResponseGeneratorTest
```

ASR capture/commit gate regression:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest \
  --tests com.retrosprite.app.ui.integration.SherpaEndpointCommitGateTest \
  --tests com.retrosprite.app.ui.integration.SherpaFinalTranscriptSelectorTest \
  --tests com.retrosprite.app.ui.integration.SherpaOnnxRecognizerFactoryTest \
  --tests com.retrosprite.app.ui.integration.VoiceSampleFanOutTest
```

All-GKP MacBook-speaker voice batch:

```bash
RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 \
CASE_FILTER=sf2_vigor_ball_observed,golden_sun_ivan_observed,chrono_marle_observed,chrono_atb_observed,ff6_magicite_observed,langrisser_commander_smoke,phantasy_star_tech_skill_smoke \
VOICE=Tingting SAY_RATE=96 PRE_SPEAK_SECONDS=3 POST_CASE_SECONDS=10 POLL_ATTEMPTS=40 POLL_INTERVAL_SECONDS=2 READY_ATTEMPTS=20 READY_INTERVAL_SECONDS=1 STRICT=0 \
./scripts/hotkey_voice_qa_batch.sh
```

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
- The bundled GKP set is still first-support content: Shining Force II is
  expanded, while the other bundled packs are Lite. They intentionally do not
  answer complete routes, full endings, full item tables, or every late-game
  spoiler.
- Local Paraformer ASR can still drift on short game-specific terms. Current
  fixes are scoped through GKP aliases and `observed_asr` rows; the release gate
  still needs real-device hotkey voice playback before preview release.
