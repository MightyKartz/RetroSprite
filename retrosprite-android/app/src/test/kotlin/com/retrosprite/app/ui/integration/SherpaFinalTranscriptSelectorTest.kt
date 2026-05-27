package com.retrosprite.app.ui.integration

import org.junit.Assert.assertEquals
import org.junit.Test

class SherpaFinalTranscriptSelectorTest {

    @Test
    fun `keeps final transcript when it is complete`() {
        assertEquals(
            "指挥官是什么",
            SherpaFinalTranscriptSelector.chooseFinalTranscript(
                finalText = "指挥官是什么",
                latestPartialText = "指挥官是什么",
            ),
        )
    }

    @Test
    fun `does not invent missing shenme tail when final and partial both drop it`() {
        assertEquals(
            "指挥官是什",
            SherpaFinalTranscriptSelector.chooseFinalTranscript(
                finalText = "指挥官是什",
                latestPartialText = "指挥官是什",
            ),
        )
    }

    @Test
    fun `does not invent play what tail when final and partial both drop it`() {
        assertEquals(
            "黄金太阳主要玩什",
            SherpaFinalTranscriptSelector.chooseFinalTranscript(
                finalText = "黄金太阳主要玩什",
                latestPartialText = "黄金太阳主要玩什",
            ),
        )
    }

    @Test
    fun `keeps longer partial when final drops the last question character`() {
        assertEquals(
            "黄金太阳主要玩什么",
            SherpaFinalTranscriptSelector.chooseFinalTranscript(
                finalText = "黄金太阳主要玩什",
                latestPartialText = "黄金太阳主要玩什么",
            ),
        )
    }

    @Test
    fun `keeps longer partial when final drops a short question tail`() {
        assertEquals(
            "克拉肯怎么过",
            SherpaFinalTranscriptSelector.chooseFinalTranscript(
                finalText = "克拉肯怎么",
                latestPartialText = "克拉肯怎么过",
            ),
        )
    }

    @Test
    fun `does not invent a specific zenme question tail`() {
        assertEquals(
            "气合之玉怎么",
            SherpaFinalTranscriptSelector.chooseFinalTranscript(
                finalText = "气合之玉怎么",
                latestPartialText = "气合之玉怎么",
            ),
        )
    }

    @Test
    fun `does not replace final with unrelated partial text`() {
        assertEquals(
            "魔石系统是什么",
            SherpaFinalTranscriptSelector.chooseFinalTranscript(
                finalText = "魔石系统是什么",
                latestPartialText = "上一轮的问题",
            ),
        )
    }

    @Test
    fun `does not prefer partial when it is not a question shaped tail`() {
        assertEquals(
            "黄金太阳主要玩",
            SherpaFinalTranscriptSelector.chooseFinalTranscript(
                finalText = "黄金太阳主要玩",
                latestPartialText = "黄金太阳主要玩法",
            ),
        )
    }
}
