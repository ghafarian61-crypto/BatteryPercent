package com.example.batterytemp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class ChargeActivity : AppCompatActivity() {

    private lateinit var chargeView: ChargeView
    private val handler = Handler(Looper.getMainLooper())
    
    private var isPluggedIn = false
    private var autoCloseRunnable: Runnable? = null
    private var dimRunnable: Runnable? = null
    private var currentLevel = 0

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { handleBatteryIntent(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make activity full screen and hide status/navigation bars
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        chargeView = ChargeView(this)
        setContentView(chargeView)

        chargeView.setOnClickListener {
            // Manual click on view: trigger 1 second max brightness pulse
            pulseMaxBrightness(1000)
        }

        // Register initial battery broadcast
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val initialStatus = registerReceiver(batteryReceiver, filter)
        initialStatus?.let { handleBatteryIntent(it) }
    }

    private fun handleBatteryIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
        val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val tempCelsius = tempTenths / 10.0f

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        isPluggedIn = plugged > 0 || status == BatteryManager.BATTERY_STATUS_CHARGING

        val levelChanged = (currentLevel != pct)
        currentLevel = pct

        chargeView.updateData(pct, tempCelsius)

        if (pct >= 100) {
            // 100% Charged behavior:
            // 10 seconds full brightness, then automatically close activity
            setBrightness(1.0f)
            cancelPendingRunnables()
            autoCloseRunnable = Runnable { finish() }
            handler.postDelayed(autoCloseRunnable!!, 10000)
        } else if (levelChanged && pct % 10 == 0 && pct > 0) {
            // Every 10% change: pulse 1 second max brightness, then dim back if charging
            pulseMaxBrightness(1000)
        } else {
            // Default brightness handling
            if (isPluggedIn) {
                // Dim screen for persistent display during charging
                setBrightness(0.15f)
            } else {
                // Not charging: full brightness with short auto-close if opened manually
                setBrightness(1.0f)
                scheduleShortAutoClose(1000)
            }
        }
    }

    private fun pulseMaxBrightness(durationMs: Long) {
        setBrightness(1.0f)
        cancelDimRunnable()
        if (isPluggedIn && currentLevel < 100) {
            dimRunnable = Runnable { setBrightness(0.15f) }
            handler.postDelayed(dimRunnable!!, durationMs)
        }
    }

    private fun scheduleShortAutoClose(durationMs: Long) {
        cancelAutoCloseRunnable()
        autoCloseRunnable = Runnable { finish() }
        handler.postDelayed(autoCloseRunnable!!, durationMs)
    }

    private fun setBrightness(value: Float) {
        val lp = window.attributes
        lp.screenBrightness = value
        window.attributes = lp
    }

    private fun cancelPendingRunnables() {
        cancelAutoCloseRunnable()
        cancelDimRunnable()
    }

    private fun cancelAutoCloseRunnable() {
        autoCloseRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun cancelDimRunnable() {
        dimRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
        cancelPendingRunnables()
    }
}
