# Phantasy Star IV MD Localized Term Inventory

## Accepted Terms

| term | category | target_entity_id | source_refs | confidence | alias_weight | spoiler_gate | action |
| --- | --- | --- | --- | --- | ---: | --- | --- |
| 梦幻之星IV | title | note.identity | ps4.localized_name_audit | high | 1.0 | start | alias |
| 梦幻之星4 | title | note.identity | ps4.localized_name_audit | high | 1.0 | start | alias |
| 千年纪的终结 | title | note.identity | ps4.sega_mdmini | verified | 1.0 | start | alias |
| 千年纪 | title | note.identity | ps4.localized_name_audit | medium | 0.9 | start | alias |
| Phantasy Star IV | title | note.identity | ps4.sega_mdmini | verified | 1.0 | start | alias |
| 查兹 | playable_character | npc.chaz | ps4.localized_name_audit | high | 1.0 | start | alias |
| 艾莉丝 | playable_character | npc.alys | ps4.localized_name_audit | high | 1.0 | start | alias |
| 哈恩 | playable_character | npc.hahn | ps4.localized_name_audit | medium | 0.9 | start | alias |
| 鲁恩 | playable_character | npc.rune | ps4.localized_name_audit | high | 1.0 | early_game | alias |
| 瑞卡 | playable_character | npc.rika | ps4.localized_name_audit | high | 1.0 | early_game | alias |
| 弗伦 | playable_character | npc.wren | ps4.localized_name_audit | medium | 0.9 | mid_game | alias |
| 黛米 | playable_character | npc.demi | ps4.localized_name_audit | medium | 0.9 | early_game | alias |
| 莫塔维亚 | location | location.motavia | ps4.localized_name_audit | high | 1.0 | start | alias |
| 德佐利斯 | location | location.dezolis | ps4.localized_name_audit | high | 1.0 | mid_game | alias |
| 技巧 | mechanic | mechanic.techniques | ps4.localized_name_audit | medium | 0.9 | start | alias |
| 技能 | mechanic | mechanic.skills | ps4.localized_name_audit | high | 0.9 | start | alias |
| 宏命令 | mechanic | mechanic.macros | ps4.localized_name_audit | high | 1.0 | start | alias |
| 组合技 | mechanic | mechanic.combo-attacks | ps4.localized_name_audit | medium | 0.9 | early_game | alias |
| 单体药 | item | item.monomate | ps4.localized_name_audit | low | 0.7 | start | alias after source review |
| 逃脱管 | item | item.escapipe | ps4.localized_name_audit | low | 0.7 | early_game | alias after source review |

## Needs Source

| term | category | suspected_entity | reason_needed |
| --- | --- | --- | --- |
| 莱卡 | playable_character | npc.rika | Could be Rika variant; needs source. |
| 赖卡 | playable_character | npc.rika | Transliteration variant. |
| 阿莉丝 | playable_character | npc.alys | Alys variant. |
| 戴米 | playable_character | npc.demi | Demi variant. |
| 吉奥 | boss | boss.zio | Needs source before boss alias. |
| 暗黑之力 | boss | boss.dark-force | Heavy-spoiler term; verify before adding. |

## Rejected Generic Terms

| term | reason |
| --- | --- |
| 角色 | Generic role word; use named party aliases. |
| 道具 | Generic object word; use concrete item names. |
| 在哪里 | Generic interrogative; not an alias. |
| 怎么打 | Generic strategy phrase; not an alias. |
| 星球 | Too broad; use Motavia/Dezolis/Rykros. |
| 技能 | Accepted only as mechanic.skills, not as universal alias for all abilities. |
| 组合 | Too broad unless paired with combat context. |
| 机器人 | Too broad for Wren/Demi without context. |

## ASR Variants

| heard_or_typed | intended_term | target_entity_id | safe_as_alias | test_location |
| --- | --- | --- | --- | --- |
| 梦幻星四 | 梦幻之星4 | note.identity | true | aliases.json |
| 梦幻之星哎威 | 梦幻之星IV | note.identity | false | normalizer only if observed |
| 千年记 | 千年纪 | note.identity | true | aliases.json |
| 千年级 | 千年纪 | note.identity | false | normalizer only if observed |
| 查斯 | 查兹 | npc.chaz | true | aliases.json |
| 茶兹 | 查兹 | npc.chaz | true | aliases.json |
| 爱丽丝 | 艾莉丝 | npc.alys | true | aliases.json |
| 艾丽丝 | 艾莉丝 | npc.alys | true | aliases.json |
| 鲁恩恩 | 鲁恩 | npc.rune | false | normalizer only if observed |
| 瑞佳 | 瑞卡 | npc.rika | true | aliases.json |
| 莱卡 | 瑞卡 | npc.rika | false | Needs Source first |
| 弗伦 | Wren | npc.wren | true | aliases.json after source review |
| 弗兰 | Wren | npc.wren | false | normalizer only if observed |
| 莫塔维亚啊 | 莫塔维亚 | location.motavia | false | normalizer only if observed |
| 摩塔维亚 | 莫塔维亚 | location.motavia | true | aliases.json |
| 德佐里斯 | 德佐利斯 | location.dezolis | true | aliases.json |
| 德索利斯 | 德佐利斯 | location.dezolis | true | aliases.json |
| 宏命令令 | 宏命令 | mechanic.macros | false | normalizer only if observed |
| 红命令 | 宏命令 | mechanic.macros | false | normalizer only if observed |
| 组合击 | 组合技 | mechanic.combo-attacks | true | aliases.json |

## Ambiguous Terms

| term | possible_targets | resolution |
| --- | --- | --- |
| 梦幻之星 | series title, note.identity | Prefer current game only when active label resolves to PS4. |
| 技能 | mechanic.skills, mechanic.techniques | Ask or route by source term if query mentions Technique/Skill. |
| 机器人 | npc.wren, npc.demi, enemy.machine | Ask clarification unless name or party context exists. |
| 星球 | location.motavia, location.dezolis | Ask clarification. |

## Planned Pure-Chinese Golden Questions

| question | expected_entity_id | note |
| --- | --- | --- |
| 梦幻之星4主要玩什么？ | note.core-gameplay | Core gameplay. |
| 千年纪的终结是什么？ | note.identity | Title alias. |
| 查兹是谁？ | npc.chaz | Character. |
| 艾莉丝适合怎么用？ | npc.alys | Character. |
| 鲁恩是谁？ | npc.rune | Character. |
| 莫塔维亚是什么地方？ | location.motavia | Location. |
| 德佐利斯什么时候再问比较好？ | location.dezolis | Spoiler-gated location. |
| 技巧和技能有什么区别？ | mechanic.techniques | Mechanic. |
| 宏命令有什么用？ | mechanic.macros | Mechanic. |
| 组合技要不要一开始研究？ | mechanic.combo-attacks | Mechanic. |
| 逃脱管有什么用？ | item.escapipe | Item. |
| 直接告诉我所有后期星球路线。 | no_evidence | Lite boundary. |
