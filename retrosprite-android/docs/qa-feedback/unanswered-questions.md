# Unanswered And Bad-Answer Question Log

> Purpose: collect real player phrasing that RetroSprite cannot answer, answers incorrectly, answers too vaguely, or answers with the wrong spoiler level. Review this file periodically and convert stable findings into GKP aliases, `answer_templates`, concept tags, matcher tests, or `qa_goldens`.

## How To Use

Add one row per failed or unsatisfying question. Keep the original player wording exactly as spoken or typed, including ASR mistakes if they are visible.

Do not paste screenshots, ROM paths, raw audio, personal data, or long copyrighted guide text here.

Recommended review rhythm:

- During manual testing: append rows quickly.
- After 20-30 rows: cluster similar questions and decide fixes.
- After fixes: move confirmed cases into `qa_goldens` or unit tests, then mark the row status as `converted`.

## Status Values

- `new`: captured, not analyzed yet.
- `triaged`: root cause identified.
- `fixed`: implementation or GKP update exists.
- `converted`: covered by `qa_goldens` or unit tests.
- `wontfix`: intentionally refused or out of product scope.

## Fix Types

- `alias`: add row alias or `aliases.json` entry.
- `template`: add or improve `answer_templates`.
- `concept`: add a phrase to `TemplateConceptExtractor`.
- `classifier`: update `QuestionIntentClassifier`.
- `retrieval`: tune matcher/ranking/filter behavior.
- `gkp_content`: add missing evidence row or source-backed content.
- `policy`: change refusal, clarification, spoiler, or answer policy.
- `asr`: add ASR confusion normalization or voice input handling.
- `ui`: improve app/overlay affordance or display.
- `wontfix`: unsupported game/system/question.

## Capture Log

| Date | Game / Label | Progress / Context | Original Question | Actual Result | Expected Behavior | Suspected Cause | Fix Type | Status | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-05-23 | Shining Force II / `mega_drive__光明力量2` | start | 这个游戏主要是干嘛的？ | Previously returned no evidence during RG476H test. | Answer from `note.core-gameplay-loop` with low-spoiler gameplay overview. | Missing colloquial `干嘛的` overview phrase in classifier/concept matcher. | classifier, concept, retrieval | fixed | Covered by unit tests and re-tested on RG476H after fix. |

## Review Template

When the table reaches 20-30 rows, summarize with this structure:

```text
Review date:
Rows reviewed:

Top clusters:
1.
2.
3.

Recommended changes:
- aliases:
- templates:
- concept tags:
- classifier:
- retrieval/ranking:
- GKP content:
- policy:

Rows to convert into qa_goldens:
-

Rows to keep collecting:
-

Rows marked wontfix:
-
```
