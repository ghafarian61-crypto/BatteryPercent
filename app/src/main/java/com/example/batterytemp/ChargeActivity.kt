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

        /** 0 = the lowest brightness the phone allows. */
        private const val DIM_BRIGHTNESS = 0f
    }

    private lateinit var chargeView: ChargeView
    private val handler = Handler(Looper.getMainLooper())
    private var preview = false

    private val closeRunnable = Runnable { closeAutomatically() }

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
        lp.screenBrightness = DIM_BRIGHTNESS
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.attributes = lp

        chargeView = ChargeView(this)
        setContentView(chargeView)
        chargeView.setOnClickListener { dismissByUser() }
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
        if (level >= 0) chargeView.level = level
        if (!preview) {
            if (level >= 100) {
                ChargeState.fullReached = true
                closeAutomatically()
            } else if (!BatteryInfo.plugged(i)) {
                closeAutomatically()
            }
        }
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
