package com.example.batterypercent

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Black screen with a glass battery "cylinder" that fills from the bottom, and "percent / temp" under it.
 *
 * Look: a thin transparent glass tube with a blue (level-100 colour) shell and an elliptical rim on top.
 * The coloured liquid is seen through the glass and touches the shell everywhere. The top edge of the
 * liquid is animated: it sloshes left and right with a small ripple.
 *
 * Rules (unchanged from the flat version):
 *  - the level is drawn from the bottom, the colour bands are at fixed percent edges (0-10-30-60-100),
 *  - only the bands up to the current level are shown, the blue cap appears at 100 % only,
 *  - burn-in protection: every time the level crosses a multiple of 10 the picture moves:
 *      * the cylinder alternates between the right and the left half of its slot,
 *      * the numbers alternate between a lower and an upper slot that do not overlap at all.
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

    // Gradients are built once on a 0..1 horizontal unit and moved/scaled with a matrix at draw time.
    private val unitMatrix = Matrix()
    private val shellShader = LinearGradient(0f, 0f, 1f, 0f, SHELL_COLORS, SHELL_POS, Shader.TileMode.CLAMP)
    private val glassShader = LinearGradient(0f, 0f, 1f, 0f, GLASS_COLORS, GLASS_POS, Shader.TileMode.CLAMP)
    private val capShader = LinearGradient(0f, 0f, 1f, 0f, CAP_COLORS, CAP_POS, Shader.TileMode.CLAMP)

    private val shellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        shader = shellShader
    }
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = glassShader
    }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = capShader
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val crestPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
        color = CREST_COLOR
    }

    private val shellPath = Path()
    private val crestPath = Path()
    private val airPath = Path()
    private val capPath = Path()
    private val ovalRect = RectF()
    private val capRect = RectF()
    private val streakRect = RectF()

    private val startMs = SystemClock.uptimeMillis()

    /** One shared timer: redraws ~30 fps while the view is on screen so the liquid keeps moving. */
    private val ticker = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, FRAME_MS)
        }
    }

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

    private fun startTicker() {
        removeCallbacks(ticker)
        if (WAVE_ENABLED) post(ticker)
    }

    private fun stopTicker() {
        removeCallbacks(ticker)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (windowVisibility == VISIBLE) startTicker()
    }

    override fun onDetachedFromWindow() {
        stopTicker()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) startTicker() else stopTicker()
    }

    /** Puts the unit gradient [shader] on the horizontal span [x0, x0 + span]. */
    private fun placeShader(shader: Shader, x0: Float, span: Float) {
        unitMatrix.setScale(span, 1f)
        unitMatrix.postTranslate(x0, 0f)
        shader.setLocalMatrix(unitMatrix)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val t = (SystemClock.uptimeMillis() - startMs) / 1000f

        // Changes every time the level crosses a multiple of 10.
        val rightSide = (percent / 10) % 2 == 0

        // ---- vertical layout: [cap][cylinder][upper text slot][lower text slot] ----
        val inkH = inkBottom - inkTop
        val lowBase = h - dp(48f)                       // baseline of the lower slot
        val highBase = lowBase - (inkH + dp(14f))       // upper slot sits above it, 14dp of clear black between
        val bottom = highBase + inkTop - dp(28f)        // lowest point of the cylinder
        val top = dp(30f)                               // highest point of the cylinder rim at 100 % (cap is above)

        // ---- horizontal layout: the tube sits in the right or the left half of its slot ----
        val fullW = min(w * 0.60f, dp(130f))
        val halfW = fullW / 2f
        val slotLeft = (w - fullW) / 2f + if (rightSide) halfW else 0f
        val tubeW = min(halfW, dp(TUBE_W_DP))
        val cx = slotLeft + halfW / 2f
        val x0 = cx - tubeW / 2f
        val x1 = x0 + tubeW
        val rx = tubeW / 2f
        val ry = rx * ELLIPSE_RATIO

        // ---- level geometry (same bands/edges as before, measured on the liquid column) ----
        val head = ry + dp(HEAD_EXTRA_DP)               // empty glass above the liquid: clears the rim's front arc + waves
        val yBot = bottom - ry                          // centre of the bottom ellipse
        val yFull = top + ry + head                     // liquid level at 100 %
        val total = yBot - yFull
        val fillH = max(total * percent / 100f, dp(4f))
        val surfaceY = yBot - fillH                     // mean liquid surface
        val rimY = surfaceY - head                      // centre of the rim ellipse (top of the glass)

        // Outline of the glass: left side, rounded bottom, right side (open at the top).
        shellPath.reset()
        shellPath.moveTo(x0, rimY)
        shellPath.lineTo(x0, yBot)
        ovalRect.set(x0, yBot - ry, x1, yBot + ry)
        shellPath.arcTo(ovalRect, 180f, -180f, false)
        shellPath.lineTo(x1, rimY)

        // ---- liquid, seen through the glass ----
        val save = canvas.save()
        canvas.clipPath(shellPath)

        // Wave: the whole surface tilts left/right (slosh) plus a small travelling ripple.
        val amp = min(1f, fillH / dp(14f))
        val tilt = dp(TILT_DP) * amp * sin(TAU * t / TILT_PERIOD_S)
        val ripple = dp(RIPPLE_DP) * amp
        val ph1 = TAU * t / RIPPLE_PERIOD1_S
        val ph2 = -TAU * t / RIPPLE_PERIOD2_S
        val k = TAU / tubeW
        crestPath.reset()
        for (i in 0..WAVE_STEPS) {
            val x = x0 - 1f + (tubeW + 2f) * i / WAVE_STEPS
            val dx = x - cx
            val y = surfaceY + tilt * (dx / rx) +
                ripple * (0.65f * sin(k * dx + ph1) + 0.35f * sin(2.3f * k * dx + ph2))
            if (i == 0) crestPath.moveTo(x, y) else crestPath.lineTo(x, y)
        }

        // Colour bands: only the bands up to the current level; the band that contains the level
        // runs up to the top of the wave, so the highest coloured part is the one that waves.
        val bandTop = surfaceY - dp(TILT_DP + RIPPLE_DP) - dp(1f)
        val topBand = when {
            percent <= 10 -> 0
            percent <= 30 -> 1
            percent <= 60 -> 2
            else -> 3
        }
        for (b in 0..topBand) {
            val yLow = if (b == 0) bottom + dp(2f) else yBot - total * EDGES[b] / 100f
            val yHigh = if (b == topBand) bandTop else yBot - total * EDGES[b + 1] / 100f
            if (yLow > yHigh) {
                fillPaint.color = BAND_COLORS[b]
                canvas.drawRect(x0 - 1f, yHigh, x1 + 1f, yLow, fillPaint)
            }
        }

        // Glass shading across the liquid (dark edges, bright reflection on the left).
        placeShader(glassShader, x0, tubeW)
        canvas.drawRect(x0 - 1f, bandTop, x1 + 1f, bottom + dp(2f), glassPaint)

        // Empty glass above the wave: paint it black again, then a thin highlight on the wave itself.
        airPath.set(crestPath)
        airPath.lineTo(x1 + 1f, bandTop - dp(2f))
        airPath.lineTo(x0 - 1f, bandTop - dp(2f))
        airPath.close()
        fillPaint.color = Color.BLACK
        canvas.drawPath(airPath, fillPaint)
        canvas.drawPath(crestPath, crestPaint)

        canvas.restoreToCount(save)

        // ---- glass shell: blue outline and the elliptical rim ----
        placeShader(shellShader, x0, tubeW)
        shellPaint.strokeWidth = dp(3f)
        canvas.drawPath(shellPath, shellPaint)
        ovalRect.set(x0, rimY - ry, x1, rimY + ry)
        shellPaint.strokeWidth = dp(3.5f)
        canvas.drawOval(ovalRect, shellPaint)

        // ---- glass reflections ----
        val sy0 = max(rimY + ry + dp(8f), surfaceY + dp(6f))   // reflections only over the liquid
        val sy1 = bottom - dp(10f)
        if (sy1 - sy0 > dp(30f)) {
            fillPaint.color = STREAK_STRONG
            streakRect.set(x0 + rx * 0.39f, sy0, x0 + rx * 0.39f + dp(4f), sy1)
            canvas.drawRoundRect(streakRect, dp(2f), dp(2f), fillPaint)
            fillPaint.color = STREAK_SOFT
            streakRect.set(x1 - rx * 0.52f, sy0 + dp(10f), x1 - rx * 0.52f + dp(2.5f), sy1 - dp(20f))
            canvas.drawRoundRect(streakRect, dp(1.2f), dp(1.2f), fillPaint)
        }

        // ---- cap (+) terminal, blue at 100% ----
        if (percent >= 100) {
            val capW = tubeW * 0.48f
            val capL = cx - capW / 2f
            val capRy = dp(3.5f)
            val capTopY = rimY - dp(13f)
            placeShader(capShader, capL, capW)
            capPath.reset()
            capPath.moveTo(capL, capTopY)
            capPath.lineTo(capL, rimY)
            capRect.set(capL, rimY - capRy, capL + capW, rimY + capRy)
            capPath.arcTo(capRect, 180f, -180f, false)
            capPath.lineTo(capL + capW, capTopY)
            capPath.close()
            canvas.drawPath(capPath, capPaint)
            capRect.set(capL, capTopY - capRy, capL + capW, capTopY + capRy)
            fillPaint.color = CAP_TOP_COLOR
            canvas.drawOval(capRect, fillPaint)
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

    private companion object {
        const val TEXT_SIZE_DP = 35f          // = 2x the old 46px on a 420dpi phone
        const val SLASH = " / "
        const val GLYPHS = "0123456789/.-"

        // ---- look ----
        const val TUBE_W_DP = 46f             // thin tube (never wider than half of the slot)
        const val ELLIPSE_RATIO = 0.35f       // rim ellipse height / its half width
        const val HEAD_EXTRA_DP = 8f          // extra empty glass above the liquid (on top of the rim height)

        // ---- wave animation (set WAVE_ENABLED = false for a still picture) ----
        const val WAVE_ENABLED = true
        const val FRAME_MS = 33L              // ~30 fps
        const val WAVE_STEPS = 14
        const val TILT_DP = 3.5f              // left/right slosh amplitude
        const val TILT_PERIOD_S = 3.2f
        const val RIPPLE_DP = 1.5f            // small travelling ripple
        const val RIPPLE_PERIOD1_S = 1.7f
        const val RIPPLE_PERIOD2_S = 2.6f
        const val TAU = 6.2831855f

        val EDGES = intArrayOf(0, 10, 30, 60, 100)
        val BAND_COLORS = intArrayOf(
            BatteryInfo.levelColor(10),
            BatteryInfo.levelColor(30),
            BatteryInfo.levelColor(60),
            BatteryInfo.levelColor(99)
        )
        val BLUE = BatteryInfo.levelColor(100)
        val SLASH_COLOR = Color.rgb(0, 200, 255)

        /** Darker/lighter shade of the level-100 blue. */
        private fun shade(f: Float) = Color.rgb(
            (Color.red(BLUE) * f).toInt(),
            (Color.green(BLUE) * f).toInt(),
            (Color.blue(BLUE) * f).toInt()
        )

        // Shell: all shades of the level-100 blue.
        val SHELL_COLORS = intArrayOf(shade(0.50f), BLUE, shade(0.68f), shade(0.58f))
        val SHELL_POS = floatArrayOf(0f, 0.25f, 0.55f, 1f)

        // Glass shading drawn over the liquid: dark edges, bright reflection near the left edge.
        val GLASS_COLORS = intArrayOf(
            Color.argb(128, 0, 0, 0),
            Color.argb(20, 0, 0, 0),
            Color.argb(77, 255, 255, 255),
            Color.argb(5, 255, 255, 255),
            Color.argb(26, 0, 0, 0),
            Color.argb(140, 0, 0, 0)
        )
        val GLASS_POS = floatArrayOf(0f, 0.14f, 0.24f, 0.40f, 0.78f, 1f)

        // Cap terminal.
        val CAP_COLORS = intArrayOf(shade(0.45f), Color.rgb(110, 210, 255), BLUE, shade(0.55f))
        val CAP_POS = floatArrayOf(0f, 0.25f, 0.55f, 1f)
        val CAP_TOP_COLOR = Color.rgb(120, 215, 255)

        val STREAK_STRONG = Color.argb(128, 255, 255, 255)
        val STREAK_SOFT = Color.argb(46, 255, 255, 255)
        val CREST_COLOR = Color.argb(90, 255, 255, 255)
    }
}
