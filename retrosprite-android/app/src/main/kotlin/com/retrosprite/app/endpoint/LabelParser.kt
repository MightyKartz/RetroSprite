package com.retrosprite.app.endpoint

/**
 * Splits a RetroArch `label` field of the form `"system__game"` into its parts.
 *
 * Per the protocol the **first** `__` delimiter separates `system` from `game`; any further
 * `__` occurrences belong to the game segment (e.g. `"a__b__c"` -> `system="a"`, `game="b__c"`).
 * Edge cases:
 *  - empty / blank label                -> ParsedLabel("", "")
 *  - no `__` delimiter (e.g. "snes")    -> ParsedLabel("snes", "")
 *  - trailing delimiter (e.g. "snes__") -> ParsedLabel("snes", "")
 *  - leading delimiter (e.g. "__game")  -> ParsedLabel("", "game")
 */
object LabelParser {

    private const val DELIMITER = "__"

    fun parse(label: String?): ParsedLabel {
        val raw = label?.trim().orEmpty()
        if (raw.isEmpty()) return ParsedLabel.EMPTY

        val idx = raw.indexOf(DELIMITER)
        if (idx < 0) return ParsedLabel(system = raw, game = "")

        val system = raw.substring(0, idx)
        val game = raw.substring(idx + DELIMITER.length)
        return ParsedLabel(system = system, game = game)
    }
}

data class ParsedLabel(val system: String, val game: String) {
    companion object {
        val EMPTY = ParsedLabel(system = "", game = "")
    }
}
