package com.retrosprite.app.domain.policy

import com.retrosprite.app.domain.models.AnswerDecision
import com.retrosprite.app.domain.models.AnswerConfidence
import com.retrosprite.app.domain.models.AnswerType
import com.retrosprite.app.domain.models.ControllerState
import com.retrosprite.app.domain.models.Evidence
import com.retrosprite.app.domain.models.GameIdentity
import com.retrosprite.app.domain.models.RetrievalResult
import com.retrosprite.app.domain.models.SessionContext
import com.retrosprite.app.domain.models.SpoilerLevel
import com.retrosprite.app.domain.intent.NaturalQuestionFrameParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceAnswerPolicyTest {

    private val policy = EvidenceAnswerPolicy()

    @Test
    fun `returns uncertainty when no evidence is available`() = runTest {
        val decision = policy.decide(results = emptyList(), context = ctx())

        val answer = decision as AnswerDecision.DirectAnswer
        assertTrue(answer.text.contains("没有足够证据"))
        assertTrue(answer.text.contains("你可以这样问"))
        assertEquals(AnswerType.NoEvidence, answer.answerType)
        assertEquals(AnswerConfidence.Low, answer.confidence)
        assertEquals(emptyList<String>(), answer.sources)
        assertEquals(SpoilerLevel.LIGHT, answer.spoilerLevel)
    }

    @Test
    fun `no evidence suggests similar questions for character-like misses`() = runTest {
        val decision = policy.decide(
            results = emptyList(),
            context = ctx(
                question = "这个人物厉害吗？",
                questionIntent = AnswerType.UnknownOrOutOfScope,
            ),
        )

        val answer = decision as AnswerDecision.DirectAnswer
        assertTrue(answer.text.contains("你可以这样问"))
        assertTrue(answer.text.contains("哪些角色适合培养？"))
        assertTrue(answer.text.contains("队伍怎么搭配？"))
    }

    @Test
    fun `no evidence suggests item questions for item-like misses`() = runTest {
        val decision = policy.decide(
            results = emptyList(),
            context = ctx(
                question = "道具咋办？",
                questionIntent = AnswerType.Usage,
            ),
        )

        val answer = decision as AnswerDecision.DirectAnswer
        assertTrue(answer.text.contains("医疗草怎么用？"))
        assertTrue(answer.text.contains("Mithril 有什么用？"))
    }

    @Test
    fun `no evidence prefers retrieval suggested questions over fixed generic suggestions`() = runTest {
        val decision = policy.decide(
            results = emptyList(),
            context = ctx(
                question = "气合之欲怎么又",
                questionIntent = AnswerType.UnknownOrOutOfScope,
                suggestedQuestions = listOf("气合之玉怎么用？", "气合之玉给谁？"),
            ),
        )

        val answer = decision as AnswerDecision.DirectAnswer
        assertTrue(answer.text.contains("气合之玉怎么用？"))
        assertTrue(answer.text.contains("气合之玉给谁？"))
        assertFalse(answer.text.contains("这游戏怎么玩？"))
        assertEquals(listOf("气合之玉怎么用？", "气合之玉给谁？"), answer.suggestedQuestions)
    }

    @Test
    fun `no evidence for incomplete question fragment asks player to retry hotkey question`() =
        runTest {
            val decision = policy.decide(
                results = emptyList(),
                context = ctx(
                    question = "怎么获",
                    questionIntent = AnswerType.UnknownOrOutOfScope,
                    suggestedQuestions = listOf("这个道具怎么获得？", "这个道具有什么用？"),
                ),
            )

            val answer = decision as AnswerDecision.DirectAnswer
            assertTrue(answer.text.contains("没听清这个问题，可以再按一次热键重问"))
            assertFalse(answer.text.contains("没有足够证据"))
            assertTrue(answer.text.contains("这个道具怎么获得？"))
        }

    @Test
    fun `successful answers carry follow up suggestions`() = runTest {
        val decision = policy.decide(
            results = listOf(
                result(
                    entityId = "item.vigor-ball",
                    snippet = "Vigor Ball 给 Priest 系角色用于转 Master Monk。",
                    sourceId = "sf2.promotion",
                    confidence = 0.97,
                    score = 1.0,
                ),
            ),
            context = ctx(
                question = "气合之玉怎么用？",
                questionIntent = AnswerType.Usage,
                suggestedQuestions = listOf("气合之玉在哪里？", "谁适合转 Master Monk？"),
            ),
        )

        val answer = decision as AnswerDecision.DirectAnswer
        assertEquals(listOf("气合之玉在哪里？", "谁适合转 Master Monk？"), answer.suggestedQuestions)
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
        assertEquals(AnswerType.Strategy, answer.answerType)
        assertEquals(AnswerConfidence.Medium, answer.confidence)
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
    fun `uses local summary by default when multiple admissible evidence snippets exist`() = runTest {
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

        val summary = decision as AnswerDecision.LocalSummary
        assertEquals(SpoilerLevel.LIGHT, summary.spoilerLevel)
        assertEquals(
            listOf("sample.2048.rules", "sample.2048.strategy"),
            summary.evidence.map { it.sourceId },
        )
        assertEquals(AnswerType.Strategy, summary.answerType)
    }

    @Test
    fun `can still opt into llm composition when multiple admissible evidence snippets exist`() = runTest {
        val decision = EvidenceAnswerPolicy(composeWithLlmForMultipleEvidence = true).decide(
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
        assertEquals(AnswerConfidence.High, answer.confidence)
        assertEquals(listOf("sample.2048.rules"), answer.sources)
    }

    @Test
    fun `route hints without progress context ask for current location`() = runTest {
        val decision = policy.decide(
            results = emptyList(),
            context = ctx(
                question = "卡住了下一步去哪？",
                questionIntent = AnswerType.RouteHint,
            ),
        )

        val answer = decision as AnswerDecision.DirectAnswer
        assertTrue(answer.text.contains("你现在在哪个城镇"))
        assertEquals(AnswerType.RouteHint, answer.answerType)
    }

    @Test
    fun `asks clarification when entity candidates are too close`() = runTest {
        val decision = policy.decide(
            results = listOf(
                result(
                    entityId = "npc.sarah",
                    snippet = "Sarah 是治疗角色。",
                    sourceId = "sf2.sarah",
                    confidence = 0.90,
                ),
                result(
                    entityId = "npc.sheela",
                    snippet = "Sheela 是后期角色。",
                    sourceId = "sf2.sheela",
                    confidence = 0.88,
                ),
            ),
            context = ctx(
                question = "这个角色怎么用？",
                questionIntent = AnswerType.Usage,
            ),
        )

        val clarification = decision as AnswerDecision.AskClarification
        assertTrue(clarification.question.contains("npc.sarah"))
        assertTrue(clarification.question.contains("npc.sheela"))
    }

    private fun ctx(
        gameId: String? = "2048",
        spoilerLevel: SpoilerLevel = SpoilerLevel.LIGHT,
        identitySource: String = "unknown",
        question: String = "怎么移动？",
        questionIntent: AnswerType = AnswerType.Strategy,
        suggestedQuestions: List<String> = emptyList(),
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
        playerQuestion = question,
        screenshotBase64 = null,
        state = ControllerState.EMPTY,
        spoilerLevel = spoilerLevel,
        language = "zh",
        recentTurns = emptyList(),
        questionIntent = questionIntent,
        naturalQuestionFrame = NaturalQuestionFrameParser.parse(question),
        suggestedQuestions = suggestedQuestions,
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
