package com.retrosprite.app.data.retrieval

import com.retrosprite.app.domain.intent.normalizeNaturalQuestion

internal enum class TemplateConceptTag {
    GameplayLoop,
    FunFactor,
    BeginnerStart,
    TeamBuild,
    Leveling,
    Location,
    ItemUsage,
    Mechanic,
    Strategy,
    Production,
    SpoilerEscalation,
}

internal object TemplateConceptExtractor {
    fun extract(vararg values: String): Set<TemplateConceptTag> =
        values.flatMap { extract(it) }.toSet()

    fun extract(value: String): Set<TemplateConceptTag> {
        val normalized = value.normalizeNaturalQuestion()
        return PHRASES.mapNotNull { (tag, phrases) ->
            if (tag == TemplateConceptTag.Location && normalized.contains("好玩在哪")) {
                return@mapNotNull null
            }
            if (tag == TemplateConceptTag.ItemUsage &&
                normalized.containsAny("主要是干嘛", "干嘛的")
            ) {
                return@mapNotNull null
            }
            tag.takeIf { phrases.any { phrase -> normalized.contains(phrase) } }
        }.toSet()
    }

    private fun String.containsAny(vararg phrases: String): Boolean =
        phrases.any(::contains)

    private val PHRASES = linkedMapOf(
        TemplateConceptTag.GameplayLoop to listOf(
            "怎么玩",
            "玩什么",
            "主要玩什么",
            "玩法是什么",
            "核心玩法",
            "主要干什么",
            "主要是干嘛",
            "干嘛的",
            "玩点是什么",
            "游戏循环",
        ),
        TemplateConceptTag.FunFactor to listOf(
            "好玩在哪",
            "乐趣",
            "爽点",
            "有意思在哪",
        ),
        TemplateConceptTag.BeginnerStart to listOf(
            "新手",
            "开局",
            "刚开始",
            "先干什么",
            "先做什么",
            "入门",
        ),
        TemplateConceptTag.TeamBuild to listOf(
            "培养",
            "值得练",
            "谁强",
            "阵容",
            "队伍",
            "搭配",
            "角色怎么搭配",
            "角色如何搭配",
            "职业怎么搭配",
            "怎么搭配",
        ),
        TemplateConceptTag.Leveling to listOf(
            "经验",
            "练级",
            "升级",
            "刷级",
            "追等级",
        ),
        TemplateConceptTag.Location to listOf(
            "在哪",
            "哪里",
            "怎么拿",
            "去哪里",
            "位置",
        ),
        TemplateConceptTag.ItemUsage to listOf(
            "干嘛",
            "有什么用",
            "给谁用",
            "怎么用",
            "用法",
        ),
        TemplateConceptTag.Mechanic to listOf(
            "转职",
            "复活",
            "属性",
            "机制",
            "规则",
        ),
        TemplateConceptTag.Strategy to listOf(
            "怎么才能赢",
            "怎样才能赢",
            "如何才能赢",
            "怎么赢",
            "有什么技巧",
            "技巧",
            "打不过",
            "打法",
            "策略",
            "站位",
            "打得稳",
            "稳健",
        ),
        TemplateConceptTag.Production to listOf(
            "谁做的",
            "谁开发",
            "开发",
            "发行",
            "哪一年",
            "平台",
        ),
        TemplateConceptTag.SpoilerEscalation to listOf(
            "直接告诉我",
            "不怕剧透",
            "具体位置",
            "详细说",
        ),
    )
}
