# RetroSprite 下一阶段实施计划

> 生成日期：2026-05-19
> 最近更新：2026-05-24
> 依据：代码现状、`docs/DELIVERY_REPORT.md`、Android AVD 实测、RetroArch v1.22.2 源码/官方 APK 行为、DeepSeek 官方 API 文档，以及 `docs/GKP_LITE_OPTIONAL_LLM_DIRECTION.md`。

> 文档口径提示：本文保留了从 Phase 0 到当前阶段的历史实施记录，前半部分可能仍提到
> 已移除的 sample 包或早期状态。当前架构、GKP Lite、expanded/deep 覆盖、Pro 商业层
> 与可选 LLM 的统一定义见 `docs/ARCHITECTURE_AND_PRODUCT_TIERS.md`；当前已验证
> 范围以 `docs/TEST_COVERAGE.md` 和 README 的 supported-games 列表为准。

## 0. 当前真实状态

RetroSprite Android 已经具备可运行的 Phase 0/Phase 1 脚手架：

- Android 本地 endpoint 默认运行在 `http://localhost:4404`（绑定 loopback），`GET /health` 和 `POST /?output=text` 可用。
- 请求可写入 Room `request_logs`，Diagnostics/UI 层可读取近期日志。
- Domain 管线已拆出 `GameResolver`、`RetrievalPipeline`、`AnswerPolicy`、`AnswerComposer`、`LlmAdapter`。
- Room/FTS5、Compose 四屏、DataStore 设置和测试骨架已存在。
- `OpenAiCompatibleLlmAdapter` 已支持非流式 OpenAI-compatible `/chat/completions`；`ServiceLocator` 通过 Settings 动态创建真实 adapter，无 key 时仍回落到 `MockLlmAdapter`。
- LLM API key 已从 DataStore 明文迁到 Android Keystore-backed 加密密文；旧明文字段会在启动后迁移并移除。
- 真实 DeepSeek BYOK smoke 已在 `RetroSprite_API_34` 上通过；DeepSeek 请求默认关闭 thinking 以控制延迟和 token 成本。
- 官方 RetroArch Android APK 已安装并配置 AI Service；实体键盘热键已触发到 RetroSprite endpoint。真实 APK 会用 `application/x-www-form-urlencoded` Content-Type 发送 JSON body，RetroSprite 已增加兼容解析并在 `RetroSprite_API_34` 上完成修复版手动热键复验。
- 内置 GKP 已接入运行时：`RepositoryGameResolver` 能把 `2048__`、`relay_station__`、`md__Shining Force II`、真机 playlist 读到的 `md__光明力量2` 和真机 AI Service 发来的 `mega_drive__光明力量2` 解析到 GKP，`LocalKnowledgeRetrievalPipeline` 可按 template/entity/FTS 漏斗命中本地证据，`EvidenceAnswerPolicy` 可输出带来源的低剧透答案；当前包含 `sample-2048` `0.1.1`（14 条知识行、16 条 golden Q&A）、自写 `sample-relay-station` `0.1.0`（14 条知识行、12 条 golden Q&A）和 `community.shining-force-ii-md` `0.2.1`（32 条知识行、34 条 golden Q&A）。
- Diagnostics 已能标记 debug 请求、source ids、pipeline stage 和 LLM 状态；`/debug/latest-request` 可返回最新请求摘要，方便 AVD/真机联调。
- `scripts/android_avd_smoke.sh` 已增强为一条命令验证：设备在线、APK 自动安装/启动、端口转发、endpoint smoke、`sample-2048` 与 `sample-relay-station` 两个内置 GKP 的 `/debug/ask` 问答、`/debug/latest-request` 链路摘要。
- Home 页已增加 App 内文字提问入口：默认使用 `2048__` 样例 label，把玩家问题直接送入 `ResponseGenerator → QueryPipeline → RequestLogger`，并在 Diagnostics 中以 `output_mode=app:text` 记录。
- Home 页文字提问入口已能自动跟随最近一次真实 RetroArch 请求 label；`debug:*`、`app:*` 和 diagnostic 自测请求不会污染默认游戏上下文。
- Home 页已展示最近 RetroArch 上下文摘要：label、时间、暂停状态、GKP/evidence 状态和来源；用户手动覆盖 label 后可一键恢复最近上下文。
- Home 页已增加快捷问题草稿：`2048__` 和 `relay_station__` 会显示上下文相关问题按钮，点选只填入输入框，不绕过原有 GKP/evidence/LLM gate。
- Home 页已增加恢复动作提示：无 evidence、GKP 禁用、LLM 失败或请求错误会在回答区提示下一步该去 Packs、Settings、Diagnostics 还是补充上下文。
- Home 页最近 RetroArch 上下文已增加行动条：可使用当前上下文 label，并复制 host 侧 `/debug/ask` curl 复现当前样例问题。
- Home 页恢复动作已支持跨 tab 跳转：请求错误进入 Diagnostics，GKP 禁用或无 evidence 进入 Packs，LLM 失败进入 Settings。
- Home 页恢复动作收口 smoke 已自动化：Compose instrumentation 覆盖 Settings、Packs、Diagnostics 三类恢复跳转，AVD smoke 继续验证 `/debug/ask` 与 latest-request 复现链路。
- Home 页已增加最小 in-app conversation tray：最近 5 次 App 内问答会保留在本机内存中，显示 label、问题、答案预览、pipeline/LLM 状态和来源；点选记录可恢复对应 label/question/result 继续追问。
- Home 页会话托盘已接入持久化 request log：真实 RetroArch / pending hotkey 请求只要带有 `question`，就会恢复为最近问答；`app:*`、`debug:*`、diagnostic 和失败请求不会重复污染托盘。
- Home 页会话托盘已增加追问草稿：每条记录可生成“更明确 / 直接答案 / 换个问法”三类草稿，点击只填入输入框，不自动提交、不绕过本地 GKP 和低剧透策略。
- Home 页“直接答案”追问已增加剧透升级提示：选择该草稿后输入区显示“剧透级别提升”，提示用户提交前确认愿意看到更明确的信息；手动改写问题会清除该提示。
- Settings 的默认剧透级别和 Home 追问草稿的单次升级已进入运行时策略：`spoiler_level` 可随 App/debug 请求传入 `QueryPipeline`；未传入时使用 Settings 的 `轻提示 / 更明确 / 直接答案` 默认值；“更明确”和“直接答案”追问会分别映射到 `CLEAR` 和 `FULL`。
- Home 页已增加 Pending Question → RetroArch Hotkey 最小闭环：玩家可先把当前文字问题准备给下一次热键；下一条同 label、且原始 `question` 为空的 RetroArch AI Service 请求会消费该问题，并沿用同一条 `ResponseGenerator → QueryPipeline → GKP/AnswerPolicy` 返回到 RetroArch。
- Request log 已进入 Room v6：每条请求可持久化 `question` / `question_source`；App 内提问、debug ask、未来 RetroArch 原生问题和 pending hotkey 消费路径会分别记录来源，`/debug/latest-request`、Diagnostics 详情和 Home 最近上下文都能显示被回答的问题。
- Diagnostics 已增加来源计数与筛选：顶部可按 `全部 / RetroArch / Pending / App / Debug` 查看请求数并过滤日志，方便确认热键是否消费了 pending question。
- 正式 App 内语音输入和 Hotkey Voice Overlay 已切到 `k2-fsa/sherpa-onnx` 本地离线 ASR：APK 内置 `sherpa-onnx-streaming-paraformer-bilingual-zh-en` Paraformer int8 模型和 Android JNI/onnxruntime native libs，当前 Debug 包按 RG 476H/Apple Silicon AVD 收敛到 `arm64-v8a`，`ServiceLocator` 默认使用 `SherpaOnnxVoiceInputProvider`，不再依赖 Android `SpeechRecognizer` 主路径。语音只负责把本地转写文本填入 question，提交后仍复用 `ResponseGenerator → QueryPipeline → GKP/AnswerPolicy/LLM` 文本链路；朗读继续使用 Android `TextToSpeech` 只读成功答案第一句/短摘要；不实现端到端 speech-to-speech，也不让语音绕过低剧透/evidence gate。Paraformer 路径不使用 sherpa 原生 hotword；游戏术语修复由当前 GKP 的 `asr_variant` / `observed_asr` metadata 和 `GameTermNormalizer` 负责。
- M8.3 真机语音 QA 已通过最小闭环：RG 476H 上点击 Home “语音输入”，说“两个 2 怎么合并？”，本地 sherpa-onnx 转写为“两个二怎么合并”，直接点击“提问”后命中 `sample-2048` GKP 并返回正确答案。当时结论是样例路径不需要引入 ASR 文本规范化；后续真实游戏术语误召回已收敛为当前 GKP scoped ASR variant 问题。
- M8.4 真机语音回归已通过：RG 476H 上 5 个语音问题全部通过，覆盖 2048 冷启动/热启动、空识别提示、Relay Station 样例和再次 2048 回归。这个结论只说明当时 2048/样例路径不需要额外 ASR 规范化；后续真实游戏专名已改为用当前 GKP 的 `asr_variant` / `observed_asr` metadata 做 scoped 术语修复，不新增云 ASR 或多模式 UI。
- M9.1 已选择 MD 平台《Shining Force II / 光明力量2》作为首个真实游戏 GKP 试点，并落地一个小而可验证的 bundled pack。第一版只覆盖游戏身份、早期低剧透方向、战斗/复活/转职机制、特殊转职道具用法和少量转职道具摘要；所有文字为 RetroSprite 原创短句，引用官方/社区页面作事实来源，不复制攻略正文或手册长段落。
- M9.2 真机 label 前置验证已推进：RG 476H 的 RetroArch playlist 中《光明力量2》实际条目为 `label=光明力量2`、core 为 `Genesis Plus GX`；真实 AI Service 请求 label 为 `mega_drive__光明力量2`。GKP `0.1.2` 已补中英标题和 `Sega - Mega Drive - Genesis__光明力量2` / `mega_drive__光明力量2` label metadata，并增加 `md__光明力量2` 与 `mega_drive__光明力量2` pipeline 回归。新 Debug APK 已在 RG 476H 上通过 `/debug/ask` 运行时验证，转职问题命中 `sf2.promotion`、低剧透下一步问题命中 `sf2.early_route`。旧真机实验曾临时写入 `8080` 和热键；当前产品默认已改为 RetroArch 默认 AI Service URL `http://localhost:4404`，不再写 cfg。
- Settings 已将 RetroArch 相关功能改为纯“设置助手”：主路径是玩家在 RetroArch **Settings → Accessibility → AI Service** 中设置；RetroSprite 只显示推荐值并提供“一键复制 AI Service URL”，不再写入 `retroarch.cfg`，不提供高级 cfg 片段，不修改快捷键，也不申请 `MANAGE_EXTERNAL_STORAGE` 或其他存储权限。
- Home 语音状态已做一次 polish：首次加载 sherpa-onnx 模型时显示“首次加载本地 ASR 模型，可能需要几秒钟…”，空识别时显示“没有识别到问题，可再试一次或使用文字输入。”；这些提示只影响 App 内语音输入体验，不改变 GKP/evidence/低剧透/LLM 决策链路。
- M10.0 已开始把产品主路径从 Home pending question 转向 Hotkey Wake Voice Overlay：真实 RetroArch `POST /` 请求现在会发出 `RetroArchHotkeyEvent`，`EndpointController` 会把该事件交给 `AndroidHotkeyVoiceOverlayController`；在用户授予 Android “显示在其他应用上层”权限后，RetroSprite 可在 RetroArch 右上角显示一个不拦截触摸的彩色波形 cue，并在短时间后自动关闭。当前 M10.0 只验证“热键唤醒 UI”这一片，不做连续监听、不做 Accessibility/MediaProjection 截屏，也尚未把 sherpa-onnx ASR/TTS 自动接进 overlay。
- M10.1/M10.3 已接入 Hotkey Wake Voice Overlay 主闭环：Settings 增加“游戏内语音 Overlay”授权入口，可跳转 Android `ACTION_MANAGE_OVERLAY_PERMISSION` 并刷新状态；热键请求进入 overlay 后会启动一次 sherpa-onnx 本地 ASR，最终转写直接送入现有 `ResponseGenerator → QueryPipeline → GKP/AnswerPolicy`，结果以 `output_mode=hotkey_voice:text`、`question_source=hotkey_voice` 写入 request log，并通过 Android TTS 朗读短答案后关闭 overlay。原始空问题 RetroArch wake 请求改为静默响应，避免 Narrator Mode 抢先朗读“没有足够证据”。
- M10.3 真机首轮问题已定位并修复：Android 在 RetroSprite 退到 RetroArch 后会把普通 `RECORD_AUDIO` 视为 foreground-only AppOps，导致后台 overlay 录音被静音；`EndpointService` 现在在已有录音权限时以前台服务 `dataSync|microphone` 类型运行，并声明 `FOREGROUND_SERVICE_MICROPHONE`。同时修复两类 overlay 卡住风险：RetroArch 连续发送热键 POST 时忽略活动中的重复 wake；ASR 超时后取消等待 final transcript 的子协程，确保无识别结果也会释放 `AudioRecord` 并隐藏 overlay。RG 476H 后台 POST 烟测确认：无 `silencing record`/`Operation not started RECORD_AUDIO` 日志，28 秒后 RetroSprite overlay window 数为 0。
- M10.2 真机主交互已通过第一轮：用户在 RG 476H 的 RetroArch《光明力量2》中按 AI Service 热键，说“什么时候转职？”，已确认右上角波形出现、识别完成后走到 TTS 朗读。随后 `/debug/latest-request` 确认真实请求为 `label=mega_drive__光明力量2`、`output_mode=hotkey_voice:text`、`question_source=hotkey_voice`。本次 latest transcript 为“接受他几部这个角色”，因此 pipeline 走 `no_evidence`；`sf2.promotion` 证据命中还需要再用更清晰短句（如“角色什么时候转职”）复测一次后才能标记完成。
- 2026-05-25 ASR capture/commit 已按“语音活动结束后再提交”轻量修复：`SherpaEndpointCommitGate` 等待 sherpa endpoint、post-voice silence 和 stable partial，保留 final flush silence，并只对不完整疑问尾音增加小等待；`SherpaFinalTranscriptSelector` 只在 final 和 partial 兼容时保留更长 partial，不做 `是什 -> 是什么` 或 `玩什 -> 玩什么` 这类文本补全。Tingting 真机批测 `build/hotkey-voice-qa/20260525-113514` 中 Golden Sun、Chrono Trigger、Langrisser II 通过并到达 overlay `finished`；Shining Force II `气合之玉怎么用？` 被识别为 `气河之欲怎么用`，命中 `sf2.characters` 而非期望 `sf2.promotion`，剩余问题归类为 GKP ASR variant/source-ranking，而不是 UI/回答生命周期问题。
- Home 文字提问链路已有 Compose instrumentation 覆盖：输入问题、点击提问、显示答案/来源，并切换到 Diagnostics 验证 `APP` 日志标签。
- Home 页已区分输入来源：RetroArch 热键负责刷新上下文/label，玩家问题来自 App 内输入框，避免把 RetroArch 原始请求误认为自然语言提问。
- LLM/DeepSeek 调用链路已有本地可诊断预算：Home 处理中会显示耗时状态，回答结果和 Diagnostics 会展示 request duration、provider/model、max token、timeout、LLM latency、token in/out 和失败信息；Settings 已可配置 timeout / max token，并可执行一次不写 request log 的低成本配置自检。
- Home 最近回答已支持本地反馈：用户可标记“有帮助 / 这不对”，反馈通过 `request_key` 关联到 Room `request_logs` v3，并在 Diagnostics 中以标签和详情展示；反馈不会上传。
- Packs 页已从占位 UI 升级为真实 GKP 管理视图：显示启动导入状态、已安装 pack/game、版本、schema、知识行数、来源数、信任/许可摘要；导入失败会在页面中暴露。
- Packs 页已增加外部 GKP 只读预检入口：通过系统文件夹选择器读取候选包，执行 manifest/JSONL/schema/license lint，阻断 ROM/脚本/可执行文件，但不会安装或覆盖 Room 数据。
- Packs 页已支持外部 GKP 确认安装/覆盖：只有预检通过后才显示安装计划，明确 `game_id`、现有/新版本、知识行数变化、来源/Golden 数；用户点击确认后才会重新预检并写入 Room。
- Packs 页已支持本地 GKP 删除确认：删除前显示 `pack_id`、`game_id`、版本、知识行数、来源数和内置样例恢复风险；用户确认后才清除对应 game/knowledge 行。
- GKP 元数据已进入 Room v5：`games` 持久化 `pack_id`、`provenance`、`signature_status`、`signature_key_id`、`content_digest`、`enabled`、`disabled_at`；Packs 页可区分内置/外部来源、签名状态和启用状态，bundled importer 不会覆盖用户安装的外部包，也不会把已禁用的 bundled 包重新启用。
- GKP 禁用边界已可诊断：`RepositoryGameResolver` 发现匹配包已禁用时返回 `gkp_disabled` 身份但不提供 `gameId`，因此不会读取知识行或调用 LLM；Home 会显示“GKP 已禁用”，Diagnostics 会标记 `GKP_DISABLED` 并解释需要在 Packs 重新启用。
- 2026-05-21 产品方向复盘结论：RetroSprite 的主体验不需要把 LLM 作为必需依赖。当前 `本地 sherpa-onnx ASR → 文本问题 → GKP template/entity/FTS → AnswerPolicy → 短答案/TTS` 已经能作为 zero-LLM 主路径；LLM 只保留为可选的 evidence-grounded composer，用于多条证据综合、翻译、解释和表达润色。无 GKP、无 evidence、GKP 禁用、证据冲突或剧透超限时不应调用 LLM 裸答。
- 2026-05-21 TTS 方向复盘结论：当前 ASR 模型本身不能直接做 TTS；ASR 和 TTS 是两套模型/管线。当前 `AndroidSpeechOutputProvider` 仍走 Android `TextToSpeech`，但 `SpeechOutputProvider` 接口已经允许后续替换为本地神经 TTS。短期可用 sherpa-onnx TTS Engine APK 作为系统 TTS 引擎来验证离线效果；中期若要产品内置，需要新增 `SherpaOnnxTtsSpeechOutputProvider`、打包对应 TTS 模型/native/API，并用 `AudioTrack` 或等价播放器输出 PCM。
- 2026-05-21 GKP 内容复盘结论：zero-LLM 体验的上限主要由 GKP 决定。复盘时 Shining Force II GKP 已能回答“这是什么游戏”“战斗怎么玩”“什么时候转职”等问题，但对“这个游戏主要是玩什么？乐趣在哪里？”这类概览/动机型问题覆盖不足；后续真实游戏 GKP 必须加入 `核心玩法/乐趣/适合谁/怎么玩才有意思` 这类玩家自然问法，并配套 golden Q&A。
- M11.1-M11.3 已按上述结论落地第一轮：Shining Force II `0.2.1` 新增 `note.core-gameplay-loop`，覆盖“主要玩什么 / 乐趣在哪里 / 好玩在哪 / 核心玩法 / 适合什么玩家”等自然问法；`qa_goldens.jsonl` 增加 4 条概览型 golden，真实游戏 GKP 生产模板也新增 Core Gameplay And Fun Hooks lane。
- 2026-05-24 后续方向复盘结论：不要把每个游戏的首个支持版本做成完整攻略级 GKP。后续应转向 **GKP Lite + 玩家可选 BYOK LLM 增强层**：GKP Lite 负责可信游戏锚点、别名、核心玩法、常见机制、低剧透门和来源；LLM 由玩家自主启用和选择 provider/model，只做 query rewrite、跨语言映射、证据综合、翻译和表达润色。无 LLM 时 App 仍必须离线可用；无 GKP/无 evidence 时不允许 LLM 裸答具体攻略事实。

这意味着：**RetroArch Android 官方 APK 触发路径已经打通，Phase 0A 可以收口；Phase 1 的主要风险转移到 GKP 检索、低剧透策略和真实问答交互。**

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
