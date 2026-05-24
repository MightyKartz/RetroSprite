# Langrisser II MD Localized Term Inventory

## Accepted Terms

| term | category | target_entity_id | source_refs | confidence | alias_weight | spoiler_gate | action |
| --- | --- | --- | --- | --- | ---: | --- | --- |
| 梦幻模拟战II | title | note.identity | l2.sega_mdmini_zh | verified | 1.0 | start | alias |
| 梦幻模拟战2 | title | note.identity | l2.localized_name_audit | high | 1.0 | start | alias |
| 兰古利萨II | title | note.identity | l2.localized_name_audit | high | 0.9 | start | alias |
| Langrisser II | title | note.identity | l2.sega_mdmini_zh | verified | 1.0 | start | alias |
| 艾尔文 | commander | npc.elwin | l2.localized_name_audit | high | 1.0 | start | alias |
| 海恩 | commander | npc.heine | l2.localized_name_audit | high | 1.0 | start | alias |
| 莉亚娜 | commander | npc.liana | l2.localized_name_audit | high | 1.0 | start | alias |
| 雪莉 | commander | npc.cherie | l2.localized_name_audit | high | 1.0 | early_game | alias |
| 雷昂 | commander | npc.leon | l2.localized_name_audit | high | 1.0 | early_game | alias |
| 伯恩哈特 | commander | npc.bernhardt | l2.localized_name_audit | high | 0.9 | early_game | alias |
| 波赞鲁 | commander | npc.boser | l2.localized_name_audit | high | 0.9 | mid_game | alias |
| 杰西卡 | npc | npc.jessica | l2.localized_name_audit | high | 0.9 | early_game | alias |
| 佣兵 | mechanic | mechanic.mercenaries | l2.project_notes | verified | 1.0 | start | alias |
| 指挥范围 | mechanic | mechanic.command-range | l2.project_notes | verified | 1.0 | start | alias |
| 转职 | mechanic | mechanic.class-change | l2.project_notes | verified | 1.0 | start | alias |
| 地形 | mechanic | mechanic.terrain | l2.project_notes | verified | 0.9 | start | alias |
| 光辉线 | route | route.light | l2.localized_name_audit | medium | 0.8 | mid_game | concept only |
| 帝国线 | route | route.empire | l2.localized_name_audit | medium | 0.8 | mid_game | concept only |
| 独立线 | route | route.independent | l2.localized_name_audit | medium | 0.8 | mid_game | concept only |
| 黑暗线 | route | route.dark | l2.localized_name_audit | medium | 0.8 | mid_game | concept only |

## Needs Source

| term | category | suspected_entity | reason_needed |
| --- | --- | --- | --- |
| 海因 | commander | npc.heine | Transliteration variant for Hein. |
| 切莉 | commander | npc.cherie | Cherie variant. |
| 博赞鲁 | commander | npc.boser | Boser variant. |
| 符文石 | item | item.rune-stone | Verify item behavior in MD version. |
| 雷卡尔特 | location | location.reikguard | Needs source for Chinese form. |

## Rejected Generic Terms

| term | reason |
| --- | --- |
| 角色 | Generic; use commander names. |
| 道具 | Generic; use class-change or named item terms. |
| 在哪里 | Generic interrogative; not alias. |
| 怎么打 | Generic strategy phrase; template only. |
| 路线 | Too broad; accepted route terms are specific. |
| 法师 | Class word; may overmatch Hein/Jessica/class rows. |
| 士兵 | Accepted only as enemy.soldier when enemy context exists. |
| 帝国 | Too broad unless route or faction context exists. |

## ASR Variants

| heard_or_typed | intended_term | target_entity_id | safe_as_alias | test_location |
| --- | --- | --- | --- | --- |
| 梦幻模拟站 | 梦幻模拟战 | note.identity | true | aliases.json |
| 梦幻模拟战二 | 梦幻模拟战II | note.identity | true | aliases.json |
| 兰古丽莎 | 兰古利萨 | note.identity | true | aliases.json |
| 兰格丽萨 | 兰古利萨 | note.identity | true | aliases.json |
| 艾文 | 艾尔文 | npc.elwin | true | aliases.json |
| 艾尔温 | 艾尔文 | npc.elwin | true | aliases.json |
| 海因 | 海恩 | npc.heine | false | Needs Source first |
| 莉安娜 | 莉亚娜 | npc.liana | true | aliases.json |
| 雪利 | 雪莉 | npc.cherie | true | aliases.json |
| 雷奥 | 雷昂 | npc.leon | false | normalizer only if observed |
| 雷昂线 | 帝国线 | route.empire | false | reject without context |
| 伯恩哈德 | 伯恩哈特 | npc.bernhardt | true | aliases.json |
| 波赞路 | 波赞鲁 | npc.boser | true | aliases.json |
| 指挥范伟 | 指挥范围 | mechanic.command-range | true | aliases.json |
| 指挥饭喂 | 指挥范围 | mechanic.command-range | false | normalizer only if observed |
| 佣兵兵 | 佣兵 | mechanic.mercenaries | false | normalizer only if observed |
| 转只 | 转职 | mechanic.class-change | true | aliases.json |
| 地刑 | 地形 | mechanic.terrain | false | normalizer only if observed |
| 光灰线 | 光辉线 | route.light | true | aliases.json |
| 独立县 | 独立线 | route.independent | false | normalizer only if observed |

## Ambiguous Terms

| term | possible_targets | resolution |
| --- | --- | --- |
| 光辉线 | route.light, generic route discussion | Route concept only; ask progress before scenario advice. |
| 帝国线 | route.empire, Empire faction | Require route context. |
| 雷昂 | npc.leon, boss.leon | Character row by default; boss row only in battle context. |
| 转职 | mechanic.class-change, item.class-change-bucket | Mechanic unless named item is present. |

## Planned Pure-Chinese Golden Questions

| question | expected_entity_id | note |
| --- | --- | --- |
| 梦幻模拟战2主要玩什么？ | note.core-gameplay | Core gameplay. |
| 新手第一关怎么稳？ | strategy.beginner-direction | Beginner direction. |
| 艾尔文是什么定位？ | npc.elwin | Commander. |
| 海恩适合怎么用？ | npc.heine | Commander. |
| 莉亚娜要保护吗？ | npc.liana | Commander. |
| 佣兵系统怎么理解？ | mechanic.mercenaries | Mechanic. |
| 指挥范围有什么用？ | mechanic.command-range | Mechanic. |
| 转职什么时候考虑？ | mechanic.class-change | Mechanic. |
| 地形影响大吗？ | mechanic.terrain | Mechanic. |
| 光辉线现在能问吗？ | route.light | Clarification/spoiler. |
| 帝国线会剧透吗？ | route.empire | Clarification/spoiler. |
| 直接告诉我所有路线条件。 | no_evidence | Lite boundary. |
