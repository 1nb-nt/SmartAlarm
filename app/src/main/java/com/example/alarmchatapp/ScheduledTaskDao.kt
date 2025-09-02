package com.example.alarmchatapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete

@Dao
interface ScheduledTaskDao {
    @Insert
    suspend fun insert(task: ScheduledTask)

    @Query("SELECT * FROM scheduled_tasks WHERE executionTime <= :currentTime ORDER BY executionTime ASC")
    suspend fun getTasksDue(currentTime: Long): List<ScheduledTask>

    @Update
    suspend fun update(task: ScheduledTask)

    @Delete
    suspend fun delete(task: ScheduledTask)
}
