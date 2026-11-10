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
        // High contrast shadow/glow for maximum visibility on OLED
        setShadowLayer(6f, 0f, 0f, Color.BLACK)
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

    // Pure absolute temperature colors (high visibility)
    private fun tempColor(temp: Float): Int = when {
        temp <= 27.0f -> Color.rgb(0, 190, 255)  // Bright Pure Blue
        temp <= 30.0f -> Color.rgb(0, 255, 120)  // Bright Pure Green
        temp <= 35.0f -> Color.rgb(255, 175, 0)  // Bright Pure Orange
        else -> Color.rgb(255, 0, 0)             // Bright Pure Red
    }

    // Force strictly English ASCII digits and '.' separator
    private fun toEnglishDigits(input: String): String {
        val builder = StringBuilder()
        for (ch in input) {
            when (ch) {
                '۰', '٠' -> builder.append('0')
                '۱', '١' -> builder.append('1')
                '۲', '٢' -> builder.append('2')
                '۳', '٣' -> builder.append('3')
                '۴', '٤' -> builder.append('4')
                '۵', '٥' -> builder.append('5')
                '۶', '٦' -> builder.append('6')
                '۷', '٧' -> builder.append('7')
                '۸', '٨' -> builder.append('8')
                '۹', '٩' -> builder.append('9')
                '٫', ',' -> builder.append('.')
                else -> builder.append(ch)
            }
        }
        return builder.toString()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Background is solid black to turn off AMOLED/OLED pixels completely above charge level
        canvas.drawColor(Color.BLACK)

        val w = width.toFloat()
        val h = height.toFloat()

        if (w <= 0 || h <= 0) return

        val batteryWidth = min(w * 0.60f, 340f)
        val capHeight = 35f
        val capWidth = batteryWidth * 0.45f

        val left = (w - batteryWidth) / 2f
        val right = left + batteryWidth

        val screenBottom = h - 20f
        val screenTop = 80f
        val totalMaxHeight = screenBottom - screenTop

        // Active top based on exact battery percentage
        val fillRatio = batteryPercent / 100f
        val currentFillHeight = (totalMaxHeight * fillRatio).coerceAtLeast(10f)
        val activeTop = screenBottom - currentFillHeight

        // 1. Top Cap (+) Terminal - Pure Blue ONLY at 100%
        if (batteryPercent >= 100) {
            val capLeft = (w - capWidth) / 2f
            val capRight = capLeft + capWidth
            val capTop = screenTop - capHeight

            val capRect = RectF(capLeft, capTop, capRight, screenTop)
            capPaint.shader = null
            capPaint.color = Color.rgb(0, 150, 255)
            canvas.drawRoundRect(capRect, 10f, 10f, capPaint)
        }

        // 2. OUTER CASING ONLY UP TO activeTop (so anything above charge level is completely black/off!)
        val casingGradient = LinearGradient(
            left, activeTop, right, screenBottom,
            intArrayOf(
                Color.argb(45, 60, 65, 75),
                Color.argb(85, 140, 145, 160),
                Color.argb(45, 60, 65, 75)
            ),
            floatArrayOf(0.0f, 0.5f, 1.0f),
            Shader.TileMode.CLAMP
        )
        batteryBodyPaint.shader = casingGradient
        batteryBodyPaint.strokeWidth = 14f
        val activeOuterRect = RectF(left, activeTop, right, screenBottom)
        canvas.drawRoundRect(activeOuterRect, 28f, 28f, batteryBodyPaint)

        // 3. DISCRETE PURE SOLID COLOR BLOCKS inside active container height
        val innerMargin = 10f
        val fillLeft = left + innerMargin
        val fillRight = right - innerMargin
        val fillBottom = screenBottom - innerMargin

        val p30Y = screenBottom - (totalMaxHeight * 0.30f)
        val p50Y = screenBottom - (totalMaxHeight * 0.50f)
        val p75Y = screenBottom - (totalMaxHeight * 0.75f)
        val p100Y = screenTop + innerMargin

        val currentTop = activeTop + innerMargin

        // Clip canvas to the curved inner battery container up to activeTop
        val clipPath = Path()
        val innerContainerRect = RectF(fillLeft, currentTop, fillRight, fillBottom)
        clipPath.addRoundRect(innerContainerRect, 18f, 18f, Path.Direction.CW)

        val saveCount = canvas.save()
        canvas.clipPath(clipPath)

        // Block 1: PURE RED (0% - 30%)
        if (fillBottom > p30Y && fillBottom > currentTop) {
            fillPaint.color = Color.rgb(255, 0, 0) // Pure Red
            fillPaint.shader = null
            val rectTop = currentTop.coerceAtLeast(p30Y)
            canvas.drawRect(fillLeft, rectTop, fillRight, fillBottom, fillPaint)
        }

        // Block 2: PURE ORANGE (30% - 50%)
        if (p30Y > p50Y && p30Y > currentTop && batteryPercent > 30) {
            fillPaint.color = Color.rgb(255, 128, 0) // Pure Orange
            fillPaint.shader = null
            val rectTop = currentTop.coerceAtLeast(p50Y)
            canvas.drawRect(fillLeft, rectTop, fillRight, p30Y, fillPaint)
        }

        // Block 3: PURE YELLOW (50% - 75%)
        if (p50Y > p75Y && p50Y > currentTop && batteryPercent > 50) {
            fillPaint.color = Color.rgb(255, 255, 0) // Pure Yellow
            fillPaint.shader = null
            val rectTop = currentTop.coerceAtLeast(p75Y)
            canvas.drawRect(fillLeft, rectTop, fillRight, p50Y, fillPaint)
        }

        // Block 4: PURE GREEN (75% - 100%)
        if (p75Y > p100Y && p75Y > currentTop && batteryPercent > 75) {
            fillPaint.color = Color.rgb(0, 255, 0) // Pure Green
            fillPaint.shader = null
            val rectTop = currentTop.coerceAtLeast(p100Y)
            canvas.drawRect(fillLeft, rectTop, fillRight, p75Y, fillPaint)
        }

        canvas.restoreToCount(saveCount)

        // 4. Draw MAXIMUM BRIGHTNESS English Numbers (e.g. 54 / 32.2) with high visibility
        val hoursElapsed = (SystemClock.elapsedRealtime() - startTime) / (3600 * 1000)
        val shiftStep = (hoursElapsed % 3).toInt()
        val yOffset = shiftStep * 35f

        val baseTextY = screenBottom - 90f - yOffset

        val tempRaw = String.format(Locale.US, "%.1f", tempCelsius)
        val percentRaw = String.format(Locale.US, "%d", batteryPercent)

        val percentText = toEnglishDigits(percentRaw)
        val slashText = " / "
        val tempText = toEnglishDigits(tempRaw)

        textPaint.textSize = 50f

        val percentColor = BatteryWidgetProvider.colorFor(batteryPercent)
        textPaint.color = percentColor
        val percentWidth = textPaint.measureText(percentText)

        textPaint.color = Color.rgb(0, 190, 255) // Bright Pure Blue slash '/'
        val slashWidth = textPaint.measureText(slashText)

        val tempNumberColor = tempColor(tempCelsius)
        textPaint.color = tempNumberColor
        val tempWidth = textPaint.measureText(tempText)

        val totalWidth = percentWidth + slashWidth + tempWidth
        val startX = (w - totalWidth) / 2f

        textPaint.textAlign = Paint.Align.LEFT

        // Draw Percent
        textPaint.color = percentColor
        canvas.drawText(percentText, startX, baseTextY, textPaint)

        // Draw Slash '/'
        textPaint.color = Color.rgb(0, 190, 255)
        canvas.drawText(slashText, startX + percentWidth, baseTextY, textPaint)

        // Draw Temp (English Digits)
        textPaint.color = tempNumberColor
        canvas.drawText(tempText, startX + percentWidth + slashWidth, baseTextY, textPaint)
    }
}
