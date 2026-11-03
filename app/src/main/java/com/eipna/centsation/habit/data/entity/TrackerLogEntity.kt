package com.eipna.centsation.habit.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracker_logs",
    foreignKeys = [
        ForeignKey(
            entity = TrackerEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("trackerId"),
        Index(value = ["trackerId", "date"], unique = true)
    ]
)
data class TrackerLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val trackerId: Long,
    val date: String, // Format: YYYY-MM-DD
    val completedCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)
