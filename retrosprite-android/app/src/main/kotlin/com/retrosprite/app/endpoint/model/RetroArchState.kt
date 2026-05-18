package com.retrosprite.app.endpoint.model

import kotlinx.serialization.Serializable

/**
 * Mirrors the `state` object inside a RetroArch AI Service request.
 *
 * Per the RetroArch protocol, every field is an integer flag (0 or 1). All buttons default
 * to `0` so partial payloads (e.g. older cores that omit `l3`/`r3`) deserialize cleanly.
 */
@Serializable
data class RetroArchState(
    val paused: Int = 0,
    val a: Int = 0,
    val b: Int = 0,
    val x: Int = 0,
    val y: Int = 0,
    val select: Int = 0,
    val start: Int = 0,
    val up: Int = 0,
    val down: Int = 0,
    val left: Int = 0,
    val right: Int = 0,
    val l: Int = 0,
    val r: Int = 0,
    val l2: Int = 0,
    val r2: Int = 0,
    val l3: Int = 0,
    val r3: Int = 0,
) {
    val isPaused: Boolean get() = paused == 1
}
