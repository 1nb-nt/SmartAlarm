package com.example.alarmchatapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_tasks")
data class ScheduledTask(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val description: String,
    val executionTimeMillis: Long,
    val isRecurring: Boolean = false,
    val endDateMillis: Long? = null
)
