// app/src/main/java/com/example/alarmchatapp/utils/AlarmHelper.kt
package com.example.alarmchatapp.utils

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.alarmchatapp.Alarm
import com.example.alarmchatapp.AlarmDao
import java.util.Calendar
import java.util.Date

object AlarmHelper {

    // One-time alarm via Google Clock (minute precision).
    // showToast=false is recommended when called from workers.
    fun scheduleAlarmClockPublic(
        context: Context,
        label: String,
        triggerAt: Long,
        alarmId: Int,
        initialNote: String? = null,
        skipUi: Boolean = true,
        showToast: Boolean = true
    ) {
        SystemAlarmScheduler.setOneTimeAlarm(
            context = context,
            label = labelForId(label, alarmId, initialNote), // "Title · #id — note?"
            triggerAtMillis = triggerAt,
            vibrate = true,
            ringtone = null,
            skipUi = skipUi
        )
        if (showToast) {
            val main = Looper.getMainLooper()
            if (Looper.myLooper() == main) {
                Toast.makeText(
                    context.applicationContext,
                    "Alarm scheduled: $label at ${Date(triggerAt)}",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Handler(main).post {
                    Toast.makeText(
                        context.applicationContext,
                        "Alarm scheduled: $label at ${Date(triggerAt)}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // Weekly/daily recurring alarm using AlarmClock EXTRA_DAYS (Calendar constants).
    // IMPORTANT: alarmId is required so the label includes “· #id” for best-effort deletion later.
    fun scheduleWeeklyInClock(
        context: Context,
        label: String,
        hour: Int,
        minute: Int,
        days: List<Int>,
        alarmId: Int,
        ringtone: Uri? = null,
        skipUi: Boolean = true,
        showToast: Boolean = false
    ) {
        SystemAlarmScheduler.setWeeklyAlarm(
            context = context,
            label = labelForId(label, alarmId, null),
            hour = hour,
            minute = minute,
            days = days,
            vibrate = true,
            skipUi = skipUi
        )
        if (showToast) {
            val hh = hour.toString().padStart(2, '0')
            val mm = minute.toString().padStart(2, '0')
            val dayNames = days.joinToString(", ") { getDayNameByCalendar(it) }
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context.applicationContext,
                    "Weekly alarm: $label at $hh:$mm on $dayNames",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Programmatic best-effort delete for a one-shot in Google Clock.
    // Tries by unique label, then by time, then opens Clock UI if OEM ignores dismiss.
    fun cancelOneShotInClock(
        context: Context,
        title: String,
        alarmId: Int,
        triggerAtMillis: Long
    ) {
        val uniqueLabel = labelForId(title, alarmId)
        ClockDismissHelper.dismissByLabel(context, uniqueLabel)
        val (h, m) = hourMinuteOf(triggerAtMillis)
        ClockDismissHelper.dismissByTime(context, h, m)
        SystemAlarmScheduler.showAlarms(context)
    }

    // Programmatic best-effort delete for a recurring series (weekly/daily).
    // Uses unique label and the series hour:minute; then opens Clock UI as fallback.
    fun cancelRecurringInClock(
        context: Context,
        title: String,
        alarmId: Int,
        hour: Int,
        minute: Int
    ) {
        val uniqueLabel = labelForId(title, alarmId)
        ClockDismissHelper.dismissByLabel(context, uniqueLabel)
        ClockDismissHelper.dismissByTime(context, hour, minute)
        SystemAlarmScheduler.showAlarms(context)
    }

    // Helpers

    private fun labelWithNote(label: String, note: String?): String =
        if (note.isNullOrBlank()) label else "$label — $note"

    private fun labelForId(title: String, id: Int, note: String? = null): String {
        val base = "${title.trim()} · #$id"
        return if (note.isNullOrBlank()) base else "$base — $note"
    }

    private fun hourMinuteOf(millis: Long): Pair<Int, Int> {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return cal.get(Calendar.HOUR_OF_DAY) to cal.get(Calendar.MINUTE)
    }

    // Next occurrence among provided weekdays at hour:minute.
    fun computeNextAmongDays(hour: Int, minute: Int, days: List<Int>): Long {
        var best: Long? = null
        for (dow in days) {
            val candidate = getNextAlarmTimeForDay(hour, minute, dow)
            if (best == null || candidate < best) best = candidate
        }
        return requireNotNull(best)
    }

    // Next time for a specific weekday at hour:minute.
    fun getNextAlarmTimeForDay(hour: Int, minute: Int, dayOfWeek: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
        }
        if (cal.before(Calendar.getInstance())) cal.add(Calendar.WEEK_OF_YEAR, 1)
        return cal.timeInMillis
    }

    fun getDayNameByCalendar(day: Int) = when (day) {
        Calendar.SUNDAY -> "Sunday"
        Calendar.MONDAY -> "Monday"
        Calendar.TUESDAY -> "Tuesday"
        Calendar.WEDNESDAY -> "Wednesday"
        Calendar.THURSDAY -> "Thursday"
        Calendar.FRIDAY -> "Friday"
        Calendar.SATURDAY -> "Saturday"
        else -> "Unknown"
    }
    suspend fun forcePlaceThreeOneShotsInClock(
        context: Context,
        dao: AlarmDao,
        title: String,
        futureTimes: List<Long>
    ): Int {
        var count = 0
        futureTimes.take(3).forEach { t ->
            val id = dao.insert(
                Alarm(
                    message = title,
                    triggerTimeMillis = t,
                    isRecurring = false,
                    recurringDays = null
                )
            ).toInt()

            // Remove any existing matching Clock entry first
            AlarmHelper.cancelOneShotInClock(
                context = context,
                title = title,
                alarmId = id,
                triggerAtMillis = t
            )

            // Create the Clock alarm immediately
            AlarmHelper.scheduleAlarmClockPublic(
                context = context,
                label = title,
                triggerAt = t,
                alarmId = id,
                initialNote = null,
                skipUi = true,
                showToast = false
            )
            count++
        }
        return count
    }

}
