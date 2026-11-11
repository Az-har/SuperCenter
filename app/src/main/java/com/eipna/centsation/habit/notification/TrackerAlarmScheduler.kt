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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

object TrackerAlarmScheduler {

    private const val TAG = "TrackerAlarmScheduler"
    const val EXTRA_TRACKER_ID = "extra_tracker_id"
    const val ACTION_ALARM_TRIGGER = "com.eipna.centsation.habit.ACTION_ALARM_TRIGGER"
    const val ACTION_SNOOZE_TRIGGER = "com.eipna.centsation.habit.ACTION_SNOOZE_TRIGGER"
    const val ACTION_MIDNIGHT_ROLLOVER = "com.eipna.centsation.habit.ACTION_MIDNIGHT_ROLLOVER"
    private const val MIDNIGHT_ROLLOVER_REQ_CODE = 0x07000001
    private val schedulerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun scopedRequestCode(slot: Int, trackerId: Long): Int {
        return (slot shl 24) or ((trackerId and 0x00FFFFFFL).toInt())
    }

    fun alarmRequestCode(trackerId: Long): Int = scopedRequestCode(1, trackerId)
    fun snoozeRequestCode(trackerId: Long): Int = scopedRequestCode(2, trackerId)
    fun contentRequestCode(trackerId: Long): Int = scopedRequestCode(3, trackerId)
    fun directReplyRequestCode(trackerId: Long): Int = scopedRequestCode(4, trackerId)
    fun quickCompleteRequestCode(trackerId: Long): Int = scopedRequestCode(5, trackerId)
    fun snoozeActionRequestCode(trackerId: Long): Int = scopedRequestCode(6, trackerId)
    fun notificationId(trackerId: Long): Int = ((trackerId and 0x7FFFFFFFL).toInt())

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
            alarmRequestCode(tracker.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleAlarmCompat(alarmManager, calendar.timeInMillis, pendingIntent, isPowerSave)
        Log.d(TAG, "Scheduled alarm for tracker ${tracker.title} at ${calendar.time} (powerSave=$isPowerSave)")
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
            snoozeRequestCode(trackerId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleAlarmCompat(alarmManager, triggerAt, pendingIntent, isPowerSave)
    }

    private fun scheduleAlarmCompat(
        alarmManager: AlarmManager,
        triggerAt: Long,
        pendingIntent: PendingIntent,
        isPowerSave: Boolean
    ) {
        try {
            if (isPowerSave) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "Exact alarm denied, falling back to inexact alarm", se)
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancelAlarm(context: Context, trackerId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val alarmIntent = Intent(context, TrackerNotificationReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
            putExtra(EXTRA_TRACKER_ID, trackerId)
        }
        cancelPendingBroadcast(context, alarmManager, alarmRequestCode(trackerId), alarmIntent)
        cancelPendingBroadcast(context, alarmManager, trackerId.toInt(), alarmIntent)

        val snoozeIntent = Intent(context, TrackerNotificationReceiver::class.java).apply {
            action = ACTION_SNOOZE_TRIGGER
            putExtra(EXTRA_TRACKER_ID, trackerId)
        }
        cancelPendingBroadcast(context, alarmManager, snoozeRequestCode(trackerId), snoozeIntent)
        cancelPendingBroadcast(context, alarmManager, (trackerId + 100000L).toInt(), snoozeIntent)
    }

    private fun cancelPendingBroadcast(
        context: Context,
        alarmManager: AlarmManager,
        requestCode: Int,
        intent: Intent
    ) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun scheduleMidnightRollover(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val midnightCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 5)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }

        val intent = Intent(context, TrackerNotificationReceiver::class.java).apply {
            action = ACTION_MIDNIGHT_ROLLOVER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            MIDNIGHT_ROLLOVER_REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC,
                midnightCal.timeInMillis,
                pendingIntent
            )
        } catch (se: SecurityException) {
            Log.w(TAG, "Unable to set idle-wakeup midnight rollover; falling back to standard alarm", se)
            alarmManager.set(AlarmManager.RTC, midnightCal.timeInMillis, pendingIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed scheduling midnight rollover", e)
        }
    }

    suspend fun rescheduleAllSuspending(context: Context) {
        val appContext = context.applicationContext
        scheduleMidnightRollover(appContext)
        try {
            val repository = TrackerRepository.getInstance(appContext)
            val trackers = repository.getTrackersWithProgress().first()
            for (item in trackers) {
                if (item.tracker.isActive) {
                    scheduleDailyAlarm(appContext, item.tracker)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error rescheduling all tracker alarms", e)
        }
    }

    fun rescheduleAllAsync(context: Context) {
        val appContext = context.applicationContext
        schedulerScope.launch {
            rescheduleAllSuspending(appContext)
        }
    }

    fun rescheduleAll(context: Context) {
        rescheduleAllAsync(context)
    }
}
