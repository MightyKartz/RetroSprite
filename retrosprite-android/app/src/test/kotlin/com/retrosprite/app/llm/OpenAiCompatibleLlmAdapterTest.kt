package com.retrosprite.app.llm

import com.retrosprite.app.domain.models.LlmRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class OpenAiCompatibleLlmAdapterTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `complete posts chat completion request and parses response`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "id": "chatcmpl-test",
                      "choices": [
                        {
                          "index": 0,
                          "message": {
                            "role": "assistant",
                            "content": "  短答案  "
                          },
                          "finish_reason": "stop"
                        }
                      ],
                      "usage": {
                        "prompt_tokens": 12,
                        "completion_tokens": 5,
                        "total_tokens": 17
                      }
                    }
                    """.trimIndent()
                )
        )

        val adapter = adapter(apiKey = "test-key")
        val response = adapter.complete(
            LlmRequest(
                systemPrompt = "Use evidence only.",
                userPrompt = "What should I do next?",
                maxTokens = 64,
            )
        )

        assertEquals("短答案", response.text)
        assertEquals(12, response.tokensIn)
        assertEquals(5, response.tokensOut)
        assertTrue(response.latencyMs >= 0L)

        val recorded = server.takeRequest()
        assertEquals("/chat/completions", recorded.path)
        assertEquals("Bearer test-key", recorded.getHeader("Authorization"))
        assertEquals("application/json; charset=utf-8", recorded.getHeader("Content-Type"))

        val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals("deepseek-v4-pro", body["model"]?.jsonPrimitive?.contentOrNull)
        assertEquals(64, body["max_tokens"]?.jsonPrimitive?.int)
        assertEquals(0.2, body["temperature"]?.jsonPrimitive?.double)
        assertFalse(body["stream"]?.jsonPrimitive?.boolean ?: true)
        assertEquals("disabled", body["thinking"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull)

        val messages = body["messages"]?.jsonArray
        assertEquals(2, messages?.size)
        requireNotNull(messages)
        assertEquals("system", messages[0].jsonObject["role"]?.jsonPrimitive?.contentOrNull)
        assertEquals(
            "Use evidence only.",
            messages[0].jsonObject["content"]?.jsonPrimitive?.contentOrNull
        )
        assertEquals("user", messages[1].jsonObject["role"]?.jsonPrimitive?.contentOrNull)
        assertEquals(
            "What should I do next?",
            messages[1].jsonObject["content"]?.jsonPrimitive?.contentOrNull
        )
    }

    @Test
    fun `complete rejects missing api key before network call`() = runTest {
        val adapter = adapter(apiKey = "")

        try {
            adapter.complete(LlmRequest(systemPrompt = "system", userPrompt = "user"))
            fail("Expected missing API key to throw")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("requires an API key"))
        }

        assertEquals(0, server.requestCount)
    }

    @Test
    fun `complete maps provider error without leaking prompt or key`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "error": {
                        "message": "bad key secret-key for prompt hidden prompt",
                        "type": "authentication_error"
                      }
                    }
                    """.trimIndent()
                )
        )

        val adapter = adapter(apiKey = "secret-key")

        try {
            adapter.complete(
                LlmRequest(
                    systemPrompt = "system secret",
                    userPrompt = "hidden prompt",
                )
            )
            fail("Expected provider error to throw")
        } catch (e: IllegalStateException) {
            val message = e.message.orEmpty()
            assertTrue(message.contains("HTTP 401"))
            assertFalse(message.contains("secret-key"))
            assertFalse(message.contains("hidden prompt"))
            assertFalse(message.contains("system secret"))
            assertTrue(message.contains("[redacted]"))
        }
    }

    @Test
    fun `complete rejects empty choices`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[],"usage":{"prompt_tokens":1,"completion_tokens":0}}""")
        )

        try {
            adapter(apiKey = "test-key").complete(
                LlmRequest(systemPrompt = "system", userPrompt = "user")
            )
            fail("Expected empty choices to throw")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("returned no completion text"))
        }
    }

    private fun adapter(apiKey: String): OpenAiCompatibleLlmAdapter =
        OpenAiCompatibleLlmAdapter(
            LlmConfig.deepSeek(
                apiKey = apiKey,
                baseUrl = server.url("/").toString().trimEnd('/'),
            )
        )
}
