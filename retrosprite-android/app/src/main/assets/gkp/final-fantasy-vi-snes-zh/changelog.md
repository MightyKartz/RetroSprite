# Changelog

## Unreleased

- Added scoped `observed_asr` aliases from MacBook-speaker Paraformer QA for 魔石系统 and related 魔石/幻兽 phrasing.
- Added `何石系统是什么 -> 魔石系统是什么` from the 2026-05-25 real-device voice retry.
- Added voice-like golden questions covering current-game ASR normalization.

## 0.1.0 - 2026-05-24

- Coverage tier: `lite`.
- Generated from `tools/gkp-builder/templates/gkp-lite/`, then replaced scaffold placeholders with reviewed Final Fantasy VI Chinese first-support data.
- Added source-cited rows for identity, FFIII/FFVI naming, core gameplay, ATB, Magicite, Espers, Relics, character commands, early characters, items, locations, bosses, enemies, and spoiler boundaries.
- Added Chinese localized aliases and ASR-prone variants for title, characters, systems, items, locations, boss/enemy terms, and region naming.
- Added 32 Chinese golden questions covering mechanics, character roles, locations, items, bosses, enemies, FFIII naming, spoiler gates, and no-evidence boundaries.

## Known Gaps

- Does not include complete World of Ruin route guidance.
- Does not include all characters, Espers, Relics, Rage/Lore lists, or full boss strategies.
- Does not cover version-specific translation tables beyond the Lite alias baseline.
- `FFIII` remains a clarification term before gameplay answers.

## Verification

- Intended validation: `audit_gkp_pack.js`, `check_localized_terms.js`, `coverage_report.js --pilot`, and Android JVM GKP lint/coverage tests.
