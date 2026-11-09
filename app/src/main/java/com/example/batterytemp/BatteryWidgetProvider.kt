package com.example.batterytemp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews

class BatteryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context, BatteryInfo.level(BatteryInfo.sticky(context)))
        try {
            context.startForegroundService(Intent(context, MonitorService::class.java))
        } catch (e: Exception) {
        }
    }

    companion object {
        /** Number colour follows the level: red up to 30, orange to 50, yellow to 70, green above. */
        fun colorFor(level: Int): Int = when {
            level < 0 -> Color.WHITE
            level <= 30 -> Color.rgb(255, 59, 48)
            level <= 50 -> Color.rgb(255, 149, 0)
            level <= 70 -> Color.rgb(255, 214, 10)
            else -> Color.rgb(48, 209, 88)
        }

        fun updateAll(context: Context, level: Int) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BatteryWidgetProvider::class.java))
            if (ids.isEmpty()) return

            // Tapping the widget shows the level full screen for 1 second.
            val tap = PendingIntent.getActivity(
                context,
                0,
                Intent(context, ChargeActivity::class.java)
                    .putExtra(ChargeActivity.EXTRA_PREVIEW, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val text = if (level >= 0) level.toString() else "--"
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_battery)
                views.setTextViewText(R.id.temp_value, text)
                views.setTextColor(R.id.temp_value, colorFor(level))
                views.setOnClickPendingIntent(R.id.widget_root, tap)
                manager.updateAppWidget(id, views)
            }
        }
    }
}
