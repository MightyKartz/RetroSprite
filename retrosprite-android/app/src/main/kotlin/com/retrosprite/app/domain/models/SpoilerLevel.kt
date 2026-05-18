package com.retrosprite.app.domain.models

/**
 * Spoiler tolerance level for an answer.
 *
 * - LIGHT: avoid plot/late-game info; safe summaries only.
 * - CLEAR: standard hints permitted, key plot beats avoided.
 * - FULL: anything goes, including endings.
 */
enum class SpoilerLevel {
    LIGHT,
    CLEAR,
    FULL
}
