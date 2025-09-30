package com.example.alarmchatapp.network

import com.google.gson.annotations.SerializedName

// Request for the backend
data class AlarmApiRequest(
    val objective: String,
    val objective_key: String,
    val model: String,
    val inputs: Map<String, String>
)

// Response from the backend (note: notification is a list of ISO strings)
data class AlarmApiResponse(
    @SerializedName("p_type") val pType: String,
    @SerializedName("alarm_type") val alarmType: String,
    val title: String,
    val datetime: String?,        // ISO e.g. 2026-05-17T09:00:00+05:30
    val time: String?,            // e.g. "09:00"
    val location: String?,
    val distance: String?,
    val timezone: String?,        // e.g. "Asia/Kolkata"
    val recurrence: String?,      // e.g. "daily" / "weekly" / "yearly"
    @SerializedName("ex_days") val exDays: List<String>?,
    val initial_note: String?,
    val response: String?,
    val notification: List<String> // ISO times or can be empty
)
