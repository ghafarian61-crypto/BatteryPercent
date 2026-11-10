package com.example.batterytemp

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager

/**
 * Full-screen charge display.
 *  - Charging mode (opened by MonitorService): stays until touched, unplugged or 100%.
 *  - Preview mode (opened by tapping the widget): shows the current level for 1 second.
 */
class ChargeActivity : Activity() {

    companion object {
        const val EXTRA_PREVIEW = "preview"
        private const val PREVIEW_MS = 1000L
        private const val DOUBLE_TAP_MS = 500L
        private const val FULL_GREEN_MS = 1000L
        private const val FLASH_MS = 1000L

        /**
         * Panel brightness stays at maximum so the blue number is really bright.
         * Everything else is made dark by dimming its colours (see ChargeView.COLOR_SCALE).
         */
        private const val SCREEN_BRIGHTNESS = 1f
    }

    private lateinit var chargeView: ChargeView
    private val handler = Handler(Looper.getMainLooper())
    private var preview = false
    private var lastTap = 0L
    private var showingFull = false
    private var prevLevel = -1

    private val closeRunnable = Runnable { closeAutomatically() }

    /** Ends the 10% flash: colours go back to the dark night look. */
    private val endFlashRunnable = Runnable {
        if (!showingFull) chargeView.bright = false
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onBattery(intent)
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preview = intent.getBooleanExtra(EXTRA_PREVIEW, false)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )
        val lp = window.attributes
        lp.screenBrightness = SCREEN_BRIGHTNESS
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.attributes = lp

        chargeView = ChargeView(this)
        setContentView(chargeView)
        // Double tap to close (like the phone's own screens); a single tap does nothing.
        chargeView.setOnClickListener {
            val now = SystemClock.uptimeMillis()
            if (now - lastTap < DOUBLE_TAP_MS) dismissByUser() else lastTap = now
        }
        hideSystemBars()

        val sticky = registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (preview) handler.postDelayed(closeRunnable, PREVIEW_MS)
        onBattery(sticky)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val newPreview = intent.getBooleanExtra(EXTRA_PREVIEW, false)
        if (preview && newPreview) {
            // Widget tapped again while previewing: restart the 1 second timer.
            handler.removeCallbacks(closeRunnable)
            handler.postDelayed(closeRunnable, PREVIEW_MS)
        } else if (preview && !newPreview) {
            // Charging started while previewing: switch to charging mode.
            preview = false
            handler.removeCallbacks(closeRunnable)
            onBattery(BatteryInfo.sticky(this))
        }
        // Charging screen already open: nothing to do.
    }

    override fun onResume() {
        super.onResume()
        if (!preview) ChargeState.foreground = true
    }

    override fun onPause() {
        ChargeState.foreground = false
        super.onPause()
    }

    /** Home / recents pressed: the user wants to leave, so do not pop up again until the screen is toggled. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!preview) ChargeState.dismissed = true
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        dismissByUser()
    }

    private fun dismissByUser() {
        if (!preview) ChargeState.dismissed = true
        finishAndRemoveTask()
    }

    private fun onBattery(i: Intent?) {
        if (isFinishing || i == null) return
        val level = BatteryInfo.level(i)
        chargeView.temp = BatteryInfo.temp(i)
        if (level >= 0) chargeView.level = level
        if (!preview && !showingFull && level in 0..99 && prevLevel >= 0 &&
            level / 10 > prevLevel / 10
        ) {
            flashBright() // crossed 10, 20, 30 ... %
        }
        if (level >= 0) prevLevel = level
        if (!preview) {
            if (level >= 100) {
                ChargeState.fullReached = true
                showFullGreenThenClose()
            } else if (!BatteryInfo.plugged(i) && !showingFull) {
                closeAutomatically()
            }
        }
    }

    /** Every 10%: one second with full-strength colours, then back to night mode. */
    private fun flashBright() {
        chargeView.bright = true
        handler.removeCallbacks(endFlashRunnable)
        handler.postDelayed(endFlashRunnable, FLASH_MS)
    }

    /** Battery is full: whole screen green at maximum brightness for 1 second, then close. */
    private fun showFullGreenThenClose() {
        if (showingFull) return
        showingFull = true
        handler.removeCallbacks(endFlashRunnable)
        chargeView.fullGreen = true
        handler.removeCallbacks(closeRunnable)
        handler.postDelayed(closeRunnable, FULL_GREEN_MS)
    }

    private fun closeAutomatically() {
        if (isFinishing) return
        finishAndRemoveTask()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: IllegalArgumentException) {
        }
        super.onDestroy()
    }
}
