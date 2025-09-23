package com.example.alarmchatapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_tasks")
data class ScheduledTask(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val taskName: String,
    val triggerTimeMillis: Long,
    val isRecurring: Boolean,
    val recurringDays: List<Int>?
)
