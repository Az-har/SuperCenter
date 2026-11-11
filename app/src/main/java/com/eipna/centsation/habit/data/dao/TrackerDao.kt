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

    @Query("SELECT COUNT(*) FROM trackers WHERE isActive = 1")
    suspend fun getActiveTrackerCount(): Int

    @Query("SELECT * FROM trackers WHERE id = :id LIMIT 1")
    suspend fun getTrackerById(id: Long): TrackerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tracker: TrackerEntity): Long

    @Update
    suspend fun update(tracker: TrackerEntity)

    @Query("SELECT * FROM trackers ORDER BY id ASC")
    fun getAllTrackersSync(): List<TrackerEntity>

    @Query("SELECT * FROM trackers WHERE id = :id LIMIT 1")
    fun getTrackerByIdSync(id: Long): TrackerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSync(tracker: TrackerEntity): Long

    @Update
    fun updateSync(tracker: TrackerEntity)

    @Delete
    suspend fun delete(tracker: TrackerEntity)
}
