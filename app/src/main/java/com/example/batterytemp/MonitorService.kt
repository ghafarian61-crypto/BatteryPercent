package com.example.batterytemp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.provider.Settings

/**
 * Always-on foreground service: keeps the widget number fresh and opens the
 * charging screen when the charger is connected (or when charging while the screen turns on).
 */
class MonitorService : Service() {

    companion object {
        private const val CHANNEL_ID = "battery_percent"
        private const val NOTIF_ID = 1
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_DISCONNECTED -> ChargeState.reset()
                Intent.ACTION_POWER_CONNECTED -> {
                    ChargeState.reset()
                    handleCharge(BatteryInfo.sticky(context))
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    BatteryWidgetProvider.updateAll(context, BatteryInfo.level(intent))
                    handleCharge(intent)
                }
                Intent.ACTION_SCREEN_ON -> {
                    val b = BatteryInfo.sticky(context)
                    BatteryWidgetProvider.updateAll(context, BatteryInfo.level(b))
                    handleCharge(b)
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
        try {
            unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
        }
        super.onDestroy()
    }

    private fun handleCharge(i: Intent?) {
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
        if (level < 0 || ChargeState.fullReached || ChargeState.dismissed || ChargeState.visible) return
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
