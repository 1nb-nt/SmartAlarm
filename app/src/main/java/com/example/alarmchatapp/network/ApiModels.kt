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
    val title: String?,
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
        val enabled: Boolean = false,
        val reminders: List<Reminder> = emptyList()
    )

    data class AlarmApiWrapper(
        val ref_id: String?,
        val prompt: String?,
        val response: String? // This is the string containing the JSON
    )
    data class Reminder(
        val id: Int,
        val text: String,
        // add more fields as needed
    )


}
