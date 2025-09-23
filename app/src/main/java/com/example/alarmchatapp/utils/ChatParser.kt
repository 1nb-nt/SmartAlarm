package com.example.alarmchatapp.utils

import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

data class ParsedCommand(
    val title: String,
    val hour: Int,
    val minute: Int,
    val explicitEpoch: Long? = null,        // for numeric dates like 30-11-2025 12:00
    val days: List<Int> = emptyList(),      // Calendar day constants for weekly
    val important: Boolean = false,         // add 30/10 min pre-alerts
    val isWeekly: Boolean = false,          // weekly recurrence
    val isOneShot: Boolean = true           // one-time when true
)

object ChatParser {
    private val dayMap = mapOf(
        "sunday" to Calendar.SUNDAY,
        "monday" to Calendar.MONDAY,
        "tuesday" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY,
        "thursday" to Calendar.THURSDAY,
        "friday" to Calendar.FRIDAY,
        "saturday" to Calendar.SATURDAY
    )

    private val time12 = Pattern.compile("""\b(1[0-2]|0?[1-9])[:.]?([0-5][0-9])?\s*(am|pm)\b""", Pattern.CASE_INSENSITIVE)
    private val time24 = Pattern.compile("""\b([01]?[0-9]|2[0-3]):([0-5][0-9])\b""", Pattern.CASE_INSENSITIVE)
    private val dateDmyDash = Pattern.compile("""\b([0-3]?\d)-([01]?\d)-(20\d\d)\b""") // dd-MM-YYYY
    private val weekdaysList = Pattern.compile("""\b(mon|tue|wed|thu|fri|sat|sun)(?:[,/ ]+(mon|tue|wed|thu|fri|sat|sun))*\b""", Pattern.CASE_INSENSITIVE)
    private val wordImportant = Pattern.compile("""\bimportant\b""", Pattern.CASE_INSENSITIVE)
    private val every = Pattern.compile("""\bevery\b""", Pattern.CASE_INSENSITIVE)

    fun parse(input: String, now: Long = System.currentTimeMillis()): ParsedCommand? {
        val lower = input.lowercase(Locale.getDefault())

        // detect "important"
        val important = wordImportant.matcher(lower).find()

        // time
        var hour = 9
        var minute = 0
        val m12 = time12.matcher(lower)
        val m24 = time24.matcher(lower)
        if (m12.find()) {
            hour = m12.group(1)!!.toInt()
            minute = m12.group(2)?.toInt() ?: 0
            val ampm = m12.group(3)!!.lowercase()
            if (ampm == "pm" && hour != 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
        } else if (m24.find()) {
            hour = m24.group(1)!!.toInt()
            minute = m24.group(2)!!.toInt()
        }

        // explicit dd-MM-YYYY
        var explicitEpoch: Long? = null
        val dmy = dateDmyDash.matcher(lower)
        if (dmy.find()) {
            val d = dmy.group(1)!!.toInt()
            val m = dmy.group(2)!!.toInt() - 1
            val y = dmy.group(3)!!.toInt()
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, y); set(Calendar.MONTH, m); set(Calendar.DAY_OF_MONTH, d)
                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            explicitEpoch = cal.timeInMillis
        }

        // weekdays
        val days = mutableListOf<Int>()
        // single weekday words
        for ((k, v) in dayMap) if (lower.contains(k)) { days.add(v); break }
        // list like Mon, Tue
        if (days.isEmpty()) {
            val w = weekdaysList.matcher(lower)
            if (w.find()) {
                for (i in 1..w.groupCount()) {
                    val g = w.group(i) ?: continue
                    val key = when (g.lowercase()) {
                        "mon" -> "monday"; "tue" -> "tuesday"; "wed" -> "wednesday"
                        "thu" -> "thursday"; "fri" -> "friday"; "sat" -> "saturday"; "sun" -> "sunday"
                        else -> continue
                    }
                    days.add(dayMap[key]!!)
                }
            }
        }

        val isWeekly = every.matcher(lower).find() || days.isNotEmpty()
        val isOneShot = !isWeekly

        val title = when {
            lower.contains("lunch") -> "Eat lunch"
            lower.contains("drink tea") || lower.contains("drink  tea") -> "Drink tea"
            lower.contains("meeting") -> "Important meeting"
            lower.contains("doctor") -> "Doctor appointment"
            else -> "Alarm"
        }

        return ParsedCommand(
            title = title,
            hour = hour,
            minute = minute,
            explicitEpoch = explicitEpoch,
            days = days.distinct(),
            important = important,
            isWeekly = isWeekly,
            isOneShot = isOneShot
        )
    }
}
