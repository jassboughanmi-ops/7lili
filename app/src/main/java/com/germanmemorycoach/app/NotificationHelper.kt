package com.germanmemorycoach.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import java.util.Calendar

object NotificationHelper {

    const val CHANNEL_ID = "gmc_study_sessions"
    private const val PREFS_NAME = "gmc_notifications_prefs"
    private const val KEY_NOTIFY_ENABLED = "notify_enabled"
    private const val KEY_TIMES_JSON = "times_json"
    private const val KEY_FOLLOW_UP = "follow_up_minutes"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Study Sessions"
            val descriptionText = "Reminders for scheduled German vocabulary sessions"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun saveScheduleAndApply(context: Context, enabled: Boolean, timesJson: String, followUpMinutes: Int) {
        getPrefs(context).edit().apply {
            putBoolean(KEY_NOTIFY_ENABLED, enabled)
            putString(KEY_TIMES_JSON, timesJson)
            putInt(KEY_FOLLOW_UP, followUpMinutes)
            apply()
        }

        if (enabled) {
            scheduleAll(context, parseTimes(timesJson), followUpMinutes)
        } else {
            cancelAll(context)
        }
    }

    fun parseTimes(timesJson: String): List<String> {
        val list = mutableListOf<String>()
        try {
            val array = JSONArray(timesJson)
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
        } catch (e: Exception) {
            list.addAll(listOf("08:00", "14:00", "20:00"))
        }
        return list
    }

    fun scheduleAll(context: Context, times: List<String>, followUpMinutes: Int) {
        cancelAll(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        times.forEachIndexed { index, timeStr ->
            val parts = timeStr.split(":")
            if (parts.size >= 2) {
                val hour = parts[0].toIntOrNull() ?: 8
                val minute = parts[1].toIntOrNull() ?: 0

                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (timeInMillis <= System.currentTimeMillis()) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }

                val intent = Intent(context, NotificationReceiver::class.java).apply {
                    action = "ACTION_SESSION_ALARM"
                    putExtra("time_label", timeStr)
                    putExtra("slot_index", index)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    1000 + index,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                    }
                } catch (e: SecurityException) {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            }
        }
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        for (index in 0..10) {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = "ACTION_SESSION_ALARM"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1000 + index,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
        cancelFollowUps(context)
    }

    fun scheduleFollowUpIfConfigured(context: Context, slotIndex: Int, timeLabel: String) {
        val prefs = getPrefs(context)
        val enabled = prefs.getBoolean(KEY_NOTIFY_ENABLED, false)
        val followUp = prefs.getInt(KEY_FOLLOW_UP, 15)
        if (enabled && followUp > 0) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val calendar = Calendar.getInstance().apply {
                add(Calendar.MINUTE, followUp)
            }
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = "ACTION_SESSION_FOLLOW_UP"
                putExtra("time_label", timeLabel)
                putExtra("slot_index", slotIndex)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                3000 + slotIndex,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } catch (e: Exception) {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        }
    }

    fun cancelFollowUps(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        for (index in 0..10) {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = "ACTION_SESSION_FOLLOW_UP"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                3000 + index,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    fun showNotification(context: Context, title: String, message: String, notificationId: Int) {
        createNotificationChannel(context)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingTap = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingTap)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }

    fun rescheduleFromPrefs(context: Context) {
        val prefs = getPrefs(context)
        val enabled = prefs.getBoolean(KEY_NOTIFY_ENABLED, false)
        if (enabled) {
            val timesJson = prefs.getString(KEY_TIMES_JSON, "[\"08:00\",\"14:00\",\"20:00\"]") ?: "[]"
            val followUp = prefs.getInt(KEY_FOLLOW_UP, 15)
            scheduleAll(context, parseTimes(timesJson), followUp)
        }
    }
}
