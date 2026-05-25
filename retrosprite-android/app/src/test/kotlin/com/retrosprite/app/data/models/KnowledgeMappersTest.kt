package com.retrosprite.app.data.models

import org.junit.Assert.assertEquals
import org.junit.Test

class KnowledgeMappersTest {

    @Test
    fun `knowledge alias metadata round trips through entity mapper`() {
        val domain = KnowledgeChunkDomain(
            id = 7L,
            gameId = "shining_force_ii_md",
            entityId = "item.mithril",
            entityType = "item",
            canonicalName = "Mithril / 秘银 / 米斯里鲁银",
            aliases = listOf("秘银", "米斯里鲁"),
            descriptionShort = "稀有锻造材料。",
            descriptionLong = null,
            progressGate = "start",
            spoilerLevel = "light",
            sourceRefs = listOf("sf2.mithril"),
            confidence = "high",
            answerTemplates = emptyList(),
            aliasMetadata = listOf(
                KnowledgeAliasDomain(
                    term = "密营",
                    entityId = "item.mithril",
                    kind = "observed_asr",
                    source = "observed_asr",
                    weight = 0.72,
                    canonicalTerm = "秘银",
                    notes = "Observed device ASR variant.",
                ),
            ),
        )

        val roundTripped = domain.toEntity().toDomain()

        assertEquals("密营", roundTripped.aliasMetadata.single().term)
        assertEquals("秘银", roundTripped.aliasMetadata.single().canonicalTerm)
        assertEquals("observed_asr", roundTripped.aliasMetadata.single().kind)
        assertEquals(0.72, roundTripped.aliasMetadata.single().weight ?: 0.0, 0.001)
    }
}
