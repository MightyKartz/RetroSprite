# Changelog

## 0.2.5 - 2026-05-23

- Added 意志之路/汉化版 name aliases for early and mid-game character questions, including 修伊, 佳佳, 卡森, 吉布, 皮特, 玛琪露达, 盖鲁哈特, and 鲁德.
- Added Chinese translation aliases for promotion and material items such as 战士的荣耀, 天马之翼, 气合之玉, 奥义之书, 银色战车, and 米斯里鲁银.
- Added a low-spoiler Elven Town/精灵森林 mapping and golden Q&A rows so these names resolve through zero-LLM retrieval.
- Added true-device player phrasing for team composition and tactics, including 角色如何搭配, 职业怎么搭配, 怎么才能赢, and 这个游戏玩的话有什么技巧吗.

## 0.2.4 - 2026-05-23

- Added ASR-observed gameplay variants such as “这游戏要怎么玩？” and “这游戏该怎么玩？” to the zero-LLM core gameplay template.
- Improved the in-game NoEvidence answer card capacity so suggested follow-up questions are visible instead of being truncated after two lines.

## 0.2.3 - 2026-05-23

- Added true-device follow-up natural variants such as “这个游戏到底要怎么玩？”, “刚开始应该干嘛？”, “新手前期怎么玩稳？”, “队伍怎么搭配？” and “升级有什么技巧？”.
- Added low-spoiler combat recovery guidance for “打不过敌人怎么办？”.
- Added ASR-friendly Mithril aliases such as “米斯里鲁” and golden rows for Mithril usage/location questions.

## 0.2.2 - 2026-05-22

- Added zero-LLM natural-language templates for “这游戏怎么玩？”, beginner guidance, team-building principles, and leveling/experience questions.
- Added aliases and golden Q&A rows for current-team, beginner, leveling, and route-hint style voice questions.
- Kept answers short, original, source-cited, and low-spoiler by default.

## 0.2.1 - 2026-05-21

- Added a zero-LLM `note.core-gameplay-loop` row for broad player questions such as “这个游戏主要是玩什么？乐趣在哪里？”.
- Added aliases and golden Q&A rows for “好玩在哪？”, “核心玩法是什么？” and “适合什么玩家？”.

## 0.2.0 - 2026-05-21

- Added `sf2-core-002` expansion rows for production facts, early character roles, early consumables, hidden-content overview, secret villages, safe formation advice, weapon buying, and mithril purpose.
- Added source rows for MobyGames, Japanese manual translation, Shining Force Central character/item/secret/weapon/spell pages, and Angelfire item notes; all RetroSprite answer text remains original short summary.
- Added ASR/voice aliases, including the observed true-device misrecognition `接受他几部这个角色` for promotion intent.
- Expanded golden Q&A coverage with production, character, item, hidden-content, spoiler-filter, ASR, and no-evidence cases.

## 0.1.2 - 2026-05-20

- Added true-device RetroArch AI Service label coverage for `mega_drive__光明力量2`.
- Canonical platform matching now treats `mega_drive` as MD/Genesis for this GKP path.

## 0.1.1 - 2026-05-20

- Added true-device RetroArch playlist label coverage for `光明力量2`.
- Kept the content slice unchanged; this release only improves game identity matching for Chinese labels.

## 0.1.0 - 2026-05-20

- Added the first small Shining Force II Mega Drive GKP slice.
- Covers game identity, low-spoiler early direction, battle basics, revive basics, promotion basics, special promotion item usage, and a few promotion item summaries.
- Intentionally does not include a full walkthrough, ROM data, manual scans, or copied guide text.
