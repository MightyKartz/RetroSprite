package com.retrosprite.app.endpoint

import com.retrosprite.app.endpoint.model.ResponseDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestLoggerTest {

    @Test
    fun `logs raw and normalized voice question diagnostics`() {
        val logger = RequestLogger()

        val entry = logger.log(
            label = "mega_drive__光明力量2",
            imageBase64 = "",
            paused = true,
            outputMode = "hotkey_voice:text",
            responseText = "answer",
            diagnostics = ResponseDiagnostics(
                rawQuestion = "修医是谁",
                normalizedQuestion = "修伊是谁",
                questionNormalizationReason = "homophone",
                normalizedQuestionMatchedTerm = "修伊",
                normalizedQuestionMatchedEntityId = "npc.jaha",
            ),
            question = "修伊是谁",
            questionSource = "hotkey_voice",
        )

        assertEquals("修伊是谁", entry.question)
        assertEquals("修医是谁", entry.rawQuestion)
        assertEquals("修伊是谁", entry.normalizedQuestion)
        assertEquals("homophone", entry.questionNormalizationReason)
        assertEquals("修伊", entry.normalizedQuestionMatchedTerm)
        assertEquals("npc.jaha", entry.normalizedQuestionMatchedEntityId)
    }
}
