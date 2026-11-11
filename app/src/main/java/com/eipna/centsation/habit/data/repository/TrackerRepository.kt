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
    private val trackerLogDao: TrackerLogDao,
    private val database: HabitDatabase? = null
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

    suspend fun getActiveTrackerCount(): Int {
        return trackerDao.getActiveTrackerCount()
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

    fun exportTrackersSync(): List<TrackerEntity> = trackerDao.getAllTrackersSync()

    fun exportTrackerLogsSync(): List<TrackerLogEntity> = trackerLogDao.getAllLogsSync()

    fun bulkImportSync(trackers: List<TrackerEntity>, logs: List<TrackerLogEntity>) {
        if (trackers.isEmpty() && logs.isEmpty()) return
        val db = database
        val importWork = {
            val idMapping = mutableMapOf<Long, Long>()
            for (tracker in trackers) {
                val existing = if (tracker.id > 0) trackerDao.getTrackerByIdSync(tracker.id) else null
                val targetId = when {
                    existing == null -> trackerDao.insertSync(tracker)
                    existing.title == tracker.title && existing.createdAt == tracker.createdAt -> {
                        trackerDao.updateSync(tracker)
                        existing.id
                    }
                    else -> trackerDao.insertSync(tracker.copy(id = 0))
                }
                if (tracker.id > 0) {
                    idMapping[tracker.id] = targetId
                }
            }

            for (log in logs) {
                val resolvedTrackerId = idMapping[log.trackerId] ?: log.trackerId
                if (trackerDao.getTrackerByIdSync(resolvedTrackerId) == null) continue
                val existingLogId = trackerLogDao.findLogIdSync(resolvedTrackerId, log.date) ?: 0L
                trackerLogDao.insertOrUpdateSync(
                    log.copy(
                        id = existingLogId,
                        trackerId = resolvedTrackerId
                    )
                )
            }
        }
        if (db != null) {
            db.runInTransaction(importWork)
        } else {
            importWork()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: TrackerRepository? = null

        @JvmStatic
        fun getInstance(context: Context): TrackerRepository {
            return INSTANCE ?: synchronized(this) {
                val db = HabitDatabase.getInstance(context)
                val instance = TrackerRepository(db.trackerDao(), db.trackerLogDao(), db)
                INSTANCE = instance
                instance
            }
        }
    }
}
