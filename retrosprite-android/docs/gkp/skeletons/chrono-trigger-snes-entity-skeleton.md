# Chrono Trigger SNES Entity Skeleton

| entity_id | entity_type | canonical_en | canonical_zh | spoiler_gate | spoiler_level | source_refs | notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| note.identity | note | Chrono Trigger | 时空之轮 | start | none | ct.square_enix, ct.project_notes | SNES/SFC first-support identity row. |
| note.core-gameplay | note | Core gameplay loop | 核心玩法 | start | none | ct.square_enix, ct.project_notes | Time travel JRPG, ATB, party techs. |
| strategy.beginner-direction | strategy | First-hour direction | 新手第一小时方向 | start | none | ct.project_notes | Low-spoiler fair/millennial opening guidance. |
| strategy.lite-boundary | strategy | Lite boundary | Lite 包边界 | start | none | ct.project_notes | No-evidence boundary for broad era/ending questions. |
| mechanic.atb | mechanic | Active Time Battle | ATB 战斗 | start | none | ct.community_wiki, ct.project_notes | Combat pace system. |
| mechanic.techs | mechanic | Techs | 技 | start | none | ct.community_wiki, ct.project_notes | Character abilities. |
| mechanic.dual-triple-techs | mechanic | Dual and Triple Techs | 双人技和三人技 | early_game | light | ct.community_wiki, ct.project_notes | Combo tech overview. |
| mechanic.time-travel | mechanic | Time travel | 时间旅行 | start | light | ct.square_enix, ct.project_notes | Core premise; avoid exact route. |
| mechanic.new-game-plus | mechanic | New Game Plus | 继承通关 | postgame_or_spoiler | medium | ct.community_wiki, ct.project_notes | Gated system row. |
| npc.crono | npc | Crono | 克罗诺 | start | none | ct.community_wiki, ct.localized_name_audit | Lead character. |
| npc.marle | npc | Marle | 玛尔 | start | light | ct.community_wiki, ct.localized_name_audit | Early party member. |
| npc.lucca | npc | Lucca | 露卡 | start | none | ct.community_wiki, ct.localized_name_audit | Early party member. |
| npc.frog | npc | Frog | 青蛙 | early_game | light | ct.community_wiki, ct.localized_name_audit | Character name can be generic term. |
| npc.robo | npc | Robo | 罗伯 | early_game | light | ct.community_wiki, ct.localized_name_audit | Party member. |
| npc.ayla | npc | Ayla | 艾拉 | mid_game | medium | ct.community_wiki, ct.localized_name_audit | Later party member. |
| npc.magus | npc | Magus | 魔王 | mid_game | medium | ct.community_wiki, ct.localized_name_audit | Ambiguous/gated character row. |
| item.tonic | item | Tonic | 回复药 | start | none | ct.community_wiki, ct.localized_name_audit | Recovery item; name needs version review. |
| item.mid-tonic | item | Mid Tonic | 中回复药 | start | none | ct.community_wiki, ct.localized_name_audit | Recovery item. |
| item.ether | item | Ether | 以太 | start | none | ct.community_wiki, ct.localized_name_audit | MP recovery. |
| item.shelter | item | Shelter | 帐篷 | start | none | ct.community_wiki, ct.localized_name_audit | Save point recovery item. |
| item.tabs | item | Tabs | 能力胶囊 | early_game | light | ct.community_wiki, ct.localized_name_audit | Permanent stat item bucket. |
| location.truce | location | Truce | 特鲁斯 | start | none | ct.community_wiki, ct.localized_name_audit | Opening town. |
| location.millennial-fair | location | Millennial Fair | 千年祭 | start | none | ct.community_wiki, ct.localized_name_audit | Opening fair. |
| location.guardian-kingdom | location | Guardia Kingdom | 加尔迪亚王国 | start | light | ct.community_wiki, ct.localized_name_audit | Kingdom concept. |
| location.end-of-time | location | End of Time | 时间尽头 | early_game | light | ct.community_wiki, ct.localized_name_audit | Hub concept, spoiler-light. |
| location.2300ad | location | 2300 AD | 未来 | early_game | light | ct.community_wiki, ct.project_notes | Era bucket. |
| location.600ad | location | 600 AD | 中世 | early_game | light | ct.community_wiki, ct.project_notes | Era bucket. |
| location.zeal | location | Zeal | 古代王国 | late_game | medium | ct.community_wiki, ct.localized_name_audit | Later era, gated. |
| boss.yakra | boss | Yakra | 亚克拉 | early_game | light | ct.community_wiki, ct.localized_name_audit | Early boss. |
| boss.dragon-tank | boss | Dragon Tank | 龙战车 | early_game | light | ct.community_wiki, ct.localized_name_audit | Early boss. |
| boss.heckran | boss | Heckran | 赫克兰 | early_game | light | ct.community_wiki, ct.localized_name_audit | Early boss. |
| boss.magus | boss | Magus | 魔王 | mid_game | medium | ct.community_wiki, ct.localized_name_audit | Boss/character ambiguity. |
| boss.lavos | boss | Lavos | 拉沃斯 | late_game | heavy | ct.community_wiki, ct.localized_name_audit | Heavy spoiler/late boss. |
| enemy.robot | enemy | Robots | 机器人 | early_game | light | ct.project_notes | Future-era enemy bucket. |
| enemy.reptite | enemy | Reptites | 恐龙人 | mid_game | medium | ct.community_wiki, ct.localized_name_audit | Later enemy bucket. |

## Future Expansion Backlog

- Complete era route map.
- All endings and New Game Plus effects.
- Optional boss and Black Omen details.
- Version-specific Chinese naming differences.

## Future Language Packs

- `chrono-trigger-snes-en`
- `chrono-trigger-snes-ja`
- `chrono-trigger-snes-ko`

Future packs must reuse the same `entity_id` values.
