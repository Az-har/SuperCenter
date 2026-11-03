package com.eipna.centsation.habit.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.eipna.centsation.habit.data.entity.TrackerEntity
import com.eipna.centsation.habit.data.repository.TrackerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

object TrackerAlarmScheduler {

    private const val TAG = "TrackerAlarmScheduler"
    const val EXTRA_TRACKER_ID = "extra_tracker_id"
    const val ACTION_ALARM_TRIGGER = "com.eipna.centsation.habit.ACTION_ALARM_TRIGGER"
    const val ACTION_SNOOZE_TRIGGER = "com.eipna.centsation.habit.ACTION_SNOOZE_TRIGGER"

    fun scheduleDailyAlarm(context: Context, tracker: TrackerEntity) {
        if (!tracker.isActive) {
            cancelAlarm(context, tracker.id)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = powerManager?.isPowerSaveMode == true

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, tracker.scheduledHour)
            set(Calendar.MINUTE, tracker.scheduledMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If scheduled time has already passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, TrackerNotificationReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
            putExtra(EXTRA_TRACKER_ID, tracker.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            tracker.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (isPowerSave) {
                // In battery saver mode, allow the system to batch alarms and save CPU wakeups
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
            Log.d(TAG, "Scheduled alarm for tracker ${tracker.title} at ${calendar.time} (powerSave=$isPowerSave)")
        } catch (se: SecurityException) {
            Log.w(TAG, "Cannot set exact alarm without permission, falling back to windowed alarm", se)
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    fun scheduleSnooze(context: Context, trackerId: Long, minutes: Int = 30) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = powerManager?.isPowerSaveMode == true
        val triggerAt = System.currentTimeMillis() + (minutes * 60 * 1000L)

        val intent = Intent(context, TrackerNotificationReceiver::class.java).apply {
            action = ACTION_SNOOZE_TRIGGER
            putExtra(EXTRA_TRACKER_ID, trackerId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (trackerId + 100000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (isPowerSave) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (se: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancelAlarm(context: Context, trackerId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, TrackerNotificationReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
            putExtra(EXTRA_TRACKER_ID, trackerId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            trackerId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }

    fun rescheduleAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = TrackerRepository.getInstance(context)
                val trackers = repository.getTrackersWithProgress().first()
                for (item in trackers) {
                    if (item.tracker.isActive) {
                        scheduleDailyAlarm(context, item.tracker)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling all tracker alarms", e)
            }
        }
    }
}
