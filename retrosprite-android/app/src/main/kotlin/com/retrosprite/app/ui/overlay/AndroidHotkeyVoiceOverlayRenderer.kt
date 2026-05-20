package com.retrosprite.app.ui.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.retrosprite.app.endpoint.RetroArchHotkeyEvent
import kotlin.math.sin

class AndroidHotkeyVoiceOverlayRenderer(
    context: Context,
) : HotkeyVoiceOverlayRenderer {

    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val view = HotkeyVoiceWaveView(appContext)
    private val params = WindowManager.LayoutParams(
        appContext.dp(176),
        appContext.dp(68),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = appContext.dp(16)
        y = appContext.dp(42)
    }

    private var isShown: Boolean = false

    override fun show(event: RetroArchHotkeyEvent) {
        view.bind(event)
        if (isShown) return
        runCatching {
            windowManager.addView(view, params)
            isShown = true
        }.onFailure { error ->
            Log.w(TAG, "Unable to show hotkey voice overlay", error)
        }
    }

    override fun render(state: HotkeyVoiceOverlayRenderState) {
        view.render(state)
    }

    override fun hide() {
        if (!isShown) return
        runCatching {
            windowManager.removeView(view)
        }.onFailure { error ->
            Log.w(TAG, "Unable to hide hotkey voice overlay", error)
        }
        isShown = false
    }

    private companion object {
        const val TAG = "RetroSprite/Overlay"
    }
}

private class HotkeyVoiceWaveView(context: Context) : View(context) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 12, 18, 28)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 28, 212, 255)
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val roundedRect = RectF()
    private val colors = intArrayOf(
        Color.rgb(45, 212, 191),
        Color.rgb(96, 165, 250),
        Color.rgb(167, 139, 250),
        Color.rgb(244, 114, 182),
        Color.rgb(251, 191, 36),
    )

    private var event: RetroArchHotkeyEvent? = null
    private var renderState: HotkeyVoiceOverlayRenderState? = null

    fun bind(event: RetroArchHotkeyEvent) {
        this.event = event
        invalidate()
    }

    fun render(state: HotkeyVoiceOverlayRenderState) {
        this.renderState = state
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        roundedRect.set(0f, 0f, w, h)
        canvas.drawRoundRect(roundedRect, h / 2f, h / 2f, backgroundPaint)
        canvas.drawCircle(w * 0.5f, h * 0.5f, h * 0.46f, glowPaint)

        val now = SystemClock.uptimeMillis()
        val barCount = 9
        val gap = w / 22f
        val barWidth = w / 28f
        val totalWidth = barCount * barWidth + (barCount - 1) * gap
        val startX = (w - totalWidth) / 2f
        val centerY = h / 2f
        val minHeight = h * 0.18f
        val maxHeight = h * 0.72f
        val amplitude = renderState?.amplitude?.coerceIn(0f, 1f) ?: 0f

        repeat(barCount) { index ->
            val phase = now / 170f + index * 0.82f
            val wave = ((sin(phase) + 1f) / 2f).coerceIn(0f, 1f)
            val liveLevel = (wave * 0.35f + amplitude * 0.85f).coerceIn(0f, 1f)
            val barHeight = minHeight + (maxHeight - minHeight) * liveLevel
            val left = startX + index * (barWidth + gap)
            val top = centerY - barHeight / 2f
            val right = left + barWidth
            val bottom = centerY + barHeight / 2f
            barPaint.color = colors[index % colors.size]
            canvas.drawRoundRect(left, top, right, bottom, barWidth / 2f, barWidth / 2f, barPaint)
        }

        if (isAttachedToWindow) {
            postInvalidateDelayed(48L)
        }
    }
}

private fun Context.dp(value: Int): Int =
    (value * resources.displayMetrics.density).toInt()
