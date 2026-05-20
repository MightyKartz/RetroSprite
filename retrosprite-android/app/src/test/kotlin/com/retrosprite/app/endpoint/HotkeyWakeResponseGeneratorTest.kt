package com.retrosprite.app.endpoint

import com.retrosprite.app.endpoint.model.RetroArchRequest
import com.retrosprite.app.endpoint.model.RetroArchResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HotkeyWakeResponseGeneratorTest {

    @Test
    fun `empty question hotkey request returns silent wake response`() = runTest {
        val delegate = CapturingGenerator()
        val generator = HotkeyWakeResponseGenerator(delegate)

        val response = generator.generate(
            request = RetroArchRequest(label = "mega_drive__光明力量2"),
            outputMode = "text",
        )

        assertEquals("", response.text)
        assertNull(response.error)
        assertNull(delegate.request)
    }

    @Test
    fun `explicit question still reaches delegate`() = runTest {
        val delegate = CapturingGenerator()
        val generator = HotkeyWakeResponseGenerator(delegate)

        val response = generator.generate(
            request = RetroArchRequest(
                label = "mega_drive__光明力量2",
                question = "什么时候转职？",
            ),
            outputMode = "text",
        )

        assertEquals("answered: 什么时候转职？", response.text)
        assertEquals("什么时候转职？", delegate.request?.question)
    }

    private class CapturingGenerator : ResponseGenerator {
        var request: RetroArchRequest? = null
            private set

        override suspend fun generate(
            request: RetroArchRequest,
            outputMode: String,
        ): RetroArchResponse {
            this.request = request
            return RetroArchResponse.text("answered: ${request.question}")
        }
    }
}
