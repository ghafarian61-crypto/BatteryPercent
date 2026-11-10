package com.example.batterytemp

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import java.util.Locale
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

    private fun tempColor(temp: Float): Int = when {
        temp <= 27.0f -> Color.rgb(64, 156, 255)
        temp <= 30.0f -> Color.rgb(48, 209, 88)
        temp <= 35.0f -> Color.rgb(255, 149, 0)
        else -> Color.rgb(255, 59, 48)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Background is always solid pitch black (off for OLED/AMOLED)
        canvas.drawColor(Color.BLACK)

        val w = width.toFloat()
        val h = height.toFloat()

        if (w <= 0 || h <= 0) return

        // 3D Battery cylinder width
        val batteryWidth = min(w * 0.60f, 340f)
        val capHeight = 35f
        val capWidth = batteryWidth * 0.45f

        val left = (w - batteryWidth) / 2f
        val right = left + batteryWidth

        // Divide screen height into 100 steps from bottom to top
        val screenBottom = h - 20f
        val screenTop = 80f
        val totalMaxHeight = screenBottom - screenTop

        // Active height based on exact charge percentage (1% to 100%)
        val fillRatio = batteryPercent / 100f
        val currentFillHeight = (totalMaxHeight * fillRatio).coerceAtLeast(15f)
        val activeTop = screenBottom - currentFillHeight

        // 1. Draw 3D Blue Positive Terminal (+) at top ONLY if 100% charged
        if (batteryPercent >= 100) {
            val capLeft = (w - capWidth) / 2f
            val capRight = capLeft + capWidth
            val capTop = screenTop - capHeight

            val capRect = RectF(capLeft, capTop, capRight, screenTop)
            val capColor = Color.rgb(0, 150, 255) // Blue at 100%

            val capGradient = LinearGradient(
                capLeft, 0f, capRight, 0f,
                intArrayOf(
                    darkenColor(capColor, 0.4f),
                    capColor,
                    lightenColor(capColor, 1.3f),
                    darkenColor(capColor, 0.5f)
                ),
                floatArrayOf(0.0f, 0.35f, 0.7f, 1.0f),
                Shader.TileMode.CLAMP
            )
            capPaint.shader = capGradient
            canvas.drawRoundRect(capRect, 10f, 10f, capPaint)
        }

        // 2. Draw Metallic 3D Shell Casing (Dim, ~0.01 brightness) ONLY up to current percent height
        val dimShellGradient = LinearGradient(
            left, 0f, right, 0f,
            intArrayOf(
                Color.argb(12, 40, 45, 55),
                Color.argb(18, 110, 115, 130),
                Color.argb(22, 180, 185, 200),
                Color.argb(12, 50, 55, 65)
            ),
            floatArrayOf(0.0f, 0.3f, 0.65f, 1.0f),
            Shader.TileMode.CLAMP
        )
        batteryBodyPaint.shader = dimShellGradient
        batteryBodyPaint.strokeWidth = 14f
        val visibleShellRect = RectF(left, activeTop, right, screenBottom)
        canvas.drawRoundRect(visibleShellRect, 24f, 24f, batteryBodyPaint)

        // 3. Draw Multi-Color Liquid Fill (Red at bottom -> Orange -> Yellow -> Green at top)
        val innerMargin = 10f
        val fillLeft = left + innerMargin
        val fillRight = right - innerMargin
        val fillBottom = screenBottom - innerMargin
        val fillTop = activeTop + innerMargin

        if (fillBottom > fillTop) {
            // Multi-color gradient matching user drawing: Red (0-30%), Orange (30-50%), Yellow (50-75%), Green (75-100%)
            val multiColorGradient = LinearGradient(
                0f, screenBottom, 0f, screenTop,
                intArrayOf(
                    Color.rgb(255, 50, 50),   // Red (bottom 0-30%)
                    Color.rgb(255, 140, 0),  // Orange (30-50%)
                    Color.rgb(255, 220, 0),  // Yellow (50-75%)
                    Color.rgb(0, 230, 80)    // Green (75-100%)
                ),
                floatArrayOf(0.0f, 0.30f, 0.55f, 0.85f),
                Shader.TileMode.CLAMP
            )
            fillPaint.shader = multiColorGradient
            val fillRect = RectF(fillLeft, fillTop, fillRight, fillBottom)
            canvas.drawRoundRect(fillRect, 16f, 16f, fillPaint)
        }

        // 4. Draw FULL BRIGHTNESS ENGLISH NUMBERS ONLY (54 / 32.7) inside bottom area of battery
        val hoursElapsed = (SystemClock.elapsedRealtime() - startTime) / (3600 * 1000)
        val shiftStep = (hoursElapsed % 3).toInt() // 3-step burn-in protection shift
        val yOffset = shiftStep * 35f

        val baseTextY = screenBottom - 90f - yOffset

        // Strictly enforce English locale for digits
        val tempFormatted = String.format(Locale.US, "%.1f", tempCelsius)
        val percentText = String.format(Locale.US, "%d", batteryPercent)
        val slashText = " / "
        val tempText = tempFormatted

        textPaint.textSize = 50f

        val percentColor = BatteryWidgetProvider.colorFor(batteryPercent)
        textPaint.color = percentColor
        val percentWidth = textPaint.measureText(percentText)

        textPaint.color = Color.rgb(0, 150, 255) // Blue slash '/'
        val slashWidth = textPaint.measureText(slashText)

        val tempNumberColor = tempColor(tempCelsius)
        textPaint.color = tempNumberColor
        val tempWidth = textPaint.measureText(tempText)

        val totalWidth = percentWidth + slashWidth + tempWidth
        val startX = (w - totalWidth) / 2f

        textPaint.textAlign = Paint.Align.LEFT

        // Draw Percent Part (English Digits)
        textPaint.color = percentColor
        canvas.drawText(percentText, startX, baseTextY, textPaint)

        // Draw Slash '/' (Blue)
        textPaint.color = Color.rgb(0, 150, 255)
        canvas.drawText(slashText, startX + percentWidth, baseTextY, textPaint)

        // Draw Temp Part (English Digits, Temp Color)
        textPaint.color = tempNumberColor
        canvas.drawText(tempText, startX + percentWidth + slashWidth, baseTextY, textPaint)
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
