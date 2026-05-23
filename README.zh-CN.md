# RetroSprite

[English](./README.md) | 中文说明

RetroSprite 是一个面向 RetroArch 的 Android 游戏内 AI 问答伙伴。玩家在
RetroArch 中按下 AI Service 热键后，可以用一句简短问题询问当前游戏内容，
RetroSprite 会优先基于本地 Game Knowledge Pack 给出低剧透、可追溯来源的答案。

当前项目重点是本地优先、证据优先的游戏内问答闭环：

```text
RetroArch AI Service 热键
  -> RetroSprite 本地 endpoint
  -> 游戏内短时语音 overlay
  -> 本地 sherpa-onnx ASR
  -> GKP 解析 / 本地检索 / AnswerPolicy
  -> 短答案 + Android TTS 朗读
```

外部 LLM 是可选的 BYOK 证据综合器，不是默认事实来源。没有本地证据、知识包被禁用、
证据超过当前剧透级别时，RetroSprite 不会让 LLM 裸答。

## 当前状态

RetroSprite 当前处在 M10/M11：Hotkey Voice Overlay + Zero-LLM GKP。

已经具备：

- Kotlin Android App，使用 Jetpack Compose + Material 3。
- 本地 Ktor endpoint，通过前台服务绑定在 `127.0.0.1:4404`。
- RetroArch 兼容接口：`POST /?output=text`、`GET /health`、
  `POST /debug/ask`、`GET /debug/latest-request`。
- 热键触发的游戏内语音 overlay：短时录音、本地 ASR、GKP 回答、日志记录和 TTS 朗读。
- Room 本地数据库：请求日志、游戏、知识行、GKP 元数据、启用/禁用状态和迁移 schema。
- GKP v0 解析、内置导入、外部知识包预检、安装/覆盖确认和 Packs 管理界面。
- 内置 `sample-2048`、`sample-relay-station`，以及首个真实游戏试点
  `community.shining-force-ii-md`。
- template / alias / entity / FTS 风格的本地检索，带剧透等级过滤和来源 ID。
- Settings 支持 RetroArch 设置助手、endpoint 端口、overlay 授权、默认剧透级别、
  OpenAI-compatible / DeepSeek BYOK LLM 配置。
- Diagnostics 显示请求来源、pipeline stage、LLM 状态、耗时、token 预算、反馈和
  latest request replay。

## 仓库结构

```text
.
├── retrosprite-android/        # Android 主应用
│   ├── app/src/main/kotlin/    # Kotlin 源码
│   ├── app/src/main/assets/    # 内置 GKP + 本地 ASR 模型资源
│   ├── app/src/test/           # JVM 单元测试
│   ├── app/src/androidTest/    # Android / Compose 集成测试
│   ├── docs/                   # 协议、GKP、设置、测试和计划文档
│   └── scripts/                # endpoint 和 AVD/真机 smoke 脚本
├── RetroSprite_Development_Plan.md
├── README_AI_SERVICE_RESEARCH.md
└── RetroArch_AI_Service_Protocol_Specification.txt
```

Android App 的更详细构建和联调说明见
[retrosprite-android/README.md](./retrosprite-android/README.md)。

## 快速开始

环境要求：

- Android Studio 或本地 Android SDK。
- JDK 17，推荐使用 Android Studio 自带 JBR。
- Android API 26+ 的真机或模拟器。
- 完整热键体验需要 RetroArch Android 并启用 AI Service。

构建 Debug APK：

```bash
cd retrosprite-android
./gradlew assembleDebug
```

安装到设备：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

在设备 App 运行时，从开发机验证 endpoint：

```bash
adb forward tcp:4404 tcp:4404
./scripts/test_endpoint.sh
```

运行更完整的 AVD/真机 smoke：

```bash
./scripts/android_avd_smoke.sh
```

## RetroArch 设置

在 RetroArch Android 中打开：

```text
Settings -> Accessibility -> AI Service
```

填写 RetroSprite 默认地址：

```text
http://localhost:4404
```

随后绑定并按下 RetroArch AI Service 热键。RetroSprite 会把这次热键视为唤醒信号，
显示短时 overlay，收一次本地语音问题，并通过同一套 GKP / evidence pipeline 作答。

完整设置和排障见
[retrosprite-android/docs/RETROARCH_SETUP.md](./retrosprite-android/docs/RETROARCH_SETUP.md)。

## 开发

运行 JVM 单元测试：

```bash
cd retrosprite-android
./gradlew testDebugUnitTest
```

在连接的真机或模拟器上运行集成测试：

```bash
cd retrosprite-android
./gradlew connectedDebugAndroidTest
```

常用文档：

- [GKP v0 Schema](./retrosprite-android/docs/GKP_V0_SCHEMA.md)
- [RetroArch AI Service 协议参考](./retrosprite-android/docs/PROTOCOL_REFERENCE.md)
- [测试覆盖说明](./retrosprite-android/docs/TEST_COVERAGE.md)
- [下一阶段实施计划](./retrosprite-android/docs/NEXT_IMPLEMENTATION_PLAN.md)

## 设计边界

RetroSprite 明确避免这些捷径：

- 不修改 RetroArch core。
- 不用 MediaProjection 做连续屏幕捕获。
- 不把 Accessibility Service 作为主集成路径。
- 不申请广泛存储权限，也不自动改写 `retroarch.cfg`。
- GKP 不包含 ROM、商业攻略书原文、可执行代码或长篇受版权保护文本。
- 没有本地证据时，不让 LLM 裸答。

## License

TBD.
