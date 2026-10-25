package de.mycountdown

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate

/** Homescreen-Widget: zeigt den nächsten anstehenden Countdown. */
class CountdownWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, build(context)) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CountdownWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun build(context: Context): RemoteViews {
            val today = LocalDate.now()
            val next = CountdownStore(context).load()
                .filter { it.daysLeft(today) >= 0 }
                .minByOrNull { it.daysLeft(today) }
            val views = RemoteViews(context.packageName, R.layout.widget_countdown)
            if (next == null) {
                views.setTextViewText(R.id.widget_emoji, "📅")
                views.setTextViewText(R.id.widget_title, "Keine Countdowns")
                views.setTextViewText(R.id.widget_days, "")
                views.setTextViewText(R.id.widget_unit, "")
            } else {
                val days = next.daysLeft(today)
                views.setTextViewText(R.id.widget_emoji, next.emoji)
                views.setTextViewText(R.id.widget_title, next.title)
                views.setTextViewText(R.id.widget_days, if (days == 0L) "Heute!" else "$days")
                views.setTextViewText(R.id.widget_unit, when (days) { 0L -> ""; 1L -> "Tag"; else -> "Tage" })
            }
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            return views
        }
    }
}
