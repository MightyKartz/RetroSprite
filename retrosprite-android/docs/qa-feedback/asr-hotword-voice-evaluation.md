# ASR Variant Voice Evaluation

Date: 2026-05-25

## Current Paraformer Snapshot

- Current default ASR model: `sherpa-onnx-streaming-paraformer-bilingual-zh-en`
  with `encoder.int8.onnx`, `decoder.int8.onnx`, and `tokens.txt`.
- Runtime path: sherpa-onnx `OnlineRecognizer` with `OnlineParaformerModelConfig`.
- Native sherpa hotwords are not part of the current product path. Overlay and
  endpoint diagnostics should report `asr_architecture=paraformer`,
  `asr_decoding_method=greedy_search`, and no `asr_hotword_*` fields.
- Game-specific proper nouns remain a GKP responsibility. Acceptable voice QA
  requires raw ASR plus current-game `GameTermNormalizer` repair from GKP
  `asr_variant` / `observed_asr` alias metadata to preserve terms such as
  `修伊`, `气合之玉`, `玛尔`, and `米斯里鲁`/`秘银`.
- The bundled APK should contain only the Paraformer assets and no Zipformer,
  Transducer hotword asset, or `asr-hotwords` file.

## Current Implementation Snapshot

- `GkpV0Parser` preserves structured alias metadata from `aliases.json` instead
  of flattening all terms into display aliases only.
- Room schema v12 stores alias metadata in `knowledge.alias_metadata_json`.
- `GkpAsrVariantIndex` reads only current-game aliases whose `kind` is
  `asr_variant` / `observed_asr` or whose `source` is `observed_asr`.
- `GameTermNormalizer` applies `term -> canonical_term` before exact/fuzzy
  retrieval, then records `gkp_asr_variant` or `gkp_observed_asr_variant`
  diagnostics.
- `SherpaEndpointCommitGate` commits after sherpa endpoint detection, voice
  inactivity, and stable partial text. It keeps final flush silence and uses a
  small extra wait for incomplete question tails, but it does not invent missing
  text such as `是什 -> 是什么`.
- Preflight and fixture lint require ASR aliases to have a non-empty
  `canonical_term` different from `term`.

## Verified

- JVM tests passed for Paraformer ASR contracts, GKP alias metadata parsing,
  preflight/lint, current-game ASR variant normalization, endpoint diagnostics,
  retrieval goldens, ASR final/partial transcript selection, and the hotkey
  endpoint commit gate.
- Real-device MacBook-speaker QA covered all six bundled GKP packs on
  2026-05-25. Initial Golden Sun, Chrono Trigger ATB, and Final Fantasy VI
  failures were converted into scoped GKP `observed_asr` aliases or retrieval
  rows, then retested.
- A later 2026-05-25 Tingting run verified the updated capture/commit lifecycle
  reached overlay `finished` for all tested cases and passed Golden Sun, Chrono
  Trigger, and Langrisser II smoke prompts.
- Debug APK assembly should be used as the packaging gate; check the APK asset
  listing for Paraformer-only contents.

## Not Yet Verified

- Reliable true microphone recognition quality for names such as `修伊`, `吉布`,
  `气合之玉`, `精灵森林`, and `米斯里鲁银` across human speakers and device
  placement.
- Before/after word error comparison between Paraformer raw transcripts and
  normalized GKP answers across all bundled packs.
- Whether `气河之欲怎么用` should be absorbed by Shining Force II GKP
  `observed_asr` metadata and routed to `sf2.promotion` instead of a character
  source row.

## Manual Test Prompts

Use the current Shining Force II GKP context and compare the raw transcript,
normalized question, answer stage, and perceived latency:

| Prompt | Expected ASR key term | Expected QA behavior |
| --- | --- | --- |
| 修伊怎么用 | 修伊 | Retrieves character guidance for Jaha/修伊 |
| 吉布是谁 | 吉布 | Retrieves the related character/entity row |
| 气合之玉怎么用 | 气合之玉 | Retrieves item usage guidance |
| 精灵森林是什么 | 精灵森林 | Retrieves location/background row |
| 米斯里鲁银有什么用 | 米斯里鲁银 | Retrieves mithril item guidance |

## 2026-05-25 all-GKP Paraformer ASR variant pass

- ASR architecture: Paraformer / greedy_search.
- Native hotwords: disabled.
- Normalization source: current-game GKP `asr_variant` / `observed_asr`.
- Evidence directories:
  - `build/hotkey-voice-qa/20260525-021905`: first seven-case all-GKP run,
    4/7 passed.
  - `build/hotkey-voice-qa/20260525-022506`: focused retry after first fixes,
    1/3 passed and exposed two narrower gaps.
  - `build/hotkey-voice-qa/20260525-022833`: focused retry after second fixes,
    confirmed Chrono ATB retrieval and exposed one Golden Sun tail variant.
  - `build/hotkey-voice-qa/20260525-023244`: final focused retry, 2/2 passed.
- Coverage added: Shining Force II, Golden Sun, Chrono Trigger, Final Fantasy VI, Langrisser II, and Phantasy Star IV all have scoped ASR variant goldens or smoke cases.
- Outcome: passed after scoped data fixes. Exact final passing rows:
  - Golden Sun / `伊凡是不是伊万？`: ASR transcript `一凡是不是意外`,
    normalized to `伊凡是不是伊万`, matched `npc.ivan`, answer type
    `name_mapping`, stage `evidence`, source `gs.localized_name_audit`,
    overlay `finished`, result `PASS`.
  - Chrono Trigger / `时间条战斗怎么理解？`: ASR transcript
    `时间挑战斗怎么理解`, normalized to `时间条战斗怎么理解`, matched
    `mechanic.atb`, answer type `mechanic`, stage `evidence`, source
    `ct.project_notes`, overlay `finished`, result `PASS`.
- First all-GKP run passing rows:
  - Shining Force II / `气合之玉怎么用？`: ASR transcript `契合之欲怎么`,
    normalized to `气合之玉怎么用`, stage `evidence`, source
    `sf2.promotion`, result `PASS`.
  - Chrono Trigger / `玛尔是谁？`: ASR transcript `迈尔是`, normalized to
    `玛尔是谁`, stage `evidence`, source `ct.project_notes`, result `PASS`.
  - Langrisser II / `指挥官是什么？`: ASR transcript `指挥官是什`, stage
    `evidence`, source `l2.project_notes`, result `PASS`.
  - Phantasy Star IV / `技巧和技能有什么区别？`: ASR transcript
    `技巧和技能有什么区别`, stage `evidence`, sources
    `ps4.techniques_wiki,ps4.community_wiki`, result `PASS`.
  - Final Fantasy VI / `魔石系统是什么？`: initial ASR transcript
    `无石系统是什么` failed; after scoped FF6 variants, retry transcript
    `无时系统是什么` normalized to `魔石系统是什么`, stage `evidence`,
    source `ff6.magicite_wiki`, result `PASS`.
- Residual note: Golden Sun's Ivan phrasing is ASR-unstable across repeated
  MacBook-speaker runs (`一凡是不是一`, `伊凡是不是一`, `一凡是不是因`,
  `一凡是不是意外`). These are now covered in GKP metadata rather than global
  code rewrites.

## 2026-05-25 mixed-pack ASR follow-up

- Evidence directories:
  - `build/hotkey-voice-qa/20260525-023941`: 12-case mixed GKP run, 11/12
    passed. Final Fantasy VI `魔石系统是什么？` failed because Paraformer heard
    `何石系统是什么`, which did not yet normalize.
  - `build/hotkey-voice-qa/20260525-024634`: focused Final Fantasy VI retry
    passed with transcript `磨石系统是什么` normalized to `魔石系统是什么`.
- Fixes applied after this follow-up:
  - Added scoped FF6 `observed_asr` metadata for
    `何石系统是什么 -> 魔石系统是什么`.
  - Added a golden for `qa.ff6.asr.magicite-heshi-system.zh`.
  - Added normalizer cleanup for ASR-variant replacement overlap so clipped
    variants no longer produce repeated tails such as `怎么用用` or `什么么`.

## 2026-05-25 capture/commit tuning and Tingting retest

- Code direction: fix tail drops at capture/commit time, not with generic text
  completion. The gate now waits for endpoint detection plus post-voice silence
  and stable partial text, keeps final flush silence, and slightly extends waits
  for incomplete question tails.
- Evidence directory: `build/hotkey-voice-qa/20260525-113514`.
- Result: 3/4 strict cases passed.
  - Golden Sun / `黄金太阳主要玩什么？`: transcript `黄金太阳主要玩什么`,
    stage `evidence`, source `gs.official_manual`, overlay `finished`, result
    `PASS`.
  - Chrono Trigger / `时空之轮主要玩什么？`: transcript `时空之轮主要完什么`,
    stage `evidence`, source `ct.square_enix`, overlay `finished`, result
    `PASS`.
  - Langrisser II / `指挥官是什么？`: transcript `指挥官是什么`, stage
    `evidence`, source `l2.project_notes`, overlay `finished`, result `PASS`.
  - Shining Force II / `气合之玉怎么用？`: transcript `气河之欲怎么用`, stage
    `evidence`, source `sf2.characters`, strict result `FAIL` because the
    expected source was `sf2.promotion`. This is a current-game ASR
    variant/source-ranking gap, not an overlay lifecycle failure.

## Historical Hotword Experiments

Rows dated 2026-05-23 below document the previous Zipformer/Transducer native
hotword experiments. They are useful failure evidence, but they are not the
current Paraformer runtime contract.

## M16 Multi-Pack Voice QA Matrix

2026-05-24 tooling status:

- `scripts/hotkey_voice_qa_cases.tsv` defines a reusable three-pack matrix for Shining Force II, Golden Sun, and Chrono Trigger.
- Each pack has three voice lanes: core gameplay, localized term, and Lite/no-evidence boundary.
- `scripts/hotkey_voice_qa_batch.sh` can run the matrix with one fixed macOS Chinese voice, defaulting to `Tingting`.
- For a more reproducible Mandarin QA source, set `TTS_BACKEND=sherpa_onnx`. This uses the local sherpa-onnx VITS wrapper in `scripts/sherpa_zh_tts.py`, the 16 kHz Chinese-only `sherpa-onnx-vits-zh-ll` model under `~/.local/share/retrosprite/sherpa-onnx-tts/models/`, and records the generated wav path plus metadata beside each case.
- Safe default is dry-run only; actual speaker playback requires both `RUN_PLAYBACK=1` and `CONFIRM_PLAYBACK=1`.
- The script captures `/debug/hotkey-voice-overlay` and `/debug/latest-request` JSON for each case, then records raw question, normalized question, matched term/entity, answer type, pipeline stage, LLM status, source ids, overlay phase, and finish reason.
- Before playing MacBook audio, the script waits for any previous overlay session to finish, then requires the current overlay `label` to match the case and `mic_live=true`. This avoids speaking into a stale game context or into the preparing/mic-off window.
- Evidence files are written under `build/hotkey-voice-qa/<timestamp>/` by default, so generated run artifacts do not dirty the docs tree.

Dry-run:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
DRY_RUN=1 ./scripts/hotkey_voice_qa_batch.sh
```

True MacBook-speaker-to-device run:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 ./scripts/hotkey_voice_qa_batch.sh
```

Stable sherpa-onnx Mandarin TTS source:

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
TTS_BACKEND=sherpa_onnx RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 ./scripts/hotkey_voice_qa_batch.sh
```

Current verification boundary: the matrix and script are implemented and have
multiple real-device MacBook-speaker evidence runs. Treat per-case status from
the latest evidence directory as authoritative; do not upgrade strict failures
to passed without a matching source/stage retest.

## Result Log

Append real-device results here:

| Time | Prompt spoken | ASR transcript | Hotwords count | Answer stage | Result | Notes |
| --- | --- | --- | ---: | --- | --- | --- |
| 2026-05-23 20:43 | 修伊怎么用 | 量化是眼睛一准备用 | 70+ | no_evidence | Fail | Pre-fix run produced a wrong transcript despite hotwords file existing. |
| 2026-05-23 20:47 | hotkey wake only | n/a | 70+ | n/a | Fail | `hotwordsFile` path caused sherpa native abort: `Load ...hotwords.txt failed`. |
| 2026-05-23 20:57 | hotkey wake only | n/a | 70+ | n/a | Fail | Per-stream hotwords without `modelingUnit` caused sherpa warning and process exit 255. |
| 2026-05-23 20:58 | hotkey wake only | n/a | 70+ | n/a | Pass | With per-stream hotwords and `modelingUnit = "cjkchar"`, AudioRecord started and process stayed alive. |
| 2026-05-23 20:59 | 修伊怎么用 | no new final transcript | 70+ | n/a | Inconclusive | MacBook speaker playback did not create a new `/debug/latest-request` entry. |
| 2026-05-23 21:00 | 这游戏怎么玩 | no new final transcript | 70+ | n/a | Inconclusive | Common phrase also produced no new final transcript through MacBook speaker playback. |
| 2026-05-23 21:03 | 修伊怎么用 x3 | no new final transcript | 70+ | n/a | Fail | Pre-format-fix: AudioRecord started then stopped after about 18 ms, before playback. |
| 2026-05-23 21:06 | hotkey wake only | n/a | 70+ | n/a | Pass | After stream format fix, AudioRecord remained active and process stayed alive. |
| 2026-05-23 21:07 | 修伊怎么用 x3 | no new final transcript | 70+ | n/a | Inconclusive | AudioRecord stayed active for about 20 s, but MacBook playback produced no final transcript. |
| 2026-05-23 21:08 | 这游戏怎么玩 x3 | no new final transcript | 70+ | n/a | Inconclusive | Common phrase also produced no final transcript through MacBook playback. |
| 2026-05-23 21:13 | 这游戏怎么玩 x4 | no new final transcript | 32 stream terms | n/a | Fail | High-gain MacBook playback; hotword stream stopped AudioRecord after about 13 ms. |
| 2026-05-23 21:14 | hotkey wake only, unknown game | n/a | 0 | n/a | Pass | No-hotword greedy path kept AudioRecord active beyond 6 s. |
| 2026-05-23 21:16 | 这游戏怎么玩 x4 | no new final transcript | 32 stream terms | n/a | Fail | Clean retest after previous session ended; hotword stream again stopped AudioRecord after about 13 ms. |
| 2026-05-23 21:30 | hotkey wake only | n/a | 0 | n/a | Pass | `@@asr:none`; isolated app restart; greedy-search path reached `AudioRecord.start` and remained active for 7 s. |
| 2026-05-23 21:31 | hotkey wake only | n/a | 1 stream term | n/a | Pass | `@@asr:stream_one`; `modified_beam_search`, `modelingUnit=cjkchar`, no `hotwordsFile`; reached `AudioRecord.start` and remained active for 7 s. |
| 2026-05-23 21:32 | hotkey wake only | n/a | 3 stream terms | n/a | Pass | `@@asr:stream_small`; reached `AudioRecord.start` and remained active for 7 s. |
| 2026-05-23 21:32 | hotkey wake only | n/a | 8 stream terms | n/a | Pass | `@@asr:stream_medium`; reached `AudioRecord.start` and remained active for 7 s. |
| 2026-05-23 21:33 | hotkey wake only | n/a | 3 asset-file terms | n/a | Pass | `@@asr:asset_file_small`; bundled asset path `asr-hotwords/shining-force-ii-md-small.hotwords.txt`; reached `AudioRecord.start` and remained active for 7 s. |
| 2026-05-23 21:34 | 修伊是谁 | 修医是谁 | 3 stream terms | no_evidence | Fail | `@@asr:stream_small`; MacBook `say -v Ting-Ting`; ASR finalized but confused `修伊` with homophone `修医`, so Q&A did not hit the character row. |
| 2026-05-24 20:43-20:47 | 10-case matrix | see `build/hotkey-voice-qa/20260524-204322/results.tsv` | 160 | mixed | 9/10 Pass | After waiting for `render_phase=listening`, Shining Force II and Golden Sun passed; only `玛尔是谁` was heard as `那儿是谁` and missed Chrono Trigger evidence. |
| 2026-05-24 20:49 | 玛尔是谁 | see `build/hotkey-voice-qa/20260524-204927/results.tsv` | 160 | evidence/name_mapping | Pass | After adding the `那儿是谁 -> 玛尔是谁` current-GKP rewrite, the Chrono Trigger localized-name case hit `ct.project_notes`. |
| 2026-05-25 11:35-11:37 | 4-case Tingting retest | see `build/hotkey-voice-qa/20260525-113514/results.tsv` | n/a Paraformer | evidence | 3/4 Pass | Golden Sun, Chrono Trigger, and Langrisser II reached overlay `finished` and matched expected sources. Shining Force II heard `气河之欲怎么用` and returned `sf2.characters` instead of expected `sf2.promotion`, so it remains a strict ASR variant/source-ranking failure. |
| 2026-05-25 17:12-17:15 | 6-case main-loop Tingting retest | see `build/hotkey-voice-qa/20260525-171231/results.tsv` | n/a Paraformer | muted/no-submit | 0/6 Pass | Playback timing was correct: every case waited for `Mic live` before speech and reached `finished` before the next case, with 9.8-10.9 s gaps. ASR selected only blank, `这游`, or `那`, so the new short-fragment guard muted all cases and prevented stale or wrong GKP submissions. |
| 2026-05-25 17:18-17:21 | 6-case main-loop Tingting retest at 50% MacBook volume | see `build/hotkey-voice-qa/20260525-171857/results.tsv` | n/a Paraformer | evidence | 6/6 Pass | Set macOS output volume to 50 before playback. Every case waited for `Mic live`, reached `answer_completed`, and waited 9.3-10.8 s before the next case. ASR variants such as `契合之欲怎么`, `一凡是不是一`, `迈尔是谁`, and `无时系统是什么` normalized to the expected GKP terms and sources. |
| 2026-05-25 17:28-17:35 | 14-case expanded Tingting retest at 50% MacBook volume | see `build/hotkey-voice-qa/20260525-172819/results.tsv` | n/a Paraformer | mixed | 13/14 Pass | Expanded across Shining Force II, Golden Sun, Chrono Trigger, FF6, Langrisser II, and Phantasy Star IV. Text baseline was 14/14 before voice. Voice timing was correct: every case waited for `Mic live`, reached `answer_completed`, and waited 9.7-11.2 s before the next case. Only `克拉肯怎么过？` failed: ASR selected `克拉盆怎么`, resulting in no-evidence while suggesting the correct `克拉肯` questions. |
| 2026-05-25 17:35-17:36 | Shining Force II boss retry | see `build/hotkey-voice-qa/20260525-173544/results.tsv` | n/a Paraformer | no_evidence | Fail | Focused retry of `克拉肯怎么过？` again produced `克拉盆怎么` and no-evidence. This is now repeated evidence for adding a current-game observed-ASR normalization or alias repair, not a timing failure. |
| 2026-05-25 17:49 | Shining Force II boss retry after observed-ASR repair | see `build/hotkey-voice-qa/20260525-174853/results.tsv` | n/a Paraformer | evidence/strategy | Pass | After adding `克拉盆怎么 -> 克拉肯怎么过` to the current Shining Force II GKP, the focused voice retry passed with `question_source=hotkey_voice`, `answer_type=strategy`, `source_ids=[sf2.enemy_boss_notes]`, `llm_status=skipped`, and overlay `finish_reason=answer_completed`. This particular retry recognized the canonical `克拉肯怎么过` directly; the runtime text probe covers the repeated `克拉盆怎么` transcript. |
| 2026-05-25 17:49-17:56 | 14-case expanded Tingting retest after Kraken repair | see `build/hotkey-voice-qa/20260525-174948/results.tsv` | n/a Paraformer | mixed | 12/14 Pass | Kraken, Vigor Ball, Ivan, Marle, Chrono ATB, FF6 Magicite, Langrisser II, and Phantasy Star IV evidence cases passed with local sources and `llm_status=skipped`. Two source-grounded no-evidence boundary cases failed only because ASR drifted once (`精灵 -> 经营`, `直接 -> 十接`) and fell back to generic no-evidence; both still refused safely without LLM. |
| 2026-05-25 17:57-17:58 | Focused no-evidence boundary retry | see `build/hotkey-voice-qa/20260525-175713/results.tsv` | n/a Paraformer | evidence/no_evidence | 2/2 Pass | Retried the two expanded-run boundary failures. Golden Sun recognized `直接列出所有精灵位置` and hit `gs.project_notes`; Chrono Trigger recognized `直接告诉我所有结局` and hit `ct.project_notes`. Treat the earlier two failures as one-off ASR drift and do not add new observed-ASR aliases unless they repeat. |
| 2026-05-25 18:11-18:18 | 15-case expanded Tingting retest | see `build/hotkey-voice-qa/20260525-181118/results.tsv` | n/a Paraformer | mixed | 14/15 Pass | Expanded to 15 cases across Shining Force II, Golden Sun, Chrono Trigger, FF6, Langrisser II, and Phantasy Star IV. The repeated Golden Sun Lite boundary drift produced `我接立出所有基精灵未位`, fell back to generic no-evidence, and stayed local with `llm_status=skipped`; because this repeated the same source-grounded boundary class, it triggered a scoped observed-ASR repair. |
| 2026-05-25 18:32 | Golden Sun boundary retry after observed-ASR and QA-gate repair | see `build/hotkey-voice-qa/20260525-183204/results.tsv` | n/a Paraformer | evidence/no_evidence | Pass | Final focused retry waited for `label=gba__黄金太阳` and `mic_live=true` before playback, then reached `answer_completed`. ASR transcript was canonical `直接列出所有精灵位置`, answer type was `no_evidence`, source was `gs.project_notes`, and `llm_status=skipped`. The earlier 18:30 strict failure was a test-harness stale-label issue (`snes__super_mario_world`), not an ASR/GKP failure. |

## Question Normalization Retest

| Time | Raw ASR transcript | Normalized question | Mode | Answer stage | Result | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| 2026-05-23 unit regression | 修医是谁 | 修伊是谁 | hotkey_voice:text | evidence | Pass | GKP-scoped `GameTermNormalizer` corrects the ASR homophone before retrieval while retaining raw/normalized diagnostics. |
| 2026-05-23 true-device HTTP retest | 修医是谁 | 修伊是谁 | hotkey_voice:text | evidence | Pass with diagnostic gap | RG476H returned the Chester/修伊 evidence answer through `/` with `output=hotkey_voice:text`; `/debug/latest-request` showed `question=修伊是谁` and `pipeline_stage=evidence`, but did not surface `raw_question` in the live JSON during this run. |
| 2026-05-23 22:48 true-device diagnostic retest | 修医是谁 | 修伊是谁 | hotkey_voice:text | evidence | Pass | After preserving normalization fields in `RequestLogEntry.toDomainModel()`, `/debug/latest-request` returned `raw_question=修医是谁`, `normalized_question=修伊是谁`, `question_normalization_reason=homophone`, and `normalized_question_matched_entity_id=npc.chester`. Pulling the Room database with its WAL confirmed the latest row as `修伊是谁|修医是谁|修伊是谁|homophone|修伊|npc.chester`. |
| 2026-05-24 true-device debug-text retest | 密营有什么用 | 秘银有什么用 | debug:hotkey_voice:text | evidence | Pass | RG476H `/debug/latest-request` returned `raw_question=密营有什么用`, `normalized_question=秘银有什么用`, `question_normalization_reason=observed_asr_rewrite`, `normalized_question_matched_entity_id=item.mithril`, and `source_ids=[sf2.items]`. |
| 2026-05-25 17:47 true-device hotkey-voice text probe | 克拉盆怎么 | 克拉肯怎么过 | hotkey_voice:text | evidence | Pass | RG476H `/debug/latest-request` returned `raw_question=克拉盆怎么`, `normalized_question=克拉肯怎么过`, `question_normalization_reason=gkp_observed_asr_variant`, `normalized_question_matched_entity_id=boss.kraken`, `source_ids=[sf2.enemy_boss_notes]`, and `llm_status=skipped`. |
| 2026-05-25 18:24 true-device debug-text regression | 直接列出所有经营位置 / 我接立出所有基精灵未位 | 直接列出所有精灵位置 | debug:hotkey_voice:text | evidence | Pass | RG476H `/debug/latest-request` normalized both repeated Golden Sun boundary ASR variants to `直接列出所有精灵位置`, matched `strategy.lite-boundary`, returned `answer_type=no_evidence`, `source_ids=[gs.project_notes]`, and `llm_status=skipped`. |
