package com.eipna.centsation.habit

import com.eipna.centsation.habit.data.entity.TrackerEntity
import com.eipna.centsation.habit.data.entity.TrackerLogEntity
import com.eipna.centsation.habit.data.model.TrackerWithProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HabitDebtCalculationTest {

    private fun createTracker(
        baseTarget: Int = 10,
        daysAgo: Long = 0,
        referenceDate: LocalDate = LocalDate.of(2026, 11, 3)
    ): TrackerEntity {
        val createdDate = referenceDate.minusDays(daysAgo)
        val epochMilli = createdDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return TrackerEntity(
            id = 1L,
            title = "Pushups",
            baseDailyTarget = baseTarget,
            unit = "reps",
            scheduledHour = 21,
            scheduledMinute = 0,
            createdAt = epochMilli
        )
    }

    @Test
    fun day1_zeroCompleted_remainingIsBase_noAccumulatedDebtYet() {
        val refDate = LocalDate.of(2026, 11, 1)
        val tracker = createTracker(baseTarget = 10, daysAgo = 0, referenceDate = refDate)
        val item = TrackerWithProgress(
            tracker = tracker,
            logs = emptyList(),
            todayDate = refDate.toString()
        )

        assertEquals(1L, item.daysActive)
        assertEquals(10, item.totalExpectedQuota)
        assertEquals(10, item.remainingToday)
        assertEquals(0, item.accumulatedBacklogDebt)
        assertEquals(10, item.effectiveTargetToday)
        assertEquals(0, item.todayCompletedReps)
        assertFalse(item.isCompletedToday)
        assertEquals(0f, item.completionRatio, 0.001f)
    }

    @Test
    fun day2_day1Missed_debtRolloverIntoDay2() {
        // Day 1: missed completely. Now on Day 2.
        val day2 = LocalDate.of(2026, 11, 2)
        val tracker = createTracker(baseTarget = 10, daysAgo = 1, referenceDate = day2)
        val item = TrackerWithProgress(
            tracker = tracker,
            logs = emptyList(),
            todayDate = day2.toString()
        )

        // Expected over 2 days = 20 reps.
        assertEquals(2L, item.daysActive)
        assertEquals(20, item.totalExpectedQuota)
        assertEquals(20, item.remainingToday)
        assertEquals(10, item.accumulatedBacklogDebt)
        assertEquals(20, item.effectiveTargetToday)
        assertFalse(item.isCompletedToday)
    }

    @Test
    fun day2_logs6Reps_remaining14_debtRolloverIntoDay3Is14() {
        val day2 = LocalDate.of(2026, 11, 2)
        val tracker = createTracker(baseTarget = 10, daysAgo = 1, referenceDate = day2)
        val logs = listOf(
            TrackerLogEntity(id = 1, trackerId = 1, date = "2026-11-02", completedCount = 6)
        )
        val item = TrackerWithProgress(
            tracker = tracker,
            logs = logs,
            todayDate = day2.toString()
        )

        assertEquals(20, item.totalExpectedQuota)
        assertEquals(6, item.totalCompletedReps)
        assertEquals(14, item.remainingToday)
        assertEquals(4, item.accumulatedBacklogDebt)

        // Day 3: Now Day 3 begins
        val day3 = LocalDate.of(2026, 11, 3)
        val trackerDay3 = createTracker(baseTarget = 10, daysAgo = 2, referenceDate = day3)
        val itemDay3 = TrackerWithProgress(
            tracker = trackerDay3,
            logs = logs,
            todayDate = day3.toString()
        )

        // Expected over 3 days = 30 reps. Total completed = 6. Remaining = 24.
        assertEquals(3L, itemDay3.daysActive)
        assertEquals(30, itemDay3.totalExpectedQuota)
        assertEquals(24, itemDay3.remainingToday)
        assertEquals(14, itemDay3.accumulatedBacklogDebt)
        assertEquals(24, itemDay3.effectiveTargetToday)
    }

    @Test
    fun day3_userCatchesUp_debtResetsToZero() {
        val day3 = LocalDate.of(2026, 11, 3)
        val tracker = createTracker(baseTarget = 10, daysAgo = 2, referenceDate = day3)
        // User logged 6 on day 2 and logs 24 on day 3
        val logs = listOf(
            TrackerLogEntity(id = 1, trackerId = 1, date = "2026-11-02", completedCount = 6),
            TrackerLogEntity(id = 2, trackerId = 1, date = "2026-11-03", completedCount = 24)
        )
        val item = TrackerWithProgress(
            tracker = tracker,
            logs = logs,
            todayDate = day3.toString()
        )

        assertEquals(30, item.totalExpectedQuota)
        assertEquals(30, item.totalCompletedReps)
        assertEquals(0, item.remainingToday)
        assertEquals(0, item.accumulatedBacklogDebt)
        assertTrue(item.isCompletedToday)
        assertEquals(1.0f, item.completionRatio, 0.001f)
    }

    @Test
    fun overachievement_doesNotProduceNegativeDebt() {
        val day1 = LocalDate.of(2026, 11, 1)
        val tracker = createTracker(baseTarget = 10, daysAgo = 0, referenceDate = day1)
        val logs = listOf(
            TrackerLogEntity(id = 1, trackerId = 1, date = "2026-11-01", completedCount = 25)
        )
        val item = TrackerWithProgress(
            tracker = tracker,
            logs = logs,
            todayDate = day1.toString()
        )

        assertEquals(0, item.remainingToday)
        assertEquals(0, item.accumulatedBacklogDebt)
        assertTrue(item.isCompletedToday)
        assertEquals(1.0f, item.completionRatio, 0.001f)
    }
}
