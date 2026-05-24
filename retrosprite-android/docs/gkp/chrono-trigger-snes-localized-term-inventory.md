# Chrono Trigger SNES Localized Term Inventory

## Accepted Terms

| term | category | target_entity_id | source_refs | confidence | alias_weight | spoiler_gate | action |
| --- | --- | --- | --- | --- | ---: | --- | --- |
| 时空之轮 | title | note.identity | ct.localized_name_audit | high | 1.0 | start | alias |
| 超时空之钥 | title | note.identity | ct.localized_name_audit | high | 1.0 | start | alias |
| Chrono Trigger | title | note.identity | ct.square_enix | verified | 1.0 | start | alias |
| 克罗诺 | playable_character | npc.crono | ct.localized_name_audit | high | 1.0 | start | alias |
| 玛尔 | playable_character | npc.marle | ct.localized_name_audit | high | 1.0 | start | alias |
| 露卡 | playable_character | npc.lucca | ct.localized_name_audit | high | 1.0 | start | alias |
| 青蛙 | playable_character | npc.frog | ct.localized_name_audit | high | 0.8 | early_game | context alias |
| 罗伯 | playable_character | npc.robo | ct.localized_name_audit | medium | 0.9 | early_game | alias |
| 艾拉 | playable_character | npc.ayla | ct.localized_name_audit | medium | 0.9 | mid_game | alias |
| 魔王 | playable_character | npc.magus | ct.localized_name_audit | medium | 0.8 | mid_game | context alias |
| 技 | mechanic | mechanic.techs | ct.localized_name_audit | high | 0.8 | start | context alias |
| 双人技 | mechanic | mechanic.dual-triple-techs | ct.localized_name_audit | high | 1.0 | early_game | alias |
| 三人技 | mechanic | mechanic.dual-triple-techs | ct.localized_name_audit | high | 1.0 | early_game | alias |
| 时间旅行 | mechanic | mechanic.time-travel | ct.square_enix | verified | 1.0 | start | alias |
| 中世 | location | location.600ad | ct.localized_name_audit | medium | 0.9 | early_game | alias |
| 未来 | location | location.2300ad | ct.localized_name_audit | medium | 0.9 | early_game | alias |
| 时间尽头 | location | location.end-of-time | ct.localized_name_audit | medium | 0.9 | early_game | alias |
| 千年祭 | location | location.millennial-fair | ct.localized_name_audit | high | 1.0 | start | alias |
| 回复药 | item | item.tonic | ct.localized_name_audit | medium | 0.8 | start | alias |
| 能力胶囊 | item | item.tabs | ct.localized_name_audit | medium | 0.8 | early_game | alias |

## Needs Source

| term | category | suspected_entity | reason_needed |
| --- | --- | --- | --- |
| 玛鲁 | playable_character | npc.marle | Variant needs source. |
| 卢卡 | playable_character | npc.lucca | Variant may be common; verify. |
| 机器人 | playable_character | npc.robo | Too generic without source/context. |
| 古代王国 | location | location.zeal | Needs source and spoiler policy. |
| 拉沃斯 | boss | boss.lavos | Heavy-spoiler term; source before alias. |

## Rejected Generic Terms

| term | reason |
| --- | --- |
| 角色 | Generic; use named party member. |
| 道具 | Generic; use named item or item bucket. |
| 在哪里 | Generic interrogative; not alias. |
| 怎么打 | Generic strategy phrase; template/golden only. |
| 时间 | Too broad; use time travel or specific era. |
| 魔王 | Accepted only when character/boss context is clear. |
| 青蛙 | Accepted only when Chrono Trigger label and character context are present. |
| 未来 | Era alias only, not generic future wording. |

## ASR Variants

| heard_or_typed | intended_term | target_entity_id | safe_as_alias | test_location |
| --- | --- | --- | --- | --- |
| 时空之论 | 时空之轮 | note.identity | true | aliases.json |
| 时空之伦 | 时空之轮 | note.identity | true | aliases.json |
| 超时空之药 | 超时空之钥 | note.identity | false | normalizer only if observed |
| 超时空之要 | 超时空之钥 | note.identity | true | aliases.json |
| 克罗呢 | 克罗诺 | npc.crono | false | normalizer only if observed |
| 克罗洛 | 克罗诺 | npc.crono | true | aliases.json |
| 玛儿 | 玛尔 | npc.marle | true | aliases.json |
| 马尔 | 玛尔 | npc.marle | true | aliases.json |
| 露佳 | 露卡 | npc.lucca | true | aliases.json |
| 卢卡 | 露卡 | npc.lucca | false | Needs Source first |
| 青挂 | 青蛙 | npc.frog | false | normalizer only if observed |
| 罗伯特 | 罗伯 | npc.robo | false | normalizer only if observed |
| 艾啦 | 艾拉 | npc.ayla | true | aliases.json |
| 魔网 | 魔王 | npc.magus | false | normalizer only if observed |
| 双人机 | 双人技 | mechanic.dual-triple-techs | true | aliases.json |
| 三人机 | 三人技 | mechanic.dual-triple-techs | true | aliases.json |
| 时间镜头 | 时间尽头 | location.end-of-time | false | normalizer only if observed |
| 千年记 | 千年祭 | location.millennial-fair | false | normalizer only if observed |
| 中是 | 中世 | location.600ad | false | normalizer only if observed |
| 能力交囊 | 能力胶囊 | item.tabs | false | normalizer only if observed |

## Ambiguous Terms

| term | possible_targets | resolution |
| --- | --- | --- |
| 魔王 | npc.magus, boss.magus, generic demon king | Require Chrono context and character/boss intent. |
| 青蛙 | npc.frog, generic animal word | Only route to npc.frog under Chrono label and character question. |
| 未来 | location.2300ad, generic time word | Use era context; otherwise ask clarification. |
| 技 | mechanic.techs, generic skill word | Use only with Chrono combat/system context. |

## Planned Pure-Chinese Golden Questions

| question | expected_entity_id | note |
| --- | --- | --- |
| 时空之轮主要玩什么？ | note.core-gameplay | Core gameplay. |
| 超时空之钥是什么游戏？ | note.identity | Title alias. |
| 克罗诺是谁？ | npc.crono | Character. |
| 玛尔和露卡前期怎么理解？ | npc.marle | Character relation without spoiler. |
| 青蛙是谁？ | npc.frog | Ambiguous character. |
| 魔王现在能问吗？ | npc.magus | Spoiler/clarification. |
| 双人技有什么用？ | mechanic.dual-triple-techs | Mechanic. |
| 时间旅行会不会剧透？ | mechanic.time-travel | Mechanic. |
| 千年祭开局要注意什么？ | location.millennial-fair | Location. |
| 时间尽头是什么地方？ | location.end-of-time | Location. |
| 能力胶囊要不要乱吃？ | item.tabs | Item. |
| 直接告诉我所有结局。 | no_evidence | Lite boundary. |
