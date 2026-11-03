package com.eipna.centsation.habit.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eipna.centsation.habit.calendar.CalendarEventItem
import com.eipna.centsation.habit.calendar.CalendarSyncManager
import com.eipna.centsation.habit.calendar.UserCalendar
import com.eipna.centsation.habit.data.entity.TrackerEntity
import com.eipna.centsation.habit.data.model.TrackerWithProgress
import com.eipna.centsation.habit.data.repository.TrackerRepository
import com.eipna.centsation.habit.notification.TrackerAlarmScheduler
import com.eipna.centsation.habit.notification.TrackerNotificationHandler
import com.eipna.centsation.habit.widget.updateAllHabitWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _calendars = MutableStateFlow<List<UserCalendar>>(emptyList())
    val calendars: StateFlow<List<UserCalendar>> = _calendars.asStateFlow()

    private val _calendarEvents = MutableStateFlow<List<CalendarEventItem>>(emptyList())
    val calendarEvents: StateFlow<List<CalendarEventItem>> = _calendarEvents.asStateFlow()

    private val _isLoadingCalendarEvents = MutableStateFlow(false)
    val isLoadingCalendarEvents: StateFlow<Boolean> = _isLoadingCalendarEvents.asStateFlow()

    init {
        loadCalendars()
    }

    fun loadCalendars() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = CalendarSyncManager.getAvailableCalendars(getApplication())
            _calendars.value = list
        }
    }

    fun loadCalendarEvents(calendarId: Long? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingCalendarEvents.value = true
            try {
                val events = CalendarSyncManager.readCalendarEvents(getApplication(), calendarId)
                _calendarEvents.value = events
            } catch (e: Exception) {
                _calendarEvents.value = emptyList()
            } finally {
                _isLoadingCalendarEvents.value = false
            }
        }
    }

    fun addTracker(
        title: String,
        baseDailyTarget: Int,
        unit: String,
        hour: Int,
        minute: Int,
        syncCalendar: Boolean,
        calendarId: Long?,
        calendarEventId: Long? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val tracker = TrackerEntity(
                title = title.trim(),
                baseDailyTarget = baseDailyTarget.coerceAtLeast(1),
                unit = unit.trim().ifEmpty { "reps" },
                scheduledHour = hour,
                scheduledMinute = minute,
                syncCalendar = syncCalendar,
                calendarId = calendarId,
                calendarEventId = calendarEventId
            )
            val newId = repository.insertTracker(tracker)
            val createdTracker = tracker.copy(id = newId)

            TrackerAlarmScheduler.scheduleDailyAlarm(getApplication(), createdTracker)

            val item = repository.getTrackerWithProgressById(newId)
            if (item != null && syncCalendar) {
                CalendarSyncManager.syncTrackerEvent(getApplication(), item)
            }
            updateAllHabitWidgets(getApplication())
        }
    }

    fun importCalendarEvent(
        event: CalendarEventItem,
        target: Int = event.parsedDetails.baseDailyTarget,
        unit: String = event.parsedDetails.unit
    ) {
        addTracker(
            title = event.parsedDetails.title,
            baseDailyTarget = target,
            unit = unit,
            hour = event.parsedDetails.scheduledHour,
            minute = event.parsedDetails.scheduledMinute,
            syncCalendar = true,
            calendarId = event.calendarId,
            calendarEventId = event.id
        )
    }

    fun updateTracker(
        tracker: TrackerEntity,
        oldHour: Int,
        oldMinute: Int,
        oldSync: Boolean
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

            val item = repository.getTrackerWithProgressById(tracker.id)
            if (item != null) {
                if (tracker.syncCalendar) {
                    CalendarSyncManager.syncTrackerEvent(getApplication(), item)
                } else if (oldSync && tracker.calendarEventId != null) {
                    CalendarSyncManager.deleteEvent(getApplication(), tracker.calendarEventId)
                    repository.updateCalendarEventId(tracker.id, null)
                }
            }
            updateAllHabitWidgets(getApplication())
        }
    }

    fun deleteTracker(item: TrackerWithProgress) {
        viewModelScope.launch(Dispatchers.IO) {
            TrackerAlarmScheduler.cancelAlarm(getApplication(), item.tracker.id)
            TrackerNotificationHandler.dismissNotification(getApplication(), item.tracker.id)
            if (item.tracker.calendarEventId != null) {
                CalendarSyncManager.deleteEvent(getApplication(), item.tracker.calendarEventId)
            }
            repository.deleteTracker(item.tracker)
            updateAllHabitWidgets(getApplication())
        }
    }

    fun logReps(trackerId: Long, additional: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = repository.logReps(trackerId, additional)
            if (updated != null) {
                if (updated.tracker.syncCalendar) {
                    CalendarSyncManager.syncTrackerEvent(getApplication(), updated)
                }
                updateAllHabitWidgets(getApplication())
            }
        }
    }

    fun setReps(trackerId: Long, totalCount: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = repository.setReps(trackerId, totalCount)
            if (updated != null) {
                if (updated.tracker.syncCalendar) {
                    CalendarSyncManager.syncTrackerEvent(getApplication(), updated)
                }
                updateAllHabitWidgets(getApplication())
            }
        }
    }
}
