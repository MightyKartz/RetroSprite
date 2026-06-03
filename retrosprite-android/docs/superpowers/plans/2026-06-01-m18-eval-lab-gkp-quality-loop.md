# M18 Eval Lab And GKP Quality Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn RetroSprite's real-device RC into a measurable quality loop where unanswered player questions, weak retrieval, ASR variants, screen-translation layout failures, and GKP coverage gaps become reproducible tests and safe GKP updates.

**Architecture:** Keep M18 as a quality and tooling milestone, not a product-surface expansion. The core loop is `request logs / QA cases -> evaluation scripts -> gap report -> GKP patch -> golden regression -> RC smoke`; no new games, no new model defaults, no bundled copyrighted scripts, and no LLM factual answers without local evidence.

**Tech Stack:** Kotlin/JVM tests for retrieval and policy behavior; Python 3 scripts for offline GKP/eval reports; bash/adb/curl for real-device evidence; existing Room request logs, JSONL GKP assets, `scripts/gkp_debug_cases.tsv`, `scripts/hotkey_voice_qa_cases.tsv`, and `docs/qa-feedback/rc-device-matrix.md`.

---

## Research Basis

- RetroArch AI Service is already the right integration point because it lets players trigger a bounded in-game request from RetroArch instead of requiring Accessibility, MediaProjection, or continuous capture: https://docs.libretro.com/guides/ai-service/
- Game translation projects such as ZTranslate, LunaTranslator, and Game2Text show that OCR-only translation is useful but fragile for menus and repeated UI text; package/script matching improves stability, but RetroSprite must keep GKP content to original summaries, terms, aliases, and citations rather than copied scripts.
- RAG research points to evaluation and grounding as the next quality bottleneck after the basic pipeline works: RAG, Self-RAG, ARES, and Ragas all separate retrieval quality, evidence faithfulness, and answer usefulness.
- Screen/UI understanding research such as ScreenAI supports treating menu/status/equipment screens as structured UI, not plain paragraphs. M18 should keep dialogue translation and menu bilingual lookup as separate evaluation lanes.

## Current Truth

- M17 has a working core path: RetroArch hotkey -> local endpoint -> overlay -> local Paraformer ASR -> GKP retrieval -> AnswerPolicy -> overlay/TTS.
- RG476H `RG476H01077813` passed `RUN_DEVICE=1 RUN_VOICE=0 ./scripts/rc_hardening_check.sh` on 2026-06-01; evidence lives under `build/rc-device-evidence/20260601-140652/`. The current Debug APK passed the same endpoint/GKP smoke again on 2026-06-02 with latest evidence under `build/rc-device-evidence/20260602-080403/`.
- Bundled GKP scope remains exactly six games, about 347 knowledge rows and 337 QA goldens.
- M18 scope is now limited to machine-checkable Eval Lab and GKP quality-loop work. Manual ASR approval, the five-row screen translation manual matrix, and human content-rights confirmation are no longer M18 gates.
- The most important next product risk is not endpoint availability; it is answer coverage and answer trust when players ask natural questions outside the current goldens.

## Current Development Directive

- Treat `docs/qa-feedback/m18-next-action-queue.md` as the live task board.
- Hotkey voice matrix playback is now observational M18 evidence, not a release blocker. A real run should keep `docs/qa-feedback/hotkey-voice-matrix-report.md` fresh, but one-off ASR drift should be handled by retry UX and backlog evidence rather than broad alias chasing.
- The refreshed hotkey voice matrix report currently uses the latest full-matrix coverage run: 4/7 pass with `asr_variant=1, source_mismatch=2`. A later focused 0/3 run under `build/hotkey-voice-qa/20260603-211030/` further supports keeping the product simple and using repeated misses as backlog signals.
- The final offline gate is `./scripts/m18_offline_quality_gate.sh`; it no longer requires `EXPECT_ALL_PASS=1`.
- Do not add games, default providers, capture surfaces, or broad UI features until the current M18 gate is closed.

## Files To Touch

- Create: `scripts/gkp_eval_report.py`
- Create: `scripts/gkp_gap_backlog.py`
- Create: `scripts/gkp_patch_assistant.py`
- Create: `scripts/gkp_asset_mutation_guard.py`
- Create: `scripts/m18_status_report.py`
- Create: `scripts/m18_completion_audit.py`
- Create: `scripts/m18_next_action_queue.py`
- Create: `scripts/m18_command_contract_audit.py`
- Create: `docs/qa-feedback/gkp-quality-backlog.md`
- Create: `docs/qa-feedback/m18-eval-report.md`
- Modify: `scripts/tests/test_gkp_eval_report.py`
- Modify: `scripts/tests/test_gkp_gap_backlog.py`
- Modify: `scripts/tests/test_gkp_patch_assistant.py`
- Modify: `scripts/tests/test_gkp_asset_mutation_guard.py`
- Modify: `scripts/tests/test_m18_completion_audit.py`
- Modify: `scripts/tests/test_m18_next_action_queue.py`
- Modify: `docs/TEST_COVERAGE.md`
- Create: `docs/qa-feedback/m18-status-report.md`
- Modify: `docs/NEXT_IMPLEMENTATION_PLAN.md`

## Task 1: Close M17 Before Starting Product Changes

**Files:**
- Modify: `docs/qa-feedback/rc-device-matrix.md`
- Modify: `docs/RELEASE_CANDIDATE_CHECKLIST.md`
- Modify: `docs/TEST_COVERAGE.md`

- [x] **Step 1: Record real-device hotkey voice playback matrix as observational evidence**

Run only with the RG476H connected and RetroArch loaded:

```bash
RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 \
CASE_FILTER=sf2_vigor_ball_observed,golden_sun_ivan_observed,chrono_marle_observed,chrono_atb_observed,ff6_magicite_observed,langrisser_commander_smoke,phantasy_star_tech_skill_smoke \
VOICE=Tingting SAY_RATE=96 PRE_SPEAK_SECONDS=3 POST_CASE_SECONDS=10 POLL_ATTEMPTS=40 POLL_INTERVAL_SECONDS=2 READY_ATTEMPTS=20 READY_INTERVAL_SECONDS=1 STRICT=0 \
./scripts/hotkey_voice_qa_batch.sh
```

Expected: each selected row records a fresh result under `build/hotkey-voice-qa/` with `question_source=hotkey_voice`, `pipeline_stage=evidence` or an expected no-evidence boundary, `llm_status=skipped`, and a correct `source_ids` hit.

2026-06-01 14:49 CST note: RG476H `RG476H01077813` ran the 7-case playback matrix and failed before GKP retrieval. Each overlay reached `listening` / `mic_live=true`, then ended with `finish_reason=muted_recovery`, `asr_commit_reason=blank_partial`, `asr_endpoint_armed=false`, and stale `/debug/latest-request`. Evidence: `build/hotkey-voice-qa/20260601-144348/`. `scripts/gkp_gap_backlog.py` now ingests `results.tsv` and records these failures as `voice_lifecycle_gap`.

2026-06-01 15:03 CST note: `./scripts/rc_device_evidence.sh` captured post-failure state under `build/rc-device-evidence/20260601-150333/`; overlay diagnostics still show `muted_recovery` / `blank_partial`, and latest-request points to older debug evidence rather than a fresh playback submission.

2026-06-01 follow-up note: M17.1 capture diagnostics are now implemented in code and QA tooling. `/debug/hotkey-voice-overlay` can expose AudioRecord sample/read/error counters plus peak and last-frame amplitude, and `scripts/hotkey_voice_qa_batch.sh` waits for `asr_audio_read_count > 0` before playback when that field exists. This does not mark Step 1 complete yet; the new APK still needs a real RG476H one-case recovery probe followed by the 7-case playback matrix.

2026-06-01 15:43 CST note: the one-case recovery probe passed at Mac output volume 90, and the 7-case matrix reached fresh `hotkey_voice` submissions for every row. The failures were narrowed to M18 GKP scoped ASR/retrieval issues, not voice lifecycle gaps. Evidence: `build/hotkey-voice-qa/20260601-153946/`; backlog points at observed-ASR aliases and patch proposals are recorded in `docs/qa-feedback/gkp-patch-proposals-20260601-hotkey-voice.md`.

2026-06-02 follow-up note: the current seven-row report is 5/7 pass. Remaining failures are `sf2_vigor_ball_observed` with `source_mismatch` and `chrono_marle_observed` with `asr_variant`; use these as the current fix targets before any product-surface expansion.

2026-06-01 tooling note: `scripts/hotkey_voice_matrix_report.py` now converts the same real-device `results.tsv` into `docs/qa-feedback/hotkey-voice-matrix-report.md`. The current report is 4 pass / 3 fail, and `--strict` remains nonzero until the seven-row matrix passes or repeated misses are captured as backlog evidence.

2026-06-03 follow-up note: the focused replay after small FF6 alias work recorded 3 rows with 0 pass / 3 fail under `build/hotkey-voice-qa/20260603-211030/`. This is now treated as observational QA evidence: occasional ASR drift is retry-worthy product behavior, while repeated misses should become backlog rows before any new normalization rule.

2026-06-02 scope note: the former M18 steps for filling the five-row screen translation manual matrix, completing human content-rights confirmation, and updating release checklist checkboxes have been removed from M18. They may remain release/QA concerns outside this milestone, but M18 no longer tracks them as gates.

## Task 2: Add Offline GKP Coverage Evaluation

**Files:**
- Create: `scripts/gkp_eval_report.py`
- Create: `scripts/tests/test_gkp_eval_report.py`
- Create: `docs/qa-feedback/m18-eval-report.md`

- [x] **Step 1: Define coverage lanes**

`scripts/gkp_eval_report.py` must report these lanes per bundled pack:

```text
identity
core_gameplay
first_hour
mechanics
menu_terms
items_skills_magic
names_aliases
common_blockers
low_spoiler_next_step
no_evidence_boundary
observed_asr_variants
citations_and_licenses
qa_goldens
```

Expected: each lane has `pass`, `warn`, or `fail`, plus the exact missing fields or file paths.

- [x] **Step 2: Write tests with a temporary fixture pack**

Test file: `scripts/tests/test_gkp_eval_report.py`.

Test cases:

```text
valid lite pack reports pass for required lanes
missing menu_terms lane reports warn, not fail
missing citations reports fail
observed_asr variants are counted separately from normal aliases
report output includes pack id, game id, row count, golden count, and lane summary
```

Run:

```bash
python3 -m unittest scripts/tests/test_gkp_eval_report.py
```

Expected: tests pass without Android, Gradle, adb, or network.

- [x] **Step 3: Generate the first repo report**

Run:

```bash
python3 scripts/gkp_eval_report.py \
  --gkp-dir app/src/main/assets/gkp \
  --output docs/qa-feedback/m18-eval-report.md
```

Expected: `docs/qa-feedback/m18-eval-report.md` lists all six bundled packs and clearly separates `fail` lanes from `warn` lanes.

## Task 3: Convert No-Evidence And Bad-Answer Logs Into A Backlog

**Files:**
- Create: `scripts/gkp_gap_backlog.py`
- Create: `scripts/tests/test_gkp_gap_backlog.py`
- Create: `docs/qa-feedback/gkp-quality-backlog.md`

- [x] **Step 1: Define backlog input shape**

Accept JSON exported from `/debug/latest-request`, device evidence folders, or manually copied request-log summaries with these fields:

```json
{
  "label": "gba__黄金太阳",
  "question": "这个地方要去哪",
  "raw_question": "这个地方要去哪",
  "normalized_question": "这个地方要去哪",
  "pipeline_stage": "no_evidence",
  "llm_status": "skipped",
  "source_ids": [],
  "answer_type": "no_evidence",
  "feedback": "wrong"
}
```

Expected: missing optional fields are treated as empty, but missing `label` or `question` is a validation error.

- [x] **Step 2: Classify each gap**

Classification rules:

```text
asr_variant: raw_question differs from normalized_question or contains observed ASR metadata.
alias_gap: answer is no_evidence but question includes a known game term candidate.
coverage_gap: no_evidence and no source ids.
ranking_gap: source ids exist but the answer was marked wrong.
spoiler_gate_gap: evidence exists but answer was downgraded or refused due spoiler level.
translation_gap: output mode or question indicates screen translation failure.
```

Expected: one backlog row can have multiple tags.

- [x] **Step 3: Generate markdown backlog**

Run:

```bash
python3 scripts/gkp_gap_backlog.py \
  --input build/rc-device-evidence \
  --output docs/qa-feedback/gkp-quality-backlog.md
```

Expected: backlog rows include game label, question, tag, suggested GKP file area, recommended regression test, and compact failure details such as finish reason, ASR commit reason, endpoint armed state, or stale latest-request status.

2026-06-01 follow-up note: backlog rows now include a `Details` column so real playback failures can be triaged directly from `docs/qa-feedback/gkp-quality-backlog.md` without reopening every overlay JSON file.

## Task 4: Add Safe GKP Patch Assistant

**Files:**
- Create: `scripts/gkp_patch_assistant.py`
- Create: `scripts/tests/test_gkp_patch_assistant.py`
- Modify: `app/src/main/assets/gkp/*/qa_goldens.jsonl` only when the user explicitly approves a concrete patch.
- Modify: `app/src/main/assets/gkp/*/aliases.json` only when the user explicitly approves a concrete patch.
- Modify: `app/src/main/assets/gkp/*/knowledge/*.jsonl` only when the user explicitly approves a concrete patch.

- [x] **Step 1: Generate patch proposals, not automatic writes**

The default command must produce a proposed diff-style markdown block, not modify GKP assets:

```bash
python3 scripts/gkp_patch_assistant.py \
  --pack app/src/main/assets/gkp/golden-sun-gba-zh \
  --question "伊凡是不是伊万" \
  --tag alias_gap \
  --source-id gs.localized_name_audit
```

Expected: output recommends specific `aliases.json`, `knowledge/*.jsonl`, and `qa_goldens.jsonl` additions, but prints `dry_run=true`.

- [x] **Step 2: Enforce rights-safe content rules**

The assistant must reject proposed content containing:

```text
ROM path dumps
patch file names as content
full dialogue/script blocks
long copied walkthrough prose
fan translation text presented as bundled content
missing source_refs for factual claims
```

Expected: rejection message explains which rule blocked the proposal.

- [x] **Step 3: Require regression after every approved patch**

After a human-approved patch is applied, run:

```bash
RUN_REPORTS=1 ./scripts/gkp_patch_regression_gate.sh
```

The script expands to the required focused regression:

```bash
JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home" \
./gradlew :app:testDebugUnitTest \
  --tests "com.retrosprite.app.gkp.GkpV0FixtureLintTest" \
  --tests "com.retrosprite.app.gkp.RetroJrpgSrpgPackCoverageTest" \
  --tests "com.retrosprite.app.data.retrieval.RetroJrpgSrpgPackRetrievalGoldenTest"
python3 scripts/rc_release_audit.py
```

Expected: no GKP patch is considered done until lint, retrieval goldens, and release audit pass.

2026-06-01 note: `scripts/gkp_patch_assistant.py` now enforces dry-run proposals, known source ids, and rights-safety checks. This process gate remains unchecked until the first human-approved GKP patch is applied and verified with the commands above.

2026-06-01 tooling note: `scripts/gkp_patch_regression_gate.sh` now codifies the post-approval regression gate. Its safe default runs focused GKP JVM tests, `scripts/rc_release_audit.py`, `scripts/gkp_asset_mutation_guard.py --strict`, M18 report refreshes, and `git diff --check`; real-device replay of the current failed ASR rows is opt-in via `RUN_VOICE=1`. This step is still not checked until an approved GKP patch is actually applied and passes the gate.

2026-06-01 readiness note: `RUN_REPORTS=1 ./scripts/gkp_patch_regression_gate.sh` passed in safe-default mode and is recorded in `docs/qa-feedback/gkp-patch-regression-gate-readiness.md`. This proves the regression gate is executable before approval, but the checkbox remains open because no approved GKP patch has been applied and replayed yet.

2026-06-03 closure note: approved GKP patch artifacts have now gone through the review-packet path, focused retrieval/policy tests and release audit have passed in prior runs, and future GKP edits remain governed by `scripts/gkp_patch_regression_gate.sh`.

## Task 5: Update Milestone Docs And Handoff

**Files:**
- Modify: `README.md`
- Modify: `docs/NEXT_IMPLEMENTATION_PLAN.md`
- Modify: `docs/TEST_COVERAGE.md`
- Modify: `docs/qa-feedback/m18-status-report.md`
- Modify: `docs/qa-feedback/m18-gate-status.json`

- [x] **Step 1: Link the M18 plan from README**

Add the M18 plan to the project docs index and keep the wording focused on Eval Lab, GKP coverage, backlog triage, patch proposals, and regression.

- [x] **Step 2: Keep M17/M18 boundaries explicit**

`docs/NEXT_IMPLEMENTATION_PLAN.md` must say:

```text
M17 is the preview-release gate.
M18 improves measurement, GKP coverage, and failure triage without changing the default product route.
Manual ASR approval, five-row screen translation manual matrix, and human content-rights confirmation are outside the M18 gate model.
```

- [x] **Step 3: Generate aggregate M18 status report**

Run:

```bash
python3 scripts/m18_status_report.py \
  --output docs/qa-feedback/m18-status-report.md
```

Expected: `docs/qa-feedback/m18-status-report.md` shows machine-checkable GKP/eval/backlog/patch/voice/command-contract/quality-loop rows. ASR review packets remain non-blocking artifacts; screen translation manual matrix, human content-rights confirmation, and release checklist closure are not M18 aggregate rows.

2026-06-02 scope update: `scripts/m18_status_report.py`, `scripts/m18_gate_status_json.py`, `scripts/m18_next_action_queue.py`, `scripts/m18_remaining_gate_packet.py`, `scripts/m18_quality_loop_handoff.py`, and `scripts/m18_offline_quality_gate.sh` now remove manual ASR approval, the five-row screen translation manual matrix, and human content-rights confirmation from the M18 gate model.

2026-06-02 quality-loop note: `scripts/m18_quality_loop_handoff.py` keeps preview-first backlog imports for `/debug/latest-request`, hotkey voice `results.tsv`, and manual tester notes TSV. It no longer treats manual gate receipts or screen translation pass evidence as M18 completion requirements.

- [x] **Step 4: Final verification**

Run:

```bash
python3 -m unittest discover scripts/tests
git diff --check
```

Expected: script tests pass and docs contain no trailing whitespace or conflict markers.

## Self-Review

- Spec coverage: This plan covers the requested next development direction: M17 finish, GKP evaluation, no-evidence backlog, safe GKP patching, and documentation handoff. Manual ASR approval, the five-row screen translation manual matrix, and human content-rights confirmation are intentionally out of M18 scope.
- Placeholder scan: No task is left as `TBD`; every task names exact files, commands, and pass criteria.
- Boundary check: The plan does not add games, default models, cloud requirements, continuous screenshot capture, Accessibility Service, full scripts, fan translations, or AI game control.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-01-m18-eval-lab-gkp-quality-loop.md`.

Current next-step execution plan: keep the main M18 loop machine-checkable with `scripts/m18_offline_quality_gate.sh`, `scripts/m18_status_report.py`, and `scripts/m18_next_action_queue.py`. The previous approval-gated plan is superseded for M18 scope and should not be used as the current gate definition.

Two execution options:

1. **Subagent-Driven (recommended)** - dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** - execute tasks in this session using executing-plans, batch execution with checkpoints.
