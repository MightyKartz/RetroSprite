package com.retrosprite.app.data.models

import com.retrosprite.app.data.db.converters.StringListConverter
import com.retrosprite.app.data.db.entity.GameEntity
import com.retrosprite.app.data.db.entity.KnowledgeEntity
import com.retrosprite.app.data.db.entity.RequestLogEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Entity <-> Domain mappers.
 *
 * Kept side-effect free and synchronous so they can be invoked from any
 * coroutine dispatcher.
 */
private val stringListConverter = StringListConverter()
private val aliasJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

// region RequestLog
fun RequestLogEntity.toDomain(): RequestLogDomain = RequestLogDomain(
    id = id,
    requestKey = requestKey,
    timestamp = timestamp,
    label = label,
    system = system,
    game = game,
    imageSize = imageSize,
    paused = paused,
    outputMode = outputMode,
    question = question,
    questionSource = questionSource,
    rawQuestion = rawQuestion,
    normalizedQuestion = normalizedQuestion,
    questionNormalizationReason = questionNormalizationReason,
    normalizedQuestionMatchedTerm = normalizedQuestionMatchedTerm,
    normalizedQuestionMatchedEntityId = normalizedQuestionMatchedEntityId,
    answerShort = answerShort,
    answerDetail = answerDetail,
    answerType = answerType,
    answerConfidence = answerConfidence,
    spoilerLevelUsed = spoilerLevelUsed,
    sourceIds = stringListConverter.toList(sourceIds),
    nextActions = stringListConverter.toList(nextActions),
    suggestedQuestions = stringListConverter.toList(suggestedQuestions),
    responseText = responseText,
    errorMessage = errorMessage,
    durationMillis = durationMillis,
    llmStatus = llmStatus,
    llmProvider = llmProvider,
    llmModel = llmModel,
    llmMaxTokens = llmMaxTokens,
    llmTimeoutMs = llmTimeoutMs,
    llmLatencyMs = llmLatencyMs,
    llmTokensIn = llmTokensIn,
    llmTokensOut = llmTokensOut,
    llmError = llmError,
    feedback = feedback,
    feedbackTimestamp = feedbackTimestamp,
)

fun RequestLogDomain.toEntity(): RequestLogEntity = RequestLogEntity(
    id = id,
    requestKey = requestKey,
    timestamp = timestamp,
    label = label,
    system = system,
    game = game,
    imageSize = imageSize,
    paused = paused,
    outputMode = outputMode,
    question = question,
    questionSource = questionSource,
    rawQuestion = rawQuestion,
    normalizedQuestion = normalizedQuestion,
    questionNormalizationReason = questionNormalizationReason,
    normalizedQuestionMatchedTerm = normalizedQuestionMatchedTerm,
    normalizedQuestionMatchedEntityId = normalizedQuestionMatchedEntityId,
    answerShort = answerShort,
    answerDetail = answerDetail,
    answerType = answerType,
    answerConfidence = answerConfidence,
    spoilerLevelUsed = spoilerLevelUsed,
    sourceIds = stringListConverter.fromList(sourceIds),
    nextActions = stringListConverter.fromList(nextActions),
    suggestedQuestions = stringListConverter.fromList(suggestedQuestions),
    responseText = responseText,
    errorMessage = errorMessage,
    durationMillis = durationMillis,
    llmStatus = llmStatus,
    llmProvider = llmProvider,
    llmModel = llmModel,
    llmMaxTokens = llmMaxTokens,
    llmTimeoutMs = llmTimeoutMs,
    llmLatencyMs = llmLatencyMs,
    llmTokensIn = llmTokensIn,
    llmTokensOut = llmTokensOut,
    llmError = llmError,
    feedback = feedback,
    feedbackTimestamp = feedbackTimestamp,
)
// endregion

// region Game
fun GameEntity.toDomain(): GameDomain = GameDomain(
    gameId = gameId,
    packId = packId.ifBlank { gameId },
    title = title,
    platform = platform,
    region = region,
    languages = stringListConverter.toList(languages),
    romCrc32 = romCrc32,
    romSha1 = romSha1,
    retroarchSystemIds = stringListConverter.toList(retroarchSystemIds),
    retroarchLabels = stringListConverter.toList(retroarchLabels),
    coverageTier = coverageTier,
    packVersion = packVersion,
    schemaVersion = schemaVersion,
    trustLevel = trustLevel,
    provenance = provenance,
    signatureStatus = signatureStatus,
    signatureKeyId = signatureKeyId,
    contentDigest = contentDigest,
    isEnabled = enabled,
    disabledAt = disabledAt,
    installedAt = installedAt
)

fun GameDomain.toEntity(): GameEntity = GameEntity(
    gameId = gameId,
    packId = packId.ifBlank { gameId },
    title = title,
    platform = platform,
    region = region,
    languages = stringListConverter.fromList(languages),
    romCrc32 = romCrc32,
    romSha1 = romSha1,
    retroarchSystemIds = stringListConverter.fromList(retroarchSystemIds),
    retroarchLabels = stringListConverter.fromList(retroarchLabels),
    coverageTier = coverageTier,
    packVersion = packVersion,
    schemaVersion = schemaVersion,
    trustLevel = trustLevel,
    provenance = provenance,
    signatureStatus = signatureStatus,
    signatureKeyId = signatureKeyId,
    contentDigest = contentDigest,
    enabled = isEnabled,
    disabledAt = disabledAt,
    installedAt = installedAt
)
// endregion

// region Knowledge
fun KnowledgeEntity.toDomain(): KnowledgeChunkDomain = KnowledgeChunkDomain(
    id = id,
    gameId = gameId,
    entityId = entityId,
    entityType = entityType,
    canonicalName = canonicalName,
    aliases = stringListConverter.toList(aliasesJson),
    aliasMetadata = aliasMetadataJson.toAliasMetadata(),
    descriptionShort = descriptionShort,
    descriptionLong = descriptionLong,
    progressGate = progressGate,
    spoilerLevel = spoilerLevel,
    sourceRefs = stringListConverter.toList(sourceRefsJson),
    confidence = confidence,
    answerTemplates = stringListConverter.toList(answerTemplatesJson)
)

fun KnowledgeChunkDomain.toEntity(): KnowledgeEntity = KnowledgeEntity(
    id = id,
    gameId = gameId,
    entityId = entityId,
    entityType = entityType,
    canonicalName = canonicalName,
    aliasesJson = stringListConverter.fromList(aliases),
    aliasMetadataJson = aliasMetadata.toAliasMetadataJson(),
    descriptionShort = descriptionShort,
    descriptionLong = descriptionLong,
    progressGate = progressGate,
    spoilerLevel = spoilerLevel,
    sourceRefsJson = stringListConverter.fromList(sourceRefs),
    confidence = confidence,
    answerTemplatesJson = if (answerTemplates.isEmpty()) {
        null
    } else {
        stringListConverter.fromList(answerTemplates)
    }
)
// endregion

@Serializable
private data class KnowledgeAliasDto(
    val term: String,
    @SerialName("entity_id") val entityId: String,
    val kind: String = "display_alias",
    val source: String? = null,
    val weight: Double? = null,
    @SerialName("canonical_term") val canonicalTerm: String? = null,
    val notes: String? = null,
)

private fun String?.toAliasMetadata(): List<KnowledgeAliasDomain> {
    if (isNullOrBlank()) return emptyList()
    return runCatching {
        aliasJson.decodeFromString<List<KnowledgeAliasDto>>(this)
            .map { dto ->
                KnowledgeAliasDomain(
                    term = dto.term,
                    entityId = dto.entityId,
                    kind = dto.kind,
                    source = dto.source,
                    weight = dto.weight,
                    canonicalTerm = dto.canonicalTerm,
                    notes = dto.notes,
                )
            }
    }.getOrDefault(emptyList())
}

private fun List<KnowledgeAliasDomain>.toAliasMetadataJson(): String? {
    if (isEmpty()) return null
    val dto = map { alias ->
        KnowledgeAliasDto(
            term = alias.term,
            entityId = alias.entityId,
            kind = alias.kind,
            source = alias.source,
            weight = alias.weight,
            canonicalTerm = alias.canonicalTerm,
            notes = alias.notes,
        )
    }
    return aliasJson.encodeToString(dto)
}
