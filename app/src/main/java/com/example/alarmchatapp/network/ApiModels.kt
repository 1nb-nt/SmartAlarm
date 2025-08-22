package com.example.alarmchatapp.network

import com.google.gson.annotations.SerializedName

// Data class for the JSON you send TO the API (This remains the same)
data class AlarmApiRequest(
    val query: String
)

// **MODIFIED**: Data class that now perfectly matches the JSON you receive FROM the API
data class AlarmApiResponse(
    @SerializedName("p_type")
    val pType: String,

    @SerializedName("alarm type")
    val alarmType: String,

    val title: String,
    val datetime: String,
    val time: String,
    val location: String?, // The '?' makes it nullable to handle `null` values
    val distance: String?, // The '?' makes it nullable to handle `null` values
    val timezone: String,
    val recurrence: String,
    val daysOfWeek: List<Int>?,
    val notification: NotificationInfo // A nested data class for the "notification" object
)

// **NEW**: A separate data class to represent the nested "notification" object in the JSON
data class NotificationInfo(
    @SerializedName("30_minutes_before")
    val thirtyMinutesBefore: Boolean,

    @SerializedName("10_minutes_before")
    val tenMinutesBefore: Boolean
)
