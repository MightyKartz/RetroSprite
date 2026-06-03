# 更新日志

本文件记录 RetroSprite 面向 GitHub Release 的项目级更新。各 GKP 包仍保留自己的 `changelog.md`，用于记录单个游戏知识包的内容变化。

## Unreleased

### 新增

- 新增正式 APK release signing 配置：Gradle 可读取本地 `keystore.properties` 或 `RETROSPRITE_RELEASE_*` 环境变量，为 release build 使用项目发布证书签名。
- 新增 `generate_release_keystore.sh` 与 `build_release_apk.sh`：支持生成本地 keystore、clean release build、单元测试、`apksigner verify`、debug 证书拦截和 SHA-256 校验文件输出。
- 新增正式 APK 签名与 GitHub Release 发布指南，明确普通用户长期下载应使用 release-signed APK，preview/debug APK 只用于测试。

### 改进

- README 中文默认首页、英文 README 和 Android README 补充正式签名包安装说明、发布脚本入口和密钥安全边界。
- `.gitignore` 明确排除 Android release keystore、`keystore.properties` 和本地 release 密钥目录，降低误提交风险。

## v0.1.0-preview.8 - 2026-06-03

### 新增

- 新增热键语音调试注入路径：`hotkey_voice_debug*` 请求可以携带 `question`，通过同一套 overlay、日志和回答链路复现真机热键问题。
- 新增 ASR 音频诊断指标：记录 sample count、AudioRecord read count、read error count、peak amplitude 和 last-frame amplitude，帮助定位“有波形但没转写 / 音量太低 / 读帧异常”。
- 新增 Diagnostics 失败归因：对 ASR、GKP no-evidence、GKP disabled、截图缺失、BYOK API 配置、API 错误、权限和超时给出直接诊断提示。
- 新增 M18 质量闭环与发布门禁脚本：覆盖 hotkey voice matrix、GKP patch proposal / regression gate、M18 status / handoff / audit、screen translation matrix 和 release candidate audit。
- 新增 M17 preview release checklist 和 M18 QA 反馈文档，用于把真实设备失败样本转成 backlog、patch proposal、golden regression 和可复跑证据。

### 改进

- GKP ASR 变体归一化更稳：支持 observed-asr 整句误识别的噪声前缀清理、截断尾部补全和更多同音映射。
- 6 个内置真实游戏包补充 RG476H 热键语音样本对应的 aliases 和 qa_goldens，重点覆盖 FF6 魔石系统、光明力量2 气合之玉、黄金太阳伊凡、梦幻之星 IV 技巧/技能、时空之轮玛尔等真实误识别。
- Request log 能把 `answerType=no_evidence` 归类到 no-evidence pipeline stage，Diagnostics 过滤和 failure explanation 更准确。
- README 中文默认首页、英文 README、Android README、架构文档和测试覆盖文档同步 M17.1/M18 最新功能、发布状态和 QA 门禁口径。

### 限制

- 当前 APK 仍是 Android debug 证书签名的 GitHub preview 包，不是 Play Store 或生产签名发行版。
- M18 质量闭环冻结新增游戏和模型扩张，当前正式支持范围仍为 6 个内置真实游戏。
- 画面翻译仍依赖用户自己配置的 BYOK API；普通 GKP 问答不会上传截图。

## v0.1.0-preview.7 - 2026-05-29

### 新增

- 屏幕翻译增加二次修复：当视觉模型只返回英文 OCR 原文时，RetroSprite 会要求模型重新输出简体中文。
- 结构化翻译解析支持 `dialogue` / `text` 等文本模式，对对白页面只显示中文译文。
- 热键语音支持直接说“翻译”，不会被普通短问题长度门槛拦截。

### 改进

- 屏幕翻译结果页停留时间延长到 10 秒，方便在掌机屏幕上阅读多页翻译。
- Settings 文案补充“翻译”作为可用画面翻译唤起词。

## v0.1.0-preview.6 - 2026-05-28

### 新增

- Sherpa ASR 最终文本选择增加 partial/final 兜底，减少 final transcript 截断导致的短问题丢失。
- 本地 GKP 检索增加低剧透实体兜底，减少“知识包里有但没有 evidence”的情况。

### 改进

- 对“怎么获”这类不完整语音片段给出更明确的重说提示，而不是勉强生成答案。
- Packs 和 Settings 页面文案、布局、导入预检和配置提示更清晰。

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
