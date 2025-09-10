package com.example.alarmchatapp

import androidx.room.TypeConverter
import java.time.LocalTime
import java.util.Date

class Converters {
    @TypeConverter fun fromLocalTime(value: LocalTime?): String? = value?.toString()
    @TypeConverter fun toLocalTime(value: String?): LocalTime? = value?.let { LocalTime.parse(it) }

    @TypeConverter fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }
    @TypeConverter fun dateToTimestamp(date: Date?): Long? = date?.time

    @TypeConverter fun fromIntList(list: List<Int>?): String? = list?.joinToString(",")
    @TypeConverter fun toIntList(csv: String?): List<Int>? =
        csv?.takeIf { it.isNotBlank() }?.split(",")?.mapNotNull { it.toIntOrNull() }
}
