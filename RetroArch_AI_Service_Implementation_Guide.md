# RetroArch AI Service 协议实现指南 (Ktor)

## 快速参考

### HTTP 方法
```
POST <base_url>?output=<format>[&source_lang=<lang>][&target_lang=<lang>]
```

### 请求体 (JSON)
```json
{
  "image": "base64_encoded_png",
  "label": "system__game",
  "state": {
    "paused": 1,
    "a": 0, "b": 0, "x": 0, "y": 0,
    "select": 0, "start": 0,
    "up": 0, "down": 0, "left": 0, "right": 0,
    "l": 0, "r": 0, "l2": 0, "r2": 0, "l3": 0, "r3": 0
  }
}
```

### 响应体 (JSON, 所有字段可选)
```json
{
  "image": "base64_encoded_png_or_bmp",
  "sound": "base64_encoded_wav",
  "text": "translation or extracted text",
  "text_position": 1,
  "press": ["a", "b"],
  "auto": "auto",
  "error": "error message"
}
```

## 关键协议细节

### 1. 输出格式 (output parameter)
- `image,png` - 图像模式 (mode=0)
- `sound,wav` - 语音模式 (mode=1)
- `text` - 文本/叙述者模式 (mode=2)
- `sound,wav,image,png` - 组合模式 (mode=3)

### 2. 图像编码
- **格式**: PNG (不是 BMP!)
- **色空间**: BGR24 (蓝-绿-红)
- **编码**: Base64 (PNG 文件字节)
- **重要**: 完整的 PNG 文件格式, 包含 PNG 签名和所有 chunks

### 3. 自动模式 (Automatic Polling)
- 响应中的 `"auto": "auto"` 继续轮询
- 响应中的 `"auto": "continue"` 跳过显示但继续轮询
- 无 auto 字段或其他值 = 停止轮询
- 轮询间隔由 `ai_service_poll_delay` 控制 (默认 200ms)

### 4. 状态字段 (state object)
- 所有 16 个 RetroPad 按钮的当前按下状态 (0 或 1)
- 游戏暂停状态 (paused: 0 或 1)
- 捕获请求时刻的快照

### 5. 标签字段 (label)
- 格式: `<system_id>__<game_id>`
- 例: `snes__chrono_trigger`, `genesis__sonic`
- 用于上下文相关的处理

## Ktor 实现框架

```kotlin
// 依赖
implementation("io.ktor:ktor-server-core:$ktor_version")
implementation("io.ktor:ktor-server-json:$ktor_version")
implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

// 数据模型
data class AIServiceRequest(
    val image: String,        // Base64 encoded PNG
    val label: String?,
    val state: GamepadState?
)

data class GamepadState(
    val paused: Int,
    val a: Int, val b: Int, val x: Int, val y: Int,
    val select: Int, val start: Int,
    val up: Int, val down: Int, val left: Int, val right: Int,
    val l: Int, val r: Int, val l2: Int, val r2: Int,
    val l3: Int, val r3: Int
)

data class AIServiceResponse(
    val image: String? = null,         // Base64 encoded image
    val sound: String? = null,         // Base64 encoded WAV
    val text: String? = null,
    val text_position: Int? = null,    // 1=bottom, 2=top
    val press: List<String>? = null,
    val auto: String? = null,          // "auto" or "continue"
    val error: String? = null
)

// 端点实现
fun Route.aiService() {
    post("/ai-service") {
        // 1. 解析查询参数
        val output = call.request.queryParameters["output"] 
            ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "output parameter required")
            )
        val sourceLang = call.request.queryParameters["source_lang"]
        val targetLang = call.request.queryParameters["target_lang"]
        
        // 2. 解析请求体
        val request = call.receive<AIServiceRequest>()
        
        // 3. 验证图像
        val imageBytes = try {
            Base64.getDecoder().decode(request.image)
        } catch (e: Exception) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                AIServiceResponse(error = "Invalid base64 image")
            )
        }
        
        // 验证 PNG 签名
        val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        if (!imageBytes.take(4).toByteArray().contentEquals(pngSignature)) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                AIServiceResponse(error = "Invalid PNG format")
            )
        }
        
        // 4. 处理请求
        val response = try {
            processAIServiceRequest(
                imageBytes = imageBytes,
                label = request.label,
                state = request.state,
                output = output,
                sourceLang = sourceLang,
                targetLang = targetLang
            )
        } catch (e: Exception) {
            AIServiceResponse(error = "Processing failed: ${e.message}")
        }
        
        // 5. 返回响应
        call.respond(HttpStatusCode.OK, response)
    }
}

// 处理函数
suspend fun processAIServiceRequest(
    imageBytes: ByteArray,
    label: String?,
    state: GamepadState?,
    output: String,
    sourceLang: String?,
    targetLang: String?
): AIServiceResponse {
    // 根据 output 参数决定处理类型
    return when {
        output.contains("text") -> {
            // OCR + 翻译
            val text = performOCRAndTranslation(imageBytes, sourceLang, targetLang)
            AIServiceResponse(
                text = text,
                auto = "auto"  // 支持自动模式
            )
        }
        output.contains("sound") -> {
            // 文本到语音
            val audioBytes = synthesizeAudio(extractText(imageBytes), targetLang)
            AIServiceResponse(
                sound = Base64.getEncoder().encodeToString(audioBytes),
                auto = "auto"
            )
        }
        output.contains("image") -> {
            // 图像处理
            val processedImage = processImage(imageBytes)
            AIServiceResponse(
                image = Base64.getEncoder().encodeToString(processedImage),
                auto = "auto"
            )
        }
        else -> AIServiceResponse(error = "Unsupported output format: $output")
    }
}
```

## 关键要点

### ✓ 必须做
1. **接受 POST 请求** 带 JSON 体
2. **解析查询参数** `output`, `source_lang`, `target_lang`
3. **Base64 解码** 图像数据
4. **验证 PNG 格式** (检查文件签名)
5. **返回 JSON 响应** (所有字段可选)
6. **Base64 编码** 输出图像/音频
7. **支持 auto 字段** 用于自动轮询

### ✗ 不要做
1. **不要期望 BMP** 格式 (已弃用, 但可兼容)
2. **不要直接发送原始像素** (必须 PNG + Base64)
3. **不要忽视 output 参数** (这控制响应类型)
4. **不要在没有 error 字段时返回错误状态码** (通常返回 200 + JSON error)
5. **不要修改查询参数** (仅追加参数)

## 错误处理

### HTTP 级别错误
```
400 Bad Request - 无效的 output 参数或解析失败
500 Internal Server Error - 处理失败
```

### 应用级别错误
```json
{
  "error": "Human readable error message"
}
```

返回 200 OK + JSON error 字段 (不是 HTTP 错误状态码)

## 测试请求示例

```bash
# 测试文本模式
curl -X POST "http://localhost:8080/ai-service?output=text&source_lang=jpn&target_lang=en" \
  -H "Content-Type: application/json" \
  -d '{
    "image": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
    "label": "snes__test",
    "state": {
      "paused": 1,
      "a": 0, "b": 0, "x": 0, "y": 0,
      "select": 0, "start": 0,
      "up": 0, "down": 0, "left": 0, "right": 0,
      "l": 0, "r": 0, "l2": 0, "r2": 0, "l3": 0, "r3": 0
    }
  }'

# 测试音频模式
curl -X POST "http://localhost:8080/ai-service?output=sound,wav" \
  -H "Content-Type: application/json" \
  -d '...'

# 测试图像模式
curl -X POST "http://localhost:8080/ai-service?output=image,png" \
  -H "Content-Type: application/json" \
  -d '...'
```

## 参考实现

- **VGTranslate**: https://gitlab.com/spherebeaker/vgtranslate
- **VGTranslate Local**: https://github.com/objaction/vgtranslate_local
- **RetroArch Source**: https://github.com/libretro/RetroArch/blob/master/tasks/task_translation.c

## 文档来源

- Libretro 官方文档: https://docs.libretro.com/guides/ai-service/
- RetroArch Network Control: https://docs.libretro.com/development/retroarch/network-control-interface/
- RetroArch 源代码: task_translation.c, config.def.h
