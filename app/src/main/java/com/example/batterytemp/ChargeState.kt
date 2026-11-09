package com.example.batterytemp

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/** Shared state for the current charging session (service and activity live in one process). */
object ChargeState {
    /** The charging screen is currently in front of the user. */
    @Volatile var foreground = false

    /** The user closed the screen (touch / back / home). Cleared when the screen turns off or on, or on replug. */
    @Volatile var dismissed = false

    /** Battery reached 100% during this charge; do not reopen until the cable is replugged. */
    @Volatile var fullReached = false

    fun reset() {
        dismissed = false
        fullReached = false
    }
}

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

    fun plugged(i: Intent?): Boolean =
        i != null && i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
}
