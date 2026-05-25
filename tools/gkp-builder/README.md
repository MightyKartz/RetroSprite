# RetroSprite GKP Builder

This directory contains developer-side tooling for producing reviewed
RetroSprite Game Knowledge Packs. It is not Android runtime code.

## Tier Vocabulary

The builder currently ships the `gkp-lite` scaffold because Lite is the
first-support contract for new games.

Use these machine-readable `coverage_tier` values:

| Value | Product label | Use |
| --- | --- | --- |
| `lite` | GKP Lite | first reviewed support package |
| `expanded` | GKP Expanded | broader reviewed pack for active users |
| `deep` | GKP Deep | mature detailed pack |

Do not emit `coverage_tier: plus` or `coverage_tier: pro`. Pro is reserved for
the paid product tier, not GKP coverage.

## Create A GKP Pack

```bash
tools/gkp-builder/bin/gkp-builder new \
  --profile lite \
  --game-id golden_sun_gba \
  --pack-id community.golden-sun-gba-zh \
  --game "Golden Sun / 黄金太阳" \
  --platform gba \
  --language zh \
  --out retrosprite-android/app/src/main/assets/gkp/golden-sun-gba-zh
```

`--profile` can be `lite`, `expanded`, or `deep`. The current scaffold keeps the
same file layout for all three profiles; the profile changes the emitted
`coverage_tier` and lets coverage checks pick the matching thresholds.

The generated pack intentionally contains
`__REPLACE_WITH_REVIEWED_GKP_DATA__` placeholders. Those placeholders must be
replaced with reviewed, source-backed, low-spoiler rows before the pack is
bundled or tested as supported content.

## Check GKP Coverage

```bash
tools/gkp-builder/bin/gkp-builder coverage \
  retrosprite-android/app/src/main/assets/gkp/golden-sun-gba-zh
```

The coverage command reads the pack `coverage_tier` and applies the matching
Lite/Expanded/Deep profile by default. Lite row/golden maximums are warnings,
not hard failures; minimums, source refs, localized aliases, goldens, and
placeholder checks still fail. Use `--json` when CI or another script needs
machine-readable metrics, failed checks, and warning checks.

RAG-Anything or other extraction tools may later draft candidate facts into this
workspace, but the Android app should only receive reviewed plain GKP data.
