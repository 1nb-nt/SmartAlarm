package com.example.alarmchatapp

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromIntList(value: List<Int>?): String? = value?.joinToString(",")

    @TypeConverter
    fun toIntList(value: String?): List<Int>? =
        value?.split(",")?.mapNotNull { it.toIntOrNull() }
}
