package com.retrosprite.app.domain.models

import kotlinx.serialization.Serializable

/**
 * Domain-layer representation of the controller / memory state passed in by
 * the AI Service request body.
 *
 * Defined here (independently from [com.retrosprite.app.endpoint] models) to
 * avoid a circular dependency: the endpoint layer depends on domain, and
 * converts its own `RetroArchState` into this neutral form before invoking
 * the [com.retrosprite.app.domain.QueryPipeline].
 *
 * Phase 0: only `raw` is populated (the verbatim button-state map). Phase 1
 * may decode well-known buttons / register banks into typed properties.
 *
 * @param raw Verbatim state map as received over HTTP (key = label,
 *   value = pressed/value as int). Empty map when no state was provided.
 */
@Serializable
data class ControllerState(
    val raw: Map<String, Int> = emptyMap(),
) {
    companion object {
        val EMPTY: ControllerState = ControllerState(raw = emptyMap())
    }
}
