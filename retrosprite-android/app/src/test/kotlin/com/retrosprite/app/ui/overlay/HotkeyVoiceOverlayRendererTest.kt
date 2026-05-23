package com.retrosprite.app.ui.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HotkeyVoiceOverlayRendererTest {

    @Test
    fun `no evidence answer card has room for suggested questions`() {
        val normal = HotkeyVoiceOverlayPhase.NoEvidence.answerCardSpec(
            fontScale = 1.0f,
            answerText = suggestedNoEvidenceText,
            cardWidthDp = 420,
        )
        assertTrue(normal.maxLines in 5..7)
        assertTrue(normal.heightDp in 144..210)
        assertTrue(normal.bottomMarginDp <= 24)

        val largeText = HotkeyVoiceOverlayPhase.NoEvidence.answerCardSpec(
            fontScale = 1.35f,
            answerText = suggestedNoEvidenceText,
            cardWidthDp = 420,
        )
        assertEquals(normal.maxLines, largeText.maxLines)
        assertTrue(largeText.heightDp > normal.heightDp)
    }

    @Test
    fun `short no evidence answer stays compact`() {
        val short = HotkeyVoiceOverlayPhase.NoEvidence.answerCardSpec(
            fontScale = 1.0f,
            answerText = "我还没有足够证据回答这个问题。",
            cardWidthDp = 420,
        )

        assertEquals(3, short.maxLines)
        assertTrue(short.heightDp in 108..136)
        assertTrue(short.estimatedCjkCapacity(cardWidthDp = 420, fontSizeSp = 18) >= 40)
    }

    @Test
    fun `long no evidence answer is capped to avoid covering the game`() {
        val long = (1..12).joinToString("\n") { index ->
            "· 建议问题 $index：哪些角色适合培养？"
        }

        val spec = HotkeyVoiceOverlayPhase.NoEvidence.answerCardSpec(
            fontScale = 1.0f,
            answerText = long,
            cardWidthDp = 420,
        )

        assertEquals(7, spec.maxLines)
        assertTrue(spec.heightDp <= 230)
    }

    @Test
    fun `regular answer card remains compact but can wrap chinese short answers`() {
        val normal = HotkeyVoiceOverlayPhase.Speaking.answerCardSpec(
            fontScale = 1.0f,
            answerText = "通用原则：优先练能稳定出场。",
            cardWidthDp = 420,
        )
        assertEquals(3, normal.maxLines)
        assertTrue(normal.heightDp in 108..128)
        assertTrue(normal.bottomMarginDp <= 24)
        assertTrue(
            "three visible lines should not leave roughly two blank lines at the bottom",
            normal.heightDp - normal.textTopDp - normal.textBottomDp <= 78,
        )
        assertTrue(normal.estimatedCjkCapacity(cardWidthDp = 420, fontSizeSp = 18) >= 40)

        val largeText = HotkeyVoiceOverlayPhase.Speaking.answerCardSpec(
            fontScale = 1.35f,
            answerText = listOf(
                "通用原则：优先练能稳定出场、补足治疗或远程输出、移动和生存不拖队伍的角色；",
                "队伍搭配上保留治疗、稳定前排和安全输出。",
                "告诉我你现在到哪一章或刚收了哪些角色，我可以更具体。",
            ).joinToString(""),
            cardWidthDp = 420,
        )
        assertTrue(largeText.maxLines > normal.maxLines)
        assertTrue(largeText.heightDp > normal.heightDp)
    }

    @Test
    fun `rg476h answer card keeps text away from waveform chrome`() {
        val normal = HotkeyVoiceOverlayPhase.Speaking.answerCardSpec(
            fontScale = 1.0f,
            answerText = "通用原则：优先练能稳定出场。",
            cardWidthDp = 420,
        )

        assertTrue(normal.textStartDp <= 58)
        assertTrue(normal.textEndDp <= 22)
        assertTrue(normal.textTopDp >= 18)
        assertTrue(normal.textBottomDp >= 18)
    }

    @Test
    fun `error answer card stays short`() {
        val normal = HotkeyVoiceOverlayPhase.Error.answerCardSpec(fontScale = 1.0f)
        assertEquals(1, normal.maxLines)
        assertEquals(112, normal.heightDp)
    }

    @Test
    fun `answer card grows by visible line count while typing`() {
        val compact = HotkeyVoiceOverlayPhase.NoEvidence.answerCardSpec(
            fontScale = 1.0f,
            answerText = suggestedNoEvidenceText.take(18),
            cardWidthDp = 420,
        )
        val expanded = HotkeyVoiceOverlayPhase.NoEvidence.answerCardSpec(
            fontScale = 1.0f,
            answerText = suggestedNoEvidenceText,
            cardWidthDp = 420,
        )

        assertEquals(3, compact.maxLines)
        assertTrue(expanded.maxLines > compact.maxLines)
        assertTrue(expanded.heightDp > compact.heightDp)
        assertEquals(compact.bottomMarginDp, expanded.bottomMarginDp)
    }

    @Test
    fun `answer text size stays fixed as visible line count grows`() {
        val compact = HotkeyVoiceOverlayPhase.Speaking.answerCardSpec(
            fontScale = 1.0f,
            answerText = "20级后再转职更稳。",
            cardWidthDp = 390,
        )
        val expanded = HotkeyVoiceOverlayPhase.Speaking.answerCardSpec(
            fontScale = 1.0f,
            answerText = "通用原则：优先练能稳定出场、补足治疗或远程输出、移动和生存不拖队伍的角色；队伍搭配上保留治疗、稳定前排和安全输出。",
            cardWidthDp = 390,
        )

        assertTrue(expanded.maxLines > compact.maxLines)
        assertTrue(expanded.heightDp > compact.heightDp)
        assertEquals(18, compact.textSizeSp)
        assertEquals(compact.textSizeSp, expanded.textSizeSp)
    }

    @Test
    fun `typewriter reveals answer text progressively`() {
        val answer = "通用原则：优先练能稳定出场、补足治疗或远程输出。"

        val first = answer.typewriterVisibleText(
            phase = HotkeyVoiceOverlayPhase.Speaking,
            elapsedMillis = 0L,
            animatorsEnabled = true,
        )
        val later = answer.typewriterVisibleText(
            phase = HotkeyVoiceOverlayPhase.Speaking,
            elapsedMillis = 4_000L,
            animatorsEnabled = true,
        )

        assertTrue(first.isNotEmpty())
        assertTrue(first.length < answer.length)
        assertEquals(answer, later)
    }

    @Test
    fun `typewriter returns full answer when animations are disabled`() {
        val answer = "我还没有足够证据回答这个问题。"

        assertEquals(
            answer,
            answer.typewriterVisibleText(
                phase = HotkeyVoiceOverlayPhase.NoEvidence,
                elapsedMillis = 0L,
                animatorsEnabled = false,
            ),
        )
    }

    @Test
    fun `typewriter does not animate non answer phases`() {
        val answer = "请求失败，请稍后再试。"

        assertEquals(
            answer,
            answer.typewriterVisibleText(
                phase = HotkeyVoiceOverlayPhase.Error,
                elapsedMillis = 0L,
                animatorsEnabled = true,
            ),
        )
    }

    @Test
    fun `listening voice energy keeps a visible tail after speech drops`() {
        val smoother = ListeningVoiceEnergySmoother()

        val peak = smoother.update(now = 1_000L, target = 0.86f)
        val afterShortPause = smoother.update(now = 1_120L, target = 0f)
        val afterLongerPause = smoother.update(now = 1_520L, target = 0f)

        assertTrue(peak > 0.80f)
        assertTrue(afterShortPause > 0.28f)
        assertTrue(afterShortPause < 0.70f)
        assertTrue(afterLongerPause > 0.10f)
        assertTrue(afterLongerPause < afterShortPause)
    }

    @Test
    fun `listening voice energy still rises quickly for new speech`() {
        val smoother = ListeningVoiceEnergySmoother()

        smoother.update(now = 1_000L, target = 0f)
        val firstVoiceFrame = smoother.update(now = 1_016L, target = 0.72f)

        assertTrue(firstVoiceFrame > 0.12f)
    }

    @Test
    fun `listening voice energy follows quieter syllables without flattening them`() {
        val smoother = ListeningVoiceEnergySmoother()

        val loudFrame = smoother.update(now = 1_000L, target = 0.90f)
        val quieterFrame = smoother.update(now = 1_048L, target = 0.18f)

        assertTrue(loudFrame > 0.80f)
        assertTrue(quieterFrame > 0.28f)
        assertTrue(quieterFrame < loudFrame - 0.18f)
    }

    @Test
    fun `listening bar energy makes low voice changes visually obvious`() {
        val lowVoice = 0.05f.toListeningVisibleBarEnergy()
        val mediumVoice = 0.22f.toListeningVisibleBarEnergy()

        assertTrue(lowVoice > 0.30f)
        assertTrue(mediumVoice - lowVoice > 0.16f)
    }

    @Test
    fun `rg476h wave hud matches the reference top right card size`() {
        val spec = hotkeyWaveWindowSpec(displayWidthPx = 1280, density = 1.75f)

        assertEquals(HotkeyVoiceWindowAnchor.TopEnd, spec.anchor)
        assertEquals("wave HUD should use the enlarged reference card width", 340, spec.widthDp)
        assertEquals("wave HUD should use the enlarged reference card height", 104, spec.heightDp)
        assertEquals("wave HUD should attach to the top-right inset", 24, spec.xDp)
        assertEquals("wave HUD should attach near the top inset", 24, spec.yDp)
        assertEquals(0.80f, spec.alpha, 0.001f)
    }

    @Test
    fun `wave and answer hud cards use the same corner radius`() {
        val wave = hotkeyWaveWindowSpec(displayWidthPx = 1280, density = 1.75f)
        val answer = HotkeyVoiceOverlayPhase.Speaking.answerCardSpec(
            fontScale = 1.0f,
            answerText = "20级后再转职更稳。",
            cardWidthDp = 390,
        )

        assertEquals(answer.cornerRadiusDp, wave.cornerRadiusDp)
        assertEquals(20, wave.cornerRadiusDp)
    }

    @Test
    fun `rg476h answer hud attaches to the bottom left corner`() {
        val card = HotkeyVoiceOverlayPhase.Speaking.answerCardSpec(
            fontScale = 1.0f,
            answerText = "通用原则：优先练能稳定出场、补足治疗或远程输出、移动和生存不拖队伍的角色；队伍搭配上保留治疗、稳定前排和安全输出。",
            cardWidthDp = 390,
        )
        val spec = hotkeyAnswerWindowSpec(
            displayWidthPx = 1280,
            density = 1.75f,
            cardSpec = card,
        )

        assertEquals(HotkeyVoiceWindowAnchor.BottomStart, spec.anchor)
        assertTrue("answer HUD should not span into the command cluster", spec.widthDp <= 390)
        assertEquals("answer HUD should attach to the left inset", 16, spec.xDp)
        assertTrue("answer HUD should attach to the bottom inset", spec.yDp <= 24)
        assertEquals(0.80f, spec.alpha, 0.001f)
    }

    private val suggestedNoEvidenceText = """
        我还没有足够证据回答这个问题。
        你可以这样问：
        · 哪些角色适合培养？
        · 队伍怎么搭配？
        · Sarah 值得练吗？
    """.trimIndent()
}
