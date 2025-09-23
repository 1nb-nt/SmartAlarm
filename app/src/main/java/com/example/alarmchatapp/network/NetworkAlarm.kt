package com.example.alarmchatapp.network

// Network model aligned to UI/domain Alarm:
// label, timeMillis, important, recurringDays (List<Int>?, Calendar constants)
data class NetworkAlarm(
    val label: String,
    val timeMillis: Long,
    val important: Boolean = false,
    val recurringDays: List<Int>? = null
)
