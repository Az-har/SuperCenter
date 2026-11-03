package com.eipna.centsation.habit

import com.eipna.centsation.habit.util.TrackerDateUtil
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class TrackerDateUtilTest {

    @Test
    fun getTodayDateString_formatsCorrectly() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 3, 14, 30, 0)
        }
        val dateStr = TrackerDateUtil.getTodayDateString(cal)
        assertEquals("2026-11-03", dateStr)
    }

    @Test
    fun calculateDaysActive_sameDay_returnsOne() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 1, 9, 0, 0)
        }
        val days = TrackerDateUtil.calculateDaysActive(cal.timeInMillis, "2026-01-01")
        assertEquals(1L, days)
    }

    @Test
    fun calculateDaysActive_nextDay_returnsTwo() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 1, 21, 0, 0)
        }
        val days = TrackerDateUtil.calculateDaysActive(cal.timeInMillis, "2026-01-02")
        assertEquals(2L, days)
    }

    @Test
    fun calculateDaysActive_tenDaysLater_returnsEleven() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 1, 10, 0, 0)
        }
        val days = TrackerDateUtil.calculateDaysActive(cal.timeInMillis, "2026-01-11")
        assertEquals(11L, days)
    }

    @Test
    fun calculateDaysActive_invalidDateString_returnsOne() {
        val cal = Calendar.getInstance()
        val days = TrackerDateUtil.calculateDaysActive(cal.timeInMillis, "invalid-date")
        assertEquals(1L, days)
    }

    @Test
    fun calculateDaysActive_acrossMonthBoundary_calculatesCorrectly() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 30, 8, 0, 0)
        }
        // Jan 30 to Feb 2 = Jan 30 (1), Jan 31 (2), Feb 1 (3), Feb 2 (4) -> 4 days
        val days = TrackerDateUtil.calculateDaysActive(cal.timeInMillis, "2026-02-02")
        assertEquals(4L, days)
    }

    @Test
    fun calculateDaysActive_daylightSavingTimeShift_handlesResiliently() {
        // Test with a timezone that undergoes daylight saving time
        val tz = TimeZone.getTimeZone("America/New_York")
        val cal = Calendar.getInstance(tz).apply {
            // March 7, 2026 is before DST transition (March 8)
            set(2026, Calendar.MARCH, 7, 10, 0, 0)
        }
        val days = TrackerDateUtil.calculateDaysActive(cal.timeInMillis, "2026-03-09")
        // March 7 (1), March 8 (2), March 9 (3) -> 3 days
        assertEquals(3L, days)
    }
}
