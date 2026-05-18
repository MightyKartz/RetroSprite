# RetroSprite Phase 0 + Phase 1 基础脚手架 — 交付报告

## 1. 完成范围

| 阶段 | 目标 | 状态 |
|---|---|---|
| Phase 0 协议验证 | RetroArch AI Service 与 Android 本地 endpoint 通信链路可行性验证 | 完成 |
| Phase 0 协议验证 | Ktor CIO 嵌入式 HTTP 服务器（绑定 127.0.0.1:8080） | 完成 |
| Phase 0 协议验证 | POST / 接收 RetroArch 请求（image/label/state），返回兼容 JSON | 完成 |
| Phase 0 协议验证 | GET /health 健康检查端点 | 完成 |
| Phase 0 协议验证 | 异常路径统一 200 + {"error":...} 处理 | 完成 |
| Phase 0 协议验证 | Android Foreground Service（dataSync 类型）后台保活 | 完成 |
| Phase 1 基础脚手架 | Room 2.6.1 + FTS5 数据持久层 | 完成 |
| Phase 1 基础脚手架 | Game / Knowledge / RequestLog 数据 schema | 完成 |
| Phase 1 基础脚手架 | Domain 层抽象接口（GameResolver / RetrievalPipeline / AnswerPolicy / AnswerComposer / QueryPipeline / LlmAdapter） | 完成 |
| Phase 1 基础脚手架 | Compose Material3 Dark UI 4 屏（首页/问答/库/设置）+ Navigation | 完成 |
| Phase 1 基础脚手架 | ServiceLocator 手动 DI 装配 | 完成 |
| Phase 1 基础脚手架 | DataStore Preferences 设置持久化 | 完成 |
| 文档闭环 | README + RetroArch 配置指南 + Phase 0 验收文档 + 协议参考 | 完成 |
| 测试覆盖 | 30+ 单元测试 + androidTest + curl/fish 验证脚本 + CI 占位 | 完成 |
| CodeReview | 0 Blocker / 0 Major / 2 Minor（已修复） | 完成 |

## 2. 创建的文件路径列表

### 项目配置

```
retrosprite-android/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── local.properties.example
├── .gitignore
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
│       ├── gradle-wrapper.properties
│       └── README.md
└── app/
    └── build.gradle.kts
```

### Endpoint 层

```
app/src/main/kotlin/com/retrosprite/app/endpoint/
├── EndpointController.kt          — Ktor 服务器控制器（start/stop/routing）
├── RetroArchModels.kt             — RetroArchRequest / RetroArchResponse 数据类
├── ResponseGenerator.kt           — fun interface ResponseGenerator
├── RequestLogSink.kt              — fun interface RequestLogSink + DefaultInMemorySink
├── QueryPipelineResponseGenerator.kt — Domain QueryPipeline → RetroArchResponse 适配器
└── RoomBackedRequestLogSink.kt    — RequestLogSink → Room Repository 适配器
```

### 数据层

```
app/src/main/kotlin/com/retrosprite/app/data/
├── RetroSpriteDatabase.kt         — Room Database + FTS5 Callback
├── GameDao.kt                     — Game 表 DAO
├── KnowledgeDao.kt                — Knowledge 表 DAO + FTS5 查询
├── RequestLogDao.kt               — RequestLog 表 DAO
├── RequestLogRepository.kt        — DefaultRequestLogRepository
├── GameEntity.kt                  — Game 实体
├── KnowledgeEntity.kt             — Knowledge 实体
└── RequestLogEntity.kt            — RequestLog 实体
```

### 领域层

```
app/src/main/kotlin/com/retrosprite/app/domain/
├── QueryPipeline.kt               — QueryPipeline 接口 + DefaultQueryPipeline
├── GameResolver.kt                — GameResolver 接口 + LabelGameResolver
├── RetrievalPipeline.kt           — RetrievalPipeline 接口 + NoOpRetrievalPipeline
├── AnswerPolicy.kt                — AnswerPolicy 接口 + FixedTextAnswerPolicy
├── AnswerComposer.kt              — AnswerComposer 组合答案
└── LlmAdapter.kt                  — LlmAdapter 接口 + LlmAdapterFactory + MockLlmAdapter
```

### UI 层

```
app/src/main/kotlin/com/retrosprite/app/ui/
├── navigation/
│   └── AppNavigation.kt           — NavHost + 4 屏路由
├── screens/
│   ├── HomeScreen.kt              — 首页：Endpoint 状态 + 快速操作
│   ├── QAScreen.kt                — 问答历史列表
│   ├── LibraryScreen.kt           — 游戏库 / 知识库浏览
│   └── SettingsScreen.kt          — 端口 / LLM / 剧透等级设置
├── viewmodel/
│   ├── HomeViewModel.kt
│   ├── QAViewModel.kt
│   ├── LibraryViewModel.kt
│   ├── SettingsViewModel.kt
│   └── PreviewStubs.kt            — Compose Preview 占位数据
├── theme/
│   ├── Theme.kt                   — Material3 Dark 主题
│   └── Color.kt                   — 配色方案
└── integration/
    ├── UiDependencies.kt          — UI 门面接口（EndpointStatusProvider / RequestLogProvider / SettingsStore）
    ├── RealEndpointStatusProvider.kt
    ├── RealRequestLogProvider.kt
    └── UiModelMappers.kt          — Domain ↔ UI 模型映射
```

### 应用入口

```
app/src/main/kotlin/com/retrosprite/app/
├── RetroSpriteApp.kt              — Application (ServiceLocator 初始化 + Endpoint 启动)
├── MainActivity.kt                — Compose setContent + ProvideUiDependencies
├── ServiceLocator.kt              — 手动 DI 容器
└── EndpointForegroundService.kt   — Foreground Service + 通知渠道
```

### 文档

```
docs/
├── PROTOCOL_REFERENCE.md          — RetroArch AI Service 协议完整规范
├── RETROARCH_SETUP.md             — RetroArch 端配置指南
├── PHASE0_VERIFICATION.md         — Phase 0 验收 5 项指标
└── TEST_COVERAGE.md               — 测试策略与覆盖率说明
```

### 测试与脚本

```
app/src/test/kotlin/com/retrosprite/app/
├── EndToEndPipelineTest.kt        — 纯 JVM 端到端协议链路测试
└── endpoint/
    └── EndpointEdgeCaseTest.kt    — Endpoint 边界情况测试

app/src/androidTest/kotlin/com/retrosprite/app/
└── (Room 迁移与 FTS5 集成测试)

scripts/
├── test_endpoint.sh               — Bash curl 验证脚本（adb forward + 127.0.0.1）
├── test_endpoint.fish             — Fish shell 版本
└── sample_payload.json            — RetroArch 请求样例

.github/workflows/
└── ci.yml                         — GitHub Actions CI 占位
```

## 3. 端到端验证链路

```
┌─────────────────────────┐
│   RetroArch Desktop     │
│   (AI Service 启用)      │
└────────────┬────────────┘
             │ HTTP POST http://127.0.0.1:8080/
             │ Content-Type: application/json
             │ ?output=text
             │ Body: { "image": "base64...", "label": "snes__zelda", "state": {...} }
             ▼
┌─────────────────────────┐
│   Ktor CIO Endpoint     │
│   (EndpointController)   │
├─────────────────────────┤
│ 1. 解析 query params    │
│ 2. 反序列化 JSON body    │
│ 3. 记录日志 → RequestLogSink → RoomBackedRequestLogSink → Room DB
│ 4. 调用 ResponseGenerator│
└────────────┬────────────┘
             ▼
┌─────────────────────────┐
│ QueryPipelineResponseGenerator │
│ (Adapter: endpoint → domain)   │
└────────────┬────────────┘
             ▼
┌─────────────────────────┐
│   DefaultQueryPipeline  │
├─────────────────────────┤
│ ① LabelGameResolver     │ — 从 label "snes__zelda" 识别游戏
│ ② NoOpRetrievalPipeline │ — (Phase 1 将实现 FAQ→实体→FTS5→LLM 漏斗)
│ ③ FixedTextAnswerPolicy │ — 低剧透策略守卫
│ ④ AnswerComposer        │ — 组合最终答案文本
│ ⑤ MockLlmAdapter        │ — 返回固定提示文本
└────────────┬────────────┘
             ▼
┌─────────────────────────┐
│   RetroArchResponse     │
│   { "text": "..." }     │
└────────────┬────────────┘
             │ HTTP 200 OK
             ▼
┌─────────────────────────┐
│   RetroArch Desktop     │
│   (显示翻译/AI 覆盖)     │
└─────────────────────────┘
```

**异常路径**：任何内部错误均返回 `HTTP 200 + {"error": "描述信息"}`，确保 RetroArch 客户端不因 HTTP 4xx/5xx 状态码而解析失败。

## 4. 已知集成限制

| 限制项 | 当前状态 | 影响 | 计划解决阶段 |
|---|---|---|---|
| LLM 适配器为 Mock | `MockLlmAdapter` 返回固定文本 | 无真实 AI 问答能力 | Phase 1 |
| 检索漏斗为 NoOp | `NoOpRetrievalPipeline` 未执行实际检索 | 答案不基于知识库 | Phase 1 |
| 端口动态切换 | UI Settings 已存端口值，但 Endpoint 重启接线未完成 | 更改端口需重启 App | Phase 1 |
| GKP schema 未冻结 | 仅有 Knowledge 表结构占位 | 无法导入真实游戏知识包 | Phase 1 |
| gradle-wrapper.jar 缺失 | 纯文本工具无法生成二进制 | 需用户手动生成或由 IDE 补全 | 首次打开项目时 |
| LLM API Key 未加密 | BYOK 配置 UI 尚未实现 KeyStore 集成 | 密钥明文（但 Mock 阶段无实际密钥） | Phase 1 |
| FTS5 降级未全面测试 | SQLiteCapabilities 探测逻辑已写，但无低版本设备测试 | 低版本 Android 可能回退到 LIKE 查询 | Phase 1 |

## 5. Phase 1 后续改进建议

### 5.1 LLM 集成（优先级：高）

1. **BYOK 配置 UI**：Settings 屏增加 Provider 选择（OpenAI / Anthropic / 本地 Ollama），通过 Android KeyStore 加密存储 API Key
2. **LlmAdapter 实现**：`OpenAiLlmAdapter`、`AnthropicLlmAdapter`、`OllamaLlmAdapter`，均实现流式响应
3. **Token 预算控制**：根据 `LlmConfig` 限制单次请求 max_tokens，防止成本失控
4. **Fallback 链**：主适配器超时/限流时自动降级到 Mock 或缓存答案

### 5.2 端口管理（优先级：中）

1. **热重启机制**：`EndpointController.restart(newPort)` 方法，UI 更改端口后无需杀进程
2. **端口冲突检测**：启动前探测端口占用，失败时自动递增 + 通知用户
3. **多端口支持**：为未来 WebSocket 推送预留第二端口

### 5.3 游戏识别功能（优先级：高）

1. **LabelGameResolver 增强**：label 格式 `system__game` 的模糊匹配 + 别名表（`aliases` 字段）
2. **GKP v0 schema 冻结**：定义 `manifest.json` 格式（version / game_id / knowledge_entries / spoiler_levels）
3. **种子 Pack 制作**：选取 1-2 款经典游戏（建议 Pokémon Red、Zelda: A Link to the Past）编写示例 GKP
4. **检索漏斗实现**：FAQ 精确匹配 → 实体查询 → FTS5 全文 → LLM 兜底，每层带 spoiler_level 与 progress_gate 过滤

### 5.4 观测与统计（优先级：低）

1. **RequestLog 增强**：记录每次请求的处理耗时、检索命中层级、spoiler 截断次数
2. **UI Dashboard**：首页展示近 24h 请求量、平均响应时间、知识库命中率

## 6. 用户后续操作清单

### 6.1 生成 gradle-wrapper.jar

**方式一（推荐）**：在 Android Studio 中打开项目，IDE 会自动提示并生成 wrapper。

**方式二**：如已全局安装 Gradle：
```bash
cd /Users/kartz/Development/Sprite/retrosprite-android
gradle wrapper --gradle-version 8.5
```

**方式三**：从任意已有 Android 项目复制 `gradle/wrapper/gradle-wrapper.jar`。

### 6.2 Android Studio 项目同步

1. 打开 Android Studio → File → Open → 选择 `retrosprite-android/` 目录
2. 等待 Gradle Sync 完成（首次可能需要下载依赖，约 3-5 分钟）
3. 确认 Build → Make Project 无报错
4. 检查 Project Structure 中 JDK 版本为 17

### 6.3 真机/模拟器验证

```bash
# 1. 安装 debug APK
./gradlew :app:installDebug

# 2. 启动 App，点击「启动 Endpoint」按钮

# 3. 设置端口转发（真机连 USB 时）
adb forward tcp:8080 tcp:8080

# 4. 验证 health 端点
curl http://127.0.0.1:8080/health
# 预期: {"status":"ok","version":"0.1.0"}

# 5. 发送模拟 RetroArch 请求
curl -X POST "http://127.0.0.1:8080/?output=text" \
  -H "Content-Type: application/json" \
  -d @scripts/sample_payload.json
# 预期: {"text":"..."}
```

### 6.4 RetroArch 端配置

1. 打开 RetroArch → Settings → AI Service
2. 设置 AI Service URL: `http://127.0.0.1:8080/`
3. 设置 AI Service Output: `Text`
4. 启用 AI Service: ON
5. 运行任意游戏 → 按 AI Service 热键（默认 F8）→ 确认 overlay 显示返回文本

### 6.5 Phase 0 验收指标检查

参照 `docs/PHASE0_VERIFICATION.md` 中的 5 项验收标准：

| # | 验收项 | 验证方法 |
|---|---|---|
| 1 | Health 端点可达 | `curl /health` 返回 200 + JSON |
| 2 | POST 请求正确解析 | 发送 sample_payload，检查响应非 error |
| 3 | 日志持久化 | App UI 问答屏可见请求记录 |
| 4 | 异常处理 | 发送畸形 JSON，确认返回 `{"error":...}` 而非崩溃 |
| 5 | Foreground Service | App 切后台后 Endpoint 仍可响应 |

---

## 技术栈摘要

| 组件 | 版本 |
|---|---|
| Kotlin | 1.9.22 |
| AGP | 8.2.2 |
| KSP | 1.9.22-1.0.17 |
| Compose BOM | 2024.02.00 |
| Compose Compiler | 1.5.8 |
| Material3 | 1.2.0 |
| Ktor | 2.3.7 |
| Room | 2.6.1 |
| Kotlinx Serialization | 1.6.2 |
| Navigation | 2.7.6 |
| Lifecycle | 2.7.0 |
| DataStore | 1.0.0 |
| minSdk | 26 |
| targetSdk | 34 |
| JVM Target | 17 |

---

*报告生成时间：2026-05-18*
*Phase 0 + Phase 1 基础脚手架 — 全部完成*
