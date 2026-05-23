package com.retrosprite.app.data.retrieval

import com.retrosprite.app.domain.models.SpoilerLevel
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

internal data class SelectedTemplateAnswer(
    val text: String,
    val spoilerLevel: String,
)

internal object TemplateAnswerSelector {
    fun select(template: JsonObject, tolerance: SpoilerLevel): SelectedTemplateAnswer? {
        val tiered = when (tolerance) {
            SpoilerLevel.LIGHT -> template.templateStringOrNull("answer_light")?.let {
                SelectedTemplateAnswer(it, template.templateStringOrNull("spoiler_light") ?: "light")
            }

            SpoilerLevel.CLEAR -> template.templateStringOrNull("answer_clear")?.let {
                SelectedTemplateAnswer(it, template.templateStringOrNull("spoiler_clear") ?: "medium")
            } ?: template.templateStringOrNull("answer_light")?.let {
                SelectedTemplateAnswer(it, template.templateStringOrNull("spoiler_light") ?: "light")
            }

            SpoilerLevel.FULL -> template.templateStringOrNull("answer_direct")?.let {
                SelectedTemplateAnswer(it, template.templateStringOrNull("spoiler_direct") ?: "heavy")
            } ?: template.templateStringOrNull("answer_clear")?.let {
                SelectedTemplateAnswer(it, template.templateStringOrNull("spoiler_clear") ?: "medium")
            } ?: template.templateStringOrNull("answer_light")?.let {
                SelectedTemplateAnswer(it, template.templateStringOrNull("spoiler_light") ?: "light")
            }
        }
        return tiered?.takeIf { it.text.isNotBlank() }
            ?: template.templateStringOrNull("answer")?.takeIf { it.isNotBlank() }?.let {
                SelectedTemplateAnswer(it, template.templateStringOrNull("spoiler_level") ?: "light")
            }
    }
}

internal fun JsonObject.templateStringOrNull(name: String): String? {
    val value = this[name] ?: return null
    if (value is JsonNull) return null
    return (value as? JsonPrimitive)?.contentOrNull
}

internal fun JsonObject.templateArrayStrings(name: String): List<String> =
    this[name]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
