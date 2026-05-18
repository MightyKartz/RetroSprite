package com.retrosprite.app.endpoint

import com.retrosprite.app.endpoint.model.RetroArchRequest
import com.retrosprite.app.endpoint.model.RetroArchResponse

/**
 * Strategy used by [RetroArchEndpointServer] to turn an inbound RetroArch request into a
 * protocol response.
 *
 * Phase 0 wires [PlaceholderResponseGenerator]. Task #5 will replace it with `QueryPipeline`
 * (RAG + LLM call) by injecting a different implementation into the server constructor —
 * no endpoint code changes.
 */
fun interface ResponseGenerator {
    suspend fun generate(request: RetroArchRequest, outputMode: String): RetroArchResponse
}

/** Returns a fixed greeting; useful for end-to-end RetroArch connectivity tests. */
class PlaceholderResponseGenerator(
    private val message: String = DEFAULT_MESSAGE,
) : ResponseGenerator {
    override suspend fun generate(
        request: RetroArchRequest,
        outputMode: String,
    ): RetroArchResponse = RetroArchResponse.text(message)

    companion object {
        const val DEFAULT_MESSAGE =
            "RetroSprite connected. Ask me anything about your game!"
    }
}
