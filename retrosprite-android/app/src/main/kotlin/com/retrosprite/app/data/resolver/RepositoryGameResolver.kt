package com.retrosprite.app.data.resolver

import com.retrosprite.app.data.models.GameDomain
import com.retrosprite.app.data.repository.GameRepository
import com.retrosprite.app.domain.models.GameIdentity
import com.retrosprite.app.domain.resolver.GameResolver
import com.retrosprite.app.domain.resolver.LabelGameResolver

/**
 * Phase 1 resolver that upgrades RetroArch label parsing with installed GKP metadata.
 *
 * The fallback [LabelGameResolver] remains the tolerant parser. This wrapper adds
 * repository lookups by ROM hash, exact game id candidates, and platform/title search.
 */
class RepositoryGameResolver(
    private val gameRepository: GameRepository,
    private val fallback: GameResolver = LabelGameResolver(),
) : GameResolver {

    override suspend fun resolve(label: String, romHash: String?): GameIdentity {
        val trimmedHash = romHash?.trim().orEmpty()
        if (trimmedHash.isNotEmpty()) {
            gameRepository.getByRomSha1(trimmedHash)?.let {
                return if (it.isEnabled) {
                    it.toIdentity("rom_sha1")
                } else {
                    it.toDisabledIdentity()
                }
            }
            gameRepository.getByRomCrc32(trimmedHash)?.let {
                return if (it.isEnabled) {
                    it.toIdentity("rom_crc32")
                } else {
                    it.toDisabledIdentity()
                }
            }
        }

        val parsed = fallback.resolve(label, romHash)

        for (candidate in idCandidates(label, parsed)) {
            gameRepository.getById(candidate)?.let {
                return if (it.isEnabled) {
                    it.toIdentity("gkp_index")
                } else {
                    it.toDisabledIdentity()
                }
            }
        }

        if (parsed.platform != UNKNOWN && parsed.title != UNKNOWN) {
            val matches = gameRepository.searchByLabel(
                platform = parsed.platform,
                titleQuery = parsed.title,
            )
            val enabledMatches = matches.filter { it.isEnabled }
            val best = enabledMatches.firstOrNull {
                it.title.equals(parsed.title, ignoreCase = true)
            } ?: enabledMatches.firstOrNull()
            if (best != null) return best.toIdentity("gkp_index")

            val disabledBest = matches.filterNot { it.isEnabled }.firstOrNull {
                it.title.equals(parsed.title, ignoreCase = true)
            } ?: matches.firstOrNull { !it.isEnabled }
            if (disabledBest != null) return disabledBest.toDisabledIdentity()
        }

        return parsed
    }

    private fun idCandidates(label: String, parsed: GameIdentity): List<String> {
        val raw = label.trim()
        val beforeSeparator = raw.substringBefore(SEPARATOR).takeIf { raw.contains(SEPARATOR) }
        val candidates = buildList {
            add(raw)
            beforeSeparator?.let(::add)
            if (parsed.platform != UNKNOWN) add(parsed.platform)
            if (parsed.title != UNKNOWN) {
                add(parsed.title)
                add(parsed.title.slugify())
            }
        }
        return candidates
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() && it != UNKNOWN }
            .distinct()
    }

    private fun GameDomain.toIdentity(source: String): GameIdentity =
        GameIdentity(
            gameId = gameId,
            title = title,
            platform = platform,
            region = region,
            source = source,
        )

    private fun GameDomain.toDisabledIdentity(): GameIdentity =
        GameIdentity(
            gameId = null,
            title = title,
            platform = platform,
            region = region,
            source = GameIdentity.SOURCE_GKP_DISABLED,
        )

    private fun String.slugify(): String =
        trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')

    private companion object {
        const val SEPARATOR = "__"
        const val UNKNOWN = "unknown"
    }
}
