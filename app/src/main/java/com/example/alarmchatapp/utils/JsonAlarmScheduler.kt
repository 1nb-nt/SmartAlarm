package com.example.alarmchatapp.utils

import android.content.Context
import com.example.alarmchatapp.workers.ClockPreSchedulerWorker
import java.time.OffsetDateTime

object JsonAlarmScheduler {

    fun scheduleFromNotifications(
        context: Context,
        title: String,
        isoList: List<String>,
        recurringDays: List<Int>? = null
    ) {
        if (!recurringDays.isNullOrEmpty()) {
            val first = isoList.firstOrNull() ?: return
            val t = OffsetDateTime.parse(first).toInstant().toEpochMilli()
            ClockPreSchedulerWorker.enqueue(context, title, t, true, recurringDays)
        } else {
            isoList.distinct().forEach { iso ->
                val t = OffsetDateTime.parse(iso).toInstant().toEpochMilli()
                ClockPreSchedulerWorker.enqueue(context, title, t, false, null)
            }
        }
    }
}
