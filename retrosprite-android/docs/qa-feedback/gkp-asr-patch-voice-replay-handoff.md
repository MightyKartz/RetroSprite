# GKP ASR Patch And Voice Replay Handoff

- Review packet: `docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md`
- Apply dry-run: `docs/qa-feedback/gkp-patch-apply-dry-run-20260601-hotkey-voice.md`
- Voice cases: `scripts/hotkey_voice_qa_cases.tsv`
- Patch rows: 7
- Voice replay cases: 7
- Apply report status: `applied`
- Apply report assets edited: `no`

## Approval Boundary

Do not apply GKP asset changes until a human reviewer approves the exact alias and golden rows in the review packet. If these rows are already `applied`, skip the apply command and use this handoff only for device replay. This handoff does not edit GKP assets and does not play audio.

Exact apply command after approval:

```bash
python3 scripts/gkp_patch_apply_review_packet.py \
  --packet docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md \
  --output docs/qa-feedback/gkp-patch-apply-result-20260601-hotkey-voice.md \
  --apply \
  --approval "I approve gkp patch review packet 20260601 hotkey voice" \
  --strict
```

## Patch Rows

| Pack | Alias Term | Canonical | Entity | Golden QA | Status |
|---|---|---|---|---|---|
| `community.shining-force-ii-md` | 契河之域怎么 | 气合之玉怎么用 | `item.vigor-ball` | `qa.sf2.asr.item-vigor-ball.57e5a96c.zh` | `applied` |
| `community.shining-force-ii-md` | 契河之域怎么用 | 气合之玉怎么用 | `item.vigor-ball` | `qa.sf2.asr.item-vigor-ball.7aa48b89.zh` | `applied` |
| `community.golden-sun-gba-zh` | 依凡士不是一晚 | 伊凡是不是伊万 | `npc.ivan` | `qa.gs.asr.npc-ivan.31272c5b.zh` | `applied` |
| `community.final-fantasy-vi-snes-zh` | 我时系统是什么 | 魔石系统是什么 | `mechanic.magicite` | `qa.ff6.asr.mechanic-magicite.754fb479.zh` | `applied` |
| `community.chrono-trigger-snes-zh` | 麦尔是谁 | 玛尔是谁 | `npc.marle` | `qa.ct.asr.npc-marle.6e32f46d.zh` | `applied` |
| `community.final-fantasy-vi-snes-zh` | 五十系统是什么 | 魔石系统是什么 | `mechanic.magicite` | `qa.ff6.asr.mechanic-magicite.e2b7d3fc.zh` | `applied` |
| `community.phantasy-star-iv-md-zh` | 气巧和技能有什么区别 | 技巧和技能有什么区别 | `mechanic.techniques` | `qa.ps4.asr.mechanic-techniques.1e50103b.zh` | `applied` |

## Regression Commands

Run the local post-approval gate after applying rows:

```bash
RUN_REPORTS=1 ./scripts/gkp_patch_regression_gate.sh
```

After installing the patched Debug APK on RG476H and loading the target games, replay the 7 failed voice row(s):

```bash
RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 \
CASE_FILTER=sf2_vigor_ball_observed,sf2_localized_term,golden_sun_ivan_observed,ff6_magicite_observed,chrono_marle_observed,ff6_magicite_observed,phantasy_star_tech_skill_smoke \
VOICE=Tingting SAY_RATE=96 PRE_SPEAK_SECONDS=3 POST_CASE_SECONDS=10 \
POLL_ATTEMPTS=40 POLL_INTERVAL_SECONDS=2 READY_ATTEMPTS=20 READY_INTERVAL_SECONDS=1 STRICT=1 \
./scripts/hotkey_voice_qa_batch.sh
```

Capture evidence after replay:

```bash
./scripts/rc_device_evidence.sh
python3 scripts/m18_status_report.py --output docs/qa-feedback/m18-status-report.md
```

## Voice Replay Cases

| Case | Label | Spoken Prompt | Expected Stage | Expected Answer Type | Expected Source | Notes |
|---|---|---|---|---|---|---|
| `sf2_vigor_ball_observed` | `md__Shining Force II` | 气合之玉怎么用？ | `evidence` | `usage` | `sf2.promotion` | Observed ASR should normalize 契合之欲/契合之域 to 气合之玉 when the recognizer produces that transcript. |
| `sf2_localized_term` | `md__Shining Force II` | 气合之玉怎么用？ | `evidence` | `usage` | `sf2.promotion` | Localized item name / ASR-prone term. |
| `golden_sun_ivan_observed` | `gba__黄金太阳` | 伊凡是不是伊万？ | `evidence` | `name_mapping` | `gs.localized_name_audit` | Observed ASR should normalize 一凡/亿万/依凡是不是意碗 variants when the recognizer produces that transcript. |
| `ff6_magicite_observed` | `super_nintendo__Final Fantasy VI (USA)` | 魔石系统是什么？ | `evidence` | `mechanic` | `ff6.magicite_wiki` | Observed ASR should normalize 魔石系统 variants, including noisy 同时系统 suffixes, clipped 扶食系统是什, 我石心统是什么么, 核实系是什, 国时系统是什么, and 国十系统是什么 when the recognizer produces those transcripts. |
| `chrono_marle_observed` | `sfc__Chrono Trigger (USA)` | 玛尔是谁？ | `evidence` | `name_mapping` | `ct.project_notes` | Observed ASR should normalize 迈尔/纳尔 variants when the recognizer produces that transcript. |
| `ff6_magicite_observed` | `super_nintendo__Final Fantasy VI (USA)` | 魔石系统是什么？ | `evidence` | `mechanic` | `ff6.magicite_wiki` | Observed ASR should normalize 魔石系统 variants, including noisy 同时系统 suffixes, clipped 扶食系统是什, 我石心统是什么么, 核实系是什, 国时系统是什么, and 国十系统是什么 when the recognizer produces those transcripts. |
| `phantasy_star_tech_skill_smoke` | `genesis__Phantasy_Star_IV` | 技巧和技能有什么区别？ | `evidence` | `mechanic` | `ps4.techniques_wiki` | Passing smoke for Phantasy Star IV; observed ASR should normalize 气巧/继巧/记巧 clipped variants. |

## Pass Criteria

- Each replay row submits a fresh `hotkey_voice` request.
- `pipeline_stage=evidence` for each replay row.
- `llm_status=skipped` for each replay row.
- Source ids match the expected source for each replay row.
- `docs/qa-feedback/gkp-quality-backlog.md` is regenerated from the new evidence and no longer lists these ASR variants as open.
