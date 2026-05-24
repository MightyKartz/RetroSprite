package com.retrosprite.app.domain.policy

import com.retrosprite.app.domain.models.AnswerResult
import com.retrosprite.app.domain.models.AnswerType
import com.retrosprite.app.domain.models.SessionContext

internal object ChineseAnswerTextLocalizer {

    fun localize(result: AnswerResult, context: SessionContext): AnswerResult {
        if (!context.language.startsWith("zh", ignoreCase = true)) return result
        if (result.answerType in ENGLISH_REQUIRED_TYPES) {
            return result.copy(
                suggestedQuestions = result.suggestedQuestions
                    .map { it.toChineseDisplayText() }
                    .distinct(),
            )
        }
        return result.copy(
            answerShort = result.answerShort.toChineseDisplayText(),
            answerDetail = result.answerDetail.toChineseDisplayText(),
            suggestedQuestions = result.suggestedQuestions
                .map { it.toChineseDisplayText() }
                .distinct(),
        )
    }

    private fun String.toChineseDisplayText(): String =
        DISPLAY_REPLACEMENTS.fold(this) { text, (english, chinese) ->
            text.replace(english, chinese, ignoreCase = false)
        }.cleanChineseSpacing()

    private fun String.cleanChineseSpacing(): String =
        replace(Regex("(?<=[\\p{IsHan}）])\\s+(?=[\\p{IsHan}（])"), "")

    private val ENGLISH_REQUIRED_TYPES: Set<AnswerType> = setOf(
        AnswerType.NameMapping,
        AnswerType.Production,
    )

    private val DISPLAY_REPLACEMENTS: List<Pair<String, String>> = listOf(
        "Vigor Ball（活力球/气合之玉）" to "气合之玉（活力球）",
        "Vigor Ball（汉化名也可见“气合之玉”）" to "气合之玉",
        "Mithril（米斯里鲁银）" to "米斯里鲁银",
        "Mithril（汉化名也可见“米斯里鲁银”）" to "米斯里鲁银",
        "Dwarven Town（矮人村）" to "矮人村",
        "Elven Town（精灵森林）" to "精灵森林",
        "Dwarven Blacksmith" to "矮人工匠",
        "Dwarven Town" to "矮人村",
        "Elven Town" to "精灵森林",
        "New Granseal" to "新格兰西尔",
        "Master Monk" to "武僧",
        "Pegasus Knight" to "飞马骑士",
        "Brass Gunner" to "重装炮手",
        "Warrior Pride" to "勇者之证",
        "Pegasus Wing" to "飞马之翼",
        "Vigor Ball" to "气合之玉",
        "Secret Book" to "秘传书",
        "Silver Tank" to "银战车",
        "Mithril" to "米斯里鲁银",
        "Medical Herb" to "医疗草",
        "Healing Seed" to "治疗种子",
        "Angel Wing" to "天使之翼",
        "Power Water" to "力量水",
        "Protect Milk" to "防御牛奶",
        "Quick Chicken" to "疾风鸡肉",
        "Bright Honey" to "明亮蜂蜜",
        "Cheerful Bread" to "快乐面包",
        "Running Pimento" to "跑步辣椒",
        "Brave Apple" to "勇气苹果",
        "Priest" to "僧侣",
        "Warrior" to "战士",
        "Knight" to "骑士",
        "Mage" to "法师",
        "Archer" to "弓箭手",
        "Sorcerer" to "召唤师",
        "Wizard" to "巫师",
        "Bowie" to "博伊",
        "Sarah" to "莎拉",
        "Chester" to "修伊",
        "Jaha" to "佳佳",
        "Kazin" to "卡森",
        "Slade" to "吉布",
        "Peter" to "皮特",
        "May" to "玛琪露达",
        "Kiwi" to "奇维",
        "Rick" to "里克",
    )
}
