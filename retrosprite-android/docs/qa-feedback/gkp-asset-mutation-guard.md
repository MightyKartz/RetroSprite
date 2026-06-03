# M18 GKP Asset Mutation Guard

- GKP root: `retrosprite-android/app/src/main/assets/gkp`
- Review packet: `retrosprite-android/docs/qa-feedback/gkp-patch-review-packet-20260601-hotkey-voice.md`
- Apply report: `retrosprite-android/docs/qa-feedback/gkp-patch-apply-result-20260601-hotkey-voice.md`
- Guard status: `pass`
- Mode: `approved_patch`
- Dirty GKP assets: 10
- Expected patch assets: 10
- Unexpected dirty assets: 0
- Apply report present: `yes`
- Apply report mode: `apply`
- Apply report assets edited: 4
- GKP assets edited by this guard: no

| Path | Classification |
|---|---|
| `retrosprite-android/app/src/main/assets/gkp/chrono-trigger-snes-zh/aliases.json` | `expected_patch_dirty` |
| `retrosprite-android/app/src/main/assets/gkp/chrono-trigger-snes-zh/qa_goldens.jsonl` | `expected_patch_dirty` |
| `retrosprite-android/app/src/main/assets/gkp/final-fantasy-vi-snes-zh/aliases.json` | `expected_patch_dirty` |
| `retrosprite-android/app/src/main/assets/gkp/final-fantasy-vi-snes-zh/qa_goldens.jsonl` | `expected_patch_dirty` |
| `retrosprite-android/app/src/main/assets/gkp/golden-sun-gba-zh/aliases.json` | `expected_patch_dirty` |
| `retrosprite-android/app/src/main/assets/gkp/golden-sun-gba-zh/qa_goldens.jsonl` | `expected_patch_dirty` |
| `retrosprite-android/app/src/main/assets/gkp/phantasy-star-iv-md-zh/aliases.json` | `expected_patch_dirty` |
| `retrosprite-android/app/src/main/assets/gkp/phantasy-star-iv-md-zh/qa_goldens.jsonl` | `expected_patch_dirty` |
| `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/aliases.json` | `expected_patch_dirty` |
| `retrosprite-android/app/src/main/assets/gkp/shining-force-ii-md/qa_goldens.jsonl` | `expected_patch_dirty` |

## Guard Rule

Bundled GKP assets must stay clean until a human-approved patch apply report exists. After approval, dirty paths must be limited to the exact `aliases.json` and `qa_goldens.jsonl` paths described by the current review packet.
