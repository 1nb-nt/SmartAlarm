package com.example.alarmchatapp.utils

import java.text.SimpleDateFormat
import java.util.*

object DateTimeUtils {
    fun parseDateTimeToMillis(datetime: String, pattern: String = "yyyy-MM-dd'T'HH:mm:ssXXX"): Long {
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        return sdf.parse(datetime)?.time ?: System.currentTimeMillis()
    }
}
