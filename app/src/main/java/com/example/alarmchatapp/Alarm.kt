package com.example.alarmchatapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val timeMillis: Long,
    val important: Boolean = false,
    val recurringDays: List<Int>? = null,
    val hour: Int,
    val minute: Int
)
