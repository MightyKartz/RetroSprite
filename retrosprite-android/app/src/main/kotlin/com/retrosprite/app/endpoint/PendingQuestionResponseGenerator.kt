package com.retrosprite.app.endpoint

import com.retrosprite.app.endpoint.model.RetroArchRequest
import com.retrosprite.app.endpoint.model.RetroArchResponse

class PendingQuestionResponseGenerator(
    private val delegate: ResponseGenerator,
    private val pendingQuestions: PendingQuestionStore,
) : ResponseGenerator {

    override suspend fun generate(
        request: RetroArchRequest,
        outputMode: String,
    ): RetroArchResponse {
        if (request.question.isNotBlank()) {
            return delegate.generate(request, outputMode)
        }

        val pending = pendingQuestions.consumeFor(request.label)
            ?: return delegate.generate(request, outputMode)

        val response = delegate.generate(
            request = request.copy(
                question = pending.question,
                spoilerLevel = pending.spoilerLevel,
            ),
            outputMode = outputMode,
        )
        return response.copy(
            diagnostics = response.diagnostics.copy(
                question = pending.question,
                questionSource = QUESTION_SOURCE_PENDING_HOTKEY,
            )
        )
    }

    companion object {
        const val QUESTION_SOURCE_PENDING_HOTKEY: String = "pending_hotkey"
    }
}
