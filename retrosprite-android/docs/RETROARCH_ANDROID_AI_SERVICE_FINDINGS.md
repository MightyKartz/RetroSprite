# RetroArch Android AI Service 联调记录

> 日期：2026-05-19
> 设备：AVD `RetroSprite_API_34`，Android 14 / API 34，arm64
> RetroArch APK：官方 `RetroArch_aarch64.apk`，package `com.retroarch.aarch64`，version `1.22.2_GIT`
> RetroSprite APK：`app/build/outputs/apk/debug/app-debug.apk`，package `com.retrosprite.app`

## 已验证成功

1. `RetroSprite_API_34` 可启动并通过 `adb` 连接。
2. RetroArch Android 官方 APK 已安装：

   ```text
   package:com.retroarch.aarch64
   ```

3. RetroSprite Debug APK 已安装：

   ```text
   package:com.retrosprite.app
   ```

4. RetroSprite endpoint 在 AVD 内运行，host 侧通过 `adb forward tcp:18080 tcp:8080` 可访问：

   ```http
   GET http://127.0.0.1:18080/health
   -> {"status":"ok","version":"0.1.0"}
   ```

5. 手工模拟 RetroArch 请求可写入 Room `request_logs`：

   ```bash
   curl -X POST 'http://127.0.0.1:18080/?output=text' \
     -H 'Content-Type: application/json' \
     --data '{"image":"aGVsbG8=","label":"retrosprite_manual_smoke","state":{"paused":1,"a":1}}'
   ```

   响应：

   ```json
   {"text":"RetroSprite 已连接，目前还在 Phase 0 协议验证阶段。Phase 1 将接入游戏知识库。"}
   ```

6. RetroArch 已加载 2048 测试 core/content，游戏画面可进入棋盘，说明 content 启动链路正常。

7. RetroArch 配置已恢复到干净 AI Service 基线：

   ```ini
   ai_service_enable = "true"
   ai_service_mode = "0"
   ai_service_pause = "true"
   ai_service_url = "http://127.0.0.1:8080/?output=text"
   input_ai_service = "f8"
   input_ai_service_axis = "nul"
   input_ai_service_btn = "nul"
   input_ai_service_mbtn = "nul"
   ```

## 未验证成功

目标是让 RetroArch Android 官方 APK 在游戏内触发 AI Service，并向 RetroSprite 发出真实 HTTP POST。以下路径均未在 `request_logs` 中产生请求：

| 尝试路径 | 结果 |
| --- | --- |
| `adb shell input keyevent 138` 注入 F8 | 无请求 |
| 将 `input_ai_service` 临时绑定到 `space` / `enter` | 无请求 |
| 将 `input_ai_service_btn` 临时绑定到 gamepad button | 无请求 |
| 临时把触屏 overlay 的 `toggle_fast_forward` 改成 `ai_service` | 无请求 |
| 启用 `network_cmd_enable` 并尝试 UDP `AI_SERVICE` | Android build 未监听 `55355` |

## 关键判断

- RetroSprite endpoint 本身不是当前阻塞点：手工 POST 已验证请求解析、响应、Room 日志均正常。
- RetroArch content 启动也不是阻塞点：2048 core 已运行。
- 阻塞点在 RetroArch Android 官方 APK 的 AI Service 触发路径，可能位于：
  - AVD 键盘/gamepad 注入没有进入 RetroArch hotkey 层；
  - Android 官方 build 对 network command 未初始化；
  - overlay meta bind 对 `ai_service` 行为与 desktop 不一致；
  - AI Service 触发需要真实手柄/实体输入或不同 RetroArch activity 状态。

## 推荐下一步

1. 在真机上使用实体手柄或蓝牙键盘手动绑定 AI Service，排除 AVD 输入注入差异。
2. 若真机仍不触发，构建 RetroArch Android debug 版，在 `CMD_EVENT_AI_SERVICE_TOGGLE` / `CMD_EVENT_AI_SERVICE_CALL` / `run_translation_service` 处加 logcat。
3. 保留 RetroSprite 侧 endpoint smoke 作为稳定基线，不要用 DeepSeek API 接入掩盖触发问题。
4. 在 RetroSprite 中增加 debug route 或 UI 字段展示“最后请求时间/最后错误/最近 label”，降低后续联调成本。

## 当前后台模拟器状态

本轮结束时，`RetroSprite_API_34` 使用 `tmux` detached session 运行：

```bash
tmux ls | grep retrosprite-api34
adb devices -l
```

停止后台模拟器：

```bash
adb emu kill
# 或
tmux kill-session -t retrosprite-api34
```
