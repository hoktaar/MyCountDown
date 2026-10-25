package de.mycountdown

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "countdown_reminders"
private const val REMIND_HOUR = 9

/** Tage vor dem Termin, an denen erinnert wird (0 = am Tag selbst). */
private val remindDays = setOf(7L, 1L, 0L)

/** Läuft täglich gegen 9 Uhr, benachrichtigt und aktualisiert die Widgets. */
class ReminderWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        CountdownWidget.updateAll(ctx)
        if (!canNotify(ctx)) return Result.success()
        createChannel(ctx)
        CountdownStore(ctx).load().forEach { c ->
            val days = c.daysLeft()
            if (days in remindDays) notify(c, days)
        }
        return Result.success()
    }

    private fun notify(c: Countdown, days: Long) {
        val text = when (days) {
            0L -> "Heute ist es soweit!"
            1L -> "Noch 1 Tag"
            else -> "Noch $days Tage"
        }
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("${c.emoji} ${c.title}")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(c.id.hashCode(), n) }
    }
}

fun canNotify(ctx: Context): Boolean =
    android.os.Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

fun createChannel(ctx: Context) {
    val channel = NotificationChannel(CHANNEL_ID, "Erinnerungen", NotificationManager.IMPORTANCE_DEFAULT)
        .apply { description = "Erinnerungen an bevorstehende Countdowns" }
    ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

fun scheduleDailyReminder(ctx: Context) {
    val now = LocalDateTime.now()
    var first = now.toLocalDate().atTime(LocalTime.of(REMIND_HOUR, 0))
    if (!first.isAfter(now)) first = first.plusDays(1)
    val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
        .setInitialDelay(Duration.between(now, first).toMinutes(), TimeUnit.MINUTES)
        .build()
    WorkManager.getInstance(ctx)
        .enqueueUniquePeriodicWork("daily_reminder", ExistingPeriodicWorkPolicy.KEEP, request)
}
