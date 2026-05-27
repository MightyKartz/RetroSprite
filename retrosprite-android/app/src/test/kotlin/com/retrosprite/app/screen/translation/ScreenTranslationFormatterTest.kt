package com.retrosprite.app.screen.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScreenTranslationFormatterTest {

    @Test
    fun `formats translated dialogue without source text`() {
        val pages = ScreenTranslationFormatter(maxCharsPerPage = 40).format(
            translatedText = "欢迎来到港口城市。你需要先去旅店打听船长的消息。",
        )

        assertEquals(listOf("欢迎来到港口城市。你需要先去旅店打听船长的消息。"), pages)
        assertFalse(pages.first().contains("Welcome"))
    }

    @Test
    fun `splits long translation into complete pages without ellipsis`() {
        val pages = ScreenTranslationFormatter(maxCharsPerPage = 12).format(
            translatedText = "第一段内容很长。第二段内容也很长。第三段内容继续显示。",
        )

        assertEquals(
            listOf("第一段内容很长。", "第二段内容也很长。", "第三段内容继续显示。"),
            pages,
        )
        assertFalse(pages.joinToString("").contains("." + "." + "."))
    }
}
