package com.eipna.centsation.habit

import com.eipna.centsation.habit.data.entity.TrackerEntity
import com.eipna.centsation.habit.data.entity.TrackerLogEntity
import com.eipna.centsation.habit.data.model.TrackerWithProgress
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
        assertEquals(2, todayItem.level) // 10 == target -> level 2 (solid green)
        assertFalse(todayItem.isFuture)

        // Check level 1 (5 reps of 10 target -> level 1, light green outline)
        val yesterdayItem = items.first { it.dateString == "2026-11-04" }
        assertEquals(1, yesterdayItem.level)

        // Check level 3 (15 reps > 10 target -> level 3, surplus blue)
        val twoDaysAgo = items.first { it.dateString == "2026-11-03" }
        assertEquals(3, twoDaysAgo.level)

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

    @Test
    fun testStreakStats_SurplusDetection() {
        val normalLogs = listOf(
            TrackerLogEntity(id = 1, trackerId = 1, date = "2026-11-05", completedCount = 10)
        )
        val normalStats = HabitHeatmapUtil.calculateStreakStats(sampleTracker, normalLogs, "2026-11-05")
        assertFalse(normalStats.isSurplusToday)

        val surplusLogs = listOf(
            TrackerLogEntity(id = 2, trackerId = 1, date = "2026-11-05", completedCount = 15)
        )
        val surplusStats = HabitHeatmapUtil.calculateStreakStats(sampleTracker, surplusLogs, "2026-11-05")
        assertTrue(surplusStats.isSurplusToday)
    }

    @Test
    fun testHeatmapGridForWeeks_OneWeekMode_ProducesSevenItems() {
        val (items, numWeeks) = HabitHeatmapUtil.generateHeatmapGridForWeeks(
            tracker = sampleTracker,
            logs = listOf(
                TrackerLogEntity(id = 1, trackerId = 1, date = "2026-11-05", completedCount = 12)
            ),
            numWeeks = 1,
            todayDate = "2026-11-05"
        )
        assertEquals(1, numWeeks)
        assertEquals(7, items.size)
        val todayItem = items.first { it.isToday }
        assertEquals(3, todayItem.level) // 12 > 10 target -> level 3 (surplus blue)
    }

    @Test
    fun testHeatmapGridForWeeks_FiveWeeksMonth_ProducesThirtyFiveItems() {
        val (items, numWeeks) = HabitHeatmapUtil.generateHeatmapGridForWeeks(
            tracker = sampleTracker,
            logs = emptyList(),
            numWeeks = 5,
            todayDate = "2026-11-05"
        )
        assertEquals(5, numWeeks)
        assertEquals(35, items.size)
        assertTrue(items.any { it.isToday })
    }

    @Test
    fun testCombinedHeatmap_EmptyTrackers_ReturnsEmpty() {
        val (items, numWeeks) = HabitHeatmapUtil.generateCombinedHeatmapGridForWeeks(
            trackersWithProgress = emptyList(),
            numWeeks = 5,
            todayDate = "2026-11-05"
        )
        assertTrue(items.isEmpty())
        assertEquals(0, numWeeks)
    }

    @Test
    fun testCombinedHeatmap_CalculatesLevelsCorrectly() {
        val trackerA = TrackerEntity(id = 1L, title = "Pushups", baseDailyTarget = 10, unit = "reps", scheduledHour = 20, scheduledMinute = 0)
        val trackerB = TrackerEntity(id = 2L, title = "Read", baseDailyTarget = 20, unit = "pages", scheduledHour = 21, scheduledMinute = 0)

        val logsA = listOf(
            TrackerLogEntity(id = 1, trackerId = 1, date = "2026-11-04", completedCount = 5),  // Partial A (5/10), B not done -> Level 1 (some activity)
            TrackerLogEntity(id = 2, trackerId = 1, date = "2026-11-03", completedCount = 10), // A done (10/10), B partial (5/20) -> Level 2 (partial goals)
            TrackerLogEntity(id = 3, trackerId = 1, date = "2026-11-02", completedCount = 10), // A done (10/10), B done (20/20) -> Level 3 (all goals met)
            TrackerLogEntity(id = 4, trackerId = 1, date = "2026-11-01", completedCount = 15)  // A surplus (15/10), B surplus (25/20) -> Level 4 (surplus)
        )
        val logsB = listOf(
            TrackerLogEntity(id = 5, trackerId = 2, date = "2026-11-04", completedCount = 0),
            TrackerLogEntity(id = 6, trackerId = 2, date = "2026-11-03", completedCount = 5),
            TrackerLogEntity(id = 7, trackerId = 2, date = "2026-11-02", completedCount = 20),
            TrackerLogEntity(id = 8, trackerId = 2, date = "2026-11-01", completedCount = 25)
        )

        val twpA = TrackerWithProgress(tracker = trackerA, logs = logsA, todayDate = "2026-11-05")
        val twpB = TrackerWithProgress(tracker = trackerB, logs = logsB, todayDate = "2026-11-05")

        val (items, numWeeks) = HabitHeatmapUtil.generateCombinedHeatmapGridForWeeks(
            trackersWithProgress = listOf(twpA, twpB),
            numWeeks = 1,
            todayDate = "2026-11-05"
        )
        assertEquals(1, numWeeks)
        assertEquals(7, items.size)

        // 2026-11-05 (today): No logs -> Level 0
        val todayItem = items.first { it.dateString == "2026-11-05" }
        assertEquals(0, todayItem.level)
        assertTrue(todayItem.isToday)

        // 2026-11-04: count=5 (A=5, B=0), target=30, goalsMet=0 -> Level 1 (some activity, no goals)
        val itemNov04 = items.first { it.dateString == "2026-11-04" }
        assertEquals(1, itemNov04.level)
        assertEquals(5, itemNov04.totalCompletedCount)
        assertEquals(0, itemNov04.trackersCompleted)

        // 2026-11-03: count=15 (A=10, B=5), target=30, goalsMet=1 (A) out of 2 -> Level 2 (partial goals)
        val itemNov03 = items.first { it.dateString == "2026-11-03" }
        assertEquals(2, itemNov03.level)
        assertEquals(1, itemNov03.trackersCompleted)

        // 2026-11-02: count=30 (A=10, B=20), target=30, goalsMet=2 out of 2 -> Level 3 (all goals)
        val itemNov02 = items.first { it.dateString == "2026-11-02" }
        assertEquals(3, itemNov02.level)
        assertEquals(2, itemNov02.trackersCompleted)

        // 2026-11-01: count=40 (A=15, B=25), target=30, goalsMet=2, surplus=true -> Level 4 (surplus)
        val itemNov01 = items.first { it.dateString == "2026-11-01" }
        assertEquals(4, itemNov01.level)
        assertEquals(2, itemNov01.trackersCompleted)
        assertEquals(40, itemNov01.totalCompletedCount)
    }

    @Test
    fun testCombinedStreakStats() {
        val trackerA = TrackerEntity(id = 1L, title = "Pushups", baseDailyTarget = 10, unit = "reps", scheduledHour = 20, scheduledMinute = 0)
        val trackerB = TrackerEntity(id = 2L, title = "Read", baseDailyTarget = 20, unit = "pages", scheduledHour = 21, scheduledMinute = 0)

        val logsA = listOf(
            TrackerLogEntity(id = 1, trackerId = 1, date = "2026-11-05", completedCount = 10), // today met
            TrackerLogEntity(id = 2, trackerId = 1, date = "2026-11-04", completedCount = 12)  // past met
        )
        val logsB = listOf(
            TrackerLogEntity(id = 3, trackerId = 2, date = "2026-11-05", completedCount = 5),  // today not met
            TrackerLogEntity(id = 4, trackerId = 2, date = "2026-11-04", completedCount = 20)  // past met
        )

        val twpA = TrackerWithProgress(tracker = trackerA, logs = logsA, todayDate = "2026-11-05")
        val twpB = TrackerWithProgress(tracker = trackerB, logs = logsB, todayDate = "2026-11-05")

        val stats = HabitHeatmapUtil.calculateCombinedStreakStats(listOf(twpA, twpB), "2026-11-05")
        assertEquals(2, stats.totalTrackers)
        assertEquals(1, stats.trackersCompletedToday) // only A met today
        assertEquals(3, stats.totalGoalsMet)          // A today, A yesterday, B yesterday
        assertEquals(47, stats.totalLogged)           // 10 + 12 + 5 + 20
    }
}
