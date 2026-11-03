package com.eipna.centsation.habit.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trackers")
data class TrackerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val baseDailyTarget: Int,
    val unit: String,
    val scheduledHour: Int = 21,
    val scheduledMinute: Int = 0,
    val syncCalendar: Boolean = false,
    val calendarId: Long? = null,
    val calendarEventId: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
