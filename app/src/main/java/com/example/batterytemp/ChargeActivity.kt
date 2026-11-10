package com.example.batterytemp

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

class ChargeActivity : Activity() {

    private lateinit var chargeView: ChargeView
    private val handler = Handler(Looper.getMainLooper())
    
    private var isPluggedIn = false
    private var autoCloseRunnable: Runnable? = null
    private var currentLevel = 0

    private lateinit var gestureDetector: GestureDetector

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

        // Setup double-tap gesture detector to close screen
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                finish()
                return true
            }
        })

        chargeView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
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
            // 100% Charged behavior: 10 seconds full brightness, then auto close
            setBrightness(1.0f)
            cancelAutoCloseRunnable()
            autoCloseRunnable = Runnable { finish() }
            handler.postDelayed(autoCloseRunnable!!, 10000)
        } else if (isPluggedIn) {
            // While plugged in: 0.01 brightness (extremely dim for OLED burn-in protection)
            setBrightness(0.01f)
        } else {
            // Opened manually from widget tap (not charging): full brightness and close after 1 second
            setBrightness(1.0f)
            cancelAutoCloseRunnable()
            autoCloseRunnable = Runnable { finish() }
            handler.postDelayed(autoCloseRunnable!!, 1000)
        }
    }

    private fun setBrightness(value: Float) {
        val lp = window.attributes
        lp.screenBrightness = value
        window.attributes = lp
    }

    private fun cancelAutoCloseRunnable() {
        autoCloseRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
        cancelAutoCloseRunnable()
    }
}
