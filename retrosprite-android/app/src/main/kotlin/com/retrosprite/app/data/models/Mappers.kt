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
    timestamp = timestamp,
    label = label,
    system = system,
    game = game,
    imageSize = imageSize,
    paused = paused,
    outputMode = outputMode,
    responseText = responseText,
    errorMessage = errorMessage
)

fun RequestLogDomain.toEntity(): RequestLogEntity = RequestLogEntity(
    id = id,
    timestamp = timestamp,
    label = label,
    system = system,
    game = game,
    imageSize = imageSize,
    paused = paused,
    outputMode = outputMode,
    responseText = responseText,
    errorMessage = errorMessage
)
// endregion

// region Game
fun GameEntity.toDomain(): GameDomain = GameDomain(
    gameId = gameId,
    title = title,
    platform = platform,
    region = region,
    languages = stringListConverter.toList(languages),
    romCrc32 = romCrc32,
    romSha1 = romSha1,
    packVersion = packVersion,
    schemaVersion = schemaVersion,
    trustLevel = trustLevel,
    installedAt = installedAt
)

fun GameDomain.toEntity(): GameEntity = GameEntity(
    gameId = gameId,
    title = title,
    platform = platform,
    region = region,
    languages = stringListConverter.fromList(languages),
    romCrc32 = romCrc32,
    romSha1 = romSha1,
    packVersion = packVersion,
    schemaVersion = schemaVersion,
    trustLevel = trustLevel,
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
