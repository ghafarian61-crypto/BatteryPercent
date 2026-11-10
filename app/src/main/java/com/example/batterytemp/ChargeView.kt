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

        // Background is always solid black to keep uncharged top/side pixels off
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
        val totalBodyHeight = bottom - top

        // 1. Draw 3D Positive Terminal (+) at top ONLY if 100% charged
        if (batteryPercent >= 100) {
            val capLeft = (w - capWidth) / 2f
            val capRight = capLeft + capWidth
            val capTop = top - capHeight

            val capRect = RectF(capLeft, capTop, capRight, top)
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

        // Calculate filled height based on percentage
        // Above batteryPercent height, everything remains black/off!
        val fillRatio = batteryPercent / 100f
        val currentLevelHeight = (totalBodyHeight * fillRatio).coerceAtLeast(10f)
        val activeTop = bottom - currentLevelHeight

        // 2. Draw 3D Metallic Shell/Casing ONLY up to current charge height (dim 3D effect)
        val dimShellGradient = LinearGradient(
            left, 0f, right, 0f,
            intArrayOf(
                Color.argb(70, 40, 45, 55),
                Color.argb(80, 110, 115, 130),
                Color.argb(90, 180, 185, 200),
                Color.argb(70, 50, 55, 65)
            ),
            floatArrayOf(0.0f, 0.3f, 0.65f, 1.0f),
            Shader.TileMode.CLAMP
        )
        batteryBodyPaint.shader = dimShellGradient
        batteryBodyPaint.strokeWidth = 14f
        
        val visibleShellRect = RectF(left, activeTop, right, bottom)
        canvas.drawRoundRect(visibleShellRect, 28f, 28f, batteryBodyPaint)

        // 3. Draw Inner Liquid Fill ONLY up to current charge height
        val innerMargin = 10f
        val fillLeft = left + innerMargin
        val fillRight = right - innerMargin
        val fillBottom = bottom - innerMargin
        val fillTop = activeTop + innerMargin

        if (fillBottom > fillTop) {
            val levelColor = calculateLevelColor(batteryPercent)

            // Dim 3D Metallic fill gradient (low brightness casing fill)
            val fillGradient = LinearGradient(
                fillLeft, 0f, fillRight, 0f,
                intArrayOf(
                    darkenColor(levelColor, 0.35f),
                    levelColor,
                    lightenColor(levelColor, 1.2f),
                    darkenColor(levelColor, 0.45f)
                ),
                floatArrayOf(0.0f, 0.35f, 0.75f, 1.0f),
                Shader.TileMode.CLAMP
            )
            fillPaint.shader = fillGradient
            val fillRect = RectF(fillLeft, fillTop, fillRight, fillBottom)
            canvas.drawRoundRect(fillRect, 18f, 18f, fillPaint)
        }

        // 4. Draw FULL BRIGHTNESS Text (Percent / Temp) inside bottom area of battery
        val hoursElapsed = (SystemClock.elapsedRealtime() - startTime) / (3600 * 1000)
        val shiftStep = (hoursElapsed % 3).toInt() // 3-step burn-in protection shift: 0, 1, 2
        val yOffset = shiftStep * 40f

        val baseTextY = bottom - 110f - yOffset
        val levelColor = calculateLevelColor(batteryPercent)

        // Format: "56 / 31.9°C"
        val tempFormatted = String.format("%.1f", tempCelsius)
        val percentText = "$batteryPercent"
        val slashText = " / "
        val tempText = "$tempFormatted°C"

        textPaint.textSize = 52f

        // Measure widths for centering
        textPaint.color = levelColor
        val percentWidth = textPaint.measureText(percentText)

        textPaint.color = Color.rgb(0, 150, 255) // Blue slash '/'
        val slashWidth = textPaint.measureText(slashText)

        val isHighTemp = tempCelsius >= 41.0f
        val tempColor = if (isHighTemp) Color.rgb(255, 60, 60) else Color.WHITE
        textPaint.color = tempColor
        val tempWidth = textPaint.measureText(tempText)

        val totalWidth = percentWidth + slashWidth + tempWidth
        val startX = (w - totalWidth) / 2f

        textPaint.textAlign = Paint.Align.LEFT

        // Draw Percent Part (Colored by Battery Level)
        textPaint.color = levelColor
        canvas.drawText(percentText, startX, baseTextY, textPaint)

        // Draw Slash '/' (Blue)
        textPaint.color = Color.rgb(0, 150, 255)
        canvas.drawText(slashText, startX + percentWidth, baseTextY, textPaint)

        // Draw Temp Part (White or Red if >= 41°C)
        textPaint.color = tempColor
        canvas.drawText(tempText, startX + percentWidth + slashWidth, baseTextY, textPaint)
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
