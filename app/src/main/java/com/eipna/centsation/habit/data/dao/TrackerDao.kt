package com.eipna.centsation.habit.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.eipna.centsation.habit.data.entity.TrackerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerDao {

    @Query("SELECT * FROM trackers WHERE isActive = 1 ORDER BY id DESC")
    fun getActiveTrackers(): Flow<List<TrackerEntity>>

    @Query("SELECT * FROM trackers ORDER BY id DESC")
    fun getAllTrackers(): Flow<List<TrackerEntity>>

    @Query("SELECT * FROM trackers WHERE id = :id LIMIT 1")
    suspend fun getTrackerById(id: Long): TrackerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tracker: TrackerEntity): Long

    @Update
    suspend fun update(tracker: TrackerEntity)

    @Delete
    suspend fun delete(tracker: TrackerEntity)

    @Query("UPDATE trackers SET calendarEventId = :eventId WHERE id = :id")
    suspend fun updateCalendarEventId(id: Long, eventId: Long?)
}
