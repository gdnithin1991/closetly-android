package com.smartwardrobe.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.smartwardrobe.app.domain.NotificationPreferences
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

class ClosetlyNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(EXTRA_TYPE) ?: TYPE_DAILY
        ClosetlyNotificationScheduler.showNotification(context, type)
        ClosetlyNotificationScheduler.rescheduleType(context, type)
    }
}

object ClosetlyNotificationScheduler {
    private const val CHANNEL_ID = "closetly_reminders"
    private const val DAILY_REQUEST = 7101
    private const val WEEKLY_REQUEST = 7102

    fun sync(context: Context, preferences: NotificationPreferences) {
        createChannel(context)
        if (preferences.dailyOutfit) scheduleType(context, TYPE_DAILY) else cancelType(context, TYPE_DAILY)
        if (preferences.weeklyPlan) scheduleType(context, TYPE_WEEKLY) else cancelType(context, TYPE_WEEKLY)
    }

    fun rescheduleType(context: Context, type: String) {
        scheduleType(context, type)
    }

    fun showNotification(context: Context, type: String) {
        createChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context,
            9001,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = if (type == TYPE_WEEKLY) "Plan next week's office outfits" else "Today's outfit is ready"
        val body = if (type == TYPE_WEEKLY) {
            "Open Closetly and refresh your Monday to Friday office plan."
        } else {
            "Check your weather-aware wardrobe recommendation for today."
        }
        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wardrobe_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(if (type == TYPE_WEEKLY) WEEKLY_REQUEST else DAILY_REQUEST, notification)
    }

    private fun scheduleType(context: Context, type: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val triggerAt = nextTrigger(type)
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            if (type == TYPE_WEEKLY) AlarmManager.INTERVAL_DAY * 7 else AlarmManager.INTERVAL_DAY,
            pendingIntent(context, type)
        )
    }

    private fun cancelType(context: Context, type: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, type))
    }

    private fun pendingIntent(context: Context, type: String): PendingIntent {
        val requestCode = if (type == TYPE_WEEKLY) WEEKLY_REQUEST else DAILY_REQUEST
        val intent = Intent(context, ClosetlyNotificationReceiver::class.java).putExtra(EXTRA_TYPE, type)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun nextTrigger(type: String): Long {
        val now = LocalDateTime.now()
        var target = if (type == TYPE_WEEKLY) {
            now.with(DayOfWeek.SUNDAY).withHour(19).withMinute(0).withSecond(0).withNano(0)
        } else {
            now.withHour(8).withMinute(0).withSecond(0).withNano(0)
        }
        if (!target.isAfter(now)) {
            target = if (type == TYPE_WEEKLY) target.plusWeeks(1) else target.plusDays(1)
        }
        return target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Closetly reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
    }
}

private const val EXTRA_TYPE = "type"
private const val TYPE_DAILY = "daily"
private const val TYPE_WEEKLY = "weekly"
