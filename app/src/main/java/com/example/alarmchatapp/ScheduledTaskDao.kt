package com.example.alarmchatapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete

@Dao
interface ScheduledTaskDao {
    @Insert
    suspend fun insert(task: ScheduledTask)

    // This is the key function to fetch tasks within the next 24 hours
    @Query("SELECT * FROM scheduled_tasks WHERE executionTimeMillis BETWEEN :startTime AND :endTime")
    suspend fun getTasksDue(startTime: Long, endTime: Long): List<ScheduledTask>

    @Delete
    suspend fun deleteTask(task: ScheduledTask)
}
