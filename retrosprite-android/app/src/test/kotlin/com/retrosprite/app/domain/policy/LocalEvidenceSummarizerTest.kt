package com.retrosprite.app.domain.policy

import com.retrosprite.app.domain.models.Evidence
import com.retrosprite.app.domain.models.SpoilerLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalEvidenceSummarizerTest {

    @Test
    fun `summarizes and deduplicates local evidence without llm`() {
        val summary = LocalEvidenceSummarizer().summarize(
            listOf(
                evidence("sf2.rules", "让低等级角色补最后一击。"),
                evidence("sf2.rules", "让低等级角色补最后一击。"),
                evidence("sf2.tactics", "治疗和辅助行动也能帮助部分角色追经验。"),
            )
        )

        assertEquals("让低等级角色补最后一击。", summary.answerShort)
        assertEquals("让低等级角色补最后一击。治疗和辅助行动也能帮助部分角色追经验。", summary.answerDetail)
        assertEquals(listOf("sf2.rules", "sf2.tactics"), summary.sources)
    }

    private fun evidence(sourceId: String, snippet: String): Evidence =
        Evidence(
            sourceId = sourceId,
            snippet = snippet,
            score = 0.8,
            spoilerLevel = SpoilerLevel.LIGHT,
            progressGate = null,
        )
}
