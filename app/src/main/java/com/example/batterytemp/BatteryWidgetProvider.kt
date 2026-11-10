package com.example.batterytemp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.widget.RemoteViews

class BatteryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context, BatteryInfo.sticky(context))
        try {
            context.startForegroundService(Intent(context, MonitorService::class.java))
        } catch (e: Exception) {
        }
    }

    companion object {
        /** Percent colour: red up to 30, orange to 50, yellow to 70, green above. */
        fun colorFor(level: Int): Int = when {
            level < 0 -> Color.WHITE
            level <= 30 -> Color.rgb(255, 59, 48)
            level <= 50 -> Color.rgb(255, 149, 0)
            level <= 70 -> Color.rgb(255, 214, 10)
            else -> Color.rgb(48, 209, 88)
        }

        /** Temperature colour: up to 27 blue, 28-30 green, 31-35 orange, 36 and above red. */
        fun tempColor(temp: Int): Int = when {
            temp <= 27 -> Color.rgb(64, 156, 255)
            temp <= 30 -> Color.rgb(48, 209, 88)
            temp <= 35 -> Color.rgb(255, 149, 0)
            else -> Color.rgb(255, 59, 48)
        }

        /** Builds "85/36" with the percent and the temperature each in their own colour. */
        private fun buildText(level: Int, temp: Int): CharSequence {
            val sb = SpannableStringBuilder()
            val levelText = if (level >= 0) level.toString() else "--"
            sb.append(levelText)
            sb.setSpan(
                ForegroundColorSpan(colorFor(level)),
                0, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (temp != BatteryInfo.NO_TEMP) {
                sb.append("/")
                val start = sb.length
                sb.append(temp.toString())
                sb.setSpan(
                    ForegroundColorSpan(tempColor(temp)),
                    start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            return sb
        }

        fun updateAll(context: Context, battery: Intent?) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BatteryWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val text = buildText(BatteryInfo.level(battery), BatteryInfo.temp(battery))

            // Tapping the widget shows the level full screen for 1 second.
            val tap = PendingIntent.getActivity(
                context,
                0,
                Intent(context, ChargeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_battery)
                views.setTextViewText(R.id.temp_value, text)
                views.setOnClickPendingIntent(R.id.widget_root, tap)
                manager.updateAppWidget(id, views)
            }
        }
    }
}
