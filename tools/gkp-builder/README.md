# RetroSprite GKP Builder

This directory contains developer-side tooling for producing reviewed
RetroSprite Game Knowledge Packs. It is not Android runtime code.

## Create A GKP Lite Pack

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

The generated pack intentionally contains
`__REPLACE_WITH_REVIEWED_GKP_DATA__` placeholders. Those placeholders must be
replaced with reviewed, source-backed, low-spoiler rows before the pack is
bundled or tested as supported content.

RAG-Anything or other extraction tools may later draft candidate facts into this
workspace, but the Android app should only receive reviewed plain GKP data.
