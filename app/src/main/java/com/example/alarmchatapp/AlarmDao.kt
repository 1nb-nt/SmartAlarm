package com.example.alarmchatapp

import androidx.room.*

@Dao
interface AlarmDao {
    @Insert
    suspend fun insert(alarm: Alarm): Long

    @Query("SELECT * FROM alarms ORDER BY triggerTimeMillis ASC")
    suspend fun getAll(): List<Alarm>

    @Query("SELECT * FROM alarms WHERE triggerTimeMillis >= :start ORDER BY triggerTimeMillis ASC LIMIT :limit")
    suspend fun getUpcomingAlarms(start: Long, limit: Int): List<Alarm>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getById(id: Int): Alarm?

    @Update
    suspend fun update(alarm: Alarm)

    @Delete
    suspend fun delete(alarm: Alarm)

    // NEW: used by the worker to fetch alarms due now or earlier
    @Query("SELECT * FROM alarms WHERE triggerTimeMillis <= :now ORDER BY triggerTimeMillis ASC")
    suspend fun getDue(now: Long): List<Alarm>

}
