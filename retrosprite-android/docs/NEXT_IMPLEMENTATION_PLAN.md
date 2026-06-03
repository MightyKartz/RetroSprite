# RetroSprite 下一阶段实施计划

> 生成日期：2026-05-19
> 最近更新：2026-06-01
> 依据：代码现状、`docs/DELIVERY_REPORT.md`、Android AVD/真机实测、RetroArch v1.22.2 源码/官方 APK 行为、DeepSeek 官方 API 文档、`docs/GKP_LITE_OPTIONAL_LLM_DIRECTION.md`，以及 2026-06-01 对 RetroArch AI Service、ZTranslate/LunaTranslator/Game2Text、RAG 评测和 ScreenAI/UI 理解路线的外部调研。

> 文档口径提示：本文保留了从 Phase 0 到当前阶段的历史实施记录，前半部分可能仍提到
> 已移除的 sample 包或早期状态。当前架构、GKP Lite、expanded/deep 覆盖、Pro 商业层
> 与可选 LLM 的统一定义见 `docs/ARCHITECTURE_AND_PRODUCT_TIERS.md`；当前已验证
> 范围以 `docs/TEST_COVERAGE.md` 和 README 的 supported-games 列表为准。

## 2026-06-01 当前执行口径：M17 Release Candidate Hardening

当前项目已经从功能原型进入 release-candidate 收口阶段。主链路
`RetroArch hotkey -> overlay -> local Paraformer ASR -> GKP retrieval -> AnswerPolicy -> TTS/overlay`
已经可运行；显式语音命令触发的 BYOK 当前画面翻译也已接入。下一步不再优先扩功能面，而是冻结主能力，围绕真实设备稳定性、翻译展示质量、GKP 命中率、诊断可解释性和发布资料做硬化。

当前代码事实：

- `./gradlew :app:testDebugUnitTest :app:assembleDebug` 在 2026-06-01 通过。
- `app/src/main/kotlin` 约 135 个 Kotlin 文件，`app/src/test/kotlin` 约 68 个 JVM test 文件，`app/src/androidTest/kotlin` 约 10 个 instrumented test 文件。
- 内置真实 GKP 为 6 个，约 347 条 knowledge row、337 条 golden；Shining Force II 为 expanded，其余为 Lite。
- Debug APK 约 251 MB，其中本地 Paraformer ASR assets 约 226 MB；发布前必须把 APK 体积作为明确产品取舍记录。
- 当前工作区存在未提交实现改动时，必须先判断这些改动属于正式能力、QA 注入能力还是临时调试能力；未归类的代码不能进入 RC。

M17 的目标不是“再做更多”，而是证明当前能力可以被玩家反复使用：

1. **功能冻结**：不新增模型、不新增 GKP 大包、不做 Live2D/宠物/自动操作等非主链路能力。
2. **真实设备验收**：RG476H 或等价 Android 设备上完成安装、启动、endpoint、hotkey voice、screen translation、Settings、Diagnostics 验收。
3. **翻译质量矩阵**：覆盖对白、菜单、状态、装备、物品、英文泄漏、数字误翻、分页停留；单页和多页结果每页停留 10 秒。
4. **GKP/版权边界**：继续只内置原创短摘要、别名、术语和来源引用；不内置 ROM、商业攻略正文、完整脚本、网友完整汉化文本或汉化补丁文本。
5. **发布资料同步**：README、测试覆盖、设置页文案、release checklist 必须与代码实际一致。

执行计划已拆到独立文档：

- `docs/superpowers/plans/2026-06-01-release-candidate-hardening.md`

M17 完成标准：

- JVM 单测和 Debug assemble 通过。
- 至少一次 `scripts/android_avd_smoke.sh` 真机/模拟器 smoke 通过。
- 6 个内置 GKP 的 `/debug/ask` matrix 全部达到期望 source/stage/LLM 状态。
- hotkey voice QA matrix 至少覆盖 6 个游戏各 1 条核心问题和 1 条 no-evidence/边界问题。
- screen translation QA matrix 至少覆盖 FF6/Chrono Trigger 类菜单、状态、装备和对白画面。
- Diagnostics 能解释每次失败属于 ASR、GKP miss、API 翻译、无截图、无 key、超时还是权限问题。
- 文档中没有把历史 sample 包、DeepSeek-OCR、ML Kit 或已移除路线描述成当前默认路径。

### 2026-06-01 立即下一步：M17.1 Hotkey Voice Lifecycle Recovery

RG476H `RG476H01077813` 已通过 device endpoint/GKP smoke，但 2026-06-01 14:49 CST 的真实 playback 矩阵 7 条全部失败在语音提交前：overlay 进入 `listening` / `mic_live=true`，随后以 `finish_reason=muted_recovery`、`asr_commit_reason=blank_partial`、`asr_endpoint_armed=false` 结束，且 `/debug/latest-request` 没有新记录。证据见 `build/hotkey-voice-qa/20260601-144348/` 和 `docs/qa-feedback/hotkey-voice-lifecycle-failure-20260601.md`。

因此下一步不是扩游戏、扩模型或补 GKP 内容，而是先恢复热键语音生命周期：

已完成的代码侧诊断改动：

1. overlay debug snapshot 已增加 `AudioRecord` read count、sample count、peak amplitude、last frame amplitude 和 read error count。
2. `scripts/hotkey_voice_qa_batch.sh` 已改为在新 APK 暴露 `asr_audio_read_count` 时，等到 ASR 读到首批 samples 后再播放，避免只凭 `mic_live=true` 过早开播。
3. GKP backlog 已能记录 capture counters；旧 evidence 因当时 APK 没有这些字段，所以仍只显示 `muted_recovery`、`blank_partial` 和 stale latest-request。

当前状态：

1. 已安装包含诊断字段的新 Debug APK 到 RG476H，并确认 endpoint/GKP smoke 通过。
2. 已跑单条 `golden_sun_ivan_observed` recovery probe：Mac output volume 13 时音频峰值只有 `0.0060507967` 并失败；Mac output volume 90 时提交 fresh `hotkey_voice` request 并命中 `gs.localized_name_audit`。
3. 最新 7 条 playback matrix 已全部提交 fresh `hotkey_voice` request，5/7 通过；剩余两条分别是 `sf2_vigor_ball_observed` 的 `source_mismatch` 和 `chrono_marle_observed` 的 `asr_variant`。证据见 `docs/qa-feedback/hotkey-voice-matrix-report.md` 和 `build/hotkey-voice-qa/20260602-083111/results.tsv`。

当前真正的下一步：

1. 以 `docs/qa-feedback/m18-next-action-queue.md` 的 `replay-full-voice-matrix` 为唯一设备前线；重复失败必须回流到 backlog/patch proposal，而不是开新功能。
2. 对 `source_mismatch` 优先检查当前 Shining Force II GKP 的 alias/entity/source 排序；对 `asr_variant` 优先补当前 Chrono Trigger GKP 的 observed-ASR alias 和 golden。
3. 只有用户明确批准 exact patch review packet 后，才修改 `app/src/main/assets/gkp/*/aliases.json` 或 `qa_goldens.jsonl`。
4. 每个已批准 patch 必须跑 `RUN_REPORTS=1 ./scripts/gkp_patch_regression_gate.sh`；安装 patched APK 后再跑 7 条 matrix。
5. 只有 7 条 matrix 全绿并且 `EXPECT_ALL_PASS=1 ./scripts/m18_offline_quality_gate.sh` 通过后，才能关闭 M17.1/M18 当前质量门。

执行计划：

- `docs/superpowers/plans/2026-06-01-m17-hotkey-voice-lifecycle-recovery.md`

## 2026-06-02 当前执行口径：M18 Eval Lab + GKP Quality Loop

M18 是 M17 之后的质量工程阶段，不是功能扩张阶段。它的目标是把“用户问了但没有相关答案”“ASR 听错游戏术语”“GKP Lite 覆盖不到自然问法”这些真实问题，转成可复现、可修复、可回归的工程闭环。

执行计划：

- `docs/superpowers/plans/2026-06-01-m18-eval-lab-gkp-quality-loop.md`

M18 当前边界：

- 保持默认路线不变：RetroArch AI Service、热键触发、本地 Paraformer ASR、本地 GKP/AnswerPolicy、可选 BYOK screen translation/LLM。
- 不新增默认云服务、不新增游戏数量、不内置完整脚本/网友汉化/商业攻略正文，也不引入 Accessibility、MediaProjection 或 AI 自动操作游戏。
- M18 不再包含人工 ASR 审批、5 个屏幕翻译手工矩阵、内容版权人工确认；这些可以保留为 release/QA 事项，但不属于 M18 aggregate status、next-action queue 或 offline gate。

M18 推荐工作流：

1. **GKP 覆盖评测**：按 identity、core gameplay、first hour、mechanics、menu terms、items/skills/magic、names/aliases、common blockers、low-spoiler next step、no-evidence boundary 和 observed ASR variants 生成每包覆盖报告。
2. **No-evidence backlog**：把 `/debug/latest-request`、真机 evidence、玩家反馈和 QA 失败记录整理成 `coverage_gap`、`alias_gap`、`ranking_gap`、`asr_variant`、`spoiler_gate_gap`、`translation_gap`。
3. **安全 GKP patch proposal**：工具只生成建议和 dry-run，不默认写入；每次补洞必须有 source id、golden regression、lint、retrieval golden 和 `rc_release_audit.py`。
4. **质量报告**：每轮 M18 产出 `docs/qa-feedback/m18-eval-report.md`、`docs/qa-feedback/gkp-quality-backlog.md`、`docs/qa-feedback/m18-status-report.md` 和 machine-readable handoff，让下次开发从失败样本开始。

当前实施状态：

- `scripts/m18_status_report.py` 聚合机器可检查的 GKP coverage、backlog triage、patch proposal/review/apply dry-run、asset guard、ASR voice handoff、hotkey voice matrix、command-contract audit 和 quality-loop handoff。
- `scripts/m18_gate_status_json.py` 输出同一口径的 machine-readable rows/counts/open_areas/overall_status。
- `scripts/m18_next_action_queue.py` 现在只保留 M18 仍关心的机器/设备 action：设备生命周期复测（如有）、热键语音矩阵复跑和最终 offline gate。
- `scripts/m18_remaining_gate_packet.py` 已改为机器/设备 handoff，不再合并 ASR 人工审批、屏幕翻译手测、内容权利人工确认或 release checklist 更新。
- `scripts/m18_quality_loop_handoff.py` 保留 preview-first backlog imports：`/debug/latest-request`、hotkey voice `results.tsv`、manual tester notes TSV。
- `scripts/m18_offline_quality_gate.sh` 的 safe default 刷新 M18 报告、handoff、completion audit、next action queue、quality loop handoff 和 command-contract audit，并运行脚本测试、`rc_release_audit.py`、`git diff --check`；不刷新或 strict-check 三类已移出 M18 的人工 gate。

当前真正的下一步：

1. 刷新 M18 reports：`./scripts/m18_offline_quality_gate.sh`。
2. 若 hotkey voice matrix 仍有重复 miss，把它作为 backlog/patch proposal 输入，而不是等待人工 ASR 审批或人工 screen/content gate。
3. 保持 `app/src/main/assets/gkp` clean；只有用户明确批准 exact patch 时才改 bundled GKP。
4. 不新增游戏、不新增默认模型、不扩大屏幕捕获能力；当前开发只围绕热键语音矩阵、GKP 命中、诊断可解释性和文档同步。

## 1. 使用的工具/技能/MCP

| 能力 | 用途 | 本轮结论 |
| --- | --- | --- |
| `retrosprite-dev` skill | 固定产品方向：RetroArch AI Service 优先、本地知识包优先、低剧透、BYOK | 主路径改为 RetroArch 热键触发的短时语音 overlay；仍不做 Accessibility/MediaProjection 连续截屏 |
| `codex-multi-agent-development-loop` skill | 按任务板选择下一项、实施、验证并回写计划；未显式要求 subagents 时不启动子代理 | 本轮完成 M8.4，未启动子代理 |
| `project-codebase-onboarding-and-roadmap` skill | 以代码为准，比较文档漂移，生成可执行路线图 | `DELIVERY_REPORT.md` 部分内容已漂移，需要以后续计划覆盖 |
| Android SDK / ADB / AVD | 安装 APK、启动 `RetroSprite_API_34`、验证 endpoint 和 RetroArch Android 行为 | endpoint 可用；官方 APK 热键可触发请求，已修复真实 Content-Type 兼容问题 |
| 官方 DeepSeek API 文档 | 确认 OpenAI-compatible base URL、model id、chat payload | `base_url=https://api.deepseek.com`，`POST /chat/completions`，`model=deepseek-v4-pro` |
| `k2-fsa/sherpa-onnx` 官方仓库/文档 | 评估并落地 Android 本地离线 ASR 路线 | 已作为正式 ASR 主路径；当前使用 streaming Paraformer，术语误识别靠当前 GKP `asr_variant` / `observed_asr` 规范化，吞尾字优先通过 capture/commit 策略处理 |
| RAG/语音/GitHub 项目调研 | 评估是否必须接 LLM、是否可本地 ASR/TTS | RAG/Self-RAG/CRAG 与高星 RAG 项目都支持“检索优先、LLM 受控生成”；Whisper/sherpa-onnx/Rhasspy/Home Assistant 类项目证明本地 ASR/离线语音链路可独立成立；sherpa-onnx 提供独立 TTS engine/Android 模块，但当前项目依赖的 AAR 暂未暴露 TTS wrapper |
| `superpowers:test-driven-development` skill | 先锁定 Hotkey Wake Voice Overlay 行为再接 Android WindowManager | 新增 endpoint event 单测和 overlay coordinator 单测，确认真实 RetroArch 请求触发、debug 不触发、有权限才显示、可自动隐藏 |
| `superpowers:writing-plans` / `superpowers:executing-plans` skills | 将 Hotkey Wake Voice Overlay 拆成可验证小步 | 已新增 `docs/superpowers/plans/2026-05-20-hotkey-wake-voice-overlay.md`；M10.0 先完成 hotkey event + overlay cue 骨架 |
| GKP Lite / LLM 方向讨论 | 收束后续产品路线 | 新增 `docs/GKP_LITE_OPTIONAL_LLM_DIRECTION.md`；后续以轻量可信锚点 + 可选 evidence-gated LLM 为主线 |

## 2. 决策

1. **Phase 0 不算完全关闭，改名为 Phase 0A：Android 触发闭环。**
   已完成“官方 RetroArch Android 真实发起请求”，修复版 APK 已能从真实热键请求返回正常 text response。

2. **不把 DeepSeek 接入作为 RetroArch 触发问题的前置条件。**
   DeepSeek 可并行实现，但端到端产品闭环必须先能从 RetroArch 触发。

3. **DeepSeek 先做非流式 OpenAI-compatible 调用。**
   先支持 `chat/completions`、短答案、max token、超时、错误映射；默认 `thinking=disabled`，流式和 reasoning 展示后置。

4. **GKP MVP 先做可测试样例包，不做大规模内容工程。**
   先证明 FAQ/entity/FTS5/低剧透策略可以工作，再扩展知识包格式。

5. **语音输入收敛到 sherpa-onnx 本地离线 ASR，不做多 ASR 模式。**
   M8.1 的 Android `SpeechRecognizer` 只作为交互验证和历史 fallback 参考，不再作为正式产品依赖。M8.2 直接引入 `k2-fsa/sherpa-onnx`：本地录音、本地转写、转写文本继续进入现有 GKP/evidence-grounded pipeline；不设计 System/Cloud/Offline/Auto 多模式 UI，也不默认接入云 ASR。

6. **TTS 先保持 Android TextToSpeech。**
   本阶段只替换 ASR。短答案朗读继续使用系统 TTS；sherpa-onnx TTS 和 RetroArch `output=sound` 等语音输出增强后置，避免同时引入录音、模型推理、音频生成三类风险。

7. **ASR 规范化只做当前 GKP scoped 术语修复；吞尾字不靠文本补全。**
   Paraformer 负责尽量把语音转成文本；真实游戏专名、汉化名、误听词由当前游戏 GKP 的 `asr_variant` / `observed_asr` metadata 和 `GameTermNormalizer` 映射回 canonical term。不要做全局同音词替换，也不要把 `是什`、`玩什` 直接补成完整问题；尾字问题优先通过 `SherpaEndpointCommitGate` 的 endpoint + voice inactivity + stable partial 提交策略和 final flush 解决。

8. **RetroArch 设置助手只做复制，不写 cfg。**
   测试机已确认 AI Service 设置实际在 RetroArch `Settings → Accessibility → AI Service` 中；因此官方 UI 是主路径。RetroSprite Settings 只显示需要填入的推荐值，并提供 RetroArch 默认 AI Service URL `http://localhost:4404` 一键复制；不写 `retroarch.cfg`，不展示高级 cfg 片段，不托管 `input_enable_hotkey`、`input_ai_service` 或各类 `_btn`/`_axis` 绑定，也不申请全文件访问权限，避免影响发布审核和用户配置安全。

9. **Hotkey Wake Voice Overlay 成为真实产品主路径。**
   Pending Question 只保留为 debug/fallback，不再作为玩家体验主链路。RetroArch 热键负责把当前游戏 label/screenshot/state 送进 RetroSprite；RetroSprite 自己负责短时 overlay、语音收音、GKP 问答和 TTS 回复。该路径需要 `SYSTEM_ALERT_WINDOW` 用户显式授权，但不需要 Accessibility、MediaProjection、全文件访问或连续后台监听。

10. **LLM 是 gate 后的可选 composer，不是产品依赖。**
    默认产品路径必须在无 LLM key、无网络或用户关闭 provider 时仍可用：本地 ASR 只产出问题文本，GKP 和 AnswerPolicy 决定能否回答。高置信模板/entity 命中直接回答；多条本地证据需要自然语言综合时才可调用 LLM；无 evidence、GKP 禁用、证据冲突或剧透超限时不允许 LLM 裸答。指标上继续把 LLM 当成本项，目标是提高本地命中率并压低调用率，而不是让每次提问都进模型。

11. **TTS 可升级为本地 sherpa-onnx，但不能与 ASR 混为一谈。**
    现有 sherpa-onnx ASR 模型不能直接合成语音。短期保留 Android `TextToSpeech` 是正确收敛；若要验证本地神经 TTS，可先安装 sherpa-onnx TTS Engine APK 并设为系统默认 TTS，让现有 `AndroidSpeechOutputProvider` 间接受益。真正内置 TTS 需要单独里程碑：新增 `SpeechOutputProvider` 实现、选择中文/多语模型、处理 APK 体积、冷启动、PCM 播放、打断/stop、overlay 生命周期和真机发热。

12. **GKP 要覆盖玩家自然意图，而不只是攻略事实。**
    对真实游戏包，必须补齐“游戏身份、主要玩什么、核心循环、乐趣在哪里、适合谁、怎么玩才有意思、第一小时目标、低剧透探索习惯”等概览型问题。例：玩家问“这个游戏主要是玩什么？乐趣在哪里？”时，Shining Force II GKP 应能 zero-LLM 回答“剧情推进 + 网格回合战斗 + 队伍培养 + 职业/站位/隐藏探索”的短答案，并带 `sf2.official_overview` / `sf2.project_mechanics` 等来源。

13. **首个支持版本改为 GKP Lite，而不是完整攻略包。**
    GKP Lite 是一个可验证的可信游戏锚点：身份、平台/区域、RetroArch label、别名、核心玩法、第一小时方向、常见机制、关键术语、少量高频卡点、剧透门、来源和 golden。它不承诺完整路线、全隐藏清单、全 Boss 数据或全角色成长表。深度包可以在 Lite 之上继续扩展，但不作为“支持一个游戏”的前置条件。

14. **保留玩家自选 LLM 设置，LLM 是增强层而不是事实层。**
    Settings 中的 BYOK/OpenAI-compatible/DeepSeek/custom provider、model、base URL、API key、timeout、max token 和自检能力继续保留。LLM 开启后可以做口语问题理解、ASR 转写清理、跨语言 term mapping、多证据综合、翻译和表达润色；关闭时本地 GKP Lite 仍要可用。无 GKP、无 evidence、GKP 禁用、证据冲突或剧透超限时仍不能让 LLM 直接编攻略事实。

15. **多语言支持走语言层，不复制完整 GKP。**
    后续英文玩家支持应拆成：答案语言设置、英文/多语言 ASR 模型、英文 aliases/glossary/goldens、运行时 row/template language selection，以及可选 LLM 翻译/综合。`game_id`、`entity_id`、`source_id` 和剧透门应尽量语言中立；不要为了英文玩家给每个游戏重写一份完整英文攻略包。

## 3. 里程碑

### M0 - Phase 0A：RetroArch Android 触发闭环

目标：在 `RetroSprite_API_34` 或一台真机上，让官方或可控 RetroArch Android build 通过 AI Service 真实请求 RetroSprite endpoint。

任务：

| ID | 任务 | 产物 | 验证 |
| --- | --- | --- | --- |
| M0.1 | 固化当前 Android 实测记录 | `docs/RETROARCH_ANDROID_AI_SERVICE_FINDINGS.md` | 文档包含 APK、AVD、配置、失败路径 |
| M0.2 | 增加 device/AVD endpoint smoke 流程 | 脚本或文档命令 | `adb forward` 后 `/health` 和模拟 POST 通过 |
| M0.3 | 用实体键盘/手柄在 RetroArch Android 手动绑定 AI Service | 联调记录 | `request_logs` 出现来自 RetroArch 的成功请求 |
| M0.4 | 若官方 APK 仍不触发，构建 RetroArch debug 版并加日志 | RetroArch build notes / patch notes | logcat 能看到 `CMD_EVENT_AI_SERVICE_TOGGLE` 或缺失原因 |

退出条件：

- `request_logs` 中至少出现 1 条由 RetroArch Android 发起、非手工 curl 的请求。
- 该请求不是 `malformed_request`，能返回 text response。
- 记录 RetroArch version、配置文件、触发方式、截图/日志。
- `docs/PHASE0_VERIFICATION.md` 的最后一项可以勾选。

### M1 - DeepSeek BYOK Adapter

目标：用户提供 API key 后，RetroSprite 能通过 DeepSeek `deepseek-v4-pro` 生成短答案，但不绕过本地检索/低剧透策略。

任务：

| ID | 任务 | 产物 | 验证 |
| --- | --- | --- | --- |
| M1.1 | 实现 OpenAI-compatible request/response DTO | `llm` package | JVM 单测覆盖 payload 与解析 |
| M1.2 | 实现 `OpenAiCompatibleLlmAdapter.complete()` | OkHttp POST `/chat/completions` | 本地 fake server 单测 |
| M1.3 | 增加 DeepSeek config preset | `providerName=deepseek`, `baseUrl=https://api.deepseek.com`, `model=deepseek-v4-pro` | Factory 单测 |
| M1.4 | API key 从 DataStore 明文迁出 | Android Keystore/Tink 方案 | 不在 logcat/Room 中出现 key |
| M1.5 | Settings UI 写入 DeepSeek 配置 | Provider / baseUrl / model / apiKey | 手动输入后重启仍保留 |
| M1.6 | 真实 DeepSeek key 最小 smoke | 手工问题 + sample evidence + max token | 返回非流式短答案，失败可诊断 |

退出条件：

- 无 key 时仍使用 Mock，不产生外部请求。
- 有 key 时可完成一次非流式 DeepSeek 调用。
- 失败时返回可诊断错误，不让 RetroArch 等待到超时。

### M2 - GKP MVP 和本地检索

目标：让 RetroSprite 对一个内置样例游戏给出来源明确、低剧透的确定性答案。

任务：

| ID | 任务 | 产物 | 验证 |
| --- | --- | --- | --- |
| M2.1 | 冻结 GKP v0 manifest/knowledge schema | `docs/GKP_V0_SCHEMA.md` | schema 示例通过 lint |
| M2.2 | 增加内置 sample pack fixture | `app/src/main/assets/gkp/...` | 安装后 DB 有 game/knowledge rows |
| M2.3 | 实现 FAQ/entity/FTS5 检索漏斗 | `RetrievalPipeline` 实现 | 查询命中来源和 spoiler filter |
| M2.4 | AnswerPolicy 支持低剧透降级 | policy tests | 高剧透内容默认不直接泄露 |
| M2.5 | LLM composer 仅在有 evidence 时调用 | `AnswerComposer`/pipeline | 无证据时不调用 LLM |

退出条件：

- 对 sample game 的至少 16 个 golden Q&A 全部通过。
- 答案包含来源 id 或可追踪 evidence。
- 无证据问题会诚实说明不确定，而不是猜。

### M3 - 诊断与开发体验

目标：把“为什么没收到 RetroArch 请求/为什么没调用 LLM/为什么没命中 GKP”变成可见信息。

任务：

| ID | 任务 | 产物 | 验证 |
| --- | --- | --- | --- |
| M3.1 | Diagnostics 展示最近请求、错误、耗时、命中层级 | UI + repository 字段 | 手动请求后即刻可见 |
| M3.2 | endpoint 增加轻量 debug route（仅 loopback） | `/debug/latest-request` 或同等能力 | curl 可查看最后一条请求 |
| M3.3 | 脚本化 AVD 验证 | `scripts/android_avd_smoke.sh` | 一条命令验证安装/endpoint |
| M3.4 | 文档漂移修复 | README / DELIVERY_REPORT 后续注记 | 文档不再声称 Android 热键已验证 |

### M11 - Zero-LLM GKP 深化与本地 TTS 评估

目标：把 RetroSprite 从“能回答具体攻略点”推进到“能解释一个游戏为什么值得玩、怎么玩才进入状态”，同时保持 LLM 可选、TTS 可替换。

任务：

| ID | 任务 | 产物 | 验证 |
| --- | --- | --- | --- |
| M11.1 | 扩展 Shining Force II GKP 概览型知识 | `knowledge/mechanics.jsonl` 或 `knowledge/strategies.jsonl` 新增 `note.core-gameplay-loop` | `/debug/ask` 问“这个游戏主要是玩什么？乐趣在哪里？”返回本地来源答案，LLM skipped |
| M11.2 | 增加玩家自然问法 golden | `qa_goldens.jsonl` 覆盖“主要玩什么/好玩在哪/适合谁/新手怎么玩才有意思” | GKP golden 和 `SampleShiningForceIIQuestionPipelineTest` 通过 |
| M11.3 | 建立真实游戏 GKP 内容清单模板 | 更新 `REAL_GAME_GKP_EXPANSION_TEMPLATE.md` | 新包必须包含核心玩法、低剧透目标、乐趣点、误识别 alias 和 10 条语音化 golden |
| M11.4 | sherpa-onnx TTS Engine 验证 | 真机手动记录或文档注记 | 安装 sherpa-onnx TTS Engine APK 后，现有 Android `TextToSpeech` 输出可离线朗读短答案 |
| M11.5 | 内置 sherpa TTS spike | `SherpaOnnxTtsSpeechOutputProvider` 技术方案，不默认启用 | 明确依赖、模型体积、API wrapper、PCM 播放、stop/interrupt、冷启动和 RG 476H 性能风险 |

退出条件：

- Shining Force II 至少 4 个概览/乐趣/新手动机问题可在无 LLM key 时稳定回答。
- 回答仍引用本地来源，不把 LLM 当事实源。
- TTS 路线明确分为“系统 TTS engine 替换”和“App 内置 provider”，不把 ASR 模型误用为 TTS。

### M12 - GKP Lite Contract

目标：把“支持一个游戏”的最低标准从完整 GKP 收敛为轻量、可信、可测试的 GKP Lite。

任务：

| ID | 任务 | 产物 | 验证 |
| --- | --- | --- | --- |
| M12.1 | 定义 GKP Lite 覆盖层级 | `docs/GKP_LITE_OPTIONAL_LLM_DIRECTION.md`、`docs/REAL_GAME_GKP_EXPANSION_TEMPLATE.md` | 文档明确 Lite/expanded/deep 的差异 |
| M12.2 | 建立标准化 GKP Lite scaffold | `tools/gkp-builder/templates/gkp-lite/` | 生成 manifest、knowledge、aliases、spoiler_graph、sources、goldens、changelog 的统一骨架 |
| M12.3 | 定义机器可读 profile | `profile.yaml` / schema | 明确 required lanes、minimum goldens、source policy、coverage tier、LLM-disabled goldens |
| M12.4 | 增加 scaffold 命令 | `gkp-builder new --profile lite ...` 或等价脚本 | 新游戏可从模板生成，不再手工复制旧 pack |
| M12.5 | 增加 coverage lint / preflight 提示 | GKP preflight warning/error | 缺少核心玩法/身份/source/no-evidence/spoiler golden 时按规则 warning 或 error |
| M12.6 | Packs UI 显示 coverage tier | Packs card / detail | 用户知道当前包是 Lite、expanded 还是 deep |

退出条件：

- 一个真实游戏可凭 reviewed Lite pack 标记为初步支持。
- 新游戏 GKP Lite 可通过统一模板一键生成初始文件。
- 文档明确 Lite 能回答什么、不能回答什么。
- scaffold 生成的 TODO 占位在 lint 前不能误通过。
- no-evidence 和低剧透策略不因降低 GKP 完整度而变弱。

### M13 - Optional LLM Intelligence Layer

目标：让玩家启用 LLM 后获得明显更自然的理解、综合和语言桥能力，但不破坏 evidence gate。

任务：

| ID | 任务 | 产物 | 验证 |
| --- | --- | --- | --- |
| M13.1 | Settings 明确 LLM assist 开关和解释 | Settings copy / UI state | 玩家可区分“本地可用”和“LLM 增强” |
| M13.2 | 启用 evidence-backed 多证据综合 | `EvidenceAnswerPolicy` / `AnswerComposer` | 多 evidence 场景 LLM used，source ids 保留 |
| M13.3 | LLM query rewrite / term mapping | pre-retrieval assist layer | ASR 噪声和跨语言问题能映射到 canonical term |
| M13.4 | no-evidence 不调用 LLM 裸答测试 | policy/composer tests | 无 evidence 的具体攻略问题 `llmTrace=skipped` |
| M13.5 | 诊断 evidence gate 决策 | Diagnostics / request log | 可看到 LLM 为什么 used/skipped/failed |

退出条件：

- LLM 关闭时全部本地 golden 仍通过。
- LLM 开启时复杂问题的表达/综合更好，但事实仍来自 evidence。
- LLM 失败时回退到本地答案或明确错误，不阻塞主体验。

### M14 - Answer Language And Multilingual Support

目标：支持英文玩家和跨语言提问，而不是为每个游戏复制完整英文 GKP。

任务：

| ID | 任务 | 产物 | 验证 |
| --- | --- | --- | --- |
| M14.1 | 增加回答语言设置 | DataStore + Settings + runtime wiring | 与 UI language 分离 |
| M14.2 | 支持多语言 row/template 选择 | resolver/repository/retrieval changes | 同 `game_id` 可按 answer language 选择 surface |
| M14.3 | 英文 no-evidence/clarification/source 文案 | AnswerComposer / AnswerPolicy | 英文问题不会收到中文 fallback |
| M14.4 | 英文或多语言 ASR 模型包方案 | ASR model manager plan / spike | ASR 是语言包，不是每游戏语音包 |
| M14.5 | Shining Force II 双语 glossary/goldens | GKP Lite localized surfaces | 英文问题可命中同一 canonical entity |

退出条件：

- 英文文字问题能命中同一游戏知识锚点。
- 英文回答可在有 evidence 时生成或本地模板返回。
- 中英文 GKP surface 不互相覆盖。

### M15 - Generic Mode And Failure Inbox

目标：让没有 GKP 的游戏也有诚实的下一步帮助，同时把失败问题转化为 GKP Lite 生产输入。

任务：

| ID | 任务 | 产物 | 验证 |
| --- | --- | --- | --- |
| M15.1 | 可选 Generic Mode | AnswerPolicy / UI copy | 明确标记不是当前游戏可靠攻略 |
| M15.2 | `generic_ungrounded` 诊断阶段 | request log / Diagnostics | generic 回答不会伪装成 evidence |
| M15.3 | 本地 unanswered question inbox | Room / Diagnostics or Packs entry | 按 label、intent、language 聚合失败问题 |
| M15.4 | Builder 任务导出 | GKP Lite production queue | 高频 no-evidence 问题能进入内容生产 |
| M15.5 | 指标看板 | diagnostics counters | local hit rate、no-evidence rate、LLM evidence-backed rate 可观察 |

退出条件：

- 未支持游戏给出诚实帮助，不编具体攻略事实。
- 失败问题可以直接指导下一个 Lite pack 的覆盖优先级。
- 支持范围扩张不再依赖先手写完整攻略包。

## 4. 活动任务板

| 状态 | ID | 任务 | Owner | 备注 |
| --- | --- | --- | --- | --- |
| Done | M0.1 | 固化 RetroArch Android AI Service 实测记录 | Codex | `docs/RETROARCH_ANDROID_AI_SERVICE_FINDINGS.md` 已记录成功请求 |
| Done | M3.4 | 修正文档漂移 | Codex | Setup / Phase0 / Findings / Plan 已更新 |
| Done | M0.2 | 增加 AVD endpoint smoke 脚本 | Codex | `scripts/android_avd_smoke.sh` |
| Done | M1.1 | DeepSeek/OpenAI-compatible DTO | Codex | `OpenAiCompatibleLlmAdapter` 内部 DTO |
| Done | M1.2 | DeepSeek/OpenAI-compatible adapter + fake server 单测 | Codex | `OpenAiCompatibleLlmAdapterTest` |
| Done | M1.3 | DeepSeek config preset + factory route | Codex | `LlmConfig.deepSeek()` |
| Done | M1.4 | API key 安全存储 | Codex | Android Keystore AES-GCM + legacy migration |
| Done | M1.5 | Settings UI 写入配置并接入运行时 wiring | Codex | `DynamicLlmAdapter` + Settings mapper |
| Done | M1.6 | 真实 DeepSeek key 最小 smoke | User/Codex | `scripts/deepseek_live_smoke.sh` |
| Done | M0.3 | 实体手柄/键盘手动验证 | User/Codex | `request_logs` id=14，`2026-05-19 13:00:08`，`2048__`，`image_size=1796`，无错误 |
| Done | M2.1 | GKP v0 schema | Codex | `docs/GKP_V0_SCHEMA.md` + fixture lint |
| Done | M2.2 | 内置 sample pack fixture | Codex | `sample-2048` 启动导入；AVD DB 验证 `2048` + 8 rows |
| Done | M2.3 | FAQ/entity/FTS5 检索漏斗 | Codex | `RepositoryGameResolver` + `LocalKnowledgeRetrievalPipeline` 已接入；sample 2048 golden 检索通过 |
| Done | M2.4 | AnswerPolicy 支持低剧透降级 | Codex | `EvidenceAnswerPolicy`：有证据直答带来源，无证据不猜，高剧透证据降级提示 |
| Done | M2.5 | LLM composer 仅在有 evidence 时调用 | Codex | 证据 prompt、source id 追踪、无证据不调用 LLM；`/debug/ask` 可验证 sample 问答 |
| Done | M3.1 | Diagnostics 展示命中链路 | Codex | 展示 debug 请求、错误、source id、pipeline stage、LLM 状态 |
| Done | M3.2 | endpoint 增加轻量 debug latest route | Codex | `/debug/latest-request` 返回最新请求摘要，loopback-only |
| Done | M3.3 | 脚本化 AVD 验证增强 | Codex | `scripts/android_avd_smoke.sh` 已纳入自动安装/启动、`/debug/ask` 和 `/debug/latest-request` |
| Done | M4.1 | App 内文字提问入口 | Codex | Home 页可输入 label/question，直接复用 GKP 问答管线并写入 Diagnostics |
| Done | M4.2 | 提问入口跟随最近 RetroArch 上下文 | Codex | `HomeViewModel` 订阅 request log，筛选真实 RetroArch label，保留用户手动覆盖 |
| Done | M4.3 | 最近 RetroArch 上下文展示与恢复 | Codex | Home 页显示 label/时间/paused/GKP evidence，并支持恢复最近上下文 |
| Done | M4.4 | Home 文字提问 Compose instrumentation 测试 | Codex | `RetroSpriteAppSmokeTest` 覆盖输入问题、显示答案/来源、Diagnostics `APP` 日志 |
| Done | M4.5 | Home 输入来源状态固化 | Codex | 明确上下文来自 RetroArch 请求，问题来自 App 输入框，并纳入 UI instrumentation 断言 |
| Done | M4.6 | LLM 耗时/预算/失败诊断 | Codex | `QueryPipelineResult`/`ResponseDiagnostics`/request log v2 贯通 provider/model/max token/timeout/latency/token/错误 |
| Done | M4.7 | LLM timeout / max token Settings 化 | Codex | Settings/DataStore/runtime wiring 已接通；`AnswerComposer` 使用动态 token budget；fake timeout UI 验收覆盖 Home + Diagnostics |
| Done | M4.8 | Settings LLM 配置自检 | Codex | `RealLlmConfigTestProvider` 使用当前 provider/model/timeout/max token 发起最小 smoke；结果显示在 Settings，不写 request log，错误会 redacts API key |
| Done | M5.1 | 扩展 `sample-2048` GKP 内容与 golden 问题 | Codex | pack `0.1.1` 覆盖开局、主方向、满盘救局、撤销/重开、胜利目标、边缘链；16 条 golden 检索通过 |
| Done | M5.2 | Home 回答反馈按钮 + Diagnostics 本地记录 | Codex | Room `request_logs` v3 增加 `request_key`/feedback 字段；Home 支持“有帮助 / 这不对”；Diagnostics 显示反馈标签和详情 |
| Done | M5.3 | 第二个 sample GKP fixture | Codex | 自写 `sample-relay-station` 覆盖状态/物品/地点/阶段/低剧透路线；lint/parser/retrieval golden/question pipeline 通过；运行时 importer 已纳入该 pack |
| Done | M5.4 | Packs 页真实 GKP 管理视图 | Codex | `GkpLibraryProvider` 观察 Room games/knowledge 与启动导入状态；Packs 显示 pack/game/version/schema/知识行数/来源数/许可摘要；Compose smoke 覆盖 |
| Done | M5.5 | 外部 GKP 导入预检 | Codex | `GkpV0PreflightValidator` 校验 manifest/JSONL/schema/license/危险文件；Packs 页可选择文件夹并展示只读预检结果；不写入 Room |
| Done | M5.6 | 外部 GKP 安装/覆盖确认 | Codex | `ExternalGkpInstaller` 在安装前重新预检，通过 Room transaction 写入 game/knowledge；Packs 显示覆盖风险并要求用户确认 |
| Done | M5.7 | 本地 GKP 删除确认 | Codex | Packs 行级删除入口；确认卡显示 `pack_id`/`game_id`/version/知识行数/来源数；确认后 transaction 清理 game + knowledge；内置样例提示重启可能恢复 |
| Done | M5.8 | GKP provenance/signature 基础字段 | Codex | Room v4 增加 `pack_id`/`provenance`/`signature_status`/`signature_key_id`/`content_digest`；parser/preflight/install/importer/UI 贯通；bundled importer 跳过外部覆盖；迁移测试覆盖 v3→v4 回填 |
| Done | M5.9 | GKP 禁用/启用 | Codex | Room v5 增加 `enabled`/`disabled_at`；Packs 行级启用/禁用按钮；禁用包保留数据但不参与 `RepositoryGameResolver`；bundled importer 保留禁用状态；迁移测试覆盖 v3→v5 回填 |
| Done | M5.10 | GKP 禁用状态解释链路 | Codex | 禁用包解析为 `gkp_disabled` 但不检索知识/调用 LLM；Home 最近上下文显示“GKP 已禁用”；Diagnostics 详情解释包存在但已禁用；Packs 说明禁用/删除边界；单测和 Compose smoke 覆盖 |
| Done | M5.11 | AVD smoke 双样例 GKP 收口 | Codex | `scripts/android_avd_smoke.sh` 默认验证 `sample-2048` 与 `sample-relay-station` 两个 `/debug/ask` 链路，并分别检查 latest-request 的 evidence/skipped/source；README/Phase0/Test Coverage 同步 |
| Done | M5.12 | M5.x 文档漂移最终收口 | Codex | `RETROARCH_SETUP`/`PHASE0_VERIFICATION`/`PROTOCOL_REFERENCE` 更新旧 Phase 0 文案；`DELIVERY_REPORT` 标记为历史快照；当前 RetroArch 触发、Home/debug 问答、BYOK evidence 边界已同步 |
| Done | M6.1 | Home 快捷问题草稿 | Codex | 已知样例 label 生成 2048/Relay Station 相关问题草稿；点选只填入问题框，不自动提交或绕过 evidence gate；ViewModel 单测与 Compose smoke 覆盖 |
| Done | M6.2 | Home 失败恢复动作 | Codex | 回答区根据 `pipeline_stage`/`llm_status`/错误文本显示下一步动作：重新启用 GKP、补充上下文、检查 LLM 配置或打开 Diagnostics；Compose smoke 覆盖 LLM timeout 恢复提示 |
| Done | M6.3 | 最近上下文行动条 | Codex | Home 最近 RetroArch 卡片新增“使用此上下文”和“复制 debug curl”；debug curl 自动带当前 label 与样例问题，便于开发机复现 `/debug/ask` 链路；ViewModel 与 Compose smoke 覆盖 |
| Done | M6.4 | Home 恢复动作跨 tab 跳转 | Codex | 恢复提示按钮可跳转 Diagnostics/Packs/Settings；Home 只上报 `HomeNavigationTarget`，由 NavHost 映射顶层 tab；Compose smoke 覆盖 LLM 失败跳到 Settings |
| Done | M6.5 | M6 恢复动作与 debug 复现 smoke 收口 | Codex | Compose instrumentation 覆盖 GKP 禁用跳 Packs、请求错误跳 Diagnostics、LLM 失败跳 Settings；`android_avd_smoke.sh` 继续验证两个样例 GKP 的 `/debug/ask` 与 `/debug/latest-request` |
| Done | M6.6 | Home 最小 in-app conversation tray | Codex | 最近 5 次 App 内问答进入 Home 内存会话托盘；记录显示 label/question/answer preview/pipeline/LLM/source；点选记录可恢复 label/question/result；ViewModel 单测与 Compose smoke 覆盖 |
| Done | M6.7 | 会话托盘追问草稿 | Codex | 每条最近问答生成“更明确 / 直接答案 / 换个问法”草稿；点击只恢复 label/result 并填入 question，不提交、不新增 request log、不绕过 evidence gate；ViewModel 单测与 Compose smoke 覆盖 |
| Done | M6.8 | 直接答案剧透升级提示 | Codex | “直接答案”草稿带 `spoilerEscalationNotice`；Home 输入区显示“剧透级别提升”提示；手动编辑问题会清除提示；不会自动提交或更改 LLM/provider 行为；ViewModel 单测与 Compose smoke 覆盖 |
| Done | M6.9 | 剧透级别策略下沉 | Codex | `RetroArchRequest` 增加可选 `spoiler_level`；`QueryPipelineResponseGenerator` 优先使用请求 override，否则使用 Settings 默认剧透级别；Home “更明确/直接答案”追问分别传入 `CLEAR/FULL`；不改变 LLM/provider/API key 行为；单测覆盖 request DTO、endpoint bridge、Settings mapper、PlayerQuestion provider 和 Home ViewModel |
| Done | M7.0 | Pending Question → RetroArch Hotkey 最小闭环 | Codex | Home 可“准备给下次热键”并显示/取消 pending 问题；endpoint wrapper 只在真实请求 `question` 为空且 label 匹配时消费 pending question，显式 App/debug 问题不受影响；pending 携带单次 `spoiler_level`；单测覆盖 store/wrapper、provider、Home ViewModel，Compose smoke 覆盖 prepare 不提交日志 |
| Done | M7.1 | Request log question persistence | Codex | Room v6 增加 `request_logs.question` / `question_source`；RequestLogger、Room sink、UI mapper、debug latest request、Diagnostics 详情和 Home 最近上下文贯通问题元数据；App/debug/pending hotkey 来源分别记录为 `app`/`debug`/`pending_hotkey`；迁移/DAO/mapper/endpoint/ViewModel 测试覆盖 |
| Done | M7.2 | Request log → Home conversation tray 恢复 | Codex | `UiRequestLogItem` 保留完整 `responseText`；`HomeViewModel` 将真实 RetroArch / pending hotkey 且带 `question` 的成功日志恢复为最近问答，支持点选恢复和继续追问；过滤 `app:*`、`debug:*`、diagnostic、失败和无问题日志；ViewModel/mapper 单测与 Compose smoke 覆盖 |
| Done | M7.3 | Diagnostics 来源筛选/计数 | Codex | Diagnostics 日志卡增加 `全部 / RetroArch / Pending / App / Debug` 来源计数与筛选按钮；普通 RetroArch、pending hotkey、App 内提问和 debug ask 分类规则集中在 `DiagnosticsSourceFilter`；JVM 单测覆盖分类/计数/过滤，Compose smoke 覆盖计数栏渲染 |
| Done | M8.1 | App 内语音输入 + 短答案 TTS MVP | Codex | `RECORD_AUDIO` 权限；`AndroidVoiceInputProvider` 优先端侧 `SpeechRecognizer`、不可用时走系统识别；Home 语音按钮只填充问题框；`AndroidSpeechOutputProvider` 使用系统 TTS 朗读成功答案第一句/短摘要；fake provider 让 Compose smoke 覆盖语音填充和朗读按钮；未改变 DeepSeek/LLM provider，也未实现 RetroArch `output=sound` |
| Done | M8.2 | 使用 sherpa-onnx 替换正式 ASR 路径 | Codex | `SherpaOnnxVoiceInputProvider` 已成为默认 wiring；APK 内置 streaming Paraformer bilingual zh-en int8 模型 assets、`libsherpa-onnx-jni.so` 和 `libonnxruntime.so`，Debug 包当前只打 `arm64-v8a` 以降低体积；JVM 测试覆盖模型资源契约，RG 476H/AVD instrumentation 均验证默认模型可从 bundled assets 初始化；Home/Hotkey 语音输入仍只产出问题文本，答案继续走 GKP/evidence/低剧透管线；Android `SpeechRecognizer` 不再作为主路径 |
| Done | M8.3 | 真机语音 QA 与状态 polish | User/Codex | RG 476H 真机说“两个 2 怎么合并？”→ 转写“两个二怎么合并”→ 点击“提问”→ 正确返回 `sample-2048` 答案；当时样例路径不需要额外 ASR 文本规范化。Home 语音状态增加首次模型加载提示和空识别提示；Compose instrumentation 覆盖两类 UI 状态 |
| Done | M8.4 | 真机语音回归与冷启动体感记录 | User | RG 476H 上 5 个语音问题全部通过：2048 冷启动、2048 热启动、空识别提示、Relay Station 样例、再次 2048 回归；该结论已被后续真实 GKP scoped ASR variant 方案取代，而不是云 ASR 或全局补全 |
| Done | M9.1 | 真实游戏 GKP 内容路线选择：Shining Force II MD | Codex | 新增 `community.shining-force-ii-md` bundled GKP：13 条原创短知识、12 条 golden Q&A、官方/社区来源引用、低剧透早期路线、战斗/复活/转职/特殊转职机制；检索 golden 和问答 pipeline 单测通过，AVD `/debug/ask` 已验证转职/低剧透问题，LLM 不参与确定性答案 |
| In Progress | M9.2 | Shining Force II 真机/RetroArch label 验证 | User/Codex | 已从 RG 476H playlist 读取实际 label `光明力量2`，并把 `md__光明力量2` 和真实 AI Service label `mega_drive__光明力量2` 纳入 GKP `0.1.2` 解析回归；RG `/debug/ask` 已验证转职/低剧透问题都能命中本地证据；真实热键问答验收迁移到 M10 overlay 语音路径，pending hotkey 仅保留为 debug/fallback |
| Done | M9.3 | Settings RetroArch 设置助手 | Codex | 移除 cfg 写入代码、provider 和 patcher 测试；Settings 只显示 RetroArch `Settings → Accessibility → AI Service` 路径、推荐值和 RetroArch 默认 AI Service URL `http://localhost:4404`；不写 cfg、不展示高级 cfg 片段、不改热键、不新增存储权限 |
| Done | M10.0 | Hotkey Wake Voice Overlay 骨架 | Codex | 真实 RetroArch `POST /` 会通知 `RetroArchHotkeyListener`，debug ask 不触发；`AndroidHotkeyVoiceOverlayController` 已接入 `EndpointController`；有 overlay 权限时显示右上角不拦截触摸的彩色波形 cue 并自动关闭；JVM 单测覆盖 endpoint event 和 coordinator 状态 |
| Done | M10.1 | Overlay 权限 onboarding | Codex | Settings 增加“游戏内语音 Overlay”区块，显示授权状态、打开系统授权页、刷新状态；`AndroidOverlayPermissionProvider` 封装 `Settings.canDrawOverlays` 和 `ACTION_MANAGE_OVERLAY_PERMISSION`；ViewModel JVM 单测与 Compose smoke 覆盖 |
| In Progress | M10.2 | 真机热键唤醒 overlay + voice loop 验收 | User/Codex | RG 476H 已确认 RetroArch 热键后右上角波形出现、语音识别完成并 TTS 朗读；`/debug/latest-request` 确认 `label=mega_drive__光明力量2`、`output_mode=hotkey_voice:text`、`question_source=hotkey_voice`。后续 Tingting 批测确认 overlay 能进入 `finished`，但 Shining Force II `气合之玉怎么用？` 仍因 `气河之欲` 误识别和 source ranking 未命中 `sf2.promotion`，所以验收剩余风险转到 GKP ASR variant/source-ranking |
| Done | M10.3 | Hotkey voice ASR → GKP → TTS loop | Codex | `HotkeyVoiceQuestionController` 接管热键主路径：有 overlay 权限时启动一次 sherpa-onnx ASR，提交最终转写到现有 GKP 问答管线，写入 `hotkey_voice:text` / `question_source=hotkey_voice` 日志，TTS 朗读后关闭 overlay；原始空问题 RetroArch wake 请求静默返回；修复后台录音 foreground-service microphone 类型、重复 wake 去抖和 ASR timeout 收口；JVM 单测覆盖成功闭环、缺权限不录音、重复 wake、timeout hide 和 silent wake wrapper |
| Done | M11.1 | Shining Force II 核心玩法/乐趣 GKP 扩展 | Codex | `note.core-gameplay-loop` 已加入 `0.2.1`，覆盖“这个游戏主要是玩什么？乐趣在哪里？”等概览型问题，zero-LLM 直答带 `sf2.official_overview` / `sf2.project_mechanics` 来源 |
| Done | M11.2 | 概览型自然问法 golden | Codex | 新增 4 条 golden，覆盖“主要玩什么 / 好玩在哪 / 核心玩法 / 适合什么玩家”，pipeline 单测确认 LLM 未调用 |
| Done | M11.3 | 真实游戏 GKP 内容清单模板更新 | Codex | `REAL_GAME_GKP_EXPANSION_TEMPLATE.md` 新增 Core Gameplay And Fun Hooks lane，并把 4 条概览型 golden 作为接受标准 |
| Pending | M11.4 | sherpa-onnx TTS Engine 验证 | User/Codex | 先用系统 TTS engine 替换验证本地离线朗读，不改问答管线 |
| Pending | M11.5 | 内置 sherpa TTS 技术 spike | Codex | 只做方案和风险确认；不阻塞 GKP/voice overlay 主路径 |

## 5. 下一步执行顺序

1. **M10.2：真机证据命中复测。**
   - 已通过：真实 RetroArch 热键、右上角波形、overlay 不阻塞游戏输入、ASR→pipeline→TTS 朗读、Diagnostics/latest-request `hotkey_voice:text`。
   - 待通过：用更清晰短句复测“角色什么时候转职？”和“下一步去哪？不要剧透”，确认分别命中 `sf2.promotion` / `sf2.early_route`。
   - 如果仍出现 ASR 误识别，先不加复杂 ASR 模式；只记录误识别样本，再考虑最小的 GKP alias/query fallback。
2. **真机验证 RetroArch 官方 UI + Settings 连接说明。**
   - 主验收路径：在 RetroArch `Settings → Accessibility → AI Service` 设置为开启，URL 保持默认 `http://localhost:4404`，`AI Service Output` 选择 `旁白模式（Narrator Mode）`。
   - RetroSprite Settings 只用于显示/复制默认 URL；不再尝试写入 cfg。
3. **M11.4/M11.5：TTS 路线只做验证和方案，不阻塞 GKP。**
   - 先用 sherpa-onnx TTS Engine APK 作为系统 TTS engine 验证本地离线朗读。
   - App 内置 TTS provider 后置；必须先确认模型体积、API wrapper、冷启动、PCM 播放、打断和 RG 476H 性能。
4. RetroArch `output=sound` 暂不做默认路径；等 hotkey voice overlay、真实 GKP 问答和 TTS 方案稳定后，再决定是否把 endpoint response 的 `sound` 字段从文档占位推进到真实音频输出。
5. registry 分发前再实现真正签名验证；当前 M5.8-M11 只持久化签名声明/摘要、来源边界、启用状态、禁用诊断解释、双样例 smoke、文档一致性、快捷提问体验、失败恢复提示、最近上下文行动条、恢复动作跨页跳转、恢复/debug smoke 自动化、最小本机会话托盘、追问草稿、直接答案剧透升级提示、剧透级别策略下沉、pending hotkey 文字闭环、request log 问题持久化、热键日志会话恢复、Diagnostics 来源筛选/计数、App 内语音/TTS MVP、sherpa-onnx 本地 ASR 主路径、真机语音 QA、语音状态 polish、5 条真机语音回归、首个真实游戏 GKP 试点、RetroArch 设置助手、Hotkey Wake Voice Overlay 骨架、hotkey voice ASR→TTS 闭环和 zero-LLM GKP 深化方向。

## 6. 验证命令

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android

# JVM 单测 + Debug APK
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew testDebugUnitTest :app:assembleDebug

# 语音/TTS MVP 窄口
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew testDebugUnitTest --tests com.retrosprite.app.ui.integration.SpeechOutputTextTest

# M8.2 sherpa-onnx 本地 ASR 资源/JNI 初始化窄口
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew testDebugUnitTest --tests com.retrosprite.app.ui.integration.SherpaOnnxAsrModelTest

JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.retrosprite.app.ui.integration.SherpaOnnxRecognizerAndroidTest

# M8.3 手动语音目标验证（2026-05-20 已通过）：
# RG 476H 真机：点 Home 的语音输入，说“两个 2 怎么合并？”
# 转写文本：“两个二怎么合并”
# 直接点击“提问”后命中 sample-2048 GKP 并返回正确答案。
# 当时结论：sample-2048 路径不需要 ASR 文本规范化；真实游戏专名另走 GKP scoped ASR variant。

# M8.4 手动语音回归（2026-05-20 已通过）：
# RG 476H 真机 5 个问题全部通过：
# 2048 冷启动、2048 热启动、空识别提示、Relay Station 样例、再次 2048 回归。
# 结论：进入真实游戏 GKP 内容路线；后续专名误识别只做当前 GKP scoped 修复。

# M8.3 语音状态 polish 窄口
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.retrosprite.app.ui.RetroSpriteAppSmokeTest#homeVoiceControlsShowLocalAsrStatusAndEmptyResultHint

# M9.1 Shining Force II MD GKP 窄口
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew testDebugUnitTest \
  --tests com.retrosprite.app.data.retrieval.SampleShiningForceIIRetrievalGoldenTest \
  --tests com.retrosprite.app.domain.SampleShiningForceIIQuestionPipelineTest

# AVD / device endpoint smoke
adb forward tcp:18080 tcp:4404
HOST=127.0.0.1 PORT=18080 ./scripts/test_endpoint.sh

# Debug question route（RetroArch 原始 body 没有 question 字段时用于开发验证）
curl -fsS -X POST 'http://127.0.0.1:18080/debug/ask?output=text' \
  -H 'Content-Type: application/json' \
  --data '{"label":"2048__","question":"两个 2 怎么合并？","state":{}}'

curl -fsS -X POST 'http://127.0.0.1:18080/debug/ask?output=text' \
  -H 'Content-Type: application/json' \
  --data '{"label":"relay_station__","question":"蓝色保险丝在哪？","spoiler_level":"direct","state":{}}'

curl -fsS 'http://127.0.0.1:18080/debug/latest-request'

# 或直接使用 device/AVD 脚本；默认会在缺失时自动安装 Debug APK
./scripts/android_avd_smoke.sh

# 强制重新构建并安装后再 smoke
BUILD=1 INSTALL=1 STRESS=5 ./scripts/android_avd_smoke.sh

# Android Keystore API key 加密验证
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.retrosprite.app.security.AndroidKeystoreSecretCipherTest

# 真实 DeepSeek BYOK smoke（从 stdin 读取 key，不把 key 放进命令行参数）
./scripts/deepseek_live_smoke.sh
```

## 7. 待确认

- DeepSeek `deepseek-v4-pro` 当前默认关闭 thinking；后续是否提供 UI 开关仍待产品确认。
- API key 已使用 Android Keystore-backed AES-GCM 加密；真实 key smoke 前仍需确认用户愿意在本机 AVD 内保存 key。
- RetroArch AI Service 原始请求只带 label/screenshot/state，不带玩家问题；Pending Question 已降级为 debug/fallback。真实产品主路径改为 RetroArch 热键唤醒 RetroSprite overlay，随后由 RetroSprite 本地 sherpa-onnx ASR 收集玩家问题。
- Settings 的 RetroArch 设置助手只复制 URL，不写 `retroarch.cfg`。RetroArch 选项和热键仍由玩家在 RetroArch 内设置，避免 Android scoped storage 和发布权限风险。
- M8.2 后 App 内语音输入不再依赖设备/系统语音服务；AVD/RG 476H 已验证 bundled sherpa 模型可初始化，真实识别质量仍以 RG 476H 断网手动语音测试为准。
- RetroArch Android 触发验证是否优先使用真机：AVD 的键盘/overlay 注入与真实手柄输入行为有差异，真机可更快排除模拟器输入层问题。
