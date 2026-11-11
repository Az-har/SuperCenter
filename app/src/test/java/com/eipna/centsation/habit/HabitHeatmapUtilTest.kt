package com.eipna.centsation.habit

import com.eipna.centsation.habit.data.entity.TrackerEntity
import com.eipna.centsation.habit.data.entity.TrackerLogEntity
import com.eipna.centsation.habit.util.HabitHeatmapUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class HabitHeatmapUtilTest {

    private val sampleTracker = TrackerEntity(
        id = 1L,
        title = "Pushups",
        baseDailyTarget = 10,
        unit = "reps",
        scheduledHour = 21,
        scheduledMinute = 0,
        createdAt = System.currentTimeMillis() - 40L * 24 * 3600 * 1000 // 40 days ago
    )

    @Test
    fun testDateFormattingAndParsing() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 5, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dateStr = HabitHeatmapUtil.formatDate(cal)
        assertEquals("2026-11-05", dateStr)

        val parsedCal = HabitHeatmapUtil.parseDate("2026-11-05")
        assertEquals(2026, parsedCal.get(Calendar.YEAR))
        assertEquals(Calendar.NOVEMBER, parsedCal.get(Calendar.MONTH))
        assertEquals(5, parsedCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun testStreak_NoLogs_ZeroStreak() {
        val stats = HabitHeatmapUtil.calculateStreakStats(
            tracker = sampleTracker,
            logs = emptyList(),
            todayDate = "2026-11-05"
        )
        assertEquals(0, stats.currentStreak)
        assertEquals(0, stats.longestStreak)
        assertEquals(0, stats.totalLogged)
    }

    @Test
    fun testStreak_CompletedTodayOnly_StreakIsOne() {
        val logs = listOf(
            TrackerLogEntity(id = 1, trackerId = 1, date = "2026-11-05", completedCount = 10)
        )
        val stats = HabitHeatmapUtil.calculateStreakStats(
            tracker = sampleTracker,
            logs = logs,
            todayDate = "2026-11-05"
        )
        assertEquals(1, stats.currentStreak)
        assertEquals(1, stats.longestStreak)
        assertEquals(10, stats.totalLogged)
    }

    @Test
    fun testStreak_CompletedTodayAndConsecutivePastDays() {
        val logs = listOf(
            TrackerLogEntity(id = 1, trackerId = 1, date = "2026-11-05", completedCount = 12),
            TrackerLogEntity(id = 2, trackerId = 1, date = "2026-11-04", completedCount = 10),
            TrackerLogEntity(id = 3, trackerId = 1, date = "2026-11-03", completedCount = 15),
            TrackerLogEntity(id = 4, trackerId = 1, date = "2026-11-02", completedCount = 8), // Incomplete (< 10)
            TrackerLogEntity(id = 5, trackerId = 1, date = "2026-11-01", completedCount = 20)
        )
        val stats = HabitHeatmapUtil.calculateStreakStats(
            tracker = sampleTracker,
            logs = logs,
            todayDate = "2026-11-05"
        )
        assertEquals(3, stats.currentStreak) // 03, 04, 05
        assertTrue(stats.longestStreak >= 3)
        assertEquals(65, stats.totalLogged)
    }

    @Test
    fun testStreak_TodayNotDoneYet_YesterdayDone_StreakMaintained() {
        // Active streak survives today until day ends
        val logs = listOf(
            TrackerLogEntity(id = 1, trackerId = 1, date = "2026-11-04", completedCount = 10),
            TrackerLogEntity(id = 2, trackerId = 1, date = "2026-11-03", completedCount = 10),
            TrackerLogEntity(id = 3, trackerId = 1, date = "2026-11-02", completedCount = 10)
        )
        val stats = HabitHeatmapUtil.calculateStreakStats(
            tracker = sampleTracker,
            logs = logs,
            todayDate = "2026-11-05"
        )
        assertEquals(3, stats.currentStreak)
    }

    @Test
    fun testStreak_TodayNotDoneAndYesterdayNotDone_StreakZero() {
        val logs = listOf(
            TrackerLogEntity(id = 1, trackerId = 1, date = "2026-11-03", completedCount = 10),
            TrackerLogEntity(id = 2, trackerId = 1, date = "2026-11-02", completedCount = 10)
        )
        val stats = HabitHeatmapUtil.calculateStreakStats(
            tracker = sampleTracker,
            logs = logs,
            todayDate = "2026-11-05"
        )
        assertEquals(0, stats.currentStreak)
        assertEquals(2, stats.longestStreak)
    }

    @Test
    fun testHeatmapGrid_35Days_AlignedToSunday() {
        val (items, numWeeks) = HabitHeatmapUtil.generateHeatmapGrid(
            tracker = sampleTracker,
            logs = listOf(
                TrackerLogEntity(id = 1, trackerId = 1, date = "2026-11-05", completedCount = 10),
                TrackerLogEntity(id = 2, trackerId = 1, date = "2026-11-04", completedCount = 5),
                TrackerLogEntity(id = 3, trackerId = 1, date = "2026-11-03", completedCount = 15)
            ),
            is365Days = false,
            todayDate = "2026-11-05"
        )

        assertTrue(numWeeks in 5..6)
        assertEquals(numWeeks * 7, items.size)

        // Find today
        val todayItem = items.first { it.isToday }
        assertEquals("2026-11-05", todayItem.dateString)
        assertEquals(3, todayItem.level) // 10 == target -> level 3
        assertFalse(todayItem.isFuture)

        // Check level 2 (5 reps of 10 target)
        val yesterdayItem = items.first { it.dateString == "2026-11-04" }
        assertEquals(2, yesterdayItem.level)

        // Check level 4 (> target)
        val twoDaysAgo = items.first { it.dateString == "2026-11-03" }
        assertEquals(4, twoDaysAgo.level)

        // Sunday is dayOfWeek = 0
        assertEquals(0, items.first().dayOfWeek)
    }

    @Test
    fun testHeatmapGrid_365Days_Produces53Weeks() {
        val (items, numWeeks) = HabitHeatmapUtil.generateHeatmapGrid(
            tracker = sampleTracker,
            logs = emptyList(),
            is365Days = true,
            todayDate = "2026-11-05"
        )

        assertTrue(numWeeks in 52..54)
        assertEquals(numWeeks * 7, items.size)
        assertTrue(items.any { it.isToday })
    }

    @Test
    fun testHeatmapGridForWeeks_SingleRepOutOf100StillVisibleLevel1() {
        val highTargetTracker = sampleTracker.copy(baseDailyTarget = 100)
        val (items, numWeeks) = HabitHeatmapUtil.generateHeatmapGridForWeeks(
            tracker = highTargetTracker,
            logs = listOf(
                TrackerLogEntity(id = 1, trackerId = 1, date = "2026-11-05", completedCount = 1)
            ),
            numWeeks = 20,
            todayDate = "2026-11-05"
        )

        assertEquals(20, numWeeks)
        assertEquals(20 * 7, items.size)
        assertEquals(0, items.first().dayOfWeek)

        val todayItem = items.first { it.isToday }
        assertEquals(1, todayItem.completedCount)
        assertEquals(1, todayItem.level) // 1 rep must always produce level >= 1
    }
}
