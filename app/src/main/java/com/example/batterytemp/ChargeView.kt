package com.example.batterytemp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import kotlin.math.min

/**
 * Black screen filled from the bottom according to the battery level.
 * Fixed bands from bottom to top: red 0-30, orange 30-50, yellow 50-70, green 70-100.
 * The level number (blue, no % sign) sits at the bottom centre.
 */
class ChargeView(context: Context) : View(context) {

    private class Band(val from: Int, val to: Int, val color: Int)

    private val bands = arrayOf(
        Band(0, 30, Color.rgb(255, 0, 0)),
        Band(30, 50, Color.rgb(255, 140, 0)),
        Band(50, 70, Color.rgb(255, 224, 0)),
        Band(70, 100, Color.rgb(0, 224, 0))
    )

    private val fillPaint = Paint()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NUMBER_COLOR
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 22f, resources.displayMetrics
        )
    }
    private val bottomMargin = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 56f, resources.displayMetrics
    )

    var level: Int = 0
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        val w = width.toFloat()
        val h = height.toFloat()
        for (b in bands) {
            val top = min(level, b.to)
            if (top > b.from) {
                fillPaint.color = b.color
                canvas.drawRect(0f, h * (1f - top / 100f), w, h * (1f - b.from / 100f), fillPaint)
            }
        }
        canvas.drawText(level.toString(), w / 2f, h - bottomMargin, textPaint)
    }

    companion object {
        /** Blue number colour; change here if you want another shade. */
        val NUMBER_COLOR: Int = Color.rgb(41, 121, 255)
    }
}
