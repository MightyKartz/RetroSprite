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
    val requestKey: String = "",
    val timestamp: Long,
    val label: String,
    val system: String?,
    val game: String?,
    val imageSize: Int,
    val paused: Boolean,
    val outputMode: String,
    val question: String? = null,
    val questionSource: String? = null,
    val rawQuestion: String? = null,
    val normalizedQuestion: String? = null,
    val questionNormalizationReason: String? = null,
    val normalizedQuestionMatchedTerm: String? = null,
    val normalizedQuestionMatchedEntityId: String? = null,
    val answerShort: String? = null,
    val answerDetail: String? = null,
    val answerType: String? = null,
    val answerConfidence: String? = null,
    val spoilerLevelUsed: String? = null,
    val nextActions: List<String> = emptyList(),
    val suggestedQuestions: List<String> = emptyList(),
    val responseText: String,
    val errorMessage: String? = null,
    val durationMillis: Long = 0L,
    val llmStatus: String? = null,
    val llmProvider: String? = null,
    val llmModel: String? = null,
    val llmMaxTokens: Int? = null,
    val llmTimeoutMs: Long? = null,
    val llmLatencyMs: Long? = null,
    val llmTokensIn: Int = 0,
    val llmTokensOut: Int = 0,
    val llmError: String? = null,
    val feedback: String? = null,
    val feedbackTimestamp: Long? = null,
)

/** Domain representation of a registered game header. */
data class GameDomain(
    val gameId: String,
    val packId: String = gameId,
    val title: String,
    val platform: String,
    val region: String?,
    val languages: List<String>,
    val romCrc32: String?,
    val romSha1: String?,
    val retroarchSystemIds: List<String> = emptyList(),
    val retroarchLabels: List<String> = emptyList(),
    val packVersion: String,
    val schemaVersion: String,
    val trustLevel: String,
    val provenance: String = "unknown",
    val signatureStatus: String = "unsigned",
    val signatureKeyId: String? = null,
    val contentDigest: String? = null,
    val isEnabled: Boolean = true,
    val disabledAt: Long? = null,
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
