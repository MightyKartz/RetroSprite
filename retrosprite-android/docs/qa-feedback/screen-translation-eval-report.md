# M18 Screen Translation Eval Report

- Cases: `/Users/kartz/Development/Sprite/retrosprite-android/scripts/screen_translation_eval_cases.tsv`
- Matrix: `/Users/kartz/Development/Sprite/retrosprite-android/docs/qa-feedback/rc-device-matrix.md`
- Total: 5
- Status: pass=0, fail=0, blocked=0, not_run=5, missing=0
- Note issues: 0

| Case | Status | Trigger | Expected Layout | Language | Number Policy | Evidence | Matrix Expected Display | Matrix Result | Result Note | Note Check |
|---|---|---|---|---|---|---|---|---|---|---|
| `ff6_dialogue` | `not_run` | 翻译 | `chinese_only` | `zh` | `no_numbers` | `manual_screenshot` | Chinese-only dialogue, no English source | Not run | - | - |
| `ff6_main_menu` | `not_run` | 翻译 | `bilingual_rows` | `en_zh` | `preserve_numbers` | `manual_screenshot` | Bilingual lookup rows, English source + Chinese translation | Not run | - | - |
| `ff6_status` | `not_run` | 翻译 | `grouped_labels` | `zh` | `preserve_hp_mp_level_exp` | `manual_screenshot` | Labels translated, HP/MP/Level/Exp numbers preserved | Not run | - | - |
| `chrono_equipment` | `not_run` | 翻译 | `bilingual_rows` | `en_zh` | `preserve_numbers` | `manual_screenshot` | Equipment slots and item names grouped, numbers preserved | Not run | - | - |
| `multi_page_any` | `not_run` | 翻译 | `paged_overlay` | `zh_or_en_zh` | `ten_seconds_per_page` | `manual_screenshot` | Every page stays visible for 10 seconds | Not run | - | - |
