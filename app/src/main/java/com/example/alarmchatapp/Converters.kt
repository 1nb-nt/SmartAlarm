package com.example.alarmchatapp

import androidx.room.TypeConverter

class Converters {

    @TypeConverter
    fun fromIntList(list: List<Int>?): String? =
        list?.joinToString(",")

    @TypeConverter
    fun toIntList(csv: String?): List<Int>? =
        csv?.takeIf { it.isNotBlank() }
            ?.split(",")
            ?.mapNotNull { it.toIntOrNull() }
}
