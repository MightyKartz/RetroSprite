package com.retrosprite.app.screen.translation

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OpenAiCompatibleScreenTranslationProvider(
    override val providerName: String,
    private val baseUrl: String,
    private val apiKey: String,
    override val model: String,
    timeoutSeconds: Long = 45L,
) : ScreenTranslationProvider {

    private val http = OkHttpClient.Builder()
        .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .build()

    override suspend fun translateScreenshotToChinese(
        imageBase64: String,
        context: ScreenTranslationContext,
    ): String {
        require(baseUrl.isNotBlank()) { "请先在 Settings 配置画面翻译 API Base URL。" }
        require(apiKey.isNotBlank()) { "请先在 Settings 配置画面翻译 API Key。" }
        require(model.isNotBlank()) { "请先在 Settings 配置画面翻译模型名。" }

        val payload = ChatCompletionRequest(
            model = model,
            messages = listOf(
                ChatMessage(
                    role = "user",
                    content = listOf(
                        ContentPart(
                            type = "text",
                            text = buildPrompt(context),
                        ),
                        ContentPart(
                            type = "image_url",
                            imageUrl = ImageUrl(url = "data:image/png;base64,$imageBase64"),
                        ),
                    ),
                )
            ),
            temperature = 0.0,
            maxTokens = 2048,
            stream = false,
        )

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", JSON_MEDIA_TYPE.toString())
            .post(json.encodeToString(ChatCompletionRequest.serializer(), payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("画面翻译 API 返回 HTTP ${response.code}。")
                }
                val decoded = json.decodeFromString(ChatCompletionResponse.serializer(), body)
                decoded.choices.firstOrNull()?.message?.content.orEmpty().trim()
            }
        }
    }

    @Serializable
    private data class ChatCompletionRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Double,
        @SerialName("max_tokens") val maxTokens: Int,
        val stream: Boolean,
    )

    @Serializable
    private data class ChatMessage(
        val role: String,
        val content: List<ContentPart>,
    )

    @Serializable
    private data class ContentPart(
        val type: String,
        val text: String? = null,
        @SerialName("image_url") val imageUrl: ImageUrl? = null,
    )

    @Serializable
    private data class ImageUrl(
        val url: String,
    )

    @Serializable
    private data class ChatCompletionResponse(
        val choices: List<Choice> = emptyList(),
    )

    @Serializable
    private data class Choice(
        val message: AssistantMessage? = null,
    )

    @Serializable
    private data class AssistantMessage(
        val content: String? = null,
    )

    private fun buildPrompt(context: ScreenTranslationContext): String {
        val glossary = context.glossary ?: return TRANSLATION_PROMPT
        val terms = glossary.terms
            .take(MAX_PROMPT_GLOSSARY_TERMS)
            .joinToString("\n") { "- ${it.source} = ${it.target} (${it.category})" }
        return buildString {
            append(TRANSLATION_PROMPT)
            append("\n\n当前游戏：")
            append(glossary.displayName)
            if (context.label.isNotBlank()) {
                append("\nRetroArch label：")
                append(context.label)
            }
            append(
                "\n请优先使用下列内置术语表；菜单、魔法、物品、人名、技能名和短提示即使很短也必须翻译。" +
                    "不要保留英文菜单原文，除非原文本身是 HP/MP 这类玩家熟悉的缩写。"
            )
            append("\n术语表：\n")
            append(terms)
        }
    }

    private companion object {
        const val TRANSLATION_PROMPT =
            "请识别这张复古游戏截图中可见的英文 UI、菜单、对白和提示文字，并翻译成简体中文。\n" +
                "如果是对白/剧情文本：只输出中文译文，保留有助于理解的换行和项目顺序，不输出英文原文，不解释。\n" +
                "如果是菜单/状态/物品/装备画面：必须输出严格 JSON，不要输出 Markdown，不要解释，格式为 " +
                "{\"mode\":\"menu\",\"entries\":[{\"source\":\"ITEM\",\"translation\":\"道具\",\"value\":\"\",\"type\":\"menu\"}]}。\n" +
                "菜单 JSON 规则：用于中英文对照速查表；source 填画面英文原文；translation 只填简体中文 UI 译名；value 填原样数字或数值，没有则为空；" +
                "type 使用 menu、equipment、stat、item、magic、skill、character、system、prompt 之一；" +
                "装备栏位如 RightHand、LeftHand、Head、Body 使用 type=equipment；装备/物品名称使用 type=item；属性和状态使用 type=stat。" +
                "纯数字、货币数值、HP/MP 数值、等级、百分比、装备参数数值不要作为独立条目；状态项必须保留标签和数值，不要把标签和值拆成两个 entries，" +
                "例如 Level 12、HP 344/344、Your Exp 12345 必须分别作为一个完整 stat entry；带英文标签的状态项可以保留，但数字必须原样保留；" +
                "不要把数值翻译成中文数字，不要输出截图描述、画面说明或推测。\n" +
                "若没有可翻译文字，输出：没有识别到可翻译的文字。"
        const val MAX_PROMPT_GLOSSARY_TERMS: Int = 80
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }
    }
}
