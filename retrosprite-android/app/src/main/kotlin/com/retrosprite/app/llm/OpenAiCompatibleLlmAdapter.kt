package com.retrosprite.app.llm

import com.retrosprite.app.domain.models.LlmRequest
import com.retrosprite.app.domain.models.LlmResponse
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Skeleton OpenAI-compatible adapter (works for OpenAI, Together, Groq,
 * DeepSeek, Ollama proxies, etc. — anything exposing `/chat/completions`).
 *
 * Phase 0 deliberately does NOT issue any network call: [complete] throws
 * [NotImplementedError]. This avoids accidental API-key exposure during the
 * protocol-validation phase while still locking down the constructor /
 * field shape so Phase 1 can drop in the real implementation without
 * touching call sites.
 *
 * The [OkHttpClient] is constructed eagerly (cheap) but unused at runtime
 * in Phase 0; it is exposed via [http] so tests can verify wiring.
 */
class OpenAiCompatibleLlmAdapter(
    private val config: LlmConfig,
) : LlmAdapter {

    override val providerName: String = config.providerName

    /** Visible for testing / future use. Configured per [LlmConfig.timeoutSeconds]. */
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        .build()

    override suspend fun complete(request: LlmRequest): LlmResponse {
        // TODO(Phase 1): build /chat/completions payload, sign with
        //  `Bearer ${config.apiKey}`, parse response, populate token counts.
        throw NotImplementedError("To be implemented in Phase 1")
    }
}
