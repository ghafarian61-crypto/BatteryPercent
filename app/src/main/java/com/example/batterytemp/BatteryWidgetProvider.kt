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
import java.util.Locale

class BatteryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context, BatteryInfo.sticky(context))
        try {
            context.startForegroundService(Intent(context, MonitorService::class.java))
        } catch (e: Exception) {
        }
    }

    companion object {
        fun colorFor(level: Int): Int = when {
            level < 0 -> Color.WHITE
            level <= 10 -> Color.rgb(255, 0, 0)     // Red
            level <= 30 -> Color.rgb(255, 128, 0)   // Orange
            level <= 60 -> Color.rgb(255, 255, 0)   // Yellow
            level < 100 -> Color.rgb(0, 255, 0)     // Green
            else -> Color.rgb(0, 150, 255)          // Blue
        }

        fun tempColor(temp: Float): Int = when {
            temp <= 27.0f -> Color.rgb(0, 190, 255)
            temp <= 30.0f -> Color.rgb(0, 255, 120)
            temp <= 35.0f -> Color.rgb(255, 175, 0)
            else -> Color.rgb(255, 0, 0)
        }

        private fun toEnglishDigits(input: String): String {
            val builder = StringBuilder()
            for (ch in input) {
                when (ch) {
                    '۰', '٠' -> builder.append('0')
                    '۱', '١' -> builder.append('1')
                    '۲', '٢' -> builder.append('2')
                    '۳', '٣' -> builder.append('3')
                    '۴', '٤' -> builder.append('4')
                    '۵', '٥' -> builder.append('5')
                    '۶', '٦' -> builder.append('6')
                    '۷', '٧' -> builder.append('7')
                    '۸', '٨' -> builder.append('8')
                    '۹', '٩' -> builder.append('9')
                    '٫', ',' -> builder.append('.')
                    else -> builder.append(ch)
                }
            }
            return builder.toString()
        }

        private fun buildText(level: Int, tempTenths: Int): CharSequence {
            val sb = SpannableStringBuilder()
            val levelRaw = if (level >= 0) String.format(Locale.US, "%d", level) else "--"
            val levelText = toEnglishDigits(levelRaw)
            
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
                    ForegroundColorSpan(Color.rgb(0, 200, 255)), // Ultra bright blue slash
                    slashStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                val start = sb.length
                val tempRaw = String.format(Locale.US, "%.1f", tempCelsius)
                val tempFormatted = toEnglishDigits(tempRaw)
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
