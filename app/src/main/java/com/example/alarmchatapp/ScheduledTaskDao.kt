package com.example.alarmchatapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ScheduledTaskDao {
    @Insert
    suspend fun insert(task: ScheduledTask): Long

    @Update
    suspend fun update(task: ScheduledTask)

    @Query("SELECT * FROM scheduled_tasks")
    suspend fun getAll(): List<ScheduledTask>

    @Query("DELETE FROM scheduled_tasks WHERE id=:id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM scheduled_tasks")
    suspend fun deleteAll()
}
