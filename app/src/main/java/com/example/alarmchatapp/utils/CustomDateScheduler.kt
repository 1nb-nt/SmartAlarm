// app/src/main/java/com/example/alarmchatapp/utils/CustomDateScheduler.kt
package com.example.alarmchatapp.utils

import android.content.Context
import com.example.alarmchatapp.Alarm
import com.example.alarmchatapp.AlarmDao
import com.example.alarmchatapp.workers.ClockPreSchedulerWorker
import java.time.OffsetDateTime

suspend fun scheduleFromIsoList(
    context: Context,
    title: String,
    isoList: List<String>,
    dao: AlarmDao
): List<Int> {
    val now = System.currentTimeMillis()
    val futureTimes = isoList.mapNotNull {
        runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
    }.filter { it > now }.distinct().sorted()

    val createdIds = mutableListOf<Int>()
    for (t in futureTimes) {
        val id = dao.insert(
            Alarm(
                message = title.ifBlank { "Alarm" },
                triggerTimeMillis = t,
                isRecurring = false,
                recurringDays = null
            )
        ).toInt()

        ClockPreSchedulerWorker.enqueue(
            context = context,
            label = title.ifBlank { "Alarm" },
            triggerAt = t,
            isRecurring = false,
            recurringDays = null,
            alarmId = id
        )
        createdIds += id
    }
    return createdIds
}
