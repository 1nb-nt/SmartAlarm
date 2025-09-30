package com.example.alarmchatapp.network

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.Locale

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AlarmContract(
    val ptype: String = "alarm",
    val alarmtype: String? = null,
    val title: String? = null,
    val datetime: String? = null,          // ISO 8601
    val time: String? = null,              // e.g. HH:mm or HHmm
    val location: String? = null,
    val distance: String? = null,          // normalized to KM in validate
    val timezone: String? = "Asia/Kolkata",
    val recurrence: Any? = "once",         // "once" | "daily" | [days...]
    val exdays: List<String> = emptyList(),
    val notification: List<String> = emptyList(), // list of ISO 8601 strings
    val responseText: String? = null       // optional natural-language response
)

object AlarmParser {
    private val mapper = jacksonObjectMapper()

    fun parseAlarmJson(json: String): AlarmContract {
        return try {
            val rawMap: Map<String, Any?> =
                mapper.readValue(json, object : TypeReference<Map<String, Any?>>() {})

            // Accept multiple key spellings from LLMs/backends
            fun getString(vararg keys: String): String? {
                for (k in keys) {
                    val v = rawMap[k]
                    if (v is String && v.isNotBlank()) return v
                }
                return null
            }

            fun getStringList(vararg keys: String): List<String> {
                for (k in keys) {
                    val v = rawMap[k]
                    if (v is List<*>) return v.filterIsInstance<String>()
                }
                return emptyList()
            }

            val recurrenceAny: Any? = rawMap["recurrence"]?.let {
                when (it) {
                    is String -> it
                    is List<*> -> it.filterIsInstance<String>()
                    else -> "once"
                }
            } ?: "once"

            AlarmContract(
                ptype = getString("ptype", "p_type") ?: "alarm",
                alarmtype = getString("alarmtype", "alarm_type", "alarm type"),
                title = getString("title"),
                datetime = getString("datetime"),
                time = getString("time"),
                location = getString("location"),
                distance = normalizeDistance(getString("distance")),
                timezone = getString("timezone") ?: "Asia/Kolkata",
                recurrence = recurrenceAny,
                exdays = getStringList("exdays", "ex_days", "ex_days_list"),
                notification = getStringList("notification", "notifications"),
                responseText = getString("responseText", "response", "message")
            )
        } catch (e: Exception) {
            // Fall back to safe defaults on any parsing problem
            AlarmContract()
        }
    }

    fun validateAndFixAlarm(alarm: AlarmContract): Pair<AlarmContract, List<String>> {
        val issues = mutableListOf<String>()
        val fixedList = mutableListOf<OffsetDateTime>()

        val eventTime: OffsetDateTime? = alarm.datetime?.let {
            if (isValidISO(it)) OffsetDateTime.parse(it) else {
                issues.add("Invalid datetime format: $it")
                null
            }
        }

        alarm.notification.forEach { nt ->
            if (isValidISO(nt)) {
                fixedList.add(OffsetDateTime.parse(nt))
            } else {
                issues.add("Invalid notification datetime: $nt (removed)")
            }
        }

        // Deduplicate and sort
        val dedup = fixedList.toSet().toMutableList()
        dedup.sort()

        // Ensure main event is included
        if (eventTime != null && !dedup.contains(eventTime)) {
            dedup.add(eventTime)
            dedup.sort()
            issues.add("Event datetime was missing, added automatically")
        }

        val fixedAlarm = alarm.copy(
            distance = normalizeDistance(alarm.distance),
            notification = dedup.map { it.toString() }
        )
        return fixedAlarm to if (issues.isEmpty()) listOf("✅ Alarm is valid") else issues
    }

    private fun isValidISO(dateTime: String): Boolean = try {
        OffsetDateTime.parse(dateTime); true
    } catch (_: DateTimeParseException) {
        false
    }

    private fun normalizeDistance(distance: String?): String? {
        if (distance == null) return null
        val lower = distance.lowercase(Locale.getDefault()).trim()
        return when {
            "km" in lower -> {
                val value = lower.replace("km", "").trim().toDoubleOrNull()
                if (value != null) "%.2f KM".format(value) else distance
            }
            "meter" in lower || "m " in lower || lower == "m" -> {
                val value = lower.replace(Regex("[^0-9.]"), "").toDoubleOrNull()
                if (value != null) "%.2f KM".format(value / 1000.0) else distance
            }
            "mile" in lower || "mi" in lower -> {
                val value = lower.replace(Regex("[^0-9.]"), "").toDoubleOrNull()
                if (value != null) "%.2f KM".format(value * 1.60934) else distance
            }
            "feet" in lower || "ft" in lower -> {
                val value = lower.replace(Regex("[^0-9.]"), "").toDoubleOrNull()
                if (value != null) "%.2f KM".format(value / 3280.84) else distance
            }
            else -> distance
        }
    }
}
