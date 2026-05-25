package com.retrosprite.app.ui.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.retrosprite.app.R
import com.retrosprite.app.endpoint.RetroArchHotkeyEvent
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class AndroidHotkeyVoiceOverlayRenderer(
    context: Context,
) : HotkeyVoiceOverlayRenderer {

    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val waveView = HotkeyVoiceWaveView(appContext)
    private val answerView = HotkeyVoiceAnswerView(appContext) {
        updateAnswerWindowForVisibleText()
    }
    private val waveParams = WindowManager.LayoutParams(
        appContext.dp(WAVE_COMPACT_WIDTH_DP),
        appContext.dp(WAVE_COMPACT_HEIGHT_DP),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        applyWindowSpec(appContext, appContext.waveWindowSpec())
    }
    private val answerParams = WindowManager.LayoutParams(
        answerWidthPx(),
        appContext.dp(112),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        applyWindowSpec(
            appContext,
            appContext.answerWindowSpec(
                HotkeyVoiceOverlayPhase.Speaking.answerCardSpec(
                    fontScale = appContext.resources.configuration.fontScale,
                    answerText = "",
                ),
            ),
        )
    }

    private var isShown: Boolean = false
    private var isAnswerShown: Boolean = false

    override fun show(event: RetroArchHotkeyEvent) {
        waveView.bind(event)
        answerView.clear()
        hideAnswerWindow()
        if (isShown) return
        waveParams.applyWindowSpec(appContext, appContext.waveWindowSpec())
        runCatching {
            windowManager.addView(waveView, waveParams)
            isShown = true
        }.onFailure { error ->
            Log.w(TAG, "Unable to show hotkey voice overlay", error)
        }
    }

    override fun render(state: HotkeyVoiceOverlayRenderState) {
        waveView.render(state)
        if (state.answerText.isNullOrBlank()) {
            hideAnswerWindow()
            return
        }
        answerView.render(state)
        showAnswerWindow()
    }

    override fun hide() {
        hideAnswerWindow()
        if (!isShown) return
        runCatching {
            windowManager.removeView(waveView)
        }.onFailure { error ->
            Log.w(TAG, "Unable to hide hotkey voice overlay", error)
        }
        isShown = false
    }

    private fun showAnswerWindow() {
        if (!isShown) return
        val answerWidth = answerWidthPx()
        val cardSpec = answerView.currentAnswerCardSpec(answerWidth)
        answerParams.applyWindowSpec(appContext, appContext.answerWindowSpec(cardSpec))
        if (isAnswerShown) {
            runCatching {
                windowManager.updateViewLayout(answerView, answerParams)
            }.onFailure { error ->
                Log.w(TAG, "Unable to update hotkey answer overlay", error)
            }
            return
        }
        runCatching {
            windowManager.addView(answerView, answerParams)
            isAnswerShown = true
        }.onFailure { error ->
            Log.w(TAG, "Unable to show hotkey answer overlay", error)
        }
    }

    private fun updateAnswerWindowForVisibleText() {
        if (!isShown || !isAnswerShown) return
        val answerWidth = answerWidthPx()
        val cardSpec = answerView.currentAnswerCardSpec(answerWidth)
        answerParams.applyWindowSpec(appContext, appContext.answerWindowSpec(cardSpec))
        runCatching {
            windowManager.updateViewLayout(answerView, answerParams)
        }.onFailure { error ->
            Log.w(TAG, "Unable to resize hotkey answer overlay", error)
        }
    }

    private fun hideAnswerWindow() {
        if (!isAnswerShown) return
        runCatching {
            windowManager.removeView(answerView)
        }.onFailure { error ->
            Log.w(TAG, "Unable to hide hotkey answer overlay", error)
        }
        isAnswerShown = false
    }

    private fun answerWidthPx(): Int =
        appContext.dp(
            appContext.answerWindowSpec(
                HotkeyVoiceOverlayPhase.Speaking.answerCardSpec(
                    fontScale = appContext.resources.configuration.fontScale,
                    answerText = "",
                ),
            ).widthDp,
        )

    private companion object {
        const val TAG = "RetroSprite/Overlay"
    }
}

private class HotkeyVoiceWaveView(context: Context) : View(context) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(224, 2, 6, 13)
    }
    private val waveTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(28, 45, 212, 191)
    }
    private val glowStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(3.0f)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(1.2f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = context.sp(12)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        letterSpacing = 0.03f
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = context.sp(13)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        letterSpacing = 0f
    }
    private val transcriptPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(238, 235, 248, 246)
        textSize = context.sp(11)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        letterSpacing = 0f
    }
    private val transcriptBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(142, 2, 6, 13)
    }
    private val scanlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(12, 255, 255, 255)
        strokeWidth = 1f
    }
    private val micPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = context.dp(2.8f)
    }
    private val microphoneIcon = context.getDrawable(R.drawable.ic_mic_filled_24)?.mutate()
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val roundedRect = RectF()
    private val waveformProfile = floatArrayOf(
        0.36f, 0.46f, 0.42f, 0.50f, 0.58f,
        0.68f, 0.54f, 0.74f, 0.50f, 0.66f,
        0.76f, 0.62f, 0.88f, 0.70f, 0.82f,
        0.58f, 0.72f, 0.64f, 0.56f, 0.68f,
        0.52f, 0.47f, 0.42f, 0.36f, 0.31f,
    )
    private var event: RetroArchHotkeyEvent? = null
    private var renderState: HotkeyVoiceOverlayRenderState? = null
    private val listeningEnergySmoother = ListeningVoiceEnergySmoother()

    fun bind(event: RetroArchHotkeyEvent) {
        this.event = event
        this.renderState = null
        listeningEnergySmoother.reset()
        invalidate()
    }

    fun render(state: HotkeyVoiceOverlayRenderState) {
        this.renderState = state
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val phase = renderState?.phase ?: HotkeyVoiceOverlayPhase.Wake
        val accent = phase.accentColor()
        val radius = context.dp(HUD_CORNER_RADIUS_DP.toFloat())
        roundedRect.set(0f, 0f, w, h)
        backgroundPaint.color = Color.argb(phase.surfaceAlpha(), 2, 6, 13)
        canvas.drawRoundRect(roundedRect, radius, radius, backgroundPaint)
        waveTrackPaint.color = Color.argb(28, Color.red(accent), Color.green(accent), Color.blue(accent))
        borderPaint.color = Color.argb(phase.borderAlpha(), Color.red(accent), Color.green(accent), Color.blue(accent))
        glowStrokePaint.color = Color.argb(phase.glowAlpha(), Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRoundRect(
            context.dp(1.6f),
            context.dp(1.6f),
            w - context.dp(1.6f),
            h - context.dp(1.6f),
            radius - context.dp(1.6f),
            radius - context.dp(1.6f),
            glowStrokePaint,
        )
        canvas.drawRoundRect(
            context.dp(0.5f),
            context.dp(0.5f),
            w - context.dp(0.5f),
            h - context.dp(0.5f),
            radius,
            radius,
            borderPaint,
        )
        drawScanlines(canvas, w, h)
        drawHudLabel(canvas, phase, accent)

        val now = SystemClock.uptimeMillis()
        val amplitude = renderState?.amplitude?.coerceIn(0f, 1f) ?: 0f
        val animatorsEnabled = ValueAnimator.areAnimatorsEnabled()
        drawReferenceWaveform(canvas, phase, now, amplitude, animatorsEnabled, w, h)
        drawTranscriptCaption(canvas, w)

        if (isAttachedToWindow && animatorsEnabled) {
            val redrawDelay = if (phase == HotkeyVoiceOverlayPhase.Listening) {
                LISTENING_REDRAW_MS
            } else {
                STATE_REDRAW_MS
            }
            postInvalidateDelayed(redrawDelay)
        }
    }

    private fun drawHudLabel(canvas: Canvas, phase: HotkeyVoiceOverlayPhase, accent: Int) {
        val labelAlpha = phase.labelAlpha()
        labelPaint.color = Color.argb(labelAlpha, 195, 255, 247)
        canvas.drawText("RETROSPRITE", context.dp(28f), context.dp(36f), labelPaint)
        val statusColor = phase.statusTextColor()
        statusPaint.color = Color.argb(
            labelAlpha,
            Color.red(statusColor),
            Color.green(statusColor),
            Color.blue(statusColor),
        )
        val status = phase.statusLabel()
        val rightInset = if (phase == HotkeyVoiceOverlayPhase.Wake ||
            phase == HotkeyVoiceOverlayPhase.Preparing
        ) {
            context.dp(28f)
        } else {
            context.dp(72f)
        }
        val statusX = width - rightInset - statusPaint.measureText(status)
        canvas.drawText(status, statusX, context.dp(36f), statusPaint)
        drawMicrophoneGlyph(canvas, phase, accent)
    }

    private fun drawMicrophoneGlyph(canvas: Canvas, phase: HotkeyVoiceOverlayPhase, accent: Int) {
        if (phase == HotkeyVoiceOverlayPhase.Wake ||
            phase == HotkeyVoiceOverlayPhase.Preparing
        ) return
        val labelAlpha = phase.labelAlpha()
        micPaint.color = Color.argb(labelAlpha, Color.red(accent), Color.green(accent), Color.blue(accent))
        val iconSize = context.dp(25f).toInt()
        val iconLeft = width - context.dp(43f).toInt()
        val iconTop = context.dp(17f).toInt()
        microphoneIcon?.let { icon ->
            icon.setTint(accent)
            icon.alpha = labelAlpha
            icon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
            icon.draw(canvas)
        }
        if (phase == HotkeyVoiceOverlayPhase.Muted) {
            micPaint.style = Paint.Style.STROKE
            micPaint.strokeWidth = context.dp(2.4f)
            canvas.drawLine(
                iconLeft + context.dp(3f),
                iconTop + context.dp(3f),
                iconLeft + iconSize - context.dp(3f),
                iconTop + iconSize - context.dp(2f),
                micPaint,
            )
        }
    }

    private fun drawTranscriptCaption(canvas: Canvas, w: Float) {
        val text = renderState?.transcriptHudText() ?: return
        val left = context.dp(28f)
        val right = w - context.dp(24f)
        val textWidth = (right - left).toInt().coerceAtLeast(1)
        val top = context.dp(44f)
        val height = context.dp(21f)
        val backgroundInsetX = context.dp(7f)
        roundedRect.set(
            left - backgroundInsetX,
            top - context.dp(2f),
            right + context.dp(5f),
            top + height,
        )
        canvas.drawRoundRect(
            roundedRect,
            context.dp(10f),
            context.dp(10f),
            transcriptBackgroundPaint,
        )

        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, transcriptPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .setMaxLines(1)
            .build()
        canvas.save()
        canvas.translate(left, top + context.dp(3f))
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawReferenceWaveform(
        canvas: Canvas,
        phase: HotkeyVoiceOverlayPhase,
        now: Long,
        amplitude: Float,
        animatorsEnabled: Boolean,
        w: Float,
        h: Float,
    ) {
        val centerY = h * 0.70f
        val targetVoiceEnergy = amplitude.toVoiceEnergy()
        val voiceEnergy = if (phase == HotkeyVoiceOverlayPhase.Listening) {
            smoothListeningEnergy(now, targetVoiceEnergy)
        } else {
            targetVoiceEnergy
        }
        if (phase == HotkeyVoiceOverlayPhase.Listening) {
            drawRealtimeListeningWaveform(canvas, voiceEnergy, w, h, centerY)
            return
        }
        if (phase == HotkeyVoiceOverlayPhase.Wake ||
            phase == HotkeyVoiceOverlayPhase.Preparing ||
            phase == HotkeyVoiceOverlayPhase.Muted
        ) {
            drawQuietWaveformLine(canvas, phase, w, centerY)
            return
        }
        roundedRect.set(context.dp(56f), h * 0.50f, w - context.dp(56f), h * 0.88f)
        waveTrackPaint.color = Color.argb(12, 45, 235, 235)
        canvas.drawRoundRect(roundedRect, context.dp(20f), context.dp(20f), waveTrackPaint)

        if (phase != HotkeyVoiceOverlayPhase.Listening) {
            drawTailDots(canvas, phase, now, animatorsEnabled, w, centerY)
        }

        val barCount = waveformProfile.size
        val barWidth = context.dp(4.0f)
        val gap = context.dp(5.8f)
        val totalWidth = barCount * barWidth + (barCount - 1) * gap
        val startX = (w - totalWidth) / 2f
        val speed = when (phase) {
            HotkeyVoiceOverlayPhase.Muted -> 360f
            HotkeyVoiceOverlayPhase.Thinking -> 270f
            HotkeyVoiceOverlayPhase.NoEvidence,
            HotkeyVoiceOverlayPhase.Error -> 230f
            HotkeyVoiceOverlayPhase.Listening -> 620f
            else -> 260f
        }

        repeat(barCount) { index ->
            val progress = index / (barCount - 1f)
            val wave = if (phase == HotkeyVoiceOverlayPhase.Listening) {
                0.5f
            } else if (animatorsEnabled) {
                ((sin(now / speed + index * 0.73f) + 1f) / 2f).coerceIn(0f, 1f)
            } else {
                0.46f
            }
            val activity = phase.waveformActivity(wave = wave, voiceEnergy = voiceEnergy).coerceIn(0f, 1f)
            val heightRatio = if (phase == HotkeyVoiceOverlayPhase.Listening) {
                (waveformProfile[index] * (0.16f + activity * 1.04f)).coerceIn(0.08f, 0.90f)
            } else {
                (
                    waveformProfile[index] * (0.36f + activity * 0.82f) +
                        wave * 0.14f * activity
                    ).coerceIn(0.12f, 0.96f)
            }
            val barHeight = h * 0.56f * heightRatio * phase.waveformScale()
            val left = startX + index * (barWidth + gap)
            val top = centerY - barHeight / 2f
            val right = left + barWidth
            val bottom = centerY + barHeight / 2f
            barPaint.color = phase.waveformColorAt(progress, alpha = 238)
            canvas.drawRoundRect(left, top, right, bottom, barWidth / 2f, barWidth / 2f, barPaint)
        }
    }

    private fun drawRealtimeListeningWaveform(
        canvas: Canvas,
        voiceEnergy: Float,
        w: Float,
        h: Float,
        centerY: Float,
    ) {
        if (voiceEnergy <= LISTENING_VISUAL_GATE) {
            drawQuietWaveformLine(canvas, HotkeyVoiceOverlayPhase.Listening, w, centerY)
            return
        }

        roundedRect.set(context.dp(40f), h * 0.49f, w - context.dp(40f), h * 0.90f)
        waveTrackPaint.color = Color.argb(16, 45, 235, 235)
        canvas.drawRoundRect(roundedRect, context.dp(22f), context.dp(22f), waveTrackPaint)

        val barCount = waveformProfile.size
        val barWidth = context.dp(4.6f)
        val availableWidth = w - context.dp(92f)
        val gap = ((availableWidth - barCount * barWidth) / (barCount - 1))
            .coerceAtLeast(context.dp(2.0f))
        val totalWidth = barCount * barWidth + (barCount - 1) * gap
        val startX = (w - totalWidth) / 2f
        val minBarHeight = context.dp(4.0f)
        val maxBarHeight = h * 0.58f

        repeat(barCount) { index ->
            val progress = index / (barCount - 1f)
            val shape = waveformProfile[index]
            val centerLift = (1f - kotlin.math.abs(progress - 0.5f) * 0.42f).coerceIn(0.76f, 1f)
            val visibleEnergy = (voiceEnergy * shape * centerLift)
                .coerceIn(0f, 1f)
                .toListeningVisibleBarEnergy()
            val barHeight = minBarHeight + maxBarHeight * visibleEnergy
            val left = startX + index * (barWidth + gap)
            val top = centerY - barHeight / 2f
            val right = left + barWidth
            val bottom = centerY + barHeight / 2f
            val edgeFade = when {
                index < 5 -> (index + 1) / 6f
                index > barCount - 6 -> (barCount - index) / 6f
                else -> 1f
            }.coerceIn(0.35f, 1f)
            val alpha = (224 * edgeFade).toInt().coerceIn(78, 238)
            barPaint.color = HotkeyVoiceOverlayPhase.Listening.waveformColorAt(progress, alpha)
            canvas.drawRoundRect(left, top, right, bottom, barWidth / 2f, barWidth / 2f, barPaint)
        }
    }

    private fun drawQuietWaveformLine(
        canvas: Canvas,
        phase: HotkeyVoiceOverlayPhase,
        w: Float,
        centerY: Float,
    ) {
        val left = context.dp(40f)
        val right = w - context.dp(40f)
        val dotCount = 64
        val dotGap = (right - left) / (dotCount - 1)
        val dotRadius = context.dp(if (phase == HotkeyVoiceOverlayPhase.Muted) 1.05f else 1.15f)
        val accent = phase.accentColor()
        val baseAlpha = if (phase == HotkeyVoiceOverlayPhase.Muted) 78 else 104
        repeat(dotCount) { index ->
            val edgeFade = if (index < 8) {
                index / 8f
            } else if (index > dotCount - 9) {
                (dotCount - 1 - index) / 8f
            } else {
                1f
            }.coerceIn(0.42f, 1f)
            dotPaint.color = Color.argb(
                (baseAlpha * edgeFade).toInt(),
                Color.red(accent),
                Color.green(accent),
                Color.blue(accent),
            )
            canvas.drawCircle(left + index * dotGap, centerY, dotRadius, dotPaint)
        }
    }

    private fun smoothListeningEnergy(now: Long, target: Float): Float {
        return listeningEnergySmoother.update(now, target)
    }

    private fun drawTailDots(
        canvas: Canvas,
        phase: HotkeyVoiceOverlayPhase,
        now: Long,
        animatorsEnabled: Boolean,
        w: Float,
        centerY: Float,
    ) {
        val dotCount = 11
        val dotGap = context.dp(6.5f)
        val dotRadius = context.dp(1.45f)
        val leftStart = context.dp(40f)
        val rightStart = w - context.dp(40f)
        repeat(dotCount) { index ->
            val pulse = if (animatorsEnabled) {
                ((sin(now / 210f + index * 0.88f) + 1f) / 2f).coerceIn(0f, 1f)
            } else {
                0.52f
            }
            val leftAlpha = (42 + index * 16 + pulse * 18).toInt().coerceAtMost(214)
            val rightAlpha = (42 + index * 15 + pulse * 18).toInt().coerceAtMost(208)
            val yNudge = if (phase == HotkeyVoiceOverlayPhase.Thinking) 0f else (pulse - 0.5f) * context.dp(1.6f)
            dotPaint.color = withAlpha(phase.accentColor(), leftAlpha)
            canvas.drawCircle(leftStart + index * dotGap, centerY + yNudge, dotRadius, dotPaint)
            dotPaint.color = withAlpha(phase.secondaryAccentColor(), rightAlpha)
            canvas.drawCircle(rightStart - index * dotGap, centerY - yNudge, dotRadius, dotPaint)
        }
    }

    private fun drawScanlines(canvas: Canvas, w: Float, h: Float) {
        var y = context.dp(9f)
        while (y < h - context.dp(8f)) {
            canvas.drawLine(context.dp(18f), y, w - context.dp(18f), y, scanlinePaint)
            y += context.dp(7f)
        }
    }

    private fun HotkeyVoiceOverlayPhase.waveformColorAt(progress: Float, alpha: Int): Int = when (this) {
        HotkeyVoiceOverlayPhase.Listening,
        HotkeyVoiceOverlayPhase.Speaking,
        HotkeyVoiceOverlayPhase.NoEvidence -> blend(
            accentColor(),
            secondaryAccentColor(),
            progress,
            alpha = alpha,
        )
        HotkeyVoiceOverlayPhase.Wake -> withAlpha(Color.rgb(56, 189, 248), (alpha * 0.72f).toInt())
        HotkeyVoiceOverlayPhase.Preparing -> withAlpha(Color.rgb(245, 158, 11), (alpha * 0.78f).toInt())
        HotkeyVoiceOverlayPhase.Muted -> withAlpha(Color.rgb(110, 176, 181), (alpha * 0.46f).toInt())
        HotkeyVoiceOverlayPhase.Thinking -> withAlpha(Color.rgb(96, 165, 250), (alpha * 0.76f).toInt())
        HotkeyVoiceOverlayPhase.Error -> withAlpha(Color.rgb(255, 107, 107), (alpha * 0.76f).toInt())
    }

}

private class HotkeyVoiceAnswerView(
    context: Context,
    private val onVisibleLineCountChanged: () -> Unit,
) : View(context) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(218, 2, 6, 13)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(2.6f)
        color = Color.argb(58, 45, 212, 191)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(1).toFloat()
    }
    private val scanlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(14, 255, 255, 255)
        strokeWidth = 1f
    }
    private val answerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(236, 245, 250, 255)
        textSize = context.sp(18)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    private val miniBarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val roundedRect = RectF()

    private var renderState: HotkeyVoiceOverlayRenderState? = null
    private var typingKey: String? = null
    private var typingStartedAtMillis: Long = 0L
    private var lastVisibleMaxLines: Int = -1

    fun clear() {
        renderState = null
        typingKey = null
        typingStartedAtMillis = 0L
        lastVisibleMaxLines = -1
        postInvalidateOnAnimation()
    }

    fun render(state: HotkeyVoiceOverlayRenderState) {
        val nextAnswer = state.answerText?.trim().orEmpty()
        val nextTypingKey = "${state.phase.name}\u0000$nextAnswer"
        if (typingKey != nextTypingKey) {
            typingKey = nextTypingKey
            typingStartedAtMillis = SystemClock.uptimeMillis()
            lastVisibleMaxLines = -1
        }
        renderState = state
        postInvalidateOnAnimation()
    }

    fun currentAnswerCardSpec(cardWidthPx: Int): HotkeyVoiceAnswerCardSpec {
        val state = renderState
        val phase = state?.phase ?: HotkeyVoiceOverlayPhase.Speaking
        val visibleAnswer = currentVisibleAnswerText(
            now = SystemClock.uptimeMillis(),
            animatorsEnabled = ValueAnimator.areAnimatorsEnabled(),
        )
        return phase.answerCardSpec(
            fontScale = context.resources.configuration.fontScale,
            answerText = visibleAnswer,
            cardWidthDp = context.pxToDp(cardWidthPx),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val state = renderState ?: return
        val answer = state.answerText?.trim().orEmpty()
        if (answer.isBlank()) return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val phase = state.phase
        val accent = phase.accentColor()
        roundedRect.set(0f, 0f, w, h)
        backgroundPaint.color = Color.argb(phase.answerSurfaceAlpha(), 2, 6, 13)
        glowPaint.color = Color.argb(phase.glowAlpha(), Color.red(accent), Color.green(accent), Color.blue(accent))
        borderPaint.color = Color.argb(156, Color.red(accent), Color.green(accent), Color.blue(accent))
        answerTextPaint.color = phase.answerTextColor()

        val radius = context.dp(
            phase.answerCardSpec(
                fontScale = context.resources.configuration.fontScale,
                answerText = state.answerText,
                cardWidthDp = context.pxToDp(w.toInt()),
            ).cornerRadiusDp.toFloat(),
        )
        canvas.drawRoundRect(roundedRect, radius, radius, backgroundPaint)
        canvas.drawRoundRect(
            context.dp(1.5f),
            context.dp(1.5f),
            w - context.dp(1.5f),
            h - context.dp(1.5f),
            radius - context.dp(1.5f),
            radius - context.dp(1.5f),
            glowPaint,
        )
        canvas.drawRoundRect(
            context.dp(0.5f),
            context.dp(0.5f),
            w - context.dp(0.5f),
            h - context.dp(0.5f),
            radius,
            radius,
            borderPaint,
        )
        drawScanlines(canvas, w, h)
        val now = SystemClock.uptimeMillis()
        val animatorsEnabled = ValueAnimator.areAnimatorsEnabled()
        val visibleAnswer = currentVisibleAnswerText(
            now = now,
            animatorsEnabled = animatorsEnabled,
        )
        notifyVisibleLineCountIfNeeded(
            phase = phase,
            visibleAnswer = visibleAnswer,
            cardWidthPx = w.toInt(),
        )
        drawMiniBars(canvas, phase, accent, now, animatorsEnabled)
        drawAnswerText(canvas, visibleAnswer, phase, w)
        if (isAttachedToWindow && animatorsEnabled && phase.isAnswerWaveAnimated()) {
            postInvalidateDelayed(72L)
        }
    }

    private fun currentVisibleAnswerText(now: Long, animatorsEnabled: Boolean): String {
        val state = renderState ?: return ""
        val fullAnswer = state.answerText?.trim().orEmpty()
        val elapsed = (now - typingStartedAtMillis).coerceAtLeast(0L)
        return fullAnswer.typewriterVisibleText(
            phase = state.phase,
            elapsedMillis = elapsed,
            animatorsEnabled = animatorsEnabled,
        )
    }

    private fun notifyVisibleLineCountIfNeeded(
        phase: HotkeyVoiceOverlayPhase,
        visibleAnswer: String,
        cardWidthPx: Int,
    ) {
        val visibleMaxLines = phase.answerCardSpec(
            fontScale = context.resources.configuration.fontScale,
            answerText = visibleAnswer,
            cardWidthDp = context.pxToDp(cardWidthPx),
        ).maxLines
        if (visibleMaxLines == lastVisibleMaxLines) return
        lastVisibleMaxLines = visibleMaxLines
        if (isAttachedToWindow) {
            post(onVisibleLineCountChanged)
        }
    }

    private fun drawMiniBars(
        canvas: Canvas,
        phase: HotkeyVoiceOverlayPhase,
        accent: Int,
        now: Long,
        animatorsEnabled: Boolean,
    ) {
        val baseX = context.dp(24f)
        val centerY = height * 0.56f
        val barWidth = context.dp(3.8f)
        val heights = when (phase) {
            HotkeyVoiceOverlayPhase.Muted -> floatArrayOf(11f, 19f, 11f)
            HotkeyVoiceOverlayPhase.Error -> floatArrayOf(13f, 21f, 13f)
            else -> floatArrayOf(18f, 34f, 24f)
        }
        val alpha = if (phase == HotkeyVoiceOverlayPhase.Muted) 164 else 235
        heights.forEachIndexed { index, heightDp ->
            val left = baseX + index * context.dp(8.5f)
            val pulse = if (animatorsEnabled && phase.isAnswerWaveAnimated()) {
                ((sin((now / 150f) + index * 1.18f) + 1f) / 2f).coerceIn(0f, 1f)
            } else {
                0.58f
            }
            val activity = when (phase) {
                HotkeyVoiceOverlayPhase.Speaking -> 0.68f + pulse * 0.44f
                HotkeyVoiceOverlayPhase.NoEvidence -> 0.56f + pulse * 0.34f
                HotkeyVoiceOverlayPhase.Thinking -> 0.46f + pulse * 0.28f
                else -> 1f
            }
            val barHeight = context.dp(heightDp * activity)
            miniBarPaint.color = when (phase) {
                HotkeyVoiceOverlayPhase.Muted,
                HotkeyVoiceOverlayPhase.Error -> Color.argb(
                    alpha,
                    Color.red(accent),
                    Color.green(accent),
                    Color.blue(accent),
                )
                else -> blend(
                    accent,
                    phase.secondaryAccentColor(),
                    index / (heights.size - 1f),
                    alpha,
                )
            }
            canvas.drawRoundRect(
                left,
                centerY - barHeight / 2f,
                left + barWidth,
                centerY + barHeight / 2f,
                barWidth / 2f,
                barWidth / 2f,
                miniBarPaint,
            )
        }
        if (phase == HotkeyVoiceOverlayPhase.Muted) {
            miniBarPaint.style = Paint.Style.STROKE
            miniBarPaint.strokeWidth = context.dp(1.5f)
            canvas.drawLine(
                baseX - context.dp(2f),
                centerY - context.dp(16f),
                baseX + context.dp(26f),
                centerY + context.dp(15f),
                miniBarPaint,
            )
            miniBarPaint.style = Paint.Style.FILL
        }
    }

    private fun drawAnswerText(canvas: Canvas, answer: String, phase: HotkeyVoiceOverlayPhase, w: Float) {
        val spec = phase.answerCardSpec(
            fontScale = context.resources.configuration.fontScale,
            answerText = answer,
            cardWidthDp = context.pxToDp(w.toInt()),
        )
        val left = context.dp(spec.textStartDp.toFloat())
        val top = context.dp(spec.textTopDp.toFloat())
        val textWidth = (w - left - context.dp(spec.textEndDp.toFloat())).toInt().coerceAtLeast(1)
        val maxLines = spec.maxLines
        val originalSize = answerTextPaint.textSize
        answerTextPaint.textSize = context.sp(spec.textSizeSp)
        val layout = buildAnswerLayout(answer, textWidth, maxLines)
        canvas.save()
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restore()
        answerTextPaint.textSize = originalSize
    }

    private fun buildAnswerLayout(
        answer: String,
        textWidth: Int,
        maxLines: Int,
    ): StaticLayout =
        StaticLayout.Builder
            .obtain(answer, 0, answer.length, answerTextPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .setLineSpacing(context.dp(1.5f), 1.10f)
            .setMaxLines(maxLines)
            .build()

    private fun drawScanlines(canvas: Canvas, w: Float, h: Float) {
        var y = context.dp(8f)
        while (y < h - context.dp(8f)) {
            canvas.drawLine(context.dp(10f), y, w - context.dp(10f), y, scanlinePaint)
            y += context.dp(8f)
        }
    }
}

private fun Context.dp(value: Int): Int =
    (value * resources.displayMetrics.density).toInt()

private fun Context.dp(value: Float): Float =
    value * resources.displayMetrics.density

private fun Context.pxToDp(value: Int): Int =
    (value / resources.displayMetrics.density).toInt()

private fun Context.sp(value: Int): Float =
    value * resources.displayMetrics.density * resources.configuration.fontScale

internal fun HotkeyVoiceOverlayRenderState.transcriptHudText(
    maxChars: Int = TRANSCRIPT_HUD_MAX_CHARS,
): String? {
    if (!showTranscriptHud) return null
    val heard = transcript?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val normalized = normalizedTranscript?.trim()?.takeIf { it.isNotEmpty() }
    val matched = transcriptMatchedTerm?.trim()?.takeIf { it.isNotEmpty() }
    val suffix = when {
        normalized != null && normalized != heard && matched != null -> " · 按「$matched」检索"
        normalized != null && normalized != heard -> " · 按「$normalized」检索"
        else -> ""
    }
    return "听到：${heard.compactForHud(maxChars)}$suffix"
        .takeWithEllipsis(maxChars = maxChars + TRANSCRIPT_HUD_EXTRA_CHARS)
}

private fun String.compactForHud(maxChars: Int): String =
    trim()
        .replace(Regex("\\s+"), " ")
        .takeWithEllipsis(maxChars = maxChars)

private fun String.takeWithEllipsis(maxChars: Int): String {
    val safeMax = maxChars.coerceAtLeast(1)
    if (codePointCount(0, length) <= safeMax) return this
    val end = offsetByCodePoints(0, safeMax)
    return substring(0, end).trimEnd() + "..."
}

internal enum class HotkeyVoiceWindowAnchor {
    TopStart,
    TopEnd,
    BottomStart,
}

internal data class HotkeyVoiceWindowSpec(
    val widthDp: Int,
    val heightDp: Int,
    val anchor: HotkeyVoiceWindowAnchor,
    val xDp: Int,
    val yDp: Int,
    val cornerRadiusDp: Int = HUD_CORNER_RADIUS_DP,
    val alpha: Float = OVERLAY_WINDOW_ALPHA,
)

internal fun hotkeyWaveWindowSpec(
    displayWidthPx: Int,
    density: Float,
): HotkeyVoiceWindowSpec {
    val screenWidthDp = (displayWidthPx / density).toInt().coerceAtLeast(320)
    val widthDp = min(
        WAVE_COMPACT_WIDTH_DP,
        (screenWidthDp - WAVE_RIGHT_MARGIN_DP * 2).coerceAtLeast(120),
    )
    return HotkeyVoiceWindowSpec(
        widthDp = widthDp,
        heightDp = WAVE_COMPACT_HEIGHT_DP,
        anchor = HotkeyVoiceWindowAnchor.TopEnd,
        xDp = WAVE_RIGHT_MARGIN_DP,
        yDp = WAVE_TOP_SAFE_MARGIN_DP,
    )
}

internal fun hotkeyAnswerWindowSpec(
    displayWidthPx: Int,
    density: Float,
    cardSpec: HotkeyVoiceAnswerCardSpec,
): HotkeyVoiceWindowSpec {
    val screenWidthDp = (displayWidthPx / density).toInt().coerceAtLeast(320)
    val widthDp = min(ANSWER_SAFE_WIDTH_DP, (screenWidthDp - 96).coerceAtLeast(300))
    return HotkeyVoiceWindowSpec(
        widthDp = widthDp,
        heightDp = cardSpec.heightDp,
        anchor = HotkeyVoiceWindowAnchor.BottomStart,
        xDp = ANSWER_LEFT_MARGIN_DP,
        yDp = ANSWER_BOTTOM_MARGIN_DP,
    )
}

private fun Context.waveWindowSpec(): HotkeyVoiceWindowSpec =
    hotkeyWaveWindowSpec(
        displayWidthPx = resources.displayMetrics.widthPixels,
        density = resources.displayMetrics.density,
    )

private fun Context.answerWindowSpec(cardSpec: HotkeyVoiceAnswerCardSpec): HotkeyVoiceWindowSpec =
    hotkeyAnswerWindowSpec(
        displayWidthPx = resources.displayMetrics.widthPixels,
        density = resources.displayMetrics.density,
        cardSpec = cardSpec,
    )

private fun WindowManager.LayoutParams.applyWindowSpec(
    context: Context,
    spec: HotkeyVoiceWindowSpec,
) {
    width = context.dp(spec.widthDp)
    height = context.dp(spec.heightDp)
    x = context.dp(spec.xDp)
    y = context.dp(spec.yDp)
    alpha = spec.alpha
    gravity = when (spec.anchor) {
        HotkeyVoiceWindowAnchor.TopStart -> Gravity.TOP or Gravity.START
        HotkeyVoiceWindowAnchor.TopEnd -> Gravity.TOP or Gravity.END
        HotkeyVoiceWindowAnchor.BottomStart -> Gravity.BOTTOM or Gravity.START
    }
}

internal data class HotkeyVoiceAnswerCardSpec(
    val heightDp: Int,
    val maxLines: Int,
    val bottomMarginDp: Int,
    val textStartDp: Int,
    val textSizeSp: Int = ANSWER_FONT_SIZE_SP,
    val cornerRadiusDp: Int = HUD_CORNER_RADIUS_DP,
    val textTopDp: Int = 18,
    val textEndDp: Int = 20,
    val textBottomDp: Int = 18,
)

internal fun HotkeyVoiceOverlayPhase.answerCardSpec(
    fontScale: Float,
    answerText: String? = null,
    cardWidthDp: Int = DEFAULT_ANSWER_CARD_WIDTH_DP,
): HotkeyVoiceAnswerCardSpec {
    val safeFontScale = fontScale.coerceIn(1.0f, 1.6f)
    return when (this) {
        HotkeyVoiceOverlayPhase.NoEvidence -> {
            val maxLines = answerText.estimatedAnswerLineCount(
                cardWidthDp = cardWidthDp,
                textStartDp = ANSWER_TEXT_START_DP,
                textEndDp = ANSWER_TEXT_END_DP,
            ).withFollowUpBreathingRoom(answerText)
                .coerceIn(NO_EVIDENCE_MIN_LINES, NO_EVIDENCE_MAX_LINES)
            HotkeyVoiceAnswerCardSpec(
                heightDp = ((NO_EVIDENCE_VERTICAL_CHROME_DP + maxLines * ANSWER_LINE_HEIGHT_DP) * safeFontScale)
                    .toInt()
                    .coerceIn(NO_EVIDENCE_MIN_HEIGHT_DP, NO_EVIDENCE_MAX_HEIGHT_DP),
                maxLines = maxLines,
                bottomMarginDp = ANSWER_BOTTOM_MARGIN_DP,
                textStartDp = ANSWER_TEXT_START_DP,
            )
        }

        HotkeyVoiceOverlayPhase.Speaking,
        HotkeyVoiceOverlayPhase.Thinking -> {
            val hasFollowUps = answerText.hasSuggestedQuestionBlock()
            val maxLineLimit = if (hasFollowUps) ANSWER_FOLLOW_UP_MAX_LINES else ANSWER_MAX_LINES
            val maxHeightLimit = if (hasFollowUps) ANSWER_FOLLOW_UP_MAX_HEIGHT_DP else ANSWER_MAX_HEIGHT_DP
            val maxLines = answerText.estimatedAnswerLineCount(
                cardWidthDp = cardWidthDp,
                textStartDp = ANSWER_TEXT_START_DP,
                textEndDp = ANSWER_TEXT_END_DP,
            ).withFollowUpBreathingRoom(answerText)
                .coerceIn(ANSWER_MIN_LINES, maxLineLimit)
            HotkeyVoiceAnswerCardSpec(
                heightDp = ((ANSWER_VERTICAL_CHROME_DP + maxLines * ANSWER_LINE_HEIGHT_DP) * safeFontScale)
                    .toInt()
                    .coerceIn(ANSWER_MIN_HEIGHT_DP, maxHeightLimit),
                maxLines = maxLines,
                bottomMarginDp = ANSWER_BOTTOM_MARGIN_DP,
                textStartDp = ANSWER_TEXT_START_DP,
            )
        }

        HotkeyVoiceOverlayPhase.Error -> HotkeyVoiceAnswerCardSpec(
            heightDp = 112,
            maxLines = 1,
            bottomMarginDp = ANSWER_BOTTOM_MARGIN_DP,
            textStartDp = ANSWER_TEXT_START_DP,
        )

        HotkeyVoiceOverlayPhase.Wake,
        HotkeyVoiceOverlayPhase.Preparing,
        HotkeyVoiceOverlayPhase.Listening,
        HotkeyVoiceOverlayPhase.Muted -> HotkeyVoiceAnswerCardSpec(
            heightDp = 112,
            maxLines = 2,
            bottomMarginDp = ANSWER_BOTTOM_MARGIN_DP,
            textStartDp = ANSWER_TEXT_START_DP,
        )
    }
}

internal fun String.typewriterVisibleText(
    phase: HotkeyVoiceOverlayPhase,
    elapsedMillis: Long,
    animatorsEnabled: Boolean,
): String {
    if (isEmpty()) return this
    if (!animatorsEnabled || !phase.isTypewriterAnimated()) return this
    val totalCodePoints = codePointCount(0, length)
    val visibleCodePoints = ((elapsedMillis * TYPEWRITER_CHARS_PER_SECOND) / 1000L)
        .toInt()
        .coerceIn(1, totalCodePoints)
    val endIndex = offsetByCodePoints(0, visibleCodePoints)
    return substring(0, endIndex)
}

private fun String?.estimatedAnswerLineCount(
    cardWidthDp: Int,
    textStartDp: Int,
    textEndDp: Int,
): Int {
    val text = this?.trim().orEmpty()
    if (text.isBlank()) return ANSWER_MIN_LINES
    val charsPerLine = ((cardWidthDp - textStartDp - textEndDp) / ANSWER_FONT_SIZE_SP)
        .coerceAtLeast(MIN_ESTIMATED_CHARS_PER_LINE)
    return text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .sumOf { line -> ((line.length + charsPerLine - 1) / charsPerLine).coerceAtLeast(1) }
        .coerceAtLeast(1)
}

private fun Int.withFollowUpBreathingRoom(answerText: String?): Int =
    if (answerText.hasSuggestedQuestionBlock()) this + 1 else this

private fun String?.hasSuggestedQuestionBlock(): Boolean {
    val text = this ?: return false
    return text.contains("你可以这样问：") || text.contains("你还可以问：")
}

internal fun HotkeyVoiceAnswerCardSpec.estimatedCjkCapacity(
    cardWidthDp: Int,
    fontSizeSp: Int,
): Int {
    val textWidthDp = (cardWidthDp - textStartDp - textEndDp).coerceAtLeast(1)
    return (textWidthDp / fontSizeSp.toFloat() * maxLines).toInt()
}

private const val DEFAULT_ANSWER_CARD_WIDTH_DP = 390
private const val ANSWER_SAFE_WIDTH_DP = 390
private const val ANSWER_LEFT_MARGIN_DP = 16
private const val ANSWER_BOTTOM_MARGIN_DP = 16
private const val WAVE_COMPACT_WIDTH_DP = 340
private const val WAVE_COMPACT_HEIGHT_DP = 104
private const val WAVE_RIGHT_MARGIN_DP = 24
private const val WAVE_TOP_SAFE_MARGIN_DP = 24
private const val HUD_CORNER_RADIUS_DP = 20
private const val OVERLAY_WINDOW_ALPHA = 0.80f
private const val ANSWER_TEXT_START_DP = 58
private const val ANSWER_TEXT_END_DP = 20
private const val ANSWER_FONT_SIZE_SP = 18
private const val ANSWER_LINE_HEIGHT_DP = 24
private const val ANSWER_MIN_LINES = 3
private const val ANSWER_MAX_LINES = 8
private const val ANSWER_FOLLOW_UP_MAX_LINES = 10
private const val ANSWER_VERTICAL_CHROME_DP = 42
private const val ANSWER_MIN_HEIGHT_DP = 112
private const val ANSWER_MAX_HEIGHT_DP = 280
private const val ANSWER_FOLLOW_UP_MAX_HEIGHT_DP = 340
private const val NO_EVIDENCE_MIN_LINES = 3
private const val NO_EVIDENCE_MAX_LINES = 8
private const val NO_EVIDENCE_VERTICAL_CHROME_DP = 42
private const val NO_EVIDENCE_MIN_HEIGHT_DP = 112
private const val NO_EVIDENCE_MAX_HEIGHT_DP = 260
private const val MIN_ESTIMATED_CHARS_PER_LINE = 8
private const val TYPEWRITER_CHARS_PER_SECOND = 24L
private const val TRANSCRIPT_HUD_MAX_CHARS = 20
private const val TRANSCRIPT_HUD_EXTRA_CHARS = 18

private fun withAlpha(color: Int, alpha: Int): Int =
    Color.argb(
        alpha.coerceIn(0, 255),
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )

private fun blend(start: Int, end: Int, amount: Float, alpha: Int): Int {
    val t = amount.coerceIn(0f, 1f)
    return Color.argb(
        alpha.coerceIn(0, 255),
        (Color.red(start) + (Color.red(end) - Color.red(start)) * t).toInt(),
        (Color.green(start) + (Color.green(end) - Color.green(start)) * t).toInt(),
        (Color.blue(start) + (Color.blue(end) - Color.blue(start)) * t).toInt(),
    )
}

internal fun HotkeyVoiceOverlayPhase.statusLabel(): String = when (this) {
    HotkeyVoiceOverlayPhase.Wake -> "Starting"
    HotkeyVoiceOverlayPhase.Preparing -> "Preparing - mic off"
    HotkeyVoiceOverlayPhase.Listening -> "Mic live"
    HotkeyVoiceOverlayPhase.Muted -> "No speech"
    HotkeyVoiceOverlayPhase.Thinking -> "Thinking"
    HotkeyVoiceOverlayPhase.Speaking -> "Answering"
    HotkeyVoiceOverlayPhase.NoEvidence -> "No evidence"
    HotkeyVoiceOverlayPhase.Error -> "Error"
}

internal fun HotkeyVoiceOverlayPhase.statusTextColor(): Int = when (this) {
    HotkeyVoiceOverlayPhase.Wake -> Color.rgb(56, 189, 248)
    HotkeyVoiceOverlayPhase.Preparing -> Color.rgb(245, 158, 11)
    HotkeyVoiceOverlayPhase.Listening -> Color.rgb(34, 197, 94)
    HotkeyVoiceOverlayPhase.Muted -> Color.rgb(110, 176, 181)
    HotkeyVoiceOverlayPhase.Thinking -> Color.rgb(96, 165, 250)
    HotkeyVoiceOverlayPhase.Speaking -> Color.rgb(45, 212, 191)
    HotkeyVoiceOverlayPhase.NoEvidence -> Color.rgb(248, 181, 0)
    HotkeyVoiceOverlayPhase.Error -> Color.rgb(255, 107, 107)
}

private fun HotkeyVoiceOverlayPhase.accentColor(): Int = when (this) {
    HotkeyVoiceOverlayPhase.Wake -> Color.rgb(56, 189, 248)
    HotkeyVoiceOverlayPhase.Preparing -> Color.rgb(245, 158, 11)
    HotkeyVoiceOverlayPhase.Listening -> Color.rgb(34, 197, 94)
    HotkeyVoiceOverlayPhase.Muted -> Color.rgb(110, 176, 181)
    HotkeyVoiceOverlayPhase.Thinking -> Color.rgb(96, 165, 250)
    HotkeyVoiceOverlayPhase.Speaking -> Color.rgb(45, 212, 191)
    HotkeyVoiceOverlayPhase.NoEvidence -> Color.rgb(248, 181, 0)
    HotkeyVoiceOverlayPhase.Error -> Color.rgb(255, 107, 107)
}

private fun HotkeyVoiceOverlayPhase.secondaryAccentColor(): Int = when (this) {
    HotkeyVoiceOverlayPhase.Wake -> Color.rgb(45, 212, 191)
    HotkeyVoiceOverlayPhase.Preparing -> Color.rgb(251, 191, 36)
    HotkeyVoiceOverlayPhase.Listening -> Color.rgb(16, 185, 129)
    HotkeyVoiceOverlayPhase.Muted -> Color.rgb(110, 176, 181)
    HotkeyVoiceOverlayPhase.Thinking -> Color.rgb(147, 197, 253)
    HotkeyVoiceOverlayPhase.Speaking -> Color.rgb(20, 184, 166)
    HotkeyVoiceOverlayPhase.NoEvidence -> Color.rgb(251, 146, 60)
    HotkeyVoiceOverlayPhase.Error -> Color.rgb(248, 113, 113)
}

private fun HotkeyVoiceOverlayPhase.glowAlpha(): Int = when (this) {
    HotkeyVoiceOverlayPhase.Wake -> 82
    HotkeyVoiceOverlayPhase.Preparing -> 96
    HotkeyVoiceOverlayPhase.Listening -> 74
    HotkeyVoiceOverlayPhase.Muted -> 32
    HotkeyVoiceOverlayPhase.Thinking -> 54
    HotkeyVoiceOverlayPhase.Speaking -> 70
    HotkeyVoiceOverlayPhase.NoEvidence -> 60
    HotkeyVoiceOverlayPhase.Error -> 68
}

private fun HotkeyVoiceOverlayPhase.waveformActivity(wave: Float, voiceEnergy: Float): Float = when (this) {
    HotkeyVoiceOverlayPhase.Wake -> 0f
    HotkeyVoiceOverlayPhase.Preparing -> 0f
    HotkeyVoiceOverlayPhase.Listening -> voiceEnergy
    HotkeyVoiceOverlayPhase.Muted -> 0f
    HotkeyVoiceOverlayPhase.Thinking -> 0.22f + wave * 0.24f
    HotkeyVoiceOverlayPhase.Speaking -> 0.48f + wave * 0.46f
    HotkeyVoiceOverlayPhase.NoEvidence -> 0.38f + wave * 0.40f
    HotkeyVoiceOverlayPhase.Error -> 0.18f + wave * 0.22f
}

private fun HotkeyVoiceOverlayPhase.surfaceAlpha(): Int = when (this) {
    HotkeyVoiceOverlayPhase.Muted -> 204
    HotkeyVoiceOverlayPhase.Error -> 226
    else -> 224
}

private fun HotkeyVoiceOverlayPhase.answerSurfaceAlpha(): Int = when (this) {
    HotkeyVoiceOverlayPhase.Muted -> 206
    HotkeyVoiceOverlayPhase.Error -> 224
    else -> 218
}

private fun HotkeyVoiceOverlayPhase.borderAlpha(): Int = when (this) {
    HotkeyVoiceOverlayPhase.Muted -> 116
    HotkeyVoiceOverlayPhase.Wake,
    HotkeyVoiceOverlayPhase.Preparing -> 166
    else -> 188
}

private fun HotkeyVoiceOverlayPhase.labelAlpha(): Int = when (this) {
    HotkeyVoiceOverlayPhase.Muted -> 166
    HotkeyVoiceOverlayPhase.NoEvidence -> 226
    HotkeyVoiceOverlayPhase.Error -> 232
    else -> 245
}

private fun HotkeyVoiceOverlayPhase.waveformScale(): Float = when (this) {
    HotkeyVoiceOverlayPhase.Wake -> 0.68f
    HotkeyVoiceOverlayPhase.Preparing -> 0.68f
    HotkeyVoiceOverlayPhase.Listening -> 1f
    HotkeyVoiceOverlayPhase.Muted -> 0.28f
    HotkeyVoiceOverlayPhase.Thinking -> 0.70f
    HotkeyVoiceOverlayPhase.Speaking -> 0.84f
    HotkeyVoiceOverlayPhase.NoEvidence -> 0.74f
    HotkeyVoiceOverlayPhase.Error -> 0.46f
}

private fun HotkeyVoiceOverlayPhase.isAnswerWaveAnimated(): Boolean = when (this) {
    HotkeyVoiceOverlayPhase.Thinking,
    HotkeyVoiceOverlayPhase.Speaking,
    HotkeyVoiceOverlayPhase.NoEvidence -> true
    HotkeyVoiceOverlayPhase.Wake,
    HotkeyVoiceOverlayPhase.Preparing,
    HotkeyVoiceOverlayPhase.Listening,
    HotkeyVoiceOverlayPhase.Muted,
    HotkeyVoiceOverlayPhase.Error -> false
}

private fun HotkeyVoiceOverlayPhase.isTypewriterAnimated(): Boolean = when (this) {
    HotkeyVoiceOverlayPhase.Speaking,
    HotkeyVoiceOverlayPhase.NoEvidence -> true
    HotkeyVoiceOverlayPhase.Wake,
    HotkeyVoiceOverlayPhase.Preparing,
    HotkeyVoiceOverlayPhase.Listening,
    HotkeyVoiceOverlayPhase.Muted,
    HotkeyVoiceOverlayPhase.Thinking,
    HotkeyVoiceOverlayPhase.Error -> false
}

private fun HotkeyVoiceOverlayPhase.answerTextColor(): Int = when (this) {
    HotkeyVoiceOverlayPhase.Muted -> Color.argb(220, 218, 233, 238)
    HotkeyVoiceOverlayPhase.NoEvidence -> Color.argb(238, 255, 236, 191)
    HotkeyVoiceOverlayPhase.Error -> Color.argb(238, 255, 222, 222)
    else -> Color.argb(236, 245, 250, 255)
}

internal class ListeningVoiceEnergySmoother(
    private val visualGate: Float = LISTENING_VISUAL_GATE,
    private val attackMs: Float = LISTENING_ATTACK_MS,
    private val responsiveReleaseMs: Float = LISTENING_RESPONSIVE_RELEASE_MS,
    private val tailReleaseMs: Float = LISTENING_TAIL_RELEASE_MS,
    private val heldTailReleaseMs: Float = LISTENING_HELD_TAIL_RELEASE_MS,
    private val peakHoldMs: Long = LISTENING_PEAK_HOLD_MS,
) {
    private var responsiveEnergy: Float = 0f
    private var tailEnergy: Float = 0f
    private var lastFrameAt: Long = 0L
    private var lastAudibleAt: Long = 0L

    fun reset() {
        responsiveEnergy = 0f
        tailEnergy = 0f
        lastFrameAt = 0L
        lastAudibleAt = 0L
    }

    fun update(now: Long, target: Float): Float {
        val clampedTarget = target.coerceIn(0f, 1f)
        if (lastFrameAt == 0L) {
            lastFrameAt = now
            responsiveEnergy = clampedTarget
            tailEnergy = clampedTarget
            if (clampedTarget > visualGate) {
                lastAudibleAt = now
            }
            return clampedTarget
        }

        val elapsed = (now - lastFrameAt).coerceIn(16L, 180L).toFloat()
        lastFrameAt = now
        if (clampedTarget > visualGate) {
            lastAudibleAt = now
        }

        val holdActive = lastAudibleAt != 0L && now - lastAudibleAt <= peakHoldMs
        responsiveEnergy = responsiveEnergy.approachEnergy(
            target = clampedTarget,
            elapsedMs = elapsed,
            responseMs = if (clampedTarget > responsiveEnergy) attackMs else responsiveReleaseMs,
            minAlpha = if (clampedTarget > responsiveEnergy) 0.14f else 0.16f,
            maxAlpha = if (clampedTarget > responsiveEnergy) 0.70f else 0.48f,
        )

        val tailTarget = if (clampedTarget >= tailEnergy) {
            clampedTarget
        } else if (holdActive) {
            (tailEnergy * LISTENING_TAIL_HOLD_DECAY_RATIO)
                .coerceAtLeast(visualGate * LISTENING_HOLD_GATE_MULTIPLIER)
                .coerceAtLeast(clampedTarget)
                .coerceAtMost(tailEnergy)
        } else {
            clampedTarget
        }

        tailEnergy = tailEnergy.approachEnergy(
            target = tailTarget.coerceIn(0f, 1f),
            elapsedMs = elapsed,
            responseMs = when {
                tailTarget > tailEnergy -> attackMs
                holdActive -> heldTailReleaseMs
                else -> tailReleaseMs
            },
            minAlpha = 0.035f,
            maxAlpha = if (holdActive) 0.46f else 0.34f,
        )

        var displayedEnergy = maxOf(
            responsiveEnergy,
            tailEnergy * LISTENING_TAIL_VISUAL_BLEND,
        )
        if (!holdActive && clampedTarget <= 0f && displayedEnergy < visualGate * 0.65f) {
            responsiveEnergy = 0f
            tailEnergy = 0f
            displayedEnergy = 0f
        }
        return displayedEnergy.coerceIn(0f, 1f)
    }

    private fun Float.approachEnergy(
        target: Float,
        elapsedMs: Float,
        responseMs: Float,
        minAlpha: Float,
        maxAlpha: Float,
    ): Float {
        val alpha = (elapsedMs / responseMs).coerceIn(minAlpha, maxAlpha)
        return (this + (target - this) * alpha).coerceIn(0f, 1f)
    }
}

private fun Float.toVoiceEnergy(): Float {
    val normalized = ((this - VOICE_NOISE_GATE) / (VOICE_SATURATION - VOICE_NOISE_GATE))
        .coerceIn(0f, 1f)
    return (sqrt(sqrt(normalized)) * VOICE_VISUAL_GAIN).coerceIn(0f, 1f)
}

internal fun Float.toListeningVisibleBarEnergy(): Float {
    if (this <= LISTENING_VISUAL_GATE) return 0f
    return (LISTENING_MIN_ACTIVE_BAR + sqrt(this) * (1f - LISTENING_MIN_ACTIVE_BAR))
        .coerceIn(0f, 1f)
}

private const val VOICE_NOISE_GATE = 0.006f
private const val VOICE_SATURATION = 0.18f
private const val VOICE_VISUAL_GAIN = 1.22f
private const val LISTENING_VISUAL_GATE = 0.03f
private const val LISTENING_MIN_ACTIVE_BAR = 0.14f
private const val LISTENING_ATTACK_MS = 34f
private const val LISTENING_RESPONSIVE_RELEASE_MS = 110f
private const val LISTENING_TAIL_RELEASE_MS = 360f
private const val LISTENING_HELD_TAIL_RELEASE_MS = 220f
private const val LISTENING_PEAK_HOLD_MS = 190L
private const val LISTENING_TAIL_HOLD_DECAY_RATIO = 0.72f
private const val LISTENING_TAIL_VISUAL_BLEND = 0.42f
private const val LISTENING_HOLD_GATE_MULTIPLIER = 2.2f
private const val LISTENING_REDRAW_MS = 16L
private const val STATE_REDRAW_MS = 48L
