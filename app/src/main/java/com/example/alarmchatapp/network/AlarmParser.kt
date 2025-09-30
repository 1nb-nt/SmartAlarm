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
    val time: String? = null,              // HH:mm or HHmm
    val location: String? = null,
    val distance: String? = null,          // normalized to KM in validate
    val timezone: String? = "Asia/Kolkata",
    val recurrence: Any? = "once",         // "once" | "daily" | [days...]
    val exdays: List<String> = emptyList(),
    val notification: List<String> = emptyList(), // ISO 8601 strings
    val responseText: String? = null       // natural-language reply from server
)

object AlarmParser {
    private val mapper = jacksonObjectMapper()

    fun parseAlarmJson(json: String): AlarmContract = runCatching {
        val raw: Map<String, Any?> =
            mapper.readValue(json, object : TypeReference<Map<String, Any?>>() {})

        fun getString(vararg keys: String): String? {
            for (k in keys) {
                val v = raw[k]
                if (v is String && v.isNotBlank()) return v
            }
            return null
        }
        fun getStringList(vararg keys: String): List<String> {
            for (k in keys) {
                val v = raw[k]
                if (v is List<*>) return v.filterIsInstance<String>()
            }
            return emptyList()
        }

        val recurrenceAny: Any? = raw["recurrence"]?.let {
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
            // Accept common variants and map to responseText used by UI
            responseText = getString("response", "responseText", "message")
        )
    }.getOrElse {
        AlarmContract()
    }

    fun validateAndFixAlarm(alarm: AlarmContract): Pair<AlarmContract, List<String>> {
        val issues = mutableListOf<String>()
        val fixedList = mutableListOf<OffsetDateTime>()

        val eventTime = alarm.datetime?.let {
            if (isValidISO(it)) OffsetDateTime.parse(it) else {
                issues.add("Invalid datetime format: $it")
                null
            }
        }

        alarm.notification.forEach { nt ->
            if (isValidISO(nt)) fixedList.add(OffsetDateTime.parse(nt))
            else issues.add("Invalid notification datetime: $nt (removed)")
        }

        val dedup = fixedList.toSet().toMutableList().apply { sort() }

        if (eventTime != null && !dedup.contains(eventTime)) {
            dedup.add(eventTime)
            dedup.sort()
            issues.add("Event datetime was missing, added automatically")
        }

        val fixed = alarm.copy(
            distance = normalizeDistance(alarm.distance),
            notification = dedup.map { it.toString() }
        )
        return fixed to if (issues.isEmpty()) listOf("✅ Alarm is valid") else issues
    }

    private fun isValidISO(dt: String): Boolean = try {
        OffsetDateTime.parse(dt); true
    } catch (_: DateTimeParseException) { false }

    private fun normalizeDistance(distance: String?): String? {
        if (distance == null) return null
        val lower = distance.lowercase(Locale.getDefault()).trim()
        return when {
            "km" in lower -> lower.replace("km", "").trim().toDoubleOrNull()?.let { "%.2f KM".format(it) } ?: distance
            "meter" in lower || "m " in lower || lower == "m" ->
                lower.replace(Regex("[^0-9.]"), "").toDoubleOrNull()?.let { "%.2f KM".format(it / 1000.0) } ?: distance
            "mile" in lower || "mi" in lower ->
                lower.replace(Regex("[^0-9.]"), "").toDoubleOrNull()?.let { "%.2f KM".format(it * 1.60934) } ?: distance
            "feet" in lower || "ft" in lower ->
                lower.replace(Regex("[^0-9.]"), "").toDoubleOrNull()?.let { "%.2f KM".format(it / 3280.84) } ?: distance
            else -> distance
        }
    }
}
