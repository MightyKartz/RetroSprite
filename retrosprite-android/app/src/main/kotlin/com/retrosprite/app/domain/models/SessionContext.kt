package com.retrosprite.app.domain.models

import kotlinx.serialization.Serializable

/**
 * Per-request execution context flowing through resolver -> retrieval ->
 * policy -> composer.
 *
 * Holds the resolved game, the player's question, the live screenshot /
 * controller state, and a small rolling window of previous turns. Spoiler
 * level and language gate which evidence is admissible.
 */
@Serializable
data class SessionContext(
    val gameIdentity: GameIdentity,
    val playerQuestion: String?,
    /** Base64-encoded PNG of the current frame, when supplied. Phase 0 unused. */
    val screenshotBase64: String?,
    val state: ControllerState,
    val spoilerLevel: SpoilerLevel,
    /** ISO 639-1 language tag. Defaults to Simplified Chinese. */
    val language: String = "zh",
    val recentTurns: List<QaTurn> = emptyList(),
)
