package com.eipna.centsation.habit.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.eipna.centsation.habit.data.repository.TrackerRepository
import com.eipna.centsation.habit.widget.HabitWidgetSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class TrackerNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val appContext = context.applicationContext

        if (action == TrackerAlarmScheduler.ACTION_MIDNIGHT_ROLLOVER) {
            val pendingResult = goAsync()
            receiverScope.launch {
                try {
                    withTimeoutOrNull(8500L) {
                        HabitWidgetSyncManager.performSynchronizedUpdate(appContext)
                        TrackerAlarmScheduler.scheduleMidnightRollover(appContext)
                    }
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
        receiverScope.launch {
            try {
                withTimeoutOrNull(8500L) {
                    val repository = TrackerRepository.getInstance(appContext)

                    when (action) {
                        TrackerAlarmScheduler.ACTION_ALARM_TRIGGER -> {
                            val current = repository.getTrackerWithProgressById(trackerId)
                            if (current != null && current.tracker.isActive) {
                                TrackerAlarmScheduler.scheduleDailyAlarm(appContext, current.tracker)
                                TrackerNotificationHandler.showTrackerNotification(appContext, current)
                            }
                        }

                        TrackerAlarmScheduler.ACTION_SNOOZE_TRIGGER -> {
                            val current = repository.getTrackerWithProgressById(trackerId)
                            if (current != null && current.tracker.isActive) {
                                TrackerNotificationHandler.showTrackerNotification(appContext, current)
                            }
                        }

                        TrackerNotificationHandler.ACTION_DIRECT_REPLY -> {
                            val remoteInputBundle = RemoteInput.getResultsFromIntent(intent)
                            val replyText = remoteInputBundle
                                ?.getCharSequence(TrackerNotificationHandler.KEY_TEXT_REPLY)
                                ?.toString()
                                ?.trim()
                            val reps = replyText?.toIntOrNull()

                            if (reps != null && reps > 0) {
                                val updated = repository.logReps(trackerId, reps)
                                if (updated != null) {
                                    TrackerNotificationHandler.showTrackerNotification(appContext, updated)
                                    HabitWidgetSyncManager.performSynchronizedUpdate(appContext)
                                }
                            }
                        }

                        TrackerNotificationHandler.ACTION_QUICK_COMPLETE -> {
                            val current = repository.getTrackerWithProgressById(trackerId)
                            if (current != null) {
                                val repsToComplete = current.remainingToday
                                val updated = if (repsToComplete > 0) {
                                    repository.logReps(trackerId, repsToComplete)
                                } else {
                                    current
                                }
                                if (updated != null) {
                                    TrackerNotificationHandler.showTrackerNotification(appContext, updated)
                                    HabitWidgetSyncManager.performSynchronizedUpdate(appContext)
                                }
                            }
                        }

                        TrackerNotificationHandler.ACTION_SNOOZE -> {
                            TrackerNotificationHandler.dismissNotification(appContext, trackerId)
                            TrackerAlarmScheduler.scheduleSnooze(appContext, trackerId, 30)
                        }
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
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
