package com.example.alarmchatapp.utils

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

object Utilities {
    fun parseISOToMillis(iso: String): Long {
        return OffsetDateTime.parse(iso).toInstant().toEpochMilli()
    }

    fun formatTimeMillisToISO(timeMillis: Long): String {
        return OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(timeMillis), java.time.ZoneId.systemDefault())
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
}
