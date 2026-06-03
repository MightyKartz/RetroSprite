# RetroSprite

中文（默认） | [English](./README.en.md)

RetroSprite 是一个面向 **RetroArch** 的 Android 游戏内 AI 问答与画面翻译伙伴。
玩家在 RetroArch 中按下 AI Service 热键后，可以直接用语音询问当前游戏里的问题，
或说“翻译”让 RetroSprite 读取当前暂停画面。普通问答会优先基于本地
Game Knowledge Pack（GKP）给出短、准、低剧透、可追溯来源的回答；画面翻译只在
玩家明确触发时调用用户自己配置的 BYOK API。

```text
RetroArch AI Service 热键
  -> RetroSprite 本地 endpoint
  -> 游戏内短时语音 overlay
  -> 本地 sherpa-onnx Paraformer ASR
  -> 普通问题：当前游戏 GKP / 本地检索 / AnswerPolicy
  -> 翻译命令：当前暂停画面 BYOK API 识别 / 翻译
  -> 游戏内短答案或完整画面翻译
```

RetroSprite 的默认路线是 **本地优先、证据优先、低剧透优先**。外部 LLM 是可选的
BYOK 增强层，不是默认事实来源；没有本地证据、知识包被禁用、证据超过当前剧透级别时，
RetroSprite 不会让 LLM 裸答。

## 当前版本状态

RetroSprite 当前处在 M17.1 / M18 发布候选硬化阶段：Hotkey Voice Overlay、本地 GKP
问答、BYOK 当前画面翻译、短答 TTS、Diagnostics 和 6 个内置真实游戏包已经形成可运行闭环。
最新版本重点收敛在真机可复现质量：热键语音请求可通过调试注入复现，ASR 会记录音频采样、
读帧错误和峰值音量，Diagnostics 会直接解释 ASR / GKP / 截图 / BYOK API / 权限 / 超时类失败，
GKP ASR 变体和 golden regression 用真实 RG476H 热键语音样本持续回归。

这仍是 preview 阶段，不是“支持所有游戏”的通用攻略机器人。当前 APK 面向测试、演示和早期反馈；
正式使用前请先确认你的游戏属于下方支持列表。

## 当前支持的游戏

RetroSprite 目前只内置支持 **6 个真实游戏**：

| 游戏 | 平台 | GKP 包 |
| --- | --- | --- |
| **Shining Force II / 光明力量2** | Sega Mega Drive / Genesis | `community.shining-force-ii-md` |
| **Golden Sun / 黄金太阳** | Game Boy Advance | `community.golden-sun-gba-zh` |
| **Phantasy Star IV / 梦幻之星 IV** | Sega Mega Drive / Genesis | `community.phantasy-star-iv-md-zh` |
| **Langrisser II / 梦幻模拟战 II** | Sega Mega Drive / Genesis | `community.langrisser-ii-md-zh` |
| **Chrono Trigger / 时空之轮** | Super Nintendo / Super Famicom | `community.chrono-trigger-snes-zh` |
| **Final Fantasy VI / 最终幻想 VI** | Super Nintendo / Super Famicom | `community.final-fantasy-vi-snes-zh` |

这是当前完整的正式游戏支持范围。此前的 `sample-2048` 和 `sample-relay-station`
演示包已从 bundled assets 移除；开发和冒烟测试也应使用真实 GKP。

## 适合谁使用

- 你在 Android 设备上使用 RetroArch，并愿意通过 AI Service 热键触发问答。
- 你正在玩上方 6 个支持游戏之一。
- 你希望得到短答案、轻提示、低剧透建议，而不是整段攻略搬运。
- 你希望默认离线、本地优先，不把游戏问题直接交给云端模型猜。
- 你愿意接受 preview 阶段的限制，并反馈识别错误、无答案问题和不准确来源。

## 主要功能

- **RetroArch AI Service 集成**：使用 RetroArch 官方 AI Service 热键作为游戏内触发入口。
- **本地 HTTP endpoint**：Android 前台服务默认监听 `127.0.0.1:4404`。
- **游戏内语音 overlay**：按热键后显示短时语音波形，收集一个问题；调试请求可注入问题复现完整热键链路。
- **本地 ASR**：使用 sherpa-onnx Paraformer 模型进行本地中文/英文语音识别，并记录采样数、读帧数、错误数和音量诊断。
- **按需画面翻译**：说“翻译”“翻译一下”“读一下”“这是什么意思”时，把当前暂停画面发送到用户配置的 BYOK 画面翻译 API，并显示完整中文译文；菜单/装备/属性画面会优先整理成可扫读的中英文对照卡片。
- **当前游戏 GKP 检索**：按 RetroArch label、游戏标题、平台和启用状态解析当前知识包。
- **ASR 变体归一化**：把真机误识别样本沉淀为游戏内 observed-asr 别名和 golden regression，减少“听到了但找不到证据”的失败。
- **低剧透回答策略**：默认轻提示，可通过追问升级为更明确或直接答案。
- **来源与诊断**：回答保留 source id，Diagnostics 可查看 pipeline stage、LLM 状态、耗时、ASR 诊断和失败原因。
- **可选 BYOK LLM**：支持 OpenAI-compatible / DeepSeek 配置，但只作为有证据时的表达和综合增强。
- **Packs 管理**：内置 GKP 自动导入，外部 GKP 支持预检、安装、覆盖、启用/禁用和删除确认。
- **M18 质量门禁**：新增离线 QA/report 脚本，把真实问题、ASR 误识别、无证据和补丁建议接入 backlog、golden 和发布审计。

## 安装

### 1. 准备环境

- Android 8.0+（API 26+）设备或模拟器。
- 已安装 RetroArch Android，并能正常载入游戏。
- 如果要走完整热键体验，需要 RetroArch 版本带有 **AI Service** 设置。
- 推荐使用手柄、掌机实体键或外接键盘绑定 AI Service 热键。

### 2. 下载 APK

从 GitHub Releases 下载最新的 RetroSprite preview APK：

[https://github.com/MightyKartz/RetroSprite/releases](https://github.com/MightyKartz/RetroSprite/releases)

当前 preview APK 是 debug-signed，用于测试和预览分发，不是 Play Store 或生产签名版本。

### 3. 安装 APK

在 Android 设备上安装下载的 APK：

```bash
adb install -r app-debug.apk
```

也可以直接在设备上打开 APK 安装。若系统提示“安装未知来源应用”，请按 Android 系统提示为当前文件管理器或浏览器授予安装权限。

### 4. 首次启动权限

首次启动 RetroSprite 后，建议按提示授予：

- **麦克风权限**：用于热键后的本地语音提问。
- **显示在其他应用上层**：用于在 RetroArch 游戏画面上显示短时语音波形和回答 HUD。
- **通知/前台服务权限**：用于保持本地 endpoint 运行。

RetroSprite 不需要修改 RetroArch core，不需要广泛存储权限，也不会自动改写 `retroarch.cfg`。

## RetroArch 配置

在 RetroArch Android 中打开：

```text
Settings -> Accessibility -> AI Service
```

推荐配置：

| RetroArch 字段 | 推荐值 |
| --- | --- |
| AI Service | `ON` |
| AI Service URL | `http://localhost:4404` |
| AI Service Output | `Narrator Mode` / 旁白模式 |
| Pause During Translation | `ON` |

然后进入：

```text
Settings -> Input -> Hotkeys -> AI Service
```

绑定一个你在游戏中容易按到的热键。掌机/手柄上推荐使用不容易误触的组合键，例如
`Select + Start` 或你自己熟悉的快捷键。

## 快速使用

1. 打开 RetroSprite，确认 Home 页显示本地 endpoint 正在运行。
2. 打开 RetroArch，载入当前支持的 6 个游戏之一。
3. 按下你在 RetroArch 中绑定的 AI Service 热键。
4. 看到 RetroSprite 语音波形后，说一个短问题。
5. RetroSprite 会用本地 ASR 识别问题，并从当前游戏 GKP 中检索证据。
6. 如果命中本地证据，会在游戏内显示短回答并通过 Android TTS 朗读。
7. 如果没有可靠证据，会说明暂时不能可靠回答，而不是猜测。
8. 如果说“翻译”“翻译一下”“读一下”“这是什么意思”，会改走当前画面翻译；翻译结果只显示中文，不朗读、不显示英文原文。

适合的提问方式：

- `修伊怎么用？`
- `角色什么时候转职？`
- `黄金太阳刚开始练谁？`
- `梦幻模拟战 II 转职怎么选？`
- `克拉肯怎么过？`
- `不要剧透，下一步去哪？`
- `直接告诉我具体位置。`
- `翻译。`
- `翻译一下。`
- `读一下这段。`
- `这是什么意思？`

建议一次只问一个短问题。当前版本更擅长角色、道具、路线、战斗、转职、地点和低剧透下一步提示。

## App 内页面说明

- **Home**：查看 endpoint 状态，发起文字问题，准备/调试热键问题，查看最近上下文。
- **Packs**：查看已导入 GKP、启用/禁用知识包、预检和安装外部 GKP。
- **Settings**：查看 RetroArch 设置助手、端口、overlay 权限、默认剧透级别、LLM provider 配置和画面翻译 API 配置。
- **Diagnostics**：查看最新请求、pipeline stage、source ids、LLM 状态、延迟、反馈和错误信息。

完整更新记录见 [CHANGELOG.md](./CHANGELOG.md)。

## GKP 是什么

GKP（Game Knowledge Pack）是 RetroSprite 的本地游戏知识包。它不是 ROM，也不是商业攻略书复制；
它是一组可检索、可测试、可标注来源和剧透等级的结构化知识文件。

一个 GKP 通常包含：

```text
manifest.json
aliases.json
knowledge/*.jsonl
sources/citations.jsonl
sources/licenses.md
spoiler_graph.json
qa_goldens.jsonl
changelog.md
```

RetroSprite 会按当前游戏解析 GKP，只检索对应游戏的本地知识，并根据剧透级别选择回答内容。

## LLM 与隐私

默认情况下，RetroSprite 的回答路径不需要外部 LLM：

- 本地 ASR 识别语音。
- 本地 GKP 提供事实和来源。
- 本地检索和 AnswerPolicy 决定是否回答、如何低剧透回答。
- 没有 evidence 时不调用 LLM 裸答。

如果你在 Settings 中配置 BYOK LLM provider，LLM 只用于有本地证据时的综合、翻译、改写或表达润色。
请不要把它理解为实时联网搜索或万能攻略库。

画面翻译不再依赖 Google ML Kit 本地模型。正式版本采用 BYOK API-only：用户在 Settings 中选择 SiliconFlow / OpenRouter / 自建 OpenAI-compatible API 模板，并填写自己的 Base URL、API Key 和模型名。推荐使用 `Qwen/Qwen3-VL-8B-Instruct`；RetroSprite 不内置任何 API Key。

普通 GKP 问答不会把 RetroArch 截图发给云端；只有玩家明确说出画面翻译意图时，当前截图才会发送到用户配置的画面翻译 API。翻译日志只保存最终中文结果、耗时、provider/model 和截图字节数，不保存截图 Base64。

RetroSprite 默认 endpoint 绑定在 `127.0.0.1`，也就是本机 loopback。请求日志保存在本机 Room 数据库中，
可通过 Diagnostics 查看和清理。

## 常见问题

### RetroArch 按热键后没有反应

- 确认 RetroSprite Home 页 endpoint 正在运行。
- 确认 RetroArch 的 AI Service 已开启。
- 确认 AI Service URL 是 `http://localhost:4404`。
- 确认热键已绑定，并且当前设备/手柄能触发该热键。
- 在开发机上可用 `adb forward tcp:18080 tcp:4404` 后访问 `/health` 验证 endpoint。

### 有波形但没有答案

- 确认已授予麦克风权限。
- 尽量使用短句提问。
- 在 RetroArch 中打开 `Pause During Translation`，减少游戏 BGM 干扰。
- 到 Diagnostics 查看 `raw_question`、`normalized_question`、`pipeline_stage` 和 `source_ids`。

### 回答说没有可靠证据

- 当前游戏可能不在 6 个支持列表中。
- 当前问题可能超出 GKP Lite 覆盖范围。
- 当前 GKP 可能被禁用。
- 问题可能需要更明确的角色、道具、地点、章节或目标。

### 画面翻译没有结果

- 确认 Settings -> 画面翻译 API 已填写 Base URL、API Key、模型名和超时时间。
- 推荐先使用 SiliconFlow / OpenRouter 模板和 `Qwen/Qwen3-VL-8B-Instruct`。
- 确认 RetroArch AI Service 请求带有截图，并已开启 `Pause During Translation`。
- 画面翻译需要网络访问用户配置的 BYOK API；离线时不会调用云端，也不会回退到猜测。

### 支持其他游戏吗

当前正式支持范围只有 README 中列出的 6 个游戏。其它游戏可能会被 RetroArch 触发到 endpoint，
但不会有可靠 GKP 证据，因此不应期待稳定回答。

### APK 为什么要 clean build

改过 ASR 模型或大 asset 后，Gradle/ZIP 增量打包可能留下异常旧产物空洞，导致 APK 体积虚高。
发布或测试前建议使用 clean build：

```bash
cd retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:clean :app:testDebugUnitTest :app:assembleDebug
```

## 开发者构建

环境要求：

- Android Studio 或本地 Android SDK。
- JDK 17，推荐使用 Android Studio 自带 JBR。
- Android API 26+ 的设备或模拟器。

构建 Debug APK：

```bash
cd retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:clean :app:assembleDebug
```

运行 JVM 单元测试：

```bash
cd retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest
```

运行发布前推荐检查：

```bash
cd retrosprite-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:clean :app:testDebugUnitTest :app:assembleDebug
```

安装到连接设备：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Host 侧 endpoint 验证：

```bash
adb forward tcp:18080 tcp:4404
curl -fsS http://127.0.0.1:18080/health
```

Debug 问答示例：

```bash
curl -fsS -X POST 'http://127.0.0.1:18080/debug/ask?output=text' \
  -H 'Content-Type: application/json' \
  --data '{"label":"mega_drive__光明力量2","question":"角色什么时候转职？","spoiler_level":"light","state":{}}'
```

完整 AVD/真机 smoke：

```bash
cd retrosprite-android
./scripts/android_avd_smoke.sh
```

## 仓库结构

```text
.
├── README.md                       # 默认中文首页
├── README.en.md                    # 英文版 README
├── retrosprite-android/            # Android 主应用
│   ├── app/src/main/kotlin/        # Kotlin 源码
│   ├── app/src/main/assets/        # 内置 GKP + 本地 ASR 模型资源
│   ├── app/src/test/               # JVM 单元测试
│   ├── app/src/androidTest/        # Android / Compose 集成测试
│   ├── docs/                       # 协议、GKP、设置、测试和计划文档
│   └── scripts/                    # endpoint、AVD、真机 smoke 脚本
├── tools/gkp-builder/              # GKP Lite 生成工具与模板
├── RetroSprite_Development_Plan.md
├── README_AI_SERVICE_RESEARCH.md
└── RetroArch_AI_Service_Protocol_Specification.txt
```

## 关键文档

- [Android App 详细说明](./retrosprite-android/README.md)
- [RetroArch AI Service 配置指南](./retrosprite-android/docs/RETROARCH_SETUP.md)
- [架构与产品层级](./retrosprite-android/docs/ARCHITECTURE_AND_PRODUCT_TIERS.md)
- [GKP v0 Schema](./retrosprite-android/docs/GKP_V0_SCHEMA.md)
- [GKP Lite + 可选 LLM 方向](./retrosprite-android/docs/GKP_LITE_OPTIONAL_LLM_DIRECTION.md)
- [真实游戏 GKP Lite 生产模板](./retrosprite-android/docs/REAL_GAME_GKP_EXPANSION_TEMPLATE.md)
- [RetroArch AI Service 协议参考](./retrosprite-android/docs/PROTOCOL_REFERENCE.md)
- [测试覆盖说明](./retrosprite-android/docs/TEST_COVERAGE.md)

## 设计边界

RetroSprite 明确避免这些路径：

- 不修改 RetroArch core。
- 不用 MediaProjection 做连续屏幕捕获。
- 不把 Accessibility Service 作为主集成路径。
- 不申请广泛存储权限，也不自动改写 `retroarch.cfg`。
- GKP 不包含 ROM、商业攻略书原文、可执行代码或长篇受版权保护文本。
- 没有本地证据时，不让 LLM 裸答。
- 不在问答产品可靠之前做 AI 自动游玩、存档/读档、自动输入控制。

## 已知限制

- 当前只支持 6 个内置真实游戏。
- 5 个 Retro JRPG/SRPG 包是 GKP Lite，不是完整攻略包。
- 语音识别会受环境噪声、BGM、发音、设备麦克风影响。
- 热键语音主路径需要 RetroArch AI Service 正确配置。
- 当前 APK 是 preview/debug-signed 构建，不是生产签名发行版。

## 许可证与第三方声明

RetroSprite 项目自身许可证暂未确定。

RetroSprite 使用并可能随 APK 打包以下第三方开源 ASR 组件：

- `sherpa-onnx`，由 k2-fsa 提供，用于本地离线语音识别。
  来源：<https://github.com/k2-fsa/sherpa-onnx>
  许可证：Apache License 2.0。
- `csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en` ONNX Paraformer int8 模型文件，
  用于本地中英 ASR。
  来源：<https://huggingface.co/csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en>
  Release 包：<https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-paraformer-bilingual-zh-en.tar.bz2>
  许可证：Apache License 2.0。

Apache-2.0 允许商业使用、修改和再分发，但需要保留相应许可证和归属声明。

## English

The English README is available here: [README.en.md](./README.en.md).
