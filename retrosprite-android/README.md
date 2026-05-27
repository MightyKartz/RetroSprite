# RetroSprite Android · 游戏内 AI 问答伙伴

> 一句话定位：玩家在 RetroArch 中按下热键，向 RetroSprite 发起一个简短、准确、低剧透的游戏内问题，得到基于本地知识包的可信答案。
>
> *RetroSprite is an in-game Q&A companion for RetroArch — local-first, low-spoiler, evidence-grounded.*
>
> 仓库首页版 README 见 [../README.md](../README.md)；本文保留 Android App 的详细构建、联调和目录说明。

**当前阶段：M10/M11 · Hotkey Voice Overlay + Zero-LLM GKP**

**后续方向：GKP Lite + Optional BYOK LLM。**每个游戏的首个支持版本不再要求完整攻略级 GKP，而是先做轻量、可信、可测试的 GKP Lite；外部 LLM 继续由玩家自主选择是否启用、使用什么 provider/model，只作为证据综合、跨语言映射和表达增强层。没有 LLM 时 RetroSprite 仍应离线可用；没有本地证据时不允许 LLM 裸答具体攻略事实。

RetroArch AI Service → Android 本地 endpoint → 热键唤醒 RetroSprite 游戏内语音 overlay → 本地 ASR → GKP/AnswerPolicy → 短答 TTS 这条主路径已经接通并进入体验打磨。Home 页文字提问、pending hotkey 问题和 debug curl 仍保留为设置验证与开发 fallback；玩家主体验应是在 RetroArch 中按热键呼出科技感语音波形，不需要频繁回到 App 里操作。

当前 Debug APK 默认打包 `sherpa-onnx-streaming-paraformer-bilingual-zh-en`
int8 三件套（`encoder.int8.onnx`、`decoder.int8.onnx`、`tokens.txt`），
通过 sherpa-onnx `OnlineParaformerModelConfig` 做本地 streaming ASR。该路径不启用
sherpa 原生热词；游戏专属名词靠当前 GKP 的别名/ASR 变体和
`GameTermNormalizer` 做游戏域内修复，不能退化成跨游戏全局替换。Paraformer 资产约
226 MB；2026-05-24 本地 `assembleDebug` 产物为 276 MB，APK 内未再打包旧
14M Zipformer 资产。真机可用性仍以 RG476H 安装、启动、录音和连续问答测试为准。

**当前真实支持游戏：仅 6 个。**RetroSprite 目前内置支持：
**Shining Force II / 光明力量2**（`community.shining-force-ii-md`）、
**Golden Sun / 黄金太阳**（`community.golden-sun-gba-zh`）、
**Phantasy Star IV / 梦幻之星 IV**（`community.phantasy-star-iv-md-zh`）、
**Langrisser II / 梦幻模拟战 II**（`community.langrisser-ii-md-zh`）、
**Chrono Trigger / 时空之轮**（`community.chrono-trigger-snes-zh`），以及
**Final Fantasy VI / 最终幻想 VI**（`community.final-fantasy-vi-snes-zh`）。这是当前完整正式游戏支持范围；`sample-2048` 和 `sample-relay-station` 已从 bundled assets 移除，开发和冒烟测试应使用真实游戏 GKP。

---

## 项目核心理念 · Core Principles

1. **RetroArch AI Service 集成优先** — 不做悬浮窗、不依赖 Accessibility Service，按 RetroArch 官方协议接入。
2. **游戏内问答伙伴** — 不是通用 ChatBot，也不是被动攻略提示器；玩家提问，RetroSprite 在当前游戏上下文中作答。
3. **本地优先 · Local-first** — 默认离线可用，知识来自本地 Game Knowledge Pack，不把实时网络搜索作为默认知识来源。
4. **低剧透默认 · Low-spoiler by default** — 答案分级：先给「不剧透」的提示，再由玩家显式升级为「直接答案」。
5. **证据驱动 · Evidence-grounded** — 引用本地知识来源；不把 LLM 生成结果当作未经验证的事实。
6. **BYOK · Bring Your Own Key** — 任何外部 LLM 调用都使用用户自带凭据，遵循成本可控原则。

---

## 快速开始 · Quick Start

> 完整指南见 [docs/RETROARCH_SETUP.md](./docs/RETROARCH_SETUP.md)。

### 1. 编译 Debug APK

```bash
cd retrosprite-android
./gradlew wrapper      # 首次拉取 wrapper（如尚未生成）
./gradlew assembleDebug
```

构建产物：`app/build/outputs/apk/debug/app-debug.apk`

### 2. 安装到 Android 设备

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

启动 App，进入 Home 屏，确认 endpoint 状态为「运行中」（默认 `http://localhost:4404`，与 RetroArch AI Service 默认地址一致）。
Home 页也可以直接提问验证本地 GKP 问答链路；当前开发测试应使用真实游戏 label，例如 `md__Shining Force II`、`mega_drive__光明力量2` 或 `gba__黄金太阳`。载入游戏并触发 RetroArch AI Service 后，提问入口会显示最近 RetroArch 上下文并自动采用对应 label，手动覆盖后也可恢复。玩家可以在 App 内输入问题并直接提交，也可以点击“准备给下次热键”，让下一条同 label、原始 `question` 为空的 RetroArch AI Service 请求消费该问题并把答案返回给 RetroArch；被消费的问题会持久化到 request log，并显示在 `/debug/latest-request`、Diagnostics 详情和 Home 最近上下文。已知真实游戏 label 会显示快捷问题草稿，点选只填入问题框，提交或准备热键仍会经过本地 GKP、低剧透策略和 LLM gate。最近上下文行动条可一键使用当前 label，并复制可在开发机运行的 `/debug/ask` curl。最近 App 内问答会显示在 Home 的本机会话托盘中，点选记录可恢复对应 label、问题和回答；也可生成“更明确 / 直接答案 / 换个问法”追问草稿，草稿只填入输入框；选择“直接答案”会显示剧透级别提升提示，并把本次提交或 pending hotkey 的策略级别提升到直接答案。未选择追问升级时，Settings 中的默认剧透级别会进入本地检索和 AnswerPolicy。回答遇到无 evidence、GKP 禁用、LLM 失败或请求错误时，Home 会给出下一步恢复动作，并可直接跳转到 Packs、Settings 或 Diagnostics。
真实 LLM/DeepSeek 调用会在 Home 和 Diagnostics 中显示耗时、provider/model、max token、timeout、LLM latency 和 token 用量，方便定位慢响应或配置失败；Settings 的 LLM 配置区可调整 timeout 与 max token，并可发起一次不写入玩家日志的配置自检。
画面翻译采用 BYOK API-only 配置：Settings 提供 SiliconFlow、OpenRouter 和自建 OpenAI-compatible 模板，用户自己填写 Base URL、API Key 和模型名；推荐使用 `Qwen/Qwen3-VL-8B-Instruct`，App 不内置任何 API Key。
回答结果可在 Home 标记「有帮助 / 这不对」；反馈只写入本机 Room `request_logs`，Diagnostics 会把反馈和来源、pipeline stage、LLM 状态放在同一条记录里。
Packs 页会显示当前已导入的内置 GKP、版本、schema、知识行数、来源数、信任/许可摘要、来源类型、签名状态和启用状态，以及最近一次启动导入状态；也可以对外部 GKP 文件夹做预检，检查 manifest / JSONL / schema / license / signature 和危险文件类型。预检通过后才会显示安装/覆盖计划，明确 `game_id`、版本、知识行数变化、来源类型和内容摘要，用户确认后才写入本机 Room 数据。已安装包支持禁用/启用和删除前确认；禁用会保留知识行但不参与游戏解析、检索或 LLM 综合，Home 会显示“GKP 已禁用”，Diagnostics 会标记 `GKP_DISABLED`。删除确认卡会显示目标 `pack_id`、`game_id`、版本和知识行数。内置真实包删除后下次启动仍可能被自动恢复，但禁用状态会被保留，外部同 `game_id` 包不会被 bundled importer 覆盖。

### 3. 配置 RetroArch

按照 [docs/RETROARCH_SETUP.md](./docs/RETROARCH_SETUP.md) 的步骤填写 AI Service URL、绑定热键，然后载入游戏按下热键即可在 Diagnostics 屏看到请求记录。

### 4. 自动化冒烟

```bash
./scripts/test_endpoint.sh
# 跨设备从开发机访问真机：先执行
adb forward tcp:4404 tcp:4404

# AVD/真机一条命令 smoke：自动检查/安装 Debug APK、启动 App、
# 跑 endpoint smoke、真实 GKP debug 问答和 latest-request 回读
./scripts/android_avd_smoke.sh
```

---

## 文档导航 · Docs

| 文档 | 内容 |
| ---- | ---- |
| [docs/RETROARCH_SETUP.md](./docs/RETROARCH_SETUP.md) | RetroArch AI Service 完整配置步骤 + 故障排查 |
| [docs/PHASE0_VERIFICATION.md](./docs/PHASE0_VERIFICATION.md) | Phase 0 验收清单（自动化 + 手动） |
| [docs/PROTOCOL_REFERENCE.md](./docs/PROTOCOL_REFERENCE.md) | RetroArch AI Service 请求 / 响应字段速查 |
| [docs/ARCHITECTURE_AND_PRODUCT_TIERS.md](./docs/ARCHITECTURE_AND_PRODUCT_TIERS.md) | 主程序、GKP builder、GKP Lite、expanded/deep 覆盖、Pro 商业层和可选 LLM 的当前口径 |
| [docs/GKP_V0_SCHEMA.md](./docs/GKP_V0_SCHEMA.md) | GKP v0 schema、pack 结构与 lint 规则 |
| [docs/GKP_LITE_OPTIONAL_LLM_DIRECTION.md](./docs/GKP_LITE_OPTIONAL_LLM_DIRECTION.md) | GKP Lite + 玩家可选 LLM 的后续产品与架构方向 |
| [docs/REAL_GAME_GKP_EXPANSION_TEMPLATE.md](./docs/REAL_GAME_GKP_EXPANSION_TEMPLATE.md) | 真实游戏 GKP Lite 生产模板、覆盖层级与验收标准 |
| [docs/NEXT_IMPLEMENTATION_PLAN.md](./docs/NEXT_IMPLEMENTATION_PLAN.md) | 下一阶段实施计划、任务板与验证门槛 |
| [docs/RETROARCH_ANDROID_AI_SERVICE_FINDINGS.md](./docs/RETROARCH_ANDROID_AI_SERVICE_FINDINGS.md) | RetroArch Android 官方 APK 首次联调记录 |
| [scripts/test_endpoint.sh](./scripts/test_endpoint.sh) | 一键 curl 冒烟脚本 |
| [scripts/android_avd_smoke.sh](./scripts/android_avd_smoke.sh) | AVD/真机上的 RetroSprite endpoint 冒烟脚本，覆盖真实内置 GKP |
| [scripts/sample_payload.json](./scripts/sample_payload.json) | 标准请求体样本 |
| [../RetroSprite_Development_Plan.md](../RetroSprite_Development_Plan.md) | 项目整体规划 |
| [../.qoder/skills/retrosprite-dev/SKILL.md](../.qoder/skills/retrosprite-dev/SKILL.md) | 项目开发约束与方向 |

---

## 技术栈 · Tech Stack

- **语言**：Kotlin（JVM 17）
- **UI**：Jetpack Compose + Material 3
- **架构**：单 Activity + Compose Navigation，分层 `ui / domain / data / endpoint / llm`
- **持久化**：Room + SQLite（FTS5），DAO 与 entity 分离
- **HTTP Server**：内嵌轻量 HTTP 服务（前台服务托管），默认监听 `127.0.0.1:4404`
- **依赖注入**：Hilt（Phase 1 起逐步引入）
- **测试**：JUnit4 + Robolectric（unit）、AndroidX Test + Compose UI Test（instrumented）

## 项目结构 · Layout

```
retrosprite-android/
├── app/                    # Android 应用模块
│   └── src/
│       ├── main/kotlin/com/retrosprite/app/
│       │   ├── ui/         # Compose 屏幕 / 组件 / 主题 / ViewModel
│       │   ├── domain/     # QueryPipeline / 检索 / 应答策略
│       │   ├── data/       # Room 数据库 / Repository / DomainModels
│       │   ├── endpoint/   # RetroArch AI Service 本地 HTTP endpoint
│       │   └── llm/        # LLM Adapter 抽象 + Mock + OpenAI 兼容实现
│       ├── test/           # JVM 单元测试
│       └── androidTest/    # 设备/模拟器集成测试
├── docs/                   # Phase 0 文档
└── scripts/                # 测试脚本与样本 payload
```

---

## 非协商护栏 · Non-Negotiable Guardrails

> 完整列表见 [SKILL.md](../.qoder/skills/retrosprite-dev/SKILL.md)。

- 不以 `MediaProjection` / Accessibility Service / 连续后台悬浮捕获作为主集成路径；当前只使用热键触发的短时语音 overlay。
- 不要求修改 RetroArch 内核或核心。
- 不把 LLM 输出当作未经核实的事实来源。
- 不以实时联网搜索作为默认知识来源。
- 不在 GKP 中包含 ROM、商业攻略书原文或长篇受版权保护文本。
- 不让 GKP 包含可执行代码。
- 不在 Q&A 主循环稳定前做 Live2D / 皮肤 / 萌宠动画。
- 不在 Q&A 产品可靠之前做 AI 控制存档/读档/输入自动化。

---

## License

TBD（暂未确定，将在 Phase 1 末尾正式选择）。
