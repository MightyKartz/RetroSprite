package com.retrosprite.app.endpoint.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Body of a RetroArch AI Service POST request.
 *
 * Fields are intentionally non-nullable with sensible defaults so that partial payloads
 * still deserialize. The endpoint converts deserialization failures into protocol-level
 * error responses (HTTP 200 with `{ "error": "..." }`).
 */
@Serializable
data class RetroArchRequest(
    val image: String = "",
    val label: String = "",
    val question: String = "",
    @SerialName("spoiler_level") val spoilerLevel: String = "",
    val state: RetroArchState = RetroArchState(),
)
