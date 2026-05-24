# Real-Game GKP Lite Expansion Template

> Scope: a repeatable production template for expanding a real game's Game
> Knowledge Pack (GKP) without changing `gkp.v0`.
>
> Use this when turning one real game into a broader RetroSprite assistant:
> start with a lightweight, source-backed, low-spoiler GKP Lite baseline, then
> expand only where player questions prove the need.

## 1. Product Goal

Build a real-game GKP Lite slice that lets a player press the RetroArch hotkey,
ask a short voice question, and get a short answer that is:

- specific to the current game, platform, region, and language;
- grounded in local GKP evidence;
- safe under the player's current spoiler level;
- useful in 1 to 3 spoken sentences;
- traceable through `source_refs` and golden Q&A tests.

This template optimizes for first support that is small, reliable, and honest.
A Lite pack does not need to be a complete guide. It must be enough to anchor
identity, language, core play, common early questions, and spoiler-safe
retrieval. Deep coverage can be added later as separate expansion slices.

## 2. Non-Goals

- No ROM data, save data, copyrighted guidebook dumps, script dumps, or copied
  walkthrough text.
- No executable code inside GKP.
- No live web lookup as the default answer path.
- No ungrounded LLM facts.
- No full walkthrough transcript as the first expansion slice.
- No advanced vision/OCR dependency for this slice; questions should work from
  game label plus player voice/text question.
- No requirement that every supported game starts with a complete item list,
  full route, all bosses, all character builds, or all secrets.

## 3. GKP Lite Definition

GKP Lite is the minimum useful support package for a game. It should contain:

| Lane | Required Baseline |
| --- | --- |
| Identity | title, platform/core, region/version note, observed RetroArch labels |
| Aliases | localized names, English names, abbreviations, ASR-prone forms |
| Core loop | what the player mainly does, why it is fun, who it suits |
| Beginner direction | first-hour goals and low-spoiler route hints |
| Core mechanics | basic combat/movement/progression/resource rules |
| Key terms | localized glossary for items, characters, systems, and name mapping |
| Common stuck points | a small set of high-frequency early questions |
| Spoiler gates | coarse progress gates and light/clear/direct answer boundaries |
| Sources | source ids, reliability labels, and no copied guide prose |
| Goldens | natural questions, ASR variants, no-evidence, and spoiler regressions |

Suggested coverage tiers:

| Tier | Meaning | Typical Size |
| --- | --- | --- |
| `lite` | first supported package, focused on common safe questions | 20 to 60 rows, 20 to 40 goldens |
| `expanded` | broader characters/items/routes for active users | 60 to 150 rows, 40 to 100 goldens |
| `deep` | mature pack with many progress gates and detailed optional content | 150+ rows, 100+ goldens |

Mark first support as `lite` unless the pack has already passed expanded/deep
review. Do not imply that a Lite pack is a complete walkthrough.

## 4. Standardized Template And Scaffold

GKP Lite should be generated from a standard scaffold instead of copied from an
existing game folder. The scaffold is the contract that lets human authors,
RAG-assisted tools, and LLM-assisted drafting produce the same pack shape.

Recommended builder template location:

```text
tools/gkp-builder/templates/gkp-lite/
├─ profile.yaml
├─ manifest.template.json
├─ aliases.template.json
├─ spoiler_graph.template.json
├─ qa_goldens.template.jsonl
├─ changelog.template.md
├─ sources/
│  ├─ citations.template.jsonl
│  └─ licenses.template.md
└─ knowledge/
   ├─ production.template.jsonl
   ├─ mechanics.template.jsonl
   ├─ strategies.template.jsonl
   ├─ entities.template.jsonl
   ├─ items.template.jsonl
   └─ locations.template.jsonl
```

The template should be parameterized by:

| Variable | Example | Use |
| --- | --- | --- |
| `game_slug` | `chrono-trigger-snes` | pack folder/id suffix |
| `game_id` | `chrono_trigger_snes` | stable runtime identity |
| `display_title` | `Chrono Trigger` | manifest and user-facing rows |
| `platform` | `snes` | resolver and pack metadata |
| `region` | `us` / `jp` / null | version-specific behavior |
| `language` | `zh` / `en` | localized surface |
| `coverage_tier` | `lite` | user/developer expectation |
| `retroarch_labels` | observed labels | resolver matching |

The generated pack should start with TODO placeholders for every required Lite
lane. Placeholders must fail coverage lint until the author replaces them with
reviewed source-backed rows.

Expected CLI shape:

```bash
gkp-builder new \
  --profile lite \
  --game "Chrono Trigger" \
  --platform snes \
  --language zh \
  --out workspaces/chrono-trigger-snes-zh

gkp-builder lint workspaces/chrono-trigger-snes-zh/out/community.chrono-trigger-snes-zh
gkp-builder test-goldens workspaces/chrono-trigger-snes-zh/out/community.chrono-trigger-snes-zh
```

Minimum scaffolded files must include:

- identity/production starter row;
- core gameplay/fun hook starter row;
- beginner direction starter row;
- key-term/name-mapping starter rows;
- source inventory placeholders;
- coarse spoiler graph;
- at least one no-evidence golden;
- at least one spoiler-downgrade golden;
- at least four core gameplay/fun hook golden templates;
- changelog section with `Coverage tier: lite`.

Validation should run in three layers:

| Layer | Checks |
| --- | --- |
| Shape lint | required files, JSON/JSONL, id format, manifest paths |
| Coverage lint | required lanes, source refs, aliases, goldens, coverage tier |
| Runtime goldens | Android parser/retrieval/AnswerPolicy, including LLM-disabled mode |

## 5. Expansion Slice Definition

Use one "slice" per content expansion. A slice is small enough to review and
test, but broad enough to feel useful in play.

Recommended first real-game slice:

```text
slice_id: real-game-core-001
target_game: <game title>
target_platform: <platform/core family>
languages: zh first, optional en aliases
coverage_tier: lite
knowledge_rows: 20 to 60
golden_questions: 20 to 40
default_spoiler: light
llm_expected_rate: 0% for required natural-question goldens; optional only for
multi-evidence synthesis outside the core checklist
```

For an existing pack, bump `pack_version` by one patch or minor version. For a
new real-game pack, start at `0.1.0`.

## 6. Required Files

The slice must update or create these files:

```text
app/src/main/assets/gkp/<pack-folder>/
├─ manifest.json
├─ knowledge/
│  ├─ entities.jsonl
│  ├─ items.jsonl
│  ├─ locations.jsonl
│  ├─ mechanics.jsonl
│  ├─ quests.jsonl
│  ├─ bosses.jsonl
│  ├─ strategies.jsonl
│  └─ production.jsonl
├─ sources/
│  ├─ citations.jsonl
│  └─ licenses.md
├─ aliases.json
├─ spoiler_graph.json
├─ qa_goldens.jsonl
└─ changelog.md
```

Only include knowledge files that contain rows. `GkpV0Parser` currently accepts
knowledge paths listed in `manifest.json`; do not add files to the folder unless
they are listed in `contents.knowledge`.

## 7. Source Policy

Every factual row must cite at least one stable source id.

Allowed source types:

- official manual or official site;
- publisher/developer page;
- in-game observation written as an original project note;
- community wiki page used as a fact pointer;
- community guide used only for factual verification, not copied prose;
- version-specific community notes when official material is incomplete.

Source rows should describe what the source supports, not copy its text.

```json
{
  "source_id": "<game>.official.manual",
  "title": "<Official manual title>",
  "kind": "manual",
  "url": "<stable URL or null>",
  "license": "<license or source terms summary>",
  "reliability": "verified",
  "notes": "Supports core controls, character names, item names, and non-spoiler system facts."
}
```

Use `project_note` for facts observed by playing the game or by inspecting the
current RetroArch session. The note must be original.

```json
{
  "source_id": "<game>.project.early-route",
  "title": "RetroSprite early-route notes for <game title>",
  "kind": "project_note",
  "url": null,
  "license": "Project-authored notes; no copied guide text.",
  "reliability": "community",
  "notes": "Original low-spoiler route summary created from manual play verification."
}
```

## 8. Spoiler Policy

Map every row to both `progress_gate` and `spoiler_level`.

| Level | Use For | Voice Answer Behavior |
| --- | --- | --- |
| `none` | controls, UI, basic mechanics, game identity, developer/publisher facts | answer directly |
| `light` | early context, vague route hints, role introductions, safe strategy | answer directly under default settings |
| `medium` | explicit item locations, puzzle steps, boss weaknesses, route specifics | answer only under `CLEAR` or higher |
| `heavy` | endings, major twists, late-game reveals, hidden outcomes | answer only under `FULL`; prefer confirmation language |

Progress gates should be game-specific but coarse. A first slice usually needs
5 to 8 gates:

```json
{
  "default_gate": "start",
  "gates": [
    { "gate_id": "start", "label": "Start / opening", "order": 0 },
    { "gate_id": "early_game", "label": "Early game", "order": 10 },
    { "gate_id": "first_major_area", "label": "First major area", "order": 20 },
    { "gate_id": "mid_game", "label": "Mid game", "order": 40 },
    { "gate_id": "late_game", "label": "Late game", "order": 70 },
    { "gate_id": "postgame", "label": "Postgame or optional cleanup", "order": 90 }
  ],
  "edges": [
    { "from": "start", "to": "early_game" },
    { "from": "early_game", "to": "first_major_area" },
    { "from": "first_major_area", "to": "mid_game" },
    { "from": "mid_game", "to": "late_game" },
    { "from": "late_game", "to": "postgame" }
  ]
}
```

If a fact is useful but progress-dependent, keep the short answer vague at
`light` and put explicit directions in a separate `medium` row.

## 9. Coverage Lanes

Each real-game slice should cover these lanes. Do not fill every possible fact;
prioritize questions a player would ask mid-session by voice.

### Lane A: Game Identity And Production

Purpose: answer "what is this game", "who made it", "which version is this".

Minimum rows:

- game identity;
- platform/region identity;
- developer;
- publisher;
- release context;
- version or localization note when relevant.

Example row:

```json
{
  "entity_id": "production.game-identity",
  "entity_type": "note",
  "canonical_name": "<Game title> identity",
  "language": "zh",
  "aliases": ["<Chinese title>", "<English title>", "<common abbreviation>"],
  "description_short": "<Game title> 是 <developer/publisher/platform> 的 <genre>；本知识包覆盖 <platform/region/version> 的低剧透问答。",
  "description_long": "本行只记录非剧透身份和覆盖范围，不承诺完整攻略。",
  "progress_gate": "start",
  "spoiler_level": "none",
  "source_refs": ["<game>.official.overview"],
  "confidence": "verified",
  "answer_templates": [
    {
      "template_id": "template.<game>.identity.zh",
      "language": "zh",
      "question_patterns": ["这是什么游戏", "<Game title> 是什么", "谁开发的"],
      "answer": "这是 <platform> 上的 <genre>《<Game title>》。本知识包先覆盖基础系统、角色/道具说明和低剧透路线。",
      "source_refs": ["<game>.official.overview"],
      "spoiler_level": "none"
    }
  ]
}
```

Golden questions:

- "这是什么游戏？"
- "谁开发的？"
- "我玩的这个版本覆盖吗？"

### Lane A2: Core Gameplay And Fun Hooks

Purpose: answer "what do you actually do in this game", "why is it fun", and
"is this for me" before the player has enough context to ask specific mechanic
questions.

Minimum rows:

- one spoiler-free core loop row;
- one short "fun hook" answer that explains the appeal in player language;
- aliases for broad voice questions such as "主要玩什么", "乐趣点", "好玩在哪", and
  "适合什么玩家";
- explicit source refs to official genre/premise material plus project-authored
  mechanics notes.

Example row:

```json
{
  "entity_id": "note.core-gameplay-loop",
  "entity_type": "note",
  "canonical_name": "核心玩法与乐趣",
  "language": "zh",
  "aliases": ["主要玩什么", "乐趣点", "核心玩法", "好玩在哪", "适合什么玩家"],
  "description_short": "核心是 <genre/core loop>; 玩家主要通过 <2-3 verbs> 推进。",
  "description_long": "用原创语言解释为什么这个循环有趣。保持低剧透，不列后期系统或具体隐藏清单。",
  "progress_gate": "start",
  "spoiler_level": "none",
  "source_refs": ["<game>.official.overview", "<game>.project.mechanics"],
  "confidence": "community",
  "answer_templates": [
    {
      "template_id": "template.<game>.core-gameplay.zh",
      "language": "zh",
      "question_patterns": ["这个游戏主要是玩什么？乐趣在哪里？", "好玩在哪", "核心玩法是什么", "适合什么玩家"],
      "answer": "<1 to 3 sentence answer explaining core loop, appeal, and player fit without spoilers>",
      "source_refs": ["<game>.official.overview", "<game>.project.mechanics"],
      "spoiler_level": "none"
    }
  ]
}
```

Golden questions:

- "这个游戏主要是玩什么？乐趣在哪里？"
- "这游戏怎么玩？"
- "好玩在哪？"
- "核心玩法是什么？"
- "适合什么玩家？"

### Lane A3: Natural Question Coverage Checklist

Every real-game GKP must cover these natural voice/text questions without LLM:

| Category | Required examples | Preferred intent |
| --- | --- | --- |
| Core gameplay | “这游戏怎么玩？” “主要玩什么？” “好玩在哪？” | `game_overview` |
| Beginner guide | “新手怎么玩？” “开局先干什么？” | `beginner_guide` |
| Team build | “哪些角色适合培养？” “谁值得练？” | `team_build` |
| Leveling | “怎么玩经验高？” “怎么练级快？” | `leveling` |
| Route hint | “卡住了下一步去哪？” “不要剧透下一步” | `route_hint` |
| Mechanics | “怎么转职？” “为什么不能复活？” | `mechanic` |
| Item usage | “这个道具干嘛？” “给谁用？” | `usage` |
| Name mapping | “这个汉化名英文叫什么？” | `name_mapping` |

For progress-sensitive questions such as “现在哪些角色适合培养？”, answer with
safe general principles and ask for chapter/current roster unless the GKP has a
trusted `progress_gate`.

### Lane B: Core Mechanics

Purpose: answer how to play and explain systems without spoilers.

Minimum rows:

- movement/combat/basic loop;
- leveling/progression;
- death/retry/save behavior;
- party/equipment/resource management if applicable;
- common status effects or UI terms;
- beginner-safe tactical advice.

Example row:

```json
{
  "entity_id": "mechanic.core-loop",
  "entity_type": "mechanic",
  "canonical_name": "Core gameplay loop",
  "language": "zh",
  "aliases": ["怎么玩", "新手", "战斗", "系统", "基础玩法"],
  "description_short": "基础玩法是 <one-sentence mechanic summary>；先掌握 <safe first principle>。",
  "description_long": "用原创语言解释 2 到 4 个关键机制。避免列出后期系统或隐藏机制，除非本行标为 medium/heavy。",
  "progress_gate": "start",
  "spoiler_level": "none",
  "source_refs": ["<game>.official.manual", "<game>.project.mechanics"],
  "confidence": "verified",
  "answer_templates": [
    {
      "template_id": "template.<game>.core-loop.zh",
      "language": "zh",
      "question_patterns": ["怎么玩", "新手怎么玩", "战斗怎么打", "基础玩法"],
      "answer": "<1 to 3 sentence beginner-safe answer>",
      "source_refs": ["<game>.official.manual"],
      "spoiler_level": "none"
    }
  ]
}
```

Golden questions:

- "新手怎么玩？"
- "这个系统是什么意思？"
- "死了怎么办？"
- "怎么升级/强化？"

### Lane C: Characters And Roles

Purpose: explain characters without revealing twists.

Minimum rows:

- protagonist;
- early companions or core NPCs;
- role summary for recruitable characters;
- spoiler-safe "should I use this character" advice;
- explicit late-game identities only at `heavy`.

Example row:

```json
{
  "entity_id": "npc.<character-id>",
  "entity_type": "npc",
  "canonical_name": "<Character name>",
  "language": "zh",
  "aliases": ["<localized name>", "<English name>", "<nickname>", "<ASR-prone variant>"],
  "description_short": "<Character> 是 <safe role>. 低剧透建议：<one safe tactical or story note>。",
  "description_long": "只说明玩家当前阶段能安全知道的身份、战斗定位或剧情功能。不写反转、离队、结局或隐藏身份。",
  "progress_gate": "start",
  "spoiler_level": "light",
  "source_refs": ["<game>.official.characters", "<game>.community.character-index"],
  "confidence": "community",
  "answer_templates": [
    {
      "template_id": "template.<game>.<character-id>.zh",
      "language": "zh",
      "question_patterns": ["<Character> 是谁", "<localized name> 值得练吗", "<nickname> 怎么用"],
      "answer": "<Character> 是 <safe role>. 先把他/她当作 <gameplay role> 使用，不需要提前知道后续剧情。",
      "source_refs": ["<game>.official.characters"],
      "spoiler_level": "light"
    }
  ]
}
```

Golden questions:

- "<角色名> 是谁？"
- "<角色名> 值得练吗？"
- "<角色名> 怎么用？"

### Lane D: Items, Equipment, And Skills

Purpose: explain item use and avoid accidental irreversible choices.

Minimum rows:

- important early consumables;
- permanent upgrade items;
- special class/route/puzzle items;
- common equipment stats;
- hidden item summaries with spoiler levels split by specificity.

Split item rows into two rows when needed:

- `light`: "what it does" or "do not sell yet";
- `medium`: exact location or step;
- `heavy`: late-game consequence or hidden outcome.

Example row:

```json
{
  "entity_id": "item.<item-id>",
  "entity_type": "item",
  "canonical_name": "<Item name>",
  "language": "zh",
  "aliases": ["<localized item>", "<English item>", "<ASR variant>"],
  "description_short": "<Item> 用于 <safe use>. 如果不确定，先保留。",
  "description_long": "说明用途、适用对象、是否消耗、是否可错过。位置和隐藏步骤放到更高剧透行。",
  "progress_gate": "start",
  "spoiler_level": "light",
  "source_refs": ["<game>.official.manual", "<game>.community.items"],
  "confidence": "community",
  "answer_templates": [
    {
      "template_id": "template.<game>.<item-id>.use.zh",
      "language": "zh",
      "question_patterns": ["<Item> 怎么用", "<Item> 给谁", "<Item> 要不要卖"],
      "answer": "<Item> 主要用于 <safe use>. 第一次见到建议先留着，等你确认目标角色/地点后再用。",
      "source_refs": ["<game>.community.items"],
      "spoiler_level": "light"
    }
  ]
}
```

Golden questions:

- "<道具> 怎么用？"
- "<道具> 给谁？"
- "<道具> 在哪？"
- "<装备> 值得买吗？"

### Lane E: Locations And Low-Spoiler Route Hints

Purpose: answer "where do I go" without turning into a full walkthrough.

Minimum rows:

- opening hub;
- first 3 to 5 route beats;
- common stuck points;
- safe "what to do after battle/event" guidance;
- exact route steps only at `medium`.

Example row:

```json
{
  "entity_id": "quest.early-direction",
  "entity_type": "quest",
  "canonical_name": "Early low-spoiler direction",
  "language": "zh",
  "aliases": ["下一步", "去哪", "卡住", "不要剧透", "低剧透提示"],
  "description_short": "低剧透方向：<safe route hint that names only the current/next broad area>。",
  "description_long": "保持方向感，但不列完整路线、不透露后续事件。若玩家要求直接答案，再由 medium 行给出明确步骤。",
  "progress_gate": "start",
  "spoiler_level": "none",
  "source_refs": ["<game>.project.early-route"],
  "confidence": "community",
  "answer_templates": [
    {
      "template_id": "template.<game>.early-direction.zh",
      "language": "zh",
      "question_patterns": ["下一步去哪", "不要剧透下一步", "我卡住了", "给我低剧透提示"],
      "answer": "低剧透方向：<one broad next action>. 如果你想要明确路线，可以再问“直接告诉我怎么走”。",
      "source_refs": ["<game>.project.early-route"],
      "spoiler_level": "none"
    }
  ]
}
```

Golden questions:

- "下一步去哪？不要剧透。"
- "我卡在这里了。"
- "直接告诉我怎么走。"

### Lane F: Bosses, Enemies, And Encounters

Purpose: give practical advice without spoiling later bosses.

Minimum rows:

- early enemy archetypes;
- first major boss or encounter;
- status/weakness explanation;
- safe positioning or preparation tips;
- explicit boss identity and phase details gated by progress.

Example row:

```json
{
  "entity_id": "boss.<boss-id>",
  "entity_type": "boss",
  "canonical_name": "<Boss name or encounter label>",
  "language": "zh",
  "aliases": ["<boss name>", "<area encounter>", "boss", "打不过"],
  "description_short": "<Boss/encounter> 的安全打法是 <short tactical principle>。",
  "description_long": "说明准备、站位、弱点或资源管理。不要透露后续剧情身份，除非本行是 heavy。",
  "progress_gate": "<relevant_gate>",
  "spoiler_level": "medium",
  "source_refs": ["<game>.community.bosses", "<game>.project.encounters"],
  "confidence": "community",
  "answer_templates": [
    {
      "template_id": "template.<game>.<boss-id>.strategy.zh",
      "language": "zh",
      "question_patterns": ["这个 boss 怎么打", "<Boss> 怎么打", "打不过怎么办"],
      "answer": "<1 to 3 sentence tactical answer>",
      "source_refs": ["<game>.project.encounters"],
      "spoiler_level": "medium"
    }
  ]
}
```

Golden questions:

- "这个 boss 怎么打？"
- "打不过怎么办？"
- "它怕什么？"

### Lane G: Hidden Content And Optional Secrets

Purpose: handle hidden items, optional recruits, secret classes, endings, and
missables safely.

Minimum rows:

- one spoiler-safe overview row;
- separate explicit rows for each hidden item/recruit/route;
- "am I about to miss something" row where useful;
- heavy rows for endings or major optional revelations.

Example overview row:

```json
{
  "entity_id": "strategy.hidden-content-overview",
  "entity_type": "strategy",
  "canonical_name": "Hidden content overview",
  "language": "zh",
  "aliases": ["隐藏物品", "隐藏要素", "会错过吗", "missable", "secret"],
  "description_short": "本作有一些隐藏或可错过内容；默认只提醒你先留意探索和关键道具，不直接列清单。",
  "description_long": "在 light 级别只给保护性建议；medium 才列当前位置附近的明确隐藏内容；heavy 才谈结局或重大后果。",
  "progress_gate": "start",
  "spoiler_level": "light",
  "source_refs": ["<game>.community.secrets"],
  "confidence": "community",
  "answer_templates": [
    {
      "template_id": "template.<game>.hidden-overview.zh",
      "language": "zh",
      "question_patterns": ["有隐藏物品吗", "有什么会错过", "隐藏要素"],
      "answer": "有一些隐藏或可错过内容。低剧透建议是多检查可疑角落、保留特殊道具；如果你愿意剧透当前位置，我可以再给明确清单。",
      "source_refs": ["<game>.community.secrets"],
      "spoiler_level": "light"
    }
  ]
}
```

Golden questions:

- "这里有隐藏物品吗？"
- "我会错过什么吗？"
- "直接告诉我隐藏道具在哪。"

### Lane H: Game-Specific Techniques

Purpose: answer "how do I play well" beyond basic mechanics.

Minimum rows:

- beginner tactic;
- resource management tactic;
- common trap to avoid;
- advanced technique if it does not spoil content;
- speedrun/glitch facts only when clearly marked as optional and sourced.

Example row:

```json
{
  "entity_id": "strategy.<technique-id>",
  "entity_type": "strategy",
  "canonical_name": "<Technique name>",
  "language": "zh",
  "aliases": ["技巧", "高级技巧", "<technique alias>", "怎么打得稳"],
  "description_short": "<Technique> 的核心是 <safe practical principle>。",
  "description_long": "解释何时使用、风险、适合新手还是高级玩家。不要依赖未验证传闻。",
  "progress_gate": "start",
  "spoiler_level": "none",
  "source_refs": ["<game>.project.mechanics", "<game>.community.strategy"],
  "confidence": "community",
  "answer_templates": [
    {
      "template_id": "template.<game>.<technique-id>.zh",
      "language": "zh",
      "question_patterns": ["有什么技巧", "怎么打得稳", "<Technique> 怎么做"],
      "answer": "<1 to 3 sentence technique answer>",
      "source_refs": ["<game>.project.mechanics"],
      "spoiler_level": "none"
    }
  ]
}
```

Golden questions:

- "有什么新手技巧？"
- "怎么打得稳？"
- "这个技巧有什么风险？"

## 10. Alias, Language, And ASR Robustness

Voice questions fail when ASR outputs a near miss. Add aliases for:

- Chinese title variants;
- English title and abbreviations;
- Japanese romanization where common;
- localized item/character names;
- common spoken forms;
- homophones or ASR-prone variants observed during tests.

Do not create a complete duplicate GKP just to support another player language.
Prefer stable language-neutral `game_id` / `entity_id` / `source_id` values plus
localized aliases, templates, and goldens. English support should add English
question surfaces, English names, and an answer-language path, not a second
full walkthrough written from scratch.

`aliases.json` example:

```json
{
  "language": "zh",
  "aliases": [
    { "term": "<spoken title>", "entity_id": "production.game-identity", "weight": 1.0 },
    { "term": "<ASR mistaken character name>", "entity_id": "npc.<character-id>", "weight": 0.8 },
    { "term": "<spoken item name>", "entity_id": "item.<item-id>", "weight": 1.0 },
    { "term": "下一步", "entity_id": "quest.early-direction", "weight": 1.0 },
    { "term": "不要剧透", "entity_id": "quest.early-direction", "weight": 1.0 }
  ]
}
```

For every real-device ASR miss, add one golden question if the intended entity
is important to the player experience.

## 11. LLM Usage In A GKP Lite Slice

GKP Lite must be useful with LLM disabled. Required goldens should pass through
local retrieval, direct templates, or deterministic local summary.

If the player enables LLM, it may improve:

- noisy ASR query rewrite;
- cross-language term mapping;
- multi-evidence synthesis;
- answer translation;
- low-spoiler phrasing;
- clarification questions.

It must not:

- invent game-specific facts without GKP evidence;
- bypass spoiler gates;
- answer exact routes, hidden item locations, boss weaknesses, endings, or
  missables from model memory;
- hide that a response is generic when the current game has no GKP.

Each slice should include at least one no-evidence golden that proves the LLM
path does not bare-answer an unsupported game-specific fact.

## 12. Golden Q&A Matrix

Each slice must include a balanced set of golden questions. The target is not
just parser validity; it is product truth.

| Category | Minimum Count | Required Spoiler Coverage |
| --- | ---: | --- |
| Game identity / production | 3 | `none` |
| Core gameplay / fun hook | 4 | `none` |
| Core mechanics | 5 | `none` or `light` |
| Characters | 5 | `light`, with one unknown/no-evidence case |
| Items/equipment/skills | 5 | mix of `light` and `medium` |
| Route/location hints | 5 | one `none`, one `medium`, one no-evidence |
| Hidden/optional content | 3 | `light` overview plus `medium` explicit |
| Techniques/strategy | 3 | `none` or `light` |
| Spoiler downgrade regressions | 3 | medium/heavy evidence blocked under `LIGHT` |
| ASR variants | 3 | observed spoken/incorrect transcript forms |

Golden row template:

```json
{
  "qa_id": "qa.<game>.<topic>.zh",
  "language": "zh",
  "question": "<natural player question>",
  "game_id": "<game_id>",
  "spoiler_level": "none",
  "progress_gate": "start",
  "expected_entity_ids": ["<entity_id>"],
  "expected_answer_contains": ["<stable phrase 1>", "<stable phrase 2>"],
  "source_refs": ["<source_id>"]
}
```

No-evidence row template:

```json
{
  "qa_id": "qa.<game>.no-evidence.<topic>.zh",
  "language": "zh",
  "question": "<question outside current coverage>",
  "game_id": "<game_id>",
  "spoiler_level": "none",
  "progress_gate": "start",
  "expected_entity_ids": [],
  "expected_answer_contains": ["没有足够证据"],
  "source_refs": []
}
```

Spoiler downgrade row template:

```json
{
  "qa_id": "qa.<game>.spoiler-block.<topic>.zh",
  "language": "zh",
  "question": "<question that has medium/heavy evidence>",
  "game_id": "<game_id>",
  "spoiler_level": "none",
  "progress_gate": "start",
  "expected_entity_ids": [],
  "expected_answer_contains": ["超过当前提示级别"],
  "source_refs": []
}
```

## 13. Manifest Expansion Checklist

When expanding an existing pack:

- bump `pack_version`;
- add new knowledge files to `contents.knowledge`;
- add exact RetroArch labels observed on device;
- add title variants used by playlists;
- keep `rom_identity` null unless hash is verified and region-specific;
- preserve existing external/user-installed pack precedence behavior.

Manifest fragment:

```json
{
  "schema_version": "gkp.v0",
  "pack_id": "community.<game-slug>",
  "pack_version": "0.2.0",
  "default_language": "zh",
  "game": {
    "game_id": "<game_id>",
    "title": "<Display Title>",
    "platform": "<platform>",
    "region": "<region or null>",
    "languages": ["zh", "en"],
    "retroarch_system_ids": ["<system_id>"],
    "retroarch_labels": ["<system_id>__<playlist label>", "<observed exact label>"],
    "rom_identity": {
      "crc32": null,
      "sha1": null
    }
  },
  "trust_level": "community",
  "min_app_version": "0.1.0",
  "generated_at": "<ISO-8601 date>",
  "signature": null,
  "contents": {
    "knowledge": [
      "knowledge/entities.jsonl",
      "knowledge/items.jsonl",
      "knowledge/locations.jsonl",
      "knowledge/mechanics.jsonl",
      "knowledge/quests.jsonl",
      "knowledge/strategies.jsonl",
      "knowledge/production.jsonl"
    ],
    "citations": "sources/citations.jsonl",
    "aliases": "aliases.json",
    "spoiler_graph": "spoiler_graph.json",
    "qa_goldens": "qa_goldens.jsonl"
  }
}
```

## 14. Production Workflow

Use this order for each expansion slice:

1. Confirm RetroArch label on device.
2. Run the GKP Lite scaffold generator.
3. Create or update source inventory.
4. Draft progress gates.
5. Replace identity/production placeholders.
6. Replace core gameplay / fun hook placeholders.
7. Add beginner-safe mechanics and early route rows.
8. Add key glossary/name-mapping rows.
9. Add only the most common character/item rows needed for first support.
10. Add hidden/optional overview rows before exact hidden details.
11. Add explicit medium/heavy rows only after safe overview rows exist.
12. Add aliases, including ASR and English variants.
13. Add golden Q&A rows, including no-evidence and spoiler regressions.
14. Run shape lint and coverage lint.
15. Run targeted retrieval/golden tests.
16. Run `/debug/ask` for top questions.
17. Run one real hotkey voice smoke on device.
18. Update `changelog.md` with coverage tier, known gaps, and test result.

Do not expand rows faster than tests. A small slice with reliable answers is
better than a wide slice that guesses. For first support, stop once the pack is
a reliable Lite package; do not force a deep guide before shipping the learning
loop.

## 15. Acceptance Criteria

A real-game expansion slice is ready when:

- the pack was created from the GKP Lite scaffold or explicitly audited against
  the same template profile;
- the intended `coverage_tier` is documented as `lite`, `expanded`, or `deep`;
- shape lint and coverage lint pass;
- pack preflight/lint passes;
- all knowledge rows have valid `source_refs`;
- every `progress_gate` exists in `spoiler_graph.json`;
- every row has at least 2 useful aliases, or a reason it is intentionally exact;
- at least 20 Lite golden questions pass;
- at least 4 core gameplay / fun hook goldens pass without LLM;
- all spoiler downgrade goldens pass under default `LIGHT`;
- at least 3 no-evidence goldens return uncertainty instead of guesses;
- LLM-disabled mode remains useful for the top supported questions;
- LLM-enabled mode, when tested, uses evidence for game-specific answers and
  preserves source ids;
- `/debug/ask` passes for the top 10 voice-like questions;
- one real-device hotkey voice loop reaches `hotkey_voice:text`;
- Diagnostics shows expected `source_ids`, `pipeline_stage`, and `llm_status`;
- `changelog.md` records coverage and known gaps.

## 16. Review Checklist

Before merging a slice, review these questions:

- Did this pack come from the current GKP Lite template profile?
- Are any scaffold TODO placeholders still present?
- Is this a Lite, expanded, or deep pack, and is that clear to users/developers?
- Does every answer stay within 1 to 3 sentences when spoken?
- Would a first-time player feel helped, not spoiled?
- Are explicit locations separated from safe item-use explanations?
- Are story twists absent from `none` and `light` rows?
- Are production facts separated from gameplay facts?
- Are community facts marked `community` unless verified by official sources?
- Can a player ask with natural voice wording and still hit the right entity?
- Does no-evidence behavior protect trust?
- Would the same row apply to another region/version? If not, is that captured?
- If LLM is enabled, does it compose from evidence rather than become a hidden
  fact source?

## 17. Example First Slice For Shining Force II

The current `shining-force-ii-md` pack already covers identity, early direction,
basic battle, revive, promotion, special promotion items, and a few characters.

The next expansion slice should add:

- a core gameplay / fun hook row for "主要玩什么", "乐趣在哪里", "好玩在哪",
  and "适合什么玩家";
- production facts: developer, publisher, platform, release context;
- more early characters and role-safe advice;
- item-use rows for early consumables/equipment;
- a hidden-content overview row at `light`;
- explicit hidden/special item rows at `medium`;
- one or two early encounter strategy rows;
- ASR aliases for observed misrecognitions around "转职", "博伊", and item names;
- 25 to 40 total golden questions, including spoiler downgrade and no-evidence
  cases.

Suggested slice id:

```text
slice_id: sf2-core-002
pack_folder: app/src/main/assets/gkp/shining-force-ii-md
pack_version: 0.2.1
coverage_tier: lite
primary_goal: make hotkey voice questions about core appeal, characters, items,
  mechanics, hidden content, and production facts feel useful while preserving
  low-spoiler defaults.
```

Suggested top voice questions:

- "这是什么游戏？"
- "这个游戏主要是玩什么？乐趣在哪里？"
- "好玩在哪？"
- "适合什么玩家？"
- "谁开发的？"
- "博伊是谁？"
- "这个角色值得练吗？"
- "什么时候转职？"
- "特殊转职道具怎么用？"
- "这个道具要不要卖？"
- "下一步去哪？不要剧透。"
- "这里有隐藏物品吗？"
- "直接告诉我隐藏道具在哪。"
- "新手战斗怎么站位？"
- "角色倒下怎么办？"
- "这个游戏有没有多结局？"
- "我问的这个会剧透吗？"

## 18. Changelog Entry Template

```markdown
## <pack_version> - <YYYY-MM-DD>

- Coverage tier: <lite | expanded | deep>
- Added <count> knowledge rows across production, mechanics, characters, items,
  locations, hidden content, and strategy.
- Added <count> golden Q&A rows, including <count> spoiler downgrade cases and
  <count> no-evidence cases.
- Added ASR aliases for: <terms>.
- Verified with:
  - JVM retrieval golden tests: <result>
  - `/debug/ask` top questions: <result>
  - real-device hotkey voice smoke: <result>
- Known gaps:
  - <specific missing area that should return no-evidence today>
```
