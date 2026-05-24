package com.retrosprite.app.data.resolver

import com.retrosprite.app.data.models.GameDomain
import com.retrosprite.app.data.repository.GameRepository
import com.retrosprite.app.domain.models.GameIdentity
import com.retrosprite.app.domain.resolver.GameResolver
import com.retrosprite.app.domain.resolver.LabelGameResolver
import com.retrosprite.app.domain.resolver.toCanonicalRetroPlatform

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
            val matches = gameMatchesForLabel(label = label, platform = parsed.platform, title = parsed.title)
            val enabledMatches = matches.filter { it.game.isEnabled }
            val best = enabledMatches.firstOrNull()?.game
            if (best != null) return best.toIdentity("gkp_index")

            val disabledBest = matches.firstOrNull { !it.game.isEnabled }?.game
            if (disabledBest != null) return disabledBest.toDisabledIdentity()
        }

        return parsed
    }

    private suspend fun gameMatchesForLabel(
        label: String,
        platform: String,
        title: String,
    ): List<ScoredGame> {
        val titleCandidates = titleSearchCandidates(title)
        val searched = titleCandidates.flatMap { candidate ->
            gameRepository.searchByLabel(platform, candidate)
        }
        val platformGames = gameRepository.searchByLabel(platform, "")
        val candidates = (searched + platformGames)
            .filter { it.platform.toCanonicalRetroPlatform() == platform }
            .distinctBy { it.gameId }
        val queryKeys = titleMatchKeys(title)
        val labelKey = label.retroArchLabelKey()
        return candidates
            .mapNotNull { game ->
                val score = maxOf(
                    game.explicitRetroArchLabelScore(labelKey),
                    titleMatchScore(queryKeys, game.titleMatchKeys()),
                )
                if (score <= 0.0) null else ScoredGame(game = game, score = score)
            }
            .sortedWith(
                compareByDescending<ScoredGame> { it.score }
                    .thenBy { it.game.title }
            )
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

    private fun titleSearchCandidates(title: String): List<String> =
        titleFragments(title)
            .map { it.stripRomDecorations() }
            .flatMap { fragment ->
                listOf(
                    fragment,
                    fragment.substringBefore(" - "),
                    fragment.substringBefore("-"),
                    fragment.substringBefore("："),
                    fragment.substringBefore(":"),
                )
            }
            .map { it.trim() }
            .filter { it.isUsefulTitleFragment() }
            .distinct()

    private fun titleMatchKeys(title: String): Set<String> =
        titleFragments(title)
            .flatMap { fragment ->
                val clean = fragment.stripRomDecorations()
                listOf(
                    clean,
                    clean.substringBefore(" - "),
                    clean.substringBefore("-"),
                    clean.substringBefore("："),
                    clean.substringBefore(":"),
                )
            }
            .map { it.toTitleKey() }
            .filter { it.isUsefulTitleKey() }
            .toSet()

    private fun GameDomain.titleMatchKeys(): Set<String> =
        (titleFragments(title) + retroarchLabels.map { it.substringAfter(SEPARATOR, it) })
            .map { it.toTitleKey() }
            .filter { it.isUsefulTitleKey() }
            .toSet()

    private fun GameDomain.explicitRetroArchLabelScore(labelKey: String): Double {
        if (labelKey.isBlank()) return 0.0
        val explicitLabels = retroarchLabels.map { it.retroArchLabelKey() }
        return if (explicitLabels.contains(labelKey)) EXPLICIT_LABEL_SCORE else 0.0
    }

    private fun titleMatchScore(
        queryKeys: Set<String>,
        gameKeys: Set<String>,
    ): Double {
        if (queryKeys.isEmpty() || gameKeys.isEmpty()) return 0.0
        queryKeys.forEach { query ->
            gameKeys.forEach { game ->
                if (query == game) return EXACT_TITLE_SCORE
            }
        }
        queryKeys.forEach { query ->
            gameKeys.forEach { game ->
                if (query.contains(game) && game.canMatchAsContainedTitle()) {
                    return CONTAINED_TITLE_SCORE
                }
                if (game.contains(query) && query.canMatchAsContainedTitle()) {
                    return CONTAINED_TITLE_SCORE
                }
            }
        }
        return 0.0
    }

    private fun String.retroArchLabelKey(): String {
        val sepIndex = indexOf(SEPARATOR)
        if (sepIndex < 0) return ""
        val platform = substring(0, sepIndex).toCanonicalRetroPlatform()
        val title = substring(sepIndex + SEPARATOR.length).toTitleKey()
        if (platform.isBlank() || title.isBlank()) return ""
        return "$platform::$title"
    }

    private fun titleFragments(title: String): List<String> =
        title.split("/", "／", "|")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(title.trim()).filter { it.isNotBlank() } }

    private fun String.stripRomDecorations(): String =
        replace(ROM_DECORATION, " ")
            .replace(FILE_EXTENSION, "")
            .replace('_', ' ')
            .trim()

    private fun String.toTitleKey(): String {
        val romanNormalized = stripRomDecorations()
            .normalizeRomanNumerals()
            .normalizeChineseNumerals()
        return buildString {
            romanNormalized.lowercase().forEach { ch ->
                when {
                    ch.isLetterOrDigit() -> append(ch)
                    ch.isCjk() -> append(ch)
                }
            }
        }
    }

    private fun String.normalizeRomanNumerals(): String =
        replace("Ⅷ", "VIII")
            .replace("Ⅶ", "VII")
            .replace("Ⅵ", "VI")
            .replace("Ⅴ", "V")
            .replace("Ⅳ", "IV")
            .replace("Ⅲ", "III")
            .replace("Ⅱ", "II")
            .replace("Ⅰ", "I")
            .replaceRomanToken("VIII", "8")
            .replaceRomanToken("VII", "7")
            .replaceRomanToken("VI", "6")
            .replaceRomanToken("IV", "4")
            .replaceRomanToken("III", "3")
            .replaceRomanToken("II", "2")
            .replaceRomanToken("IX", "9")
            .replaceRomanToken("V", "5")
            .replaceRomanToken("I", "1")

    private fun String.replaceRomanToken(roman: String, value: String): String =
        replace(Regex("(?i)(?<![A-Za-z])$roman(?![A-Za-z])"), value)

    private fun String.normalizeChineseNumerals(): String =
        replace("一", "1")
            .replace("二", "2")
            .replace("三", "3")
            .replace("四", "4")
            .replace("五", "5")
            .replace("六", "6")
            .replace("七", "7")
            .replace("八", "8")
            .replace("九", "9")

    private fun String.isUsefulTitleFragment(): Boolean =
        isNotBlank() && toTitleKey().isUsefulTitleKey()

    private fun String.isUsefulTitleKey(): Boolean =
        length >= if (any { it.isCjk() }) MIN_CJK_TITLE_KEY_LENGTH else MIN_ASCII_TITLE_KEY_LENGTH

    private fun String.canMatchAsContainedTitle(): Boolean =
        length >= if (any { it.isCjk() }) MIN_CJK_CONTAINED_TITLE_LENGTH else MIN_ASCII_CONTAINED_TITLE_LENGTH

    private fun Char.isCjk(): Boolean =
        code in 0x4E00..0x9FFF

    private data class ScoredGame(
        val game: GameDomain,
        val score: Double,
    )

    private companion object {
        const val SEPARATOR = "__"
        const val UNKNOWN = "unknown"
        const val EXPLICIT_LABEL_SCORE = 1.10
        const val EXACT_TITLE_SCORE = 1.00
        const val CONTAINED_TITLE_SCORE = 0.86
        const val MIN_CJK_TITLE_KEY_LENGTH = 2
        const val MIN_ASCII_TITLE_KEY_LENGTH = 3
        const val MIN_CJK_CONTAINED_TITLE_LENGTH = 4
        const val MIN_ASCII_CONTAINED_TITLE_LENGTH = 8
        val ROM_DECORATION = Regex("""\s*[\(\[\{][^\)\]\}]*[\)\]\}]\s*""")
        val FILE_EXTENSION = Regex("""(?i)\.(gba|gb|gbc|sfc|smc|fig|md|gen|bin|zip|7z)$""")
    }
}
