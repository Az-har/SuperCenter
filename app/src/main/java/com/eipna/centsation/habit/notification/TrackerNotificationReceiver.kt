package com.eipna.centsation.habit.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.eipna.centsation.habit.data.repository.TrackerRepository
import com.eipna.centsation.habit.widget.updateAllHabitWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TrackerNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action == TrackerAlarmScheduler.ACTION_MIDNIGHT_ROLLOVER) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    com.eipna.centsation.habit.widget.HabitWidgetSyncManager.performSynchronizedUpdate(context)
                    TrackerAlarmScheduler.scheduleMidnightRollover(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Error performing midnight rollover refresh", e)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        val trackerId = intent.getLongExtra(TrackerAlarmScheduler.EXTRA_TRACKER_ID, -1L)
        if (trackerId == -1L) {
            Log.w(TAG, "Received action $action with invalid trackerId")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = TrackerRepository.getInstance(context)

                when (action) {
                    TrackerAlarmScheduler.ACTION_ALARM_TRIGGER -> {
                        val current = repository.getTrackerWithProgressById(trackerId)
                        if (current != null && current.tracker.isActive) {
                            // Schedule tomorrow's alarm to maintain daily recurring cycle
                            TrackerAlarmScheduler.scheduleDailyAlarm(context, current.tracker)
                            // Display notification
                            TrackerNotificationHandler.showTrackerNotification(context, current)
                        }
                    }

                    TrackerAlarmScheduler.ACTION_SNOOZE_TRIGGER -> {
                        val current = repository.getTrackerWithProgressById(trackerId)
                        if (current != null && current.tracker.isActive) {
                            TrackerNotificationHandler.showTrackerNotification(context, current)
                        }
                    }

                    TrackerNotificationHandler.ACTION_DIRECT_REPLY -> {
                        val remoteInputBundle = RemoteInput.getResultsFromIntent(intent)
                        val replyText = remoteInputBundle?.getCharSequence(TrackerNotificationHandler.KEY_TEXT_REPLY)?.toString()?.trim()
                        val reps = replyText?.toIntOrNull()

                        if (reps != null && reps > 0) {
                            val updated = repository.logReps(trackerId, reps)
                            if (updated != null) {
                                TrackerNotificationHandler.showTrackerNotification(context, updated)
                                updateAllHabitWidgets(context)
                            }
                        }
                    }

                    TrackerNotificationHandler.ACTION_QUICK_COMPLETE -> {
                        val current = repository.getTrackerWithProgressById(trackerId)
                        if (current != null) {
                            val updated = repository.logReps(trackerId, current.tracker.baseDailyTarget)
                            if (updated != null) {
                                TrackerNotificationHandler.showTrackerNotification(context, updated)
                                updateAllHabitWidgets(context)
                            }
                        }
                    }

                    TrackerNotificationHandler.ACTION_SNOOZE -> {
                        TrackerNotificationHandler.dismissNotification(context, trackerId)
                        TrackerAlarmScheduler.scheduleSnooze(context, trackerId, 30)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling broadcast action $action for tracker $trackerId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "TrackerNotificationRcvr"
    }
}
