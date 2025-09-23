package com.example.alarmchatapp.network

import com.example.alarmchatapp.network.NetworkAlarm
import java.util.Calendar
import java.util.Locale

// Optional: parser for remote/NLP commands if the server accepts chat text.
// Here we mirror the app-side parser to build NetworkAlarm rows from free text.
object AlarmParser {

    fun parse(text: String): List<NetworkAlarm> {
        val lower = text.lowercase(Locale.getDefault())
        val now = Calendar.getInstance()

        // Parse time
        var hour: Int? = null
        var minute: Int? = null
        val re12 = Regex("""\b(1[0-2]|0?[1-9])[:.]?([0-5][0-9])?\s*(am|pm)\b""", RegexOption.IGNORE_CASE)
        val re24 = Regex("""\b([01]?[0-9]|2[0-3]):([0-5][0-9])\b""", RegexOption.IGNORE_CASE)
        when {
            re12.containsMatchIn(lower) -> {
                val m = re12.find(lower)!!
                var h = m.groupValues[1].toInt()
                val mm = m.groupValues[2].ifBlank { "0" }.toInt()
                val ap = m.groupValues[3].lowercase()
                if (ap == "pm" && h != 12) h += 12
                if (ap == "am" && h == 12) h = 0
                hour = h; minute = mm
            }
            re24.containsMatchIn(lower) -> {
                val m = re24.find(lower)!!
                hour = m.groupValues[1].toInt()
                minute = m.groupValues[2].toInt()
            }
        }
        val h = hour ?: 9
        val m = minute ?: 0

        // Weekdays
        val daysMap = mapOf(
            "sunday" to Calendar.SUNDAY, "monday" to Calendar.MONDAY, "tuesday" to Calendar.TUESDAY,
            "wednesday" to Calendar.WEDNESDAY, "thursday" to Calendar.THURSDAY,
            "friday" to Calendar.FRIDAY, "saturday" to Calendar.SATURDAY
        )
        val recurringDays = daysMap.filter { lower.contains(it.key) }.values.toList().ifEmpty { null }

        // Explicit date dd-mm-yyyy or dd/mm/yyyy
        val reDate = Regex("""\b([0-3]?\d)[-/]([01]?\d)[-/](20\d\d)\b""")
        val explicitEpoch = reDate.find(lower)?.let { d ->
            val day = d.groupValues[1].toInt()
            val mon = d.groupValues[2].toInt() - 1
            val yr = d.groupValues[3].toInt()
            Calendar.getInstance().apply {
                set(Calendar.YEAR, yr); set(Calendar.MONTH, mon); set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        // Next occurrence if needed
        val timeMillis = explicitEpoch ?: run {
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
            }.timeInMillis
        }

        val isImportant = lower.contains("important")
        val label = when {
            lower.contains("lunch") -> "Eat lunch"
            lower.contains("drink tea") -> "Drink tea"
            lower.contains("doctor") -> "Doctor appointment"
            lower.contains("meeting") && isImportant -> "Important meeting"
            lower.contains("meeting") -> "Meeting"
            else -> "Alarm"
        }

        // For recurring, compute the first upcoming trigger similarly
        val firstMillis = if (!recurringDays.isNullOrEmpty()) {
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                var add = 0
                while (add <= 7) {
                    val c = (clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, add) }
                    if (recurringDays.contains(c.get(Calendar.DAY_OF_WEEK)) && c.timeInMillis > now.timeInMillis) {
                        timeInMillis = c.timeInMillis; break
                    }
                    add++
                }
            }.timeInMillis
        } else timeMillis

        return listOf(
            NetworkAlarm(
                label = label,
                timeMillis = firstMillis,
                important = isImportant,
                recurringDays = recurringDays
            )
        )
    }
}
