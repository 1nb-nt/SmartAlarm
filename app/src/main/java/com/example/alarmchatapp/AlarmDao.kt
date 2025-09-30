package com.example.alarmchatapp

import androidx.room.*

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): Alarm?

    @Query("SELECT * FROM alarms ORDER BY triggerTimeMillis ASC")
    suspend fun getAll(): List<Alarm>

    @Insert
    suspend fun insert(alarm: Alarm): Long

    @Update
    suspend fun update(alarm: Alarm)

    @Delete
    suspend fun delete(alarm: Alarm)

    @Query("SELECT * FROM alarms WHERE triggerTimeMillis <= :now ORDER BY triggerTimeMillis ASC")
    suspend fun getDue(now: Long): List<Alarm>
}

