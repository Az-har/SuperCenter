package com.eipna.centsation.habit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.eipna.centsation.habit.data.entity.TrackerLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerLogDao {

    @Query("SELECT * FROM tracker_logs ORDER BY date ASC")
    fun getAllLogs(): Flow<List<TrackerLogEntity>>

    @Query("SELECT * FROM tracker_logs WHERE trackerId = :trackerId")
    suspend fun getLogsSync(trackerId: Long): List<TrackerLogEntity>

    @Query("SELECT * FROM tracker_logs WHERE trackerId = :trackerId AND date = :date LIMIT 1")
    suspend fun getLogForDate(trackerId: Long, date: String): TrackerLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(log: TrackerLogEntity): Long
}
