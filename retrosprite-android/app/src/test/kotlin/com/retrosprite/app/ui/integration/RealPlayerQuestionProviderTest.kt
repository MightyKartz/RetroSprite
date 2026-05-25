package com.retrosprite.app.ui.integration

import com.retrosprite.app.endpoint.RequestLogger
import com.retrosprite.app.endpoint.ResponseGenerator
import com.retrosprite.app.endpoint.model.RetroArchRequest
import com.retrosprite.app.endpoint.model.RetroArchResponse
import com.retrosprite.app.endpoint.model.ResponseDiagnostics
import com.retrosprite.app.ui.viewmodel.UiSpoilerLevel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealPlayerQuestionProviderTest {

    @Test
    fun `ask routes player question through response generator and request log`() = runTest {
        val logger = RequestLogger()
        val generator = CapturingGenerator(
            RetroArchResponse.text(
                content = "把两个相同数字滑到一起会合并。\n来源：本地知识",
                diagnostics = ResponseDiagnostics(
                    sourceIds = listOf("sample.2048.rules"),
                    llmStatus = "used",
                    llmProvider = "deepseek",
                    llmModel = "deepseek-v4-pro",
                    llmMaxTokens = 256,
                    llmTimeoutMs = 30_000L,
                    llmLatencyMs = 1_234L,
                    llmTokensIn = 12,
                    llmTokensOut = 5,
                )
            )
        )
        val provider = RealPlayerQuestionProvider(
            responseGenerator = generator,
            loggerProvider = { logger },
        )

        val result = provider.ask(" 2048__ ", " 两个 2 怎么合并？ ")

        assertTrue(result.ok)
        assertEquals("2048__", result.label)
        assertEquals("两个 2 怎么合并？", result.question)
        assertEquals(listOf("sample.2048.rules"), result.sourceIds)
        assertEquals("evidence", result.pipelineStage)
        assertEquals("used", result.llmStatus)
        assertEquals("deepseek", result.llmProvider)
        assertEquals("deepseek-v4-pro", result.llmModel)
        assertEquals(256, result.llmMaxTokens)
        assertEquals(30_000L, result.llmTimeoutMs)
        assertEquals(1_234L, result.llmLatencyMs)
        assertEquals(12, result.llmTokensIn)
        assertEquals(5, result.llmTokensOut)
        assertTrue(result.durationMillis >= 0L)
        assertEquals("2048__", generator.request?.label)
        assertEquals("两个 2 怎么合并？", generator.request?.question)
        assertEquals("", generator.request?.spoilerLevel)
        assertEquals(1, generator.request?.state?.paused)
        assertEquals("text", generator.outputMode)

        val entry = logger.entries.value.first()
        assertEquals(entry.id, result.requestLogId)
        assertEquals("2048__", entry.label)
        assertEquals(RealPlayerQuestionProvider.OUTPUT_MODE, entry.outputMode)
        assertEquals("两个 2 怎么合并？", entry.question)
        assertEquals(RealPlayerQuestionProvider.QUESTION_SOURCE_APP, entry.questionSource)
        assertFalse(entry.isDebugRequest)
        assertEquals(listOf("sample.2048.rules"), entry.sourceIds)
        assertEquals("used", entry.llmStatus)
        assertEquals("deepseek", entry.llmProvider)
    }

    @Test
    fun `ask can pass one shot spoiler override through request model`() = runTest {
        val logger = RequestLogger()
        val generator = CapturingGenerator(RetroArchResponse.text("answer"))
        val provider = RealPlayerQuestionProvider(
            responseGenerator = generator,
            loggerProvider = { logger },
        )

        provider.ask(
            label = "relay_station__",
            question = "直接告诉我路线",
            spoilerLevelOverride = UiSpoilerLevel.Direct,
        )

        assertEquals("direct", generator.request?.spoilerLevel)
    }

    @Test
    fun `blank question returns validation error without logging`() = runTest {
        val logger = RequestLogger()
        val provider = RealPlayerQuestionProvider(
            responseGenerator = CapturingGenerator(RetroArchResponse.text("unused")),
            loggerProvider = { logger },
        )

        val result = provider.ask("", "   ")

        assertFalse(result.ok)
        assertEquals(RealPlayerQuestionProvider.DEFAULT_LABEL, result.label)
        assertEquals("missing_question", result.errorMessage)
        assertEquals("error", result.pipelineStage)
        assertTrue(logger.entries.value.isEmpty())
    }

    @Test
    fun `generator failure is logged as app error`() = runTest {
        val logger = RequestLogger()
        val provider = RealPlayerQuestionProvider(
            responseGenerator = object : ResponseGenerator {
                override suspend fun generate(
                    request: RetroArchRequest,
                    outputMode: String,
                ): RetroArchResponse {
                    error("boom")
                }
            },
            loggerProvider = { logger },
        )

        val result = provider.ask("2048__", "两个 2 怎么合并？")

        assertFalse(result.ok)
        assertEquals(logger.entries.value.first().id, result.requestLogId)
        assertEquals("error", result.pipelineStage)
        assertTrue(result.errorMessage.orEmpty().contains("app_generator_failed"))
        assertEquals(result.errorMessage, logger.entries.value.first().errorMessage)
    }

    private class CapturingGenerator(
        private val response: RetroArchResponse,
    ) : ResponseGenerator {
        var request: RetroArchRequest? = null
        var outputMode: String? = null

        override suspend fun generate(
            request: RetroArchRequest,
            outputMode: String,
        ): RetroArchResponse {
            this.request = request
            this.outputMode = outputMode
            return response
        }
    }
}
