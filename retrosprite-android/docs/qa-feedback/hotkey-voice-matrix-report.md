# M18 Hotkey Voice Matrix Report

- Cases: `scripts/hotkey_voice_qa_cases.tsv`
- Results: `build/hotkey-voice-qa/20260603-200807/results.tsv`
- Evidence root: `build/hotkey-voice-qa/20260603-200807`
- Total: 7
- Status: pass=4, fail=3, blocked=0, not_run=0, missing=0
- Failure categories: asr_variant=1, source_mismatch=2
- Strict pass: `no`

| Case | Status | Pack | Label | Prompt | Transcript | Stage | Answer | LLM | Sources | Finish | ASR Commit | Audio Reads | Peak | Failure Category | Contract Check | Evidence |
|---|---|---|---|---|---|---|---|---|---|---|---|---:|---:|---|---|---|
| `sf2_vigor_ball_observed` | `pass` | `shining-force-ii-md` | `md__Shining Force II` | 气合之玉怎么用？ | 气合之欲怎么用 | `evidence` | `usage` | `skipped` | sf2.promotion | `answer_completed` | `soft_stop_after_silence_and_stable_partial` | 969 | 0.032551326 | `-` | - | `build/hotkey-voice-qa/20260603-200807` |
| `golden_sun_ivan_observed` | `fail` | `golden-sun-gba-zh` | `gba__黄金太阳` | 伊凡是不是伊万？ | 依凡是不是意碗 | `no_evidence` | `no_evidence` | `skipped` | - | `answer_completed` | `soft_stop_after_silence_and_stable_partial` | 969 | 0.021863889 | `asr_variant` | pipeline_stage expected evidence actual no_evidence; answer_type expected name_mapping actual no_evidence; source_ids missing gs.localized_name_audit | `build/hotkey-voice-qa/20260603-200807` |
| `chrono_marle_observed` | `pass` | `chrono-trigger-snes-zh` | `sfc__Chrono Trigger (USA)` | 玛尔是谁？ | 麦尔是谁 | `evidence` | `name_mapping` | `skipped` | ct.project_notes | `answer_completed` | `soft_stop_after_silence_and_stable_partial` | 909 | 0.021524446 | `-` | - | `build/hotkey-voice-qa/20260603-200807` |
| `chrono_atb_observed` | `pass` | `chrono-trigger-snes-zh` | `sfc__Chrono Trigger (USA)` | 时间条战斗怎么理解？ | 时间调战斗怎么理解 | `evidence` | `mechanic` | `skipped` | ct.project_notes | `answer_completed` | `soft_stop_after_silence_and_stable_partial` | 1029 | 0.03142566 | `-` | - | `build/hotkey-voice-qa/20260603-200807` |
| `ff6_magicite_observed` | `fail` | `final-fantasy-vi-snes-zh` | `super_nintendo__Final Fantasy VI (USA)` | 魔石系统是什么？ | 核实系是什 | `unknown` | `no_evidence` | `skipped` | - | `answer_completed` | `soft_stop_after_silence_and_stable_partial` | 969 | 0.0361312 | `source_mismatch` | pipeline_stage expected evidence actual unknown; answer_type expected mechanic actual no_evidence; source_ids missing ff6.magicite_wiki | `build/hotkey-voice-qa/20260603-200807` |
| `langrisser_commander_smoke` | `pass` | `langrisser-ii-md-zh` | `md__Langrisser II (Japan)` | 指挥官是什么？ | 指挥官是什么 | `evidence` | `mechanic` | `skipped` | l2.project_notes | `answer_completed` | `soft_stop_after_silence_and_stable_partial` | 980 | 0.028207889 | `-` | - | `build/hotkey-voice-qa/20260603-200807` |
| `phantasy_star_tech_skill_smoke` | `fail` | `phantasy-star-iv-md-zh` | `genesis__Phantasy_Star_IV` | 技巧和技能有什么区别？ | 记巧和技能有什么区 | `evidence` | `unknown_or_out_of_scope` | `skipped` | ps4.community_wiki | `answer_completed` | `soft_stop_after_silence_and_stable_partial` | 1029 | 0.02141558 | `source_mismatch` | answer_type expected mechanic actual unknown_or_out_of_scope; source_ids missing ps4.techniques_wiki | `build/hotkey-voice-qa/20260603-200807` |
