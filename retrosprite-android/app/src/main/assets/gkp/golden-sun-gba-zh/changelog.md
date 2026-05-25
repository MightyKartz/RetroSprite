# Changelog

## Unreleased

## 0.1.2 - 2026-05-25

- Added scoped `observed_asr` aliases from MacBook-speaker Paraformer QA for Ivan / 伊万 / 伊凡.
- Added voice-like golden questions covering current-game ASR normalization.
- Added scoped `observed_asr` aliases for the repeated Golden Sun Lite boundary transcript drift around `直接列出所有精灵位置`.

## 0.1.1 - 2026-05-24

- Added a low-spoiler `mechanic.stats-equipment` row for basic stat and equipment questions such as `攻击力高有什么用`.
- Added a golden case to prevent generic stat questions from falling through to unrelated item usage templates.

## 0.1.0 - 2026-05-24

- Coverage tier: `lite`.
- Generated from `tools/gkp-builder/templates/gkp-lite/`, then replaced scaffold placeholders with reviewed Golden Sun Chinese first-support data.
- Added source-cited rows for identity, core gameplay, Psynergy, Djinn, classes, summons, beginner direction, key characters, items, locations, bosses, and enemy buckets.
- Added Chinese localized aliases and ASR-prone variants for title, characters, systems, items, locations, bosses, and enemies.
- Added 34 Chinese golden questions covering mechanics, fun-hook intent, characters, items, locations, bosses, enemies, spoiler gates, and no-evidence boundaries.

## Known Gaps

- Does not include a full walkthrough.
- Does not list every Djinn location or ability.
- Does not include all equipment, all enemies, or full boss statistics.
- Heavy-spoiler final boss details are intentionally gated or out of scope for Lite.

## Verification

- Intended validation: `audit_gkp_pack.js`, `check_localized_terms.js`, `coverage_report.js --pilot`, and Android JVM GKP lint/coverage tests.
