package com.retrosprite.app.domain.policy

import com.retrosprite.app.domain.models.AnswerDecision
import com.retrosprite.app.domain.models.ControllerState
import com.retrosprite.app.domain.models.Evidence
import com.retrosprite.app.domain.models.GameIdentity
import com.retrosprite.app.domain.models.RetrievalResult
import com.retrosprite.app.domain.models.SessionContext
import com.retrosprite.app.domain.models.SpoilerLevel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceAnswerPolicyTest {

    private val policy = EvidenceAnswerPolicy()

    @Test
    fun `returns uncertainty when no evidence is available`() = runTest {
        val decision = policy.decide(results = emptyList(), context = ctx())

        val answer = decision as AnswerDecision.DirectAnswer
        assertTrue(answer.text.contains("没有足够证据"))
        assertEquals(emptyList<String>(), answer.sources)
        assertEquals(SpoilerLevel.LIGHT, answer.spoilerLevel)
    }

    @Test
    fun `asks for game identification when resolver has no game id`() = runTest {
        val decision = policy.decide(
            results = listOf(result("mechanic.slide", "可以移动。")),
            context = ctx(gameId = null),
        )

        val answer = decision as AnswerDecision.DirectAnswer
        assertTrue(answer.text.contains("还没识别出当前游戏"))
        assertEquals(emptyList<String>(), answer.sources)
    }

    @Test
    fun `explains when a matching knowledge pack is disabled`() = runTest {
        val decision = policy.decide(
            results = listOf(result("mechanic.slide", "可以移动。")),
            context = ctx(
                gameId = null,
                identitySource = GameIdentity.SOURCE_GKP_DISABLED,
            ),
        )

        val answer = decision as AnswerDecision.DirectAnswer
        assertTrue(answer.text.contains("知识包已禁用"))
        assertTrue(answer.text.contains("Packs"))
        assertEquals(emptyList<String>(), answer.sources)
    }

    @Test
    fun `returns best admissible evidence as direct answer`() = runTest {
        val decision = policy.decide(
            results = listOf(
                result(
                    entityId = "strategy.keep-space",
                    snippet = "保持空格比追求一次大合并更重要。",
                    sourceId = "sample.2048.strategy",
                    confidence = 0.93,
                    score = 0.90,
                    spoilerLevel = SpoilerLevel.LIGHT,
                ),
            ),
            context = ctx(spoilerLevel = SpoilerLevel.LIGHT),
        )

        val answer = decision as AnswerDecision.DirectAnswer
        assertEquals("保持空格比追求一次大合并更重要。", answer.text)
        assertEquals(listOf("sample.2048.strategy"), answer.sources)
        assertEquals(SpoilerLevel.LIGHT, answer.spoilerLevel)
    }

    @Test
    fun `withholds evidence above the current spoiler tolerance`() = runTest {
        val decision = policy.decide(
            results = listOf(
                result(
                    entityId = "strategy.snake-order",
                    snippet = "把最大数字放角落，再沿边缘按递减顺序排成一条链。",
                    spoilerLevel = SpoilerLevel.CLEAR,
                )
            ),
            context = ctx(spoilerLevel = SpoilerLevel.LIGHT),
        )

        val answer = decision as AnswerDecision.DirectAnswer
        assertTrue(answer.text.contains("超过当前提示级别"))
        assertEquals(emptyList<String>(), answer.sources)
        assertEquals(SpoilerLevel.LIGHT, answer.spoilerLevel)
    }

    @Test
    fun `uses lower spoiler evidence when mixed evidence is present`() = runTest {
        val decision = policy.decide(
            results = listOf(
                result(
                    entityId = "mixed",
                    snippet = "直接答案。",
                    sourceId = "high.source",
                    confidence = 0.99,
                    score = 0.95,
                    spoilerLevel = SpoilerLevel.FULL,
                ),
                result(
                    entityId = "safe",
                    snippet = "轻提示版本。",
                    sourceId = "safe.source",
                    confidence = 0.70,
                    score = 0.80,
                    spoilerLevel = SpoilerLevel.LIGHT,
                ),
            ),
            context = ctx(spoilerLevel = SpoilerLevel.LIGHT),
        )

        val answer = decision as AnswerDecision.DirectAnswer
        assertEquals("轻提示版本。", answer.text)
        assertEquals(listOf("safe.source"), answer.sources)
    }

    @Test
    fun `uses LLM composition only when multiple admissible evidence snippets exist`() = runTest {
        val decision = policy.decide(
            results = listOf(
                result(
                    entityId = "mechanic.tile-merge",
                    snippet = "两个相同数字滑到一起会合并。",
                    sourceId = "sample.2048.rules",
                    confidence = 0.88,
                ),
                result(
                    entityId = "strategy.keep-space",
                    snippet = "棋盘快满时优先制造空格。",
                    sourceId = "sample.2048.strategy",
                    confidence = 0.86,
                ),
            ),
            context = ctx(spoilerLevel = SpoilerLevel.LIGHT),
        )

        val compose = decision as AnswerDecision.ComposeWithLlm
        assertEquals(SpoilerLevel.LIGHT, compose.spoilerLevel)
        assertEquals(
            listOf("sample.2048.rules", "sample.2048.strategy"),
            compose.evidence.map { it.sourceId },
        )
        assertTrue(compose.prompt.contains("综合本地证据"))
    }

    @Test
    fun `exact template evidence wins without llm even when other evidence exists`() = runTest {
        val decision = policy.decide(
            results = listOf(
                result(
                    entityId = "mechanic.tile-merge",
                    snippet = "把两个相同数字滑到同一方向相邻位置，它们会合成一个翻倍方块。",
                    sourceId = "sample.2048.rules",
                    confidence = 0.97,
                    score = 1.0,
                ),
                result(
                    entityId = "strategy.keep-space",
                    snippet = "棋盘快满时优先制造空格。",
                    sourceId = "sample.2048.strategy",
                    confidence = 0.86,
                    score = 0.66,
                ),
            ),
            context = ctx(spoilerLevel = SpoilerLevel.LIGHT),
        )

        val answer = decision as AnswerDecision.DirectAnswer
        assertTrue(answer.text.contains("翻倍方块"))
        assertEquals(listOf("sample.2048.rules"), answer.sources)
    }

    private fun ctx(
        gameId: String? = "2048",
        spoilerLevel: SpoilerLevel = SpoilerLevel.LIGHT,
        identitySource: String = "unknown",
    ): SessionContext = SessionContext(
        gameIdentity = if (gameId == null) {
            if (identitySource == GameIdentity.SOURCE_GKP_DISABLED) {
                GameIdentity(
                    gameId = null,
                    title = "2048",
                    platform = "libretro",
                    region = null,
                    source = identitySource,
                )
            } else {
                GameIdentity.unknown()
            }
        } else {
            GameIdentity(
                gameId = gameId,
                title = "2048",
                platform = "libretro",
                region = null,
                source = "gkp_index",
            )
        },
        playerQuestion = "怎么移动？",
        screenshotBase64 = null,
        state = ControllerState.EMPTY,
        spoilerLevel = spoilerLevel,
        language = "zh",
        recentTurns = emptyList(),
    )

    private fun result(
        entityId: String,
        snippet: String,
        sourceId: String = "sample.2048.rules",
        confidence: Double = 0.90,
        score: Double = 0.88,
        spoilerLevel: SpoilerLevel = SpoilerLevel.LIGHT,
    ): RetrievalResult = RetrievalResult(
        entityId = entityId,
        canonicalName = entityId,
        evidence = listOf(
            Evidence(
                sourceId = sourceId,
                snippet = snippet,
                score = score,
                spoilerLevel = spoilerLevel,
                progressGate = null,
            )
        ),
        confidence = confidence,
    )
}
