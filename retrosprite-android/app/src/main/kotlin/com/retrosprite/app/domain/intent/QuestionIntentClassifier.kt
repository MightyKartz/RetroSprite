package com.retrosprite.app.domain.intent

import com.retrosprite.app.domain.models.AnswerType

object QuestionIntentClassifier {

    fun classify(rawQuestion: String): AnswerType {
        val question = rawQuestion.normalizeNaturalQuestion()
        return classifyNormalized(question)
    }

    fun classifyNormalized(question: String): AnswerType {
        if (question.isBlank()) return AnswerType.UnknownOrOutOfScope
        if (question.containsAny("谁开发", "开发商", "发行", "发售", "制作公司", "什么时候出")) {
            return AnswerType.Production
        }
        if (question.containsAny("英文", "中文", "汉化名", "原名", "对应", "叫什么", "叫啥")) {
            return AnswerType.NameMapping
        }
        if (question.containsAny("这游戏怎么玩", "这个游戏怎么玩", "游戏怎么玩", "这游戏要怎么玩", "这个游戏要怎么玩", "游戏要怎么玩", "这游戏该怎么玩", "要怎么玩", "该怎么玩", "到底要怎么玩", "这游戏玩什么", "这个游戏玩什么", "游戏玩什么", "主要玩什么", "玩法是什么", "主要干什么", "主要是干嘛", "干嘛的", "玩点是什么", "好玩在哪", "好玩在哪里", "乐趣", "核心玩法", "适合什么玩家")) {
            return AnswerType.GameOverview
        }
        if (question.isLocationQuestion()) {
            return AnswerType.Location
        }
        if (question.containsAny("下一步", "去哪", "卡住", "路线", "往哪", "接下来")) {
            return AnswerType.RouteHint
        }
        if (question.containsAny("怎么玩经验", "经验高", "经验怎么", "练级", "升级快", "升级技巧", "升级有什么技巧", "怎样升级", "怎么升级", "刷级", "低等级怎么追")) {
            return AnswerType.Leveling
        }
        if (question.containsAny("新手", "开局", "刚开始", "前期怎么玩", "第一小时", "先干什么", "先做什么")) {
            return AnswerType.BeginnerGuide
        }
        if (question.containsAny("怎么用", "干嘛", "有什么用", "给谁用", "用法")) {
            return AnswerType.Usage
        }
        if (question.containsAny("怎么复活", "为什么不能", "怎么转职", "几级转职", "转职", "复活", "机制")) {
            return AnswerType.Mechanic
        }
        if (question.containsAny("值得练", "培养", "练谁", "练哪些", "角色练", "谁强", "阵容", "队伍里谁", "队伍怎么搭配", "队伍搭配", "角色怎么搭配", "角色如何搭配", "职业怎么搭配", "怎么搭配", "搭配", "哪些角色")) {
            return AnswerType.TeamBuild
        }
        if (question.containsAny("打不过", "敌人怎么办", "怎么打", "打法", "策略", "培养", "站位", "稳吗", "怎么才能赢", "怎样才能赢", "如何才能赢", "怎么赢", "有什么技巧", "技巧")) {
            return AnswerType.Strategy
        }
        return AnswerType.UnknownOrOutOfScope
    }

    private fun String.isLocationQuestion(): Boolean =
        containsAny("在哪里", "在哪儿", "哪拿", "怎么拿", "位置", "怎么找") ||
            (contains("在哪") && !contains("哪些")) ||
            (containsAny("是什么", "什么地方") && containsAny("森林", "村庄", "村", "城镇", "城堡", "塔"))
}
