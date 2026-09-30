package com.erohsik.pixels.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.erohsik.pixels.R
import com.erohsik.pixels.data.SettingsStore
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * The one daily reminder. Inexact repeating is enough for "sometime around 9pm", and it avoids
 * SCHEDULE_EXACT_ALARM entirely. Rescheduled on boot and on time/zone changes.
 */
object ReminderScheduler {
    const val CHANNEL_ID = "daily_reminder"
    const val ACTION_REMINDER = "com.erohsik.pixels.action.REMINDER"
    private const val REQUEST_CODE = 1

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.reminder_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun schedule(context: Context, minuteOfDay: Int) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            nextTriggerMillis(minuteOfDay, ZonedDateTime.now()),
            AlarmManager.INTERVAL_DAY,
            pendingIntent(context),
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    /** Re-applies the stored setting; used after boot and clock/zone changes. */
    fun reschedule(context: Context, settings: SettingsStore) {
        if (settings.currentReminderEnabled()) {
            schedule(context, settings.currentReminderMinuteOfDay())
        } else {
            cancel(context)
        }
    }

    /** The next wall-clock occurrence of [minuteOfDay] strictly after [now], in [now]'s zone. */
    fun nextTriggerMillis(minuteOfDay: Int, now: ZonedDateTime): Long {
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        var candidate = ZonedDateTime.of(LocalDate.from(now), time, now.zone)
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
        return candidate.toInstant().toEpochMilli()
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMINDER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
