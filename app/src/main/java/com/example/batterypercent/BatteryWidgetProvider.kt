package com.example.batterypercent

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.BatteryManager
import android.text.Spanned
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.widget.RemoteViews

class BatteryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context, BatteryInfo.sticky(context), force = true)
        try {
            context.startForegroundService(Intent(context, MonitorService::class.java))
        } catch (e: Exception) {
        }
    }

    companion object {
        private val SLASH_COLOR = Color.rgb(0, 200, 255)

        // Last values pushed to the widget: unchanged battery broadcasts are skipped.
        @Volatile private var lastLevel = Int.MIN_VALUE
        @Volatile private var lastTemp = Int.MIN_VALUE

        private fun buildText(level: Int, tempTenths: Int): CharSequence {
            val sb = SpannableStringBuilder()
            sb.append(if (level >= 0) level.toString() else "--")
            sb.setSpan(
                ForegroundColorSpan(BatteryInfo.levelColor(level)),
                0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            if (tempTenths != Int.MIN_VALUE) {
                val temp = tempTenths / 10f

                val slashStart = sb.length
                sb.append("/")
                sb.setSpan(
                    ForegroundColorSpan(SLASH_COLOR),
                    slashStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                val tempStart = sb.length
                sb.append(BatteryInfo.formatTemp(temp))
                sb.setSpan(
                    ForegroundColorSpan(BatteryInfo.tempColor(temp)),
                    tempStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            return sb
        }

        fun updateAll(context: Context, battery: Intent?, force: Boolean = false) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BatteryWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val level = BatteryInfo.level(battery)
            val tempTenths =
                battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
            if (!force && level == lastLevel && tempTenths == lastTemp) return
            lastLevel = level
            lastTemp = tempTenths

            // Tapping the widget shows the current level + temperature for one second, charger or not.
            val tap = PendingIntent.getActivity(
                context,
                0,
                Intent(context, PeekActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val views = RemoteViews(context.packageName, R.layout.widget_battery)
            views.setTextViewText(R.id.widget_text, buildText(level, tempTenths))
            views.setOnClickPendingIntent(R.id.widget_root, tap)
            manager.updateAppWidget(ids, views)
        }
    }
}
