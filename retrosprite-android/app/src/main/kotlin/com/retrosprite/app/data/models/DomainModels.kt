package com.retrosprite.app.data.models

/**
 * Domain representation of a logged AI Service request.
 *
 * Lives temporarily in the data layer (`com.retrosprite.app.data.models`)
 * to avoid clashing with the Task 5 domain workspace. It will be promoted
 * to `com.retrosprite.app.domain.models` once that namespace is owned.
 */
data class RequestLogDomain(
    val id: Long = 0L,
    val timestamp: Long,
    val label: String,
    val system: String?,
    val game: String?,
    val imageSize: Int,
    val paused: Boolean,
    val outputMode: String,
    val responseText: String,
    val errorMessage: String? = null
)

/** Domain representation of a registered game header. */
data class GameDomain(
    val gameId: String,
    val title: String,
    val platform: String,
    val region: String?,
    val languages: List<String>,
    val romCrc32: String?,
    val romSha1: String?,
    val packVersion: String,
    val schemaVersion: String,
    val trustLevel: String,
    val installedAt: Long
)

/** Domain representation of a knowledge chunk. */
data class KnowledgeChunkDomain(
    val id: Long,
    val gameId: String,
    val entityId: String,
    val entityType: String,
    val canonicalName: String,
    val aliases: List<String>,
    val descriptionShort: String,
    val descriptionLong: String?,
    val progressGate: String?,
    val spoilerLevel: String,
    val sourceRefs: List<String>,
    val confidence: String,
    val answerTemplates: List<String>
)
