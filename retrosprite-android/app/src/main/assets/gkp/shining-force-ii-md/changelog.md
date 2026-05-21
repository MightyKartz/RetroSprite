# Changelog

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
