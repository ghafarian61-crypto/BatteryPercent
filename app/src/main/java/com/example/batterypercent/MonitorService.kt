package com.example.batterypercent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings

/**
 * Always-on foreground service.
 *
 * While the charger is connected the charging screen replaces the switched-off screen:
 *  - the charger is plugged in while the screen is off  -> open it;
 *  - the screen turns off while charging                -> open it (shortly after);
 *  - the screen turns off while it is in front          -> the user pressed the power button:
 *                                                          wake the phone to the home screen instead.
 * It is never opened while the screen is on (the user is using the phone).
 * It also keeps the widget fresh.
 */
class MonitorService : Service() {

    companion object {
        private const val CHANNEL_ID = "battery_percent"
        private const val NOTIF_ID = 1
        private const val OPEN_DELAY_MS = 400L
        private const val WAKE_DELAY_MS = 200L
        private const val WAKE_HOLD_MS = 3000L
    }

    private val handler = Handler(Looper.getMainLooper())

    private val openRunnable = Runnable {
        showChargeScreen(BatteryInfo.sticky(this@MonitorService), assumePlugged = false)
    }

    private val wakeRunnable = Runnable {
        wakeScreen()
        goHome()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    ChargeState.reset()
                    // The sticky battery intent may still say "unplugged" for a moment, so trust the event.
                    showChargeScreen(BatteryInfo.sticky(context), assumePlugged = true)
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    handler.removeCallbacks(openRunnable)
                    handler.removeCallbacks(wakeRunnable)
                    ChargeState.reset()
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    // Nobody sees the widget while the screen is off; ACTION_SCREEN_ON refreshes it.
                    if (isInteractive()) BatteryWidgetProvider.updateAll(context, intent)
                }
                Intent.ACTION_SCREEN_OFF -> onScreenOff(context)
                Intent.ACTION_SCREEN_ON -> BatteryWidgetProvider.updateAll(context, BatteryInfo.sticky(context))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(receiver, filter)
        BatteryWidgetProvider.updateAll(this, BatteryInfo.sticky(this), force = true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
        }
        super.onDestroy()
    }

    private fun isInteractive(): Boolean = getSystemService(PowerManager::class.java).isInteractive

    private fun onScreenOff(context: Context) {
        handler.removeCallbacks(openRunnable)
        handler.removeCallbacks(wakeRunnable)
        if (!BatteryInfo.plugged(BatteryInfo.sticky(context))) {
            ChargeState.reset()
            return
        }
        if (ChargeState.showing) {
            // The charging screen was in front, so this is the power button: act like "wake the phone".
            ChargeState.closeScreen()
            handler.postDelayed(wakeRunnable, WAKE_DELAY_MS)
        } else {
            // The screen turned off by timeout / power button while using the phone: put our screen in its place.
            handler.postDelayed(openRunnable, OPEN_DELAY_MS)
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        try {
            val pm = getSystemService(PowerManager::class.java)
            pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "BatteryPercent:wake"
            ).acquire(WAKE_HOLD_MS)
        } catch (e: Exception) {
        }
    }

    private fun goHome() {
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
        }
    }

    private fun showChargeScreen(i: Intent?, assumePlugged: Boolean) {
        if (i == null) return
        if (isInteractive()) return                      // screen is on: the user is using the phone
        if (!assumePlugged && !BatteryInfo.plugged(i)) {
            ChargeState.reset()
            return
        }
        val level = BatteryInfo.level(i)
        if (level >= 100) {
            ChargeState.fullReached = true
            return
        }
        if (level < 0 || ChargeState.fullReached || ChargeState.showing) return
        if (!Settings.canDrawOverlays(this)) return
        try {
            startActivity(
                Intent(this, ChargeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_MIN
            )
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(getString(R.string.notif_title))
            .setOngoing(true)
            .build()
    }
}
