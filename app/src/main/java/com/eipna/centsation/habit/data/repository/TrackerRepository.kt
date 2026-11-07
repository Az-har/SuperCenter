package com.eipna.centsation.habit.data.repository

import android.content.Context
import com.eipna.centsation.habit.data.dao.TrackerDao
import com.eipna.centsation.habit.data.dao.TrackerLogDao
import com.eipna.centsation.habit.data.db.HabitDatabase
import com.eipna.centsation.habit.data.entity.TrackerEntity
import com.eipna.centsation.habit.data.entity.TrackerLogEntity
import com.eipna.centsation.habit.data.model.TrackerWithProgress
import com.eipna.centsation.habit.util.TrackerDateUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class TrackerRepository(
    private val trackerDao: TrackerDao,
    private val trackerLogDao: TrackerLogDao
) {

    fun getTrackersWithProgress(): Flow<List<TrackerWithProgress>> {
        return trackerDao.getActiveTrackers().combine(trackerLogDao.getAllLogs()) { trackers, logs ->
            val logsByTracker = logs.groupBy { it.trackerId }
            val today = TrackerDateUtil.getTodayDateString()
            trackers.map { tracker ->
                TrackerWithProgress(
                    tracker = tracker,
                    logs = logsByTracker[tracker.id] ?: emptyList(),
                    todayDate = today
                )
            }
        }
    }

    suspend fun getTrackerById(id: Long): TrackerEntity? {
        return trackerDao.getTrackerById(id)
    }

    suspend fun getTrackerWithProgressById(id: Long): TrackerWithProgress? {
        val tracker = trackerDao.getTrackerById(id) ?: return null
        val logs = trackerLogDao.getLogsSync(id)
        return TrackerWithProgress(tracker, logs, TrackerDateUtil.getTodayDateString())
    }

    suspend fun insertTracker(tracker: TrackerEntity): Long {
        return trackerDao.insert(tracker)
    }

    suspend fun updateTracker(tracker: TrackerEntity) {
        trackerDao.update(tracker)
    }

    suspend fun deleteTracker(tracker: TrackerEntity) {
        trackerDao.delete(tracker)
    }

    /**
     * Increment completed count for a given date by additionalCount
     */
    suspend fun logReps(
        trackerId: Long,
        additionalCount: Int,
        date: String = TrackerDateUtil.getTodayDateString()
    ): TrackerWithProgress? {
        val existingLog = trackerLogDao.getLogForDate(trackerId, date)
        val currentCount = existingLog?.completedCount ?: 0
        val newCount = (currentCount + additionalCount).coerceAtLeast(0)

        val logEntity = existingLog?.copy(completedCount = newCount, timestamp = System.currentTimeMillis())
            ?: TrackerLogEntity(trackerId = trackerId, date = date, completedCount = newCount)

        trackerLogDao.insertOrUpdate(logEntity)
        return getTrackerWithProgressById(trackerId)
    }

    /**
     * Directly set completed count for a given date
     */
    suspend fun setReps(
        trackerId: Long,
        totalCount: Int,
        date: String = TrackerDateUtil.getTodayDateString()
    ): TrackerWithProgress? {
        val count = totalCount.coerceAtLeast(0)
        val existingLog = trackerLogDao.getLogForDate(trackerId, date)
        val logEntity = existingLog?.copy(completedCount = count, timestamp = System.currentTimeMillis())
            ?: TrackerLogEntity(trackerId = trackerId, date = date, completedCount = count)

        trackerLogDao.insertOrUpdate(logEntity)
        return getTrackerWithProgressById(trackerId)
    }

    companion object {
        @Volatile
        private var INSTANCE: TrackerRepository? = null

        fun getInstance(context: Context): TrackerRepository {
            return INSTANCE ?: synchronized(this) {
                val db = HabitDatabase.getInstance(context)
                val instance = TrackerRepository(db.trackerDao(), db.trackerLogDao())
                INSTANCE = instance
                instance
            }
        }
    }
}
