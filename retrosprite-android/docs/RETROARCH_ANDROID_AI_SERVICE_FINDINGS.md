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

4. RetroSprite endpoint 在 AVD 内运行，host 侧通过 `adb forward tcp:18080 tcp:4404` 可访问：

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

7. RetroArch 配置文件中可见的 AI Service 基线：

   ```ini
   ai_service_enable = "true"
   ai_service_mode = "0"
   ai_service_pause = "true"
   ai_service_url = "http://localhost:4404"
   input_ai_service = "k"
   input_ai_service_axis = "nul"
   input_ai_service_btn = "nul"
   input_ai_service_mbtn = "nul"
   ```

   > 备注：本轮成功触发来自手动桌面/实体键盘路径；AVD/RetroArch 运行态热键绑定可能不会立刻落盘到 `retroarch.cfg`，以实际 `request_logs` 成功请求为验收依据。

8. 实体键盘/手动桌面输入已触发官方 RetroArch Android APK 的 AI Service POST，并由 RetroSprite 修复版成功解析：

   ```text
   request_logs id=14
   ts=2026-05-19 13:00:08
   label=2048__
   image_size=1796
   paused=1
   output_mode=text
   response_text=RetroSprite 已连接，目前还在 Phase 0 协议验证阶段。Phase 1 将接入游戏知识库。
   error_message=<empty>
   ```

9. 兼容性修复结论：真实 RetroArch Android APK 会用 `application/x-www-form-urlencoded` Content-Type 发送 JSON body；RetroSprite endpoint 已改为读取原始 body 后按 RetroArch JSON 解码，避免 Ktor ContentNegotiation 按错误 Content-Type 拒收。

## 仍未验证成功的自动注入路径

以下路径均未可靠地产生 AI Service 请求，不能替代实体键盘/手柄或手动桌面输入：

| 尝试路径 | 结果 |
| --- | --- |
| `adb shell input keyevent 138` 注入 F8 | 无请求 |
| 将 `input_ai_service` 临时绑定到 `space` / `enter` | 无请求 |
| 将 `input_ai_service_btn` 临时绑定到 gamepad button | 无请求 |
| 临时把触屏 overlay 的 `toggle_fast_forward` 改成 `ai_service` | 无请求 |
| 启用 `network_cmd_enable` 并尝试 UDP `AI_SERVICE` | Android build 未监听 `55355` |

## 关键判断

- RetroSprite endpoint 和官方 RetroArch Android APK 的 AI Service 手动触发链路已经打通。
- RetroSprite 需要兼容 RetroArch 的真实 HTTP 行为：JSON body 不一定配套 `application/json` Content-Type。
- RetroArch content 启动也不是阻塞点：2048 core 已运行。
- ADB 自动注入不是可靠验收信号，可能卡在：
  - AVD 键盘/gamepad 注入没有进入 RetroArch hotkey 层；
  - Android 官方 build 对 network command 未初始化；
  - overlay meta bind 对 `ai_service` 行为与 desktop 不一致；
  - AI Service 触发需要实体键盘/手柄或真实桌面输入路径。

## 推荐下一步

1. 进入 GKP MVP：冻结 GKP v0 schema，增加内置 sample pack 和 golden Q&A。
2. 在 RetroSprite 中增加 debug route 或 UI 字段展示“最后请求时间/最后错误/最近 label”，降低后续联调成本。
3. 保留 RetroSprite 侧 endpoint smoke 作为稳定基线，不要用 DeepSeek API 接入掩盖检索/证据链问题。
4. 后续若需要完全自动化端到端热键测试，再考虑构建 RetroArch Android debug 版或引入更可控的输入设备模拟。

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
