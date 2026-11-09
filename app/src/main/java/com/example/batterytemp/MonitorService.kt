package com.example.batterytemp

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
import android.provider.Settings

/**
 * Always-on foreground service: keeps the widget fresh and shows the charging
 * screen whenever the phone is charging (on plug-in and every time the screen turns on).
 */
class MonitorService : Service() {

    companion object {
        private const val CHANNEL_ID = "battery_percent"
        private const val NOTIF_ID = 1
    }

    private val handler = Handler(Looper.getMainLooper())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_DISCONNECTED -> ChargeState.reset()
                Intent.ACTION_POWER_CONNECTED -> {
                    ChargeState.reset()
                    showChargeScreen(BatteryInfo.sticky(context))
                }
                Intent.ACTION_BATTERY_CHANGED ->
                    BatteryWidgetProvider.updateAll(context, BatteryInfo.level(intent))
                Intent.ACTION_SCREEN_OFF -> {
                    // Screen turned off while charging: wake it again with our screen
                    // so the phone's default charging display never takes over.
                    ChargeState.dismissed = false
                    handler.postDelayed({ showChargeScreen(BatteryInfo.sticky(this@MonitorService)) }, 400)
                }
                Intent.ACTION_SCREEN_ON -> {
                    ChargeState.dismissed = false
                    val b = BatteryInfo.sticky(context)
                    BatteryWidgetProvider.updateAll(context, BatteryInfo.level(b))
                    showChargeScreen(b)
                }
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
        val b = BatteryInfo.sticky(this)
        BatteryWidgetProvider.updateAll(this, BatteryInfo.level(b))
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

    private fun showChargeScreen(i: Intent?) {
        if (i == null) return
        if (!BatteryInfo.plugged(i)) {
            ChargeState.reset()
            return
        }
        val level = BatteryInfo.level(i)
        if (level >= 100) {
            ChargeState.fullReached = true
            return
        }
        if (level < 0 || ChargeState.fullReached || ChargeState.dismissed) return
        if (!Settings.canDrawOverlays(this)) return
        try {
            // singleTask: if the screen already exists it is simply brought to the front.
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
