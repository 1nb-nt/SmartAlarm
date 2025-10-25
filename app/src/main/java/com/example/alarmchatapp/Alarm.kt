package com.example.alarmchatapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val message: String,
    val triggerTimeMillis: Long,
    val isRecurring: Boolean = false,
    val recurringDays: List<Int>? = null,
    val initialNote: String? = null,
    val isIntervalBased: Boolean = false,
    val intervalMinutes: Int? = null, // e.g., 25 for "every 25 minutes"
    val expiryTimeMillis: Long? = null // null = no expiry, otherwise stop after this time
)
