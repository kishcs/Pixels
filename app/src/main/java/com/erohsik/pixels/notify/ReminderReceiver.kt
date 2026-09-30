package com.erohsik.pixels.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.erohsik.pixels.MainActivity
import com.erohsik.pixels.R
import com.erohsik.pixels.data.homeTrackerBlocking
import com.erohsik.pixels.di.ServiceLocator
import com.erohsik.pixels.domain.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the daily alarm, plus boot and clock/zone changes (which reset or shift alarms).
 * The notification is neutral ("How was today?") and is skipped when today is already logged.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ReminderScheduler.ACTION_REMINDER -> {
                val pending = goAsync()
                ServiceLocator.appScope.launch(Dispatchers.IO) {
                    try {
                        maybeNotify(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                ServiceLocator.refreshToday()
                ReminderScheduler.reschedule(context, ServiceLocator.settings)
            }
        }
    }

    // The permission is checked explicitly just above the notify() call.
    @SuppressLint("MissingPermission")
    private fun maybeNotify(context: Context) {
        val settings = ServiceLocator.settings
        if (!settings.currentReminderEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val tracker = ServiceLocator.trackerDao.homeTrackerBlocking(settings) ?: return
        if (ServiceLocator.entryDao.getForDayBlocking(tracker.id, DateUtils.today()) != null) return

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pixels)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.reminder_body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val NOTIFICATION_ID = 1
    }
}
