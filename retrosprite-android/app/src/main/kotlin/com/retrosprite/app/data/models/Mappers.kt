package com.retrosprite.app.data.models

import com.retrosprite.app.data.db.converters.StringListConverter
import com.retrosprite.app.data.db.entity.GameEntity
import com.retrosprite.app.data.db.entity.KnowledgeEntity
import com.retrosprite.app.data.db.entity.RequestLogEntity

/**
 * Entity <-> Domain mappers.
 *
 * Kept side-effect free and synchronous so they can be invoked from any
 * coroutine dispatcher.
 */
private val stringListConverter = StringListConverter()

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
    nextActions = stringListConverter.toList(nextActions),
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
    nextActions = stringListConverter.fromList(nextActions),
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
