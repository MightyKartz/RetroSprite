# RetroArch AI Service 配置指南

> 目标：让你在约 10 分钟内完成 RetroArch 与 RetroSprite 的对接，按下热键即可在 Diagnostics 屏看到首次请求记录。

## 概述

RetroArch 内建一个名为 **AI Service** 的功能。当玩家在游戏中按下绑定的 AI Service 热键时，RetroArch 会冻结画面、截取一帧，并以 HTTP POST 的方式把 PNG 图像、当前 ROM 的 `system__game` 标签以及手柄/暂停状态发送到玩家自行配置的 URL。

RetroSprite Android 应用就是这个「自行配置的 URL」对应的本地服务端。它在手机/平板上启动一个监听 `127.0.0.1:8080` 的轻量 HTTP endpoint，接收 RetroArch 的请求，未来负责把问题路由到本地知识检索 + LLM 流水线，并按照 RetroArch 的协议返回字幕文本、覆盖图或 PCM 语音。

Phase 0 阶段我们只验证「链路打通」：RetroArch 能成功把数据送达 RetroSprite，RetroSprite 返回固定占位文本，玩家在 Diagnostics 屏可以看到完整的请求记录。

## 前置条件

- 已安装 **RetroSprite Android** 应用（Phase 0 Debug 构建即可）。
- 已安装 **RetroArch 1.16.0+**：Android 端或 PC 端均可（PC 1.16.0+ / Android 1.16.0+）。
- 部署形态满足以下任一项：
  - **同设备（推荐）**：RetroArch 与 RetroSprite 都跑在同一台 Android 设备上。
  - **跨设备同局域网**：Android 设备运行 RetroSprite，另一台机器（PC / 其他手机）运行 RetroArch，二者处于同一 Wi-Fi。

> 备注：Phase 0 默认仅监听 `127.0.0.1`，跨设备访问需要额外步骤，请见下文「不同部署场景」。

## 配置步骤

### 1. 启动 RetroSprite，确认 endpoint 运行

打开 RetroSprite App，进入 **Home** 屏，确认 endpoint 状态指示为「运行中（绿色）」，并显示监听端口（默认 `8080`）。
若未启动，点击 Home 屏的 **Start Endpoint** 按钮；首次启动会请求前台服务通知权限，请允许。

### 2. 记录 endpoint URL

根据部署场景选择 URL：

| 场景 | URL |
| ---- | ---- |
| 同设备（RetroArch 与 RetroSprite 均在 Android 上） | `http://localhost:8080?output=text` |
| 跨设备（RetroArch 在 PC，RetroSprite 在 Android） | `http://<Android-IP>:8080?output=text` |

> `<Android-IP>` 可在 RetroSprite 的 Settings 屏查看，或在系统 **设置 → 关于本机 → 状态信息 → IP 地址** 中获取。

> 重要：RetroSprite **默认仅监听 `127.0.0.1`**，跨设备直连在 Phase 0 不开箱即用。Phase 1 计划提供「允许局域网访问（绑定 0.0.0.0）」开关。Phase 0 阶段强烈建议先在同设备上验证。

### 3. 进入 RetroArch AI Service 配置页

RetroArch 主菜单 → **Settings → AI Service**。

### 4. 填写以下字段

| 字段 | 推荐值 |
| ---- | ---- |
| AI Service | **ON** |
| AI Service URL | 步骤 2 记录的 URL |
| AI Service Mode | **Image Mode**（Phase 0 仅支持图像模式） |
| AI Service Output | **Text** 或 **Subtitles**（推荐 Subtitles，叠加在游戏画面上） |
| Source Language | Auto，或当前游戏文本语言 |
| Target Language | 中文（如需翻译） / None |

### 5. 绑定 AI Service 热键

进入 **Settings → Input → Hotkeys → AI Service**，绑定一个易触发的组合：

- 推荐：长按 `Select + Start`，或 `L3 + R3`（手柄按摇杆）。
- 也可绑定到键盘单键，便于桌面 RetroArch 调试。

### 6. 启动游戏并触发

载入任意游戏 ROM，按下上一步绑定的热键。RetroArch 会冻结画面并显示发送状态。

### 7. 在 RetroSprite 验证

切回 RetroSprite App，进入 **Diagnostics** 屏：

- 应出现一条新的请求记录，包含 `label`、`output=text`、请求时间戳。
- 默认占位响应文本（例如 `RetroSprite connected. Ask me anything about your game!`）会以字幕或文本形式回显在 RetroArch 中。

至此 Phase 0 链路已打通。

## 常见问题排查（FAQ）

| 现象 | 排查方向 |
| ---- | ---- |
| RetroSprite 没有收到任何请求 | 1) Home 屏 endpoint 是否运行中；2) RetroArch 中 URL 拼写、端口、`?output=text` 是否完整；3) RetroArch 端 AI Service 是否 ON；4) 防火墙 / 路由器是否阻断；5) 跨设备时是否使用了正确的 Android IP。 |
| RetroArch 提示 `Connection refused` | 1) 端口被其他应用占用，可在 RetroSprite Settings 屏修改端口；2) RetroSprite 进程被系统杀死，重新启动；3) 跨设备时未启用「允许局域网访问」（Phase 0 暂未开放）。 |
| RetroArch 提示超时 / Image too large | 1) RetroArch 截图尺寸过大，降低分辨率或缩小窗口；2) Wi-Fi 不稳定，改为同设备验证；3) RetroSprite 处于后台被系统冻结，调整电池优化白名单。 |
| RetroArch 中显示 `N/A` 或没有字幕 | 1) `AI Service Output` 与 URL 中 `output=` 不一致；2) Phase 0 仅返回 `text` 字段，不支持 `image` / `sound`，请确保 Output 选 `Text` 或 `Subtitles`。 |
| Diagnostics 屏看到请求但 RetroArch 一直「Loading」 | 检查 RetroSprite 日志中是否抛错；可执行 `scripts/test_endpoint.sh` 快速复现。 |
| Android 系统横幅提示后台限制 | 在系统 **设置 → 应用 → RetroSprite → 电池 → 不受限制** 中放行。 |

## 不同部署场景

### 场景 A：同一台 Android 设备（推荐 Phase 0）

- RetroArch Android 与 RetroSprite Android 同机。
- URL 使用 `http://localhost:8080?output=text`。
- 无需任何网络/防火墙配置。

### 场景 B：PC 上的 RetroArch + Android 上的 RetroSprite

- 二者必须处于同一局域网（同一 Wi-Fi）。
- URL 使用 `http://<Android-IP>:8080?output=text`。
- 由于 Phase 0 默认 bind 在 `127.0.0.1`，PC 实际无法连入。**Phase 0 暂不推荐此场景**，请等待 Phase 1 的「允许局域网访问」开关。
- 临时方案：使用 `adb reverse tcp:8080 tcp:8080`，让 PC 上的 RetroArch 通过 `http://localhost:8080` 转发到手机端。

### 场景 C：Android 模拟器（AVD）+ 桌面 RetroSprite

- 当前 RetroSprite 仅 Android，不适用此场景。

## 隐私说明

- RetroSprite endpoint **默认仅监听 `127.0.0.1`**（loopback），不向局域网或公网暴露。
- RetroArch 截图 / 标签 / 按键状态仅在本机 SQLite 中作为请求日志保存，可在 Diagnostics 屏一键清空。
- Phase 0 阶段没有任何外部网络调用；后续 Phase 启用 LLM 时，仅向用户在 Settings 中显式配置的 provider 发送数据，遵循 BYOK（自带密钥）模式。
- 不会上传 ROM、不会上传游戏存档、不会读取截图以外的任何系统截屏。

---

相关文档：

- [Phase 0 验收清单](./PHASE0_VERIFICATION.md)
- [RetroArch AI Service 协议速查](./PROTOCOL_REFERENCE.md)
- 上层规划：[../../RetroSprite_Development_Plan.md](../../RetroSprite_Development_Plan.md)
