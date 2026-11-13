package com.example.batterypercent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent

/**
 * Charging screen. While the charger is connected it stands in for the switched-off screen
 * ([MonitorService] opens it whenever the screen turns off):
 *  - always on, at the lowest brightness the system allows;
 *  - full brightness for 10 s every time the level reaches 10, 20 ... 90;
 *  - at 100%: full brightness for 10 s, then it closes itself (and stays closed until the cable is replugged);
 *  - opened while the battery is already 100%: closes at once;
 *  - closes immediately when the charger is unplugged;
 *  - double tap (or the power button, handled by the service) closes it and shows the home screen.
 */
class ChargeActivity : FullScreenActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastLevel = -1
    private var closing = false
    private var seenPlugged = false

    private val dimRunnable = Runnable { setBrightness(DIM_BRIGHTNESS) }
    private val closeRunnable = Runnable { finishAndRemoveTask() }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            onBattery(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChargeState.attach(this)
        setBrightness(DIM_BRIGHTNESS)

        val gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                goHomeAndClose()
                return true
            }
        })
        chargeView.setOnTouchListener { _, event ->
            gestures.onTouchEvent(event)
            true
        }

        val sticky = registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (sticky != null) onBattery(sticky)
    }

    private fun onBattery(i: Intent) {
        if (!BatteryInfo.plugged(i)) {
            // Ignore a stale "unplugged" sticky value right after the plug-in event opened us.
            if (seenPlugged) finishAndRemoveTask()
            return
        }
        seenPlugged = true
        val level = BatteryInfo.level(i)
        if (level < 0) return

        val prev = lastLevel
        lastLevel = level

        if (level >= 100 && prev < 0) {
            // Already full when we were opened: nothing to show.
            ChargeState.fullReached = true
            finishAndRemoveTask()
            return
        }

        chargeView.updateData(level, BatteryInfo.temp(i))

        if (level >= 100) {
            ChargeState.fullReached = true
            if (!closing) {
                closing = true
                handler.removeCallbacks(dimRunnable)
                setBrightness(FULL_BRIGHTNESS)
                handler.postDelayed(closeRunnable, FULL_HOLD_MS)
            }
        } else if (prev >= 0 && level / 10 > prev / 10) {
            // Reached 10, 20 ... 90: 10 seconds at full brightness, then back to the dimmest.
            setBrightness(FULL_BRIGHTNESS)
            handler.removeCallbacks(dimRunnable)
            handler.postDelayed(dimRunnable, FULL_HOLD_MS)
        }
    }

    private fun goHomeAndClose() {
        ChargeState.showing = false
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
        }
        finishAndRemoveTask()
    }

    /** Home / recents gesture: the user left the charging screen on purpose. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        ChargeState.showing = false
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: IllegalArgumentException) {
        }
        ChargeState.detach(this)
        super.onDestroy()
    }
}
