package com.retrosprite.app.llm

import com.retrosprite.app.domain.models.LlmRequest
import com.retrosprite.app.domain.models.LlmResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OpenAI-compatible chat completion adapter.
 *
 * This is intentionally non-streaming for the first DeepSeek BYOK milestone.
 * Streaming, tool calls, and reasoning controls can be layered onto the same
 * DTOs once the basic request/response contract is stable.
 */
class OpenAiCompatibleLlmAdapter(
    private val config: LlmConfig,
) : LlmAdapter {

    override val providerName: String = config.providerName
    override val modelName: String = config.model
    override val timeoutMs: Long = config.timeoutSeconds * 1_000L

    /** Visible for testing / future use. Configured per [LlmConfig.timeoutSeconds]. */
    val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        .connectTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        .build()

    override suspend fun complete(request: LlmRequest): LlmResponse {
        validateConfig()

        val startedAt = System.currentTimeMillis()
        val payload = ChatCompletionRequest(
            model = config.model,
            messages = listOf(
                ChatMessage(role = "system", content = request.systemPrompt),
                ChatMessage(role = "user", content = request.userPrompt),
            ),
            maxTokens = request.maxTokens,
            temperature = config.temperature,
            stream = false,
            thinking = deepSeekThinkingOverride(),
        )

        val httpRequest = Request.Builder()
            .url("${config.baseUrl.trim().trimEnd('/')}/chat/completions")
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", JSON_MEDIA_TYPE.toString())
            .post(json.encodeToString(ChatCompletionRequest.serializer(), payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return withContext(Dispatchers.IO) {
            http.newCall(httpRequest).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val latencyMs = System.currentTimeMillis() - startedAt
                if (!response.isSuccessful) {
                    throw IllegalStateException(providerError(response.code, body, request))
                }

                val completion = json.decodeFromString(ChatCompletionResponse.serializer(), body)
                val text = completion.choices
                    .firstOrNull()
                    ?.message
                    ?.content
                    ?.trim()
                    .orEmpty()

                if (text.isBlank()) {
                    throw IllegalStateException(
                        "LLM provider ${config.providerName} returned no completion text"
                    )
                }

                LlmResponse(
                    text = text,
                    tokensIn = completion.usage?.promptTokens ?: 0,
                    tokensOut = completion.usage?.completionTokens ?: 0,
                    latencyMs = latencyMs,
                )
            }
        }
    }

    private fun validateConfig() {
        require(config.baseUrl.isNotBlank()) {
            "LLM provider ${config.providerName} requires a baseUrl"
        }
        require(config.apiKey.isNotBlank()) {
            "LLM provider ${config.providerName} requires an API key"
        }
        require(config.model.isNotBlank()) {
            "LLM provider ${config.providerName} requires a model"
        }
    }

    private fun providerError(statusCode: Int, body: String, request: LlmRequest): String {
        val message = runCatching {
            json.decodeFromString(ChatCompletionError.serializer(), body).error?.message
        }.getOrNull()
            ?.redact(request)
            ?.take(300)
            ?: "empty error body"

        return "LLM provider ${config.providerName} returned HTTP $statusCode: $message"
    }

    private fun String.redact(request: LlmRequest): String {
        var sanitized = this
        listOf(config.apiKey, request.systemPrompt, request.userPrompt)
            .filter { it.isNotBlank() }
            .forEach { sensitive ->
                sanitized = sanitized.replace(sensitive, "[redacted]")
            }
        return sanitized
    }

    private fun deepSeekThinkingOverride(): DeepSeekThinking? =
        if (config.providerName.equals(LlmConfig.PROVIDER_DEEPSEEK, ignoreCase = true)) {
            DeepSeekThinking(type = "disabled")
        } else {
            null
        }

    @Serializable
    private data class ChatCompletionRequest(
        val model: String,
        val messages: List<ChatMessage>,
        @SerialName("max_tokens") val maxTokens: Int? = null,
        val temperature: Double,
        val stream: Boolean,
        val thinking: DeepSeekThinking? = null,
    )

    @Serializable
    private data class DeepSeekThinking(
        val type: String,
    )

    @Serializable
    private data class ChatMessage(
        val role: String,
        val content: String,
    )

    @Serializable
    private data class ChatCompletionResponse(
        val choices: List<Choice> = emptyList(),
        val usage: Usage? = null,
    )

    @Serializable
    private data class Choice(
        val message: ChatMessage? = null,
    )

    @Serializable
    private data class Usage(
        @SerialName("prompt_tokens") val promptTokens: Int = 0,
        @SerialName("completion_tokens") val completionTokens: Int = 0,
    )

    @Serializable
    private data class ChatCompletionError(
        val error: ProviderError? = null,
    )

    @Serializable
    private data class ProviderError(
        val message: String? = null,
        val type: String? = null,
        val code: String? = null,
    )

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }
    }
}
