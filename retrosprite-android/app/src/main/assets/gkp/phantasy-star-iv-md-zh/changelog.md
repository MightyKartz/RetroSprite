# Changelog

## Unreleased

- Added regression coverage for existing scoped `observed_asr` aliases around 组合技.
- Added voice-like golden questions covering current-game ASR normalization.

## 0.1.1 - 2026-05-24

- Added the runtime wording variant “组合技要不要一开始研究” to the combination-attack template so natural Chinese phrasing resolves locally.

## 0.1.0 - 2026-05-24

- Coverage tier: `lite`.
- Generated from `tools/gkp-builder/templates/gkp-lite/`, then replaced scaffold placeholders with reviewed Phantasy Star IV Chinese first-support data.
- Added source-cited rows for identity, core gameplay, Techniques, Skills, Macros, combination attacks, party members, items, locations, bosses, and enemy buckets.
- Added Chinese localized aliases and ASR-prone variants for title, characters, systems, items, planets, bosses, and enemies.
- Added 30 Chinese golden questions covering mechanics, core gameplay, characters, items, locations, enemies, spoiler gates, and no-evidence boundaries.

## Known Gaps

- Does not include a full walkthrough.
- Does not include all Techniques, Skills, equipment, or combination attacks.
- Does not include complete planet routes or late boss strategies.
- Some transliteration variants remain in planning docs until source-backed.

## Verification

- Intended validation: `audit_gkp_pack.js`, `check_localized_terms.js`, `coverage_report.js --pilot`, and Android JVM GKP lint/coverage tests.
