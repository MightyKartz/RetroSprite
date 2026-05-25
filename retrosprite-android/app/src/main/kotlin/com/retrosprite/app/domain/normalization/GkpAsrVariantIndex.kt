package com.retrosprite.app.domain.normalization

import com.retrosprite.app.data.models.KnowledgeAliasDomain
import com.retrosprite.app.data.models.KnowledgeChunkDomain

data class GkpAsrVariant(
    val term: String,
    val canonicalTerm: String,
    val entityId: String,
    val weight: Double,
    val kind: String,
    val source: String?,
)

class GkpAsrVariantIndex {
    fun build(rows: List<KnowledgeChunkDomain>): List<GkpAsrVariant> =
        rows.asSequence()
            .flatMap { row ->
                row.aliasMetadata.asSequence().mapNotNull { alias ->
                    alias.toAsrVariant(row)
                }
            }
            .filter { it.term != it.canonicalTerm }
            .distinctBy { "${it.term}\u0000${it.entityId}\u0000${it.canonicalTerm}" }
            .sortedWith(
                compareByDescending<GkpAsrVariant> { it.source == OBSERVED_ASR }
                    .thenByDescending { it.term.length }
                    .thenByDescending { it.weight }
                    .thenBy { it.term },
            )
            .toList()

    private fun KnowledgeAliasDomain.toAsrVariant(row: KnowledgeChunkDomain): GkpAsrVariant? {
        val cleanTerm = term.trim()
        val cleanCanonical = canonicalTerm?.trim().orEmpty()
        if (cleanTerm.isBlank() || cleanCanonical.isBlank()) return null
        val isAsrKind = kind == ASR_VARIANT || kind == OBSERVED_ASR
        val isObservedSource = source == OBSERVED_ASR
        if (!isAsrKind && !isObservedSource) return null
        return GkpAsrVariant(
            term = cleanTerm,
            canonicalTerm = cleanCanonical,
            entityId = entityId.takeIf { it.isNotBlank() } ?: row.entityId,
            weight = weight ?: if (kind == OBSERVED_ASR) DEFAULT_OBSERVED_WEIGHT else DEFAULT_ASR_VARIANT_WEIGHT,
            kind = kind,
            source = source,
        )
    }

    private companion object {
        const val ASR_VARIANT = "asr_variant"
        const val OBSERVED_ASR = "observed_asr"
        const val DEFAULT_OBSERVED_WEIGHT = 0.72
        const val DEFAULT_ASR_VARIANT_WEIGHT = 0.65
    }
}
