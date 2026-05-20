package com.retrosprite.app.endpoint

import com.retrosprite.app.endpoint.model.RetroArchRequest
import com.retrosprite.app.endpoint.model.RetroArchResponse

class HotkeyWakeResponseGenerator(
    private val delegate: ResponseGenerator,
) : ResponseGenerator {

    override suspend fun generate(
        request: RetroArchRequest,
        outputMode: String,
    ): RetroArchResponse {
        if (request.question.isBlank()) {
            return RetroArchResponse.text("")
        }
        return delegate.generate(request, outputMode)
    }
}
