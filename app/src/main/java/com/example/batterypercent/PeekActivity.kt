package com.example.batterypercent

import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * Opened by tapping the widget. Independent of the charger: shows the current level and temperature
 * at full brightness for three seconds and then closes itself (and leaves no trace in recents).
 */
class PeekActivity : FullScreenActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val closer = Runnable { finishAndRemoveTask() }
    private var timerStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setBrightness(FULL_BRIGHTNESS)
        chargeView.centerAlways = true      // same picture as while charging, but the tube stays in the middle

        val battery = BatteryInfo.sticky(this)
        chargeView.updateData(BatteryInfo.level(battery).coerceAtLeast(0), BatteryInfo.temp(battery))

        // A touch closes it early.
        chargeView.setOnTouchListener { _, _ ->
            finishAndRemoveTask()
            true
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Count the three seconds from the moment the picture is really on screen.
        if (hasFocus && !timerStarted) {
            timerStarted = true
            handler.postDelayed(closer, PEEK_MS)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
