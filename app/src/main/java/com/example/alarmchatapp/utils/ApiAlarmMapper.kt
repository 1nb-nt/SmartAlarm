package com.example.alarmchatapp.utils

import com.example.alarmchatapp.Alarm
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

data class ApiAlarm(
    val title: String,
    val datetimeIso: String,            // "2025-10-11T18:00:00+05:30"
    val time24: String,                 // "18:00"
    val timezone: String,               // "Asia/Calcutta"
    val recurrenceShort: List<String>?, // e.g., ["Sat","Sun"] or ["Mon"] or ["daily"]
    val initialNote: String?
)

object ApiAlarmMapper {

    fun toAlarmAndEpoch(api: ApiAlarm, nowMs: Long = System.currentTimeMillis()): Pair<Alarm, Long> {
        val zone = runCatching { ZoneId.of(api.timezone) }.getOrElse { ZoneId.systemDefault() }
        val parsed = OffsetDateTime.parse(api.datetimeIso, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val baseMs = parsed.toInstant().toEpochMilli()

        val (h, m) = api.time24.split(":").map { it.toInt() }

        // Expand daily/everyday/all to all 7 weekdays
        val expandedShort = when {
            api.recurrenceShort.isNullOrEmpty() -> emptyList()
            api.recurrenceShort.size == 1 && api.recurrenceShort.first().equals("daily", true) ->
                listOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")
            api.recurrenceShort.size == 1 && api.recurrenceShort.first().equals("everyday", true) ->
                listOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")
            api.recurrenceShort.size == 1 && api.recurrenceShort.first().equals("all", true) ->
                listOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")
            else -> api.recurrenceShort
        }

        val recurringDaysInts = expandedShort
            .mapNotNull { AlarmHelper.dayShortToCal[it] }

        val firstTrigger = if (recurringDaysInts.isNotEmpty()) {
            AlarmHelper.computeNextAmongDays(h, m, recurringDaysInts, nowMs)
        } else {
            // one-time: anchor on parsed date/time, roll forward if past
            val base = Calendar.getInstance().apply {
                timeInMillis = baseMs
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, m)
            }
            if (base.timeInMillis <= nowMs) base.add(Calendar.DAY_OF_YEAR, 1)
            base.timeInMillis
        }

        val alarm = Alarm(
            message = api.title.ifBlank { "Alarm" },
            triggerTimeMillis = firstTrigger,
            isRecurring = recurringDaysInts.isNotEmpty(),
            recurringDays = if (recurringDaysInts.isNotEmpty()) recurringDaysInts else null,
            initialNote = api.initialNote
        )
        return alarm to firstTrigger
    }
}
