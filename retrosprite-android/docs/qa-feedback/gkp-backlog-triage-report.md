# M18 GKP Backlog Triage Report

- Backlog: `docs/qa-feedback/gkp-quality-backlog.md`
- Review packet: `docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md`
- Items: 10
- Categories: asr_patch_applied=7, device_rerun_passed=1, policy_golden_existing=2
- Status: covered_by_applied_patch=7, covered_by_device_rerun=1, covered_by_existing_golden=2
- GKP assets edited: no

| Label | Question | Tags | Category | Status | Patch Match | Next Step | Evidence |
|---|---|---|---|---|---|---|---|
| `md__Shining Force II` | 气合之玉怎么用？ | `alias_gap, asr_variant, coverage_gap` | `asr_patch_applied` | `covered_by_applied_patch` | community.shining-force-ii-md: 契河之域怎么 -> 气合之玉怎么用 | Patch rows are already applied; keep the GKP regression result and replay this voice row on device. | `build/hotkey-voice-qa/20260602-051126/results.tsv` |
| `sfc__Chrono Trigger (USA)` | 时空之轮主要玩什么？ | `voice_lifecycle_gap` | `device_rerun_passed` | `covered_by_device_rerun` | build/hotkey-voice-qa/20260602-060413/results.tsv | No GKP asset change is needed; keep the fresh voice evidence and reopen only if a later matrix run regresses. finish=answer_completed; sources=ct.square_enix | `build/hotkey-voice-qa/20260602-060413/results.tsv` |
| `md__Shining Force II` | 气合之玉怎么用？ | `asr_variant, ranking_gap` | `asr_patch_applied` | `covered_by_applied_patch` | community.shining-force-ii-md: 契河之域怎么 -> 气合之玉怎么用 | Patch rows are already applied; keep the GKP regression result and replay this voice row on device. | `build/hotkey-voice-qa/20260602-051126/results.tsv` |
| `gba__黄金太阳` | 伊凡是不是伊万？ | `alias_gap, asr_variant, coverage_gap` | `asr_patch_applied` | `covered_by_applied_patch` | community.golden-sun-gba-zh: 依凡士不是一晚 -> 伊凡是不是伊万 | Patch rows are already applied; keep the GKP regression result and replay this voice row on device. | `build/hotkey-voice-qa/20260602-051126/results.tsv` |
| `super_nintendo__Final Fantasy VI (USA)` | 魔石系统是什么？ | `alias_gap, asr_variant, coverage_gap` | `asr_patch_applied` | `covered_by_applied_patch` | community.final-fantasy-vi-snes-zh: 我时系统是什么 -> 魔石系统是什么 | Patch rows are already applied; keep the GKP regression result and replay this voice row on device. | `build/hotkey-voice-qa/20260602-051126/results.tsv` |
| `super_nintendo__Final Fantasy VI (USA)` | 直接告诉我崩坏世界完整路线。 | `coverage_gap, spoiler_gate_gap` | `policy_golden_existing` | `covered_by_existing_golden` | app/src/main/assets/gkp/final-fantasy-vi-snes-zh: qa.ff6.no-ruin-route.zh | Do not add a duplicate golden; inspect runtime/latest-request stage and replay this boundary row if the voice result still fails. | `build/hotkey-voice-qa/20260602-051126/results.tsv` |
| `md__Langrisser II (Japan)` | 直接告诉我所有路线条件。 | `coverage_gap, spoiler_gate_gap` | `policy_golden_existing` | `covered_by_existing_golden` | app/src/main/assets/gkp/langrisser-ii-md-zh: qa.l2.no-all-routes.zh | Do not add a duplicate golden; inspect runtime/latest-request stage and replay this boundary row if the voice result still fails. | `build/hotkey-voice-qa/20260602-051126/results.tsv` |
| `sfc__Chrono Trigger (USA)` | 玛尔是谁？ | `alias_gap, asr_variant, coverage_gap` | `asr_patch_applied` | `covered_by_applied_patch` | community.chrono-trigger-snes-zh: 麦尔是谁 -> 玛尔是谁 | Patch rows are already applied; keep the GKP regression result and replay this voice row on device. | `build/hotkey-voice-qa/20260602-083111/results.tsv` |
| `genesis__Phantasy_Star_IV` | 技巧和技能有什么区别？ | `ranking_gap` | `asr_patch_applied` | `covered_by_applied_patch` | community.phantasy-star-iv-md-zh: 气巧和技能有什么区别 -> 技巧和技能有什么区别 | Patch rows are already applied; keep the GKP regression result and replay this voice row on device. | `build/hotkey-voice-qa/20260603-073814/results.tsv` |
| `super_nintendo__Final Fantasy VI (USA)` | 魔石系统是什么？ | `alias_gap, asr_variant, coverage_gap` | `asr_patch_applied` | `covered_by_applied_patch` | community.final-fantasy-vi-snes-zh: 五十系统是什么 -> 魔石系统是什么 | Patch rows are already applied; keep the GKP regression result and replay this voice row on device. | `build/hotkey-voice-qa/20260603-073814/results.tsv` |
