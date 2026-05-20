# RetroArch AI Service 协议速查

> 本文档摘录 RetroSprite 当前实现所遵循的 RetroArch AI Service HTTP 协议，便于排错与对照。完整规范以官方文档为准。

## 协议来源

- 官方文档：<https://docs.libretro.com/guides/ai-service/>
- 相关源码（libretro/RetroArch）：`tasks/task_translation.c` 中的 HTTP 请求构造逻辑。
- 网络控制接口参考：<https://docs.libretro.com/development/retroarch/network-control-interface/>

> 协议在不同 RetroArch 版本间偶有微调，RetroSprite 以 **RetroArch 1.16.0+** 为基准。

## 端点

| 路径 | 方法 | 用途 |
| ---- | ---- | ---- |
| `/` | `POST` | 主请求入口，由 RetroArch 在用户按下 AI Service 热键时发起 |
| `/health` | `GET` | RetroSprite 自定义健康检查（非 RetroArch 协议的一部分），用于诊断与脚本探活 |
| `/debug/latest-request` | `GET` | RetroSprite 自定义 loopback 诊断端点，返回最近一条请求摘要 |

默认监听地址：`127.0.0.1:4404`（loopback only），RetroArch 侧推荐使用默认 AI Service URL `http://localhost:4404`。

## 查询参数（Query Parameters）

RetroArch 在 URL 上附加这些参数，用以表达「我希望你用什么形式回我」：

| 参数 | 类型 | 默认 | 说明 |
| ---- | ---- | ---- | ---- |
| `output` | string | `text` | 期望响应模式，见下文「output 模式」 |
| `source_lang` | string | 空 | 源语言（ISO 639-1，如 `ja`、`en`），由 RetroArch 配置传入 |
| `source_target` | string | 空 | 目标语言（ISO 639-1，如 `zh`），用于翻译类场景 |

> 注意：RetroArch 的字段在不同版本中也可能写作 `target_lang`，RetroSprite 同时接受两者，未识别的参数会被忽略。

## 请求体（Request Body）

`Content-Type: application/json`，字段如下：

| 字段 | 类型 | 必填 | 说明（中文） | English |
| ---- | ---- | :--: | ---- | ---- |
| `image` | string (Base64) | 是 | RetroArch 当前帧的 PNG 截图，Base64 编码（不含 `data:` 前缀） | Base64-encoded PNG screenshot of the current frame |
| `label` | string | 是 | 形如 `system__game` 的 ROM 标签，例如 `snes__super_mario_world`；用于路由到对应 GKP | ROM label `system__game`, used for GKP routing |
| `state` | object | 是 | 当前手柄/暂停状态，见下文「state 字段」 | Controller and pause state |
| `question` | string | 否 | RetroSprite App/debug 扩展字段；官方 RetroArch AI Service 请求通常不带自然语言问题 | RetroSprite app/debug extension for player text questions |
| `spoiler_level` | string | 否 | RetroSprite App/debug 扩展字段；`light` / `clear` / `direct`，未提供时使用 Settings 默认剧透级别 | RetroSprite app/debug extension; `light` / `clear` / `direct` |

> 字段顺序由 RetroArch 决定，解析方不应假设顺序。

### state 字段

每个键对应 RetroPad 的标准按钮，值为 `0`（未按下）/ `1`（按下）。Phase 0 共 17 个键：

| 键名 | 含义（中文） | RetroPad 映射 |
| ---- | ---- | ---- |
| `paused` | 游戏是否处于暂停 | RetroArch pause flag |
| `a` | A 键 | RETRO_DEVICE_ID_JOYPAD_A |
| `b` | B 键 | RETRO_DEVICE_ID_JOYPAD_B |
| `x` | X 键 | RETRO_DEVICE_ID_JOYPAD_X |
| `y` | Y 键 | RETRO_DEVICE_ID_JOYPAD_Y |
| `select` | Select | RETRO_DEVICE_ID_JOYPAD_SELECT |
| `start` | Start | RETRO_DEVICE_ID_JOYPAD_START |
| `up` | 方向上 | RETRO_DEVICE_ID_JOYPAD_UP |
| `down` | 方向下 | RETRO_DEVICE_ID_JOYPAD_DOWN |
| `left` | 方向左 | RETRO_DEVICE_ID_JOYPAD_LEFT |
| `right` | 方向右 | RETRO_DEVICE_ID_JOYPAD_RIGHT |
| `l` | L 肩键 | RETRO_DEVICE_ID_JOYPAD_L |
| `r` | R 肩键 | RETRO_DEVICE_ID_JOYPAD_R |
| `l2` | L2 扳机 | RETRO_DEVICE_ID_JOYPAD_L2 |
| `r2` | R2 扳机 | RETRO_DEVICE_ID_JOYPAD_R2 |
| `l3` | 左摇杆按下 | RETRO_DEVICE_ID_JOYPAD_L3 |
| `r3` | 右摇杆按下 | RETRO_DEVICE_ID_JOYPAD_R3 |

> 缺失的键解析为 `0`。RetroSprite 不假设核心 / 平台一定支持所有按钮（NES、Game Boy 等并不会都按下 L3/R3）。

### 完整请求示例

```json
{
  "image": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=",
  "label": "snes__super_mario_world",
  "question": "现在应该往哪里走？",
  "spoiler_level": "light",
  "state": {
    "paused": 1,
    "a": 0, "b": 0, "x": 0, "y": 0,
    "select": 0, "start": 0,
    "up": 0, "down": 0, "left": 0, "right": 0,
    "l": 0, "r": 0, "l2": 0, "r2": 0, "l3": 0, "r3": 0
  }
}
```

## 响应体（Response Body）

`Content-Type: application/json`，HTTP 状态码 **始终为 200**（即便处理出错；错误信息走 `error` 字段）。所有字段均为可选，按 `output` 模式取用：

| 字段 | 类型 | 说明（中文） | English |
| ---- | ---- | ---- | ---- |
| `text` | string | 字幕 / 文本应答，`output=text` 或 `subtitles` 时使用 | Subtitle / chat text |
| `image` | string (Base64) | Base64 编码的覆盖图（PNG），`output=image` 时使用 | Base64 PNG overlay |
| `sound` | string (Base64) | Base64 编码的 PCM/WAV 语音，`output=sound` 时使用 | Base64 PCM/WAV audio |
| `error` | string | 错误码或错误说明；若存在，RetroArch 会显示「N/A」并尝试取消叠加 | Error code/message |
| `auto` | string | `continue` / `pause`：RetroArch 收到响应后是否继续运行游戏 | Continuation hint |

### 当前 text 响应示例

```json
{"text":"把两个相同数字向同一方向滑动即可合并。例如两个 2 相撞后会变成 4。来源：sample.2048.rules"}
```

错误样例：

```json
{"error":"invalid_payload"}
```

## output 模式

| 模式 | 适用响应字段 | 说明 |
| ---- | ---- | ---- |
| `text` | `text` | RetroArch 以系统消息（OSD）样式短暂显示文本 |
| `subtitles` *(部分版本)* | `text` | 与 `text` 类似，但持续显示直至下一帧字幕（适合翻译场景） |
| `image` | `image` | 覆盖一张 PNG 在游戏画面上 |
| `sound` | `sound` | 播放一段 PCM 语音（常用于 TTS 翻译） |
| `combined` | 任意组合 | 同时使用以上多种字段 |

> 当前仅实现 `text` 响应字段；`image`、`sound`、`combined` 仍在后续阶段。RetroArch 端选择 `Subtitles` 时，URL 通常仍是 `?output=text`，以 `text` 字段返回即可。

## Request log question metadata

RetroSprite 会在本地 `request_logs` 中记录实际被回答的问题：

| 字段 | 说明 |
| ---- | ---- |
| `question` | App/debug 显式传入的问题，或 pending hotkey 被消费的问题 |
| `question_source` | `app` / `debug` / `pending_hotkey` / `retroarch` |

`/debug/latest-request` 会返回同名字段，便于确认某次 RetroArch 热键是否消费了 Home 页准备好的 pending question。官方 RetroArch 当前通常不会在原始 body 中发送 `question`，所以普通热键请求在未消费 pending question 时这两个字段为空。

## /health（RetroSprite 自定义）

```http
GET /health HTTP/1.1
Host: localhost:4404
```

```json
{"status":"ok","version":"0.1.0"}
```

> 该端点不属于 RetroArch 协议，仅用于自动化探活与脚本验证；RetroArch 不会调用它。

## 默认运行参数

| 项 | 默认值 |
| ---- | ---- |
| 监听地址 | `127.0.0.1` |
| 监听端口 | `4404` |
| 前台服务通知文案 | `RetroSprite endpoint running on port {port}` |
| 协议版本 | RetroArch 1.16.0+ |

---

相关文档：

- [RetroArch 配置指南](./RETROARCH_SETUP.md)
- [Phase 0 验收清单](./PHASE0_VERIFICATION.md)
- 官方协议：<https://docs.libretro.com/guides/ai-service/>
