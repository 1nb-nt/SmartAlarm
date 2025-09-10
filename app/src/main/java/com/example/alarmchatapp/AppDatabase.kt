package com.example.alarmchatapp

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ScheduledTask::class,   // keep if this entity exists in the project
        Alarm::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class) // register all converters for the DB scope
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduledTaskDao(): ScheduledTaskDao
    abstract fun alarmDao(): AlarmDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
