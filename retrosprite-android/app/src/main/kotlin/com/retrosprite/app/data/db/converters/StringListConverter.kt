package com.retrosprite.app.data.db.converters

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Converts between List<String> and a JSON array string.
 *
 * Storage format is a JSON array (e.g. `["en","ja"]`) so it remains
 * portable for tooling and parseable without Room. Empty/null input
 * normalizes to an empty array.
 */
class StringListConverter {

    @TypeConverter
    fun fromList(value: List<String>?): String {
        val list = value ?: emptyList()
        return JSON.encodeToString(SERIALIZER, list)
    }

    @TypeConverter
    fun toList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching { JSON.decodeFromString(SERIALIZER, value) }
            .getOrElse { emptyList() }
    }

    companion object {
        private val JSON = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        private val SERIALIZER = ListSerializer(String.serializer())
    }
}
