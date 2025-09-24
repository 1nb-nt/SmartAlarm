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

    // Force-create up to three one-shot alarms immediately in Google Clock,
// inserting them into Room first so labels include “· #id”.
// Use this for “important + explicit date” and “important + weekday” paths.
    suspend fun forcePlaceThreeOneShotsInClock(
        context: Context,
        dao: AlarmDao,
        title: String,
        futureTimes: List<Long>
    ): Int {
        val three = futureTimes.distinct().sorted().take(3)
        var count = 0
        for (t in three) {
            // Insert local row first for stable unique label
            val id = runCatching {
                dao.insert(
                    Alarm(
                        message = title,
                        triggerTimeMillis = t,
                        isRecurring = false,
                        recurringDays = null
                    )
                ).toInt()
            }.getOrElse { -1 }

            // Optional: try to dismiss any stale duplicates (best-effort)
            if (id > 0) {
                runCatching {
                    cancelOneShotInClock(
                        context = context,
                        title = title,
                        alarmId = id,
                        triggerAtMillis = t
                    )
                }.onFailure { /* ignore */ }
            }

            // Create the Clock alarm immediately for the HH:mm of 't'
            scheduleAlarmClockPublic(
                context = context,
                label = title,
                triggerAt = t,
                alarmId = if (id > 0) id else (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
                initialNote = null,
                skipUi = true,
                showToast = false
            )

            // Tiny stagger to avoid OEM dropping back-to-back creates
            try {
                android.os.SystemClock.sleep(200L)
            } catch (_: Throwable) { }

            count++
        }
        return count
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
}