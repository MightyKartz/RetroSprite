# Phase 0 验收清单

> 本文档列出 Phase 0「协议验证」阶段的全部验收点。完成 = 自动化验证全部通过 + 手动清单全部勾选。

## 验收目标

证明从 RetroArch 到 RetroSprite Android endpoint 的链路可行：

1. RetroSprite App 可启动一个稳定的本地 HTTP endpoint。
2. 该 endpoint 严格遵循 RetroArch AI Service 协议，能接收并解析 `image / label / state` 请求体。
3. 即使收到畸形输入也不会让 App 崩溃。
4. RetroArch 实机配置后，按下 AI Service 热键能在 RetroSprite Diagnostics 屏看到完整请求记录。

Phase 0 原始验收不要求真实的游戏知识检索或 LLM 应答；后续历史版本曾用 `sample-2048` 与 `sample-relay-station` 验证 debug question route。M17 当前发布范围已改为 README 列出的 6 个真实游戏 GKP，sample 包不再随 APK 内置。

## 自动化验证（curl）

> 使用项目根目录提供的脚本 `scripts/test_endpoint.sh` 一键执行，或按下方逐步操作。
> 测试图像使用一个有效的 1×1 透明 PNG（RFC 标准最小 PNG），Base64 已内联在命令里。

### 1) 健康检查

```bash
curl -i 'http://localhost:4404/health'
```

预期：

- HTTP 状态码 `200 OK`
- 响应体 JSON：

```json
{"status":"ok","version":"0.1.0"}
```

### 2) 标准请求（暂停帧 + 完整 17 按钮 state）

```bash
curl -X POST 'http://localhost:4404/?output=text' \
  -H 'Content-Type: application/json' \
  -d '{
    "image": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=",
    "label": "snes__super_mario_world",
    "state": {
      "paused": 1,
      "a": 0, "b": 0, "x": 0, "y": 0,
      "select": 0, "start": 0,
      "up": 0, "down": 0, "left": 0, "right": 0,
      "l": 0, "r": 0, "l2": 0, "r2": 0, "l3": 0, "r3": 0
    }
  }'
```

预期：

- HTTP 状态码 `200 OK`
- 响应体包含 `text` 字段，不包含 `error` 字段。当前若 label 没有对应 GKP，会返回“暂时不能给可靠答案”这类不确定性提示，而不是猜测。

> 也可直接使用项目根 `scripts/sample_payload.json` 作为请求体：
> `curl -X POST 'http://localhost:4404/?output=text' -H 'Content-Type: application/json' --data @scripts/sample_payload.json`

### 3) 畸形 JSON 不崩溃

```bash
curl -i -X POST 'http://localhost:4404/?output=text' \
  -H 'Content-Type: application/json' \
  --data '{not valid json'
```

预期：

- HTTP 状态码 `200 OK`（RetroArch 协议约定即使输入无效也回 200）
- 响应体包含 `error` 字段，例如：

```json
{"error":"invalid_payload"}
```

- App 无 ANR、无崩溃、无前台服务退出。

### 4) 一键脚本

```bash
cd retrosprite-android
./scripts/test_endpoint.sh
# 自定义端口： PORT=8081 ./scripts/test_endpoint.sh

# AVD/真机端到端 smoke：自动检查/安装 Debug APK、启动 App、
# adb forward、endpoint smoke、6 个真实内置 GKP debug 问答和 latest-request 回读
./scripts/android_avd_smoke.sh
```

脚本会依次跑健康检查、标准请求、畸形请求三步并打印响应。

> 在 PC 上访问 Android 设备时，先执行：`adb forward tcp:4404 tcp:4404`。

## 手动验证清单

按顺序在真机上勾选：

- [ ] App 启动后 Home 屏显示 endpoint **运行中（绿色）**，并展示监听端口。
- [ ] 在浏览器或 curl 中访问 `http://localhost:4404/health` 返回有效 JSON。
- [ ] 执行上文「标准请求」curl，返回 `text` JSON；未知游戏会给出不确定性提示，内置真实 GKP 可通过 `/debug/ask` 返回带来源的本地 evidence 答案。
- [ ] Diagnostics 屏出现一条新的请求记录，时间、`label`、`output` 字段完整。
- [ ] 关闭 / 强制停止 App 后，再次 curl `http://localhost:4404/health` 不再响应（连接被拒绝）。
- [ ] 重新启动 App 后，无需手动操作，endpoint 自动恢复并能接收请求。
- [ ] 发送畸形 JSON 后，App 没有崩溃、没有 ANR、Diagnostics 屏依然可正常打开。
- [ ] 执行 `./scripts/android_avd_smoke.sh` 后，`scripts/gkp_debug_cases.tsv` 覆盖的 6 个真实内置 GKP debug 问答都返回 `pipeline_stage=evidence`、`llm_status=skipped`，并在 latest-request 中看到对应 source id。
- [x] 在 RetroArch 配置 AI Service URL（参见 [RETROARCH_SETUP.md](./RETROARCH_SETUP.md)），载入任意游戏，按下 AI Service 热键，Diagnostics 屏出现来自 RetroArch 的请求记录。

> 2026-05-19 AVD 实测备注：`RetroSprite_API_34` 上官方 RetroArch Android APK 已安装、2048 core 可运行。实体键盘热键已触发真实 AI Service POST，Room `request_logs` 记录 `id=14`、`2026-05-19 13:00:08`、`label=2048__`、`image_size=1796`、`paused=1`、`output_mode=text`、无 `error_message`。ADB 键盘/gamepad/overlay/network-command 自动注入仍不可靠，但不再阻塞 Phase 0 验收。详见 [RETROARCH_ANDROID_AI_SERVICE_FINDINGS.md](./RETROARCH_ANDROID_AI_SERVICE_FINDINGS.md)。

## 性能基线（参考，非阻塞）

Phase 0 不做严格 SLA，仅作回归参照：

| 指标 | 参考值 |
| ---- | ---- |
| 单次请求 P50 端到端延迟（curl ↔ App，短文本响应） | < 100 ms |
| 1 分钟 60 次请求（1 QPS）后 RSS 内存增长 | 无明显增长（< 5 MB） |
| 连续 30 分钟空载 | 前台服务不被系统杀死 |

> 测量建议：使用 `hyperfine` 或 `wrk` 在 `adb forward` 后从 PC 端跑 60 次。

## 已知限制

Phase 0 原始阶段下列功能当时尚未实现，**不在 Phase 0 验收范围内**；当前 M17 发布口径以 README、`docs/TEST_COVERAGE.md` 和 release checklist 为准：

- 原始 RetroArch AI Service 请求仍只提供截图/label/state，不包含玩家自然语言问题；当前真实问答主路径是 hotkey voice overlay，debug 验证仍可使用 `/debug/ask`。
- 当前内置确定性本地答案来自 6 个真实游戏 GKP；没有匹配 GKP 或没有 evidence 时仍会进入不确定性路径。
- LLM Adapter 已支持 BYOK OpenAI-compatible API；AnswerPolicy 要求有本地 evidence 才允许调用，默认不会无证据猜答案。画面翻译推荐模型为 `Qwen/Qwen3-VL-8B-Instruct`。
- 不支持 `output=image` / `output=sound` / `output=combined`，仅支持 `output=text`。
- 不支持跨设备访问（默认仅 `127.0.0.1`），如需 PC 调试请使用 `adb forward` / `adb reverse`。
- 当前已支持 App/Hotkey 语音输入；RetroArch 的 `Sound Mode` 仍不在发布范围内。
- 不做 ROM 合法性、字段越界等高级校验，仅保证不崩溃。
- ADB 键盘/gamepad/overlay/network-command 自动注入仍不能可靠代表实体输入；真实 AI Service 热键验证以实体键盘/手柄或手动桌面输入为准。

---

相关文档：

- [RetroArch 配置指南](./RETROARCH_SETUP.md)
- [协议速查](./PROTOCOL_REFERENCE.md)
- 上层规划：[../../RetroSprite_Development_Plan.md](../../RetroSprite_Development_Plan.md)
