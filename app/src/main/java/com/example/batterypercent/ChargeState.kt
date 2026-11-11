package com.example.batterypercent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import java.lang.ref.WeakReference
import java.util.Locale

/** Shared state for the current charging session (service and activity live in one process). */
object ChargeState {
    /** Battery reached 100% during this charge; do not reopen until the cable is replugged. */
    @Volatile var fullReached = false

    /** The charging screen is alive (it is "standing in" for the switched-off screen). */
    @Volatile var showing = false

    private var activity: WeakReference<Activity>? = null

    fun attach(a: Activity) {
        activity = WeakReference(a)
        showing = true
    }

    fun detach(a: Activity) {
        if (activity?.get() === a) activity = null
        showing = false
    }

    /** Closes the charging screen (main thread only). */
    fun closeScreen() {
        showing = false
        activity?.get()?.finishAndRemoveTask()
    }

    fun reset() {
        fullReached = false
    }
}

/** Battery helpers and the single colour scheme used by both the widget and the charging screen. */
object BatteryInfo {
    fun sticky(ctx: Context): Intent? =
        ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    /** Battery level 0..100, or -1 when unknown. */
    fun level(i: Intent?): Int {
        if (i == null) return -1
        val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (l < 0 || s <= 0) -1 else l * 100 / s
    }

    /** Battery temperature in degrees Celsius, or NaN when unknown. */
    fun temp(i: Intent?): Float {
        if (i == null) return Float.NaN
        val t = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        return if (t == Int.MIN_VALUE) Float.NaN else t / 10f
    }

    fun plugged(i: Intent?): Boolean =
        i != null && i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0

    fun formatTemp(temp: Float): String = String.format(Locale.US, "%.1f", temp)

    /** 0-10 red, 11-30 orange, 31-60 yellow, 61-99 green, 100 blue. */
    fun levelColor(level: Int): Int = when {
        level < 0 -> Color.WHITE
        level <= 10 -> Color.rgb(255, 0, 0)
        level <= 30 -> Color.rgb(255, 128, 0)
        level <= 60 -> Color.rgb(255, 255, 0)
        level < 100 -> Color.rgb(0, 255, 0)
        else -> Color.rgb(0, 150, 255)
    }

    fun tempColor(temp: Float): Int = when {
        temp.isNaN() -> Color.WHITE
        temp <= 27.0f -> Color.rgb(0, 190, 255)
        temp <= 30.0f -> Color.rgb(0, 255, 120)
        temp <= 35.0f -> Color.rgb(255, 175, 0)
        else -> Color.rgb(255, 0, 0)
    }
}
