package com.example.batterypercent

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Black screen with a battery "cylinder" that fills from the bottom, and "percent / temp" under it.
 *
 * Burn-in protection (AMOLED): every time the level crosses a multiple of 10 the picture moves:
 *  - the cylinder is half of its full width and alternates between the right and the left half,
 *  - the numbers alternate between a lower and an upper slot that do not overlap at all,
 * so no pixel stays lit for the whole charge.
 *
 * Nothing is allocated inside onDraw.
 */
class ChargeView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private var percent = 0
    private var percentText = "0"
    private var tempText = "--"
    private var tempTextColor = Color.WHITE
    private var percentWidth = 0f
    private var tempWidth = 0f

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = dp(TEXT_SIZE_DP)
    }
    private val slashWidth = textPaint.measureText(SLASH)

    /** Ink bounds of the widest possible text line relative to its baseline (top is negative). */
    private val inkTop: Float
    private val inkBottom: Float

    private val casingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(5f)
        color = Color.rgb(110, 115, 130)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val casingRect = RectF()
    private val innerRect = RectF()
    private val capRect = RectF()
    private val clipPath = Path()

    init {
        val r = Rect()
        textPaint.getTextBounds(GLYPHS, 0, GLYPHS.length, r)
        inkTop = r.top.toFloat()
        inkBottom = r.bottom.toFloat()
    }

    fun updateData(percent: Int, temp: Float) {
        this.percent = percent.coerceIn(0, 100)
        percentText = this.percent.toString()
        percentWidth = textPaint.measureText(percentText)
        tempText = if (temp.isNaN()) "--" else BatteryInfo.formatTemp(temp)
        tempWidth = textPaint.measureText(tempText)
        tempTextColor = BatteryInfo.tempColor(temp)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Changes every time the level crosses a multiple of 10.
        val rightSide = (percent / 10) % 2 == 0

        // ---- vertical layout: [cap][cylinder][upper text slot][lower text slot] ----
        val inkH = inkBottom - inkTop
        val lowBase = h - dp(48f)                       // baseline of the lower slot
        val highBase = lowBase - (inkH + dp(14f))       // upper slot sits above it, 14dp of clear black between
        val bottom = highBase + inkTop - dp(28f)        // cylinder bottom
        val top = dp(30f)                               // cylinder top (cap is drawn above it)
        val capH = dp(13f)
        val totalH = bottom - top

        // ---- horizontal layout: half-width cylinder on the right or the left half ----
        val fullW = min(w * 0.60f, dp(130f))
        val halfW = fullW / 2f
        val left = (w - fullW) / 2f + if (rightSide) halfW else 0f
        val right = left + halfW

        val fillH = max(totalH * percent / 100f, dp(4f))
        val activeTop = bottom - fillH

        // Cap (+) terminal, blue at 100%
        if (percent >= 100) {
            val capW = halfW * 0.45f
            val capL = left + (halfW - capW) / 2f
            capRect.set(capL, top - capH, capL + capW, top)
            fillPaint.color = BLUE
            canvas.drawRoundRect(capRect, dp(4f), dp(4f), fillPaint)
        }

        // Casing
        casingRect.set(left, activeTop, right, bottom)
        canvas.drawRoundRect(casingRect, dp(11f), dp(11f), casingPaint)

        // Colour blocks inside the casing
        val m = dp(4f)
        innerRect.set(left + m, activeTop + m, right - m, bottom - m)
        if (innerRect.height() > 0f) {
            clipPath.reset()
            clipPath.addRoundRect(innerRect, dp(7f), dp(7f), Path.Direction.CW)
            val save = canvas.save()
            canvas.clipPath(clipPath)
            drawBands(canvas, bottom, totalH)
            canvas.restoreToCount(save)
        }

        // Numbers: "percent / temp", centered, alternating between the two slots
        val baseline = if (rightSide) lowBase else highBase
        val x = (w - (percentWidth + slashWidth + tempWidth)) / 2f

        textPaint.color = BatteryInfo.levelColor(percent)
        canvas.drawText(percentText, x, baseline, textPaint)
        textPaint.color = SLASH_COLOR
        canvas.drawText(SLASH, x + percentWidth, baseline, textPaint)
        textPaint.color = tempTextColor
        canvas.drawText(tempText, x + percentWidth + slashWidth, baseline, textPaint)
    }

    private fun drawBands(canvas: Canvas, bottom: Float, totalH: Float) {
        for (k in 0 until 4) {
            val yLow = bottom - totalH * EDGES[k] / 100f
            val yHigh = bottom - totalH * EDGES[k + 1] / 100f
            val a = max(yHigh, innerRect.top)
            val b = min(yLow, innerRect.bottom)
            if (b > a) {
                fillPaint.color = BAND_COLORS[k]
                canvas.drawRect(innerRect.left, a, innerRect.right, b, fillPaint)
            }
        }
    }

    private companion object {
        const val TEXT_SIZE_DP = 35f          // = 2x the old 46px on a 420dpi phone
        const val SLASH = " / "
        const val GLYPHS = "0123456789/.-"
        val EDGES = intArrayOf(0, 10, 30, 60, 100)
        val BAND_COLORS = intArrayOf(
            BatteryInfo.levelColor(10),
            BatteryInfo.levelColor(30),
            BatteryInfo.levelColor(60),
            BatteryInfo.levelColor(99)
        )
        val BLUE = BatteryInfo.levelColor(100)
        val SLASH_COLOR = Color.rgb(0, 200, 255)
    }
}
