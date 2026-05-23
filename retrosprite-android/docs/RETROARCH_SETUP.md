# RetroArch AI Service 配置指南

> 目标：让你在约 10 分钟内完成 RetroArch 与 RetroSprite 的对接，按下热键即可呼出游戏内语音波形，并可在「设置 -> 开发者诊断」查看请求记录。

## 概述

RetroArch 内建一个名为 **AI Service** 的功能。当玩家在游戏中按下绑定的 AI Service 热键时，RetroArch 会冻结画面、截取一帧，并以 HTTP POST 的方式把 PNG 图像、当前 ROM 的 `system__game` 标签以及手柄/暂停状态发送到玩家自行配置的 URL。

RetroSprite Android 应用就是这个「自行配置的 URL」对应的本地服务端。它在手机/平板上启动一个默认监听 `127.0.0.1:4404` 的轻量 HTTP endpoint，推荐直接使用 RetroArch 默认 AI Service 地址 `http://localhost:4404`，接收 RetroArch 的请求，记录 `label/state/image`，把 label 映射到启用中的本地 GKP，并把短答案交给游戏内语音波形和 TTS 链路。

需要特别区分两条链路：RetroArch AI Service 原始请求只包含截图、ROM label 和手柄状态，**不包含玩家自然语言问题**；RetroSprite 收到热键信号后会在本机启动一次性语音识别，把玩家语音问题和最近游戏上下文一起送入本地 GKP/AnswerPolicy。开发验证仍可使用 loopback-only 的 `/debug/ask` route。只要有启用的本地 GKP 和匹配 evidence，RetroSprite 会优先返回带来源的低剧透答案；配置 BYOK LLM 后，也只会在有 evidence 时进入 LLM 组合。

> AVD 备注（2026-05-19）：官方 RetroArch Android APK 在 `RetroSprite_API_34` 中可安装、可运行 2048 core，实体键盘热键已成功触发 AI Service 请求并写入 RetroSprite `request_logs`。ADB 注入键盘/gamepad/overlay/network command 仍不能可靠代表实体输入。详见 [RETROARCH_ANDROID_AI_SERVICE_FINDINGS.md](./RETROARCH_ANDROID_AI_SERVICE_FINDINGS.md)。

## 前置条件

- 已安装 **RetroSprite Android** 应用（当前 Debug 构建即可）。
- 已安装 **RetroArch 1.16.0+**：Android 端或 PC 端均可（PC 1.16.0+ / Android 1.16.0+）。
- 部署形态满足以下任一项：
  - **同设备（推荐）**：RetroArch 与 RetroSprite 都跑在同一台 Android 设备上。
  - **跨设备同局域网**：Android 设备运行 RetroSprite，另一台机器（PC / 其他手机）运行 RetroArch，二者处于同一 Wi-Fi。

> 备注：当前默认仅监听 `127.0.0.1`，跨设备访问需要额外步骤，请见下文「不同部署场景」。

## 配置步骤

### 1. 启动 RetroSprite，确认 endpoint 运行

打开 RetroSprite App，进入 **Home** 屏，确认本地服务状态为「运行中」，并显示监听端口（默认 `4404`）。
若未启动，点击 Home 屏的 **启动本地端点** 按钮；首次启动会请求前台服务通知权限，请允许。

### 2. 记录 endpoint URL

根据部署场景选择 URL：

| 场景 | URL |
| ---- | ---- |
| 同设备（RetroArch 与 RetroSprite 均在 Android 上） | `http://localhost:4404` |
| 跨设备（RetroArch 在 PC，RetroSprite 在 Android） | `http://<Android-IP>:4404` |

> `<Android-IP>` 可在 RetroSprite 的 Settings 屏查看，或在系统 **设置 → 关于本机 → 状态信息 → IP 地址** 中获取。

> 重要：RetroSprite **默认仅监听 `127.0.0.1`**，跨设备直连不默认开放。后续可提供「允许局域网访问（绑定 0.0.0.0）」开关；当前强烈建议先在同设备或 `adb forward` 场景验证。

### 3. 进入 RetroArch AI Service 配置页

RetroArch 主菜单 → **Settings → Accessibility → AI Service**。

> 备注：部分 RetroArch 版本/菜单驱动可能把 AI Service 放在 Settings 根目录下，或因翻译显示为“无障碍”。以能看到 `AI Service`、`AI Service URL`、`AI Service Output` 的页面为准。

### 4. 填写以下字段

| 字段 | 推荐值 |
| ---- | ---- |
| AI Service | **开启** |
| AI Service URL | 步骤 2 记录的 URL |
| AI Service Output | **旁白模式（Narrator Mode）** |
| Pause During Translation | **ON**（推荐：监听时暂停游戏，减少游戏音乐干扰语音识别） |
| Source Language | Auto，或当前游戏文本语言 |
| Target Language | 中文（如需翻译） / None |

### 5. 绑定 AI Service 热键

进入 **Settings → Input → Hotkeys → AI Service**，绑定一个易触发的组合：

- 推荐：长按 `Select + Start`，或 `L3 + R3`（手柄按摇杆）。
- 也可绑定到键盘单键，便于桌面 RetroArch 调试。

### 6. 启动游戏并触发

载入任意游戏 ROM，按下上一步绑定的热键。若已开启 `Pause During Translation`，RetroArch 会在本次 AI Service 请求期间暂停游戏，减少 BGM 被麦克风录入。看完或听完 RetroSprite 回答后，再按一次同一个 AI Service 热键即可清除显示并恢复游戏。

### 7. 在 RetroSprite 验证

切回 RetroSprite App，进入 **设置 -> 开发者诊断 -> 打开诊断日志**：

- 应出现一条新的请求记录，包含 `label`、`output=text`、请求时间戳、pipeline stage、source ids、LLM 状态等诊断信息。
- 如果这是 RetroArch 热键原始请求，因为没有自然语言问题，返回文本通常是上下文/诊断类回答。
- 如果随后在 Home 输入框或 `/debug/ask` 提供问题，且当前 label 命中启用的本地 GKP，回答会包含本地 evidence 来源；无 evidence 时会明确说明暂时不能可靠回答，而不是猜测。

至此 RetroArch 触发链路已打通。

### 8. 验证本地 GKP 问答（可选但推荐）

在 AVD/真机上调试时，可先转发端口：

```bash
adb forward tcp:18080 tcp:4404
```

然后在本机验证两个内置样例 GKP：

```bash
curl -fsS -X POST 'http://127.0.0.1:18080/debug/ask?output=text' \
  -H 'Content-Type: application/json' \
  --data '{"label":"2048__","question":"两个 2 怎么合并？","state":{}}'

curl -fsS -X POST 'http://127.0.0.1:18080/debug/ask?output=text' \
  -H 'Content-Type: application/json' \
  --data '{"label":"relay_station__","question":"蓝色保险丝在哪？","spoiler_level":"direct","state":{}}'

curl -fsS 'http://127.0.0.1:18080/debug/latest-request'
```

预期：两个问题都走 `pipeline_stage=evidence`，`llm_status=skipped`，并能看到 `sample.2048.*` 或 `sample.relay.*` 来源 id。也可直接运行 `./scripts/android_avd_smoke.sh`，脚本会自动覆盖 endpoint smoke 和两个样例 GKP debug 问答。

## 常见问题排查（FAQ）

| 现象 | 排查方向 |
| ---- | ---- |
| RetroSprite 没有收到任何请求 | 1) Home 屏 endpoint 是否运行中；2) RetroArch 中 URL 拼写和端口是否正确；3) RetroArch 端 AI Service 是否 ON；4) 防火墙 / 路由器是否阻断；5) 跨设备时是否使用了正确的 Android IP。 |
| RetroArch 提示 `Connection refused` | 1) 端口被其他应用占用，可在 RetroSprite Settings 屏修改端口；2) RetroSprite 进程被系统杀死，重新启动；3) 跨设备时当前未开放「允许局域网访问」，请改用同设备或 `adb forward` 验证。 |
| RetroArch 提示超时 / Image too large | 1) RetroArch 截图尺寸过大，降低分辨率或缩小窗口；2) Wi-Fi 不稳定，改为同设备验证；3) RetroSprite 处于后台被系统冻结，调整电池优化白名单。 |
| 游戏中没有听到回答 | 1) `AI Service Output` 是否为 **旁白模式（Narrator Mode）**；2) RetroSprite 是否已获得麦克风和“显示在其他应用上层”权限；3) 开发者诊断中是否能看到最新 RetroArch 请求。 |
| 游戏音乐影响语音识别 | 在 RetroArch 中开启 **Settings → AI Service → Pause During Translation → ON**，让监听期间暂停游戏；听完回答后再按一次 AI Service 热键恢复游戏。 |
| Diagnostics 屏看到请求但 RetroArch 一直「Loading」 | 检查 RetroSprite 日志中是否抛错；可执行 `scripts/test_endpoint.sh` 快速复现，或在 AVD/真机上执行 `scripts/android_avd_smoke.sh` 同时验证 `/debug/latest-request`。 |
| 找不到 AI Service 页面 | 优先查看 **Settings → Accessibility → AI Service**。RetroSprite Settings 只提供推荐值和“一键复制 AI Service URL”，不会写入 RetroArch cfg，也不会申请全文件权限。 |
| Android 系统横幅提示后台限制 | 在系统 **设置 → 应用 → RetroSprite → 电池 → 不受限制** 中放行。 |

## 不同部署场景

### 场景 A：同一台 Android 设备（推荐）

- RetroArch Android 与 RetroSprite Android 同机。
- URL 使用 RetroArch 默认值 `http://localhost:4404`。
- 无需任何网络/防火墙配置。

### 场景 B：PC 上的 RetroArch + Android 上的 RetroSprite

- 二者必须处于同一局域网（同一 Wi-Fi）。
- URL 使用 `http://<Android-IP>:4404`。
- 由于当前默认 bind 在 `127.0.0.1`，PC 实际无法直连。请等待「允许局域网访问」开关，或使用下方临时方案。
- 临时方案：使用 `adb reverse tcp:4404 tcp:4404`，让 PC 上的 RetroArch 通过 `http://localhost:4404` 转发到手机端。

### 场景 C：Android 模拟器（AVD）+ 桌面 RetroSprite

- 当前 RetroSprite 仅 Android，不适用此场景。

## 隐私说明

- RetroSprite endpoint **默认仅监听 `127.0.0.1`**（loopback），不向局域网或公网暴露。
- RetroArch 截图 / 标签 / 按键状态仅在本机 SQLite 中作为请求日志保存，可在 Diagnostics 屏一键清空。
- 默认不产生外部 LLM 请求；只有用户在 Settings 中显式配置 BYOK provider，且当前问题已有本地 evidence 时，才会向该 provider 发送短 prompt。无 evidence、GKP 被禁用或未配置 key 时不会调用 LLM。
- 不会上传 ROM、不会上传游戏存档、不会读取截图以外的任何系统截屏。

---

相关文档：

- [Phase 0 验收清单](./PHASE0_VERIFICATION.md)
- [RetroArch AI Service 协议速查](./PROTOCOL_REFERENCE.md)
- 上层规划：[../../RetroSprite_Development_Plan.md](../../RetroSprite_Development_Plan.md)
