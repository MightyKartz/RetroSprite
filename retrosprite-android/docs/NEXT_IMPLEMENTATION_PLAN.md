# RetroSprite 下一阶段实施计划

> 生成日期：2026-05-19
> 依据：代码现状、`docs/DELIVERY_REPORT.md`、Android AVD 实测、RetroArch v1.22.2 源码/官方 APK 行为、DeepSeek 官方 API 文档。

## 0. 当前真实状态

RetroSprite Android 已经具备可运行的 Phase 0/Phase 1 脚手架：

- Android 本地 endpoint 运行在 `127.0.0.1:8080`，`GET /health` 和 `POST /?output=text` 可用。
- 请求可写入 Room `request_logs`，Diagnostics/UI 层可读取近期日志。
- Domain 管线已拆出 `GameResolver`、`RetrievalPipeline`、`AnswerPolicy`、`AnswerComposer`、`LlmAdapter`。
- Room/FTS5、Compose 四屏、DataStore 设置和测试骨架已存在。
- `OpenAiCompatibleLlmAdapter` 仍是 skeleton；`ServiceLocator` 当前强制使用 `MockLlmAdapter`。
- 官方 RetroArch Android APK 已安装并配置 AI Service，但在 `RetroSprite_API_34` 上尚未通过热键/overlay/network command 触发真实请求。

这意味着：**RetroSprite endpoint 协议已通，Phase 0 的剩余风险集中在 RetroArch Android 官方 APK 的触发路径；Phase 1 的剩余风险集中在 DeepSeek BYOK、GKP 检索和低剧透策略。**

## 1. 使用的工具/技能/MCP

| 能力 | 用途 | 本轮结论 |
| --- | --- | --- |
| `retrosprite-dev` skill | 固定产品方向：RetroArch AI Service 优先、本地知识包优先、低剧透、BYOK | 后续不转向悬浮窗/Accessibility/MediaProjection |
| `project-codebase-onboarding-and-roadmap` skill | 以代码为准，比较文档漂移，生成可执行路线图 | `DELIVERY_REPORT.md` 部分内容已漂移，需要以后续计划覆盖 |
| Android SDK / ADB / AVD | 安装 APK、启动 `RetroSprite_API_34`、验证 endpoint 和 RetroArch Android 行为 | endpoint 可用；官方 APK 热键触发未通 |
| 官方 DeepSeek API 文档 | 确认 OpenAI-compatible base URL、model id、chat payload | `base_url=https://api.deepseek.com`，`model=deepseek-v4-pro` |

## 2. 决策

1. **Phase 0 不算完全关闭，改名为 Phase 0A：Android 触发闭环。**
   已完成“RetroSprite 可收 RetroArch 形状请求”，但还未完成“官方 RetroArch Android 真实发起请求”。

2. **不把 DeepSeek 接入作为 RetroArch 触发问题的前置条件。**
   DeepSeek 可并行实现，但端到端产品闭环必须先能从 RetroArch 触发。

3. **DeepSeek 先做非流式 OpenAI-compatible 调用。**
   先支持 `chat/completions`、短答案、max token、超时、错误映射；流式和 reasoning 展示后置。

4. **GKP MVP 先做可测试样例包，不做大规模内容工程。**
   先证明 FAQ/entity/FTS5/低剧透策略可以工作，再扩展知识包格式。

## 3. 里程碑

### M0 - Phase 0A：RetroArch Android 触发闭环

目标：在 `RetroSprite_API_34` 或一台真机上，让官方或可控 RetroArch Android build 通过 AI Service 真实请求 RetroSprite endpoint。

任务：

| ID | 任务 | 产物 | 验证 |
| --- | --- | --- | --- |
| M0.1 | 固化当前 Android 实测记录 | `docs/RETROARCH_ANDROID_AI_SERVICE_FINDINGS.md` | 文档包含 APK、AVD、配置、失败路径 |
| M0.2 | 增加 device/AVD endpoint smoke 流程 | 脚本或文档命令 | `adb forward` 后 `/health` 和模拟 POST 通过 |
| M0.3 | 用实体键盘/手柄在 RetroArch Android 手动绑定 AI Service | 联调记录 | `request_logs` 出现来自 RetroArch 的请求 |
| M0.4 | 若官方 APK 仍不触发，构建 RetroArch debug 版并加日志 | RetroArch build notes / patch notes | logcat 能看到 `CMD_EVENT_AI_SERVICE_TOGGLE` 或缺失原因 |

退出条件：

- `request_logs` 中至少出现 1 条由 RetroArch Android 发起、非手工 curl 的请求。
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

- 对 sample game 的 10 个 golden Q&A 全部通过。
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

## 4. 活动任务板

| 状态 | ID | 任务 | Owner | 备注 |
| --- | --- | --- | --- | --- |
| Active | M0.1 | 固化 RetroArch Android AI Service 实测记录 | Codex | 本轮执行 |
| Active | M3.4 | 修正文档漂移 | Codex | 本轮执行 |
| Done | M0.2 | 增加 AVD endpoint smoke 脚本 | Codex | `scripts/android_avd_smoke.sh` |
| Next | M1.1 | DeepSeek/OpenAI-compatible DTO + adapter 单测 | Codex | 不需要真实 key |
| Blocked | M1.4 | API key 安全存储 | User/Codex | 需确认是否接受 Android Keystore 方案 |
| Blocked | M0.3 | 实体手柄/键盘手动验证 | User/Codex | 需要真实输入设备或可交互桌面 |
| Later | M2.1 | GKP v0 schema | Codex | 等 Phase 0A/M1 基线稳定 |

## 5. 下一步执行顺序

1. 先完成本轮文档落地：Android AI Service findings、计划、README/验收文档漂移修复。
2. 增加 `scripts/android_avd_smoke.sh`，把“安装 APK、启动 App、转发端口、模拟 POST”变成可重复命令。
3. 实现 DeepSeek adapter 的本地 fake-server 单测，不需要真实 API key。
4. 用户提供 DeepSeek API key 后，只做一次最小实机验证：手工问题 + sample evidence + max token 限制。
5. 回到 RetroArch Android 触发：优先真机/实体手柄验证；若仍不通，再构建 RetroArch debug 版定位。

## 6. 验证命令

```bash
cd /Users/kartz/Development/Sprite/retrosprite-android

# JVM 单测 + Debug APK
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew testDebugUnitTest :app:assembleDebug

# AVD / device endpoint smoke
adb forward tcp:18080 tcp:8080
HOST=127.0.0.1 PORT=18080 ./scripts/test_endpoint.sh

# 或直接使用 device/AVD 脚本
./scripts/android_avd_smoke.sh
```

## 7. 待确认

- DeepSeek `deepseek-v4-pro` 默认 thinking mode 是否开启：建议 UI 提供开关，产品默认先关闭或限制 `max_tokens`，避免响应慢和成本不可控。
- API key 存储：建议 Phase 1 使用 Android Keystore-backed 加密存储，不继续写入 DataStore 明文。
- RetroArch Android 触发验证是否优先使用真机：AVD 的键盘/overlay 注入与真实手柄输入行为有差异，真机可更快排除模拟器输入层问题。
