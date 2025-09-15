package com.example.alarmchatapp.network

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.Locale

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AlarmContract(
    val p_type: String = "alarm",
    val alarm_type: String? = null,
    val title: String? = null,
    val datetime: String? = null,   // ISO 8601
    val time: String? = null,       // "HH:mm"
    val location: String? = null,
    val distance: String? = null,
    val timezone: String? = "Asia/Kolkata",
    val recurrence: Any? = "once",  // String or List<String>
    val ex_days: List<String> = emptyList(),
    val notification: List<String> = emptyList(),
    val response: String? = null,
    val initial_note: String? = null
)

object AlarmParser {
    private val mapper = jacksonObjectMapper()

    fun parseAlarmJson(json: String): AlarmContract {
        return try {
            val rawMap: Map<String, Any?> =
                mapper.readValue(json, object : TypeReference<Map<String, Any?>>() {})
            val recurrence: Any? = rawMap["recurrence"]?.let {
                when (it) {
                    is String -> it
                    is List<*> -> it.filterIsInstance<String>()
                    else -> "once"
                }
            } ?: "once"

            AlarmContract(
                p_type = rawMap["p_type"] as? String ?: "alarm",
                alarm_type = (rawMap["alarm_type"] as? String) ?: (rawMap["alarm type"] as? String),
                title = rawMap["title"] as? String,
                datetime = rawMap["datetime"] as? String,
                time = rawMap["time"] as? String,
                location = rawMap["location"] as? String,
                distance = normalizeDistance(rawMap["distance"] as? String),
                timezone = rawMap["timezone"] as? String ?: "Asia/Kolkata",
                recurrence = recurrence,
                ex_days = (rawMap["ex_days"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                notification = when (val n = rawMap["notification"]) {
                    is List<*> -> n.filterIsInstance<String>()
                    else -> emptyList()
                },
                response = rawMap["response"] as? String,
                initial_note = rawMap["initial_note"] as? String

            )
        } catch (e: Exception) {
            println("Error parsing JSON: ${e.message}")
            AlarmContract()
        }
    }

    fun validateAndFixAlarm(alarm: AlarmContract): Pair<AlarmContract, List<String>> {
        val issues = mutableListOf<String>()
        var fixedNotifications: MutableList<OffsetDateTime> = mutableListOf()

        val eventTime: OffsetDateTime? = alarm.datetime?.let {
            if (isValidISO(it)) OffsetDateTime.parse(it) else {
                issues.add("Invalid datetime format: $it")
                null
            }
        }

        alarm.notification.forEach { nt ->
            if (!isValidISO(nt)) {
                issues.add("Invalid notification datetime: $nt (removed)")
            } else {
                fixedNotifications.add(OffsetDateTime.parse(nt))
            }
        }

        fixedNotifications = fixedNotifications.toSet().toMutableList()
        fixedNotifications.sort()

        if (eventTime != null && !fixedNotifications.contains(eventTime)) {
            fixedNotifications.add(eventTime)
            fixedNotifications.sort()
            issues.add("Event datetime was missing, added automatically")
        }

        val fixedAlarm = alarm.copy(
            distance = normalizeDistance(alarm.distance),
            notification = fixedNotifications.map { it.toString() },
            response = alarm.response,
            initial_note = alarm.initial_note
        )
        return fixedAlarm to (if (issues.isEmpty()) listOf("✅ Alarm is valid") else issues)
    }

    private fun isValidISO(dateTime: String): Boolean = try {
        OffsetDateTime.parse(dateTime); true
    } catch (_: DateTimeParseException) { false }

    private fun normalizeDistance(distance: String?): String? {
        if (distance == null) return null
        val lower = distance.lowercase(Locale.getDefault()).trim()
        return when {
            lower.contains("km") -> {
                val value = lower.replace("km", "").trim().toDoubleOrNull()
                if (value != null) "%.2f KM".format(value) else distance
            }
            lower.contains("meter") || lower.contains("m ") -> {
                val value = lower.replace(Regex("[^0-9.]"), "").toDoubleOrNull()
                if (value != null) "%.2f KM".format(value / 1000.0) else distance
            }
            lower.contains("mile") -> {
                val value = lower.replace(Regex("[^0-9.]"), "").toDoubleOrNull()
                if (value != null) "%.2f KM".format(value * 1.60934) else distance
            }
            lower.contains("feet") || lower.contains("ft") -> {
                val value = lower.replace(Regex("[^0-9.]"), "").toDoubleOrNull()
                if (value != null) "%.2f KM".format(value / 3280.84) else distance
            }
            else -> distance
        }
    }
}
