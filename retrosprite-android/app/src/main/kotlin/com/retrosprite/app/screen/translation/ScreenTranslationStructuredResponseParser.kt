package com.retrosprite.app.screen.translation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class ScreenTranslationStructuredResponseParser(
    private val glossaryPostProcessor: ScreenTranslationGlossaryPostProcessor =
        ScreenTranslationGlossaryPostProcessor(),
) {

    fun parse(rawText: String, glossary: ScreenTranslationGlossary?): String? {
        val root = parseJsonObject(rawText) ?: return null
        val mode = root.stringValue("mode").lowercase()
        val entries = root["entries"] as? JsonArray ?: return null
        if (mode !in structuredMenuModes && entries.isEmpty()) return null

        val normalizedEntries = normalizeEntries(
            entries = entries.mapNotNull { it as? JsonObject },
            glossary = glossary,
        )
        if (normalizedEntries.isEmpty()) return null

        return render(mode, normalizedEntries)
    }

    private fun normalizeEntries(
        entries: List<JsonObject>,
        glossary: ScreenTranslationGlossary?,
    ): List<StructuredEntry> {
        val normalized = mutableListOf<StructuredEntry>()
        for (jsonObject in entries) {
            val entry = objectToEntry(jsonObject, glossary) ?: continue
            if (entry.numericOnly) {
                val last = normalized.lastOrNull()
                if (last != null && last.type in statusTypes && !last.translation.containsDigit()) {
                    normalized[normalized.lastIndex] = last.copy(
                        value = entry.value.ifBlank { entry.translation },
                    )
                }
                continue
            }
            normalized += entry
        }
        return normalized
    }

    private fun parseJsonObject(rawText: String): JsonObject? {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return null
        val unwrapped = unwrapCodeFence(trimmed)
        return runCatching { json.parseToJsonElement(unwrapped) as? JsonObject }.getOrNull()
    }

    private fun unwrapCodeFence(text: String): String {
        if (!text.startsWith("```")) return text
        return text
            .lineSequence()
            .drop(1)
            .filterNot { it.trim() == "```" }
            .joinToString("\n")
            .trim()
    }

    private fun objectToEntry(
        jsonObject: JsonObject,
        glossary: ScreenTranslationGlossary?,
    ): StructuredEntry? {
        val source = jsonObject.stringValue("source").trim()
        val type = jsonObject.stringValue("type").lowercase().trim()
        val sourceParts = splitSourceValue(source, type)
        val explicitValue = jsonObject.stringValue("value").trim()
        val rawTranslation = listOf("translation", "target", "text")
            .firstNotNullOfOrNull { key -> jsonObject.stringValue(key).takeIf { it.isNotBlank() } }
            .orEmpty()
            .trim()
        val translation = normalizeTranslation(
            source = sourceParts.label,
            rawTranslation = rawTranslation,
            glossary = glossary,
        )
        val numericOnly = sourceParts.label.isNumericOnly() || translation.isNumericOnly()

        if (translation.isBlank()) return null
        if (numericOnly) {
            return StructuredEntry(
                type = type,
                source = sourceParts.label,
                translation = sourceParts.label.takeIf { it.isNumericOnly() } ?: translation,
                value = sourceParts.label.takeIf { it.isNumericOnly() }.orEmpty(),
                numericOnly = true,
            )
        }
        if (shouldDropEntry(translation, type)) return null

        return StructuredEntry(
            type = type,
            source = sourceParts.label,
            translation = translation,
            value = explicitValue.ifBlank { sourceParts.value },
        )
    }

    private fun normalizeTranslation(
        source: String,
        rawTranslation: String,
        glossary: ScreenTranslationGlossary?,
    ): String {
        val glossaryTranslation = glossaryPostProcessor.apply(rawTranslation, glossary).trim()
        if (source.isBlank()) return glossaryTranslation
        val sourceWithGlossary = glossaryPostProcessor.apply(source, glossary).trim()

        if (sourceWithGlossary != source) {
            return sourceWithGlossary
        }

        if (source.containsGlossaryTerm(glossary)) {
            return sourceWithGlossary
        }

        if (source.containsDigit() && !source.isNumericOnly()) {
            return source.numericTokens().fold(glossaryTranslation) { current, token ->
                if (current.contains(token)) current else "$current $token".trim()
            }
        }

        return glossaryTranslation
    }

    private fun shouldDropEntry(translation: String, type: String): Boolean {
        if (type in droppedTypes) return true
        if (translation.isBoilerplateDescription()) return true
        return false
    }

    private fun render(mode: String, entries: List<StructuredEntry>): String {
        val menuEntries = entries
            .filter { it.type in menuTypes }
            .map { it.toBilingualText() }
            .distinct()
        val equipmentSlotEntries = entries
            .filter { it.type in equipmentTypes }
            .map { it.toBilingualText() }
            .distinct()
        val itemEntries = entries
            .filter { it.type in itemTypes }
            .map { it.toBilingualText() }
            .distinct()
        val statusEntries = entries
            .filter { it.type in statusTypes }
            .map { it.toBilingualText() }
            .distinct()
        val otherEntries = entries
            .filterNot {
                it.type in menuTypes ||
                    it.type in equipmentTypes ||
                    it.type in itemTypes ||
                    it.type in statusTypes
            }
            .map { it.toBilingualText() }
            .distinct()

        val lines = mutableListOf<String>()
        if (menuEntries.isNotEmpty()) {
            lines += "菜单"
            lines += menuEntries.joinToString(" | ")
        }
        if (equipmentSlotEntries.isNotEmpty() || itemEntries.isNotEmpty()) {
            lines += if (mode == "inventory") "物品" else "装备"
            if (equipmentSlotEntries.isNotEmpty()) {
                lines += equipmentSlotEntries.joinToString(" | ")
            }
            if (itemEntries.isNotEmpty()) {
                lines += itemEntries.joinToString(" | ")
            }
        }
        if (statusEntries.isNotEmpty()) {
            lines += "属性"
            lines += statusEntries
        }
        if (otherEntries.isNotEmpty()) {
            if (lines.isNotEmpty()) lines += "内容"
            lines += otherEntries
        }
        return lines.joinToString("\n").trim()
    }

    private fun JsonObject.stringValue(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun String.containsDigit(): Boolean = any { it.isDigit() }

    private fun String.numericTokens(): List<String> =
        numberTokenRegex.findAll(this).map { it.value }.toList()

    private fun splitSourceValue(source: String, type: String): SourceParts {
        if (type !in statusTypes) return SourceParts(label = source)
        val match = labeledValueRegex.matchEntire(source)
        return if (match == null) {
            SourceParts(label = source)
        } else {
            SourceParts(
                label = match.groupValues[1].trim(),
                value = match.groupValues[2].trim(),
            )
        }
    }

    private fun String.isNumericOnly(): Boolean {
        val compact = replace(Regex("\\s+"), "")
        return compact.isNotBlank() && numericOnlyRegex.matches(compact)
    }

    private fun String.isBoilerplateDescription(): Boolean {
        val compact = replace(Regex("\\s+"), "")
        return boilerplateFragments.any { compact.contains(it) }
    }

    private fun String.containsGlossaryTerm(glossary: ScreenTranslationGlossary?): Boolean {
        if (glossary == null) return false
        return glossary.terms.any { term ->
            term.source.isNotBlank() &&
                Regex(
                    pattern = "(?i)(?<![A-Za-z0-9])${Regex.escape(term.source)}(?![A-Za-z0-9])",
                ).containsMatchIn(this)
        }
    }

    private data class StructuredEntry(
        val type: String,
        val source: String,
        val translation: String,
        val value: String = "",
        val numericOnly: Boolean = false,
    ) {
        fun toBilingualText(): String {
            val label = when {
                source.isBlank() -> translation
                source.equals(translation, ignoreCase = true) -> source
                else -> "$source $translation"
            }
            return listOf(label, value)
                .filter { it.isNotBlank() }
                .joinToString(" ")
        }
    }

    private data class SourceParts(
        val label: String,
        val value: String = "",
    )

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val structuredMenuModes = setOf("menu", "interface", "status", "inventory", "equipment")
        val menuTypes = setOf("menu", "command", "control", "option", "ui")
        val equipmentTypes = setOf("equipment", "equip", "slot", "equipment_slot")
        val itemTypes = setOf("item", "equipment_item")
        val statusTypes = setOf("stat", "status", "system", "value")
        val droppedTypes = setOf("number", "numeric", "description", "screen_description")
        val numberTokenRegex = Regex("\\d+(?:[/:.]\\d+)*")
        val numericOnlyRegex = Regex("[\\d.,:/%+\\-]+")
        val labeledValueRegex = Regex("(.+?)\\s+([+-]?\\d+(?:[/:.]\\d+)*(?:%)?)")
        val boilerplateFragments = listOf(
            "这是一张",
            "这是一个",
            "截图",
            "游戏画面",
            "菜单界面",
            "界面中",
            "画面显示",
        )
    }
}
