# RetroSprite UI/UX Optimization Guide

> Status: product/design direction draft
> Created: 2026-05-21
> Basis: project Markdown review, current Android UI code, and `ui-ux-pro-max` design-system pass for a minimal futuristic gaming voice assistant.

## 1. Core UI/UX Verdict

RetroSprite should feel less like "an app players operate" and more like "a game-native voice companion that appears only when called."

The primary experience is:

1. Player is playing in RetroArch.
2. Player presses the RetroArch AI Service hotkey.
3. RetroSprite appears as a small, futuristic voice waveform overlay.
4. Player asks a short question by voice.
5. RetroSprite answers briefly by voice.
6. The overlay disappears and gives the game back.

This should become the product's main interaction loop. The Android app shell should become a low-frequency setup, diagnostics, and knowledge-pack maintenance surface.

## 2. Product Principles

### 2.1 Game First, App Second

The player should not need to enter RetroSprite during normal play.

The app exists for:

- First-run setup.
- Endpoint health.
- Overlay and microphone permission onboarding.
- GKP install/update/disable/delete.
- BYOK LLM configuration for advanced users.
- Diagnostics and debugging.

The app should not become the main Q&A interface for normal players.

### 2.2 No In-App RetroArch Configuration Mutation

RetroArch settings should stay inside RetroArch. RetroSprite can explain recommended values and copy the endpoint URL, but should not write `retroarch.cfg`, mutate hotkeys, or ask for broad storage permissions.

This matches the current project decision that Settings is a pure RetroArch setup helper, not a cfg writer.

### 2.3 Minimal Surface, Expressive Moment

The UI should be extremely minimal overall, but the hotkey voice overlay should feel premium and memorable.

The right product shape is:

- Quiet app shell.
- Strong game-in-overlay identity.
- No decorative complexity outside the overlay.
- No Live2D, mascot, skins, or large visual system until the Q&A loop is reliable.

### 2.4 Local-First Confidence

The UI must constantly reinforce that RetroSprite is local-first and evidence-grounded without forcing the player to read technical details.

Preferred copy patterns:

- "Ready"
- "Listening"
- "Thinking"
- "Answered from local GKP"
- "No reliable evidence yet"

Avoid copy that sounds like a generic chatbot:

- "Ask me anything"
- "AI will figure it out"
- "Powered by LLM"

## 3. Recommended Information Architecture

### 3.1 Player-Facing App Shell

For a release-oriented build, collapse the app around one primary status screen:

- RetroSprite readiness.
- Endpoint running state.
- Current RetroArch URL.
- Overlay permission state.
- Microphone permission state.
- Current game/GKP status, if known.
- One clear repair action when something blocks the game loop.

Keep these as secondary or advanced surfaces:

- Diagnostics.
- Packs.
- LLM/BYOK.
- Developer request logs.
- Debug curl generation.
- Pending hotkey fallback.

### 3.2 Suggested Top-Level Navigation

Short term, current tabs can remain while the product is still in active debugging:

- Home
- Diagnostics
- Packs
- Settings

Release direction, reduce player-facing emphasis:

- Home: "Ready / Needs Setup" dashboard.
- Packs: library management.
- Settings: permissions and advanced options.
- Diagnostics: not a top-level player tab. Keep it reachable from Settings -> Developer Diagnostics and from recovery actions.

Do not make text Q&A the visual center of Home once hotkey voice overlay is stable.

## 4. Game Overlay Design Direction

### 4.1 Overlay Role

The overlay is not a chat window. It is a temporary voice HUD.

It should communicate only:

- RetroSprite heard the hotkey.
- It is listening.
- It is thinking.
- It is speaking.
- It failed or needs a clearer question.

It may show one compact short-answer card while TTS is speaking, but it should not become a chat window. It should never show long answers, expandable source lists, settings, buttons, or menus during gameplay.

### 4.2 Placement

Default placement should remain top-right because the current implementation already uses a non-touchable top-right overlay and this is usually less disruptive than center or bottom placement.

Rules:

- Never cover the central game action.
- Never intercept game input.
- Avoid bottom placement because RetroArch subtitles/OSD often appear there.
- Respect status bar/notch/safe-area offsets.
- Consider an alternate top-left placement only if specific cores or handheld overlays conflict.

### 4.3 Visual Language

Use a restrained retro-futurist HUD style:

- Deep translucent black/ink background.
- Thin neon edge or glow.
- Live waveform bars or radial waveform.
- Cyan/green/blue as active listening colors.
- Amber for caution/no evidence.
- Red only for true failure.
- Optional subtle scanline/noise texture at very low opacity.
- Optional compact answer card with the same border/glow language and a small GKP source chip.

Avoid:

- Full-screen cyberpunk panels.
- Heavy purple/pink gradients.
- Constant glitch effects.
- Decorative particles.
- Large text blocks.
- Any mascot/pet/skin system before the core loop is polished.

### 4.4 Overlay States

Use these states as the canonical UI model:

| State | Meaning | Visual | Audio / Haptic |
| --- | --- | --- | --- |
| `Wake` | Hotkey received | Quick pulse, 150-220ms | Optional tiny haptic |
| `Listening` | ASR is recording | Live waveform reacts to mic amplitude | No TTS |
| `Thinking` | Query is being resolved | Waveform compresses into scanning pulse | No TTS |
| `Speaking` | TTS answer is playing | Waveform follows speech-like motion | TTS active |
| `NoEvidence` | Could not answer reliably | Amber blink + short message | Short spoken fallback |
| `Error` | Permission/runtime failure | Red/amber compact notice | Optional spoken fallback |

The overlay should auto-hide after success or timeout. It should never stay pinned over the game.

### 4.5 Motion Rules

Motion should feel alive but never distracting.

- Use 150-300ms transitions for wake/show/hide.
- Animate only transform, alpha, glow, and waveform geometry.
- Keep the waveform below 60fps-equivalent workload on low-power handhelds.
- Reduce idle animation in `Thinking`.
- Provide a reduced-motion mode that replaces live animation with a simple level meter or static pulse.
- Avoid strong glitch, screen shake, fast flicker, or large geometry changes.

### 4.6 Text in Overlay

Default overlay should be mostly visual. Text is allowed only as a short state label.

Allowed examples:

- "LISTENING"
- "THINKING"
- "SPEAKING"
- "NO EVIDENCE"

When an answer is available, a second compact card may show the same short answer that TTS is speaking. Keep it to 1-2 lines and strip technical/source boilerplate from the main text.

Allowed answer card pattern:

- Short answer: "20级后再转职更稳，先把主力练起来。"
- Source chip: "GKP · sf2.promotion"

Avoid long answers inside the overlay during gameplay. The answer should primarily be TTS. Long answer/source review belongs in the app after play, not over the game.

## 5. Voice Interaction Model

### 5.1 Preferred Voice Loop

Canonical loop:

1. Hotkey wakes RetroSprite.
2. Overlay enters `Listening`.
3. One-shot ASR captures the question.
4. Transcript goes through existing `ResponseGenerator -> QueryPipeline -> GKP/AnswerPolicy`.
5. TTS speaks only the short answer.
6. Overlay closes.

The voice loop must not bypass:

- Current game label.
- GKP matching.
- Evidence gate.
- Low-spoiler policy.
- No-evidence honesty.

### 5.2 Spoiler Control by Natural Follow-Up

Do not make spoiler level a setting players must manage during play.

Support natural follow-up phrases:

- "说得明确一点"
- "别剧透"
- "直接告诉我"
- "只给方向"

The app can keep a default spoiler level in Settings, but the in-game experience should treat spoiler control as conversational.

### 5.3 Error and Recovery UX

For in-game failures, keep recovery brief:

- No speech detected: "我没听清，再按一次热键。"
- No GKP: "这个游戏还没有知识包。"
- No evidence: "我现在没有可靠资料回答这个问题。"
- Permission missing: app setup screen should explain the missing permission, not the overlay.

Do not let a technical error dump into the game overlay.

## 6. App UI Simplification Plan

### 6.1 Home

Change Home from "ask console" to "readiness dashboard."

Primary content:

- RetroSprite status: Ready / Needs setup / Endpoint stopped.
- Current endpoint URL.
- Overlay permission status.
- Microphone permission status.
- Last game detected.
- Current GKP status.
- One primary repair action.

Move down or hide behind advanced mode:

- Manual label input.
- Text question input.
- Pending hotkey question.
- Debug curl.
- Conversation tray.
- Raw LLM latency/token diagnostics.

Release rule:

- When the system is ready, Home should not show the App text Q&A entry by default.
- If the debug console must remain available, place its entry under Settings -> Developer Diagnostics instead of the first Home viewport.
- Home must not duplicate the RetroArch setup checklist. It should show readiness and one repair action; detailed RetroArch setup belongs only in Settings.
- Ready state copy should point the player back to RetroArch, not deeper into the app.

### 6.2 Settings

Settings should become grouped and progressive. The first screen should answer "can the in-game voice HUD appear?" before exposing ports or AI-provider details.

1. Game Overlay
   - Overlay permission.
   - Microphone permission.
   - Test overlay button.
   - Optional reduced-motion toggle.

2. RetroArch Setup
   - Recommended AI Service URL.
   - Copy URL.
   - Minimal step checklist.
   - AI Service Output should be written in Chinese as `旁白模式（Narrator Mode）`.
   - Do not tell players to switch to `Image Mode`; that phrase is protocol/debug language and causes setup confusion.
   - Avoid PC-specific hotkey examples such as `ALT+...` on handheld builds. Say "your bound AI Service hotkey."

3. Endpoint Details
   - Local port.
   - Restart endpoint action.
   - Keep this below RetroArch setup; players should think in terms of "connect RetroArch", not "configure a server."

4. Knowledge Packs
   - Link to Packs screen, not duplicate controls.

5. Advanced AI
   - BYOK provider/API key.
   - Timeout/max token.
   - Test config.

6. Developer Diagnostics
   - Only if advanced mode is enabled.
   - Entry point to Diagnostics logs, request payloads, source filters, and provider failures.

### 6.3 Packs

Packs is important, but it is a maintenance surface.

Prioritize:

- Installed games.
- Enabled/disabled state.
- Trust/provenance.
- Version/update state.
- Whether the current game has a usable pack.

De-emphasize:

- Raw schema detail.
- Long license summaries.
- Dense counts unless in detail view.

### 6.4 Diagnostics

Diagnostics should remain powerful but should not read like a primary player screen.

Use it for:

- Latest RetroArch request.
- Last hotkey voice transcript.
- Pipeline stage.
- Source IDs.
- GKP disabled/no evidence explanation.
- LLM skipped/used/failure.

Keep it behind advanced/debug positioning in release UX.

## 7. Visual Design System

### 7.1 Style

Use: restrained retro-futurism + OLED dark.

The design-system pass suggested retro-futurism for a gaming companion, with dark mode, neon glow, CRT scanline, and monospace/HUD cues. For RetroSprite, apply that recommendation selectively.

Product-level interpretation:

- Minimal app layout.
- Futuristic overlay moment.
- Dark-first, high-contrast surfaces.
- Small neon accents.
- No dense decorative theme.

### 7.2 Color Tokens

Current project tokens are already close:

- Deep purple/ink canvas.
- Neon green as live/active state.
- Amber as warning/secondary highlight.
- Lavender bridge color.
- Warm red for errors.

Keep the palette, but make usage stricter:

- Green: ready, listening, evidence hit, primary action.
- Cyan/blue: live waveform and active audio energy.
- Amber: no evidence, caution, partial setup.
- Red: true error only.
- Purple: background/surface identity, not primary action everywhere.

### 7.3 Typography

Keep sans-serif for readable prose and monospace for HUD/status labels.

Rules:

- Overlay labels should use short uppercase monospace text.
- App body copy should stay readable, not fully terminal-themed.
- App prose should be Chinese-first for the current target user; reserve English labels for HUD/status/system tokens.
- Avoid tiny text under 12sp.
- Avoid heavy letter spacing in body text.
- Keep long diagnostic strings wrapped or tucked into detail views.

### 7.4 Shape and Elevation

The current "cartridge edge/control panel" feel is appropriate.

Refinements:

- Use cards for repeated/detail items, not for every page section.
- Avoid nesting cards inside cards.
- Shared card primitives should default to vertical content flow. Only use `Box` when a component explicitly needs layering.
- Use compact 6-10dp radii for app panels.
- Use pill/rounded capsule only for the overlay waveform container, because it reads as a HUD/audio device.

## 8. Accessibility and Performance Requirements

### 8.1 Accessibility

Must-have:

- Text contrast >= 4.5:1 for app body text.
- Icon-only buttons must have accessibility labels.
- Touch targets >= 44dp.
- Overlay must not block input.
- Reduced-motion mode for overlay animation.
- Error/no-evidence states must not rely on color alone in the app UI.
- TTS should have a stop path when speaking from the app.

### 8.2 Performance

Must-have:

- Overlay animation should not allocate per frame.
- No layout reflow during waveform animation.
- Keep overlay drawing simple enough for Android handhelds.
- Avoid heavy blur or bitmap effects over the game.
- Auto-hide and timeout must be reliable.
- Repeated hotkeys during active sessions should be ignored or coalesced.

## 9. Implementation Roadmap

### Phase A: Document and Align

- Treat this document as the UI/UX source of truth for player-facing changes.
- Update README/NEXT_IMPLEMENTATION_PLAN wording so current docs no longer imply Home/pending question is the main player path.
- Decide which debug controls move behind advanced mode.

### Phase B: Overlay Polish

- Refine current `AndroidHotkeyVoiceOverlayRenderer`.
- Add explicit visual states: Wake, Listening, Thinking, Speaking, NoEvidence, Error.
- Add compact state labels.
- Add reduced-motion behavior.
- Add device validation on RG 476H in portrait/landscape and common RetroArch overlays.

### Phase C: Home Simplification

- Convert Home into readiness dashboard.
- Move text Q&A and pending hotkey into advanced/debug.
- Surface only the next blocking setup action.
- Keep latest game/GKP status visible.

### Phase D: Settings Reorganization

- Put Game Overlay permissions first.
- Keep RetroArch setup helper small and copy-focused.
- Move BYOK and diagnostics to Advanced.
- Add "test overlay" and "test microphone" actions if feasible.

### Phase E: Diagnostics and Packs Polish

- Make Packs scan-friendly for player maintenance.
- Keep Diagnostics dense but clearly developer-oriented.
- Add current-game filters where possible.

## 10. Acceptance Checklist for Future UI Changes

Before considering a UI/UX change complete, verify:

- [ ] Normal gameplay Q&A can happen without opening the RetroSprite app.
- [ ] The game overlay appears quickly after hotkey press.
- [ ] Overlay does not block game input.
- [ ] Overlay hides after success, no evidence, error, or timeout.
- [ ] Voice answer is short and does not read long source/debug text.
- [ ] The app does not require players to configure gameplay behavior outside RetroArch except RetroSprite-specific permissions/GKP/optional BYOK.
- [ ] Home emphasizes readiness, not manual text chat.
- [ ] Settings are grouped by setup need, with advanced controls de-emphasized.
- [ ] Release navigation does not expose Diagnostics as a top-level player tab.
- [ ] Ready-state Home does not expose App text Q&A as a default first-screen action.
- [ ] App prose is Chinese-first while HUD state labels remain short uppercase English.
- [ ] Overlay source chips are bounded, ellipsized, and cannot overflow their HUD card.
- [ ] Shared card components stack normal content vertically by default to prevent accidental overlap.
- [ ] No decorative animation competes with gameplay.
- [ ] Reduced-motion and readable contrast are respected.
- [ ] GKP/evidence/no-evidence states are understandable without technical knowledge.

## 11. 2026-05-21 UI/UX Audit Addendum

The latest `ui-ux-pro-max` pass still supports the same core direction: minimal app shell, dark restrained retro-futurism, and a memorable in-game voice waveform HUD.

Priority refinements:

1. Keep reducing the App shell. Home should become a readiness surface, not a place where players browse debug tools.
2. Move Diagnostics out of top-level navigation for release builds. It remains important, but it is a developer/support surface.
3. Put Settings in player setup order: Game Overlay first, RetroArch connection second, endpoint details third, AI/provider details later.
4. Keep visual intensity concentrated in the in-game overlay. App cards should be quieter and more utilitarian.
5. Preserve strong mobile UX basics: 44dp+ touch targets, 8dp spacing between adjacent controls, clear pressed/loading states, and reduced-motion behavior.
6. Treat overflow as a blocker. Diagnostic tags, source chips, long labels, and answers must wrap, truncate, or move into detail views instead of pushing layouts out of shape.

### 11.1 RG 476H Alignment Findings

Follow-up analysis after RG 476H testing found these concrete UI/UX mismatches:

1. `RETROARCH 接入指引` on Home and `RETROARCH 设置助手` on Settings duplicated the same setup task. Home should not teach setup when Settings already owns setup.
2. The phrase `模式选择 Image` / `Image Mode` is wrong for player-facing UI. It makes the user think RetroSprite is asking them to change a gameplay setting to image mode. Remove it from app UI and setup docs.
3. `AI Service Output` must be shown as `旁白模式（Narrator Mode）` in Chinese UI. Keep the English RetroArch label in parentheses only to help the user match the setting.
4. On RG 476H, "hotkey cannot summon UI" can be caused by at least three layers: RetroArch not posting to the endpoint, overlay permission missing, or microphone permission/ASR failure. The app needs explicit test buttons and last-signal feedback, not only passive readiness text.
5. Player-facing copy should avoid English technical chips such as `Endpoint: ready`, `Overlay: ready`, and `Mic: ready`. Use Chinese status labels; keep raw endpoint/output/pipeline wording for Developer Diagnostics.
6. Handheld copy should not mention PC keyboard defaults such as `ALT+...`. It should say "按你在 RetroArch 中绑定的 AI Service 快捷键".

Resulting product rule:

- Home = game loop readiness only.
- Settings = RetroArch connection, permissions, and tests.
- Diagnostics/App question console = developer support only.

### 11.2 Immediate UI Optimization Tasks

- Remove the full RetroArch setup checklist from Home.
- Rename Settings setup card to `RETROARCH 连接`.
- Show only these player-facing RetroArch values:
  - `AI Service`: `开启`
  - `AI Service URL`: `http://localhost:<port>`
  - `AI Service Output`: `旁白模式（Narrator Mode）`
  - `AI Service 快捷键`: `在 RetroArch 中确认或绑定`
- Add future controls for:
  - Test game overlay.
  - Test microphone.
  - Test latest RetroArch hotkey signal.
  - Explain exactly which layer blocked the in-game HUD.

### 11.3 RG 476H Landscape Home Layout Rule

The RG 476H is a horizontal handheld. A two-column Home layout must not leave the right side blank when App question tools are hidden.

Home should use the two columns as:

- Left: game-loop readiness and the primary repair action.
- Right: hotkey signal diagnostics and the latest RetroArch request.

The right column must answer these questions without opening Diagnostics:

- Did RetroArch send a request after the player pressed the AI Service hotkey?
- Which game label was received?
- Was a screenshot payload received?
- What output mode did RetroArch send?
- Which layer is blocking the in-game RetroSprite UI: endpoint, overlay permission, microphone/ASR, or no hotkey signal?

This is not a developer nicety. It is a player-facing recovery surface. If the player says "the hotkey cannot summon RetroSprite UI," Home should make the answer visible:

- `尚未收到 RetroArch 热键请求`: check RetroArch AI Service enabled, URL, and hotkey binding.
- `热键已收到，波形未授权`: open Android overlay permission.
- `热键已收到，麦克风未授权`: open microphone permission.
- `热键链路可用`: return to RetroArch and speak the question.

On narrow portrait layouts, the same diagnostics card should appear below the readiness card and above lower-priority endpoint details. The endpoint card is secondary; it should not occupy prime space ahead of "why the in-game UI did not appear."

For RG 476H landscape specifically, the first viewport must show the full primary readiness card and the full hotkey diagnosis action row. Do not let a decorative status hero, endpoint details, or debug console push the "open settings / view diagnostics" actions below the bottom navigation. The top app bar already provides page identity; the Home content area should behave like a compact two-panel instrument cluster.

### 11.4 Current RG 476H Finding

During the latest true-device check, RetroSprite received a real RetroArch request:

- `label`: `mega_drive__光明力量2`
- `image_bytes`: non-zero screenshot payload
- `output_mode`: `text`

At the same time, Android reported:

- `SYSTEM_ALERT_WINDOW`: not granted
- `RECORD_AUDIO`: not granted

UX implication:

- The hotkey path is reaching RetroSprite.
- The in-game UI cannot appear because Android overlay permission is missing.
- Voice Q&A cannot start because microphone permission is missing.
- Home must show this as a concrete diagnosis, not as generic setup copy.

### 11.5 Implementation Adjustment from This Audit

The current Home optimization should implement these concrete changes:

- In RG 476H landscape, remove the duplicate large status hero from the left column.
- Align the left `游戏内语音就绪` card and right `热键信号诊断` card as the first visible row.
- Keep endpoint details below the readiness card as secondary support information.
- Move the diagnostic repair actions (`打开设置授权` / `查看诊断日志`) near the top of the hotkey card so they remain visible without scrolling.
- Compress latest-signal metadata into a compact block: time, game label, output mode, screenshot size, running/paused state.
- Continue showing the exact blocker layer: no endpoint, no RetroArch request, no game-overlay permission, or no microphone/ASR.

### 11.6 HUD Quality Bar After Target Mock Review

The target visual direction is the supplied handheld mock: the game remains visually dominant, while RetroSprite appears as a premium, compact voice instrument. The app shell should stay quiet; the overlay should carry the product identity.

Immediate HUD requirements:

- The first hotkey visual state should read as `LISTENING...`, not `READY`. Pressing the hotkey means the player expects RetroSprite to listen immediately.
- The top-right waveform should be large enough to feel intentional on RG 476H landscape, roughly 25-40% of the game viewport width, but still avoid the central action area.
- The waveform card should include `RETROSPRITE`, a short state label, a vector microphone glyph, and live bars. Do not use emoji or decorative mascot art.
- `Listening`, `Thinking`, `Speaking`, `NoEvidence`, and `Error` must have visibly different motion/color behavior, while preserving the same HUD frame.
- Answer text belongs in the bottom-left compact answer card only during speaking or result display. Keep the answer to 1-2 lines and the source chip to one bounded `GKP · source` pill.
- `NoEvidence` and `Error` should use amber/red accents plus explicit short text; color alone is not enough.
- The overlay must remain non-touchable and auto-hide after success, no evidence, error, or timeout.
- The overlay must respect Android animator scale / reduced-motion behavior. If animations are disabled, the waveform should become a stable level display, not disappear.
- Avoid heavy pixel fonts in Chinese text. Monospace HUD labels are fine; app body copy should remain readable Chinese-first Material typography.

RG 476H visual acceptance:

- Hotkey press produces visible feedback within about 300ms after RetroSprite receives the request.
- The top-right waveform does not cover common RPG command menus, central characters, or bottom RetroArch OSD/subtitles.
- The bottom-left answer card does not collide with the bottom system/nav area and never expands into a chat window.
- The HUD hides cleanly; no stale overlay window remains after the voice session.
- A screenshot of the overlay should visually resemble the target mock's hierarchy: top-right voice device, optional bottom-left answer, game first.

### 11.7 App Shell Follow-Up Optimization

Further app-side simplification should happen after the HUD is stable:

- Collapse `本机端点` into a quieter connection-detail section on Home; the first screen should answer only "can I go back to RetroArch and press the hotkey?"
- Add Settings actions for `测试游戏内波形` and `测试麦克风`, so players can validate permissions without switching to RetroArch.
- After returning from Android permission pages, refresh overlay and microphone state automatically. Manual `刷新状态` should remain as a fallback, not the only way to recover.
- Keep App text Q&A and raw request logs behind Developer Diagnostics. Normal players should not see them in the default path.
- Keep the current dark retro palette, but avoid increasing purple/pink intensity in the app shell. Use brighter neon primarily inside the overlay moment.

### 11.8 Style Decision Record

Chosen style: restrained retro-futurist OLED HUD.

This is the best fit for the current product because RetroSprite is not a normal app-first assistant. It is a low-frequency Android setup shell plus a high-signal in-game voice instrument. The App should feel calm and functional; the overlay should carry the brand.

`ui-ux-pro-max` recommended retro-futurism for the gaming companion direction. Apply that recommendation selectively:

- Use retro-futurism strongly in the hotkey HUD: thin neon edge, live waveform, short uppercase state labels, deep translucent ink surface, subtle scanline texture.
- Use minimal single-column / utility dashboard behavior in the App shell: readiness first, one repair action, progressive disclosure for diagnostics and BYOK settings.
- Keep the existing green/cyan/amber OLED palette. Do not drift toward a purple/rose cyberpunk CTA system.
- Avoid Press Start 2P / VT323 as App body fonts. They are poor for Chinese readability. Use readable Material typography in the App and reserve monospace for HUD labels, endpoint URLs, and source chips.
- Avoid heavy glitch, particles, mascot art, full-screen panels, chat-window overlays, or decorative animation that competes with gameplay.

Style scope:

| Surface | Visual Intensity | Rule |
| --- | --- | --- |
| In-game HUD | High | Premium voice waveform, compact answer card, stateful color/motion |
| Home | Low | Readiness dashboard; send player back to RetroArch |
| Settings | Low-medium | Setup checklist, permission tests, advanced controls below |
| Packs | Low | Maintenance/library surface, scan-friendly rows |
| Diagnostics | Dense but contained | Developer/support surface; no player-facing visual priority |

### 11.9 Next Optimization Backlog

Priority order for the next UI/UX pass:

1. Permission lifecycle refresh: after returning from Android overlay or microphone permission screens, Home and Settings must refresh automatically. Manual refresh remains as fallback only.
2. Settings test actions: keep `测试游戏内波形`; add a real `测试麦克风` action that starts/stops one-shot ASR and shows the latest transcript or ASR error.
3. Diagnostics layout containment: request detail dialogs, filters, tags, and long labels must scroll, wrap, or ellipsize instead of pushing the RG 476H landscape layout out of frame.
4. HUD collision avoidance: the top-right waveform should sit below Android privacy indicators and away from common RetroArch top overlays; bottom-left answer card should keep a safe gap from OS/navigation areas.
5. Home endpoint quieting: endpoint details should remain available, but the first viewport should prioritize `游戏内语音就绪` and `热键信号诊断`.
6. Copy consistency: App copy is Chinese-first; keep raw English protocol labels only when they match RetroArch settings (`AI Service Output`, `Narrator Mode`) or developer diagnostics.
7. Reduced-motion verification: when Android animation scale is disabled, the waveform should remain visible as a stable level display.

Additional acceptance items:

- [ ] Returning from Android permission pages updates Home/Settings without restarting the App.
- [ ] Settings can test overlay and microphone independently before opening RetroArch.
- [ ] Diagnostics detail content scrolls on RG 476H landscape.
- [ ] Long diagnostic tags, source IDs, labels, and output modes cannot overflow their cards.
- [ ] HUD top-right card does not collide with Android microphone/privacy indicators.
- [ ] The selected style remains "restrained retro-futurist OLED HUD"; App shell does not become purple/pink cyberpunk or pixel-font themed.

### 11.10 HUD Readability and Home Symmetry Correction

The next optimization pass should prioritize clarity over spectacle. The current direction is right, but several details still work against the product target:

1. Top-right HUD text and microphone glyph are too small on RG 476H. `RETROSPRITE`, the state label, and the microphone icon must remain readable at handheld distance while the game is moving.
2. The large circular glow behind the top-right HUD waveform reads as decoration and competes with the text/icon layer. Remove circular decorative backgrounds from the HUD. Keep only the deep translucent panel, thin edge, live waveform, and a subtle non-circular glow if needed.
3. The Home landscape layout must feel like a balanced two-panel instrument cluster. The first row should place `游戏内语音就绪` and `热键信号诊断` side by side with equal width and similar visual weight. Endpoint details should not sit only under the left card in the first viewport.
4. The App shell should become quieter. Repeated bright tabs, neon rules, and monospace Chinese labels make the setup app feel busier than the actual game overlay. Use neon primarily for live/active state, not as decoration on every panel.
5. Chinese app copy should use readable sans-serif typography. Reserve monospace for endpoint URLs, source chips, raw protocol labels, and HUD English status tokens.
6. Home should not repeat the same concept in both cards. Left card answers: "Can I go back to RetroArch and use voice?" Right card answers: "Where is the hotkey/HUD chain blocked?"

Implementation requirements:

- Increase top-right HUD label/state text size and icon stroke/size.
- Keep the HUD label area visually isolated from scanlines and waveform motion.
- Use the target mock's exact top-right label pattern: `RETROSPRITE` on the left, `Listening...` on the right, and a cyan microphone glyph at the far right.
- Remove circular glow drawing from the top-right HUD, and avoid similar decorative circles in answer cards unless they carry functional meaning.
- In RG 476H landscape, make Home use one vertical scroll surface with a first-row two-column layout. Place endpoint details below the two main cards as a compact connection detail section.
- In the ready state, collapse per-layer diagnostic success rows into one compact "all checks passed" line. Expand endpoint/hotkey/overlay/microphone blockers only when something is wrong.
- Reduce global App card decoration intensity so the App shell supports the HUD instead of competing with it.

### 11.11 Target Mock 1:1 HUD Direction

The supplied `retro sprite.png` is the visual contract for the in-game HUD. The previous HUD was functionally correct but too plain; the right-top HUD must become the product signature.

Top-right HUD target:

- Shape: wide rounded translucent black capsule, roughly 3.4:1 width/height ratio on RG 476H.
- Border: 1px-2px cyan outline with a soft but restrained glow.
- Background: deep black/ink with slight translucency; no decorative circles, no large filled panels behind the waveform.
- Text: left `RETROSPRITE`, right `Listening...`, both readable at handheld distance. Keep English casing from the mock.
- Icon: cyan microphone glyph on the far right, aligned to the `Listening...` baseline and large enough to read.
- Waveform: centered horizontal waveform with bright multi-color vertical bars, tallest near the center, shorter toward the sides.
- Tails: dotted cyan/green and amber/orange tails fading out on both sides of the main waveform.
- Motion: waveform may animate amplitude, but the frame, text, and microphone must stay stable.
- Gameplay priority: do not increase the HUD beyond the mock's visual footprint; it should feel premium, not dominant.

Bottom-left answer card target:

- Same translucent black and cyan edge language.
- Left mini waveform icon, 1-2 lines of Chinese answer text, bounded `GKP · source` chip.
- No circular glow or mascot/decorative object.

Home symmetry target:

- RG 476H first viewport must show two same-weight cards: left readiness, right hotkey diagnosis.
- Both main cards should share minimum content height so the first row reads as a deliberate two-panel dashboard.
- If one side has fewer details in a ready state, use one concise confirmation line rather than leaving the card visually collapsed.

## 12. Key Project References

- `../../RetroSprite_Development_Plan.md`: product positioning, local-first Q&A, low-spoiler principles, original app page plan.
- `NEXT_IMPLEMENTATION_PLAN.md`: latest decision that Hotkey Wake Voice Overlay is the real product path.
- `RETROARCH_SETUP.md`: RetroArch setup remains in RetroArch; RetroSprite only assists.
- `TEST_COVERAGE.md`: current validation coverage for hotkey overlay, ASR/TTS, and UI smoke paths.
- `../app/src/main/kotlin/com/retrosprite/app/ui/overlay/AndroidHotkeyVoiceOverlayRenderer.kt`: current waveform overlay implementation.
- `../app/src/main/kotlin/com/retrosprite/app/ui/theme/Color.kt`: current dark retro palette.
- `../app/src/main/kotlin/com/retrosprite/app/ui/screens/home/HomeScreen.kt`: current Home text/pending question surface to simplify.
- `../app/src/main/kotlin/com/retrosprite/app/ui/screens/settings/SettingsScreen.kt`: current Settings permission/setup/BYOK surface to reorganize.
