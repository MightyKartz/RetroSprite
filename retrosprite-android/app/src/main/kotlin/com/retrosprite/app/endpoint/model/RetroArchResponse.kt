package com.retrosprite.app.endpoint.model

import kotlinx.serialization.Serializable

/**
 * Response payload returned to the RetroArch frontend.
 *
 * All fields are optional per the AI Service spec. The frontend decides how to render based
 * on which fields are present (e.g. `text` for an OSD overlay, `image` for a Base64 PNG
 * overlay, `text_position` 1=top / 2=bottom, `press` to inject button input, `auto` to
 * auto-resume after pause, `error` to surface a non-fatal protocol error).
 */
@Serializable
data class RetroArchResponse(
    val text: String? = null,
    val image: String? = null,
    val sound: String? = null,
    val text_position: Int? = null,
    val press: List<String>? = null,
    val auto: Int? = null,
    val error: String? = null,
) {
    companion object {
        /** Convenience builder for the most common case: a textual answer. */
        fun text(content: String, textPosition: Int? = null): RetroArchResponse =
            RetroArchResponse(text = content, text_position = textPosition)

        /** Convenience builder for protocol-level errors (still HTTP 200). */
        fun error(message: String): RetroArchResponse = RetroArchResponse(error = message)
    }
}

/** Tiny payload returned by the `/health` route — useful for diagnostics surfaces. */
@Serializable
data class HealthResponse(
    val status: String = "ok",
    val version: String = "0.1.0",
)
