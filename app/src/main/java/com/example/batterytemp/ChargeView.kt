package com.example.batterytemp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import kotlin.math.min

/**
 * Black screen filled from the bottom like glossy liquid layers in a glass tube.
 * Bands from bottom to top: red 0-30, orange 30-50, yellow 50-70, green 70-100.
 * Every layer has depth shading, a curved front edge and the top layer has a shiny surface.
 * The level number (blue, no % sign) sits at the bottom centre.
 */
class ChargeView(context: Context) : View(context) {

    private class Band(val from: Int, val to: Int, val color: Int)

    private val bands = arrayOf(
        Band(0, 30, Color.rgb(235, 40, 40)),
        Band(30, 50, Color.rgb(255, 140, 20)),
        Band(50, 70, Color.rgb(255, 215, 30)),
        Band(70, 100, Color.rgb(40, 215, 80))
    )

    private val density = resources.displayMetrics.density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadePaint = Paint()
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(120, 255, 255, 255)
        strokeWidth = 1.5f * density
    }
    private val edgeShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(170, 0, 0, 0)
        strokeWidth = 6f * density
    }
    private val dimPaint = Paint().apply {
        color = Color.argb(((1f - COLOR_SCALE) * 255f).toInt(), 0, 0, 0)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = scale(NUMBER_COLOR, NUMBER_SCALE)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 22f, resources.displayMetrics
        )
    }
    private val bottomMargin = 56f * density

    private val path = Path()
    private val oval = RectF()
    private var cylinderShade: Shader? = null

    var level: Int = 0
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Horizontal shading that makes the fill look like a round glass tube.
        cylinderShade = LinearGradient(
            0f, 0f, w.toFloat(), 0f,
            intArrayOf(
                Color.argb(150, 0, 0, 0),
                Color.argb(0, 0, 0, 0),
                Color.argb(80, 255, 255, 255),
                Color.argb(0, 255, 255, 255),
                Color.argb(0, 0, 0, 0),
                Color.argb(140, 0, 0, 0)
            ),
            floatArrayOf(0f, 0.2f, 0.3f, 0.42f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        val w = width.toFloat()
        val h = height.toFloat()
        val eh = h * 0.035f // height of the perspective ellipses

        var capY = 0f
        var capColor = 0
        var drawn = false

        for (b in bands) {
            val top = min(level, b.to)
            if (top <= b.from) break
            val yTop = h * (1f - top / 100f)
            val yBot = if (b.from == 0) h else h * (1f - b.from / 100f)

            // Layer shape: flat top, curved (bulging) front edge at the bottom.
            oval.set(0f, yBot - eh / 2f, w, yBot + eh / 2f)
            path.reset()
            path.moveTo(0f, yTop)
            path.lineTo(w, yTop)
            path.lineTo(w, yBot)
            path.arcTo(oval, 0f, 180f, false)
            path.close()

            // Soft shadow this layer casts on the one below it.
            if (b.from > 0) {
                canvas.save()
                canvas.translate(0f, 4f * density)
                canvas.drawArc(oval, 0f, 180f, false, edgeShadowPaint)
                canvas.restore()
            }

            // Vertical depth: lighter at the top, darker at the bottom.
            fillPaint.shader = LinearGradient(
                0f, yTop, 0f, yBot,
                scale(b.color, 1.2f), scale(b.color, 0.6f),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(path, fillPaint)
            fillPaint.shader = null

            // Round-tube shading and highlight.
            canvas.save()
            canvas.clipPath(path)
            shadePaint.shader = cylinderShade
            canvas.drawRect(0f, yTop - eh, w, yBot + eh, shadePaint)
            shadePaint.shader = null
            canvas.restore()

            // Bright rim along the front edge.
            if (b.from > 0) canvas.drawArc(oval, 0f, 180f, false, rimPaint)

            capY = yTop
            capColor = b.color
            drawn = true
        }

        // Shiny liquid surface on the top layer.
        if (drawn) {
            oval.set(0f, capY - eh / 2f, w, capY + eh / 2f)
            capPaint.shader = RadialGradient(
                w / 2f, capY, w / 2f,
                intArrayOf(scale(capColor, 1.6f), scale(capColor, 1.15f)),
                floatArrayOf(0.15f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawOval(oval, capPaint)
            capPaint.shader = null
            canvas.drawOval(oval, rimPaint)
        }

        // Night mode: darken everything drawn so far.
        canvas.drawRect(0f, 0f, w, h, dimPaint)

        canvas.drawText(level.toString(), w / 2f, h - bottomMargin, textPaint)
    }

    private fun scale(c: Int, f: Float): Int {
        val r = (Color.red(c) * f).toInt().coerceIn(0, 255)
        val g = (Color.green(c) * f).toInt().coerceIn(0, 255)
        val b = (Color.blue(c) * f).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    companion object {
        /** How bright the colours are, 0..1. Lower = darker at night (1.0 = full colour). */
        const val COLOR_SCALE = 0.35f

        /** Blue number colour and its own brightness (0..1). */
        val NUMBER_COLOR: Int = Color.rgb(41, 121, 255)
        const val NUMBER_SCALE = 0.6f
    }
}
