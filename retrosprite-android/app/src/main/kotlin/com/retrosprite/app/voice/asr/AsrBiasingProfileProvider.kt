package com.retrosprite.app.voice.asr

import com.retrosprite.app.data.repository.GameRepository
import com.retrosprite.app.data.repository.KnowledgeRepository
import com.retrosprite.app.domain.models.GameIdentity
import com.retrosprite.app.domain.resolver.GameResolver

class AsrBiasingProfileProvider(
    private val resolver: GameResolver,
    private val gameRepository: GameRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val extractor: GkpAsrHotwordExtractor = GkpAsrHotwordExtractor(),
) {

    suspend fun profileForLabel(label: String): AsrBiasingProfile? {
        return resolveForLabel(label).profile
    }

    suspend fun resolveForLabel(label: String): AsrBiasingResolution {
        val override = AsrLabelOverrideParser.parse(label)
        if (override.hotwordMode == AsrHotwordMode.None) {
            return AsrBiasingResolution(
                label = override.cleanLabel,
                hotwordMode = override.hotwordMode,
                profile = null,
            )
        }

        val identity = resolver.resolve(label = override.cleanLabel, romHash = null)
        if (identity.source == GameIdentity.SOURCE_GKP_DISABLED) {
            return AsrBiasingResolution(
                label = override.cleanLabel,
                hotwordMode = override.hotwordMode,
                profile = null,
            )
        }
        val gameId = identity.gameId ?: return AsrBiasingResolution(
            label = override.cleanLabel,
            hotwordMode = override.hotwordMode,
            profile = null,
        )
        val rows = knowledgeRepository.listByGame(gameId)
        if (rows.isEmpty()) {
            return AsrBiasingResolution(
                label = override.cleanLabel,
                hotwordMode = override.hotwordMode,
                profile = null,
            )
        }
        val packVersion = gameRepository.getById(gameId)?.packVersion ?: "installed"
        val profile = extractor.extract(
            gameId = gameId,
            packVersion = packVersion,
            rows = rows,
        ).takeIf { it.normalizedEntries.isNotEmpty() }
        return AsrBiasingResolution(
            label = override.cleanLabel,
            hotwordMode = override.hotwordMode,
            profile = profile,
        )
    }
}

object NoopAsrBiasingProfileProvider {
    val instance: AsrBiasingProfileProvider? = null
}
