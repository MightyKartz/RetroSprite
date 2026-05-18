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
| `EndToEndPipelineTest` | Task 7 | Full HTTP → endpoint → domain → policy → logger walk via ktor `testApplication`. Verifies response text contains `RetroSprite`, that exactly one log entry is produced per request, and that `/health` does not log. |
| `endpoint.LabelParserTest` | Task 2 | 8 cases for `LabelParser.parse`: standard, multi-delimiter, no delimiter, trailing/leading delimiter, null, blank, single underscore. |
| `endpoint.RetroArchModelTest` | Task 2 | DTO round-trip: default state, full payload, partial state + unknown keys, response factory output, null-field decoding. |
| `endpoint.RetroArchEndpointServerTest` | Task 2 | Ktor route behavior: `/health`, happy POST + log entry, malformed JSON → HTTP 200 + error, partial state defaults, missing `output` param defaults to `text`, generator failure path, `decodedBase64Length` math. |
| `endpoint.EndpointEdgeCaseTest` | Task 7 | **New.** Oversized 5 MB Base64 image, wrong `Content-Type: text/plain`, empty body, exotic `output=text|sound` query param. |
| `endpoint.QueryPipelineResponseGeneratorTest` | Task 8 | Endpoint↔domain bridge: full pipeline returns ack, empty image+label tolerated, mixed output mode, `RetroArchState.toFlagMap` semantics. |
| `endpoint.RoomBackedRequestLogSinkTest` | Task 8 | Repository-backed sink with in-memory fake: append flows through, null system → empty string, clear empties the flow, domain/endpoint mapping symmetry. |
| `domain.DefaultQueryPipelineTest` | Task 5 | Pipeline end-to-end with all Phase 0 collaborators: typical request, empty label, null state. |
| `domain.resolver.LabelGameResolverTest` | Task 5 | Resolver behavior for `system__game` labels. |
| `domain.policy.FixedTextAnswerPolicyTest` | Task 5 | Fixed acknowledgement text is always emitted. |
| `domain.retrieval.NoOpRetrievalPipelineTest` | Task 5 | No-op retrieval returns empty results. |
| `llm.MockLlmAdapterTest` | Task 5 | Mock adapter returns the deterministic stub answer. |
| `ui.integration.UiModelMappersTest` | Task 8 | Domain ↔ UI model mappers. |

## Android instrumented tests (`app/src/androidTest/kotlin/com/retrosprite/app/`)

These require an emulator or device (API 26+, x86_64 system image recommended).

| Test class | Owner | Coverage |
|---|---|---|
| `data.RetroSpriteDatabaseTest` | Task 4 | Database boots with FTS5, migrations are a no-op for v1, DAOs are reachable. |
| `data.RequestLogDaoTest` | Task 4 | Insert, observeRecent ordering by timestamp DESC, count, clear. |
| `data.GameDaoTest` | Task 4 | CRUD + lookup by rom hash / label. |
| `data.KnowledgeDaoTest` | Task 4 | Knowledge entry CRUD + spoiler-level filter. |
| `data.KnowledgeFtsDaoTest` | Task 4 | FTS5 MATCH queries — verifies tokenizer + ranking. |
| `ui.RetroSpriteAppSmokeTest` | Task 3 | Compose smoke test: four bottom tabs visible, Diagnostics / Packs / Settings each renders its distinctive headline. |

The UI smoke test was reviewed for Task 7 and **needs no fix** — the
assertions match the current `RetroSpriteRoot` markup.

## End-to-end shell scripts (`scripts/`)

| Script | Shell | What it does |
|---|---|---|
| `test_endpoint.sh` | bash 3.2+ (macOS-safe) | 4 checks: `/health` probe, happy POST asserting `RetroSprite` in body, malformed JSON expects HTTP 200 + error, 100-shot stress test reporting avg latency. Colored PASS/FAIL summary. Exit `0` (all pass) / `1` (any fail) / `2` (curl missing). |
| `test_endpoint.fish` | fish | Functional equivalent of `.sh` version. |
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

- The Gradle wrapper JAR (`gradle/wrapper/gradle-wrapper.jar`) is missing from
  the repo, so `./gradlew …` cannot be run locally without regenerating it
  (`gradle wrapper --gradle-version 8.x` from a system Gradle install, or via
  the `gradle/actions/setup-gradle` CI step).
- The shell scripts assume the endpoint is already running — start the app on
  a device/emulator and `adb forward tcp:8080 tcp:8080` before invoking them.
- No real LLM API is hit anywhere in the test suite: `LlmConfig.MOCK` and
  `MockLlmAdapter` are used end-to-end. Phase 1 will introduce contract tests
  for `OpenAiCompatibleLlmAdapter` against a recorded response.
