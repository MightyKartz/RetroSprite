package com.retrosprite.app.domain.policy

import com.retrosprite.app.domain.models.AnswerResult
import com.retrosprite.app.domain.models.AnswerType
import com.retrosprite.app.domain.models.ControllerState
import com.retrosprite.app.domain.models.GameIdentity
import com.retrosprite.app.domain.models.SessionContext
import com.retrosprite.app.domain.models.SpoilerLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseAnswerTextLocalizerTest {

    @Test
    fun `localizes avoidable english in chinese strategy and overview answers`() {
        val result = ChineseAnswerTextLocalizer.localize(
            result = answer(
                type = AnswerType.Strategy,
                text = "Kraken / 克拉肯按中期 Boss 处理。核心是 JRPG 的 ATB、ATB 战斗和 Tech 节奏。",
            ),
            context = ctx(),
        )

        assertTrue(result.answerDetail.contains("克拉肯按中期首领战处理"))
        assertTrue(result.answerDetail.contains("日式角色扮演"))
        assertTrue(result.answerDetail.contains("行动条战斗"))
        assertFalse(result.answerDetail.contains("行动条战斗战斗"))
        assertTrue(result.answerDetail.contains("角色技"))
        assertFalse(result.answerDetail.contains("Kraken"))
        assertFalse(result.answerDetail.contains("Boss"))
    }

    @Test
    fun `does not partially replace technique skill and macro terms`() {
        val result = ChineseAnswerTextLocalizer.localize(
            result = answer(
                type = AnswerType.Mechanic,
                text = "开局不用背全表，先把 Technique、Skill 和 Macro 用顺，Tech 后面再慢慢试。",
            ),
            context = ctx(questionIntent = AnswerType.Mechanic),
        )

        assertTrue(result.answerDetail.contains("技巧、技能和宏命令"))
        assertTrue(result.answerDetail.contains("角色技"))
        assertFalse(result.answerDetail.contains("角色技nique"))
        assertFalse(result.answerDetail.contains("Technique"))
        assertFalse(result.answerDetail.contains("Skill"))
        assertFalse(result.answerDetail.contains("Macro"))
    }

    @Test
    fun `localizes avoidable english in chinese name mapping answers`() {
        val result = ChineseAnswerTextLocalizer.localize(
            result = answer(
                type = AnswerType.NameMapping,
                text = "是，同一名角色。伊万是本包规范名，伊凡作为常见中文译名/ASR 变体一起归到 Ivan。",
            ),
            context = ctx(question = "伊凡是不是伊万？", questionIntent = AnswerType.NameMapping),
        )

        assertTrue(result.answerDetail.contains("伊凡作为常见中文译名/语音识别变体一起归到伊万"))
        assertFalse(result.answerDetail.contains("Ivan"))
        assertFalse(result.answerDetail.contains("ASR"))
    }

    @Test
    fun `keeps english when chinese question explicitly asks for english name`() {
        val result = ChineseAnswerTextLocalizer.localize(
            result = answer(
                type = AnswerType.NameMapping,
                text = "Ivan / 伊万和伊凡是同一名角色。",
            ),
            context = ctx(question = "伊万英文叫什么？", questionIntent = AnswerType.NameMapping),
        )

        assertTrue(result.answerDetail.contains("Ivan"))
    }

    @Test
    fun `localizes production proper names unless question asks for english`() {
        val result = ChineseAnswerTextLocalizer.localize(
            result = answer(
                type = AnswerType.Production,
                text = "官方 Mega Drive Mini 页面把 Mega Drive 版开发商列为 SEGA；MobyGames 的资料把 Sonic Co. 列为开发者、SEGA 列为发行商。",
            ),
            context = ctx(question = "谁开发的？", questionIntent = AnswerType.Production),
        )

        assertTrue(result.answerDetail.contains("世嘉迷你复刻主机"))
        assertTrue(result.answerDetail.contains("世嘉 MD 版开发商列为世嘉"))
        assertTrue(result.answerDetail.contains("资料站的资料把索尼克公司列为开发者"))
        listOf("Mega Drive", "SEGA", "MobyGames", "Sonic").forEach { term ->
            assertFalse("answer=<${result.answerDetail}>", result.answerDetail.contains(term))
        }
    }

    @Test
    fun `localizes common pack terms and resource abbreviations in chinese answers`() {
        val result = ChineseAnswerTextLocalizer.localize(
            result = answer(
                type = AnswerType.Usage,
                text = "Fairy Powder / 妖精粉恢复异常状态；医疗草回复 10 HP。Pacalon / 帕卡隆 和 Red Baron 先低剧透处理，Boss 前留 MP。",
            ),
            context = ctx(question = "妖精粉是干嘛的？", questionIntent = AnswerType.Usage),
        )

        assertTrue(result.answerDetail.contains("妖精粉恢复异常状态"))
        assertTrue(result.answerDetail.contains("回复 10 生命值"))
        assertTrue(result.answerDetail.contains("帕卡隆"))
        assertTrue(result.answerDetail.contains("红男爵"))
        assertTrue(result.answerDetail.contains("首领战前留魔法值"))
        listOf("Fairy Powder", "Pacalon", "Red Baron", "Boss", "HP", "MP").forEach { term ->
            assertFalse("answer=<${result.answerDetail}>", result.answerDetail.contains(term))
        }
    }

    private fun answer(type: AnswerType, text: String): AnswerResult =
        AnswerResult(
            answerShort = text,
            answerDetail = text,
            answerType = type,
        )

    private fun ctx(
        question: String = "测试问题",
        questionIntent: AnswerType = AnswerType.Strategy,
    ): SessionContext = SessionContext(
        gameIdentity = GameIdentity(
            gameId = "test_game",
            title = "Test Game",
            platform = "libretro",
            region = null,
            source = "gkp_index",
        ),
        playerQuestion = question,
        screenshotBase64 = null,
        state = ControllerState.EMPTY,
        spoilerLevel = SpoilerLevel.LIGHT,
        language = "zh",
        questionIntent = questionIntent,
    )
}
