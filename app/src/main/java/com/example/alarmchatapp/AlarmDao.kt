package com.example.alarmchatapp

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface AlarmDao {
    @Insert
    suspend fun insert(alarm: Alarm): Long

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getById(id: Int): Alarm?

    @Delete
    suspend fun delete(alarm: Alarm)

    @Update
    suspend fun update(alarm: Alarm)
}
