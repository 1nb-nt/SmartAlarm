package com.example.alarmchatapp

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.alarmchatapp.Alarm // Import your Alarm entity.
import com.example.alarmchatapp.AlarmDao
import com.example.alarmchatapp.ScheduledTask
import com.example.alarmchatapp.ScheduledTaskDao

// Add ScheduledTask to the entities list and increment the version number if needed
@Database(entities = [Alarm::class, ScheduledTask::class], version = 2) // Assuming Alarm entity exists
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun scheduledTaskDao(): ScheduledTaskDao // Add this abstract function

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "alarm_database"
                )
                    .fallbackToDestructiveMigration() // Use this for simplicity during development
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
