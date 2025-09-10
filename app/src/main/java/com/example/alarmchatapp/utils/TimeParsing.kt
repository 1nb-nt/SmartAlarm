
package com.example.alarmchatapp.util

import org.threeten.bp.OffsetDateTime
import org.threeten.bp.format.DateTimeFormatter

fun parseIsoToMillis(iso: String): Long =
    OffsetDateTime.parse(iso, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        .toInstant().toEpochMilli()
