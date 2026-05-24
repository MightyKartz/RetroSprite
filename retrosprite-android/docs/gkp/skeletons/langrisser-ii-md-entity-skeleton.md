# Langrisser II MD Entity Skeleton

| entity_id | entity_type | canonical_en | canonical_zh | spoiler_gate | spoiler_level | source_refs | notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| note.identity | note | Langrisser II | 梦幻模拟战 II | start | none | l2.sega_mdmini_zh, l2.project_notes | MD first-support identity row. |
| note.core-gameplay | note | Core gameplay loop | 核心玩法 | start | none | l2.sega_mdmini_zh, l2.project_notes | Commander, mercenary, terrain, route SRPG loop. |
| strategy.beginner-direction | strategy | First-hour direction | 新手第一小时方向 | start | none | l2.project_notes | Scenario 1/early deployment guidance. |
| mechanic.commanders | mechanic | Commanders | 指挥官 | start | none | l2.community_wiki, l2.project_notes | Commander-centric tactical system. |
| mechanic.mercenaries | mechanic | Mercenaries | 佣兵 | start | none | l2.community_wiki, l2.project_notes | Hire and command mercenary units. |
| mechanic.command-range | mechanic | Command range | 指挥范围 | start | none | l2.community_wiki, l2.project_notes | Keep units near commanders. |
| mechanic.class-change | mechanic | Class change | 转职 | start | light | l2.community_wiki, l2.project_notes | Class tree overview only. |
| mechanic.terrain | mechanic | Terrain | 地形 | start | none | l2.community_wiki, l2.project_notes | Movement/defense effects. |
| route.light | strategy | Light route | 光辉线 | mid_game | medium | l2.community_wiki, l2.localized_name_audit | Route concept only; no scenario answer without context. |
| route.empire | strategy | Empire route | 帝国线 | mid_game | medium | l2.community_wiki, l2.localized_name_audit | Route concept only. |
| route.independent | strategy | Independent route | 独立线 | mid_game | medium | l2.community_wiki, l2.localized_name_audit | Route concept only. |
| route.dark | strategy | Dark route | 黑暗线 | mid_game | medium | l2.community_wiki, l2.localized_name_audit | Route concept only. |
| npc.elwin | npc | Elwin | 艾尔文 | start | none | l2.community_wiki, l2.localized_name_audit | Main commander. |
| npc.heine | npc | Hein | 海恩 | start | none | l2.community_wiki, l2.localized_name_audit | Early ally. |
| npc.liana | npc | Liana | 莉亚娜 | start | light | l2.community_wiki, l2.localized_name_audit | Important character; keep route spoilers out. |
| npc.cherie | npc | Cherie | 雪莉 | early_game | light | l2.community_wiki, l2.localized_name_audit | Ally/commander. |
| npc.leon | npc | Leon | 雷昂 | early_game | light | l2.community_wiki, l2.localized_name_audit | Enemy/route-related commander. |
| npc.bernhardt | npc | Bernhardt | 伯恩哈特 | early_game | medium | l2.community_wiki, l2.localized_name_audit | Empire leader; avoid route spoilers. |
| npc.boser | npc | Boser | 波赞鲁 | mid_game | medium | l2.community_wiki, l2.localized_name_audit | Antagonist row. |
| npc.jessica | npc | Jessica | 杰西卡 | early_game | light | l2.community_wiki, l2.localized_name_audit | Recurring sage. |
| item.rune-stone | item | Rune Stone | 符文石 | early_game | light | l2.community_wiki, l2.localized_name_audit | Needs source review for MD behavior. |
| item.class-change-bucket | item | Class-change items | 转职道具 | start | light | l2.community_wiki, l2.project_notes | Bucket until source review. |
| item.weapon-bucket | item | Weapons | 武器 | start | none | l2.project_notes | Equipment bucket. |
| item.armor-bucket | item | Armor | 防具 | start | none | l2.project_notes | Equipment bucket. |
| item.recovery-bucket | item | Recovery items | 回复物品 | start | none | l2.project_notes | Consumable bucket. |
| location.scenario-1 | location | Scenario 1 | 第一关 | start | light | l2.project_notes | Opening map concept. |
| location.baldea | location | Baldea | 巴尔迪亚 | start | light | l2.community_wiki, l2.localized_name_audit | Kingdom/place concept. |
| location.reikguard | location | Reikguard Empire | 雷卡尔特帝国 | early_game | light | l2.community_wiki, l2.localized_name_audit | Empire concept. |
| location.castle-bucket | location | Castles | 城堡 | start | light | l2.project_notes | Location bucket. |
| location.route-branch | location | Route branch | 路线分歧 | mid_game | medium | l2.community_wiki, l2.project_notes | Clarification row, not exact branch guide. |
| boss.leon | boss | Leon | 雷昂 | early_game | light | l2.community_wiki, l2.localized_name_audit | Enemy commander boss row when relevant. |
| boss.egbert | boss | Egbert | 埃格贝尔特 | early_game | light | l2.community_wiki, l2.localized_name_audit | Enemy commander. |
| boss.bernhardt | boss | Bernhardt | 伯恩哈特 | mid_game | medium | l2.community_wiki, l2.localized_name_audit | Major boss/commander, gated. |
| enemy.soldier | enemy | Soldiers | 士兵 | start | none | l2.project_notes | Common enemy bucket. |
| enemy.cavalry | enemy | Cavalry | 骑兵 | start | light | l2.project_notes | Unit-type bucket. |
| enemy.flying | enemy | Flying units | 飞兵 | early_game | light | l2.project_notes | Unit-type bucket. |

## Future Expansion Backlog

- Complete scenario route requirements.
- Full class tree and item list.
- Per-scenario enemy commander tactics.
- Ending and route-specific story outcomes.

## Future Language Packs

- `langrisser-ii-md-en`
- `langrisser-ii-md-ja`
- `langrisser-ii-md-ko`

Future packs must reuse the same `entity_id` values.
