# Changelog

## Unreleased

- Added scoped `observed_asr` aliases from MacBook-speaker Paraformer QA for 时空之轮, 玛尔, and 时间条战斗.
- Added voice-like golden questions covering current-game ASR normalization.

## 0.1.1 - 2026-05-24

- Added the runtime wording variant “三人技要不要背全” to the Dual/Triple Tech template so low-spoiler player phrasing resolves without requiring an explicit progress gate.

## 0.1.0 - 2026-05-24

- Coverage tier: `lite`.
- Generated from `tools/gkp-builder/templates/gkp-lite/`, then replaced scaffold placeholders with reviewed Chrono Trigger Chinese first-support data.
- Added source-cited rows for identity, core gameplay, ATB, Techs, Dual/Triple Techs, time travel, New Game+, party members, items, eras, locations, bosses, and enemy buckets.
- Added Chinese localized aliases and ASR-prone variants for title, characters, mechanics, items, eras, bosses, and ambiguous terms.
- Added 32 Chinese golden questions covering systems, core gameplay, characters, locations, items, bosses, enemies, spoiler gates, and no-evidence boundaries.

## Known Gaps

- Does not include all endings.
- Does not include a complete era route map.
- Does not include full Tech tables, optional boss routes, or final boss strategy.
- Version-specific Chinese naming differences remain in the localized-name audit.

## Verification

- Intended validation: `audit_gkp_pack.js`, `check_localized_terms.js`, `coverage_report.js --pilot`, and Android JVM GKP lint/coverage tests.
