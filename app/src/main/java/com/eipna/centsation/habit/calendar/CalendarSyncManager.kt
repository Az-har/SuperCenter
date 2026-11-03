package com.eipna.centsation.habit.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.eipna.centsation.habit.data.model.TrackerWithProgress
import com.eipna.centsation.habit.data.repository.TrackerRepository
import java.util.Calendar
import java.util.TimeZone

data class UserCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val isPrimary: Boolean
)

object CalendarSyncManager {

    private const val TAG = "CalendarSyncManager"

    fun hasCalendarPermissions(context: Context): Boolean {
        val readGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        val writeGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        return readGranted && writeGranted
    }

    fun getAvailableCalendars(context: Context): List<UserCalendar> {
        if (!hasCalendarPermissions(context)) {
            return emptyList()
        }

        val calendars = mutableListOf<UserCalendar>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.IS_PRIMARY
        )

        try {
            val cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
            )

            cursor?.use {
                val idIdx = it.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                val nameIdx = it.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accountIdx = it.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
                val primaryIdx = it.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)

                while (it.moveToNext()) {
                    val id = it.getLong(idIdx)
                    val name = it.getString(nameIdx) ?: "Calendar $id"
                    val account = it.getString(accountIdx) ?: ""
                    val isPrimary = if (primaryIdx >= 0) it.getInt(primaryIdx) == 1 else false

                    calendars.add(UserCalendar(id, name, account, isPrimary))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying device calendars", e)
        }

        return calendars
    }

    suspend fun syncTrackerEvent(context: Context, item: TrackerWithProgress): Long? {
        val tracker = item.tracker
        if (!tracker.syncCalendar || tracker.calendarId == null || !hasCalendarPermissions(context)) {
            return null
        }

        val calendarId = tracker.calendarId
        val startTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, tracker.scheduledHour)
            set(Calendar.MINUTE, tracker.scheduledMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val endTime = startTime + (30 * 60 * 1000L) // 30 minute block

        val title = if (item.isCompletedToday) {
            "✅ ${tracker.title} [Done]"
        } else {
            "${tracker.title} (${item.remainingToday} ${tracker.unit} due)"
        }

        val description = buildString {
            append("Daily Target: ${tracker.baseDailyTarget} ${tracker.unit}\n")
            append("Completed Today: ${item.todayCompletedReps} ${tracker.unit}\n")
            if (item.accumulatedBacklogDebt > 0) {
                append("Habit Debt Rollover: +${item.accumulatedBacklogDebt} ${tracker.unit}\n")
            }
            append("Remaining Today: ${item.remainingToday} ${tracker.unit}\n")
            append("Tracked with Super Center Daily Quota Module")
        }

        try {
            val existingEventId = tracker.calendarEventId
            if (existingEventId != null && existingEventId > 0) {
                // Try updating existing event
                val updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingEventId)
                val values = ContentValues().apply {
                    put(CalendarContract.Events.TITLE, title)
                    put(CalendarContract.Events.DESCRIPTION, description)
                    put(CalendarContract.Events.DTSTART, startTime)
                    put(CalendarContract.Events.DTEND, endTime)
                }

                val rows = context.contentResolver.update(updateUri, values, null, null)
                if (rows > 0) {
                    Log.d(TAG, "Updated existing calendar event $existingEventId for ${tracker.title}")
                    return existingEventId
                }
            }

            // Create new calendar event
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, description)
                put(CalendarContract.Events.DTSTART, startTime)
                put(CalendarContract.Events.DTEND, endTime)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
                put(CalendarContract.Events.HAS_ALARM, 1)
            }

            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val newEventId = uri?.lastPathSegment?.toLongOrNull()
            if (newEventId != null) {
                TrackerRepository.getInstance(context).updateCalendarEventId(tracker.id, newEventId)
                Log.d(TAG, "Created calendar event $newEventId for ${tracker.title}")
                return newEventId
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing calendar event for tracker ${tracker.title}", e)
        }

        return null
    }

    fun deleteEvent(context: Context, eventId: Long?) {
        if (eventId == null || eventId <= 0 || !hasCalendarPermissions(context)) return
        try {
            val deleteUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            context.contentResolver.delete(deleteUri, null, null)
            Log.d(TAG, "Deleted calendar event $eventId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting calendar event $eventId", e)
        }
    }
}
