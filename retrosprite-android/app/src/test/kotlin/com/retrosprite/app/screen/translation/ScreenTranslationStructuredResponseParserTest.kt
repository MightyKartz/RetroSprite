package com.retrosprite.app.screen.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenTranslationStructuredResponseParserTest {

    @Test
    fun `formats menu json while dropping numeric noise and preserving labeled values`() {
        val glossary = ScreenTranslationGlossary(
            gameId = "final_fantasy_vi",
            displayName = "Final Fantasy VI",
            terms = listOf(
                ScreenTranslationGlossaryTerm("ITEM", "道具", "menu"),
                ScreenTranslationGlossaryTerm("RELIC", "饰品", "menu"),
                ScreenTranslationGlossaryTerm("HP", "HP", "system"),
                ScreenTranslationGlossaryTerm("Vigor", "力量", "system"),
            ),
        )

        val parsed = ScreenTranslationStructuredResponseParser().parse(
            rawText = """
                {
                  "mode": "menu",
                  "entries": [
                    {"source": "ITEM", "translation": "ITEM", "type": "menu"},
                    {"source": "RELIC", "translation": "RELIC", "type": "menu"},
                    {"source": "344", "translation": "三百四十四", "type": "number"},
                    {"source": "HP 344/344", "translation": "HP 三百四十四/三百四十四", "type": "stat"},
                    {"source": "Vigor 36", "translation": "力量 三十六", "type": "stat"},
                    {"source": "", "translation": "这是一张游戏菜单界面", "type": "description"}
                  ]
                }
            """.trimIndent(),
            glossary = glossary,
        )

        assertEquals(
            """
            菜单
            ITEM 道具 | RELIC 饰品
            属性
            HP 344/344
            Vigor 力量 36
            """.trimIndent(),
            parsed,
        )
    }

    @Test
    fun `prefers glossary source terms for ff6 equipment screen and avoids generic content group`() {
        val glossary = ScreenTranslationGlossary(
            gameId = "final_fantasy_vi",
            displayName = "Final Fantasy VI",
            terms = listOf(
                ScreenTranslationGlossaryTerm("EQUIP", "装备", "menu"),
                ScreenTranslationGlossaryTerm("OPTIMUM", "最强装备", "menu"),
                ScreenTranslationGlossaryTerm("REMOVE", "卸下", "menu"),
                ScreenTranslationGlossaryTerm("EMPTY", "空", "menu"),
                ScreenTranslationGlossaryTerm("RightHand", "右手", "equipment"),
                ScreenTranslationGlossaryTerm("LeftHand", "左手", "equipment"),
                ScreenTranslationGlossaryTerm("Head", "头部", "equipment"),
                ScreenTranslationGlossaryTerm("Body", "身体", "equipment"),
                ScreenTranslationGlossaryTerm("MBlock", "魔法回避", "system"),
                ScreenTranslationGlossaryTerm("Atk", "攻击力", "system"),
            ),
        )

        val parsed = ScreenTranslationStructuredResponseParser().parse(
            rawText = """
                {
                  "mode": "equipment",
                  "entries": [
                    {"source": "EQUIP", "translation": "装备", "type": "menu"},
                    {"source": "OPTIMUM", "translation": "最强装备", "type": "menu"},
                    {"source": "REMOVE", "translation": "卸下", "type": "menu"},
                    {"source": "EMPTY", "translation": "空", "type": "menu"},
                    {"source": "RightHand", "translation": "右手上", "type": "equipment"},
                    {"source": "LeftHand", "translation": "左手上", "type": "equipment"},
                    {"source": "Head", "translation": "头部", "type": "equipment"},
                    {"source": "Body", "translation": "身体", "type": "equipment"},
                    {"source": "MBlock", "translation": "魔法封印", "type": "stat"},
                    {"source": "Atk 108", "translation": "攻击力 一百零八", "type": "stat"},
                    {"source": "MithrilKnife", "translation": "精钢短刀", "type": "item"},
                    {"source": "Buckler", "translation": "圆盾", "type": "item"}
                  ]
                }
            """.trimIndent(),
            glossary = glossary,
        )

        assertEquals(
            """
            菜单
            EQUIP 装备 | OPTIMUM 最强装备 | REMOVE 卸下 | EMPTY 空
            装备
            RightHand 右手 | LeftHand 左手 | Head 头部 | Body 身体
            MithrilKnife 精钢短刀 | Buckler 圆盾
            属性
            MBlock 魔法回避
            Atk 攻击力 108
            """.trimIndent(),
            parsed,
        )
    }

    @Test
    fun `merges split ff6 status labels with following numeric values`() {
        val glossary = ScreenTranslationGlossary(
            gameId = "final_fantasy_vi",
            displayName = "Final Fantasy VI",
            terms = listOf(
                ScreenTranslationGlossaryTerm("STATUS", "状态", "menu"),
                ScreenTranslationGlossaryTerm("BATTLE", "战斗", "menu"),
                ScreenTranslationGlossaryTerm("MAGIC", "魔法", "menu"),
                ScreenTranslationGlossaryTerm("ITEM", "道具", "menu"),
                ScreenTranslationGlossaryTerm("Level", "等级", "system"),
                ScreenTranslationGlossaryTerm("HP", "HP", "system"),
                ScreenTranslationGlossaryTerm("MP", "MP", "system"),
                ScreenTranslationGlossaryTerm("Your Exp", "当前经验", "system"),
                ScreenTranslationGlossaryTerm("To Next Level", "升级所需", "system"),
                ScreenTranslationGlossaryTerm("Vigor", "力量", "system"),
            ),
        )

        val parsed = ScreenTranslationStructuredResponseParser().parse(
            rawText = """
                {
                  "mode": "status",
                  "entries": [
                    {"source": "STATUS", "translation": "状态", "type": "menu"},
                    {"source": "BATTLE", "translation": "战斗", "type": "menu"},
                    {"source": "MAGIC", "translation": "魔法", "type": "menu"},
                    {"source": "ITEM", "translation": "道具", "type": "menu"},
                    {"source": "Level", "translation": "等级", "type": "stat"},
                    {"source": "12", "translation": "十二", "type": "number"},
                    {"source": "HP", "translation": "HP", "type": "stat"},
                    {"source": "344/344", "translation": "三百四十四/三百四十四", "type": "number"},
                    {"source": "MP", "translation": "MP", "type": "stat"},
                    {"source": "72/72", "translation": "七十二/七十二", "type": "number"},
                    {"source": "Your Exp", "translation": "Your 经验", "type": "stat"},
                    {"source": "12345", "translation": "一万二千三百四十五", "type": "number"},
                    {"source": "To Next Level", "translation": "升级所需", "type": "stat"},
                    {"source": "879", "translation": "八百七十九", "type": "number"},
                    {"source": "Vigor", "translation": "力量", "type": "stat"},
                    {"source": "36", "translation": "三十六", "type": "number"}
                  ]
                }
            """.trimIndent(),
            glossary = glossary,
        )

        assertEquals(
            """
            菜单
            STATUS 状态 | BATTLE 战斗 | MAGIC 魔法 | ITEM 道具
            属性
            Level 等级 12
            HP 344/344
            MP 72/72
            Your Exp 当前经验 12345
            To Next Level 升级所需 879
            Vigor 力量 36
            """.trimIndent(),
            parsed,
        )
    }

    @Test
    fun `uses explicit json value field for bilingual status rows`() {
        val glossary = ScreenTranslationGlossary(
            gameId = "final_fantasy_vi",
            displayName = "Final Fantasy VI",
            terms = listOf(
                ScreenTranslationGlossaryTerm("Your Exp", "当前经验", "system"),
                ScreenTranslationGlossaryTerm("To Next Level", "升级所需", "system"),
            ),
        )

        val parsed = ScreenTranslationStructuredResponseParser().parse(
            rawText = """
                {
                  "mode": "status",
                  "entries": [
                    {"source": "Your Exp", "translation": "Your 经验", "value": "12345", "type": "stat"},
                    {"source": "To Next Level", "translation": "升级所需", "value": "879", "type": "stat"}
                  ]
                }
            """.trimIndent(),
            glossary = glossary,
        )

        assertEquals(
            """
            属性
            Your Exp 当前经验 12345
            To Next Level 升级所需 879
            """.trimIndent(),
            parsed,
        )
    }

    @Test
    fun `renders dialogue text json without English original`() {
        val parsed = ScreenTranslationStructuredResponseParser().parse(
            rawText = """
                {
                  "mode": "dialogue",
                  "text": "卫兵：骑着机器、自命不凡的家伙！\n看招！"
                }
            """.trimIndent(),
            glossary = null,
        )

        assertEquals(
            """
            卫兵：骑着机器、自命不凡的家伙！
            看招！
            """.trimIndent(),
            parsed,
        )
    }

    @Test
    fun `renders dialogue entries as Chinese only even when source is present`() {
        val parsed = ScreenTranslationStructuredResponseParser().parse(
            rawText = """
                {
                  "mode": "dialogue",
                  "entries": [
                    {
                      "source": "GUARD: Machine-riding, self-important swine!",
                      "translation": "卫兵：骑着机器、自命不凡的家伙！",
                      "type": "dialogue"
                    },
                    {
                      "source": "Take this!",
                      "translation": "看招！",
                      "type": "dialogue"
                    }
                  ]
                }
            """.trimIndent(),
            glossary = null,
        )

        assertEquals(
            """
            卫兵：骑着机器、自命不凡的家伙！
            看招！
            """.trimIndent(),
            parsed,
        )
    }

    @Test
    fun `returns null for normal dialogue text`() {
        val parsed = ScreenTranslationStructuredResponseParser().parse(
            rawText = "欢迎来到港口城市。你需要先去旅店打听船长的消息。",
            glossary = null,
        )

        assertNull(parsed)
    }
}
