# 更新日志

本文件记录 RetroSprite 面向 GitHub Release 的项目级更新。各 GKP 包仍保留自己的 `changelog.md`，用于记录单个游戏知识包的内容变化。

## v0.1.0-preview.5 - 2026-05-27

### 新增

- 新增热键语音触发的当前画面翻译：玩家在 RetroArch AI Service 热键 overlay 中说“翻译一下”“读一下”“这是什么意思”或 `translate this`，会把当前暂停画面交给用户配置的 BYOK 画面翻译 API，并在游戏内显示中文译文。
- 新增菜单/装备/属性画面的结构化翻译输出：画面翻译 API 可返回 JSON 条目，RetroSprite 会整理成菜单、装备、物品、属性等分区，生成更适合掌机 HUD 扫读的中英文对照卡片。
- 新增 Settings -> 画面翻译 API 配置：支持 SiliconFlow、OpenRouter 和自建 OpenAI-compatible 模板，用户自行填写 Base URL、API Key、模型名和超时时间；推荐模型为 `Qwen/Qwen3-VL-8B-Instruct`。
- 新增画面翻译术语表与后处理：可按当前 RetroArch label 匹配内置游戏术语，优先修正菜单、魔法、物品、人名、技能名和 UI 短语。
- 新增画面翻译日志模式：`question_source=hotkey_screen_translation`，`output_mode=hotkey_screen_translation:text`，日志保留最终中文译文、耗时、provider/model 和截图字节数，不保存截图 Base64。
- Final Fantasy VI GKP Lite 升级到 `0.1.1`，新增 PlayStation `Final Fantasy Anthology - Final Fantasy VI` 的 RetroArch label，使 PS1/PSX/PlayStation label 可复用当前 FF6 Lite 知识包。

### 改进

- 普通热键语音问题继续走本地 ASR -> 当前游戏 GKP -> AnswerPolicy；只有明确画面翻译意图才会把截图发送给用户配置的 BYOK API。
- Overlay 新增翻译状态与长文本分页展示：短答仍可 TTS 朗读，画面翻译结果只显示中文，不朗读、不显示英文原文。
- Diagnostics 和协议文档同步记录 `hotkey_voice` 与 `hotkey_screen_translation` 的 question source / output mode 差异，方便排查热键是否进入正确路径。
- README、Android README、架构文档和协议文档已同步当前画面翻译能力、隐私边界和配置方式。

### 限制

- 当前 APK 仍是 preview/debug-signed 构建，不是 Play Store 或生产签名发行版。
- 画面翻译依赖用户自己的 BYOK API Key 和网络连接；RetroSprite 不内置任何 API Key，也不会在 API 不可用时让 LLM 猜测画面内容。
- 当前正式支持的内置游戏仍为 6 个，GKP Lite 覆盖范围不是完整攻略。

## v0.1.0-preview.4 - 2026-05-25

### 新增

- 中文 README 成为 GitHub 仓库默认首页，英文 README 通过顶部链接访问。
- 内置 6 个真实游戏 GKP Lite：Shining Force II、Golden Sun、Phantasy Star IV、Langrisser II、Chrono Trigger、Final Fantasy VI。
- ASR 打包切换到 `sherpa-onnx-streaming-paraformer-bilingual-zh-en`，ONNX 模型文件通过 Git LFS 管理。
- GKP schema、数据库迁移、检索/localization 行为、QA 脚本和 gkp-builder 工具更新到上一版发布候选状态。

### 限制

- APK 使用 Android debug 证书签名，仅适合 GitHub preview 分发和设备测试。
