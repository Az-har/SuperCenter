package com.eipna.centsation.habit.util

import android.content.Context
import android.text.format.DateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max

object TrackerDateUtil {

    fun formatReminderTime(context: Context, hour: Int, minute: Int): String =
        formatReminderTime(DateFormat.is24HourFormat(context), hour, minute)

    fun formatReminderTime(is24Hour: Boolean, hour: Int, minute: Int): String {
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        return if (is24Hour) {
            String.format(Locale.getDefault(), "%02d:%02d", safeHour, safeMinute)
        } else {
            val h = when {
                safeHour == 0 -> 12
                safeHour > 12 -> safeHour - 12
                else -> safeHour
            }
            val amPm = if (safeHour < 12) "AM" else "PM"
            String.format(Locale.getDefault(), "%02d:%02d %s", h, safeMinute, amPm)
        }
    }

    /**
     * Returns today's date formatted as "YYYY-MM-DD" using local calendar.
     */
    fun getTodayDateString(calendar: Calendar = Calendar.getInstance()): String {
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * Calculates the active day count (1-based) between the creation timestamp and target date string (YYYY-MM-DD).
     * Fully compatible with Android API 24+ without requiring desugaring.
     */
    fun calculateDaysActive(createdAtEpochMilli: Long, targetDateString: String): Long {
        val startCal = Calendar.getInstance().apply {
            timeInMillis = createdAtEpochMilli
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val parts = targetDateString.split("-")
        if (parts.size != 3) return 1L

        val year = parts[0].toIntOrNull() ?: return 1L
        val month = parts[1].toIntOrNull() ?: return 1L
        val day = parts[2].toIntOrNull() ?: return 1L

        val targetCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diffMs = targetCal.timeInMillis - startCal.timeInMillis
        // Add 12 hours before division to prevent daylight saving time (23/25 hour) offsets from skewing day calculations
        val diffDays = (diffMs + TimeUnit.HOURS.toMillis(12)) / TimeUnit.DAYS.toMillis(1)
        return max(1L, diffDays + 1L)
    }
}
