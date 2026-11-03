package com.eipna.centsation.habit.data.model

import com.eipna.centsation.habit.data.entity.TrackerEntity
import com.eipna.centsation.habit.data.entity.TrackerLogEntity
import com.eipna.centsation.habit.util.TrackerDateUtil
import kotlin.math.max
import kotlin.math.min

data class TrackerWithProgress(
    val tracker: TrackerEntity,
    val logs: List<TrackerLogEntity> = emptyList(),
    val todayDate: String = TrackerDateUtil.getTodayDateString()
) {
    val totalCompletedReps: Int
        get() = logs.sumOf { it.completedCount }

    val todayCompletedReps: Int
        get() = logs.firstOrNull { it.date == todayDate }?.completedCount ?: 0

    val daysActive: Long
        get() = TrackerDateUtil.calculateDaysActive(tracker.createdAt, todayDate)

    val totalExpectedQuota: Int
        get() = (daysActive * tracker.baseDailyTarget).toInt()

    val remainingToday: Int
        get() = max(0, totalExpectedQuota - totalCompletedReps)

    val accumulatedBacklogDebt: Int
        get() = max(0, remainingToday - tracker.baseDailyTarget)

    val effectiveTargetToday: Int
        get() = tracker.baseDailyTarget + accumulatedBacklogDebt

    val isCompletedToday: Boolean
        get() = remainingToday == 0

    val progressPercent: Int
        get() {
            if (totalExpectedQuota <= 0) return 100
            val p = (totalCompletedReps.toDouble() / totalExpectedQuota.toDouble()) * 100.0
            return min(100, max(0, p.toInt()))
        }

    val completionRatio: Float
        get() = (progressPercent / 100f).coerceIn(0f, 1f)
}
