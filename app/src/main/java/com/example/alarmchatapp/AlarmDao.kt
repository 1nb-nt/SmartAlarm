package com.example.alarmchatapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AlarmDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(alarm: Alarm): Long

    @Query("SELECT * FROM alarms ORDER BY timeMillis ASC")
    fun getAllAlarms(): List<Alarm>

    @Query("SELECT * FROM alarms WHERE id = :id LIMIT 1")
    fun getById(id: Int): Alarm?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(alarm: Alarm): Long

    @Query("DELETE FROM alarms WHERE id = :id")
    fun deleteAlarm(id: Int)
}
