# M18 GKP Patch Proposals - Hotkey Voice ASR Variants

- Date: 2026-06-02
- Input evidence: `build/hotkey-voice-qa/20260602-051126/results.tsv` and `build/hotkey-voice-qa/20260602-083111/results.tsv`
- Status: dry-run proposals only; no GKP asset was edited.
- Reason: RG476H playback matrix submits fresh `hotkey_voice` requests for most rows. The 2026-06-03 replay left two actionable rows: FF6 `五十系统是什么` stayed `no_evidence`, and PS4 `气巧和技能有什么区别` selected the wrong source. This packet keeps them as scoped alias + golden changes before the next device replay.

## Proposed Alias Additions

These are scoped `observed_asr` aliases. Existing knowledge rows already exist, so the likely safe patch is alias + regression golden only.

| Pack | Observed ASR | Canonical | Entity | Source |
|---|---|---|---|---|
| `community.shining-force-ii-md` | `契河之域怎么` | `气合之玉怎么用` | `item.vigor-ball` | `sf2.promotion` |
| `community.shining-force-ii-md` | `契河之域怎么用` | `气合之玉怎么用` | `item.vigor-ball` | `sf2.promotion` |
| `community.golden-sun-gba-zh` | `依凡士不是一晚` | `伊凡是不是伊万` | `npc.ivan` | `gs.localized_name_audit` |
| `community.final-fantasy-vi-snes-zh` | `我时系统是什么` | `魔石系统是什么` | `mechanic.magicite` | `ff6.magicite_wiki` |
| `community.chrono-trigger-snes-zh` | `麦尔是谁` | `玛尔是谁` | `npc.marle` | `ct.project_notes` |
| `community.final-fantasy-vi-snes-zh` | `五十系统是什么` | `魔石系统是什么` | `mechanic.magicite` | `ff6.magicite_wiki` |
| `community.phantasy-star-iv-md-zh` | `气巧和技能有什么区别` | `技巧和技能有什么区别` | `mechanic.techniques` | `ps4.techniques_wiki` |

## Generated Commands

```bash
python3 scripts/gkp_patch_assistant.py \
  --pack app/src/main/assets/gkp/shining-force-ii-md \
  --question '气合之玉怎么用？' \
  --tag asr_variant \
  --source-id sf2.promotion \
  --entity-id item.vigor-ball \
  --observed-asr '契河之域怎么' \
  --canonical-term '气合之玉怎么用'

python3 scripts/gkp_patch_assistant.py \
  --pack app/src/main/assets/gkp/shining-force-ii-md \
  --question '气合之玉怎么用？' \
  --tag asr_variant \
  --source-id sf2.promotion \
  --entity-id item.vigor-ball \
  --observed-asr '契河之域怎么用' \
  --canonical-term '气合之玉怎么用'

python3 scripts/gkp_patch_assistant.py \
  --pack app/src/main/assets/gkp/golden-sun-gba-zh \
  --question '伊凡是不是伊万？' \
  --tag asr_variant \
  --source-id gs.localized_name_audit \
  --entity-id npc.ivan \
  --observed-asr '依凡士不是一晚' \
  --canonical-term '伊凡是不是伊万'

python3 scripts/gkp_patch_assistant.py \
  --pack app/src/main/assets/gkp/final-fantasy-vi-snes-zh \
  --question '魔石系统是什么？' \
  --tag asr_variant \
  --source-id ff6.magicite_wiki \
  --entity-id mechanic.magicite \
  --observed-asr '我时系统是什么' \
  --canonical-term '魔石系统是什么'

python3 scripts/gkp_patch_assistant.py \
  --pack app/src/main/assets/gkp/chrono-trigger-snes-zh \
  --question '玛尔是谁？' \
  --tag asr_variant \
  --source-id ct.project_notes \
  --entity-id npc.marle \
  --observed-asr '麦尔是谁' \
  --canonical-term '玛尔是谁'

python3 scripts/gkp_patch_assistant.py \
  --pack app/src/main/assets/gkp/final-fantasy-vi-snes-zh \
  --question '魔石系统是什么？' \
  --tag asr_variant \
  --source-id ff6.magicite_wiki \
  --entity-id mechanic.magicite \
  --observed-asr '五十系统是什么' \
  --canonical-term '魔石系统是什么'

python3 scripts/gkp_patch_assistant.py \
  --pack app/src/main/assets/gkp/phantasy-star-iv-md-zh \
  --question '技巧和技能有什么区别？' \
  --tag asr_variant \
  --source-id ps4.techniques_wiki \
  --entity-id mechanic.techniques \
  --observed-asr '气巧和技能有什么区别' \
  --canonical-term '技巧和技能有什么区别'
```

## Required Regression After Approval

Only after a human approves the concrete GKP patch:

```bash
JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home" \
./gradlew :app:testDebugUnitTest \
  --tests "com.retrosprite.app.gkp.GkpV0FixtureLintTest" \
  --tests "com.retrosprite.app.gkp.RetroJrpgSrpgPackCoverageTest" \
  --tests "com.retrosprite.app.data.retrieval.RetroJrpgSrpgPackRetrievalGoldenTest"

python3 scripts/rc_release_audit.py

RUN_PLAYBACK=1 CONFIRM_PLAYBACK=1 \
CASE_FILTER=sf2_localized_term,sf2_vigor_ball_observed,golden_sun_ivan_observed,ff6_magicite_observed,chrono_marle_observed,phantasy_star_tech_skill_smoke \
VOICE=Tingting SAY_RATE=96 PRE_SPEAK_SECONDS=3 POST_CASE_SECONDS=10 \
POLL_ATTEMPTS=40 POLL_INTERVAL_SECONDS=2 READY_ATTEMPTS=20 READY_INTERVAL_SECONDS=1 STRICT=1 \
./scripts/hotkey_voice_qa_batch.sh
```
