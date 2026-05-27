package com.retrosprite.app.ui.overlay

import android.graphics.Color
import com.retrosprite.app.endpoint.RetroArchHotkeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HotkeyVoiceOverlayRendererTest {

    @Test
    fun `status label color is explicit for each hotkey voice phase`() {
        assertEquals(Color.rgb(56, 189, 248), HotkeyVoiceOverlayPhase.Wake.statusTextColor())
        assertEquals(Color.rgb(245, 158, 11), HotkeyVoiceOverlayPhase.Preparing.statusTextColor())
        assertEquals(Color.rgb(34, 197, 94), HotkeyVoiceOverlayPhase.Listening.statusTextColor())
        assertEquals(Color.rgb(110, 176, 181), HotkeyVoiceOverlayPhase.Muted.statusTextColor())
        assertEquals(Color.rgb(96, 165, 250), HotkeyVoiceOverlayPhase.Thinking.statusTextColor())
        assertEquals(Color.rgb(45, 212, 191), HotkeyVoiceOverlayPhase.Speaking.statusTextColor())
        assertEquals(Color.rgb(248, 181, 0), HotkeyVoiceOverlayPhase.NoEvidence.statusTextColor())
        assertEquals(Color.rgb(255, 107, 107), HotkeyVoiceOverlayPhase.Error.statusTextColor())
    }

    @Test
    fun `core voice status labels are english and keep mic readiness explicit`() {
        assertEquals("Preparing - mic off", HotkeyVoiceOverlayPhase.Preparing.statusLabel())
        assertEquals("Mic live", HotkeyVoiceOverlayPhase.Listening.statusLabel())
        assertEquals("Thinking", HotkeyVoiceOverlayPhase.Thinking.statusLabel())
        assertEquals("Answering", HotkeyVoiceOverlayPhase.Speaking.statusLabel())
    }

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

        assertEquals(8, spec.maxLines)
        assertTrue(spec.heightDp <= 260)
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
    fun `successful answer card has room for three follow up questions`() {
        val answerWithFollowUps = """
            Vigor Ball（活力球/气合之玉）给 Priest 系角色用于转 Master Monk。这个路线适合想让治疗角色也能打前线的队伍。

            你还可以问：
            · 气合之玉怎么用？
            · Vigor Ball 给谁？
            · 僧侣怎么变武僧？
        """.trimIndent()

        val spec = HotkeyVoiceOverlayPhase.Speaking.answerCardSpec(
            fontScale = 1.0f,
            answerText = answerWithFollowUps,
            cardWidthDp = 420,
        )

        assertTrue(spec.maxLines >= 9)
        assertTrue(spec.heightDp >= 250)
        assertTrue(spec.estimatedCjkCapacity(cardWidthDp = 420, fontSizeSp = 18) >= 170)
    }

    @Test
    fun `translation answer spec keeps complete page readable`() {
        val text = "第一段完整译文。第二段完整译文。第三段完整译文。第四段完整译文。"
        val spec = HotkeyVoiceOverlayPhase.Translation.answerCardSpec(
            fontScale = 1.0f,
            answerText = text,
            cardWidthDp = 420,
            contentKind = HotkeyVoiceOverlayContentKind.ScreenTranslation,
        )

        assertTrue(spec.heightDp >= 128)
        assertTrue(spec.maxLines >= 3)
        assertTrue(spec.estimatedCjkCapacity(cardWidthDp = 420, fontSizeSp = 16) >= text.length)
    }

    @Test
    fun `translation lookup card parses menu chips and attribute rows`() {
        val card = """
            菜单
            EQUIP 装备 | OPTIMUM 最强装备 | REMOVE 卸下 | EMPTY 空
            装备
            R-hand 右手 | L-hand 左手 | Head 头部 | Body 身体
            Mythril Knife 精钢短刀 | Buckler 圆盾
            属性
            Vigor 力量
            Speed 速度
        """.trimIndent().toTranslationLookupCardOrNull()

        requireNotNull(card)
        assertEquals("菜单", card.sections[0].title)
        assertEquals(TranslationLookupSectionStyle.Chips, card.sections[0].style)
        assertEquals(
            listOf("EQUIP 装备", "OPTIMUM 最强装备", "REMOVE 卸下", "EMPTY 空"),
            card.sections[0].items,
        )
        assertEquals("装备", card.sections[1].title)
        assertEquals(TranslationLookupSectionStyle.Chips, card.sections[1].style)
        assertTrue(card.sections[1].items.contains("Mythril Knife 精钢短刀"))
        assertEquals("属性", card.sections[2].title)
        assertEquals(TranslationLookupSectionStyle.Rows, card.sections[2].style)
        assertEquals(listOf("Vigor 力量", "Speed 速度"), card.sections[2].items)
    }

    @Test
    fun `plain dialogue translation does not become lookup card`() {
        assertEquals(null, "欢迎来到港口城市。请先去旅店。".toTranslationLookupCardOrNull())
    }

    @Test
    fun `translation lookup card spec estimates chip grid height`() {
        val text = """
            菜单
            EQUIP 装备 | OPTIMUM 最强装备 | REMOVE 卸下 | EMPTY 空
            装备
            R-hand 右手 | L-hand 左手 | Head 头部 | Body 身体
            Mythril Knife 精钢短刀 | Buckler 圆盾 | Leather Hat 皮帽 | Leather Armor 皮甲
            属性
            Vigor 力量
            Speed 速度
        """.trimIndent()

        val spec = HotkeyVoiceOverlayPhase.Translation.answerCardSpec(
            fontScale = 1.0f,
            answerText = text,
            cardWidthDp = 390,
            contentKind = HotkeyVoiceOverlayContentKind.ScreenTranslation,
        )

        assertTrue(spec.maxLines >= 8)
        assertTrue(spec.heightDp >= 220)
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

    @Test
    fun `transcript hud text shows heard question`() {
        val state = HotkeyVoiceOverlayRenderState(
            event = event(),
            phase = HotkeyVoiceOverlayPhase.Listening,
            transcript = "角色如何搭配",
        )

        assertEquals("听到：角色如何搭配", state.transcriptHudText())
    }

    @Test
    fun `transcript hud text is hidden when render state disables it`() {
        val state = HotkeyVoiceOverlayRenderState(
            event = event(),
            phase = HotkeyVoiceOverlayPhase.Listening,
            transcript = "角色如何搭配",
            showTranscriptHud = false,
        )

        assertEquals(null, state.transcriptHudText())
    }

    @Test
    fun `transcript hud text remains available when render state enables it`() {
        val state = HotkeyVoiceOverlayRenderState(
            event = event(),
            phase = HotkeyVoiceOverlayPhase.Listening,
            transcript = "角色如何搭配",
            showTranscriptHud = true,
        )

        assertEquals("听到：角色如何搭配", state.transcriptHudText())
    }

    @Test
    fun `transcript hud text shows normalized search term when different`() {
        val state = HotkeyVoiceOverlayRenderState(
            event = event(),
            phase = HotkeyVoiceOverlayPhase.Speaking,
            transcript = "修医是谁",
            normalizedTranscript = "修伊是谁",
            transcriptMatchedTerm = "修伊",
        )

        assertEquals("听到：修医是谁 · 按「修伊」检索", state.transcriptHudText())
    }

    @Test
    fun `transcript hud text truncates long recognized text`() {
        val state = HotkeyVoiceOverlayRenderState(
            event = event(),
            phase = HotkeyVoiceOverlayPhase.Listening,
            transcript = "这个游戏玩的话有什么技巧吗我现在应该怎么搭配角色",
        )

        assertEquals("听到：这个游戏玩的话有什么技巧吗我现在应该怎么...", state.transcriptHudText())
    }

    private fun event(): RetroArchHotkeyEvent =
        RetroArchHotkeyEvent(
            label = "mega_drive__光明力量2",
            outputMode = "text",
            imageBytes = 4,
            paused = false,
            imageBase64 = "fake_screen_png_base64",
            receivedAtMillis = 1L,
        )

    private val suggestedNoEvidenceText = """
        我还没有足够证据回答这个问题。
        你可以这样问：
        · 哪些角色适合培养？
        · 队伍怎么搭配？
        · Sarah 值得练吗？
    """.trimIndent()
}
