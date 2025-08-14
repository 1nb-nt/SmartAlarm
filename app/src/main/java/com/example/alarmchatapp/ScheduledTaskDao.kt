package com.example.alarmchatapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Update

@Dao
interface ScheduledTaskDao {
    @Insert
    suspend fun insert(task: ScheduledTask)

    // This is the key function to fetch tasks within the next 24 hours
    @Query("SELECT * FROM scheduled_tasks WHERE executionTimeMillis <= :currentTime")
    suspend fun getTasksDue(currentTime: Long): List<ScheduledTask>
    @Delete
    suspend fun deleteTask(task: ScheduledTask)

    @Update
    suspend fun update(task: ScheduledTask)
}
