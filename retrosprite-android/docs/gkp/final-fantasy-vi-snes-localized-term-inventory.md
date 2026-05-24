# Final Fantasy VI SNES Localized Term Inventory

## Accepted Terms

| term | category | target_entity_id | source_refs | confidence | alias_weight | spoiler_gate | action |
| --- | --- | --- | --- | --- | ---: | --- | --- |
| 最终幻想VI | title | note.identity | ff6.localized_name_audit | high | 1.0 | start | alias |
| 最终幻想6 | title | note.identity | ff6.localized_name_audit | high | 1.0 | start | alias |
| 太空战士VI | title | note.identity | ff6.localized_name_audit | high | 0.9 | start | alias |
| 太空战士6 | title | note.identity | ff6.localized_name_audit | high | 0.9 | start | alias |
| FFVI | title | note.identity | ff6.square_enix | verified | 1.0 | start | alias |
| FFIII | title | topic.ffiii-naming | ff6.project_notes | verified | 0.8 | start | clarification |
| 蒂娜 | playable_character | npc.terra | ff6.localized_name_audit | high | 1.0 | start | alias |
| 洛克 | playable_character | npc.locke | ff6.localized_name_audit | high | 1.0 | start | alias |
| 艾德加 | playable_character | npc.edgar | ff6.localized_name_audit | high | 1.0 | start | alias |
| 马修 | playable_character | npc.sabin | ff6.localized_name_audit | high | 1.0 | start | alias |
| 塞丽丝 | playable_character | npc.celes | ff6.localized_name_audit | high | 1.0 | early_game | alias |
| 凯夫卡 | npc | npc.kefka | ff6.localized_name_audit | high | 1.0 | start | alias |
| 魔石 | mechanic | mechanic.magicite | ff6.localized_name_audit | high | 1.0 | early_game | alias |
| 幻兽 | mechanic | mechanic.espers | ff6.localized_name_audit | high | 1.0 | early_game | alias |
| 饰品 | mechanic | mechanic.relics | ff6.localized_name_audit | high | 1.0 | start | alias |
| 特技 | mechanic | mechanic.character-commands | ff6.localized_name_audit | medium | 0.8 | start | alias |
| 凤凰尾 | item | item.phoenix-down | ff6.localized_name_audit | high | 1.0 | start | alias |
| 纳尔谢 | location | location.narshe | ff6.localized_name_audit | high | 1.0 | start | alias |
| 平衡世界 | location | location.world-of-balance | ff6.localized_name_audit | medium | 0.8 | start | concept |
| 崩坏世界 | location | location.world-of-ruin | ff6.localized_name_audit | medium | 0.8 | late_game | heavy spoiler concept |

## Needs Source

| term | category | suspected_entity | reason_needed |
| --- | --- | --- | --- |
| 泰娜 | playable_character | npc.terra | Terra variant needs source. |
| 洛克克 | playable_character | npc.locke | ASR-like variant; don't ship until observed. |
| 加源 | playable_character | npc.cyan | Cyan translation needs source review. |
| 影忍 | playable_character | npc.shadow | Shadow translation needs source review. |
| 奥尔特罗斯 | boss | boss.ultros | Boss transliteration needs source. |
| 贝壳怪 | boss | boss.whelk | Opening boss Chinese name needs source. |

## Rejected Generic Terms

| term | reason |
| --- | --- |
| 角色 | Generic; use named character. |
| 道具 | Generic; use named item/system term. |
| 在哪里 | Generic interrogative; not alias. |
| 怎么打 | Generic strategy phrase; template/golden only. |
| 魔法 | Too broad; prefer Magicite/Espers/magic-learning context. |
| 世界 | Too broad; use World of Balance/Ruin terms only with spoiler policy. |
| 帝国 | Too broad without location/faction context. |
| 幻兽 | Accepted as mechanic.espers, not generic monster alias. |

## ASR Variants

| heard_or_typed | intended_term | target_entity_id | safe_as_alias | test_location |
| --- | --- | --- | --- | --- |
| 最终幻想六 | 最终幻想6 | note.identity | true | aliases.json |
| 最终幻想威 | 最终幻想VI | note.identity | false | normalizer only if observed |
| 太空战士六 | 太空战士6 | note.identity | true | aliases.json |
| 太空战士威 | 太空战士VI | note.identity | false | normalizer only if observed |
| FF3 | FFIII | topic.ffiii-naming | true | aliases.json |
| FF6 | FFVI | note.identity | true | aliases.json |
| 蒂娜啊 | 蒂娜 | npc.terra | false | normalizer only if observed |
| 提娜 | 蒂娜 | npc.terra | true | aliases.json |
| 洛克可 | 洛克 | npc.locke | false | normalizer only if observed |
| 艾德嘎 | 艾德加 | npc.edgar | true | aliases.json |
| 马休 | 马修 | npc.sabin | true | aliases.json |
| 塞莉丝 | 塞丽丝 | npc.celes | true | aliases.json |
| 凯夫咖 | 凯夫卡 | npc.kefka | true | aliases.json |
| 魔十 | 魔石 | mechanic.magicite | false | normalizer only if observed |
| 魔食 | 魔石 | mechanic.magicite | false | normalizer only if observed |
| 幻受 | 幻兽 | mechanic.espers | false | normalizer only if observed |
| 饰平 | 饰品 | mechanic.relics | false | normalizer only if observed |
| 凤凰为 | 凤凰尾 | item.phoenix-down | true | aliases.json |
| 纳尔写 | 纳尔谢 | location.narshe | false | normalizer only if observed |
| 崩坏试件 | 崩坏世界 | location.world-of-ruin | false | reject unless observed and spoiler-safe |

## Ambiguous Terms

| term | possible_targets | resolution |
| --- | --- | --- |
| FFIII | topic.ffiii-naming, another Final Fantasy III game | Always clarify region/version before answering gameplay facts. |
| 魔石 | mechanic.magicite, item.magicite-bucket | Mechanic overview by default; item details only with item context. |
| 幻兽 | mechanic.espers, enemy.monster-bucket | Mechanic unless generic monster context. |
| 崩坏世界 | location.world-of-ruin, generic spoiler phrase | Heavy spoiler; default to warning/clarification. |

## Planned Pure-Chinese Golden Questions

| question | expected_entity_id | note |
| --- | --- | --- |
| 最终幻想6主要玩什么？ | note.core-gameplay | Core gameplay. |
| 太空战士6和最终幻想6是一个吗？ | topic.ffiii-naming | Name mapping. |
| FFIII指的是这个游戏吗？ | topic.ffiii-naming | Clarification. |
| 蒂娜是谁？ | npc.terra | Character. |
| 洛克前期怎么用？ | npc.locke | Character. |
| 艾德加有什么特点？ | npc.edgar | Character. |
| 魔石系统是什么？ | mechanic.magicite | Mechanic. |
| 幻兽和魔石有什么关系？ | mechanic.espers | Mechanic. |
| 饰品要怎么理解？ | mechanic.relics | Mechanic. |
| 纳尔谢开局要注意什么？ | location.narshe | Location. |
| 凯夫卡现在能问多少？ | npc.kefka | Spoiler-safe character. |
| 直接告诉我崩坏世界完整路线。 | no_evidence | Lite boundary. |
