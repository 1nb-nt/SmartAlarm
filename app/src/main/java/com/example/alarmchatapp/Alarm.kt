package com.example.alarmchatapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val message: String,
    val triggerTimeMillis: Long,
    val isRecurring: Boolean = false
)
