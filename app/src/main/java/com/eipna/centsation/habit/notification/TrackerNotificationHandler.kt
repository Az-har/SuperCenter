package com.eipna.centsation.habit.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.eipna.centsation.R
import com.eipna.centsation.habit.data.model.TrackerWithProgress
import com.eipna.centsation.habit.ui.HabitTrackerActivity

object TrackerNotificationHandler {

    private const val TAG = "TrackerNotificationHdlr"

    const val CHANNEL_ID = "channel_habit_tracker_v1"
    const val CHANNEL_NAME = "Daily Quota & Habit Tracker"
    const val KEY_TEXT_REPLY = "key_habit_text_reply"

    const val ACTION_DIRECT_REPLY = "com.eipna.centsation.habit.ACTION_DIRECT_REPLY"
    const val ACTION_QUICK_COMPLETE = "com.eipna.centsation.habit.ACTION_QUICK_COMPLETE"
    const val ACTION_SNOOZE = "com.eipna.centsation.habit.ACTION_SNOOZE"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily quota reminders, habit debt rollover alerts, and quick actions"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showTrackerNotification(context: Context, item: TrackerWithProgress) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        createNotificationChannel(context)

        val tracker = item.tracker
        val remaining = item.remainingToday
        val unit = tracker.unit

        val title = if (remaining > 0) {
            "${tracker.title}: $remaining $unit remaining!"
        } else {
            "✅ ${tracker.title}: Daily quota completed!"
        }

        val contentText = if (item.accumulatedBacklogDebt > 0) {
            "Base target: ${tracker.baseDailyTarget} | Accumulated Backlog: +${item.accumulatedBacklogDebt} $unit"
        } else {
            "Target: ${tracker.baseDailyTarget} $unit | Total logged today: ${item.todayCompletedReps} $unit"
        }

        // Tap opens HabitTrackerActivity
        val contentIntent = Intent(context, HabitTrackerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(TrackerAlarmScheduler.EXTRA_TRACKER_ID, tracker.id)
        }
        val pendingContentIntent = PendingIntent.getActivity(
            context,
            TrackerAlarmScheduler.contentRequestCode(tracker.id),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_centsation)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingContentIntent)

        if (remaining > 0) {
            // 1. Direct Reply Action
            val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel("Enter reps completed (e.g. 5)")
                .build()

            val replyIntent = Intent(context, TrackerNotificationReceiver::class.java).apply {
                action = ACTION_DIRECT_REPLY
                putExtra(TrackerAlarmScheduler.EXTRA_TRACKER_ID, tracker.id)
            }
            val replyPendingIntent = PendingIntent.getBroadcast(
                context,
                TrackerAlarmScheduler.directReplyRequestCode(tracker.id),
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            val replyAction = NotificationCompat.Action.Builder(
                R.drawable.ic_save,
                "Log Count",
                replyPendingIntent
            ).addRemoteInput(remoteInput).build()

            // 2. Quick Complete Action (+Remaining)
            val quickCompleteIntent = Intent(context, TrackerNotificationReceiver::class.java).apply {
                action = ACTION_QUICK_COMPLETE
                putExtra(TrackerAlarmScheduler.EXTRA_TRACKER_ID, tracker.id)
            }
            val quickCompletePendingIntent = PendingIntent.getBroadcast(
                context,
                TrackerAlarmScheduler.quickCompleteRequestCode(tracker.id),
                quickCompleteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val quickCompleteAction = NotificationCompat.Action.Builder(
                R.drawable.ic_save,
                "+$remaining Done",
                quickCompletePendingIntent
            ).build()

            // 3. Snooze Action
            val snoozeIntent = Intent(context, TrackerNotificationReceiver::class.java).apply {
                action = ACTION_SNOOZE
                putExtra(TrackerAlarmScheduler.EXTRA_TRACKER_ID, tracker.id)
            }
            val snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                TrackerAlarmScheduler.snoozeActionRequestCode(tracker.id),
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val snoozeAction = NotificationCompat.Action.Builder(
                R.drawable.ic_history,
                "Snooze 30m",
                snoozePendingIntent
            ).build()

            builder.addAction(replyAction)
            builder.addAction(quickCompleteAction)
            builder.addAction(snoozeAction)
        }

        try {
            notificationManager.notify(TrackerAlarmScheduler.notificationId(tracker.id), builder.build())
        } catch (se: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS denied while posting tracker notification", se)
        }
    }

    fun dismissNotification(context: Context, trackerId: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        notificationManager.cancel(TrackerAlarmScheduler.notificationId(trackerId))
    }
}
