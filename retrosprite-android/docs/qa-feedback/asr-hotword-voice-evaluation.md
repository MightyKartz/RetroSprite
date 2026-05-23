# ASR Hotword Voice Evaluation

Date: 2026-05-23

## Implementation Snapshot

- ASR hotword profiles are generated from installed GKP knowledge rows for the current resolved game.
- Hotword candidates include canonical names, aliases, and selected question-template terms.
- sherpa-onnx is configured with `modified_beam_search` only when a hotwords file is active.
- Hotword files are generated locally under the app cache directory.
- Hotkey voice flow passes the resolved ASR recognition context into the voice input provider.
- Settings shows the active ASR game-term hotword count after a contextual voice session starts.
- 2026-05-23 diagnostic hotword modes can be selected by appending `@@asr:<mode>` to the RetroArch label:
  - `none`
  - `stream_one`
  - `stream_small`
  - `stream_medium`
  - `asset_file_small`
- The diagnostic suffix is stripped before game resolution and before the hotkey voice answer request is sent to the Q&A pipeline.

## Verified

- JVM tests passed for ASR contracts, GKP hotword extraction, hotword file writing, profile provider, and recognizer config.
- Hotkey voice controller tests passed after the voice-input context signature change.
- Debug APK assembled successfully.
- RG 476H device install succeeded.
- `com.retrosprite.app/.MainActivity` started successfully and remained foreground with process id present.
- 2026-05-23 true-device retest found and fixed two sherpa hotword integration blockers:
  - `hotwordsFile` made sherpa native abort with `Load ...hotwords.txt failed`.
  - `createStream(hotwords)` required `modelingUnit = "cjkchar"`; without it sherpa exited with code 255.
- After switching to per-stream hotwords and setting `modelingUnit = "cjkchar"`, hotkey voice startup reached `AudioRecord.start` and the app process stayed alive.
- 2026-05-23 second MacBook playback retest corrected the per-stream hotword format to `修 伊/气 合 之 玉` style (`/` between hotwords, spaces between CJK chars).
- 2026-05-23 isolated true-device matrix retest restarted the app before each hotword mode. `none`, `stream_one`, `stream_small`, `stream_medium`, and `asset_file_small` all reached `AudioRecord.start`, remained active for the 7 s observation window, and showed no native crash.

## Not Yet Verified

- Reliable true microphone recognition quality for names such as `修伊`, `吉布`, `气合之玉`, `精灵森林`, and `米斯里鲁银`.
- Before/after word error comparison against the previous greedy-search ASR path.
- Latency impact of `modified_beam_search` on RG 476H during real speech.
- MacBook-speaker-to-device-microphone playback with `stream_small` produced a final transcript, but `修伊是谁` was recognized as `修医是谁`, so hotword biasing is not yet sufficient to preserve the intended character name.

## Manual Test Prompts

Use the current Shining Force II GKP context and compare the ASR transcript, answer stage, and perceived latency:

| Prompt | Expected ASR key term | Expected QA behavior |
| --- | --- | --- |
| 修伊怎么用 | 修伊 | Retrieves character guidance for Jaha/修伊 |
| 吉布是谁 | 吉布 | Retrieves the related character/entity row |
| 气合之玉怎么用 | 气合之玉 | Retrieves item usage guidance |
| 精灵森林是什么 | 精灵森林 | Retrieves location/background row |
| 米斯里鲁银有什么用 | 米斯里鲁银 | Retrieves mithril item guidance |

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

## Question Normalization Retest

| Time | Raw ASR transcript | Normalized question | Mode | Answer stage | Result | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| 2026-05-23 unit regression | 修医是谁 | 修伊是谁 | hotkey_voice:text | evidence | Pass | GKP-scoped `GameTermNormalizer` corrects the ASR homophone before retrieval while retaining raw/normalized diagnostics. |
| 2026-05-23 true-device HTTP retest | 修医是谁 | 修伊是谁 | hotkey_voice:text | evidence | Pass with diagnostic gap | RG476H returned the Chester/修伊 evidence answer through `/` with `output=hotkey_voice:text`; `/debug/latest-request` showed `question=修伊是谁` and `pipeline_stage=evidence`, but did not surface `raw_question` in the live JSON during this run. |
| 2026-05-23 22:48 true-device diagnostic retest | 修医是谁 | 修伊是谁 | hotkey_voice:text | evidence | Pass | After preserving normalization fields in `RequestLogEntry.toDomainModel()`, `/debug/latest-request` returned `raw_question=修医是谁`, `normalized_question=修伊是谁`, `question_normalization_reason=homophone`, and `normalized_question_matched_entity_id=npc.chester`. Pulling the Room database with its WAL confirmed the latest row as `修伊是谁|修医是谁|修伊是谁|homophone|修伊|npc.chester`. |
