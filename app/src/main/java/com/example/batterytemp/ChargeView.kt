package com.example.batterytemp

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.min

class ChargeView(context: Context) : View(context) {

    private var batteryPercent: Int = 0
    private var tempCelsius: Float = 0.0f

    // Burn-in protection shift calculation (changes position every 1 hour in 3 steps)
    private val startTime = SystemClock.elapsedRealtime()

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val batteryBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    fun updateData(percent: Int, temp: Float) {
        this.batteryPercent = percent.coerceIn(0, 100)
        this.tempCelsius = temp
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Background is always solid black to keep side/unused pixels off
        canvas.drawColor(Color.BLACK)

        val w = width.toFloat()
        val h = height.toFloat()

        if (w <= 0 || h <= 0) return

        // Calculate 3D battery dimensions in the center of the screen
        val batteryWidth = min(w * 0.55f, 320f)
        val capHeight = 35f
        val capWidth = batteryWidth * 0.45f
        
        val left = (w - batteryWidth) / 2f
        val right = left + batteryWidth
        val top = 80f
        val bottom = h - 60f
        val bodyHeight = bottom - top

        // 1. Draw 3D Positive Terminal (+) at top
        val capLeft = (w - capWidth) / 2f
        val capRight = capLeft + capWidth
        val capTop = top - capHeight

        val capRect = RectF(capLeft, capTop, capRight, top)
        val capColor = if (batteryPercent >= 100) Color.rgb(0, 150, 255) else Color.rgb(180, 180, 190)

        val capGradient = LinearGradient(
            capLeft, 0f, capRight, 0f,
            intArrayOf(
                darkenColor(capColor, 0.5f),
                capColor,
                lightenColor(capColor, 1.4f),
                darkenColor(capColor, 0.6f)
            ),
            floatArrayOf(0.0f, 0.35f, 0.7f, 1.0f),
            Shader.TileMode.CLAMP
        )
        capPaint.shader = capGradient
        canvas.drawRoundRect(capRect, 10f, 10f, capPaint)

        // 2. Draw 3D Metallic Shell/Casing for full battery length
        val shellGradient = LinearGradient(
            left, 0f, right, 0f,
            intArrayOf(
                Color.rgb(30, 35, 45),
                Color.rgb(90, 95, 110),
                Color.rgb(200, 205, 220),
                Color.rgb(40, 45, 55)
            ),
            floatArrayOf(0.0f, 0.3f, 0.65f, 1.0f),
            Shader.TileMode.CLAMP
        )
        batteryBodyPaint.shader = shellGradient
        batteryBodyPaint.strokeWidth = 14f
        val outerRect = RectF(left, top, right, bottom)
        canvas.drawRoundRect(outerRect, 28f, 28f, batteryBodyPaint)

        // Inner fill area
        val innerMargin = 10f
        val fillLeft = left + innerMargin
        val fillRight = right - innerMargin
        val fillBottom = bottom - innerMargin
        val fillMaxHeight = bodyHeight - (innerMargin * 2)

        // Level height inside battery
        val currentFillHeight = (fillMaxHeight * (batteryPercent / 100f)).coerceAtLeast(12f)
        val fillTop = fillBottom - currentFillHeight

        // Gradient color based on percentage (Red at bottom -> Yellow -> Green at top)
        val levelColor = calculateLevelColor(batteryPercent)

        // 3D Metallic liquid fill gradient
        val fillGradient = LinearGradient(
            fillLeft, 0f, fillRight, 0f,
            intArrayOf(
                darkenColor(levelColor, 0.5f),
                levelColor,
                lightenColor(levelColor, 1.35f),
                darkenColor(levelColor, 0.6f)
            ),
            floatArrayOf(0.0f, 0.35f, 0.75f, 1.0f),
            Shader.TileMode.CLAMP
        )
        fillPaint.shader = fillGradient
        val fillRect = RectF(fillLeft, fillTop, fillRight, fillBottom)
        canvas.drawRoundRect(fillRect, 18f, 18f, fillPaint)

        // 3. Draw Text (Percent & Temperature with decimal point) inside battery body at bottom
        val hoursElapsed = (SystemClock.elapsedRealtime() - startTime) / (3600 * 1000)
        val shiftStep = (hoursElapsed % 3).toInt() // 3-step burn-in protection shift: 0, 1, 2
        val yOffset = shiftStep * 40f

        // Position text in bottom area inside battery body
        val baseTextY = fillBottom - 110f - yOffset

        // Text 1: Percent
        textPaint.textSize = 58f
        textPaint.color = Color.WHITE
        canvas.drawText("$batteryPercent%", w / 2f, baseTextY, textPaint)

        // Text 2: Temperature with 1 decimal place (e.g. 25.5°C)
        val tempFormatted = String.format("%.1f", tempCelsius)
        val parts = tempFormatted.split(".")
        val intPart = parts.getOrNull(0) ?: "0"
        val decPart = parts.getOrNull(1) ?: "0"

        textPaint.textSize = 42f
        val tempY = baseTextY + 60f

        val mainText = "$intPart."
        val mainWidth = textPaint.measureText(mainText)
        val decWidth = textPaint.measureText("$decPart°C")
        val totalWidth = mainWidth + decWidth

        val startX = (w - totalWidth) / 2f

        // Integer part + dot color (White)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.WHITE
        canvas.drawText(mainText, startX, tempY, textPaint)

        // Decimal part color (Red if >= 41°C, else White)
        if (tempCelsius >= 41.0f) {
            textPaint.color = Color.rgb(255, 60, 60) // Bright Red
        } else {
            textPaint.color = Color.WHITE
        }
        canvas.drawText("$decPart°C", startX + mainWidth, tempY, textPaint)
    }

    private fun calculateLevelColor(pct: Int): Int {
        return when {
            pct <= 15 -> Color.rgb(240, 50, 50)   // Red
            pct <= 45 -> Color.rgb(255, 170, 0)  // Orange / Amber
            pct <= 75 -> Color.rgb(220, 220, 30) // Yellow-Green
            else -> Color.rgb(40, 210, 90)        // Bright Green
        }
    }

    private fun lightenColor(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).coerceAtMost(255f).toInt()
        val g = (Color.green(color) * factor).coerceAtMost(255f).toInt()
        val b = (Color.blue(color) * factor).coerceAtMost(255f).toInt()
        return Color.rgb(r, g, b)
    }

    private fun darkenColor(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).coerceAtLeast(0f).toInt()
        val g = (Color.green(color) * factor).coerceAtLeast(0f).toInt()
        val b = (Color.blue(color) * factor).coerceAtLeast(0f).toInt()
        return Color.rgb(r, g, b)
    }
}
