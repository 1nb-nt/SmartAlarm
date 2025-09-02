package com.example.alarmchatapp.network

import com.google.gson.annotations.SerializedName

data class AlarmApiRequest(
    val objective: String,
    val objective_key: String,
    val model: String,
    val inputs: Map<String, String>
)

data class AlarmApiResponse(
    @SerializedName("p_type")
    val pType: String,
    @SerializedName("alarm type")
    val alarmType: String,
    val title: String,
    val datetime: String?,
    val time: String?,
    val location: String?,
    val distance: String?,
    val timezone: String?,
    val recurrence: String?,
    val daysOfWeek: List<Int>?,
    val notification: NotificationInfo?
) {
    data class NotificationInfo(
        @SerializedName("important")
        val important: Boolean = false,
        @SerializedName("30_minutes_before")
        val thirtyMinutesBefore: Boolean = false,
        @SerializedName("10_minutes_before")
        val tenMinutesBefore: Boolean = false
    )
}
