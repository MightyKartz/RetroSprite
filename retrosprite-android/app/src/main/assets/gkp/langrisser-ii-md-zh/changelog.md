# Changelog

## 0.1.0 - 2026-05-24

- Coverage tier: `lite`.
- Generated from `tools/gkp-builder/templates/gkp-lite/`, then replaced scaffold placeholders with reviewed Langrisser II Chinese first-support data.
- Added source-cited rows for identity, SRPG core gameplay, commanders, mercenaries, command range, class change, terrain, route concepts, characters, items, locations, bosses, and enemy buckets.
- Added Chinese localized aliases and ASR-prone variants for title, commanders, routes, system terms, items, locations, bosses, and enemy unit types.
- Added 32 Chinese golden questions covering systems, commanders, routes, map hints, bosses, enemies, spoiler gates, and no-evidence boundaries.

## Known Gaps

- Does not include a full scenario walkthrough.
- Does not include complete route-entry conditions.
- Does not include full class trees, item tables, or per-scenario enemy formations.
- Some route and item details remain version-sensitive and are intentionally gated.

## Verification

- Intended validation: `audit_gkp_pack.js`, `check_localized_terms.js`, `coverage_report.js --pilot`, and Android JVM GKP lint/coverage tests.
