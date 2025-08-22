package com.example.alarmchatapp

import androidx.room.*

@Dao
interface AlarmDao {
    @Insert
    suspend fun insert(alarm: Alarm): Long

    @Query("SELECT * FROM alarms ORDER BY triggerTimeMillis ASC")
    suspend fun getAll(): List<Alarm>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getById(id: Int): Alarm?

    @Delete
    suspend fun delete(alarm: Alarm)

    @Update
    suspend fun update(alarm: Alarm)
}
