package com.example.alarmchatapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_tasks")
data class ScheduledTask(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val description: String,
    val executionTimeMillis: Long // The exact time the task is for
)
