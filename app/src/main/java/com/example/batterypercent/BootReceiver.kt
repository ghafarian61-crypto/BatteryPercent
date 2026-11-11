package com.example.batterypercent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the monitor service after a reboot and after the app itself has been updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                try {
                    context.startForegroundService(Intent(context, MonitorService::class.java))
                } catch (e: Exception) {
                }
            }
        }
    }
}
