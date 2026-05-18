# RetroSprite Android · 游戏内 AI 问答伙伴

> 一句话定位：玩家在 RetroArch 中按下热键，向 RetroSprite 发起一个简短、准确、低剧透的游戏内问题，得到基于本地知识包的可信答案。
>
> *RetroSprite is an in-game Q&A companion for RetroArch — local-first, low-spoiler, evidence-grounded.*

**当前阶段：Phase 0 · 协议验证（含 Phase 1 基础脚手架）**

Phase 0 的唯一目标是打通 RetroArch AI Service → Android 本地 endpoint → 占位响应这条链路；Phase 1 则在此基础上铺好 UI 骨架（Home / Diagnostics / Settings）、本地数据库、LLM Adapter 抽象等脚手架，为后续问答 MVP 做准备。

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

启动 App，进入 Home 屏，确认 endpoint 状态为「运行中」（默认 `127.0.0.1:8080`）。

### 3. 配置 RetroArch

按照 [docs/RETROARCH_SETUP.md](./docs/RETROARCH_SETUP.md) 的步骤填写 AI Service URL、绑定热键，然后载入游戏按下热键即可在 Diagnostics 屏看到请求记录。

### 4. 自动化冒烟

```bash
./scripts/test_endpoint.sh
# 跨设备从开发机访问真机：先执行
adb forward tcp:8080 tcp:8080
```

---

## 文档导航 · Docs

| 文档 | 内容 |
| ---- | ---- |
| [docs/RETROARCH_SETUP.md](./docs/RETROARCH_SETUP.md) | RetroArch AI Service 完整配置步骤 + 故障排查 |
| [docs/PHASE0_VERIFICATION.md](./docs/PHASE0_VERIFICATION.md) | Phase 0 验收清单（自动化 + 手动） |
| [docs/PROTOCOL_REFERENCE.md](./docs/PROTOCOL_REFERENCE.md) | RetroArch AI Service 请求 / 响应字段速查 |
| [scripts/test_endpoint.sh](./scripts/test_endpoint.sh) | 一键 curl 冒烟脚本 |
| [scripts/sample_payload.json](./scripts/sample_payload.json) | 标准请求体样本 |
| [../RetroSprite_Development_Plan.md](../RetroSprite_Development_Plan.md) | 项目整体规划 |
| [../.qoder/skills/retrosprite-dev/SKILL.md](../.qoder/skills/retrosprite-dev/SKILL.md) | 项目开发约束与方向 |

---

## 技术栈 · Tech Stack

- **语言**：Kotlin（JVM 17）
- **UI**：Jetpack Compose + Material 3
- **架构**：单 Activity + Compose Navigation，分层 `ui / domain / data / endpoint / llm`
- **持久化**：Room + SQLite（FTS5），DAO 与 entity 分离
- **HTTP Server**：内嵌轻量 HTTP 服务（前台服务托管），监听 `127.0.0.1:8080`
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

- 不以 `MediaProjection` / Accessibility Service / 全局悬浮窗作为主集成路径。
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
