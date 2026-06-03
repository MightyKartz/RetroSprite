# RetroSprite Android · 游戏内 AI 问答伙伴

> 一句话定位：玩家在 RetroArch 中按下热键，向 RetroSprite 发起一个简短、准确、低剧透的游戏内问题，得到基于本地知识包的可信答案。
>
> *RetroSprite is an in-game Q&A companion for RetroArch — local-first, low-spoiler, evidence-grounded.*
>
> 仓库首页版 README 见 [../README.md](../README.md)；本文保留 Android App 的详细构建、联调和目录说明。

**当前阶段：v0.1.0 正式 GitHub Release · Release-signed APK**

**当前正式版本：v0.1.0。**RetroSprite 已经可以通过 GitHub Release 分发 release-signed APK，
面向普通用户长期下载和后续同签名升级。热键语音问答、GKP 本地检索、BYOK 画面翻译、
设置/诊断和 6 个内置真实游戏包都已经形成可运行闭环。正式 APK 发布流程见
[docs/RELEASE_SIGNING.md](./docs/RELEASE_SIGNING.md)。

v0.1.0 最终发布版补强了两个真机风险点：GKP manifest 新增 `title_aliases`，导入时会按
`retroarch_system_ids × title_aliases` 自动生成身份标签，提升不同 RetroArch system id、
英文/中文/罗马数字标题写法下的游戏解析命中率；热键语音启动 ASR 前会刷新 endpoint 前台服务
状态，减少麦克风捕获在服务状态切换后失败。

**后续质量闭环：M18 Eval Lab + GKP Quality Loop。**v0.1.0 后不急于扩新游戏、不改默认模型路线，
而是把真实玩家问题、无 evidence、ASR 误识别和 GKP 覆盖缺口转成可复现的评测报告、backlog、
GKP patch proposal 和 golden 回归。详见
[M18 Eval Lab And GKP Quality Loop Plan](./docs/superpowers/plans/2026-06-01-m18-eval-lab-gkp-quality-loop.md)。

**后续方向仍是：GKP Lite + Optional BYOK LLM。**每个游戏的首个支持版本不再要求完整攻略级 GKP，而是先做轻量、可信、可测试的 GKP Lite；外部 LLM 继续由玩家自主选择是否启用、使用什么 provider/model，只作为证据综合、跨语言映射和表达增强层。没有 LLM 时 RetroSprite 仍应离线可用；没有本地证据时不允许 LLM 裸答具体攻略事实。

RetroArch AI Service → Android 本地 endpoint → 热键唤醒 RetroSprite 游戏内语音 overlay → 本地 ASR → GKP/AnswerPolicy → 短答 TTS 这条主路径已经接通并进入体验打磨。Home 页文字提问、pending hotkey 问题和 debug curl 仍保留为设置验证与开发 fallback；玩家主体验应是在 RetroArch 中按热键呼出科技感语音波形，不需要频繁回到 App 里操作。

当前 APK 默认打包 sherpa-onnx 本地 ASR 资产，通过 sherpa-onnx streaming ASR 路径完成
中文/英文语音识别。该路径不启用 sherpa 原生热词；游戏专属名词靠当前 GKP 的别名/ASR
变体和 `GameTermNormalizer` 做游戏域内修复，不能退化成跨游戏全局替换。发布或测试 APK
前必须使用 clean build，尤其是改过 ASR 模型、大型 assets 或 native libs 后，避免
Gradle/ZIP 增量打包留下旧产物空洞。真机可用性仍以 RG476H 安装、启动、录音和连续问答测试为准。

**当前真实支持游戏：仅 6 个。**RetroSprite 目前内置支持：
**Shining Force II / 光明力量2**（`community.shining-force-ii-md`）、
**Golden Sun / 黄金太阳**（`community.golden-sun-gba-zh`）、
**Phantasy Star IV / 梦幻之星 IV**（`community.phantasy-star-iv-md-zh`）、
**Langrisser II / 梦幻模拟战 II**（`community.langrisser-ii-md-zh`）、
**Chrono Trigger / 时空之轮**（`community.chrono-trigger-snes-zh`），以及
**Final Fantasy VI / 最终幻想 VI**（`community.final-fantasy-vi-snes-zh`）。这是当前完整正式游戏支持范围；`sample-2048` 和 `sample-relay-station` 已从 bundled assets 移除，开发和冒烟测试应使用真实游戏 GKP。

当前内置 GKP 规模约为 347 条 knowledge row 与 337 条 golden；发布前必须保持这些 goldens、端点 smoke 和 hotkey voice matrix 可解释、可回归。

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

### 5. 构建正式发布 APK

普通用户长期下载应使用 release-signed APK，而不是 debug-signed preview APK。首次正式发布前生成本地 release keystore：

```bash
cd retrosprite-android
./scripts/generate_release_keystore.sh
```

填写本地 `keystore.properties` 后构建正式包：

```bash
TAG=v0.1.0 ./scripts/build_release_apk.sh
```

产物会输出到 `app/build/release-artifacts/`，包含 APK、SHA-256 校验文件和签名证书校验文本。完整流程见 [docs/RELEASE_SIGNING.md](./docs/RELEASE_SIGNING.md)。

---

## 文档导航 · Docs

| 文档 | 内容 |
| ---- | ---- |
| [docs/RETROARCH_SETUP.md](./docs/RETROARCH_SETUP.md) | RetroArch AI Service 完整配置步骤 + 故障排查 |
| [docs/PHASE0_VERIFICATION.md](./docs/PHASE0_VERIFICATION.md) | Phase 0 验收清单（自动化 + 手动） |
| [docs/PROTOCOL_REFERENCE.md](./docs/PROTOCOL_REFERENCE.md) | RetroArch AI Service 请求 / 响应字段速查 |
| [docs/RELEASE_SIGNING.md](./docs/RELEASE_SIGNING.md) | 正式 APK 签名、clean release build 和 GitHub Release 上传流程 |
| [docs/ARCHITECTURE_AND_PRODUCT_TIERS.md](./docs/ARCHITECTURE_AND_PRODUCT_TIERS.md) | 主程序、GKP builder、GKP Lite、expanded/deep 覆盖、Pro 商业层和可选 LLM 的当前口径 |
| [docs/GKP_V0_SCHEMA.md](./docs/GKP_V0_SCHEMA.md) | GKP v0 schema、pack 结构与 lint 规则 |
| [docs/GKP_LITE_OPTIONAL_LLM_DIRECTION.md](./docs/GKP_LITE_OPTIONAL_LLM_DIRECTION.md) | GKP Lite + 玩家可选 LLM 的后续产品与架构方向 |
| [docs/REAL_GAME_GKP_EXPANSION_TEMPLATE.md](./docs/REAL_GAME_GKP_EXPANSION_TEMPLATE.md) | 真实游戏 GKP Lite 生产模板、覆盖层级与验收标准 |
| [docs/NEXT_IMPLEMENTATION_PLAN.md](./docs/NEXT_IMPLEMENTATION_PLAN.md) | 下一阶段实施计划、任务板与验证门槛 |
| [docs/superpowers/plans/2026-06-01-release-candidate-hardening.md](./docs/superpowers/plans/2026-06-01-release-candidate-hardening.md) | M17 Release Candidate Hardening 可执行计划 |
| [docs/superpowers/plans/2026-06-01-m17-hotkey-voice-lifecycle-recovery.md](./docs/superpowers/plans/2026-06-01-m17-hotkey-voice-lifecycle-recovery.md) | M17.1 热键语音生命周期恢复计划 |
| [docs/superpowers/plans/2026-06-01-m18-eval-lab-gkp-quality-loop.md](./docs/superpowers/plans/2026-06-01-m18-eval-lab-gkp-quality-loop.md) | M18 Eval Lab + GKP 质量闭环实施计划 |
| [docs/superpowers/plans/2026-06-01-m18-approval-gated-quality-loop.md](./docs/superpowers/plans/2026-06-01-m18-approval-gated-quality-loop.md) | 已被 2026-06-02 M18 范围更新取代的历史计划 |
| [docs/RELEASE_CANDIDATE_CHECKLIST.md](./docs/RELEASE_CANDIDATE_CHECKLIST.md) | M17 preview release 出包前检查清单 |
| [docs/qa-feedback/m18-status-report.md](./docs/qa-feedback/m18-status-report.md) | M18 GKP 覆盖、backlog、patch dry-run、hotkey voice 和 quality-loop 汇总状态 |
| [docs/qa-feedback/m18-gate-status.json](./docs/qa-feedback/m18-gate-status.json) | M18 gate 状态的机器可读 JSON 摘要 |
| [docs/qa-feedback/m18-plan-execution-audit.md](./docs/qa-feedback/m18-plan-execution-audit.md) | M18 主计划 checkbox 与 aggregate gate 的执行状态审计，并生成机器可读 JSON |
| [docs/qa-feedback/m18-completion-audit.md](./docs/qa-feedback/m18-completion-audit.md) | M18 完成性审计：逐项列出 plan、aggregate gate、机器/实机证据和最终 strict gate 是否足以证明完成，并生成机器可读 JSON |
| [docs/qa-feedback/m18-next-action-queue.md](./docs/qa-feedback/m18-next-action-queue.md) | M18 下一步行动队列：仅保留当前机器/设备 gate 的 owner、ready/blocked、阻塞条件、证据和命令 |
| [docs/qa-feedback/m18-quality-loop-handoff.md](./docs/qa-feedback/m18-quality-loop-handoff.md) | M18 持续质量闭环交接：preview-first backlog 导入入口、修复验收规则和新增游戏冻结规则，并生成机器可读 JSON |
| [docs/qa-feedback/m18-command-contract-audit.md](./docs/qa-feedback/m18-command-contract-audit.md) | M18 生成命令契约审计：覆盖 queue、quality-loop JSON、plan execution JSON、completion JSON、remaining handoff、offline gate、README、Architecture、NEXT 和 TEST_COVERAGE |
| [docs/qa-feedback/m18-remaining-gate-handoff.md](./docs/qa-feedback/m18-remaining-gate-handoff.md) | M18 剩余机器/设备 gate 总交接 |
| [docs/qa-feedback/hotkey-voice-matrix-report.md](./docs/qa-feedback/hotkey-voice-matrix-report.md) | M18 热键语音 7 条实机 playback matrix 的 pass/fail、ASR transcript 和失败分类报告 |
| [docs/qa-feedback/gkp-asset-mutation-guard.md](./docs/qa-feedback/gkp-asset-mutation-guard.md) | M18 内置 GKP 资产变更门禁，确保资产保持 clean；只有用户明确批准 exact patch 后才允许预期 alias/golden 文件变更 |
| [docs/qa-feedback/gkp-patch-regression-gate-readiness.md](./docs/qa-feedback/gkp-patch-regression-gate-readiness.md) | M18 GKP patch regression gate safe-default 实跑证据，证明批准后的回归门禁可执行 |
| [docs/qa-feedback/hotkey-voice-lifecycle-failure-20260601.md](./docs/qa-feedback/hotkey-voice-lifecycle-failure-20260601.md) | 2026-06-01 RG476H 热键语音 playback 失败归因记录 |
| [docs/qa-feedback/gkp-patch-proposals-20260601-hotkey-voice.md](./docs/qa-feedback/gkp-patch-proposals-20260601-hotkey-voice.md) | 2026-06-01 热键语音 ASR 变体 GKP patch proposals |
| [docs/RETROARCH_ANDROID_AI_SERVICE_FINDINGS.md](./docs/RETROARCH_ANDROID_AI_SERVICE_FINDINGS.md) | RetroArch Android 官方 APK 首次联调记录 |
| [scripts/test_endpoint.sh](./scripts/test_endpoint.sh) | 一键 curl 冒烟脚本 |
| [scripts/android_avd_smoke.sh](./scripts/android_avd_smoke.sh) | AVD/真机上的 RetroSprite endpoint 冒烟脚本，覆盖真实内置 GKP |
| [scripts/screen_translation_matrix_update.py](./scripts/screen_translation_matrix_update.py) | 通用 QA 工具：安全更新屏幕翻译手测矩阵单行结果，避免手改 Markdown 表格 |
| [scripts/m18_quality_loop_handoff.py](./scripts/m18_quality_loop_handoff.py) | 生成 M18 持续质量闭环交接，固化 latest-request、voice TSV 和 manual notes 的 preview-first backlog 导入流程 |
| [scripts/m18_command_contract_audit.py](./scripts/m18_command_contract_audit.py) | 审计 M18 生成文档、核心计划/测试文档和结构化 JSON，阻止过期参数、危险 apply、ready frontier 漂移和旧人工 gate 口径回流 |
| [scripts/m18_offline_quality_gate.sh](./scripts/m18_offline_quality_gate.sh) | M18 离线报告刷新、strict open-gate 探针、脚本测试和 release audit 总入口 |
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
