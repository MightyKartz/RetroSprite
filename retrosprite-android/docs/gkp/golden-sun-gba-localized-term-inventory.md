# Golden Sun GBA Localized Term Inventory

## Accepted Terms

| term | category | target_entity_id | source_refs | confidence | alias_weight | spoiler_gate | action |
| --- | --- | --- | --- | --- | ---: | --- | --- |
| 黄金太阳 | title | note.identity | gs.localized_name_audit | high | 1.0 | start | alias |
| Golden Sun | title | note.identity | gs.official_manual | verified | 1.0 | start | alias |
| 伊萨克 | playable_character | npc.isaac | gs.localized_name_audit | high | 1.0 | start | alias |
| 加雷特 | playable_character | npc.garet | gs.localized_name_audit | high | 1.0 | start | alias |
| 伊万 | playable_character | npc.ivan | gs.localized_name_audit | high | 1.0 | early_game | alias |
| 米娅 | playable_character | npc.mia | gs.localized_name_audit | high | 1.0 | early_game | alias |
| 杰娜 | npc | npc.jenna | gs.localized_name_audit | medium | 0.9 | start | alias |
| 菲利克斯 | npc | npc.felix | gs.localized_name_audit | medium | 0.9 | start | alias |
| 克拉登 | npc | npc.kraden | gs.localized_name_audit | medium | 0.9 | start | alias |
| 精神力 | mechanic | mechanic.psynergy | gs.localized_name_audit | high | 1.0 | start | alias |
| Psynergy | mechanic | mechanic.psynergy | gs.official_manual | verified | 1.0 | start | alias |
| 精灵 | mechanic | mechanic.djinn | gs.localized_name_audit | medium | 0.8 | start | alias with context |
| Djinn | mechanic | mechanic.djinn | gs.official_manual | verified | 1.0 | start | alias |
| 召唤 | mechanic | mechanic.summons | gs.official_manual | verified | 0.9 | start | alias |
| 索尔神殿 | location | location.sol-sanctum | gs.localized_name_audit | high | 1.0 | start | alias |
| 水星灯塔 | location | location.mercury-lighthouse | gs.localized_name_audit | high | 1.0 | early_game | alias |
| 金星灯塔 | location | location.venus-lighthouse | gs.localized_name_audit | high | 0.9 | late_game | alias |
| 药草 | item | item.herb | gs.localized_name_audit | medium | 0.9 | start | alias |
| 精神水晶 | item | item.psy-crystal | gs.localized_name_audit | medium | 0.9 | early_game | alias |
| 宝箱怪 | enemy | enemy.mimic | gs.localized_name_audit | medium | 0.8 | early_game | alias |

## Needs Source

| term | category | suspected_entity | reason_needed |
| --- | --- | --- | --- |
| 太阳神殿 | location | location.sol-sanctum | Common translation variant; do not ship until sourced. |
| 萨帝罗斯 | npc | npc.saturos | Transliteration variant needs source. |
| 梅娜迪 | npc | npc.menardi | Transliteration variant needs source. |
| 维尔村 | location | location.vale | Needs localized-name source. |
| 科利玛森林 | location | location.kolima | Needs source and may be a sublocation. |

## Rejected Generic Terms

| term | reason |
| --- | --- |
| 角色 | Generic role word; use named party aliases instead. |
| 道具 | Generic object word; use named items or item buckets. |
| 在哪里 | Generic interrogative; belongs in question patterns, not alias. |
| 怎么打 | Generic strategy phrase; belongs in templates/goldens, not alias. |
| 灯塔 | Too broad across multiple lighthouses. |
| 精灵 | Only accepted when context indicates Djinn mechanic, not any fairy/monster. |
| 法术 | Too broad; prefer Psynergy / 精神力. |
| 地图 | Generic UI/location word. |

## ASR Variants

| heard_or_typed | intended_term | target_entity_id | safe_as_alias | test_location |
| --- | --- | --- | --- | --- |
| 金太阳 | 黄金太阳 | note.identity | true | aliases.json |
| 黄金太郎 | 黄金太阳 | note.identity | false | GameTermNormalizerTest only if observed |
| 黄金三 | 黄金太阳 | note.identity | false | reject unless observed |
| 伊萨克斯 | 伊萨克 | npc.isaac | true | aliases.json |
| 依萨克 | 伊萨克 | npc.isaac | true | aliases.json |
| 加雷特特 | 加雷特 | npc.garet | false | normalizer only if observed |
| 伊凡 | 伊万 | npc.ivan | true | aliases.json |
| 米亚 | 米娅 | npc.mia | true | aliases.json |
| 克拉灯 | 克拉登 | npc.kraden | false | normalizer only if observed |
| 精神利 | 精神力 | mechanic.psynergy | true | aliases.json |
| 精神里 | 精神力 | mechanic.psynergy | true | aliases.json |
| 赛能量 | Psynergy | mechanic.psynergy | false | normalizer only if observed |
| 迪金 | Djinn | mechanic.djinn | true | aliases.json when question context is mechanic |
| 金星经营 | 金星精灵 | item.venus-djinn | false | reject until observed |
| 火星经营 | 火星精灵 | item.mars-djinn | false | reject until observed |
| 水晶灯塔 | 水星灯塔 | location.mercury-lighthouse | false | normalizer only if observed |
| 水星登塔 | 水星灯塔 | location.mercury-lighthouse | true | aliases.json |
| 金星登塔 | 金星灯塔 | location.venus-lighthouse | true | aliases.json |
| 索尔神天 | 索尔神殿 | location.sol-sanctum | false | normalizer only if observed |
| 宝箱改 | 宝箱怪 | enemy.mimic | true | aliases.json |

## Ambiguous Terms

| term | possible_targets | resolution |
| --- | --- | --- |
| 精灵 | mechanic.djinn, elemental Djinn buckets, generic creature | Use only when question mentions Djinn system, summon, class, or element. |
| 灯塔 | location.mercury-lighthouse, location.venus-lighthouse | Ask clarification unless element/color is present. |
| Isaac | npc.isaac, English title/source text | Entity only in character questions. |
| Mercury | item.mercury-djinn, location.mercury-lighthouse | Prefer lighthouse only with location context. |

## Planned Pure-Chinese Golden Questions

| question | expected_entity_id | note |
| --- | --- | --- |
| 黄金太阳主要玩什么？ | note.core-gameplay | Core gameplay. |
| 新手先做什么比较稳？ | strategy.beginner-direction | Beginner direction. |
| 精神力是什么？ | mechanic.psynergy | Mechanic. |
| 精灵系统有什么用？ | mechanic.djinn | Context disambiguates. |
| 伊萨克是什么定位？ | npc.isaac | Character. |
| 加雷特适合怎么用？ | npc.garet | Character. |
| 水星灯塔是什么地方？ | location.mercury-lighthouse | Location. |
| 索尔神殿前期要注意什么？ | location.sol-sanctum | Location. |
| 药草要不要留？ | item.herb | Item. |
| 精神水晶有什么用？ | item.psy-crystal | Item. |
| 宝箱怪要小心吗？ | enemy.mimic | Enemy. |
| 直接列出所有精灵位置。 | no_evidence | Lite boundary. |
