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
        fun tempColor(temp: Float): Int = when {
            temp <= 27.0f -> Color.rgb(64, 156, 255)
            temp <= 30.0f -> Color.rgb(48, 209, 88)
            temp <= 35.0f -> Color.rgb(255, 149, 0)
            else -> Color.rgb(255, 59, 48)
        }

        /** Builds "55/33.8" with percent, blue slash, and decimal temperature with its color. */
        private fun buildText(level: Int, tempTenths: Int): CharSequence {
            val sb = SpannableStringBuilder()
            val levelText = if (level >= 0) level.toString() else "--"
            sb.append(levelText)
            sb.setSpan(
                ForegroundColorSpan(colorFor(level)),
                0, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            if (tempTenths != Int.MIN_VALUE) {
                val tempCelsius = tempTenths / 10.0f
                sb.append("/")
                val slashStart = sb.length - 1
                sb.setSpan(
                    ForegroundColorSpan(Color.rgb(0, 150, 255)), // Blue slash
                    slashStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                val start = sb.length
                val tempFormatted = String.format("%.1f", tempCelsius)
                sb.append(tempFormatted)
                sb.setSpan(
                    ForegroundColorSpan(tempColor(tempCelsius)),
                    start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            return sb
        }

        fun updateAll(context: Context, battery: Intent?) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BatteryWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val level = BatteryInfo.level(battery)
            val tempTenths = battery?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE

            val text = buildText(level, tempTenths)

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
