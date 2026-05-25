package com.retrosprite.app.domain.normalization

import com.retrosprite.app.data.models.KnowledgeAliasDomain
import com.retrosprite.app.data.models.KnowledgeChunkDomain
import org.junit.Assert.assertEquals
import org.junit.Test

class GkpAsrVariantIndexTest {

    @Test
    fun `index includes only asr variants with canonical terms`() {
        val variants = GkpAsrVariantIndex().build(
            listOf(
                rowWithAlias("item.mithril", "item", "密营", "observed_asr", "observed_asr", "秘银", 0.72),
                rowWithAlias("item.mithril", "item", "秘银", "display_alias", "community", null, 1.0),
            ),
        )

        assertEquals(listOf("密营"), variants.map { it.term })
        assertEquals("秘银", variants.single().canonicalTerm)
    }

    @Test
    fun `index sorts observed longer and higher confidence variants first`() {
        val variants = GkpAsrVariantIndex().build(
            listOf(
                rowWithAlias("item.mithril", "item", "米斯", "asr_variant", "generated_phonetic", "米斯里鲁", 0.50),
                rowWithAlias("item.mithril", "item", "米斯林鲁", "observed_asr", "observed_asr", "米斯里鲁", 0.70),
            ),
        )

        assertEquals("米斯林鲁", variants.first().term)
    }

    private fun rowWithAlias(
        entityId: String,
        entityType: String,
        term: String,
        kind: String,
        source: String?,
        canonicalTerm: String?,
        weight: Double?,
    ): KnowledgeChunkDomain =
        KnowledgeChunkDomain(
            id = 0L,
            gameId = "test_game",
            entityId = entityId,
            entityType = entityType,
            canonicalName = "测试",
            aliases = listOf(term),
            descriptionShort = "测试。",
            descriptionLong = null,
            progressGate = "start",
            spoilerLevel = "light",
            sourceRefs = listOf("test.source"),
            confidence = "high",
            answerTemplates = emptyList(),
            aliasMetadata = listOf(
                KnowledgeAliasDomain(
                    term = term,
                    entityId = entityId,
                    kind = kind,
                    source = source,
                    weight = weight,
                    canonicalTerm = canonicalTerm,
                ),
            ),
        )
}
