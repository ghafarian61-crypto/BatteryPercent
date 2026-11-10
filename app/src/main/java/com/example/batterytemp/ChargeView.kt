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
    private var isPulseBright: Boolean = false

    private val startTime = SystemClock.elapsedRealtime()

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
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

    fun updateData(percent: Int, temp: Float, pulseBright: Boolean) {
        this.batteryPercent = percent.coerceIn(0, 100)
        this.tempCelsius = temp
        this.isPulseBright = pulseBright
        invalidate()
    }

    // Unified color mapping:
    // 0 - 10: Red, 11 - 30: Orange, 31 - 60: Yellow, 61 - 99: Green, 100: Blue
    fun getColorForLevel(level: Int): Int = when {
        level <= 10 -> Color.rgb(255, 0, 0)
        level <= 30 -> Color.rgb(255, 128, 0)
        level <= 60 -> Color.rgb(255, 255, 0)
        level < 100 -> Color.rgb(0, 255, 0)
        else -> Color.rgb(0, 150, 255)
    }

    private fun tempColor(temp: Float): Int = when {
        temp <= 27.0f -> Color.rgb(0, 190, 255)
        temp <= 30.0f -> Color.rgb(0, 255, 120)
        temp <= 35.0f -> Color.rgb(255, 175, 0)
        else -> Color.rgb(255, 0, 0)
    }

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

        canvas.drawColor(Color.BLACK)

        val w = width.toFloat()
        val h = height.toFloat()

        if (w <= 0 || h <= 0) return

        val batteryWidth = min(w * 0.60f, 340f)
        val capHeight = 35f
        val capWidth = batteryWidth * 0.45f

        val left = (w - batteryWidth) / 2f
        val right = left + batteryWidth

        // Leave room at the bottom for the text below the container
        val screenBottom = h - 90f
        val screenTop = 80f
        val totalMaxHeight = screenBottom - screenTop

        val fillRatio = batteryPercent / 100f
        val currentFillHeight = (totalMaxHeight * fillRatio).coerceAtLeast(10f)
        val activeTop = screenBottom - currentFillHeight

        // 1. Top Cap (+) Terminal - Blue at 100%
        if (batteryPercent >= 100) {
            val capLeft = (w - capWidth) / 2f
            val capRight = capLeft + capWidth
            val capTop = screenTop - capHeight

            val capRect = RectF(capLeft, capTop, capRight, screenTop)
            capPaint.shader = null
            
            // Pixel-level dimming for burn-in protection: very dim alpha if not pulsing, full bright if pulsing
            val alphaFactor = if (isPulseBright) 255 else 15
            capPaint.color = Color.argb(alphaFactor, 0, 150, 255)
            canvas.drawRoundRect(capRect, 10f, 10f, capPaint)
        }

        // 2. Casing up to activeTop
        val casingAlpha = if (isPulseBright) 180 else 18
        val casingGradient = LinearGradient(
            left, activeTop, right, screenBottom,
            intArrayOf(
                Color.argb(casingAlpha, 40, 45, 55),
                Color.argb(casingAlpha + 20, 120, 125, 140),
                Color.argb(casingAlpha, 40, 45, 55)
            ),
            floatArrayOf(0.0f, 0.5f, 1.0f),
            Shader.TileMode.CLAMP
        )
        batteryBodyPaint.shader = casingGradient
        batteryBodyPaint.strokeWidth = 14f
        val activeOuterRect = RectF(left, activeTop, right, screenBottom)
        canvas.drawRoundRect(activeOuterRect, 28f, 28f, batteryBodyPaint)

        // 3. Discrete solid color blocks inside active container height
        val innerMargin = 10f
        val fillLeft = left + innerMargin
        val fillRight = right - innerMargin
        val fillBottom = screenBottom - innerMargin

        val p10Y  = screenBottom - (totalMaxHeight * 0.10f)
        val p30Y  = screenBottom - (totalMaxHeight * 0.30f)
        val p60Y  = screenBottom - (totalMaxHeight * 0.60f)
        val p100Y = screenTop + innerMargin

        val currentTop = activeTop + innerMargin

        val clipPath = Path()
        val innerContainerRect = RectF(fillLeft, currentTop, fillRight, fillBottom)
        clipPath.addRoundRect(innerContainerRect, 18f, 18f, Path.Direction.CW)

        val saveCount = canvas.save()
        canvas.clipPath(clipPath)

        // Pixel-level alpha for fill blocks: very dark/dim (alpha ~20) when idle, full vivid (alpha 255) when pulsing!
        val fillAlpha = if (isPulseBright) 255 else 20

        // Block 1: RED (0% - 10%)
        if (fillBottom > p10Y && fillBottom > currentTop) {
            fillPaint.color = Color.argb(fillAlpha, 255, 0, 0)
            fillPaint.shader = null
            val rectTop = currentTop.coerceAtLeast(p10Y)
            canvas.drawRect(fillLeft, rectTop, fillRight, fillBottom, fillPaint)
        }

        // Block 2: ORANGE (11% - 30%)
        if (p10Y > p30Y && p10Y > currentTop && batteryPercent > 10) {
            fillPaint.color = Color.argb(fillAlpha, 255, 128, 0)
            fillPaint.shader = null
            val rectTop = currentTop.coerceAtLeast(p30Y)
            canvas.drawRect(fillLeft, rectTop, fillRight, p10Y, fillPaint)
        }

        // Block 3: YELLOW (31% - 60%)
        if (p30Y > p60Y && p30Y > currentTop && batteryPercent > 30) {
            fillPaint.color = Color.argb(fillAlpha, 255, 255, 0)
            fillPaint.shader = null
            val rectTop = currentTop.coerceAtLeast(p60Y)
            canvas.drawRect(fillLeft, rectTop, fillRight, p30Y, fillPaint)
        }

        // Block 4: GREEN (61% - 99%)
        if (p60Y > p100Y && p60Y > currentTop && batteryPercent > 60) {
            fillPaint.color = Color.argb(fillAlpha, 0, 255, 0)
            fillPaint.shader = null
            val rectTop = currentTop.coerceAtLeast(p100Y)
            canvas.drawRect(fillLeft, rectTop, fillRight, p60Y, fillPaint)
        }

        // Block 5: BLUE at 100%
        if (batteryPercent >= 100 && p100Y > currentTop) {
            fillPaint.color = Color.argb(fillAlpha, 0, 150, 255)
            fillPaint.shader = null
            canvas.drawRect(fillLeft, currentTop, fillRight, p100Y, fillPaint)
        }

        canvas.restoreToCount(saveCount)

        // 4. Draw Numbers BELOW the battery container in the black area (with burn-in protection shift)
        val hoursElapsed = (SystemClock.elapsedRealtime() - startTime) / (3600 * 1000)
        val shiftStep = (hoursElapsed % 3).toInt()
        val yOffset = shiftStep * 25f

        val baseTextY = screenBottom + 55f + yOffset

        val tempRaw = String.format(Locale.US, "%.1f", tempCelsius)
        val percentRaw = String.format(Locale.US, "%d", batteryPercent)

        val percentText = toEnglishDigits(percentRaw)
        val slashText = " / "
        val tempText = toEnglishDigits(tempRaw)

        textPaint.textSize = 46f

        // Text alpha: very dim (alpha 30) when idle, full vivid (alpha 255) when pulsing!
        val textAlpha = if (isPulseBright) 255 else 30

        val basePercentColor = getColorForLevel(batteryPercent)
        val percentColor = Color.argb(textAlpha, Color.red(basePercentColor), Color.green(basePercentColor), Color.blue(basePercentColor))
        
        val slashColor = Color.argb(textAlpha, 0, 200, 255)
        
        val baseTempColor = tempColor(tempCelsius)
        val tempColorFinal = Color.argb(textAlpha, Color.red(baseTempColor), Color.green(baseTempColor), Color.blue(baseTempColor))

        textPaint.color = percentColor
        val percentWidth = textPaint.measureText(percentText)

        textPaint.color = slashColor
        val slashWidth = textPaint.measureText(slashText)

        textPaint.color = tempColorFinal
        val tempWidth = textPaint.measureText(tempText)

        val totalWidth = percentWidth + slashWidth + tempWidth
        val startX = (w - totalWidth) / 2f

        textPaint.textAlign = Paint.Align.LEFT

        // Draw Percent
        textPaint.color = percentColor
        canvas.drawText(percentText, startX, baseTextY, textPaint)

        // Draw Slash '/'
        textPaint.color = slashColor
        canvas.drawText(slashText, startX + percentWidth, baseTextY, textPaint)

        // Draw Temp
        textPaint.color = tempColorFinal
        canvas.drawText(tempText, startX + percentWidth + slashWidth, baseTextY, textPaint)
    }
}
