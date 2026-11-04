package com.eipna.centsation.habit.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eipna.centsation.habit.data.entity.TrackerEntity
import com.eipna.centsation.habit.data.model.TrackerWithProgress
import com.eipna.centsation.habit.data.repository.TrackerRepository
import com.eipna.centsation.habit.notification.TrackerAlarmScheduler
import com.eipna.centsation.habit.notification.TrackerNotificationHandler
import com.eipna.centsation.habit.widget.updateAllHabitWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HabitViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TrackerRepository.getInstance(application)

    val trackers: StateFlow<List<TrackerWithProgress>> = repository.getTrackersWithProgress()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addTracker(
        title: String,
        baseDailyTarget: Int,
        unit: String,
        hour: Int,
        minute: Int
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val tracker = TrackerEntity(
                title = title.trim(),
                baseDailyTarget = baseDailyTarget.coerceAtLeast(1),
                unit = unit.trim().ifEmpty { "reps" },
                scheduledHour = hour,
                scheduledMinute = minute,
                syncCalendar = false,
                calendarId = null,
                calendarEventId = null
            )
            val newId = repository.insertTracker(tracker)
            val createdTracker = tracker.copy(id = newId)

            TrackerAlarmScheduler.scheduleDailyAlarm(getApplication(), createdTracker)
            updateAllHabitWidgets(getApplication())
        }
    }

    fun updateTracker(
        tracker: TrackerEntity,
        oldHour: Int,
        oldMinute: Int
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateTracker(tracker)

            if (oldHour != tracker.scheduledHour || oldMinute != tracker.scheduledMinute || !tracker.isActive) {
                if (tracker.isActive) {
                    TrackerAlarmScheduler.scheduleDailyAlarm(getApplication(), tracker)
                } else {
                    TrackerAlarmScheduler.cancelAlarm(getApplication(), tracker.id)
                }
            }

            updateAllHabitWidgets(getApplication())
        }
    }

    fun deleteTracker(item: TrackerWithProgress) {
        viewModelScope.launch(Dispatchers.IO) {
            TrackerAlarmScheduler.cancelAlarm(getApplication(), item.tracker.id)
            TrackerNotificationHandler.dismissNotification(getApplication(), item.tracker.id)
            repository.deleteTracker(item.tracker)
            updateAllHabitWidgets(getApplication())
        }
    }

    fun logReps(trackerId: Long, additional: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = repository.logReps(trackerId, additional)
            if (updated != null) {
                updateAllHabitWidgets(getApplication())
            }
        }
    }

    fun setReps(trackerId: Long, totalCount: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = repository.setReps(trackerId, totalCount)
            if (updated != null) {
                updateAllHabitWidgets(getApplication())
            }
        }
    }
}
