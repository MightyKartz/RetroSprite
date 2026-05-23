package com.retrosprite.app.domain.intent

import com.retrosprite.app.domain.models.AnswerType
import org.junit.Assert.assertEquals
import org.junit.Test

class QuestionIntentClassifierTest {

    @Test
    fun `classifies high confidence game question intents with rules`() {
        val cases = listOf(
            "勇者之证英文叫什么" to AnswerType.NameMapping,
            "Medical Herb 怎么用" to AnswerType.Usage,
            "Mithril 在哪里" to AnswerType.Location,
            "精灵森林是什么" to AnswerType.Location,
            "古代之塔是什么" to AnswerType.Location,
            "下一步去哪" to AnswerType.RouteHint,
            "怎么复活" to AnswerType.Mechanic,
            "Sarah 值得练吗" to AnswerType.TeamBuild,
            "这游戏怎么玩" to AnswerType.GameOverview,
            "这游戏要怎么玩" to AnswerType.GameOverview,
            "这个游戏要怎么玩" to AnswerType.GameOverview,
            "这游戏该怎么玩" to AnswerType.GameOverview,
            "这个游戏到底要怎么玩" to AnswerType.GameOverview,
            "这游戏玩什么" to AnswerType.GameOverview,
            "这个游戏玩什么" to AnswerType.GameOverview,
            "玩法是什么" to AnswerType.GameOverview,
            "主要干什么" to AnswerType.GameOverview,
            "这个游戏主要是干嘛的" to AnswerType.GameOverview,
            "玩点是什么" to AnswerType.GameOverview,
            "新手先干什么" to AnswerType.BeginnerGuide,
            "刚开始应该干嘛" to AnswerType.BeginnerGuide,
            "新手前期怎么玩稳" to AnswerType.BeginnerGuide,
            "现在哪些角色适合培养" to AnswerType.TeamBuild,
            "角色练哪些比较稳" to AnswerType.TeamBuild,
            "队伍怎么搭配" to AnswerType.TeamBuild,
            "角色如何搭配" to AnswerType.TeamBuild,
            "职业怎么搭配" to AnswerType.TeamBuild,
            "哪些角色直练" to AnswerType.TeamBuild,
            "怎么玩经验高" to AnswerType.Leveling,
            "升级有什么技巧" to AnswerType.Leveling,
            "打不过敌人怎么办" to AnswerType.Strategy,
            "怎么才能赢" to AnswerType.Strategy,
            "这个游戏玩的话有什么技巧吗" to AnswerType.Strategy,
            "谁开发的" to AnswerType.Production,
            "这个游戏有没有交易系统" to AnswerType.UnknownOrOutOfScope,
        )

        cases.forEach { (question, expected) ->
            assertEquals("question=<$question>", expected, QuestionIntentClassifier.classify(question))
        }
    }

    @Test
    fun `normalizes observed asr promotion confusions before intent classification`() {
        val cases = listOf(
            "角色怎么专职",
            "角色怎么转直",
            "角色什么软直啊",
            "接受他几部这个角色",
        )

        cases.forEach { question ->
            assertEquals("question=<$question>", AnswerType.Mechanic, QuestionIntentClassifier.classify(question))
        }
    }

    @Test
    fun `normalizes observed asr team building confusions before intent classification`() {
        val cases = listOf(
            "那些角色适合培养",
            "那这些角色适合培养",
            "那些人物适合培养",
            "哪些人物适合培养",
            "那些队员适合培养",
        )

        cases.forEach { question ->
            assertEquals(
                "question=<$question>",
                AnswerType.TeamBuild,
                QuestionIntentClassifier.classify(question),
            )
            assertEquals(
                "question=<$question>",
                "哪些角色适合培养",
                question.normalizeNaturalQuestion(),
            )
        }
    }

    @Test
    fun `normalizes observed asr team composition confusions before intent classification`() {
        val cases = listOf(
            "对我怎么搭配",
            "对于我怎么搭配",
        )

        cases.forEach { question ->
            assertEquals(
                "question=<$question>",
                AnswerType.TeamBuild,
                QuestionIntentClassifier.classify(question),
            )
            assertEquals(
                "question=<$question>",
                "队伍怎么搭配",
                question.normalizeNaturalQuestion(),
            )
        }
    }
}
